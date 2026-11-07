package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SuitePriceCalculationTest {

    private SuiteRepository suiteRepository;
    private co.palaciodelmar.backend.repository.BranchRepository branchRepository;
    private PropertyRepository propertyRepository;
    private PromotionRepository promotionRepository;
    private ExperienceRepository experienceRepository;
    private PricingService pricingService;
    private SuiteService suiteService;

    @BeforeEach
    void setUp() {
        suiteRepository = mock(SuiteRepository.class);
        branchRepository = mock(co.palaciodelmar.backend.repository.BranchRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        experienceRepository = mock(ExperienceRepository.class);
        pricingService = new PricingService();

        suiteService = new SuiteService(
                suiteRepository,
                branchRepository,
                propertyRepository,
                experienceRepository,
                promotionRepository,
                pricingService
        );
    }

    @Test
    @DisplayName("calculatePrice con 4 huéspedes y 2 noches: calcula $480.000 por huéspedes adicionales y $100.000 de garantía")
    void testCalculatePriceIncludesExtraGuestsAndGuarantee() {
        LocalDate checkIn = LocalDate.of(2026, 11, 10); // Martes
        LocalDate checkOut = LocalDate.of(2026, 11, 12); // Jueves (2 noches temporada baja x1.0)
        BigDecimal basePrice = new BigDecimal("500000"); // 500.000/noche

        Suite suite = Suite.builder()
                .id("suite-1")
                .basePrice(basePrice)
                .propertyId("prop-1")
                .branch("bocagrande")
                .build();

        Property property = Property.builder()
                .id("prop-1")
                .status("APPROVED")
                .active(true)
                .build();

        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));
        when(propertyRepository.findById("prop-1")).thenReturn(Optional.of(property));
        when(promotionRepository.findByBranchAndActiveTrue(any())).thenReturn(List.of());

        CalculatePriceRequest request = CalculatePriceRequest.builder()
                .suiteId("suite-1")
                .checkIn(checkIn)
                .checkOut(checkOut)
                .guests(4) // 2 huéspedes extra
                .includeExperiences(false)
                .build();

        CalculatePriceResponseDTO response = suiteService.calculatePrice(request);

        assertNotNull(response);
        // Subtotal = 500.000 * 2 = 1.000.000
        // IVA 19% = 190.000 -> roomTotal = 1.190.000
        assertEquals(new BigDecimal("1000000.00"), response.getSubtotal());
        assertEquals(new BigDecimal("1190000.00"), response.getRoomTotal());
        
        // Huéspedes extra: (4 - 2) * 120.000 * 2 = 480.000
        assertEquals(new BigDecimal("480000"), response.getExtraGuestsFee());
        assertEquals(new BigDecimal("100000"), response.getGuaranteeDeposit());

        // Total esperado: 1.190.000 + 480.000 = 1.670.000
        assertEquals(new BigDecimal("1670000.00"), response.getTotal());
    }
}
