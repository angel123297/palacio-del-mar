package co.palaciodelmar.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingServiceTest {

    private final PricingService service = new PricingService();

    @ParameterizedTest(name = "{0} has nightly multiplier {1}")
    @CsvSource({
            "2026-05-04, 1.00", // Temporada baja: lunes ordinario
            "2026-05-09, 1.15", // Temporada media: sábado
            "2026-06-01, 1.30", // Temporada alta: junio
            "2026-08-31, 1.30", // Temporada alta: agosto
            "2026-04-01, 1.30", // Semana Santa aproximada: inicio
            "2026-04-10, 1.30", // Semana Santa aproximada: último día
            "2026-04-11, 1.15", // Fuera de Semana Santa; sábado
            "2026-12-20, 1.15", // Antes de Navidad; domingo
            "2026-12-21, 1.50", // Navidad: primer día pico
            "2027-01-10, 1.50", // Navidad: último día pico
            "2027-01-11, 1.00"  // Después del pico: lunes ordinario
    })
    void appliesSeasonalMultiplierAndCalculatesTaxes(LocalDate checkIn, BigDecimal multiplier) {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("100.00"), checkIn, checkIn.plusDays(1), BigDecimal.ZERO);
        BigDecimal nightlyAmount = new BigDecimal("100.00").multiply(multiplier);
        BigDecimal expectedTaxes = nightlyAmount.multiply(new BigDecimal("0.19"));

        assertMoneyEquals(nightlyAmount, result.subtotal());
        assertMoneyEquals(nightlyAmount, result.nightlyPrices().get(0).price());
        assertEquals(checkIn, result.nightlyPrices().get(0).date());
        assertMoneyEquals(BigDecimal.ZERO, result.discount());
        assertMoneyEquals(expectedTaxes, result.taxes());
        assertMoneyEquals(nightlyAmount.add(expectedTaxes), result.total());
    }

    @Test
    void appliesFivePercentDiscountStartingAtFiveNights() {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("100.00"),
                LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-07"),
                null);

        assertMoneyEquals(new BigDecimal("500.00"), result.subtotal());
        assertMoneyEquals(new BigDecimal("25.00"), result.discount());
        assertMoneyEquals(new BigDecimal("90.25"), result.taxes());
        assertMoneyEquals(new BigDecimal("565.25"), result.total());
        assertEquals(5, result.nightlyPrices().size());
    }

    @Test
    void appliesTenPercentDiscountStartingAtSevenNights() {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("100.00"),
                LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-09"),
                null);

        assertMoneyEquals(new BigDecimal("730.00"), result.subtotal());
        assertMoneyEquals(new BigDecimal("73.00"), result.discount());
        assertMoneyEquals(new BigDecimal("124.83"), result.taxes());
        assertMoneyEquals(new BigDecimal("781.83"), result.total());
        assertEquals(7, result.nightlyPrices().size());
    }

    @Test
    void doesNotApplyLongStayDiscountBelowFiveNights() {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("100.00"),
                LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-06"),
                BigDecimal.ZERO);

        assertMoneyEquals(new BigDecimal("400.00"), result.subtotal());
        assertMoneyEquals(BigDecimal.ZERO, result.discount());
        assertMoneyEquals(new BigDecimal("76.00"), result.taxes());
        assertMoneyEquals(new BigDecimal("476.00"), result.total());
    }

    @Test
    void usesLargerOfPromotionAndLongStayDiscount() {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("100.00"),
                LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-09"),
                new BigDecimal("15"));

        assertMoneyEquals(new BigDecimal("730.00"), result.subtotal());
        assertMoneyEquals(new BigDecimal("109.50"), result.discount());
        assertMoneyEquals(new BigDecimal("117.90"), result.taxes());
        assertMoneyEquals(new BigDecimal("738.40"), result.total());
    }

    @Test
    void keepsLongStayDiscountWhenItExceedsPromotion() {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("100.00"),
                LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-09"),
                new BigDecimal("5"));

        assertMoneyEquals(new BigDecimal("73.00"), result.discount());
        assertMoneyEquals(new BigDecimal("124.83"), result.taxes());
        assertMoneyEquals(new BigDecimal("781.83"), result.total());
    }

    @Test
    void chargesPromotionWhenNoLongStayDiscountApplies() {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("100.00"),
                LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-06"),
                new BigDecimal("10"));

        assertMoneyEquals(new BigDecimal("400.00"), result.subtotal());
        assertMoneyEquals(new BigDecimal("40.00"), result.discount());
        assertMoneyEquals(new BigDecimal("68.40"), result.taxes());
        assertMoneyEquals(new BigDecimal("428.40"), result.total());
    }

    @Test
    void roundsDiscountAndTaxToTwoDecimalPlaces() {
        PricingService.PricingResult result = service.calculatePrice(
                new BigDecimal("10.01"),
                LocalDate.parse("2026-05-04"),
                LocalDate.parse("2026-05-09"),
                null);

        assertMoneyEquals(new BigDecimal("50.05"), result.subtotal());
        assertMoneyEquals(new BigDecimal("2.50"), result.discount());
        assertMoneyEquals(new BigDecimal("9.03"), result.taxes());
        assertMoneyEquals(new BigDecimal("56.58"), result.total());
    }

    @Test
    void rejectsZeroOrNegativeLengthStay() {
        LocalDate checkIn = LocalDate.parse("2026-05-04");

        assertThrows(IllegalArgumentException.class,
                () -> service.calculatePrice(new BigDecimal("100"), checkIn, checkIn, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> service.calculatePrice(new BigDecimal("100"), checkIn, checkIn.minusDays(1), BigDecimal.ZERO));
    }

    private static void assertMoneyEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
                () -> "Expected " + expected.toPlainString() + " but got " + actual.toPlainString());
    }
}
