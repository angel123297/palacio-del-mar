package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.PaymentDTO;
import co.palaciodelmar.backend.dto.PaymentRequest;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentGateway paymentGateway;
    private final EmailNotificationService emailNotificationService;
    private final WhatsAppService whatsAppService;
    private final SuiteRepository suiteRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getPaymentConfig() {
        return Map.of(
                "simulated", true,
                "methods", List.of(
                        Map.of("id", "card", "label", "Tarjeta de crédito o débito"),
                        Map.of("id", "pse", "label", "PSE (débito desde tu banco)"),
                        Map.of("id", "nequi", "label", "Nequi")
                ),
                "cancellationPolicy", "Cancelación gratuita hasta 7 días antes del check-in. Devolución del 100%, de lo contrario aplica retención del 10%."
        );
    }

    public PaymentGateway.PaymentIntentResponse createPaymentIntent(String userId, String bookingId, String method, String idempotencyKey) {
        if (idempotencyKey != null) {
            Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existingPayment != null && existingPayment.getIntentId() != null) {
                if (userId != null && !existingPayment.getUser().equals(userId)) throw new IllegalArgumentException("Idempotency key mismatch");
                if (!existingPayment.getBooking().equals(bookingId)) throw new IllegalArgumentException("Idempotency key mismatch");
                return new PaymentGateway.PaymentIntentResponse(existingPayment.getIntentId(), "secret_" + existingPayment.getIntentId(), existingPayment.getAmount(), existingPayment.getCurrency(), existingPayment.getStatus());
            }
        }

        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("Reserva no especificada");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + bookingId));

        if (userId != null && !booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para pagar esta reserva");
        }

        if ("cancelled".equalsIgnoreCase(booking.getStatus()) || "expired".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede pagar una reserva cancelada o vencida");
        }

        PaymentGateway.PaymentIntentResponse intentResponse = paymentGateway.createIntent(booking, method, idempotencyKey);

        Payment payment = Payment.builder()
                .booking(booking.getId())
                .user(userId != null ? userId : booking.getUser())
                .amount(intentResponse.amount())
                .currency(intentResponse.currency())
                .status("pending")
                .provider("simulated")
                .paymentMethod(method != null ? method : "card")
                .idempotencyKey(idempotencyKey)
                .intentId(intentResponse.intentId())
                .notes("Intención de pago creada")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        paymentRepository.save(payment);
        return intentResponse;
    }

    public Map<String, Object> confirmPayment(String userId, String intentId, String paymentToken) {
        Payment payment = paymentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Intención de pago no encontrada"));

        Booking booking = bookingRepository.findById(payment.getBooking())
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (userId != null && !booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para confirmar este pago");
        }

        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("No se puede confirmar el pago para una reserva en estado " + booking.getStatus());
        }
        if ("pending".equalsIgnoreCase(booking.getStatus()) && booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("El tiempo para pagar esta reserva ha expirado");
        }

        if ("completed".equals(payment.getStatus())) {
            return Map.of("payment", toDTO(payment), "booking", bookingService.toDTO(booking));
        }

        PaymentGateway.PaymentConfirmResponse confirmResponse = paymentGateway.confirmIntent(intentId, paymentToken);

        if (!confirmResponse.success()) {
            payment.setStatus("failed");
            payment.setNotes(confirmResponse.errorMessage());
            paymentRepository.save(payment);
            throw new IllegalStateException("Pago fallido: " + confirmResponse.errorMessage());
        }

        booking.setPaidAmount((booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO).add(payment.getAmount()));
        
        BigDecimal balance = booking.getTotalPrice().subtract(booking.getPaidAmount());
        booking.setBalanceDue(balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance);

        if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
             booking.setPaymentStatus("paid");
             bookingService.changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CONFIRMED, "System", "Pago completado");
        } else {
             booking.setPaymentStatus("partial");
        }

        Booking updatedBooking;
        try {
            updatedBooking = bookingRepository.save(booking);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            payment.setStatus("failed");
            payment.setNotes("Conflicto por concurrencia. La reserva fue modificada simultáneamente.");
            paymentRepository.save(payment);
            throw new IllegalStateException("Conflicto al procesar el pago. La reserva fue modificada o cancelada simultáneamente.");
        }

        payment.setStatus("completed");
        payment.setTransactionId(confirmResponse.transactionId());
        payment.setUpdatedAt(Instant.now());
        payment.setNotes("Pago confirmado exitosamente");
        Payment savedPayment = paymentRepository.save(payment);

        if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
             emailNotificationService.sendBookingConfirmation(updatedBooking);
             Suite suite = updatedBooking.getSuite() != null ? suiteRepository.findById(updatedBooking.getSuite()).orElse(null) : null;
             User guest = updatedBooking.getUser() != null ? userRepository.findById(updatedBooking.getUser()).orElse(null) : null;
             whatsAppService.sendBookingConfirmation(updatedBooking, guest, suite);
        }

        return Map.of(
            "payment", toDTO(savedPayment),
            "booking", bookingService.toDTO(updatedBooking)
        );
    }

    public PaymentDTO toDTO(Payment payment) {
        if (payment == null) return null;
        return PaymentDTO.builder()
                .id(payment.getId())
                .bookingId(payment.getBooking())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .provider(payment.getProvider())
                .transactionId(payment.getTransactionId())
                .paymentMethod(payment.getPaymentMethod())
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
