package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingStateMachineTest {

    private static final String CHANGED_BY = "unit-test";
    private static final String REASON = "status transition test";
    private final BookingService service = new BookingService(null, null, null, null, null, null, null);

    @ParameterizedTest(name = "{0} -> {1} must be {2}")
    @MethodSource("allStatusTransitions")
    void validatesEveryStatusTransition(BookingStatus current, BookingStatus next, boolean allowed) {
        Instant previousUpdate = Instant.parse("2025-01-01T00:00:00Z");
        Booking booking = Booking.builder()
                .status(current.name().toLowerCase())
                .updatedAt(previousUpdate)
                .build();

        if (allowed) {
            service.changeBookingStatus(booking, next, CHANGED_BY, REASON);

            assertEquals(next.name().toLowerCase(), booking.getStatus());
            assertEquals(1, booking.getStatusHistory().size());
            Booking.StatusHistoryEntry history = booking.getStatusHistory().get(0);
            assertEquals(next.name().toLowerCase(), history.getStatus());
            assertEquals(CHANGED_BY, history.getChangedBy());
            assertEquals(REASON, history.getReason());
            assertNotNull(history.getTimestamp());
            assertNotNull(booking.getUpdatedAt());
        } else {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> service.changeBookingStatus(booking, next, CHANGED_BY, REASON));

            assertEquals("No se puede transicionar del estado " + current + " a " + next, exception.getMessage());
            assertEquals(current.name().toLowerCase(), booking.getStatus());
            assertEquals(0, booking.getStatusHistory().size());
            assertEquals(previousUpdate, booking.getUpdatedAt());
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> allStatusTransitions() {
        return Stream.of(BookingStatus.values())
                .flatMap(current -> Stream.of(BookingStatus.values())
                        .map(next -> org.junit.jupiter.params.provider.Arguments.of(
                                current, next, current.canTransitionTo(next))));
    }
}
