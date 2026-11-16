package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.service.AvailabilityCacheEvictor;
import co.palaciodelmar.backend.service.BookingService;
import co.palaciodelmar.backend.service.RedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final BookingService bookingService;

    @Setter
    @Autowired(required = false)
    private RedisLockService redisLockService;

    @Setter
    @Autowired(required = false)
    private AvailabilityCacheEvictor availabilityCacheEvictor;

    @Setter
    @Autowired(required = false)
    private java.time.Clock clock = java.time.Clock.system(java.time.ZoneId.of("America/Bogota"));

    /**
     * Limpia periódicamente las reservas en estado "pending" que no completaron el pago
     * dentro del tiempo límite de 15 minutos (expiresAt < now), liberando el inventario de suites.
     */
    @Scheduled(fixedDelayString = "${booking.expiry.check.interval.ms:60000}")
    public void cleanupExpiredBookings() {
        Instant now = clock.instant();
        // Incluir también reservas pending con pago parcial cuyo tiempo de retención expiró
        List<Booking> expiredBookings = bookingRepository.findByStatusAndExpiresAtBefore("pending", now).stream()
                .filter(b -> !"paid".equalsIgnoreCase(b.getPaymentStatus()))
                .toList();

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Procesando {} reservas expiradas para liberación de inventario...", expiredBookings.size());
        boolean anyReleased = false;

        for (Booking candidate : expiredBookings) {
            if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
                continue;
            }
            try {
                boolean released = executeWithBookingLock(candidate.getId(), () -> expireSingleBookingSafely(candidate.getId()));
                if (released) {
                    anyReleased = true;
                }
            } catch (Exception e) {
                log.error("Error al expirar reserva {}: {}", candidate.getId(), e.getMessage());
            }
        }

        if (anyReleased && availabilityCacheEvictor != null) {
            availabilityCacheEvictor.evictAvailability();
        }
    }

    private boolean expireSingleBookingSafely(String bookingId) {
        Booking fresh = bookingRepository.findById(bookingId).orElse(null);
        if (fresh == null) {
            return false;
        }

        boolean stillPending = "pending".equalsIgnoreCase(fresh.getStatus());
        boolean notFullyPaid = !"paid".equalsIgnoreCase(fresh.getPaymentStatus());
        boolean hasUnsettledBalance = fresh.getBalanceDue() == null
                || fresh.getBalanceDue().signum() > 0
                || fresh.getPaidAmount() == null
                || fresh.getPaidAmount().signum() <= 0;
        boolean stillExpired = fresh.getExpiresAt() != null && fresh.getExpiresAt().isBefore(Instant.now());

        if (!stillPending || !notFullyPaid || !hasUnsettledBalance || !stillExpired) {
            log.info("Omitiendo expiración de reserva {} porque su estado cambió concurrentemente (status={}, paidAmount={})",
                    bookingId, fresh.getStatus(), fresh.getPaidAmount());
            return false;
        }

        // 1. Transicionar y persistir EXPIRED primero (protegido por @Version contra confirmación concurrente).
        // Si ya hubo un pago parcial cobrado, registrar requires_refund atómicamente junto con EXPIRED
        // para no perder la devolución pendiente ante fallos posteriores.
        java.math.BigDecimal paid = fresh.getPaidAmount() != null ? fresh.getPaidAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal refunded = fresh.getRefundedAmount() != null ? fresh.getRefundedAmount() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal netPaid = paid.subtract(refunded).max(java.math.BigDecimal.ZERO);
        String expKey = "ref_exp_" + fresh.getId();
        if (netPaid.signum() > 0) {
            fresh.setPendingRefundAmount(netPaid);
            fresh.setPendingRefundIdempotencyKey(expKey);
            fresh.setPaymentStatus("requires_refund");
        }
        bookingService.changeBookingStatus(
                fresh,
                BookingStatus.EXPIRED,
                "System-Scheduler",
                "Tiempo límite de pago expirado (15 minutos)"
        );
        bookingRepository.save(fresh);

        // 2. Solo tras persistir EXPIRED con éxito, liberar noches retenidas no confirmadas y hold en Redis
        List<SuiteNight> heldNights = suiteNightRepository.findByBooking(bookingId).stream()
                .filter(sn -> !"confirmed".equalsIgnoreCase(sn.getStatus()))
                .toList();
        if (!heldNights.isEmpty()) {
            suiteNightRepository.deleteAll(heldNights);
            log.info("Liberadas {} noches retenidas para la reserva {}", heldNights.size(), bookingId);
        }
        if (redisLockService != null) {
            redisLockService.releaseSuiteHold(
                    fresh.getSuite(),
                    fresh.getUnitSlot(),
                    fresh.getCheckIn(),
                    fresh.getCheckOut(),
                    bookingId
            );
        }

        // 3. Si la reserva expirada tenía un pago parcial cobrado, intentar reembolso automático
        if (netPaid.signum() > 0) {
            bookingService.executeSequentialRefund(fresh, netPaid, expKey);
            java.math.BigDecimal updatedRefunded = fresh.getRefundedAmount() != null ? fresh.getRefundedAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal remaining = paid.subtract(updatedRefunded).max(java.math.BigDecimal.ZERO);
            if (remaining.signum() > 0) {
                fresh.setPendingRefundAmount(remaining);
                fresh.setPendingRefundIdempotencyKey(expKey);
                fresh.setPaymentStatus("requires_refund");
                bookingRepository.save(fresh);
            }
        }

        log.info("Reserva {} marcada como EXPIRED exitosamente", bookingId);
        return true;
    }

    private <T> T executeWithBookingLock(String bookingId, Supplier<T> action) {
        if (redisLockService == null) {
            return action.get();
        }
        String lockKey = "booking:" + bookingId;
        if (redisLockService.isDistributedConfigured()) {
            return redisLockService.executeWithStrictDistributedLock(
                    lockKey, Duration.ofSeconds(10), Duration.ofSeconds(2), action);
        }
        return redisLockService.executeWithLock(
                lockKey, Duration.ofSeconds(10), Duration.ofSeconds(2), action);
    }
}
