package co.palaciodelmar.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Pruebas Unitarias - Servicio Híbrido de Rate Limiting (Redis / Memoria)")
class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    @DisplayName("M-5: Ejecuta script Lua atómico en Redis (permitido retorna 0, bloqueado retorna Retry-After)")
    @SuppressWarnings("unchecked")
    void testRedisAllowsUnderLimitAndSetsTtl() {
        when(redisTemplate.execute(
                any(org.springframework.data.redis.core.script.RedisScript.class),
                eq(java.util.List.of("palacio:ratelimit:login:192.168.1.1")),
                eq("5"),
                eq("60")
        )).thenReturn(0L);

        RateLimitService.RateLimitResult result = rateLimitService.checkLimit("login", "192.168.1.1", 5, 60);

        assertTrue(result.isAllowed());
        assertEquals(0, result.getRetryAfterSeconds());
        verify(redisTemplate).execute(
                any(org.springframework.data.redis.core.script.RedisScript.class),
                eq(java.util.List.of("palacio:ratelimit:login:192.168.1.1")),
                eq("5"),
                eq("60")
        );
    }

    @Test
    @DisplayName("M-5: Debe bloquear solicitudes que superen el límite en Redis vía Lua y devolver Retry-After")
    @SuppressWarnings("unchecked")
    void testRedisBlocksWhenExceeded() {
        when(redisTemplate.execute(
                any(org.springframework.data.redis.core.script.RedisScript.class),
                eq(java.util.List.of("palacio:ratelimit:login:192.168.1.1")),
                eq("5"),
                eq("60")
        )).thenReturn(45L);

        RateLimitService.RateLimitResult result = rateLimitService.checkLimit("login", "192.168.1.1", 5, 60);

        assertFalse(result.isAllowed());
        assertEquals(45, result.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Debe usar fallback en memoria si Redis lanza excepción")
    @SuppressWarnings("unchecked")
    void testInMemoryFallbackWhenRedisThrows() {
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(), any()))
                .thenThrow(new RuntimeException("Redis connection lost"));

        // Primeras 3 solicitudes permitidas (límite = 3)
        assertTrue(rateLimitService.checkLimit("forgot-password", "10.0.0.1", 3, 60).isAllowed());
        assertTrue(rateLimitService.checkLimit("forgot-password", "10.0.0.1", 3, 60).isAllowed());
        assertTrue(rateLimitService.checkLimit("forgot-password", "10.0.0.1", 3, 60).isAllowed());

        // 4ta solicitud bloqueada por fallback en memoria
        RateLimitService.RateLimitResult blocked = rateLimitService.checkLimit("forgot-password", "10.0.0.1", 3, 60);
        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Debe funcionar exclusivamente en memoria si redisTemplate es nulo")
    void testPureInMemoryWhenRedisIsNull() {
        RateLimitService memoryOnlyService = new RateLimitService(null);

        // Límite de 2 solicitudes por ventana
        assertTrue(memoryOnlyService.checkLimit("resend-verification", "127.0.0.1", 2, 60).isAllowed());
        assertTrue(memoryOnlyService.checkLimit("resend-verification", "127.0.0.1", 2, 60).isAllowed());

        // 3ra solicitud bloqueada
        RateLimitService.RateLimitResult blocked = memoryOnlyService.checkLimit("resend-verification", "127.0.0.1", 2, 60);
        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRetryAfterSeconds() > 0);
    }

    @Test
    @DisplayName("Reset debe limpiar contador tanto en memoria como en Redis")
    void testResetClearsLimit() {
        RateLimitService memoryOnlyService = new RateLimitService(null);

        // Agotar límite
        memoryOnlyService.checkLimit("login", "client-a", 1, 60);
        assertFalse(memoryOnlyService.checkLimit("login", "client-a", 1, 60).isAllowed());

        // Resetear
        memoryOnlyService.reset("login", "client-a");

        // Debe permitir de nuevo
        assertTrue(memoryOnlyService.checkLimit("login", "client-a", 1, 60).isAllowed());
    }

    @Test
    @DisplayName("M-5: Bucket de ventana larga (900s) conserva su propio expiresAtMillis y aplica tope de cardinalidad")
    void testPerBucketExpiresAtMillisAndCardinalityCap() {
        RateLimitService memoryOnly = new RateLimitService(null);
        assertTrue(memoryOnly.checkLimit("forgot-password", "victim-ip", 1, 900).isAllowed());
        assertFalse(memoryOnly.checkLimit("forgot-password", "victim-ip", 1, 900).isAllowed());

        // Insertar miles de buckets cortos (1s) no debe borrar prematuramente el bucket activo de 900s
        for (int i = 0; i < 5100; i++) {
            memoryOnly.checkLimit("short-cat", "ip-" + i, 5, 1);
        }
        assertFalse(memoryOnly.checkLimit("forgot-password", "victim-ip", 1, 900).isAllowed(),
                "El bucket de 900s no debe expirar por barridos disparados desde categorías de 1s");

        for (int i = 5100; i < 10_500; i++) {
            memoryOnly.checkLimit("short-cat", "ip-" + i, 5, 60);
        }
        assertTrue(memoryOnly.getInMemoryBucketCount() <= 10_000,
                "inMemoryBuckets debe mantenerse acotado <= 10000");
    }
}
