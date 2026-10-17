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
    private PropertyRepository propertyRepository;
    private PaymentRepository paymentRepository;
    private PaymentGateway paymentGateway;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        userRepository = mock(UserRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        bookingService = mock(BookingService.class);
        propertyRepository = mock(PropertyRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        paymentGateway = mock(PaymentGateway.class);

        adminService = new AdminService(
                bookingRepository,
                suiteRepository,
                userRepository,
                promotionRepository,
                suiteNightRepository,
                bookingService,
                propertyRepository,
                paymentRepository,
                paymentGateway
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

        co.palaciodelmar.backend.model.Suite s1 = co.palaciodelmar.backend.model.Suite.builder().id("suite-1").totalUnits(1).active(true).build();
        co.palaciodelmar.backend.model.Suite s2 = co.palaciodelmar.backend.model.Suite.builder().id("suite-2").totalUnits(1).active(true).build();
        co.palaciodelmar.backend.model.Suite s3 = co.palaciodelmar.backend.model.Suite.builder().id("suite-3").totalUnits(1).active(true).build();
        co.palaciodelmar.backend.model.Suite s4 = co.palaciodelmar.backend.model.Suite.builder().id("suite-4").totalUnits(1).active(true).build();
        when(suiteRepository.findAll()).thenReturn(List.of(s1, s2, s3, s4));

        when(bookingRepository.findAll()).thenReturn(List.of(b1, b2, b3, b4, b5));

        AdminService.AdminDashboardStats stats = adminService.getDashboardStats();

        assertEquals(4L, stats.getTotalSuites());
        assertEquals(5L, stats.getTotalBookings());
        assertEquals(10L, stats.getTotalUsers());

        // Activas: b1, b2, b3 (confirmed, checked_in, confirmed). b4 es checked_out, b5 es cancelled.
        assertEquals(3L, stats.getActiveBookings());

        // 2 unidades ocupadas hoy (suite-1 y suite-2) de 4 unidades totales = 50.0%
        assertEquals(50.0, stats.getAverageOccupancy());

        // Revenue: 300000 + 400000 + 500000 + 200000 + 0 = 1400000
        assertEquals(new BigDecimal("1400000"), stats.getTotalRevenue());
    }

    @Test
    @DisplayName("getDashboardStats con suite multi-unidad no infla la ocupación")
    void testDashboardOccupancyMultiUnitSuiteDoesNotInflate() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));

        when(suiteRepository.count()).thenReturn(1L);
        when(bookingRepository.count()).thenReturn(1L);
        when(userRepository.count()).thenReturn(1L);

        // 1 sola suite pero con 4 unidades físicas vendibles
        co.palaciodelmar.backend.model.Suite multiSuite = co.palaciodelmar.backend.model.Suite.builder()
                .id("suite-multi-4")
                .totalUnits(4)
                .active(true)
                .build();
        when(suiteRepository.findAll()).thenReturn(List.of(multiSuite));

        // 1 sola reserva ocupando slot 1 hoy
        Booking b = Booking.builder()
                .id("bk-slot-1")
                .suite("suite-multi-4")
                .unitSlot(1)
                .status("confirmed")
                .checkIn(today)
                .checkOut(today.plusDays(2))
                .paidAmount(new BigDecimal("200000"))
                .build();
        when(bookingRepository.findAll()).thenReturn(List.of(b));

        AdminService.AdminDashboardStats stats = adminService.getDashboardStats();

        assertEquals(1L, stats.getTotalSuites());
        assertEquals(1L, stats.getActiveBookings());

        // 1 unidad ocupada de 4 unidades vendibles = 25.0% (no 100%)
        assertEquals(25.0, stats.getAverageOccupancy());
    }

    @Test
    @DisplayName("getDashboardStats excluye suites de propiedades pendientes o rechazadas del cálculo de ocupación")
    void testDashboardOccupancyExcludesSuitesOfUnapprovedProperties() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));

        co.palaciodelmar.backend.model.Property approvedProp = co.palaciodelmar.backend.model.Property.builder()
                .id("prop-approved").status("APPROVED").active(true).build();
        co.palaciodelmar.backend.model.Property pendingProp = co.palaciodelmar.backend.model.Property.builder()
                .id("prop-pending").status("PENDING").active(true).build();

        when(propertyRepository.findAll()).thenReturn(List.of(approvedProp, pendingProp));

        // Suite 1: propiedad aprobada (1 unidad)
        co.palaciodelmar.backend.model.Suite sApproved = co.palaciodelmar.backend.model.Suite.builder()
                .id("s-approved").propertyId("prop-approved").totalUnits(1).active(true).build();
        // Suite 2: propiedad pendiente (1 unidad) - NO DEBE CONTAR
        co.palaciodelmar.backend.model.Suite sPending = co.palaciodelmar.backend.model.Suite.builder()
                .id("s-pending").propertyId("prop-pending").totalUnits(1).active(true).build();

        when(suiteRepository.findAll()).thenReturn(List.of(sApproved, sPending));

        // Reserva ocupando la suite aprobada hoy
        Booking b = Booking.builder()
                .id("bk-appr")
                .suite("s-approved")
                .status("confirmed")
                .checkIn(today)
                .checkOut(today.plusDays(2))
                .paidAmount(new BigDecimal("150000"))
                .build();
        when(bookingRepository.findAll()).thenReturn(List.of(b));

        AdminService.AdminDashboardStats stats = adminService.getDashboardStats();

        // Solo 1 unidad vendible (la suite aprobada), ocupada hoy = 100.0%
        // Si se incluyera la suite pendiente, daría 50.0%
        assertEquals(100.0, stats.getAverageOccupancy());
    }

    @Test
    @DisplayName("resolveRefund con mark_refunded invoca pasarela y actualiza estado a refunded")
    void testResolveRefundMarkRefundedCallsGateway() {
        co.palaciodelmar.backend.model.Payment payment = co.palaciodelmar.backend.model.Payment.builder()
                .id("pay-ref-1")
                .status("requires_refund")
                .amount(new BigDecimal("250000"))
                .transactionId("tx_gate_123")
                .notes("Pago tardío")
                .build();

        when(paymentRepository.findById("pay-ref-1")).thenReturn(java.util.Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        co.palaciodelmar.backend.model.Payment result = adminService.resolveRefund("pay-ref-1", "mark_refunded", "Aprobado tras revisión de soporte");

        assertEquals("refunded", result.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getNotes().contains("Reembolso procesado por ADMIN"));
        verify(paymentGateway).refund("tx_gate_123", new BigDecimal("250000"));
    }

    @Test
    @DisplayName("resolveRefund con dismiss actualiza estado a failed sin invocar pasarela")
    void testResolveRefundDismissSetsFailedStatus() {
        co.palaciodelmar.backend.model.Payment payment = co.palaciodelmar.backend.model.Payment.builder()
                .id("pay-ref-2")
                .status("requires_refund")
                .amount(new BigDecimal("100000"))
                .notes("Pago dudoso")
                .build();

        when(paymentRepository.findById("pay-ref-2")).thenReturn(java.util.Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        co.palaciodelmar.backend.model.Payment result = adminService.resolveRefund("pay-ref-2", "dismiss", "Fraude confirmado");

        assertEquals("failed", result.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getNotes().contains("Reembolso descartado por ADMIN"));
        verify(paymentGateway, never()).refund(any(), any());
    }
}
