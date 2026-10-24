package com.palaciodelmar.backend.controller;

import com.palaciodelmar.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/completar")
    public ResponseEntity<String> completarReserva(@RequestBody ReservaRequest request) {
        
        // Lógica de procesamiento de pago y guardado en base de datos aquí...

        // Disparador automático del correo con los datos reales de la compra
        emailService.sendReservationReceipt(
            request.getEmail(),
            request.getGuestName(),
            request.getHotelDetails(),
            request.getAmount()
        );

        return ResponseEntity.ok("Reserva procesada con éxito y correo de comprobante enviado.");
    }
}
class ReservaRequest {
    private String email;
    private String guestName;
    private String hotelDetails;
    private String amount;

    public String getEmail() { return email; }
    public String getGuestName() { return guestName; }
    public String getHotelDetails() { return hotelDetails; }
    public String getAmount() { return amount; }
}
