package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
