package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.webhook.WebhookSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/api/demo/webhook-receiver")
public class DemoWebhookReceiverController {

    private static final int MAX_DEMO_PAYLOAD_CHARS = 16_384;
    private static final java.util.regex.Pattern EMAIL_PII_PATTERN =
            java.util.regex.Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final java.util.regex.Pattern SENSITIVE_JSON_FIELD_PATTERN =
            java.util.regex.Pattern.compile("(\"(?:guestPhone|phone|smartLockPin|documentId|documentNumber|accountNumber)\"\\s*:\\s*\")([^\"]+)(\")");

    private final List<Map<String, Object>> receivedEvents = new CopyOnWriteArrayList<>();
    private volatile boolean simulateFailure = false;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInfo() {
        return ResponseEntity.ok(ApiResponse.ok("Receptor Demo de Webhooks Palacio del Mar", Map.of(
                "status", "ONLINE",
                "simulateFailure", simulateFailure,
                "receivedEventsCount", receivedEvents.size(),
                "recentEvents", receivedEvents,
                "usage", "Este endpoint recibe peticiones POST con cabecera X-Palacio-Signature para registrar entregas de webhooks en vivo."
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveWebhook(
            @RequestHeader(value = "X-Palacio-Signature", required = false) String signature,
            @RequestHeader(value = "X-Palacio-Event-Id", required = false) String eventId,
            @RequestHeader(value = "X-Palacio-Event-Type", required = false) String eventType,
            @RequestHeader(value = "X-Palacio-Timestamp", required = false) String timestamp,
            @RequestParam(required = false) String secret,
            @RequestBody(required = false) String rawBody
    ) {
        if (rawBody != null && rawBody.length() > MAX_DEMO_PAYLOAD_CHARS) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiResponse.error("El payload excede el tamaño máximo permitido (16 KB) en el receptor de demostración"));
        }

        if (simulateFailure) {
            log.warn("Receptor Demo: Simulando fallo 500 a solicitud del usuario para probar reintentos");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Simulación de fallo del receptor activada para demostración"));
        }

        boolean signatureValid = false;
        if (secret != null && signature != null && rawBody != null) {
            signatureValid = WebhookSigner.verify(rawBody, secret, signature);
        }

        String sanitizedPayload = sanitizePayloadPii(rawBody);
        String maskedSignature = (signature != null && signature.length() > 14)
                ? signature.substring(0, 14) + "..."
                : signature;

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("eventId", eventId);
        record.put("eventType", eventType);
        record.put("signature", maskedSignature);
        record.put("timestamp", timestamp);
        record.put("signatureValid", signatureValid);
        record.put("payload", sanitizedPayload);
        record.put("receivedAt", Instant.now().toString());

        receivedEvents.add(0, record);
        if (receivedEvents.size() > 50) {
            receivedEvents.remove(receivedEvents.size() - 1);
        }

        log.info("Receptor Demo: Evento {} ({}) recibido exitosamente", eventId, eventType);
        return ResponseEntity.ok(ApiResponse.ok("Evento recibido y registrado en receptor demo", record));
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEvents() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "simulateFailure", simulateFailure,
                "count", receivedEvents.size(),
                "events", receivedEvents
        )));
    }

    @GetMapping("/simulate-failure")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSimulateFailure() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "simulateFailure", this.simulateFailure,
                "message", this.simulateFailure ? "Fallo 500 activado" : "Receptor funcionando normalmente"
        )));
    }

    @PostMapping("/simulate-failure")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setSimulateFailure(
            @RequestParam(required = false) Boolean enabled,
            @RequestBody(required = false) Map<String, Boolean> body
    ) {
        boolean nextVal = enabled != null ? enabled : (body != null && Boolean.TRUE.equals(body.get("enabled")));
        this.simulateFailure = nextVal;
        log.info("Receptor Demo: Simulación de fallo establecida en: {}", this.simulateFailure);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "simulateFailure", this.simulateFailure,
                "message", this.simulateFailure ? "Fallo 500 activado" : "Receptor funcionando normalmente"
        )));
    }

    @DeleteMapping("/events")
    public ResponseEntity<ApiResponse<Void>> clearEvents() {
        receivedEvents.clear();
        return ResponseEntity.ok(ApiResponse.ok("Eventos eliminados", null));
    }

    static String sanitizePayloadPii(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return rawBody;
        }
        String maskedEmails = EMAIL_PII_PATTERN.matcher(rawBody).replaceAll("$1***$2");
        return SENSITIVE_JSON_FIELD_PATTERN.matcher(maskedEmails).replaceAll("$1***REDACTED***$3");
    }
}
