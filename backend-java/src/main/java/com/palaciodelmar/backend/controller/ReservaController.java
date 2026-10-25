package com.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import com.palaciodelmar.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private co.palaciodelmar.backend.service.EmailService emailServiceCo;

    @Autowired
    private co.palaciodelmar.backend.repository.BookingRepository bookingRepository;

    @GetMapping("/mis-reservas")
    public ResponseEntity<ApiResponse<List<BookingDTO>>> getMisReservas(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        List<BookingDTO> bookings = bookingService.getUserBookings(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ApiResponse<BookingDTO>> cancelarReserva(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : "Cancelada por usuario";
        BookingDTO booking = bookingService.cancelBooking(id, principal != null ? principal.getUser().getId() : null, reason);
        return ResponseEntity.ok(ApiResponse.ok("Reserva cancelada exitosamente", booking));
    }

    @GetMapping("/{id}/comprobante-pdf")
    public ResponseEntity<byte[]> descargarComprobante(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        BookingDTO bookingDTO = bookingService.getBookingByIdSecure(id, principal != null ? principal.getUser() : null);
        co.palaciodelmar.backend.model.Booking booking = bookingRepository.findById(bookingDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));
        byte[] pdfBytes = emailServiceCo.generateReceiptPdf(booking);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=comprobante-" + booking.getId() + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @PostMapping("/completar")
    public ResponseEntity<String> completarReserva(@RequestBody ReservaRequest request) {
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
