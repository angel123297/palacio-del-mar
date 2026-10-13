package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.SuiteRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final SuiteRepository suiteRepository;
    private final co.palaciodelmar.backend.repository.UserRepository userRepository;

    @Async
    public void sendBookingConfirmation(Booking booking) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("reservas@palaciodelmar.co");
            String email = userRepository.findById(booking.getUser()).map(co.palaciodelmar.backend.model.User::getEmail).orElse("cliente@ejemplo.com");
            helper.setTo(email);
            helper.setSubject("Confirmación de tu reserva - Palacio del Mar");

            String text = "<h1>¡Gracias por tu reserva en Palacio del Mar!</h1>"
                    + "<p>Tu reserva ha sido confirmada.</p>"
                    + "<p><strong>ID de Reserva:</strong> " + booking.getId() + "</p>"
                    + "<p><strong>Check-in:</strong> " + booking.getCheckIn() + "</p>"
                    + "<p><strong>Check-out:</strong> " + booking.getCheckOut() + "</p>"
                    + "<p><strong>Total:</strong> COP $" + booking.getTotalPrice() + "</p>"
                    + "<br/><p>Adjuntamos el comprobante de pago y tu invitación al calendario.</p>";

            helper.setText(text, true);

            // Generar PDF
            byte[] pdfBytes = generateReceiptPdf(booking);
            helper.addAttachment("Comprobante_Reserva_" + booking.getId() + ".pdf", new ByteArrayResource(pdfBytes));

            // Generar ICS
            byte[] icsBytes = generateIcsFile(booking);
            helper.addAttachment("Reserva_" + booking.getId() + ".ics", new ByteArrayResource(icsBytes));

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Error al enviar el correo de confirmación", e);
        }
    }

    private byte[] generateReceiptPdf(Booking booking) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("PALACIO DEL MAR - COMPROBANTE DE RESERVA"));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("ID Reserva: " + booking.getId()));
            document.add(new Paragraph("Estado: " + booking.getStatus()));
            document.add(new Paragraph("Check-in: " + booking.getCheckIn()));
            document.add(new Paragraph("Check-out: " + booking.getCheckOut()));
            document.add(new Paragraph("Huéspedes: " + booking.getGuests()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Subtotal: $" + (booking.getNightlyPrices() != null ? booking.getNightlyPrices().stream().map(p -> p.getPrice()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add) : booking.getTotalPrice())));
            document.add(new Paragraph("Total Pagado: $" + booking.getPaidAmount()));
            document.add(new Paragraph("Balance Pendiente: $" + booking.getBalanceDue()));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF", e);
        }
    }

    private byte[] generateIcsFile(Booking booking) {
        String dtStart = booking.getCheckIn().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dtEnd = booking.getCheckOut().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String icsContent = "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//Palacio del Mar//Reservas//ES\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + UUID.randomUUID() + "@palaciodelmar.co\r\n" +
                "DTSTAMP:" + java.time.Instant.now().toString().replaceAll("[-:]", "").split("\\.")[0] + "Z\r\n" +
                "DTSTART;VALUE=DATE:" + dtStart + "\r\n" +
                "DTEND;VALUE=DATE:" + dtEnd + "\r\n" +
                "SUMMARY:Reserva en Palacio del Mar\r\n" +
                "DESCRIPTION:Reserva confirmada. ID: " + booking.getId() + "\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";

        return icsContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
