package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.event.BookingDomainEvent;
import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class WebhookDispatcherTest {

    private WebhookSubscriptionRepository subscriptionRepository;
    private WebhookDeliveryRepository deliveryRepository;
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private WebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(WebhookSubscriptionRepository.class);
        deliveryRepository = mock(WebhookDeliveryRepository.class);
        httpClient = mock(HttpClient.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        dispatcher = new WebhookDispatcher(
                subscriptionRepository,
                deliveryRepository,
                objectMapper,
                httpClient
        );

        when(deliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @Test
    void testDispatchBookingConfirmedEventDeliversToSubscribedEndpoints() throws IOException, InterruptedException {
        String propId = "prop_cartagena_01";
        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub_01")
                .propertyId(propId)
                .ownerId("host_01")
                .url("https://example.com/pms-webhook")
                .secret("whsec_secret12345")
                .events(List.of("booking.confirmed", "booking.cancelled"))
                .active(true)
                .build();

        when(subscriptionRepository.findByPropertyIdAndActiveTrue(propId)).thenReturn(List.of(sub));

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"ok\":true}");
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        co.palaciodelmar.backend.dto.BookingDTO booking = co.palaciodelmar.backend.dto.BookingDTO.builder()
                .id("b_123")
                .guestName("Juan Pérez")
                .build();

        BookingDomainEvent event = BookingDomainEvent.builder()
                .eventType("booking.confirmed")
                .bookingId("b_123")
                .propertyId(propId)
                .booking(booking)
                .build();
        dispatcher.onBookingDomainEvent(event);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest request = requestCaptor.getValue();
        assertEquals("https://example.com/pms-webhook", request.uri().toString());
        assertEquals("booking.confirmed", request.headers().firstValue("X-Palacio-Event-Type").orElse(null));
        assertTrue(request.headers().firstValue("X-Palacio-Signature").isPresent());
        assertTrue(request.headers().firstValue("X-Palacio-Event-Id").isPresent());
        assertTrue(request.headers().firstValue("X-Palacio-Timestamp").isPresent());

        ArgumentCaptor<WebhookDelivery> deliveryCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, atLeastOnce()).save(deliveryCaptor.capture());

        WebhookDelivery finalDelivery = deliveryCaptor.getValue();
        assertEquals("SUCCESS", finalDelivery.getStatus());
        assertEquals(200, finalDelivery.getResponseCode());
        assertEquals(1, finalDelivery.getAttempts());
        assertNotNull(finalDelivery.getDeliveredAt());
        assertNull(finalDelivery.getNextRetryAt());
    }

    @Test
    void testIgnoresEventIfNotSubscribed() throws IOException, InterruptedException {
        String propId = "prop_cartagena_01";
        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub_01")
                .propertyId(propId)
                .url("https://example.com/pms-webhook")
                .events(List.of("booking.cancelled")) // Only cancelled
                .active(true)
                .build();

        when(subscriptionRepository.findByPropertyIdAndActiveTrue(propId)).thenReturn(List.of(sub));

        BookingDomainEvent event = BookingDomainEvent.builder()
                .eventType("booking.created")
                .propertyId(propId)
                .build();
        dispatcher.onBookingDomainEvent(event);
 
        verifyNoInteractions(httpClient);
        verifyNoInteractions(deliveryRepository);
    }

    @Test
    void testFailedDeliverySchedulesRetryWithExponentialBackoff() throws IOException, InterruptedException {
        String propId = "prop_02";
        WebhookSubscription sub = WebhookSubscription.builder()
                .id("sub_02")
                .propertyId(propId)
                .ownerId("host_02")
                .url("https://example.com/failing-endpoint")
                .secret("secret_xyz")
                .events(List.of("booking.created"))
                .active(true)
                .build();

        when(subscriptionRepository.findByPropertyIdAndActiveTrue(propId)).thenReturn(List.of(sub));

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        BookingDomainEvent event = BookingDomainEvent.builder()
                .eventType("booking.created")
                .propertyId(propId)
                .build();
        dispatcher.onBookingDomainEvent(event);

        ArgumentCaptor<WebhookDelivery> deliveryCaptor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryRepository, atLeastOnce()).save(deliveryCaptor.capture());

        WebhookDelivery delivery = deliveryCaptor.getValue();
        assertEquals("PENDING", delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertEquals(500, delivery.getResponseCode());
        assertNotNull(delivery.getNextRetryAt());
        // Next retry should be roughly ~60 seconds in the future
        assertTrue(delivery.getNextRetryAt().isAfter(Instant.now().plusSeconds(50)));
        assertTrue(delivery.getNextRetryAt().isBefore(Instant.now().plusSeconds(70)));
    }

    @Test
    void testDeliveryFailsPermanentlyAfterMaxAttemptsExhausted() throws IOException, InterruptedException {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id("del_exhausted")
                .eventId("evt_ex_1")
                .eventType("test.exhausted")
                .subscriptionId("sub_01")
                .propertyId("prop_01")
                .ownerId("host_01")
                .targetUrl("https://example.com/error")
                .payload("{\"test\":true}")
                .attempts(2) // already 2 attempts
                .maxAttempts(3)
                .status("PENDING")
                .build();

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(502);
        when(mockResponse.body()).thenReturn("Bad Gateway");
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        dispatcher.executeDelivery(delivery, "mysecret");

        assertEquals(3, delivery.getAttempts());
        assertEquals("FAILED", delivery.getStatus());
        assertNull(delivery.getNextRetryAt());
        assertTrue(delivery.getErrorMessage().contains("502"));
    }

    @Test
    void testBackoffIntervalsCalculation() {
        assertEquals(60, dispatcher.getBackoffSeconds(1));
        assertEquals(300, dispatcher.getBackoffSeconds(2));
        assertEquals(1800, dispatcher.getBackoffSeconds(3));
        assertEquals(1800, dispatcher.getBackoffSeconds(4));
    }
}
