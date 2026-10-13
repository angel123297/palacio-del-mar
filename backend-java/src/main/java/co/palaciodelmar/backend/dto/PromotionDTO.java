package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionDTO {
    private String id;
    private String branch;
    private String title;
    private Integer discountPercent;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;
    private Boolean isSample;
    private Instant createdAt;
    private Instant updatedAt;
}
