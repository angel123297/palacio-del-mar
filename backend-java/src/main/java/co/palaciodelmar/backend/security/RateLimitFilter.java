package co.palaciodelmar.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    public static final String PROXY_TOKEN_HEADER = "X-Palacio-Proxy-Token";
    private static final java.util.Set<String> FORBIDDEN_PROXY_TOKENS = java.util.Set.of(
            "pdm_internal_trusted_proxy_token_2026",
            "whsec_simulated_secret_key_palacio_2026",
            "super_secret_jwt_key_palacio_del_mar_2026_dev_min32chars"
    );

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Getter
    @Setter
    @Value("${palacio.security.proxy-token}")
    private String proxyToken;

    @jakarta.annotation.PostConstruct
    public void validateProxyToken() {
        if (proxyToken == null || proxyToken.isBlank()) {
            throw new IllegalStateException("PALACIO_PROXY_TOKEN es obligatorio y no puede estar vacío");
        }
        String trimmed = proxyToken.trim();
        if (trimmed.length() < 24 || FORBIDDEN_PROXY_TOKENS.contains(trimmed)) {
            throw new IllegalStateException("PALACIO_PROXY_TOKEN inseguro: debe tener >= 24 caracteres y no usar el valor público por defecto");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        RateLimitService.RateLimitResult result = null;
        String clientIp = null;

        if ("POST".equalsIgnoreCase(method)) {
            if (path.endsWith("/api/auth/login")) {
                clientIp = resolveClientIp(request);
                // Login: 10 intentos por minuto por IP
                result = rateLimitService.checkLimit("login", clientIp, 10, 60);
            } else if (path.endsWith("/api/auth/forgot-password")) {
                clientIp = resolveClientIp(request);
                // Recuperación: 3 solicitudes por cada 15 minutos por IP
                result = rateLimitService.checkLimit("forgot-password", clientIp, 3, 900);
            } else if (path.endsWith("/api/auth/resend-verification")) {
                clientIp = resolveClientIp(request);
                // Reenvío de verificación: 3 solicitudes por cada 15 minutos por IP
                result = rateLimitService.checkLimit("resend-verification", clientIp, 3, 900);
            } else if (path.endsWith("/api/auth/register")) {
                clientIp = resolveClientIp(request);
                // Registro: 5 cuentas por cada 10 minutos por IP
                result = rateLimitService.checkLimit("register", clientIp, 5, 600);
            } else if (path.endsWith("/api/auth/reset-password")) {
                clientIp = resolveClientIp(request);
                // Restablecimiento: 5 intentos por cada 5 minutos por IP
                result = rateLimitService.checkLimit("reset-password", clientIp, 5, 300);
            } else if (path.endsWith("/viewers/ping") && path.contains("/api/suites/")) {
                clientIp = resolveClientIp(request);
                // Ping de espectadores activos (Social Proof): máx. 30 pings por minuto por IP
                result = rateLimitService.checkLimit("viewers-ping", clientIp, 30, 60);
            } else if (path.equals("/api/bookings") || path.endsWith("/api/bookings")) {
                clientIp = resolveClientIp(request);
                // Creación de reservas (protege IdempotencyService + retención de noches): 15 req/min por IP
                result = rateLimitService.checkLimit("bookings-create", clientIp, 15, 60);
            } else if (path.endsWith("/api/demo/webhook-receiver")) {
                clientIp = resolveClientIp(request);
                // Receptor demo de webhooks: máx. 30 entregas por minuto por IP
                result = rateLimitService.checkLimit("demo-webhook-receiver", clientIp, 30, 60);
            }
        } else if ("GET".equalsIgnoreCase(method)) {
            if (path.endsWith("/social-proof") && path.contains("/api/suites/")) {
                clientIp = resolveClientIp(request);
                // Lectura pública de prueba social: máx. 60 lecturas por minuto por IP
                result = rateLimitService.checkLimit("social-proof-get", clientIp, 60, 60);
            }
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

        // 1. Si la conexión socket directa NO proviene de un proxy confiable autorizado (loopback o red interna de contenedores),
        // ignoramos cualquier encabezado de reenvío para prevenir falsificación directa de IP.
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        // 2. Para prevenir que otro contenedor o servicio en la red Docker interna falsifique cabeceras,
        // exigimos la validación en tiempo constante del token secreto compartido inyectado exclusivamente por el proxy Nginx.
        if (proxyToken == null || proxyToken.isBlank() || FORBIDDEN_PROXY_TOKENS.contains(proxyToken.trim())) {
            return remoteAddr;
        }
        String receivedToken = request.getHeader(PROXY_TOKEN_HEADER);
        if (receivedToken == null || !java.security.MessageDigest.isEqual(
                proxyToken.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                receivedToken.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return remoteAddr;
        }

        // 3. Con proxy y token autenticado, priorizamos X-Real-IP saneado y sustituido por el proxy
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            String candidate = xRealIp.trim();
            if (isValidIpLiteral(candidate)) {
                return candidate;
            }
        }

        // 4. Como fallback de proxy confiable, tomamos el salto inmediato (último/derecho) de X-Forwarded-For
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] hops = xForwardedFor.split(",");
            String rightmost = hops[hops.length - 1].trim();
            if (isValidIpLiteral(rightmost)) {
                return rightmost;
            }
        }

        return remoteAddr;
    }
}
