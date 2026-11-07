package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscriptionDTO {

    private String id;
    private String propertyId;
    private String ownerId;
    private String url;
    private String secret;
    private List<String> events;
    private Boolean active;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
