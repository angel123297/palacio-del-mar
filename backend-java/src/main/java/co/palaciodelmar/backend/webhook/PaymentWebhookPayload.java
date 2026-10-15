package co.palaciodelmar.backend.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentWebhookPayload {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private PaymentWebhookData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentWebhookData {
        private String intentId;
        private String bookingId;
        private BigDecimal amount;
        private String currency;
        private String status; // "succeeded", "failed", "refunded"
        private String transactionId;
        private String failureMessage;
    }
}
