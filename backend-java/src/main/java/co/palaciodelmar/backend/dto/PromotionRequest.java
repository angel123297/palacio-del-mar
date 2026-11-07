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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {
    @NotBlank(message = "El título de la promoción es obligatorio")
    private String title;

    private String branchId;
    private String branch;

    private LocalDate startDate;
    private LocalDate endDate;

    @NotNull(message = "El porcentaje de descuento es obligatorio")
    @Min(value = 1, message = "El descuento mínimo es 1%")
    @Max(value = 100, message = "El descuento máximo es 100%")
    private Integer discountPercent;

    private String code;

    public String resolveBranch() {
        if (branchId != null && !branchId.isBlank()) {
            return branchId;
        }
        return branch;
    }
}
