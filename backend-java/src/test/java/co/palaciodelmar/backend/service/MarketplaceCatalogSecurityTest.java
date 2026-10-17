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
}
