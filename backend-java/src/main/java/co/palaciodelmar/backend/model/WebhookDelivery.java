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
@Document(collection = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    private String id;

    @Indexed
    private String subscriptionId;

    @Indexed
    private String propertyId;

    @Indexed
    private String ownerId;

    @Indexed
    private String eventId;

    private String eventType;

    private String targetUrl;

    private String payload;

    @Builder.Default
    private Integer attempts = 0;

    @Builder.Default
    private Integer maxAttempts = 3;

    @Indexed
    @Builder.Default
    private String status = "PENDING"; // PENDING, SUCCESS, FAILED

    private Integer responseCode;

    private String responseBody;

    private String errorMessage;

    @Indexed
    private Instant nextRetryAt;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant deliveredAt;
}
