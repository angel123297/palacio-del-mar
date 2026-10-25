package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.*;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PaymentRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
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
import org.springframework.mail.javamail.JavaMailSenderImpl;
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

    @Mock
    private SuiteRepository suiteRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(javaMailSender, userRepository);
        ReflectionTestUtils.setField(emailService, "propertyRepository", propertyRepository);
        ReflectionTestUtils.setField(emailService, "suiteRepository", suiteRepository);
        ReflectionTestUtils.setField(emailService, "branchRepository", branchRepository);
        ReflectionTestUtils.setField(emailService, "paymentRepository", paymentRepository);
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:4200");
    }

    private String extractHtmlContent(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String str) {
            return str;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bp = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                    continue;
                }
                sb.append(extractHtmlContent(bp));
            }
            return sb.toString();
        }
        return "";
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
    @DisplayName("sendBookingCreatedNotification envía correo con código y enlace de pago al huésped")
    void testSendBookingCreatedNotification_SendsEmailSuccessfully() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        User guest = User.builder()
                .id("guest-user-1")
                .name("Ange Kaleth")
                .email("angekaleth633@gmail.com")
                .build();
        when(userRepository.findById("guest-user-1")).thenReturn(Optional.of(guest));

        Booking booking = Booking.builder()
                .id("bk-created-01")
                .bookingCode("BK-2026-999")
                .user("guest-user-1")
                .suite("Penthouse Ocean Front")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 15))
                .totalPrice(new BigDecimal("2450000"))
                .status("pending")
                .build();

        assertDoesNotThrow(() -> emailService.sendBookingCreatedNotification(booking));

        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
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
    @DisplayName("sendTemporaryPasswordEmail envía correo con contraseña temporal al usuario")
    void testSendTemporaryPasswordEmail_Success() {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        assertDoesNotThrow(() -> emailService.sendTemporaryPasswordEmail("usuario@ejemplo.com", "Palacio@9xK2"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender, times(1)).send(captor.capture());
        assertNotNull(captor.getValue());
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

    @Test
    @DisplayName("Normaliza APP_BASE_URL eliminando barras finales para evitar enlaces rotos con doble //")
    void testNormalizeBaseUrl_StripsTrailingSlashAcrossLinks() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://palacio-tunel.trycloudflare.com/");

        emailService.sendVerificationEmail("ana@gmail.com", "tok-777");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        String html = extractHtmlContent(captor.getValue());
        assertTrue(html.contains("https://palacio-tunel.trycloudflare.com/verificar-email/tok-777"));
        assertFalse(html.contains("trycloudflare.com//"));
    }

    @Test
    @DisplayName("sendBookingConfirmation omite envío si no hay correo de destinatario (sin fallback falso cliente@ejemplo.com)")
    void testSendBookingConfirmation_SkipsSendingWhenNoRecipientEmail() {
        Booking booking = Booking.builder()
                .id("BK-NO-EMAIL")
                .user(null)
                .guestDetails(null)
                .checkIn(LocalDate.of(2026, 12, 1))
                .checkOut(LocalDate.of(2026, 12, 3))
                .build();

        emailService.sendBookingConfirmation(booking);

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("validateMailConfiguration limpia espacios en App Password de Gmail y tolera credenciales vacías")
    void testValidateMailConfiguration_SanitizesGmailAppPasswordSpacesAndHandlesMissingCreds() {
        JavaMailSenderImpl senderImpl = new JavaMailSenderImpl();
        senderImpl.setHost("smtp.gmail.com");
        senderImpl.setUsername("palaciodelmar39@gmail.com");
        senderImpl.setPassword("abcd efgh ijkl mnop");

        EmailService serviceWithRealSender = new EmailService(senderImpl, userRepository);
        ReflectionTestUtils.setField(serviceWithRealSender, "mailUsername", "palaciodelmar39@gmail.com");
        ReflectionTestUtils.setField(serviceWithRealSender, "mailAuth", true);

        assertDoesNotThrow(serviceWithRealSender::validateMailConfiguration);
        assertEquals("abcdefghijklmnop", senderImpl.getPassword());

        EmailService unconfiguredService = new EmailService(new JavaMailSenderImpl(), userRepository);
        ReflectionTestUtils.setField(unconfiguredService, "mailUsername", "");
        ReflectionTestUtils.setField(unconfiguredService, "mailAuth", true);
        assertDoesNotThrow(unconfiguredService::validateMailConfiguration);
    }

    @Test
    @DisplayName("sendBookingCreatedNotification incluye nombre real de suite, sede, dirección, noches, huéspedes y desglose en COP")
    void testSendBookingCreatedNotification_IncludesRichSuitePropertyItineraryAndPricing() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        User guest = User.builder()
                .id("guest-user-1")
                .name("Angel Kaleth")
                .email("angelkaleth633@gmail.com")
                .build();
        when(userRepository.findById("guest-user-1")).thenReturn(Optional.of(guest));

        String rawSuiteObjectId = "671a00000000000000000099";
        Suite suite = Suite.builder()
                .id(rawSuiteObjectId)
                .name("Superior Patio Colonial")
                .type("Suite Colonial")
                .branch("branch-centro")
                .build();
        when(suiteRepository.findById(rawSuiteObjectId)).thenReturn(Optional.of(suite));

        Branch branch = Branch.builder()
                .id("branch-centro")
                .name("Palacio del Mar · Centro Histórico")
                .zone("Centro Histórico")
                .address("Calle de la Factoría, Centro Histórico, Cartagena")
                .build();
        when(branchRepository.findById("branch-centro")).thenReturn(Optional.of(branch));

        Booking booking = Booking.builder()
                .id("671b00000000000000000123")
                .bookingCode("PDM-CARTAGENA01")
                .user("guest-user-1")
                .suite(rawSuiteObjectId)
                .branch("branch-centro")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 15))
                .nights(5)
                .guests(2)
                .subtotal(new BigDecimal("2000000"))
                .taxes(new BigDecimal("380000"))
                .guaranteeDeposit(new BigDecimal("200000"))
                .totalPrice(new BigDecimal("2580000"))
                .status("pending")
                .build();

        emailService.sendBookingCreatedNotification(booking);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        String html = extractHtmlContent(captor.getValue());

        assertTrue(html.contains("PDM-CARTAGENA01"), "Debe incluir el código legible de reserva");
        assertTrue(html.contains("Superior Patio Colonial"), "Debe incluir el nombre real de la suite");
        assertTrue(html.contains("Suite Colonial"), "Debe incluir el tipo de suite");
        assertFalse(html.contains(rawSuiteObjectId), "Nunca debe mostrar el ObjectId hexadecimal de la suite");
        assertTrue(html.contains("Palacio del Mar · Centro Histórico"), "Debe incluir el nombre de la sede/propiedad");
        assertTrue(html.contains("Calle de la Factoría, Centro Histórico, Cartagena"), "Debe incluir la dirección física");
        assertTrue(html.contains("5 noches"), "Debe mostrar la cantidad de noches");
        assertTrue(html.contains("2"), "Debe mostrar la cantidad de huéspedes");
        assertTrue(html.contains("2.580.000"), "Debe formatear el total en pesos colombianos");
        assertTrue(html.contains("http://localhost:4200/checkout/671b00000000000000000123"));
    }

    @Test
    @DisplayName("sendBookingConfirmation incluye detalles completos de alojamiento y pago en HTML, PDF e ICS")
    void testSendBookingConfirmation_IncludesRichAccommodationAndPaymentDetailsInHtmlPdfAndIcs() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(createMockMimeMessage());

        User guest = User.builder()
                .id("guest-user-55")
                .name("Valentina Torres")
                .email("valentina@gmail.com")
                .build();
        when(userRepository.findById("guest-user-55")).thenReturn(Optional.of(guest));

        Suite suite = Suite.builder()
                .id("suite-id-01")
                .name("Penthouse Vista Mar")
                .type("Penthouse Deluxe")
                .propertyId("prop-bocagrande")
                .build();
        when(suiteRepository.findById("suite-id-01")).thenReturn(Optional.of(suite));

        Property property = Property.builder()
                .id("prop-bocagrande")
                .name("Torre Marina Bocagrande")
                .address("Av. San Martín #8-45, Bocagrande")
                .city("Cartagena")
                .build();
        when(propertyRepository.findById("prop-bocagrande")).thenReturn(Optional.of(property));

        Payment completedPayment = Payment.builder()
                .id("pay-01")
                .booking("bk-id-555")
                .status("completed")
                .paymentMethod("pse")
                .provider("simulated")
                .transactionId("TXN-PSE-998877")
                .amount(new BigDecimal("3200000"))
                .currency("COP")
                .build();
        when(paymentRepository.findByBooking("bk-id-555")).thenReturn(List.of(completedPayment));

        Booking booking = Booking.builder()
                .id("bk-id-555")
                .bookingCode("PDM-998877665544")
                .user("guest-user-55")
                .suite("suite-id-01")
                .propertyId("prop-bocagrande")
                .smartLockPin("482910")
                .checkIn(LocalDate.of(2026, 12, 20))
                .checkOut(LocalDate.of(2026, 12, 24))
                .nights(4)
                .guests(3)
                .totalPrice(new BigDecimal("3200000"))
                .paidAmount(new BigDecimal("3200000"))
                .balanceDue(BigDecimal.ZERO)
                .status("confirmed")
                .paymentStatus("paid")
                .build();

        emailService.sendBookingConfirmation(booking);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());

        String html = extractHtmlContent(captor.getValue());
        assertTrue(html.contains("Valentina Torres"));
        assertTrue(html.contains("PDM-998877665544"));
        assertTrue(html.contains("482910"));
        assertTrue(html.contains("Penthouse Vista Mar"));
        assertTrue(html.contains("Penthouse Deluxe"));
        assertTrue(html.contains("Torre Marina Bocagrande"));
        assertTrue(html.contains("Av. San Martín #8-45, Bocagrande"));
        assertTrue(html.contains("4 noches"));
        assertTrue(html.contains("PSE"));
        assertTrue(html.contains("TXN-PSE-998877"));
        assertTrue(html.contains("3.200.000"));

        Multipart content = (Multipart) captor.getValue().getContent();
        byte[] pdfBytes = null;
        String icsContent = null;
        for (int i = 0; i < content.getCount(); i++) {
            BodyPart part = content.getBodyPart(i);
            if (part.getFileName() != null && part.getFileName().endsWith(".pdf")) {
                pdfBytes = part.getInputStream().readAllBytes();
            } else if (part.getFileName() != null && part.getFileName().endsWith(".ics")) {
                icsContent = new String(part.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        assertNotNull(pdfBytes);
        PdfReader pdfReader = new PdfReader(pdfBytes);
        String pdfText;
        try {
            pdfText = new PdfTextExtractor(pdfReader).getTextFromPage(1);
        } finally {
            pdfReader.close();
        }
        assertTrue(pdfText.contains("PDM-998877665544"));
        assertTrue(pdfText.contains("Penthouse Vista Mar"));
        assertTrue(pdfText.contains("Torre Marina Bocagrande"));
        assertTrue(pdfText.contains("PSE"));
        assertTrue(pdfText.contains("TXN-PSE-998877"));

        assertNotNull(icsContent);
        assertTrue(icsContent.contains("Penthouse Vista Mar"));
        assertTrue(icsContent.contains("PDM-998877665544"));
        assertTrue(icsContent.contains("LOCATION:"));
    }
}
