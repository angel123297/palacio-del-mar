package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Servicio transaccional de correo electrónico compatible con Mailpit (SMTP localhost:1025).
 * Envía notificaciones a huéspedes (vouchers con PIN digital y calendario) y anfitriones
 * (nuevas reservas, liquidaciones del 92%, moderación de propiedades y fallas de webhook).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private PropertyRepository propertyRepository;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl = "http://localhost:4200";

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("soporte@palaciodelmar.co");
            helper.setTo(toEmail);
            helper.setSubject("Verifica tu cuenta - Palacio del Mar");

            String verifyLink = (baseUrl != null ? baseUrl : "http://localhost:4200") + "/verificar-email/" + token;
            String htmlMsg = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h1 style=\"color:#c9a96e; font-size:24px; margin-top:0;\">Bienvenido a Palacio del Mar</h1>"
                    + "<p>Gracias por unirte a nuestro marketplace de hospedaje.</p>"
                    + "<p>Por favor, confirma tu dirección de correo electrónico haciendo clic en el siguiente enlace:</p>"
                    + "<p style=\"margin:20px 0;\"><a href=\"" + verifyLink + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:12px 24px; text-decoration:none; border-radius:6px; font-weight:bold; display:inline-block;\">Verificar Cuenta</a></p>"
                    + "<p style=\"color:#888; font-size:12px;\">Si no solicitaste este registro, puedes ignorar este mensaje.</p>"
                    + "</div>";

            helper.setText(htmlMsg, true);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar correo de verificación a {} (servidor SMTP no disponible): {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendBookingConfirmation(Booking booking) {
        if (booking == null) return;
        try {
            String email = null;
            if (booking.getUser() != null) {
                email = userRepository.findById(booking.getUser()).map(User::getEmail).orElse(null);
            }
            if ((email == null || email.isBlank()) && booking.getGuestDetails() != null) {
                email = booking.getGuestDetails().getEmail();
            }
            if (email == null || email.isBlank()) {
                email = "cliente@ejemplo.com";
            }

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("reservas@palaciodelmar.co");
            helper.setTo(email);
            helper.setSubject("Confirmación de tu reserva y voucher de acceso - Palacio del Mar");

            String pinCode = getAccessPin(booking);
            String voucherLink = (baseUrl != null ? baseUrl : "http://localhost:4200") + "/reservas/" + booking.getId() + "/comprobante";

            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h1 style=\"color:#c9a96e; font-size:24px; margin-top:0;\">¡Reserva Confirmada en Palacio del Mar!</h1>"
                    + "<p>Tu pago ha sido procesado exitosamente y tu suite está asegurada.</p>"
                    + "<div style=\"background:#1a1a1a; border:2px solid #c9a96e; padding:18px; border-radius:8px; text-align:center; margin:20px 0;\">"
                    + "<p style=\"color:#c9a96e; text-transform:uppercase; font-size:12px; letter-spacing:2px; margin:0 0 5px 0;\">PIN de Acceso Digital a la Suite</p>"
                    + "<span style=\"font-size:32px; letter-spacing:8px; font-weight:bold; color:#ffffff;\">" + pinCode + "</span>"
                    + "<p style=\"color:#aaa; font-size:11px; margin:5px 0 0 0;\">Ingresa este código en la cerradura inteligente el día de tu llegada.</p>"
                    + "</div>"
                    + "<table style=\"width:100%; border-collapse:collapse; margin-bottom:15px; color:#ddd; font-size:14px;\">"
                    + "<tr><td style=\"padding:6px 0;\"><strong>Código de Reserva:</strong></td><td>" + booking.getId() + "</td></tr>"
                    + "<tr><td style=\"padding:6px 0;\"><strong>Check-in:</strong></td><td>" + booking.getCheckIn() + " (a partir de las 15:00)</td></tr>"
                    + "<tr><td style=\"padding:6px 0;\"><strong>Check-out:</strong></td><td>" + booking.getCheckOut() + " (hasta las 13:00)</td></tr>"
                    + "<tr><td style=\"padding:6px 0;\"><strong>Total Pagado:</strong></td><td>COP $" + (booking.getPaidAmount() != null ? booking.getPaidAmount() : booking.getTotalPrice()) + "</td></tr>"
                    + "</table>"
                    + "<p style=\"margin:20px 0;\"><a href=\"" + voucherLink + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:12px 24px; text-decoration:none; border-radius:6px; font-weight:bold; display:inline-block;\">Ver Comprobante con QR</a></p>"
                    + "<p style=\"font-size:12px; color:#888;\">Adjuntamos tu comprobante en PDF y el archivo de calendario .ics.</p>"
                    + "</div>";

            helper.setText(html, true);

            // Adjuntos PDF e ICS
            try {
                byte[] pdfBytes = generateReceiptPdf(booking);
                helper.addAttachment("Comprobante_Reserva_" + booking.getId() + ".pdf", new ByteArrayResource(pdfBytes));
            } catch (Exception pdfEx) {
                log.warn("No se pudo generar adjunto PDF para reserva {}: {}", booking.getId(), pdfEx.getMessage());
            }

            try {
                byte[] icsBytes = generateIcsFile(booking);
                helper.addAttachment("Reserva_" + booking.getId() + ".ics", new ByteArrayResource(icsBytes));
            } catch (Exception icsEx) {
                log.warn("No se pudo generar adjunto ICS para reserva {}: {}", booking.getId(), icsEx.getMessage());
            }

            javaMailSender.send(message);
            log.info("Voucher de reserva {} enviado a {}", booking.getId(), email);
        } catch (Exception e) {
            log.warn("No se pudo enviar correo de confirmación para reserva {} (servidor SMTP no disponible): {}",
                    booking != null ? booking.getId() : "N/A", e.getMessage());
        }
    }

    @Async
    public void sendHostBookingNotification(Booking booking) {
        if (booking == null) return;
        try {
            String propertyId = booking.getPropertyId();
            Property property = null;
            if (propertyId != null && propertyRepository != null) {
                property = propertyRepository.findById(propertyId).orElse(null);
            }

            String ownerId = property != null ? property.getOwnerId() : null;
            String hostEmail = null;
            if (ownerId != null) {
                hostEmail = userRepository.findById(ownerId).map(User::getEmail).orElse(null);
            }

            if (hostEmail == null || hostEmail.isBlank()) {
                log.debug("sendHostBookingNotification: no se encontró email de anfitrión para la reserva {}", booking.getId());
                return;
            }

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("notificaciones@palaciodelmar.co");
            helper.setTo(hostEmail);
            String propertyName = property != null ? property.getName() : "Tu alojamiento";
            helper.setSubject("¡Nueva reserva confirmada en " + propertyName + "!");

            String guestName = (booking.getGuestDetails() != null && booking.getGuestDetails().getFullName() != null)
                    ? booking.getGuestDetails().getFullName()
                    : "Huésped";
            String guestPhone = (booking.getGuestDetails() != null && booking.getGuestDetails().getPhone() != null)
                    ? booking.getGuestDetails().getPhone()
                    : "No especificado";

            BigDecimal hostPayout = booking.getHostPayout() != null
                    ? booking.getHostPayout()
                    : (booking.getTotalPrice() != null ? booking.getTotalPrice().multiply(new BigDecimal("0.92")) : BigDecimal.ZERO);

            String hostUrl = (baseUrl != null ? baseUrl : "http://localhost:4200") + "/anfitrion";

            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h1 style=\"color:#c9a96e; font-size:22px; margin-top:0;\">¡Nueva Reserva Confirmada!</h1>"
                    + "<p>Has recibido una nueva reserva garantizada en <strong>" + propertyName + "</strong>.</p>"
                    + "<div style=\"background:#1a1a1a; border-left:4px solid #c9a96e; padding:15px; margin:15px 0;\">"
                    + "<p style=\"margin:4px 0;\"><strong>Huésped:</strong> " + guestName + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Teléfono:</strong> " + guestPhone + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Check-in:</strong> " + booking.getCheckIn() + " (15:00)</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Check-out:</strong> " + booking.getCheckOut() + " (13:00)</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Huéspedes:</strong> " + booking.getGuests() + "</p>"
                    + "<p style=\"margin:8px 0 0 0; color:#c9a96e; font-size:16px;\"><strong>Tu Liquidación Neta (92%):</strong> COP $" + hostPayout + "</p>"
                    + "</div>"
                    + "<p><a href=\"" + hostUrl + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:10px 20px; text-decoration:none; border-radius:4px; font-weight:bold; display:inline-block;\">Ver en el Panel de Anfitrión</a></p>"
                    + "</div>";

            helper.setText(html, true);
            javaMailSender.send(message);
            log.info("Notificación de nueva reserva enviada exitosamente al anfitrión {}", hostEmail);
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de reserva al anfitrión para la reserva {}: {}",
                    booking != null ? booking.getId() : "N/A", e.getMessage());
        }
    }

    @Async
    public void sendPropertyStatusNotification(Property property, String status) {
        if (property == null || property.getOwnerId() == null) return;
        try {
            String hostEmail = userRepository.findById(property.getOwnerId()).map(User::getEmail).orElse(null);
            if (hostEmail == null || hostEmail.isBlank()) return;

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("moderacion@palaciodelmar.co");
            helper.setTo(hostEmail);

            boolean approved = "APPROVED".equalsIgnoreCase(status);
            String subject = approved
                    ? "¡Tu alojamiento '" + property.getName() + "' ha sido aprobado!"
                    : "Actualización sobre tu alojamiento '" + property.getName() + "'";
            helper.setSubject(subject);

            String hostUrl = (baseUrl != null ? baseUrl : "http://localhost:4200") + "/anfitrion";

            String html;
            if (approved) {
                html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                        + "<h1 style=\"color:#c9a96e; font-size:22px; margin-top:0;\">¡Felicitaciones! Alojamiento Aprobado</h1>"
                        + "<p>Tu alojamiento <strong>" + property.getName() + "</strong> ha superado la revisión administrativa.</p>"
                        + "<p>Ya se encuentra publicado y visible para todos los viajeros en el catálogo del marketplace.</p>"
                        + "<p><a href=\"" + hostUrl + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:10px 20px; text-decoration:none; border-radius:4px; font-weight:bold; display:inline-block;\">Administrar Disponibilidad</a></p>"
                        + "</div>";
            } else {
                html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                        + "<h1 style=\"color:#e53e3e; font-size:22px; margin-top:0;\">Alojamiento No Aprobado</h1>"
                        + "<p>Tu solicitud para el alojamiento <strong>" + property.getName() + "</strong> ha sido evaluada y requiere ajustes en su descripción, fotografías o documentación.</p>"
                        + "<p>Por favor, ingresa a tu panel de anfitrión para actualizar los datos requeridos y solicitar una nueva revisión.</p>"
                        + "<p><a href=\"" + hostUrl + "\" style=\"background:#e53e3e; color:#fff; padding:10px 20px; text-decoration:none; border-radius:4px; font-weight:bold; display:inline-block;\">Revisar Alojamiento</a></p>"
                        + "</div>";
            }

            helper.setText(html, true);
            javaMailSender.send(message);
            log.info("Notificación de estado de propiedad ({}) enviada al anfitrión {}", status, hostEmail);
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de estado de propiedad para {}: {}",
                    property != null ? property.getId() : "N/A", e.getMessage());
        }
    }

    @Async
    public void sendWebhookFailureAlert(WebhookDelivery delivery) {
        if (delivery == null || delivery.getOwnerId() == null) return;
        try {
            String hostEmail = userRepository.findById(delivery.getOwnerId()).map(User::getEmail).orElse(null);
            if (hostEmail == null || hostEmail.isBlank()) return;

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("alertas@palaciodelmar.co");
            helper.setTo(hostEmail);
            helper.setSubject("Alerta: Fallo persistente en entregas de Webhook - Palacio del Mar");

            String hostUrl = (baseUrl != null ? baseUrl : "http://localhost:4200") + "/anfitrion";

            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h1 style=\"color:#e53e3e; font-size:22px; margin-top:0;\">Alerta de Entrega de Webhook</h1>"
                    + "<p>Detectamos que tu sistema receptor no pudo recibir eventos de webhook tras agotar todos los reintentos automáticos (" + delivery.getMaxAttempts() + " intentos).</p>"
                    + "<div style=\"background:#1a1a1a; border-left:4px solid #e53e3e; padding:15px; margin:15px 0;\">"
                    + "<p style=\"margin:4px 0;\"><strong>URL Destino:</strong> " + delivery.getTargetUrl() + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Evento:</strong> " + delivery.getEventType() + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Código HTTP:</strong> " + delivery.getResponseCode() + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Último Error:</strong> " + (delivery.getErrorMessage() != null ? delivery.getErrorMessage() : "Sin respuesta") + "</p>"
                    + "</div>"
                    + "<p>Puedes reenviar manualmente la entrega desde tu panel de anfitrión una vez verificado tu servidor.</p>"
                    + "<p><a href=\"" + hostUrl + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:10px 20px; text-decoration:none; border-radius:4px; font-weight:bold; display:inline-block;\">Ver Entregas de Webhook</a></p>"
                    + "</div>";

            helper.setText(html, true);
            javaMailSender.send(message);
            log.info("Alerta de webhook fallido enviada al anfitrión {}", hostEmail);
        } catch (Exception e) {
            log.warn("No se pudo enviar alerta de webhook fallido al anfitrión: {}", e.getMessage());
        }
    }

    @Async
    public void sendReviewInvitation(Booking booking) {
        if (booking == null) return;
        try {
            String email = null;
            if (booking.getUser() != null) {
                email = userRepository.findById(booking.getUser()).map(User::getEmail).orElse(null);
            }
            if ((email == null || email.isBlank()) && booking.getGuestDetails() != null) {
                email = booking.getGuestDetails().getEmail();
            }

            if (email == null || email.isBlank()) {
                log.warn("sendReviewInvitation omitido: no se encontró email para la reserva {}", booking.getId());
                return;
            }

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("experiencias@palaciodelmar.co");
            helper.setTo(email);
            helper.setSubject("¿Cómo estuvo tu estadía? - Califica tu experiencia");

            String link = (baseUrl != null ? baseUrl : "http://localhost:4200") + "/reservas/" + booking.getId();
            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h2 style=\"color:#c9a96e; margin-top:0;\">¡Gracias por hospedarte con nosotros!</h2>"
                    + "<p>Esperamos que hayas disfrutado de tu tiempo en Palacio del Mar.</p>"
                    + "<p>Nos encantaría conocer tu opinión para seguir mejorando. Déjanos una reseña verificada de tu estancia:</p>"
                    + "<p style=\"margin:20px 0;\"><a href=\"" + link + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:12px 24px; text-decoration:none; border-radius:6px; font-weight:bold; display:inline-block;\">Calificar mi Estadía</a></p>"
                    + "<p style=\"font-size:12px; color:#888;\">Tu opinión ayuda a futuros viajeros y a nuestros anfitriones.</p>"
                    + "</div>";

            helper.setText(html, true);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar invitación de reseña para la reserva {} (servidor SMTP no disponible): {}", booking.getId(), e.getMessage());
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
            document.add(new Paragraph("PIN de Acceso: " + getAccessPin(booking)));
            document.add(new Paragraph("Check-in: " + booking.getCheckIn() + " (15:00)"));
            document.add(new Paragraph("Check-out: " + booking.getCheckOut() + " (13:00)"));
            document.add(new Paragraph("Huéspedes: " + booking.getGuests()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Subtotal: $" + (booking.getNightlyPrices() != null ? booking.getNightlyPrices().stream().map(p -> p.getPrice()).reduce(BigDecimal.ZERO, BigDecimal::add) : booking.getTotalPrice())));
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
                "DTSTAMP:" + Instant.now().toString().replaceAll("[-:]", "").split("\\.")[0] + "Z\r\n" +
                "DTSTART;VALUE=DATE:" + dtStart + "\r\n" +
                "DTEND;VALUE=DATE:" + dtEnd + "\r\n" +
                "SUMMARY:Reserva en Palacio del Mar\r\n" +
                "DESCRIPTION:Reserva confirmada. ID: " + booking.getId() + " - PIN de Acceso: " + getAccessPin(booking) + "\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";

        return icsContent.getBytes(StandardCharsets.UTF_8);
    }

    private String getAccessPin(Booking booking) {
        String pin = booking.getSmartLockPin();
        return pin == null || pin.isBlank() ? "Pendiente de asignación" : pin;
    }
}
