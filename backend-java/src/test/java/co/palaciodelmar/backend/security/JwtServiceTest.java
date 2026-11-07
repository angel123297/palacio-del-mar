package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "palaciodelmarsecretkeyforjwttokengeneration2026_verylongkey");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 86400000L);
    }

    @Test
    void testGenerateAndValidateToken() {
        User user = User.builder()
                .id("123")
                .email("test@palaciodelmar.co")
                .role("user")
                .status("active")
                .build();

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(userPrincipal);

        assertNotNull(token);
        assertEquals("test@palaciodelmar.co", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, userPrincipal));
    }

    @Test
    void testTokenInvalidatedWhenPasswordChangedAfterTokenIssuance() {
        User user = User.builder()
                .id("123")
                .email("test@palaciodelmar.co")
                .role("user")
                .status("active")
                .build();

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(userPrincipal);

        // Simulamos que el usuario cambió su contraseña 2 segundos después
        user.setPasswordChangedAt(Instant.now().plusSeconds(2));

        assertFalse(jwtService.isTokenValid(token, userPrincipal),
                "El token emitido antes del cambio de contraseña debe ser rechazado");
    }

    @Test
    void testTokenValidWhenIssuedInSameSecondAsPasswordChange() {
        User user = User.builder()
                .id("123")
                .email("test@palaciodelmar.co")
                .role("user")
                .status("active")
                .build();

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(userPrincipal);

        // Extraemos el iat exacto del token y fijamos passwordChangedAt en el mismo segundo con milisegundos posteriores
        Date issuedAt = jwtService.extractClaim(token, Claims::getIssuedAt);
        Instant sameSecondLater = issuedAt.toInstant().plusMillis(500);
        user.setPasswordChangedAt(sameSecondLater);

        assertTrue(jwtService.isTokenValid(token, userPrincipal),
                "El token recién emitido tras el cambio de contraseña debe ser válido en el mismo segundo exacto");
    }

    @Test
    void testSuspendedOrInactiveUserTokenRejectedImmediately() {
        User user = User.builder()
                .id("123")
                .email("suspended@palaciodelmar.co")
                .role("user")
                .status("active")
                .build();

        UserPrincipal activePrincipal = new UserPrincipal(user);
        String token = jwtService.generateToken(activePrincipal);
        assertTrue(jwtService.isTokenValid(token, activePrincipal));

        user.setStatus("suspended");
        UserPrincipal suspendedPrincipal = new UserPrincipal(user);
        assertFalse(jwtService.isTokenValid(token, suspendedPrincipal),
                "El token de un usuario suspendido debe ser rechazado de inmediato");

        user.setStatus("inactive");
        UserPrincipal inactivePrincipal = new UserPrincipal(user);
        assertFalse(jwtService.isTokenValid(token, inactivePrincipal),
                "El token de un usuario inactivo debe ser rechazado de inmediato");
    }

    @Test
    void testSessionsRevokedAtInvalidatesPriorTokensAndAllowsSubsequentLogin() throws InterruptedException {
        UserSessionCacheService sessionCacheService = new UserSessionCacheService(null, null);
        jwtService.setUserSessionCacheService(sessionCacheService);

        User user = User.builder()
                .id("u-revoked")
                .email("revoked@palaciodelmar.co")
                .role("user")
                .status("active")
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        String tokenBeforeLogoutAll = jwtService.generateToken(principal);

        Instant revocationCutoff = Instant.now().plusMillis(5);
        sessionCacheService.recordGlobalRevocation("revoked@palaciodelmar.co", revocationCutoff);

        assertFalse(jwtService.isTokenValid(tokenBeforeLogoutAll, principal),
                "Un token emitido antes de logout-all debe ser rechazado instantáneamente");

        // Un nuevo login posterior al cutoff de revocación debe ser válido de inmediato (precisión iat_ms)
        Thread.sleep(15);
        String freshTokenAfterRelogin = jwtService.generateToken(principal);
        assertTrue(jwtService.isTokenValid(freshTokenAfterRelogin, principal),
                "Un nuevo token emitido tras el logout-all debe ser válido en el mismo segundo");
    }

    @Test
    void testValidateSecretRejectsKnownFallbackAndWeakKeys() {
        ReflectionTestUtils.setField(jwtService, "secretKey", "super_secret_jwt_key_palacio_del_mar_2026_dev_min32chars");
        assertThrows(IllegalStateException.class, () -> jwtService.validateSecret(),
                "Debe rechazar el secreto JWT público que antes estaba en docker-compose");

        ReflectionTestUtils.setField(jwtService, "secretKey", "short-jwt-key");
        assertThrows(IllegalStateException.class, () -> jwtService.validateSecret());

        ReflectionTestUtils.setField(jwtService, "secretKey", "palaciodelmarsecretkeyforjwttokengeneration2026_verylongkey");
        assertDoesNotThrow(() -> jwtService.validateSecret());
    }
}
