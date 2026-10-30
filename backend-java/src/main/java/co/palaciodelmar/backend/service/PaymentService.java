package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.PaymentDTO;
import co.palaciodelmar.backend.dto.PaymentRequest;
import co.palaciodelmar.backend.exception.ForbiddenAccessException;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentGateway paymentGateway;
    private final EmailService emailService;
    private final UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BookingSseService bookingSseService;

    @Setter
    @Autowired(required = false)
    private RedisLockService redisLockService;

    @Setter
    @Autowired(required = false)
    private SocialProofService socialProofService;

    @Setter
    @Autowired(required = false)
    private AvailabilityCacheEvictor availabilityCacheEvictor;

    @Setter
    @Autowired(required = false)
    private co.palaciodelmar.backend.repository.SuiteNightRepository suiteNightRepository;

    public Map<String, Object> getPaymentConfig() {
        return Map.of(
                "simulated", true,
                "methods", List.of(
                        Map.of("id", "card", "label", "Tarjeta de crédito o débito"),
                        Map.of("id", "pse", "label", "PSE (débito desde tu banco)"),
                        Map.of("id", "nequi", "label", "Nequi")
                ),
                "cancellationPolicy", "Cancelación gratuita hasta 7 días antes del check-in (devolución del 100%). Entre 7 días y 48 horas antes aplica retención del 10%; con menos de 48 horas antes del check-in la retención es del 100%."
        );
    }

    public PaymentGateway.PaymentIntentResponse createPaymentIntent(String userId, String bookingId, String method, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (existingPayment != null && existingPayment.getIntentId() != null) {
                if (!Objects.equals(existingPayment.getBooking(), bookingId)) {
                    throw new IllegalArgumentException("Idempotency key mismatch");
                }
                if (userId != null && !Objects.equals(existingPayment.getUser(), userId)) {
                    throw new IllegalArgumentException("Idempotency key mismatch");
                }
                if (userId == null && existingPayment.getUser() != null) {
                    userRepository.findById(existingPayment.getUser()).ifPresent(u -> {
                        if (u.getPassword() != null && !u.getPassword().isBlank()) {
                            throw new IllegalArgumentException("Idempotency key mismatch");
                        }
                    });
                }
                return new PaymentGateway.PaymentIntentResponse(
                        existingPayment.getIntentId(),
                        "secret_" + existingPayment.getIntentId(),
                        existingPayment.getAmount(),
                        existingPayment.getCurrency(),
                        existingPayment.getStatus()
                );
            }
        }

        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("Reserva no especificada");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + bookingId));

        if (booking.getUser() != null) {
            if (userId != null && !booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para pagar esta reserva");
            }
            if (userId == null) {
                userRepository.findById(booking.getUser()).ifPresent(u -> {
                    if (u.getPassword() != null && !u.getPassword().isBlank()) {
                        throw new IllegalArgumentException("Esta reserva pertenece a un usuario registrado. Inicia sesión para continuar con el pago.");
                    }
                });
            }
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
        if (intentId == null || intentId.isBlank()) {
            throw new IllegalArgumentException("Intención de pago no especificada");
        }
        if (redisLockService != null) {
            return redisLockService.executeWithLock(
                    "payment:" + intentId.trim(),
                    Duration.ofSeconds(15),
                    Duration.ofSeconds(5),
                    () -> doConfirmPayment(userId, intentId.trim(), paymentToken)
            );
        }
        return doConfirmPayment(userId, intentId.trim(), paymentToken);
    }

    private Map<String, Object> doConfirmPayment(String userId, String intentId, String paymentToken) {
        Payment payment = paymentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Intención de pago no encontrada"));

        if (redisLockService != null && payment.getBooking() != null && !payment.getBooking().isBlank()) {
            return redisLockService.executeWithLock(
                    "booking:" + payment.getBooking().trim(),
                    Duration.ofSeconds(15),
                    Duration.ofSeconds(5),
                    () -> doConfirmPaymentUnderLocks(userId, intentId, paymentToken, payment)
            );
        }
        return doConfirmPaymentUnderLocks(userId, intentId, paymentToken, payment);
    }

    private Map<String, Object> doConfirmPaymentUnderLocks(String userId, String intentId, String paymentToken, Payment payment) {
        Booking booking = bookingRepository.findById(payment.getBooking())
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (booking.getUser() != null) {
            if (userId != null && !booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para confirmar este pago");
            }
            if (userId == null) {
                userRepository.findById(booking.getUser()).ifPresent(u -> {
                    if (u.getPassword() != null && !u.getPassword().isBlank()) {
                        throw new IllegalArgumentException("Esta reserva pertenece a un usuario registrado. Inicia sesión para continuar con el pago.");
                    }
                });
            }
        }

        // Reproducción idempotente si esta intención específica ya fue completada previamente
        if ("completed".equalsIgnoreCase(payment.getStatus())) {
            java.util.Map<String, Object> replayMap = new java.util.HashMap<>();
            replayMap.put("payment", toDTO(payment));
            replayMap.put("booking", bookingService.toDTO(booking));
            replayMap.put("status", payment.getStatus());
            replayMap.put("transactionId", payment.getTransactionId());
            return replayMap;
        }

        if (booking.getBalanceDue() != null && booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0
                && "paid".equalsIgnoreCase(booking.getPaymentStatus())) {
            throw new IllegalStateException("La reserva ya ha sido pagada en su totalidad.");
        }

        if (!"pending".equalsIgnoreCase(booking.getStatus()) && !"confirmed".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("No se puede confirmar el pago para una reserva en estado " + booking.getStatus());
        }
        if ("pending".equalsIgnoreCase(booking.getStatus()) && booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("El tiempo para pagar esta reserva ha expirado");
        }

        PaymentGateway.PaymentConfirmResponse confirmResponse = paymentGateway.confirmIntent(intentId, paymentToken);

        if (!confirmResponse.success()) {
            payment.setStatus("failed");
            payment.setNotes(confirmResponse.errorMessage());
            paymentRepository.save(payment);
            throw new IllegalStateException("Pago fallido: " + confirmResponse.errorMessage());
        }

        if ("processing".equalsIgnoreCase(confirmResponse.status())) {
            payment.setStatus("processing");
            payment.setNotes("Procesando pago con pasarela");
            payment.setUpdatedAt(Instant.now());
            Payment savedPayment = paymentRepository.save(payment);

            java.util.Map<String, Object> returnMap = new java.util.HashMap<>();
            returnMap.put("payment", toDTO(savedPayment));
            returnMap.put("booking", bookingService.toDTO(booking));
            returnMap.put("status", "processing");
            return returnMap;
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
            // El cargo síncrono ya fue emitido: registrar transactionId e intentar reversión inmediata en pasarela
            payment.setTransactionId(confirmResponse.transactionId());
            boolean refunded = false;
            if (confirmResponse.transactionId() != null && !confirmResponse.transactionId().isBlank() && payment.getAmount() != null) {
                try {
                    PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(confirmResponse.transactionId(), payment.getAmount());
                    refunded = (refundResp != null && refundResp.success());
                } catch (Exception ignored) {
                }
            }
            payment.setStatus(refunded ? "refunded" : "requires_refund");
            payment.setUpdatedAt(Instant.now());
            payment.setNotes(refunded
                    ? "Conflicto por concurrencia tras cobro síncrono. Reembolso automático emitido."
                    : "Conflicto por concurrencia tras cobro síncrono. Requiere reembolso manual.");
            paymentRepository.save(payment);

            if (suiteNightRepository != null && booking.getId() != null) {
                try {
                    suiteNightRepository.deleteByBooking(booking.getId());
                    if (availabilityCacheEvictor != null) {
                        availabilityCacheEvictor.evictAvailability();
                    }
                } catch (Exception ex) {
                    // Log y continuar propagando la excepción
                }
            }

            throw new IllegalStateException("Conflicto al procesar el pago. La reserva fue modificada o cancelada simultáneamente.");
        }

        payment.setStatus("completed");
        payment.setTransactionId(confirmResponse.transactionId());
        payment.setUpdatedAt(Instant.now());
        payment.setNotes("Pago confirmado exitosamente");
        Payment savedPayment = paymentRepository.save(payment);

        if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
             if (redisLockService != null) {
                 redisLockService.releaseSuiteHold(
                         updatedBooking.getSuite(),
                         updatedBooking.getUnitSlot(),
                         updatedBooking.getCheckIn(),
                         updatedBooking.getCheckOut(),
                         updatedBooking.getId()
                 );
             }
             if (socialProofService != null) {
                 socialProofService.recordBookingActivity(updatedBooking.getSuite(), null, "paid:" + updatedBooking.getId());
             }
             if (availabilityCacheEvictor != null) {
                 availabilityCacheEvictor.evictAvailability();
             }
             emailService.sendBookingConfirmation(updatedBooking);
             emailService.sendHostBookingNotification(updatedBooking);
             try {
                 if (updatedBooking.getUser() != null) {
                     bookingSseService.emitBookingUpdate(updatedBooking.getUser(), bookingService.toDTO(updatedBooking));
                 }
             } catch (Exception ex) {
                 // Non-blocking SSE notification
             }
        }

        java.util.Map<String, Object> returnMap = new java.util.HashMap<>();
        returnMap.put("payment", toDTO(savedPayment));
        returnMap.put("booking", bookingService.toDTO(updatedBooking));
        returnMap.put("status", savedPayment.getStatus());
        returnMap.put("transactionId", savedPayment.getTransactionId());
        return returnMap;
    }

    public java.util.Map<String, Object> getPaymentStatus(String intentId, String userId) {
        Payment payment = paymentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Intención de pago no encontrada: " + intentId));

        Booking booking = bookingRepository.findById(payment.getBooking())
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (booking.getUser() != null) {
            if (userId != null && !booking.getUser().equals(userId)) {
                throw new ForbiddenAccessException("No tienes permiso para consultar este pago");
            }
            if (userId == null) {
                userRepository.findById(booking.getUser()).ifPresent(u -> {
                    if (u.getPassword() != null && !u.getPassword().isBlank()) {
                        throw new ForbiddenAccessException("No tienes permiso para consultar este pago. Inicia sesión.");
                    }
                });
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("intentId", intentId);
        result.put("status", payment.getStatus());
        result.put("paymentMethod", payment.getPaymentMethod());
        result.put("amount", payment.getAmount());
        result.put("transactionId", payment.getTransactionId());
        result.put("bookingId", booking.getId());
        result.put("bookingStatus", booking.getStatus());
        result.put("paymentStatus", booking.getPaymentStatus());
        result.put("notes", payment.getNotes());
        return result;
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
