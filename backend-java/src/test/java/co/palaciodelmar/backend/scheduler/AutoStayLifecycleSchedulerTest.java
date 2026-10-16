package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutoStayLifecycleSchedulerTest {

    private BookingRepository bookingRepository;
    private BookingService bookingService;
    private AutoStayLifecycleScheduler scheduler;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        bookingService = mock(BookingService.class);
        scheduler = new AutoStayLifecycleScheduler(bookingRepository, bookingService);
        ReflectionTestUtils.setField(scheduler, "autoCheckInHour", 15);
        ReflectionTestUtils.setField(scheduler, "autoCheckOutHour", 12);
    }

    @Test
    @DisplayName("Self Check-in automático: Activa PIN y pasa a CHECKED_IN a la hora de llegada oficial (15:00)")
    void testAutoCheckInExecution() {
        LocalDate today = LocalDate.now();

        Booking booking = Booking.builder()
                .id("bk-checkin-1")
                .status("confirmed")
                .checkIn(today)
                .checkOut(today.plusDays(2))
                .user("guest-1")
                .build();

        when(bookingRepository.findByStatusIn(List.of("confirmed"))).thenReturn(List.of(booking));

        scheduler.processStayLifecycleTransitions();

        // Si la hora del sistema es >= 15:00, se debe ejecutar. O forzamos con ReflectionTestUtils
        // Probamos llamando con hora baja y hora alta
        ReflectionTestUtils.setField(scheduler, "autoCheckInHour", 0); // para garantizar ejecución en cualquier hora
        scheduler.processStayLifecycleTransitions();

        verify(bookingService, atLeastOnce()).changeBookingStatus(
                eq(booking), eq(BookingStatus.CHECKED_IN), any(), any()
        );
        assertNotNull(booking.getSmartLockPin());
        assertEquals(6, booking.getSmartLockPin().length());
    }

    @Test
    @DisplayName("Auto Check-out programado: A la hora de salida (12:00) devuelve depósito y pasa a CHECKED_OUT")
    void testAutoCheckOutExecution() {
        LocalDate today = LocalDate.now();

        Booking booking = Booking.builder()
                .id("bk-checkout-1")
                .status("checked_in")
                .checkIn(today.minusDays(2))
                .checkOut(today)
                .paidAmount(new BigDecimal("600000"))
                .guaranteeDeposit(new BigDecimal("100000"))
                .guaranteeRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        when(bookingRepository.findByStatusIn(List.of("checked_in"))).thenReturn(List.of(booking));

        ReflectionTestUtils.setField(scheduler, "autoCheckOutHour", 0); // garantiza ejecución
        scheduler.processStayLifecycleTransitions();

        verify(bookingService, atLeastOnce()).changeBookingStatus(
                eq(booking), eq(BookingStatus.CHECKED_OUT), any(), any()
        );
        assertTrue(booking.getGuaranteeRefunded());
        assertEquals(new BigDecimal("100000"), booking.getRefundedAmount());
    }
}
