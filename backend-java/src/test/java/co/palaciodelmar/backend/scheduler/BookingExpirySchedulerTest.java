package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.service.BookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BookingExpirySchedulerTest {

    @Test
    @DisplayName("Libera inventario y marca reserva como expirada cuando se vence el tiempo límite")
    void releasesInventoryAndExpiresUnpaidBookings() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        BookingService bookingService = mock(BookingService.class);

        BookingExpiryScheduler scheduler = new BookingExpiryScheduler(bookingRepository, suiteNightRepository, bookingService);

        Booking expiredBooking = Booking.builder()
                .id("B-EXPIRED-1")
                .status("pending")
                .expiresAt(Instant.now().minusSeconds(120))
                .build();

        SuiteNight heldNight = SuiteNight.builder()
                .id("SN-1")
                .booking("B-EXPIRED-1")
                .status("held")
                .build();

        when(bookingRepository.findByStatusAndExpiresAtBefore(eq("pending"), any(Instant.class)))
                .thenReturn(List.of(expiredBooking));
        when(suiteNightRepository.findByBooking("B-EXPIRED-1"))
                .thenReturn(List.of(heldNight));

        scheduler.cleanupExpiredBookings();

        // Verifica que se borran las noches retenidas
        verify(suiteNightRepository, times(1)).deleteAll(List.of(heldNight));
        // Verifica que se hace la transición a EXPIRED
        verify(bookingService, times(1)).changeBookingStatus(
                eq(expiredBooking),
                eq(BookingStatus.EXPIRED),
                eq("System-Scheduler"),
                anyString()
        );
        // Verifica que se guarda la reserva
        verify(bookingRepository, times(1)).save(expiredBooking);
    }

    @Test
    @DisplayName("No realiza acciones si no hay reservas expiradas")
    void doesNothingWhenNoExpiredBookings() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        BookingService bookingService = mock(BookingService.class);

        BookingExpiryScheduler scheduler = new BookingExpiryScheduler(bookingRepository, suiteNightRepository, bookingService);

        when(bookingRepository.findByStatusAndExpiresAtBefore(eq("pending"), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.cleanupExpiredBookings();

        verify(suiteNightRepository, never()).deleteAll(any());
        verify(bookingService, never()).changeBookingStatus(any(), any(), any(), any());
        verify(bookingRepository, never()).save(any());
    }
}
