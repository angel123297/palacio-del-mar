package co.palaciodelmar.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class BlockNightsRequest {
    @NotNull(message = "La fecha de inicio es requerida")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "La fecha de fin es requerida")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String reason;
}
