package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {
    private String id;
    private String suite;
    private String user;
    private String bookingId;
    private String userName;
    private Integer rating;
    private String comment;
    private Boolean verifiedStay;
    private String sentiment;
    private Double sentimentScore;
    private List<String> keyHighlights;
    private String maintenanceAlert;
    private Boolean approved;
    private Instant createdAt;
}
