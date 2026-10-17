package co.palaciodelmar.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "suites")
public class Suite implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @JsonProperty("_id")
    public String get_id() {
        return id;
    }

    @Indexed
    private String branch; // Branch ObjectId reference (mantenido por compatibilidad)

    @Indexed
    private String propertyId; // Property ObjectId reference (marketplace)

    @Indexed
    @Builder.Default
    private String source = "NATIVE"; // "NATIVE", "EXTERNAL"

    private String externalId; // ID en sistema externo del anfitrión

    private String name;

    @Indexed(unique = true)
    private String slug;

    @Indexed
    private String type; // "Habitación", "Suite Deluxe", etc.

    private String description;
    private String shortDescription;

    @Indexed
    private BigDecimal basePrice;
    private BigDecimal originalPrice;

    private String mainImage;

    @Builder.Default
    private Integer maxGuests = 2;

    @Builder.Default
    private Integer size = 30;

    @Builder.Default
    private Integer maxOccupancy = 2;

    @Builder.Default
    private Integer sizeSqm = 30;

    @Builder.Default
    private List<BedConfig> beds = new ArrayList<>();
    private String viewType;

    @Builder.Default
    private List<String> amenities = new ArrayList<>();
    @Builder.Default
    private Boolean hasBalcony = false;
    @Builder.Default
    private Boolean hasTerrace = false;
    @Builder.Default
    private Boolean hasJacuzzi = false;
    private String view;
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Builder.Default
    private Integer totalUnits = 1;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean available = true;

    @Builder.Default
    private Boolean featured = false;

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
    public static class BedConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        private String type; // "single", "double", "queen", "king"
        private Integer quantity;
        private String size;
    }
}
