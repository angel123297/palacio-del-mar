package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.WebhookDeliveryDTO;
import co.palaciodelmar.backend.dto.WebhookSubscriptionDTO;
import co.palaciodelmar.backend.dto.WebhookSubscriptionRequest;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.webhook.HostWebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/webhooks")
@RequiredArgsConstructor
public class HostWebhookController {

    private final HostWebhookService hostWebhookService;

    @GetMapping({"", "/subscriptions"})
    public ResponseEntity<ApiResponse<List<WebhookSubscriptionDTO>>> getSubscriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String propertyId
    ) {
        User user = principal != null ? principal.getUser() : null;
        List<WebhookSubscriptionDTO> list = hostWebhookService.getSubscriptions(user, propertyId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping({"", "/subscriptions"})
    public ResponseEntity<ApiResponse<WebhookSubscriptionDTO>> createSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WebhookSubscriptionRequest request
    ) {
        User user = principal != null ? principal.getUser() : null;
        WebhookSubscriptionDTO created = hostWebhookService.createSubscription(user, request);
        return ResponseEntity.ok(ApiResponse.ok("Suscripción de webhook creada exitosamente", created));
    }

    @PutMapping({"/{id}", "/subscriptions/{id}"})
    public ResponseEntity<ApiResponse<WebhookSubscriptionDTO>> updateSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody WebhookSubscriptionRequest request
    ) {
        User user = principal != null ? principal.getUser() : null;
        WebhookSubscriptionDTO updated = hostWebhookService.updateSubscription(user, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Suscripción de webhook actualizada exitosamente", updated));
    }

    @DeleteMapping({"/{id}", "/subscriptions/{id}"})
    public ResponseEntity<ApiResponse<Void>> deleteSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        User user = principal != null ? principal.getUser() : null;
        hostWebhookService.deleteSubscription(user, id);
        return ResponseEntity.ok(ApiResponse.ok("Suscripción de webhook eliminada exitosamente", null));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<ApiResponse<List<WebhookDeliveryDTO>>> getDeliveries(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String propertyId,
            @RequestParam(required = false) String subscriptionId
    ) {
        List<WebhookDeliveryDTO> deliveries = hostWebhookService.getDeliveries(principal.getUser(), propertyId, subscriptionId);
        return ResponseEntity.ok(ApiResponse.ok(deliveries));
    }

    @PostMapping("/deliveries/{id}/redeliver")
    public ResponseEntity<ApiResponse<WebhookDeliveryDTO>> redeliver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        WebhookDeliveryDTO delivery = hostWebhookService.redeliver(principal.getUser(), id);
        return ResponseEntity.ok(ApiResponse.ok("Entrega de webhook reenviada exitosamente", delivery));
    }

    @PostMapping({"/{id}/test", "/subscriptions/{id}/test"})
    public ResponseEntity<ApiResponse<WebhookDeliveryDTO>> sendTestEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        User user = principal != null ? principal.getUser() : null;
        WebhookDeliveryDTO delivery = hostWebhookService.sendTestEvent(user, id);
        return ResponseEntity.ok(ApiResponse.ok("Evento de prueba emitido exitosamente", delivery));
    }
}
