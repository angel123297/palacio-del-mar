package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPricingTest {

    @Mock
    private SuiteRepository suiteRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ExperienceRepository experienceRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private co.palaciodelmar.backend.repository.PropertyRepository propertyRepository;

    @Spy
    private PricingService pricingService = new PricingService();

    @InjectMocks
    private SuiteService suiteService;

    private Suite testSuite;
    private Branch testBranch;

    @BeforeEach
    void setUp() {
        testBranch = Branch.builder()
                .id("branch-getsemani")
                .name("Getsemaní")
                .slug("getsemani")
                .build();

        testSuite = Suite.builder()
                .id("suite-101")
                .name("Suite Presidencial")
                .slug("suite-presidencial")
                .branch("branch-getsemani")
                .basePrice(new BigDecimal("1000.00"))
                .active(true)
                .build();
    }

    @Test
    void calculatePriceAppliesActiveBranchPromotion() {
        LocalDate checkIn = LocalDate.parse("2026-11-02"); // Lunes ordinario (x1.00)
        LocalDate checkOut = LocalDate.parse("2026-11-04"); // 2 noches

        Promotion promo20 = Promotion.builder()
                .id("promo-1")
                .branch("branch-getsemani")
                .title("Especial Getsemaní")
                .discountPercent(20)
                .startDate(LocalDate.parse("2026-11-01"))
                .endDate(LocalDate.parse("2026-11-10"))
                .active(true)
                .build();

        when(suiteRepository.findById("suite-101")).thenReturn(Optional.of(testSuite));
        when(promotionRepository.findByBranchAndActiveTrue("branch-getsemani")).thenReturn(List.of(promo20));

        CalculatePriceRequest request = CalculatePriceRequest.builder()
                .suiteId("suite-101")
                .checkIn(checkIn)
                .checkOut(checkOut)
                .includeExperiences(false)
                .build();

        CalculatePriceResponseDTO response = suiteService.calculatePrice(request);

        assertNotNull(response);
        // Subtotal: 2 noches * 1000 = 2000
        assertEquals(0, new BigDecimal("2000.00").compareTo(response.getSubtotal()));
        // Descuento: 20% de 2000 = 400
        assertEquals(0, new BigDecimal("400.00").compareTo(response.getDiscount()));
        // Impuestos: (2000 - 400) * 0.19 = 1600 * 0.19 = 304
        // Total: 1600 + 304 = 1904.00
        assertEquals(0, new BigDecimal("1904.00").compareTo(response.getTotal()));
        assertEquals("Promoción / Larga estadía", response.getDiscountReason());
    }

    @Test
    void calculatePriceUsesZeroPromotionWhenNoneMatchesDates() {
        LocalDate checkIn = LocalDate.parse("2026-11-15");
        LocalDate checkOut = LocalDate.parse("2026-11-17");

        Promotion expiredPromo = Promotion.builder()
                .id("promo-exp")
                .branch("branch-getsemani")
                .discountPercent(25)
                .startDate(LocalDate.parse("2026-11-01"))
                .endDate(LocalDate.parse("2026-11-10"))
                .active(true)
                .build();

        when(suiteRepository.findById("suite-101")).thenReturn(Optional.of(testSuite));
        when(promotionRepository.findByBranchAndActiveTrue("branch-getsemani")).thenReturn(List.of(expiredPromo));

        CalculatePriceRequest request = CalculatePriceRequest.builder()
                .suiteId("suite-101")
                .checkIn(checkIn)
                .checkOut(checkOut)
                .build();

        CalculatePriceResponseDTO response = suiteService.calculatePrice(request);

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getDiscount()));
        assertNull(response.getDiscountReason());
    }
}
