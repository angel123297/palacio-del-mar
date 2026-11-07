package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.model.User;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pilar 7 Redis: Verificación instantánea de sesión en RAM (< 0.2 ms) y cierre global
 * de sesiones en todos los dispositivos (palacio:user:revoked_before:{email} + snapshot de usuario).
 */
@Slf4j
@Service
public class UserSessionCacheService {

    public static final String USER_CACHE_PREFIX = "palacio:auth:user:";
    public static final String REVOKED_BEFORE_PREFIX = "palacio:user:revoked_before:";

    private static final Duration USER_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration L1_USER_TTL = Duration.ofSeconds(30);
    private static final Duration REVOKED_BEFORE_TTL = Duration.ofDays(7);
    private static final int MAX_L1_CACHE_ENTRIES = 5_000;

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<Long> MONOTONIC_REVOCATION_SCRIPT;

    static {
        MONOTONIC_REVOCATION_SCRIPT = new DefaultRedisScript<>();
        MONOTONIC_REVOCATION_SCRIPT.setScriptText(
                "local current = redis.call('GET', KEYS[1]) " +
                "if not current or tonumber(ARGV[1]) > tonumber(current) then " +
                "  redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2])) " +
                "  return 1 " +
                "else " +
                "  return 0 " +
                "end"
        );
        MONOTONIC_REVOCATION_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final co.palaciodelmar.backend.repository.UserRepository userRepository;

    private record CachedUser(User user, long expiresAtMs) {}
    private record RevocationMarker(long revokedBeforeEpochMs, long expiresAtMs) {}

    private final ConcurrentHashMap<String, CachedUser> l1Users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RevocationMarker> l1RevokedBefore = new ConcurrentHashMap<>();

    @Autowired
    public UserSessionCacheService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) ObjectMapper providedMapper,
            @Autowired(required = false) co.palaciodelmar.backend.repository.UserRepository userRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        ObjectMapper base = providedMapper != null ? providedMapper.copy() : new ObjectMapper();
        this.objectMapper = base
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public UserSessionCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper providedMapper
    ) {
        this(redisTemplate, providedMapper, null);
    }

    public Optional<User> getCachedUser(String email) {
        String norm = normalizeEmail(email);
        if (norm == null) {
            return Optional.empty();
        }
        long now = System.currentTimeMillis();
        CachedUser l1 = l1Users.get(norm);
        if (l1 != null) {
            Long revokedCutoff = getRevokedBeforeEpochMs(norm);
            Instant uRevoked = l1.user().getSessionsRevokedAt();
            long uRevEpoch = uRevoked != null ? uRevoked.toEpochMilli() : 0L;
            if (revokedCutoff != null && revokedCutoff > uRevEpoch) {
                l1Users.remove(norm, l1);
            } else if (l1.expiresAtMs() > now) {
                return Optional.of(l1.user());
            } else {
                l1Users.remove(norm, l1);
            }
        }

        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(USER_CACHE_PREFIX + norm);
                if (json != null && !json.isBlank()) {
                    User deserialized = sanitizeForCache(objectMapper.readValue(json, User.class));
                    Long revokedCutoff = getRevokedBeforeEpochMs(norm);
                    Instant uRevoked = deserialized.getSessionsRevokedAt();
                    long uRevEpoch = uRevoked != null ? uRevoked.toEpochMilli() : 0L;
                    if (revokedCutoff != null && revokedCutoff > uRevEpoch) {
                        redisTemplate.delete(USER_CACHE_PREFIX + norm);
                        return Optional.empty();
                    }
                    putBoundedL1User(norm, new CachedUser(deserialized, now + L1_USER_TTL.toMillis()));
                    return Optional.of(deserialized);
                }
            } catch (Exception e) {
                log.debug("Falla leyendo caché de sesión Redis para {}: {}. Forzando lectura autoritativa de MongoDB.", norm, e.getMessage());
                l1Users.remove(norm);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public void putCachedUser(User user) {
        if (user == null || user.getEmail() == null) {
            return;
        }
        String norm = normalizeEmail(user.getEmail());
        if (norm == null) {
            return;
        }
        User sanitized = sanitizeForCache(user);
        putBoundedL1User(norm, new CachedUser(sanitized, System.currentTimeMillis() + L1_USER_TTL.toMillis()));
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(sanitized);
                redisTemplate.opsForValue().set(USER_CACHE_PREFIX + norm, json, USER_CACHE_TTL);
            } catch (Exception e) {
                log.debug("No se pudo almacenar snapshot de usuario en Redis para {}: {}", norm, e.getMessage());
            }
        }
    }

    public static User sanitizeForCache(User source) {
        if (source == null) {
            return null;
        }
        return User.builder()
                .id(source.getId())
                .name(source.getName())
                .lastName(source.getLastName())
                .email(source.getEmail())
                .password(null)
                .role(source.getRole())
                .branches(source.getBranches() != null ? new java.util.ArrayList<>(source.getBranches()) : new java.util.ArrayList<>())
                .status(source.getStatus())
                .emailVerified(source.getEmailVerified())
                .verificationTokenHash(null)
                .verificationTokenExpiresAt(null)
                .resetPasswordTokenHash(null)
                .resetPasswordTokenExpiresAt(null)
                .profile(source.getProfile())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .deletedAt(source.getDeletedAt())
                .passwordChangedAt(source.getPasswordChangedAt())
                .sessionsRevokedAt(source.getSessionsRevokedAt())
                .build();
    }

    private void putBoundedL1User(String normEmail, CachedUser entry) {
        l1Users.put(normEmail, entry);
        if (l1Users.size() > MAX_L1_CACHE_ENTRIES) {
            cleanupExpiredLocalEntries();
            if (l1Users.size() > MAX_L1_CACHE_ENTRIES) {
                var it = l1Users.keySet().iterator();
                while (l1Users.size() > MAX_L1_CACHE_ENTRIES && it.hasNext()) {
                    String k = it.next();
                    if (!k.equals(normEmail)) {
                        it.remove();
                    }
                }
            }
        }
    }

    public void evictUser(String email) {
        String norm = normalizeEmail(email);
        if (norm == null) {
            return;
        }
        l1Users.remove(norm);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(USER_CACHE_PREFIX + norm);
            } catch (Exception e) {
                log.warn("No se pudo invalidar caché de usuario en Redis para {}: {}", norm, e.getMessage());
            }
        }
    }

    /**
     * Registra en Redis y L1 la marca de tiempo (en milisegundos epoch) antes de la cual
     * todos los JWT del usuario quedan revocados globalmente.
     */
    public void recordGlobalRevocation(String email, Instant revokedAt) {
        String norm = normalizeEmail(email);
        if (norm == null || revokedAt == null) {
            return;
        }
        long epochMs = revokedAt.toEpochMilli();
        l1RevokedBefore.put(norm, new RevocationMarker(epochMs, System.currentTimeMillis() + REVOKED_BEFORE_TTL.toMillis()));
        evictUser(norm);

        if (redisTemplate != null) {
            try {
                redisTemplate.execute(
                        MONOTONIC_REVOCATION_SCRIPT,
                        List.of(REVOKED_BEFORE_PREFIX + norm),
                        String.valueOf(epochMs),
                        String.valueOf(REVOKED_BEFORE_TTL.toSeconds())
                );
            } catch (Exception e) {
                log.warn("No se pudo registrar revocación global en Redis para {}: {}. Persistido en MongoDB.", norm, e.getMessage());
            }
        }
    }

    /**
     * Verifica en < 0.2 ms si la marca de emisión (iatMs) del token es anterior o igual a una revocación global.
     */
    public boolean isTokenRevokedForUser(String email, long tokenIssuedAtMs) {
        String norm = normalizeEmail(email);
        if (norm == null || tokenIssuedAtMs <= 0) {
            return false;
        }
        Long revokedBeforeMs = getRevokedBeforeEpochMs(norm);
        return revokedBeforeMs != null && tokenIssuedAtMs <= revokedBeforeMs;
    }

    public Long getRevokedBeforeEpochMs(String normalizedEmail) {
        long now = System.currentTimeMillis();
        RevocationMarker rawMarker = l1RevokedBefore.get(normalizedEmail);
        final RevocationMarker activeMarker;
        if (rawMarker != null && rawMarker.expiresAtMs() <= now) {
            l1RevokedBefore.remove(normalizedEmail);
            activeMarker = null;
        } else {
            activeMarker = rawMarker;
        }

        if (redisTemplate == null) {
            if (activeMarker != null) {
                return activeMarker.revokedBeforeEpochMs();
            }
            if (userRepository != null) {
                try {
                    return userRepository.findByEmail(normalizedEmail)
                            .map(User::getSessionsRevokedAt)
                            .map(Instant::toEpochMilli)
                            .map(revEpoch -> {
                                l1RevokedBefore.put(normalizedEmail, new RevocationMarker(revEpoch, now + L1_USER_TTL.toMillis()));
                                return revEpoch;
                            })
                            .orElse(null);
                } catch (Exception ex) {
                    log.error("Falla consultando MongoDB para revocación de {}: {}", normalizedEmail, ex.getMessage());
                    return Long.MAX_VALUE;
                }
            }
            return null;
        }

        try {
            String raw = redisTemplate.opsForValue().get(REVOKED_BEFORE_PREFIX + normalizedEmail);
            if (raw != null && !raw.isBlank()) {
                long parsed = Long.parseLong(raw.trim());
                if (parsed > 0) {
                    long effective = (activeMarker != null) ? Math.max(parsed, activeMarker.revokedBeforeEpochMs()) : parsed;
                    l1RevokedBefore.put(normalizedEmail, new RevocationMarker(effective, now + L1_USER_TTL.toMillis()));
                    return effective;
                }
            }
        } catch (Exception e) {
            log.debug("Redis no disponible para consultar marca revoked_before en {}: {}. Verificando MongoDB de forma autoritativa.", normalizedEmail, e.getMessage());
            // Fallback autoritativo a MongoDB cuando Redis no está disponible
            if (userRepository != null) {
                try {
                    return userRepository.findByEmail(normalizedEmail)
                            .map(User::getSessionsRevokedAt)
                            .map(Instant::toEpochMilli)
                            .orElse(activeMarker != null ? activeMarker.revokedBeforeEpochMs() : null);
                } catch (Exception ex) {
                    log.error("Falla simultánea en Redis y MongoDB al verificar revocación para {}: {}. Fail-closed.", normalizedEmail, ex.getMessage());
                    // Alert 2: Fallo de ambos almacenes -> fail-closed (invalida tokens para seguridad)
                    return activeMarker != null ? activeMarker.revokedBeforeEpochMs() : Long.MAX_VALUE;
                }
            }
            return activeMarker != null ? activeMarker.revokedBeforeEpochMs() : Long.MAX_VALUE;
        }

        // Si Redis no contiene la clave ("raw == null") o no está configurado:
        // Consultar la base de datos MongoDB para verificar si existe una revocación persistida
        if (userRepository != null) {
            try {
                return userRepository.findByEmail(normalizedEmail)
                        .map(User::getSessionsRevokedAt)
                        .map(Instant::toEpochMilli)
                        .map(revEpoch -> {
                            long effective = (activeMarker != null) ? Math.max(revEpoch, activeMarker.revokedBeforeEpochMs()) : revEpoch;
                            l1RevokedBefore.put(normalizedEmail, new RevocationMarker(effective, now + L1_USER_TTL.toMillis()));
                            if (redisTemplate != null) {
                                try {
                                    redisTemplate.execute(
                                            MONOTONIC_REVOCATION_SCRIPT,
                                            List.of(REVOKED_BEFORE_PREFIX + normalizedEmail),
                                            String.valueOf(effective),
                                            String.valueOf(REVOKED_BEFORE_TTL.toSeconds())
                                    );
                                } catch (Exception ignored) {}
                            }
                            return effective;
                        })
                        .orElse(activeMarker != null ? activeMarker.revokedBeforeEpochMs() : null); // Alert 1: Si no hay revocación en MongoDB, NUNCA escribir "0" en Redis ni cachear negativo
            } catch (Exception ex) {
                log.error("Falla consultando MongoDB para revocación de {}: {}", normalizedEmail, ex.getMessage());
                return activeMarker != null ? activeMarker.revokedBeforeEpochMs() : null;
            }
        }
        return activeMarker != null ? activeMarker.revokedBeforeEpochMs() : null;
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupExpiredLocalEntries() {
        long now = System.currentTimeMillis();
        l1Users.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
        l1RevokedBefore.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
