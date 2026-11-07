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

    @Test
    void testValidateSecretRejectsPublicDefaultBlankAndShortSecrets() {
        ReflectionTestUtils.setField(webhookService, "webhookSecret", "whsec_simulated_secret_key_palacio_2026");
        assertThrows(IllegalStateException.class, () -> webhookService.validateSecret(),
                "Debe fallar en el arranque si se usa el secreto simulado público");

        String rawBody = """
                {"eventId":"evt_forged_1","eventType":"payment.succeeded","data":{"intentId":"pi_1"}}
                """;
        String forgedSig = WebhookSigner.sign(rawBody, "whsec_simulated_secret_key_palacio_2026");
        String ts = String.valueOf(Instant.now().getEpochSecond());
        assertThrows(SecurityException.class, () ->
                webhookService.processWebhook(rawBody, forgedSig, "evt_forged_1", ts),
                "Debe rechazar en caliente cualquier webhook si el secreto activo es el público por defecto");

        ReflectionTestUtils.setField(webhookService, "webhookSecret", "   ");
        assertThrows(IllegalStateException.class, () -> webhookService.validateSecret());

        ReflectionTestUtils.setField(webhookService, "webhookSecret", "short_secret_123");
        assertThrows(IllegalStateException.class, () -> webhookService.validateSecret());

        ReflectionTestUtils.setField(webhookService, "webhookSecret", "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90");
        assertDoesNotThrow(() -> webhookService.validateSecret());
    }

    @Test
    void testA1BookingSaveFailureDoesNotLeavePaymentCompletedAndRetrySucceeds() {
        String eventId = "evt_a1_atomic";
        String intentId = "pi_a1_atomic";
        String bookingId = "book_a1_atomic";

        Payment payment = Payment.builder()
                .id("pay_a1")
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
        when(bookingRepository.save(any()))
                .thenThrow(new RuntimeException("Transient Mongo write error"))
                .thenAnswer(i -> i.getArguments()[0]);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String rawBody = """
                {
                  "eventId": "evt_a1_atomic",
                  "eventType": "payment.succeeded",
                  "timestamp": "%s",
                  "data": {
                    "intentId": "pi_a1_atomic",
                    "bookingId": "book_a1_atomic",
                    "amount": 500000,
                    "currency": "COP",
                    "status": "succeeded",
                    "transactionId": "ch_a1_tx"
                  }
                }
                """.formatted(Instant.now().toString());
        String sig = WebhookSigner.sign(rawBody, secret);

        // Intento 1 falla al guardar Booking: Payment NO debe quedar como completed ni enviarse emails
        assertThrows(RuntimeException.class, () ->
                webhookService.processWebhook(rawBody, sig, eventId, timestamp));
        assertNotEquals("completed", payment.getStatus(),
                "Si falla el guardado de Booking, Payment no debe sellarse como completed antes de tiempo");
        verify(emailService, never()).sendBookingConfirmation(any());
        verify(processedWebhookEventRepository, never()).save(any());

        // Reintento del webhook ahora logra acreditar Booking, marcar Payment completed y enviar correos
        Booking freshPending = Booking.builder()
                .id(bookingId)
                .status("pending")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("500000"))
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(freshPending));

        Map<String, Object> retryResult = webhookService.processWebhook(rawBody, sig, eventId, timestamp);
        assertEquals("processed", retryResult.get("status"));
        assertEquals("completed", payment.getStatus());
        assertEquals("paid", freshPending.getPaymentStatus());
        verify(emailService, times(1)).sendBookingConfirmation(freshPending);
    }

    @Test
    void testA3RefundProcessedIsIdempotentWithCancelBookingAndCapsAtPaidAmount() {
        String intentId = "pi_refund_a3";
        String bookingId = "book_refund_a3";

        Payment payment = Payment.builder()
                .id("pay_ref_1")
                .intentId(intentId)
                .booking(bookingId)
                .amount(new BigDecimal("500000"))
                .status("completed")
                .build();

        // Caso 1: cancelBooking ya registró refundedAmount = 450000 y dejó la reserva en cancelled
        Booking cancelledBooking = Booking.builder()
                .id(bookingId)
                .status("cancelled")
                .paymentStatus("partial")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(new BigDecimal("450000"))
                .build();

        when(processedWebhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(paymentRepository.findByIntentId(intentId)).thenReturn(Optional.of(payment));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(cancelledBooking));

        String ts = String.valueOf(Instant.now().getEpochSecond());
        String body1 = """
                {
                  "eventId": "evt_ref_1",
                  "eventType": "refund.processed",
                  "data": { "intentId": "pi_refund_a3", "bookingId": "book_refund_a3", "amount": 450000 }
                }
                """;
        webhookService.processWebhook(body1, WebhookSigner.sign(body1, secret), "evt_ref_1", ts);

        assertEquals(new BigDecimal("450000"), cancelledBooking.getRefundedAmount(),
                "No debe duplicar el reembolso ya contabilizado por cancelBooking");
        assertEquals("refunded", payment.getStatus());

        // Caso 2: Evento duplicado con otro eventId sobre Payment ya en status refunded -> no toca refundedAmount
        String body2 = """
                {
                  "eventId": "evt_ref_2",
                  "eventType": "refund.processed",
                  "data": { "intentId": "pi_refund_a3", "bookingId": "book_refund_a3", "amount": 450000 }
                }
                """;
        webhookService.processWebhook(body2, WebhookSigner.sign(body2, secret), "evt_ref_2", ts);
        assertEquals(new BigDecimal("450000"), cancelledBooking.getRefundedAmount());

        // Caso 3: Reembolso iniciado externamente con monto inflado se topea estrictamente a paidAmount
        Payment payment2 = Payment.builder()
                .id("pay_ref_ext")
                .intentId("pi_ext_cap")
                .booking("book_ext_cap")
                .amount(new BigDecimal("300000"))
                .status("completed")
                .build();
        Booking activePaidBooking = Booking.builder()
                .id("book_ext_cap")
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(new BigDecimal("300000"))
                .refundedAmount(BigDecimal.ZERO)
                .build();
        when(paymentRepository.findByIntentId("pi_ext_cap")).thenReturn(Optional.of(payment2));
        when(bookingRepository.findById("book_ext_cap")).thenReturn(Optional.of(activePaidBooking));

        String body3 = """
                {
                  "eventId": "evt_ref_cap",
                  "eventType": "refund.processed",
                  "data": { "intentId": "pi_ext_cap", "bookingId": "book_ext_cap", "amount": 9999999 }
                }
                """;
        webhookService.processWebhook(body3, WebhookSigner.sign(body3, secret), "evt_ref_cap", ts);
        assertEquals(new BigDecimal("300000"), activePaidBooking.getRefundedAmount(),
                "refundedAmount jamás debe superar paidAmount");
    }
}
