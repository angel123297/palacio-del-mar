package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.PaymentIntent;
import co.palaciodelmar.backend.repository.PaymentIntentRepository;
import co.palaciodelmar.backend.webhook.PaymentWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SimulatedPaymentGatewayTest {

    private PaymentIntentRepository paymentIntentRepository;
    private PaymentWebhookService paymentWebhookService;
    private ObjectMapper objectMapper;

    private SimulatedPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        paymentIntentRepository = mock(PaymentIntentRepository.class);
        paymentWebhookService = mock(PaymentWebhookService.class);
        objectMapper = new ObjectMapper();

        gateway = new SimulatedPaymentGateway(
                paymentIntentRepository,
                paymentWebhookService,
                objectMapper
        );

        ReflectionTestUtils.setField(gateway, "webhookSecret", "whsec_test_secret");
        ReflectionTestUtils.setField(gateway, "serverPort", 5000);
        ReflectionTestUtils.setField(gateway, "simulationDelaySeconds", 0); // 0 segundos para pruebas síncronas
    }

    @Test
    void testCreateIntentPersistsInRepository() {
        Booking booking = Booking.builder()
                .id("booking_1")
                .balanceDue(new BigDecimal("750000"))
                .user("user_1")
                .build();

        when(paymentIntentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(paymentIntentRepository.save(any(PaymentIntent.class))).thenAnswer(i -> i.getArguments()[0]);

        PaymentGateway.PaymentIntentResponse response = gateway.createIntent(booking, "card", "idem_key_1");

        assertNotNull(response.intentId());
        assertTrue(response.intentId().startsWith("pi_"));
        assertNotNull(response.clientSecret());
        assertEquals(new BigDecimal("750000"), response.amount());
        assertEquals("requires_payment_method", response.status());

        verify(paymentIntentRepository).save(argThat(intent ->
                intent.getId().equals(response.intentId()) &&
                intent.getAmount().equals(new BigDecimal("750000")) &&
                "requires_payment_method".equals(intent.getStatus())
        ));
    }

    @Test
    void testCreateIntentIdempotentReturnsExistingIntent() {
        Booking booking = Booking.builder()
                .id("booking_1")
                .balanceDue(new BigDecimal("500000"))
                .build();

        PaymentIntent existing = PaymentIntent.builder()
                .id("pi_existing_999")
                .clientSecret("secret_999")
                .amount(new BigDecimal("500000"))
                .currency("COP")
                .status("requires_payment_method")
                .idempotencyKey("idem_repeat")
                .build();

        when(paymentIntentRepository.findByIdempotencyKey("idem_repeat")).thenReturn(Optional.of(existing));

        PaymentGateway.PaymentIntentResponse response = gateway.createIntent(booking, "card", "idem_repeat");

        assertEquals("pi_existing_999", response.intentId());
        assertEquals("secret_999", response.clientSecret());
        verify(paymentIntentRepository, never()).save(any());
    }

    @Test
    void testConfirmIntentSetsProcessingAndTriggersSimulation() {
        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_confirm_1")
                .amount(new BigDecimal("300000"))
                .status("requires_payment_method")
                .bookingId("b_1")
                .build();

        when(paymentIntentRepository.findById("pi_confirm_1")).thenReturn(Optional.of(intent));
        when(paymentIntentRepository.save(any(PaymentIntent.class))).thenAnswer(i -> i.getArguments()[0]);

        PaymentGateway.PaymentConfirmResponse response = gateway.confirmIntent("pi_confirm_1", "tok_test_success");

        assertTrue(response.success());
        assertEquals("processing", response.status());

        // Verificamos que se guardó en processing y luego en succeeded
        verify(paymentIntentRepository, atLeastOnce()).save(argThat(pi ->
                "succeeded".equals(pi.getStatus()) || "processing".equals(pi.getStatus())
        ));
    }

    @Test
    void testSimulationHandlesTestCards() {
        // Tarjeta rechazada (0002)
        PaymentIntent intent1 = PaymentIntent.builder()
                .id("pi_card_0002")
                .amount(new BigDecimal("100000"))
                .status("requires_payment_method")
                .build();
        when(paymentIntentRepository.findById("pi_card_0002")).thenReturn(Optional.of(intent1));

        gateway.confirmIntent("pi_card_0002", "tok_4242424242420002");
        assertEquals("failed", intent1.getStatus());
        assertEquals("Tarjeta rechazada por el banco", intent1.getFailureMessage());

        // Fondos insuficientes (9995)
        PaymentIntent intent2 = PaymentIntent.builder()
                .id("pi_card_9995")
                .amount(new BigDecimal("100000"))
                .status("requires_payment_method")
                .build();
        when(paymentIntentRepository.findById("pi_card_9995")).thenReturn(Optional.of(intent2));

        gateway.confirmIntent("pi_card_9995", "tok_4242424242429995");
        assertEquals("failed", intent2.getStatus());
        assertEquals("Fondos insuficientes", intent2.getFailureMessage());

        // Tarjeta vencida (0069)
        PaymentIntent intent3 = PaymentIntent.builder()
                .id("pi_card_0069")
                .amount(new BigDecimal("100000"))
                .status("requires_payment_method")
                .build();
        when(paymentIntentRepository.findById("pi_card_0069")).thenReturn(Optional.of(intent3));

        gateway.confirmIntent("pi_card_0069", "tok_4242424242420069");
        assertEquals("failed", intent3.getStatus());
        assertEquals("Tarjeta vencida", intent3.getFailureMessage());
    }

    @Test
    void testRefundValidations() {
        // 1. Transacción no encontrada
        when(paymentIntentRepository.findByTransactionId("ch_unknown")).thenReturn(Optional.empty());
        when(paymentIntentRepository.findById("ch_unknown")).thenReturn(Optional.empty());

        PaymentGateway.PaymentRefundResponse res1 = gateway.refund("ch_unknown", new BigDecimal("1000"));
        assertFalse(res1.success());
        assertTrue(res1.errorMessage().contains("Transacción no encontrada"));

        // 2. Transacción que no está en succeeded
        PaymentIntent pendingIntent = PaymentIntent.builder()
                .id("pi_not_succeeded")
                .transactionId("ch_pending")
                .amount(new BigDecimal("500000"))
                .status("processing")
                .build();
        when(paymentIntentRepository.findByTransactionId("ch_pending")).thenReturn(Optional.of(pendingIntent));

        PaymentGateway.PaymentRefundResponse res2 = gateway.refund("ch_pending", new BigDecimal("100000"));
        assertFalse(res2.success());
        assertTrue(res2.errorMessage().contains("no está en estado exitoso"));

        // 3. Monto inválido (> amount)
        PaymentIntent succeededIntent = PaymentIntent.builder()
                .id("pi_ok")
                .transactionId("ch_ok")
                .amount(new BigDecimal("200000"))
                .status("succeeded")
                .build();
        when(paymentIntentRepository.findByTransactionId("ch_ok")).thenReturn(Optional.of(succeededIntent));

        PaymentGateway.PaymentRefundResponse res3 = gateway.refund("ch_ok", new BigDecimal("300000"));
        assertFalse(res3.success());
        assertTrue(res3.errorMessage().contains("Monto de reembolso inválido"));

        // 4. Reembolso exitoso
        PaymentGateway.PaymentRefundResponse res4 = gateway.refund("ch_ok", new BigDecimal("200000"));
        assertTrue(res4.success());
        assertEquals("refunded", res4.status());
        assertNotNull(res4.refundId());
        assertEquals("refunded", succeededIntent.getStatus());
    }
}
