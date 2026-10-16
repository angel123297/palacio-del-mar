package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import co.palaciodelmar.backend.webhook.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRetryScheduler {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDispatcher webhookDispatcher;

    @Scheduled(fixedDelay = 30000) // cada 30 segundos
    public void processPendingRetries() {
        Instant now = Instant.now();
        List<WebhookDelivery> pendingDeliveries = deliveryRepository.findByStatusAndNextRetryAtBefore("PENDING", now);

        if (pendingDeliveries.isEmpty()) {
            return;
        }

        log.info("Procesando {} entregas de webhook pendientes de reintento...", pendingDeliveries.size());

        for (WebhookDelivery delivery : pendingDeliveries) {
            try {
                WebhookSubscription sub = subscriptionRepository.findById(delivery.getSubscriptionId()).orElse(null);
                if (sub == null) {
                    delivery.setStatus("FAILED");
                    delivery.setErrorMessage("Suscripción de webhook no encontrada o eliminada");
                    delivery.setNextRetryAt(null);
                    deliveryRepository.save(delivery);
                    continue;
                }

                if (!Boolean.TRUE.equals(sub.getActive())) {
                    delivery.setStatus("FAILED");
                    delivery.setErrorMessage("Suscripción de webhook desactivada");
                    delivery.setNextRetryAt(null);
                    deliveryRepository.save(delivery);
                    continue;
                }

                webhookDispatcher.executeDelivery(delivery, sub.getSecret());
            } catch (Exception e) {
                log.error("Error al reintentar entrega de webhook {}: {}", delivery.getId(), e.getMessage(), e);
            }
        }
    }
}
