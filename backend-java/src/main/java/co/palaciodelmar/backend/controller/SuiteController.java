package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.dto.SocialProofDTO;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.dto.SuiteTypeDTO;
import co.palaciodelmar.backend.security.RateLimitFilter;
import co.palaciodelmar.backend.service.ICalService;
import co.palaciodelmar.backend.service.SocialProofService;
import co.palaciodelmar.backend.service.SuiteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suites")
@RequiredArgsConstructor
public class SuiteController {

    private final SuiteService suiteService;

    @Autowired(required = false)
    private ICalService icalService;

    @Autowired(required = false)
    private SocialProofService socialProofService;

    @Autowired(required = false)
    private RateLimitFilter rateLimitFilter;

    public void setIcalService(ICalService icalService) {
        this.icalService = icalService;
    }

    public void setSocialProofService(SocialProofService socialProofService) {
        this.socialProofService = socialProofService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SuiteResponseDTO>>> getAllSuites(
            @RequestParam(required = false) String branch) {
        List<SuiteResponseDTO> suites = suiteService.getAllActiveSuites(branch);
        return ResponseEntity.ok(ApiResponse.ok(suites));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<SuiteTypeDTO>>> getSuiteTypes() {
        List<SuiteTypeDTO> types = suiteService.getSuiteTypes();
        return ResponseEntity.ok(ApiResponse.ok(types));
    }

    @PostMapping("/calculate-price")
    public ResponseEntity<ApiResponse<CalculatePriceResponseDTO>> calculatePrice(
            @RequestBody CalculatePriceRequest request) {
        CalculatePriceResponseDTO result = suiteService.calculatePrice(request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<SuiteResponseDTO>>> getSuitesByBranch(@PathVariable String branchId) {
        List<SuiteResponseDTO> suites = suiteService.getSuitesByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.ok(suites));
    }

    @GetMapping("/{idOrSlug}")
    public ResponseEntity<ApiResponse<SuiteResponseDTO>> getSuiteByIdOrSlug(@PathVariable String idOrSlug) {
        SuiteResponseDTO suite = suiteService.getSuiteByIdOrSlug(idOrSlug);
        return ResponseEntity.ok(ApiResponse.ok(suite));
    }

    @PostMapping("/{idOrSlug}/viewers/ping")
    public ResponseEntity<ApiResponse<SocialProofDTO>> pingSuiteViewer(
            @PathVariable String idOrSlug,
            @RequestHeader(value = "X-Viewer-Session", required = false) String headerSession,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request
    ) {
        if (socialProofService == null) {
            return ResponseEntity.ok(ApiResponse.ok(SocialProofDTO.builder()
                    .suiteKey(idOrSlug)
                    .activeViewers(1)
                    .bookingsLast24h(0)
                    .highDemand(false)
                    .build()));
        }
        String clientIp = rateLimitFilter != null
                ? rateLimitFilter.resolveClientIp(request)
                : (request != null ? request.getRemoteAddr() : "unknown");
        String sessionId = (headerSession != null && !headerSession.isBlank())
                ? headerSession
                : (body != null ? body.get("sessionId") : null);
        SocialProofDTO snapshot = socialProofService.recordViewerPing(idOrSlug, clientIp, sessionId);
        return ResponseEntity.ok(ApiResponse.ok(snapshot));
    }

    @GetMapping("/{idOrSlug}/social-proof")
    public ResponseEntity<ApiResponse<SocialProofDTO>> getSuiteSocialProof(@PathVariable String idOrSlug) {
        if (socialProofService == null) {
            return ResponseEntity.ok(ApiResponse.ok(SocialProofDTO.builder()
                    .suiteKey(idOrSlug)
                    .activeViewers(0)
                    .bookingsLast24h(0)
                    .highDemand(false)
                    .build()));
        }
        return ResponseEntity.ok(ApiResponse.ok(socialProofService.getSocialProof(idOrSlug)));
    }

    @GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getSuiteCalendarIcs(@PathVariable String id) {
        if (icalService == null) {
            return ResponseEntity.status(503).body("Servicio de calendario no disponible");
        }
        String ics = icalService.generateSuiteCalendarIcs(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"suite-" + id + ".ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(ics);
    }
}
