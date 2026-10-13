package co.palaciodelmar.backend.dto;

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
public class PaymentDTO {
    private String id;
    private String bookingId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String provider;
    private String transactionId;
    private String paymentMethod;
    private String notes;
    private Instant createdAt;
}
