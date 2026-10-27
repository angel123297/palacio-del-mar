package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;


import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.IdempotencyService;
import co.palaciodelmar.backend.service.PaymentGateway;
import co.palaciodelmar.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired(required = false)
    private IdempotencyService idempotencyService;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentConfig() {
        Map<String, Object> config = paymentService.getPaymentConfig();
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<PaymentGateway.PaymentIntentResponse>> createIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String bookingId = (body != null && body.get("bookingId") != null) ? String.valueOf(body.get("bookingId")) : null;
        String method = (body != null && body.get("method") != null) ? String.valueOf(body.get("method")) : "card";
        String userId = (principal != null && principal.getUser() != null) ? principal.getUser().getId() : null;

        if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            Map<String, String> fingerprint = Map.of(
                    "bookingId", bookingId != null ? bookingId : "",
                    "method", method
            );
            IdempotencyService.IdempotentResult<PaymentGateway.PaymentIntentResponse> res = idempotencyService.execute(
                    "payments:checkout",
                    userId,
                    idempotencyKey,
                    fingerprint,
                    PaymentGateway.PaymentIntentResponse.class,
                    () -> paymentService.createPaymentIntent(userId, bookingId, method, idempotencyKey)
            );
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            if (res.replayed()) {
                builder.header("X-Idempotent-Replay", "true");
            }
            return builder.body(ApiResponse.ok("Payment intent created", res.body()));
        }

        PaymentGateway.PaymentIntentResponse result = paymentService.createPaymentIntent(userId, bookingId, method, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok("Payment intent created", result));
    }

    @PostMapping("/confirm")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String intentId = (body != null && body.get("intentId") != null) ? String.valueOf(body.get("intentId")) : null;
        String paymentToken = (body != null && body.get("paymentToken") != null) ? String.valueOf(body.get("paymentToken")) : null;
        String userId = (principal != null && principal.getUser() != null) ? principal.getUser().getId() : null;

        if (idempotencyService != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
            Map<String, String> fingerprint = Map.of(
                    "intentId", intentId != null ? intentId : "",
                    "paymentToken", paymentToken != null ? paymentToken : ""
            );
            IdempotencyService.IdempotentResult<Map> res = idempotencyService.execute(
                    "payments:confirm",
                    userId,
                    idempotencyKey,
                    fingerprint,
                    Map.class,
                    () -> paymentService.confirmPayment(userId, intentId, paymentToken)
            );
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
            if (res.replayed()) {
                builder.header("X-Idempotent-Replay", "true");
            }
            return builder.body(ApiResponse.ok("Pago confirmado exitosamente", (Map<String, Object>) res.body()));
        }

        Map<String, Object> result = paymentService.confirmPayment(userId, intentId, paymentToken);
        return ResponseEntity.ok(ApiResponse.ok("Pago confirmado exitosamente", result));
    }

    @GetMapping("/status/{intentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String intentId
    ) {
        String userId = (principal != null && principal.getUser() != null) ? principal.getUser().getId() : null;
        Map<String, Object> status = paymentService.getPaymentStatus(intentId, userId);
        return ResponseEntity.ok(ApiResponse.ok(status));
    }
}
