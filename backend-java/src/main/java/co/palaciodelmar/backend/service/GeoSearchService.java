package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.GeoPlaceDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Motor Geoespacial de Cartagena sobre Redis Geo (GEOADD, GEOSEARCH, GEODIST).
 * Indexa sedes, propiedades aprobadas, monumentos, playas y puntos de embarque en Cartagena de Indias,
 * permitiendo búsquedas por radio sub-milisegundo con respaldo Haversine en memoria.
 */
@Slf4j
@Service
public class GeoSearchService {

    public static final String GEO_KEY = "palacio:geo:cartagena";
    public static final String META_HASH_KEY = "palacio:geo:meta";

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private StringRedisTemplate redisTemplate;
    private final BranchRepository branchRepository;
    private final PropertyRepository propertyRepository;
    private final SuiteRepository suiteRepository;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, GeoPlaceDTO> localCatalog = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Autowired
    public GeoSearchService(
            @Autowired(required = false) StringRedisTemplate redisTemplate,
            @Autowired(required = false) BranchRepository branchRepository,
            @Autowired(required = false) PropertyRepository propertyRepository,
            @Autowired(required = false) SuiteRepository suiteRepository,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.branchRepository = branchRepository;
        this.propertyRepository = propertyRepository;
        this.suiteRepository = suiteRepository;
        this.objectMapper = (objectMapper != null ? objectMapper.copy() : new ObjectMapper())
                .findAndRegisterModules()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void setRedisTemplate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            refreshCatalogIndex();
        } catch (Exception e) {
            log.warn("Inicialización geoespacial diferida: {}", e.getMessage());
        }
    }

    public synchronized void refreshCatalogIndex() {
        Map<String, GeoPlaceDTO> fresh = new LinkedHashMap<>();

        // 1. Puntos emblemáticos y muelles de experiencias de lujo en Cartagena
        addCuratedCartagenaLandmarks(fresh);

        // 2. Sedes oficiales (Branches) y sus highlights
        if (branchRepository != null) {
            try {
                for (Branch b : branchRepository.findAll()) {
                    if (Boolean.FALSE.equals(b.getActive())) continue;
                    if (b.getLocation() != null && isValidCoord(b.getLocation().getLat(), b.getLocation().getLng())) {
                        String memberId = "branch:" + (b.getSlug() != null ? b.getSlug() : b.getId());
                        fresh.put(memberId, GeoPlaceDTO.builder()
                                .id(memberId)
                                .name(b.getName())
                                .type("BRANCH")
                                .category("hotel")
                                .zone(b.getZone())
                                .address(b.getAddress())
                                .slug(b.getSlug())
                                .lat(b.getLocation().getLat())
                                .lng(b.getLocation().getLng())
                                .build());
                    }
                    if (b.getHighlights() != null) {
                        for (Branch.PointOfInterest poi : b.getHighlights()) {
                            if (poi != null && poi.getLocation() != null
                                    && isValidCoord(poi.getLocation().getLat(), poi.getLocation().getLng())) {
                                String poiId = "poi:" + slugify(poi.getName());
                                fresh.put(poiId, GeoPlaceDTO.builder()
                                        .id(poiId)
                                        .name(poi.getName())
                                        .type("POI")
                                        .category(poi.getType() != null ? poi.getType() : "landmark")
                                        .zone(b.getZone())
                                        .address(b.getZone() + ", Cartagena")
                                        .lat(poi.getLocation().getLat())
                                        .lng(poi.getLocation().getLng())
                                        .walkMinutes(poi.getWalkMinutes())
                                        .build());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("No se pudieron leer sedes para índice geo: {}", e.getMessage());
            }
        }

        // 3. Propiedades aprobadas del marketplace multi-anfitrión
        if (propertyRepository != null) {
            try {
                for (Property p : propertyRepository.findAll()) {
                    if (Boolean.FALSE.equals(p.getActive()) || !"APPROVED".equalsIgnoreCase(p.getStatus())) {
                        continue;
                    }
                    double[] coords = inferCartagenaCoordinates(p.getAddress(), p.getCity(), p.getName());
                    String propId = "property:" + (p.getSlug() != null ? p.getSlug() : p.getId());
                    fresh.put(propId, GeoPlaceDTO.builder()
                            .id(propId)
                            .name(p.getName())
                            .type("PROPERTY")
                            .category("hotel")
                            .zone(p.getCity() != null ? p.getCity() : "Cartagena")
                            .address(p.getAddress())
                            .slug(p.getSlug())
                            .lat(coords[0])
                            .lng(coords[1])
                            .build());
                }
            } catch (Exception e) {
                log.debug("No se pudieron indexar propiedades en geo: {}", e.getMessage());
            }
        }

        localCatalog.clear();
        localCatalog.putAll(fresh);
        initialized.set(true);

        if (redisTemplate != null && !fresh.isEmpty()) {
            try {
                Map<String, Point> geoPoints = new HashMap<>();
                Map<String, String> metaMap = new HashMap<>();
                for (Map.Entry<String, GeoPlaceDTO> entry : fresh.entrySet()) {
                    GeoPlaceDTO dto = entry.getValue();
                    // Redis Geo requiere orden (longitude, latitude)
                    geoPoints.put(entry.getKey(), new Point(dto.getLng(), dto.getLat()));
                    metaMap.put(entry.getKey(), objectMapper.writeValueAsString(dto));
                }
                redisTemplate.opsForGeo().add(GEO_KEY, geoPoints);
                redisTemplate.opsForHash().putAll(META_HASH_KEY, metaMap);

                // Depurar entradas obsoletas (p. ej. propiedades suspendidas o rechazadas) sin ventana de indisponibilidad
                Set<Object> existingKeys = redisTemplate.opsForHash().keys(META_HASH_KEY);
                if (existingKeys != null && !existingKeys.isEmpty()) {
                    List<String> staleMembers = existingKeys.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .filter(k -> !fresh.containsKey(k))
                            .toList();
                    if (!staleMembers.isEmpty()) {
                        redisTemplate.opsForGeo().remove(GEO_KEY, staleMembers.toArray(new String[0]));
                        redisTemplate.opsForHash().delete(META_HASH_KEY, staleMembers.toArray());
                    }
                }
                log.info("Índice geoespacial Redis actualizado con {} ubicaciones en Cartagena", fresh.size());
            } catch (Exception redisEx) {
                log.warn("No se pudo sincronizar índice Geo en Redis (usando motor Haversine local): {}", redisEx.getMessage());
            }
        }
    }

    public List<GeoPlaceDTO> findNearby(double lat, double lng, double radiusKm, String typeFilter, int limit) {
        if (!isValidCoord(lat, lng)) {
            throw new IllegalArgumentException("Coordenadas geográficas fuera de rango válido");
        }
        ensureInitialized();

        double clampedRadius = Math.max(0.1, Math.min(100.0, radiusKm));
        int clampedLimit = Math.max(1, Math.min(50, limit));
        String normalizedType = (typeFilter != null && !typeFilter.isBlank()) ? typeFilter.trim().toUpperCase() : null;

        if (redisTemplate != null) {
            try {
                GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                        GEO_KEY,
                        GeoReference.fromCoordinate(lng, lat),
                        new Distance(clampedRadius, Metrics.KILOMETERS),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance()
                                .includeCoordinates()
                                .sortAscending()
                                .limit(50)
                );

                if (results != null && !results.getContent().isEmpty()) {
                    List<GeoPlaceDTO> out = new ArrayList<>();
                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> gr : results.getContent()) {
                        String memberId = gr.getContent().getName();
                        GeoPlaceDTO base = resolvePlaceMetadata(memberId, gr.getContent().getPoint());
                        if (base == null) continue;
                        if (normalizedType != null && !normalizedType.equalsIgnoreCase(base.getType())
                                && !normalizedType.equalsIgnoreCase(base.getCategory())) {
                            continue;
                        }
                        double distKm = gr.getDistance() != null ? round2(gr.getDistance().getValue()) : haversineKm(lat, lng, base.getLat(), base.getLng());
                        out.add(enrichWithDistance(base, distKm));
                        if (out.size() >= clampedLimit) break;
                    }
                    return out;
                }
            } catch (Exception ex) {
                log.debug("Fallback Haversine activado en findNearby: {}", ex.getMessage());
            }
        }

        // Respaldo exacto Haversine en memoria
        return localCatalog.values().stream()
                .filter(p -> normalizedType == null
                        || normalizedType.equalsIgnoreCase(p.getType())
                        || normalizedType.equalsIgnoreCase(p.getCategory()))
                .map(p -> enrichWithDistance(p, round2(haversineKm(lat, lng, p.getLat(), p.getLng()))))
                .filter(p -> p.getDistanceKm() != null && p.getDistanceKm() <= clampedRadius)
                .sorted(Comparator.comparingDouble(GeoPlaceDTO::getDistanceKm))
                .limit(clampedLimit)
                .toList();
    }

    public List<GeoPlaceDTO> findNearbyForSuite(String suiteIdOrSlug, double radiusKm, String typeFilter, int limit) {
        if (suiteIdOrSlug == null || suiteIdOrSlug.isBlank()) {
            throw new IllegalArgumentException("Identificador de suite requerido");
        }
        ensureInitialized();

        double[] origin = resolveSuiteCoordinates(suiteIdOrSlug.trim());
        return findNearby(origin[0], origin[1], radiusKm, typeFilter, limit).stream()
                .filter(p -> p.getDistanceKm() == null || p.getDistanceKm() > 0.01 || !"BRANCH".equalsIgnoreCase(p.getType()))
                .toList();
    }

    public Double distanceBetweenKm(String fromMemberId, String toMemberId) {
        if (fromMemberId == null || toMemberId == null || fromMemberId.isBlank() || toMemberId.isBlank()) {
            throw new IllegalArgumentException("Se requieren ambos identificadores de ubicación");
        }
        ensureInitialized();

        if (redisTemplate != null) {
            try {
                Distance d = redisTemplate.opsForGeo().distance(GEO_KEY, fromMemberId.trim(), toMemberId.trim(), Metrics.KILOMETERS);
                if (d != null) {
                    return round2(d.getValue());
                }
            } catch (Exception ex) {
                log.debug("Fallback Haversine en distanceBetweenKm: {}", ex.getMessage());
            }
        }

        GeoPlaceDTO a = localCatalog.get(fromMemberId.trim());
        GeoPlaceDTO b = localCatalog.get(toMemberId.trim());
        if (a == null || b == null) {
            return null;
        }
        return round2(haversineKm(a.getLat(), a.getLng(), b.getLat(), b.getLng()));
    }

    private void ensureInitialized() {
        if (!initialized.get()) {
            refreshCatalogIndex();
        }
    }

    private GeoPlaceDTO resolvePlaceMetadata(String memberId, Point point) {
        GeoPlaceDTO cached = localCatalog.get(memberId);
        if (cached != null) {
            return cached;
        }
        if (redisTemplate != null) {
            try {
                Object raw = redisTemplate.opsForHash().get(META_HASH_KEY, memberId);
                if (raw instanceof String json) {
                    return objectMapper.readValue(json, GeoPlaceDTO.class);
                }
            } catch (Exception ignored) {
            }
        }
        if (point != null) {
            return GeoPlaceDTO.builder()
                    .id(memberId)
                    .name(memberId)
                    .type("POI")
                    .lat(point.getY())
                    .lng(point.getX())
                    .build();
        }
        return null;
    }

    private GeoPlaceDTO enrichWithDistance(GeoPlaceDTO src, double distKm) {
        Integer walkMins = distKm <= 2.5 ? Math.max(1, (int) Math.round(distKm * 12.5)) : null;
        int driveMins = Math.max(2, (int) Math.round(distKm * 3.0));
        return GeoPlaceDTO.builder()
                .id(src.getId())
                .name(src.getName())
                .type(src.getType())
                .category(src.getCategory())
                .zone(src.getZone())
                .address(src.getAddress())
                .slug(src.getSlug())
                .lat(src.getLat())
                .lng(src.getLng())
                .distanceKm(distKm)
                .walkMinutes(walkMins != null ? walkMins : src.getWalkMinutes())
                .driveMinutes(driveMins)
                .build();
    }

    private double[] resolveSuiteCoordinates(String idOrSlug) {
        String lower = idOrSlug.toLowerCase();
        String alias = AvailabilityService.SLUG_ALIASES.getOrDefault(lower, lower);

        if (suiteRepository != null) {
            Optional<Suite> suiteOpt = suiteRepository.findById(idOrSlug)
                    .or(() -> suiteRepository.findBySlug(lower))
                    .or(() -> suiteRepository.findBySlug(alias));
            if (suiteOpt.isPresent()) {
                Suite s = suiteOpt.get();
                if (s.getBranch() != null && branchRepository != null) {
                    Optional<Branch> br = branchRepository.findById(s.getBranch())
                            .or(() -> branchRepository.findBySlug(s.getBranch().toLowerCase()));
                    if (br.isPresent() && br.get().getLocation() != null
                            && isValidCoord(br.get().getLocation().getLat(), br.get().getLocation().getLng())) {
                        return new double[]{br.get().getLocation().getLat(), br.get().getLocation().getLng()};
                    }
                }
                if (s.getPropertyId() != null && propertyRepository != null) {
                    Optional<Property> pr = propertyRepository.findById(s.getPropertyId());
                    if (pr.isPresent()) {
                        return inferCartagenaCoordinates(pr.get().getAddress(), pr.get().getCity(), pr.get().getName());
                    }
                }
                return inferCartagenaCoordinates(s.getSlug(), null, s.getName());
            }
        }
        return inferCartagenaCoordinates(lower, null, lower);
    }

    private void addCuratedCartagenaLandmarks(Map<String, GeoPlaceDTO> target) {
        List<GeoPlaceDTO> landmarks = List.of(
                GeoPlaceDTO.builder().id("poi:plaza-santo-domingo").name("Plaza Santo Domingo").type("POI").category("plaza")
                        .zone("Centro Histórico").address("Plaza Santo Domingo, Ciudad Amurallada").lat(10.4231).lng(-75.5512).walkMinutes(3).build(),
                GeoPlaceDTO.builder().id("poi:torre-del-reloj").name("Torre del Reloj Monumental").type("POI").category("monument")
                        .zone("Centro Histórico").address("Boca del Puente, Centro Histórico").lat(10.4225).lng(-75.5492).walkMinutes(5).build(),
                GeoPlaceDTO.builder().id("poi:baluarte-santo-domingo").name("Baluarte de Santo Domingo · Atardecer").type("POI").category("dining")
                        .zone("Centro Histórico").address("Murallas de Cartagena").lat(10.4252).lng(-75.5540).walkMinutes(6).build(),
                GeoPlaceDTO.builder().id("poi:muelle-bodeguita").name("Muelle de la Bodeguita · Yates Islas del Rosario").type("EXPERIENCE_HUB").category("yacht")
                        .zone("Centro Histórico").address("Av. Blas de Lezo, Bahía de las Ánimas").lat(10.4209).lng(-75.5489).walkMinutes(7).build(),
                GeoPlaceDTO.builder().id("poi:plaza-trinidad").name("Plaza de la Trinidad").type("POI").category("plaza")
                        .zone("Getsemaní").address("Calle 29, Getsemaní").lat(10.4202).lng(-75.5461).walkMinutes(2).build(),
                GeoPlaceDTO.builder().id("poi:calle-arsenal").name("Calle del Arsenal · Gastronomía & Coctelería").type("POI").category("nightlife")
                        .zone("Getsemaní").address("Calle del Arsenal, Getsemaní").lat(10.4210).lng(-75.5470).walkMinutes(1).build(),
                GeoPlaceDTO.builder().id("poi:castillo-san-felipe").name("Castillo San Felipe de Barajas").type("POI").category("monument")
                        .zone("Pie del Cerro").address("Cra. 17, Cartagena").lat(10.4227).lng(-75.5393).build(),
                GeoPlaceDTO.builder().id("poi:playa-bocagrande").name("Playa VIP Bocagrande").type("POI").category("beach")
                        .zone("Bocagrande").address("Malecón Bahía, Bocagrande").lat(10.4045).lng(-75.5580).walkMinutes(2).build(),
                GeoPlaceDTO.builder().id("poi:plaza-bocagrande-mall").name("Plaza Bocagrande Luxury Shopping").type("POI").category("shopping")
                        .zone("Bocagrande").address("Cra. 1 #12-118, Bocagrande").lat(10.4080).lng(-75.5540).walkMinutes(5).build(),
                GeoPlaceDTO.builder().id("poi:playa-castillogrande").name("Playa Exclusiva Castillogrande").type("POI").category("beach")
                        .zone("Castillogrande").address("Paseo Peatonal Castillogrande").lat(10.3948).lng(-75.5522).build(),
                GeoPlaceDTO.builder().id("poi:marina-manga").name("Club Náutico & Marina de Manga").type("EXPERIENCE_HUB").category("yacht")
                        .zone("Manga").address("Paseo de la Bahía, Manga").lat(10.4132).lng(-75.5398).build(),
                GeoPlaceDTO.builder().id("poi:manglares-boquilla").name("Manglares Eco-Reserva La Boquilla").type("POI").category("nature")
                        .zone("La Boquilla").address("Vía al Mar, La Boquilla").lat(10.4735).lng(-75.5015).walkMinutes(5).build()
        );
        for (GeoPlaceDTO p : landmarks) {
            target.put(p.getId(), p);
        }
    }

    public static double[] inferCartagenaCoordinates(String address, String zone, String name) {
        String combined = ((address != null ? address : "") + " " + (zone != null ? zone : "") + " " + (name != null ? name : "")).toLowerCase();
        if (combined.contains("bocagrande") || combined.contains("laguito")) {
            return new double[]{10.4042, -75.5574};
        }
        if (combined.contains("castillogrande")) {
            return new double[]{10.3948, -75.5522};
        }
        if (combined.contains("getseman")) {
            return new double[]{10.4208, -75.5468};
        }
        if (combined.contains("boquilla") || combined.contains("manzanillo") || combined.contains("morros")) {
            return new double[]{10.4725, -75.5028};
        }
        if (combined.contains("manga")) {
            return new double[]{10.4132, -75.5398};
        }
        if (combined.contains("baru") || combined.contains("barú") || combined.contains("rosario")) {
            return new double[]{10.2165, -75.6114};
        }
        // Default: Centro Histórico / Ciudad Amurallada
        return new double[]{10.4236, -75.5515};
    }

    public static boolean isValidCoord(Double lat, Double lng) {
        return lat != null && lng != null
                && !Double.isNaN(lat) && !Double.isNaN(lng)
                && lat >= -85.05112878 && lat <= 85.05112878
                && lng >= -180.0 && lng <= 180.0;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String slugify(String input) {
        if (input == null) return "poi";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
