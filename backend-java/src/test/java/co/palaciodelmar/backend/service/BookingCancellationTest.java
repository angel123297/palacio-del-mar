package co.palaciodelmar.backend.service;

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

        return new BookingService(bookingRepository, null, null, null, suiteNightRepository, null, null, null);
    }
}
