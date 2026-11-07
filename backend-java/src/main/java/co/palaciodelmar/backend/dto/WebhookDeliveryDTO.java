package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDeliveryDTO {

    private String id;
    private String subscriptionId;
    private String propertyId;
    private String eventId;
    private String eventType;
    private String targetUrl;
    private String payload;
    private Integer attempts;
    private Integer maxAttempts;
    private String status;
    private Integer responseCode;
    private String responseBody;
    private String errorMessage;
    private Instant nextRetryAt;
    private Instant createdAt;
    private Instant deliveredAt;
}
