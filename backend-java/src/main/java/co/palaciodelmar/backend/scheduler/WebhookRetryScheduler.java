package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import co.palaciodelmar.backend.service.RedisLockService;
import co.palaciodelmar.backend.webhook.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookRetryScheduler {

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDispatcher webhookDispatcher;

    @Autowired(required = false)
    private RedisLockService redisLockService;

    @Scheduled(fixedDelay = 30000) // cada 30 segundos (red de respaldo para Redis Streams ZSET)
    public void processPendingRetries() {
        Instant now = Instant.now();
        List<WebhookDelivery> pendingDeliveries = new java.util.ArrayList<>(
                deliveryRepository.findByStatusAndNextRetryAtBefore("PENDING", now)
        );

        // Rescate durable de entregas huérfanas: si un worker cayó tras leer el stream o falló el scheduling inicial
        List<WebhookDelivery> strandedDeliveries = deliveryRepository
                .findByStatusAndNextRetryAtIsNullAndCreatedAtBefore("PENDING", now.minusSeconds(60));
        if (strandedDeliveries != null && !strandedDeliveries.isEmpty()) {
            pendingDeliveries.addAll(strandedDeliveries);
        }

        if (pendingDeliveries.isEmpty()) {
            return;
        }

        log.info("Procesando {} entregas de webhook pendientes de reintento...", pendingDeliveries.size());

        for (WebhookDelivery delivery : pendingDeliveries) {
            String lockName = "webhook:delivery:" + delivery.getId();
            Optional<String> lockToken = Optional.empty();
            if (redisLockService != null && delivery.getId() != null) {
                lockToken = redisLockService.tryLock(lockName, Duration.ofSeconds(15));
                if (lockToken.isEmpty()) {
                    continue;
                }
            }
            try {
                // Re-verificar estado actual por si RedisWebhookStreamService ya la procesó
                WebhookDelivery fresh = delivery.getId() != null
                        ? deliveryRepository.findById(delivery.getId()).orElse(delivery)
                        : delivery;
                if (!"PENDING".equalsIgnoreCase(fresh.getStatus())) {
                    continue;
                }
                if (fresh.getNextRetryAt() != null && fresh.getNextRetryAt().isAfter(Instant.now())) {
                    continue;
                }

                WebhookSubscription sub = subscriptionRepository.findById(fresh.getSubscriptionId()).orElse(null);
                if (sub == null) {
                    fresh.setStatus("FAILED");
                    fresh.setErrorMessage("Suscripción de webhook no encontrada o eliminada");
                    fresh.setNextRetryAt(null);
                    deliveryRepository.save(fresh);
                    continue;
                }

                if (!Boolean.TRUE.equals(sub.getActive())) {
                    fresh.setStatus("FAILED");
                    fresh.setErrorMessage("Suscripción de webhook desactivada");
                    fresh.setNextRetryAt(null);
                    deliveryRepository.save(fresh);
                    continue;
                }

                webhookDispatcher.executeDelivery(fresh, sub.getSecret());
            } catch (Exception e) {
                log.error("Error al reintentar entrega de webhook {}: {}", delivery.getId(), e.getMessage(), e);
            } finally {
                if (redisLockService != null && lockToken.isPresent()) {
                    redisLockService.unlock(lockName, lockToken.get());
                }
            }
        }
    }
}
