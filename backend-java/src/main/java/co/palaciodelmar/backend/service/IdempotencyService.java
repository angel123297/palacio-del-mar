package co.palaciodelmar.backend.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Servicio de Idempotencia distribuida respaldado en Redis (y fallback L1 en memoria).
 * Evita cobros o reservas duplicadas ante dobles clics o reconexiones móviles:
 * - Registra IN_PROGRESS (TTL 60s) de forma atómica con SET NX.
 * - Rechaza peticiones concurrentes idénticas en vuelo con HTTP 409 Conflict.
 * - Almacena la respuesta exitosa por 24h y la reproduce sin reprocesar si los parámetros coinciden.
 * - Aísla las claves por actor/usuario y valida el hash SHA-256 del payload para impedir fugas o colisiones.
 */
@Slf4j
@Service
public class IdempotencyService {

    public static final String REDIS_PREFIX = "palacio:idempotency:";
    public static final int MAX_LOCAL_ENTRIES = 2_000;
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Duration IN_PROGRESS_TTL = Duration.ofSeconds(60);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private static final DefaultRedisScript<Long> CONDITIONAL_DELETE_SCRIPT;
    static {
        CONDITIONAL_DELETE_SCRIPT = new DefaultRedisScript<>();
        CONDITIONAL_DELETE_SCRIPT.setScriptText(
                "local cur = redis.call('get', KEYS[1]) " +
                "if cur and string.find(cur, ARGV[1], 1, true) then " +
                "  return redis.call('del', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end"
        );
        CONDITIONAL_DELETE_SCRIPT.setResultType(Long.class);
    }

    private StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private co.palaciodelmar.backend.security.RateLimitFilter rateLimitFilter;

    private record LocalEntry(String serializedRecord, long expiresAtMs) {}
    private final ConcurrentHashMap<String, LocalEntry> localStore = new ConcurrentHashMap<>();

    public record StoredRecord(
            String status,
            String ownerToken,
            String payloadHash,
            String responseJson
    ) {}

    public record IdempotentResult<T>(T body, boolean replayed) {}

    @Autowired
    public IdempotencyService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = (objectMapper != null ? objectMapper.copy() : new ObjectMapper())
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    int getLocalStoreSize() {
        return localStore.size();
    }

    public <T> IdempotentResult<T> execute(
            String scope,
            String actorId,
            String idempotencyKey,
            Object requestPayload,
            Class<T> responseType,
            Supplier<T> action
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new IdempotentResult<>(action.get(), false);
        }

        String trimmedKey = idempotencyKey.trim();
        if (!VALID_KEY_PATTERN.matcher(trimmedKey).matches()) {
            throw new IllegalArgumentException(
                    "Cabecera Idempotency-Key inválida: usa entre 1 y 128 caracteres alfanuméricos, guiones o puntos.");
        }

        String actorScope = resolveActorScope(actorId);
        String fullKey = REDIS_PREFIX + scope + ":" + actorScope + ":" + trimmedKey;
        String payloadHash = computePayloadHash(requestPayload);
        String ownerToken = "tok_" + UUID.randomUUID().toString().replace("-", "");

        StoredRecord inProgress = new StoredRecord("IN_PROGRESS", ownerToken, payloadHash, null);
        String inProgressJson = toJson(inProgress);

        String existingRaw = tryAcquireOrGetExisting(fullKey, inProgressJson);
        if (existingRaw != null) {
            return handleExistingRecord(existingRaw, payloadHash, responseType, fullKey);
        }

