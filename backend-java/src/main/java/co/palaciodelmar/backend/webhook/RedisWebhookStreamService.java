package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import co.palaciodelmar.backend.service.RedisLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pilar 5 Redis: Cola asíncrona confiable de Webhooks con Redis Streams (XADD / XREADGROUP / XACK)
 * y cola diferida de reintentos con Sorted Sets (ZSET).
 * Desacopla las transacciones críticas de reserva/pago de servidores PMS externos lentos o caídos.
 */
@Slf4j
@Service
public class RedisWebhookStreamService {

    public static final String STREAM_KEY = "palacio:webhooks:stream";
    public static final String CONSUMER_GROUP = "palacio-webhook-workers";
    public static final String RETRY_ZSET_KEY = "palacio:webhooks:retry_zset";
    private static final long MAX_STREAM_LENGTH = 10_000L;

    /**
     * Extrae atómicamente hasta N IDs de entregas cuyo tiempo de reintento ya venció (score <= nowMs).
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> POP_DUE_RETRIES_SCRIPT;

    static {
        POP_DUE_RETRIES_SCRIPT = new DefaultRedisScript<>();
        POP_DUE_RETRIES_SCRIPT.setScriptText(
                "local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, tonumber(ARGV[2])) " +
                "if #due > 0 then " +
                "  redis.call('ZREM', KEYS[1], unpack(due)) " +
                "end " +
                "return due"
        );
        POP_DUE_RETRIES_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDispatcher webhookDispatcher;
    private final RedisLockService redisLockService;
    private final String consumerName = "worker-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private final AtomicBoolean groupInitialized = new AtomicBoolean(false);

    @Autowired
    public RedisWebhookStreamService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            WebhookDeliveryRepository deliveryRepository,
            WebhookSubscriptionRepository subscriptionRepository,
            @Lazy WebhookDispatcher webhookDispatcher,
            @Autowired(required = false) RedisLockService redisLockService
    ) {
        this.redisTemplate = redisTemplate;
        this.deliveryRepository = deliveryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.webhookDispatcher = webhookDispatcher;
        this.redisLockService = redisLockService;
    }

    /**
     * Publica una entrega pendiente en el Redis Stream para despacho asíncrono inmediato.
     *
     * @return true si fue encolado en Redis; false si Redis no está disponible (activar fallback directo).
     */
    public boolean enqueueDelivery(String deliveryId, String eventId, String eventType) {
        if (redisTemplate == null || deliveryId == null || deliveryId.isBlank()) {
            return false;
        }
        try {
            ensureConsumerGroup();
            StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("deliveryId", deliveryId);
            fields.put("eventId", eventId != null ? eventId : "");
            fields.put("eventType", eventType != null ? eventType : "");
            fields.put("enqueuedAt", String.valueOf(Instant.now().toEpochMilli()));

            RecordId recordId = streamOps.add(STREAM_KEY, fields);
            if (recordId == null) {
                return false;
            }
            streamOps.trim(STREAM_KEY, MAX_STREAM_LENGTH, true);
            log.debug("Webhook delivery {} ({}) encolado en Redis Stream con ID {}", deliveryId, eventId, recordId.getValue());
            return true;
        } catch (Exception e) {
            log.warn("Redis Stream no disponible al encolar webhook {} ({}). Ejecutando fallback directo.", deliveryId, e.getMessage());
            groupInitialized.set(false);
            return false;
        }
    }

    /**
     * Registra un reintento con precisión de milisegundos en un Sorted Set de Redis.
     */
    public void scheduleRetry(String deliveryId, Instant nextRetryAt) {
        if (redisTemplate == null || deliveryId == null || deliveryId.isBlank() || nextRetryAt == null) {
            return;
        }
        try {
            redisTemplate.opsForZSet().add(RETRY_ZSET_KEY, deliveryId, (double) nextRetryAt.toEpochMilli());
        } catch (Exception e) {
            log.debug("No se pudo agendar reintento ZSET en Redis para {}: {}. MongoDB RetryScheduler actuará de respaldo.",
                    deliveryId, e.getMessage());
        }
    }

