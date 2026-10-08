package co.palaciodelmar.backend.config;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
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

    @Scheduled(cron = "0 */5 * * * *")
    public void releaseExpiredBookings() {
        Instant now = Instant.now();
        List<Booking> expiredPending = bookingRepository.findByStatusAndExpiresAtBefore("pending", now);

        for (Booking booking : expiredPending) {
            log.info("Expirando reserva vencida: {}", booking.getId());
            booking.setStatus("expired");
            booking.setUpdatedAt(now);
            bookingRepository.save(booking);
            suiteNightRepository.deleteByBooking(booking.getId());
        }
    }
}
