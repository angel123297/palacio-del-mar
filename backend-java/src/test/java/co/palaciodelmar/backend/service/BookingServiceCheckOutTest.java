package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceCheckOutTest {

    private BookingRepository bookingRepository;
    private SuiteNightRepository suiteNightRepository;
    private PaymentRepository paymentRepository;
    private PropertyRepository propertyRepository;
    private PaymentGateway paymentGateway;
    private RedisLockService redisLockService;
    private BookingService bookingService;

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        paymentGateway = mock(PaymentGateway.class);
        redisLockService = mock(RedisLockService.class);

        // Make redisLockService execute action directly
        when(redisLockService.executeWithLock(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(3);
                    return action.get();
                });

        bookingService = new BookingService(
                bookingRepository,
                null,
                null,
                null,
                suiteNightRepository,
                null,
                null,
                null,
                paymentGateway
        );

        bookingService.setPaymentRepository(paymentRepository);
        bookingService.setRedisLockService(redisLockService);
        ReflectionTestUtils.setField(bookingService, "propertyRepository", propertyRepository);

        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("R4: Check-out a tiempo devuelve el depósito de garantía completo usando transacción real de pago")
    void testCheckOutOnTimeRefundsFullDepositWithRealPayment() {
        LocalDate today = LocalDate.now(BOGOTA);
        Booking booking = Booking.builder()
                .id("b-ontime-1")
                .propertyId("prop-1")
                .status("checked_in")
                .paymentStatus("paid")
                .checkIn(today.minusDays(3))
                .checkOut(today.plusDays(1)) // future checkout date = definitely on-time
                .pricePerNight(new BigDecimal("200000"))
                .totalPrice(new BigDecimal("600000"))
                .paidAmount(new BigDecimal("700000")) // 600k stay + 100k deposit
                .balanceDue(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .guaranteeDeposit(new BigDecimal("100000"))
                .guaranteeRefunded(false)
                .smartLockPin("PIN-1234")
                .build();

        Payment realPayment = Payment.builder()
                .id("pay-real-1")
                .booking("b-ontime-1")
                .amount(new BigDecimal("700000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .transactionId("tx_stripe_real_777")
                .build();

        when(bookingRepository.findById("b-ontime-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking("b-ontime-1")).thenReturn(List.of(realPayment));
        when(paymentGateway.refund(eq("tx_stripe_real_777"), eq(new BigDecimal("100000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_real_01", "refunded", null));

        BookingDTO result = bookingService.processCheckOut("b-ontime-1", "host-ana", "Salida normal", false);

        assertEquals("checked_out", result.getStatus());
        assertNull(booking.getSmartLockPin(), "El PIN de smart lock debe ser revocado al hacer check-out");
        assertTrue(booking.getGuaranteeRefunded(), "El depósito debe marcarse como gestionado/reembolsado");
        assertEquals(new BigDecimal("100000"), booking.getRefundedAmount(), "El depósito de 100.000 debe haber sido reembolsado");
        verify(paymentGateway).refund(eq("tx_stripe_real_777"), eq(new BigDecimal("100000")), anyString());
        verify(paymentRepository).save(argThat(p -> p.getId().equals("pay-real-1") && p.getRefundedAmount().compareTo(new BigDecimal("100000")) == 0));
    }

    @Test
    @DisplayName("R4: Check-out tardío calcula recargo del 50%, compensa con depósito y devuelve exceso restante")
    void testCheckOutLateAppliesPenaltyOffsetsDepositAndRefundsExcess() {
        LocalDate today = LocalDate.now(BOGOTA);
        // CheckOut was yesterday => definitely late
        Booking booking = Booking.builder()
                .id("b-late-1")
                .propertyId("prop-1")
                .status("checked_in")
                .paymentStatus("paid")
                .checkIn(today.minusDays(3))
                .checkOut(today.minusDays(1)) // checkout was yesterday -> isLate is true
                .pricePerNight(new BigDecimal("200000"))
                .totalPrice(new BigDecimal("600000"))
                .paidAmount(new BigDecimal("800000")) // 600k room + 200k deposit
                .balanceDue(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .guaranteeDeposit(new BigDecimal("200000"))
                .guaranteeRefunded(false)
                .smartLockPin("PIN-LATE-999")
                .build();

        Payment realPayment = Payment.builder()
                .id("pay-late-real")
                .booking("b-late-1")
                .amount(new BigDecimal("800000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .transactionId("tx_late_real_555")
                .build();

        when(bookingRepository.findById("b-late-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking("b-late-1")).thenReturn(List.of(realPayment));
        when(paymentGateway.refund(eq("tx_late_real_555"), argThat(amt -> amt != null && amt.compareTo(new BigDecimal("100000")) == 0), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_late_01", "refunded", null));

        BookingDTO result = bookingService.processCheckOut("b-late-1", "host-carlos", "Salida tardía", false);

        assertEquals("checked_out", result.getStatus());
        // Penalty is 50% of 200,000 = 100,000
        // Total price increases from 600,000 to 700,000
        assertEquals(0, new BigDecimal("700000").compareTo(booking.getTotalPrice()));
        // Deposit 200k compensates 100k debt, leaving 100k excess to refund
        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getBalanceDue()));
        assertEquals("paid", booking.getPaymentStatus());
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getRefundedAmount()));
        assertTrue(booking.getGuaranteeRefunded());

        // Refund for excess deposit (100k) was made with real transaction ID
        verify(paymentGateway).refund(eq("tx_late_real_555"), argThat(amt -> amt != null && amt.compareTo(new BigDecimal("100000")) == 0), anyString());
    }

    @Test
    @DisplayName("R4: Check-out tardío con depósito insuficiente retiene todo el depósito y deja saldo pendiente")
    void testCheckOutLateInsufficientDepositLeavesBalanceDue() {
        LocalDate today = LocalDate.now(BOGOTA);
        Booking booking = Booking.builder()
                .id("b-late-short")
                .propertyId("prop-1")
                .status("checked_in")
                .paymentStatus("paid")
                .checkIn(today.minusDays(2))
                .checkOut(today.minusDays(1)) // checkout was yesterday -> isLate is true
                .pricePerNight(new BigDecimal("200000"))
                .totalPrice(new BigDecimal("400000"))
                .paidAmount(new BigDecimal("450000")) // 400k room + 50k deposit
                .balanceDue(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .guaranteeDeposit(new BigDecimal("500000")) // Note: deposit is 50,000
                .guaranteeDeposit(new BigDecimal("50000"))
                .guaranteeRefunded(false)
                .smartLockPin("PIN-LATE-SHORT")
                .build();

        when(bookingRepository.findById("b-late-short")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking("b-late-short")).thenReturn(Collections.emptyList());

        bookingService.processCheckOut("b-late-short", "host-carlos", "Salida tardía", false);

        assertEquals("checked_out", booking.getStatus());
        // Penalty = 100,000. Deposit = 50,000. Compensated = 50,000.
        // Balance due should be 100,000 - 50,000 = 50,000!
        assertEquals(0, new BigDecimal("50000").compareTo(booking.getBalanceDue()));
        assertEquals("partial", booking.getPaymentStatus());
        // No refund to guest
        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getRefundedAmount()));
        assertTrue(booking.getGuaranteeRefunded());
        verify(paymentGateway, never()).refund(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("R4: processCheckOut adquiere y ejecuta bajo el bloqueo de reserva de Redis")
    void testCheckOutExecutesUnderRedisLock() {
        Booking booking = Booking.builder()
                .id("b-lock-test")
                .status("checked_in")
                .checkOut(LocalDate.now().plusDays(1))
                .build();

        when(bookingRepository.findById("b-lock-test")).thenReturn(Optional.of(booking));

        bookingService.processCheckOut("b-lock-test", "system", "auto checkout", true);

        verify(redisLockService).executeWithLock(eq("booking:b-lock-test"), any(), any(), any());
    }

    @Test
    @DisplayName("R4: processCheckOut rechaza si la reserva no está en estado checked_in")
    void testCheckOutRejectsInvalidStatus() {
        Booking booking = Booking.builder()
                .id("b-invalid-status")
                .status("confirmed")
                .build();

        when(bookingRepository.findById("b-invalid-status")).thenReturn(Optional.of(booking));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                bookingService.processCheckOut("b-invalid-status", "admin", "test", false)
        );

        assertTrue(ex.getMessage().contains("No se puede realizar check-out desde el estado confirmed"));
    }
}
