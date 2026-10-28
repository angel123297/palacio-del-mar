package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("M-3: Receptor Demo de Webhooks endurecido (límite de payload y enmascaramiento PII)")
class DemoWebhookReceiverControllerTest {

    @Test
    @DisplayName("M-3: Rechaza payloads mayores a 16 KB con HTTP 413 Payload Too Large")
    void testRejectsOversizedPayloadWith413() {
        DemoWebhookReceiverController controller = new DemoWebhookReceiverController();
        String hugePayload = "{\"data\":\"" + "X".repeat(17_000) + "\"}";

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.receiveWebhook(
                null, "evt_big", "booking.confirmed", "1700000000", null, hugePayload
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
    }

    @Test
    @DisplayName("M-3: Enmascara emails, teléfonos y smartLockPin antes de guardar en memoria")
    @SuppressWarnings("unchecked")
    void testSanitizesPiiInStoredEvents() {
        DemoWebhookReceiverController controller = new DemoWebhookReceiverController();
        String rawPayload = "{\"guestEmail\":\"cliente.vip@gmail.com\",\"guestPhone\":\"+573001234567\",\"smartLockPin\":\"987654\",\"bookingCode\":\"PDM-101\"}";

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.receiveWebhook(
                null, "evt_pii", "booking.confirmed", "1700000000", null, rawPayload
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResponseEntity<ApiResponse<Map<String, Object>>> eventsResp = controller.getEvents();
        List<Map<String, Object>> events = (List<Map<String, Object>>) eventsResp.getBody().getData().get("events");
        assertEquals(1, events.size());
        String storedPayload = (String) events.get(0).get("payload");
        assertNotNull(storedPayload);
        assertFalse(storedPayload.contains("cliente.vip@gmail.com"), "Email en texto plano no debe quedar expuesto");
        assertFalse(storedPayload.contains("+573001234567"), "Teléfono completo no debe quedar expuesto");
        assertFalse(storedPayload.contains("987654"), "smartLockPin no debe quedar expuesto");
        assertTrue(storedPayload.contains("PDM-101"), "Código de reserva no sensible debe conservarse");
    }
}
