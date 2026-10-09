package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDTO {
    private String id;
    private String suite;
    private String user;
    private String userName;
    private Integer rating;
    private String comment;
    private Boolean approved;
    private Instant createdAt;
}
