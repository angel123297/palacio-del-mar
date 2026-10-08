package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.PaymentRequest;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public Map<String, Object> getPaymentConfig() {
        return Map.of(
                "simulated", true,
                "methods", List.of(
                        Map.of("id", "card", "label", "Tarjeta de crédito o débito"),
                        Map.of("id", "pse", "label", "PSE (débito desde tu banco)"),
                        Map.of("id", "nequi", "label", "Nequi")
                ),
                "cancellationPolicy", "Cancelación gratuita hasta 48 horas antes del check-in. Devolución del 100%."
        );
    }

    public Map<String, Object> checkoutPayment(String userId, String bookingId, String method) {
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

        BigDecimal balance = booking.getBalanceDue() != null ? booking.getBalanceDue() : BigDecimal.ZERO;

        if ("paid".equalsIgnoreCase(booking.getPaymentStatus()) && balance.compareTo(BigDecimal.ZERO) <= 0) {
            BookingDTO bookingDTO = bookingService.toDTO(booking);
            Payment existingPayment = paymentRepository.findByBooking(bookingId)
                    .stream().findFirst().orElse(null);
            return Map.of(
                    "payment", existingPayment != null ? existingPayment : Map.of("amount", booking.getTotalPrice(), "status", "completed"),
                    "booking", bookingDTO
            );
        }

        BigDecimal amount = balance.compareTo(BigDecimal.ZERO) > 0
                ? balance
                : booking.getTotalPrice();

        String receiptNumber = "REC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .booking(booking.getId())
                .user(userId != null ? userId : booking.getUser())
                .amount(amount)
                .currency("COP")
                .status("completed")
                .provider("simulated")
                .transactionId(transactionId)
                .paymentMethod(method != null ? method : "card")
                .notes("Pago procesado exitosamente en modo simulado")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        booking.setPaidAmount((booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO).add(amount));
        booking.setBalanceDue(BigDecimal.ZERO);
        booking.setPaymentStatus("paid");
        booking.setStatus("confirmed");
        booking.setUpdatedAt(Instant.now());
        Booking updatedBooking = bookingRepository.save(booking);

        BookingDTO bookingDTO = bookingService.toDTO(updatedBooking);

        Map<String, Object> paymentData = Map.of(
                "id", savedPayment.getId(),
                "receiptNumber", receiptNumber,
                "amount", savedPayment.getAmount(),
                "status", savedPayment.getStatus(),
                "paymentMethod", savedPayment.getPaymentMethod(),
                "transactionId", transactionId,
                "createdAt", savedPayment.getCreatedAt()
        );

        return Map.of(
                "payment", paymentData,
                "booking", bookingDTO
        );
    }

    public Payment processPayment(String userId, PaymentRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + request.getBookingId()));

        if (userId != null && !booking.getUser().equals(userId)) {
            throw new IllegalArgumentException("No tienes permiso para pagar esta reserva");
        }

        if ("cancelled".equalsIgnoreCase(booking.getStatus()) || "expired".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalArgumentException("No se puede pagar una reserva cancelada o vencida");
        }

        boolean isSuccess = request.getSimulateFailure() == null || !request.getSimulateFailure();
        String paymentStatus = isSuccess ? "completed" : "failed";
        String transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        BigDecimal balance = booking.getBalanceDue() != null && booking.getBalanceDue().compareTo(BigDecimal.ZERO) > 0
                ? booking.getBalanceDue()
                : booking.getTotalPrice();

        BigDecimal amount = request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getAmount()
                : balance;

        Payment payment = Payment.builder()
                .booking(booking.getId())
                .user(userId)
                .amount(amount)
                .currency("COP")
                .status(paymentStatus)
                .provider("simulated")
                .transactionId(transactionId)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "simulated")
                .notes(isSuccess ? "Pago simulado exitoso (localhost)" : "Pago simulado fallido (localhost)")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        if (isSuccess) {
            BigDecimal currentPaid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal newPaid = currentPaid.add(amount);
            booking.setPaidAmount(newPaid);

            if (newPaid.compareTo(booking.getTotalPrice()) >= 0) {
                booking.setBalanceDue(BigDecimal.ZERO);
                booking.setPaymentStatus("paid");
                booking.setStatus("confirmed");
            } else {
                booking.setBalanceDue(booking.getTotalPrice().subtract(newPaid));
                booking.setPaymentStatus("partial");
            }
            booking.setUpdatedAt(Instant.now());
            bookingRepository.save(booking);
        }

        return savedPayment;
    }
}
