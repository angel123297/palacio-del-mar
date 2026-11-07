package co.palaciodelmar.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyDTO {
    private String id;
    private String propertyId;
    private String ownerId;
    private String name;
    private String keyPrefix;
    private String rawKey; // Solo se retorna al crearse
    private Boolean active;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;
}
