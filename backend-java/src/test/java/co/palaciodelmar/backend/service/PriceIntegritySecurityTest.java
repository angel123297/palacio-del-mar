package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.model.Suite;
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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests intensivos de integridad de precios en createBooking.
 * Verifican que el CLIENTE no pueda manipular precios/cantidades de experiencias ni de órdenes de comida.
 * Generados por la auditoría de seguridad del 2026-10-15 (ver /AUDITORIA_SEGURIDAD.md).
 *
 * Regla de oro: todo precio usado para calcular el total debe venir del backend (BD),
 * nunca del body de la petición.
 */
class PriceIntegritySecurityTest {

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

    private static final BigDecimal BASE_PRICE = new BigDecimal("860000");
    private static final BigDecimal EXP_PRICE = new BigDecimal("500000");
    private static final BigDecimal GUARANTEE = new BigDecimal("100000");

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

        Suite suite = Suite.builder()
                .id("suite-1").slug("suite-real").branch("branch-1")
                .basePrice(BASE_PRICE).maxGuests(2).totalUnits(1)
                .active(true).available(true).build();
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(suiteRepository.findBySlug(anyString())).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(anyString(), any(), any()))
                .thenReturn(new ArrayList<>());
        when(promotionRepository.findByBranchAndActiveTrue(anyString())).thenReturn(new ArrayList<>());
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LocalDate checkIn = LocalDate.now().plus(5, ChronoUnit.DAYS);
        LocalDate checkOut = checkIn.plus(2, ChronoUnit.DAYS);
        when(pricingService.calculatePrice(any(), any(), any(), any()))
                .thenReturn(new PricingService.PricingResult(
                        BASE_PRICE, BigDecimal.ZERO, BigDecimal.ZERO, BASE_PRICE,
                        List.of(new PricingService.NightlyPrice(checkIn, BASE_PRICE))));
    }

    private BookingRequest baseRequest() {
        BookingRequest request = new BookingRequest();
        request.setSuiteId("suite-1");
        request.setGuests(2);
        request.setCheckIn(LocalDate.now().plus(5, ChronoUnit.DAYS));
        request.setCheckOut(LocalDate.now().plus(7, ChronoUnit.DAYS));
        return request;
    }

    private Booking lastSavedBooking() {
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository, atLeast(1)).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Experiencia vía Map: el precio SIEMPRE sale de la BD aunque el cliente mande price=0 y quantity=999")
    void testExperiencePriceAndQuantityAlwaysComeFromDatabase() {
        Experience yate = Experience.builder().id("exp-1").name("Yate Islas del Rosario").price(EXP_PRICE).build();
        when(experienceRepository.findById("exp-1")).thenReturn(Optional.of(yate));

        Map<String, Object> tampered = new HashMap<>();
        tampered.put("experienceId", "exp-1");
        tampered.put("price", BigDecimal.ZERO);       // intento de manipulación
        tampered.put("quantity", 999);                // intento de manipulación

        BookingRequest request = baseRequest();
        request.setExperiences(List.of(tampered));

        bookingService.createBooking("u1", request);

        Booking saved = lastSavedBooking();
        assertEquals(1, saved.getExperiences().size());
        assertEquals(Integer.valueOf(1), saved.getExperiences().get(0).getQuantity());
        assertTrue(saved.getExperiences().get(0).getPrice().compareTo(EXP_PRICE) == 0,
                "El precio debe ser el de la BD (500000), no el del cliente");
        // total = 860000 (hab) + 500000 (exp BD) + 100000 (depósito) = 1460000
        BigDecimal expected = BASE_PRICE.add(EXP_PRICE).add(GUARANTEE);
        assertTrue(saved.getTotalPrice().compareTo(expected) == 0,
                "Total esperado " + expected + ", fue: " + saved.getTotalPrice());
    }

    @Test
    @DisplayName("BUG-PDM-01: una orden de comida con price=0 debe cobrarse con el precio del catálogo (80000)")
    void testFoodOrderWithZeroPriceMustChargeDefaultPrice() {
        Booking.FoodOrder freeLunch = Booking.FoodOrder.builder()
                .dishName("Pescado frito")
                .price(BigDecimal.ZERO) // manipulación del cliente
                .build();

        BookingRequest request = baseRequest();
        request.setFoodOrders(List.of(freeLunch));

        bookingService.createBooking("u1", request);

        Booking saved = lastSavedBooking();
        // Comportamiento SEGURO esperado: total = 860000 + 80000 (default) + 100000 = 1040000
        BigDecimal expected = BASE_PRICE.add(new BigDecimal("80000")).add(GUARANTEE);
        assertTrue(saved.getTotalPrice().compareTo(expected) == 0,
                "El total debe incluir el precio de catálogo (80000). Total actual: "
                        + saved.getTotalPrice() + " (vulnerable si es " + BASE_PRICE.add(GUARANTEE) + ")");
    }

    @Test
    @DisplayName("BUG-PDM-02: una SelectedExperience inyectada con price=0 debe re-cotizarse desde la BD")
    void testSelectedExperienceInstanceMustBeRepricedFromDatabase() {
        Experience yate = Experience.builder().id("exp-1").name("Yate").price(EXP_PRICE).build();
        when(experienceRepository.findById("exp-1")).thenReturn(Optional.of(yate));

        Booking.SelectedExperience tampered = Booking.SelectedExperience.builder()
                .experienceId("exp-1")
                .name("Yate")
                .price(BigDecimal.ZERO) // manipulación
                .quantity(1)
                .build();

        BookingRequest request = baseRequest();
        request.setExperiences(List.of(tampered));

        bookingService.createBooking("u1", request);

        Booking saved = lastSavedBooking();
        BigDecimal expected = BASE_PRICE.add(EXP_PRICE).add(GUARANTEE);
        assertTrue(saved.getTotalPrice().compareTo(expected) == 0,
                "El total debe usar el precio de BD. Total actual: " + saved.getTotalPrice());
    }
}
