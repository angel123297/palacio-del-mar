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
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutoStayLifecycleSchedulerTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

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
        LocalDate today = LocalDate.now(BOGOTA);

        Booking booking = Booking.builder()
                .id("bk-checkin-1")
                .status("confirmed")
                .checkIn(today)
                .checkOut(today.plusDays(2))
                .user("guest-1")
                .build();

        when(bookingRepository.findByStatusInAndCheckInLessThanEqual(eq(List.of("confirmed")), any(LocalDate.class))).thenReturn(List.of(booking));
        when(bookingRepository.findById("bk-checkin-1")).thenReturn(java.util.Optional.of(booking));

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
        LocalDate today = LocalDate.now(BOGOTA);

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

        when(bookingRepository.findByStatusInAndCheckOutLessThanEqual(eq(List.of("checked_in")), any(LocalDate.class))).thenReturn(List.of(booking));
        when(bookingRepository.findById("bk-checkout-1")).thenReturn(java.util.Optional.of(booking));

        ReflectionTestUtils.setField(scheduler, "autoCheckOutHour", 0); // garantiza ejecución
        scheduler.processStayLifecycleTransitions();

        verify(bookingService, atLeastOnce()).changeBookingStatus(
                eq(booking), eq(BookingStatus.CHECKED_OUT), any(), any()
        );
        assertTrue(booking.getGuaranteeRefunded());
        assertEquals(new BigDecimal("100000"), booking.getRefundedAmount());
    }

    @Test
    @DisplayName("Late Check-out: Si el PIN sigue activo pasadas las 13:00 antes de cerrar, se aplica recargo del 50%")
    void testLateCheckOutPenaltyAppliedWhenPinActiveAfter13() {
        LocalDate today = LocalDate.now(BOGOTA);

        Booking booking = Booking.builder()
                .id("bk-late-1")
                .status("checked_in")
                .checkIn(today.minusDays(2))
                .checkOut(today)
                .smartLockPin("654321")
                .pricePerNight(new BigDecimal("300000"))
                .totalPrice(new BigDecimal("600000"))
                .balanceDue(BigDecimal.ZERO)
                .paidAmount(new BigDecimal("600000"))
                .guaranteeDeposit(new BigDecimal("100000"))
                .guaranteeRefunded(false)
                .refundedAmount(BigDecimal.ZERO)
                .build();

        when(bookingRepository.findByStatusInAndCheckOutLessThanEqual(eq(List.of("checked_in")), any(LocalDate.class))).thenReturn(List.of(booking));
        when(bookingRepository.findById("bk-late-1")).thenReturn(java.util.Optional.of(booking));

        // Ejecutar a las 13:30 (pasadas las 13:00 límite)
        scheduler.processAutoCheckOuts(today, LocalTime.of(13, 30));

        verify(bookingService, atLeastOnce()).changeBookingStatus(
                eq(booking), eq(BookingStatus.CHECKED_OUT), any(), any()
        );

        // Recargo de 50% de 300000 = 150000; base habitación 500000 + 150000 = 650000 -> fee 8% = 52000, host 92% = 598000
        assertEquals(new BigDecimal("750000.00"), booking.getTotalPrice());
        assertEquals(new BigDecimal("150000.00"), booking.getBalanceDue());
        assertEquals(new BigDecimal("52000.00"), booking.getPlatformFee());
        assertEquals(new BigDecimal("598000.00"), booking.getHostPayout());
        assertNull(booking.getSmartLockPin()); // PIN desactivado al cerrar
        assertTrue(booking.getGuaranteeRefunded());
    }

    @Test
    @DisplayName("C-2: AutoStayLifecycle ignora reservas cuyo estado ya cambió concurrentemente al re-leer")
    void testSkipsStaleBookingWhenFreshStatusChanged() {
        LocalDate today = LocalDate.now(BOGOTA);

        Booking stale = Booking.builder()
                .id("bk-stale-1")
                .status("checked_in")
                .checkIn(today.minusDays(2))
                .checkOut(today)
                .smartLockPin("123456")
                .pricePerNight(new BigDecimal("300000"))
                .totalPrice(new BigDecimal("600000"))
                .build();

        Booking freshAlreadyOut = Booking.builder()
                .id("bk-stale-1")
                .status("checked_out")
                .checkIn(today.minusDays(2))
                .checkOut(today)
                .totalPrice(new BigDecimal("600000"))
                .build();

        when(bookingRepository.findByStatusInAndCheckOutLessThanEqual(eq(List.of("checked_in")), any(LocalDate.class))).thenReturn(List.of(stale));
        when(bookingRepository.findById("bk-stale-1")).thenReturn(java.util.Optional.of(freshAlreadyOut));

        scheduler.processAutoCheckOuts(today, LocalTime.of(13, 30));

        verify(bookingService, never()).changeBookingStatus(any(), any(), any(), any());
        verify(bookingRepository, never()).save(any());
    }
}
