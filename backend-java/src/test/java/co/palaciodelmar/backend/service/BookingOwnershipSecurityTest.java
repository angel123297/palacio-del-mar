package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests intensivos de autorización (anti-IDOR) sobre BookingService.
 * Generados por la auditoría de seguridad del 2026-10-15 (ver /AUDITORIA_SEGURIDAD.md).
 * Todos deben pasar con el código actual; los que documentan bugs van @Disabled.
 */
class BookingOwnershipSecurityTest {

    private BookingRepository bookingRepository;
    private SuiteRepository suiteRepository;
    private BranchRepository branchRepository;
    private ExperienceRepository experienceRepository;
    private SuiteNightRepository suiteNightRepository;
    private UserRepository userRepository;
    private PromotionRepository promotionRepository;
    private PricingService pricingService;
    private PaymentGateway paymentGateway;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        branchRepository = mock(BranchRepository.class);
        experienceRepository = mock(ExperienceRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        userRepository = mock(UserRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        pricingService = mock(PricingService.class);
        paymentGateway = mock(PaymentGateway.class);

        bookingService = new BookingService(
                bookingRepository, suiteRepository, branchRepository, experienceRepository,
                suiteNightRepository, userRepository, promotionRepository, pricingService, paymentGateway);

        // toDTO consulta suite y branch: stub genérico para evitar NPE
        when(suiteRepository.findById(anyString())).thenReturn(Optional.empty());
        when(branchRepository.findById(anyString())).thenReturn(Optional.empty());
    }

    private Booking victimBooking() {
        return Booking.builder()
                .id("b-victim")
                .user("user-victim")
                .suite("suite-1")
                .branch("branch-1")
                .status("pending")
                .checkIn(LocalDate.now().plus(10, ChronoUnit.DAYS))
                .checkOut(LocalDate.now().plus(12, ChronoUnit.DAYS))
                .nights(2)
                .guests(2)
                .totalPrice(java.math.BigDecimal.valueOf(1000000))
                .paidAmount(java.math.BigDecimal.ZERO)
                .balanceDue(java.math.BigDecimal.valueOf(1000000))
                .guaranteeDeposit(java.math.BigDecimal.ZERO)
                .build();
    }

    @Test
    @DisplayName("IDOR: un usuario no puede modificar la reserva de otro")
    void testModifyBookingOfAnotherUserFails() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));
        BookingRequest request = new BookingRequest();

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.modifyBooking("b-victim", "user-attacker", request));
    }

    @Test
    @DisplayName("IDOR: un usuario no puede cancelar la reserva de otro")
    void testCancelBookingOfAnotherUserFails() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.cancelBooking("b-victim", "user-attacker", "porque sí"));
    }

    @Test
    @DisplayName("IDOR: un usuario no puede agregar experiencias a la reserva de otro")
    void testAddExperienceToAnotherUserBookingFails() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.addExperienceToBooking("b-victim", "user-attacker", "exp-1"));
    }

    @Test
    @DisplayName("IDOR: un usuario no puede quitar experiencias de la reserva de otro")
    void testRemoveExperienceFromAnotherUserBookingFails() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.removeExperienceFromBooking("b-victim", "user-attacker", "exp-1"));
    }

    @Test
    @DisplayName("IDOR: un usuario no puede hacer pre-checkin de la reserva de otro")
    void testPreCheckInOfAnotherUserBookingFails() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.performPreCheckIn("b-victim", "user-attacker", "123456789", ""));
    }

    @Test
    @DisplayName("Lectura: un huésped no puede leer la reserva de otro usuario")
    void testGuestCannotReadOthersBookingById() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));
        User attacker = User.builder().id("user-attacker").email("a@a.com").role("user").build();

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.getBookingByIdSecure("b-victim", attacker));
    }

    @Test
    @DisplayName("Lectura: un ADMIN sí puede leer cualquier reserva")
    void testAdminCanReadAnyBooking() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));
        User admin = User.builder().id("admin-1").email("adm@a.com").role("admin").build();

        assertDoesNotThrow(() -> {
            co.palaciodelmar.backend.dto.BookingDTO dto = bookingService.getBookingByIdSecure("b-victim", admin);
            assertNotNull(dto);
            assertEquals("b-victim", dto.getId());
        });
    }

    @Test
    @DisplayName("Lectura: un HOST puede leer reservas de su propia sede")
    void testHostCanReadBookingOfOwnBranch() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));
        User host = User.builder().id("host-1").email("h@a.com").role("host")
                .branches(List.of("branch-1")).build();

        assertDoesNotThrow(() -> bookingService.getBookingByIdSecure("b-victim", host));
    }

    @Test
    @DisplayName("Lectura: un HOST NO puede leer reservas de otra sede")
    void testHostCannotReadBookingOfOtherBranch() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));
        User host = User.builder().id("host-1").email("h@a.com").role("host")
                .branches(List.of("branch-other")).build();

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.getBookingByIdSecure("b-victim", host));
    }

    @Test
    @DisplayName("Lectura sin sesión: reserva de invitado (sin contraseña) es consultable por código")
    void testAnonymousCanReadGuestBookingByCode() {
        Booking booking = victimBooking();
        when(bookingRepository.findByBookingCode("PDM-XYZ")).thenReturn(Optional.of(booking));
        User guest = User.builder().id("user-victim").email("g@g.com").build(); // sin password
        when(userRepository.findById("user-victim")).thenReturn(Optional.of(guest));

        assertDoesNotThrow(() -> bookingService.getBookingByCodeSecure("PDM-XYZ", null));
    }

    @Test
    @DisplayName("Lectura sin sesión: reserva de usuario registrado NO es consultable por código")
    void testAnonymousCannotReadRegisteredUserBooking() {
        Booking booking = victimBooking();
        when(bookingRepository.findByBookingCode("PDM-XYZ")).thenReturn(Optional.of(booking));
        User registered = User.builder().id("user-victim").email("g@g.com")
                .password("hash-bcrypt").build();
        when(userRepository.findById("user-victim")).thenReturn(Optional.of(registered));

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.getBookingByCodeSecure("PDM-XYZ", null));
    }

    @Test
    @Disabled("BUG-PDM-03: un HOST sin branches asignadas puede leer CUALQUIER reserva (PII + PIN). " +
              "Al corregir getBookingByIdSecure/getBookingByCodeSecure, quitar @Disabled y este test debe pasar.")
    @DisplayName("BUG-PDM-03: un HOST sin branches NO debería poder leer reservas ajenas")
    void testHostWithoutBranchesMustNotReadOthersBookings() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));
        User hostSinSedes = User.builder().id("host-1").email("h@a.com").role("host")
                .branches(new ArrayList<>()).build();

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.getBookingByIdSecure("b-victim", hostSinSedes));
    }
}
