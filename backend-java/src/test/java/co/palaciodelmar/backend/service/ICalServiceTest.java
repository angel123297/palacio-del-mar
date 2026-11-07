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
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ICalServiceTest {

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

    private Property property;
    private Suite suite1;
    private Suite suite2;

    @BeforeEach
    void setUp() {
        property = Property.builder()
                .id("prop-caribe-01")
                .name("Palacio del Sol Caribe")
                .active(true)
                .status("APPROVED")
                .build();

        suite1 = Suite.builder()
                .id("suite-101")
                .propertyId("prop-caribe-01")
                .name("Suite Presidencial Vista Mar")
                .active(true)
                .build();

        suite2 = Suite.builder()
                .id("suite-102")
                .propertyId("prop-caribe-01")
                .name("Suite Deluxe Balcón")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("generatePropertyCalendarIcs genera VCALENDAR RFC 5545 válido con reservas y bloqueos sin filtrar PII")
    void testGeneratePropertyCalendarIcs_Success() {
        when(propertyRepository.findById("prop-caribe-01")).thenReturn(Optional.of(property));
        when(suiteRepository.findByPropertyId("prop-caribe-01")).thenReturn(List.of(suite1, suite2));

        Booking booking1 = Booking.builder()
                .id("BK-100")
                .propertyId("prop-caribe-01")
                .suite("suite-101")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 15))
                .status("confirmed")
                .guestDetails(Booking.GuestDetails.builder().fullName("Juan Secreto").email("secreto@huesped.com").phone("+573009999999").build())
                .build();

        when(bookingRepository.findByPropertyId("prop-caribe-01")).thenReturn(List.of(booking1));

        SuiteNight maintenanceBlock = SuiteNight.builder()
                .id("sn-block-1")
                .suite("suite-102")
                .booking(null)
                .status("maintenance")
                .dates(List.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21)))
                .build();

        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of(maintenanceBlock));

        String ics = icalService.generatePropertyCalendarIcs("prop-caribe-01");

        assertNotNull(ics);
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        assertTrue(ics.contains("VERSION:2.0"));
        assertTrue(ics.contains("PRODID:-//Palacio del Mar//Marketplace Calendar Feed//ES"));
        assertTrue(ics.contains("X-WR-CALNAME:Palacio del Mar - Palacio del Sol Caribe"));
        assertTrue(ics.contains("X-WR-TIMEZONE:America/Bogota"));

        // Evento de reserva
        assertTrue(ics.contains("UID:booking-BK-100@palaciodelmar.co"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261110"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261115"));
        assertTrue(ics.contains("SUMMARY:Reservado (Palacio del Mar)"));
        assertTrue(ics.contains("DESCRIPTION:Reserva confirmada en Suite Presidencial Vista Mar (Palacio del Sol Caribe)"));
        assertTrue(ics.contains("TRANSP:OPAQUE"));

        // Evento de mantenimiento
        assertTrue(ics.contains("UID:block-sn-block-1-1@palaciodelmar.co"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261120"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261122")); // 20 y 21 ocupados -> fin el 22
        assertTrue(ics.contains("SUMMARY:Bloqueado (Mantenimiento)"));

        // Aislamiento de privacidad (No PII)
        assertFalse(ics.contains("Juan Secreto"), "El feed público de iCal jamás debe exponer el nombre del huésped");
        assertFalse(ics.contains("secreto@huesped.com"), "El feed público jamás debe exponer el email del huésped");
        assertFalse(ics.contains("+573009999999"), "El feed público jamás debe exponer el teléfono del huésped");
    }

    @Test
    @DisplayName("generatePropertyCalendarIcs lanza ResourceNotFoundException si la propiedad no existe")
    void testGeneratePropertyCalendarIcs_NotFound_ThrowsException() {
        when(propertyRepository.findById("prop-inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> icalService.generatePropertyCalendarIcs("prop-inexistente"));
    }

    @Test
    @DisplayName("generatePropertyCalendarIcs lanza ResourceNotFoundException si la propiedad está inactiva")
    void testGeneratePropertyCalendarIcs_InactiveProperty_ThrowsException() {
        property.setActive(false);
        when(propertyRepository.findById("prop-caribe-01")).thenReturn(Optional.of(property));

        assertThrows(ResourceNotFoundException.class, () -> icalService.generatePropertyCalendarIcs("prop-caribe-01"));
    }

    @Test
    @DisplayName("generateSuiteCalendarIcs genera feed exclusivo para una suite")
    void testGenerateSuiteCalendarIcs_Success() {
        when(suiteRepository.findById("suite-101")).thenReturn(Optional.of(suite1));
        when(propertyRepository.findById("prop-caribe-01")).thenReturn(Optional.of(property));

        Booking booking = Booking.builder()
                .id("BK-200")
                .propertyId("prop-caribe-01")
                .suite("suite-101")
                .checkIn(LocalDate.of(2026, 12, 1))
                .checkOut(LocalDate.of(2026, 12, 5))
                .status("checked_in")
                .build();

        when(bookingRepository.findBySuite("suite-101")).thenReturn(List.of(booking));
        when(suiteNightRepository.findBySuite("suite-101")).thenReturn(List.of());

        String ics = icalService.generateSuiteCalendarIcs("suite-101");

        assertNotNull(ics);
        assertTrue(ics.contains("X-WR-CALNAME:Palacio del Mar - Suite Presidencial Vista Mar (Palacio del Sol Caribe)"));
        assertTrue(ics.contains("UID:booking-BK-200@palaciodelmar.co"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261201"));
        assertTrue(ics.contains("DTEND;VALUE=DATE:20261205"));
    }

    @Test
    @DisplayName("generateSuiteCalendarIcs valida pertenencia a propiedad")
    void testGenerateSuiteCalendarIcs_PropertyMismatch_ThrowsException() {
        when(suiteRepository.findById("suite-101")).thenReturn(Optional.of(suite1));

        assertThrows(ResourceNotFoundException.class, () -> icalService.generateSuiteCalendarIcs("otra-propiedad-999", "suite-101"));
    }

    @Test
    @DisplayName("Excluye reservas canceladas y expiradas")
    void testExcludesCancelledAndExpiredBookings() {
        when(propertyRepository.findById("prop-caribe-01")).thenReturn(Optional.of(property));
        when(suiteRepository.findByPropertyId("prop-caribe-01")).thenReturn(List.of(suite1));

        Booking cancelled = Booking.builder()
                .id("BK-CANCELLED")
                .propertyId("prop-caribe-01")
                .suite("suite-101")
                .checkIn(LocalDate.of(2026, 11, 1))
                .checkOut(LocalDate.of(2026, 11, 3))
                .status("cancelled")
                .build();

        Booking expiredPending = Booking.builder()
                .id("BK-EXPIRED")
                .propertyId("prop-caribe-01")
                .suite("suite-101")
                .checkIn(LocalDate.of(2026, 11, 5))
                .checkOut(LocalDate.of(2026, 11, 8))
                .status("pending")
                .expiresAt(Instant.now().minusSeconds(3600)) // expirado hace 1h
                .build();

        when(bookingRepository.findByPropertyId("prop-caribe-01")).thenReturn(List.of(cancelled, expiredPending));
        when(suiteNightRepository.findBySuiteIn(anyList())).thenReturn(List.of());

        String ics = icalService.generatePropertyCalendarIcs("prop-caribe-01");

        assertFalse(ics.contains("BK-CANCELLED"));
        assertFalse(ics.contains("BK-EXPIRED"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    @DisplayName("groupContiguousDates agrupa fechas consecutivas y separa discontinuas")
    void testGroupContiguousDates() {
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 3),
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 11)
        );

        List<ICalService.DateRange> ranges = ICalService.groupContiguousDates(dates);

        assertEquals(2, ranges.size());
        assertEquals(LocalDate.of(2026, 10, 1), ranges.get(0).start());
        assertEquals(LocalDate.of(2026, 10, 3), ranges.get(0).endInclusive());
        assertEquals(LocalDate.of(2026, 10, 10), ranges.get(1).start());
        assertEquals(LocalDate.of(2026, 10, 11), ranges.get(1).endInclusive());
    }

    @Test
    @DisplayName("escapeIcsText escapa caracteres especiales según RFC 5545")
    void testEscapeIcsText() {
        assertEquals("Texto normal", ICalService.escapeIcsText("Texto normal"));
        assertEquals("Linea 1\\nLinea 2", ICalService.escapeIcsText("Linea 1\nLinea 2"));
        assertEquals("Punto y coma\\; coma\\, y barra\\\\", ICalService.escapeIcsText("Punto y coma; coma, y barra\\"));
        assertEquals("", ICalService.escapeIcsText(null));
    }
}
