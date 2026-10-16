package co.palaciodelmar.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "properties")
public class Property implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @JsonProperty("_id")
    public String get_id() {
        return id;
    }

    @Indexed
    private String ownerId; // User ObjectId reference (HOST)

    private String name;

    @Indexed(unique = true)
    private String slug;

    @Builder.Default
    private String type = "HOTEL"; // "HOTEL", "ROOM", "APARTMENT", "VILLA"

    @Indexed
    private String city;

    private String address;

    private String description;
    private String shortDescription;

    private String mainImage;

    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Builder.Default
    private BigDecimal commissionRate = new BigDecimal("0.08"); // 8% fee de plataforma

    @Indexed
    @Builder.Default
    private String status = "PENDING"; // "PENDING", "APPROVED", "REJECTED"

    private String externalSiteUrl; // URL opcional de la web externa del anfitrión

    private String phone;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
