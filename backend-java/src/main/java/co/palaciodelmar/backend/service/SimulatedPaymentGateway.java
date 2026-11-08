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
import java.util.List;
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
    private final String instanceId = "pdm_gw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    public void setMongoTemplate(org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    private static final java.util.Set<String> FORBIDDEN_SECRETS = java.util.Set.of(
            "whsec_simulated_secret_key_palacio_2026",
            "super_secret_jwt_key_palacio_del_mar_2026_dev_min32chars",
            "pdm_internal_trusted_proxy_token_2026"
    );

    @Value("${palacio.webhooks.payments.secret}")
    private String webhookSecret;

    @jakarta.annotation.PostConstruct
    public void validateSecret() {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("PAYMENTS_WEBHOOK_SECRET es obligatorio en SimulatedPaymentGateway");
        }
        String trimmed = webhookSecret.trim();
        if (trimmed.length() < 32 || FORBIDDEN_SECRETS.contains(trimmed)) {
            throw new IllegalStateException("PAYMENTS_WEBHOOK_SECRET inseguro en SimulatedPaymentGateway: debe tener >= 32 caracteres y no usar el valor público");
        }
    }

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
        if ("refunded".equalsIgnoreCase(intent.getStatus()) || "partially_refunded".equalsIgnoreCase(intent.getStatus())) {
            return new PaymentConfirmResponse(true, intent.getTransactionId(), intent.getStatus(), null);
        }

        // Si ya está en processing:
        if ("processing".equalsIgnoreCase(intent.getStatus())) {
            if (inFlightIntents.contains(intentId)) {
                return new PaymentConfirmResponse(true, intent.getTransactionId(), "processing", null);
            }
            // Intento huérfano (ej. tras reinicio del servidor o tarea de scheduler perdida)
            if (tryClaimProcessingIntent(intent)) {
                log.info("Reclamado intento huérfano {} en confirmIntent. Re-agendando simulación...", intentId);
                scheduleSimulation(intentId);
            }
            return new PaymentConfirmResponse(true, intent.getTransactionId(), "processing", null);
        }

        if (!inFlightIntents.add(intentId)) {
            return new PaymentConfirmResponse(true, intent.getTransactionId(), "processing", null);
        }

        try {
            String safeToken = paymentToken != null ? paymentToken.trim() : "";
            boolean isFailure = false;
            String errorMessage = null;
            String outcome = "SUCCEEDED";

            // Evaluar reglas de prueba inmediatamente (sin persistir el token sensible en BD)
            if (safeToken.endsWith("0002")) {
                isFailure = true;
                errorMessage = "Tarjeta rechazada por el banco";
                outcome = "FAILED_DECLINED";
            } else if (safeToken.endsWith("9995")) {
                isFailure = true;
                errorMessage = "Fondos insuficientes";
                outcome = "FAILED_INSUFFICIENT_FUNDS";
            } else if (safeToken.endsWith("0069")) {
                isFailure = true;
                errorMessage = "Tarjeta vencida";
                outcome = "FAILED_EXPIRED";
            }

            String maskedToken = safeToken.length() >= 4 ? "tok_****" + safeToken.substring(safeToken.length() - 4) : "tok_****";

            intent.setStatus("processing");
            intent.setSimulationOutcome(outcome);
            intent.setFailureMessage(errorMessage);
            intent.setMaskedPaymentToken(maskedToken);
            intent.setPaymentToken(null); // No persistir el token completo
            intent.setClaimLock(instanceId);
            intent.setClaimExpiresAt(Instant.now().plusSeconds(60));
            intent.setUpdatedAt(Instant.now());
            paymentIntentRepository.save(intent);

            log.info("Intento de pago {} marcado en estado 'processing' (outcome: {}). Programando resolución asíncrona...", intentId, outcome);

            scheduleSimulation(intentId);

            return new PaymentConfirmResponse(true, null, "processing", null);
        } catch (RuntimeException e) {
            inFlightIntents.remove(intentId);
            throw e;
        }
    }

    private void scheduleSimulation(String intentId) {
        inFlightIntents.add(intentId);
        Runnable simulationTask = () -> {
            try {
                processIntentSimulation(intentId);
            } finally {
                inFlightIntents.remove(intentId);
            }
        };

        if (simulationDelaySeconds <= 0) {
            simulationTask.run();
        } else {
            scheduler.schedule(simulationTask, simulationDelaySeconds, TimeUnit.SECONDS);
        }
    }

    public boolean tryClaimProcessingIntent(PaymentIntent intent) {
        if (mongoTemplate != null) {
            org.springframework.data.mongodb.core.query.Query q = new org.springframework.data.mongodb.core.query.Query(
                    org.springframework.data.mongodb.core.query.Criteria.where("id").is(intent.getId())
                            .and("status").is("processing")
                            .orOperator(
                                    org.springframework.data.mongodb.core.query.Criteria.where("claimExpiresAt").exists(false),
                                    org.springframework.data.mongodb.core.query.Criteria.where("claimExpiresAt").is(null),
                                    org.springframework.data.mongodb.core.query.Criteria.where("claimExpiresAt").lte(Instant.now()),
                                    org.springframework.data.mongodb.core.query.Criteria.where("claimLock").is(instanceId)
                            )
            );
            org.springframework.data.mongodb.core.query.Update u = new org.springframework.data.mongodb.core.query.Update()
                    .set("claimLock", instanceId)
                    .set("claimExpiresAt", Instant.now().plusSeconds(60))
                    .set("updatedAt", Instant.now());
            PaymentIntent claimed = mongoTemplate.findAndModify(q, u, PaymentIntent.class);
            return claimed != null;
        } else {
            Instant now = Instant.now();
            if (intent.getClaimExpiresAt() == null || intent.getClaimExpiresAt().isBefore(now) || instanceId.equals(intent.getClaimLock())) {
                intent.setClaimLock(instanceId);
                intent.setClaimExpiresAt(now.plusSeconds(60));
                intent.setUpdatedAt(now);
                paymentIntentRepository.save(intent);
                return true;
            }
            return false;
        }
    }

    public void processIntentSimulation(String intentId, String paymentToken) {
        // Mantenido por compatibilidad con sobrecarga previa
        processIntentSimulation(intentId);
    }

    public void processIntentSimulation(String intentId) {
        try {
            PaymentIntent intent = paymentIntentRepository.findById(intentId).orElse(null);
            if (intent == null || !"processing".equalsIgnoreCase(intent.getStatus())) {
                return;
            }

            boolean isFailure = false;
            String errorMessage = intent.getFailureMessage();
            String outcome = intent.getSimulationOutcome();

            if ("FAILED_DECLINED".equals(outcome) || "FAILED_INSUFFICIENT_FUNDS".equals(outcome) || "FAILED_EXPIRED".equals(outcome)) {
                isFailure = true;
            }

            String txId = null;
            String eventType;
            if (isFailure) {
                intent.setStatus("failed");
                eventType = "payment.failed";
            } else {
                txId = "ch_" + UUID.randomUUID().toString().replace("-", "");
                intent.setStatus("succeeded");
                intent.setTransactionId(txId);
                eventType = "payment.succeeded";
            }

            intent.setClaimLock(null);
            intent.setClaimExpiresAt(null);
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
            intent.setWebhookDelivered(true);
            paymentIntentRepository.save(intent);

        } catch (Exception e) {
            log.error("Error durante simulación de pago para intent {}: {}", intentId, e.getMessage(), e);
        }
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverProcessingIntents() {
        try {
            List<PaymentIntent> pending = paymentIntentRepository.findByStatus("processing");
            if (pending == null || pending.isEmpty()) return;

            log.info("Recuperando {} intentos de pago en estado 'processing' tras arranque...", pending.size());
            for (PaymentIntent intent : pending) {
                if (intent.getId() != null && tryClaimProcessingIntent(intent)) {
                    log.info("Reclamado intent huérfano {} por instancia {}. Reanudando simulación...", intent.getId(), instanceId);
                    scheduleSimulation(intent.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error durante recuperación de intentos de pago en arranque: {}", e.getMessage(), e);
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
        return refund(transactionId, amount, null);
    }

    @Override
    public PaymentRefundResponse refund(String transactionId, BigDecimal amount, String idempotencyKey) {
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
                                .filter(pi -> "succeeded".equalsIgnoreCase(pi.getStatus()) || "partially_refunded".equalsIgnoreCase(pi.getStatus()))
                                .findFirst()
                                .or(() -> Optional.of(byBooking.get(0)));
                    }
                    return paymentIntentRepository.findByBookingId(candidateBookingId);
                })
                .orElse(null);

        if (intent == null) {
            return new PaymentRefundResponse(false, null, "failed", "Transacción no encontrada: " + transactionId);
        }

        if (!"succeeded".equalsIgnoreCase(intent.getStatus())
                && !"refunded".equalsIgnoreCase(intent.getStatus())
                && !"partially_refunded".equalsIgnoreCase(intent.getStatus())) {
            return new PaymentRefundResponse(false, null, "failed", "La transacción no está en estado exitoso (estado actual: " + intent.getStatus() + ")");
        }

        BigDecimal currentRefunded = intent.getRefundedAmount() != null ? intent.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = intent.getAmount().subtract(currentRefunded);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(remaining) > 0) {
            return new PaymentRefundResponse(false, null, "failed", "Monto de reembolso inválido");
        }

        String refundId = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? "re_" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 16)
                : "re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        BigDecimal newRefunded = currentRefunded.add(amount);
        intent.setRefundedAmount(newRefunded);
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