    /**
     * Worker periódico que promueve reintentos vencidos desde el ZSET y consume mensajes del Consumer Group.
     */
    @Scheduled(fixedDelayString = "${palacio.webhooks.stream.poll-ms:1000}")
    public void pollAndProcessStream() {
        if (redisTemplate == null) {
            return;
        }
        try {
            promoteDueRetries(25);
            consumeBatch(20);
        } catch (Exception e) {
            log.debug("Ciclo de RedisWebhookStreamService omitido ({})", e.getMessage());
            groupInitialized.set(false);
        }
    }

    @SuppressWarnings("unchecked")
    public int promoteDueRetries(int batchSize) {
        if (redisTemplate == null) {
            return 0;
        }
        List<Object> dueIds = redisTemplate.execute(
                POP_DUE_RETRIES_SCRIPT,
                List.of(RETRY_ZSET_KEY),
                String.valueOf(Instant.now().toEpochMilli()),
                String.valueOf(Math.max(1, batchSize))
        );
        if (dueIds == null || dueIds.isEmpty()) {
            return 0;
        }
        int promoted = 0;
        for (Object rawId : dueIds) {
            if (rawId != null) {
                String deliveryId = rawId.toString();
                if (enqueueDelivery(deliveryId, "retry", "webhook.retry")) {
                    promoted++;
                }
            }
        }
        return promoted;
    }

    @SuppressWarnings("unchecked")
    public int consumeBatch(int count) {
        if (redisTemplate == null) {
            return 0;
        }
        ensureConsumerGroup();
        StreamOperations<String, Object, Object> streamOps = redisTemplate.opsForStream();
        int processed = 0;

        // Recuperar y procesar mensajes huérfanos del PEL (idle >= 60s) por caída de workers previos
        try {
            processed += claimAbandonedPendingEntries(streamOps, count);
        } catch (Exception e) {
            log.debug("No se pudieron inspeccionar mensajes huérfanos en PEL para {}: {}", STREAM_KEY, e.getMessage());
        }

        List<MapRecord<String, Object, Object>> records = streamOps.read(
                Consumer.from(CONSUMER_GROUP, consumerName),
                StreamReadOptions.empty().count(Math.max(1, count)),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
        );

        if (records == null || records.isEmpty()) {
            return processed;
        }

        for (MapRecord<String, Object, Object> record : records) {
            Object rawDeliveryId = record.getValue().get("deliveryId");
            String deliveryId = rawDeliveryId != null ? rawDeliveryId.toString() : null;
            boolean shouldAck = true;
            if (deliveryId != null && !deliveryId.isBlank()) {
                shouldAck = processDeliverySafely(deliveryId);
            }
            if (shouldAck) {
                streamOps.acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
                processed++;
            }
        }
        return processed;
    }

    private int claimAbandonedPendingEntries(StreamOperations<String, Object, Object> streamOps, int batchLimit) {
        int claimedCount = 0;
        Duration minIdle = Duration.ofSeconds(60);
        String lastSeenId = "-";
        int pageLimit = Math.max(20, batchLimit);

        // Paginación exhaustiva del PEL (hasta 50 páginas) para drenar completamente mensajes huérfanos
        for (int page = 0; page < 50; page++) {
            PendingMessages pending = streamOps.pending(
                    STREAM_KEY,
                    CONSUMER_GROUP,
                    Range.closed(lastSeenId, "+"),
                    pageLimit
            );
            if (pending == null || pending.isEmpty()) {
                break;
            }

            int newEntriesInPage = 0;
            for (PendingMessage pm : pending) {
                String currentId = pm.getIdAsString();
                if (currentId.equals(lastSeenId)) {
                    continue; // Evitar procesar dos veces el límite de la página anterior
                }
                lastSeenId = currentId;
                newEntriesInPage++;

                // Si el mensaje ha fallado más de 5 veces en el stream, descartarlo con ACK para evitar veneno en la cola
                if (pm.getTotalDeliveryCount() >= 5) {
                    log.warn("Mensaje en stream de webhook descartado con XACK tras superar 5 entregas fallidas en PEL: id={}", pm.getIdAsString());
                    streamOps.acknowledge(STREAM_KEY, CONSUMER_GROUP, pm.getId());
                    continue;
                }
                if (pm.getElapsedTimeSinceLastDelivery() != null && pm.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0) {
                    List<MapRecord<String, Object, Object>> claimed = streamOps.claim(
                            STREAM_KEY,
                            CONSUMER_GROUP,
                            consumerName,
                            minIdle,
                            pm.getId()
                    );
                    if (claimed != null) {
                        for (MapRecord<String, Object, Object> record : claimed) {
                            Object rawDeliveryId = record.getValue().get("deliveryId");
                            String deliveryId = rawDeliveryId != null ? rawDeliveryId.toString() : null;
                            boolean shouldAck = true;
                            if (deliveryId != null && !deliveryId.isBlank()) {
                                shouldAck = processDeliverySafely(deliveryId);
                            }
                            if (shouldAck) {
                                streamOps.acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
                                claimedCount++;
                            }
                        }
                    }
                }
            }
            if (newEntriesInPage == 0 || pending.size() < pageLimit) {
                break;
            }
        }
        return claimedCount;
    }

