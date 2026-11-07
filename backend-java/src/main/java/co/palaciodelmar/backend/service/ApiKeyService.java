package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ApiKeyDTO;
import co.palaciodelmar.backend.dto.CreateApiKeyRequest;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.ApiKeyRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final PropertyRepository propertyRepository;

    public ApiKeyDTO createApiKey(User currentUser, CreateApiKeyRequest request) {
        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Alojamiento no encontrado"));

        if (!isAdmin(currentUser) && !property.getOwnerId().equals(currentUser.getId())) {
            // Anti-IDOR: Responder 404 para no revelar la existencia
            throw new ResourceNotFoundException("Alojamiento no encontrado");
        }

        String rawKey = generateRawKey();
        String hashedKey = hashKey(rawKey);
        String prefix = formatPrefix(rawKey);

        ApiKey apiKey = ApiKey.builder()
                .propertyId(property.getId())
                .ownerId(property.getOwnerId())
                .name(request.getName().trim())
                .keyPrefix(prefix)
                .hashedKey(hashedKey)
                .active(true)
                .createdAt(Instant.now())
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("API Key generada: {} para propiedad: {} por anfitrión: {}",
                saved.getId(), property.getId(), currentUser.getId());

        ApiKeyDTO dto = toDTO(saved);
        dto.setRawKey(rawKey); // Solo se muestra al momento de la creación
        return dto;
    }

    public List<ApiKeyDTO> listApiKeys(User currentUser, String propertyId) {
        if (propertyId != null && !propertyId.isBlank()) {
            Property property = propertyRepository.findById(propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Alojamiento no encontrado"));

            if (!isAdmin(currentUser) && !property.getOwnerId().equals(currentUser.getId())) {
                throw new ResourceNotFoundException("Alojamiento no encontrado");
            }
            return apiKeyRepository.findByPropertyId(propertyId).stream()
                    .map(this::toDTO)
                    .toList();
        }

        if (isAdmin(currentUser)) {
            return apiKeyRepository.findAll().stream()
                    .map(this::toDTO)
                    .toList();
        }

        return apiKeyRepository.findByOwnerId(currentUser.getId()).stream()
                .map(this::toDTO)
                .toList();
    }

    public void revokeApiKey(User currentUser, String apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new ResourceNotFoundException("API Key no encontrada"));

        if (!isAdmin(currentUser) && !apiKey.getOwnerId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("API Key no encontrada");
        }

        apiKey.setActive(false);
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);
        log.info("API Key revocada: {} por usuario: {}", apiKeyId, currentUser.getId());
    }

    public Optional<ApiKey> authenticateApiKey(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return Optional.empty();
        }

        String token = rawHeader.trim();
        if (token.startsWith("Bearer ") || token.startsWith("bearer ")) {
            token = token.substring(7).trim();
        }

        if (!token.startsWith("pdm_")) {
            return Optional.empty();
        }

        String hashedKey = hashKey(token);
        Optional<ApiKey> match = apiKeyRepository.findByHashedKeyAndActiveTrue(hashedKey);
        if (match.isPresent()) {
            ApiKey apiKey = match.get();
            apiKey.setLastUsedAt(Instant.now());
            apiKeyRepository.save(apiKey);
            return Optional.of(apiKey);
        }

        return Optional.empty();
    }

    public String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    private String generateRawKey() {
        return "pdm_live_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String formatPrefix(String rawKey) {
        if (rawKey.length() <= 16) return rawKey;
        return rawKey.substring(0, 14) + "..." + rawKey.substring(rawKey.length() - 4);
    }

    private boolean isAdmin(User user) {
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    public ApiKeyDTO toDTO(ApiKey key) {
        if (key == null) return null;
        return ApiKeyDTO.builder()
                .id(key.getId())
                .propertyId(key.getPropertyId())
                .ownerId(key.getOwnerId())
                .name(key.getName())
                .keyPrefix(key.getKeyPrefix())
                .active(key.getActive())
                .createdAt(key.getCreatedAt())
                .lastUsedAt(key.getLastUsedAt())
                .revokedAt(key.getRevokedAt())
                .build();
    }
}
