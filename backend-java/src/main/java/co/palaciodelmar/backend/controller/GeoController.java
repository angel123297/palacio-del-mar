package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.GeoPlaceDTO;
import co.palaciodelmar.backend.service.GeoSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

    private final GeoSearchService geoSearchService;

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<GeoPlaceDTO>>> findNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "3.0") double radiusKm,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "12") int limit
    ) {
        if (radiusKm <= 0 || radiusKm > 100) {
            throw new IllegalArgumentException("El radio de búsqueda debe estar entre 0.1 y 100 km");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("El límite debe estar entre 1 y 100");
        }
        List<GeoPlaceDTO> places = geoSearchService.findNearby(lat, lng, radiusKm, type, limit);
        return ResponseEntity.ok(ApiResponse.ok(places));
    }

    @GetMapping("/suites/{idOrSlug}/nearby")
    public ResponseEntity<ApiResponse<List<GeoPlaceDTO>>> findNearbySuite(
            @PathVariable String idOrSlug,
            @RequestParam(defaultValue = "3.0") double radiusKm,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "8") int limit
    ) {
        if (radiusKm <= 0 || radiusKm > 100) {
            throw new IllegalArgumentException("El radio de búsqueda debe estar entre 0.1 y 100 km");
        }
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("El límite debe estar entre 1 y 100");
        }
        List<GeoPlaceDTO> places = geoSearchService.findNearbyForSuite(idOrSlug, radiusKm, type, limit);
        return ResponseEntity.ok(ApiResponse.ok(places));
    }

    @GetMapping("/distance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDistance(
            @RequestParam String from,
            @RequestParam String to
    ) {
        Double distKm = geoSearchService.distanceBetweenKm(from, to);
        if (distKm == null) {
            throw new IllegalArgumentException("No se encontraron una o ambas ubicaciones especificadas");
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "from", from,
                "to", to,
                "distanceKm", distKm
        )));
    }
}
