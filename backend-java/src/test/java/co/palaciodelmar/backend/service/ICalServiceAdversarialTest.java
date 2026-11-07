package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.SuiteNight;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteNightRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Batería adversarial del feed iCal: escenarios que un huésped, anfitrión o administrador
 * podría provocar en producción (fechas inválidas, suites huérfanas, estados en mayúsculas,
 * bloqueos expirados, nombres con caracteres especiales RFC 5545, rangos múltiples).
 */
@ExtendWith(MockitoExtension.class)
class ICalServiceAdversarialTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private SuiteRepository suiteRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SuiteNightRepository suiteNightRepository;

    @InjectMocks
    private ICalService icalService;

    private Property activeProperty() {
        return Property.builder()
                .id("prop-adv-01")
                .name("Villa Adversarial")
                .active(true)
                .status("APPROVED")
                .build();
    }

    // --- Escenarios de datos corruptos o inválidos provocables por usuarios ---

    @Test
    @DisplayName("Reserva confirmada con checkIn == checkOut (0 noches) se omite sin romper el feed")
    void sameDayBookingIsOmittedNotCrash() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        Booking zeroNights = Booking.builder()
                .id("BK-ZERO")
                .suite("suite-x")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 10)) // mismo día: inválido
                .status("confirmed")
                .build();

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of(zeroNights));
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertFalse(ics.contains("BK-ZERO"), "Una reserva de 0 noches no debe generar VEVENT");
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @DisplayName("Reserva confirmada con checkOut anterior a checkIn (fechas cruzadas) se omite sin romper el feed")
    void invertedDatesBookingIsOmitted() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        Booking inverted = Booking.builder()
                .id("BK-INV")
                .suite("suite-x")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 5)) // cruzada
                .status("confirmed")
                .build();

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of(inverted));
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertFalse(ics.contains("BK-INV"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @DisplayName("Reserva con fechas null (dato corrupto) se omite sin NPE")
    void nullDatesBookingIsOmitted() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        Booking corrupt = Booking.builder()
                .id("BK-NULL")
                .suite("suite-x")
                .checkIn(null)
                .checkOut(null)
                .status("confirmed")
                .build();

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of(corrupt));
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertFalse(ics.contains("BK-NULL"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    // --- Escenarios de estado provocables por un admin (mayúsculas, inconsistencias) ---

    @Test
    @DisplayName("Estados de reserva en MAYÚSCULAS (inserción manual de admin) se filtran igual: confirmada entra, cancelada no")
    void uppercaseStatusesAreHandledCaseInsensitively() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        Booking confirmedUpper = Booking.builder()
                .id("BK-UPPER-OK")
                .suite("suite-x")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 12))
                .status("CONFIRMED") // admin insertó en mayúsculas
                .build();

        Booking cancelledUpper = Booking.builder()
                .id("BK-UPPER-NO")
                .suite("suite-x")
                .checkIn(LocalDate.of(2026, 11, 20))
                .checkOut(LocalDate.of(2026, 11, 22))
                .status("CANCELLED")
                .build();

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of(confirmedUpper, cancelledUpper));
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertTrue(ics.contains("BK-UPPER-OK"), "CONFIRMED en mayúsculas debe ocupar fechas");
        assertFalse(ics.contains("BK-UPPER-NO"), "CANCELLED en mayúsculas no debe aparecer");
    }

    @Test
    @DisplayName("Reserva pending sin expiresAt (dato legacy) bloquea fechas; con expiresAt futuro también")
    void pendingBookingsWithoutOrFutureExpiryOccupyDates() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        Booking pendingNoExpiry = Booking.builder()
                .id("BK-PEND-LEGACY")
                .suite("suite-x")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 12))
                .status("pending")
                .expiresAt(null)
                .build();

        Booking pendingFuture = Booking.builder()
                .id("BK-PEND-FUTURE")
                .suite("suite-x")
                .checkIn(LocalDate.of(2026, 11, 20))
                .checkOut(LocalDate.of(2026, 11, 22))
                .status("pending")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of(pendingNoExpiry, pendingFuture));
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertTrue(ics.contains("BK-PEND-LEGACY"));
        assertTrue(ics.contains("BK-PEND-FUTURE"));
    }

    // --- Escenarios de bloqueos que un anfitrión o integración podría crear ---

    @Test
    @DisplayName("Bloqueo 'blocked' externo sin reserva aparece como 'Bloqueo externo'; 'held' y expirados se excluyen")
    void externalBlockedIncludedHeldAndExpiredExcluded() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        SuiteNight externalBlock = SuiteNight.builder()
                .id("sn-ext-1")
                .suite("suite-x")
                .booking(null)
                .status("blocked")
                .dates(List.of(LocalDate.of(2026, 12, 24)))
                .build();

        SuiteNight heldNoBooking = SuiteNight.builder()
                .id("sn-held-1")
                .suite("suite-x")
                .booking(null)
                .status("held")
                .dates(List.of(LocalDate.of(2026, 12, 25)))
                .build();

        SuiteNight expiredBlock = SuiteNight.builder()
                .id("sn-exp-1")
                .suite("suite-x")
                .booking(null)
                .status("blocked")
                .dates(List.of(LocalDate.of(2026, 12, 26)))
                .expiresAt(Instant.now().minusSeconds(60))
                .build();

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());
        when(suiteNightRepository.findBySuiteIn(anyList()))
                .thenReturn(List.of(externalBlock, heldNoBooking, expiredBlock));

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertTrue(ics.contains("UID:block-sn-ext-1-1@palaciodelmar.co"), "Bloqueo externo debe aparecer");
        assertTrue(ics.contains("SUMMARY:Bloqueado (Bloqueo externo)"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261224"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261225"), "1 noche -> DTEND exclusivo al día siguiente");
        assertFalse(ics.contains("sn-held-1"), "Hold sin reserva no debe emitirse como bloqueo");
        assertFalse(ics.contains("sn-exp-1"), "Bloqueo expirado no debe emitirse");
    }

    @Test
    @DisplayName("SuiteNight con booking asignado no se duplica como bloqueo (ya está cubierto por la reserva)")
    void nightsWithBookingAreNotDuplicatedAsBlocks() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of(
                SuiteNight.builder()
                        .id("sn-bk-1")
                        .suite("suite-x")
                        .booking("BK-OTRA")
                        .status("blocked")
                        .dates(List.of(LocalDate.of(2026, 12, 1)))
                        .build()
        ));

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertFalse(ics.contains("sn-bk-1"), "Noche con reserva asignada no debe emitirse como bloqueo duplicado");
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @DisplayName("Bloqueo con fechas desordenadas, duplicadas y dos rangos genera UIDs únicos por rango")
    void messyDatesProduceDistinctUidsPerRange() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        SuiteNight messy = SuiteNight.builder()
                .id("sn-messy")
                .suite("suite-x")
                .booking(null)
                .status("maintenance")
                .dates(List.of(
                        LocalDate.of(2026, 10, 5),
                        LocalDate.of(2026, 10, 3),
                        LocalDate.of(2026, 10, 4), // rango 1: 3,4,5 (desordenado)
                        LocalDate.of(2026, 10, 4), // duplicado
                        LocalDate.of(2026, 10, 10), // rango 2: 10
                        LocalDate.of(2026, 10, 12)  // rango 3: 12
                ))
                .build();

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of(messy));

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");

        assertEquals(3, ics.split("BEGIN:VEVENT", -1).length - 1, "Debe emitir exactamente 3 VEVENT (3 rangos)");
        assertTrue(ics.contains("UID:block-sn-messy-1@palaciodelmar.co"));
        assertTrue(ics.contains("UID:block-sn-messy-2@palaciodelmar.co"));
        assertTrue(ics.contains("UID:block-sn-messy-3@palaciodelmar.co"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261003"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261010"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261011"), "Rango de 1 noche del 10 -> DTEND el 11");
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261012"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261013"), "Rango de 1 noche -> DTEND al día siguiente");
        assertFalse(ics.contains("DTEND;VALUE=DATE:20261005"), "No debe existir DTEND intermedio del duplicado");
    }

    @Test
    @DisplayName("SuiteNight con lista de fechas null o vacía no rompe el feed")
    void nullOrEmptyDatesDoNotCrash() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());

        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of());
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of(
                SuiteNight.builder().id("sn-empty").suite("suite-x").booking(null).status("blocked").build(),
                SuiteNight.builder().id("sn-nulldates").suite("suite-x").booking(null).status("maintenance").dates(null).build()
        ));

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertNotNull(ics);
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    // --- Escenarios de suites / propiedades que un anfitrión puede dejar en estados raros ---

    @Test
    @DisplayName("Feed de suite huérfana (propertyId inexistente) no explota: usa fallback 'Alojamiento'")
    void orphanSuiteDoesNotCrashFeed() {
        Suite orphan = Suite.builder()
                .id("suite-orphan")
                .propertyId("prop-que-ya-no-existe")
                .name("Suite Huérfana")
                .active(true)
                .build();

        when(suiteRepository.findById("suite-orphan")).thenReturn(Optional.of(orphan));
        when(propertyRepository.findById("prop-que-ya-no-existe")).thenReturn(Optional.empty());
        when(bookingRepository.findBySuite("suite-orphan")).thenReturn(List.of());
        when(suiteNightRepository.findBySuite("suite-orphan")).thenReturn(List.of());

        String ics = icalService.generateSuiteCalendarIcs("suite-orphan");
        assertNotNull(ics, "Suite huérfana no debe romper el feed");
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("(Alojamiento)"), "Debe usar fallback cuando la propiedad no existe");
    }

    @Test
    @DisplayName("Feed de suite cuya propiedad asociada está inactiva responde 404 (no filtra disponibilidad de propiedad desactivada)")
    void suiteWithInactiveParentPropertyReturns404() {
        Suite child = Suite.builder()
                .id("suite-child")
                .propertyId("prop-inactive-parent")
                .name("Suite Niña")
                .active(true)
                .build();
        Property parent = Property.builder()
                .id("prop-inactive-parent")
                .name("Padre Inactivo")
                .active(false)
                .build();

        when(suiteRepository.findById("suite-child")).thenReturn(Optional.of(child));
        when(propertyRepository.findById("prop-inactive-parent")).thenReturn(Optional.of(parent));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> icalService.generateSuiteCalendarIcs("suite-child"));
        assertEquals("La propiedad asociada se encuentra inactiva", ex.getMessage());
    }

    @Test
    @DisplayName("Los feeds públicos rechazan propiedades pendientes o rechazadas")
    void unapprovedPropertyCalendarReturns404() {
        for (String status : List.of("PENDING", "REJECTED")) {
            Property unapproved = Property.builder()
                    .id("prop-unapproved")
                    .name("No publicada")
                    .status(status)
                    .active(true)
                    .build();
            when(propertyRepository.findById("prop-unapproved")).thenReturn(Optional.of(unapproved));

            assertThrows(ResourceNotFoundException.class,
                    () -> icalService.generatePropertyCalendarIcs("prop-unapproved"));
        }
    }

    @Test
    @DisplayName("El feed de suite rechaza la propiedad padre pendiente")
    void suiteWithUnapprovedParentPropertyReturns404() {
        Suite child = Suite.builder()
                .id("suite-pending-parent")
                .propertyId("prop-pending-parent")
                .name("Suite pendiente")
                .active(true)
                .build();
        Property parent = Property.builder()
                .id("prop-pending-parent")
                .name("Propiedad pendiente")
                .status("PENDING")
                .active(true)
                .build();

        when(suiteRepository.findById("suite-pending-parent")).thenReturn(Optional.of(child));
        when(propertyRepository.findById("prop-pending-parent")).thenReturn(Optional.of(parent));

        assertThrows(ResourceNotFoundException.class,
                () -> icalService.generateSuiteCalendarIcs("suite-pending-parent"));
    }

    @Test
    @DisplayName("Suite inactiva responde 404 y suite inexistente también")
    void inactiveOrMissingSuiteReturns404() {
        Suite inactive = Suite.builder().id("suite-off").propertyId("p1").name("Apagada").active(false).build();
        when(suiteRepository.findById("suite-off")).thenReturn(Optional.of(inactive));
        when(suiteRepository.findById("suite-404")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> icalService.generateSuiteCalendarIcs("suite-off"));
        assertThrows(ResourceNotFoundException.class, () -> icalService.generateSuiteCalendarIcs("suite-404"));
        assertThrows(ResourceNotFoundException.class, () -> icalService.generateSuiteCalendarIcs(""));
        assertThrows(ResourceNotFoundException.class, () -> icalService.generateSuiteCalendarIcs(null));
    }

    @Test
    @DisplayName("IDs null o blank en feed de propiedad responden 404, y propiedad con active=null se trata como activa (no 404 espuria)")
    void propertyIdEdgeCases() {
        assertThrows(ResourceNotFoundException.class, () -> icalService.generatePropertyCalendarIcs(null));
        assertThrows(ResourceNotFoundException.class, () -> icalService.generatePropertyCalendarIcs("   "));

        // active=null (legacy): Boolean.FALSE.equals(null) es false -> feed se sirve
        Property nullActive = Property.builder()
                .id("prop-nullactive").name("Sin Flag").status("APPROVED").active(null).build();
        when(propertyRepository.findById("prop-nullactive")).thenReturn(Optional.of(nullActive));
        when(suiteRepository.findByPropertyId("prop-nullactive")).thenReturn(List.of());
        when(bookingRepository.findByPropertyId("prop-nullactive")).thenReturn(List.of());
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-nullactive");
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
    }

    // --- Inyección de texto por anfitrión en nombres (RFC 5545 sección 3.3.11) ---

    @Test
    @DisplayName("Nombre de propiedad con comas, punto y coma y saltos de línea se escapa correctamente (no rompe la estructura ICS)")
    void hostInjectsSpecialCharsInPropertyName() {
        Property malicious = Property.builder()
                .id("prop-evil")
                .name("Villa\\; Premium, SA\\nDESCRIPTION:fake\r\nSUMMARY:hacked")
                .status("APPROVED")
                .active(true)
                .build();
        when(propertyRepository.findById("prop-evil")).thenReturn(Optional.of(malicious));
        when(suiteRepository.findByPropertyId("prop-evil")).thenReturn(List.of());
        when(bookingRepository.findByPropertyId("prop-evil")).thenReturn(List.of());
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-evil");

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertFalse(ics.contains("SUMMARY:hacked"), "El CRLF inyectado no debe crear una línea SUMMARY falsa");
        assertFalse(ics.contains("\r\nSUMMARY"), "No debe existir un salto de línea sin escapar dentro de valores TEXT");
        long veventCount = ics.split("BEGIN:VEVENT", -1).length - 1;
        assertEquals(0, veventCount, "La inyección no debe generar VEVENTs falsos");
        // Los caracteres especiales deben quedar escapados
        assertTrue(ics.contains("\\;"));
        assertTrue(ics.contains("\\,"));
    }

    @Test
    @DisplayName("Nombre de suite con caracteres especiales también se escapa en DESCRIPTION del feed")
    void hostInjectsSpecialCharsInSuiteName() {
        when(propertyRepository.findById("prop-adv-01")).thenReturn(Optional.of(activeProperty()));
        Suite evilSuite = Suite.builder()
                .id("suite-evil")
                .propertyId("prop-adv-01")
                .name("Suite; Deluxe, 5 estrellas")
                .active(true)
                .build();
        when(suiteRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of(evilSuite));

        Booking b = Booking.builder()
                .id("BK-EVIL")
                .suite("suite-evil")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 12))
                .status("confirmed")
                .build();
        when(bookingRepository.findByPropertyId("prop-adv-01")).thenReturn(List.of(b));
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-adv-01");
        assertTrue(ics.contains("BK-EVIL"));
        assertFalse(ics.contains("Suite; Deluxe"), "El ';' del nombre de suite debe ir escapado en DESCRIPTION");
        assertTrue(ics.contains("Suite\\; Deluxe\\, 5 estrellas"));
    }
}
