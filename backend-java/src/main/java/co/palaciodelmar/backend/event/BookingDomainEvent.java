package co.palaciodelmar.backend.event;

import co.palaciodelmar.backend.dto.BookingDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDomainEvent {
    private String eventType; // booking.created, booking.confirmed, booking.cancelled
    private String bookingId;
    private String propertyId;
    private BookingDTO booking;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
