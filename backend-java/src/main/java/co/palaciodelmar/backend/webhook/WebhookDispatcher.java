package co.palaciodelmar.backend.webhook;

import co.palaciodelmar.backend.event.BookingDomainEvent;
import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.model.WebhookSubscription;
import co.palaciodelmar.backend.repository.WebhookDeliveryRepository;
import co.palaciodelmar.backend.repository.WebhookSubscriptionRepository;
import co.palaciodelmar.backend.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class WebhookDispatcher {

    public static final int MAX_RESPONSE_BYTES = 4096;
    private static final int MAX_HEADER_BYTES = 16_384;

    public record OutboundResponse(int statusCode, String body) {}

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean customHttpClientProvided;
    private final SsrfProtectionValidator ssrfProtectionValidator;

    @Autowired(required = false)
    private EmailService emailService;

    @org.springframework.context.annotation.Lazy
    @Autowired(required = false)
    private RedisWebhookStreamService redisWebhookStreamService;

    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void setRedisWebhookStreamService(RedisWebhookStreamService redisWebhookStreamService) {
        this.redisWebhookStreamService = redisWebhookStreamService;
    }

    @Value("${palacio.webhooks.retry.backoff-attempt-1:60}")
    private long backoffAttempt1Seconds = 60; // 1 minuto por defecto

    @Value("${palacio.webhooks.retry.backoff-attempt-2:300}")
    private long backoffAttempt2Seconds = 300; // 5 minutos por defecto

    @Value("${palacio.webhooks.retry.backoff-attempt-3:1800}")
    private long backoffAttempt3Seconds = 1800; // 30 minutos por defecto

    @org.springframework.beans.factory.annotation.Autowired
    public WebhookDispatcher(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryRepository deliveryRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SsrfProtectionValidator ssrfProtectionValidator
    ) {
        this(subscriptionRepository, deliveryRepository, objectMapper, null, ssrfProtectionValidator);
    }

    public WebhookDispatcher(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryRepository deliveryRepository,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this(subscriptionRepository, deliveryRepository, objectMapper, httpClient, new SsrfProtectionValidator());
    }

    public WebhookDispatcher(
            WebhookSubscriptionRepository subscriptionRepository,
            WebhookDeliveryRepository deliveryRepository,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            SsrfProtectionValidator ssrfProtectionValidator
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper != null ? objectMapper.copy().findAndRegisterModules() : new ObjectMapper().findAndRegisterModules();
        this.customHttpClientProvided = (httpClient != null);
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.ssrfProtectionValidator = ssrfProtectionValidator != null ? ssrfProtectionValidator : new SsrfProtectionValidator();
    }

    @EventListener
    public void onBookingDomainEvent(BookingDomainEvent event) {
        if (event == null || event.getPropertyId() == null) {
            return;
        }

        log.info("Recibido evento de dominio: {} para propiedad: {}", event.getEventType(), event.getPropertyId());
        dispatchToProperty(event.getPropertyId(), event.getEventType(), event.getBooking());
    }

    public void dispatchToProperty(String propertyId, String eventType, Object data) {
        List<WebhookSubscription> activeSubscriptions = subscriptionRepository.findByPropertyIdAndActiveTrue(propertyId);
        if (activeSubscriptions.isEmpty()) {
            log.debug("No hay suscripciones activas de webhook para propiedad: {}", propertyId);
            return;
        }

        for (WebhookSubscription sub : activeSubscriptions) {
            if (isSubscribed(sub, eventType)) {
                createAndDispatchDelivery(sub, eventType, data);
            }
        }
    }

    /**
     * Crea el registro de entrega y lo publica en Redis Stream (XADD) para ejecución asíncrona no bloqueante.
     * Si Redis no está disponible o no está configurado (p.ej. tests unitarios), ejecuta sincrónicamente como fallback.
     */
    public WebhookDelivery createAndDispatchDelivery(WebhookSubscription sub, String eventType, Object data) {
        WebhookDelivery saved = createPendingDelivery(sub, eventType, data);
        if (redisWebhookStreamService != null && saved.getId() != null
                && redisWebhookStreamService.enqueueDelivery(saved.getId(), saved.getEventId(), saved.getEventType())) {
            return saved;
        }
        executeDelivery(saved, sub.getSecret());
        return saved;
    }

    /**
     * Crea y ejecuta sincrónicamente la entrega (usado en pruebas interactivas desde el panel de anfitrión).
     */
    public WebhookDelivery createAndExecuteDelivery(WebhookSubscription sub, String eventType, Object data) {
        WebhookDelivery saved = createPendingDelivery(sub, eventType, data);
        executeDelivery(saved, sub.getSecret());
        return saved;
    }

    private WebhookDelivery createPendingDelivery(WebhookSubscription sub, String eventType, Object data) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String payloadJson = buildPayloadJson(eventId, eventType, sub.getPropertyId(), data);

        WebhookDelivery delivery = WebhookDelivery.builder()
                .subscriptionId(sub.getId())
                .propertyId(sub.getPropertyId())
                .ownerId(sub.getOwnerId())
                .eventId(eventId)
                .eventType(eventType)
                .targetUrl(sub.getUrl())
                .payload(payloadJson)
                .attempts(0)
                .maxAttempts(3)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        return deliveryRepository.save(delivery);
    }

    public void executeDelivery(WebhookDelivery delivery, String secret) {
        delivery.setAttempts(delivery.getAttempts() + 1);

        // Segunda barrera estricta anti-SSRF y anti-DNS-Rebinding: resolver y fijar IP pública
        SsrfProtectionValidator.ValidatedTarget validatedTarget = null;
        if (ssrfProtectionValidator != null) {
            try {
                validatedTarget = ssrfProtectionValidator.resolveAndValidate(delivery.getTargetUrl());
                if (customHttpClientProvided) {
                    // Cuando se inyecta un HttpClient externo (tests), re-verificamos resolución DNS para detectar mutación TTL=0
                    SsrfProtectionValidator.ValidatedTarget recheck = ssrfProtectionValidator.resolveAndValidate(delivery.getTargetUrl());
                    if (!Arrays.equals(validatedTarget.pinnedAddress().getAddress(), recheck.pinnedAddress().getAddress())) {
                        throw new IllegalArgumentException("DNS Rebinding detectado en host " + validatedTarget.host());
                    }
                }
            } catch (Exception ssrfEx) {
                log.error("Entrega de webhook {} bloqueada por política SSRF/Rebinding en destino {}: {}",
                        delivery.getEventId(), delivery.getTargetUrl(), ssrfEx.getMessage());
                delivery.setStatus("FAILED");
                delivery.setErrorMessage("Destino bloqueado por política de seguridad SSRF o host inalcanzable");
                delivery.setNextRetryAt(null);
                deliveryRepository.save(delivery);
                return;
            }
        }

        String payloadBody = delivery.getPayload() != null ? delivery.getPayload() : "{}";
        String signature = WebhookSigner.sign(payloadBody, secret != null ? secret : "secret");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        log.info("Despachando webhook {} (intento {}/{}) hacia {}",
                delivery.getEventId(), delivery.getAttempts(), delivery.getMaxAttempts(), delivery.getTargetUrl());

        Map<String, String> headersMap = new LinkedHashMap<>();
        headersMap.put("X-Palacio-Signature", signature != null ? signature : "");
        headersMap.put("X-Palacio-Event-Id", delivery.getEventId() != null ? delivery.getEventId() : "");
        headersMap.put("X-Palacio-Event-Type", delivery.getEventType() != null ? delivery.getEventType() : "");
        headersMap.put("X-Palacio-Timestamp", timestamp);

        try {
            int code;
            String boundedBody;
            if (!customHttpClientProvided && validatedTarget != null) {
                OutboundResponse outbound = sendPinnedHttpPost(
                        validatedTarget,
                        headersMap,
                        payloadBody,
                        Duration.ofSeconds(4),
                        MAX_RESPONSE_BYTES
                );
                code = outbound.statusCode();
                boundedBody = outbound.body();
            } else {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(delivery.getTargetUrl()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payloadBody))
                        .timeout(Duration.ofSeconds(4));
                headersMap.forEach(reqBuilder::header);

                HttpResponse<?> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                code = response.statusCode();
                boundedBody = extractBoundedBody(response.body(), MAX_RESPONSE_BYTES);
            }

            delivery.setResponseCode(code);
            delivery.setResponseBody(truncate(boundedBody, 1000));

            if (code >= 200 && code < 300) {
                delivery.setStatus("SUCCESS");
                delivery.setDeliveredAt(Instant.now());
                delivery.setNextRetryAt(null);
                delivery.setErrorMessage(null);
                log.info("Webhook {} entregado con éxito (HTTP {})", delivery.getEventId(), code);
            } else {
                handleDeliveryFailure(delivery, "HTTP status " + code);
            }
        } catch (Exception e) {
            log.warn("Fallo en entrega de webhook {}: {}", delivery.getEventId(), e.getMessage());
            handleDeliveryFailure(delivery, e.getMessage());
        }

        deliveryRepository.save(delivery);
    }

    public static OutboundResponse sendPinnedHttpPost(
            SsrfProtectionValidator.ValidatedTarget target,
            Map<String, String> headers,
            String jsonBody,
            Duration timeout,
            int maxResponseBytes
    ) throws java.io.IOException {
        int timeoutMs = timeout != null && timeout.toMillis() > 0 ? (int) timeout.toMillis() : 4000;
        byte[] bodyBytes = (jsonBody != null ? jsonBody : "{}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.net.InetAddress boundAddress = java.net.InetAddress.getByAddress(target.host(), target.pinnedAddress().getAddress());

        java.net.Socket rawSocket = new java.net.Socket();
        rawSocket.connect(new java.net.InetSocketAddress(boundAddress, target.port()), timeoutMs);
        rawSocket.setSoTimeout(timeoutMs);

        java.net.Socket activeSocket = rawSocket;
        if (target.https()) {
            javax.net.ssl.SSLSocketFactory sslFactory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
            javax.net.ssl.SSLSocket sslSocket = (javax.net.ssl.SSLSocket) sslFactory.createSocket(
                    rawSocket, target.host(), target.port(), true);
            javax.net.ssl.SSLParameters sslParams = sslSocket.getSSLParameters();
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
            if (!co.palaciodelmar.backend.security.RateLimitFilter.isValidIpLiteral(target.host())) {
                sslParams.setServerNames(List.of(new javax.net.ssl.SNIHostName(target.host())));
            }
            sslSocket.setSSLParameters(sslParams);
            sslSocket.startHandshake();
            activeSocket = sslSocket;
        }

        try (java.net.Socket sock = activeSocket;
             java.io.OutputStream out = sock.getOutputStream();
             java.io.InputStream in = sock.getInputStream()) {

            String rawPath = target.uri().getRawPath();
            if (rawPath == null || rawPath.isEmpty()) {
                rawPath = "/";
            }
            if (target.uri().getRawQuery() != null && !target.uri().getRawQuery().isEmpty()) {
                rawPath = rawPath + "?" + target.uri().getRawQuery();
            }
            boolean standardPort = (target.https() && target.port() == 443) || (!target.https() && target.port() == 80);
            String hostHeader = standardPort ? target.host() : (target.host() + ":" + target.port());

            StringBuilder reqHead = new StringBuilder(512);
            reqHead.append("POST ").append(sanitizeHeaderToken(rawPath)).append(" HTTP/1.1\r\n");
            reqHead.append("Host: ").append(sanitizeHeaderToken(hostHeader)).append("\r\n");
            reqHead.append("User-Agent: PalacioDelMar-Webhooks/1.0\r\n");
            reqHead.append("Content-Type: application/json; charset=UTF-8\r\n");
            reqHead.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            reqHead.append("Connection: close\r\n");
            if (headers != null) {
                for (var entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        reqHead.append(sanitizeHeaderToken(entry.getKey()))
                                .append(": ")
                                .append(sanitizeHeaderToken(entry.getValue()))
                                .append("\r\n");
                    }
                }
            }
            reqHead.append("\r\n");

            out.write(reqHead.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.write(bodyBytes);
            out.flush();

            String statusLine = readAsciiLine(in, 1024);
            if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                throw new java.io.IOException("Respuesta HTTP inválida del destino de webhook");
            }
            String[] statusParts = statusLine.split(" ", 3);
            if (statusParts.length < 2) {
                throw new java.io.IOException("Línea de estado HTTP incompleta: " + statusLine);
            }
            int statusCode = Integer.parseInt(statusParts[1].trim());

            boolean chunked = false;
            long contentLength = -1L;
            int totalHeaderBytes = statusLine.length();
            while (true) {
                String line = readAsciiLine(in, 4096);
                if (line == null || line.isEmpty()) {
                    break;
                }
                totalHeaderBytes += line.length();
                if (totalHeaderBytes > MAX_HEADER_BYTES) {
                    throw new java.io.IOException("Cabeceras de respuesta HTTP exceden el límite de seguridad (16 KB)");
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String hName = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    String hVal = line.substring(colon + 1).trim();
                    if ("transfer-encoding".equals(hName) && hVal.toLowerCase(Locale.ROOT).contains("chunked")) {
                        chunked = true;
                    } else if ("content-length".equals(hName)) {
                        try {
                            contentLength = Long.parseLong(hVal);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            String responseText = chunked
                    ? readBoundedChunkedBody(in, maxResponseBytes)
                    : readBoundedStreamBody(in, maxResponseBytes, contentLength);
            return new OutboundResponse(statusCode, responseText);
        }
    }

    private static String extractBoundedBody(Object rawBody, int maxBytes) throws java.io.IOException {
        if (rawBody == null) {
            return "";
        }
        if (rawBody instanceof String s) {
            return s.length() <= maxBytes ? s : s.substring(0, maxBytes) + "...";
        }
        if (rawBody instanceof java.io.InputStream is) {
            try (java.io.InputStream in = is) {
                return readBoundedStreamBody(in, maxBytes, -1L);
            }
        }
        String str = String.valueOf(rawBody);
        return str.length() <= maxBytes ? str : str.substring(0, maxBytes) + "...";
    }

    private static String readBoundedStreamBody(java.io.InputStream in, int maxBytes, long contentLength) throws java.io.IOException {
        int cap = Math.max(64, maxBytes);
        long maxToRead = contentLength >= 0 ? Math.min(contentLength, (long) cap + 1) : ((long) cap + 1);
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(Math.min(cap + 1, 4097));
        byte[] chunk = new byte[1024];
        while (buffer.size() < maxToRead) {
            int want = (int) Math.min(chunk.length, maxToRead - buffer.size());
            int n = in.read(chunk, 0, want);
            if (n < 0) {
                break;
            }
            buffer.write(chunk, 0, n);
        }
        byte[] bytes = buffer.toByteArray();
        boolean truncated = bytes.length > cap || (contentLength > cap);
        int len = Math.min(bytes.length, cap);
        String text = new String(bytes, 0, len, java.nio.charset.StandardCharsets.UTF_8);
        return truncated ? text + "..." : text;
    }

    private static String readBoundedChunkedBody(java.io.InputStream in, int maxBytes) throws java.io.IOException {
        int cap = Math.max(64, maxBytes);
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(Math.min(cap, 4096));
        boolean truncated = false;
        byte[] tmp = new byte[1024];
        while (true) {
            String sizeLine = readAsciiLine(in, 256);
            if (sizeLine == null) {
                break;
            }
            int semi = sizeLine.indexOf(';');
            String hex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();
            if (hex.isEmpty()) {
                continue;
            }
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(hex, 16);
            } catch (NumberFormatException nfe) {
                break;
            }
            if (chunkSize <= 0) {
                break;
            }
            int remainingCap = cap - buffer.size();
            int toCopy = Math.min(chunkSize, remainingCap);
            int copied = 0;
            while (copied < toCopy) {
                int n = in.read(tmp, 0, Math.min(tmp.length, toCopy - copied));
                if (n < 0) {
                    break;
                }
                buffer.write(tmp, 0, n);
                copied += n;
            }
            if (chunkSize > remainingCap) {
                truncated = true;
                break; // Abortar inmediatamente sin consumir el resto del stream gigante
            }
            // Consumir CRLF final del chunk
            readAsciiLine(in, 16);
            if (buffer.size() >= cap) {
                truncated = true;
                break;
            }
        }
        String text = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        return truncated ? text + "..." : text;
    }

    private static String readAsciiLine(java.io.InputStream in, int maxLineBytes) throws java.io.IOException {
        java.io.ByteArrayOutputStream lineBuf = new java.io.ByteArrayOutputStream(128);
        int b;
        boolean readAny = false;
        while ((b = in.read()) != -1) {
            readAny = true;
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                if (lineBuf.size() >= maxLineBytes) {
                    throw new java.io.IOException("Línea HTTP excede longitud máxima permitida");
                }
                lineBuf.write(b);
            }
        }
        if (!readAny && lineBuf.size() == 0) {
            return null;
        }
        return lineBuf.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static String sanitizeHeaderToken(String val) {
        return val == null ? "" : val.replace("\r", "").replace("\n", "");
    }

    private void handleDeliveryFailure(WebhookDelivery delivery, String errorMsg) {
        delivery.setErrorMessage(errorMsg);
        if (delivery.getAttempts() < delivery.getMaxAttempts()) {
            delivery.setStatus("PENDING");
            long backoffSeconds = getBackoffSeconds(delivery.getAttempts());
            Instant nextRetry = Instant.now().plusSeconds(backoffSeconds);
            delivery.setNextRetryAt(nextRetry);
            if (redisWebhookStreamService != null && delivery.getId() != null) {
                redisWebhookStreamService.scheduleRetry(delivery.getId(), nextRetry);
            }
            log.info("Webhook {} programado para reintento en {}s (intento {})", delivery.getEventId(), backoffSeconds, delivery.getAttempts());
        } else {
            delivery.setStatus("FAILED");
            delivery.setNextRetryAt(null);
            log.warn("Webhook {} ha agotado sus {} intentos. Marcado como FAILED", delivery.getEventId(), delivery.getMaxAttempts());
            if (emailService != null) {
                try {
                    emailService.sendWebhookFailureAlert(delivery);
                } catch (Exception e) {
                    log.warn("Error enviando alerta por email para webhook fallido {}: {}", delivery.getId(), e.getMessage());
                }
            }
        }
    }

    public long getBackoffSeconds(int attempt) {
        return switch (attempt) {
            case 1 -> backoffAttempt1Seconds; // 1m
            case 2 -> backoffAttempt2Seconds; // 5m
            default -> backoffAttempt3Seconds; // 30m
        };
    }

    private boolean isSubscribed(WebhookSubscription sub, String eventType) {
        if (sub.getEvents() == null || sub.getEvents().isEmpty()) {
            return true; // Si no especifica eventos, suscribe a todos
        }
        for (String ev : sub.getEvents()) {
            if ("*".equals(ev) || ev.equalsIgnoreCase(eventType)) {
                return true;
            }
        }
        return false;
    }

    private String buildPayloadJson(String eventId, String eventType, String propertyId, Object data) {
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("eventId", eventId);
        payloadMap.put("eventType", eventType);
        payloadMap.put("propertyId", propertyId);
        payloadMap.put("timestamp", Instant.now().toString());
        payloadMap.put("data", data);

        try {
            return objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            log.error("Error serializando payload para webhook: {}", e.getMessage());
            return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\"}";
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return null;
        if (text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }
}
