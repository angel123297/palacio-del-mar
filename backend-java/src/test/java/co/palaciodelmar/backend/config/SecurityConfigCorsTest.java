package co.palaciodelmar.backend.config;

import co.palaciodelmar.backend.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private CorsConfiguration buildCorsConfig(String appBaseUrl) {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(UserDetailsService.class)
        );
        if (appBaseUrl != null) {
            ReflectionTestUtils.setField(securityConfig, "appBaseUrl", appBaseUrl);
        }
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/verify-email");
        return source.getCorsConfiguration(request);
    }

    @Test
    @DisplayName("CORS permite orígenes locales de desarrollo (localhost y 127.0.0.1)")
    void testAllowsLocalhostOrigins() {
        CorsConfiguration config = buildCorsConfig("http://localhost:4200");
        assertNotNull(config);
        assertEquals("http://localhost:4200", config.checkOrigin("http://localhost:4200"));
        assertEquals("http://127.0.0.1:4200", config.checkOrigin("http://127.0.0.1:4200"));
    }

    @Test
    @DisplayName("CORS permite cualquier subdominio HTTPS de Cloudflare Quick Tunnel (*.trycloudflare.com)")
    void testAllowsCloudflareQuickTunnelOrigins() {
        CorsConfiguration config = buildCorsConfig("http://localhost:4200");
        assertNotNull(config);
        assertEquals(
                "https://palacio-cartagena-demo.trycloudflare.com",
                config.checkOrigin("https://palacio-cartagena-demo.trycloudflare.com")
        );
    }

    @Test
    @DisplayName("CORS permite APP_BASE_URL personalizado incluso si tiene barra final (/)")
    void testAllowsConfiguredAppBaseUrlNormalized() {
        CorsConfiguration config = buildCorsConfig("https://reservas.palacio-ejemplo.co/");
        assertNotNull(config);
        assertEquals(
                "https://reservas.palacio-ejemplo.co",
                config.checkOrigin("https://reservas.palacio-ejemplo.co")
        );
    }

    @Test
    @DisplayName("CORS rechaza orígenes externos no autorizados")
    void testRejectsUntrustedOrigins() {
        CorsConfiguration config = buildCorsConfig("http://localhost:4200");
        assertNotNull(config);
        assertNull(config.checkOrigin("https://sitio-malicioso.ejemplo.com"));
        assertNull(config.checkOrigin("http://insecure-tunnel.trycloudflare.com"));
    }
}
