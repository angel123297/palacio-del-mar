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
public class BookingMessageDTO {
    private String id;
    private String bookingId;
    private String senderId;
    private String senderName;
    private String senderRole;
    private String message;
    private Instant createdAt;
    private Instant readAt;
    private boolean me;
}
