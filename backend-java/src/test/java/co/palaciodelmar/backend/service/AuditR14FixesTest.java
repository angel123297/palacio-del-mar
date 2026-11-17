package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.config.LegacyEmailNormalizer;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.exception.GlobalExceptionHandler;
import co.palaciodelmar.backend.model.*;
import co.palaciodelmar.backend.repository.*;
import co.palaciodelmar.backend.webhook.PaymentWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Regresiones de la ronda de auditoría r14 (hallazgos verificados y corregidos). */
class AuditR14FixesTest {

    // ---------- #1 CRITICAL: transactionId null explícito rompe el índice unique-sparse ----------
    @Test
    @DisplayName("#1: un pago fallido hace $unset de transactionId (nunca $set null)")
    void failedPaymentUnsetsTransactionId() {
        PaymentIntentRepository repo = mock(PaymentIntentRepository.class);
        PaymentWebhookService webhook = mock(PaymentWebhookService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        SimulatedPaymentGateway gateway = new SimulatedPaymentGateway(repo, webhook, new ObjectMapper().findAndRegisterModules());
        gateway.setMongoTemplate(mongo);
        ReflectionTestUtils.setField(gateway, "webhookSecret", "whsec_test_secret");
        ReflectionTestUtils.setField(gateway, "serverPort", 5000);
        String instanceId = (String) ReflectionTestUtils.getField(gateway, "instanceId");

        PaymentIntent intent = PaymentIntent.builder()
                .id("pi_fail_1").amount(new BigDecimal("1000")).status("processing")
                .simulationOutcome("DECLINED").failureMessage("Tarjeta rechazada por el banco")
                .claimLock(instanceId).claimExpiresAt(Instant.now().plusSeconds(60)).build();
        when(repo.findById("pi_fail_1")).thenReturn(Optional.of(intent));
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(PaymentIntent.class))).thenReturn(intent);

        gateway.processIntentSimulation("pi_fail_1");

