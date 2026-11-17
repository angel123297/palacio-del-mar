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
@Document(collection = "booking_messages")
public class BookingMessage {

    @Id
    private String id;

    @Indexed
    private String bookingId;

    private String senderId;
    private String senderName;
    private String senderRole; // "GUEST", "HOST", "ADMIN"

    private String message;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant readAt;
}
