package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatePriceRequest {

    @NotBlank(message = "El suiteId es obligatorio")
    private String suiteId;

    @NotNull(message = "La fecha de check-in es obligatoria")
    private LocalDate checkIn;

    @NotNull(message = "La fecha de check-out es obligatoria")
    private LocalDate checkOut;

    @Min(value = 1, message = "Debe haber al menos 1 huésped")
    @Max(value = 50, message = "El número de huéspedes no puede superar 50")
    private Integer guests;

    @Min(value = 0, message = "El número de niños no puede ser negativo")
    @Max(value = 50, message = "El número de niños no puede superar 50")
    private Integer children;

    private Boolean includeExperiences;
    private List<String> experienceIds;

    private Boolean airportPickup;
    private Boolean petFriendly;
    private List<co.palaciodelmar.backend.model.Booking.FoodOrder> foodOrders;
}
