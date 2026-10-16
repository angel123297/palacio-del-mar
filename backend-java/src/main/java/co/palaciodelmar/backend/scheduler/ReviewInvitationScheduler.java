package co.palaciodelmar.backend.scheduler;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Tarea programada que envía invitaciones automáticas por correo para calificar la estadía
 * a huéspedes cuyas reservas ya finalizaron (CHECKED_OUT o COMPLETED) y aún no han recibido la invitación.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewInvitationScheduler {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final BookingRepository bookingRepository;
    private final EmailService emailService;

    @Scheduled(fixedRateString = "${palacio.reviews.invitation.interval.ms:300000}") // cada 5 min
    public void sendAutomatedReviewInvitations() {
        LocalDate today = LocalDate.now(BOGOTA);
        List<String> completedStatuses = List.of("checked_out", "completed");

        List<Booking> candidates = bookingRepository.findByStatusIn(completedStatuses).stream()
                .filter(b -> b.getReviewRequestSentAt() == null)
                .filter(b -> b.getCheckOut() != null && !b.getCheckOut().isAfter(today))
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        log.info("Encontradas {} reservas finalizadas para envío automático de invitación a reseña...", candidates.size());

        for (Booking booking : candidates) {
            try {
                emailService.sendReviewInvitation(booking);
                booking.setReviewRequestSentAt(Instant.now());
                booking.setUpdatedAt(Instant.now());
                bookingRepository.save(booking);

                log.info("Invitación a reseña enviada automáticamente para reserva {}", booking.getId());
            } catch (Exception e) {
                log.error("Error al enviar invitación a reseña para reserva {}: {}", booking.getId(), e.getMessage());
            }
        }
    }
}
