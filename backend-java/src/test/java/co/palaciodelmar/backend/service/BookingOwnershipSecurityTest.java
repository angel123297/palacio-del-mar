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
    @DisplayName("Lectura sin sesión: reserva de invitado (sin contraseña) es consultable por código pero oculta el smartLockPin")
    void testAnonymousCanReadGuestBookingByCode() {
        Booking booking = victimBooking();
        booking.setSmartLockPin("123456");
        when(bookingRepository.findByBookingCode("PDM-XYZ")).thenReturn(Optional.of(booking));
        User guest = User.builder().id("user-victim").email("g@g.com").build(); // sin password
        when(userRepository.findById("user-victim")).thenReturn(Optional.of(guest));

        co.palaciodelmar.backend.dto.BookingDTO dto = assertDoesNotThrow(() -> bookingService.getBookingByCodeSecure("PDM-XYZ", null));
        org.junit.jupiter.api.Assertions.assertNull(dto.getSmartLockPin(), "El PIN de la cerradura no debe exponerse en consultas sin sesión");
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
    @DisplayName("BUG-PDM-03: un HOST sin branches NO debería poder leer reservas ajenas")
    void testHostWithoutBranchesMustNotReadOthersBookings() {
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(victimBooking()));
        User hostSinSedes = User.builder().id("host-1").email("h@a.com").role("host")
                .branches(new ArrayList<>()).build();

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.getBookingByIdSecure("b-victim", hostSinSedes));
    }

    @Test
    @DisplayName("Concurrencia Multi-Slot: si el slot 1 falla por DuplicateKeyException, asigna el slot 2")
    void testCreateBookingRetriesNextAvailableSlotOnCollision() {
        co.palaciodelmar.backend.model.Suite multiUnitSuite = co.palaciodelmar.backend.model.Suite.builder()
                .id("suite-multi")
                .branch("branch-1")
                .propertyId("prop-1")
                .basePrice(java.math.BigDecimal.valueOf(500000))
                .maxGuests(4)
                .totalUnits(2)
                .active(true)
                .available(true)
                .build();

        when(suiteRepository.findById("suite-multi")).thenReturn(Optional.of(multiUnitSuite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(promotionRepository.findByBranchAndActiveTrue("branch-1")).thenReturn(List.of());
        when(pricingService.calculatePrice(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PricingService.PricingResult(
                        java.math.BigDecimal.valueOf(500000),
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.valueOf(95000),
                        java.math.BigDecimal.valueOf(595000),
                        List.of()
                ));
        when(bookingRepository.save(org.mockito.ArgumentMatchers.any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) b.setId("booking-retry-1");
            return b;
        });

        // Simular que otro hilo ocupó el slot 1 justo antes del insert, pero el slot 2 está libre
        when(suiteNightRepository.save(org.mockito.ArgumentMatchers.any(co.palaciodelmar.backend.model.SuiteNight.class)))
                .thenAnswer(inv -> {
                    co.palaciodelmar.backend.model.SuiteNight sn = inv.getArgument(0);
                    if (Integer.valueOf(1).equals(sn.getUnitSlot())) {
                        throw new org.springframework.dao.DuplicateKeyException("Slot 1 ocupado concurrentemente");
                    }
                    return sn;
                });

        BookingRequest req = BookingRequest.builder()
                .suiteId("suite-multi")
                .checkIn(LocalDate.now().plusDays(5))
                .checkOut(LocalDate.now().plusDays(6))
                .guests(2)
                .build();

        co.palaciodelmar.backend.dto.BookingDTO result = assertDoesNotThrow(() -> bookingService.createBooking("user-1", req));
        assertNotNull(result);
        assertEquals(2, result.getUnitSlot(), "Debe asignar automáticamente el slot 2 tras la colisión en el slot 1");
    }

    @Test
    @DisplayName("modifyBooking rechaza mover la fecha de check-in al pasado")
    void testModifyBookingRejectsPastCheckInDate() {
        Booking existing = victimBooking();
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(existing));
        co.palaciodelmar.backend.model.Suite suite = co.palaciodelmar.backend.model.Suite.builder()
                .id("suite-1")
                .maxGuests(4)
                .build();
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));

        BookingRequest pastReq = BookingRequest.builder()
                .suiteId("suite-1")
                .checkIn(LocalDate.now().minusDays(2))
                .checkOut(LocalDate.now().plusDays(1))
                .guests(2)
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.modifyBooking("b-victim", "user-victim", pastReq));
    }

    @Test
    @DisplayName("modifyBooking en reserva confirmada reintenta en slot 2 ante colisión y conserva status confirmed en SuiteNight")
    void testModifyBookingRetriesNextAvailableSlotAndKeepsConfirmedStatus() {
        Booking existing = victimBooking();
        existing.setStatus("confirmed");
        existing.setPaidAmount(null); // Verifica además protección contra paidAmount nulo
        when(bookingRepository.findById("b-victim")).thenReturn(Optional.of(existing));

        co.palaciodelmar.backend.model.Suite suite = co.palaciodelmar.backend.model.Suite.builder()
                .id("suite-1")
                .branch("branch-1")
                .basePrice(java.math.BigDecimal.valueOf(500000))
                .maxGuests(4)
                .totalUnits(2)
                .active(true)
                .available(true)
                .build();
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ArrayList<>());
        when(promotionRepository.findByBranchAndActiveTrue("branch-1")).thenReturn(List.of());
        when(pricingService.calculatePrice(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PricingService.PricingResult(
                        java.math.BigDecimal.valueOf(500000),
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.valueOf(95000),
                        java.math.BigDecimal.valueOf(595000),
                        List.of()
                ));
        when(bookingRepository.save(org.mockito.ArgumentMatchers.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        List<co.palaciodelmar.backend.model.SuiteNight> savedNights = new ArrayList<>();
        when(suiteNightRepository.save(org.mockito.ArgumentMatchers.any(co.palaciodelmar.backend.model.SuiteNight.class)))
                .thenAnswer(inv -> {
                    co.palaciodelmar.backend.model.SuiteNight sn = inv.getArgument(0);
                    if (Integer.valueOf(1).equals(sn.getUnitSlot())) {
                        throw new org.springframework.dao.DuplicateKeyException("Slot 1 ocupado");
                    }
                    savedNights.add(sn);
                    return sn;
                });

        BookingRequest modReq = BookingRequest.builder()
                .suiteId("suite-1")
                .checkIn(LocalDate.now().plusDays(4))
                .checkOut(LocalDate.now().plusDays(5))
                .guests(2)
                .build();

        co.palaciodelmar.backend.dto.BookingDTO dto = assertDoesNotThrow(() ->
                bookingService.modifyBooking("b-victim", "user-victim", modReq));
        assertEquals(2, dto.getUnitSlot());
        assertEquals(1, savedNights.size());
        assertEquals("confirmed", savedNights.get(0).getStatus());
    }
}
