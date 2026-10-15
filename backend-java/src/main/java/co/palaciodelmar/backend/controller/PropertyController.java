package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.service.PropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PropertyDTO>>> getApprovedProperties(
            @RequestParam(required = false) String city) {
        List<PropertyDTO> properties = propertyService.getApprovedProperties(city);
        return ResponseEntity.ok(ApiResponse.ok(properties));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyDTO>> getPropertyById(@PathVariable String id) {
        PropertyDTO property = propertyService.getApprovedPropertyById(id);
        return ResponseEntity.ok(ApiResponse.ok(property));
    }

    @GetMapping("/{id}/suites")
    public ResponseEntity<ApiResponse<List<Suite>>> getPropertySuites(@PathVariable String id) {
        List<Suite> suites = propertyService.getApprovedPropertySuites(id);
        return ResponseEntity.ok(ApiResponse.ok(suites));
    }
}
