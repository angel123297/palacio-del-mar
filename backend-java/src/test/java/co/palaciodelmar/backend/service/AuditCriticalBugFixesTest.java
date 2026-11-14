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
}
