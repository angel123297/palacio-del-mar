package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ApiKeyDTO;
import co.palaciodelmar.backend.dto.CreateApiKeyRequest;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.ApiKeyRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    private ApiKeyRepository apiKeyRepository;
    private PropertyRepository propertyRepository;
    private ApiKeyService apiKeyService;

    private User host1;
    private User host2;
    private Property prop1;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        apiKeyService = new ApiKeyService(apiKeyRepository, propertyRepository);

        host1 = User.builder().id("host_01").role("host").build();
        host2 = User.builder().id("host_02").role("host").build();

        prop1 = Property.builder()
                .id("prop_01")
                .ownerId("host_01")
                .name("Hotel Cartagena")
                .build();
    }

    @Test
    void testCreateApiKeySuccessForOwner() {
        when(propertyRepository.findById("prop_01")).thenReturn(Optional.of(prop1));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> {
            ApiKey k = i.getArgument(0);
            k.setId("key_id_1");
            return k;
        });

        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .propertyId("prop_01")
                .name("Web PMS Propia")
                .build();

        ApiKeyDTO result = apiKeyService.createApiKey(host1, request);

        assertNotNull(result);
        assertEquals("prop_01", result.getPropertyId());
        assertEquals("host_01", result.getOwnerId());
        assertEquals("Web PMS Propia", result.getName());
        assertNotNull(result.getRawKey());
        assertTrue(result.getRawKey().startsWith("pdm_live_"));
        assertNotNull(result.getKeyPrefix());
        assertTrue(result.getKeyPrefix().startsWith("pdm_live_"));
    }

    @Test
    void testCreateApiKeyAntiIdorThrows404ForNonOwner() {
        when(propertyRepository.findById("prop_01")).thenReturn(Optional.of(prop1));

        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .propertyId("prop_01")
                .name("Malicious Key")
                .build();

        // Host 2 no es dueño de prop1
        assertThrows(ResourceNotFoundException.class, () ->
                apiKeyService.createApiKey(host2, request)
        );

        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void testAuthenticateValidApiKey() {
        String rawKey = "pdm_live_secret1234567890abcdef";
        String hashed = apiKeyService.hashKey(rawKey);

        ApiKey key = ApiKey.builder()
                .id("key_01")
                .propertyId("prop_01")
                .ownerId("host_01")
                .hashedKey(hashed)
                .active(true)
                .build();

        when(apiKeyRepository.findByHashedKeyAndActiveTrue(hashed)).thenReturn(Optional.of(key));

        Optional<ApiKey> result = apiKeyService.authenticateApiKey("Bearer " + rawKey);

        assertTrue(result.isPresent());
        assertEquals("prop_01", result.get().getPropertyId());
        assertNotNull(result.get().getLastUsedAt());
        verify(apiKeyRepository).save(key);
    }

    @Test
    void testAuthenticateInvalidOrRevokedApiKeyReturnsEmpty() {
        String rawKey = "pdm_live_invalid";
        String hashed = apiKeyService.hashKey(rawKey);

        when(apiKeyRepository.findByHashedKeyAndActiveTrue(hashed)).thenReturn(Optional.empty());

        Optional<ApiKey> result = apiKeyService.authenticateApiKey(rawKey);
        assertTrue(result.isEmpty());

        // Header vacío o malformado
        assertTrue(apiKeyService.authenticateApiKey("").isEmpty());
        assertTrue(apiKeyService.authenticateApiKey("invalid_prefix_token").isEmpty());
    }

    @Test
    void testRevokeApiKeyAntiIdor() {
        ApiKey key = ApiKey.builder()
                .id("key_01")
                .propertyId("prop_01")
                .ownerId("host_01")
                .active(true)
                .build();

        when(apiKeyRepository.findById("key_01")).thenReturn(Optional.of(key));

        // Host 2 no puede revocar key de Host 1
        assertThrows(ResourceNotFoundException.class, () ->
                apiKeyService.revokeApiKey(host2, "key_01")
        );

        assertTrue(key.getActive());
        verify(apiKeyRepository, never()).save(any());

        // Host 1 sí puede revocarla
        apiKeyService.revokeApiKey(host1, "key_01");
        assertFalse(key.getActive());
        assertNotNull(key.getRevokedAt());
        verify(apiKeyRepository).save(key);
    }
}
