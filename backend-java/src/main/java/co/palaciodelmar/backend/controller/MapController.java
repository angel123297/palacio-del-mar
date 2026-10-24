package co.palaciodelmar.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maps")
@CrossOrigin(origins = "http://localhost:4200")
public class MapController {

    @Value("${carto.api.key}")
    private String cartoApiKey;

    @Value("${carto.base.url}")
    private String cartoBaseUrl;

    @GetMapping("/tiles/{z}/{x}/{y}")
    public ResponseEntity<byte[]> getCartoTile(@PathVariable String z, @PathVariable String x, @PathVariable String y) {
        RestTemplate restTemplate = new RestTemplate();
        String url = cartoBaseUrl.replace("{z}", z).replace("{x}", x).replace("{y}", y) + "?key=" + cartoApiKey;
        
        byte[] imageBytes = restTemplate.getForObject(url, byte[].class);
        return ResponseEntity.ok().header("Content-Type", "image/png").body(imageBytes);
    }

    @GetMapping("/sectors")
    public ResponseEntity<List<Map<String, Object>>> getHotelSectors() {
        List<Map<String, Object>> sectors = List.of(
            Map.of("name", "Centro Histórico", "lat", 10.4236, "lng", -75.5511, "hotelCount", 2),
            Map.of("name", "Getsemaní", "lat", 10.4215, "lng", -75.5444, "hotelCount", 1),
            Map.of("name", "Bocagrande", "lat", 10.3980, "lng", -75.5574, "hotelCount", 1),
            Map.of("name", "Manga", "lat", 10.4070, "lng", -75.5350, "hotelCount", 0)
        );
        return ResponseEntity.ok(sectors);
    }
}
