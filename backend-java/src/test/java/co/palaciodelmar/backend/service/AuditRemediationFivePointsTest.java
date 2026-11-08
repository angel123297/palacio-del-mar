package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.model.*;
import co.palaciodelmar.backend.repository.*;
import co.palaciodelmar.backend.webhook.PaymentWebhookPayload;
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

    @Test
    @DisplayName("Copilot Edge 4: Reintento idempotente preserva el estado original de cada operación")
    void testRefundIdempotencyKeyPreservesOriginalOperationStatusAcrossMultipleRefunds() {
        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_idem_multi")
                .transactionId("ch_idem_multi")
                .amount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("succeeded")
                .refundRecords(new ArrayList<>())
                .build();

        when(paymentIntentRepository.findByTransactionId("ch_idem_multi")).thenReturn(Optional.of(intent));

        String keyA = "idem_key_part_A";
        String keyB = "idem_key_full_B";

        // 1. Operación A: Reembolso parcial de 100.000
        PaymentGateway.PaymentRefundResponse respA = simulatedGateway.refund("ch_idem_multi", new BigDecimal("100000"), keyA);
        assertTrue(respA.success());
        assertEquals("partially_refunded", respA.status());

        // 2. Operación B: Reembolso restante de 400.000 (completa el 100% de la transacción)
        PaymentGateway.PaymentRefundResponse respB = simulatedGateway.refund("ch_idem_multi", new BigDecimal("400000"), keyB);
        assertTrue(respB.success());
        assertEquals("refunded", respB.status());
        assertEquals(new BigDecimal("500000"), intent.getRefundedAmount());

        // 3. Reintento de la Clave A: debe devolver "partially_refunded" (su estado original), NO "refunded"
        PaymentGateway.PaymentRefundResponse retryA = simulatedGateway.refund("ch_idem_multi", new BigDecimal("100000"), keyA);
        assertTrue(retryA.success());
        assertEquals("partially_refunded", retryA.status());
        assertEquals(respA.refundId(), retryA.refundId());

        // 4. Reintento de la Clave B: debe devolver "refunded"
        PaymentGateway.PaymentRefundResponse retryB = simulatedGateway.refund("ch_idem_multi", new BigDecimal("400000"), keyB);
        assertTrue(retryB.success());
        assertEquals("refunded", retryB.status());
        assertEquals(respB.refundId(), retryB.refundId());
    }

    @Test
    @DisplayName("Copilot Edge 5: Reutilizar misma clave de reembolso con monto distinto falla con error explícito")
    void testRefundIdempotencyKeyWithMismatchedAmountFails() {
        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_idem_mismatch")
                .transactionId("ch_idem_mismatch")
                .amount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("succeeded")
                .refundRecords(new ArrayList<>())
                .build();

        when(paymentIntentRepository.findByTransactionId("ch_idem_mismatch")).thenReturn(Optional.of(intent));

        String key = "idem_key_same";
        PaymentGateway.PaymentRefundResponse firstResp = simulatedGateway.refund("ch_idem_mismatch", new BigDecimal("100000"), key);
        assertTrue(firstResp.success());

        // Reintento con la misma clave pero diferente monto (200.000 vs 100.000)
        PaymentGateway.PaymentRefundResponse mismatchResp = simulatedGateway.refund("ch_idem_mismatch", new BigDecimal("200000"), key);
        assertFalse(mismatchResp.success());
        assertTrue(mismatchResp.errorMessage().contains("monto diferente"));

        // Reintento con monto nulo
        PaymentGateway.PaymentRefundResponse nullResp = simulatedGateway.refund("ch_idem_mismatch", null, key);
        assertFalse(nullResp.success());
        assertTrue(nullResp.errorMessage().contains("monto diferente"));
    }

    @Test
    @DisplayName("Copilot Edge 6: Intent con webhook no entregado es reintentado tras reinicio del servidor")
    void testUndeliveredWebhookRecoveredOnApplicationReady() {
        PaymentIntent unDeliveredIntent = PaymentIntent.builder()
                .id("pi_undelivered_1")
                .bookingId("b_undelivered_1")
                .amount(new BigDecimal("350000"))
                .currency("COP")
                .status("succeeded")
                .transactionId("ch_undelivered_1")
                .webhookDelivered(false)
                .build();

        when(paymentIntentRepository.findByStatus("processing")).thenReturn(Collections.emptyList());
        when(paymentIntentRepository.findByWebhookDeliveredFalse()).thenReturn(List.of(unDeliveredIntent));
        when(paymentIntentRepository.findById("pi_undelivered_1")).thenReturn(Optional.of(unDeliveredIntent));

        simulatedGateway.recoverProcessingIntents();

        // Debe haber intentado la entrega y marcado webhookDelivered = true
        assertTrue(unDeliveredIntent.getWebhookDelivered());
        verify(paymentIntentRepository, atLeastOnce()).save(unDeliveredIntent);
    }

    // =========================================================================
    // NUEVOS 6 HALLAZGOS PENDIENTES (SEGUNDA RONDA DE AUDITORÍA)
    // =========================================================================

    @Test
    @DisplayName("Pendiente 1: Recuperación periódica (@Scheduled) rescata pagos en processing tras expirar su claimLock sin depender de confirmIntent")
    void testPendingFinding1_PeriodicRecoveryRescuesLockedIntentAfterExpirationWithoutClientRetry() throws Exception {
        // 1. Verificar que recoverProcessingIntents tiene @Scheduled para ejecutarse periódicamente sin intervención del cliente
        java.lang.reflect.Method recoveryMethod = SimulatedPaymentGateway.class.getMethod("recoverProcessingIntents");
        org.springframework.scheduling.annotation.Scheduled scheduledAnnotation =
                recoveryMethod.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertNotNull(scheduledAnnotation, "recoverProcessingIntents debe tener @Scheduled para rescate periódico");

        // 2. Simular un intent creado por otra instancia ("pdm_gw_crashed") hace 10s cuyo lock vence en +50s
        PaymentIntent lockedIntent = PaymentIntent.builder()
                .id("pi_locked_restart")
                .bookingId("b_locked_restart")
                .amount(new BigDecimal("420000"))
                .currency("COP")
                .status("processing")
                .simulationOutcome("SUCCEEDED")
                .claimLock("pdm_gw_crashed_node")
                .claimExpiresAt(Instant.now().plusSeconds(50))
                .build();

        when(paymentIntentRepository.findByStatus("processing")).thenReturn(List.of(lockedIntent));
        when(paymentIntentRepository.findById("pi_locked_restart")).thenReturn(Optional.of(lockedIntent));
        when(paymentIntentRepository.findByWebhookDeliveredFalse()).thenReturn(Collections.emptyList());
        when(paymentIntentRepository.findByRefundRecordsWebhookDeliveredFalse()).thenReturn(Collections.emptyList());

        // Al arrancar a los 10s, el lock aún no ha vencido: no debe romper el lock activo
        simulatedGateway.recoverProcessingIntents();
        assertEquals("processing", lockedIntent.getStatus());
        assertNull(lockedIntent.getTransactionId());

        // 3. El tiempo avanza y el lock expira (sin que el cliente vuelva a llamar a confirmIntent)
        lockedIntent.setClaimExpiresAt(Instant.now().minusSeconds(1));

        // La ejecución periódica de recoverProcessingIntents reclama el intent vencido y lo resuelve
        simulatedGateway.recoverProcessingIntents();

        assertEquals("succeeded", lockedIntent.getStatus());
        assertNotNull(lockedIntent.getTransactionId());
        assertTrue(lockedIntent.getWebhookDelivered());
        assertNull(lockedIntent.getClaimLock());
        assertNull(lockedIntent.getClaimExpiresAt());
    }

    @Test
    @DisplayName("Pendiente 2: Intentos antiguos o corruptos con simulationOutcome nulo o desconocido NUNCA se aprueban por error")
    void testPendingFinding2_LegacyOrNullSimulationOutcomeIsNeverAutoApproved() {
        PaymentIntent legacyNullOutcome = PaymentIntent.builder()
                .id("pi_legacy_null")
                .bookingId("b_legacy_null")
                .amount(new BigDecimal("600000"))
                .currency("COP")
                .status("processing")
                .simulationOutcome(null) // Intento antiguo sin simulationOutcome
                .claimExpiresAt(Instant.now().minusSeconds(5))
                .build();

        PaymentIntent unknownOutcome = PaymentIntent.builder()
                .id("pi_unknown_outcome")
                .bookingId("b_unknown_outcome")
                .amount(new BigDecimal("300000"))
                .currency("COP")
                .status("processing")
                .simulationOutcome("UNKNOWN_STATE")
                .claimExpiresAt(Instant.now().minusSeconds(5))
                .build();

        when(paymentIntentRepository.findByStatus("processing")).thenReturn(List.of(legacyNullOutcome, unknownOutcome));
        when(paymentIntentRepository.findById("pi_legacy_null")).thenReturn(Optional.of(legacyNullOutcome));
        when(paymentIntentRepository.findById("pi_unknown_outcome")).thenReturn(Optional.of(unknownOutcome));
        when(paymentIntentRepository.findByWebhookDeliveredFalse()).thenReturn(Collections.emptyList());
        when(paymentIntentRepository.findByRefundRecordsWebhookDeliveredFalse()).thenReturn(Collections.emptyList());

        simulatedGateway.recoverProcessingIntents();

        // Ninguno de los dos puede aprobarse como succeeded
        assertEquals("failed", legacyNullOutcome.getStatus());
        assertNull(legacyNullOutcome.getTransactionId());
        assertNotNull(legacyNullOutcome.getFailureMessage());
        assertTrue(legacyNullOutcome.getFailureMessage().contains("requiere reconfirmación"));

        assertEquals("failed", unknownOutcome.getStatus());
        assertNull(unknownOutcome.getTransactionId());
        assertNotNull(unknownOutcome.getFailureMessage());
    }

    @Test
    @DisplayName("Pendiente 3: El webhook de reembolso (refund.processed) persiste su estado y se recupera con eventId estable tras una caída")
    void testPendingFinding3_LostRefundWebhookIsPersistedAndRecoveredWithStableEventId() throws Exception {
        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_ref_webhook_loss")
                .bookingId("b_ref_webhook_loss")
                .transactionId("ch_ref_webhook_loss")
                .amount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .currency("COP")
                .status("succeeded")
                .refundRecords(new ArrayList<>())
                .build();

        when(paymentIntentRepository.findByTransactionId("ch_ref_webhook_loss")).thenReturn(Optional.of(intent));
        when(paymentIntentRepository.findById("pi_ref_webhook_loss")).thenReturn(Optional.of(intent));

        // Simulamos que durante el refund inicial el servidor falla al despachar el webhook
        java.util.concurrent.atomic.AtomicBoolean serverDown = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.List<String> capturedEventIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            String evtId = inv.getArgument(2);
            capturedEventIds.add(evtId);
            if (serverDown.get()) {
                throw new RuntimeException("Caída del servidor antes de entregar webhook de reembolso");
            }
            return java.util.Map.of("status", "processed");
        }).when(paymentWebhookService).processWebhook(anyString(), anyString(), anyString(), anyString());

        PaymentGateway.PaymentRefundResponse resp = simulatedGateway.refund(
                "ch_ref_webhook_loss", new BigDecimal("150000"), "idem_ref_loss_1"
        );
        assertTrue(resp.success());
        assertEquals(1, intent.getRefundRecords().size());

        // Ejecutar de forma determinista el intento fallido inicial
        simulatedGateway.deliverRefundWebhook("pi_ref_webhook_loss", resp.refundId());

        PaymentIntent.RefundRecord recordAfterCrash = intent.getRefundRecords().get(0);
        assertNotNull(recordAfterCrash.getWebhookEventId());
        String stableEventId = recordAfterCrash.getWebhookEventId();
        assertFalse(Boolean.TRUE.equals(recordAfterCrash.getWebhookDelivered()),
                "Tras fallar el despacho inicial, webhookDelivered debe permanecer en false");

        // Ahora el servidor se recupera y ejecuta recoverProcessingIntents()
        serverDown.set(false);
        when(paymentIntentRepository.findByStatus("processing")).thenReturn(Collections.emptyList());
        when(paymentIntentRepository.findByWebhookDeliveredFalse()).thenReturn(Collections.emptyList());
        when(paymentIntentRepository.findByRefundRecordsWebhookDeliveredFalse()).thenReturn(List.of(intent));

        simulatedGateway.recoverProcessingIntents();

        assertTrue(Boolean.TRUE.equals(recordAfterCrash.getWebhookDelivered()),
                "La tarea de recuperación debe entregar el webhook de reembolso pendiente y marcarlo como entregado");
        assertTrue(capturedEventIds.stream().allMatch(stableEventId::equals),
                "Todos los intentos de entrega deben usar exactamente el mismo eventId estable: " + capturedEventIds);
    }

    @Test
    @DisplayName("Pendiente 4: Cancelar tras un reembolso parcial calcula la devolución sobre netPaid y preserva reembolsos previos si la pasarela falla")
    void testPendingFinding4_CancelAfterPartialRefundUsesNetPaidAndPreservesPriorRefundOnGatewayFailure() {
        LocalDate checkIn = LocalDate.now().plusDays(10); // >= 7 días -> 0% retención

        // Caso A: Reserva pagó $500.000, ya se reembolsaron $100.000, y al cancelar falla la pasarela
        Booking bookingFailCase = Booking.builder()
                .id("b_cancel_partial_fail")
                .user("u_cancel_1")
                .suite("suite_1")
                .status("confirmed")
                .paymentStatus("paid")
                .checkIn(checkIn)
                .checkOut(checkIn.plusDays(3))
                .totalPrice(new BigDecimal("400000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(new BigDecimal("100000"))
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        when(bookingRepository.findById("b_cancel_partial_fail")).thenReturn(Optional.of(bookingFailCase));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentGateway.refund(eq("tx_b_cancel_partial_fail"), eq(new BigDecimal("400000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(false, null, "failed", "Error de red con banco"));

        BookingDTO cancelledFailDto = bookingService.cancelBooking("b_cancel_partial_fail", "u_cancel_1", "Viaje cancelado");

        // Debe haber solicitado SOLO $400.000 con la clave idempotente generada previamente
        org.mockito.ArgumentCaptor<String> keyCaptorFail = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(paymentGateway).refund(eq("tx_b_cancel_partial_fail"), eq(new BigDecimal("400000")), keyCaptorFail.capture());
        assertEquals(bookingFailCase.getPendingRefundIdempotencyKey(), keyCaptorFail.getValue(),
                "La clave enviada a la pasarela en el primer intento debe ser exactamente la clave persistida como pendiente");

        // Al fallar la pasarela, debe conservar los $100.000 ya reembolsados (JAMÁS borrarlos a 0 ni inventar 500.000)
        assertEquals(new BigDecimal("100000"), cancelledFailDto.getRefundedAmount());
        assertEquals(new BigDecimal("100000"), bookingFailCase.getRefundedAmount());
        assertEquals("requires_refund", cancelledFailDto.getPaymentStatus());
        assertEquals(new BigDecimal("400000"), bookingFailCase.getPendingRefundAmount());
        assertNotNull(bookingFailCase.getPendingRefundIdempotencyKey());

        // Caso B: Reserva pagó $500.000, ya se reembolsaron $100.000, y al cancelar la pasarela aprueba los $400.000 restantes
        Booking bookingOkCase = Booking.builder()
                .id("b_cancel_partial_ok")
                .user("u_cancel_1")
                .suite("suite_1")
                .status("confirmed")
                .paymentStatus("paid")
                .checkIn(checkIn)
                .checkOut(checkIn.plusDays(3))
                .totalPrice(new BigDecimal("400000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(new BigDecimal("100000"))
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        when(bookingRepository.findById("b_cancel_partial_ok")).thenReturn(Optional.of(bookingOkCase));
        when(paymentGateway.refund(eq("tx_b_cancel_partial_ok"), eq(new BigDecimal("400000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_canc_400k", "refunded", null));

        BookingDTO cancelledOkDto = bookingService.cancelBooking("b_cancel_partial_ok", "u_cancel_1", "Viaje cancelado");

        verify(paymentGateway).refund(eq("tx_b_cancel_partial_ok"), eq(new BigDecimal("400000")), anyString());
        assertEquals(new BigDecimal("500000"), cancelledOkDto.getRefundedAmount());
        assertEquals("refunded", cancelledOkDto.getPaymentStatus());
        assertTrue(bookingOkCase.getProcessedRefundIds().contains("re_canc_400k"));
    }

    @Test
    @DisplayName("Pendiente 5: Reembolsos simultáneos desde múltiples instancias de pasarela no pueden sobre-reembolsar el saldo")
    void testPendingFinding5_ConcurrentRefundsAcrossTwoGatewayInstancesCannotOverRefund() throws Exception {
        PaymentIntent sharedIntent = PaymentIntent.builder()
                .id("pi_concurrent_nodes")
                .transactionId("ch_concurrent_nodes")
                .amount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("succeeded")
                .refundRecords(new ArrayList<>())
                .build();

        when(paymentIntentRepository.findByTransactionId("ch_concurrent_nodes")).thenReturn(Optional.of(sharedIntent));
        when(paymentIntentRepository.findById("pi_concurrent_nodes")).thenReturn(Optional.of(sharedIntent));

        // Dos instancias separadas de SimulatedPaymentGateway (simulando 2 nodos de backend)
        SimulatedPaymentGateway nodeA = new SimulatedPaymentGateway(paymentIntentRepository, paymentWebhookService, new ObjectMapper());
        SimulatedPaymentGateway nodeB = new SimulatedPaymentGateway(paymentIntentRepository, paymentWebhookService, new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(nodeA, "webhookSecret", "f0e1d2c3b4a5968778695a4b3c2d1e0ff0e1d2c3b4a5968778695a4b3c2d1e0f");
        org.springframework.test.util.ReflectionTestUtils.setField(nodeB, "webhookSecret", "f0e1d2c3b4a5968778695a4b3c2d1e0ff0e1d2c3b4a5968778695a4b3c2d1e0f");

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<PaymentGateway.PaymentRefundResponse> futureA = pool.submit(() -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return nodeA.refund("ch_concurrent_nodes", new BigDecimal("350000"), "key_node_A");
            });
            java.util.concurrent.Future<PaymentGateway.PaymentRefundResponse> futureB = pool.submit(() -> {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                return nodeB.refund("ch_concurrent_nodes", new BigDecimal("350000"), "key_node_B");
            });

            PaymentGateway.PaymentRefundResponse resA = futureA.get(5, java.util.concurrent.TimeUnit.SECONDS);
            PaymentGateway.PaymentRefundResponse resB = futureB.get(5, java.util.concurrent.TimeUnit.SECONDS);

            // Exactamente UNO debe tener éxito ($350.000) y el otro debe rechazarse porque solo quedan $150.000
            assertTrue(resA.success() ^ resB.success(),
                    "Exactamente una instancia debe lograr reembolsar $350.000 de $500.000; la otra debe fallar");
            assertEquals(new BigDecimal("350000"), sharedIntent.getRefundedAmount());
            assertEquals(1, sharedIntent.getRefundRecords().size());
        } finally {
            pool.shutdownNow();
            nodeA.shutdown();
            nodeB.shutdown();
        }
    }

    @Test
    @DisplayName("Pendiente 6: Un reembolso pendiente nunca es sustituido por una clave nueva sin antes conciliar el primero con la pasarela")
    void testPendingFinding6_PendingRefundKeyIsNeverReplacedWithoutReconciliation() {
        String bookingId = "b_pending_reconcile";
        String userId = "u_reconcile";
        LocalDate today = LocalDate.now();

        Suite suite = Suite.builder().id("suite_rec").branch("branch_1").basePrice(new BigDecimal("100000")).build();
        // La reserva pagó $500.000. En una primera modificación bajó a $400.000 y quedó pendiente "ref_mod_first_key" por $100.000
        Booking booking = Booking.builder()
                .id(bookingId)
                .user(userId)
                .suite("suite_rec")
                .checkIn(today.plusDays(10))
                .checkOut(today.plusDays(14))
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("400000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .pendingRefundIdempotencyKey("ref_mod_first_key")
                .pendingRefundAmount(new BigDecimal("100000"))
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        BookingRequest secondModRequest = new BookingRequest();
        secondModRequest.setCheckIn(today.plusDays(10));
        secondModRequest.setCheckOut(today.plusDays(13)); // Nuevo precio será $300.000

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(suiteRepository.findById("suite_rec")).thenReturn(Optional.of(suite));
        when(suiteNightRepository.findBySuiteAndDatesBetween(any(), any(), any())).thenReturn(Collections.emptyList());
        when(suiteNightRepository.findByBooking(bookingId)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        PricingService.PricingResult newPricing = new PricingService.PricingResult(
                new BigDecimal("300000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300000"), Collections.emptyList()
        );
        when(pricingService.calculatePrice(any(), any(), any(), any())).thenReturn(newPricing);

        // Sub-caso A: Si la pasarela sigue sin poder conciliar "ref_mod_first_key", bloquea la nueva modificación y NO pisa la clave
        when(paymentGateway.refund("tx_" + bookingId, new BigDecimal("100000"), "ref_mod_first_key"))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(false, null, "failed", "Timeout de red con pasarela"));

        IllegalStateException blockedEx = assertThrows(IllegalStateException.class, () ->
                bookingService.modifyBooking(bookingId, userId, secondModRequest)
        );
        assertTrue(blockedEx.getMessage().contains("ref_mod_first_key"));
        assertEquals("ref_mod_first_key", booking.getPendingRefundIdempotencyKey(),
                "La clave original pendiente jamás debe ser sobrescrita si no se pudo conciliar");
        assertEquals(new BigDecimal("100000"), booking.getPendingRefundAmount());

        // Sub-caso B: Ahora la pasarela confirma "ref_mod_first_key" ($100.000) y luego procesa solo los $100.000 adicionales
        when(paymentGateway.refund(eq("tx_" + bookingId), eq(new BigDecimal("100000")), anyString()))
                .thenAnswer(inv -> {
                    String keyUsed = inv.getArgument(2);
                    if ("ref_mod_first_key".equals(keyUsed)) {
                        return new PaymentGateway.PaymentRefundResponse(true, "re_first_100k", "partially_refunded", null);
                    }
                    return new PaymentGateway.PaymentRefundResponse(true, "re_second_100k", "partially_refunded", null);
                });

        BookingDTO dtoAfterSecondMod = bookingService.modifyBooking(bookingId, userId, secondModRequest);

        // 1. Primero concilió "ref_mod_first_key" por $100.000
        verify(paymentGateway, atLeastOnce()).refund("tx_" + bookingId, new BigDecimal("100000"), "ref_mod_first_key");
        // 2. El total reembolsado acumulado es $100.000 (primera) + $100.000 (segunda) = $200.000
        assertEquals(new BigDecimal("200000"), dtoAfterSecondMod.getRefundedAmount());
        assertEquals(new BigDecimal("300000"), dtoAfterSecondMod.getTotalPrice());
        assertNull(booking.getPendingRefundIdempotencyKey());
        assertEquals(BigDecimal.ZERO, booking.getPendingRefundAmount());
        assertTrue(booking.getProcessedRefundIds().contains("re_first_100k"));
        assertTrue(booking.getProcessedRefundIds().contains("re_second_100k"));
    }

    // =========================================================================
    // NUEVOS 5 HALLAZGOS (TERCERA RONDA: CANCELACIONES Y CONCURRENCIA FINA)
    // =========================================================================

    @Test
    @DisplayName("Ronda 3 - Hallazgo 1: cancelBooking no asume reembolso previo a respuesta y maneja respuesta nula sin mostrar dinero no devuelto")
    void testRound3_CancelBookingHandlesNullGatewayResponseWithoutGhostRefund() {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        Booking booking = Booking.builder()
                .id("b_canc_null_resp")
                .user("u_null_resp")
                .suite("suite_1")
                .status("confirmed")
                .paymentStatus("paid")
                .checkIn(checkIn)
                .checkOut(checkIn.plusDays(2))
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        when(bookingRepository.findById("b_canc_null_resp")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        // Pasarela retorna NULL (ej. timeout de socket o cliente HTTP roto)
        when(paymentGateway.refund(eq("tx_b_canc_null_resp"), any(BigDecimal.class), anyString()))
                .thenReturn(null);

        BookingDTO cancelledDto = bookingService.cancelBooking("b_canc_null_resp", "u_null_resp", "Cancelación por fuerza mayor");

        // El estado de pago debe ser requires_refund y el refundedAmount debe seguir en CERO (sin reembolso fantasma)
        assertEquals("cancelled", cancelledDto.getStatus());
        assertEquals("requires_refund", cancelledDto.getPaymentStatus());
        assertEquals(BigDecimal.ZERO, cancelledDto.getRefundedAmount());
        assertEquals(new BigDecimal("500000"), booking.getPendingRefundAmount());
        assertNotNull(booking.getPendingRefundIdempotencyKey());
    }

    @Test
    @DisplayName("Ronda 3 - Hallazgo 2: cancelBooking genera y usa la clave de idempotencia desde la PRIMERA llamada a la pasarela")
    void testRound3_CancelBookingUsesIdempotencyKeyFromFirstGatewayCall() {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        Booking booking = Booking.builder()
                .id("b_canc_idem_first")
                .user("u_idem_first")
                .suite("suite_1")
                .status("confirmed")
                .paymentStatus("paid")
                .checkIn(checkIn)
                .checkOut(checkIn.plusDays(2))
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .guaranteeDeposit(BigDecimal.ZERO)
                .build();

        when(bookingRepository.findById("b_canc_idem_first")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(paymentGateway.refund(eq("tx_b_canc_idem_first"), eq(new BigDecimal("500000")), keyCaptor.capture()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_canc_first_ok", "refunded", null));

        BookingDTO result = bookingService.cancelBooking("b_canc_idem_first", "u_idem_first", "Cambio de planes");

        assertEquals("refunded", result.getPaymentStatus());
        assertEquals(new BigDecimal("500000"), result.getRefundedAmount());
        // Se verificó que se pasó una clave idempotente válida desde la primera llamada
        String usedKey = keyCaptor.getValue();
        assertNotNull(usedKey);
        assertTrue(usedKey.startsWith("ref_canc_b_canc_idem_first_"));
    }

    @Test
    @DisplayName("Ronda 3 - Hallazgo 3: deliverRefundWebhook usa actualización posicional atómica y no sobrescribe reembolsos concurrentes")
    void testRound3_DeliverRefundWebhookUsesAtomicPositionalUpdateWithoutOverwritingOtherRefunds() {
        org.springframework.data.mongodb.core.MongoTemplate mockMongo =
                mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        simulatedGateway.setMongoTemplate(mockMongo);

        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_webhook_pos")
                .bookingId("b_pos")
                .status("partially_refunded")
                .amount(new BigDecimal("500000"))
                .refundRecords(new ArrayList<>(List.of(
                        PaymentIntent.RefundRecord.builder().refundId("re_1").amount(new BigDecimal("100000")).webhookDelivered(false).build(),
                        PaymentIntent.RefundRecord.builder().refundId("re_2").amount(new BigDecimal("200000")).webhookDelivered(false).build()
                )))
                .build();

        when(paymentIntentRepository.findById("pi_webhook_pos")).thenReturn(Optional.of(intent));

        // Entregar webhook para re_1
        simulatedGateway.deliverRefundWebhook("pi_webhook_pos", "re_1");

        // Verificar que usó mongoTemplate.updateFirst con query posicional refundRecords.$.webhookDelivered
        org.mockito.ArgumentCaptor<org.springframework.data.mongodb.core.query.Query> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);
        org.mockito.ArgumentCaptor<org.springframework.data.mongodb.core.query.Update> updateCaptor =
                org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);

        verify(mockMongo).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(PaymentIntent.class));
        String qStr = queryCaptor.getValue().getQueryObject().toString();
        String uStr = updateCaptor.getValue().getUpdateObject().toString();

        assertTrue(qStr.contains("refundRecords.refundId") && qStr.contains("re_1"),
                "Debe apuntar exactamente al elemento del array en MongoDB: " + qStr);
        assertTrue(uStr.contains("refundRecords.$.webhookDelivered"),
                "Debe usar el operador posicional $ de MongoDB para no sobreescribir el documento completo: " + uStr);

        // Limpiar MongoTemplate para tests posteriores
        simulatedGateway.setMongoTemplate(null);
    }

    @Test
    @DisplayName("Ronda 3 - Hallazgo 4: Deduplicación cruzada entre Payment y Booking protege ante caídas entre ambos guardados")
    void testRound3_WebhookRefundProcessedDeduplicatesPaymentAndBookingIndependentlyAcrossCrashes() {
        String eventId = "evt_crash_recovery_1";
        String refundId = "re_crash_1";
        String intentId = "pi_crash_test";
        String bookingId = "b_crash_test";

        // Simulamos que el Payment YA fue guardado con el refundId en una invocación anterior antes de que el servidor cayera
        co.palaciodelmar.backend.model.Payment paymentAlreadySaved = co.palaciodelmar.backend.model.Payment.builder()
                .id("pay_crash")
                .intentId(intentId)
                .booking(bookingId)
                .amount(new BigDecimal("500000"))
                .refundedAmount(new BigDecimal("150000")) // Ya acreditado en Payment
                .status("partially_refunded")
                .processedRefundIds(new ArrayList<>(List.of(refundId))) // Ya lo tiene
                .build();

        // Pero la reserva aún NO lo tenía porque el servidor cayó antes de guardar la reserva
        Booking bookingPendingCatchup = Booking.builder()
                .id(bookingId)
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO) // Aún en 0
                .processedRefundIds(new ArrayList<>())
                .build();

        co.palaciodelmar.backend.repository.PaymentRepository mockPayRepo =
                mock(co.palaciodelmar.backend.repository.PaymentRepository.class);
        when(mockPayRepo.findByIntentId(intentId)).thenReturn(Optional.of(paymentAlreadySaved));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(bookingPendingCatchup));

        ProcessedWebhookEventRepository mockEventRepo = mock(ProcessedWebhookEventRepository.class);
        when(mockEventRepo.existsByEventId(eventId)).thenReturn(false);

        PaymentWebhookService realWebhookService = new PaymentWebhookService(
                mockPayRepo,
                bookingRepository,
                bookingService,
                mockEventRepo,
                emailService,
                new ObjectMapper().findAndRegisterModules(),
                paymentGateway
        );
        org.springframework.test.util.ReflectionTestUtils.setField(realWebhookService, "webhookSecret", "whsec_test_secret");

        String rawBody = """
                {
                  "eventId": "%s",
                  "eventType": "refund.processed",
                  "timestamp": "%s",
                  "data": {
                    "intentId": "%s",
                    "bookingId": "%s",
                    "amount": 150000,
                    "currency": "COP",
                    "status": "partially_refunded",
                    "transactionId": "%s"
                  }
                }
                """.formatted(eventId, Instant.now().toString(), intentId, bookingId, refundId);

        String sig = co.palaciodelmar.backend.webhook.WebhookSigner.sign(rawBody, "whsec_test_secret");
        String ts = String.valueOf(Instant.now().getEpochSecond());

        realWebhookService.processWebhook(rawBody, sig, eventId, ts);

        // Payment NO debe haberse incrementado a 300.000: debe mantenerse exactamente en 150.000
        assertEquals(new BigDecimal("150000"), paymentAlreadySaved.getRefundedAmount());
        // Booking debió alcanzar el estado y registrar los 150.000
        assertEquals(new BigDecimal("150000"), bookingPendingCatchup.getRefundedAmount());
        assertTrue(bookingPendingCatchup.getProcessedRefundIds().contains(refundId));
    }

    @Test
    @DisplayName("Ronda 3 - Hallazgo 5: processIntentSimulation comprueba propiedad del claimLock y aborta si otra instancia tomó el bloqueo")
    void testRound3_ProcessIntentSimulationAbortsIfClaimLockLostOrPreempted() {
        PaymentIntent intentPreempted = PaymentIntent.builder()
                .id("pi_preempted_node")
                .bookingId("b_preempted")
                .amount(new BigDecimal("300000"))
                .currency("COP")
                .status("processing")
                .simulationOutcome("SUCCEEDED")
                .claimLock("pdm_gw_other_instance_999") // Pertenece a otra instancia
                .claimExpiresAt(Instant.now().plusSeconds(40)) // Bloqueo vigente de la otra
                .build();

        when(paymentIntentRepository.findById("pi_preempted_node")).thenReturn(Optional.of(intentPreempted));

        simulatedGateway.processIntentSimulation("pi_preempted_node");

        // La instancia actual no debe tocar ni alterar el intento ajeno
        verify(paymentIntentRepository, never()).save(argThat(pi -> "succeeded".equals(pi.getStatus())));
        assertEquals("processing", intentPreempted.getStatus());
        assertNull(intentPreempted.getTransactionId());
    }
}
