package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;


import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaymentConfig() {
        Map<String, Object> config = paymentService.getPaymentConfig();
        return ResponseEntity.ok(ApiResponse.ok(config));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<co.palaciodelmar.backend.service.PaymentGateway.PaymentIntentResponse>> createIntent(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String bookingId = (String) body.get("bookingId");
        String method = (String) body.getOrDefault("method", "card");
        String userId = principal != null ? principal.getUser().getId() : null;

        co.palaciodelmar.backend.service.PaymentGateway.PaymentIntentResponse result = paymentService.createPaymentIntent(userId, bookingId, method, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.ok("Payment intent created", result));
    }


    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        String intentId = (String) body.get("intentId");
        String paymentToken = (String) body.get("paymentToken"); // e.g., "tok_4242"
        String userId = principal != null ? principal.getUser().getId() : null;

        Map<String, Object> result = paymentService.confirmPayment(userId, intentId, paymentToken);
        return ResponseEntity.ok(ApiResponse.ok("Pago confirmado exitosamente", result));
    }
}
