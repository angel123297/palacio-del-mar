package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostPayoutDTO {
    private String id;
    private String payoutCode;
    private String propertyId;
    private String propertyName;
    private String ownerId;
    private List<String> bookingIds;
    private Integer totalBookings;
    private BigDecimal grossTotal;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private String status;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Instant processedAt;
    private Instant createdAt;
}
