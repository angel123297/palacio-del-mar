package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingMessageDTO;
import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.exception.ForbiddenAccessException;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.*;
import co.palaciodelmar.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class FeaturesTwoThreeFiveTest {

    // Feature 2: Review Service & Host response
    private ReviewRepository reviewRepository;
    private BookingRepository bookingRepository;
    private UserRepository userRepository;
    private SuiteRepository suiteRepository;
    private PropertyRepository propertyRepository;
    private ReviewService reviewService;

    // Feature 3: Property Service & Maintenance block/unblock
    private SuiteNightRepository suiteNightRepository;
    private PropertyService propertyService;
    private AvailabilityCacheEvictor availabilityCacheEvictor;

    // Feature 5: Booking Message Service
    private BookingMessageRepository bookingMessageRepository;
    private BookingMessageService bookingMessageService;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        bookingRepository = mock(BookingRepository.class);
        userRepository = mock(UserRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        availabilityCacheEvictor = mock(AvailabilityCacheEvictor.class);
        bookingMessageRepository = mock(BookingMessageRepository.class);

        // Feature 2 Setup
        reviewService = new ReviewService(reviewRepository, bookingRepository, userRepository);
        ReflectionTestUtils.setField(reviewService, "suiteRepository", suiteRepository);
        ReflectionTestUtils.setField(reviewService, "propertyRepository", propertyRepository);

        // Feature 3 Setup
        propertyService = new PropertyService(propertyRepository, suiteRepository);
        ReflectionTestUtils.setField(propertyService, "suiteNightRepository", suiteNightRepository);
        ReflectionTestUtils.setField(propertyService, "availabilityCacheEvictor", availabilityCacheEvictor);

        // Feature 5 Setup
        bookingMessageService = new BookingMessageService(
                bookingMessageRepository, bookingRepository, propertyRepository, suiteRepository);
    }

    // ==========================================
    // FEATURE 2: RESPUESTA PÚBLICA DEL ANFITRIÓN
    // ==========================================

    @Test
    @DisplayName("F2: Anfitrión responde a reseña pública de su propiedad exitosamente")
    void testHostRespondToReviewSuccess() {
        Review review = Review.builder()
                .id("rev-1")
                .bookingId("b-1")
                .suite("suite-1")
                .user("guest-1")
                .comment("Excelente estancia")
                .rating(5)
                .build();
        Property prop = Property.builder()
                .id("prop-1")
                .ownerId("host-1")
                .build();
        Suite suite = Suite.builder()
                .id("suite-1")
                .propertyId("prop-1")
                .build();

        when(reviewRepository.findById("rev-1")).thenReturn(Optional.of(review));
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(prop));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewDTO dto = reviewService.respondToReview("rev-1", "host-1", "¡Muchas gracias por visitarnos!");

        assertNotNull(dto);
        assertEquals("¡Muchas gracias por visitarnos!", dto.getHostResponse());
        assertNotNull(dto.getHostResponseAt());
        assertEquals("host-1", dto.getHostResponseBy());
        verify(reviewRepository).save(review);
    }

    @Test
    @DisplayName("F2: Host ajeno es rechazado con 403 al intentar responder reseña de otra propiedad")
    void testHostRespondToReviewDeniedForOtherProperty() {
        Review review = Review.builder().id("rev-2").suite("suite-2").user("guest-2").build();
        Suite suite = Suite.builder().id("suite-2").propertyId("prop-2").build();
        Property prop = Property.builder().id("prop-2").ownerId("host-owner").build();

        when(reviewRepository.findById("rev-2")).thenReturn(Optional.of(review));
        when(suiteRepository.findById("suite-2")).thenReturn(Optional.of(suite));
        when(propertyRepository.findById("prop-2")).thenReturn(Optional.of(prop));

        assertThrows(AccessDeniedException.class, () ->
                reviewService.respondToReview("rev-2", "host-impostor", "Respuesta fraudulenta")
        );
    }

    // ==========================================
    // FEATURE 3: BLOQUEO MANUAL POR MANTENIMIENTO
    // ==========================================

    @Test
    @DisplayName("F3: Anfitrión bloquea noches por mantenimiento con éxito y se invalidan cachés")
    void testBlockSuiteNightsForMaintenanceSuccess() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        Property prop = Property.builder().id("prop-1").ownerId("host-1").build();
        Suite suite = Suite.builder().id("suite-1").propertyId("prop-1").totalUnits(1).build();

        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(prop));
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-1"), eq(start), eq(end.minusDays(1))))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = propertyService.blockSuiteNightsForMaintenance(
                "prop-1", "suite-1", "host-1", start, end, "Mantenimiento aire acondicionado", false);

        assertNotNull(result);
        assertEquals(3, result.get("blockedNights"));
        assertTrue(result.get("blockRef").toString().startsWith("MAINT_"));
        verify(suiteNightRepository).saveAll(anyList());
        verify(availabilityCacheEvictor).evictAll();
    }

    @Test
    @DisplayName("F3: Bloqueo rechazado si existen reservas activas de huéspedes en ese rango")
    void testBlockSuiteNightsFailsWhenActiveBookingsExist() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        Property prop = Property.builder().id("prop-1").ownerId("host-1").build();
        Suite suite = Suite.builder().id("suite-1").propertyId("prop-1").totalUnits(1).build();

        SuiteNight activeGuestNight = SuiteNight.builder()
                .suite("suite-1")
                .dates(List.of(start))
                .booking("booking-active-123")
                .status("confirmed")
                .expiresAt(null)
                .build();

        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(prop));
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-1"), eq(start), eq(end.minusDays(1))))
                .thenReturn(List.of(activeGuestNight));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                propertyService.blockSuiteNightsForMaintenance(
                        "prop-1", "suite-1", "host-1", start, end, "Pintura", false)
        );
        assertTrue(ex.getMessage().contains("existen reservas activas"));
        verify(suiteNightRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("F3: Desbloqueo manual elimina noches de mantenimiento y purga caché")
    void testUnblockSuiteNightsSuccess() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        Property prop = Property.builder().id("prop-1").ownerId("host-1").build();
        Suite suite = Suite.builder().id("suite-1").propertyId("prop-1").build();

        SuiteNight maintNight = SuiteNight.builder()
                .id("sn-maint-1")
                .suite("suite-1")
                .dates(List.of(start))
                .status("maintenance")
                .booking("MAINT_123")
                .build();

        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(prop));
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-1"), eq(start), eq(end.minusDays(1))))
                .thenReturn(List.of(maintNight));

        Map<String, Object> result = propertyService.unblockSuiteNights(
                "prop-1", "suite-1", "host-1", start, end, false);

        assertNotNull(result);
        assertEquals(1, result.get("unblockedNights"));
        verify(suiteNightRepository).deleteAll(List.of(maintNight));
        verify(availabilityCacheEvictor).evictAll();
    }

    // ==========================================
    // FEATURE 5: MENSAJERÍA DIRECTA HUÉSPED ↔ HOST
    // ==========================================

    @Test
    @DisplayName("F5: Huésped envía mensaje al anfitrión de su reserva")
    void testGuestSendsMessageSuccess() {
        Booking booking = Booking.builder()
                .id("book-1")
                .user("guest-user-1")
                .propertyId("prop-1")
                .build();

        when(bookingRepository.findById("book-1")).thenReturn(Optional.of(booking));
        when(bookingMessageRepository.save(any(BookingMessage.class))).thenAnswer(invocation -> {
            BookingMessage msg = invocation.getArgument(0);
            msg.setId("msg-1");
            return msg;
        });

        BookingMessageDTO dto = bookingMessageService.sendMessage(
                "book-1", "guest-user-1", "Carlos Pérez", "¿A qué hora puedo hacer el check-in?", false);

        assertNotNull(dto);
        assertEquals("GUEST", dto.getSenderRole());
        assertEquals("Carlos Pérez", dto.getSenderName());
        assertEquals("¿A qué hora puedo hacer el check-in?", dto.getMessage());
        assertTrue(dto.isMe());
    }

    @Test
    @DisplayName("F5: Anfitrión responde al huésped y se asigna rol HOST")
    void testHostSendsMessageSuccess() {
        Booking booking = Booking.builder()
                .id("book-1")
                .user("guest-user-1")
                .propertyId("prop-1")
                .build();
        Property prop = Property.builder().id("prop-1").ownerId("host-user-1").build();

        when(bookingRepository.findById("book-1")).thenReturn(Optional.of(booking));
        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(prop));
        when(bookingMessageRepository.save(any(BookingMessage.class))).thenAnswer(invocation -> {
            BookingMessage msg = invocation.getArgument(0);
            msg.setId("msg-2");
            return msg;
        });

        BookingMessageDTO dto = bookingMessageService.sendMessage(
                "book-1", "host-user-1", "Anfitrión Pedro", "El check-in es a partir de las 3:00 PM.", false);

        assertNotNull(dto);
        assertEquals("HOST", dto.getSenderRole());
        assertEquals("Anfitrión Pedro", dto.getSenderName());
        assertTrue(dto.isMe());
    }

    @Test
    @DisplayName("F5: Usuario no autorizado es rechazado con AccessDeniedException al enviar o ver mensajes")
    void testUnauthorizedUserDeniedFromMessaging() {
        Booking booking = Booking.builder()
                .id("book-1")
                .user("guest-user-1")
                .propertyId("prop-1")
                .build();
        Property prop = Property.builder().id("prop-1").ownerId("host-user-1").build();

        when(bookingRepository.findById("book-1")).thenReturn(Optional.of(booking));
        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(prop));

        assertThrows(AccessDeniedException.class, () ->
                bookingMessageService.sendMessage("book-1", "intruder-user", "Intruder", "Hola", false)
        );

        assertThrows(AccessDeniedException.class, () ->
                bookingMessageService.getMessages("book-1", "intruder-user", false)
        );
    }

    @Test
    @DisplayName("F5: Al consultar mensajes se marca readAt para mensajes recibidos de la otra parte")
    void testMessagesMarkedAsReadUponFetch() {
        Booking booking = Booking.builder()
                .id("book-1")
                .user("guest-user-1")
                .propertyId("prop-1")
                .build();

        BookingMessage incomingMsg = BookingMessage.builder()
                .id("msg-in-1")
                .bookingId("book-1")
                .senderId("host-user-1")
                .senderName("Host Pedro")
                .senderRole("HOST")
                .message("Bienvenido")
                .readAt(null)
                .build();

        when(bookingRepository.findById("book-1")).thenReturn(Optional.of(booking));
        when(bookingMessageRepository.findByBookingIdOrderByCreatedAtAsc(eq("book-1"), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(incomingMsg)));

        List<BookingMessageDTO> result = bookingMessageService.getMessages("book-1", "guest-user-1", false);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(incomingMsg.getReadAt());
        verify(bookingMessageRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Bug 1: Bloqueo de mantenimiento no colisiona con bloqueos iCal existentes (EXT_BLOCK_*)")
    void testMaintenanceBlockSkipsExistingIcalBlocksWithoutDuplicateViolation() {
        Property property = Property.builder()
                .id("prop-1")
                .ownerId("host-1")
                .status("APPROVED")
                .build();
        Suite suite = Suite.builder()
                .id("suite-1")
                .propertyId("prop-1")
                .totalUnits(1)
                .build();

        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = start.plusDays(3);

        SuiteNight externalBlock = SuiteNight.builder()
                .suite("suite-1")
                .unitSlot(1)
                .dates(List.of(start))
                .booking("EXT_BLOCK_ab12cd34")
                .status("blocked")
                .build();

        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(property));
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-1"), any(), any()))
                .thenReturn(List.of(externalBlock));

        when(suiteNightRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = propertyService.blockSuiteNightsForMaintenance(
                "prop-1", "suite-1", "host-1", start, end, "Pintura y acabados", false
        );

        assertNotNull(result);
        assertEquals("Noches bloqueadas por mantenimiento exitosamente", result.get("message"));
        // El día que ya tenía EXT_BLOCK no fue reinsertado (se bloquearon los 2 días restantes de 3)
        assertEquals(2, result.get("blockedNights"));
        verify(availabilityCacheEvictor).evictAll();
    }

    @Test
    @DisplayName("Bug 3: GlobalExceptionHandler retorna 405 Method Not Allowed ante HttpRequestMethodNotSupportedException")
    void testMethodNotSupportedExceptionReturns405() {
        co.palaciodelmar.backend.exception.GlobalExceptionHandler handler = new co.palaciodelmar.backend.exception.GlobalExceptionHandler();
        org.springframework.web.HttpRequestMethodNotSupportedException ex =
                new org.springframework.web.HttpRequestMethodNotSupportedException("POST");

        var response = handler.handleMethodNotSupported(ex);
        assertEquals(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("POST"));
    }
}
