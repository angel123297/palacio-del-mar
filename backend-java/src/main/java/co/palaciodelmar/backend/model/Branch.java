package co.palaciodelmar.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "branches")
public class Branch {

    @Id
    private String id;

    @JsonProperty("_id")
    public String get_id() {
        return id;
    }

    @Indexed(unique = true)
    private String name;

    @Indexed(unique = true)
    private String slug;

    private String zone;
    private String tagline;
    private String description;
    private String address;

    private GeoLocation location;
    private String mainImage;

    @Builder.Default
    private List<String> vibe = new ArrayList<>();

    @Builder.Default
    private List<PointOfInterest> highlights = new ArrayList<>();

    private String phone;

    @Builder.Default
    private String checkInTime = "15:00";

    @Builder.Default
    private String checkOutTime = "12:00";

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Integer order = 0;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        private Double lat;
        private Double lng;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointOfInterest {
        private String name;
        private String type;
        private Integer walkMinutes;
        private GeoLocation location;
    }
}
