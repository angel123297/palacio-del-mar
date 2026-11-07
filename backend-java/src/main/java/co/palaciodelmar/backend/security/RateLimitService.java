package co.palaciodelmar.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio de control de tasa (Rate Limiting) para protección contra ataques de fuerza bruta
 * y agotamiento de recursos (como cuotas de correo SMTP).
 * Opera con arquitectura híbrida:
 * 1. Redis (L2 distribuido con expiración atómica) cuando está disponible.
 * 2. Almacén local en memoria (L1 / Fallback) concurrente ante caídas de Redis.
 */
@Slf4j
@Service
public class RateLimitService {

    private static final String REDIS_PREFIX = "palacio:ratelimit:";
    public static final int MAX_IN_MEMORY_BUCKETS = 10_000;

    private static final DefaultRedisScript<Long> RATE_LIMIT_LUA_SCRIPT;
    static {
        RATE_LIMIT_LUA_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_LUA_SCRIPT.setScriptText(
                "local current = redis.call('INCR', KEYS[1]) " +
                "local ttl = redis.call('TTL', KEYS[1]) " +
                "if current == 1 or ttl < 0 then " +
                "  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) " +
                "  ttl = tonumber(ARGV[2]) " +
                "end " +
                "if current > tonumber(ARGV[1]) then " +
                "  if ttl > 0 then return ttl else return tonumber(ARGV[2]) end " +
                "end " +
                "return 0"
        );
        RATE_LIMIT_LUA_SCRIPT.setResultType(Long.class);
    }

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final Map<String, InMemoryBucket> inMemoryBuckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong(0L);

    private static class InMemoryBucket {
        final long windowStartMillis;
        final long expiresAtMillis;
        final AtomicInteger count;

        InMemoryBucket(long windowStartMillis, long expiresAtMillis, int initialCount) {
            this.windowStartMillis = windowStartMillis;
            this.expiresAtMillis = expiresAtMillis;
            this.count = new AtomicInteger(initialCount);
        }
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean allowed, long retryAfterSeconds) {
            this.allowed = allowed;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public RateLimitService() {}

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    int getInMemoryBucketCount() {
        return inMemoryBuckets.size();
    }

    /**
     * Evalúa si una solicitud identificada por categoría y clave (e.g. IP) es permitida bajo
     * el límite de tasa especificado.
     *
     * @param category nombre de la categoría (e.g. "login", "forgot-password")
     * @param key clave identificadora del cliente (e.g. IP o email)
     * @param maxRequests número máximo de solicitudes permitidas en la ventana
     * @param windowSeconds duración de la ventana en segundos
     * @return {@link RateLimitResult} indicando si es permitida y cuántos segundos esperar
     */
    public RateLimitResult checkLimit(String category, String key, int maxRequests, int windowSeconds) {
        if (key == null || key.isBlank()) {
            return new RateLimitResult(true, 0);
        }

        String fullKey = category + ":" + key.trim();

        // 1. Intento primario atómico con Redis Lua (INCR + TTL + EXPIRE en 1 roundtrip)
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_PREFIX + fullKey;
                Long retryAfter = redisTemplate.execute(
                        RATE_LIMIT_LUA_SCRIPT,
                        List.of(redisKey),
                        String.valueOf(maxRequests),
                        String.valueOf(windowSeconds)
                );

                if (retryAfter != null && retryAfter > 0L) {
                    log.warn("Rate limit excedido en Redis para {} (máx={} en {}s, retryAfter={}s)",
                            fullKey, maxRequests, windowSeconds, retryAfter);
                    return new RateLimitResult(false, retryAfter);
                }

                return new RateLimitResult(true, 0);
            } catch (Exception ex) {
                log.warn("Error accediendo a Redis para rate limit ({}): {}. Empleando fallback en memoria.", fullKey, ex.getMessage());
            }
        }

        // 2. Fallback resiliente en memoria
        return checkInMemoryLimit(fullKey, maxRequests, windowSeconds);
    }

    private RateLimitResult checkInMemoryLimit(String fullKey, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1000L, windowSeconds * 1000L);

        InMemoryBucket bucket = inMemoryBuckets.compute(fullKey, (k, existing) -> {
            if (existing == null || now >= existing.expiresAtMillis) {
                return new InMemoryBucket(now, now + windowMillis, 1);
            } else {
                existing.count.incrementAndGet();
                return existing;
            }
        });

        if (inMemoryBuckets.size() > MAX_IN_MEMORY_BUCKETS) {
            cleanupAndCapInMemoryBuckets(now, fullKey);
        } else {
            long prevSweep = lastCleanupMillis.get();
            if (inMemoryBuckets.size() > 2_000 && (now - prevSweep) >= 10_000L
                    && lastCleanupMillis.compareAndSet(prevSweep, now)) {
                inMemoryBuckets.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtMillis);
            }
        }

        int current = bucket.count.get();
        if (current > maxRequests) {
            long remainingMillis = Math.max(0L, bucket.expiresAtMillis - now);
            long retryAfter = (long) Math.ceil(remainingMillis / 1000.0);
            if (retryAfter <= 0) retryAfter = 1;
            log.warn("Rate limit excedido en memoria para {} ({} solicitudes de {} permitidas)", fullKey, current, maxRequests);
            return new RateLimitResult(false, retryAfter);
        }

        return new RateLimitResult(true, 0);
    }

    private synchronized void cleanupAndCapInMemoryBuckets(long now, String protectedKey) {
        inMemoryBuckets.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtMillis);
        while (inMemoryBuckets.size() > MAX_IN_MEMORY_BUCKETS) {
            String oldestKey = null;
            long earliestExpiry = Long.MAX_VALUE;
            for (var entry : inMemoryBuckets.entrySet()) {
                if (entry.getKey().equals(protectedKey)) {
                    continue;
                }
                if (entry.getValue().expiresAtMillis < earliestExpiry) {
                    earliestExpiry = entry.getValue().expiresAtMillis;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                break;
            }
            inMemoryBuckets.remove(oldestKey);
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void scheduledSweepInMemoryBuckets() {
        long now = System.currentTimeMillis();
        inMemoryBuckets.entrySet().removeIf(entry -> now >= entry.getValue().expiresAtMillis);
    }

    public void reset(String category, String key) {
        String fullKey = category + ":" + (key != null ? key.trim() : "");
        inMemoryBuckets.remove(fullKey);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_PREFIX + fullKey);
            } catch (Exception ignored) {}
        }
    }
}
