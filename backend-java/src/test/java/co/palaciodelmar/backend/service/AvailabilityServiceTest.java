package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock private SuiteRepository suiteRepository;
    @Mock private SuiteNightRepository suiteNightRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private PricingService pricingService;

    private AvailabilityService availabilityService;

    private Suite penthouseSuite;
    private Property bocagrandeProp;
    private Branch bocagrandeBranch;

    @BeforeEach
    void setUp() {
        availabilityService = new AvailabilityService(
                suiteRepository,
                suiteNightRepository,
                bookingRepository,
                promotionRepository,
                branchRepository,
                propertyRepository,
                pricingService
        );

        bocagrandeProp = Property.builder()
                .id("prop-boca")
                .slug("bocagrande")
                .status("APPROVED")
                .active(true)
                .build();

        bocagrandeBranch = Branch.builder()
                .id("branch-boca")
                .slug("bocagrande")
                .name("Palacio Bocagrande")
                .active(true)
                .build();

        penthouseSuite = Suite.builder()
                .id("suite-boca-penthouse")
                .slug("penthouse-bocagrande")
                .name("Penthouse Ocean Front")
                .propertyId("prop-boca")
                .branch("branch-boca")
                .basePrice(new BigDecimal("2450000"))
                .totalUnits(1)
                .maxGuests(4)
                .active(true)
                .build();

        lenient().when(pricingService.calculatePrice(any(), any(), any(), any()))
                .thenReturn(new PricingService.PricingResult(
                        new BigDecimal("2450000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("2450000"),
                        List.of(new PricingService.NightlyPrice(LocalDate.of(2026, 10, 1), new BigDecimal("2450000")))
                ));
    }

    @Test
    @DisplayName("Cuando no hay suites que coincidan (totalSuites == 0), ningún día debe estar marcado como soldOut (evita falso muro de ocupación)")
    void whenNoSuitesFound_daysMustNotBeSoldOut() {
        when(propertyRepository.findAll()).thenReturn(List.of(bocagrandeProp));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of());
        when(suiteRepository.findById("unknown-suite")).thenReturn(Optional.empty());
        when(suiteRepository.findBySlug("unknown-suite")).thenReturn(Optional.empty());

        AvailabilityService.MonthlyAvailabilityResponseData res =
                availabilityService.getMonthlyAvailability(2026, 10, 2, "bocagrande", null, "unknown-suite");

        assertNotNull(res);
        assertEquals(0, res.getTotalSuites());
        assertEquals(0, res.getSummary().getSoldOutDays(), "Los días agotados deben ser 0 si no hay inventario");
        for (AvailabilityService.CalendarDay day : res.getCalendar()) {
            assertFalse(day.getSoldOut(), "El día " + day.getDate() + " no debe marcarse como soldOut");
            assertTrue(day.getAvailableSuites() >= 0);
        }
    }

    @Test
    @DisplayName("Cuando se consulta con un slug alias (suite-presidencial-mar), resuelve al penthouse real y muestra disponibilidad real")
    void whenAliasSlugUsed_resolvesToRealSuiteAndShowsAvailability() {
        when(propertyRepository.findAll()).thenReturn(List.of(bocagrandeProp));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of(penthouseSuite));
        when(suiteRepository.findById("suite-presidencial-mar")).thenReturn(Optional.empty());
        when(suiteRepository.findBySlug("suite-presidencial-mar")).thenReturn(Optional.empty());
        // El alias apunta a "penthouse-bocagrande"
        when(suiteRepository.findById("penthouse-bocagrande")).thenReturn(Optional.of(penthouseSuite));
        when(propertyRepository.findById("prop-boca")).thenReturn(Optional.of(bocagrandeProp));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-boca-penthouse"), any(), any()))
                .thenReturn(Collections.emptyList());

        AvailabilityService.MonthlyAvailabilityResponseData res =
                availabilityService.getMonthlyAvailability(2026, 10, 2, "bocagrande", null, "suite-presidencial-mar");

        assertNotNull(res);
        assertEquals(1, res.getTotalSuites(), "Debe contar 1 unidad de la suite real");
        assertEquals(0, res.getSummary().getSoldOutDays());
        assertFalse(res.getCalendar().get(0).getSoldOut());
        assertEquals(1, res.getCalendar().get(0).getAvailableSuites());
    }

    @Test
    @DisplayName("Noches retenidas expiradas (held con expiresAt en el pasado) no deben contarse como ocupadas")
    void expiredHoldsAreIgnoredInOccupancyCalculation() {
        when(propertyRepository.findAll()).thenReturn(List.of(bocagrandeProp));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of(penthouseSuite));
        when(suiteRepository.findById("suite-boca-penthouse")).thenReturn(Optional.of(penthouseSuite));
        when(propertyRepository.findById("prop-boca")).thenReturn(Optional.of(bocagrandeProp));

        // Noche retenida expirada hace 5 minutos
        SuiteNight expiredNight = SuiteNight.builder()
                .suite("suite-boca-penthouse")
                .status("held")
                .expiresAt(Instant.now().minusSeconds(300))
                .dates(List.of(LocalDate.of(2026, 10, 22)))
                .build();

        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-boca-penthouse"), any(), any()))
                .thenReturn(List.of(expiredNight));

        AvailabilityService.MonthlyAvailabilityResponseData res =
                availabilityService.getMonthlyAvailability(2026, 10, 2, "bocagrande", null, "suite-boca-penthouse");

        // El día 22 debe estar disponible (availableSuites = 1, soldOut = false)
        AvailabilityService.CalendarDay day22 = res.getCalendar().stream()
                .filter(d -> "2026-10-22".equals(d.getDate()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, day22.getAvailableSuites(), "La noche retenida expirada no debe restar disponibilidad");
        assertFalse(day22.getSoldOut(), "La suite debe estar disponible");
    }

    @Test
    @DisplayName("Noches confirmadas efectivamente marcan el día como soldOut cuando se agota la capacidad")
    void confirmedNightsMarkSoldOutWhenCapacityExhausted() {
        when(propertyRepository.findAll()).thenReturn(List.of(bocagrandeProp));
        when(suiteRepository.findByActiveTrue()).thenReturn(List.of(penthouseSuite));
        when(suiteRepository.findById("suite-boca-penthouse")).thenReturn(Optional.of(penthouseSuite));
        when(propertyRepository.findById("prop-boca")).thenReturn(Optional.of(bocagrandeProp));

        // Noche confirmada vigente
        SuiteNight confirmedNight = SuiteNight.builder()
                .suite("suite-boca-penthouse")
                .status("confirmed")
                .expiresAt(null)
                .dates(List.of(LocalDate.of(2026, 10, 25)))
                .build();

        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-boca-penthouse"), any(), any()))
                .thenReturn(List.of(confirmedNight));

        AvailabilityService.MonthlyAvailabilityResponseData res =
                availabilityService.getMonthlyAvailability(2026, 10, 2, "bocagrande", null, "suite-boca-penthouse");

        AvailabilityService.CalendarDay day25 = res.getCalendar().stream()
                .filter(d -> "2026-10-25".equals(d.getDate()))
                .findFirst()
                .orElseThrow();

        assertEquals(0, day25.getAvailableSuites());
        assertTrue(day25.getSoldOut(), "El día 25 debe estar agotado porque la única unidad está reservada");
    }

    @Test
    @DisplayName("getPeakDates retorna rangos y festivos con nombres compatibles para frontend y backend")
    void getPeakDatesReturnsCompatibleStructures() {
        Map<String, Object> peakDates = availabilityService.getPeakDates(2026);
        assertNotNull(peakDates);
        assertTrue(peakDates.containsKey("highSeason"));
        assertTrue(peakDates.containsKey("peakSeason"));
        assertTrue(peakDates.containsKey("holidays"));

        @SuppressWarnings("unchecked")
        List<Map<String, String>> highSeason = (List<Map<String, String>>) peakDates.get("highSeason");
        assertFalse(highSeason.isEmpty());
        // Debe tener tanto start como startDate, y name como label
        Map<String, String> firstSeason = highSeason.get(0);
        assertTrue(firstSeason.containsKey("start"));
        assertTrue(firstSeason.containsKey("startDate"));
        assertTrue(firstSeason.containsKey("name"));
        assertTrue(firstSeason.containsKey("label"));
    }
}
