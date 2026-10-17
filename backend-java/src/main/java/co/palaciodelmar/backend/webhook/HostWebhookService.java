package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.dto.WebhookDeliveryDTO;
import co.palaciodelmar.backend.dto.WebhookSubscriptionDTO;
import co.palaciodelmar.backend.dto.WebhookSubscriptionRequest;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class HostWebhookService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final PropertyRepository propertyRepository;
    private final WebhookDispatcher webhookDispatcher;

    public List<WebhookSubscriptionDTO> getSubscriptions(User currentUser, String propertyId) {
        if (propertyId != null && !propertyId.isBlank()) {
            assertPropertyOwnership(propertyId, currentUser);
            return subscriptionRepository.findByPropertyId(propertyId).stream()
                    .map(this::toSubscriptionDTO)
                    .toList();
        }

        if (isAdmin(currentUser)) {
            return subscriptionRepository.findAll().stream()
                    .map(this::toSubscriptionDTO)
                    .toList();
        }

        return subscriptionRepository.findByOwnerId(currentUser.getId()).stream()
                .map(this::toSubscriptionDTO)
                .toList();
    }

    public WebhookSubscriptionDTO createSubscription(User currentUser, WebhookSubscriptionRequest request) {
        Property property = assertPropertyOwnership(request.getPropertyId(), currentUser);
        String validatedUrl = validateWebhookUrl(request.getUrl());

        String secret = "whsec_" + UUID.randomUUID().toString().replace("-", "");
        List<String> events = (request.getEvents() != null && !request.getEvents().isEmpty())
                ? request.getEvents()
                : List.of("booking.created", "booking.confirmed", "booking.cancelled");

        WebhookSubscription subscription = WebhookSubscription.builder()
                .propertyId(property.getId())
                .ownerId(property.getOwnerId())
                .url(validatedUrl)
                .secret(secret)
                .events(events)
                .description(request.getDescription())
                .active(request.getActive() != null ? request.getActive() : true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        WebhookSubscription saved = subscriptionRepository.save(subscription);
        log.info("Suscripción de webhook creada: {} para propiedad: {} por anfitrión: {}",
                saved.getId(), property.getId(), currentUser.getId());
        return toSubscriptionDTO(saved);
    }

    public WebhookSubscriptionDTO updateSubscription(User currentUser, String subscriptionId, WebhookSubscriptionRequest request) {
        WebhookSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción de webhook no encontrada"));

        assertSubscriptionOwnership(sub, currentUser);

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            sub.setUrl(validateWebhookUrl(request.getUrl()));
        }
        if (request.getEvents() != null) {
            sub.setEvents(request.getEvents());
        }
        if (request.getActive() != null) {
            sub.setActive(request.getActive());
        }
        if (request.getDescription() != null) {
            sub.setDescription(request.getDescription());
        }
        sub.setUpdatedAt(Instant.now());

        WebhookSubscription saved = subscriptionRepository.save(sub);
        return toSubscriptionDTO(saved);
    }

    private String validateWebhookUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("La URL del webhook es obligatoria");
        }
        String trimmed = rawUrl.trim();
        java.net.URI uri;
        try {
            uri = java.net.URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Formato de URL de webhook inválido");
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("La URL del webhook debe usar protocolo http o https");
        }
        String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        if (host.isBlank()) {
            throw new IllegalArgumentException("La URL del webhook debe incluir un host válido");
        }
        Set<String> forbiddenHosts = Set.of(
                "169.254.169.254", "metadata.google.internal", "0.0.0.0",
                "mongo", "palacio-mongo", "redis", "palacio-redis", "mailpit", "palacio-mailpit"
        );
        if (forbiddenHosts.contains(host)) {
            throw new IllegalArgumentException("URL de webhook no permitida: apunta a un servicio interno restringido");
        }
        int port = uri.getPort();
        if (port == 27017 || port == 6379 || port == 1025) {
            throw new IllegalArgumentException("Puerto de destino no permitido para webhooks");
        }
        return trimmed;
    }

    public void deleteSubscription(User currentUser, String subscriptionId) {
        WebhookSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción de webhook no encontrada"));

        assertSubscriptionOwnership(sub, currentUser);
        subscriptionRepository.delete(sub);
        log.info("Suscripción de webhook eliminada: {}", subscriptionId);
    }

    public List<WebhookDeliveryDTO> getDeliveries(User currentUser, String propertyId, String subscriptionId) {
        if (subscriptionId != null && !subscriptionId.isBlank()) {
            WebhookSubscription sub = subscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Suscripción no encontrada"));
            assertSubscriptionOwnership(sub, currentUser);
            return deliveryRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId).stream()
                    .map(this::toDeliveryDTO)
                    .toList();
        }

        if (propertyId != null && !propertyId.isBlank()) {
            assertPropertyOwnership(propertyId, currentUser);
            return deliveryRepository.findByPropertyIdOrderByCreatedAtDesc(propertyId).stream()
                    .map(this::toDeliveryDTO)
                    .toList();
        }

        if (isAdmin(currentUser)) {
            return deliveryRepository.findAll().stream()
                    .sorted(Comparator.comparing(WebhookDelivery::getCreatedAt).reversed())
                    .map(this::toDeliveryDTO)
                    .toList();
        }

        return deliveryRepository.findByOwnerIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(this::toDeliveryDTO)
                .toList();
    }

    public WebhookDeliveryDTO redeliver(User currentUser, String deliveryId) {
        WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega de webhook no encontrada"));

        assertDeliveryOwnership(delivery, currentUser);

        WebhookSubscription sub = subscriptionRepository.findById(delivery.getSubscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción de webhook asociada no encontrada"));

        webhookDispatcher.executeDelivery(delivery, sub.getSecret());
        return toDeliveryDTO(delivery);
    }

    public WebhookDeliveryDTO sendTestEvent(User currentUser, String subscriptionId) {
        WebhookSubscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suscripción de webhook no encontrada"));

        assertSubscriptionOwnership(sub, currentUser);

        Map<String, Object> testData = Map.of(
                "type", "test.ping",
                "message", "Evento de prueba emitido desde Palacio del Mar Marketplace",
                "timestamp", Instant.now().toString(),
                "subscriptionId", sub.getId(),
                "propertyId", sub.getPropertyId()
        );

        WebhookDelivery delivery = webhookDispatcher.createAndExecuteDelivery(sub, "test.ping", testData);
        return toDeliveryDTO(delivery);
    }

    private Property assertPropertyOwnership(String propertyId, User currentUser) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Alojamiento no encontrado"));

        if (!isAdmin(currentUser) && !property.getOwnerId().equals(currentUser.getId())) {
            // Anti-IDOR: No revelar la existencia a anfitriones no autorizados
            throw new ResourceNotFoundException("Alojamiento no encontrado");
        }
        return property;
    }

    private void assertSubscriptionOwnership(WebhookSubscription sub, User currentUser) {
        if (!isAdmin(currentUser) && !sub.getOwnerId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Suscripción de webhook no encontrada");
        }
    }

    private void assertDeliveryOwnership(WebhookDelivery delivery, User currentUser) {
        if (!isAdmin(currentUser) && !delivery.getOwnerId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Entrega de webhook no encontrada");
        }
    }

    private boolean isAdmin(User user) {
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    public WebhookSubscriptionDTO toSubscriptionDTO(WebhookSubscription sub) {
        if (sub == null) return null;
        return WebhookSubscriptionDTO.builder()
                .id(sub.getId())
                .propertyId(sub.getPropertyId())
                .ownerId(sub.getOwnerId())
                .url(sub.getUrl())
                .secret(sub.getSecret())
                .events(sub.getEvents())
                .active(sub.getActive())
                .description(sub.getDescription())
                .createdAt(sub.getCreatedAt())
                .updatedAt(sub.getUpdatedAt())
                .build();
    }

    public WebhookDeliveryDTO toDeliveryDTO(WebhookDelivery delivery) {
        if (delivery == null) return null;
        return WebhookDeliveryDTO.builder()
                .id(delivery.getId())
                .subscriptionId(delivery.getSubscriptionId())
                .propertyId(delivery.getPropertyId())
                .eventId(delivery.getEventId())
                .eventType(delivery.getEventType())
                .targetUrl(delivery.getTargetUrl())
                .payload(delivery.getPayload())
                .attempts(delivery.getAttempts())
                .maxAttempts(delivery.getMaxAttempts())
                .status(delivery.getStatus())
                .responseCode(delivery.getResponseCode())
                .responseBody(delivery.getResponseBody())
                .errorMessage(delivery.getErrorMessage())
                .nextRetryAt(delivery.getNextRetryAt())
                .createdAt(delivery.getCreatedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }
}
