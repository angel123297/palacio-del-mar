package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketplaceCommissionTest {

    @Test
    @DisplayName("Cálculo de comisión de marketplace: 8% platformFee y 92% hostPayout")
    void verifiesMarketplaceCommissionCalculation() {
        BigDecimal totalRoomPrice = new BigDecimal("1000000.00"); // 1 millón COP

        BigDecimal feeRate = new BigDecimal("0.08");
        BigDecimal platformFee = totalRoomPrice.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hostPayout = totalRoomPrice.subtract(platformFee);

        assertEquals(new BigDecimal("80000.00"), platformFee, "La comisión del 8% debe ser $80.000 COP");
        assertEquals(new BigDecimal("920000.00"), hostPayout, "La liquidación al anfitrión del 92% debe ser $920.000 COP");

        Booking booking = Booking.builder()
                .id("B-COMMISSION-TEST")
                .totalPrice(totalRoomPrice)
                .platformFee(platformFee)
                .hostPayout(hostPayout)
                .build();

        assertEquals(new BigDecimal("80000.00"), booking.getPlatformFee());
        assertEquals(new BigDecimal("920000.00"), booking.getHostPayout());
    }
}
