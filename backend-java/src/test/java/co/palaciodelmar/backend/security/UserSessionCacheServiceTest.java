package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserSessionCacheServiceTest {

    @Test
    @DisplayName("Pilar 7: Serializa y deserializa User (incluyendo @JsonProperty _id) en Redis sin fallar y revoca sesiones con precisión de milisegundos")
    @SuppressWarnings("unchecked")
    void testCachedUserRoundtripAndGlobalRevocation() {
        Map<String, String> redisMap = new ConcurrentHashMap<>();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valOps);

        doAnswer(inv -> {
            redisMap.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valOps).set(anyString(), anyString(), any(Duration.class));
        when(valOps.get(anyString())).thenAnswer(inv -> redisMap.get(inv.getArgument(0)));
        when(redisTemplate.delete(anyString())).thenAnswer(inv -> redisMap.remove(inv.getArgument(0)) != null);
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), anyString(), anyString()))
                .thenAnswer(inv -> {
                    List<String> keys = inv.getArgument(1);
                    String val = inv.getArgument(2);
                    redisMap.put(keys.get(0), val);
                    return 1L;
                });

        UserSessionCacheService cacheReader = new UserSessionCacheService(redisTemplate, new ObjectMapper());
        UserSessionCacheService secondReplicaReader = new UserSessionCacheService(redisTemplate, new ObjectMapper());

        User user = User.builder()
                .id("u-cartagena-1")
                .email("VIP@PalacioDelMar.co")
                .name("Valentina")
                .role("user")
                .status("active")
                .emailVerified(true)
                .build();

        cacheReader.putCachedUser(user);

        // Otra réplica sin L1 debe reconstruir el User desde Redis sin fallar por "_id"
        Optional<User> fromRedis = secondReplicaReader.getCachedUser("vip@palaciodelmar.co");
        assertTrue(fromRedis.isPresent());
        assertEquals("u-cartagena-1", fromRedis.get().getId());
        assertEquals("Valentina", fromRedis.get().getName());

        // Revocación global en todos los dispositivos con precisión de milisegundos
        Instant revokedAt = Instant.ofEpochMilli(1_793_000_000_500L);
        cacheReader.recordGlobalRevocation("vip@palaciodelmar.co", revokedAt);

        assertFalse(redisMap.containsKey("palacio:auth:user:vip@palaciodelmar.co"), "El snapshot de usuario debe invalidarse al revocar sesiones");
        assertTrue(secondReplicaReader.isTokenRevokedForUser("vip@palaciodelmar.co", 1_793_000_000_100L), "Token emitido 400ms antes en el mismo segundo debe quedar revocado");
        assertTrue(secondReplicaReader.isTokenRevokedForUser("vip@palaciodelmar.co", 1_793_000_000_500L), "Token emitido en el milisegundo exacto de revocación debe quedar revocado");
        assertFalse(secondReplicaReader.isTokenRevokedForUser("vip@palaciodelmar.co", 1_793_000_000_505L), "Token nuevo emitido 5ms después del logout-all debe ser válido");
    }

    @Test
    @DisplayName("Pilar 7: Fallback durable contra MongoDB ante falla de Redis evita que un snapshot L1 desactualizado eluda la revocación")
    void testFallbackToMongoWhenRedisFails() {
        co.palaciodelmar.backend.repository.UserRepository userRepo = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        StringRedisTemplate failingRedis = mock(StringRedisTemplate.class);
        when(failingRedis.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis down"));

        Instant revokedAt = Instant.now().minusSeconds(10);
        User dbUser = User.builder()
                .id("u-fallback-1")
                .email("fallback@palaciodelmar.co")
                .name("Roberto")
                .sessionsRevokedAt(revokedAt)
                .build();
        when(userRepo.findByEmail("fallback@palaciodelmar.co")).thenReturn(Optional.of(dbUser));

        UserSessionCacheService cacheService = new UserSessionCacheService(failingRedis, new ObjectMapper(), userRepo);

        // Al consultar la marca de revocación con Redis caído, debe recurrir a MongoDB
        Long revokedEpoch = cacheService.getRevokedBeforeEpochMs("fallback@palaciodelmar.co");
        assertNotNull(revokedEpoch);
        assertEquals(revokedAt.toEpochMilli(), revokedEpoch);

        // Tokens emitidos antes de revokedAt deben marcarse como revocados
        assertTrue(cacheService.isTokenRevokedForUser("fallback@palaciodelmar.co", revokedAt.toEpochMilli() - 1000));
        assertFalse(cacheService.isTokenRevokedForUser("fallback@palaciodelmar.co", revokedAt.toEpochMilli() + 1000));
    }

    @Test
    @DisplayName("Pilar 7 (Alert 1): Si Redis está activo pero la clave no existe y Mongo no tiene revocación, retorna null y NUNCA escribe '0' para no suprimir revocaciones futuras")
    void testColdCacheNeverWritesZeroMarkerToRedis() {
        co.palaciodelmar.backend.repository.UserRepository userRepo = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valOps);

        User normalUser = User.builder()
                .id("u-normal")
                .email("clean@palaciodelmar.co")
                .sessionsRevokedAt(null)
                .build();
        when(userRepo.findByEmail("clean@palaciodelmar.co")).thenReturn(Optional.of(normalUser));
        when(valOps.get("palacio:user:revoked_before:clean@palaciodelmar.co")).thenReturn(null);

        UserSessionCacheService cacheService = new UserSessionCacheService(redisTemplate, new ObjectMapper(), userRepo);
        Long cutoff = cacheService.getRevokedBeforeEpochMs("clean@palaciodelmar.co");

        assertNull(cutoff);
        verify(valOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Pilar 7 (Alert 2): Si fallan Redis y Mongo simultáneamente, la validación falla cerrada (fail-closed)")
    void testBothRedisAndMongoFailureFailsClosed() {
        co.palaciodelmar.backend.repository.UserRepository userRepo = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        StringRedisTemplate failingRedis = mock(StringRedisTemplate.class);
        when(failingRedis.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis down"));
        when(userRepo.findByEmail(anyString())).thenThrow(new RuntimeException("Mongo cluster timeout"));

        UserSessionCacheService cacheService = new UserSessionCacheService(failingRedis, new ObjectMapper(), userRepo);

        // Fail-closed: se considera el token revocado para salvaguardar el acceso
        assertTrue(cacheService.isTokenRevokedForUser("victim@palaciodelmar.co", System.currentTimeMillis()));
    }

    @Test
    @DisplayName("Bajo #4: Redis nunca almacena password ni hashes de verificación/reset, sin romper logins consecutivos")
    @SuppressWarnings("unchecked")
    void testSanitizesSensitiveFieldsInRedisWithoutBreakingConsecutiveLogins() {
        Map<String, String> redisMap = new ConcurrentHashMap<>();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valOps);
        doAnswer(inv -> {
            redisMap.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valOps).set(anyString(), anyString(), any(Duration.class));
        when(valOps.get(anyString())).thenAnswer(inv -> redisMap.get(inv.getArgument(0)));

        co.palaciodelmar.backend.repository.UserRepository userRepo = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        User dbUser = User.builder()
                .id("u-login-1")
                .email("huesped@palaciodelmar.co")
                .name("Camila")
                .password("$2a$10$secretBcryptHashValue1234567890")
                .verificationTokenHash("verif_hash_secret")
                .resetPasswordTokenHash("reset_hash_secret")
                .role("user")
                .status("active")
                .emailVerified(true)
                .build();
        when(userRepo.findByEmail("huesped@palaciodelmar.co")).thenReturn(Optional.of(dbUser));

        UserSessionCacheService cacheService = new UserSessionCacheService(redisTemplate, new ObjectMapper(), userRepo);
        UserDetailsServiceImpl userDetailsService = new UserDetailsServiceImpl(userRepo, cacheService);

        // Login #1 (DaoAuthenticationProvider llama loadUserByUsername y requiere el hash BCrypt)
        org.springframework.security.core.userdetails.UserDetails login1 =
                userDetailsService.loadUserByUsername("huesped@palaciodelmar.co");
        assertEquals("$2a$10$secretBcryptHashValue1234567890", login1.getPassword());

        // Login #2 consecutivo (debe seguir devolviendo el hash BCrypt para DaoAuthenticationProvider)
        org.springframework.security.core.userdetails.UserDetails login2 =
                userDetailsService.loadUserByUsername("huesped@palaciodelmar.co");
        assertEquals("$2a$10$secretBcryptHashValue1234567890", login2.getPassword());

        // Mientras tanto, el JSON en Redis y la lectura de sesión JWT están completamente limpios de secretos
        String storedJson = redisMap.get("palacio:auth:user:huesped@palaciodelmar.co");
        assertNotNull(storedJson);
        assertFalse(storedJson.contains("$2a$10$secretBcryptHashValue1234567890"), "Redis no debe contener el hash BCrypt");
        assertFalse(storedJson.contains("verif_hash_secret"), "Redis no debe contener verificationTokenHash");
        assertFalse(storedJson.contains("reset_hash_secret"), "Redis no debe contener resetPasswordTokenHash");

        org.springframework.security.core.userdetails.UserDetails sessionPrincipal =
                userDetailsService.loadUserForSession("huesped@palaciodelmar.co");
        assertEquals("huesped@palaciodelmar.co", sessionPrincipal.getUsername());
        assertNull(sessionPrincipal.getPassword(), "El principal de sesión JWT cacheado no debe exponer password");
    }
}
