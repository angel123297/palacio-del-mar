package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicPricingTest {

    private SuiteRepository suiteRepository;
    private SuiteNightRepository suiteNightRepository;
    private DynamicPricingService dynamicPricingService;
    private PricingService pricingService;

    private final LocalDate fixedToday = LocalDate.of(2026, 10, 20);
    private final ZoneId bogota = ZoneId.of("America/Bogota");

    @BeforeEach
    void setUp() {
        suiteRepository = mock(SuiteRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        pricingService = new PricingService();

        Clock fixedClock = Clock.fixed(fixedToday.atStartOfDay(bogota).toInstant(), bogota);
        dynamicPricingService = new DynamicPricingService(suiteRepository, suiteNightRepository, fixedClock);
    }

    @Test
    @DisplayName("Tarifa dinámica: Ocupación >= 75% aplica recargo por alta demanda (+15%)")
    void testSurgePricingOnHighOccupancy() {
        Suite suite1 = Suite.builder().id("s1").propertyId("prop-1").totalUnits(2).basePrice(new BigDecimal("100000")).build();
        Suite suite2 = Suite.builder().id("s2").propertyId("prop-1").totalUnits(2).basePrice(new BigDecimal("100000")).build();

        when(suiteRepository.findByPropertyIdAndActiveTrue("prop-1")).thenReturn(List.of(suite1, suite2));

        LocalDate checkIn = fixedToday.plusDays(2); // Jueves (temporada normal x1.0)
        LocalDate checkOut = checkIn.plusDays(1);

        // 3 de 4 unidades ocupadas = 75% ocupación
        SuiteNight sn1 = SuiteNight.builder().suite("s1").dates(List.of(checkIn)).build();
        SuiteNight sn2 = SuiteNight.builder().suite("s1").dates(List.of(checkIn)).build();
        SuiteNight sn3 = SuiteNight.builder().suite("s2").dates(List.of(checkIn)).build();

        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("s1"), any(), any())).thenReturn(List.of(sn1, sn2));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("s2"), any(), any())).thenReturn(List.of(sn3));

        Map<LocalDate, DynamicPricingService.Adjustment> adjustments = dynamicPricingService.adjustmentsFor(suite1, checkIn, checkOut, null);

        assertNotNull(adjustments);
        assertTrue(adjustments.containsKey(checkIn));
        assertEquals(new BigDecimal("1.15"), adjustments.get(checkIn).factor());
        assertTrue(adjustments.get(checkIn).reason().contains("Alta demanda"));

        PricingService.PricingResult result = pricingService.calculatePrice(
                suite1.getBasePrice(), checkIn, checkOut, BigDecimal.ZERO,
                DynamicPricingService.asFactorFunction(adjustments)
        );

        // Martes: base x1.0 x 1.15 = 115000 subtotal, con IVA 19% = 136850
        assertEquals(new BigDecimal("115000.00"), result.subtotal());
    }

    @Test
    @DisplayName("Tarifa dinámica: Última hora (< 48h) con baja ocupación (< 50%) aplica descuento flash (-10%)")
    void testLastMinuteDiscountOnLowOccupancy() {
        Suite suite = Suite.builder().id("s1").propertyId("prop-1").totalUnits(2).basePrice(new BigDecimal("200000")).build();
        when(suiteRepository.findByPropertyIdAndActiveTrue("prop-1")).thenReturn(List.of(suite));

        LocalDate checkIn = fixedToday.plusDays(1); // Mañana (< 48h)
        LocalDate checkOut = checkIn.plusDays(1);

        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(List.of());

        Map<LocalDate, DynamicPricingService.Adjustment> adjustments = dynamicPricingService.adjustmentsFor(suite, checkIn, checkOut, null);

        assertNotNull(adjustments);
        assertTrue(adjustments.containsKey(checkIn));
        assertEquals(new BigDecimal("0.90"), adjustments.get(checkIn).factor());
        assertTrue(adjustments.get(checkIn).reason().contains("última hora"));

        PricingService.PricingResult result = pricingService.calculatePrice(
                suite.getBasePrice(), checkIn, checkOut, BigDecimal.ZERO,
                DynamicPricingService.asFactorFunction(adjustments)
        );

        // Miércoles: base 200000 x 0.90 = 180000 subtotal
        assertEquals(new BigDecimal("180000.00"), result.subtotal());
    }

    @Test
    @DisplayName("Tarifa dinámica: Ocupación moderada no aplica factores extraordinarios (factor 1.0)")
    void testNormalPricingWhenOccupancyIsModerate() {
        Suite suite = Suite.builder().id("s1").propertyId("prop-1").totalUnits(4).basePrice(new BigDecimal("100000")).build();
        when(suiteRepository.findByPropertyIdAndActiveTrue("prop-1")).thenReturn(List.of(suite));

        LocalDate checkIn = fixedToday.plusDays(10);
        LocalDate checkOut = checkIn.plusDays(2);

        // 2 de 4 ocupadas = 50% (no es surge >= 75% ni last minute)
        SuiteNight sn1 = SuiteNight.builder().suite("s1").dates(List.of(checkIn)).build();
        SuiteNight sn2 = SuiteNight.builder().suite("s1").dates(List.of(checkIn)).build();
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(List.of(sn1, sn2));

        Map<LocalDate, DynamicPricingService.Adjustment> adjustments = dynamicPricingService.adjustmentsFor(suite, checkIn, checkOut, null);

        assertTrue(adjustments.isEmpty());
    }

    @Test
    @DisplayName("Tarifa dinámica: Holds expirados y noches canceladas no disparan falso recargo de alta demanda")
    void testExpiredAndCancelledSuiteNightsDoNotTriggerFalseSurgePricing() {
        Suite suite = Suite.builder().id("s1").propertyId("prop-1").totalUnits(2).basePrice(new BigDecimal("100000")).build();
        when(suiteRepository.findByPropertyIdAndActiveTrue("prop-1")).thenReturn(List.of(suite));

        LocalDate checkIn = fixedToday.plusDays(4);
        LocalDate checkOut = checkIn.plusDays(1);
        Instant fixedNow = fixedToday.atStartOfDay(bogota).toInstant();

        // 1 unidad realmente ocupada (50%), más 1 cancelada y 1 expirada -> NO debe superar el umbral del 75%
        SuiteNight activeBooked = SuiteNight.builder()
                .suite("s1")
                .status("booked")
                .dates(List.of(checkIn))
                .build();
        SuiteNight cancelledNight = SuiteNight.builder()
                .suite("s1")
                .status("cancelled")
                .dates(List.of(checkIn))
                .build();
        SuiteNight expiredHold = SuiteNight.builder()
                .suite("s1")
                .status("hold")
                .expiresAt(fixedNow.minusSeconds(120))
                .dates(List.of(checkIn))
                .build();

        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("s1"), any(), any()))
                .thenReturn(List.of(activeBooked, cancelledNight, expiredHold));

        Map<LocalDate, DynamicPricingService.Adjustment> adjustments = dynamicPricingService.adjustmentsFor(suite, checkIn, checkOut, null);

        assertTrue(adjustments.isEmpty(), "Los holds expirados y noches canceladas no deben inflar la ocupación ni activar surge pricing");
    }
}
