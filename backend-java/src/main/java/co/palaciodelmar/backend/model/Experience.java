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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "experiences")
public class Experience {

    @Id
    private String id;

    private String name;
    private String description;
    private String shortDescription;
    private BigDecimal price;

    @Indexed
    private String branch; // Branch ObjectId reference or null for all

    private String category; // "spa", "dining", "adventure", etc.
    private String image;
    private String mainImage;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean available = true;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
