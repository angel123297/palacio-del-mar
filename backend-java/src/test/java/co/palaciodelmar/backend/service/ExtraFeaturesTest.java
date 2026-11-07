package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.exception.ForbiddenAccessException;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Review;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.ReviewRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ExtraFeaturesTest {

    @Test
    @DisplayName("ReviewService valida estancia completada y analiza sentimiento con alertas de mantenimiento")
    void verifiesVerifiedReviewAndSentimentAnalysis() {
        ReviewRepository reviewRepository = Mockito.mock(ReviewRepository.class);
        BookingRepository bookingRepository = Mockito.mock(BookingRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        ReviewService reviewService = new ReviewService(reviewRepository, bookingRepository, userRepository);

        String bookingId = "BOOK-123";
        String userId = "USER-456";

        Booking booking = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("SUITE-001")
                .status("checked_out")
                .guestDetails(Booking.GuestDetails.builder().fullName("Mariana Gomez").build())
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> {
            Review r = i.getArgument(0);
            r.setId("REV-001");
            return r;
        });

        // Comentario con elogio y advertencia sobre el aire
        String comment = "La piscina es un sueño y la vista increíble, pero el aire acondicionado hace ruido en la noche.";
        ReviewDTO result = reviewService.addVerifiedReview(bookingId, userId, 4, comment);

        assertNotNull(result);
        assertTrue(result.getVerifiedStay(), "La reseña debe marcarse como estancia verificada");
        assertEquals("POSITIVE", result.getSentiment(), "Con 4 estrellas debe catalogarse como positiva");
        assertTrue(result.getKeyHighlights().contains("Piscina"), "Debe detectar Piscina en los aspectos destacados");
        assertTrue(result.getKeyHighlights().contains("Vistas"), "Debe detectar Vistas en los aspectos destacados");
        assertNotNull(result.getMaintenanceAlert(), "Debe emitir alerta de mantenimiento por el aire");
        assertTrue(result.getMaintenanceAlert().contains("aire acondicionado"));
    }

    @Test
    @DisplayName("ReviewService bloquea duplicados para la misma reserva")
    void blocksDuplicateReviewForSameBooking() {
        ReviewRepository reviewRepository = Mockito.mock(ReviewRepository.class);
        BookingRepository bookingRepository = Mockito.mock(BookingRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        ReviewService reviewService = new ReviewService(reviewRepository, bookingRepository, userRepository);

        when(bookingRepository.findById("BOOK-DUP")).thenReturn(Optional.of(Booking.builder().id("BOOK-DUP").user("U1").status("checked_out").build()));
        when(reviewRepository.existsByBookingId("BOOK-DUP")).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                reviewService.addVerifiedReview("BOOK-DUP", "U1", 5, "Excelente")
        );
    }

    @Test
    @DisplayName("ReviewService rechaza reseña si la reserva no ha completado el check-out (pending, confirmed, expired)")
    void blocksReviewBeforeStayCompleted() {
        ReviewRepository reviewRepository = Mockito.mock(ReviewRepository.class);
        BookingRepository bookingRepository = Mockito.mock(BookingRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        ReviewService reviewService = new ReviewService(reviewRepository, bookingRepository, userRepository);

        when(bookingRepository.findById("BK-PENDING")).thenReturn(Optional.of(
                Booking.builder().id("BK-PENDING").user("U1").status("pending").build()
        ));
        when(bookingRepository.findById("BK-EXPIRED")).thenReturn(Optional.of(
                Booking.builder().id("BK-EXPIRED").user("U1").status("expired").build()
        ));

        IllegalStateException exPending = assertThrows(IllegalStateException.class, () ->
                reviewService.addVerifiedReview("BK-PENDING", "U1", 5, "Excelente")
        );
        assertTrue(exPending.getMessage().contains("después del check-out"));

        IllegalStateException exExpired = assertThrows(IllegalStateException.class, () ->
                reviewService.addVerifiedReview("BK-EXPIRED", "U1", 5, "Excelente")
        );
        assertTrue(exExpired.getMessage().contains("después del check-out"));
    }

    @Test
    @DisplayName("ReviewService lanza ForbiddenAccessException si el usuario no es el titular de la reserva")
    void blocksReviewWhenUserIsNotOwner() {
        ReviewRepository reviewRepository = Mockito.mock(ReviewRepository.class);
        BookingRepository bookingRepository = Mockito.mock(BookingRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);

        ReviewService reviewService = new ReviewService(reviewRepository, bookingRepository, userRepository);

        when(bookingRepository.findById("BK-LEGIT")).thenReturn(Optional.of(
                Booking.builder().id("BK-LEGIT").user("U1").status("checked_out").build()
        ));

        ForbiddenAccessException ex = assertThrows(ForbiddenAccessException.class, () ->
                reviewService.addVerifiedReview("BK-LEGIT", "U2_ATTACKER", 5, "Excelente")
        );
        assertEquals("Solo el huésped titular puede calificar esta reserva", ex.getMessage());
    }
}
