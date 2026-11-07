package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.service.BookingService;
import co.palaciodelmar.backend.service.RedisLockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BookingExpirySchedulerTest {

    @Test
    @DisplayName("Persiste primero el estado EXPIRED y solo después libera las noches retenidas bajo lock")
    void releasesInventoryOnlyAfterSavingExpiredStatus() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        BookingService bookingService = mock(BookingService.class);
        RedisLockService redisLockService = spy(new RedisLockService(null));

        BookingExpiryScheduler scheduler = new BookingExpiryScheduler(bookingRepository, suiteNightRepository, bookingService);
        scheduler.setRedisLockService(redisLockService);

        Booking expiredBooking = Booking.builder()
                .id("B-EXPIRED-1")
                .suite("S-1")
                .status("pending")
                .paidAmount(BigDecimal.ZERO)
                .expiresAt(Instant.now().minusSeconds(120))
                .build();

        SuiteNight heldNight = SuiteNight.builder()
                .id("SN-1")
                .booking("B-EXPIRED-1")
                .status("held")
                .build();

        when(bookingRepository.findByStatusAndExpiresAtBefore(eq("pending"), any(Instant.class)))
                .thenReturn(List.of(expiredBooking));
        when(bookingRepository.findById("B-EXPIRED-1"))
                .thenReturn(Optional.of(expiredBooking));
        when(bookingRepository.save(expiredBooking)).thenReturn(expiredBooking);
        when(suiteNightRepository.findByBooking("B-EXPIRED-1"))
                .thenReturn(List.of(heldNight));

        scheduler.cleanupExpiredBookings();

        InOrder inOrder = inOrder(redisLockService, bookingService, bookingRepository, suiteNightRepository);
        inOrder.verify(redisLockService).executeWithLock(eq("booking:B-EXPIRED-1"), any(), any(), any());
        inOrder.verify(bookingService).changeBookingStatus(
                eq(expiredBooking),
                eq(BookingStatus.EXPIRED),
                eq("System-Scheduler"),
                anyString()
        );
        inOrder.verify(bookingRepository).save(expiredBooking);
        inOrder.verify(suiteNightRepository).deleteAll(List.of(heldNight));
    }

    @Test
    @DisplayName("C-2: Si la reserva fue confirmada/pagada entre la consulta inicial y el lock, jamás borra SuiteNights")
    void doesNotDeleteSuiteNightsWhenBookingConfirmedConcurrently() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        BookingService bookingService = mock(BookingService.class);

        BookingExpiryScheduler scheduler = new BookingExpiryScheduler(bookingRepository, suiteNightRepository, bookingService);
        scheduler.setRedisLockService(new RedisLockService(null));

        Booking staleSnapshot = Booking.builder()
                .id("B-RACE-1")
                .suite("S-1")
                .status("pending")
                .paidAmount(BigDecimal.ZERO)
                .expiresAt(Instant.now().minusSeconds(5))
                .build();

        Booking confirmedFresh = Booking.builder()
                .id("B-RACE-1")
                .suite("S-1")
                .status("confirmed")
                .paymentStatus("paid")
                .paidAmount(new BigDecimal("500000"))
                .expiresAt(Instant.now().minusSeconds(5))
                .build();

        when(bookingRepository.findByStatusAndExpiresAtBefore(eq("pending"), any(Instant.class)))
                .thenReturn(List.of(staleSnapshot));
        when(bookingRepository.findById("B-RACE-1"))
                .thenReturn(Optional.of(confirmedFresh));

        scheduler.cleanupExpiredBookings();

        verify(bookingService, never()).changeBookingStatus(any(), any(), any(), any());
        verify(bookingRepository, never()).save(any());
        verify(suiteNightRepository, never()).deleteAll(any());
        verify(suiteNightRepository, never()).deleteByBooking(any());
    }

    @Test
    @DisplayName("C-2: Si bookingRepository.save falla por @Version (confirmación concurrente), jamás borra SuiteNights")
    void doesNotDeleteSuiteNightsWhenOptimisticLockFailsOnSave() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        BookingService bookingService = mock(BookingService.class);

        BookingExpiryScheduler scheduler = new BookingExpiryScheduler(bookingRepository, suiteNightRepository, bookingService);

        Booking pendingBooking = Booking.builder()
                .id("B-RACE-2")
                .suite("S-1")
                .status("pending")
                .paidAmount(BigDecimal.ZERO)
                .expiresAt(Instant.now().minusSeconds(10))
                .build();

        when(bookingRepository.findByStatusAndExpiresAtBefore(eq("pending"), any(Instant.class)))
                .thenReturn(List.of(pendingBooking));
        when(bookingRepository.findById("B-RACE-2"))
                .thenReturn(Optional.of(pendingBooking));
        when(bookingRepository.save(pendingBooking))
                .thenThrow(new OptimisticLockingFailureException("Concurrent confirmation updated @Version"));

        scheduler.cleanupExpiredBookings();

        verify(suiteNightRepository, never()).deleteAll(any());
        verify(suiteNightRepository, never()).deleteByBooking(any());
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
