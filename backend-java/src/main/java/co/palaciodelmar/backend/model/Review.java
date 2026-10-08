package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviews")
public class Review {

    @Id
    private String id;

    @Indexed
    private String suite; // Suite ObjectId reference

    @Indexed
    private String user; // User ObjectId reference

    private String userName;
    private Integer rating; // 1 to 5
    private String comment;

    @Builder.Default
    private Boolean approved = true;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
