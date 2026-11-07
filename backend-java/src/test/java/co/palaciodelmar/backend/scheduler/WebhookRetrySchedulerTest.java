package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import co.palaciodelmar.backend.webhook.WebhookDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebhookRetrySchedulerTest {

    @Test
    @DisplayName("Pilar 5: Scheduler rescata entregas pendientes normales y entregas huérfanas con nextRetryAt=null creadas > 60s")
    void testRescuesPendingAndStrandedDeliveries() {
        WebhookDeliveryRepository deliveryRepo = mock(WebhookDeliveryRepository.class);
        WebhookSubscriptionRepository subRepo = mock(WebhookSubscriptionRepository.class);
        WebhookDispatcher dispatcher = mock(WebhookDispatcher.class);

        Instant now = Instant.now();

        WebhookDelivery regularPending = WebhookDelivery.builder()
                .id("deliv-reg-1")
                .subscriptionId("sub-1")
                .status("PENDING")
                .nextRetryAt(now.minusSeconds(10))
                .build();

        WebhookDelivery strandedDelivery = WebhookDelivery.builder()
                .id("deliv-stranded-2")
                .subscriptionId("sub-1")
                .status("PENDING")
                .nextRetryAt(null)
                .createdAt(now.minusSeconds(120))
                .build();

        when(deliveryRepo.findByStatusAndNextRetryAtBefore(eq("PENDING"), any(Instant.class)))
                .thenReturn(List.of(regularPending));
        when(deliveryRepo.findByStatusAndNextRetryAtIsNullAndCreatedAtBefore(eq("PENDING"), any(Instant.class)))
                .thenReturn(List.of(strandedDelivery));

        when(deliveryRepo.findById("deliv-reg-1")).thenReturn(Optional.of(regularPending));
        when(deliveryRepo.findById("deliv-stranded-2")).thenReturn(Optional.of(strandedDelivery));

        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub-1")
                .secret("secret_123")
                .active(true)
                .build();
        when(subRepo.findById("sub-1")).thenReturn(Optional.of(sub));

        WebhookRetryScheduler scheduler = new WebhookRetryScheduler(deliveryRepo, subRepo, dispatcher);
        scheduler.processPendingRetries();

        verify(dispatcher, times(1)).executeDelivery(regularPending, "secret_123");
        verify(dispatcher, times(1)).executeDelivery(strandedDelivery, "secret_123");
    }
}
