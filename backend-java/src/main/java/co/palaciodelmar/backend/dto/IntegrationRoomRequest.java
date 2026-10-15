package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationRoomRequest {

    @NotBlank(message = "El externalId de la habitación en tu sistema es obligatorio")
    private String externalId;

    @NotBlank(message = "El nombre de la habitación es obligatorio")
    private String name;

    private String type; // ej: "Suite Presidencial", "Habitación Deluxe"

    @NotNull(message = "El precio base por noche es obligatorio")
    @DecimalMin(value = "10000.0", message = "El precio base debe ser mayor o igual a 10,000")
    private BigDecimal basePrice;

    @Builder.Default
    private Integer maxGuests = 2;

    private String description;

    private String mainImage;

    private List<String> amenities;

    @Builder.Default
    private Integer totalUnits = 1;

    @Builder.Default
    private Boolean active = true;
}
