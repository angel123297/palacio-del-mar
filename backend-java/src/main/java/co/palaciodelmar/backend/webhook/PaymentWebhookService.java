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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${palacio.webhooks.payments.secret:whsec_simulated_secret_key_palacio_2026}")
    private String webhookSecret;

    private static final long MAX_TOLERANCE_SECONDS = 300; // 5 minutos

    public Map<String, Object> processWebhook(String rawBody, String signature, String eventId, String timestampHeader) {
        // 1. Validar presencia de cabeceras requeridas
        if (signature == null || signature.isBlank()) {
            throw new SecurityException("Cabecera X-Palacio-Signature requerida");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Cabecera X-Palacio-Event-Id requerida");
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new IllegalArgumentException("Cabecera X-Palacio-Timestamp requerida");
        }

        // 2. Validar firma HMAC-SHA256
        if (!WebhookSigner.verify(rawBody, webhookSecret, signature)) {
            log.warn("Firma de webhook de pago inválida para evento: {}", eventId);
            throw new SecurityException("Firma de webhook inválida");
        }

        // 3. Validar timestamp (protección anti-replay)
        long eventTimestamp = parseTimestamp(timestampHeader);
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - eventTimestamp) > MAX_TOLERANCE_SECONDS) {
            log.warn("Timestamp de webhook fuera de tolerancia: {} (actual: {})", eventTimestamp, now);
            throw new IllegalArgumentException("Timestamp fuera de ventana permitida (anti-replay)");
        }

        // 4. Idempotencia: Verificar si el evento ya fue procesado
        if (processedWebhookEventRepository.existsByEventId(eventId)) {
            log.info("Evento de webhook ya procesado previamente (idempotencia): {}", eventId);
            return Map.of(
                    "status", "already_processed",
                    "idempotent", true,
                    "eventId", eventId
            );
        }

        // 5. Deserializar payload
        PaymentWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, PaymentWebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cuerpo del webhook con formato JSON inválido", e);
        }

        String eventType = payload.getEventType();
        PaymentWebhookPayload.PaymentWebhookData data = payload.getData();

        if (data == null || data.getIntentId() == null) {
            throw new IllegalArgumentException("El evento no contiene información del intento de pago (intentId)");
        }

        log.info("Procesando evento de webhook: {} tipo: {} para intentId: {}", eventId, eventType, data.getIntentId());

        // 6. Aplicar lógica de dominio según el tipo de evento
        switch (eventType) {
            case "payment.succeeded" -> handlePaymentSucceeded(data, eventId);
            case "payment.failed" -> handlePaymentFailed(data, eventId);
            case "refund.processed" -> handleRefundProcessed(data, eventId);
            default -> log.warn("Tipo de evento no manejado: {}", eventType);
        }

        // 7. Registrar evento procesado para garantizar idempotencia futura
        ProcessedWebhookEvent record = ProcessedWebhookEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .processedAt(Instant.now())
                .details("Procesado exitosamente para intentId: " + data.getIntentId())
                .build();
        processedWebhookEventRepository.save(record);

        return Map.of(
                "status", "processed",
                "idempotent", false,
                "eventId", eventId,
                "eventType", eventType
        );
    }

    private void handlePaymentSucceeded(PaymentWebhookPayload.PaymentWebhookData data, String eventId) {
        Payment payment = paymentRepository.findByIntentId(data.getIntentId()).orElse(null);
        if (payment == null) {
            log.warn("No se encontró Payment para intentId: {}", data.getIntentId());
            return;
        }

        if ("completed".equalsIgnoreCase(payment.getStatus())) {
            log.info("Payment {} ya estaba completado", payment.getId());
            return;
        }

        payment.setStatus("completed");
        payment.setTransactionId(data.getTransactionId());
        payment.setUpdatedAt(Instant.now());
        payment.setNotes("Pago confirmado exitosamente via webhook (" + eventId + ")");
        paymentRepository.save(payment);

        Booking booking = bookingRepository.findById(payment.getBooking()).orElse(null);
        if (booking == null) {
            log.warn("No se encontró Booking para el pago: {}", payment.getId());
            return;
        }

        BigDecimal currentPaid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(payment.getAmount());
        booking.setPaidAmount(newPaid);

        BigDecimal balance = booking.getTotalPrice().subtract(newPaid);
        booking.setBalanceDue(balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance);

        if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
            booking.setPaymentStatus("paid");
            bookingService.changeBookingStatus(booking, BookingStatus.CONFIRMED, "Webhook", "Pago completado via webhook");
            try {
                emailService.sendBookingConfirmation(booking);
            } catch (Exception e) {
                log.warn("Error enviando email de confirmación: {}", e.getMessage());
            }
        } else {
            booking.setPaymentStatus("partial");
        }

        bookingRepository.save(booking);
        log.info("Booking {} actualizado a status: {}, paymentStatus: {}", booking.getId(), booking.getStatus(), booking.getPaymentStatus());
    }

    private void handlePaymentFailed(PaymentWebhookPayload.PaymentWebhookData data, String eventId) {
        Payment payment = paymentRepository.findByIntentId(data.getIntentId()).orElse(null);
        if (payment != null) {
            payment.setStatus("failed");
            payment.setNotes(data.getFailureMessage() != null ? data.getFailureMessage() : "Pago rechazado por pasarela");
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            Booking booking = bookingRepository.findById(payment.getBooking()).orElse(null);
            if (booking != null && "pending".equalsIgnoreCase(booking.getStatus())) {
                booking.setPaymentStatus("failed");
                bookingRepository.save(booking);
            }
        }
    }

    private void handleRefundProcessed(PaymentWebhookPayload.PaymentWebhookData data, String eventId) {
        Payment payment = paymentRepository.findByIntentId(data.getIntentId()).orElse(null);
        if (payment != null) {
            payment.setStatus("refunded");
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);

            Booking booking = bookingRepository.findById(payment.getBooking()).orElse(null);
            if (booking != null) {
                BigDecimal currentRefunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal refundAmount = data.getAmount() != null ? data.getAmount() : payment.getAmount();
                booking.setRefundedAmount(currentRefunded.add(refundAmount));
                booking.setPaymentStatus("refunded");
                bookingRepository.save(booking);
            }
        }
    }

    private long parseTimestamp(String header) {
        try {
            // Intentar número de época (segundos o milisegundos)
            long val = Long.parseLong(header.trim());
            return val > 1000000000000L ? val / 1000 : val;
        } catch (NumberFormatException e) {
            // Intentar formato ISO Instant
            return Instant.parse(header.trim()).getEpochSecond();
        }
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}
