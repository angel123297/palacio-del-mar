package co.palaciodelmar.backend.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private final Map<String, InMemoryBucket> inMemoryBuckets = new ConcurrentHashMap<>();

    private static class InMemoryBucket {
        long windowStartMillis;
        final AtomicInteger count;

        InMemoryBucket(long windowStartMillis, int initialCount) {
            this.windowStartMillis = windowStartMillis;
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

        // 1. Intento primario con Redis
        if (redisTemplate != null) {
            try {
                String redisKey = REDIS_PREFIX + fullKey;
                Long current = redisTemplate.opsForValue().increment(redisKey);
                if (current != null && current == 1L) {
                    redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
                }

                if (current != null && current > maxRequests) {
                    Long ttl = redisTemplate.getExpire(redisKey);
                    long retryAfter = (ttl != null && ttl > 0) ? ttl : windowSeconds;
                    log.warn("Rate limit excedido en Redis para {} ({} solicitudes de {} permitidas)", fullKey, current, maxRequests);
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
        long windowMillis = windowSeconds * 1000L;

        cleanupExpiredInMemoryBuckets(now, windowMillis);

        InMemoryBucket bucket = inMemoryBuckets.compute(fullKey, (k, existing) -> {
            if (existing == null || (now - existing.windowStartMillis) >= windowMillis) {
                return new InMemoryBucket(now, 1);
            } else {
                existing.count.incrementAndGet();
                return existing;
            }
        });

        int current = bucket.count.get();
        if (current > maxRequests) {
            long elapsed = now - bucket.windowStartMillis;
            long remainingMillis = Math.max(0, windowMillis - elapsed);
            long retryAfter = (long) Math.ceil(remainingMillis / 1000.0);
            if (retryAfter <= 0) retryAfter = 1;
            log.warn("Rate limit excedido en memoria para {} ({} solicitudes de {} permitidas)", fullKey, current, maxRequests);
            return new RateLimitResult(false, retryAfter);
        }

        return new RateLimitResult(true, 0);
    }

    private void cleanupExpiredInMemoryBuckets(long now, long windowMillis) {
        if (inMemoryBuckets.size() > 5000) {
            inMemoryBuckets.entrySet().removeIf(entry -> (now - entry.getValue().windowStartMillis) >= windowMillis);
        }
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
