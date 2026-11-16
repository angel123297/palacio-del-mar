package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.IntegrationAvailabilityRequest;
import co.palaciodelmar.backend.dto.IntegrationRoomRequest;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class IntegrationService {

    private final SuiteRepository suiteRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final PropertyRepository propertyRepository;
    private final Object[] localLocks;

    @Autowired(required = false)
    private AvailabilityCacheEvictor availabilityCacheEvictor;

    @Autowired(required = false)
    private RedisLockService redisLockService;

    @Autowired
    public IntegrationService(SuiteRepository suiteRepository,
                              SuiteNightRepository suiteNightRepository,
                              @Autowired(required = false) PropertyRepository propertyRepository) {
        this.suiteRepository = suiteRepository;
        this.suiteNightRepository = suiteNightRepository;
        this.propertyRepository = propertyRepository;
        this.localLocks = new Object[64];
        for (int i = 0; i < localLocks.length; i++) {
            localLocks[i] = new Object();
        }
    }

    public IntegrationService(SuiteRepository suiteRepository, SuiteNightRepository suiteNightRepository) {
        this(suiteRepository, suiteNightRepository, null);
    }

    private void validatePropertyActiveAndApproved(String propertyId) {
        if (propertyRepository != null && propertyId != null) {
            Property property = propertyRepository.findById(propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada para esta API key"));
            if (!Boolean.TRUE.equals(property.getActive()) || !"APPROVED".equalsIgnoreCase(property.getStatus())) {
                throw new IllegalStateException("La propiedad no está activa o aprobada para gestionar habitaciones o disponibilidad");
            }
        }
    }

    @Caching(evict = {
            @CacheEvict(value = "suites", allEntries = true),
            @CacheEvict(value = "suite_detail", allEntries = true),
            @CacheEvict(value = "suite_types", allEntries = true)
    })
    public Suite upsertRoom(ApiKey apiKey, IntegrationRoomRequest request) {
        if (request.getMaxGuests() != null && request.getMaxGuests() <= 0) {
            throw new IllegalArgumentException("La capacidad máxima de huéspedes (maxGuests) debe ser al menos 1");
        }
        if (request.getTotalUnits() != null && request.getTotalUnits() <= 0) {
            throw new IllegalArgumentException("El número de unidades físicas (totalUnits) debe ser al menos 1");
        }

        String propertyId = apiKey.getPropertyId();
        validatePropertyActiveAndApproved(propertyId);
        String externalId = request.getExternalId().trim();
        String lockKey = "integration:room:" + propertyId + ":" + externalId;

        return executeWithRoomLock(lockKey, () -> {
            Optional<Suite> existingOpt = suiteRepository.findByPropertyIdAndExternalId(propertyId, externalId);

            Suite suite;
            if (existingOpt.isPresent()) {
                Suite existing = existingOpt.get();
                if (existing.getId() != null) {
                    return executeWithRoomLock("suite:" + existing.getId(), () -> {
                        Suite fresh = suiteRepository.findById(existing.getId()).orElse(existing);
                        applyExistingRoomUpdate(fresh, propertyId, externalId, request);
                        Suite saved = suiteRepository.save(fresh);
                        if (availabilityCacheEvictor != null) {
                            availabilityCacheEvictor.evictAll();
                        }
                        return saved;
                    });
                }
                applyExistingRoomUpdate(existing, propertyId, externalId, request);
                suite = existing;
            } else {
                log.info("Creando nueva habitación externa {} para propiedad {}", externalId, propertyId);
                String slug = generateSlug(request.getName().trim());

                suite = Suite.builder()
                        .propertyId(propertyId)
                        .branch(propertyId)
                        .source("EXTERNAL")
                        .externalId(externalId)
                        .name(request.getName().trim())
                        .slug(slug)
                        .type(request.getType() != null ? request.getType() : "Habitación")
                        .basePrice(request.getBasePrice())
                        .maxGuests(request.getMaxGuests() != null ? request.getMaxGuests() : 2)
                        .description(request.getDescription())
                        .mainImage(request.getMainImage())
                        .amenities(request.getAmenities() != null ? request.getAmenities() : new ArrayList<>())
                        .totalUnits(request.getTotalUnits() != null ? request.getTotalUnits() : 1)
                        .active(request.getActive() != null ? request.getActive() : true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
            }

            Suite saved = suiteRepository.save(suite);
            if (availabilityCacheEvictor != null) {
                availabilityCacheEvictor.evictAll();
            }
            return saved;
        });
    }

    private void applyExistingRoomUpdate(Suite suite, String propertyId, String externalId, IntegrationRoomRequest request) {
        log.info("Actualizando habitación externa existente {} para propiedad {}", externalId, propertyId);
        suite.setName(request.getName().trim());
        if (request.getType() != null) suite.setType(request.getType());
        suite.setBasePrice(request.getBasePrice());
        if (request.getMaxGuests() != null) suite.setMaxGuests(request.getMaxGuests());
        if (request.getDescription() != null) suite.setDescription(request.getDescription());
        if (request.getMainImage() != null) suite.setMainImage(request.getMainImage());
        if (suite.getBranch() == null || suite.getBranch().isBlank() || "branch-1".equals(suite.getBranch())) {
            suite.setBranch(propertyId);
        }
        if (request.getAmenities() != null) suite.setAmenities(request.getAmenities());
        if (request.getTotalUnits() != null) {
            int currentUnits = suite.getTotalUnits() != null ? suite.getTotalUnits() : 1;
            int newUnits = request.getTotalUnits();
            if (newUnits < currentUnits && suite.getId() != null && suiteNightRepository != null) {
                LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Bogota"));
                Instant now = Instant.now();
                List<SuiteNight> futureNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), today, today.plusYears(5));
                boolean hasConflict = futureNights.stream()
                        .filter(sn -> !"cancelled".equalsIgnoreCase(sn.getStatus()))
                        .filter(sn -> sn.getExpiresAt() == null || sn.getExpiresAt().isAfter(now))
                        .anyMatch(sn -> sn.getUnitSlot() != null && sn.getUnitSlot() > newUnits);
                if (hasConflict) {
                    throw new IllegalStateException("No se puede reducir totalUnits a " + newUnits + " porque existen reservas o bloqueos activos en unidades superiores");
                }
            }
            suite.setTotalUnits(newUnits);
        }
        if (request.getActive() != null) suite.setActive(request.getActive());
        suite.setUpdatedAt(Instant.now());
    }

    private <T> T executeWithRoomLock(String lockKey, java.util.function.Supplier<T> action) {
        if (redisLockService != null) {
            return redisLockService.executeWithLock(lockKey, java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(2), action);
        }
        int index = Math.abs(lockKey.hashCode() % localLocks.length);
        synchronized (localLocks[index]) {
            return action.get();
        }
    }

    public Map<String, Object> manageAvailability(ApiKey apiKey, IntegrationAvailabilityRequest request) {
        String propertyId = apiKey.getPropertyId();
        validatePropertyActiveAndApproved(propertyId);
        String externalId = request.getExternalId().trim();

        Suite suite = suiteRepository.findByPropertyIdAndExternalId(propertyId, externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación con externalId '" + externalId + "' no encontrada en esta propiedad"));

        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        if (end.isBefore(start) || end.isEqual(start)) {
            throw new IllegalArgumentException("La fecha de fin (check-out) debe ser estrictamente posterior a la fecha de inicio");
        }

        long nights = ChronoUnit.DAYS.between(start, end);
        if (nights > 365) {
            throw new IllegalArgumentException("El rango de fechas no puede superar los 365 días");
        }
        int totalUnits = suite.getTotalUnits() != null && suite.getTotalUnits() > 0 ? suite.getTotalUnits() : 1;
        if (request.getUnitSlot() != null && (request.getUnitSlot() < 1 || request.getUnitSlot() > totalUnits)) {
            throw new IllegalArgumentException("El unitSlot (" + request.getUnitSlot() + ") debe estar entre 1 y el total de unidades de la habitación (" + totalUnits + ")");
        }
        int slot = request.getUnitSlot() != null ? request.getUnitSlot() : 1;
        String action = request.getAction() != null ? request.getAction().toUpperCase() : "BLOCK";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("propertyId", propertyId);
        response.put("externalId", externalId);
        response.put("suiteId", suite.getId());
        response.put("unitSlot", slot);
        response.put("startDate", start.toString());
        response.put("endDate", end.toString());
        response.put("action", action);

        if (!"BLOCK".equals(action) && !"UNBLOCK".equals(action)) {
            throw new IllegalArgumentException("Acción no válida: " + action + ". Use 'BLOCK' o 'UNBLOCK'.");
        }

        return executeWithRoomLock("suite:" + suite.getId(), () -> {
            if ("BLOCK".equals(action)) {
                Instant now = Instant.now();
                List<SuiteNight> rangeNights = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), start, end.minusDays(1));
                List<SuiteNight> expiredHeld = rangeNights.stream()
                        .filter(sn -> "held".equalsIgnoreCase(sn.getStatus()))
                        .filter(sn -> sn.getExpiresAt() != null && sn.getExpiresAt().isBefore(now))
                        .toList();
                if (!expiredHeld.isEmpty()) {
                    suiteNightRepository.deleteAll(expiredHeld);
                }

                List<SuiteNight> occupied = rangeNights.stream()
                        .filter(sn -> !"cancelled".equalsIgnoreCase(sn.getStatus()))
                        .filter(sn -> sn.getExpiresAt() == null || sn.getExpiresAt().isAfter(now))
                        .toList();

                // Verificar si hay reservas nativas o bloqueos conflictivos en ese slot
                for (SuiteNight sn : occupied) {
                    if (sn.getUnitSlot() != null && sn.getUnitSlot() == slot) {
                        if (sn.getBooking() != null && !sn.getBooking().startsWith("EXT_BLOCK_")) {
                            throw new IllegalStateException("Conflicto: La fecha " + sn.getDates() + " ya tiene una reserva activa (" + sn.getBooking() + ")");
                        }
                    }
                }

                String blockRef = "EXT_BLOCK_" + UUID.randomUUID().toString().substring(0, 8);
                List<SuiteNight> toInsert = new ArrayList<>();
                for (int i = 0; i < nights; i++) {
                    LocalDate date = start.plusDays(i);

                    // Verificar si ya está bloqueado en esa fecha para ese slot
                    boolean alreadyBlocked = occupied.stream().anyMatch(sn ->
                            sn.getUnitSlot() != null && sn.getUnitSlot() == slot &&
                            sn.getDates() != null && sn.getDates().contains(date) &&
                            "blocked".equalsIgnoreCase(sn.getStatus())
                    );

                    if (!alreadyBlocked) {
                        SuiteNight sn = SuiteNight.builder()
                                .suite(suite.getId())
                                .branch(suite.getBranch())
                                .unitSlot(slot)
                                .dates(Collections.singletonList(date))
                                .booking(blockRef)
                                .status("blocked")
                                .createdAt(Instant.now())
                                .build();
                        toInsert.add(sn);
                    }
                }

                List<SuiteNight> inserted = new ArrayList<>();
                try {
                    if (!toInsert.isEmpty()) {
                        inserted.addAll(suiteNightRepository.saveAll(toInsert));
                    }
                } catch (DuplicateKeyException e) {
                    suiteNightRepository.deleteAll(inserted);
                    throw new IllegalStateException("Colisión de concurrencia al bloquear fechas. Intente nuevamente.");
                }

                if (availabilityCacheEvictor != null) {
                    availabilityCacheEvictor.evictAvailability();
                }

                response.put("status", "SUCCESS");
                response.put("nightsBlocked", nights);
                response.put("recordsCreated", inserted.size());
                response.put("message", "Fechas bloqueadas exitosamente. Las reservas nativas quedan protegidas contra overbooking.");
                log.info("Bloqueadas {} noches para suite {} desde integración externa ({})", nights, suite.getId(), externalId);
                return response;

            } else {
                List<SuiteNight> occupied = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), start, end.minusDays(1));
                List<SuiteNight> toRemove = occupied.stream()
                        .filter(sn -> sn.getUnitSlot() != null && sn.getUnitSlot() == slot)
                        .filter(sn -> "blocked".equalsIgnoreCase(sn.getStatus()))
                        .filter(sn -> sn.getBooking() != null && sn.getBooking().startsWith("EXT_BLOCK_"))
                        .toList();

                suiteNightRepository.deleteAll(toRemove);
                if (availabilityCacheEvictor != null) {
                    availabilityCacheEvictor.evictAvailability();
                }

                response.put("status", "SUCCESS");
                response.put("recordsRemoved", toRemove.size());
                response.put("message", "Bloqueos liberados. Las fechas vuelven a estar disponibles para reserva.");
                log.info("Desbloqueadas {} noches para suite {} desde integración externa", toRemove.size(), suite.getId());
                return response;
            }
        });
    }

    private String generateSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        return base + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
