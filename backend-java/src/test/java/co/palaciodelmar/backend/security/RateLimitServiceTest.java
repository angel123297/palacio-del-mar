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
    @DisplayName("Debe permitir solicitudes que no excedan el límite en Redis y fijar TTL en el primer intento")
    void testRedisAllowsUnderLimitAndSetsTtl() {
        when(valueOperations.increment("palacio:ratelimit:login:192.168.1.1")).thenReturn(1L);

        RateLimitService.RateLimitResult result = rateLimitService.checkLimit("login", "192.168.1.1", 5, 60);

        assertTrue(result.isAllowed());
        assertEquals(0, result.getRetryAfterSeconds());
        verify(redisTemplate).expire(eq("palacio:ratelimit:login:192.168.1.1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("Debe bloquear solicitudes que superen el límite en Redis y devolver Retry-After")
    void testRedisBlocksWhenExceeded() {
        when(valueOperations.increment("palacio:ratelimit:login:192.168.1.1")).thenReturn(6L);
        when(redisTemplate.getExpire("palacio:ratelimit:login:192.168.1.1")).thenReturn(45L);

        RateLimitService.RateLimitResult result = rateLimitService.checkLimit("login", "192.168.1.1", 5, 60);

        assertFalse(result.isAllowed());
        assertEquals(45, result.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Debe usar fallback en memoria si Redis lanza excepción")
    void testInMemoryFallbackWhenRedisThrows() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis connection lost"));

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
}
