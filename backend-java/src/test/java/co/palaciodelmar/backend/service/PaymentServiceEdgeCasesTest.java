package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentServiceEdgeCasesTest {

    private PaymentRepository paymentRepository;
    private BookingRepository bookingRepository;
    private BookingService bookingService;
    private PaymentGateway paymentGateway;
    private EmailService emailService;
    private UserRepository userRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingService = mock(BookingService.class);
        paymentGateway = mock(PaymentGateway.class);
        emailService = mock(EmailService.class);
        userRepository = mock(UserRepository.class);

        paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                bookingService,
                paymentGateway,
                emailService,
                userRepository
        );
    }

    @Test
    void testCannotPayExpiredBooking() {
        Booking booking = Booking.builder()
                .id("book-exp")
                .status("pending")
                .expiresAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .totalPrice(new BigDecimal("1000"))
                .build();

        Payment payment = Payment.builder()
                .id("pay-exp")
                .booking("book-exp")
                .status("pending")
                .intentId("intent-exp")
                .build();

        when(paymentRepository.findByIntentId("intent-exp")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("book-exp")).thenReturn(Optional.of(booking));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                paymentService.confirmPayment(null, "intent-exp", "tok_test")
        );
        assertEquals("El tiempo para pagar esta reserva ha expirado", ex.getMessage());
    }

    @Test
    void testCannotPayCancelledBooking() {
        Booking booking = Booking.builder()
                .id("book-canc")
                .status("cancelled")
                .totalPrice(new BigDecimal("1000"))
                .build();

        when(bookingRepository.findById("book-canc")).thenReturn(Optional.of(booking));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                paymentService.createPaymentIntent(null, "book-canc", "card", null)
        );
        assertEquals("No se puede pagar una reserva cancelada o vencida", ex.getMessage());
    }

    @Test
    void testPartialPaymentsUntilPaid() {
        Booking booking = Booking.builder()
                .id("book-partial")
                .status("pending")
                .totalPrice(new BigDecimal("1000"))
                .paidAmount(new BigDecimal("0"))
                .balanceDue(new BigDecimal("1000"))
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        Payment payment1 = Payment.builder()
                .id("pay-1")
                .booking("book-partial")
                .amount(new BigDecimal("600"))
                .status("pending")
                .intentId("intent-1")
                .build();

        when(paymentRepository.findByIntentId("intent-1")).thenReturn(Optional.of(payment1));
        when(bookingRepository.findById("book-partial")).thenReturn(Optional.of(booking));
        when(paymentGateway.confirmIntent(eq("intent-1"), any())).thenReturn(
                new PaymentGateway.PaymentConfirmResponse(true, "tx-1", "succeeded", null)
        );
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        paymentService.confirmPayment(null, "intent-1", "tok_1");

        assertEquals(new BigDecimal("600"), booking.getPaidAmount());
        assertEquals(new BigDecimal("400"), booking.getBalanceDue());
        assertEquals("partial", booking.getPaymentStatus());
        assertEquals("pending", booking.getStatus());

        Payment payment2 = Payment.builder()
                .id("pay-2")
                .booking("book-partial")
                .amount(new BigDecimal("400"))
                .status("pending")
                .intentId("intent-2")
                .build();

        when(paymentRepository.findByIntentId("intent-2")).thenReturn(Optional.of(payment2));
        when(paymentGateway.confirmIntent(eq("intent-2"), any())).thenReturn(
                new PaymentGateway.PaymentConfirmResponse(true, "tx-2", "succeeded", null)
        );

        paymentService.confirmPayment(null, "intent-2", "tok_2");

        assertEquals(new BigDecimal("1000"), booking.getPaidAmount());
        assertEquals(new BigDecimal("0"), booking.getBalanceDue());
        assertEquals("paid", booking.getPaymentStatus());

        // Verificamos si se cambia a CONFIRMED
        verify(bookingService).changeBookingStatus(eq(booking), eq(BookingStatus.CONFIRMED), eq("System"), anyString());
    }


    @Test
    void testOverpaymentRejectsExtraPayment() {
        Booking booking = Booking.builder()
                .id("book-overpay")
                .status("confirmed") // ya pagada
                .totalPrice(new BigDecimal("1000"))
                .paidAmount(new BigDecimal("1000"))
                .balanceDue(new BigDecimal("0"))
                .paymentStatus("paid")
                .build();

        Payment payment = Payment.builder()
                .id("pay-over")
                .booking("book-overpay")
                .amount(new BigDecimal("100"))
                .status("pending")
                .intentId("intent-over")
                .build();

        when(paymentRepository.findByIntentId("intent-over")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("book-overpay")).thenReturn(Optional.of(booking));
        when(paymentGateway.confirmIntent(eq("intent-over"), any())).thenReturn(
                new PaymentGateway.PaymentConfirmResponse(true, "tx-over", "succeeded", null)
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                paymentService.confirmPayment(null, "intent-over", "tok_test")
        );
        assertEquals("La reserva ya ha sido pagada en su totalidad.", ex.getMessage());
    }

    @Test
    void testConfirmPaymentOnAlreadyCompletedIntentReturnsIdempotently() {
        Booking booking = Booking.builder()
                .id("book-paid")
                .user("user-1")
                .status("confirmed")
                .totalPrice(new BigDecimal("1000"))
                .paidAmount(new BigDecimal("1000"))
                .balanceDue(BigDecimal.ZERO)
                .paymentStatus("paid")
                .build();

        Payment completedPayment = Payment.builder()
                .id("pay-done")
                .booking("book-paid")
                .user("user-1")
                .amount(new BigDecimal("1000"))
                .status("completed")
                .intentId("intent-done")
                .transactionId("tx-done-123")
                .build();

        when(paymentRepository.findByIntentId("intent-done")).thenReturn(Optional.of(completedPayment));
        when(bookingRepository.findById("book-paid")).thenReturn(Optional.of(booking));

        java.util.Map<String, Object> result = paymentService.confirmPayment("user-1", "intent-done", "tok_4242");

        assertEquals("completed", result.get("status"));
        assertEquals("tx-done-123", result.get("transactionId"));
        verify(paymentGateway, never()).confirmIntent(any(), any());
    }

    @Test
    void testGetPaymentStatusBlocksUnauthenticatedAccessForRegisteredUser() {
        Booking booking = Booking.builder()
                .id("book-reg")
                .user("user-owner-1")
                .status("confirmed")
                .build();
        Payment registeredPayment = Payment.builder()
                .id("pay-reg")
                .booking("book-reg")
                .user("user-owner-1")
                .status("completed")
                .intentId("intent-reg")
                .build();
        co.palaciodelmar.backend.model.User owner = co.palaciodelmar.backend.model.User.builder()
                .id("user-owner-1")
                .email("owner@palaciodelmar.co")
                .password("$2a$10$hashbcrypt")
                .build();

        when(paymentRepository.findByIntentId("intent-reg")).thenReturn(Optional.of(registeredPayment));
        when(bookingRepository.findById("book-reg")).thenReturn(Optional.of(booking));
        when(userRepository.findById("user-owner-1")).thenReturn(Optional.of(owner));

        assertThrows(IllegalArgumentException.class, () ->
                paymentService.getPaymentStatus("intent-reg", null)
        );
    }

    @Test
    void testCreatePaymentIntentBlocksUnauthenticatedIdempotencyReplayForRegisteredUser() {
        Payment registeredPayment = Payment.builder()
                .id("pay-idem")
                .booking("book-idem")
                .user("user-owner-1")
                .status("pending")
                .intentId("intent-idem")
                .idempotencyKey("idem-key-secret")
                .build();
        co.palaciodelmar.backend.model.User owner = co.palaciodelmar.backend.model.User.builder()
                .id("user-owner-1")
                .email("owner@palaciodelmar.co")
                .password("$2a$10$hashbcrypt")
                .build();

        when(paymentRepository.findByIdempotencyKey("idem-key-secret")).thenReturn(Optional.of(registeredPayment));
        when(userRepository.findById("user-owner-1")).thenReturn(Optional.of(owner));

        assertThrows(IllegalArgumentException.class, () ->
                paymentService.createPaymentIntent(null, "book-idem", "card", "idem-key-secret")
        );
    }
}
