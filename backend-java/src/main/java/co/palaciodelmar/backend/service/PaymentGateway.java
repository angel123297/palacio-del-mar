package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentIntentResponse createIntent(Booking booking, String method, String idempotencyKey);
    PaymentConfirmResponse confirmIntent(String intentId, String paymentToken);

    record PaymentIntentResponse(String intentId, String clientSecret, BigDecimal amount, String currency, String status) {}
    record PaymentConfirmResponse(boolean success, String transactionId, String status, String errorMessage) {}
}
