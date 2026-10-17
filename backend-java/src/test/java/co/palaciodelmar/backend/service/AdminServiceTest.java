package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminServiceTest {

    private BookingRepository bookingRepository;
    private SuiteRepository suiteRepository;
    private UserRepository userRepository;
    private PromotionRepository promotionRepository;
    private SuiteNightRepository suiteNightRepository;
    private BookingService bookingService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        userRepository = mock(UserRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        bookingService = mock(BookingService.class);

        adminService = new AdminService(
                bookingRepository,
                suiteRepository,
                userRepository,
                promotionRepository,
                suiteNightRepository,
                bookingService
        );
    }

    @Test
    @DisplayName("getDashboardStats calcula ocupación real de hoy según fechas de estancia")
    void testDashboardOccupancyCalculatesTodayStays() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));

        when(suiteRepository.count()).thenReturn(4L);
        when(bookingRepository.count()).thenReturn(5L);
        when(userRepository.count()).thenReturn(10L);

        // Reserva 1: suite-1 alojada hoy (ayer a pasado mañana) -> OCUPADA HOY
        Booking b1 = Booking.builder()
                .id("b1")
                .suite("suite-1")
                .status("confirmed")
                .checkIn(today.minusDays(1))
                .checkOut(today.plusDays(2))
                .paidAmount(new BigDecimal("300000"))
                .build();

        // Reserva 2: suite-2 alojada hoy (entra hoy y sale en 3 días) -> OCUPADA HOY
        Booking b2 = Booking.builder()
                .id("b2")
                .suite("suite-2")
                .status("checked_in")
                .checkIn(today)
                .checkOut(today.plusDays(3))
                .paidAmount(new BigDecimal("400000"))
                .build();

        // Reserva 3: suite-3 en el futuro (entra la próxima semana) -> NO OCUPADA HOY
        Booking b3 = Booking.builder()
                .id("b3")
                .suite("suite-3")
                .status("confirmed")
                .checkIn(today.plusDays(7))
                .checkOut(today.plusDays(10))
                .paidAmount(new BigDecimal("500000"))
                .build();

        // Reserva 4: suite-4 en el pasado (salió ayer) -> NO OCUPADA HOY
        Booking b4 = Booking.builder()
                .id("b4")
                .suite("suite-4")
                .status("checked_out")
                .checkIn(today.minusDays(5))
                .checkOut(today.minusDays(1))
                .paidAmount(new BigDecimal("200000"))
                .build();

        // Reserva 5: cancelada -> NO OCUPADA
        Booking b5 = Booking.builder()
                .id("b5")
                .suite("suite-1")
                .status("cancelled")
                .checkIn(today)
                .checkOut(today.plusDays(1))
                .paidAmount(new BigDecimal("100000"))
                .refundedAmount(new BigDecimal("100000"))
                .build();

        when(bookingRepository.findAll()).thenReturn(List.of(b1, b2, b3, b4, b5));

        AdminService.AdminDashboardStats stats = adminService.getDashboardStats();

        assertEquals(4L, stats.getTotalSuites());
        assertEquals(5L, stats.getTotalBookings());
        assertEquals(10L, stats.getTotalUsers());

        // Activas: b1, b2, b3 (confirmed, checked_in, confirmed). b4 es checked_out, b5 es cancelled.
        assertEquals(3L, stats.getActiveBookings());

        // 2 suites ocupadas hoy (suite-1 y suite-2) de 4 suites totales = 50.0%
        assertEquals(50.0, stats.getAverageOccupancy());

        // Revenue: 300000 + 400000 + 500000 + 200000 + 0 = 1400000
        assertEquals(new BigDecimal("1400000"), stats.getTotalRevenue());
    }
}
