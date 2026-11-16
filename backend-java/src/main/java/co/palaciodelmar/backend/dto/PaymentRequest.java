package co.palaciodelmar.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "El ID de reserva es obligatorio")
    private String bookingId;

    private BigDecimal amount;

    @JsonAlias({"method", "paymentMethod"})
    private String paymentMethod;

    private Boolean simulateFailure;

    public String getMethod() {
        return paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod : "card";
    }
}

