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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.regex.Pattern;

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

    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
    private static final Pattern IPV6_PATTERN =
            Pattern.compile("^[0-9a-fA-F:]+$");

    public static boolean isValidIpLiteral(String ip) {
        if (ip == null || ip.isBlank()) return false;
        String trimmed = ip.trim();
        return IPV4_PATTERN.matcher(trimmed).matches() || (trimmed.contains(":") && IPV6_PATTERN.matcher(trimmed).matches());
    }

    public static boolean isTrustedProxy(String ip) {
        if (!isValidIpLiteral(ip)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip.trim());
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return "unknown";
        }
        remoteAddr = remoteAddr.trim();

        // 1. Si la conexión socket directa NO proviene de un proxy confiable (loopback o red privada interna),
        // ignoramos cualquier encabezado X-Forwarded-For / X-Real-IP para prevenir IP spoofing directo.
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 2. Si proviene de un proxy de confianza (Nginx, Docker bridge, localhost),
        // inspeccionamos la cadena X-Forwarded-For de derecha a izquierda (desde el salto más cercano y confiable).
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] hops = xForwardedFor.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String candidate = hops[i].trim();
                if (isValidIpLiteral(candidate) && !isTrustedProxy(candidate)) {
                    // Primer IP público / no confiable desde la derecha: el cliente externo legítimo
                    return candidate;
                }
            }
            // Si todos los saltos fueron proxies internos/locales (entorno de pruebas o LAN),
            // seleccionamos el último salto válido
            for (int i = hops.length - 1; i >= 0; i--) {
                String candidate = hops[i].trim();
                if (isValidIpLiteral(candidate)) {
                    return candidate;
                }
            }
        }

        // 3. Alternativa X-Real-IP provista por el proxy confiable
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            String candidate = xRealIp.trim();
            if (isValidIpLiteral(candidate)) {
                return candidate;
            }
        }

        return remoteAddr;
    }
}
