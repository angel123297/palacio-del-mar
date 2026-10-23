package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.model.ProcessedWebhookEvent;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository;
import co.palaciodelmar.backend.service.BookingService;
import co.palaciodelmar.backend.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentWebhookServiceTest {

    private PaymentRepository paymentRepository;
    private BookingRepository bookingRepository;
    private BookingService bookingService;
    private ProcessedWebhookEventRepository processedWebhookEventRepository;
    private EmailService emailService;
    private ObjectMapper objectMapper;
    private co.palaciodelmar.backend.service.PaymentGateway paymentGateway;

    private PaymentWebhookService webhookService;
    private final String secret = "test_webhook_secret_key_123456";

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingService = mock(BookingService.class);
        processedWebhookEventRepository = mock(ProcessedWebhookEventRepository.class);
        emailService = mock(EmailService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        paymentGateway = mock(co.palaciodelmar.backend.service.PaymentGateway.class);

        webhookService = new PaymentWebhookService(
                paymentRepository,
                bookingRepository,
                bookingService,
                processedWebhookEventRepository,
                emailService,
                objectMapper,
                paymentGateway
        );

        ReflectionTestUtils.setField(webhookService, "webhookSecret", secret);
    }

    @Test
    void testValidWebhookPaymentSucceededConfirmsBooking() {
        String eventId = "evt_test_1001";
        String intentId = "pi_test_intent";
        String bookingId = "book_test_123";

        Payment payment = Payment.builder()
                .id("pay_01")
                .intentId(intentId)
                .booking(bookingId)
                .amount(new BigDecimal("500000"))
                .currency("COP")
                .status("processing")
                .build();

        Booking booking = Booking.builder()
                .id(bookingId)
                .status("pending")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("500000"))
                .build();

        when(processedWebhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(paymentRepository.findByIntentId(intentId)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(bookingRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String rawBody = """
                {
                  "eventId": "evt_test_1001",
                  "eventType": "payment.succeeded",
                  "timestamp": "%s",
                  "data": {
                    "intentId": "pi_test_intent",
                    "bookingId": "book_test_123",
                    "amount": 500000,
                    "currency": "COP",
                    "status": "succeeded",
                    "transactionId": "ch_tx_9999"
                  }
                }
                """.formatted(Instant.now().toString());

        String validSignature = WebhookSigner.sign(rawBody, secret);

        Map<String, Object> result = webhookService.processWebhook(rawBody, validSignature, eventId, timestamp);

        assertEquals("processed", result.get("status"));
        assertEquals(false, result.get("idempotent"));
        assertEquals("completed", payment.getStatus());
        assertEquals("ch_tx_9999", payment.getTransactionId());

        assertEquals(new BigDecimal("500000"), booking.getPaidAmount());
        assertEquals(BigDecimal.ZERO, booking.getBalanceDue());
        assertEquals("paid", booking.getPaymentStatus());

        verify(bookingService).changeBookingStatus(eq(booking), eq(BookingStatus.CONFIRMED), eq("Webhook"), anyString());
        verify(emailService).sendBookingConfirmation(booking);
        verify(processedWebhookEventRepository).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testInvalidSignatureThrowsSecurityExceptionAndPreservesState() {
        String eventId = "evt_bad_sig";
        String rawBody = """
                {"eventId": "evt_bad_sig", "eventType": "payment.succeeded"}
                """;
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        SecurityException ex = assertThrows(SecurityException.class, () ->
                webhookService.processWebhook(rawBody, "invalid_sig_abc123", eventId, timestamp)
        );

        assertEquals("Firma de webhook inválida", ex.getMessage());
        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void testReplayProtectionRejectsOldTimestamp() {
        String eventId = "evt_replay";
        String rawBody = """
                {"eventId": "evt_replay", "eventType": "payment.succeeded"}
                """;
        String validSignature = WebhookSigner.sign(rawBody, secret);

        // Timestamp de hace 10 minutos (600 segundos)
        String oldTimestamp = String.valueOf(Instant.now().getEpochSecond() - 600);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                webhookService.processWebhook(rawBody, validSignature, eventId, oldTimestamp)
        );

        assertTrue(ex.getMessage().contains("anti-replay"));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void testIdempotencyReturnsEarlyWithoutDuplicateEffects() {
        String eventId = "evt_duplicate_idempotency";
        String rawBody = """
                {
                  "eventId": "evt_duplicate_idempotency",
                  "eventType": "payment.succeeded",
                  "data": { "intentId": "pi_123" }
                }
                """;
        String validSignature = WebhookSigner.sign(rawBody, secret);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        when(processedWebhookEventRepository.existsByEventId(eventId)).thenReturn(true);

        Map<String, Object> result = webhookService.processWebhook(rawBody, validSignature, eventId, timestamp);

        assertEquals("already_processed", result.get("status"));
        assertEquals(true, result.get("idempotent"));
        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(paymentRepository);
        verify(processedWebhookEventRepository, never()).save(any());
    }

    @Test
    void duplicateKeyWhenSavingEventReturnsAlreadyProcessed() {
        String eventId = "evt_concurrent_duplicate";
        String rawBody = """
                {
                  "eventId": "evt_concurrent_duplicate",
                  "eventType": "payment.event_without_effects",
                  "data": { "intentId": "pi_duplicate" }
                }
                """;
        String signature = WebhookSigner.sign(rawBody, secret);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        when(processedWebhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(processedWebhookEventRepository.save(any(ProcessedWebhookEvent.class)))
                .thenThrow(new DuplicateKeyException("Duplicate eventId"));

        Map<String, Object> result = webhookService.processWebhook(rawBody, signature, eventId, timestamp);

        assertEquals("already_processed", result.get("status"));
        assertEquals(true, result.get("idempotent"));
        assertEquals(eventId, result.get("eventId"));
        verify(processedWebhookEventRepository).save(any(ProcessedWebhookEvent.class));
        verifyNoInteractions(bookingRepository, paymentRepository, bookingService, emailService);
    }

    @Test
    void testPaymentFailedWebhookUpdatesPaymentAndBooking() {
        String eventId = "evt_fail_1";
        String intentId = "pi_fail";
        String bookingId = "book_fail";

        Payment payment = Payment.builder()
                .id("pay_fail")
                .intentId(intentId)
                .booking(bookingId)
                .status("processing")
                .build();

        Booking booking = Booking.builder()
                .id(bookingId)
                .status("pending")
                .paymentStatus("pending")
                .build();

        when(processedWebhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(paymentRepository.findByIntentId(intentId)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        String rawBody = """
                {
                  "eventId": "evt_fail_1",
                  "eventType": "payment.failed",
                  "timestamp": "%s",
                  "data": {
                    "intentId": "pi_fail",
                    "bookingId": "book_fail",
                    "status": "failed",
                    "failureMessage": "Tarjeta rechazada por el banco"
                  }
                }
                """.formatted(Instant.now().toString());

        String signature = WebhookSigner.sign(rawBody, secret);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        Map<String, Object> result = webhookService.processWebhook(rawBody, signature, eventId, timestamp);

        assertEquals("processed", result.get("status"));
        assertEquals("failed", payment.getStatus());
        assertEquals("Tarjeta rechazada por el banco", payment.getNotes());
        assertEquals("failed", booking.getPaymentStatus());
        assertEquals("pending", booking.getStatus()); // no se confirma
    }

    @Test
    void testLatePaymentSucceededOnExpiredBookingExecutesAutomaticRefund() {
        String eventId = "evt_late_expired";
        String intentId = "pi_late_expired";
        String bookingId = "book_expired_999";

        Payment payment = Payment.builder()
                .id("pay_expired")
                .intentId(intentId)
                .booking(bookingId)
                .amount(new BigDecimal("450000"))
                .currency("COP")
                .status("processing")
                .build();

        Booking booking = Booking.builder()
                .id(bookingId)
                .status("expired")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("450000"))
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("450000"))
                .build();

        when(processedWebhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(paymentRepository.findByIntentId(intentId)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(paymentGateway.refund("tx_late_999", new BigDecimal("450000")))
                .thenReturn(new co.palaciodelmar.backend.service.PaymentGateway.PaymentRefundResponse(true, "ref_auto_123", "succeeded", null));

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String rawBody = """
                {
                  "eventId": "evt_late_expired",
                  "eventType": "payment.succeeded",
                  "timestamp": "%s",
                  "data": {
                    "intentId": "pi_late_expired",
                    "bookingId": "book_expired_999",
                    "amount": 450000,
                    "currency": "COP",
                    "status": "succeeded",
                    "transactionId": "tx_late_999"
                  }
                }
                """.formatted(Instant.now().toString());

        String signature = WebhookSigner.sign(rawBody, secret);

        Map<String, Object> result = webhookService.processWebhook(rawBody, signature, eventId, timestamp);

        assertEquals("processed", result.get("status"));
        assertEquals("refunded", payment.getStatus());
        assertTrue(payment.getNotes().contains("Reembolso automático exitoso"));
        assertEquals("expired", booking.getStatus());
        verify(paymentGateway).refund("tx_late_999", new BigDecimal("450000"));
        verify(bookingService, never()).changeBookingStatus(any(), any(), any(), any());
        verify(emailService, never()).sendBookingConfirmation(any());
        verify(processedWebhookEventRepository).save(any(ProcessedWebhookEvent.class));
    }

    @Test
    void testLatePaymentSucceededOnCancelledBookingWhenGatewayFailsFallsBackToRequiresRefund() {
        String eventId = "evt_late_cancelled";
        String intentId = "pi_late_cancelled";
        String bookingId = "book_cancelled_888";

        Payment payment = Payment.builder()
                .id("pay_cancelled")
                .intentId(intentId)
                .booking(bookingId)
                .amount(new BigDecimal("300000"))
                .currency("COP")
                .status("processing")
                .build();

        Booking booking = Booking.builder()
                .id(bookingId)
                .status("cancelled")
                .paymentStatus("refunded")
                .totalPrice(new BigDecimal("300000"))
                .build();

        when(processedWebhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(paymentRepository.findByIntentId(intentId)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
        when(paymentGateway.refund("tx_late_888", new BigDecimal("300000")))
                .thenReturn(new co.palaciodelmar.backend.service.PaymentGateway.PaymentRefundResponse(false, null, "failed", "Saldo insuficiente en cuenta de liquidación"));

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String rawBody = """
                {
                  "eventId": "evt_late_cancelled",
                  "eventType": "payment.succeeded",
                  "timestamp": "%s",
                  "data": {
                    "intentId": "pi_late_cancelled",
                    "bookingId": "book_cancelled_888",
                    "amount": 300000,
                    "currency": "COP",
                    "status": "succeeded",
                    "transactionId": "tx_late_888"
                  }
                }
                """.formatted(Instant.now().toString());

        String signature = WebhookSigner.sign(rawBody, secret);

        Map<String, Object> result = webhookService.processWebhook(rawBody, signature, eventId, timestamp);

        assertEquals("processed", result.get("status"));
        assertEquals("requires_refund", payment.getStatus());
        assertTrue(payment.getNotes().contains("Requiere reembolso manual"));
        assertEquals("cancelled", booking.getStatus());
        verify(paymentGateway).refund("tx_late_888", new BigDecimal("300000"));
        verify(bookingService, never()).changeBookingStatus(any(), any(), any(), any());
    }
}
