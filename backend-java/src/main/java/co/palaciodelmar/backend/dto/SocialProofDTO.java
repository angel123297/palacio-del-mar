package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialProofDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String suiteKey;
    private long activeViewers;
    private Long lastBookedMinutesAgo;
    private long bookingsLast24h;
    private boolean highDemand;
}
