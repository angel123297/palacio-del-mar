package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests intensivos de frontera sobre la política de cancelación (RN-F03):
 * <48h = retención del 100% (sin depósito); <7 días = 10%; >=7 días = gratis.
 * La retención se calcula sobre (totalPrice - guaranteeDeposit).
 * Generados por la auditoría de seguridad del 2026-10-15 (ver /AUDITORIA_SEGURIDAD.md).
 */
class CancellationPolicyBoundaryTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private BookingRepository bookingRepository;
    private SuiteRepository suiteRepository;
    private BranchRepository branchRepository;
    private ExperienceRepository experienceRepository;
    private SuiteNightRepository suiteNightRepository;
    private UserRepository userRepository;
    private PromotionRepository promotionRepository;
    private PricingService pricingService;
    private PaymentGateway paymentGateway;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        branchRepository = mock(BranchRepository.class);
        experienceRepository = mock(ExperienceRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        userRepository = mock(UserRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        pricingService = mock(PricingService.class);
        paymentGateway = mock(PaymentGateway.class);

        bookingService = new BookingService(
                bookingRepository, suiteRepository, branchRepository, experienceRepository,
                suiteNightRepository, userRepository, promotionRepository, pricingService, paymentGateway);

        when(suiteRepository.findById(anyString())).thenReturn(Optional.empty());
        when(branchRepository.findById(anyString())).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking paidBooking(LocalDate checkIn) {
        return Booking.builder()
                .id("b1")
                .user("u1")
                .suite("suite-1")
                .branch("branch-1")
                .status("pending")
                .checkIn(checkIn)
                .checkOut(checkIn.plus(2, ChronoUnit.DAYS))
                .nights(2)
                .totalPrice(new BigDecimal("1000000"))
                .paidAmount(new BigDecimal("500000"))
                .balanceDue(new BigDecimal("500000"))
                .guaranteeDeposit(new BigDecimal("100000"))
                .paymentStatus("partial")
                .build();
    }

    @Test
    @DisplayName("Cancelación <48h (1 día antes): retención del 100% de la base sin depósito, sin reembolso")
    void testCancelLessThan48HoursRetainsEverything() {
        LocalDate checkIn = LocalDate.now(BOGOTA).plus(1, ChronoUnit.DAYS);
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(paidBooking(checkIn)));

        bookingService.cancelBooking("b1", "u1", "emergencia");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();

        assertEquals("cancelled", saved.getStatus());
        // retentionBase = 1000000 - 100000 = 900000; retention = 100% = 900000
        assertTrue(saved.getRefundedAmount().compareTo(BigDecimal.ZERO) == 0,
                "No debe haber reembolso con retención del 100%");
        assertEquals("partial", saved.getPaymentStatus());
        verify(paymentGateway, never()).refund(any(), any());
    }

    @Test
    @DisplayName("Frontera exacta: cancelar 2 días antes ya cae en el tramo del 10% (no 100%)")
    void testCancelAtExactlyTwoDaysFallsInto10PercentBracket() {
        LocalDate checkIn = LocalDate.now(BOGOTA).plus(2, ChronoUnit.DAYS);
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(paidBooking(checkIn)));

        bookingService.cancelBooking("b1", "u1", "cambio de planes");

        // retention = 10% de 900000 = 90000; refund = 500000 - 90000 = 410000
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();

        assertTrue(saved.getRefundedAmount().compareTo(new BigDecimal("410000")) == 0,
                "Reembolso esperado: 410000 (500000 - 90000), fue: " + saved.getRefundedAmount());
        verify(paymentGateway).refund(eq("tx_b1"), any());
    }

    @Test
    @DisplayName("Frontera exacta: cancelar 7 días antes es gratis (0% retención)")
    void testCancelAtExactlySevenDaysIsFree() {
        LocalDate checkIn = LocalDate.now(BOGOTA).plus(7, ChronoUnit.DAYS);
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(paidBooking(checkIn)));

        bookingService.cancelBooking("b1", "u1", "cambio de planes");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();

        assertTrue(saved.getRefundedAmount().compareTo(new BigDecimal("500000")) == 0,
                "Reembolso esperado: 500000 completo");
        assertEquals("refunded", saved.getPaymentStatus());
        verify(paymentGateway).refund(eq("tx_b1"), any());
    }

    @Test
    @DisplayName("Cancelar reserva impaga: no se llama al gateway ni cambia el paymentStatus")
    void testCancelUnpaidBookingNeverTouchesGateway() {
        LocalDate checkIn = LocalDate.now(BOGOTA).plus(1, ChronoUnit.DAYS);
        Booking booking = paidBooking(checkIn);
        booking.setPaidAmount(BigDecimal.ZERO);
        booking.setPaymentStatus("pending");
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking));

        bookingService.cancelBooking("b1", "u1", "sin pagar");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();

        assertEquals("cancelled", saved.getStatus());
        assertEquals("pending", saved.getPaymentStatus());
        assertTrue(saved.getBalanceDue().compareTo(BigDecimal.ZERO) == 0);
        verify(paymentGateway, never()).refund(any(), any());
    }

    @Test
    @DisplayName("Cancelar una reserva confirmada es una transición válida de la máquina de estados")
    void testCancelConfirmedBookingIsAllowed() {
        LocalDate checkIn = LocalDate.now(BOGOTA).plus(1, ChronoUnit.DAYS);
        Booking booking = paidBooking(checkIn);
        booking.setStatus("confirmed");
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(booking));

        bookingService.cancelBooking("b1", "u1", "cancelación confirmada");

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertEquals("cancelled", captor.getValue().getStatus());
        verify(suiteNightRepository).deleteByBooking("b1");
    }
}
