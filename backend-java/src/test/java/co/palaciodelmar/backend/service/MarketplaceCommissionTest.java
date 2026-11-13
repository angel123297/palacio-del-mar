package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketplaceCommissionTest {

    private PropertyRepository propertyRepository;
    private BookingRepository bookingRepository;
    private ExperienceRepository experienceRepository;
    private co.palaciodelmar.backend.repository.SuiteRepository suiteRepository;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        propertyRepository = mock(PropertyRepository.class);
        bookingRepository = mock(BookingRepository.class);
        experienceRepository = mock(ExperienceRepository.class);
        suiteRepository = mock(co.palaciodelmar.backend.repository.SuiteRepository.class);

        bookingService = new BookingService(
                bookingRepository,
                suiteRepository,
                null,
                experienceRepository,
                null,
                null,
                null,
                null,
                null
        );

        when(suiteRepository.findById(anyString())).thenReturn(Optional.of(co.palaciodelmar.backend.model.Suite.builder().id("suite-deluxe").name("Suite Deluxe").build()));
        ReflectionTestUtils.setField(bookingService, "propertyRepository", propertyRepository);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("R8: Cálculo de comisión por defecto (8%) sobre base imponible sin duplicar IVA")
    void testCommissionCalculationWithDefaultRate() {
        // Habitación: subtotal 1.000.000, descuento 100.000 -> base imponible 900.000 (sin contar IVA 19% de 171.000)
        Booking booking = Booking.builder()
                .id("b-comm-def")
                .suite("suite-deluxe")
                .subtotal(new BigDecimal("1000000.00"))
                .discount(new BigDecimal("100000.00"))
                .taxes(new BigDecimal("171000.00"))
                .totalPrice(new BigDecimal("1071000.00"))
                .build();

        bookingService.recalculateBookingFeesAndPayout(booking, null);

        // 900.000 * 0.08 = 72.000
        assertEquals(0, new BigDecimal("72000.00").compareTo(booking.getPlatformFee()), "La comisión debe ser 72.000 COP");
        // 900.000 - 72.000 = 828.000
        assertEquals(0, new BigDecimal("828000.00").compareTo(booking.getHostPayout()), "El payout debe ser 828.000 COP");
    }

    @Test
    @DisplayName("R8: Comisión configurable por propiedad (ej. 12%) se respeta sobre la base imponible")
    void testCommissionCalculationWithCustomPropertyCommissionRate() {
        Property property = Property.builder()
                .id("prop-vip")
                .commissionRate(new BigDecimal("0.12")) // 12%
                .build();

        when(propertyRepository.findById("prop-vip")).thenReturn(Optional.of(property));

        Booking booking = Booking.builder()
                .id("b-comm-custom")
                .suite("suite-deluxe")
                .propertyId("prop-vip")
                .subtotal(new BigDecimal("2000000.00"))
                .discount(BigDecimal.ZERO)
                .taxes(new BigDecimal("380000.00"))
                .totalPrice(new BigDecimal("2380000.00"))
                .build();

        bookingService.recalculateBookingFeesAndPayout(booking, null);

        // 2.000.000 * 0.12 = 240.000
        assertEquals(0, new BigDecimal("240000.00").compareTo(booking.getPlatformFee()), "La comisión del 12% debe ser 240.000 COP");
        // 2.000.000 - 240.000 = 1.760.000
        assertEquals(0, new BigDecimal("1760000.00").compareTo(booking.getHostPayout()), "El payout debe ser 1.760.000 COP");
    }

    @Test
    @DisplayName("R8: Agregar y eliminar experiencias recalcula comisiones y payout del anfitrión")
    void testRecalculateFeesAndPayoutOnExperienceAddedAndRemoved() {
        Property property = Property.builder()
                .id("prop-std")
                .commissionRate(new BigDecimal("0.10")) // 10%
                .build();
        when(propertyRepository.findById("prop-std")).thenReturn(Optional.of(property));

        Experience tour = Experience.builder()
                .id("exp-islas")
                .name("Tour Islas del Rosario")
                .price(new BigDecimal("200000.00"))
                .active(true)
                .available(true)
                .build();
        when(experienceRepository.findById("exp-islas")).thenReturn(Optional.of(tour));

        Booking booking = Booking.builder()
                .id("b-exp-test")
                .user("user-exp")
                .suite("suite-deluxe")
                .propertyId("prop-std")
                .status("confirmed")
                .paymentStatus("paid")
                .subtotal(new BigDecimal("1000000.00"))
                .discount(BigDecimal.ZERO)
                .taxes(new BigDecimal("190000.00"))
                .totalPrice(new BigDecimal("1190000.00"))
                .paidAmount(new BigDecimal("1190000.00"))
                .balanceDue(BigDecimal.ZERO)
                .experiences(new ArrayList<>())
                .build();

        when(bookingRepository.findById("b-exp-test")).thenReturn(Optional.of(booking));

        // Inicial: base 1.000.000 -> fee: 100.000, payout: 900.000
        bookingService.recalculateBookingFeesAndPayout(booking, null);
        assertEquals(0, new BigDecimal("100000.00").compareTo(booking.getPlatformFee()));
        assertEquals(0, new BigDecimal("900000.00").compareTo(booking.getHostPayout()));

        // Agregar experiencia de 200.000
        bookingService.addExperienceToBooking("b-exp-test", "user-exp", "exp-islas");
        // Base imponible = 1.000.000 + 200.000 = 1.200.000
        // fee (10%) = 120.000, payout = 1.080.000
        assertEquals(0, new BigDecimal("120000.00").compareTo(booking.getPlatformFee()));
        assertEquals(0, new BigDecimal("1080000.00").compareTo(booking.getHostPayout()));

        // Eliminar experiencia de 200.000
        bookingService.removeExperienceFromBooking("b-exp-test", "user-exp", "exp-islas");
        // Base imponible vuelve a 1.000.000 -> fee = 100.000, payout = 900.000
        assertEquals(0, new BigDecimal("100000.00").compareTo(booking.getPlatformFee()));
        assertEquals(0, new BigDecimal("900000.00").compareTo(booking.getHostPayout()));
    }
}
