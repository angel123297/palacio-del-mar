package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationAvailabilityRequest {

    @NotBlank(message = "El externalId de la habitación es obligatorio")
    private String externalId;

    @NotNull(message = "La fecha de inicio (check-in) es obligatoria")
    private LocalDate startDate;

    @NotNull(message = "La fecha de fin (check-out) es obligatoria")
    private LocalDate endDate;

    @Builder.Default
    private String action = "BLOCK"; // "BLOCK" o "UNBLOCK"

    @Builder.Default
    private Integer unitSlot = 1;

    private String reason; // ej: "Bloqueo por reserva externa en Booking.com"
}
