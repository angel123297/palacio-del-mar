package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentWebhookController HTTP & Security Tests")
class PaymentWebhookControllerTest {

    @Mock
    private PaymentWebhookService paymentWebhookService;

    @InjectMocks
    private PaymentWebhookController paymentWebhookController;

    @Test
    @DisplayName("Rechaza payload nulo o vacío con HTTP 400 Bad Request")
    void testRejectsNullOrBlankPayload() {
        ResponseEntity<ApiResponse<Map<String, Object>>> respNull =
                paymentWebhookController.handlePaymentWebhook("sig", "evt_1", "123", null);
        assertEquals(HttpStatus.BAD_REQUEST, respNull.getStatusCode());
        assertFalse(respNull.getBody().getSuccess());

        ResponseEntity<ApiResponse<Map<String, Object>>> respBlank =
                paymentWebhookController.handlePaymentWebhook("sig", "evt_1", "123", "   ");
        assertEquals(HttpStatus.BAD_REQUEST, respBlank.getStatusCode());
        assertFalse(respBlank.getBody().getSuccess());
    }

    @Test
    @DisplayName("Retorna HTTP 401 Unauthorized ante firma HMAC inválida")
    void testReturns401OnSecurityException() {
        when(paymentWebhookService.processWebhook(any(), any(), any(), any()))
                .thenThrow(new SecurityException("Firma del webhook inválida"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                paymentWebhookController.handlePaymentWebhook("bad_sig", "evt_2", "123", "{\"status\":\"paid\"}");

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertFalse(resp.getBody().getSuccess());
        assertTrue(resp.getBody().getMessage().contains("Firma del webhook inválida"));
    }

    @Test
    @DisplayName("Retorna HTTP 400 Bad Request ante validación fallida")
    void testReturns400OnIllegalArgumentException() {
        when(paymentWebhookService.processWebhook(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Evento ya procesado o inválido"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                paymentWebhookController.handlePaymentWebhook("sig", "evt_3", "123", "{\"status\":\"invalid\"}");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertFalse(resp.getBody().getSuccess());
        assertTrue(resp.getBody().getMessage().contains("Evento ya procesado o inválido"));
    }

    @Test
    @DisplayName("Retorna HTTP 500 ante error interno no controlado")
    void testReturns500OnUnexpectedException() {
        when(paymentWebhookService.processWebhook(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB Connection Timeout"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                paymentWebhookController.handlePaymentWebhook("sig", "evt_4", "123", "{\"status\":\"paid\"}");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertFalse(resp.getBody().getSuccess());
    }

    @Test
    @DisplayName("Retorna HTTP 200 OK cuando el webhook se procesa exitosamente")
    void testReturns200OnSuccessfulProcessing() {
        when(paymentWebhookService.processWebhook(any(), any(), any(), any()))
                .thenReturn(Map.of("processed", true, "bookingId", "b_123"));

        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                paymentWebhookController.handlePaymentWebhook("sig", "evt_5", "123", "{\"status\":\"paid\"}");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().getSuccess());
        assertNotNull(resp.getBody().getData());
        assertEquals("b_123", resp.getBody().getData().get("bookingId"));
    }
}
