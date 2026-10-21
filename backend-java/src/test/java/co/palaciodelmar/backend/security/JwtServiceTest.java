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
}
