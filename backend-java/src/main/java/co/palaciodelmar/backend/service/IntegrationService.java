package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.IntegrationAvailabilityRequest;
import co.palaciodelmar.backend.dto.IntegrationRoomRequest;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final SuiteRepository suiteRepository;
    private final SuiteNightRepository suiteNightRepository;

    public Suite upsertRoom(ApiKey apiKey, IntegrationRoomRequest request) {
        String propertyId = apiKey.getPropertyId();
        String externalId = request.getExternalId().trim();

        Optional<Suite> existingOpt = suiteRepository.findByPropertyIdAndExternalId(propertyId, externalId);

        Suite suite;
        if (existingOpt.isPresent()) {
            suite = existingOpt.get();
            log.info("Actualizando habitación externa existente {} para propiedad {}", externalId, propertyId);
            suite.setName(request.getName().trim());
            if (request.getType() != null) suite.setType(request.getType());
            suite.setBasePrice(request.getBasePrice());
            if (request.getMaxGuests() != null) suite.setMaxGuests(request.getMaxGuests());
            if (request.getDescription() != null) suite.setDescription(request.getDescription());
            if (request.getMainImage() != null) suite.setMainImage(request.getMainImage());
            if (request.getAmenities() != null) suite.setAmenities(request.getAmenities());
            if (request.getTotalUnits() != null) suite.setTotalUnits(request.getTotalUnits());
            if (request.getActive() != null) suite.setActive(request.getActive());
            suite.setUpdatedAt(Instant.now());
        } else {
            log.info("Creando nueva habitación externa {} para propiedad {}", externalId, propertyId);
            String slug = generateSlug(request.getName().trim());

            suite = Suite.builder()
                    .propertyId(propertyId)
                    .branch("branch-1") // Compatibilidad con consultas monohotel
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

        return suiteRepository.save(suite);
    }

    public Map<String, Object> manageAvailability(ApiKey apiKey, IntegrationAvailabilityRequest request) {
        String propertyId = apiKey.getPropertyId();
        String externalId = request.getExternalId().trim();

        Suite suite = suiteRepository.findByPropertyIdAndExternalId(propertyId, externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación con externalId '" + externalId + "' no encontrada en esta propiedad"));

        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        if (end.isBefore(start) || end.isEqual(start)) {
            throw new IllegalArgumentException("La fecha de fin (check-out) debe ser estrictamente posterior a la fecha de inicio");
        }

        long nights = ChronoUnit.DAYS.between(start, end);
        int slot = request.getUnitSlot() != null && request.getUnitSlot() > 0 ? request.getUnitSlot() : 1;
        String action = request.getAction() != null ? request.getAction().toUpperCase() : "BLOCK";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("propertyId", propertyId);
        response.put("externalId", externalId);
        response.put("suiteId", suite.getId());
        response.put("unitSlot", slot);
        response.put("startDate", start.toString());
        response.put("endDate", end.toString());
        response.put("action", action);

        if ("BLOCK".equals(action)) {
            List<SuiteNight> occupied = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), start, end.minusDays(1));

            // Verificar si hay reservas nativas o bloqueos conflictivos en ese slot
            for (SuiteNight sn : occupied) {
                if (sn.getUnitSlot() != null && sn.getUnitSlot() == slot) {
                    if (sn.getBooking() != null && !sn.getBooking().startsWith("EXT_BLOCK_")) {
                        throw new IllegalStateException("Conflicto: La fecha " + sn.getDates() + " ya tiene una reserva activa (" + sn.getBooking() + ")");
                    }
                }
            }

            String blockRef = "EXT_BLOCK_" + UUID.randomUUID().toString().substring(0, 8);
            List<SuiteNight> inserted = new ArrayList<>();
            try {
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
                        inserted.add(suiteNightRepository.save(sn));
                    }
                }
            } catch (DuplicateKeyException e) {
                suiteNightRepository.deleteAll(inserted);
                throw new IllegalStateException("Colisión de concurrencia al bloquear fechas. Intente nuevamente.");
            }

            response.put("status", "SUCCESS");
            response.put("nightsBlocked", nights);
            response.put("recordsCreated", inserted.size());
            response.put("message", "Fechas bloqueadas exitosamente. Las reservas nativas quedan protegidas contra overbooking.");
            log.info("Bloqueadas {} noches para suite {} desde integración externa ({})", nights, suite.getId(), externalId);
            return response;

        } else if ("UNBLOCK".equals(action)) {
            List<SuiteNight> occupied = suiteNightRepository.findBySuiteAndDatesBetween(suite.getId(), start, end.minusDays(1));
            List<SuiteNight> toRemove = occupied.stream()
                    .filter(sn -> sn.getUnitSlot() != null && sn.getUnitSlot() == slot)
                    .filter(sn -> "blocked".equalsIgnoreCase(sn.getStatus()))
                    .toList();

            suiteNightRepository.deleteAll(toRemove);

            response.put("status", "SUCCESS");
            response.put("recordsRemoved", toRemove.size());
            response.put("message", "Bloqueos liberados. Las fechas vuelven a estar disponibles para reserva.");
            log.info("Desbloqueadas {} noches para suite {} desde integración externa", toRemove.size(), suite.getId());
            return response;

        } else {
            throw new IllegalArgumentException("Acción no válida: " + action + ". Use 'BLOCK' o 'UNBLOCK'.");
        }
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
