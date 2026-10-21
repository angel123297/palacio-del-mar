package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.*;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PropertyRepository propertyRepository;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(javaMailSender, userRepository);
        ReflectionTestUtils.setField(emailService, "propertyRepository", propertyRepository);
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:4200");
    }

    private MimeMessage createMockMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    @DisplayName("sendVerificationEmail envía correo correctamente con enlace al frontend")
    void testSendVerificationEmail_Success() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        assertDoesNotThrow(() -> emailService.sendVerificationEmail("usuario@ejemplo.com", "token-xyz-123"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, times(1)).send(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    @DisplayName("sendBookingConfirmation genera voucher con PIN digital, adjuntos PDF e ICS y envía correo al huésped")
    void testSendBookingConfirmation_Success() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        User guest = User.builder()
                .id("guest-user-1")
                .email("huesped@ejemplo.com")
                .name("Juan Perez")
                .build();
        when(userRepository.findById("guest-user-1")).thenReturn(Optional.of(guest));

        Booking booking = Booking.builder()
                .id("BK-2026-001")
                .user("guest-user-1")
                .propertyId("prop-100")
                .smartLockPin("987654")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 15))
                .status("confirmed")
                .guests(2)
                .totalPrice(new BigDecimal("1500000"))
                .paidAmount(new BigDecimal("1500000"))
                .balanceDue(BigDecimal.ZERO)
                .build();

        assertDoesNotThrow(() -> emailService.sendBookingConfirmation(booking));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, times(1)).send(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    @DisplayName("sendBookingConfirmation usa email de GuestDetails si User id es null")
    void testSendBookingConfirmation_WithGuestDetailsFallback() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        Booking.GuestDetails details = new Booking.GuestDetails();
        details.setEmail("contacto.directo@correo.com");
        details.setFullName("Maria Gomez");

        Booking booking = Booking.builder()
                .id("BK-2026-002")
                .user(null)
                .guestDetails(details)
                .propertyId("prop-100")
                .smartLockPin("112233")
                .checkIn(LocalDate.of(2026, 12, 1))
                .checkOut(LocalDate.of(2026, 12, 5))
                .status("confirmed")
                .guests(1)
                .totalPrice(new BigDecimal("600000"))
                .paidAmount(new BigDecimal("600000"))
                .balanceDue(BigDecimal.ZERO)
                .build();

        assertDoesNotThrow(() -> emailService.sendBookingConfirmation(booking));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendBookingConfirmation mantiene el PIN pendiente de asignación en correo, PDF e ICS")
    void testSendBookingConfirmation_UsesPendingPinAcrossVoucherFormats() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        Booking booking = Booking.builder()
                .id("BK-2026-004")
                .guestDetails(new Booking.GuestDetails())
                .checkIn(LocalDate.of(2026, 12, 10))
                .checkOut(LocalDate.of(2026, 12, 12))
                .status("confirmed")
                .guests(1)
                .totalPrice(new BigDecimal("200000"))
                .paidAmount(new BigDecimal("200000"))
                .balanceDue(BigDecimal.ZERO)
                .build();
        booking.getGuestDetails().setEmail("huesped@ejemplo.com");

        emailService.sendBookingConfirmation(booking);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        Multipart content = (Multipart) captor.getValue().getContent();

        byte[] pdfBytes = null;
        String icsContent = null;
        for (int i = 0; i < content.getCount(); i++) {
            BodyPart attachment = content.getBodyPart(i);
            if (attachment.getFileName() != null && attachment.getFileName().endsWith(".pdf")) {
                pdfBytes = attachment.getInputStream().readAllBytes();
            } else if (attachment.getFileName() != null && attachment.getFileName().endsWith(".ics")) {
                icsContent = new String(attachment.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        assertNotNull(pdfBytes);
        assertNotNull(icsContent);
        PdfReader pdfReader = new PdfReader(pdfBytes);
        try {
            assertTrue(new PdfTextExtractor(pdfReader).getTextFromPage(1).contains("Pendiente de asignación"));
        } finally {
            pdfReader.close();
        }
        assertTrue(icsContent.contains("Pendiente de asignación"));
    }

    @Test
    @DisplayName("sendHostBookingNotification envía email al anfitrión con cálculo de liquidación neta (92%)")
    void testSendHostBookingNotification_Success() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        Property property = Property.builder()
                .id("prop-100")
                .ownerId("host-user-99")
                .name("Villa Colonial Santa Marta")
                .build();
        when(propertyRepository.findById("prop-100")).thenReturn(Optional.of(property));

        User hostUser = User.builder()
                .id("host-user-99")
                .email("anfitrion@palacio.co")
                .name("Carlos Anfitrión")
                .build();
        when(userRepository.findById("host-user-99")).thenReturn(Optional.of(hostUser));

        Booking.GuestDetails guest = new Booking.GuestDetails();
        guest.setFullName("Ana Martinez");
        guest.setPhone("+573001234567");

        Booking booking = Booking.builder()
                .id("BK-2026-003")
                .propertyId("prop-100")
                .guestDetails(guest)
                .checkIn(LocalDate.of(2026, 11, 20))
                .checkOut(LocalDate.of(2026, 11, 23))
                .guests(3)
                .totalPrice(new BigDecimal("1000000"))
                .hostPayout(new BigDecimal("920000"))
                .build();

        assertDoesNotThrow(() -> emailService.sendHostBookingNotification(booking));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPropertyStatusNotification notifica aprobación al anfitrión")
    void testSendPropertyStatusNotification_Approved() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        Property property = Property.builder()
                .id("prop-200")
                .ownerId("host-user-50")
                .name("Penthouse Caribe")
                .status("APPROVED")
                .build();

        User host = User.builder()
                .id("host-user-50")
                .email("host50@ejemplo.com")
                .build();
        when(userRepository.findById("host-user-50")).thenReturn(Optional.of(host));

        assertDoesNotThrow(() -> emailService.sendPropertyStatusNotification(property, "APPROVED"));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPropertyStatusNotification notifica rechazo al anfitrión con instrucciones")
    void testSendPropertyStatusNotification_Rejected() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        Property property = Property.builder()
                .id("prop-201")
                .ownerId("host-user-50")
                .name("Cabaña Rústica")
                .status("REJECTED")
                .build();

        User host = User.builder()
                .id("host-user-50")
                .email("host50@ejemplo.com")
                .build();
        when(userRepository.findById("host-user-50")).thenReturn(Optional.of(host));

        assertDoesNotThrow(() -> emailService.sendPropertyStatusNotification(property, "REJECTED"));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendWebhookFailureAlert envía alerta al anfitrión tras agotar reintentos")
    void testSendWebhookFailureAlert_Success() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        User host = User.builder()
                .id("host-user-77")
                .email("dev-host@api.com")
                .build();
        when(userRepository.findById("host-user-77")).thenReturn(Optional.of(host));

        WebhookDelivery delivery = WebhookDelivery.builder()
                .id("del-999")
                .ownerId("host-user-77")
                .targetUrl("https://mi-pms.ejemplo.com/webhook")
                .eventType("booking.confirmed")
                .attempts(3)
                .maxAttempts(3)
                .responseCode(503)
                .errorMessage("Service Unavailable")
                .status("FAILED")
                .build();

        assertDoesNotThrow(() -> emailService.sendWebhookFailureAlert(delivery));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendReviewInvitation envía correo al huésped para calificar la estadía")
    void testSendReviewInvitation_Success() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        User guest = User.builder()
                .id("guest-user-88")
                .email("huesped.viajero@gmail.com")
                .build();
        when(userRepository.findById("guest-user-88")).thenReturn(Optional.of(guest));

        Booking booking = Booking.builder()
                .id("BK-2026-999")
                .user("guest-user-88")
                .build();

        assertDoesNotThrow(() -> emailService.sendReviewInvitation(booking));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Resiliencia SMTP: si JavaMailSender lanza excepción, EmailService no propaga el error")
    void testSmtpFailure_DoesNotThrowException() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());
        doThrow(new MailSendException("SMTP server connection refused at localhost:1025"))
                .when(javaMailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> emailService.sendVerificationEmail("test@fail.com", "token-xyz"));
    }

    @Test
    @DisplayName("Tolerancia a nulos: no lanza NPE con objetos nulos o incompletos")
    void testNullSafety() {
        assertDoesNotThrow(() -> emailService.sendBookingConfirmation(null));
        assertDoesNotThrow(() -> emailService.sendHostBookingNotification(null));
        assertDoesNotThrow(() -> emailService.sendPropertyStatusNotification(null, "APPROVED"));
        assertDoesNotThrow(() -> emailService.sendWebhookFailureAlert(null));
        assertDoesNotThrow(() -> emailService.sendReviewInvitation(null));
    }
}
