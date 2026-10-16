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
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/properties")
@RequiredArgsConstructor
public class HostPropertyController {

    private final PropertyService propertyService;

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
}
