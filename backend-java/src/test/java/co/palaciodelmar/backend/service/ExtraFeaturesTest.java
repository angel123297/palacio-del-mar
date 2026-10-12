package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Review;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.ReviewRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ExtraFeaturesTest {

    @Test
    @DisplayName("WhatsAppService envía notificación simulada con formato de lujo")
    void verifiesWhatsAppSimulationFormatting() {
        WhatsAppService whatsAppService = new WhatsAppService();
        Booking booking = Booking.builder()
                .id("BOOK-777")
                .guestDetails(Booking.GuestDetails.builder().fullName("Carlos Santana").phone("+573001234567").build())
                .checkIn(LocalDate.of(2026, 12, 1))
                .checkOut(LocalDate.of(2026, 12, 5))
                .smartLockPin("928174")
                .build();

        Suite suite = Suite.builder().name("Suite Presidencial Getsemaní").build();
        User guest = User.builder().name("Carlos Santana")
                .profile(User.Profile.builder().phone("+573001234567").build())
                .build();

        boolean sent = whatsAppService.sendBookingConfirmation(booking, guest, suite);
        assertTrue(sent, "WhatsApp simulator debe retornar true exitosamente");
    }

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

        when(bookingRepository.findById("BOOK-DUP")).thenReturn(Optional.of(Booking.builder().id("BOOK-DUP").user("U1").build()));
        when(reviewRepository.existsByBookingId("BOOK-DUP")).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                reviewService.addVerifiedReview("BOOK-DUP", "U1", 5, "Excelente")
        );
    }
}
