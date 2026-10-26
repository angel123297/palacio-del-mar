package co.palaciodelmar.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Pruebas Unitarias - Filtro de Seguridad RateLimitFilter")
class RateLimitFilterTest {

    private RateLimitService rateLimitService;
    private RateLimitFilter rateLimitFilter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        rateLimitFilter = new RateLimitFilter(rateLimitService);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Debe dejar pasar peticiones GET sin aplicar rate limit")
    void testGetRequestsBypassFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/suites");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimitService);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Debe permitir POST /api/auth/login si el servicio aprueba la solicitud")
    void testPostLoginAllowed() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.50");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.checkLimit(eq("login"), eq("192.168.1.50"), anyInt(), anyInt()))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Debe retornar HTTP 429 con cabecera Retry-After cuando se excede el rate limit")
    void testPostLoginBlockedWhenExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.addHeader("X-Real-IP", "203.0.113.195");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.checkLimit(eq("login"), eq("203.0.113.195"), eq(10), eq(60)))
                .thenReturn(new RateLimitService.RateLimitResult(false, 45));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(429, response.getStatus());
        assertEquals("45", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Has superado el límite"));
    }

    @Test
    @DisplayName("Debe aplicar rate limiting en /api/auth/forgot-password para proteger cuota de correo")
    void testForgotPasswordRateLimiting() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/forgot-password");
        request.setRemoteAddr("198.51.100.22");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.checkLimit(eq("forgot-password"), eq("198.51.100.22"), eq(3), eq(900)))
                .thenReturn(new RateLimitService.RateLimitResult(false, 600));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertEquals(429, response.getStatus());
        assertEquals("600", response.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("Debe ignorar X-Forwarded-For falsificado si la conexión socket directa no proviene de un proxy confiable")
    void testDirectUntrustedSocketConnectionIgnoresSpoofedXForwardedFor() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("198.51.100.77"); // IP pública directa
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.checkLimit(eq("login"), eq("198.51.100.77"), eq(10), eq(60)))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // Debe haberse evaluado con la IP del socket, nunca con el header spoofed
        verify(rateLimitService).checkLimit(eq("login"), eq("198.51.100.77"), eq(10), eq(60));
        verify(rateLimitService, never()).checkLimit(eq("login"), eq("1.2.3.4"), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Debe priorizar X-Real-IP saneado por proxy confiable o tomar salto derecho de X-Forwarded-For")
    void testTrustedProxyUsesRealIpOrRightmostForwardedFor() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("172.18.0.3"); // Proxy Nginx en red Docker privada (confiable)
        request.addHeader("X-Real-IP", "203.0.113.195");
        request.addHeader("X-Forwarded-For", "203.0.113.195");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.checkLimit(eq("login"), eq("203.0.113.195"), eq(10), eq(60)))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitService).checkLimit(eq("login"), eq("203.0.113.195"), eq(10), eq(60));
    }

    @Test
    @DisplayName("Debe manejar cabeceras IP malformadas sin fallar y respaldarse en remoteAddr")
    void testMalformedIpHeaderFallsBackToRemoteAddr() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "attacker-domain.com; inject, not-an-ip");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.checkLimit(eq("login"), eq("127.0.0.1"), eq(10), eq(60)))
                .thenReturn(new RateLimitService.RateLimitResult(true, 0));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(rateLimitService).checkLimit(eq("login"), eq("127.0.0.1"), eq(10), eq(60));
    }
}
