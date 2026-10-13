package co.palaciodelmar.backend.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class PricingService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19"); // IVA 19%

    public PricingResult calculatePrice(BigDecimal basePrice, LocalDate checkIn, LocalDate checkOut, BigDecimal branchPromotionPercent) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights <= 0) {
            throw new IllegalArgumentException("La estadía debe ser de al menos 1 noche.");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<NightlyPrice> nightlyPrices = new ArrayList<>();

        for (int i = 0; i < nights; i++) {
            LocalDate date = checkIn.plusDays(i);
            BigDecimal multiplier = getSeasonMultiplier(date);
            BigDecimal nightlyRaw = basePrice.multiply(multiplier);
            subtotal = subtotal.add(nightlyRaw);
            nightlyPrices.add(new NightlyPrice(date, nightlyRaw));
        }

        // Descuentos: se aplica el mayor entre promoción y larga estadía
        BigDecimal longStayDiscountPercent = longStayPercent(nights).divide(new BigDecimal("100"));

        BigDecimal promoPercent = branchPromotionPercent != null ? branchPromotionPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE) : BigDecimal.ZERO;
        BigDecimal appliedDiscountPercent = promoPercent.compareTo(longStayDiscountPercent) > 0 ? promoPercent : longStayDiscountPercent;

        BigDecimal discountAmount = subtotal.multiply(appliedDiscountPercent).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotalAfterDiscount = subtotal.subtract(discountAmount);

        BigDecimal taxes = subtotalAfterDiscount.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotalAfterDiscount.add(taxes).setScale(2, RoundingMode.HALF_UP);

        return new PricingResult(subtotal.setScale(2, RoundingMode.HALF_UP), discountAmount, taxes, total, nightlyPrices);
    }

    /**
     * Descuento por estadía larga (en puntos porcentuales): única fuente de verdad.
     * 5% desde 5 noches. BookingService la usa también para la etiqueta que ve el huésped.
     */
    public static BigDecimal longStayPercent(long nights) {
        return nights >= 5 ? new BigDecimal("5") : BigDecimal.ZERO;
    }

    private BigDecimal getSeasonMultiplier(LocalDate date) {
        // Navidad / Año Nuevo: 21 Dic - 10 Ene (Pico x1.5)
        if ((date.getMonth() == Month.DECEMBER && date.getDayOfMonth() >= 21) ||
            (date.getMonth() == Month.JANUARY && date.getDayOfMonth() <= 10)) {
            return new BigDecimal("1.50");
        }

        // Alta: Junio, Julio, Agosto (x1.3)
        if (date.getMonth() == Month.JUNE || date.getMonth() == Month.JULY || date.getMonth() == Month.AUGUST) {
            return new BigDecimal("1.30");
        }

        // Semana Santa simplificada (Aprox Abril) - Alta x1.3
        if (date.getMonth() == Month.APRIL && date.getDayOfMonth() >= 1 && date.getDayOfMonth() <= 10) {
             return new BigDecimal("1.30");
        }

        // Media: Fines de semana (x1.15)
        if (date.getDayOfWeek().getValue() == 6 || date.getDayOfWeek().getValue() == 7) {
            return new BigDecimal("1.15");
        }

        // Baja: Resto (x1.0)
        return BigDecimal.ONE;
    }

    public record PricingResult(
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal taxes,
            BigDecimal total,
            List<NightlyPrice> nightlyPrices
    ) {}

    public record NightlyPrice(
            LocalDate date,
            BigDecimal price
    ) {}
}
