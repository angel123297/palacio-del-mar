package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Payment;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.model.WebhookDelivery;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio transaccional de correo electrónico vía SMTP real con TLS (Gmail por defecto;
 * compatible con Brevo, SendGrid o cualquier proveedor estándar mediante variables SPRING_MAIL_*).
 * Envía notificaciones a huéspedes (verificación de cuenta, resumen de reserva pendiente,
 * confirmación de pago con voucher, PIN digital, PDF y calendario .ics) y anfitriones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Locale LOCALE_ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter HUMAN_DATE_FMT =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", LOCALE_ES_CO);

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private PropertyRepository propertyRepository;

    @Autowired(required = false)
    private SuiteRepository suiteRepository;

    @Autowired(required = false)
    private BranchRepository branchRepository;

    @Autowired(required = false)
    private PaymentRepository paymentRepository;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl = "http://localhost:4200";

    @Value("${spring.mail.username:}")
    private String mailUsername = "";

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean mailAuth = true;

    @Value("${spring.mail.properties.mail.from:palaciodelmar39@gmail.com}")
    private String defaultFromAddress = "palaciodelmar39@gmail.com";

    @PostConstruct
    public void validateMailConfiguration() {
        if (javaMailSender instanceof JavaMailSenderImpl senderImpl) {
            String host = senderImpl.getHost();
            String pwd = senderImpl.getPassword();
            if (host != null && host.toLowerCase(Locale.ROOT).contains("gmail.com") && pwd != null && pwd.contains(" ")) {
                senderImpl.setPassword(pwd.replace(" ", ""));
                log.info("Espacios internos eliminados automáticamente de SPRING_MAIL_PASSWORD para Gmail SMTP.");
            }
            String effectivePwd = senderImpl.getPassword();
            if (mailAuth && (isBlank(mailUsername) || isBlank(effectivePwd))) {
                log.warn("Configuración SMTP incompleta: SPRING_MAIL_USERNAME o SPRING_MAIL_PASSWORD están vacíos. "
                        + "Define tus credenciales en el archivo .env para habilitar el envío real de correos.");
            } else if (!isBlank(mailUsername)) {
                log.info("Servicio de correo SMTP listo: remitente={} servidor={}:{}",
                        mailUsername, host != null ? host : "smtp", senderImpl.getPort());
            }
        } else if (mailAuth && isBlank(mailUsername)) {
            log.warn("Configuración SMTP incompleta: SPRING_MAIL_USERNAME está vacío.");
        }
    }

    private String normalizedBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:4200";
        }
        String trimmed = baseUrl.trim().replaceAll("/+$", "");
        return trimmed.isEmpty() ? "http://localhost:4200" : trimmed;
    }

    private void configureSender(MimeMessageHelper helper, String defaultAlias) throws Exception {
        if (!isBlank(mailUsername)) {
            helper.setFrom(mailUsername.trim(), "Palacio del Mar");
        } else if (!isBlank(defaultFromAddress)) {
            helper.setFrom(defaultFromAddress.trim(), "Palacio del Mar");
        } else if (!isBlank(defaultAlias)) {
            helper.setFrom(defaultAlias.trim(), "Palacio del Mar");
        } else {
            helper.setFrom("reservas@palaciodelmar.co", "Palacio del Mar");
        }
    }

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        sendVerificationEmail(toEmail, token, null);
    }

    @Async
    public void sendVerificationEmail(String toEmail, String token, String returnUrl) {
        if (isBlank(toEmail)) return;
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "soporte@palaciodelmar.co");
            helper.setTo(toEmail.trim());
            helper.setSubject("Verifica tu cuenta - Palacio del Mar");

            String queryParam = (returnUrl != null && !returnUrl.isBlank())
                    ? "?returnUrl=" + java.net.URLEncoder.encode(returnUrl, java.nio.charset.StandardCharsets.UTF_8)
                    : "";
            String verifyLink = normalizedBaseUrl() + "/verificar-email/" + token + queryParam;
            String htmlMsg = "<div style=\"background:#0d0d0d; color:#fff; font-family:'Segoe UI',sans-serif; padding:30px; border-radius:10px; max-width:600px; margin:auto; border:1px solid #c9a96e;\">"
                    + "<div style=\"text-align:center; margin-bottom:20px;\">"
                    + "<h1 style=\"color:#c9a96e; font-size:24px; margin:0 0 6px 0; font-family:Georgia,serif;\">Palacio del Mar</h1>"
                    + "<p style=\"color:#e2c99b; font-size:11px; letter-spacing:2px; text-transform:uppercase; margin:0;\">Cartagena de Indias · Colombia</p>"
                    + "</div>"
                    + "<h2 style=\"color:#ffffff; font-size:19px; margin-top:0;\">Confirma tu dirección de correo</h2>"
                    + "<p style=\"color:#ccc; font-size:14px; line-height:1.6;\">Gracias por registrarte en Palacio del Mar. Para activar tu cuenta y realizar reservas, confirma tu correo electrónico haciendo clic en el siguiente botón:</p>"
                    + "<div style=\"text-align:center; margin:28px 0;\">"
                    + "<a href=\"" + verifyLink + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:13px 28px; text-decoration:none; border-radius:6px; font-weight:bold; font-size:15px; display:inline-block;\">Verificar mi Cuenta</a>"
                    + "</div>"
                    + "<p style=\"color:#999; font-size:12px; line-height:1.5; word-break:break-all;\">Si el botón no abre directamente, copia y pega este enlace en tu navegador:<br>"
                    + "<a href=\"" + verifyLink + "\" style=\"color:#c9a96e;\">" + verifyLink + "</a></p>"
                    + "<p style=\"color:#777; font-size:11px; margin-top:20px; border-top:1px solid #222; padding-top:12px;\">Si no solicitaste este registro, puedes ignorar este mensaje con tranquilidad.</p>"
                    + "</div>";

            helper.setText(htmlMsg, true);
            javaMailSender.send(message);
            log.info("Correo de verificación enviado exitosamente a {}", toEmail);
        } catch (Exception e) {
            log.warn("No se pudo enviar correo de verificación a {} (servidor SMTP no disponible): {}", toEmail, e.getMessage());
        }
    }

    @Async("mailTaskExecutor")
    public void sendBookingConfirmation(Booking booking) {
        if (booking == null) return;
        try {
            BookingEmailContext ctx = resolveBookingContext(booking);
            if (isBlank(ctx.guestEmail())) {
                log.warn("sendBookingConfirmation omitido: la reserva {} no tiene un correo electrónico de destinatario válido.", booking.getId());
                return;
            }

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "reservas@palaciodelmar.co");
            helper.setTo(ctx.guestEmail().trim());
            helper.setSubject("¡Tu reserva ha sido registrada y confirmada! [" + ctx.bookingCode() + "] - Palacio del Mar");

            String pinCode = getAccessPin(booking);
            String voucherLink = normalizedBaseUrl() + "/reservas/" + booking.getId() + "/comprobante";
            String myBookingsLink = normalizedBaseUrl() + "/mis-reservas";

            BigDecimal paid = booking.getPaidAmount() != null && booking.getPaidAmount().compareTo(BigDecimal.ZERO) > 0
                    ? booking.getPaidAmount()
                    : (ctx.paymentAmount() != null ? ctx.paymentAmount() : (booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO));
            BigDecimal total = booking.getTotalPrice() != null ? booking.getTotalPrice() : paid;
            BigDecimal balance = booking.getBalanceDue() != null ? booking.getBalanceDue() : BigDecimal.ZERO;

            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:'Segoe UI',sans-serif; padding:30px; border-radius:10px; max-width:620px; margin:auto; border:1px solid #c9a96e;\">"
                    + "<div style=\"text-align:center; margin-bottom:22px;\">"
                    + "<h1 style=\"color:#c9a96e; font-size:26px; margin:0 0 5px 0; font-family:Georgia,serif;\">Palacio del Mar</h1>"
                    + "<p style=\"color:#e2c99b; font-size:11px; letter-spacing:2px; text-transform:uppercase; margin:0;\">Confirmación Oficial de Reserva y Pago</p>"
                    + "</div>"
                    + "<h2 style=\"color:#ffffff; font-size:18px; margin-top:0;\">¡Hola, " + escapeHtml(ctx.guestName()) + "! Tu reserva ha sido registrada y confirmada exitosamente</h2>"
                    + "<p style=\"color:#ccc; font-size:14px; line-height:1.6;\">Hemos verificado tu pago exitosamente. Aquí tienes toda la información de tu alojamiento, el desglose de tu transacción y tu llave digital de ingreso:</p>"
                    + "<div style=\"background:#1a1a1a; border:2px solid #c9a96e; padding:18px; border-radius:8px; text-align:center; margin:22px 0;\">"
                    + "<p style=\"color:#c9a96e; text-transform:uppercase; font-size:11px; letter-spacing:2px; margin:0 0 6px 0;\">PIN de Acceso Digital (Smart Lock)</p>"
                    + "<span style=\"font-size:30px; letter-spacing:6px; font-weight:bold; color:#ffffff;\">" + escapeHtml(pinCode) + "</span>"
                    + "<p style=\"color:#aaa; font-size:11px; margin:6px 0 0 0;\">Ingresa este código en la cerradura electrónica a partir del día de tu llegada.</p>"
                    + "</div>"
                    + "<h3 style=\"color:#c9a96e; font-size:14px; text-transform:uppercase; letter-spacing:1px; margin:20px 0 8px 0;\">Detalles del Alojamiento</h3>"
                    + "<div style=\"background:#141414; border:1px solid rgba(201,169,110,0.3); border-radius:8px; padding:16px;\">"
                    + "<table style=\"width:100%; border-collapse:collapse; color:#ddd; font-size:14px;\">"
                    + rowHtml("Código de Reserva:", "<strong style=\"color:#c9a96e;\">" + escapeHtml(ctx.bookingCode()) + "</strong>")
                    + rowHtml("Propiedad / Sede:", "<strong>" + escapeHtml(ctx.propertyName()) + "</strong>")
                    + rowHtml("Dirección:", escapeHtml(ctx.address()))
                    + rowHtml("Suite / Habitación:", "<strong>" + escapeHtml(ctx.suiteDisplay()) + "</strong>")
                    + rowHtml("Llegada (Check-in):", escapeHtml(formatDateEs(booking.getCheckIn())) + " (desde las " + escapeHtml(ctx.checkInTime()) + ")")
                    + rowHtml("Salida (Check-out):", escapeHtml(formatDateEs(booking.getCheckOut())) + " (hasta las " + escapeHtml(ctx.checkOutTime()) + ")")
                    + rowHtml("Duración:", escapeHtml(ctx.nightsLabel()))
                    + rowHtml("Huéspedes:", escapeHtml(ctx.guestsLabel()))
                    + "</table>"
                    + "</div>"
                    + "<h3 style=\"color:#c9a96e; font-size:14px; text-transform:uppercase; letter-spacing:1px; margin:20px 0 8px 0;\">Resumen del Pago</h3>"
                    + "<div style=\"background:#141414; border:1px solid rgba(46,204,113,0.35); border-radius:8px; padding:16px;\">"
                    + "<table style=\"width:100%; border-collapse:collapse; color:#ddd; font-size:14px;\">"
                    + rowHtml("Estado del Pago:", "<strong style=\"color:#2ecc71;\">" + escapeHtml(ctx.paymentStatusLabel()) + "</strong>")
                    + rowHtml("Método de Pago:", escapeHtml(ctx.paymentMethodLabel()))
                    + rowHtml("Pasarela / Medio:", escapeHtml(ctx.paymentProviderLabel()))
                    + rowHtml("Referencia de Transacción:", "<span style=\"font-family:monospace; color:#e2c99b;\">" + escapeHtml(ctx.transactionReference()) + "</span>")
                    + rowHtml("Valor Total Reserva:", formatCop(total))
                    + rowHtml("Monto Pagado:", "<strong style=\"color:#2ecc71; font-size:15px;\">" + formatCop(paid) + "</strong>")
                    + rowHtml("Saldo Pendiente:", formatCop(balance))
                    + "</table>"
                    + "</div>"
                    + "<div style=\"text-align:center; margin:26px 0;\">"
                    + "<a href=\"" + voucherLink + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:13px 26px; text-decoration:none; border-radius:6px; font-weight:bold; font-size:15px; display:inline-block;\">Ver Voucher Digital con QR</a>"
                    + "</div>"
                    + "<p style=\"font-size:12px; color:#aaa; line-height:1.5;\">Adjuntamos en este correo tu <strong>comprobante oficial en PDF</strong> y la invitación de calendario <strong>.ics</strong> para Google Calendar / Apple Calendar / Outlook.</p>"
                    + "<p style=\"font-size:11px; color:#888; margin-top:16px; border-top:1px solid #222; padding-top:12px;\">Gestiona tu estadía en cualquier momento desde <a href=\"" + myBookingsLink + "\" style=\"color:#c9a96e;\">Mis Reservas</a>.</p>"
                    + "</div>";

            helper.setText(html, true);

            String safeFileCode = ctx.bookingCode().replaceAll("[^a-zA-Z0-9_-]", "_");
            try {
                byte[] pdfBytes = generateReceiptPdf(booking, ctx, total, paid, balance);
                helper.addAttachment("Comprobante_Reserva_" + safeFileCode + ".pdf", new ByteArrayResource(pdfBytes));
            } catch (Exception pdfEx) {
                log.warn("No se pudo generar adjunto PDF para reserva {}: {}", booking.getId(), pdfEx.getMessage());
            }

            try {
                byte[] icsBytes = generateIcsFile(booking, ctx);
                helper.addAttachment("Reserva_" + safeFileCode + ".ics", new ByteArrayResource(icsBytes));
            } catch (Exception icsEx) {
                log.warn("No se pudo generar adjunto ICS para reserva {}: {}", booking.getId(), icsEx.getMessage());
            }

            javaMailSender.send(message);
            log.info("Voucher de reserva {} enviado exitosamente a {}", ctx.bookingCode(), ctx.guestEmail());
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

            if (isBlank(hostEmail)) {
                log.debug("sendHostBookingNotification: no se encontró email de anfitrión para la reserva {}", booking.getId());
                return;
            }

            BookingEmailContext ctx = resolveBookingContext(booking);

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "notificaciones@palaciodelmar.co");
            helper.setTo(hostEmail.trim());
            String propertyName = property != null && !isBlank(property.getName()) ? property.getName() : ctx.propertyName();
            helper.setSubject("¡Nueva reserva confirmada en " + propertyName + "! [" + ctx.bookingCode() + "]");

            String guestPhone = (booking.getGuestDetails() != null && !isBlank(booking.getGuestDetails().getPhone()))
                    ? booking.getGuestDetails().getPhone()
                    : "No especificado";

            BigDecimal hostPayout = booking.getHostPayout() != null && booking.getHostPayout().compareTo(BigDecimal.ZERO) > 0
                    ? booking.getHostPayout()
                    : (booking.getTotalPrice() != null ? booking.getTotalPrice().multiply(new BigDecimal("0.92")) : BigDecimal.ZERO);

            String hostUrl = normalizedBaseUrl() + "/anfitrion";

            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h1 style=\"color:#c9a96e; font-size:22px; margin-top:0;\">¡Nueva Reserva Confirmada!</h1>"
                    + "<p>Has recibido una nueva reserva garantizada en <strong>" + escapeHtml(propertyName) + "</strong>.</p>"
                    + "<div style=\"background:#1a1a1a; border-left:4px solid #c9a96e; padding:15px; margin:15px 0;\">"
                    + "<p style=\"margin:4px 0;\"><strong>Código:</strong> " + escapeHtml(ctx.bookingCode()) + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Suite / Unidad:</strong> " + escapeHtml(ctx.suiteDisplay()) + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Huésped:</strong> " + escapeHtml(ctx.guestName()) + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Teléfono:</strong> " + escapeHtml(guestPhone) + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Check-in:</strong> " + escapeHtml(formatDateEs(booking.getCheckIn())) + " (" + escapeHtml(ctx.checkInTime()) + ")</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Check-out:</strong> " + escapeHtml(formatDateEs(booking.getCheckOut())) + " (" + escapeHtml(ctx.checkOutTime()) + ")</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Huéspedes:</strong> " + escapeHtml(ctx.guestsLabel()) + "</p>"
                    + "<p style=\"margin:8px 0 0 0; color:#c9a96e; font-size:16px;\"><strong>Tu Liquidación Neta (92%):</strong> " + formatCop(hostPayout) + "</p>"
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
            if (isBlank(hostEmail)) return;

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "moderacion@palaciodelmar.co");
            helper.setTo(hostEmail.trim());

            boolean approved = "APPROVED".equalsIgnoreCase(status);
            String subject = approved
                    ? "¡Tu alojamiento '" + property.getName() + "' ha sido aprobado!"
                    : "Actualización sobre tu alojamiento '" + property.getName() + "'";
            helper.setSubject(subject);

            String hostUrl = normalizedBaseUrl() + "/anfitrion";

            String html;
            if (approved) {
                html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                        + "<h1 style=\"color:#c9a96e; font-size:22px; margin-top:0;\">¡Felicitaciones! Alojamiento Aprobado</h1>"
                        + "<p>Tu alojamiento <strong>" + escapeHtml(property.getName()) + "</strong> ha superado la revisión administrativa.</p>"
                        + "<p>Ya se encuentra publicado y visible para todos los viajeros en el catálogo del marketplace.</p>"
                        + "<p><a href=\"" + hostUrl + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:10px 20px; text-decoration:none; border-radius:4px; font-weight:bold; display:inline-block;\">Administrar Disponibilidad</a></p>"
                        + "</div>";
            } else {
                html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                        + "<h1 style=\"color:#e53e3e; font-size:22px; margin-top:0;\">Alojamiento No Aprobado</h1>"
                        + "<p>Tu solicitud para el alojamiento <strong>" + escapeHtml(property.getName()) + "</strong> ha sido evaluada y requiere ajustes en su descripción, fotografías o documentación.</p>"
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
            if (isBlank(hostEmail)) return;

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "alertas@palaciodelmar.co");
            helper.setTo(hostEmail.trim());
            helper.setSubject("Alerta: Fallo persistente en entregas de Webhook - Palacio del Mar");

            String hostUrl = normalizedBaseUrl() + "/anfitrion";

            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h1 style=\"color:#e53e3e; font-size:22px; margin-top:0;\">Alerta de Entrega de Webhook</h1>"
                    + "<p>Detectamos que tu sistema receptor no pudo recibir eventos de webhook tras agotar todos los reintentos automáticos (" + delivery.getMaxAttempts() + " intentos).</p>"
                    + "<div style=\"background:#1a1a1a; border-left:4px solid #e53e3e; padding:15px; margin:15px 0;\">"
                    + "<p style=\"margin:4px 0;\"><strong>URL Destino:</strong> " + escapeHtml(delivery.getTargetUrl()) + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Evento:</strong> " + escapeHtml(delivery.getEventType()) + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Código HTTP:</strong> " + delivery.getResponseCode() + "</p>"
                    + "<p style=\"margin:4px 0;\"><strong>Último Error:</strong> " + escapeHtml(delivery.getErrorMessage() != null ? delivery.getErrorMessage() : "Sin respuesta") + "</p>"
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
            BookingEmailContext ctx = resolveBookingContext(booking);
            if (isBlank(ctx.guestEmail())) {
                log.warn("sendReviewInvitation omitido: no se encontró email para la reserva {}", booking.getId());
                return;
            }

            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "experiencias@palaciodelmar.co");
            helper.setTo(ctx.guestEmail().trim());
            helper.setSubject("¿Cómo estuvo tu estadía? - Califica tu experiencia en " + ctx.propertyName());

            String link = normalizedBaseUrl() + "/reservas/" + booking.getId();
            String html = "<div style=\"background:#0d0d0d; color:#fff; font-family:sans-serif; padding:25px; border-radius:8px; max-width:600px; margin:auto;\">"
                    + "<h2 style=\"color:#c9a96e; margin-top:0;\">¡Gracias por hospedarte con nosotros, " + escapeHtml(ctx.guestName()) + "!</h2>"
                    + "<p>Esperamos que hayas disfrutado de tu estancia en <strong>" + escapeHtml(ctx.suiteDisplay()) + "</strong> (" + escapeHtml(ctx.propertyName()) + ").</p>"
                    + "<p>Nos encantaría conocer tu opinión para seguir mejorando. Déjanos una reseña verificada de tu experiencia:</p>"
                    + "<p style=\"margin:20px 0;\"><a href=\"" + link + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:12px 24px; text-decoration:none; border-radius:6px; font-weight:bold; display:inline-block;\">Calificar mi Estadía</a></p>"
                    + "<p style=\"font-size:12px; color:#888;\">Tu opinión ayuda a futuros viajeros y a nuestros anfitriones.</p>"
                    + "</div>";

            helper.setText(html, true);
            javaMailSender.send(message);
        } catch (Exception e) {
            log.warn("No se pudo enviar invitación de reseña para la reserva {} (servidor SMTP no disponible): {}", booking.getId(), e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        if (isBlank(toEmail)) return;
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "seguridad@palaciodelmar.co");
            helper.setTo(toEmail.trim());
            helper.setSubject("Restablece tu Contraseña - Palacio del Mar");

            String resetLink = normalizedBaseUrl() + "/restablecer-contrasena/" + token;
            String htmlMsg = "<div style=\"background:#0d0d0d; color:#fff; font-family:'Segoe UI',Roboto,Helvetica,sans-serif; padding:30px; border-radius:10px; max-width:600px; margin:auto; border:1px solid #c9a96e;\">"
                    + "<div style=\"text-align:center; margin-bottom:20px;\">"
                    + "<h1 style=\"color:#c9a96e; font-size:24px; margin:0 0 6px 0; font-family:Georgia,serif;\">Palacio del Mar</h1>"
                    + "<p style=\"color:#e2c99b; font-size:11px; letter-spacing:2px; text-transform:uppercase; margin:0;\">Seguridad y Acceso</p>"
                    + "</div>"
                    + "<h2 style=\"color:#ffffff; font-size:19px; margin-top:0;\">Restablecimiento de Contraseña</h2>"
                    + "<p style=\"color:#ccc; font-size:14px; line-height:1.6;\">Has solicitado restablecer la contraseña de tu cuenta en Palacio del Mar. Haz clic en el siguiente botón para definir tu nueva clave:</p>"
                    + "<div style=\"text-align:center; margin:28px 0;\">"
                    + "<a href=\"" + resetLink + "\" style=\"background:#c9a96e; color:#0d0d0d; padding:13px 28px; text-decoration:none; border-radius:6px; font-weight:bold; font-size:15px; display:inline-block;\">Restablecer mi Contraseña</a>"
                    + "</div>"
                    + "<p style=\"color:#999; font-size:12px; line-height:1.5; word-break:break-all;\">Si el botón no abre directamente, copia y pega este enlace en tu navegador:<br>"
                    + "<a href=\"" + resetLink + "\" style=\"color:#c9a96e;\">" + resetLink + "</a></p>"
                    + "<p style=\"color:#e6c887; font-size:12px; margin-top:16px;\">⏱️ Este enlace es válido por <strong>15 minutos</strong> y expira automáticamente tras su uso.</p>"
                    + "<p style=\"color:#777; font-size:11px; margin-top:20px; border-top:1px solid #222; padding-top:12px;\">Si no solicitaste este cambio, puedes ignorar este correo; tu contraseña actual continúa siendo segura y nadie puede acceder sin este enlace.</p>"
                    + "</div>";

            helper.setText(htmlMsg, true);
            javaMailSender.send(message);
            log.info("Correo con enlace de restablecimiento de contraseña enviado exitosamente a {}", toEmail);
        } catch (Exception e) {
            log.warn("No se pudo enviar correo con enlace de restablecimiento a {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendTemporaryPasswordEmail(String toEmail, String temporaryPassword) {
        if (isBlank(toEmail)) return;
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            configureSender(helper, "seguridad@palaciodelmar.co");
            helper.setTo(toEmail.trim());
            helper.setSubject("Tu Contraseña Temporal de Acceso - Palacio del Mar");

            String loginUrl = normalizedBaseUrl();
            String htmlMsg = "<div style=\"background:#0d0d0d; color:#fff; font-family:'Segoe UI',Roboto,Helvetica,sans-serif; padding:30px; border-radius:10px; max-width:600px; margin:auto; border:1px solid #2a2a2a;\">"
                    + "<div style=\"text-align:center; margin-bottom:20px;\">"
                    + "<span style=\"color:#c9a96e; letter-spacing:3px; font-size:12px; text-transform:uppercase; font-weight:600;\">Palacio del Mar • Seguridad</span>"
                    + "<h1 style=\"color:#ffffff; font-size:24px; margin:10px 0 0 0; font-weight:400;\">Recuperación de Acceso</h1>"
                    + "</div>"
                    + "<p style=\"color:#ccc; font-size:15px; line-height:1.6;\">Has solicitado una contraseña temporal para ingresar a tu cuenta de Palacio del Mar. Puedes utilizar la siguiente contraseña provisional para iniciar sesión:</p>"
                    + "<div style=\"background:#161616; border:2px dashed #c9a96e; border-radius:8px; padding:20px; text-align:center; margin:25px 0;\">"
                    + "<span style=\"color:#aaa; font-size:11px; text-transform:uppercase; letter-spacing:2px; display:block; margin-bottom:8px;\">Contraseña Temporal</span>"
                    + "<span style=\"color:#c9a96e; font-size:26px; font-family:monospace; letter-spacing:4px; font-weight:bold;\">" + escapeHtml(temporaryPassword) + "</span>"
                    + "</div>"
                    + "<p style=\"color:#e6c887; font-size:13px; line-height:1.5;\">⚠️ Por tu seguridad, una vez que inicies sesión te recomendamos ir a tu perfil y definir una nueva contraseña personalizada.</p>"
                    + "<div style=\"text-align:center; margin:30px 0 20px 0;\">"
                    + "<a href=\"" + loginUrl + "\" style=\"background:#c9a96e; color:#0d0d0d; font-weight:bold; padding:14px 28px; text-decoration:none; border-radius:6px; display:inline-block; font-size:15px;\">Iniciar Sesión Ahora</a>"
                    + "</div>"
                    + "<hr style=\"border:none; border-top:1px solid #222; margin:25px 0;\">"
                    + "<p style=\"color:#777; font-size:12px; line-height:1.4; margin:0;\">Si no solicitaste este cambio, puedes ignorar este correo o contactar a soporte.</p>"
                    + "</div>";

            helper.setText(htmlMsg, true);
            javaMailSender.send(message);
            log.info("Correo con contraseña temporal enviado exitosamente a {}", toEmail);
        } catch (Exception e) {
            log.warn("No se pudo enviar correo con contraseña temporal a {}: {}", toEmail, e.getMessage());
        }
    }

    private byte[] generateReceiptPdf(Booking booking, BookingEmailContext ctx, BigDecimal total, BigDecimal paid, BigDecimal balance) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("PALACIO DEL MAR - COMPROBANTE OFICIAL DE RESERVA Y PAGO"));
            document.add(new Paragraph("=========================================================="));
            document.add(new Paragraph("Código de Reserva: " + ctx.bookingCode()));
            if (!isBlank(booking.getId()) && !booking.getId().equals(ctx.bookingCode())) {
                document.add(new Paragraph("ID Interno: " + booking.getId()));
            }
            document.add(new Paragraph("Huésped Titular: " + ctx.guestName()));
            document.add(new Paragraph("Estado de Reserva: " + (booking.getStatus() != null ? booking.getStatus().toUpperCase(Locale.ROOT) : "CONFIRMED")));
            document.add(new Paragraph("PIN de Acceso: " + getAccessPin(booking)));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("--- DETALLES DEL ALOJAMIENTO ---"));
            document.add(new Paragraph("Propiedad / Sede: " + ctx.propertyName()));
            document.add(new Paragraph("Dirección: " + ctx.address()));
            document.add(new Paragraph("Suite / Unidad: " + ctx.suiteDisplay()));
            document.add(new Paragraph("Check-in: " + formatDateEs(booking.getCheckIn()) + " (" + ctx.checkInTime() + ")"));
            document.add(new Paragraph("Check-out: " + formatDateEs(booking.getCheckOut()) + " (" + ctx.checkOutTime() + ")"));
            document.add(new Paragraph("Duración: " + ctx.nightsLabel()));
            document.add(new Paragraph("Huéspedes: " + ctx.guestsLabel()));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("--- DETALLES DEL PAGO ---"));
            document.add(new Paragraph("Estado del Pago: " + ctx.paymentStatusLabel()));
            document.add(new Paragraph("Método de Pago: " + ctx.paymentMethodLabel()));
            document.add(new Paragraph("Pasarela: " + ctx.paymentProviderLabel()));
            document.add(new Paragraph("Referencia de Transacción: " + ctx.transactionReference()));
            document.add(new Paragraph("Valor Total Reserva: " + formatCop(total)));
            document.add(new Paragraph("Total Pagado: " + formatCop(paid)));
            document.add(new Paragraph("Balance Pendiente: " + formatCop(balance)));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF", e);
        }
    }

    private byte[] generateIcsFile(Booking booking, BookingEmailContext ctx) {
        String dtStart = booking.getCheckIn().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String dtEnd = booking.getCheckOut().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String cleanSummary = sanitizeIcsText("Estadía en " + ctx.propertyName() + " - " + ctx.suiteName());
        String cleanLocation = sanitizeIcsText(ctx.address());
        String cleanDesc = sanitizeIcsText("Reserva confirmada [" + ctx.bookingCode() + "] | Suite: "
                + ctx.suiteDisplay() + " | PIN de Acceso: " + getAccessPin(booking));

        String icsContent = "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//Palacio del Mar//Reservas//ES\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + UUID.randomUUID() + "@palaciodelmar.co\r\n" +
                "DTSTAMP:" + Instant.now().toString().replaceAll("[-:]", "").split("\\.")[0] + "Z\r\n" +
                "DTSTART;VALUE=DATE:" + dtStart + "\r\n" +
                "DTEND;VALUE=DATE:" + dtEnd + "\r\n" +
                "SUMMARY:" + cleanSummary + "\r\n" +
                "LOCATION:" + cleanLocation + "\r\n" +
                "DESCRIPTION:" + cleanDesc + "\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";

        return icsContent.getBytes(StandardCharsets.UTF_8);
    }

    private BookingEmailContext resolveBookingContext(Booking booking) {
        String guestEmail = null;
        String userName = null;
        if (!isBlank(booking.getUser()) && userRepository != null) {
            User u = userRepository.findById(booking.getUser()).orElse(null);
            if (u != null) {
                guestEmail = u.getEmail();
                userName = u.getName();
            }
        }
        if (isBlank(guestEmail) && booking.getGuestDetails() != null) {
            guestEmail = booking.getGuestDetails().getEmail();
        }

        String guestName = "Huésped";
        if (booking.getGuestDetails() != null && !isBlank(booking.getGuestDetails().getFullName())) {
            guestName = booking.getGuestDetails().getFullName().trim();
        } else if (!isBlank(userName)) {
            guestName = userName.trim();
        }

        String bookingCode = !isBlank(booking.getBookingCode())
                ? booking.getBookingCode().trim()
                : (!isBlank(booking.getId()) ? booking.getId().trim() : "PDM-RESERVA");

        Suite suite = null;
        if (!isBlank(booking.getSuite()) && suiteRepository != null) {
            String rawSuite = booking.getSuite().trim();
            suite = suiteRepository.findById(rawSuite)
                    .or(() -> suiteRepository.findBySlug(rawSuite.toLowerCase(Locale.ROOT)))
                    .orElse(null);
        }

        String suiteName;
        String suiteType = null;
        if (suite != null && !isBlank(suite.getName())) {
            suiteName = suite.getName().trim();
            if (!isBlank(suite.getType())) {
                suiteType = suite.getType().trim();
            }
        } else if (!isBlank(booking.getSuite()) && !booking.getSuite().trim().matches("^[a-fA-F0-9]{24}$")) {
            suiteName = booking.getSuite().trim();
        } else {
            suiteName = "Suite Exclusiva Palacio del Mar";
        }
        String suiteDisplay = suiteType != null && !suiteName.toLowerCase(Locale.ROOT).contains(suiteType.toLowerCase(Locale.ROOT))
                ? suiteName + " (" + suiteType + ")"
                : (suiteType != null ? suiteName + " · " + suiteType : suiteName);

        String propId = !isBlank(booking.getPropertyId())
                ? booking.getPropertyId()
                : (suite != null ? suite.getPropertyId() : null);
        Property property = (!isBlank(propId) && propertyRepository != null)
                ? propertyRepository.findById(propId).orElse(null)
                : null;

        String branchId = !isBlank(booking.getBranch())
                ? booking.getBranch()
                : (suite != null ? suite.getBranch() : null);
        Branch branch = (!isBlank(branchId) && branchRepository != null)
                ? branchRepository.findById(branchId).orElse(null)
                : null;

        String propertyName;
        if (property != null && !isBlank(property.getName())) {
            propertyName = property.getName().trim();
        } else if (branch != null && !isBlank(branch.getName())) {
            propertyName = branch.getName().trim();
        } else {
            propertyName = "Palacio del Mar · Cartagena";
        }

        String address;
        if (property != null && !isBlank(property.getAddress())) {
            String rawAddr = property.getAddress().trim();
            if (!isBlank(property.getCity()) && !rawAddr.toLowerCase(Locale.ROOT).contains(property.getCity().trim().toLowerCase(Locale.ROOT))) {
                address = rawAddr + ", " + property.getCity().trim();
            } else {
                address = rawAddr;
            }
        } else if (branch != null && !isBlank(branch.getAddress())) {
            address = branch.getAddress().trim();
        } else {
            address = "Cartagena de Indias, Bolívar, Colombia";
        }

        String checkInTime = (branch != null && !isBlank(branch.getCheckInTime())) ? branch.getCheckInTime() : "15:00";
        String checkOutTime = (branch != null && !isBlank(branch.getCheckOutTime())) ? branch.getCheckOutTime() : "13:00";

        int nights = 1;
        if (booking.getNights() != null && booking.getNights() > 0) {
            nights = booking.getNights();
        } else if (booking.getCheckIn() != null && booking.getCheckOut() != null) {
            nights = Math.max(1, (int) ChronoUnit.DAYS.between(booking.getCheckIn(), booking.getCheckOut()));
        }
        String nightsLabel = nights == 1 ? "1 noche" : nights + " noches";

        int guests = (booking.getGuests() != null && booking.getGuests() > 0) ? booking.getGuests() : 1;
        int children = (booking.getChildren() != null && booking.getChildren() > 0) ? booking.getChildren() : 0;
        String guestsLabel = children > 0
                ? guests + (guests == 1 ? " adulto, " : " adultos, ") + children + (children == 1 ? " niño" : " niños")
                : guests + (guests == 1 ? " huésped" : " huéspedes");

        Payment payment = resolveLatestPayment(booking.getId());
        String paymentMethodLabel = formatPaymentMethod(payment != null ? payment.getPaymentMethod() : null);
        String paymentProviderLabel = formatPaymentProvider(payment != null ? payment.getProvider() : null);
        String transactionReference;
        if (payment != null && !isBlank(payment.getTransactionId())) {
            transactionReference = payment.getTransactionId().trim();
        } else if (payment != null && !isBlank(payment.getIntentId())) {
            transactionReference = payment.getIntentId().trim();
        } else {
            transactionReference = "Confirmado (" + bookingCode + ")";
        }

        String paymentStatusLabel = (booking.getBalanceDue() != null && booking.getBalanceDue().compareTo(BigDecimal.ZERO) > 0)
                ? "Pago Parcial Registrado"
                : "Pagado y Confirmado";
        BigDecimal paymentAmount = payment != null ? payment.getAmount() : null;

        return new BookingEmailContext(
                guestEmail,
                guestName,
                bookingCode,
                suiteName,
                suiteDisplay,
                propertyName,
                address,
                checkInTime,
                checkOutTime,
                nightsLabel,
                guestsLabel,
                paymentMethodLabel,
                paymentProviderLabel,
                transactionReference,
                paymentStatusLabel,
                paymentAmount
        );
    }

    private Payment resolveLatestPayment(String bookingId) {
        if (isBlank(bookingId) || paymentRepository == null) {
            return null;
        }
        List<Payment> payments = paymentRepository.findByBooking(bookingId);
        if (payments == null || payments.isEmpty()) {
            return null;
        }
        Comparator<Payment> byTimestamp = Comparator.comparing(
                p -> p.getUpdatedAt() != null ? p.getUpdatedAt() : (p.getCreatedAt() != null ? p.getCreatedAt() : Instant.EPOCH)
        );
        Optional<Payment> completed = payments.stream()
                .filter(p -> p != null && "completed".equalsIgnoreCase(p.getStatus()))
                .max(byTimestamp);
        return completed.orElseGet(() -> payments.stream().filter(p -> p != null).max(byTimestamp).orElse(null));
    }

    private String formatPaymentMethod(String method) {
        if (isBlank(method)) {
            return "Pago electrónico en línea";
        }
        return switch (method.trim().toLowerCase(Locale.ROOT)) {
            case "card", "credit_card", "debit_card" -> "Tarjeta de crédito o débito";
            case "pse" -> "PSE (Transferencia bancaria)";
            case "nequi" -> "Nequi";
            case "cash" -> "Efectivo";
            case "simulated" -> "Pago electrónico verificado";
            default -> method.trim();
        };
    }

    private String formatPaymentProvider(String provider) {
        if (isBlank(provider) || "simulated".equalsIgnoreCase(provider.trim())) {
            return "Pasarela Segura Palacio del Mar";
        }
        return provider.trim();
    }

    private String formatCop(BigDecimal amount) {
        BigDecimal safe = amount != null ? amount.setScale(0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(LOCALE_ES_CO);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return "COP $" + df.format(safe);
    }

    private String formatDateEs(LocalDate date) {
        if (date == null) {
            return "Por confirmar";
        }
        return date.format(HUMAN_DATE_FMT);
    }

    private String rowHtml(String label, String valueHtml) {
        return "<tr>"
                + "<td style=\"padding:7px 8px 7px 0; color:#999; vertical-align:top; width:42%; border-bottom:1px solid rgba(255,255,255,0.05);\">" + escapeHtml(label) + "</td>"
                + "<td style=\"padding:7px 0; color:#fff; vertical-align:top; border-bottom:1px solid rgba(255,255,255,0.05);\">" + valueHtml + "</td>"
                + "</tr>";
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String sanitizeIcsText(String value) {
        if (value == null) return "";
        return value.replace("\r", " ").replace("\n", " ").replace(",", "\\,").replace(";", "\\;");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String getAccessPin(Booking booking) {
        String pin = booking.getSmartLockPin();
        return pin == null || pin.isBlank() ? "Pendiente de asignación" : pin;
    }

    private record BookingEmailContext(
            String guestEmail,
            String guestName,
            String bookingCode,
            String suiteName,
            String suiteDisplay,
            String propertyName,
            String address,
            String checkInTime,
            String checkOutTime,
            String nightsLabel,
            String guestsLabel,
            String paymentMethodLabel,
            String paymentProviderLabel,
            String transactionReference,
            String paymentStatusLabel,
            BigDecimal paymentAmount
    ) {}
}
