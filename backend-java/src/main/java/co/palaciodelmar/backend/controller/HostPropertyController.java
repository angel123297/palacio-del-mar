package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<PropertyDTO>>> getMyProperties(
            @AuthenticationPrincipal UserPrincipal principal) {
        String userId = principal.getUser().getId();
        List<PropertyDTO> properties = propertyService.getPropertiesByOwner(userId);
        return ResponseEntity.ok(ApiResponse.ok(properties));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PropertyDTO>> createProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PropertyRequest req) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        // Los admins auto-aprueban sus propiedades; anfitriones estándar quedan PENDING para revisión
        PropertyDTO created = propertyService.createProperty(userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Propiedad registrada exitosamente", created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyDTO>> getProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        PropertyDTO dto = propertyService.getPropertyByOwner(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyDTO>> updateProperty(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody PropertyRequest req) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        PropertyDTO updated = propertyService.updateProperty(id, userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Propiedad actualizada exitosamente", updated));
    }

    // --- Sub-recursos: Habitaciones de la Propiedad (Anti-IDOR) ---

    @GetMapping("/{propertyId}/suites")
    public ResponseEntity<ApiResponse<List<SuiteResponseDTO>>> getPropertySuites(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
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
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Suite created = propertyService.createSuiteForProperty(propertyId, userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Habitación creada exitosamente", SuiteResponseDTO.from(created, null)));
    }

    @PutMapping("/{propertyId}/suites/{suiteId}")
    public ResponseEntity<ApiResponse<SuiteResponseDTO>> updateSuite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @PathVariable String suiteId,
            @Valid @RequestBody HostSuiteRequest req) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        Suite updated = propertyService.updateSuiteForProperty(propertyId, suiteId, userId, req, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Habitación actualizada exitosamente", SuiteResponseDTO.from(updated, null)));
    }

    @DeleteMapping("/{propertyId}/suites/{suiteId}")
    public ResponseEntity<ApiResponse<Void>> deleteSuite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String propertyId,
            @PathVariable String suiteId) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        propertyService.deleteSuiteForProperty(propertyId, suiteId, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok("Habitación desactivada exitosamente", null));
    }

    // --- Liquidaciones Automáticas (Host Payouts) ---

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<HostPayoutDTO>>> getMyPayouts(
            @AuthenticationPrincipal UserPrincipal principal) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        List<HostPayoutDTO> payouts = isAdmin && hostPayoutService != null
                ? hostPayoutService.getPayoutsForAdmin()
                : (hostPayoutService != null ? hostPayoutService.getPayoutsForHost(userId) : List.of());
        return ResponseEntity.ok(ApiResponse.ok(payouts));
    }

    @PostMapping("/payouts/process-now")
    public ResponseEntity<ApiResponse<List<HostPayoutDTO>>> triggerPayoutProcess(
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            return ResponseEntity.status(403).body(ApiResponse.error("Solo administradores pueden forzar la liquidación inmediata"));
        }
        List<HostPayoutDTO> payouts = hostPayoutService != null ? hostPayoutService.processAutomatedPayouts() : List.of();
        return ResponseEntity.ok(ApiResponse.ok("Proceso de liquidación ejecutado exitosamente", payouts));
    }

    @PutMapping("/reviews/{reviewId}/resolve-maintenance")
    public ResponseEntity<ApiResponse<ReviewDTO>> resolveMaintenance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String reviewId) {
        String userId = principal.getUser().getId();
        ReviewDTO dto = reviewService != null ? reviewService.resolveMaintenanceAlert(reviewId, userId) : null;
        return ResponseEntity.ok(ApiResponse.ok("Alerta de mantenimiento marcada como resuelta", dto));
    }

    @GetMapping("/{id}/sync/ical")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getPropertyIcalSyncInfo(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id) {
        String userId = principal.getUser().getId();
        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        PropertyDTO property = propertyService.getPropertyByOwner(id, userId, isAdmin);

        java.util.Map<String, Object> syncInfo = new java.util.LinkedHashMap<>();
        syncInfo.put("propertyId", property.getId());
        syncInfo.put("propertyName", property.getName());
        syncInfo.put("propertyIcalUrl", "/api/properties/" + property.getId() + "/calendar.ics");

        List<Suite> suites = propertyService.getPropertySuitesForHost(id, userId, isAdmin);
        List<java.util.Map<String, String>> suiteFeeds = suites.stream().map(s -> java.util.Map.of(
                "suiteId", s.getId(),
                "suiteName", s.getName(),
                "icalUrl", "/api/suites/" + s.getId() + "/calendar.ics"
        )).toList();
        syncInfo.put("suiteFeeds", suiteFeeds);
        syncInfo.put("instructions", java.util.Map.of(
                "airbnb", "En Airbnb: Anuncios > Precios y disponibilidad > Sincronización del calendario > Exportar calendario.",
                "booking", "En Booking.com Extranet: Calendario > Sincronizar calendarios > Añadir conexión y pega el enlace del feed."
        ));

        return ResponseEntity.ok(ApiResponse.ok(syncInfo));
    }
}
