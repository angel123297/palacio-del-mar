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
    private co.palaciodelmar.backend.repository.SuiteRepository suiteRepository;
    private co.palaciodelmar.backend.repository.PropertyRepository propertyRepository;
    private WebhookDispatcher webhookDispatcher;
    private EmailService emailService;
    private ReviewService reviewService;
    private ReviewInvitationScheduler invitationScheduler;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        bookingRepository = mock(BookingRepository.class);
        userRepository = mock(UserRepository.class);
        suiteRepository = mock(co.palaciodelmar.backend.repository.SuiteRepository.class);
        propertyRepository = mock(co.palaciodelmar.backend.repository.PropertyRepository.class);
        webhookDispatcher = mock(WebhookDispatcher.class);
        emailService = mock(EmailService.class);

        reviewService = new ReviewService(reviewRepository, bookingRepository, userRepository);
        ReflectionTestUtils.setField(reviewService, "suiteRepository", suiteRepository);
        ReflectionTestUtils.setField(reviewService, "propertyRepository", propertyRepository);
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
    @DisplayName("Gestión de mantenimiento: El anfitrión dueño puede marcar una alerta de mantenimiento como resuelta")
    void testResolveMaintenanceAlert() {
        co.palaciodelmar.backend.model.Suite suite = co.palaciodelmar.backend.model.Suite.builder().id("suite-1").propertyId("prop-1").build();
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));

        co.palaciodelmar.backend.model.Property property = co.palaciodelmar.backend.model.Property.builder().id("prop-1").ownerId("host-owner-1").build();
        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(property));

        Review review = Review.builder()
                .id("rev-1")
                .suite("suite-1")
                .maintenanceAlert("Revisión preventiva: Posible fallo o ruido en aire acondicionado")
                .build();
        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDTO resolved = reviewService.resolveMaintenanceAlert("rev-1", "host-owner-1");

        assertNotNull(resolved.getMaintenanceResolvedAt());
        assertEquals("host-owner-1", resolved.getMaintenanceResolvedBy());
    }

    @Test
    @DisplayName("Anti-IDOR: Host A no puede resolver alertas de la propiedad del Host B")
    void testHostCannotResolveMaintenanceAlertOfOtherHostProperty() {
        co.palaciodelmar.backend.model.Suite suite = co.palaciodelmar.backend.model.Suite.builder().id("suite-b").propertyId("prop-b").build();
        when(suiteRepository.findById("suite-b")).thenReturn(Optional.of(suite));

        co.palaciodelmar.backend.model.Property propertyB = co.palaciodelmar.backend.model.Property.builder().id("prop-b").ownerId("host-b").build();
        when(propertyRepository.findById("prop-b")).thenReturn(Optional.of(propertyB));

        Review review = Review.builder()
                .id("rev-b")
                .suite("suite-b")
                .maintenanceAlert("Revisión preventiva: Presión o temperatura de agua")
                .build();
        when(reviewRepository.findById("rev-b")).thenReturn(Optional.of(review));

        // Host A intenta resolver la alerta de la propiedad de Host B
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                reviewService.resolveMaintenanceAlert("rev-b", "host-a")
        );
    }

    @Test
    @DisplayName("Gestión de mantenimiento: No se puede resolver si la reseña no tiene alerta activa")
    void testCannotResolveReviewWithoutMaintenanceAlert() {
        Review reviewWithoutAlert = Review.builder()
                .id("rev-clean")
                .suite("suite-1")
                .maintenanceAlert(null)
                .build();
        when(reviewRepository.findById("rev-clean")).thenReturn(Optional.of(reviewWithoutAlert));

        assertThrows(IllegalStateException.class, () ->
                reviewService.resolveMaintenanceAlert("rev-clean", "host-owner-1")
        );
    }

    @Test
    @DisplayName("Invitación automática a reseña: El scheduler envía correo al huésped para reservas finalizadas")
    void testReviewInvitationScheduler() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Bogota"));

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

    @Test
    @DisplayName("Anti-Fraude: Anfitrión no puede calificar su propia propiedad")
    void testHostCannotSelfReviewOwnSuite() {
        co.palaciodelmar.backend.model.Suite suite = co.palaciodelmar.backend.model.Suite.builder().id("suite-own").propertyId("prop-own").build();
        when(suiteRepository.findById("suite-own")).thenReturn(Optional.of(suite));

        co.palaciodelmar.backend.model.Property property = co.palaciodelmar.backend.model.Property.builder().id("prop-own").ownerId("host-1").build();
        when(propertyRepository.findById("prop-own")).thenReturn(Optional.of(property));

        assertThrows(IllegalArgumentException.class, () ->
                reviewService.addReview("suite-own", "host-1", "Host 1", 5, "Mi propia suite es fantástica")
        );
    }

    @Test
    @DisplayName("Consulta de reseña por reserva: huésped y host de la propiedad tienen acceso autorizado")
    void testGetReviewByBookingId() {
        Booking booking = Booking.builder()
                .id("bk-rev-1")
                .user("guest-1")
                .propertyId("prop-1")
                .build();
        when(bookingRepository.findById("bk-rev-1")).thenReturn(Optional.of(booking));

        Review review = Review.builder()
                .id("rev-10")
                .bookingId("bk-rev-1")
                .rating(5)
                .comment("Inmejorable")
                .build();
        when(reviewRepository.findByBookingId("bk-rev-1")).thenReturn(Optional.of(review));

        co.palaciodelmar.backend.model.Property property = co.palaciodelmar.backend.model.Property.builder()
                .id("prop-1")
                .ownerId("host-1")
                .build();
        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(property));

        // Huésped legítimo
        co.palaciodelmar.backend.model.User guestUser = co.palaciodelmar.backend.model.User.builder().id("guest-1").role("USER").build();
        ReviewDTO guestDto = reviewService.getReviewByBookingId("bk-rev-1", guestUser);
        assertNotNull(guestDto);
        assertEquals("Inmejorable", guestDto.getComment());

        // Host legítimo
        co.palaciodelmar.backend.model.User hostUser = co.palaciodelmar.backend.model.User.builder().id("host-1").role("HOST").build();
        ReviewDTO hostDto = reviewService.getReviewByBookingId("bk-rev-1", hostUser);
        assertNotNull(hostDto);

        // Host ajeno (IDOR)
        co.palaciodelmar.backend.model.User otherHost = co.palaciodelmar.backend.model.User.builder().id("host-attacker").role("HOST").build();
        ReviewDTO otherDto = reviewService.getReviewByBookingId("bk-rev-1", otherHost);
        assertNull(otherDto);
    }
}
