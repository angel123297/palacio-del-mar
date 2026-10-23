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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de exportación de calendarios en estándar iCalendar (RFC 5545).
 * Genera feeds sincronizables (.ics) para Airbnb, Booking.com, Google Calendar y PMS externos.
 * Aplica anonimización de datos personales para garantizar la privacidad del huésped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ICalService {

    private final PropertyRepository propertyRepository;
    private final SuiteRepository suiteRepository;
    private final BookingRepository bookingRepository;
    private final SuiteNightRepository suiteNightRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CRLF = "\r\n";

    private static final Set<String> CONFIRMED_STATUSES = Set.of(
            "confirmed", "checked_in", "checked_out", "completed"
    );

    /**
     * Genera el feed iCalendar (.ics) para una propiedad completa (todas sus habitaciones y bloqueos).
     */
    public String generatePropertyCalendarIcs(String propertyId) {
        if (propertyId == null || propertyId.isBlank()) {
            throw new ResourceNotFoundException("ID de propiedad no especificado");
        }

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad no encontrada con ID: " + propertyId));

        if (!"APPROVED".equalsIgnoreCase(property.getStatus())) {
            throw new ResourceNotFoundException("La propiedad no se encuentra disponible");
        }
        if (Boolean.FALSE.equals(property.getActive())) {
            throw new ResourceNotFoundException("La propiedad se encuentra inactiva");
        }

        List<Suite> suites = suiteRepository.findByPropertyId(propertyId);
        Map<String, String> suiteNames = suites.stream()
                .collect(Collectors.toMap(Suite::getId, Suite::getName, (a, b) -> a));
        List<String> suiteIds = suites.stream().map(Suite::getId).toList();

        // Reservas activas de la propiedad
        Instant now = Instant.now();
        List<Booking> activeBookings = bookingRepository.findByPropertyId(propertyId).stream()
                .filter(b -> isBookingOccupyingDates(b, now))
                .toList();

        // Bloqueos de disponibilidad sin reserva (mantenimiento o bloqueos de integración/anfitrión)
        List<SuiteNight> blocks = suiteNightRepository.findBySuiteIn(suiteIds).stream()
                .filter(this::isMaintenanceOrExternalBlock)
                .toList();

        String cleanPropName = cleanCalendarTitle(property.getName());
        return buildIcsCalendar(
                "Palacio del Mar - " + cleanPropName,
                cleanPropName,
                activeBookings,
                blocks,
                suiteNames
        );
    }

    /**
     * Genera el feed iCalendar (.ics) para una suite específica (usado para sincronizar con un listado puntual en Airbnb).
     */
    public String generateSuiteCalendarIcs(String suiteId) {
        if (suiteId == null || suiteId.isBlank()) {
            throw new ResourceNotFoundException("ID de habitación no especificado");
        }

        Suite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada con ID: " + suiteId));

        if (Boolean.FALSE.equals(suite.getActive())) {
            throw new ResourceNotFoundException("La habitación se encuentra inactiva");
        }

        String propertyName = "Alojamiento";
        if (suite.getPropertyId() != null) {
            Property p = propertyRepository.findById(suite.getPropertyId()).orElse(null);
            if (p != null) {
                if (Boolean.FALSE.equals(p.getActive())) {
                    throw new ResourceNotFoundException("La propiedad asociada se encuentra inactiva");
                }
                if (!"APPROVED".equalsIgnoreCase(p.getStatus())) {
                    throw new ResourceNotFoundException("La propiedad asociada no se encuentra disponible");
                }
                propertyName = p.getName();
            }
        }

        Instant now = Instant.now();
        List<Booking> activeBookings = bookingRepository.findBySuite(suiteId).stream()
                .filter(b -> isBookingOccupyingDates(b, now))
                .toList();

        List<SuiteNight> blocks = suiteNightRepository.findBySuite(suiteId).stream()
                .filter(this::isMaintenanceOrExternalBlock)
                .toList();

        Map<String, String> suiteNames = Map.of(suite.getId(), suite.getName());

        String cleanSuiteName = cleanCalendarTitle(suite.getName());
        String cleanPropName = cleanCalendarTitle(propertyName);

        return buildIcsCalendar(
                "Palacio del Mar - " + cleanSuiteName + " (" + cleanPropName + ")",
                cleanPropName,
                activeBookings,
                blocks,
                suiteNames
        );
    }

    /**
     * Genera el feed validando que la suite pertenezca a la propiedad dada.
     */
    public String generateSuiteCalendarIcs(String propertyId, String suiteId) {
        if (suiteId == null || suiteId.isBlank()) {
            throw new ResourceNotFoundException("ID de habitación no especificado");
        }
        Suite suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new ResourceNotFoundException("Habitación no encontrada con ID: " + suiteId));

        if (propertyId != null && !propertyId.equals(suite.getPropertyId())) {
            throw new ResourceNotFoundException("La habitación no pertenece a la propiedad especificada");
        }

        return generateSuiteCalendarIcs(suiteId);
    }

    // --- Helpers de construcción de VCALENDAR / VEVENT (RFC 5545) ---

    private String buildIcsCalendar(
            String calendarName,
            String propertyName,
            List<Booking> bookings,
            List<SuiteNight> blocks,
            Map<String, String> suiteNames
    ) {
        StringBuilder sb = new StringBuilder();
        String dtStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now());

        sb.append("BEGIN:VCALENDAR").append(CRLF);
        sb.append("VERSION:2.0").append(CRLF);
        sb.append("PRODID:-//Palacio del Mar//Marketplace Calendar Feed//ES").append(CRLF);
        sb.append("CALSCALE:GREGORIAN").append(CRLF);
        sb.append("METHOD:PUBLISH").append(CRLF);
        sb.append("X-WR-CALNAME:").append(escapeIcsText(calendarName)).append(CRLF);
        sb.append("X-WR-TIMEZONE:America/Bogota").append(CRLF);

        // 1. Eventos de reservas
        for (Booking booking : bookings) {
            if (booking.getCheckIn() == null || booking.getCheckOut() == null || !booking.getCheckOut().isAfter(booking.getCheckIn())) {
                continue;
            }

            String suiteName = booking.getSuite() != null ? suiteNames.getOrDefault(booking.getSuite(), "Suite") : "Alojamiento";
            String uid = "booking-" + booking.getId() + "@palaciodelmar.co";
            String dtStart = booking.getCheckIn().format(DATE_FORMATTER);
            String dtEnd = booking.getCheckOut().format(DATE_FORMATTER); // En RFC 5545 VALUE=DATE, DTEND es no inclusivo (fecha de check-out)

            sb.append("BEGIN:VEVENT").append(CRLF);
            sb.append("UID:").append(uid).append(CRLF);
            sb.append("DTSTAMP:").append(dtStamp).append(CRLF);
            sb.append("DTSTART;VALUE=DATE:").append(dtStart).append(CRLF);
            sb.append("DTEND;VALUE=DATE:").append(dtEnd).append(CRLF);
            // Máxima privacidad: no filtrar nombres, emails ni teléfonos en feeds públicos de sincronización
            sb.append("SUMMARY:Reservado (Palacio del Mar)").append(CRLF);
            sb.append("DESCRIPTION:").append(escapeIcsText("Reserva confirmada en " + suiteName + " (" + propertyName + ")")).append(CRLF);
            sb.append("STATUS:CONFIRMED").append(CRLF);
            sb.append("TRANSP:OPAQUE").append(CRLF);
            sb.append("END:VEVENT").append(CRLF);
        }

        // 2. Eventos de bloqueos de disponibilidad o mantenimiento
        for (SuiteNight block : blocks) {
            if (block.getDates() == null || block.getDates().isEmpty()) {
                continue;
            }

            List<DateRange> ranges = groupContiguousDates(block.getDates());
            String suiteName = block.getSuite() != null ? suiteNames.getOrDefault(block.getSuite(), "Habitación") : "Alojamiento";
            String reason = "maintenance".equalsIgnoreCase(block.getStatus()) ? "Mantenimiento" : "Bloqueo externo";

            int rangeIndex = 1;
            for (DateRange range : ranges) {
                String uid = "block-" + block.getId() + "-" + rangeIndex + "@palaciodelmar.co";
                String dtStart = range.start.format(DATE_FORMATTER);
                String dtEnd = range.endInclusive.plusDays(1).format(DATE_FORMATTER); // DTEND exclusivo

                sb.append("BEGIN:VEVENT").append(CRLF);
                sb.append("UID:").append(uid).append(CRLF);
                sb.append("DTSTAMP:").append(dtStamp).append(CRLF);
                sb.append("DTSTART;VALUE=DATE:").append(dtStart).append(CRLF);
                sb.append("DTEND;VALUE=DATE:").append(dtEnd).append(CRLF);
                sb.append("SUMMARY:Bloqueado (").append(reason).append(")").append(CRLF);
                sb.append("DESCRIPTION:").append(escapeIcsText("Fechas no disponibles por " + reason + " - " + suiteName)).append(CRLF);
                sb.append("STATUS:CONFIRMED").append(CRLF);
                sb.append("TRANSP:OPAQUE").append(CRLF);
                sb.append("END:VEVENT").append(CRLF);

                rangeIndex++;
            }
        }

        sb.append("END:VCALENDAR").append(CRLF);
        return sb.toString();
    }

    private boolean isBookingOccupyingDates(Booking b, Instant now) {
        if (b == null || b.getStatus() == null) return false;
        String status = b.getStatus().toLowerCase();
        if (CONFIRMED_STATUSES.contains(status)) {
            return true;
        }
        // Las reservas pendientes solo ocupan si no han expirado
        if ("pending".equals(status)) {
            return b.getExpiresAt() == null || b.getExpiresAt().isAfter(now);
        }
        return false;
    }

    private boolean isMaintenanceOrExternalBlock(SuiteNight sn) {
        if (sn == null) return false;
        if (sn.getBooking() != null && !sn.getBooking().isBlank()) {
            return false; // Ya está representada por una reserva
        }
        if (sn.getExpiresAt() != null && sn.getExpiresAt().isBefore(Instant.now())) {
            return false; // Bloqueo temporal ya expirado
        }
        String status = sn.getStatus();
        return "blocked".equalsIgnoreCase(status) || "maintenance".equalsIgnoreCase(status);
    }

    /**
     * Agrupa una lista de fechas en intervalos continuos.
     */
    static List<DateRange> groupContiguousDates(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) return Collections.emptyList();

        List<LocalDate> sorted = dates.stream().distinct().sorted().toList();
        List<DateRange> ranges = new ArrayList<>();

        LocalDate start = sorted.get(0);
        LocalDate prev = start;

        for (int i = 1; i < sorted.size(); i++) {
            LocalDate current = sorted.get(i);
            if (current.equals(prev.plusDays(1))) {
                prev = current;
            } else {
                ranges.add(new DateRange(start, prev));
                start = current;
                prev = current;
            }
        }
        ranges.add(new DateRange(start, prev));
        return ranges;
    }

    static class DateRange {
        private final LocalDate start;
        private final LocalDate endInclusive;

        public DateRange(LocalDate start, LocalDate endInclusive) {
            this.start = start;
            this.endInclusive = endInclusive;
        }

        public LocalDate start() {
            return start;
        }

        public LocalDate endInclusive() {
            return endInclusive;
        }
    }

    /**
     * Escapa caracteres especiales en campos TEXT según RFC 5545 sección 3.3.11.
     */
    static String escapeIcsText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    /**
     * Sanitiza el nombre de calendario o propiedad para encabezados iCalendar de una sola línea (ej. X-WR-CALNAME).
     * Trunca ante saltos de línea inyectados (CRLF) para prevenir header injection según RFC 5545 sección 3.1.
     */
    static String cleanCalendarTitle(String title) {
        if (title == null || title.isBlank()) return "Alojamiento";
        int nlIndex = -1;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c == '\r' || c == '\n') {
                nlIndex = i;
                break;
            }
        }
        String clean = nlIndex >= 0 ? title.substring(0, nlIndex) : title;
        return clean.trim();
    }
}
