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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HostWebhookServiceTest {

    private WebhookSubscriptionRepository subscriptionRepository;
    private WebhookDeliveryRepository deliveryRepository;
    private PropertyRepository propertyRepository;
    private WebhookDispatcher webhookDispatcher;
    private HostWebhookService hostWebhookService;

    private User hostUser1;
    private User hostUser2;
    private User adminUser;
    private Property prop1;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        deliveryRepository = mock(WebhookDeliveryRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        webhookDispatcher = mock(WebhookDispatcher.class);

        hostWebhookService = new HostWebhookService(
                subscriptionRepository,
                deliveryRepository,
                propertyRepository,
                webhookDispatcher
        );

        hostUser1 = User.builder().id("usr_host_1").email("host1@palacio.co").role("host").build();
        hostUser2 = User.builder().id("usr_host_2").email("host2@palacio.co").role("host").build();
        adminUser = User.builder().id("usr_admin").email("admin@palacio.co").role("admin").build();

        prop1 = Property.builder()
                .id("prop_01")
                .ownerId("usr_host_1")
                .name("Villa del Mar")
                .build();
    }

    @Test
    void testCreateSubscriptionSuccessForOwner() {
        when(propertyRepository.findById("prop_01")).thenReturn(Optional.of(prop1));
        when(subscriptionRepository.save(any(WebhookSubscription.class))).thenAnswer(i -> {
            WebhookSubscription s = i.getArgument(0);
            s.setId("sub_new_1");
            return s;
        });

        WebhookSubscriptionRequest req = WebhookSubscriptionRequest.builder()
                .propertyId("prop_01")
                .url("https://external-pms.com/webhook")
                .events(List.of("booking.created", "booking.confirmed"))
                .description("PMS propio")
                .build();

        WebhookSubscriptionDTO dto = hostWebhookService.createSubscription(hostUser1, req);

        assertNotNull(dto);
        assertEquals("prop_01", dto.getPropertyId());
        assertEquals("usr_host_1", dto.getOwnerId());
        assertEquals("https://external-pms.com/webhook", dto.getUrl());
        assertNotNull(dto.getSecret());
        assertTrue(dto.getSecret().startsWith("whsec_"));
        assertEquals(2, dto.getEvents().size());
    }

    @Test
    void testAntiIdorCreateSubscriptionForDifferentHostThrows404() {
        when(propertyRepository.findById("prop_01")).thenReturn(Optional.of(prop1));

        WebhookSubscriptionRequest req = WebhookSubscriptionRequest.builder()
                .propertyId("prop_01")
                .url("https://malicious.com/webhook")
                .build();

        // Host 2 intenta suscribirse a la propiedad de Host 1
        assertThrows(ResourceNotFoundException.class, () ->
                hostWebhookService.createSubscription(hostUser2, req)
        );

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void testDeleteSubscriptionAntiIdorThrows404() {
        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub_01")
                .propertyId("prop_01")
                .ownerId("usr_host_1")
                .build();

        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(sub));

        // Host 2 intenta borrar webhook de Host 1
        assertThrows(ResourceNotFoundException.class, () ->
                hostWebhookService.deleteSubscription(hostUser2, "sub_01")
        );

        verify(subscriptionRepository, never()).delete(any());
    }

    @Test
    void testAdminCanAccessAnySubscription() {
        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub_01")
                .propertyId("prop_01")
                .ownerId("usr_host_1")
                .build();

        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(sub));

        assertDoesNotThrow(() -> hostWebhookService.deleteSubscription(adminUser, "sub_01"));
        verify(subscriptionRepository).delete(sub);
    }

    @Test
    void testRedeliverAntiIdorProtection() {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id("del_01")
                .subscriptionId("sub_01")
                .propertyId("prop_01")
                .ownerId("usr_host_1")
                .status("FAILED")
                .build();

        when(deliveryRepository.findById("del_01")).thenReturn(Optional.of(delivery));

        // Host 2 no puede reenviar deliveries de Host 1
        assertThrows(ResourceNotFoundException.class, () ->
                hostWebhookService.redeliver(hostUser2, "del_01")
        );

        verifyNoInteractions(webhookDispatcher);
    }

    @Test
    void testRedeliverCallsDispatcherWhenAuthorized() {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id("del_01")
                .subscriptionId("sub_01")
                .propertyId("prop_01")
                .ownerId("usr_host_1")
                .status("FAILED")
                .build();

        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub_01")
                .propertyId("prop_01")
                .ownerId("usr_host_1")
                .secret("sec_test")
                .build();

        when(deliveryRepository.findById("del_01")).thenReturn(Optional.of(delivery));
        when(subscriptionRepository.findById("sub_01")).thenReturn(Optional.of(sub));

        WebhookDeliveryDTO result = hostWebhookService.redeliver(hostUser1, "del_01");

        assertNotNull(result);
        verify(webhookDispatcher).executeDelivery(delivery, "sec_test");
    }
}
