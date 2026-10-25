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
import co.palaciodelmar.backend.service.PaymentGateway;
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
public class PaymentWebhookService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final ProcessedWebhookEventRepository processedWebhookEventRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PaymentGateway paymentGateway;

    @org.springframework.beans.factory.annotation.Autowired

    public PaymentWebhookService(

            PaymentRepository paymentRepository,

            BookingRepository bookingRepository,
            @org.springframework.context.annotation.Lazy BookingService bookingService,
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            EmailService emailService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper
    ) {
        this(paymentRepository, bookingRepository, bookingService, processedWebhookEventRepository, emailService, objectMapper, null);
    }

    public PaymentWebhookService(
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            @org.springframework.context.annotation.Lazy BookingService bookingService,
            ProcessedWebhookEventRepository processedWebhookEventRepository,
            EmailService emailService,
            ObjectMapper objectMapper,
            PaymentGateway paymentGateway
    ) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.processedWebhookEventRepository = processedWebhookEventRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.paymentGateway = paymentGateway;
    }

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

        Booking booking = bookingRepository.findById(payment.getBooking()).orElse(null);
        if (booking == null) {
            log.warn("No se encontró Booking para el pago: {}", payment.getId());
            payment.setStatus("requires_refund");
            payment.setTransactionId(data.getTransactionId());
            payment.setUpdatedAt(Instant.now());
            payment.setNotes("Pago huérfano sin reserva asociada (" + payment.getBooking() + "). Requiere verificación manual.");
            paymentRepository.save(payment);
            return;
        }

        BookingStatus currentStatus = null;
        if (booking.getStatus() != null) {
            try {
                currentStatus = BookingStatus.valueOf(booking.getStatus().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        boolean canConfirm = (currentStatus != null && currentStatus.canTransitionTo(BookingStatus.CONFIRMED));
        boolean alreadyConfirmed = (currentStatus == BookingStatus.CONFIRMED);

        if (!canConfirm && !alreadyConfirmed) {
            payment.setTransactionId(data.getTransactionId());
            payment.setUpdatedAt(Instant.now());

            boolean refundSuccess = false;
            String refundDetails = "";
            if (paymentGateway != null && data.getTransactionId() != null && payment.getAmount() != null) {
                try {
                    PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(data.getTransactionId(), payment.getAmount());
                    if (refundResp != null && refundResp.success()) {
                        refundSuccess = true;
                        refundDetails = " Reembolso automático exitoso (ID: " + refundResp.refundId() + ").";
                    } else {
                        refundDetails = " Falló pasarela: " + (refundResp != null ? refundResp.errorMessage() : "respuesta nula");
                    }
                } catch (Exception e) {
                    log.error("Excepción al ejecutar reembolso automático para pago {}: {}", payment.getId(), e.getMessage());
                    refundDetails = " Excepción en reembolso: " + e.getMessage();
                }
            }

            if (refundSuccess) {
                payment.setStatus("refunded");
                payment.setNotes("Pago recibido para reserva en estado " + booking.getStatus() + " (" + booking.getId() + ")." + refundDetails);
            } else {
                payment.setStatus("requires_refund");
                payment.setNotes("Pago recibido para reserva en estado " + booking.getStatus() + " (" + booking.getId() + "). Requiere reembolso manual." + refundDetails);
            }

            paymentRepository.save(payment);
            log.warn("Pago {} actualizado a status {} para reserva {} en {}", payment.getId(), payment.getStatus(), booking.getId(), booking.getStatus());
            return;
        }

        payment.setStatus("completed");
        payment.setTransactionId(data.getTransactionId());
        payment.setUpdatedAt(Instant.now());
        payment.setNotes("Pago confirmado exitosamente via webhook (" + eventId + ")");
        paymentRepository.save(payment);

        BigDecimal currentPaid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(payment.getAmount());
        booking.setPaidAmount(newPaid);

        BigDecimal balance = booking.getTotalPrice().subtract(newPaid);
        booking.setBalanceDue(balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance);

        if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
            booking.setPaymentStatus("paid");
            if (canConfirm) {
                bookingService.changeBookingStatus(booking, BookingStatus.CONFIRMED, "Webhook", "Pago completado via webhook");
                try {
                    emailService.sendBookingConfirmation(booking);
                } catch (Exception e) {
                    log.warn("Error enviando email de confirmación: {}", e.getMessage());
                }
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
