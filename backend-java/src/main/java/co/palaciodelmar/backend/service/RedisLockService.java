package co.palaciodelmar.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Servicio de bloqueo distribuido sobre Redis (SET NX PX + liberación atómica Lua CAS)
 * y retención temporal de inventario para eliminar condiciones de carrera (overbooking)
 * y doble confirmación de pagos. Incluye respaldo local en memoria (L1) ante fallas de red.
 */
@Slf4j
@Service
public class RedisLockService {

    public static final String LOCK_PREFIX = "palacio:lock:";
    public static final String HOLD_PREFIX = "palacio:hold:suite:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('del', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private StringRedisTemplate redisTemplate;

    // Respaldo L1 por bandas (striped locks) acotado en memoria: evita fugas de RAM y protege concurrencia local
    private static final int STRIPE_COUNT = 256;
    private final ReentrantLock[] localStripes = new ReentrantLock[STRIPE_COUNT];
    private record TimedOwner(String token, long expiresAtMs) {}
    private final ConcurrentHashMap<String, TimedOwner> localTryLocks = new ConcurrentHashMap<>();

    public RedisLockService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        for (int i = 0; i < STRIPE_COUNT; i++) {
            localStripes[i] = new ReentrantLock();
        }
    }

    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isDistributedConfigured() {
        return this.redisTemplate != null;
    }

    private ReentrantLock stripeFor(String key) {
        int idx = (key.hashCode() & 0x7fffffff) % STRIPE_COUNT;
        return localStripes[idx];
    }

    public <T> T executeWithLock(String resourceKey, Duration ttl, Duration maxWait, Supplier<T> action) {
        try (LockHandle ignored = acquireLock(resourceKey, ttl, maxWait)) {
            return action.get();
        }
    }

    public <T> T executeWithStrictDistributedLock(String resourceKey, Duration ttl, Duration maxWait, Supplier<T> action) {
        try (LockHandle ignored = acquireLock(resourceKey, ttl, maxWait, true)) {
            return action.get();
        }
    }

    /**
     * Adquisición no bloqueante (0ms espera) ideal para workers asíncronos y schedulers distribuidos.
     */
    public Optional<String> tryLock(String resourceKey, Duration ttl) {
        if (resourceKey == null || resourceKey.isBlank()) {
            return Optional.empty();
        }
        String sanitizedKey = LOCK_PREFIX + resourceKey.trim();
        String ownerToken = UUID.randomUUID().toString();
        long ttlMillis = ttl != null && ttl.toMillis() > 0 ? ttl.toMillis() : 10_000L;

        if (redisTemplate != null) {
            try {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(sanitizedKey, ownerToken, Duration.ofMillis(ttlMillis));
                return Boolean.TRUE.equals(acquired) ? Optional.of(ownerToken) : Optional.empty();
            } catch (Exception e) {
                log.debug("Redis no disponible en tryLock '{}': {}. Usando L1.", sanitizedKey, e.getMessage());
            }
        }

        long now = System.currentTimeMillis();
        boolean[] won = new boolean[]{false};
        localTryLocks.compute(sanitizedKey, (k, existing) -> {
            if (existing != null && existing.expiresAtMs() > now) {
                return existing;
            }
            won[0] = true;
            return new TimedOwner(ownerToken, now + ttlMillis);
        });
        return won[0] ? Optional.of(ownerToken) : Optional.empty();
    }

    public void unlock(String resourceKey, String ownerToken) {
        if (resourceKey == null || resourceKey.isBlank() || ownerToken == null) {
            return;
        }
        String sanitizedKey = LOCK_PREFIX + resourceKey.trim();
        localTryLocks.computeIfPresent(sanitizedKey, (k, existing) ->
                Objects.equals(existing.token(), ownerToken) ? null : existing);
        if (redisTemplate != null) {
            try {
                redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(sanitizedKey), ownerToken);
            } catch (Exception e) {
                log.debug("Falla al liberar tryLock '{}' en Redis: {}", sanitizedKey, e.getMessage());
            }
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300_000)
    public void cleanupLocalLocks() {
        long now = System.currentTimeMillis();
        localTryLocks.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
    }

    public LockHandle acquireLock(String resourceKey, Duration ttl, Duration maxWait) {
        return acquireLock(resourceKey, ttl, maxWait, false);
    }

    public LockHandle acquireLock(String resourceKey, Duration ttl, Duration maxWait, boolean requireDistributed) {
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("La clave del bloqueo distribuido es obligatoria");
        }
        String sanitizedKey = LOCK_PREFIX + resourceKey.trim();
        String ownerToken = UUID.randomUUID().toString();
        long waitMillis = maxWait != null ? Math.max(0, maxWait.toMillis()) : 0;
        long ttlMillis = ttl != null && ttl.toMillis() > 0 ? ttl.toMillis() : 10_000L;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(waitMillis);

        ReentrantLock localLock = stripeFor(sanitizedKey);
        boolean localAcquired = false;
        try {
            localAcquired = localLock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Operación interrumpida mientras se reservaba disponibilidad. Intenta nuevamente.");
        }

        if (!localAcquired) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta habitación o transacción está siendo procesada en este momento. Intenta en unos segundos.");
        }

        if (redisTemplate == null) {
            if (requireDistributed) {
                localLock.unlock();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Coordinación distribuida requerida pero Redis no está configurado.");
            }
            return new LockHandle(sanitizedKey, ownerToken, false, localLock);
        }

        try {
            while (true) {
                try {
                    Boolean acquired = redisTemplate.opsForValue()
                            .setIfAbsent(sanitizedKey, ownerToken, Duration.ofMillis(ttlMillis));
                    if (Boolean.TRUE.equals(acquired)) {
                        return new LockHandle(sanitizedKey, ownerToken, true, localLock);
                    }
                } catch (Exception redisEx) {
                    if (requireDistributed) {
                        localLock.unlock();
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Falla de conectividad con Redis durante bloqueo distribuido estricto: " + redisEx.getMessage());
                    }
                    log.warn("Redis no disponible al adquirir lock '{}': {}. Usando protección concurrente L1.",
                            sanitizedKey, redisEx.getMessage());
                    return new LockHandle(sanitizedKey, ownerToken, false, localLock);
                }

                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    localLock.unlock();
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Esta habitación está siendo reservada en este momento por otro huésped. Intenta en unos segundos.");
                }

                long sleepMs = Math.min(30L, Math.max(5L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    localLock.unlock();
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Operación interrumpida mientras se verificaba disponibilidad.");
                }
            }
        } catch (RuntimeException ex) {
            if (!(ex instanceof ResponseStatusException) && localLock.isHeldByCurrentThread()) {
                localLock.unlock();
            }
            throw ex;
        }
    }

    /**
     * Registra en Redis una retención temporal de inventario por noche (estilo Airbnb/Ticketmaster)
     * con expiración automática sincronizada con la ventana de pago de la reserva.
     */
    public void holdSuiteDates(String suiteId, int unitSlot, LocalDate checkIn, LocalDate checkOut, String bookingId, Duration holdTtl) {
        if (suiteId == null || checkIn == null || checkOut == null || bookingId == null) {
            return;
        }
        Duration effectiveTtl = (holdTtl != null && !holdTtl.isNegative() && !holdTtl.isZero())
                ? holdTtl
                : Duration.ofMinutes(15);

        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            String holdKey = buildHoldKey(suiteId, unitSlot, d);
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(holdKey, bookingId, effectiveTtl);
                } catch (Exception e) {
                    log.debug("No se pudo registrar hold en Redis para {}: {}", holdKey, e.getMessage());
                }
            }
        }
    }

    /**
     * Libera de forma segura las claves de retención temporal pertenecientes a una reserva específica.
     */
    public void releaseSuiteHold(String suiteId, Integer unitSlot, LocalDate checkIn, LocalDate checkOut, String bookingId) {
        if (suiteId == null || checkIn == null || checkOut == null || bookingId == null) {
            return;
        }
        int slot = unitSlot != null && unitSlot > 0 ? unitSlot : 1;
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            String holdKey = buildHoldKey(suiteId, slot, d);
            if (redisTemplate != null) {
                try {
                    redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(holdKey), bookingId);
                } catch (Exception e) {
                    log.debug("No se pudo liberar hold en Redis para {}: {}", holdKey, e.getMessage());
                }
            }
        }
    }

    public static String buildHoldKey(String suiteId, int unitSlot, LocalDate date) {
        return HOLD_PREFIX + suiteId + ":slot:" + unitSlot + ":" + date;
    }

    public final class LockHandle implements AutoCloseable {
        private final String lockKey;
        private final String ownerToken;
        private final boolean redisAcquired;
        private final ReentrantLock localLock;
        private boolean closed = false;

        private LockHandle(String lockKey, String ownerToken, boolean redisAcquired, ReentrantLock localLock) {
            this.lockKey = lockKey;
            this.ownerToken = ownerToken;
            this.redisAcquired = redisAcquired;
            this.localLock = localLock;
        }

        public String getLockKey() {
            return lockKey;
        }

        public String getOwnerToken() {
            return ownerToken;
        }

        public boolean isRedisAcquired() {
            return redisAcquired;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (redisAcquired && redisTemplate != null) {
                    try {
                        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), ownerToken);
                    } catch (Exception ex) {
                        log.warn("Falla al liberar lock distribuido '{}' en Redis (expirará por TTL): {}",
                                lockKey, ex.getMessage());
                    }
                }
            } finally {
                if (localLock != null && localLock.isHeldByCurrentThread()) {
                    localLock.unlock();
                }
            }
        }
    }
}
