package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.SocialProofDTO;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Servicio de Prueba Social y Urgencia en Tiempo Real sobre Redis Sorted Sets (ZSET).
 * Registra espectadores activos en una ventana deslizante de 3 minutos y métricas reales
 * de reservas recientes en las últimas 24–48 horas sin sobrecargar MongoDB.
 */
@Slf4j
@Service
public class SocialProofService {

    public static final String VIEWERS_PREFIX = "palacio:social:viewers:";
    public static final String LAST_BOOKED_PREFIX = "palacio:social:last_booked:";
    public static final String BOOKINGS_24H_PREFIX = "palacio:social:bookings_24h:";

    private static final long VIEWER_WINDOW_MS = 180_000L; // 3 minutos
    private static final long DAY_WINDOW_MS = 86_400_000L; // 24 horas
    private static final long MAX_VIEWERS_PER_SUITE = 250L;
    public static final int MAX_LOCAL_SUITES = 500;
    private static final long SUITE_KEY_CACHE_TTL_MS = 300_000L; // 5 minutos
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,80}$");

    private StringRedisTemplate redisTemplate;
    private final SuiteRepository suiteRepository;

    // Respaldo L1 en memoria ante indisponibilidad de Redis (con tope duro de cardinalidad)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> localViewers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> localLastBooked = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> localBookings24h = new ConcurrentHashMap<>();
    private record CachedCanonicalKey(String canonicalId, long expiresAtMs) {}
    private final ConcurrentHashMap<String, CachedCanonicalKey> knownSuiteCache = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong lastCleanupAtMs = new java.util.concurrent.atomic.AtomicLong(0L);

    @Autowired
    public SocialProofService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) SuiteRepository suiteRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.suiteRepository = suiteRepository;
    }

    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    int getLocalViewersSuiteCount() {
        return localViewers.size();
    }

    public SocialProofDTO recordViewerPing(String idOrSlug, String clientIp, String viewerSessionId) {
        String canonicalKey = resolveCanonicalSuiteKey(idOrSlug);
        String viewerMember = buildViewerHash(clientIp, viewerSessionId);
        long nowMs = System.currentTimeMillis();
        long minScore = nowMs - VIEWER_WINDOW_MS;

        long activeViewers = updateAndCountViewers(canonicalKey, viewerMember, nowMs, minScore, true);
        return buildSnapshot(canonicalKey, activeViewers, nowMs);
    }

    public SocialProofDTO getSocialProof(String idOrSlug) {
        String canonicalKey = resolveCanonicalSuiteKey(idOrSlug);
        long nowMs = System.currentTimeMillis();
        long minScore = nowMs - VIEWER_WINDOW_MS;

        long activeViewers = updateAndCountViewers(canonicalKey, null, nowMs, minScore, false);
        return buildSnapshot(canonicalKey, activeViewers, nowMs);
    }

    public void recordBookingActivity(String suiteId, String suiteSlug, String bookingEventId) {
        long nowMs = System.currentTimeMillis();
        String eventToken = (bookingEventId != null && !bookingEventId.isBlank())
                ? bookingEventId.trim()
                : UUID.randomUUID().toString();

        if (suiteId != null && !suiteId.isBlank()) {
            recordBookingForCanonicalKey(sanitizeKey(suiteId), eventToken, nowMs);
        }
        if (suiteSlug != null && !suiteSlug.isBlank()) {
            String slugKey = sanitizeKey(suiteSlug);
            if (suiteId == null || !slugKey.equalsIgnoreCase(suiteId.trim())) {
                recordBookingForCanonicalKey(slugKey, eventToken, nowMs);
            }
        }
    }

    private void recordBookingForCanonicalKey(String key, String eventToken, long nowMs) {
        maybeThrottleCleanup(nowMs);
        localLastBooked.put(key, nowMs);
        evictMapOverflow(localLastBooked, MAX_LOCAL_SUITES, key);
        ConcurrentHashMap<String, Long> bMap = localBookings24h.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        bMap.put(eventToken, nowMs);
        bMap.entrySet().removeIf(e -> e.getValue() < nowMs - DAY_WINDOW_MS);
        evictMapOverflow(localBookings24h, MAX_LOCAL_SUITES, key);

        if (redisTemplate != null) {
            try {
                String lastKey = LAST_BOOKED_PREFIX + key;
                String b24Key = BOOKINGS_24H_PREFIX + key;
                redisTemplate.opsForValue().set(lastKey, String.valueOf(nowMs), Duration.ofHours(48));
                redisTemplate.opsForZSet().removeRangeByScore(b24Key, 0, nowMs - DAY_WINDOW_MS);
                redisTemplate.opsForZSet().add(b24Key, eventToken, nowMs);
                redisTemplate.expire(b24Key, Duration.ofHours(24));
            } catch (Exception e) {
                log.debug("No se pudo actualizar prueba social de reserva en Redis ({}): {}", key, e.getMessage());
            }
        }
    }

    private long updateAndCountViewers(String canonicalKey, String viewerMember, long nowMs, long minScore, boolean addViewer) {
        if (redisTemplate != null) {
            try {
                String zsetKey = VIEWERS_PREFIX + canonicalKey;
                redisTemplate.opsForZSet().removeRangeByScore(zsetKey, 0, minScore);
                if (addViewer && viewerMember != null) {
                    redisTemplate.opsForZSet().add(zsetKey, viewerMember, nowMs);
                    redisTemplate.expire(zsetKey, Duration.ofMinutes(5));
                }
                Long count = redisTemplate.opsForZSet().zCard(zsetKey);
                long total = count != null ? count : 0L;
                if (total > MAX_VIEWERS_PER_SUITE) {
                    redisTemplate.opsForZSet().removeRange(zsetKey, 0, total - MAX_VIEWERS_PER_SUITE - 1);
                    total = MAX_VIEWERS_PER_SUITE;
                }
                return total;
            } catch (Exception ex) {
                log.debug("Falla en Redis ZSET para social proof ({}): {}. Usando respaldo L1.", canonicalKey, ex.getMessage());
            }
        }

        if (!addViewer) {
            ConcurrentHashMap<String, Long> existing = localViewers.get(canonicalKey);
            if (existing == null) {
                return 0L;
            }
            existing.entrySet().removeIf(e -> e.getValue() <= minScore);
            if (existing.isEmpty()) {
                localViewers.remove(canonicalKey, existing);
                return 0L;
            }
            return existing.size();
        }

        maybeThrottleCleanup(nowMs);
        ConcurrentHashMap<String, Long> map = localViewers.computeIfAbsent(canonicalKey, k -> new ConcurrentHashMap<>());
        map.entrySet().removeIf(e -> e.getValue() <= minScore);
        if (viewerMember != null && map.size() < MAX_VIEWERS_PER_SUITE) {
            map.put(viewerMember, nowMs);
        }
        evictMapOverflow(localViewers, MAX_LOCAL_SUITES, canonicalKey);
        return map.size();
    }

    private SocialProofDTO buildSnapshot(String canonicalKey, long activeViewers, long nowMs) {
        Long lastBookedEpochMs = null;
        long bookings24h = 0L;

        if (redisTemplate != null) {
            try {
                String rawLast = redisTemplate.opsForValue().get(LAST_BOOKED_PREFIX + canonicalKey);
                if (rawLast != null && !rawLast.isBlank()) {
                    lastBookedEpochMs = Long.parseLong(rawLast.trim());
                }
                String b24Key = BOOKINGS_24H_PREFIX + canonicalKey;
                redisTemplate.opsForZSet().removeRangeByScore(b24Key, 0, nowMs - DAY_WINDOW_MS);
                Long count24h = redisTemplate.opsForZSet().zCard(b24Key);
                bookings24h = count24h != null ? count24h : 0L;
            } catch (Exception e) {
                log.debug("Usando métricas locales de reserva para social proof ({}): {}", canonicalKey, e.getMessage());
                lastBookedEpochMs = localLastBooked.get(canonicalKey);
                bookings24h = countLocalBookings24h(canonicalKey, nowMs);
            }
        } else {
            lastBookedEpochMs = localLastBooked.get(canonicalKey);
            bookings24h = countLocalBookings24h(canonicalKey, nowMs);
        }

        Long minutesAgo = null;
        if (lastBookedEpochMs != null && lastBookedEpochMs <= nowMs) {
            minutesAgo = Math.max(0L, (nowMs - lastBookedEpochMs) / 60_000L);
        }

        boolean highDemand = activeViewers >= 3 || bookings24h >= 2;

        return SocialProofDTO.builder()
                .suiteKey(canonicalKey)
                .activeViewers(activeViewers)
                .lastBookedMinutesAgo(minutesAgo)
                .bookingsLast24h(bookings24h)
                .highDemand(highDemand)
                .build();
    }

    private long countLocalBookings24h(String canonicalKey, long nowMs) {
        Map<String, Long> bMap = localBookings24h.get(canonicalKey);
        if (bMap == null) return 0L;
        bMap.entrySet().removeIf(e -> e.getValue() < nowMs - DAY_WINDOW_MS);
        return bMap.size();
    }

    private String resolveCanonicalSuiteKey(String idOrSlug) {
        String sanitized = sanitizeKey(idOrSlug);
        if (suiteRepository != null) {
            long now = System.currentTimeMillis();
            CachedCanonicalKey cached = knownSuiteCache.get(sanitized);
            if (cached != null && cached.expiresAtMs() > now) {
                return cached.canonicalId();
            }
            String alias = AvailabilityService.SLUG_ALIASES.getOrDefault(sanitized, sanitized);
            Optional<Suite> found = suiteRepository.findById(idOrSlug.trim())
                    .or(() -> suiteRepository.findBySlug(sanitized))
                    .or(() -> suiteRepository.findBySlug(alias));
            if (found.isEmpty() || Boolean.FALSE.equals(found.get().getActive()) || found.get().getId() == null) {
                throw new co.palaciodelmar.backend.exception.ResourceNotFoundException("Suite no encontrada: " + sanitized);
            }
            String canonical = sanitizeKey(found.get().getId());
            CachedCanonicalKey entry = new CachedCanonicalKey(canonical, now + SUITE_KEY_CACHE_TTL_MS);
            knownSuiteCache.put(sanitized, entry);
            knownSuiteCache.put(canonical, entry);
            evictMapOverflow(knownSuiteCache, MAX_LOCAL_SUITES * 2, canonical);
            return canonical;
        }
        return sanitized;
    }

    private void maybeThrottleCleanup(long nowMs) {
        long last = lastCleanupAtMs.get();
        if ((localViewers.size() > MAX_LOCAL_SUITES / 2 || localBookings24h.size() > MAX_LOCAL_SUITES / 2)
                && (nowMs - last) >= 10_000L
                && lastCleanupAtMs.compareAndSet(last, nowMs)) {
            cleanupLocalState();
        }
    }

    private static <V> void evictMapOverflow(ConcurrentHashMap<String, V> map, int maxEntries, String protectedKey) {
        if (map.size() <= maxEntries) {
            return;
        }
        synchronized (map) {
            var it = map.keySet().iterator();
            while (map.size() > maxEntries && it.hasNext()) {
                String k = it.next();
                if (!k.equals(protectedKey)) {
                    it.remove();
                }
            }
        }
    }

    private String sanitizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Identificador de suite requerido");
        }
        String trimmed = raw.trim().toLowerCase();
        if (!SAFE_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Identificador de suite inválido");
        }
        return trimmed;
    }

    private String buildViewerHash(String clientIp, String sessionToken) {
        String ipPart = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "unknown";
        String sessPart = (sessionToken != null && SAFE_ID_PATTERN.matcher(sessionToken.trim()).matches())
                ? sessionToken.trim()
                : "default";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((ipPart + "|" + sessPart).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (Exception e) {
            return ipPart + "_" + sessPart;
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupLocalState() {
        long now = System.currentTimeMillis();
        long minViewer = now - VIEWER_WINDOW_MS;
        localViewers.forEach((k, map) -> map.entrySet().removeIf(e -> e.getValue() <= minViewer));
        localViewers.entrySet().removeIf(e -> e.getValue().isEmpty());

        long minBooking = now - DAY_WINDOW_MS;
        localBookings24h.forEach((k, map) -> map.entrySet().removeIf(e -> e.getValue() < minBooking));
        localBookings24h.entrySet().removeIf(e -> e.getValue().isEmpty());

        long minLastBooked = now - (7L * DAY_WINDOW_MS);
        localLastBooked.entrySet().removeIf(e -> e.getValue() < minLastBooked);
        knownSuiteCache.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
    }
}
