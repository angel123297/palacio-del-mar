package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.dto.SuiteTypeDTO;
import co.palaciodelmar.backend.service.SuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suites")
@RequiredArgsConstructor
public class SuiteController {

    private final SuiteService suiteService;

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
}
