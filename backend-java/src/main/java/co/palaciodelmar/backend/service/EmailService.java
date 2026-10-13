package co.palaciodelmar.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("Verifica tu cuenta - Palacio del Mar");

            String htmlMsg = "<p>Hola,</p>"
                    + "<p>Gracias por registrarte en Palacio del Mar.</p>"
                    + "<p>Por favor, verifica tu cuenta haciendo clic en el siguiente enlace:</p>"
                    + "<p><a href=\"http://localhost:4200/verificar-email/" + token + "\">Verificar cuenta</a></p>"
                    + "<p>Si no solicitaste esto, puedes ignorar este correo.</p>";

            helper.setText(htmlMsg, true);

            javaMailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Error al enviar el correo de verificación", e);
        }
    }
}
