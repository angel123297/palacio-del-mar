package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> handlePaymentWebhook(
            @RequestHeader(value = "X-Palacio-Signature", required = false) String signature,
            @RequestHeader(value = "X-Palacio-Event-Id", required = false) String eventId,
            @RequestHeader(value = "X-Palacio-Timestamp", required = false) String timestamp,
            @RequestBody(required = false) String rawBody
    ) {
        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Cuerpo del webhook no puede estar vacío"));
        }

        try {
            Map<String, Object> result = paymentWebhookService.processWebhook(rawBody, signature, eventId, timestamp);
            return ResponseEntity.ok(ApiResponse.ok("Webhook de pago procesado exitosamente", result));
        } catch (SecurityException e) {
            log.warn("Firma inválida o no autorizada en webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Error de validación en webhook: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado procesando webhook de pago: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Error interno al procesar webhook: " + e.getMessage()));
        }
    }
}
