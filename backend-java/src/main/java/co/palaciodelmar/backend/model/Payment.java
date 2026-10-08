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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    @Indexed
    private String booking; // Booking ObjectId reference

    @Indexed
    private String user; // User ObjectId reference

    private BigDecimal amount;
    private String currency; // e.g., "COP", "USD"

    @Indexed
    @Builder.Default
    private String status = "pending"; // "pending", "completed", "failed", "refunded"

    @Builder.Default
    private String provider = "simulated";

    private String transactionId;
    private String paymentMethod; // "credit_card", "pse", "cash", "simulated"

    private String notes;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
