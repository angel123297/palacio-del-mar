package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketplaceCatalogSecurityTest {

    private SuiteRepository suiteRepository;
    private BranchRepository branchRepository;
    private PropertyRepository propertyRepository;
    private ExperienceRepository experienceRepository;
    private PromotionRepository promotionRepository;
    private PricingService pricingService;
    private SuiteService suiteService;

    private BookingRepository bookingRepository;
    private SuiteNightRepository suiteNightRepository;
    private UserRepository userRepository;
    private PaymentGateway paymentGateway;
    private BookingService bookingService;
    private AvailabilityService availabilityService;
    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        suiteRepository = mock(SuiteRepository.class);
        branchRepository = mock(BranchRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        experienceRepository = mock(ExperienceRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        pricingService = mock(PricingService.class);

        suiteService = new SuiteService(
                suiteRepository,
                branchRepository,
                propertyRepository,
                experienceRepository,
                promotionRepository,
                pricingService
        );

        bookingRepository = mock(BookingRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        userRepository = mock(UserRepository.class);
        paymentGateway = mock(PaymentGateway.class);

        bookingService = new BookingService(
                bookingRepository,
                suiteRepository,
                branchRepository,
                experienceRepository,
                suiteNightRepository,
                userRepository,
                promotionRepository,
                pricingService,
                paymentGateway
        );
        ReflectionTestUtils.setField(bookingService, "propertyRepository", propertyRepository);

        availabilityService = new AvailabilityService(
                suiteRepository,
                suiteNightRepository,
                bookingRepository,
                promotionRepository,
                branchRepository,
                propertyRepository,
                pricingService
        );

        propertyService = new PropertyService(propertyRepository, suiteRepository);
    }

    @Test
    @DisplayName("getAllActiveSuites solo incluye suites de sedes oficiales o propiedades marketplace aprobadas y activas")
    void getAllActiveSuitesFiltersUnapprovedProperties() {
        Property approvedProp = Property.builder()
                .id("prop-approved")
                .name("Villa Sol")
                .slug("villa-sol")
                .status("APPROVED")
                .active(true)
                .build();

        Property pendingProp = Property.builder()
                .id("prop-pending")
                .name("Cabaña Luna")
                .slug("cabana-luna")
                .status("PENDING")
                .active(true)
                .build();

        Property rejectedProp = Property.builder()
                .id("prop-rejected")
                .name("Hostal Mar")
                .slug("hostal-mar")
                .status("REJECTED")
                .active(true)
                .build();

        Property inactiveProp = Property.builder()
                .id("prop-inactive")
                .name("Finca Cielo")
                .slug("finca-cielo")
                .status("APPROVED")
                .active(false)
                .build();

        Suite legacySuite = Suite.builder().id("s-legacy").name("Suite Centro").branch("branch-1").active(true).build();
        Suite approvedSuite = Suite.builder().id("s-approved").name("Suite Villa").propertyId("prop-approved").active(true).build();
        Suite pendingSuite = Suite.builder().id("s-pending").name("Suite Luna").propertyId("prop-pending").active(true).build();
        Suite rejectedSuite = Suite.builder().id("s-rejected").name("Suite Mar").propertyId("prop-rejected").active(true).build();
        Suite inactiveSuite = Suite.builder().id("s-inactive").name("Suite Cielo").propertyId("prop-inactive").active(true).build();

        when(branchRepository.findAll()).thenReturn(List.of(
                Branch.builder().id("branch-1").name("Centro").build()
        ));
        when(propertyRepository.findAll()).thenReturn(List.of(approvedProp, pendingProp, rejectedProp, inactiveProp));
        when(suiteRepository.findAll()).thenReturn(List.of(legacySuite, approvedSuite, pendingSuite, rejectedSuite, inactiveSuite));

        List<SuiteResponseDTO> result = suiteService.getAllActiveSuites(null);

        assertEquals(2, result.size(), "Solo deben estar la suite de sede oficial y la suite de la propiedad aprobada");
        assertTrue(result.stream().anyMatch(s -> "s-legacy".equals(s.getId())));
        assertTrue(result.stream().anyMatch(s -> "s-approved".equals(s.getId())));
        assertFalse(result.stream().anyMatch(s -> "s-pending".equals(s.getId())));
        assertFalse(result.stream().anyMatch(s -> "s-rejected".equals(s.getId())));
        assertFalse(result.stream().anyMatch(s -> "s-inactive".equals(s.getId())));
    }

    @Test
    @DisplayName("getSuiteByIdOrSlug rechaza suites de propiedades no aprobadas")
    void getSuiteByIdOrSlugRejectsPendingProperty() {
        Suite pendingSuite = Suite.builder().id("s-pending-1").name("Suite Luna").propertyId("prop-pending").active(true).build();
        Property pendingProp = Property.builder().id("prop-pending").status("PENDING").active(true).build();

        when(suiteRepository.findById("s-pending-1")).thenReturn(Optional.of(pendingSuite));
        when(propertyRepository.findById("prop-pending")).thenReturn(Optional.of(pendingProp));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                suiteService.getSuiteByIdOrSlug("s-pending-1")
        );
        assertTrue(ex.getMessage().contains("no está aprobada"));
    }

    @Test
    @DisplayName("calculatePrice rechaza cálculo para suites de propiedades pendientes o rechazadas")
    void calculatePriceRejectsUnapprovedProperty() {
        Suite pendingSuite = Suite.builder().id("s-pending-2").name("Suite Luna").basePrice(new BigDecimal("200000")).propertyId("prop-pending").active(true).build();
        Property pendingProp = Property.builder().id("prop-pending").status("PENDING").active(true).build();

        when(suiteRepository.findById("s-pending-2")).thenReturn(Optional.of(pendingSuite));
        when(propertyRepository.findById("prop-pending")).thenReturn(Optional.of(pendingProp));

        CalculatePriceRequest req = CalculatePriceRequest.builder()
                .suiteId("s-pending-2")
                .checkIn(LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1))
                .checkOut(LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3))
                .build();

        assertThrows(IllegalArgumentException.class, () -> suiteService.calculatePrice(req));
    }

    @Test
    @DisplayName("createBooking rechaza reservas en habitaciones de propiedades en estado PENDING o REJECTED")
    void createBookingRejectsUnapprovedProperties() {
        Suite pendingSuite = Suite.builder()
                .id("s-pend-book")
                .name("Suite Sin Aprobar")
                .propertyId("prop-pend-id")
                .active(true)
                .available(true)
                .maxGuests(2)
                .build();

        Property pendingProp = Property.builder()
                .id("prop-pend-id")
                .name("Hotel Nuevo")
                .status("PENDING")
                .active(true)
                .build();

        when(suiteRepository.findById("s-pend-book")).thenReturn(Optional.of(pendingSuite));
        when(propertyRepository.findById("prop-pend-id")).thenReturn(Optional.of(pendingProp));

        LocalDate checkIn = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2);
        LocalDate checkOut = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(5);

        BookingRequest req = BookingRequest.builder()
                .suiteId("s-pend-book")
                .checkIn(checkIn)
                .checkOut(checkOut)
                .guests(2)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                bookingService.createBooking("user-guest-1", req)
        );
        assertTrue(ex.getMessage().contains("no está aprobada"));
    }

    @Test
    @DisplayName("createBooking rechaza que un anfitrión reserve su propia propiedad")
    void createBookingRejectsHostSelfBooking() {
        Suite suite = Suite.builder()
                .id("s-own-book")
                .propertyId("prop-host-1")
                .active(true)
                .available(true)
                .basePrice(new BigDecimal("300000"))
                .build();

        Property property = Property.builder()
                .id("prop-host-1")
                .ownerId("host-owner-id")
                .status("APPROVED")
                .active(true)
                .build();

        when(suiteRepository.findById("s-own-book")).thenReturn(Optional.of(suite));
        when(propertyRepository.findById("prop-host-1")).thenReturn(Optional.of(property));

        LocalDate checkIn = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(2);
        LocalDate checkOut = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(5);

        BookingRequest req = BookingRequest.builder()
                .suiteId("s-own-book")
                .checkIn(checkIn)
                .checkOut(checkOut)
                .guests(2)
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                bookingService.createBooking("host-owner-id", req)
        );
        assertTrue(ex.getMessage().contains("Un anfitrión no puede reservar su propia propiedad"));
    }

    @Test
    @DisplayName("AvailabilityService.checkAvailability excluye habitaciones de propiedades pendientes o rechazadas")
    void checkAvailabilityExcludesSuitesOfUnapprovedProperties() {
        LocalDate checkIn = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1);
        LocalDate checkOut = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(3);

        Property approvedProp = Property.builder().id("p-appr").status("APPROVED").active(true).build();
        Property pendingProp = Property.builder().id("p-pend").status("PENDING").active(true).build();

        Suite approvedSuite = Suite.builder().id("s-appr").name("Suite Aprobada").propertyId("p-appr").active(true).basePrice(new BigDecimal("200000")).build();
        Suite pendingSuite = Suite.builder().id("s-pend").name("Suite Pendiente").propertyId("p-pend").active(true).basePrice(new BigDecimal("150000")).build();

        when(propertyRepository.findAll()).thenReturn(List.of(approvedProp, pendingProp));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of(approvedSuite, pendingSuite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(List.of());
        when(pricingService.calculatePrice(any(), any(), any(), any()))
                .thenReturn(new PricingService.PricingResult(new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400000"), List.of()));

        AvailabilityService.AvailabilityResponseData data = availabilityService.checkAvailability(checkIn, checkOut, 1, null, null);

        assertEquals(1, data.getAvailableSuites().size(), "Solo la suite de la propiedad aprobada debe estar disponible");
        assertEquals("s-appr", data.getAvailableSuites().get(0).getSuite().getId());
    }

    @Test
    @DisplayName("AvailabilityService.getMonthlyAvailability excluye habitaciones de propiedades pendientes o rechazadas")
    void getMonthlyAvailabilityExcludesSuitesOfUnapprovedProperties() {
        Property approvedProp = Property.builder().id("p-appr").status("APPROVED").active(true).build();
        Property pendingProp = Property.builder().id("p-pend").status("PENDING").active(true).build();

        Suite approvedSuite = Suite.builder().id("s-appr").name("Suite Aprobada").propertyId("p-appr").active(true).basePrice(new BigDecimal("150000")).build();
        Suite pendingSuite = Suite.builder().id("s-pend").name("Suite Pendiente").propertyId("p-pend").active(true).basePrice(new BigDecimal("150000")).build();

        when(propertyRepository.findAll()).thenReturn(List.of(approvedProp, pendingProp));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of(approvedSuite, pendingSuite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(List.of());
        when(pricingService.calculatePrice(any(), any(), any(), any()))
                .thenReturn(new PricingService.PricingResult(new BigDecimal("150000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("150000"),
                        List.of(new PricingService.NightlyPrice(LocalDate.of(2026, 11, 1), new BigDecimal("150000")))));

        AvailabilityService.MonthlyAvailabilityResponseData monthly = availabilityService.getMonthlyAvailability(2026, 11, 1, null, null);

        assertEquals(1, monthly.getTotalSuites(), "Solo debe contar las unidades de la suite aprobada");
    }

    @Test
    @DisplayName("SuiteService.getSuitesByBranch excluye suites de propiedades pendientes")
    void getSuitesByBranchExcludesUnapprovedPropertySuites() {
        Branch branch = Branch.builder().id("branch-1").name("Centro").build();
        when(branchRepository.findById("branch-1")).thenReturn(Optional.of(branch));

        Property pendingProp = Property.builder().id("prop-pend").status("PENDING").active(true).build();
        Suite s1 = Suite.builder().id("s1").branch("branch-1").active(true).build(); // legacy
        Suite s2 = Suite.builder().id("s2").branch("branch-1").propertyId("prop-pend").active(true).build(); // unapproved prop

        when(propertyRepository.findAll()).thenReturn(List.of(pendingProp));
        when(suiteRepository.findAll()).thenReturn(List.of(s1, s2));

        List<SuiteResponseDTO> list = suiteService.getSuitesByBranch("branch-1");

        assertEquals(1, list.size());
        assertEquals("s1", list.get(0).getId());
    }

    @Test
    @DisplayName("SuiteService.getSuiteTypes excluye suites de propiedades pendientes o rechazadas")
    void getSuiteTypesExcludesUnapprovedPropertySuites() {
        Property approvedProp = Property.builder().id("p-appr").status("APPROVED").active(true).build();
        Property pendingProp = Property.builder().id("p-pend").status("PENDING").active(true).build();

        Suite approvedSuite = Suite.builder().id("s-appr").type("Deluxe").basePrice(new BigDecimal("250000")).propertyId("p-appr").active(true).build();
        Suite pendingSuite = Suite.builder().id("s-pend").type("Deluxe").basePrice(new BigDecimal("100000")).propertyId("p-pend").active(true).build();

        when(propertyRepository.findAll()).thenReturn(List.of(approvedProp, pendingProp));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of(approvedSuite, pendingSuite));

        var types = suiteService.getSuiteTypes();

        assertEquals(1, types.size());
        assertEquals(1, types.get(0).getCount(), "Solo debe contar 1 suite aprobada");
        assertEquals(new BigDecimal("250000"), types.get(0).getMinPrice());
    }

    @Test
    @DisplayName("PropertyService.updatePropertyStatus cambia estado de aprobación correctamente")
    void testUpdatePropertyStatusUpdatesStatusCorrectly() {
        Property prop = Property.builder().id("p-1").status("PENDING").build();
        when(propertyRepository.findById("p-1")).thenReturn(Optional.of(prop));
        when(propertyRepository.save(any(Property.class))).thenAnswer(i -> i.getArgument(0));

        var dto = propertyService.updatePropertyStatus("p-1", "APPROVED");

        assertEquals("APPROVED", dto.getStatus());
        verify(propertyRepository).save(any(Property.class));
    }
}
