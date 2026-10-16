package co.palaciodelmar.backend.model;

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
@Document(collection = "webhook_subscriptions")
public class WebhookSubscription {

    @Id
    private String id;

    @Indexed
    private String propertyId;

    @Indexed
    private String ownerId;

    private String url;

    private String secret;

    @Builder.Default
    private List<String> events = new ArrayList<>();

    @Builder.Default
    private Boolean active = true;

    private String description;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