    /**
     * Ejecuta la entrega de forma exclusiva usando RedisLockService para evitar colisiones
     * entre múltiples workers o el scheduler de respaldo de MongoDB.
     */
    public boolean processDeliverySafely(String deliveryId) {
        Optional<String> lockToken = Optional.empty();
        String lockName = "webhook:delivery:" + deliveryId;
        if (redisLockService != null) {
            lockToken = redisLockService.tryLock(lockName, Duration.ofSeconds(15));
            if (lockToken.isEmpty()) {
                return true; // Otro worker ya la está procesando
            }
        }
        try {
            WebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
            if (delivery == null) {
                return true;
            }
            if (!"PENDING".equalsIgnoreCase(delivery.getStatus())) {
                return true; // Ya entregada o fallida definitivamente
            }
            if (delivery.getNextRetryAt() != null && delivery.getNextRetryAt().isAfter(Instant.now())) {
                scheduleRetry(deliveryId, delivery.getNextRetryAt());
                return true;
            }

            WebhookSubscription sub = subscriptionRepository.findById(delivery.getSubscriptionId()).orElse(null);
            if (sub == null || !Boolean.TRUE.equals(sub.getActive())) {
                delivery.setStatus("FAILED");
                delivery.setErrorMessage(sub == null
                        ? "Suscripción de webhook no encontrada o eliminada"
                        : "Suscripción de webhook desactivada");
                delivery.setNextRetryAt(null);
                deliveryRepository.save(delivery);
                return true;
            }

            webhookDispatcher.executeDelivery(delivery, sub.getSecret());
            return true;
        } catch (Exception e) {
            log.warn("Error procesando entrega de webhook {} desde Redis Stream: {}", deliveryId, e.getMessage());
            return false; // Mantener en PEL (Pending Entries List) o permitir reintento posterior
        } finally {
            if (redisLockService != null && lockToken.isPresent()) {
                redisLockService.unlock(lockName, lockToken.get());
            }
        }
    }

    private void ensureConsumerGroup() {
        if (groupInitialized.get() || redisTemplate == null) {
            return;
        }
        try {
            byte[] keyBytes = STREAM_KEY.getBytes(StandardCharsets.UTF_8);
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                try {
                    connection.streamCommands().xGroupCreate(keyBytes, CONSUMER_GROUP, ReadOffset.from("0-0"), true);
                } catch (Exception ex) {
                    String msg = ex.getMessage() != null ? ex.getMessage() : "";
                    String causeMsg = ex.getCause() != null && ex.getCause().getMessage() != null ? ex.getCause().getMessage() : "";
                    if (!msg.contains("BUSYGROUP") && !causeMsg.contains("BUSYGROUP")) {
                        throw ex;
                    }
                }
                return null;
            });
            groupInitialized.set(true);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("BUSYGROUP")) {
                groupInitialized.set(true);
            } else {
                throw e;
            }
        }
    }
}