        ArgumentCaptor<Update> cap = ArgumentCaptor.forClass(Update.class);
        verify(mongo, atLeastOnce()).findAndModify(any(Query.class), cap.capture(), eq(PaymentIntent.class));
        Update finalUpdate = cap.getAllValues().stream()
                .filter(u -> "failed".equals(((Document) u.getUpdateObject().get("$set")).get("status")))
                .findFirst().orElseThrow(() -> new AssertionError("No se selló la transición a failed"));
        Document set = (Document) finalUpdate.getUpdateObject().get("$set");
        Document unset = (Document) finalUpdate.getUpdateObject().get("$unset");
        assertFalse(set.containsKey("transactionId"), "No debe escribirse transactionId:null");
        assertNotNull(unset);
        assertTrue(unset.containsKey("transactionId"));
    }

    // ---------- #3 / #22: límites de huéspedes ----------
    @Test
    @DisplayName("#3/#22: guests/children con valores absurdos se rechazan por validación")
    void guestLimitsAreValidated() {
        Validator v = Validation.buildDefaultValidatorFactory().getValidator();
        BookingRequest br = BookingRequest.builder().guests(Integer.MAX_VALUE).children(1).build();
        assertFalse(v.validate(br).isEmpty());
        br = BookingRequest.builder().guests(2).children(Integer.MAX_VALUE).build();
        assertFalse(v.validate(br).isEmpty());
        br = BookingRequest.builder().guests(2).children(1).build();
        assertTrue(v.validate(br).isEmpty());

        CalculatePriceRequest cp = CalculatePriceRequest.builder().suiteId("s").checkIn(LocalDate.now())
                .checkOut(LocalDate.now().plusDays(1)).guests(100000).build();
        assertFalse(v.validate(cp).isEmpty());
        cp.setGuests(4);
        assertTrue(v.validate(cp).isEmpty());
    }

    // ---------- #4: GET público de propiedades no filtra datos internos ----------
    @Test
    @DisplayName("#4: DTO público oculta ownerId, commissionRate, rejectionReason y phone")
    void publicPropertyDtoHidesInternalFields() {
        PropertyRepository pr = mock(PropertyRepository.class);
        SuiteRepository sr = mock(SuiteRepository.class);
        PropertyService ps = new PropertyService(pr, sr);
        Property p = Property.builder().id("p1").ownerId("host-1").name("Hotel").status("APPROVED").active(true)
                .commissionRate(new BigDecimal("0.08")).rejectionReason("x").phone("+573001112233").build();
        when(pr.findByStatus("APPROVED")).thenReturn(List.of(p));
        when(pr.findById("p1")).thenReturn(Optional.of(p));

        PropertyDTO fromList = ps.getApprovedProperties(null).get(0);
        PropertyDTO fromId = ps.getApprovedPropertyById("p1");
        for (PropertyDTO d : List.of(fromList, fromId)) {
            assertEquals("Hotel", d.getName());
            assertNull(d.getOwnerId());
            assertNull(d.getCommissionRate());
            assertNull(d.getRejectionReason());
            assertNull(d.getPhone());
        }
        // El DTO privado (host/admin) conserva los campos
        assertEquals("host-1", ps.toDTO(p).getOwnerId());
    }

    // ---------- #9: normalización de emails legacy ----------
    @Test
    @DisplayName("#9: emails con mayúsculas se pasan a minúsculas; si hay choque se omiten")
    void legacyEmailsAreNormalized() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        User ok = User.builder().id("u1").email("Foo@Mail.COM").build();
        User clash = User.builder().id("u2").email("Dup@Mail.com").build();
        when(mongo.find(any(Query.class), eq(User.class))).thenReturn(List.of(ok, clash));
        when(mongo.exists(any(Query.class), eq(User.class))).thenReturn(false, true);

        int fixed = new LegacyEmailNormalizer(mongo).normalize();

        assertEquals(1, fixed);
        verify(mongo, times(1)).updateFirst(any(Query.class), any(Update.class), eq(User.class));
    }

    // ---------- #11: reservas terminadas no bloquean mantenimiento ----------
    @Test
    @DisplayName("#11: noches de reservas checked_out/no_show se liberan y no impiden el bloqueo")
    void finishedBookingsDoNotBlockMaintenance() {
        PropertyRepository pr = mock(PropertyRepository.class);
        SuiteRepository sr = mock(SuiteRepository.class);
        SuiteNightRepository snr = mock(SuiteNightRepository.class);
        BookingRepository br = mock(BookingRepository.class);
        PropertyService ps = new PropertyService(pr, sr);
        ReflectionTestUtils.setField(ps, "suiteNightRepository", snr);
        ReflectionTestUtils.setField(ps, "bookingRepository", br);

        LocalDate start = LocalDate.now().plusDays(3);
        LocalDate end = start.plusDays(2);
        Property prop = Property.builder().id("p1").ownerId("h1").build();
        Suite suite = Suite.builder().id("s1").propertyId("p1").totalUnits(1).build();
        SuiteNight done = SuiteNight.builder().id("n1").suite("s1").unitSlot(1).dates(List.of(start))
                .booking("b-done").status("confirmed").build();
        when(pr.findById("p1")).thenReturn(Optional.of(prop));
        when(sr.findById("s1")).thenReturn(Optional.of(suite));
        when(snr.findBySuiteAndDatesBetween(eq("s1"), eq(start), eq(end.minusDays(1)))).thenReturn(List.of(done));
        when(br.findAllById(anyList())).thenReturn(List.of(Booking.builder().id("b-done").status("checked_out").build()));

        Map<String, Object> res = ps.blockSuiteNightsForMaintenance("p1", "s1", "h1", start, end, "Pintura", false);

        assertEquals(2, res.get("blockedNights"));
        verify(snr).deleteAll(List.of(done));
    }

    @Test
    @DisplayName("#11: una reserva realmente activa sigue bloqueando el mantenimiento")
    void activeBookingStillBlocksMaintenance() {
        PropertyRepository pr = mock(PropertyRepository.class);
        SuiteRepository sr = mock(SuiteRepository.class);
        SuiteNightRepository snr = mock(SuiteNightRepository.class);
        BookingRepository br = mock(BookingRepository.class);
        PropertyService ps = new PropertyService(pr, sr);
        ReflectionTestUtils.setField(ps, "suiteNightRepository", snr);
        ReflectionTestUtils.setField(ps, "bookingRepository", br);

        LocalDate start = LocalDate.now().plusDays(3);
        LocalDate end = start.plusDays(2);
        when(pr.findById("p1")).thenReturn(Optional.of(Property.builder().id("p1").ownerId("h1").build()));
        when(sr.findById("s1")).thenReturn(Optional.of(Suite.builder().id("s1").propertyId("p1").totalUnits(1).build()));
        SuiteNight active = SuiteNight.builder().id("n1").suite("s1").unitSlot(1).dates(List.of(start))
                .booking("b-live").status("confirmed").build();
        when(snr.findBySuiteAndDatesBetween(eq("s1"), eq(start), eq(end.minusDays(1)))).thenReturn(List.of(active));
        when(br.findAllById(anyList())).thenReturn(List.of(Booking.builder().id("b-live").status("confirmed").build()));

        assertThrows(IllegalStateException.class,
                () -> ps.blockSuiteNightsForMaintenance("p1", "s1", "h1", start, end, "Pintura", false));
        verify(snr, never()).saveAll(anyList());
    }

    // ---------- #12: mensajería en reservas canceladas ----------
    @Test
    @DisplayName("#12: no se puede enviar mensajes en reservas canceladas")
    void cannotMessageCancelledBooking() {
        BookingMessageRepository mr = mock(BookingMessageRepository.class);
        BookingRepository br = mock(BookingRepository.class);
        BookingMessageService svc = new BookingMessageService(mr, br, mock(PropertyRepository.class), mock(SuiteRepository.class));
        when(br.findById("b1")).thenReturn(Optional.of(Booking.builder().id("b1").user("g1").status("cancelled").build()));

        assertThrows(IllegalStateException.class, () -> svc.sendMessage("b1", "g1", "Ana", "hola", false));
        verify(mr, never()).save(any());
    }

    // ---------- #15: 415 ----------
    @Test
    @DisplayName("#15: Content-Type no soportado responde 415 y no 500")
    void unsupportedMediaTypeReturns415() {
        var resp = new GlobalExceptionHandler().handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("text/plain"));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, resp.getStatusCode());
    }

    // ---------- #21: el feed .ics incluye bloqueos MAINT_ ----------
    @Test
    @DisplayName("#21: isMaintenanceOrExternalBlock incluye MAINT_ y EXT_BLOCK_ pero no reservas nativas")
    void icsIncludesMaintenanceBlocks() throws Exception {
        ICalService ical = new ICalService(mock(PropertyRepository.class), mock(SuiteRepository.class), mock(BookingRepository.class), mock(SuiteNightRepository.class));
        Method m = ICalService.class.getDeclaredMethod("isMaintenanceOrExternalBlock", SuiteNight.class);
        m.setAccessible(true);
        SuiteNight maint = SuiteNight.builder().booking("MAINT_ab12").status("maintenance").build();
        SuiteNight ext = SuiteNight.builder().booking("EXT_BLOCK_x").status("blocked").build();
        SuiteNight guest = SuiteNight.builder().booking("booking-1").status("confirmed").build();
        assertTrue((boolean) m.invoke(ical, maint));
        assertTrue((boolean) m.invoke(ical, ext));
        assertFalse((boolean) m.invoke(ical, guest));
    }

    // ---------- #24: checkout con hold vencido ----------
    @Test
    @DisplayName("#24: no se crea intent de pago si el hold de la reserva ya venció")
    void checkoutWithExpiredHoldIsRejected() {
        PaymentRepository payRepo = mock(PaymentRepository.class);
        BookingRepository bookRepo = mock(BookingRepository.class);
        PaymentGateway gateway = mock(PaymentGateway.class);
        PaymentService svc = new PaymentService(payRepo, bookRepo, mock(BookingService.class), gateway,
                mock(EmailService.class), mock(UserRepository.class));
        Booking b = Booking.builder().id("b1").user("u1").status("pending")
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .balanceDue(new BigDecimal("1000")).paymentStatus("pending").build();
        when(bookRepo.findById("b1")).thenReturn(Optional.of(b));

        assertThrows(IllegalStateException.class, () -> svc.createPaymentIntent("u1", "b1", "card", null));
        verify(gateway, never()).createIntent(any(), any(), any());
    }

    // ---------- #10: reseña duplicada con alerta de mantenimiento pendiente ----------
    @Test
    @DisplayName("#10: reenviar una reseña con contenido distinto NO devuelve la reseña vieja como éxito")
    void duplicateReviewWithDifferentContentIsRejected() {
        ReviewRepository rr = mock(ReviewRepository.class);
        BookingRepository br = mock(BookingRepository.class);
        ReviewService rs = new ReviewService(rr, br, mock(UserRepository.class));
        Booking b = Booking.builder().id("b1").user("g1").suite("s1").status("checked_out").build();
        when(br.findById("b1")).thenReturn(Optional.of(b));
        Review existing = Review.builder().id("r1").bookingId("b1").rating(2).comment("Aire dañado")
                .maintenanceAlert("Aire acondicionado").maintenanceWebhookDispatched(false).build();
        when(rr.existsByBookingId("b1")).thenReturn(true);
        when(rr.findByBookingId("b1")).thenReturn(Optional.of(existing));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> rs.addVerifiedReview("b1", "g1", 5, "Todo perfecto ahora"));
        assertTrue(ex.getMessage().contains("Ya has enviado"));
    }

    // ---------- #2 HIGH: extras cobrados al huésped se liquidan en fee y payout ----------
    @Test
    @DisplayName("#2: extras (mascotas, transporte, huéspedes extra) se incluyen en taxableTotal y se liquidan")
    void extrasAreIncludedInFeeAndPayout() {
        BookingRepository br = mock(BookingRepository.class);
        PropertyRepository pr = mock(PropertyRepository.class);
        SuiteRepository sr = mock(SuiteRepository.class);
        BookingService bs = new BookingService(br, sr, null, mock(ExperienceRepository.class), null, null, null, null, null);
        ReflectionTestUtils.setField(bs, "propertyRepository", pr);

        // Reserva con habitación base 500.000, petFee 80.000, airportPickup 150.000
        Booking booking = Booking.builder()
                .id("b-extras")
                .suite("suite-1")
                .subtotal(new BigDecimal("500000.00"))
                .discount(BigDecimal.ZERO)
                .taxes(BigDecimal.ZERO)
                .petFriendly(true) // +80.000
                .airportPickup(true) // +150.000
                .totalPrice(new BigDecimal("930000.00")) // 500k + 80k + 150k + 200k depósito
                .guaranteeDeposit(new BigDecimal("200000.00"))
                .build();

        bs.recalculateBookingFeesAndPayout(booking, null);

        // Base imponible = 500.000 + 80.000 + 150.000 = 730.000
        // fee (8%) = 58.400, payout (92%) = 671.600
        assertEquals(0, new BigDecimal("58400.00").compareTo(booking.getPlatformFee()), "Comisión debe ser 58.400 COP");
        assertEquals(0, new BigDecimal("671600.00").compareTo(booking.getHostPayout()), "Payout debe ser 671.600 COP");
    }
}
