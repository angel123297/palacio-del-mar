package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.event.BookingDomainEvent;
import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class WebhookDispatcher {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${palacio.webhooks.retry.backoff-attempt-1:60}")
    private long backoffAttempt1Seconds = 60; // 1 minuto por defecto

    @Value("${palacio.webhooks.retry.backoff-attempt-2:300}")
    private long backoffAttempt2Seconds = 300; // 5 minutos por defecto

    @Value("${palacio.webhooks.retry.backoff-attempt-3:1800}")
    private long backoffAttempt3Seconds = 1800; // 30 minutos por defecto

    public WebhookDispatcher(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryRepository deliveryRepository,
            ObjectMapper objectMapper
    ) {
        this(subscriptionRepository, deliveryRepository, objectMapper, null);
    }

    public WebhookDispatcher(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryRepository deliveryRepository,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper != null ? objectMapper.copy().findAndRegisterModules() : new ObjectMapper().findAndRegisterModules();
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    @EventListener
    public void onBookingDomainEvent(BookingDomainEvent event) {
        if (event == null || event.getPropertyId() == null) {
            return;
        }

        log.info("Recibido evento de dominio: {} para propiedad: {}", event.getEventType(), event.getPropertyId());
        dispatchToProperty(event.getPropertyId(), event.getEventType(), event.getBooking());
    }

    public void dispatchToProperty(String propertyId, String eventType, Object data) {
        List<WebhookSubscription> activeSubscriptions = subscriptionRepository.findByPropertyIdAndActiveTrue(propertyId);
        if (activeSubscriptions.isEmpty()) {
            log.debug("No hay suscripciones activas de webhook para propiedad: {}", propertyId);
            return;
        }

        for (WebhookSubscription sub : activeSubscriptions) {
            if (isSubscribed(sub, eventType)) {
                createAndExecuteDelivery(sub, eventType, data);
            }
        }
    }

    public WebhookDelivery createAndExecuteDelivery(WebhookSubscription sub, String eventType, Object data) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String payloadJson = buildPayloadJson(eventId, eventType, sub.getPropertyId(), data);

        WebhookDelivery delivery = WebhookDelivery.builder()
                .subscriptionId(sub.getId())
                .propertyId(sub.getPropertyId())
                .ownerId(sub.getOwnerId())
                .eventId(eventId)
                .eventType(eventType)
                .targetUrl(sub.getUrl())
                .payload(payloadJson)
                .attempts(0)
                .maxAttempts(3)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        WebhookDelivery saved = deliveryRepository.save(delivery);
        executeDelivery(saved, sub.getSecret());
        return saved;
    }

    public void executeDelivery(WebhookDelivery delivery, String secret) {
        delivery.setAttempts(delivery.getAttempts() + 1);

        String signature = WebhookSigner.sign(delivery.getPayload(), secret != null ? secret : "secret");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        log.info("Despachando webhook {} (intento {}/{}) hacia {}",
                delivery.getEventId(), delivery.getAttempts(), delivery.getMaxAttempts(), delivery.getTargetUrl());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(delivery.getTargetUrl()))
                .header("Content-Type", "application/json")
                .header("X-Palacio-Signature", signature != null ? signature : "")
                .header("X-Palacio-Event-Id", delivery.getEventId() != null ? delivery.getEventId() : "")
                .header("X-Palacio-Event-Type", delivery.getEventType() != null ? delivery.getEventType() : "")
                .header("X-Palacio-Timestamp", timestamp)
                .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload() != null ? delivery.getPayload() : "{}"))
                .timeout(Duration.ofSeconds(4))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            delivery.setResponseCode(code);
            delivery.setResponseBody(truncate(response.body(), 1000));

            if (code >= 200 && code < 300) {
                delivery.setStatus("SUCCESS");
                delivery.setDeliveredAt(Instant.now());
                delivery.setNextRetryAt(null);
                delivery.setErrorMessage(null);
                log.info("Webhook {} entregado con éxito (HTTP {})", delivery.getEventId(), code);
            } else {
                handleDeliveryFailure(delivery, "HTTP status " + code);
            }
        } catch (Exception e) {
            log.warn("Fallo en entrega de webhook {}: {}", delivery.getEventId(), e.getMessage());
            handleDeliveryFailure(delivery, e.getMessage());
        }

        deliveryRepository.save(delivery);
    }

    private void handleDeliveryFailure(WebhookDelivery delivery, String errorMsg) {
        delivery.setErrorMessage(errorMsg);
        if (delivery.getAttempts() < delivery.getMaxAttempts()) {
            delivery.setStatus("PENDING");
            long backoffSeconds = getBackoffSeconds(delivery.getAttempts());
            delivery.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            log.info("Webhook {} programado para reintento en {}s (intento {})", delivery.getEventId(), backoffSeconds, delivery.getAttempts());
        } else {
            delivery.setStatus("FAILED");
            delivery.setNextRetryAt(null);
            log.warn("Webhook {} ha agotado sus {} intentos. Marcado como FAILED", delivery.getEventId(), delivery.getMaxAttempts());
        }
    }

    public long getBackoffSeconds(int attempt) {
        return switch (attempt) {
            case 1 -> backoffAttempt1Seconds; // 1m
            case 2 -> backoffAttempt2Seconds; // 5m
            default -> backoffAttempt3Seconds; // 30m
        };
    }

    private boolean isSubscribed(WebhookSubscription sub, String eventType) {
        if (sub.getEvents() == null || sub.getEvents().isEmpty()) {
            return true; // Si no especifica eventos, suscribe a todos
        }
        for (String ev : sub.getEvents()) {
            if ("*".equals(ev) || ev.equalsIgnoreCase(eventType)) {
                return true;
            }
        }
        return false;
    }

    private String buildPayloadJson(String eventId, String eventType, String propertyId, Object data) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("eventId", eventId);
        payloadMap.put("eventType", eventType);
        payloadMap.put("propertyId", propertyId);
        payloadMap.put("timestamp", Instant.now().toString());
        payloadMap.put("data", data);

        try {
            return objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            log.error("Error serializando payload para webhook: {}", e.getMessage());
            return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\"}";
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
