package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.PaymentIntent;
import co.palaciodelmar.backend.repository.PaymentIntentRepository;
import co.palaciodelmar.backend.webhook.PaymentWebhookPayload;
import co.palaciodelmar.backend.webhook.PaymentWebhookService;
import co.palaciodelmar.backend.webhook.WebhookSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SimulatedPaymentGateway implements PaymentGateway {

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentWebhookService paymentWebhookService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final HttpClient httpClient;
    private final java.util.Set<String> inFlightIntents = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Value("${palacio.webhooks.payments.secret:whsec_simulated_secret_key_palacio_2026}")
    private String webhookSecret = "whsec_simulated_secret_key_palacio_2026";

    @Value("${server.port:5000}")
    private int serverPort = 5000;

    @Value("${palacio.payments.simulation-delay-seconds:2}")
    private int simulationDelaySeconds = 2;

    public SimulatedPaymentGateway(
            PaymentIntentRepository paymentIntentRepository,
            @org.springframework.context.annotation.Lazy PaymentWebhookService paymentWebhookService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper
    ) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentWebhookService = paymentWebhookService;
        this.objectMapper = objectMapper != null ? objectMapper.copy().findAndRegisterModules() : new ObjectMapper().findAndRegisterModules();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public PaymentIntentResponse createIntent(Booking booking, String method, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PaymentIntent> existing = paymentIntentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                PaymentIntent intent = existing.get();
                return new PaymentIntentResponse(intent.getId(), intent.getClientSecret(), intent.getAmount(), intent.getCurrency(), intent.getStatus());
            }
        }

        String intentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = intentId + "_secret_" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal amount = booking.getBalanceDue();

        PaymentIntent intent = PaymentIntent.builder()
                .id(intentId)
                .clientSecret(clientSecret)
                .amount(amount)
                .currency("COP")
                .status("requires_payment_method")
                .bookingId(booking.getId())
                .userId(booking.getUser())
                .paymentMethod(method != null ? method : "card")
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        paymentIntentRepository.save(intent);
        log.info("Intento de pago persistido en MongoDB: {} por monto {}", intentId, amount);

        return new PaymentIntentResponse(intentId, clientSecret, amount, "COP", "requires_payment_method");
    }

    @Override
    public PaymentConfirmResponse confirmIntent(String intentId, String paymentToken) {
        PaymentIntent intent = paymentIntentRepository.findById(intentId).orElse(null);
        if (intent == null) {
            return new PaymentConfirmResponse(false, null, "failed", "Intent not found");
        }

        if ("succeeded".equalsIgnoreCase(intent.getStatus())) {
            return new PaymentConfirmResponse(true, intent.getTransactionId(), "succeeded", null);
        }
        if ("processing".equalsIgnoreCase(intent.getStatus()) || !inFlightIntents.add(intentId)) {
            return new PaymentConfirmResponse(true, intent.getTransactionId(), "processing", null);
        }

        try {
            String safeToken = paymentToken != null ? paymentToken : "";
            intent.setStatus("processing");
            intent.setPaymentToken(safeToken);
            intent.setUpdatedAt(Instant.now());
            paymentIntentRepository.save(intent);

            log.info("Intento de pago {} marcado en estado 'processing'. Programando resolución asíncrona...", intentId);

            Runnable simulationTask = () -> {
                try {
                    processIntentSimulation(intentId, safeToken);
                } finally {
                    inFlightIntents.remove(intentId);
                }
            };

            if (simulationDelaySeconds <= 0) {
                simulationTask.run();
            } else {
                scheduler.schedule(simulationTask, simulationDelaySeconds, TimeUnit.SECONDS);
            }

            return new PaymentConfirmResponse(true, null, "processing", null);
        } catch (RuntimeException e) {
            inFlightIntents.remove(intentId);
            throw e;
        }
    }

    public void processIntentSimulation(String intentId, String paymentToken) {
        try {
            PaymentIntent intent = paymentIntentRepository.findById(intentId).orElse(null);
            if (intent == null) return;

            boolean isFailure = false;
            String errorMessage = null;

            // Tarjetas de prueba simuladas
            if (paymentToken.endsWith("0002")) {
                isFailure = true;
                errorMessage = "Tarjeta rechazada por el banco";
            } else if (paymentToken.endsWith("9995")) {
                isFailure = true;
                errorMessage = "Fondos insuficientes";
            } else if (paymentToken.endsWith("0069")) {
                isFailure = true;
                errorMessage = "Tarjeta vencida";
            }

            String txId = null;
            String eventType;
            if (isFailure) {
                intent.setStatus("failed");
                intent.setFailureMessage(errorMessage);
                eventType = "payment.failed";
            } else {
                txId = "ch_" + UUID.randomUUID().toString().replace("-", "");
                intent.setStatus("succeeded");
                intent.setTransactionId(txId);
                eventType = "payment.succeeded";
            }

            intent.setUpdatedAt(Instant.now());
            paymentIntentRepository.save(intent);

            // Generar y emitir el webhook
            String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
            PaymentWebhookPayload payload = PaymentWebhookPayload.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .timestamp(Instant.now().toString())
                    .data(PaymentWebhookPayload.PaymentWebhookData.builder()
                            .intentId(intentId)
                            .bookingId(intent.getBookingId())
                            .amount(intent.getAmount())
                            .currency(intent.getCurrency())
                            .status(isFailure ? "failed" : "succeeded")
                            .transactionId(txId)
                            .failureMessage(errorMessage)
                            .build())
                    .build();

            dispatchWebhook(payload, eventId);

        } catch (Exception e) {
            log.error("Error durante simulación de pago para intent {}: {}", intentId, e.getMessage(), e);
        }
    }

    private void dispatchWebhook(PaymentWebhookPayload payload, String eventId) {
        try {
            String rawJson = objectMapper.writeValueAsString(payload);
            String signature = WebhookSigner.sign(rawJson, webhookSecret);
            String timestamp = String.valueOf(Instant.now().getEpochSecond());

            if (simulationDelaySeconds > 0 && serverPort > 0) {
                String webhookUrl = "http://localhost:" + serverPort + "/api/webhooks/payments";
                log.info("Emitiendo webhook asíncrono para evento {} a {}", eventId, webhookUrl);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .header("X-Palacio-Signature", signature)
                        .header("X-Palacio-Event-Id", eventId)
                        .header("X-Palacio-Timestamp", timestamp)
                        .POST(HttpRequest.BodyPublishers.ofString(rawJson))
                        .timeout(Duration.ofSeconds(4))
                        .build();

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        log.info("Webhook {} entregado exitosamente (HTTP {})", eventId, response.statusCode());
                        return;
                    }
                    log.debug("Webhook respondió status {}. Ejecutando procesamiento interno...", response.statusCode());
                } catch (Exception httpEx) {
                    log.debug("Despachando webhook {} mediante canal interno directo", eventId);
                }
            }

            // Procesamiento interno garantizado (contrato idéntico: verifica firma HMAC, timestamp e idempotencia)
            paymentWebhookService.processWebhook(rawJson, signature, eventId, timestamp);

        } catch (Exception e) {
            log.error("Error despachando webhook para evento {}: {}", eventId, e.getMessage(), e);
        }
    }

    @Override
    public PaymentRefundResponse refund(String transactionId, BigDecimal amount) {
        if (transactionId == null || transactionId.isBlank()) {
            return new PaymentRefundResponse(false, null, "failed", "ID de transacción requerido");
        }

        PaymentIntent intent = paymentIntentRepository.findByTransactionId(transactionId)
                .or(() -> paymentIntentRepository.findById(transactionId))
                .or(() -> {
                    String candidateBookingId = transactionId.startsWith("tx_")
                            ? transactionId.substring(3)
                            : transactionId;
                    java.util.List<PaymentIntent> byBooking = paymentIntentRepository.findAllByBookingId(candidateBookingId);
                    if (byBooking != null && !byBooking.isEmpty()) {
                        return byBooking.stream()
                                .filter(pi -> "succeeded".equalsIgnoreCase(pi.getStatus()))
                                .findFirst()
                                .or(() -> Optional.of(byBooking.get(0)));
                    }
                    return paymentIntentRepository.findByBookingId(candidateBookingId);
                })
                .orElse(null);

        if (intent == null) {
            return new PaymentRefundResponse(false, null, "failed", "Transacción no encontrada: " + transactionId);
        }

        if (!"succeeded".equalsIgnoreCase(intent.getStatus())) {
            return new PaymentRefundResponse(false, null, "failed", "La transacción no está en estado exitoso (estado actual: " + intent.getStatus() + ")");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(intent.getAmount()) > 0) {
            return new PaymentRefundResponse(false, null, "failed", "Monto de reembolso inválido");
        }

        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        intent.setStatus("refunded");
        intent.setUpdatedAt(Instant.now());
        paymentIntentRepository.save(intent);

        // Emitir webhook refund.processed
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        PaymentWebhookPayload payload = PaymentWebhookPayload.builder()
                .eventId(eventId)
                .eventType("refund.processed")
                .timestamp(Instant.now().toString())
                .data(PaymentWebhookPayload.PaymentWebhookData.builder()
                        .intentId(intent.getId())
                        .bookingId(intent.getBookingId())
                        .amount(amount)
                        .currency(intent.getCurrency())
                        .status("refunded")
                        .transactionId(transactionId)
                        .failureMessage(null)
                        .build())
                .build();

        scheduler.execute(() -> dispatchWebhook(payload, eventId));

        return new PaymentRefundResponse(true, refundId, "refunded", null);
    }

    @PreDestroy
    public void shutdown() {
        try {
            scheduler.shutdown();
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
