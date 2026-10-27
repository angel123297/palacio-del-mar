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
    void testDeliveryBlockedBySsrfAtDispatchTime() throws IOException, InterruptedException {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id("del_ssrf")
                .eventId("evt_ssrf_1")
                .eventType("test.ssrf")
                .subscriptionId("sub_ssrf")
                .propertyId("prop_01")
                .ownerId("host_01")
                .targetUrl("http://169.254.169.254/latest/meta-data")
                .payload("{\"test\":true}")
                .attempts(0)
                .maxAttempts(3)
                .status("PENDING")
                .build();

        dispatcher.executeDelivery(delivery, "mysecret");

        assertEquals(1, delivery.getAttempts());
        assertEquals("FAILED", delivery.getStatus());
        assertNull(delivery.getNextRetryAt());
        assertTrue(delivery.getErrorMessage().contains("SSRF"));
        // El cliente HTTP jamás debió ser llamado
        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void testBackoffIntervalsCalculation() {
        assertEquals(60, dispatcher.getBackoffSeconds(1));
        assertEquals(300, dispatcher.getBackoffSeconds(2));
        assertEquals(1800, dispatcher.getBackoffSeconds(3));
        assertEquals(1800, dispatcher.getBackoffSeconds(4));
    }

    @Test
    void testDnsRebindingAttemptBlockedBeforeHttpSend() throws Exception {
        SsrfProtectionValidator rebindingValidator = new SsrfProtectionValidator();
        java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger(0);
        rebindingValidator.setDnsResolver(host -> {
            int n = lookups.incrementAndGet();
            if (n == 1) {
                return new java.net.InetAddress[]{
                        java.net.InetAddress.getByAddress(host, new byte[]{93, (byte) 184, (byte) 215, 14})
                };
            }
            return new java.net.InetAddress[]{
                    java.net.InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1})
            };
        });

        WebhookDispatcher guardedDispatcher = new WebhookDispatcher(
                subscriptionRepository,
                deliveryRepository,
                objectMapper,
                httpClient,
                rebindingValidator
        );

        WebhookDelivery delivery = WebhookDelivery.builder()
                .id("del_rebind")
                .eventId("evt_rebind_1")
                .eventType("booking.confirmed")
                .subscriptionId("sub_rebind")
                .propertyId("prop_01")
                .ownerId("host_01")
                .targetUrl("https://evil-rebinder.example/hook")
                .payload("{\"test\":true}")
                .attempts(0)
                .maxAttempts(3)
                .status("PENDING")
                .build();

        guardedDispatcher.executeDelivery(delivery, "whsec_test");

        assertEquals("FAILED", delivery.getStatus());
        assertNull(delivery.getNextRetryAt());
        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void testPinnedTransportTruncatesHugeStreamingResponseWithoutOom() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/giant", exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked streaming
            byte[] chunk = "A".repeat(8192).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                for (int i = 0; i < 256; i++) { // 2 MB stream
                    os.write(chunk);
                    os.flush();
                }
            } catch (IOException ignored) {
                // Expected when client closes socket early after 4 KB!
            }
        });
        server.start();
        try {
            SsrfProtectionValidator.ValidatedTarget pinnedTarget = new SsrfProtectionValidator.ValidatedTarget(
                    java.net.URI.create("http://partner.example:" + port + "/giant"),
                    "partner.example",
                    port,
                    false,
                    java.net.InetAddress.getByAddress("partner.example", new byte[]{127, 0, 0, 1})
            );

            WebhookDispatcher.OutboundResponse resp = WebhookDispatcher.sendPinnedHttpPost(
                    pinnedTarget,
                    java.util.Map.of("X-Palacio-Event-Id", "evt_huge_1"),
                    "{\"ping\":true}",
                    java.time.Duration.ofSeconds(4),
                    4096
            );

            assertEquals(200, resp.statusCode());
            assertNotNull(resp.body());
            assertTrue(resp.body().length() <= 4100,
                    "La respuesta leída del socket debe acotarse a ~4 KB, longitud=" + resp.body().length());
            assertTrue(resp.body().startsWith("AAAA"));
        } finally {
            server.stop(0);
        }
    }
}
