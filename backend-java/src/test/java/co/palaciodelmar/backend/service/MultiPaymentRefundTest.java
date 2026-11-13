package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MultiPaymentRefundTest {

    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private SuiteNightRepository suiteNightRepository;
    private PaymentGateway paymentGateway;
    private BookingService bookingService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        paymentGateway = mock(PaymentGateway.class);

        bookingService = new BookingService(
                bookingRepository,
                null,
                null,
                null,
                suiteNightRepository,
                null,
                null,
                null,
                paymentGateway
        );
        bookingService.setPaymentRepository(paymentRepository);

        adminService = new AdminService(
                bookingRepository,
                null,
                null,
                null,
                suiteNightRepository,
                bookingService,
                null,
                paymentRepository,
                paymentGateway
        );

        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("R5: Reembolso secuencial consume múltiples pagos reales en orden")
    void testSequentialRefundAcrossMultiplePayments() {
        Booking booking = Booking.builder()
                .id("b-multi-ref")
                .status("cancelled")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .build();

        Payment payment1 = Payment.builder()
                .id("pay-inst-1")
                .booking("b-multi-ref")
                .amount(new BigDecimal("300000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .transactionId("ch_tx_inst_1")
                .build();

        Payment payment2 = Payment.builder()
                .id("pay-inst-2")
                .booking("b-multi-ref")
                .amount(new BigDecimal("200000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .transactionId("ch_tx_inst_2")
                .build();

        when(paymentRepository.findByBooking("b-multi-ref")).thenReturn(List.of(payment1, payment2));
        when(paymentGateway.refund(eq("ch_tx_inst_1"), eq(new BigDecimal("300000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_inst_1", "refunded", null));
        when(paymentGateway.refund(eq("ch_tx_inst_2"), eq(new BigDecimal("150000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_inst_2", "refunded", null));

        // Request refund of 450,000 (300,000 from payment1, 150,000 from payment2)
        boolean allProcessed = bookingService.executeSequentialRefund(booking, new BigDecimal("450000"), "idem-seq-ref");

        assertTrue(allProcessed, "El reembolso secuencial de 450.000 debe completarse con éxito");

        // Verify Payment 1 was fully refunded
        assertEquals(0, new BigDecimal("300000").compareTo(payment1.getRefundedAmount()));
        assertEquals("refunded", payment1.getStatus());

        // Verify Payment 2 was partially refunded
        assertEquals(0, new BigDecimal("150000").compareTo(payment2.getRefundedAmount()));
        assertEquals("partially_refunded", payment2.getStatus());

        // Verify Booking totals and status
        assertEquals(0, new BigDecimal("450000").compareTo(booking.getRefundedAmount()));
        assertEquals("partial", booking.getPaymentStatus());

        // Verify calls to gateway in order with real transaction IDs
        InOrder inOrder = inOrder(paymentGateway);
        inOrder.verify(paymentGateway).refund(eq("ch_tx_inst_1"), eq(new BigDecimal("300000")), anyString());
        inOrder.verify(paymentGateway).refund(eq("ch_tx_inst_2"), eq(new BigDecimal("150000")), anyString());
    }

    @Test
    @DisplayName("R5: Fallo parcial de pasarela en reembolso secuencial marca requires_refund y pendingRefundAmount")
    void testSequentialRefundWithPartialFailureMarksPendingRefund() {
        Booking booking = Booking.builder()
                .id("b-multi-fail")
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .build();

        Payment payment1 = Payment.builder()
                .id("pay-fail-1")
                .booking("b-multi-fail")
                .amount(new BigDecimal("300000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .transactionId("ch_tx_fail_1")
                .build();

        Payment payment2 = Payment.builder()
                .id("pay-fail-2")
                .booking("b-multi-fail")
                .amount(new BigDecimal("200000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .transactionId("ch_tx_fail_2")
                .build();

        when(paymentRepository.findByBooking("b-multi-fail")).thenReturn(List.of(payment1, payment2));
        when(paymentGateway.refund(eq("ch_tx_fail_1"), eq(new BigDecimal("300000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_ok_1", "refunded", null));
        // Payment 2 fails in gateway
        when(paymentGateway.refund(eq("ch_tx_fail_2"), eq(new BigDecimal("150000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(false, null, "failed", "Pasarela rechaza transacción"));

        boolean allProcessed = bookingService.executeSequentialRefund(booking, new BigDecimal("450000"), "idem-fail-ref");

        assertFalse(allProcessed, "Debe retornar false al fallar una porción del reembolso");
        assertEquals(0, new BigDecimal("300000").compareTo(booking.getRefundedAmount()));
        assertEquals(0, new BigDecimal("150000").compareTo(booking.getPendingRefundAmount()));
        assertEquals("requires_refund", booking.getPaymentStatus());
        assertNotNull(booking.getPendingRefundIdempotencyKey());
    }

    @Test
    @DisplayName("R5: Cancelación administrativa de reserva confirmada con fondos netos activa reembolso secuencial")
    void testAdminCancellationTriggersSequentialRefundForNetPaidFunds() {
        Booking booking = Booking.builder()
                .id("b-admin-cancel")
                .bookingCode("PDM-20261113-AC01")
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("600000"))
                .paidAmount(new BigDecimal("600000"))
                .refundedAmount(BigDecimal.ZERO)
                .build();

        Payment payment = Payment.builder()
                .id("pay-admin-1")
                .booking("b-admin-cancel")
                .amount(new BigDecimal("600000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .transactionId("tx_admin_cancel_600")
                .build();

        when(bookingRepository.findById("b-admin-cancel")).thenReturn(Optional.of(booking));
        when(bookingRepository.findByBookingCode("PDM-20261113-AC01")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking("b-admin-cancel")).thenReturn(List.of(payment));
        when(paymentGateway.refund(eq("tx_admin_cancel_600"), eq(new BigDecimal("600000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_adm_01", "refunded", null));

        adminService.updateBookingStatus("PDM-20261113-AC01", "CANCELLED");

        assertEquals("cancelled", booking.getStatus());
        // Verify sequential refund was triggered for the full 600,000 net paid amount
        assertEquals(0, new BigDecimal("600000").compareTo(booking.getRefundedAmount()));
        verify(paymentGateway).refund(eq("tx_admin_cancel_600"), eq(new BigDecimal("600000")), anyString());
        verify(suiteNightRepository).deleteByBooking("b-admin-cancel");
    }
}
