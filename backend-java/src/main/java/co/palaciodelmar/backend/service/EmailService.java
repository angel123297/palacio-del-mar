package co.palaciodelmar.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Error al enviar el correo electrónico: " + e.getMessage());
        }
    }

    public void sendReservationConfirmation(String to, String userName, String details, String actionUrl) {
        String htmlContent = "<div style=\"background-color: #0f0f0f; color: #f5f5f5; padding: 30px; font-family: Arial, sans-serif; text-align: center;\">" +
            "<h1 style=\"color: #d4af37; letter-spacing: 2px;\">Palacio del Mar</h1>" +
            "<p style=\"font-size: 12px; color: #b3b3b3; text-transform: uppercase;\">Cartagena de Indias · Colombia</p>" +
            "<div style=\"background-color: #1a1a1a; padding: 25px; border-radius: 8px; margin-top: 20px; border: 1px solid #333;\">" +
                "<h2 style=\"color: #ffffff;\">¡Reserva Confirmada!</h2>" +
                "<p style=\"color: #cccccc;\">Hola " + userName + ", tu reserva ha sido procesada con éxito.</p>" +
                "<p style=\"background-color: #262626; padding: 15px; border-radius: 5px; color: #d4af37;\">" + details + "</p>" +
                "<a href=\"" + actionUrl + "\" style=\"display: inline-block; background-color: #d4af37; color: #000000; padding: 12px 25px; text-decoration: none; font-weight: bold; border-radius: 4px; margin-top: 20px;\">Ver Detalles en la App</a>" +
            "</div>" +
        "</div>";
        
        sendHtmlEmail(to, "Confirmación de Reserva - Palacio del Mar", htmlContent);
    }
}