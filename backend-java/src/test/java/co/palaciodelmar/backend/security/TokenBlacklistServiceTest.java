package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.RevokedToken;
import co.palaciodelmar.backend.repository.RevokedTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtService jwtService;

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService(revokedTokenRepository, jwtService);
    }

    @Test
    void testBlacklistWithInMemoryFallbackWhenRedisIsNull() {
        ReflectionTestUtils.setField(tokenBlacklistService, "redisTemplate", null);
        when(jwtService.extractExpiration(anyString())).thenReturn(new Date(System.currentTimeMillis() + 60000));

        String token = "sample.valid.jwt.token";

        assertFalse(tokenBlacklistService.isBlacklisted(token));
        tokenBlacklistService.blacklistToken(token);
        assertTrue(tokenBlacklistService.isBlacklisted(token));
        assertFalse(tokenBlacklistService.isBlacklisted("another.token"));

        verify(revokedTokenRepository).save(any(RevokedToken.class));
    }

    @Test
    void testBlacklistWithRedisTemplateSuccess() {
        ReflectionTestUtils.setField(tokenBlacklistService, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.extractExpiration(anyString())).thenReturn(new Date(System.currentTimeMillis() + 120000));

        String token = "sample.redis.jwt.token";

        tokenBlacklistService.blacklistToken(token);
        verify(valueOperations).set(startsWith("palacio:jwt:blacklist:"), eq("revoked"), any(Duration.class));
        verify(revokedTokenRepository).save(any(RevokedToken.class));

        assertTrue(tokenBlacklistService.isBlacklisted(token));
    }

    @Test
    void testIsBlacklistedConsultsRedisWhenL1Misses() {
        ReflectionTestUtils.setField(tokenBlacklistService, "redisTemplate", redisTemplate);
        when(redisTemplate.hasKey(startsWith("palacio:jwt:blacklist:"))).thenReturn(true);

        String token = "remote.redis.revoked.token";

        // No está en L1, debe consultar Redis y devolver true
        assertTrue(tokenBlacklistService.isBlacklisted(token));
        verify(redisTemplate).hasKey(startsWith("palacio:jwt:blacklist:"));
    }

    @Test
    void testMongoFallbackWhenRedisThrowsException() {
        ReflectionTestUtils.setField(tokenBlacklistService, "redisTemplate", redisTemplate);
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis connection refused"));
        when(revokedTokenRepository.existsById(anyString())).thenReturn(true);

        String token = "sample.failing.redis.token";

        // Demuestra que no falla de forma abierta (anti fail-open)
        assertTrue(tokenBlacklistService.isBlacklisted(token));
        verify(revokedTokenRepository).existsById(anyString());
    }

    @Test
    void testBlacklistIgnoresNullOrBlankToken() {
        tokenBlacklistService.blacklistToken(null);
        tokenBlacklistService.blacklistToken("   ");

        assertFalse(tokenBlacklistService.isBlacklisted(null));
        assertFalse(tokenBlacklistService.isBlacklisted(""));
        assertFalse(tokenBlacklistService.isBlacklisted("   "));
        verifyNoInteractions(jwtService);
        verifyNoInteractions(revokedTokenRepository);
    }

    @Test
    void testBlacklistHandlesAlreadyExpiredToken() {
        ReflectionTestUtils.setField(tokenBlacklistService, "redisTemplate", null);
        // Expirado hace 10 segundos
        when(jwtService.extractExpiration(anyString())).thenReturn(new Date(System.currentTimeMillis() - 10000));

        String expiredToken = "expired.jwt.token";
        tokenBlacklistService.blacklistToken(expiredToken);

        // No se almacena en BD porque ya expiró
        verify(revokedTokenRepository, never()).save(any(RevokedToken.class));
        assertFalse(tokenBlacklistService.isBlacklisted(expiredToken));
    }

    @Test
    void testFailClosedWhenMongoThrowsExceptionOnRead() {
        ReflectionTestUtils.setField(tokenBlacklistService, "redisTemplate", null);
        when(revokedTokenRepository.existsById(anyString())).thenThrow(new RuntimeException("Mongo connection failure"));

        String token = "suspicious.token";

        // Fail-closed: ante falla de base de datos, el token se trata como revocado
        assertTrue(tokenBlacklistService.isBlacklisted(token));
    }

    @Test
    void testBlacklistThrowsExceptionWhenMongoWriteFails() {
        ReflectionTestUtils.setField(tokenBlacklistService, "redisTemplate", null);
        when(jwtService.extractExpiration(anyString())).thenReturn(new Date(System.currentTimeMillis() + 60000));
        doThrow(new RuntimeException("Mongo write timeout")).when(revokedTokenRepository).save(any());

        assertThrows(IllegalStateException.class, () -> tokenBlacklistService.blacklistToken("failing.token"));
    }
}
