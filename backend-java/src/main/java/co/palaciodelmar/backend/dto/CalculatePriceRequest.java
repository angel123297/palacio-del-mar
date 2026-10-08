package co.palaciodelmar.backend.dto;

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
    private String suiteId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Boolean includeExperiences;
    private List<String> experienceIds;
}
