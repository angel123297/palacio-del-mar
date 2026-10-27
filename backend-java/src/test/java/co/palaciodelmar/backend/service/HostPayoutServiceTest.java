package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.HostPayoutDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.HostPayout;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.HostPayoutRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.webhook.WebhookDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HostPayoutServiceTest {

    private BookingRepository bookingRepository;
    private HostPayoutRepository hostPayoutRepository;
    private PropertyRepository propertyRepository;
    private WebhookDispatcher webhookDispatcher;
    private HostPayoutService hostPayoutService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        hostPayoutRepository = mock(HostPayoutRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        webhookDispatcher = mock(WebhookDispatcher.class);

        hostPayoutService = new HostPayoutService(bookingRepository, hostPayoutRepository, propertyRepository);
        ReflectionTestUtils.setField(hostPayoutService, "webhookDispatcher", webhookDispatcher);
    }

    @Test
    @DisplayName("Liquidación automática: Agrupa reservas elegibles, deduce comisión de 8% y emite webhook payout.processed")
    void testProcessAutomatedPayouts() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Bogota"));

        Booking b1 = Booking.builder()
                .id("b1")
                .propertyId("prop-alpha")
                .status("completed")
                .paymentStatus("paid")
                .paidAmount(new BigDecimal("1000000"))
                .subtotal(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("80000"))
                .hostPayout(new BigDecimal("920000"))
                .checkIn(today.minusDays(2))
                .checkOut(today.plusDays(1))
                .build();

        Booking b2 = Booking.builder()
                .id("b2")
                .propertyId("prop-alpha")
                .status("checked_out")
                .paymentStatus("paid")
                .paidAmount(new BigDecimal("500000"))
                .subtotal(new BigDecimal("500000"))
                .platformFee(new BigDecimal("40000"))
                .hostPayout(new BigDecimal("460000"))
                .checkIn(today.minusDays(3))
                .checkOut(today.minusDays(1))
                .build();

        when(bookingRepository.findByStatusIn(anyList())).thenReturn(List.of(b1, b2));
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(b1));
        when(bookingRepository.findById("b2")).thenReturn(Optional.of(b2));

        Property prop = Property.builder()
                .id("prop-alpha")
                .name("Villa del Sol")
                .ownerId("host-123")
                .build();
        when(propertyRepository.findById("prop-alpha")).thenReturn(Optional.of(prop));

        when(hostPayoutRepository.save(any(HostPayout.class))).thenAnswer(inv -> {
            HostPayout p = inv.getArgument(0);
            p.setId("payout-generated-1");
            return p;
        });

        List<HostPayoutDTO> payouts = hostPayoutService.processAutomatedPayouts();

        assertEquals(1, payouts.size());
        HostPayoutDTO dto = payouts.get(0);
        assertEquals("prop-alpha", dto.getPropertyId());
        assertEquals("Villa del Sol", dto.getPropertyName());
        assertEquals("host-123", dto.getOwnerId());
        assertEquals(2, dto.getTotalBookings());
        assertEquals(new BigDecimal("1500000.00"), dto.getGrossTotal());
        assertEquals(new BigDecimal("120000.00"), dto.getPlatformFee()); // 8%
        assertEquals(new BigDecimal("1380000.00"), dto.getNetAmount());   // 92%
        assertEquals("PROCESSED", dto.getStatus());

        // Verificar que las reservas se marcaron con el ID de la liquidación
        verify(bookingRepository, times(2)).save(any(Booking.class));
        assertEquals("payout-generated-1", b1.getPayoutId());
        assertEquals("payout-generated-1", b2.getPayoutId());

        // Verificar despacho de webhook payout.processed
        verify(webhookDispatcher, times(1)).dispatchToProperty(eq("prop-alpha"), eq("payout.processed"), any());
    }

    @Test
    @DisplayName("Liquidación automática: Si no hay reservas pendientes no genera lotes ni dispara webhooks")
    void testNoPayoutsWhenNoEligibleBookings() {
        when(bookingRepository.findByStatusIn(anyList())).thenReturn(List.of());

        List<HostPayoutDTO> payouts = hostPayoutService.processAutomatedPayouts();

        assertTrue(payouts.isEmpty());
        verify(hostPayoutRepository, never()).save(any());
        verify(webhookDispatcher, never()).dispatchToProperty(any(), any(), any());
    }

    @Test
    @DisplayName("Anti-doble liquidación: Si una reserva ya tiene payoutId o no está finalizada no se vuelve a liquidar")
    void testAvoidDoublePayoutForAlreadyLiquidatedOrActiveBookings() {
        when(bookingRepository.findByStatusIn(anyList())).thenReturn(List.of());

        List<HostPayoutDTO> payouts = hostPayoutService.processAutomatedPayouts();
        assertTrue(payouts.isEmpty());
        verify(hostPayoutRepository, never()).save(any());
    }

    @Test
    @DisplayName("A-5: Si falla el marcado de una reserva en el lote, el payout se reconcilia solo con las reservas efectivamente marcadas")
    void testPartialBookingSaveFailureReconcilesPayoutPreventingDoubleSettlement() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Bogota"));

        Booking b1 = Booking.builder()
                .id("b1")
                .propertyId("prop-alpha")
                .status("completed")
                .paidAmount(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("80000"))
                .hostPayout(new BigDecimal("920000"))
                .checkIn(today.minusDays(2))
                .checkOut(today.minusDays(1))
                .build();

        Booking b2 = Booking.builder()
                .id("b2")
                .propertyId("prop-alpha")
                .status("checked_out")
                .paidAmount(new BigDecimal("500000"))
                .platformFee(new BigDecimal("40000"))
                .hostPayout(new BigDecimal("460000"))
                .checkIn(today.minusDays(3))
                .checkOut(today.minusDays(1))
                .build();

        when(bookingRepository.findByStatusIn(anyList())).thenReturn(List.of(b1, b2));
        when(bookingRepository.findById("b1")).thenReturn(Optional.of(b1));
        when(bookingRepository.findById("b2")).thenReturn(Optional.of(b2));
        when(propertyRepository.findById("prop-alpha")).thenReturn(Optional.of(
                Property.builder().id("prop-alpha").name("Villa").ownerId("host-1").build()
        ));
        when(hostPayoutRepository.save(any(HostPayout.class))).thenAnswer(inv -> {
            HostPayout p = inv.getArgument(0);
            if (p.getId() == null) p.setId("payout-1");
            return p;
        });
        when(bookingRepository.save(b1)).thenReturn(b1);
        when(bookingRepository.save(b2)).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Conflict on b2"));

        List<HostPayoutDTO> payouts = hostPayoutService.processAutomatedPayouts();

        assertEquals(1, payouts.size());
        HostPayoutDTO dto = payouts.get(0);
        assertEquals(List.of("b1"), dto.getBookingIds());
        assertEquals(1, dto.getTotalBookings());
        assertEquals(new BigDecimal("920000.00"), dto.getNetAmount());
        assertEquals("payout-1", b1.getPayoutId());
        assertNull(b2.getPayoutId());
    }

    @Test
    @DisplayName("A-5: Auto-reparación si una reserva sin payoutId ya figura en un HostPayout previo tras caída abrupta")
    void testSelfHealsBookingAlreadyRecordedInExistingPayout() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Bogota"));
        Booking orphan = Booking.builder()
                .id("b-orphan")
                .propertyId("prop-alpha")
                .status("completed")
                .paidAmount(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("80000"))
                .hostPayout(new BigDecimal("920000"))
                .checkIn(today.minusDays(2))
                .checkOut(today.minusDays(1))
                .build();

        HostPayout existing = HostPayout.builder()
                .id("payout-prev-99")
                .bookingIds(List.of("b-orphan"))
                .status("PROCESSED")
                .build();

        when(bookingRepository.findByStatusIn(anyList())).thenReturn(List.of(orphan));
        when(bookingRepository.findById("b-orphan")).thenReturn(Optional.of(orphan));
        when(hostPayoutRepository.findFirstByBookingIdsContaining("b-orphan")).thenReturn(Optional.of(existing));

        List<HostPayoutDTO> payouts = hostPayoutService.processAutomatedPayouts();

        assertTrue(payouts.isEmpty(), "No debe emitir un segundo payout para una reserva ya incluida en payout-prev-99");
        assertEquals("payout-prev-99", orphan.getPayoutId());
        verify(bookingRepository).save(orphan);
        verify(hostPayoutRepository, never()).save(any());
    }

    @Test
    @DisplayName("A-9: Liquidación captura datos bancarios enmascarados del anfitrión si están configurados")
    void testProcessAutomatedPayoutsCapturesHostBankAccount() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Bogota"));
        co.palaciodelmar.backend.repository.UserRepository userRepo = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        ReflectionTestUtils.setField(hostPayoutService, "userRepository", userRepo);

        co.palaciodelmar.backend.model.User host = co.palaciodelmar.backend.model.User.builder()
                .id("host-99")
                .bankAccount(co.palaciodelmar.backend.model.User.BankAccount.builder()
                        .bankName("Bancolombia")
                        .accountType("Ahorros")
                        .accountNumber("9876543210")
                        .accountNumberMasked("***3210")
                        .holderName("María Anfitriona")
                        .build())
                .build();
        when(userRepo.findById("host-99")).thenReturn(Optional.of(host));

        Booking b1 = Booking.builder()
                .id("b-bank")
                .propertyId("prop-bank")
                .status("completed")
                .paymentStatus("paid")
                .paidAmount(new BigDecimal("1000000"))
                .subtotal(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("80000"))
                .hostPayout(new BigDecimal("920000"))
                .checkIn(today.minusDays(2))
                .checkOut(today.minusDays(1))
                .build();

        when(bookingRepository.findByStatusIn(anyList())).thenReturn(List.of(b1));
        when(bookingRepository.findById("b-bank")).thenReturn(Optional.of(b1));
        when(propertyRepository.findById("prop-bank")).thenReturn(Optional.of(
                Property.builder().id("prop-bank").name("Penthouse").ownerId("host-99").build()
        ));
        when(hostPayoutRepository.save(any(HostPayout.class))).thenAnswer(inv -> {
            HostPayout p = inv.getArgument(0);
            if (p.getId() == null) p.setId("payout-bank-1");
            return p;
        });
        when(bookingRepository.save(b1)).thenReturn(b1);

        List<HostPayoutDTO> payouts = hostPayoutService.processAutomatedPayouts();

        assertEquals(1, payouts.size());
        HostPayoutDTO dto = payouts.get(0);
        assertEquals("Bancolombia", dto.getDestinationBank());
        assertEquals("Ahorros", dto.getDestinationAccountType());
        assertEquals("***3210", dto.getDestinationAccountMasked());
        assertEquals("María Anfitriona", dto.getDestinationHolder());
    }
}
