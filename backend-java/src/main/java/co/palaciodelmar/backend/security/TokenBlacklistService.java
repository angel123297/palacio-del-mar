package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.RevokedToken;
import co.palaciodelmar.backend.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de revocación de tokens JWT (Blacklist) con arquitectura multi-capa:
 * 1. Capa L1 (Memoria local concurrente con sweeper): acceso inmediato en nanosegundos.
 * 2. Capa L2 (Redis distribuido con TTL nativo opcional): sincronización entre réplicas.
 * 3. Capa L3 (MongoDB con índice TTL durable obligatorio): garantiza persistencia durable
 *    eliminando el riesgo fail-open ante caídas o reinicios de instancias/Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String REDIS_PREFIX = "palacio:jwt:blacklist:";

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // Almacén en memoria L1 para lecturas ultrarrápidas
    private final Map<String, Long> inMemoryBlacklist = new ConcurrentHashMap<>();

    /**
     * Revoca un token JWT de forma duradera en MongoDB, Redis y memoria local.
     *
     * @param token el token JWT a revocar
     */
    public void blacklistToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        try {
            String tokenHash = hashToken(token);
            Date expiration = null;
            try {
                expiration = jwtService.extractExpiration(token);
            } catch (Exception ex) {
                log.debug("No se pudo extraer la fecha de expiración del token a revocar: {}", ex.getMessage());
            }

            long ttlMillis = (expiration != null)
                    ? Math.max(0, expiration.getTime() - System.currentTimeMillis())
                    : 86400000L; // Fallback 24h

            if (ttlMillis <= 0) {
                // Si el token ya expiró de forma natural, no requiere blacklist
                return;
            }

            Date expiresAtDate = new Date(System.currentTimeMillis() + ttlMillis);

            // 1. Persistencia duradera obligatoria en MongoDB
            RevokedToken revokedToken = RevokedToken.builder()
                    .tokenHash(tokenHash)
                    .expiresAt(expiresAtDate)
                    .revokedAt(new Date())
                    .build();
            revokedTokenRepository.save(revokedToken);

            // 2. Persistencia en Redis L2 (para alta concurrencia)
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(REDIS_PREFIX + tokenHash, "revoked", Duration.ofMillis(ttlMillis));
                } catch (Exception redisEx) {
                    log.warn("Falla temporal de escritura en Redis para revocación de JWT: {}. Salvaguardado en MongoDB/Memoria.", redisEx.getMessage());
                }
            }

            // 3. Almacén L1 en memoria local
            inMemoryBlacklist.put(tokenHash, System.currentTimeMillis() + ttlMillis);
            cleanExpiredInMemory();

            log.info("Token JWT revocado exitosamente (hash={}) por {} ms", tokenHash, ttlMillis);
        } catch (Exception e) {
            log.error("Falla al revocar token JWT de forma duradera: {}", e.getMessage(), e);
            throw new IllegalStateException("No se pudo persistir la revocación del token: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica si un token JWT ha sido revocado consultando Memoria L1 -> Redis L2 -> MongoDB L3.
     * Si ocurre un error de infraestructura al consultar MongoDB, aplica fail-closed devolviendo true.
     *
     * @param token el token JWT a verificar
     * @return true si el token está en la lista negra (o ante falla de BD), false si es válido
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            String tokenHash = hashToken(token);

            // 1. Verificación en memoria L1
            Long expiry = inMemoryBlacklist.get(tokenHash);
            if (expiry != null) {
                if (System.currentTimeMillis() < expiry) {
                    return true;
                } else {
                    inMemoryBlacklist.remove(tokenHash);
                }
            }

            // 2. Verificación en Redis L2
            if (redisTemplate != null) {
                try {
                    Boolean hasKey = redisTemplate.hasKey(REDIS_PREFIX + tokenHash);
                    if (Boolean.TRUE.equals(hasKey)) {
                        inMemoryBlacklist.put(tokenHash, System.currentTimeMillis() + 60000); // Calentar L1
                        return true;
                    }
                } catch (Exception ex) {
                    log.warn("Falla de consulta en Redis para blacklist: {}. Verificando persistencia en MongoDB.", ex.getMessage());
                }
            }

            // 3. Verificación duradera obligatoria en MongoDB L3 (elimina el fail-open)
            try {
                if (revokedTokenRepository.existsById(tokenHash)) {
                    inMemoryBlacklist.put(tokenHash, System.currentTimeMillis() + 60000); // Calentar L1
                    return true;
                }
            } catch (Exception mongoEx) {
                log.error("Falla al consultar revocación de token en MongoDB: {}. Aplicando fail-closed.", mongoEx.getMessage());
                // Fail-closed por seguridad: ante falla de base de datos se trata como revocado
                return true;
            }
        } catch (Exception e) {
            log.warn("Error al consultar revocación de token: {}", e.getMessage());
        }

        return false;
    }

    @Scheduled(fixedRate = 600000)
    public void cleanExpiredInMemory() {
        long now = System.currentTimeMillis();
        inMemoryBlacklist.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Algoritmo de hash no soportado en JVM: " + e.getMessage(), e);
        }
    }
}
