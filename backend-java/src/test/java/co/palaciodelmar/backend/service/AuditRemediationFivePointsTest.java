package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.model.*;
import co.palaciodelmar.backend.repository.*;
import co.palaciodelmar.backend.webhook.PaymentWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditRemediationFivePointsTest {

    // Repositorios mockeados
    private BookingRepository bookingRepository;
    private SuiteRepository suiteRepository;
    private BranchRepository branchRepository;
    private ExperienceRepository experienceRepository;
    private SuiteNightRepository suiteNightRepository;
    private UserRepository userRepository;
    private PromotionRepository promotionRepository;
    private PaymentRepository paymentRepository;
    private PaymentIntentRepository paymentIntentRepository;

    // Servicios
    private PricingService pricingService;
    private PaymentGateway paymentGateway;
    private PaymentWebhookService paymentWebhookService;
    private EmailService emailService;

    private BookingService bookingService;
    private PaymentService paymentService;
    private SimulatedPaymentGateway simulatedGateway;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        branchRepository = mock(BranchRepository.class);
        experienceRepository = mock(ExperienceRepository.class);
        suiteNightRepository = mock(SuiteNightRepository.class);
        userRepository = mock(UserRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        paymentIntentRepository = mock(PaymentIntentRepository.class);
        paymentGateway = mock(PaymentGateway.class);
        paymentWebhookService = mock(PaymentWebhookService.class);
        emailService = mock(EmailService.class);
        pricingService = mock(PricingService.class);

        bookingService = new BookingService(
                bookingRepository,
                suiteRepository,
                branchRepository,
                experienceRepository,
                suiteNightRepository,
                userRepository,
                promotionRepository,
                pricingService,
                paymentGateway
        );
        bookingService.setPaymentRepository(paymentRepository);

        paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                bookingService,
                paymentGateway,
                emailService,
                userRepository
        );
        paymentService.setPaymentIntentRepository(paymentIntentRepository);

        simulatedGateway = new SimulatedPaymentGateway(
                paymentIntentRepository,
                paymentWebhookService,
                new ObjectMapper().findAndRegisterModules()
        );
        ReflectionTestUtils.setField(simulatedGateway, "webhookSecret", "whsec_test_secret_for_remediation_suite_min32chars");
        ReflectionTestUtils.setField(simulatedGateway, "simulationDelaySeconds", 0);
    }

    // =========================================================================
    // HALLAZGO 1: Modificación con reembolso seguro e idempotente
    // =========================================================================
    @Test
    @DisplayName("H1: Modificación calcula netPaid (paid - refunded) y solo incrementa refundedAmount tras confirmación de pasarela")
    void testFinding1_ModificationSafelyRefundsThroughGateway() {
        String bookingId = "b_mod_1";
        String userId = "user_1";
        LocalDate today = LocalDate.now();

        Suite suite = Suite.builder()
                .id("suite_1")
                .branch("branch_1")
                .basePrice(new BigDecimal("200000"))
                .maxGuests(2)
                .build();

        Booking existing = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("suite_1")
                .checkIn(today.plusDays(10))
                .checkOut(today.plusDays(15))
                .status("confirmed")
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(new BigDecimal("100000")) // netPaid = 400.000
                .processedRefundIds(new ArrayList<>(List.of("re_prev_1")))
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        BookingRequest request = new BookingRequest();
        request.setCheckIn(today.plusDays(10));
        request.setCheckOut(today.plusDays(12)); // Menos noches -> nuevo total menor
        request.setGuests(1);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(existing));
        when(suiteRepository.findById("suite_1")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        when(suiteNightRepository.findByBooking(bookingId)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        // Pricing regresa 300.000 -> netPaid era 400.000 -> refundDiff = 100.000
        PricingService.PricingResult pricingResult = new PricingService.PricingResult(
                new BigDecimal("300000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300000"), Collections.emptyList()
        );
        when(pricingService.calculatePrice(any(), any(), any(), any())).thenReturn(pricingResult);

        // Simulamos pago previo en paymentRepository
        Payment completedPayment = Payment.builder()
                .id("pay_1")
                .booking(bookingId)
                .status("completed")
                .transactionId("ch_tx_real_1")
                .amount(new BigDecimal("500000"))
                .build();
        when(paymentRepository.findByBooking(bookingId)).thenReturn(List.of(completedPayment));

        // Pasarela aprueba el reembolso de 100.000
        when(paymentGateway.refund(eq("ch_tx_real_1"), eq(new BigDecimal("100000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_mod_100", "partially_refunded", null));

        BookingDTO modified = bookingService.modifyBooking(bookingId, userId, request);

        // Verificamos que se llamó a la pasarela con el monto exacto de la diferencia neta
        verify(paymentGateway, times(1)).refund(eq("ch_tx_real_1"), eq(new BigDecimal("100000")), anyString());
        // El nuevo refundedAmount debe ser 100.000 anteriores + 100.000 nuevos = 200.000
        assertEquals(new BigDecimal("200000"), modified.getRefundedAmount());
        assertEquals(BigDecimal.ZERO, existing.getPendingRefundAmount());
        assertTrue(existing.getProcessedRefundIds().contains("re_mod_100"));
    }

    @Test
    @DisplayName("H1: Si pasarela falla, NO incrementa refundedAmount y marca pendingRefundAmount para conciliación")
    void testFinding1_ModificationHandlesGatewayFailureWithoutLyingInRefundedAmount() {
        String bookingId = "b_mod_fail";
        String userId = "user_1";
        LocalDate today = LocalDate.now();

        Suite suite = Suite.builder().id("suite_1").branch("branch_1").basePrice(new BigDecimal("200000")).build();
        Booking existing = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("suite_1")
                .checkIn(today.plusDays(10))
                .checkOut(today.plusDays(15))
                .status("confirmed")
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        BookingRequest request = new BookingRequest();
        request.setCheckIn(today.plusDays(10));
        request.setCheckOut(today.plusDays(12));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(existing));
        when(suiteRepository.findById("suite_1")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        when(suiteNightRepository.findByBooking(bookingId)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        // Nuevo precio: 400.000 -> refundDiff = 100.000
        PricingService.PricingResult pricingResult = new PricingService.PricingResult(
                new BigDecimal("400000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400000"), Collections.emptyList()
        );
        when(pricingService.calculatePrice(any(), any(), any(), any())).thenReturn(pricingResult);

        when(paymentGateway.refund(anyString(), any(), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(false, null, "failed", "Gateway timeout"));

        BookingDTO modified = bookingService.modifyBooking(bookingId, userId, request);

        // refundedAmount DEBE permanecer en 0 (no se miente contablemente)
        assertEquals(BigDecimal.ZERO, modified.getRefundedAmount());
        // Se registra el pendiente para auditoría y conciliación
        assertEquals(new BigDecimal("100000"), existing.getPendingRefundAmount());
    }

    // =========================================================================
    // HALLAZGO 2 Y 5: No persistir token sensible + recuperación segura en arranque
    // =========================================================================
    @Test
    @DisplayName("H5: confirmIntent enmascara el token y NUNCA persiste el valor sensible completo en MongoDB")
    void testFinding5_SimulatedGatewayMasksTokenAndNeverPersistsRawCard() {
        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_token_sec")
                .status("requires_payment_method")
                .amount(new BigDecimal("150000"))
                .build();

        when(paymentIntentRepository.findById("pi_token_sec")).thenReturn(Optional.of(intent));
        ArgumentCaptor<PaymentIntent> captor = ArgumentCaptor.forClass(PaymentIntent.class);

        simulatedGateway.confirmIntent("pi_token_sec", "4111111111110002");

        verify(paymentIntentRepository, atLeastOnce()).save(captor.capture());
        PaymentIntent saved = captor.getValue();

        // El token plano sensible NO está persistido
        assertNull(saved.getPaymentToken());
        // Se preserva la versión enmascarada
        assertEquals("tok_****0002", saved.getMaskedPaymentToken());
        // El resultado de fallo de prueba se evaluó inmediatamente
        assertEquals("FAILED_DECLINED", saved.getSimulationOutcome());
    }

    @Test
    @DisplayName("H2: recoverProcessingIntents rescata intentos huérfanos sin requerir el token sensible original")
    void testFinding2_SimulatedGatewayRecoversOrphanProcessingIntentsOnStartup() {
        PaymentIntent orphanIntent = PaymentIntent.builder()
                .id("pi_orphan_1")
                .status("processing")
                .simulationOutcome("SUCCEEDED")
                .amount(new BigDecimal("250000"))
                .claimExpiresAt(Instant.now().minusSeconds(10)) // Lock vencido
                .build();

        when(paymentIntentRepository.findByStatus("processing")).thenReturn(List.of(orphanIntent));
        when(paymentIntentRepository.findById("pi_orphan_1")).thenReturn(Optional.of(orphanIntent));

        simulatedGateway.recoverProcessingIntents();

        // El worker de simulación debió procesarlo a succeeded usando simulationOutcome
        assertEquals("succeeded", orphanIntent.getStatus());
        assertNotNull(orphanIntent.getTransactionId());
        assertTrue(orphanIntent.getWebhookDelivered());
    }

    @Test
    @DisplayName("H2: SimulatedPaymentGateway soporta múltiples reembolsos parciales sucesivos hasta completar el monto")
    void testFinding2_SupportsMultiplePartialRefundsConsecutively() {
        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_multi_refund")
                .transactionId("ch_multi_refund")
                .amount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("succeeded")
                .build();

        when(paymentIntentRepository.findByTransactionId("ch_multi_refund")).thenReturn(Optional.of(intent));

        // 1. Reembolso parcial de 100.000
        PaymentGateway.PaymentRefundResponse r1 = simulatedGateway.refund("ch_multi_refund", new BigDecimal("100000"));
        assertTrue(r1.success());
        assertEquals("partially_refunded", r1.status());
        assertEquals("partially_refunded", intent.getStatus());
        assertEquals(new BigDecimal("100000"), intent.getRefundedAmount());

        // 2. Segundo reembolso parcial de 200.000
        PaymentGateway.PaymentRefundResponse r2 = simulatedGateway.refund("ch_multi_refund", new BigDecimal("200000"));
        assertTrue(r2.success());
        assertEquals("partially_refunded", r2.status());
        assertEquals("partially_refunded", intent.getStatus());
        assertEquals(new BigDecimal("300000"), intent.getRefundedAmount());

        // 3. Intento de reembolsar más del remanente (remanente = 200.000, pide 250.000)
        PaymentGateway.PaymentRefundResponse r3 = simulatedGateway.refund("ch_multi_refund", new BigDecimal("250000"));
        assertFalse(r3.success());
        assertEquals("Monto de reembolso inválido", r3.errorMessage());

        // 4. Reembolso final del remanente exacto (200.000)
        PaymentGateway.PaymentRefundResponse r4 = simulatedGateway.refund("ch_multi_refund", new BigDecimal("200000"));
        assertTrue(r4.success());
        assertEquals("refunded", r4.status());
        assertEquals("refunded", intent.getStatus());
        assertEquals(new BigDecimal("500000"), intent.getRefundedAmount());
    }

    // =========================================================================
    // HALLAZGO 3: Experiencias independientes sin duplicidad de precio
    // =========================================================================
    @Test
    @DisplayName("H3: createStandaloneExperienceBooking asigna subtotal=0 y add/remove recalculan fielmente sin duplicar")
    void testFinding3_StandaloneExperiencePriceIntegrityOnModify() {
        String bookingId = "b_exp_1";
        String userId = "user_exp";

        Experience expA = Experience.builder().id("exp_A").name("Catamarán").price(new BigDecimal("100000")).build();
        Experience expB = Experience.builder().id("exp_B").name("Snorkel").price(new BigDecimal("50000")).build();

        Booking booking = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("standalone-experience")
                .subtotal(BigDecimal.ZERO) // Hospedaje en 0
                .totalPrice(new BigDecimal("100000"))
                .experiences(new ArrayList<>(List.of(
                        Booking.SelectedExperience.builder().experienceId("exp_A").name("Catamarán").price(new BigDecimal("100000")).quantity(1).build()
                )))
                .status("confirmed")
                .paidAmount(new BigDecimal("100000"))
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(experienceRepository.findById("exp_B")).thenReturn(Optional.of(expB));

        // 1. Agregar experiencia B ($50.000)
        BookingDTO dtoAfterAdd = bookingService.addExperienceToBooking(bookingId, userId, "exp_B");
        // El total debe ser 100.000 + 50.000 = 150.000 (NO se duplica ni se suma subtotal fantasma)
        assertEquals(new BigDecimal("150000"), dtoAfterAdd.getTotalPrice());
        assertEquals(new BigDecimal("150000"), booking.getTotalPrice());

        // 2. Remover experiencia A ($100.000)
        when(paymentGateway.refund(anyString(), any(), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_rm_1", "partially_refunded", null));

        BookingDTO dtoAfterRemove = bookingService.removeExperienceFromBooking(bookingId, userId, "exp_A");
        // El nuevo total debe ser exactamente 50.000
        assertEquals(new BigDecimal("50000"), dtoAfterRemove.getTotalPrice());
    }

    @Test
    @DisplayName("H3: Inmunidad ante reservas legacy donde subtotal contenía basura histórica")
    void testFinding3_LegacyStandaloneExperienceResilience() {
        String bookingId = "b_legacy_exp";
        String userId = "user_legacy";

        Experience expB = Experience.builder().id("exp_B").name("Buceo").price(new BigDecimal("80000")).build();

        // Reserva antigua afectada por el bug anterior (subtotal guardó 150.000 en vez de 0)
        Booking legacyBooking = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("standalone-experience")
                .subtotal(new BigDecimal("150000")) // Basura histórica
                .taxes(new BigDecimal("28500"))
                .discount(new BigDecimal("10000"))
                .totalPrice(new BigDecimal("150000"))
                .experiences(new ArrayList<>())
                .status("confirmed")
                .build();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(legacyBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(experienceRepository.findById("exp_B")).thenReturn(Optional.of(expB));

        BookingDTO result = bookingService.addExperienceToBooking(bookingId, userId, "exp_B");

        // Al detectar suite standalone-experience, ignora la basura del subtotal y calcula exactamente 80.000
        assertEquals(new BigDecimal("80000"), result.getTotalPrice());
    }

    // =========================================================================
    // HALLAZGO 4: clientSecret devuelto es fiel al original (sin inventar datos)
    // =========================================================================
    @Test
    @DisplayName("H4: createPaymentIntent devuelve el clientSecret original auténtico de PaymentIntent en reintentos idempotentes")
    void testFinding4_IdempotentPaymentReturnsAuthenticClientSecret() {
        String idempotencyKey = "idem_key_real_secret";
        String bookingId = "b_pay_idem";
        String userId = "u_idem";
        String realSecret = "pi_998877_secret_super_authentic_token_xyz";

        Payment existingPayment = Payment.builder()
                .id("pay_idem_1")
                .idempotencyKey(idempotencyKey)
                .intentId("pi_998877")
                .booking(bookingId)
                .user(userId)
                .amount(new BigDecimal("450000"))
                .currency("COP")
                .status("requires_payment_method")
                .build();

        PaymentIntent originalIntent = PaymentIntent.builder()
                .id("pi_998877")
                .clientSecret(realSecret)
                .amount(new BigDecimal("450000"))
                .currency("COP")
                .status("requires_payment_method")
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));
        when(paymentIntentRepository.findById("pi_998877")).thenReturn(Optional.of(originalIntent));

        PaymentGateway.PaymentIntentResponse response = paymentService.createPaymentIntent(userId, bookingId, "card", idempotencyKey);

        assertEquals("pi_998877", response.intentId());
        // NO inventa "secret_pi_998877": devuelve el clientSecret auténtico
        assertEquals(realSecret, response.clientSecret());
        assertNotEquals("secret_pi_998877", response.clientSecret());
    }

    @Test
    @DisplayName("H4: Si el PaymentIntent original no existe, lanza ResponseStatusException(HttpStatus.CONFLICT)")
    void testFinding4_MissingIntentThrowsConflictInsteadOfInventingSecret() {
        String idempotencyKey = "idem_key_corrupted";
        String bookingId = "b_pay_corrupted";
        String userId = "u_idem";

        Payment existingPayment = Payment.builder()
                .id("pay_corrupted")
                .idempotencyKey(idempotencyKey)
                .intentId("pi_missing")
                .booking(bookingId)
                .user(userId)
                .build();

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));
        when(paymentIntentRepository.findById("pi_missing")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                paymentService.createPaymentIntent(userId, bookingId, "card", idempotencyKey)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Inconsistencia en clave de idempotencia"));
    }

    // =========================================================================
    // CASOS LÍMITE Y FORTALECIMIENTO ADICIONAL (Copilot Review Verification)
    // =========================================================================
    @Test
    @DisplayName("Copilot Edge 1: SimulatedPaymentGateway.refund es idempotente con idempotencyKey repetida")
    void testRefundIdempotencyKeyReturnsSameRefundIdWithoutDoubleCharging() {
        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_idem_refund")
                .transactionId("ch_idem_refund")
                .amount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("succeeded")
                .refundRecords(new ArrayList<>())
                .build();

        when(paymentIntentRepository.findByTransactionId("ch_idem_refund")).thenReturn(Optional.of(intent));

        String key = "idem_key_ref_unique_123";
        // 1. Primer reembolso
        PaymentGateway.PaymentRefundResponse firstResp = simulatedGateway.refund("ch_idem_refund", new BigDecimal("100000"), key);
        assertTrue(firstResp.success());
        assertEquals("partially_refunded", firstResp.status());
        assertEquals(new BigDecimal("100000"), intent.getRefundedAmount());
        assertEquals(1, intent.getRefundRecords().size());
        String refundId = firstResp.refundId();

        // 2. Segundo intento con MISMA idempotencyKey
        PaymentGateway.PaymentRefundResponse secondResp = simulatedGateway.refund("ch_idem_refund", new BigDecimal("100000"), key);
        assertTrue(secondResp.success());
        assertEquals("partially_refunded", secondResp.status());
        // El refundId devuelto debe ser exactamente el mismo
        assertEquals(refundId, secondResp.refundId());
        // El monto acumulado NO debe duplicarse a 200.000, debe seguir en 100.000
        assertEquals(new BigDecimal("100000"), intent.getRefundedAmount());
        assertEquals(1, intent.getRefundRecords().size());
    }

    @Test
    @DisplayName("Copilot Edge 2: modifyBooking NO ejecuta reembolso si colisión concurrente aborta la reserva")
    void testModifyBookingDoesNotRefundWhenSlotCollisionOccurs() {
        String bookingId = "b_mod_collision";
        String userId = "user_collision";
        LocalDate today = LocalDate.now();

        Suite suite = Suite.builder().id("suite_col").branch("branch_1").basePrice(new BigDecimal("200000")).build();
        Booking existing = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("suite_col")
                .checkIn(today.plusDays(10))
                .checkOut(today.plusDays(15))
                .status("confirmed")
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .build();

        BookingRequest request = new BookingRequest();
        request.setCheckIn(today.plusDays(10));
        request.setCheckOut(today.plusDays(12));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(existing));
        when(suiteRepository.findById("suite_col")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        when(suiteNightRepository.findByBooking(bookingId)).thenReturn(Collections.emptyList());

        // Simulamos que al guardar los suiteNights, ocurre colisión de clave única en MongoDB
        when(suiteNightRepository.save(any(SuiteNight.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("Slot collision"));

        PricingService.PricingResult pricingResult = new PricingService.PricingResult(
                new BigDecimal("300000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300000"), Collections.emptyList()
        );
        when(pricingService.calculatePrice(any(), any(), any(), any())).thenReturn(pricingResult);

        // Debe fallar con IllegalStateException ante colisión
        assertThrows(IllegalStateException.class, () ->
                bookingService.modifyBooking(bookingId, userId, request)
        );

        // JAMÁS debe llamar a la pasarela para reembolsar dinero en una modificación abortada
        verify(paymentGateway, never()).refund(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Copilot Edge 3: netPaid evita marcar como 'paid' una reserva con saldo pendiente tras reembolso parcial previo")
    void testNetPaidPreventsPrematurePaidStatusOnPartiallyRefundedBooking() {
        String bookingId = "b_partial_netpaid";
        String userId = "user_netpaid";
        LocalDate today = LocalDate.now();

        Suite suite = Suite.builder().id("suite_net").branch("branch_1").basePrice(new BigDecimal("200000")).build();
        // Reserva pagó 500.000, pero ya se le reembolsaron 200.000 -> netPaid = 300.000
        Booking existing = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("suite_net")
                .checkIn(today.plusDays(10))
                .checkOut(today.plusDays(15))
                .status("confirmed")
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(new BigDecimal("200000"))
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        BookingRequest request = new BookingRequest();
        request.setCheckIn(today.plusDays(10));
        request.setCheckOut(today.plusDays(14));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(existing));
        when(suiteRepository.findById("suite_net")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        when(suiteNightRepository.findByBooking(bookingId)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        // Nuevo precio es 350.000. Si se comparara contra paidAmount (500.000), daría "paid".
        // Pero netPaid es 300.000 < 350.000, por lo que falta pagar 50.000.
        PricingService.PricingResult pricingResult = new PricingService.PricingResult(
                new BigDecimal("350000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("350000"), Collections.emptyList()
        );
        when(pricingService.calculatePrice(any(), any(), any(), any())).thenReturn(pricingResult);

        BookingDTO modified = bookingService.modifyBooking(bookingId, userId, request);

        // paymentStatus debe ser "partial", NO "paid"
        assertEquals("partial", modified.getPaymentStatus());
        assertEquals(new BigDecimal("50000"), modified.getBalanceDue());
        // No hay reembolso que procesar
        verify(paymentGateway, never()).refund(anyString(), any(), anyString());
    }
}
