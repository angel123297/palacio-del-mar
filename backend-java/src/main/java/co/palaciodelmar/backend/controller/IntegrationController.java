package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.IntegrationAvailabilityRequest;
import co.palaciodelmar.backend.dto.IntegrationRoomRequest;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.service.ApiKeyService;
import co.palaciodelmar.backend.service.IntegrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final ApiKeyService apiKeyService;
    private final IntegrationService integrationService;

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<SuiteResponseDTO>> upsertRoom(
            @RequestHeader(value = "X-Palacio-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "X-API-KEY", required = false) String xApiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody IntegrationRoomRequest request
    ) {
        ApiKey apiKey = authenticate(apiKeyHeader, xApiKeyHeader, authHeader);
        Suite suite = integrationService.upsertRoom(apiKey, request);
        return ResponseEntity.ok(ApiResponse.success(SuiteResponseDTO.from(suite, null)));
    }

    @PutMapping("/availability")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manageAvailability(
            @RequestHeader(value = "X-Palacio-Api-Key", required = false) String apiKeyHeader,
            @RequestHeader(value = "X-API-KEY", required = false) String xApiKeyHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody IntegrationAvailabilityRequest request
    ) {
        ApiKey apiKey = authenticate(apiKeyHeader, xApiKeyHeader, authHeader);
        Map<String, Object> result = integrationService.manageAvailability(apiKey, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    private ApiKey authenticate(String... tokens) {
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                return apiKeyService.authenticateApiKey(token)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key inválida o revocada"));
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API Key inválida o revocada");
    }
}
