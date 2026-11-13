package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.service.PropertyService;
import co.palaciodelmar.backend.service.ICalService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;

    @Autowired(required = false)
    private ICalService icalService;

    public void setIcalService(ICalService icalService) {
        this.icalService = icalService;
    }

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

    @GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getPropertyCalendarIcs(@PathVariable String id) {
        if (icalService == null) {
            return ResponseEntity.status(503).body("Servicio de calendario no disponible");
        }
        String ics = icalService.generatePropertyCalendarIcs(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"property-" + id + ".ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(ics);
    }

    @GetMapping(value = "/{id}/suites/{suiteId}/calendar.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> getPropertySuiteCalendarIcs(@PathVariable String id, @PathVariable String suiteId) {
        if (icalService == null) {
            return ResponseEntity.status(503).body("Servicio de calendario no disponible");
        }
        String ics = icalService.generateSuiteCalendarIcs(id, suiteId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"suite-" + suiteId + ".ics\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(ics);
    }
}
