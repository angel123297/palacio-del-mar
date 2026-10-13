package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String bookingId;
    private BigDecimal amount;
    private String paymentMethod; // "simulated", "credit_card", etc.
    private Boolean simulateFailure;
}
