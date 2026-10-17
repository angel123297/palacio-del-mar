package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class HostSuiteRequest {

    @NotBlank(message = "El nombre de la habitación es obligatorio")
    private String name;

    private String type; // "Habitación", "Suite Deluxe", etc.
    private String description;
    private String shortDescription;

    @NotNull(message = "El precio base es obligatorio")
    @Positive(message = "El precio base debe ser mayor a 0")
    private BigDecimal basePrice;

    private BigDecimal originalPrice;
    private String mainImage;
    private List<String> images;

    @Builder.Default
    private Integer maxGuests = 2;

    @Builder.Default
    private Integer size = 30;

    private List<String> amenities;

    @Builder.Default
    private Integer totalUnits = 1;

    @Builder.Default
    private Boolean active = true;
}
