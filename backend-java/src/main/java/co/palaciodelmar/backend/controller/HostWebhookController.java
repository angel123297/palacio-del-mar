package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.WebhookDeliveryDTO;
import co.palaciodelmar.backend.dto.WebhookSubscriptionDTO;
import co.palaciodelmar.backend.dto.WebhookSubscriptionRequest;
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

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<WebhookSubscriptionDTO>>> getSubscriptions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String propertyId
    ) {
        List<WebhookSubscriptionDTO> list = hostWebhookService.getSubscriptions(principal.getUser(), propertyId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<WebhookSubscriptionDTO>> createSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WebhookSubscriptionRequest request
    ) {
        WebhookSubscriptionDTO created = hostWebhookService.createSubscription(principal.getUser(), request);
        return ResponseEntity.ok(ApiResponse.ok("Suscripción de webhook creada exitosamente", created));
    }

    @PutMapping("/subscriptions/{id}")
    public ResponseEntity<ApiResponse<WebhookSubscriptionDTO>> updateSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody WebhookSubscriptionRequest request
    ) {
        WebhookSubscriptionDTO updated = hostWebhookService.updateSubscription(principal.getUser(), id, request);
        return ResponseEntity.ok(ApiResponse.ok("Suscripción de webhook actualizada exitosamente", updated));
    }

    @DeleteMapping("/subscriptions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSubscription(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        hostWebhookService.deleteSubscription(principal.getUser(), id);
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

    @PostMapping("/subscriptions/{id}/test")
    public ResponseEntity<ApiResponse<WebhookDeliveryDTO>> sendTestEvent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        WebhookDeliveryDTO delivery = hostWebhookService.sendTestEvent(principal.getUser(), id);
        return ResponseEntity.ok(ApiResponse.ok("Evento de prueba emitido exitosamente", delivery));
    }
}
