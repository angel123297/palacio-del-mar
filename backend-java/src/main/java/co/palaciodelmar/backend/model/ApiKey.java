package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "api_keys")
public class ApiKey {

    @Id
    private String id;

    @Indexed
    private String propertyId;

    @Indexed
    private String ownerId;

    private String name;

    private String keyPrefix; // ej: pdm_live_a1b2... (mostrable en UI)

    @Indexed(unique = true)
    private String hashedKey; // SHA-256(rawKey) para lookup seguro

    @Builder.Default
    private Boolean active = true;

    private Instant lastUsedAt;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant revokedAt;
}
