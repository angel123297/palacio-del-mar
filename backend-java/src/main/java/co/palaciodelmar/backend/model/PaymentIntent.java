package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payment_intents")
public class PaymentIntent {

    @Id
    private String id; // e.g. "pi_..."

    private String clientSecret;

    private BigDecimal amount;

    @Builder.Default
    private String currency = "COP";

    @Indexed
    @Builder.Default
    private String status = "requires_payment_method"; // requires_payment_method, processing, succeeded, failed, refunded

    @Indexed
    private String bookingId;

    private String userId;

    @Builder.Default
    private String paymentMethod = "card";

    @Indexed(sparse = true)
    private String idempotencyKey;

    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    private String simulationOutcome;

    private String claimLock;

    private Instant claimExpiresAt;

    @Builder.Default
    private Boolean webhookDelivered = false;

    private String maskedPaymentToken;

    @org.springframework.data.annotation.Transient
    private String paymentToken;

    @Indexed(sparse = true)
    private String transactionId; // "ch_..."

    private String failureMessage;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @org.springframework.data.annotation.Version
    private Long version;

    @Builder.Default
    private List<RefundRecord> refundRecords = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRecord {
        private String refundId;
        private String idempotencyKey;
        private BigDecimal amount;
        private String status;
        private String webhookEventId;
        @Builder.Default
        private Boolean webhookDelivered = false;
        @Builder.Default
        private Instant createdAt = Instant.now();
    }
}
