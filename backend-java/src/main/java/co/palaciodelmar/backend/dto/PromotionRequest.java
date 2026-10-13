package co.palaciodelmar.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PromotionRequest {
    private String title;
    private String branchId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer discountPercent;
    private String code;
}
