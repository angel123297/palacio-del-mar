package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.IntegrationAvailabilityRequest;
import co.palaciodelmar.backend.dto.IntegrationRoomRequest;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.service.ApiKeyService;
import co.palaciodelmar.backend.service.IntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntegrationControllerTest {

    private ApiKeyService apiKeyService;
    private IntegrationService integrationService;
    private IntegrationController integrationController;

    private ApiKey sampleApiKey;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        integrationService = mock(IntegrationService.class);
        integrationController = new IntegrationController(apiKeyService, integrationService);

        sampleApiKey = ApiKey.builder()
                .id("key-1")
                .propertyId("prop-1")
                .ownerId("host-1")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("upsertRoom autentica correctamente con cabecera X-API-KEY (documentada en README)")
    void upsertRoomWithXApiKeyHeader() {
        when(apiKeyService.authenticateApiKey("pdm_secret_test")).thenReturn(Optional.of(sampleApiKey));
        Suite mockSuite = Suite.builder().id("suite-1").name("Suite Deluxe").basePrice(BigDecimal.valueOf(100000)).build();
        when(integrationService.upsertRoom(any(), any())).thenReturn(mockSuite);

        IntegrationRoomRequest req = IntegrationRoomRequest.builder()
                .externalId("ext-room-101")
                .name("Suite Deluxe")
                .basePrice(BigDecimal.valueOf(100000))
                .build();

        ResponseEntity<ApiResponse<SuiteResponseDTO>> response =
                integrationController.upsertRoom(null, "pdm_secret_test", null, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
    }

    @Test
    @DisplayName("upsertRoom autentica correctamente con cabecera legacy X-Palacio-Api-Key")
    void upsertRoomWithXPalacioApiKeyHeader() {
        when(apiKeyService.authenticateApiKey("pdm_legacy_key")).thenReturn(Optional.of(sampleApiKey));
        Suite mockSuite = Suite.builder().id("suite-2").name("Suite Standalone").basePrice(BigDecimal.valueOf(150000)).build();
        when(integrationService.upsertRoom(any(), any())).thenReturn(mockSuite);

        IntegrationRoomRequest req = IntegrationRoomRequest.builder()
                .externalId("ext-room-102")
                .name("Suite Standalone")
                .basePrice(BigDecimal.valueOf(150000))
                .build();

        ResponseEntity<ApiResponse<SuiteResponseDTO>> response =
                integrationController.upsertRoom("pdm_legacy_key", null, null, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData());
    }

    @Test
    @DisplayName("manageAvailability rechaza solicitud sin cabeceras de API Key (401)")
    void manageAvailabilityWithoutApiKeyThrowsUnauthorized() {
        IntegrationAvailabilityRequest req = IntegrationAvailabilityRequest.builder()
                .externalId("ext-room-101")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(2))
                .action("BLOCK")
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                integrationController.manageAvailability(null, null, null, req)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
