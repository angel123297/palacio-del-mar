package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final SuiteNightRepository suiteNightRepository;
    private final BookingService bookingService;

    /**
     * Limpia periódicamente las reservas en estado "pending" que no completaron el pago
     * dentro del tiempo límite de 30 minutos (expiresAt < now), liberando el inventario de suites.
     */
    @Scheduled(fixedRateString = "${booking.expiry.check.interval.ms:60000}")
    public void cleanupExpiredBookings() {
        Instant now = Instant.now();
        List<Booking> expiredBookings = bookingRepository.findByStatusAndExpiresAtBefore("pending", now);

        if (expiredBookings.isEmpty()) {
            return;
        }

        log.info("Procesando {} reservas expiradas para liberación de inventario...", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            try {
                // 1. Liberar noches retenidas en SuiteNight
                List<SuiteNight> heldNights = suiteNightRepository.findByBooking(booking.getId());
                if (!heldNights.isEmpty()) {
                    suiteNightRepository.deleteAll(heldNights);
                    log.info("Liberadas {} noches retenidas para la reserva {}", heldNights.size(), booking.getId());
                }

                // 2. Transicionar la reserva al estado terminal EXPIRED
                bookingService.changeBookingStatus(
                        booking,
                        BookingStatus.EXPIRED,
                        "System-Scheduler",
                        "Tiempo límite de pago expirado (30 minutos)"
                );
                bookingRepository.save(booking);

                log.info("Reserva {} marcada como EXPIRED exitosamente", booking.getId());
            } catch (Exception e) {
                log.error("Error al expirar reserva {}: {}", booking.getId(), e.getMessage());
            }
        }
    }
}
