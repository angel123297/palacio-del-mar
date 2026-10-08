package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.PaymentRequest;
import co.palaciodelmar.backend.model.Payment;
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkoutPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        String bookingId = (String) body.get("bookingId");
        String method = (String) body.getOrDefault("method", "card");
        String userId = principal != null ? principal.getUser().getId() : null;

        Map<String, Object> result = paymentService.checkoutPayment(userId, bookingId, method);
        return ResponseEntity.ok(ApiResponse.ok("Pago procesado exitosamente (simulado)", result));
    }

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<Payment>> processPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody PaymentRequest request
    ) {
        Payment payment = paymentService.processPayment(principal.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Pago procesado exitosamente (simulado)", payment));
    }
}
