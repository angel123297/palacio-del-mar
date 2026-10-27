package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import co.palaciodelmar.backend.service.RedisLockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisWebhookStreamServiceTest {

    @Test
    @DisplayName("Pilar 5: Encola entrega en Redis Stream (XADD), promueve reintentos vencidos (Lua ZSET) y consume con ACK")
    @SuppressWarnings("unchecked")
    void testEnqueuePromoteAndConsumeWebhookStream() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);
        when(streamOps.add(eq(RedisWebhookStreamService.STREAM_KEY), anyMap()))
                .thenReturn(RecordId.of("1793000000000-0"));

        WebhookDeliveryRepository deliveryRepo = mock(WebhookDeliveryRepository.class);
        WebhookSubscriptionRepository subRepo = mock(WebhookSubscriptionRepository.class);
        WebhookDispatcher dispatcher = mock(WebhookDispatcher.class);
        RedisLockService lockService = new RedisLockService(null);

        WebhookDelivery delivery = WebhookDelivery.builder()
                .id("deliv-101")
                .subscriptionId("sub-1")
                .status("PENDING")
                .build();
        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub-1")
                .secret("whsec_test_123")
                .active(true)
                .build();

        when(deliveryRepo.findById("deliv-101")).thenReturn(Optional.of(delivery));
        when(subRepo.findById("sub-1")).thenReturn(Optional.of(sub));

        RedisWebhookStreamService streamService = new RedisWebhookStreamService(
                redisTemplate, deliveryRepo, subRepo, dispatcher, lockService
        );

        assertTrue(streamService.enqueueDelivery("deliv-101", "evt-1", "booking.confirmed"));
        verify(streamOps).add(eq(RedisWebhookStreamService.STREAM_KEY), anyMap());

        // Promoción atómica de reintentos vencidos
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(RedisWebhookStreamService.RETRY_ZSET_KEY)), any(), any()))
                .thenReturn(List.of("deliv-101"));
        int promoted = streamService.promoteDueRetries(10);
        assertEquals(1, promoted);

        // Consumo de batch y XACK
        MapRecord<String, Object, Object> record = StreamRecords.newRecord()
                .in(RedisWebhookStreamService.STREAM_KEY)
                .withId(RecordId.of("1793000000000-0"))
                .ofMap(Map.of("deliveryId", "deliv-101"));
        when(streamOps.read(
                any(org.springframework.data.redis.connection.stream.Consumer.class),
                any(org.springframework.data.redis.connection.stream.StreamReadOptions.class),
                any(org.springframework.data.redis.connection.stream.StreamOffset.class)
        )).thenReturn(List.of(record));

        int processed = streamService.consumeBatch(10);
        assertEquals(1, processed);
        verify(dispatcher).executeDelivery(delivery, "whsec_test_123");
        verify(streamOps).acknowledge(
                RedisWebhookStreamService.STREAM_KEY,
                RedisWebhookStreamService.CONSUMER_GROUP,
                RecordId.of("1793000000000-0")
        );
    }

    @Test
    @DisplayName("Pilar 5: Si otro worker ya posee el lock de la entrega, processDeliverySafely evita doble envío")
    void testProcessDeliverySkipsWhenLockedByAnotherWorker() {
        WebhookDeliveryRepository deliveryRepo = mock(WebhookDeliveryRepository.class);
        WebhookSubscriptionRepository subRepo = mock(WebhookSubscriptionRepository.class);
        WebhookDispatcher dispatcher = mock(WebhookDispatcher.class);
        RedisLockService lockService = new RedisLockService(null);

        Optional<String> heldToken = lockService.tryLock("webhook:delivery:deliv-locked", Duration.ofSeconds(10));
        assertTrue(heldToken.isPresent());

        RedisWebhookStreamService streamService = new RedisWebhookStreamService(
                null, deliveryRepo, subRepo, dispatcher, lockService
        );

        assertTrue(streamService.processDeliverySafely("deliv-locked"));
        verifyNoInteractions(deliveryRepo, dispatcher);

        lockService.unlock("webhook:delivery:deliv-locked", heldToken.get());
    }
}
