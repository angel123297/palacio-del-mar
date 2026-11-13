package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentConcurrencyTest {

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
    void confirmPaymentCatchesOptimisticLockingAndMarksPaymentFailed() {
        Payment payment = Payment.builder()
                .id("pay-1")
                .booking("book-1")
                .amount(new BigDecimal("500000"))
                .currency("COP")
                .status("pending")
                .intentId("intent-123")
                .createdAt(Instant.now())
                .build();

        Booking booking = Booking.builder()
                .id("book-1")
                .user("user-1")
                .status("pending")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("500000"))
                .version(1L)
                .build();

        when(paymentRepository.findByIntentId("intent-123")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("book-1")).thenReturn(Optional.of(booking));
        when(paymentGateway.confirmIntent(eq("intent-123"), any())).thenReturn(
                new PaymentGateway.PaymentConfirmResponse(true, "tx-999", "succeeded", null)
        );

        // Simulate concurrent modification conflict on booking
        when(bookingRepository.save(any(Booking.class))).thenThrow(
                new OptimisticLockingFailureException("Version conflict: Booking modified concurrently")
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                paymentService.confirmPayment("user-1", "intent-123", "tok-abc")
        );

        assertEquals("Conflicto al procesar el pago. La reserva fue modificada o cancelada simultáneamente.", ex.getMessage());
        assertEquals("requires_refund", payment.getStatus());
        assertEquals("tx-999", payment.getTransactionId());
        verify(paymentGateway).refund("tx-999", new BigDecimal("500000"));
        verify(paymentRepository).save(payment);
    }

    @Test
    void confirmPaymentDoesNotOverwriteAlreadyCompletedPaymentFromAsyncWebhook() {
        // Initial pending payment retrieved by intentId
        Payment payment = Payment.builder()
                .id("pay-webhook-race")
                .booking("book-race")
                .amount(new BigDecimal("500000"))
                .currency("COP")
                .status("pending")
                .intentId("intent-race")
                .createdAt(Instant.now())
                .build();

        // Payment in DB is already "completed" by concurrent webhook
        Payment completedInDb = Payment.builder()
                .id("pay-webhook-race")
                .booking("book-race")
                .amount(new BigDecimal("500000"))
                .currency("COP")
                .status("completed")
                .intentId("intent-race")
                .transactionId("tx-webhook-777")
                .createdAt(Instant.now())
                .build();

        Booking booking = Booking.builder()
                .id("book-race")
                .user("user-race")
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .balanceDue(BigDecimal.ZERO)
                .build();

        when(paymentRepository.findByIntentId("intent-race")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("book-race")).thenReturn(Optional.of(booking));
        when(paymentRepository.findById("pay-webhook-race")).thenReturn(Optional.of(completedInDb));

        java.util.Map<String, Object> result = paymentService.confirmPayment("user-race", "intent-race", "tok-xyz");

        assertEquals("completed", result.get("status"));
        assertEquals("tx-webhook-777", result.get("transactionId"));
        // Ensure gateway was NEVER called and status was NOT regressed to processing
        verify(paymentGateway, never()).confirmIntent(any(), any());
        verify(paymentRepository, never()).save(argThat(p -> "processing".equals(p.getStatus())));
    }
}
