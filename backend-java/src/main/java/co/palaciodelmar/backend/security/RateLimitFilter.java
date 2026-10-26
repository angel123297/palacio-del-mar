package co.palaciodelmar.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Filtro de seguridad para aplicar límites de tasa (Rate Limiting) en endpoints críticos de autenticación.
 * Protege contra fuerza bruta, enumeración y consumo desmedido de la cuota de correo SMTP.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            String clientIp = resolveClientIp(request);
            RateLimitService.RateLimitResult result = null;

            if (path.endsWith("/api/auth/login")) {
                // Login: 10 intentos por minuto por IP
                result = rateLimitService.checkLimit("login", clientIp, 10, 60);
            } else if (path.endsWith("/api/auth/forgot-password")) {
                // Recuperación: 3 solicitudes por cada 15 minutos por IP
                result = rateLimitService.checkLimit("forgot-password", clientIp, 3, 900);
            } else if (path.endsWith("/api/auth/resend-verification")) {
                // Reenvío de verificación: 3 solicitudes por cada 15 minutos por IP
                result = rateLimitService.checkLimit("resend-verification", clientIp, 3, 900);
            } else if (path.endsWith("/api/auth/register")) {
                // Registro: 5 cuentas por cada 10 minutos por IP
                result = rateLimitService.checkLimit("register", clientIp, 5, 600);
            } else if (path.endsWith("/api/auth/reset-password")) {
                // Restablecimiento: 5 intentos por cada 5 minutos por IP
                result = rateLimitService.checkLimit("reset-password", clientIp, 5, 300);
            }

            if (result != null && !result.isAllowed()) {
                log.warn("Solicitud bloqueada por Rate Limit en {} desde IP {}", path, clientIp);
                response.setStatus(429); // HTTP 429 Too Many Requests
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));

                Map<String, Object> errorBody = Map.of(
                        "success", false,
                        "message", "Has superado el límite de solicitudes permitidas. Por favor intenta de nuevo en unos minutos.",
                        "retryAfterSeconds", result.getRetryAfterSeconds()
                );
                response.getWriter().write(objectMapper.writeValueAsString(errorBody));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
