package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BlockNightsRequest;
import co.palaciodelmar.backend.dto.HostSuiteRequest;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.dto.PropertyRequest;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.PropertyService;
import jakarta.validation.Valid;
import co.palaciodelmar.backend.dto.HostPayoutDTO;
import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.service.HostPayoutService;
import co.palaciodelmar.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/properties")
@RequiredArgsConstructor
public class HostPropertyController {

    private final PropertyService propertyService;

    @Autowired(required = false)
    private HostPayoutService hostPayoutService;

    @Autowired(required = false)
    private ReviewService reviewService;

    private String resolveUserId(UserPrincipal principal) {
        if (principal == null) return null;
        if (principal.getUser() != null && principal.getUser().getId() != null) {
            return principal.getUser().getId();
        }
        return principal.getUsername();
    }

    private boolean resolveIsAdmin(UserPrincipal principal) {
        if (principal == null || principal.getAuthorities() == null) return false;
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PropertyDTO>>> getMyProperties(
            @AuthenticationPrincipal UserPrincipal principal) {
        String userId = resolveUserId(principal);
        List<PropertyDTO> properties = propertyService.getPropertiesByOwner(userId);
        return ResponseEntity.ok(ApiResponse.ok(properties));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PropertyDTO>> createProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PropertyRequest req) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        // Los admins auto-aprueban sus propiedades; anfitriones estándar quedan PENDING para revisión
        PropertyDTO created = propertyService.createProperty(userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Propiedad registrada exitosamente", created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyDTO>> getProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        PropertyDTO dto = propertyService.getPropertyByOwner(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyDTO>> updateProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody PropertyRequest req) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        PropertyDTO updated = propertyService.updateProperty(id, userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Propiedad actualizada exitosamente", updated));
    }

    // --- Sub-recursos: Habitaciones de la Propiedad (Anti-IDOR) ---

    @GetMapping("/{propertyId}/suites")
    public ResponseEntity<ApiResponse<List<SuiteResponseDTO>>> getPropertySuites(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        List<Suite> suites = propertyService.getPropertySuitesForHost(propertyId, userId, isAdmin);
        List<SuiteResponseDTO> dtos = suites.stream()
                .map(s -> SuiteResponseDTO.from(s, null))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PostMapping("/{propertyId}/suites")
    public ResponseEntity<ApiResponse<SuiteResponseDTO>> createSuite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @Valid @RequestBody HostSuiteRequest req) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        Suite created = propertyService.createSuiteForProperty(propertyId, userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Habitación creada exitosamente", SuiteResponseDTO.from(created, null)));
    }

    @PutMapping("/{propertyId}/suites/{suiteId}")
    public ResponseEntity<ApiResponse<SuiteResponseDTO>> updateSuite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @PathVariable String suiteId,
            @Valid @RequestBody HostSuiteRequest req) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        Suite updated = propertyService.updateSuiteForProperty(propertyId, suiteId, userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Habitación actualizada exitosamente", SuiteResponseDTO.from(updated, null)));
    }

    @DeleteMapping("/{propertyId}/suites/{suiteId}")
    public ResponseEntity<ApiResponse<Void>> deleteSuite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @PathVariable String suiteId) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        propertyService.deleteSuiteForProperty(propertyId, suiteId, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Habitación desactivada exitosamente", null));
    }

    // --- Bloqueo / Desbloqueo de Noches por Mantenimiento ---

    @PostMapping("/{propertyId}/suites/{suiteId}/block-nights")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> blockSuiteNights(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @PathVariable String suiteId,
            @Valid @RequestBody BlockNightsRequest req) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        java.util.Map<String, Object> result = propertyService.blockSuiteNightsForMaintenance(
                propertyId, suiteId, userId, req.getStartDate(), req.getEndDate(), req.getReason(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(result.get("message").toString(), result));
    }

    @PostMapping("/{propertyId}/suites/{suiteId}/unblock-nights")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> unblockSuiteNights(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @PathVariable String suiteId,
            @Valid @RequestBody BlockNightsRequest req) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        java.util.Map<String, Object> result = propertyService.unblockSuiteNights(
                propertyId, suiteId, userId, req.getStartDate(), req.getEndDate(), isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(result.get("message").toString(), result));
    }

    @GetMapping("/{propertyId}/suites/{suiteId}/blocked-nights")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getBlockedSuiteNights(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @PathVariable String suiteId) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        List<java.util.Map<String, Object>> result = propertyService.getBlockedSuiteNights(
                propertyId, suiteId, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // --- Liquidaciones Automáticas (Host Payouts) ---

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<HostPayoutDTO>>> getMyPayouts(
            @AuthenticationPrincipal UserPrincipal principal) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        List<HostPayoutDTO> payouts = isAdmin && hostPayoutService != null
                ? hostPayoutService.getPayoutsForAdmin()
                : (hostPayoutService != null ? hostPayoutService.getPayoutsForHost(userId) : List.of());
        return ResponseEntity.ok(ApiResponse.ok(payouts));
    }

    @PostMapping("/payouts/process-now")
    public ResponseEntity<ApiResponse<List<HostPayoutDTO>>> triggerPayoutProcess(
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = resolveIsAdmin(principal);
        if (!isAdmin) {
            return ResponseEntity.status(403).body(ApiResponse.error("Solo administradores pueden forzar la liquidación inmediata"));
        }
        List<HostPayoutDTO> payouts = hostPayoutService != null ? hostPayoutService.processAutomatedPayouts() : List.of();
        return ResponseEntity.ok(ApiResponse.ok("Proceso de liquidación ejecutado exitosamente", payouts));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDTO>>> getHostReviews(
            @AuthenticationPrincipal UserPrincipal principal) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        List<PropertyDTO> properties = isAdmin ? propertyService.getAllPropertiesForAdmin(null) : propertyService.getPropertiesByOwner(userId);
        List<ReviewDTO> hostReviews = new java.util.ArrayList<>();
        if (reviewService != null && properties != null) {
            for (PropertyDTO prop : properties) {
                String pId = prop.getId() != null ? prop.getId() : prop.get_id();
                List<Suite> suites = propertyService.getPropertySuitesForHost(pId, userId, isAdmin);
                if (suites != null) {
                    for (Suite suite : suites) {
                        List<ReviewDTO> suiteReviews = reviewService.getSuiteReviews(suite.getId());
                        if (suiteReviews != null) {
                            hostReviews.addAll(suiteReviews);
                        }
                    }
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(hostReviews));
    }

    @PutMapping("/reviews/{reviewId}/resolve-maintenance")
    public ResponseEntity<ApiResponse<ReviewDTO>> resolveMaintenance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String reviewId) {
        String userId = resolveUserId(principal);
        ReviewDTO dto = reviewService != null ? reviewService.resolveMaintenanceAlert(reviewId, userId) : null;
        return ResponseEntity.ok(ApiResponse.ok("Alerta de mantenimiento marcada como resuelta", dto));
    }

    @PutMapping("/reviews/{reviewId}/response")
    public ResponseEntity<ApiResponse<ReviewDTO>> respondToReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String reviewId,
            @RequestBody java.util.Map<String, String> body) {
        String userId = resolveUserId(principal);
        String responseText = body != null ? body.get("response") : null;
        ReviewDTO dto = reviewService != null ? reviewService.respondToReview(reviewId, userId, responseText) : null;
        return ResponseEntity.ok(ApiResponse.ok("Respuesta del anfitrión publicada exitosamente", dto));
    }

    @GetMapping("/{id}/sync/ical")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getPropertyIcalSyncInfo(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        String userId = resolveUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        PropertyDTO property = propertyService.getPropertyByOwner(id, userId, isAdmin);

        java.util.Map<String, Object> syncInfo = new java.util.LinkedHashMap<>();
        syncInfo.put("propertyId", property.getId());
        syncInfo.put("propertyName", property.getName());
        syncInfo.put("propertyIcalUrl", "/api/properties/" + property.getId() + "/calendar.ics");

        List<Suite> suites = propertyService.getPropertySuitesForHost(id, userId, isAdmin);
        List<java.util.Map<String, String>> suiteFeeds = suites.stream().map(s -> {
            String suiteId = s.getId() != null ? s.getId().trim() : "";
            String icalUrl = suiteId.isEmpty() ? "" : "/api/suites/" + suiteId + "/calendar.ics";
            return java.util.Map.of(
                    "suiteId", suiteId,
                    "suiteName", s.getName() != null ? s.getName() : "Suite",
                    "icalUrl", icalUrl
            );
        }).toList();
        syncInfo.put("suiteFeeds", suiteFeeds);
        syncInfo.put("instructions", java.util.Map.of(
                "airbnb", "En Airbnb: Anuncios > Precios y disponibilidad > Sincronización del calendario > Exportar calendario.",
                "booking", "En Booking.com Extranet: Calendario > Sincronizar calendarios > Añadir conexión y pega el enlace del feed."
        ));

        return ResponseEntity.ok(ApiResponse.ok(syncInfo));
    }
}
