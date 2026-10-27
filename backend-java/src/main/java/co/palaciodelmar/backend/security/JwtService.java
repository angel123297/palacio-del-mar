package co.palaciodelmar.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final java.util.Set<String> FORBIDDEN_SECRETS = java.util.Set.of(
            "super_secret_jwt_key_palacio_del_mar_2026_dev_min32chars",
            "whsec_simulated_secret_key_palacio_2026",
            "pdm_internal_trusted_proxy_token_2026"
    );

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    @jakarta.annotation.PostConstruct
    public void validateSecret() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT_SECRET es obligatorio y no puede estar vacío");
        }
        String trimmed = secretKey.trim();
        if (trimmed.getBytes(StandardCharsets.UTF_8).length < 32 || FORBIDDEN_SECRETS.contains(trimmed)) {
            throw new IllegalStateException("JWT_SECRET inseguro: requiere >= 32 bytes y no puede usar un secreto público de desarrollo");
        }
    }

    @Autowired(required = false)
    private UserSessionCacheService userSessionCacheService;

    public void setUserSessionCacheService(UserSessionCacheService userSessionCacheService) {
        this.userSessionCacheService = userSessionCacheService;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long nowMs = System.currentTimeMillis();
        Map<String, Object> claimsToSign = new HashMap<>();
        if (extraClaims != null) {
            claimsToSign.putAll(extraClaims);
        }
        claimsToSign.putIfAbsent("iat_ms", nowMs);

        return Jwts.builder()
                .setClaims(claimsToSign)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(nowMs))
                .setExpiration(new Date(nowMs + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (userDetails == null || !userDetails.isEnabled() || !userDetails.isAccountNonLocked() || !userDetails.isAccountNonExpired()) {
            return false;
        }

        final Claims claims = extractAllClaims(token);
        final String username = claims.getSubject();
        final Date expiration = claims.getExpiration();
        if (username == null || !username.equals(userDetails.getUsername()) || expiration == null || expiration.before(new Date())) {
            return false;
        }

        Date issuedAt = claims.getIssuedAt();
        Number iatMsClaim = claims.get("iat_ms", Number.class);
        long tokenIssuedAtMs = iatMsClaim != null
                ? iatMsClaim.longValue()
                : (issuedAt != null ? issuedAt.getTime() : 0L);

        // 1. Verificación instantánea en RAM/Redis (< 0.2 ms) para cierre global de dispositivos
        if (userSessionCacheService != null && tokenIssuedAtMs > 0
                && userSessionCacheService.isTokenRevokedForUser(username, tokenIssuedAtMs)) {
            return false;
        }

        // 2. Verificación durable contra estado del usuario (passwordChangedAt y sessionsRevokedAt)
        if (userDetails instanceof UserPrincipal principal && principal.getUser() != null) {
            java.time.Instant sessionsRevokedAt = principal.getUser().getSessionsRevokedAt();
            if (sessionsRevokedAt != null && tokenIssuedAtMs > 0 && tokenIssuedAtMs <= sessionsRevokedAt.toEpochMilli()) {
                return false;
            }

            java.time.Instant passwordChangedAt = principal.getUser().getPasswordChangedAt();
            if (passwordChangedAt != null && issuedAt != null) {
                long issuedAtSec = issuedAt.getTime() / 1000;
                long pwdChangedSec = passwordChangedAt.getEpochSecond();
                // JWT 'iat' tiene resolución en segundos (RFC 7519). Se invalidan los tokens emitidos en segundos anteriores
                if (issuedAtSec < pwdChangedSec) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
