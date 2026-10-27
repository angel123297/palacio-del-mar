package co.palaciodelmar.backend.service;
import static org.mockito.Mockito.mock;
import co.palaciodelmar.backend.service.PaymentGateway;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingCancellationTest {

    private static final String BOOKING_ID = "booking-1";
    private static final String USER_ID = "user-1";
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");
    private SuiteNightRepository suiteNightRepository;

    @Test
    void cancellationAtLeastSevenDaysAheadRefundsFullPaymentAndReleasesInventory() {
        Booking booking = bookingWithCheckIn(LocalDate.now(BOGOTA_ZONE).plusDays(7));
        BookingService service = serviceFor(booking);

        BookingDTO result = service.cancelBooking(BOOKING_ID, USER_ID, "Cambio de planes");

        assertEquals("cancelled", result.getStatus());
        assertEquals(new BigDecimal("1000.00"), result.getRefundedAmount());
        assertEquals("refunded", result.getPaymentStatus());
        assertEquals(BigDecimal.ZERO, result.getBalanceDue());
        assertEquals("Cambio de planes", result.getCancellationReason());
        assertNotNull(booking.getStatusHistory());
        assertEquals("cancelled", booking.getStatusHistory().get(0).getStatus());
        assertEquals(USER_ID, booking.getStatusHistory().get(0).getChangedBy());
        verify(suiteNightRepository).deleteByBooking(BOOKING_ID);
    }

    @Test
    void cancellationLessThanSevenDaysAheadRetainsTenPercentOfTotalPrice() {
        Booking booking = bookingWithCheckIn(LocalDate.now(BOGOTA_ZONE).plusDays(6));
        booking.setPaidAmount(new BigDecimal("500.00"));
        BookingService service = serviceFor(booking);

        BookingDTO result = service.cancelBooking(BOOKING_ID, USER_ID, "Cancelación tardía");

        assertEquals(new BigDecimal("400.00"), result.getRefundedAmount());
        assertEquals("partial", result.getPaymentStatus());
        assertEquals("cancelled", result.getStatus());
        verify(suiteNightRepository).deleteByBooking(BOOKING_ID);
    }

    @Test
    void cancellationByBookingCodeReleasesSuiteNightsUsingInternalBookingId() {
        Booking booking = bookingWithCheckIn(LocalDate.now(BOGOTA_ZONE).plusDays(10));
        booking.setBookingCode("PDM-20261026-ABCD");

        BookingRepository bookingRepository = mock(BookingRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        when(bookingRepository.findById("PDM-20261026-ABCD")).thenReturn(Optional.empty());
        when(bookingRepository.findByBookingCode("PDM-20261026-ABCD")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingService service = new BookingService(
                bookingRepository, null, null, null, suiteNightRepository, null, null, null, mock(PaymentGateway.class)
        );

        BookingDTO result = service.cancelBooking("PDM-20261026-ABCD", USER_ID, "Cancelación por código");

        assertEquals("cancelled", result.getStatus());
        verify(suiteNightRepository).deleteByBooking(BOOKING_ID);
    }

    @Test
    void doesNotRefundOrDeleteNightsWhenSaveFailsAndUsesCompletedPaymentTransactionId() {
        Booking booking = bookingWithCheckIn(LocalDate.now(BOGOTA_ZONE).plusDays(10));
        BookingRepository bookingRepository = mock(BookingRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        co.palaciodelmar.backend.repository.PaymentRepository paymentRepository =
                mock(co.palaciodelmar.backend.repository.PaymentRepository.class);

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Version conflict"));

        BookingService service = new BookingService(
                bookingRepository, null, null, null, suiteNightRepository, null, null, null, gateway
        );
        service.setPaymentRepository(paymentRepository);

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.OptimisticLockingFailureException.class,
                () -> service.cancelBooking(BOOKING_ID, USER_ID, "Conflicto concurrente")
        );
        org.mockito.Mockito.verifyNoInteractions(gateway);
        org.mockito.Mockito.verify(suiteNightRepository, org.mockito.Mockito.never()).deleteByBooking(any());

        // Ahora cuando save tiene éxito, persiste primero y luego reembolsa con el transactionId real del pago
        Booking freshBooking = bookingWithCheckIn(LocalDate.now(BOGOTA_ZONE).plusDays(10));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(freshBooking));
        org.mockito.Mockito.doReturn(freshBooking).when(bookingRepository).save(any(Booking.class));
        when(paymentRepository.findByBooking(BOOKING_ID)).thenReturn(java.util.List.of(
                co.palaciodelmar.backend.model.Payment.builder()
                        .id("pay-1")
                        .booking(BOOKING_ID)
                        .status("completed")
                        .transactionId("ch_real_tx_777")
                        .amount(new BigDecimal("1000.00"))
                        .build()
        ));
        when(gateway.refund("ch_real_tx_777", new BigDecimal("1000.00")))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_777", "refunded", null));

        service.cancelBooking(BOOKING_ID, USER_ID, "Cancelación válida");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(bookingRepository, gateway);
        inOrder.verify(bookingRepository).save(freshBooking);
        inOrder.verify(gateway).refund("ch_real_tx_777", new BigDecimal("1000.00"));
    }

    private Booking bookingWithCheckIn(LocalDate checkIn) {
        return Booking.builder()
                .id(BOOKING_ID)
                .user(USER_ID)
                .checkIn(checkIn)
                .totalPrice(new BigDecimal("1000.00"))
                .paidAmount(new BigDecimal("1000.00"))
                .balanceDue(BigDecimal.ZERO)
                .status("confirmed")
                .paymentStatus("paid")
                .build();
    }

    private BookingService serviceFor(Booking booking) {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        return new BookingService(bookingRepository, null, null, null, suiteNightRepository, null, null, null, mock(PaymentGateway.class));
    }
}
