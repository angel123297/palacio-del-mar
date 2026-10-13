package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentIntentResponse createIntent(Booking booking, String method, String idempotencyKey);
    PaymentConfirmResponse confirmIntent(String intentId, String paymentToken);
    PaymentRefundResponse refund(String transactionId, BigDecimal amount);

    record PaymentIntentResponse(String intentId, String clientSecret, BigDecimal amount, String currency, String status) {}
    record PaymentConfirmResponse(boolean success, String transactionId, String status, String errorMessage) {}
    record PaymentRefundResponse(boolean success, String refundId, String status, String errorMessage) {}
}
