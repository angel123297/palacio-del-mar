package com.palaciodelmar.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendReservationReceipt(String toEmail, String guestName, String hotelDetails, String amount) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("reservas.palaciodelmar@gmail.com");
        message.setTo(toEmail);
        message.setSubject("Comprobante de Reserva - Palacio del Mar");
        message.setText("Hola " + guestName + ",\n\n" +
                "¡Gracias por elegir Palacio del Mar!\n\n" +
                "Detalles de tu reserva:\n" +
                hotelDetails + "\n" +
                "Monto total pagado: $" + amount + "\n\n" +
                "Te esperamos pronto.\n" +
                "Atentamente,\n" +
                "Equipo de Reservas Palacio del Mar");

        mailSender.send(message);
    }
}
