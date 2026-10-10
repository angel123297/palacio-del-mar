package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulatedPaymentGateway implements PaymentGateway {

    private final ConcurrentHashMap<String, IntentRecord> intents = new ConcurrentHashMap<>();

    @Override
    public PaymentIntentResponse createIntent(Booking booking, String method, String idempotencyKey) {
        if (idempotencyKey != null) {
            for (IntentRecord existing : intents.values()) {
                if (idempotencyKey.equals(existing.idempotencyKey())) {
                    return new PaymentIntentResponse(existing.id(), existing.clientSecret(), existing.amount(), "COP", "requires_payment_method");
                }
            }
        }
        String intentId = "pi_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = intentId + "_secret_" + UUID.randomUUID().toString().substring(0, 8);
        BigDecimal amount = booking.getBalanceDue();
        
        intents.put(intentId, new IntentRecord(intentId, amount, "pending", idempotencyKey, clientSecret));
        
        return new PaymentIntentResponse(intentId, clientSecret, amount, "COP", "requires_payment_method");
    }

    @Override
    public PaymentConfirmResponse confirmIntent(String intentId, String paymentToken) {
        IntentRecord record = intents.get(intentId);
        if (record == null) {
            return new PaymentConfirmResponse(false, null, "failed", "Intent not found");
        }

        if (paymentToken == null) paymentToken = "";

        // Simulated card responses
        if (paymentToken.endsWith("0002")) {
            return new PaymentConfirmResponse(false, null, "failed", "Tarjeta rechazada por el banco");
        } else if (paymentToken.endsWith("9995")) {
            return new PaymentConfirmResponse(false, null, "failed", "Fondos insuficientes");
        } else if (paymentToken.endsWith("0069")) {
            return new PaymentConfirmResponse(false, null, "failed", "Tarjeta vencida");
        }

        String txId = "ch_" + UUID.randomUUID().toString().replace("-", "");
        return new PaymentConfirmResponse(true, txId, "succeeded", null);
    }

    private record IntentRecord(String id, BigDecimal amount, String status, String idempotencyKey, String clientSecret) {}
}
