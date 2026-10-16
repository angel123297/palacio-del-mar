package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Review;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.ReviewRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.scheduler.ReviewInvitationScheduler;
import co.palaciodelmar.backend.webhook.WebhookDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReviewAndMaintenanceAutomationTest {

    private ReviewRepository reviewRepository;
    private BookingRepository bookingRepository;
    private UserRepository userRepository;
    private WebhookDispatcher webhookDispatcher;
    private EmailService emailService;
    private ReviewService reviewService;
    private ReviewInvitationScheduler invitationScheduler;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        bookingRepository = mock(BookingRepository.class);
        userRepository = mock(UserRepository.class);
        webhookDispatcher = mock(WebhookDispatcher.class);
        emailService = mock(EmailService.class);

        reviewService = new ReviewService(reviewRepository, bookingRepository, userRepository);
        ReflectionTestUtils.setField(reviewService, "webhookDispatcher", webhookDispatcher);

        invitationScheduler = new ReviewInvitationScheduler(bookingRepository, emailService);
    }

    @Test
    @DisplayName("Alertas automáticas de mantenimiento: Si la reseña detecta fallas críticas emite webhook maintenance.alert")
    void testMaintenanceAlertWebhookDispatchedOnCriticalReview() {
        Booking booking = Booking.builder()
                .id("bk-maint-1")
                .suite("suite-101")
                .user("user-guest")
                .propertyId("prop-caribe")
                .status("checked_out")
                .build();
        when(bookingRepository.findById("bk-maint-1")).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId("bk-maint-1")).thenReturn(false);

        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId("rev-1");
            return r;
        });

        // Comentario con problema de aire acondicionado y ruido
        ReviewDTO dto = reviewService.addVerifiedReview(
                "bk-maint-1",
                "user-guest",
                2,
                "El aire acondicionado hacía mucho ruido y no enfriaba bien en la noche."
        );

        assertNotNull(dto.getMaintenanceAlert());
        assertTrue(dto.getMaintenanceAlert().contains("aire acondicionado"));
        assertEquals("NEGATIVE", dto.getSentiment());

        // Debe haber despachado webhook al anfitrión de prop-caribe
        verify(webhookDispatcher, times(1)).dispatchToProperty(
                eq("prop-caribe"),
                eq("maintenance.alert"),
                any()
        );
    }

    @Test
    @DisplayName("Gestión de mantenimiento: El anfitrión puede marcar una alerta de mantenimiento como resuelta")
    void testResolveMaintenanceAlert() {
        Review review = Review.builder()
                .id("rev-1")
                .maintenanceAlert("Revisión preventiva: Posible fallo o ruido en aire acondicionado")
                .build();
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDTO resolved = reviewService.resolveMaintenanceAlert("rev-1", "host-owner-1");

        assertNotNull(resolved.getMaintenanceResolvedAt());
        assertEquals("host-owner-1", resolved.getMaintenanceResolvedBy());
    }

    @Test
    @DisplayName("Invitación automática a reseña: El scheduler envía correo al huésped para reservas finalizadas")
    void testReviewInvitationScheduler() {
        LocalDate today = LocalDate.now();

        Booking booking = Booking.builder()
                .id("bk-finished-1")
                .status("checked_out")
                .checkOut(today.minusDays(1))
                .user("u-guest")
                .build();

        when(bookingRepository.findByStatusIn(anyList())).thenReturn(List.of(booking));

        invitationScheduler.sendAutomatedReviewInvitations();

        verify(emailService, times(1)).sendReviewInvitation(eq(booking));
        assertNotNull(booking.getReviewRequestSentAt());
        verify(bookingRepository, times(1)).save(booking);
    }
}
