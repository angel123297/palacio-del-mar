package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "host_payouts")
public class HostPayout {

    @Id
    private String id;

    @Indexed(unique = true)
    private String payoutCode;

    @Indexed
    private String propertyId;

    private String propertyName;

    @Indexed
    private String ownerId;

    @Builder.Default
    private List<String> bookingIds = new ArrayList<>();

    private Integer totalBookings;

    private BigDecimal grossTotal;

    private BigDecimal platformFee;

    private BigDecimal netAmount;

    @Builder.Default
    private String status = "PROCESSED"; // "PROCESSED", "TRANSFERRED"

    private LocalDate periodStart;

    private LocalDate periodEnd;

    @Builder.Default
    private Instant processedAt = Instant.now();

    @Builder.Default
    private Instant createdAt = Instant.now();
}