        try {
            T result = action.get();
            String resultJson = toJson(result);
            StoredRecord completed = new StoredRecord("COMPLETED", ownerToken, payloadHash, resultJson);
            saveCompleted(fullKey, toJson(completed));
            return new IdempotentResult<>(result, false);
        } catch (Throwable ex) {
            releaseInProgress(fullKey, ownerToken);
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            if (ex instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private String tryAcquireOrGetExisting(String fullKey, String inProgressJson) {
        if (redisTemplate != null) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(fullKey, inProgressJson, IN_PROGRESS_TTL);
                if (Boolean.TRUE.equals(acquired)) {
                    return null;
                }
                String existing = redisTemplate.opsForValue().get(fullKey);
                if (existing != null) {
                    return existing;
                }
                // Si expiró justo entre setIfAbsent y get, reintentar una vez
                Boolean retry = redisTemplate.opsForValue().setIfAbsent(fullKey, inProgressJson, IN_PROGRESS_TTL);
                if (Boolean.TRUE.equals(retry)) {
                    return null;
                }
                return redisTemplate.opsForValue().get(fullKey);
            } catch (Exception redisEx) {
                log.warn("Falla de Redis en IdempotencyService ({}): {}. Usando almacén L1.", fullKey, redisEx.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        LocalEntry[] existingHolder = new LocalEntry[1];
        localStore.compute(fullKey, (k, current) -> {
            if (current != null && current.expiresAtMs() > now) {
                existingHolder[0] = current;
                return current;
            }
            return new LocalEntry(inProgressJson, now + IN_PROGRESS_TTL.toMillis());
        });
        if (existingHolder[0] == null && localStore.size() > MAX_LOCAL_ENTRIES) {
            enforceLocalCapacity(now, fullKey);
        }
        return existingHolder[0] != null ? existingHolder[0].serializedRecord() : null;
    }

    private <T> IdempotentResult<T> handleExistingRecord(
            String existingRaw,
            String payloadHash,
            Class<T> responseType,
            String fullKey
    ) {
        StoredRecord record;
        try {
            record = objectMapper.readValue(existingRaw, StoredRecord.class);
        } catch (Exception e) {
            log.warn("Registro de idempotencia corrupto en {}, tratando como conflicto en progreso", fullKey);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu solicitud ya se está procesando. Por favor espera unos segundos.");
        }

        if ("IN_PROGRESS".equalsIgnoreCase(record.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu solicitud ya se está procesando. Evita hacer doble clic y espera unos segundos.");
        }

        if (!Objects.equals(record.payloadHash(), payloadHash)) {
            throw new IllegalArgumentException(
                    "La clave Idempotency-Key ya fue utilizada con parámetros de reserva o pago diferentes.");
        }

        try {
            T cachedResponse = objectMapper.readValue(record.responseJson(), responseType);
            log.info("Respuesta idempotente reproducida desde caché para clave {}", fullKey);
            return new IdempotentResult<>(cachedResponse, true);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo reconstruir la respuesta idempotente previa", e);
        }
    }

    private void saveCompleted(String fullKey, String completedJson) {
        long now = System.currentTimeMillis();
        localStore.put(fullKey, new LocalEntry(completedJson, now + COMPLETED_TTL.toMillis()));
        if (localStore.size() > MAX_LOCAL_ENTRIES) {
            enforceLocalCapacity(now, fullKey);
        }
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(fullKey, completedJson, COMPLETED_TTL);
            } catch (Exception e) {
                log.warn("No se pudo persistir resultado idempotente en Redis ({}): {}", fullKey, e.getMessage());
            }
        }
    }

    private String resolveActorScope(String actorId) {
        if (actorId != null && !actorId.isBlank()) {
            return sha256("u:" + actorId.trim());
        }
        org.springframework.web.context.request.RequestAttributes attrs =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra) {
            jakarta.servlet.http.HttpServletRequest req = sra.getRequest();
            String ip = (rateLimitFilter != null)
                    ? rateLimitFilter.resolveClientIp(req)
                    : (req.getRemoteAddr() != null ? req.getRemoteAddr().trim() : "unknown");
            jakarta.servlet.http.HttpSession session = req.getSession(false);
            String sessionPart = session != null ? session.getId() : "";
            return sha256("anon:" + ip + ":" + sessionPart);
        }
        return sha256("anon:no-request");
    }

    private synchronized void enforceLocalCapacity(long now, String protectedKey) {
        if (localStore.size() <= MAX_LOCAL_ENTRIES) {
            return;
        }
        localStore.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
        while (localStore.size() > MAX_LOCAL_ENTRIES) {
            String candidateKey = null;
            long earliestExpiry = Long.MAX_VALUE;
            for (var entry : localStore.entrySet()) {
                if (Objects.equals(entry.getKey(), protectedKey)) {
                    continue;
                }
                if (entry.getValue().expiresAtMs() < earliestExpiry) {
                    earliestExpiry = entry.getValue().expiresAtMs();
                    candidateKey = entry.getKey();
                }
            }
            if (candidateKey == null) {
                break;
            }
            localStore.remove(candidateKey);
        }
    }

    private void releaseInProgress(String fullKey, String ownerToken) {
        localStore.computeIfPresent(fullKey, (k, v) ->
                v.serializedRecord().contains(ownerToken) ? null : v);
        if (redisTemplate != null) {
            try {
                redisTemplate.execute(CONDITIONAL_DELETE_SCRIPT, Collections.singletonList(fullKey), ownerToken);
            } catch (Exception e) {
                log.warn("No se pudo liberar clave IN_PROGRESS en Redis ({}): {}", fullKey, e.getMessage());
            }
        }
    }

    private String computePayloadHash(Object payload) {
        if (payload == null) {
            return sha256("null");
        }
        try {
            return sha256(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            return sha256(String.valueOf(payload));
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Error serializando estado de idempotencia", e);
        }
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanExpiredLocal() {
        long now = System.currentTimeMillis();
        localStore.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
    }
}
