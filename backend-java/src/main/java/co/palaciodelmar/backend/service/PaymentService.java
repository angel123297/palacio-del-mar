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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentGateway paymentGateway;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Setter
    @Autowired(required = false)
    private co.palaciodelmar.backend.repository.PaymentIntentRepository paymentIntentRepository;

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
                String realSecret = null;
                String status = existingPayment.getStatus();
                BigDecimal amount = existingPayment.getAmount();
                String currency = existingPayment.getCurrency();

                if (paymentIntentRepository != null) {
                    co.palaciodelmar.backend.model.PaymentIntent intent = paymentIntentRepository.findById(existingPayment.getIntentId()).orElse(null);
                    if (intent != null) {
                        realSecret = intent.getClientSecret();
                        status = intent.getStatus();
                        amount = intent.getAmount();
                        currency = intent.getCurrency();
                    }
                }
                if (realSecret == null && existingPayment.getClientSecret() != null) {
                    realSecret = existingPayment.getClientSecret();
                }

                if (realSecret == null || realSecret.isBlank()) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.CONFLICT,
                            "Inconsistencia en clave de idempotencia: el intento de pago original no tiene secreto válido"
                    );
                }

                return new PaymentGateway.PaymentIntentResponse(
                        existingPayment.getIntentId(),
                        realSecret,
                        amount,
                        currency,
                        status
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
        if (booking.getBalanceDue() != null && booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0
                && "paid".equalsIgnoreCase(booking.getPaymentStatus())) {
            throw new IllegalStateException("La reserva ya ha sido pagada en su totalidad.");
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
                .clientSecret(intentResponse.clientSecret())
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

        // Re-consultar payment y booking bajo el lock para evitar carreras con webhooks concurrentes
        payment = paymentRepository.findById(payment.getId()).orElse(payment);
        booking = bookingRepository.findById(booking.getId()).orElse(booking);

        // Reproducción idempotente si esta intención específica ya fue completada o reembolsada previamente
        String currentPayStatus = payment.getStatus();
        boolean alreadyFinalizedOrRefunded = "completed".equalsIgnoreCase(currentPayStatus)
                || "partially_refunded".equalsIgnoreCase(currentPayStatus)
                || "refunded".equalsIgnoreCase(currentPayStatus)
                || "requires_refund".equalsIgnoreCase(currentPayStatus);
        if (alreadyFinalizedOrRefunded) {
            if ("completed".equalsIgnoreCase(currentPayStatus) && "confirmed".equalsIgnoreCase(booking.getStatus())) {
                bookingService.confirmBookingSuiteNights(booking);
            }
            java.util.Map<String, Object> replayMap = new java.util.HashMap<>();
            replayMap.put("payment", toDTO(payment));
            replayMap.put("booking", bookingService.toDTO(booking));
            replayMap.put("status", payment.getStatus());
            replayMap.put("transactionId", payment.getTransactionId());
            return replayMap;
        }

        boolean alreadyCreditedToBooking = payment.getId() != null
                && booking.getProcessedPaymentIds() != null
                && booking.getProcessedPaymentIds().contains(payment.getId());

        if (!alreadyCreditedToBooking && booking.getBalanceDue() != null && booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0
                && "paid".equalsIgnoreCase(booking.getPaymentStatus())) {
            throw new IllegalStateException("La reserva ya ha sido pagada en su totalidad.");
        }

        boolean isPayableStatus = "pending".equalsIgnoreCase(booking.getStatus())
                || "confirmed".equalsIgnoreCase(booking.getStatus())
                || "checked_in".equalsIgnoreCase(booking.getStatus())
                || ("checked_out".equalsIgnoreCase(booking.getStatus()) && booking.getBalanceDue() != null && booking.getBalanceDue().compareTo(BigDecimal.ZERO) > 0);

        if (!alreadyCreditedToBooking && !isPayableStatus) {
            throw new IllegalStateException("No se puede confirmar el pago para una reserva en estado " + booking.getStatus());
        }
        if (!alreadyCreditedToBooking && "pending".equalsIgnoreCase(booking.getStatus()) && booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("El tiempo para pagar esta reserva ha expirado");
        }

        if (!alreadyCreditedToBooking && booking.getBalanceDue() != null && payment.getAmount() != null
                && payment.getAmount().compareTo(booking.getBalanceDue()) > 0) {
            throw new IllegalStateException("El monto del intento de pago (" + payment.getAmount() + ") excede el saldo pendiente actual (" + booking.getBalanceDue() + ")");
        }

        PaymentGateway.PaymentConfirmResponse confirmResponse = paymentGateway.confirmIntent(intentId, paymentToken);

        if (!confirmResponse.success()) {
            payment.setStatus("failed");
            payment.setNotes(confirmResponse.errorMessage());
            paymentRepository.save(payment);
            throw new IllegalStateException("Pago fallido: " + confirmResponse.errorMessage());
        }

        if ("processing".equalsIgnoreCase(confirmResponse.status())) {
            Payment freshPayment = paymentRepository.findById(payment.getId()).orElse(payment);
            if ("completed".equalsIgnoreCase(freshPayment.getStatus())) {
                log.info("Payment {} ya fue completado concurrentemente por webhook; no degradar a processing", payment.getId());
                Booking freshBooking = bookingRepository.findById(payment.getBooking()).orElse(booking);
                java.util.Map<String, Object> returnMap = new java.util.HashMap<>();
                returnMap.put("payment", toDTO(freshPayment));
                returnMap.put("booking", bookingService.toDTO(freshBooking));
                returnMap.put("status", freshPayment.getStatus());
                returnMap.put("transactionId", freshPayment.getTransactionId());
                return returnMap;
            }
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

        String gwStatus = confirmResponse.status() != null ? confirmResponse.status().trim().toLowerCase(java.util.Locale.ROOT) : "";
        boolean isSucceededStatus = "succeeded".equals(gwStatus) || "completed".equals(gwStatus) || "paid".equals(gwStatus);
        if (!isSucceededStatus) {
            payment.setTransactionId(confirmResponse.transactionId());
            if ("refunded".equals(gwStatus)) {
                payment.setStatus("refunded");
                payment.setRefundedAmount(payment.getAmount());
                payment.setNotes("El intento en pasarela ya se encontraba reembolsado");
            } else if ("partially_refunded".equals(gwStatus)) {
                payment.setStatus("partially_refunded");
                payment.setNotes("El intento en pasarela ya se encontraba parcialmente reembolsado");
            } else {
                payment.setStatus("failed");
                payment.setNotes("Estado de pasarela no acreditable: " + confirmResponse.status());
            }
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            throw new IllegalStateException("El pago no puede acreditarse porque la pasarela reportó estado: " + confirmResponse.status());
        }

        Booking updatedBooking;
        boolean newlyConfirmedInThisCall = false;
        if (alreadyCreditedToBooking) {
            log.info("Payment {} ya fue acreditado previamente en Booking {} (processedPaymentIds); evitando doble abono y finalizando Payment.",
                    payment.getId(), booking.getId());
            if ("confirmed".equalsIgnoreCase(booking.getStatus())) {
                bookingService.confirmBookingSuiteNights(booking);
            }
            updatedBooking = booking;
        } else {
            booking.setPaidAmount((booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO).add(payment.getAmount()));
            if (payment.getId() != null && !payment.getId().isBlank()) {
                if (booking.getProcessedPaymentIds() == null) {
                    booking.setProcessedPaymentIds(new java.util.ArrayList<>());
                }
                if (!booking.getProcessedPaymentIds().contains(payment.getId())) {
                    booking.getProcessedPaymentIds().add(payment.getId());
                }
            }

            BigDecimal refundedAmt = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal netPaid = booking.getPaidAmount().subtract(refundedAmt).max(BigDecimal.ZERO);
            BigDecimal balance = booking.resolveEffectiveTotalPrice().subtract(netPaid);
            booking.setBalanceDue(balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance);

            boolean wasPending = "pending".equalsIgnoreCase(booking.getStatus());
            try {
                boolean shouldConfirm = booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0 && wasPending;
                if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                    booking.setPaymentStatus("paid");
                    if (wasPending) {
                        bookingService.changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CONFIRMED, "System", "Pago completado");
                        newlyConfirmedInThisCall = true;
                    }
                } else {
                    booking.setPaymentStatus("partial");
                }

                // Si changeBookingStatus ya persistió la reserva en una instancia real de BookingService, evitar doble guardado redundante
                if (!shouldConfirm || "pending".equalsIgnoreCase(booking.getStatus()) || booking.getId() == null) {
                    updatedBooking = bookingRepository.save(booking);
                } else {
                    updatedBooking = booking;
                }
            } catch (RuntimeException e) {
                // El cargo síncrono ya fue emitido: registrar transactionId e intentar reversión inmediata en pasarela
                payment.setTransactionId(confirmResponse.transactionId());
                boolean refunded = false;
                String refundId = null;
                if (confirmResponse.transactionId() != null && !confirmResponse.transactionId().isBlank() && payment.getAmount() != null) {
                    try {
                        PaymentGateway.PaymentRefundResponse refundResp = paymentGateway.refund(confirmResponse.transactionId(), payment.getAmount());
                        if (refundResp == null) {
                            String compKey = "ref_comp_" + (payment.getId() != null ? payment.getId() : intentId);
                            refundResp = paymentGateway.refund(confirmResponse.transactionId(), payment.getAmount(), compKey);
                        }
                        refunded = (refundResp != null && refundResp.success());
                        refundId = refundResp != null ? refundResp.refundId() : null;
                    } catch (Exception ignored) {
                    }
                }
                payment.setStatus(refunded ? "refunded" : "requires_refund");
                if (refunded) {
                    payment.setRefundedAmount(payment.getAmount());
                    if (refundId != null && !refundId.isBlank()) {
                        if (payment.getProcessedRefundIds() == null) {
                            payment.setProcessedRefundIds(new java.util.ArrayList<>());
                        }
                        if (!payment.getProcessedRefundIds().contains(refundId)) {
                            payment.getProcessedRefundIds().add(refundId);
                        }
                    }
                }
                payment.setUpdatedAt(Instant.now());
                payment.setNotes(refunded
                        ? "Conflicto o fallo de persistencia tras cobro síncrono. Reembolso automático emitido."
                        : "Conflicto o fallo de persistencia tras cobro síncrono. Requiere reembolso manual.");
                paymentRepository.save(payment);

                if (wasPending && booking.getId() != null) {
                    if (bookingService != null) {
                        bookingService.releaseUnpersistedConfirmedSuiteNights(booking);
                    }
                    if (suiteNightRepository != null) {
                        try {
                            suiteNightRepository.deleteByBooking(booking.getId());
                            if (availabilityCacheEvictor != null) {
                                availabilityCacheEvictor.evictAvailability();
                            }
                        } catch (Exception ex) {
                            // Log y continuar propagando la excepción
                        }
                    }
                }

                if (e instanceof org.springframework.dao.OptimisticLockingFailureException) {
                    throw new IllegalStateException("Conflicto al procesar el pago. La reserva fue modificada o cancelada simultáneamente.", e);
                }

                throw new IllegalStateException("Error al persistir la reserva tras el cobro: " + e.getMessage(), e);
            }
        }

        payment.setStatus("completed");
        payment.setTransactionId(confirmResponse.transactionId());
        payment.setUpdatedAt(Instant.now());
        payment.setNotes("Pago confirmado exitosamente");
        Payment savedPayment = paymentRepository.save(payment);

        if (booking.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
             if (redisLockService != null) {
                 try {
                     redisLockService.releaseSuiteHold(
                             updatedBooking.getSuite(),
                             updatedBooking.getUnitSlot(),
                             updatedBooking.getCheckIn(),
                             updatedBooking.getCheckOut(),
                             updatedBooking.getId()
                     );
                 } catch (Exception ex) {
                     log.warn("Error liberando hold Redis tras pago de reserva {}: {}", updatedBooking.getId(), ex.getMessage());
                 }
             }
             if (socialProofService != null) {
                 try {
                     socialProofService.recordBookingActivity(updatedBooking.getSuite(), null, "paid:" + updatedBooking.getId());
                 } catch (Exception ex) {
                     log.warn("Error registrando social proof tras pago de reserva {}: {}", updatedBooking.getId(), ex.getMessage());
                 }
             }
             if (availabilityCacheEvictor != null) {
                 try {
                     availabilityCacheEvictor.evictAvailability();
                 } catch (Exception ex) {
                     log.warn("Error limpiando caché de disponibilidad tras pago de reserva {}: {}", updatedBooking.getId(), ex.getMessage());
                 }
             }
             try {
                 emailService.sendBookingConfirmation(updatedBooking);
             } catch (Exception ex) {
                 log.warn("Error enviando email de confirmación para reserva {}: {}", updatedBooking.getId(), ex.getMessage());
             }
             try {
                 emailService.sendHostBookingNotification(updatedBooking);
             } catch (Exception ex) {
                 log.warn("Error enviando notificación al anfitrión para reserva {}: {}", updatedBooking.getId(), ex.getMessage());
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
