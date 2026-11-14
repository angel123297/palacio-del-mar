package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.dto.IntegrationRoomRequest;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.model.ApiKey;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingStatus;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditCriticalBugFixesTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private BookingService buildBookingService(
            BookingRepository bookingRepository,
            SuiteRepository suiteRepository,
            SuiteNightRepository suiteNightRepository,
            PaymentRepository paymentRepository,
            PaymentGateway paymentGateway
    ) {
        BookingService service = new BookingService(
                bookingRepository,
                suiteRepository,
                null,
                null,
                suiteNightRepository,
                null,
                null,
                new PricingService(),
                paymentGateway
        );
        if (paymentRepository != null) {
            service.setPaymentRepository(paymentRepository);
        }
        return service;
    }

    @Test
    @DisplayName("1. Check-out automático (1 PM) no aplica recargo tardío ni confisca el depósito")
    void automaticCheckOutDoesNotPenalizeOrConfiscateDeposit() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        LocalDate today = LocalDate.now(BOGOTA);
        Booking booking = Booking.builder()
                .id("b-auto-1")
                .user("u-1")
                .suite("suite-1")
                .status("checked_in")
                .paymentStatus("paid")
                .checkIn(today.minusDays(2))
                .checkOut(today.minusDays(1)) // Incluso después de mediodía o fecha pasada
                .pricePerNight(new BigDecimal("200000"))
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .balanceDue(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .guaranteeDeposit(new BigDecimal("100000"))
                .guaranteeRefunded(false)
                .smartLockPin("4321")
                .build();

        when(bookingRepository.findById("b-auto-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByBooking("b-auto-1")).thenReturn(List.of(
                Payment.builder()
                        .id("pay-1")
                        .booking("b-auto-1")
                        .amount(new BigDecimal("500000"))
                        .refundedAmount(BigDecimal.ZERO)
                        .transactionId("txn-1")
                        .status("completed")
                        .build()
        ));
        when(paymentGateway.refund(anyString(), any(), anyString())).thenReturn(
                new PaymentGateway.PaymentRefundResponse(true, "ref-1", "refunded", null)
        );

        BookingService bookingService = buildBookingService(
                bookingRepository, mock(co.palaciodelmar.backend.repository.SuiteRepository.class), suiteNightRepository, paymentRepository, paymentGateway);

        BookingDTO dto = bookingService.processCheckOut("b-auto-1", "SYSTEM", "Check-out automático", true);

        assertEquals("checked_out", dto.getStatus());
        assertEquals(0, new BigDecimal("500000").compareTo(booking.getTotalPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getBalanceDue()));
        assertTrue(Boolean.TRUE.equals(booking.getGuaranteeRefunded()));
        verify(paymentGateway).refund(eq("txn-1"), argThat(a -> a.compareTo(new BigDecimal("100000")) == 0), anyString());
    }

    @Test
    @DisplayName("2. Confirmar una reserva limpia expiresAt en Booking y SuiteNight")
    void confirmingBookingClearsExpiresAt() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);

        Booking booking = Booking.builder()
                .id("b-conf-1")
                .suite("suite-1")
                .status("pending")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        SuiteNight night = SuiteNight.builder()
                .id("sn-1")
                .suite("suite-1")
                .booking("b-conf-1")
                .status("held")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(suiteNightRepository.findByBooking("b-conf-1")).thenReturn(List.of(night));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService bookingService = buildBookingService(
                bookingRepository, null, suiteNightRepository, null, null);

        bookingService.changeBookingStatus(booking, BookingStatus.CONFIRMED, "PAYMENT", "Pago aprobado");

        assertEquals("confirmed", booking.getStatus());
        assertNull(booking.getExpiresAt());
        assertNull(night.getExpiresAt());
        assertEquals("confirmed", night.getStatus());
        verify(suiteNightRepository).save(night);
    }

    @Test
    @DisplayName("3. modifyBooking rechaza standalone-experience y bloquea cambio de fechas <48h en reservas pagadas/confirmadas")
    void modifyBookingEnforcesStandaloneAndPenaltyWindowGuards() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        BookingService bookingService = buildBookingService(
                bookingRepository, suiteRepository, suiteNightRepository, null, null);

        Booking standalone = Booking.builder()
                .id("b-exp-1")
                .user("u-1")
                .suite("standalone-experience")
                .status("confirmed")
                .checkIn(LocalDate.now(BOGOTA).plusDays(5))
                .checkOut(LocalDate.now(BOGOTA).plusDays(5))
                .build();
        when(bookingRepository.findById("b-exp-1")).thenReturn(Optional.of(standalone));

        BookingRequest req = BookingRequest.builder()
                .checkIn(LocalDate.now(BOGOTA).plusDays(6))
                .checkOut(LocalDate.now(BOGOTA).plusDays(7))
                .guests(1)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.modifyBooking("b-exp-1", "u-1", req));

        LocalDate tomorrow = LocalDate.now(BOGOTA).plusDays(1);
        Suite suite = Suite.builder()
                .id("suite-1")
                .name("Suite Sol")
                .basePrice(new BigDecimal("300000"))
                .maxGuests(4)
                .totalUnits(2)
                .build();
        Booking nearConfirmed = Booking.builder()
                .id("b-near-1")
                .user("u-1")
                .suite("suite-1")
                .status("confirmed")
                .paidAmount(new BigDecimal("300000"))
                .checkIn(tomorrow)
                .checkOut(tomorrow.plusDays(2))
                .totalPrice(new BigDecimal("700000"))
                .build();

        when(bookingRepository.findById("b-near-1")).thenReturn(Optional.of(nearConfirmed));
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));

        BookingRequest shiftReq = BookingRequest.builder()
                .checkIn(tomorrow.plusDays(4))
                .checkOut(tomorrow.plusDays(6))
                .guests(2)
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.modifyBooking("b-near-1", "u-1", shiftReq));
    }

    @Test
    @DisplayName("4. removeExperienceFromBooking bloquea checked_in, última experiencia standalone y reembolso <48h")
    void removeExperienceFromBookingEnforcesSafetyGuards() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        BookingService bookingService = buildBookingService(
                bookingRepository, null, null, null, paymentGateway);

        Booking checkedInBooking = Booking.builder()
                .id("b-ci-1")
                .user("u-1")
                .suite("suite-1")
                .status("checked_in")
                .experiences(List.of(Booking.SelectedExperience.builder().experienceId("exp-1").name("Tour").price(new BigDecimal("100000")).quantity(1).build()))
                .build();
        when(bookingRepository.findById("b-ci-1")).thenReturn(Optional.of(checkedInBooking));
        assertThrows(IllegalStateException.class,
                () -> bookingService.removeExperienceFromBooking("b-ci-1", "u-1", "exp-1"));

        Booking standaloneBooking = Booking.builder()
                .id("b-st-1")
                .user("u-1")
                .suite("standalone-experience")
                .status("confirmed")
                .checkIn(LocalDate.now(BOGOTA).plusDays(5))
                .experiences(List.of(Booking.SelectedExperience.builder().experienceId("exp-1").name("Yate").price(new BigDecimal("200000")).quantity(1).build()))
                .build();
        when(bookingRepository.findById("b-st-1")).thenReturn(Optional.of(standaloneBooking));
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.removeExperienceFromBooking("b-st-1", "u-1", "exp-1"));

        Booking nearPaidBooking = Booking.builder()
                .id("b-near-exp")
                .user("u-1")
                .suite("suite-1")
                .status("confirmed")
                .checkIn(LocalDate.now(BOGOTA).plusDays(1))
                .subtotal(new BigDecimal("200000"))
                .taxes(new BigDecimal("38000"))
                .discount(BigDecimal.ZERO)
                .totalPrice(new BigDecimal("338000"))
                .paidAmount(new BigDecimal("338000"))
                .experiences(List.of(Booking.SelectedExperience.builder().experienceId("exp-1").name("Spa").price(new BigDecimal("100000")).quantity(1).build()))
                .build();
        when(bookingRepository.findById("b-near-exp")).thenReturn(Optional.of(nearPaidBooking));
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.removeExperienceFromBooking("b-near-exp", "u-1", "exp-1"));
    }

    @Test
    @DisplayName("5. IntegrationService.upsertRoom asigna branch=propertyId y bloquea reducir totalUnits si hay unidades ocupadas")
    void integrationUpsertRoomSetsBranchAndGuardsUnitReduction() {
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        IntegrationService integrationService = new IntegrationService(suiteRepository, suiteNightRepository);

        ApiKey apiKey = ApiKey.builder().id("k1").propertyId("prop-99").ownerId("h1").active(true).build();
        when(suiteRepository.findByPropertyIdAndExternalId("prop-99", "EXT-1")).thenReturn(Optional.empty());
        when(suiteRepository.save(any(Suite.class))).thenAnswer(i -> i.getArgument(0));

        Suite created = integrationService.upsertRoom(apiKey, IntegrationRoomRequest.builder()
                .externalId("EXT-1")
                .name("Suite PMS")
                .basePrice(new BigDecimal("400000"))
                .totalUnits(3)
                .build());
        assertEquals("prop-99", created.getBranch());

        created.setId("suite-pms-1");
        when(suiteRepository.findByPropertyIdAndExternalId("prop-99", "EXT-1")).thenReturn(Optional.of(created));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-pms-1"), any(), any())).thenReturn(List.of(
                SuiteNight.builder()
                        .id("sn-3")
                        .suite("suite-pms-1")
                        .status("confirmed")
                        .unitSlot(3)
                        .dates(List.of(LocalDate.now(BOGOTA).plusDays(2)))
                        .build()
        ));

        IntegrationRoomRequest shrinkReq = IntegrationRoomRequest.builder()
                .externalId("EXT-1")
                .name("Suite PMS")
                .basePrice(new BigDecimal("400000"))
                .totalUnits(2)
                .build();
        assertThrows(IllegalStateException.class, () -> integrationService.upsertRoom(apiKey, shrinkReq));
    }

    @Test
    @DisplayName("6. AdminService expone y concilia reembolsos pendientes a nivel Booking (prefijo booking:)")
    void adminServiceSurfacesAndResolvesBookingPendingRefunds() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);

        Booking bookingWithPending = Booking.builder()
                .id("b-pending-ref")
                .user("u-1")
                .bookingCode("PDM-999")
                .status("cancelled")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(new BigDecimal("300000"))
                .refundedAmount(new BigDecimal("100000"))
                .pendingRefundAmount(new BigDecimal("200000"))
                .pendingRefundIdempotencyKey("ref_mod_123")
                .paymentStatus("requires_refund")
                .build();

        when(paymentRepository.findByStatus("requires_refund")).thenReturn(List.of());
        when(bookingRepository.findByPendingRefundIdempotencyKeyIsNotNull()).thenReturn(List.of(bookingWithPending));
        when(bookingRepository.findById("b-pending-ref")).thenReturn(Optional.of(bookingWithPending));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        AdminService adminService = new AdminService(
                bookingRepository, null, null, null, null, null, null, paymentRepository, null);

        List<Payment> pending = adminService.getPendingRefunds();
        assertEquals(1, pending.size());
        assertEquals("booking:b-pending-ref", pending.get(0).getId());
        assertEquals(0, new BigDecimal("200000").compareTo(pending.get(0).getAmount()));

        Payment resolved = adminService.resolveRefund("booking:b-pending-ref", "complete", "Transferencia manual");
        assertEquals("refunded", resolved.getStatus());
        assertEquals(0, new BigDecimal("300000").compareTo(bookingWithPending.getRefundedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(bookingWithPending.getPendingRefundAmount()));
        assertNull(bookingWithPending.getPendingRefundIdempotencyKey());
        assertEquals("refunded", bookingWithPending.getPaymentStatus());
    }

    @Test
    @DisplayName("7. PropertyService.updatePropertyStatus persiste rejectionReason al rechazar y lo limpia al aprobar")
    void propertyServicePersistsAndClearsRejectionReason() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        SuiteRepository suiteRepository = mock(SuiteRepository.class);

        Property property = Property.builder()
                .id("prop-mod-1")
                .ownerId("host-1")
                .name("Casa Cartagena")
                .status("PENDING")
                .build();

        when(propertyRepository.findById("prop-mod-1")).thenReturn(Optional.of(property));
        when(propertyRepository.save(any(Property.class))).thenAnswer(i -> i.getArgument(0));
        when(suiteRepository.findByPropertyId("prop-mod-1")).thenReturn(List.of());

        PropertyService propertyService = new PropertyService(propertyRepository, suiteRepository);

        PropertyDTO rejected = propertyService.updatePropertyStatus("prop-mod-1", "REJECTED", "Faltan fotos de licencia RNT");
        assertEquals("REJECTED", rejected.getStatus());
        assertEquals("Faltan fotos de licencia RNT", rejected.getRejectionReason());

        PropertyDTO approved = propertyService.updatePropertyStatus("prop-mod-1", "APPROVED", null);
        assertEquals("APPROVED", approved.getStatus());
        assertNull(approved.getRejectionReason());
    }

    @Test
    @DisplayName("8. AdminService exige pasarela para acción 'refund' y conserva pendiente parcial / al descartar")
    void adminServiceRequiresGatewayForRefundAndPreservesRemainingPendingRefund() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);

        Booking booking = Booking.builder()
                .id("b-multi-ref")
                .bookingCode("PDM-MREF")
                .status("confirmed")
                .totalPrice(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .pendingRefundAmount(new BigDecimal("300000"))
                .pendingRefundIdempotencyKey("ref_key_300k")
                .paymentStatus("requires_refund")
                .build();

        Payment payPartial = Payment.builder()
                .id("pay-part-1")
                .booking("b-multi-ref")
                .amount(new BigDecimal("100000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("requires_refund")
                .transactionId("tx_part_1")
                .build();

        Payment payOther = Payment.builder()
                .id("pay-other-2")
                .booking("b-multi-ref")
                .amount(new BigDecimal("50000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("requires_refund")
                .transactionId("tx_other_2")
                .build();

        when(bookingRepository.findById("b-multi-ref")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findById("pay-part-1")).thenReturn(Optional.of(payPartial));
        when(paymentRepository.findById("pay-other-2")).thenReturn(Optional.of(payOther));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        AdminService noGatewayAdmin = new AdminService(
                bookingRepository, null, null, null, null, null, null, paymentRepository, null);
        assertThrows(IllegalStateException.class,
                () -> noGatewayAdmin.resolveRefund("booking:b-multi-ref", "refund", "Sin pasarela"));
        assertThrows(IllegalStateException.class,
                () -> noGatewayAdmin.resolveRefund("pay-part-1", "refund", "Sin pasarela"));

        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.refund("tx_part_1", new BigDecimal("100000")))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_part_1", "refunded", null));

        AdminService withGatewayAdmin = new AdminService(
                bookingRepository, null, null, null, null, null, null, paymentRepository, gateway);

        when(paymentRepository.findByBooking("b-multi-ref")).thenReturn(List.of(payPartial, payOther));
        withGatewayAdmin.resolveRefund("pay-part-1", "refund", "Primer abono");
        assertEquals(0, new BigDecimal("200000").compareTo(booking.getPendingRefundAmount()));
        assertEquals("ref_key_300k", booking.getPendingRefundIdempotencyKey());
        assertEquals("requires_refund", booking.getPaymentStatus());

        payPartial.setStatus("refunded");
        withGatewayAdmin.resolveRefund("pay-other-2", "dismiss", "Pago duplicado no procede");
        assertEquals(0, new BigDecimal("200000").compareTo(booking.getPendingRefundAmount()));
        assertEquals("ref_key_300k", booking.getPendingRefundIdempotencyKey());
        assertEquals("requires_refund", booking.getPaymentStatus());
    }

    @Test
    @DisplayName("9. AuthService bloquea activación pública de cuentas admin sin contraseña")
    void authServiceBlocksPublicRegisterOverwriteOfAdminAccount() {
        co.palaciodelmar.backend.repository.UserRepository userRepository =
                mock(co.palaciodelmar.backend.repository.UserRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
                mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        EmailService emailService = mock(EmailService.class);

        co.palaciodelmar.backend.model.User dormantAdmin = co.palaciodelmar.backend.model.User.builder()
                .id("adm-1")
                .email("admin@palaciodelmar.co")
                .role("admin")
                .password("")
                .build();

        when(userRepository.existsByEmail("admin@palaciodelmar.co")).thenReturn(true);
        when(userRepository.findByEmail("admin@palaciodelmar.co")).thenReturn(Optional.of(dormantAdmin));

        AuthService authService = new AuthService(
                userRepository, passwordEncoder, null, null, emailService, null);

        co.palaciodelmar.backend.dto.RegisterRequest req = co.palaciodelmar.backend.dto.RegisterRequest.builder()
                .name("Intruder")
                .email("admin@palaciodelmar.co")
                .password("Password123!")
                .role("user")
                .build();

        assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("10. Ventana de 48h en BookingService usa hora exacta de check-in en Bogotá (15:00)")
    void bookingService48hWindowUsesBogotaCheckInInstant() {
        BookingService bookingService = buildBookingService(
                mock(BookingRepository.class), null, null, null, null);

        Booking booking = Booking.builder()
                .id("b-tz-48h")
                .checkIn(LocalDate.of(2026, 12, 10))
                .build();

        Instant at47HoursBefore = LocalDate.of(2026, 12, 8)
                .atTime(16, 0).atZone(BOGOTA).toInstant();
        Instant at49HoursBefore = LocalDate.of(2026, 12, 8)
                .atTime(14, 0).atZone(BOGOTA).toInstant();

        assertTrue(bookingService.isWithinHoursOfCheckIn(booking, 48, at47HoursBefore));
        assertFalse(bookingService.isWithinHoursOfCheckIn(booking, 48, at49HoursBefore));
    }

    @Test
    @DisplayName("11. IntegrationService.upsertRoom adquiere lock suite:<id> al actualizar habitación existente")
    void integrationServiceUpsertRoomAcquiresSuiteLockOnUpdate() {
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        RedisLockService redisLockService = mock(RedisLockService.class);

        when(redisLockService.executeWithLock(anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(3)).get());

        IntegrationService integrationService = new IntegrationService(suiteRepository, suiteNightRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(integrationService, "redisLockService", redisLockService);

        ApiKey apiKey = ApiKey.builder().id("k-1").propertyId("prop-lock-1").active(true).build();
        Suite existing = Suite.builder()
                .id("suite-locked-1")
                .propertyId("prop-lock-1")
                .externalId("EXT-99")
                .name("Old Room")
                .totalUnits(3)
                .build();

        when(suiteRepository.findByPropertyIdAndExternalId("prop-lock-1", "EXT-99")).thenReturn(Optional.of(existing));
        when(suiteRepository.findById("suite-locked-1")).thenReturn(Optional.of(existing));
        when(suiteNightRepository.findBySuiteAndDatesBetween(eq("suite-locked-1"), any(), any())).thenReturn(List.of());
        when(suiteRepository.save(any(Suite.class))).thenAnswer(i -> i.getArgument(0));

        IntegrationRoomRequest req = IntegrationRoomRequest.builder()
                .externalId("EXT-99")
                .name("Updated Room")
                .basePrice(new BigDecimal("400000"))
                .totalUnits(2)
                .build();

        Suite saved = integrationService.upsertRoom(apiKey, req);
        assertEquals(2, saved.getTotalUnits());
        verify(redisLockService).executeWithLock(eq("integration:room:prop-lock-1:EXT-99"), any(), any(), any());
        verify(redisLockService).executeWithLock(eq("suite:suite-locked-1"), any(), any(), any());
    }

    @Test
    @DisplayName("12. ReviewService.getSuiteReviews resuelve suiteName una sola vez evitando consultas N+1")
    void reviewServiceGetSuiteReviewsResolvesSuiteNameOnce() {
        co.palaciodelmar.backend.repository.ReviewRepository reviewRepository =
                mock(co.palaciodelmar.backend.repository.ReviewRepository.class);
        SuiteRepository suiteRepository = mock(SuiteRepository.class);

        List<co.palaciodelmar.backend.model.Review> reviews = List.of(
                co.palaciodelmar.backend.model.Review.builder().id("r1").suite("suite-n1").rating(5).comment("Excelente").approved(true).build(),
                co.palaciodelmar.backend.model.Review.builder().id("r2").suite("suite-n1").rating(4).comment("Muy buena").approved(true).build(),
                co.palaciodelmar.backend.model.Review.builder().id("r3").suite("suite-n1").rating(5).comment("Increíble").approved(true).build()
        );
        when(reviewRepository.findBySuiteAndApprovedTrueOrderByCreatedAtDesc("suite-n1")).thenReturn(reviews);
        when(suiteRepository.findById("suite-n1"))
                .thenReturn(Optional.of(Suite.builder().id("suite-n1").name("Suite Imperial N1").build()));

        ReviewService reviewService = new ReviewService(
                reviewRepository,
                mock(BookingRepository.class),
                mock(co.palaciodelmar.backend.repository.UserRepository.class));
        org.springframework.test.util.ReflectionTestUtils.setField(reviewService, "suiteRepository", suiteRepository);

        List<co.palaciodelmar.backend.dto.ReviewDTO> result = reviewService.getSuiteReviews("suite-n1");
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(dto -> "Suite Imperial N1".equals(dto.getSuiteName())));
        verify(suiteRepository, times(1)).findById("suite-n1");
    }

    @Test
    @DisplayName("13. AdminService registra refundId en booking.processedRefundIds para evitar doble conteo con webhook")
    void adminRefundRegistersRefundIdOnBooking() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        Booking booking = Booking.builder()
                .id("bk-adm-ref-1")
                .status("cancelled")
                .paymentStatus("requires_refund")
                .paidAmount(new BigDecimal("200000"))
                .refundedAmount(BigDecimal.ZERO)
                .pendingRefundAmount(new BigDecimal("200000"))
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment payment = Payment.builder()
                .id("pay-adm-1")
                .booking("bk-adm-ref-1")
                .transactionId("tx-adm-1")
                .amount(new BigDecimal("200000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("requires_refund")
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        when(paymentRepository.findById("pay-adm-1")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-adm-ref-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking("bk-adm-ref-1")).thenReturn(List.of(payment));
        when(paymentGateway.refund("tx-adm-1", new BigDecimal("200000")))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "ref_gw_123", "succeeded", null));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        AdminService adminService = new AdminService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(co.palaciodelmar.backend.repository.UserRepository.class),
                mock(co.palaciodelmar.backend.repository.PromotionRepository.class),
                mock(SuiteNightRepository.class),
                mock(BookingService.class),
                mock(PropertyRepository.class),
                paymentRepository,
                paymentGateway
        );

        adminService.resolveRefund("pay-adm-1", "refund", "OK");

        assertTrue(payment.getProcessedRefundIds().contains("ref_gw_123"));
        assertTrue(booking.getProcessedRefundIds().contains("ref_gw_123"));
        assertEquals(0, new BigDecimal("200000").compareTo(booking.getRefundedAmount()));
    }

    @Test
    @DisplayName("14. Pago posterior al checkout no vuelve a exigir la garantía compensada")
    void postCheckoutPaymentDoesNotReDemandCompensatedGuarantee() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = buildBookingService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(SuiteNightRepository.class),
                mock(PaymentRepository.class),
                null
        );

        Booking booking = Booking.builder()
                .id("bk-comp-1")
                .status("checked_in")
                .paymentStatus("partial")
                .checkIn(LocalDate.now(BOGOTA).minusDays(2))
                .checkOut(LocalDate.now(BOGOTA).plusDays(1))
                .totalPrice(new BigDecimal("750000"))
                .paidAmount(new BigDecimal("600000"))
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("150000"))
                .guaranteeDeposit(new BigDecimal("100000"))
                .guaranteeRefunded(false)
                .guaranteeCompensated(BigDecimal.ZERO)
                .build();

        when(bookingRepository.findById("bk-comp-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        bookingService.processCheckOut("bk-comp-1", "host-1", "Check-out normal", false);

        assertEquals(0, new BigDecimal("100000").compareTo(booking.getGuaranteeCompensated()));
        assertEquals(0, new BigDecimal("50000").compareTo(booking.getBalanceDue()));
        assertTrue(booking.getGuaranteeRefunded());

        // Simular pago posterior de los $50.000 restantes
        BigDecimal newNetPaid = booking.getPaidAmount().add(new BigDecimal("50000"));
        BigDecimal balanceAfter = booking.resolveEffectiveTotalPrice().subtract(newNetPaid).max(BigDecimal.ZERO);
        assertEquals(0, BigDecimal.ZERO.compareTo(balanceAfter));
    }

    @Test
    @DisplayName("15. Garantía no se marca como devuelta si la pasarela falla al reembolsar el excedente")
    void guaranteeNotMarkedRefundedWhenGatewayFails() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        BookingService bookingService = buildBookingService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(SuiteNightRepository.class),
                paymentRepository,
                paymentGateway
        );

        Booking booking = Booking.builder()
                .id("bk-dep-fail-1")
                .status("checked_in")
                .paymentStatus("paid")
                .checkIn(LocalDate.now(BOGOTA).minusDays(2))
                .checkOut(LocalDate.now(BOGOTA).plusDays(1))
                .totalPrice(new BigDecimal("600000"))
                .paidAmount(new BigDecimal("600000"))
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO)
                .guaranteeDeposit(new BigDecimal("100000"))
                .guaranteeRefunded(false)
                .build();

        Payment pay = Payment.builder()
                .id("pay-dep-1")
                .booking("bk-dep-fail-1")
                .transactionId("tx-dep-1")
                .amount(new BigDecimal("600000"))
                .refundedAmount(BigDecimal.ZERO)
                .status("completed")
                .build();

        when(bookingRepository.findById("bk-dep-fail-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking("bk-dep-fail-1")).thenReturn(List.of(pay));
        when(paymentGateway.refund(eq("tx-dep-1"), eq(new BigDecimal("100000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(false, null, "failed", "Gateway down"));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        bookingService.processCheckOut("bk-dep-fail-1", "host-1", "Check-out", false);

        assertFalse(Boolean.TRUE.equals(booking.getGuaranteeRefunded()));
        assertEquals("requires_refund", booking.getPaymentStatus());
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getPendingRefundAmount()));
    }

    @Test
    @DisplayName("16. executeSequentialRefund preserva pendingRefundAmount tras el primer pago si el segundo falla")
    void sequentialRefundPreservesRemainingPendingAfterPartialSuccess() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        BookingService bookingService = buildBookingService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(SuiteNightRepository.class),
                paymentRepository,
                paymentGateway
        );

        Booking booking = Booking.builder()
                .id("bk-seq-1")
                .status("cancelled")
                .paymentStatus("paid")
                .paidAmount(new BigDecimal("200000"))
                .refundedAmount(BigDecimal.ZERO)
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment p1 = Payment.builder()
                .id("p1").booking("bk-seq-1").transactionId("tx-1")
                .amount(new BigDecimal("100000")).refundedAmount(BigDecimal.ZERO)
                .status("completed").createdAt(Instant.now().minusSeconds(60)).build();
        Payment p2 = Payment.builder()
                .id("p2").booking("bk-seq-1").transactionId("tx-2")
                .amount(new BigDecimal("100000")).refundedAmount(BigDecimal.ZERO)
                .status("completed").createdAt(Instant.now()).build();

        when(paymentRepository.findByBooking("bk-seq-1")).thenReturn(List.of(p1, p2));
        when(paymentGateway.refund(eq("tx-1"), eq(new BigDecimal("100000")), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "ref-1", "succeeded", null));
        when(paymentGateway.refund(eq("tx-2"), eq(new BigDecimal("50000")), anyString()))
                .thenThrow(new RuntimeException("Connection reset"));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = bookingService.executeSequentialRefund(booking, new BigDecimal("150000"), "ref_seq_1");

        assertFalse(ok);
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getRefundedAmount()));
        assertEquals(0, new BigDecimal("50000").compareTo(booking.getPendingRefundAmount()));
        assertEquals("ref_seq_1", booking.getPendingRefundIdempotencyKey());
        assertEquals("requires_refund", booking.getPaymentStatus());
    }

    @Test
    @DisplayName("17. HostPayoutService recupera liquidaciones atascadas en estado PENDING")
    void hostPayoutServiceRecoversPendingPayouts() {
        co.palaciodelmar.backend.repository.HostPayoutRepository payoutRepo =
                mock(co.palaciodelmar.backend.repository.HostPayoutRepository.class);
        BookingRepository bookingRepo = mock(BookingRepository.class);
        PropertyRepository propRepo = mock(PropertyRepository.class);
        co.palaciodelmar.backend.repository.UserRepository userRepo =
                mock(co.palaciodelmar.backend.repository.UserRepository.class);

        co.palaciodelmar.backend.model.HostPayout stuck = co.palaciodelmar.backend.model.HostPayout.builder()
                .id("po-stuck-1")
                .payoutCode("PO-20261114-STUCK1")
                .propertyId("prop-1")
                .propertyName("Palacio Centro")
                .ownerId("host-1")
                .status("PENDING")
                .bookingIds(List.of("bk-p1"))
                .build();

        Booking bk = Booking.builder()
                .id("bk-p1")
                .propertyId("prop-1")
                .status("checked_out")
                .subtotal(new BigDecimal("500000"))
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO)
                .platformFee(new BigDecimal("40000"))
                .hostPayout(new BigDecimal("460000"))
                .payoutId("po-stuck-1")
                .checkIn(LocalDate.now(BOGOTA).minusDays(2))
                .checkOut(LocalDate.now(BOGOTA))
                .build();

        when(payoutRepo.findByStatus("PENDING")).thenReturn(List.of(stuck));
        when(bookingRepo.findById("bk-p1")).thenReturn(Optional.of(bk));
        when(bookingRepo.findByStatusIn(any())).thenReturn(List.of());
        when(payoutRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        HostPayoutService payoutService = new HostPayoutService(bookingRepo, payoutRepo, propRepo);
        List<co.palaciodelmar.backend.dto.HostPayoutDTO> result = payoutService.processAutomatedPayouts();

        assertEquals(1, result.size());
        assertEquals("PROCESSED", stuck.getStatus());
        assertNotNull(stuck.getProcessedAt());
        assertEquals(0, new BigDecimal("460000").compareTo(stuck.getNetAmount()));
    }

    @Test
    @DisplayName("18. Conciliar manualmente garantía en Admin no crea deuda inexistente en reserva checked_out")
    void adminManualGuaranteeConciliationDoesNotCreateFakeDebt() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        Booking booking = Booking.builder()
                .id("bk-man-dep-1")
                .status("checked_out")
                .paymentStatus("requires_refund")
                .totalPrice(new BigDecimal("600000"))
                .paidAmount(new BigDecimal("600000"))
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO)
                .guaranteeDeposit(new BigDecimal("100000"))
                .guaranteeCompensated(BigDecimal.ZERO)
                .guaranteeRefunded(false)
                .pendingRefundAmount(new BigDecimal("100000"))
                .pendingRefundIdempotencyKey("ref_dep_bk-man-dep-1")
                .build();

        when(bookingRepository.findById("bk-man-dep-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        AdminService adminService = new AdminService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(co.palaciodelmar.backend.repository.UserRepository.class),
                mock(co.palaciodelmar.backend.repository.PromotionRepository.class),
                mock(SuiteNightRepository.class),
                mock(BookingService.class),
                mock(PropertyRepository.class),
                mock(PaymentRepository.class),
                null
        );

        adminService.resolveRefund("booking:bk-man-dep-1", "mark_refunded", "Conciliado manual");

        assertTrue(Boolean.TRUE.equals(booking.getGuaranteeRefunded()));
        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getBalanceDue()));
        assertEquals("paid", booking.getPaymentStatus());
    }

    @Test
    @DisplayName("19. Recuperar payout PENDING descarta reservas sin fondos netos o con saldo pendiente")
    void recoverPendingPayoutSkipsIneligibleBookings() {
        co.palaciodelmar.backend.repository.HostPayoutRepository payoutRepo =
                mock(co.palaciodelmar.backend.repository.HostPayoutRepository.class);
        BookingRepository bookingRepo = mock(BookingRepository.class);
        PropertyRepository propRepo = mock(PropertyRepository.class);

        co.palaciodelmar.backend.model.HostPayout stuck = co.palaciodelmar.backend.model.HostPayout.builder()
                .id("po-stuck-inelig")
                .payoutCode("PO-20261114-BAD1")
                .propertyId("prop-1")
                .status("PENDING")
                .bookingIds(List.of("bk-bad-1"))
                .build();

        Booking refundedBk = Booking.builder()
                .id("bk-bad-1")
                .propertyId("prop-1")
                .status("checked_out")
                .paidAmount(new BigDecimal("500000"))
                .refundedAmount(new BigDecimal("500000"))
                .balanceDue(BigDecimal.ZERO)
                .payoutId("po-stuck-inelig")
                .checkIn(LocalDate.now(BOGOTA).minusDays(2))
                .checkOut(LocalDate.now(BOGOTA))
                .build();

        when(payoutRepo.findByStatus("PENDING")).thenReturn(List.of(stuck));
        when(bookingRepo.findById("bk-bad-1")).thenReturn(Optional.of(refundedBk));
        when(bookingRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(bookingRepo.findByStatusIn(any())).thenReturn(List.of());

        HostPayoutService payoutService = new HostPayoutService(bookingRepo, payoutRepo, propRepo);
        List<co.palaciodelmar.backend.dto.HostPayoutDTO> result = payoutService.processAutomatedPayouts();

        assertTrue(result.isEmpty());
        assertNull(refundedBk.getPayoutId());
        verify(payoutRepo).delete(stuck);
    }

    @Test
    @DisplayName("20. executeSequentialRefund usa claves de idempotencia estables por payment.id al reintentar")
    void sequentialRefundUsesStablePerPaymentIdempotencyKeysAcrossRetries() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        BookingService bookingService = buildBookingService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(SuiteNightRepository.class),
                paymentRepository,
                paymentGateway
        );

        Booking booking = Booking.builder()
                .id("bk-key-stable-1")
                .status("cancelled")
                .paymentStatus("paid")
                .paidAmount(new BigDecimal("200000"))
                .refundedAmount(BigDecimal.ZERO)
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment p1 = Payment.builder()
                .id("pay-id-A").booking("bk-key-stable-1").transactionId("tx-A")
                .amount(new BigDecimal("100000")).refundedAmount(BigDecimal.ZERO)
                .status("completed").createdAt(Instant.now().minusSeconds(60)).build();
        Payment p2 = Payment.builder()
                .id("pay-id-B").booking("bk-key-stable-1").transactionId("tx-B")
                .amount(new BigDecimal("100000")).refundedAmount(BigDecimal.ZERO)
                .status("completed").createdAt(Instant.now()).build();

        when(paymentRepository.findByBooking("bk-key-stable-1"))
                .thenReturn(List.of(p1, p2));
        when(paymentGateway.refund(eq("tx-A"), eq(new BigDecimal("100000")), eq("ref_stable_pay-id-A")))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "r-A", "succeeded", null));
        when(paymentGateway.refund(eq("tx-B"), eq(new BigDecimal("50000")), eq("ref_stable_pay-id-B")))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(false, null, "failed", "Timeout"))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "r-B", "succeeded", null));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        assertFalse(bookingService.executeSequentialRefund(booking, new BigDecimal("150000"), "ref_stable"));
        p1.setStatus("refunded");
        p1.setRefundedAmount(new BigDecimal("100000"));
        assertTrue(bookingService.executeSequentialRefund(booking, new BigDecimal("50000"), "ref_stable"));
        verify(paymentGateway, times(2)).refund(eq("tx-B"), eq(new BigDecimal("50000")), eq("ref_stable_pay-id-B"));
    }

    @Test
    @DisplayName("21. AuthService.updateProfile usa actualización parcial atómica sin sobrescribir password ni tokens")
    void updateProfileUsesAtomicPartialUpdate() {
        co.palaciodelmar.backend.repository.UserRepository userRepo = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        co.palaciodelmar.backend.model.User existing = co.palaciodelmar.backend.model.User.builder()
                .id("usr-1")
                .email("u@ejemplo.com")
                .name("Viejo")
                .password("old_hash")
                .resetPasswordTokenHash("old_tok")
                .build();
        co.palaciodelmar.backend.model.User updatedFromDb = co.palaciodelmar.backend.model.User.builder()
                .id("usr-1")
                .email("u@ejemplo.com")
                .name("Nuevo")
                .password("new_hash_from_reset")
                .build();

        when(userRepo.findById("usr-1")).thenReturn(Optional.of(existing));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(co.palaciodelmar.backend.model.User.class)))
                .thenReturn(updatedFromDb);

        AuthService authService = new AuthService(
                userRepo,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(co.palaciodelmar.backend.security.JwtService.class),
                mock(org.springframework.security.authentication.AuthenticationManager.class),
                mock(EmailService.class),
                mongoTemplate
        );

        co.palaciodelmar.backend.model.User req = co.palaciodelmar.backend.model.User.builder().name("Nuevo").build();
        var dto = authService.updateProfile("usr-1", req);

        assertEquals("Nuevo", dto.getName());
        verify(mongoTemplate, times(1)).findAndModify(any(), any(), any(), eq(co.palaciodelmar.backend.model.User.class));
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("22. BookingExpiryScheduler expira reservas pending con pago parcial y ejecuta reembolso")
    void expirySchedulerExpiresAndRefundsPartialPaymentBooking() {
        BookingRepository bookingRepo = mock(BookingRepository.class);
        SuiteNightRepository nightRepo = mock(SuiteNightRepository.class);
        BookingService bookingService = mock(BookingService.class);

        Booking partialExpired = Booking.builder()
                .id("bk-part-exp-1")
                .status("pending")
                .paymentStatus("partial")
                .paidAmount(new BigDecimal("150000"))
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("350000"))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        when(bookingRepo.findByStatusAndExpiresAtBefore(eq("pending"), any())).thenReturn(List.of(partialExpired));
        when(bookingRepo.findById("bk-part-exp-1")).thenReturn(Optional.of(partialExpired));
        when(nightRepo.findByBooking("bk-part-exp-1")).thenReturn(List.of());
        when(bookingRepo.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(bookingService.executeSequentialRefund(eq(partialExpired), eq(new BigDecimal("150000")), eq("ref_exp_bk-part-exp-1")))
                .thenReturn(false);

        co.palaciodelmar.backend.scheduler.BookingExpiryScheduler scheduler =
                new co.palaciodelmar.backend.scheduler.BookingExpiryScheduler(bookingRepo, nightRepo, bookingService);
        scheduler.cleanupExpiredBookings();

        verify(bookingService).changeBookingStatus(eq(partialExpired), eq(BookingStatus.EXPIRED), anyString(), anyString());
        verify(bookingService).executeSequentialRefund(eq(partialExpired), eq(new BigDecimal("150000")), eq("ref_exp_bk-part-exp-1"));
        assertEquals("requires_refund", partialExpired.getPaymentStatus());
        assertEquals(0, new BigDecimal("150000").compareTo(partialExpired.getPendingRefundAmount()));
    }

    @Test
    @DisplayName("23. HostController.regeneratePin rechaza reservas canceladas o terminadas")
    void regeneratePinRejectsCancelledOrCheckedOutBookings() {
        BookingRepository bookingRepo = mock(BookingRepository.class);
        PropertyRepository propRepo = mock(PropertyRepository.class);

        co.palaciodelmar.backend.model.User host = co.palaciodelmar.backend.model.User.builder()
                .id("host-1").role("host").email("h@ejemplo.com").build();
        co.palaciodelmar.backend.security.UserPrincipal principal =
                new co.palaciodelmar.backend.security.UserPrincipal(host);

        Property prop = Property.builder().id("prop-1").ownerId("host-1").build();
        Booking cancelled = Booking.builder().id("bk-c1").propertyId("prop-1").status("cancelled").build();
        Booking checkedOut = Booking.builder().id("bk-co1").propertyId("prop-1").status("checked_out").build();

        when(propRepo.findById("prop-1")).thenReturn(Optional.of(prop));
        when(bookingRepo.findById("bk-c1")).thenReturn(Optional.of(cancelled));
        when(bookingRepo.findById("bk-co1")).thenReturn(Optional.of(checkedOut));

        co.palaciodelmar.backend.controller.HostController controller =
                new co.palaciodelmar.backend.controller.HostController(
                        bookingRepo,
                        mock(SuiteRepository.class),
                        mock(BookingService.class),
                        mock(co.palaciodelmar.backend.repository.PromotionRepository.class),
                        propRepo
                );

        var res1 = controller.regeneratePin(principal, "bk-c1");
        var res2 = controller.regeneratePin(principal, "bk-co1");

        assertEquals(400, res1.getStatusCode().value());
        assertEquals(400, res2.getStatusCode().value());
        verify(bookingRepo, never()).save(any());
    }

    @Test
    @DisplayName("24. Pago duplicado reembolsado no se atribuye a la reserva ni omite la llamada a pasarela")
    void duplicateSurplusPaymentRefundDoesNotBypassGatewayRefundForRealPayment() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        BookingService bookingService = buildBookingService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(SuiteNightRepository.class),
                paymentRepository,
                paymentGateway
        );

        Booking booking = Booking.builder()
                .id("bk-dup-1")
                .status("cancelled")
                .paymentStatus("paid")
                .paidAmount(new BigDecimal("100000"))
                .refundedAmount(BigDecimal.ZERO)
                .processedPaymentIds(new java.util.ArrayList<>(List.of("pay-real-1")))
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment realPay = Payment.builder()
                .id("pay-real-1").booking("bk-dup-1").transactionId("tx-real-1")
                .amount(new BigDecimal("100000")).refundedAmount(BigDecimal.ZERO)
                .status("completed").createdAt(Instant.now().minusSeconds(60)).build();

        Payment surplusPay = Payment.builder()
                .id("pay-surplus-2").booking("bk-dup-1").transactionId("tx-surplus-2")
                .amount(new BigDecimal("100000")).refundedAmount(new BigDecimal("100000"))
                .processedRefundIds(new java.util.ArrayList<>(List.of("ref-surplus-2")))
                .status("refunded").createdAt(Instant.now()).build();

        when(paymentRepository.findByBooking("bk-dup-1")).thenReturn(List.of(realPay, surplusPay));
        when(paymentGateway.refund(eq("tx-real-1"), eq(new BigDecimal("100000")), eq("ref_dup_test")))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "ref-real-1", "succeeded", null));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = bookingService.executeSequentialRefund(booking, new BigDecimal("100000"), "ref_dup_test");

        assertTrue(ok);
        verify(paymentGateway, times(1)).refund("tx-real-1", new BigDecimal("100000"), "ref_dup_test");
        assertTrue(booking.getProcessedRefundIds().contains("ref-real-1"));
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getRefundedAmount()));
    }

    @Test
    @DisplayName("25. Reconciliación tras caída conserva refundId en booking.processedRefundIds para evitar doble conteo por webhook")
    void recoveryReconciliationPreservesRefundIdOnBooking() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        BookingService bookingService = buildBookingService(
                bookingRepository,
                mock(SuiteRepository.class),
                mock(SuiteNightRepository.class),
                paymentRepository,
                paymentGateway
        );

        Booking booking = Booking.builder()
                .id("bk-rec-id-1")
                .status("cancelled")
                .paymentStatus("requires_refund")
                .paidAmount(new BigDecimal("100000"))
                .refundedAmount(BigDecimal.ZERO)
                .pendingRefundAmount(new BigDecimal("100000"))
                .processedPaymentIds(new java.util.ArrayList<>(List.of("pay-rec-1")))
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment alreadyUpdatedPay = Payment.builder()
                .id("pay-rec-1").booking("bk-rec-id-1").transactionId("tx-rec-1")
                .amount(new BigDecimal("100000")).refundedAmount(new BigDecimal("100000"))
                .processedRefundIds(new java.util.ArrayList<>(List.of("ref-gw-rec-99")))
                .status("refunded").createdAt(Instant.now()).build();

        when(paymentRepository.findByBooking("bk-rec-id-1")).thenReturn(List.of(alreadyUpdatedPay));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = bookingService.executeSequentialRefund(booking, new BigDecimal("100000"), "ref_rec_key");

        assertTrue(ok);
        verify(paymentGateway, never()).refund(anyString(), any(), anyString());
        assertTrue(booking.getProcessedRefundIds().contains("ref-gw-rec-99"));
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getRefundedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getPendingRefundAmount()));
    }

    @Test
    @DisplayName("26. Recuperación de múltiples reembolsos registra todos los refundId en una sola escritura atómica")
    void multiRefundRecoveryPersistsAllMissingRefundIdsAtomically() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);
        BookingService bookingService = buildBookingService(bookingRepository, suiteRepository, suiteNightRepository, paymentRepository, paymentGateway);
        org.springframework.test.util.ReflectionTestUtils.setField(bookingService, "mongoTemplate", mongoTemplate);

        Booking booking = Booking.builder()
                .id("bk-multi-rec")
                .status("cancelled")
                .paymentStatus("requires_refund")
                .paidAmount(new BigDecimal("200000"))
                .refundedAmount(BigDecimal.ZERO)
                .pendingRefundAmount(new BigDecimal("200000"))
                .pendingRefundIdempotencyKey("ref_multi_key")
                .processedPaymentIds(new java.util.ArrayList<>(List.of("pay-m1", "pay-m2")))
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment p1 = Payment.builder()
                .id("pay-m1").booking("bk-multi-rec").transactionId("tx-m1")
                .amount(new BigDecimal("100000")).refundedAmount(new BigDecimal("100000"))
                .processedRefundIds(new java.util.ArrayList<>(List.of("ref-m1")))
                .status("refunded").createdAt(Instant.now().minusSeconds(10)).build();
        Payment p2 = Payment.builder()
                .id("pay-m2").booking("bk-multi-rec").transactionId("tx-m2")
                .amount(new BigDecimal("100000")).refundedAmount(new BigDecimal("100000"))
                .processedRefundIds(new java.util.ArrayList<>(List.of("ref-m2")))
                .status("refunded").createdAt(Instant.now()).build();

        when(paymentRepository.findByBooking("bk-multi-rec")).thenReturn(List.of(p1, p2));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(Booking.class))).thenAnswer(inv -> {
            booking.setVersion(1L);
            return booking;
        });

        boolean ok = bookingService.executeSequentialRefund(booking, new BigDecimal("200000"), "ref_multi_key");

        assertTrue(ok);
        verify(paymentGateway, never()).refund(anyString(), any(), anyString());
        verify(mongoTemplate, times(1)).findAndModify(any(), any(), any(), eq(Booking.class));
        verify(bookingRepository, times(1)).save(any(Booking.class));
        assertTrue(booking.getProcessedRefundIds().containsAll(List.of("ref-m1", "ref-m2")));
        assertEquals(0, new BigDecimal("200000").compareTo(booking.getRefundedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getPendingRefundAmount()));
    }

    @Test
    @DisplayName("27. Confirmación síncrona registra paymentId en processedPaymentIds para no excluirse al combinar con webhook")
    void syncPaymentConfirmationRegistersProcessedPaymentIdForCombinedWebhookRefunds() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService helperBookingService = mock(BookingService.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        EmailService emailService = mock(EmailService.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);

        PaymentService paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                helperBookingService,
                paymentGateway,
                emailService,
                userRepository
        );

        Booking booking = Booking.builder()
                .id("bk-sync-wh-1")
                .user("user-1")
                .suite("suite-1")
                .status("pending")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("200000"))
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("200000"))
                .processedPaymentIds(new java.util.ArrayList<>())
                .build();

        Payment payment1 = Payment.builder()
                .id("pay-sync-1")
                .booking("bk-sync-wh-1")
                .user("user-1")
                .amount(new BigDecimal("100000"))
                .intentId("pi_sync_1")
                .status("pending")
                .build();

        when(paymentRepository.findByIntentId("pi_sync_1")).thenReturn(Optional.of(payment1));
        when(paymentRepository.findById("pay-sync-1")).thenReturn(Optional.of(payment1));
        when(bookingRepository.findById("bk-sync-wh-1")).thenReturn(Optional.of(booking));
        when(paymentGateway.confirmIntent("pi_sync_1", "tok_1"))
                .thenReturn(new PaymentGateway.PaymentConfirmResponse(true, "tx_sync_1", "succeeded", null));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.confirmPayment("user-1", "pi_sync_1", "tok_1");

        assertTrue(booking.getProcessedPaymentIds().contains("pay-sync-1"));
    }

    @Test
    @DisplayName("28. Reconexión SSE concurrente con limpieza del emisor anterior no pierde la nueva suscripción")
    void sseReconnectDoesNotDropNewSubscriptionDuringOldCleanup() throws Exception {
        BookingSseService sseService = new BookingSseService();
        String userId = "user-sse-race";

        for (int i = 0; i < 100; i++) {
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter oldEmitter =
                    mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter newEmitter =
                    mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);

            final Runnable[] completionHook = new Runnable[1];
            doAnswer(inv -> {
                completionHook[0] = inv.getArgument(0);
                return null;
            }).when(oldEmitter).onCompletion(any(Runnable.class));

            sseService.subscribe(userId, oldEmitter);

            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            Thread tCleanup = new Thread(() -> {
                try {
                    start.await();
                    completionHook[0].run();
                } catch (InterruptedException ignored) {
                }
            });
            Thread tReconnect = new Thread(() -> {
                try {
                    start.await();
                    sseService.subscribe(userId, newEmitter);
                } catch (InterruptedException ignored) {
                }
            });
            tCleanup.start();
            tReconnect.start();
            start.countDown();
            tCleanup.join();
            tReconnect.join();

            assertEquals(1, sseService.getActiveSubscribersCount(), "Debe conservarse la nueva suscripción en iteración " + i);
            clearInvocations(newEmitter);
            sseService.emitBookingUpdate(userId, co.palaciodelmar.backend.dto.BookingDTO.builder().id("bk-1").build());
            verify(newEmitter, times(1)).send(any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));

            // Limpiar newEmitter para la siguiente iteración
            org.mockito.Mockito.reset(newEmitter);
            doThrow(new java.io.IOException("close")).when(newEmitter).send(any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
            sseService.emitBookingUpdate(userId, co.palaciodelmar.backend.dto.BookingDTO.builder().id("bk-cleanup").build());
        }
    }

    @Test
    @DisplayName("29. Reintentar confirmPayment tras fallo guardando Payment no acredita doblemente la reserva")
    void retryConfirmPaymentDoesNotDoubleCreditBookingWhenPaymentSavePreviouslyFailed() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService helperBookingService = mock(BookingService.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        EmailService emailService = mock(EmailService.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);

        PaymentService paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                helperBookingService,
                paymentGateway,
                emailService,
                userRepository
        );

        // El intento 1 guardó Booking (300.000 pagados y pay-retry-1 en processedPaymentIds), pero falló al guardar Payment
        Booking booking = Booking.builder()
                .id("bk-retry-1")
                .user("user-1")
                .suite("suite-1")
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(new BigDecimal("300000"))
                .balanceDue(BigDecimal.ZERO)
                .processedPaymentIds(new java.util.ArrayList<>(List.of("pay-retry-1")))
                .build();

        Payment payment = Payment.builder()
                .id("pay-retry-1")
                .booking("bk-retry-1")
                .user("user-1")
                .amount(new BigDecimal("300000"))
                .intentId("pi_retry_1")
                .status("pending")
                .build();

        when(paymentRepository.findByIntentId("pi_retry_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findById("pay-retry-1")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-retry-1")).thenReturn(Optional.of(booking));
        when(paymentGateway.confirmIntent("pi_retry_1", "tok_1"))
                .thenReturn(new PaymentGateway.PaymentConfirmResponse(true, "tx_retry_1", "succeeded", null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.confirmPayment("user-1", "pi_retry_1", "tok_1");

        assertEquals(0, new BigDecimal("300000").compareTo(booking.getPaidAmount()));
        assertEquals("completed", payment.getStatus());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("30. Reconfirmar un intento partially_refunded retorna idempotente sin volver a acreditar saldo")
    void reconfirmPartiallyRefundedPaymentDoesNotReCreditBooking() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService helperBookingService = mock(BookingService.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        EmailService emailService = mock(EmailService.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);

        PaymentService paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                helperBookingService,
                paymentGateway,
                emailService,
                userRepository
        );

        Booking booking = Booking.builder()
                .id("bk-part-ref-1")
                .user("user-1")
                .status("confirmed")
                .paymentStatus("partial")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(new BigDecimal("300000"))
                .refundedAmount(new BigDecimal("100000"))
                .balanceDue(new BigDecimal("100000"))
                .processedPaymentIds(new java.util.ArrayList<>(List.of("pay-pr-1")))
                .build();

        Payment payment = Payment.builder()
                .id("pay-pr-1")
                .booking("bk-part-ref-1")
                .user("user-1")
                .amount(new BigDecimal("300000"))
                .refundedAmount(new BigDecimal("100000"))
                .intentId("pi_pr_1")
                .transactionId("tx_pr_1")
                .status("partially_refunded")
                .build();

        when(paymentRepository.findByIntentId("pi_pr_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findById("pay-pr-1")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-part-ref-1")).thenReturn(Optional.of(booking));

        java.util.Map<String, Object> result = paymentService.confirmPayment("user-1", "pi_pr_1", "tok_1");

        assertEquals("partially_refunded", result.get("status"));
        assertEquals(0, new BigDecimal("300000").compareTo(booking.getPaidAmount()));
        verify(paymentGateway, never()).confirmIntent(anyString(), anyString());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("31. Si falta marca en Redis y falla MongoDB, UserSessionCacheService hace fail-closed incluso con caché previa")
    @SuppressWarnings("unchecked")
    void missingRedisMarkerPlusMongoFailureFailsClosedEvenWithCachedUser() {
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate =
                mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository =
                mock(co.palaciodelmar.backend.repository.UserRepository.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByEmail("stale@palaciodelmar.co"))
                .thenThrow(new RuntimeException("MongoDB timeout"));

        co.palaciodelmar.backend.security.UserSessionCacheService cacheService =
                new co.palaciodelmar.backend.security.UserSessionCacheService(
                        redisTemplate,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        userRepository
                );

        // Simular usuario antiguo presente en caché L1
        co.palaciodelmar.backend.model.User cachedUser = co.palaciodelmar.backend.model.User.builder()
                .id("u-stale")
                .email("stale@palaciodelmar.co")
                .role("GUEST")
                .build();
        cacheService.putCachedUser(cachedUser);

        assertTrue(cacheService.isTokenRevokedForUser("stale@palaciodelmar.co", System.currentTimeMillis()));
    }

    @Test
    @DisplayName("32. Actualizar cuenta bancaria usa actualización parcial atómica sin sobrescribir contraseña ni token")
    void updateBankAccountUsesPartialAtomicUpdateWithoutOverwritingCredentials() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        BookingService bookingService = mock(BookingService.class);
        co.palaciodelmar.backend.repository.PromotionRepository promotionRepository = mock(co.palaciodelmar.backend.repository.PromotionRepository.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);

        co.palaciodelmar.backend.controller.HostController controller = new co.palaciodelmar.backend.controller.HostController(
                bookingRepository,
                suiteRepository,
                bookingService,
                promotionRepository
        );
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        controller.setMongoTemplate(mongoTemplate);

        co.palaciodelmar.backend.model.User hostUser = co.palaciodelmar.backend.model.User.builder()
                .id("host-bank-1")
                .email("host@palaciodelmar.co")
                .password("OLD_HASH")
                .role("HOST")
                .build();
        when(userRepository.findById("host-bank-1")).thenReturn(Optional.of(hostUser));

        co.palaciodelmar.backend.security.UserPrincipal principal = new co.palaciodelmar.backend.security.UserPrincipal(hostUser);
        co.palaciodelmar.backend.dto.BankAccountDTO req = co.palaciodelmar.backend.dto.BankAccountDTO.builder()
                .bankName("Bancolombia")
                .accountType("Ahorros")
                .accountNumber("1234567890")
                .holderName("Anfitrion Prueba")
                .documentType("CC")
                .documentId("1000111222")
                .build();

        controller.updateBankAccount(principal, req);

        verify(mongoTemplate, times(1)).updateFirst(
                any(org.springframework.data.mongodb.core.query.Query.class),
                any(org.springframework.data.mongodb.core.query.Update.class),
                eq(co.palaciodelmar.backend.model.User.class)
        );
        verify(userRepository, never()).save(any(co.palaciodelmar.backend.model.User.class));
    }

    @Test
    @DisplayName("33. Confirmar pago pendiente cuando pasarela reporta refunded no acredita saldo en Booking")
    void confirmPendingPaymentWhenGatewayReportsRefundedDoesNotCreditBooking() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService helperBookingService = mock(BookingService.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        EmailService emailService = mock(EmailService.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);

        PaymentService paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                helperBookingService,
                paymentGateway,
                emailService,
                userRepository
        );

        Booking booking = Booking.builder()
                .id("bk-gw-ref-1")
                .user("user-1")
                .status("pending")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("300000"))
                .processedPaymentIds(new java.util.ArrayList<>())
                .build();

        Payment payment = Payment.builder()
                .id("pay-gw-ref-1")
                .booking("bk-gw-ref-1")
                .user("user-1")
                .amount(new BigDecimal("300000"))
                .intentId("pi_gw_ref_1")
                .status("pending")
                .build();

        when(paymentRepository.findByIntentId("pi_gw_ref-1")).thenReturn(Optional.empty());
        when(paymentRepository.findByIntentId("pi_gw_ref_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findById("pay-gw-ref-1")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-gw-ref-1")).thenReturn(Optional.of(booking));
        when(paymentGateway.confirmIntent("pi_gw_ref_1", "tok_1"))
                .thenReturn(new PaymentGateway.PaymentConfirmResponse(true, "tx_gw_ref_1", "refunded", null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalStateException.class, () -> paymentService.confirmPayment("user-1", "pi_gw_ref_1", "tok_1"));

        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getPaidAmount()));
        assertEquals("refunded", payment.getStatus());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("34. Calendario mensual de una suite inexistente no devuelve disponibilidad de otras suites")
    void monthlyCalendarForNonexistentSuiteDoesNotReturnOtherSuitesAvailability() {
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        co.palaciodelmar.backend.repository.PromotionRepository promotionRepository = mock(co.palaciodelmar.backend.repository.PromotionRepository.class);
        co.palaciodelmar.backend.repository.BranchRepository branchRepository = mock(co.palaciodelmar.backend.repository.BranchRepository.class);
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PricingService pricingService = new PricingService();

        AvailabilityService availabilityService = new AvailabilityService(
                suiteRepository,
                suiteNightRepository,
                bookingRepository,
                promotionRepository,
                branchRepository,
                propertyRepository,
                pricingService
        );

        Suite otherSuite = Suite.builder()
                .id("suite-real-1")
                .name("Suite Real")
                .active(true)
                .maxGuests(4)
                .totalUnits(1)
                .basePrice(new BigDecimal("400000"))
                .build();

        when(suiteRepository.findById("suite-inexistente")).thenReturn(Optional.empty());
        when(suiteRepository.findBySlug("suite-inexistente")).thenReturn(Optional.empty());
        when(suiteRepository.findAll()).thenReturn(List.of(otherSuite));

        LocalDate nextMonth = LocalDate.now().plusMonths(1);
        AvailabilityService.MonthlyAvailabilityResponseData data = availabilityService.getMonthlyAvailability(
                nextMonth.getYear(),
                nextMonth.getMonthValue(),
                2,
                null,
                null,
                "suite-inexistente"
        );

        assertEquals(0, data.getTotalSuites());
        assertTrue(data.getCalendar().stream().allMatch(day -> day.getAvailableSuites() == 0));
    }

    @Test
    @DisplayName("35. Webhooks fuera de orden: refund.processed antes que payment.succeeded no confirma ni marca pagada la reserva")
    void outOfOrderRefundBeforePaymentSucceededDoesNotConfirmOrMarkBookingPaid() {
        String secret = "whsec_out_of_order_test_secret_1234567890123456";
        co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository processedRepo =
                mock(co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = mock(BookingService.class);
        EmailService emailService = mock(EmailService.class);

        co.palaciodelmar.backend.webhook.PaymentWebhookService webhookService =
                new co.palaciodelmar.backend.webhook.PaymentWebhookService(
                        paymentRepository,
                        bookingRepository,
                        bookingService,
                        processedRepo,
                        emailService,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        null
                );
        org.springframework.test.util.ReflectionTestUtils.setField(webhookService, "webhookSecret", secret);

        Booking booking = Booking.builder()
                .id("bk-ooo-1")
                .status("pending")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("300000"))
                .processedPaymentIds(new java.util.ArrayList<>())
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment payment = Payment.builder()
                .id("pay-ooo-1")
                .booking("bk-ooo-1")
                .intentId("pi_ooo_1")
                .amount(new BigDecimal("300000"))
                .status("pending")
                .build();

        when(processedRepo.existsByEventId(anyString())).thenReturn(false);
        when(processedRepo.existsByPayloadHash(anyString())).thenReturn(false);
        when(processedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByIntentId("pi_ooo_1")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-ooo-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String ts = String.valueOf(Instant.now().getEpochSecond());

        // 1. Llega primero refund.processed
        String refundBody = "{\"eventType\":\"refund.processed\",\"data\":{\"intentId\":\"pi_ooo_1\",\"transactionId\":\"re_ooo_1\",\"amount\":300000}}";
        webhookService.processWebhook(refundBody, co.palaciodelmar.backend.webhook.WebhookSigner.sign(refundBody, secret), "evt_ooo_refund", ts);
        assertEquals("refunded", payment.getStatus());

        // 2. Llega después payment.succeeded
        String succeededBody = "{\"eventType\":\"payment.succeeded\",\"data\":{\"intentId\":\"pi_ooo_1\",\"transactionId\":\"tx_ooo_1\",\"amount\":300000}}";
        webhookService.processWebhook(succeededBody, co.palaciodelmar.backend.webhook.WebhookSigner.sign(succeededBody, secret), "evt_ooo_paid", ts);

        assertEquals("pending", booking.getStatus());
        assertEquals("pending", booking.getPaymentStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(booking.getPaidAmount()));
        assertEquals("refunded", payment.getStatus());
        verify(bookingService, never()).changeBookingStatus(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("36. Reenvío de webhook firmado cambiando cabeceras eventId y timestamp es bloqueado")
    void replayingSignedWebhookWithSwappedTimestampAndEventIdHeadersIsRejected() {
        String secret = "whsec_replay_header_swap_secret_123456789012345";
        co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository processedRepo =
                mock(co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = mock(BookingService.class);
        EmailService emailService = mock(EmailService.class);

        co.palaciodelmar.backend.webhook.PaymentWebhookService webhookService =
                new co.palaciodelmar.backend.webhook.PaymentWebhookService(
                        paymentRepository,
                        bookingRepository,
                        bookingService,
                        processedRepo,
                        emailService,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        null
                );
        org.springframework.test.util.ReflectionTestUtils.setField(webhookService, "webhookSecret", secret);

        String nowTs = String.valueOf(Instant.now().getEpochSecond());
        String oldTs = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());

        // Caso A: payload incluye eventId y timestamp firmados -> cambiar cabeceras falla
        String bodyWithMeta = "{\"eventId\":\"evt_orig_1\",\"timestamp\":\"" + oldTs + "\",\"eventType\":\"payment.failed\",\"data\":{\"intentId\":\"pi_rep_1\"}}";
        String sigWithMeta = co.palaciodelmar.backend.webhook.WebhookSigner.sign(bodyWithMeta, secret);

        assertThrows(SecurityException.class, () ->
                webhookService.processWebhook(bodyWithMeta, sigWithMeta, "evt_forged_new", nowTs));
        assertThrows(IllegalArgumentException.class, () ->
                webhookService.processWebhook(bodyWithMeta, sigWithMeta, "evt_orig_1", nowTs));

        // Caso B: payload sin eventId/timestamp internos -> hash del payload bloquea reenvío con cabeceras nuevas
        String bodyNoMeta = "{\"eventType\":\"payment.failed\",\"data\":{\"intentId\":\"pi_rep_2\"}}";
        String sigNoMeta = co.palaciodelmar.backend.webhook.WebhookSigner.sign(bodyNoMeta, secret);
        when(processedRepo.existsByEventId("evt_new_header_2")).thenReturn(false);
        when(processedRepo.existsByPayloadHash(anyString())).thenReturn(true);

        java.util.Map<String, Object> replayRes = webhookService.processWebhook(bodyNoMeta, sigNoMeta, "evt_new_header_2", nowTs);
        assertEquals("already_processed", replayRes.get("status"));
        assertEquals(true, replayRes.get("idempotent"));
        verify(paymentRepository, never()).findByIntentId("pi_rep_2");
    }

    @Test
    @DisplayName("37. Reserva legada sin processedPaymentIds no duplica reembolso parcial al llegar payment.succeeded")
    void partialRefundAlreadyAppliedOnLegacyBookingNotDoubleCountedOnPaymentSucceeded() {
        String secret = "whsec_legacy_partial_ref_secret_123456789012345";
        co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository processedRepo =
                mock(co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = mock(BookingService.class);
        EmailService emailService = mock(EmailService.class);

        co.palaciodelmar.backend.webhook.PaymentWebhookService webhookService =
                new co.palaciodelmar.backend.webhook.PaymentWebhookService(
                        paymentRepository,
                        bookingRepository,
                        bookingService,
                        processedRepo,
                        emailService,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        null
                );
        org.springframework.test.util.ReflectionTestUtils.setField(webhookService, "webhookSecret", secret);

        // Reserva legada sin processedPaymentIds, pero ya tiene el reembolso parcial de $100.000 contabilizado
        Booking booking = Booking.builder()
                .id("bk-leg-pr-1")
                .status("confirmed")
                .paymentStatus("partial")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(BigDecimal.ZERO)
                .refundedAmount(new BigDecimal("100000"))
                .balanceDue(new BigDecimal("300000"))
                .processedPaymentIds(new java.util.ArrayList<>())
                .processedRefundIds(new java.util.ArrayList<>(List.of("re_legacy_1")))
                .build();

        Payment payment = Payment.builder()
                .id("pay-leg-pr-1")
                .booking("bk-leg-pr-1")
                .intentId("pi_leg_pr_1")
                .amount(new BigDecimal("300000"))
                .refundedAmount(new BigDecimal("100000"))
                .processedRefundIds(new java.util.ArrayList<>(List.of("re_legacy_1")))
                .status("partially_refunded")
                .build();

        when(processedRepo.existsByEventId(anyString())).thenReturn(false);
        when(processedRepo.existsByPayloadHash(anyString())).thenReturn(false);
        when(processedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByIntentId("pi_leg_pr_1")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-leg-pr-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        String ts = String.valueOf(Instant.now().getEpochSecond());
        String succeededBody = "{\"eventType\":\"payment.succeeded\",\"data\":{\"intentId\":\"pi_leg_pr_1\",\"transactionId\":\"tx_leg_pr_1\",\"amount\":300000}}";
        webhookService.processWebhook(succeededBody, co.palaciodelmar.backend.webhook.WebhookSigner.sign(succeededBody, secret), "evt_leg_pr_ok", ts);

        assertEquals(0, new BigDecimal("100000").compareTo(booking.getRefundedAmount()));
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getBalanceDue()));
        assertEquals("partially_refunded", payment.getStatus());
    }

    @Test
    @DisplayName("38. Devolución parcial antes del cobro calcula saldo neto pendiente sin confirmar como pagada")
    void outOfOrderPartialRefundBeforePaymentSucceededComputesNetBalanceWithoutConfirmingAsPaid() {
        String secret = "whsec_ooo_partial_ref_test_secret_12345678901234";
        co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository processedRepo =
                mock(co.palaciodelmar.backend.repository.ProcessedWebhookEventRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingService bookingService = mock(BookingService.class);
        EmailService emailService = mock(EmailService.class);

        co.palaciodelmar.backend.webhook.PaymentWebhookService webhookService =
                new co.palaciodelmar.backend.webhook.PaymentWebhookService(
                        paymentRepository,
                        bookingRepository,
                        bookingService,
                        processedRepo,
                        emailService,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        null
                );
        org.springframework.test.util.ReflectionTestUtils.setField(webhookService, "webhookSecret", secret);

        Booking booking = Booking.builder()
                .id("bk-ooo-pr-1")
                .status("pending")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("200000"))
                .paidAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("200000"))
                .processedPaymentIds(new java.util.ArrayList<>())
                .processedRefundIds(new java.util.ArrayList<>())
                .build();

        Payment payment = Payment.builder()
                .id("pay-ooo-pr-1")
                .booking("bk-ooo-pr-1")
                .intentId("pi_ooo_pr_1")
                .amount(new BigDecimal("200000"))
                .status("pending")
                .build();

        when(processedRepo.existsByEventId(anyString())).thenReturn(false);
        when(processedRepo.existsByPayloadHash(anyString())).thenReturn(false);
        when(processedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByIntentId("pi_ooo_pr_1")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-ooo-pr-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        String ts = String.valueOf(Instant.now().getEpochSecond());

        // 1. Llega primero refund.processed parcial por $100.000
        String refundBody = "{\"eventType\":\"refund.processed\",\"data\":{\"intentId\":\"pi_ooo_pr_1\",\"transactionId\":\"re_ooo_pr_1\",\"amount\":100000}}";
        webhookService.processWebhook(refundBody, co.palaciodelmar.backend.webhook.WebhookSigner.sign(refundBody, secret), "evt_ooo_pr_ref", ts);

        // 2. Llega después payment.succeeded por $200.000
        String succeededBody = "{\"eventType\":\"payment.succeeded\",\"data\":{\"intentId\":\"pi_ooo_pr_1\",\"transactionId\":\"tx_ooo_pr_1\",\"amount\":200000}}";
        webhookService.processWebhook(succeededBody, co.palaciodelmar.backend.webhook.WebhookSigner.sign(succeededBody, secret), "evt_ooo_pr_paid", ts);

        assertEquals(0, new BigDecimal("200000").compareTo(booking.getPaidAmount()));
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getRefundedAmount()));
        assertEquals(0, new BigDecimal("100000").compareTo(booking.getBalanceDue()));
        assertEquals("partial", booking.getPaymentStatus());
        assertEquals("pending", booking.getStatus());
        assertEquals("partially_refunded", payment.getStatus());
        verify(bookingService, never()).changeBookingStatus(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("39. logoutAllDevices cierra los canales SSE abiertos del usuario para cortar eventos en tiempo real")
    void logoutAllDevicesClosesActiveSseStreamsForUser() {
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        co.palaciodelmar.backend.security.JwtService jwtService = mock(co.palaciodelmar.backend.security.JwtService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = mock(org.springframework.security.authentication.AuthenticationManager.class);
        EmailService emailService = mock(EmailService.class);
        org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);

        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                authManager,
                emailService,
                mongoTemplate
        );
        BookingSseService sseService = new BookingSseService();
        authService.setBookingSseService(sseService);

        co.palaciodelmar.backend.model.User user = co.palaciodelmar.backend.model.User.builder()
                .id("u-sse-logout")
                .email("sse@palaciodelmar.co")
                .role("GUEST")
                .build();
        when(userRepository.findById("u-sse-logout")).thenReturn(Optional.of(user));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(co.palaciodelmar.backend.model.User.class)))
                .thenReturn(user);

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        sseService.subscribe("u-sse-logout", emitter);
        assertEquals(1, sseService.getActiveSubscribersCount());
        clearInvocations(emitter);

        authService.logoutAllDevices("u-sse-logout");

        verify(emitter, times(1)).complete();
        assertEquals(0, sseService.getActiveSubscribersCount());

        sseService.emitBookingUpdate("u-sse-logout", co.palaciodelmar.backend.dto.BookingDTO.builder().id("bk-after-logout").build());
        verifyNoMoreInteractions(emitter);
    }

    @Test
    @DisplayName("40. confirmPayment ejecuta compensación y registra transacción en Payment si falla el primer guardado de Booking tras el cobro")
    void confirmPaymentExecutesCompensationAndRecordsTransactionWhenFirstBookingSaveFails() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        co.palaciodelmar.backend.repository.BranchRepository branchRepository = mock(co.palaciodelmar.backend.repository.BranchRepository.class);
        co.palaciodelmar.backend.repository.ExperienceRepository experienceRepository = mock(co.palaciodelmar.backend.repository.ExperienceRepository.class);
        co.palaciodelmar.backend.repository.PromotionRepository promotionRepository = mock(co.palaciodelmar.backend.repository.PromotionRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        EmailService emailService = mock(EmailService.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        BookingService realBookingService = new BookingService(
                bookingRepository,
                suiteRepository,
                branchRepository,
                experienceRepository,
                suiteNightRepository,
                userRepository,
                promotionRepository,
                new PricingService(),
                paymentGateway
        );
        realBookingService.setPaymentRepository(paymentRepository);

        PaymentService paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                realBookingService,
                paymentGateway,
                emailService,
                userRepository
        );

        Booking booking = Booking.builder()
                .id("bk-first-save-fail")
                .user("u-1")
                .suite("suite-1")
                .status("pending")
                .paymentStatus("pending")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(BigDecimal.ZERO)
                .balanceDue(new BigDecimal("300000"))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();

        Payment payment = Payment.builder()
                .id("pay-first-save-fail")
                .booking("bk-first-save-fail")
                .user("u-1")
                .intentId("pi_first_save_fail")
                .amount(new BigDecimal("300000"))
                .status("pending")
                .build();

        when(paymentRepository.findByIntentId("pi_first_save_fail")).thenReturn(Optional.of(payment));
        when(paymentRepository.findById("pay-first-save-fail")).thenReturn(Optional.of(payment));
        when(bookingRepository.findById("bk-first-save-fail")).thenReturn(Optional.of(booking));
        when(paymentGateway.confirmIntent("pi_first_save_fail", "tok_ok"))
                .thenReturn(new PaymentGateway.PaymentConfirmResponse(true, "tx_first_save_fail", "succeeded", null));
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new RuntimeException("Simulated DB write failure on first save after charge"));
        when(paymentGateway.refund(eq("tx_first_save_fail"), eq(new BigDecimal("300000"))))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(true, "re_comp_first_save", "refunded", null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalStateException.class,
                () -> paymentService.confirmPayment("u-1", "pi_first_save_fail", "tok_ok"));

        assertEquals("tx_first_save_fail", payment.getTransactionId());
        assertEquals("refunded", payment.getStatus());
        assertEquals(0, new BigDecimal("300000").compareTo(payment.getRefundedAmount()));
        verify(paymentGateway, times(1)).refund(eq("tx_first_save_fail"), eq(new BigDecimal("300000")));
        verify(paymentRepository, atLeastOnce()).save(payment);
    }

    @Test
    @DisplayName("41. Cancelación administrativa persiste pendingRefundAmount y requires_refund desde el primer guardado de CANCELLED")
    void adminCancellationPersistsPendingRefundAtomicallyWithCancelledStatus() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        SuiteRepository suiteRepository = mock(SuiteRepository.class);
        co.palaciodelmar.backend.repository.BranchRepository branchRepository = mock(co.palaciodelmar.backend.repository.BranchRepository.class);
        co.palaciodelmar.backend.repository.ExperienceRepository experienceRepository = mock(co.palaciodelmar.backend.repository.ExperienceRepository.class);
        co.palaciodelmar.backend.repository.PromotionRepository promotionRepository = mock(co.palaciodelmar.backend.repository.PromotionRepository.class);
        SuiteNightRepository suiteNightRepository = mock(SuiteNightRepository.class);
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        EmailService emailService = mock(EmailService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);

        BookingService realBookingService = new BookingService(
                bookingRepository,
                suiteRepository,
                branchRepository,
                experienceRepository,
                suiteNightRepository,
                userRepository,
                promotionRepository,
                new PricingService(),
                paymentGateway
        );
        realBookingService.setPaymentRepository(paymentRepository);

        AdminService adminService = new AdminService(
                bookingRepository,
                suiteRepository,
                userRepository,
                promotionRepository,
                suiteNightRepository,
                realBookingService,
                null,
                paymentRepository,
                paymentGateway
        );

        Booking booking = Booking.builder()
                .id("bk-admin-canc-1")
                .suite("suite-1")
                .status("confirmed")
                .paymentStatus("paid")
                .totalPrice(new BigDecimal("300000"))
                .paidAmount(new BigDecimal("300000"))
                .refundedAmount(BigDecimal.ZERO)
                .balanceDue(BigDecimal.ZERO)
                .checkIn(LocalDate.now().plusDays(10))
                .checkOut(LocalDate.now().plusDays(12))
                .build();

        java.util.concurrent.atomic.AtomicReference<Booking> persistedSnapshot = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger saveCount = new java.util.concurrent.atomic.AtomicInteger(0);

        when(bookingRepository.findById("bk-admin-canc-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking arg = inv.getArgument(0);
            int call = saveCount.incrementAndGet();
            if (call == 1) {
                persistedSnapshot.set(Booking.builder()
                        .id(arg.getId())
                        .status(arg.getStatus())
                        .paymentStatus(arg.getPaymentStatus())
                        .paidAmount(arg.getPaidAmount())
                        .refundedAmount(arg.getRefundedAmount())
                        .pendingRefundAmount(arg.getPendingRefundAmount())
                        .pendingRefundIdempotencyKey(arg.getPendingRefundIdempotencyKey())
                        .build());
                return arg;
            }
            throw new RuntimeException("Simulated persistence failure after initial CANCELLED save");
        });
        when(paymentGateway.refund(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new PaymentGateway.PaymentRefundResponse(false, null, "failed", "Gateway down"));

        adminService.updateBookingStatus("bk-admin-canc-1", "CANCELLED");

        Booking firstSaved = persistedSnapshot.get();
        assertNotNull(firstSaved);
        assertEquals("cancelled", firstSaved.getStatus());
        assertEquals("requires_refund", firstSaved.getPaymentStatus());
        assertNotNull(firstSaved.getPendingRefundIdempotencyKey());
        assertEquals(0, new BigDecimal("300000").compareTo(firstSaved.getPendingRefundAmount()));
    }

    @Test
    @DisplayName("42. Una petición autenticada antes de logoutAllDevices no puede abrir canal SSE después del cierre")
    void preAuthenticatedRequestCannotOpenSseStreamAfterLogoutAllDevices() {
        co.palaciodelmar.backend.repository.UserRepository userRepository = mock(co.palaciodelmar.backend.repository.UserRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder passwordEncoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        co.palaciodelmar.backend.security.JwtService jwtService = mock(co.palaciodelmar.backend.security.JwtService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = mock(org.springframework.security.authentication.AuthenticationManager.class);
        EmailService emailService = mock(EmailService.class);
        org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = mock(org.springframework.data.mongodb.core.MongoTemplate.class);

        BookingSseService sseService = new BookingSseService();
        AuthService authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                authManager,
                emailService,
                mongoTemplate
        );
        authService.setBookingSseService(sseService);

        co.palaciodelmar.backend.controller.BookingStreamController streamController =
                new co.palaciodelmar.backend.controller.BookingStreamController(sseService, userRepository);

        co.palaciodelmar.backend.model.User user = co.palaciodelmar.backend.model.User.builder()
                .id("u-race-sse")
                .email("race-sse@palaciodelmar.co")
                .role("GUEST")
                .build();
        when(userRepository.findById("u-race-sse")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("race-sse@palaciodelmar.co")).thenReturn(Optional.of(user));
        when(mongoTemplate.findAndModify(any(), any(), any(), eq(co.palaciodelmar.backend.model.User.class)))
                .thenReturn(user);

        // 1. La petición se autentica ANTES de logoutAllDevices
        co.palaciodelmar.backend.security.UserPrincipal preAuthPrincipal =
                new co.palaciodelmar.backend.security.UserPrincipal(user);
        org.springframework.security.core.Authentication preAuth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        preAuthPrincipal, null, preAuthPrincipal.getAuthorities());

        // 2. Ocurre «cerrar todas las sesiones»
        authService.logoutAllDevices("u-race-sse");

        // 3. La petición en vuelo intenta abrir su canal SSE DESPUÉS del cierre
        assertThrows(SecurityException.class, () -> streamController.streamBookings(preAuth));
        assertEquals(0, sseService.getActiveSubscribersCount());
    }
}
