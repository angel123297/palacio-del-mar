package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.webhook.WebhookSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@RestController
@RequestMapping("/api/demo/webhook-receiver")
public class DemoWebhookReceiverController {

    private static final int MAX_DEMO_PAYLOAD_CHARS = 16_384;
    private static final java.util.regex.Pattern EMAIL_PII_PATTERN =
            java.util.regex.Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final java.util.regex.Pattern SENSITIVE_JSON_FIELD_PATTERN =
            java.util.regex.Pattern.compile("(\"(?:guestPhone|phone|smartLockPin|documentId|documentNumber|accountNumber|guestName|fullName|customerName|specialRequests|notes)\"\\s*:\\s*\")([^\"]+)(\")");

    @Autowired(required = false)
    private PropertyRepository propertyRepository;

    @Autowired(required = false)
    private UserRepository userRepository;

    private final ConcurrentLinkedDeque<Map<String, Object>> receivedEvents = new ConcurrentLinkedDeque<>();
    private volatile boolean simulateFailure = false;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInfo() {
        List<Map<String, Object>> filtered = filterEventsForCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok("Receptor Demo de Webhooks Palacio del Mar", Map.of(
                "status", "ONLINE",
                "simulateFailure", simulateFailure,
                "receivedEventsCount", filtered.size(),
                "recentEvents", filtered,
                "usage", "Este endpoint recibe peticiones POST con cabecera X-Palacio-Signature para registrar entregas de webhooks en vivo."
        )));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveWebhook(
            @RequestHeader(value = "X-Palacio-Signature", required = false) String signature,
            @RequestHeader(value = "X-Palacio-Event-Id", required = false) String eventId,
            @RequestHeader(value = "X-Palacio-Event-Type", required = false) String eventType,
            @RequestHeader(value = "X-Palacio-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Palacio-Secret", required = false) String secretHeader,
            @RequestParam(required = false) String secret,
            @RequestBody(required = false) String rawBody
    ) {
        if (secret != null && !secret.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Por motivos de seguridad, el secreto no debe enviarse como parámetro de URL (query string). Utilice la cabecera HTTP 'X-Palacio-Secret'."));
        }
        String effectiveSecret = secretHeader;
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
        if (effectiveSecret != null && signature != null && rawBody != null) {
            signatureValid = WebhookSigner.verify(rawBody, effectiveSecret, signature);
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

        receivedEvents.addFirst(record);
        while (receivedEvents.size() > 50) {
            receivedEvents.pollLast();
        }

        log.info("Receptor Demo: Evento {} ({}) recibido exitosamente", eventId, eventType);
        return ResponseEntity.ok(ApiResponse.ok("Evento recibido y registrado en receptor demo", record));
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> receiveWebhook(
            String signature,
            String eventId,
            String eventType,
            String timestamp,
            String secret,
            String rawBody
    ) {
        return receiveWebhook(signature, eventId, eventType, timestamp, secret, null, rawBody);
    }

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEvents() {
        List<Map<String, Object>> filtered = filterEventsForCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "simulateFailure", simulateFailure,
                "count", filtered.size(),
                "events", filtered
        )));
    }

    private List<Map<String, Object>> filterEventsForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return new ArrayList<>(receivedEvents);
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()) || "ADMIN".equalsIgnoreCase(a.getAuthority()));
        if (isAdmin || propertyRepository == null) {
            return new ArrayList<>(receivedEvents);
        }

        String usernameOrEmail = auth.getName();
        co.palaciodelmar.backend.model.User user = null;
        if (userRepository != null && usernameOrEmail != null) {
            user = userRepository.findByEmail(usernameOrEmail)
                    .or(() -> userRepository.findById(usernameOrEmail))
                    .orElse(null);
        }
        String effectiveHostId = (user != null && user.getId() != null) ? user.getId() : usernameOrEmail;

        List<co.palaciodelmar.backend.model.Property> hostProps = propertyRepository.findByOwnerId(effectiveHostId);
        Set<String> allowedPropIds = new HashSet<>();
        for (co.palaciodelmar.backend.model.Property p : hostProps) {
            if (p.getId() != null) allowedPropIds.add(p.getId());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> evt : receivedEvents) {
            Object payloadObj = evt.get("payload");
            if (payloadObj instanceof String payloadStr) {
                boolean match = false;
                for (String propId : allowedPropIds) {
                    if (payloadStr.contains(propId)) {
                        match = true;
                        break;
                    }
                }
                if (match) {
                    result.add(evt);
                }
            } else {
                result.add(evt);
            }
        }
        return result;
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
