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
@Document(collection = "processed_webhook_events")
public class ProcessedWebhookEvent {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId; // e.g., "evt_..."

    private String eventType; // e.g., "payment.succeeded"

    @Builder.Default
    private Instant processedAt = Instant.now();

    private String details;
}
