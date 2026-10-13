package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculatePriceResponseDTO {

    private String suiteId;
    private DatesInfo dates;
    private BigDecimal nightlyPrice;
    private BigDecimal subtotal;
    private BigDecimal experiencesTotal;
    private BigDecimal discount;
    private String discountReason;
    private BigDecimal total;
    private List<LineItem> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DatesInfo {
        private LocalDate checkIn;
        private LocalDate checkOut;
        private Integer nights;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItem {
        private BigDecimal unitPrice;
        private Integer nights;
        private BigDecimal amount;
        private String season;
    }
}
