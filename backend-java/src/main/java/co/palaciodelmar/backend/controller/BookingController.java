package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.BookingRequest;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final co.palaciodelmar.backend.service.EmailService emailService;
    private final co.palaciodelmar.backend.repository.BookingRepository bookingRepository;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingDTO>> createBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BookingRequest request
    ) {
        String userId = principal != null ? principal.getUser().getId() : null;
        BookingDTO booking = bookingService.createBooking(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Reserva creada exitosamente", booking));
    }

    @GetMapping({"/", "/my-bookings", "/mis-reservas"})
    public ResponseEntity<ApiResponse<List<BookingDTO>>> getMyBookings(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        List<BookingDTO> bookings = bookingService.getUserBookings(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @GetMapping("/{id}/voucher/pdf")
    public ResponseEntity<byte[]> getVoucherPdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        BookingDTO bookingDTO = bookingService.getBookingByIdSecure(id, principal != null ? principal.getUser() : null);
        co.palaciodelmar.backend.model.Booking booking = bookingRepository.findById(bookingDTO.getId())
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));
        byte[] pdfBytes = emailService.generateReceiptPdf(booking);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=voucher-" + booking.getId() + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<BookingDTO>>> getUpcomingBookings(@AuthenticationPrincipal UserPrincipal principal) {
        List<BookingDTO> bookings = bookingService.getUpcomingBookings(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @GetMapping("/code/{bookingCode}")
    public ResponseEntity<ApiResponse<BookingDTO>> getBookingByCode(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String bookingCode
    ) {
        BookingDTO booking = bookingService.getBookingByCodeSecure(bookingCode, principal != null ? principal.getUser() : null);
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingDTO>> getBookingById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        BookingDTO booking = bookingService.getBookingByIdSecure(id, principal != null ? principal.getUser() : null);
        return ResponseEntity.ok(ApiResponse.ok(booking));
    }

    @PutMapping({"/{id}/modify", "/{id}/modify-dates"})
    public ResponseEntity<ApiResponse<BookingDTO>> modifyBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody BookingRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud no puede estar vacío");
        }
        if (request.getCheckIn() == null || request.getCheckOut() == null) {
            throw new IllegalArgumentException("Las fechas de check-in y check-out son obligatorias");
        }
        if (!request.getCheckOut().isAfter(request.getCheckIn())) {
            throw new IllegalArgumentException("La fecha de salida debe ser posterior a la fecha de llegada");
        }
        BookingDTO booking = bookingService.modifyBooking(id, principal.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Reserva modificada exitosamente", booking));
    }

    @RequestMapping(value = {"/{id}/cancel", "/cancelar/{id}"}, method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<ApiResponse<BookingDTO>> cancelBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String reason = body != null ? body.get("reason") : null;
        BookingDTO booking = bookingService.cancelBooking(id, principal.getUser().getId(), reason);
        return ResponseEntity.ok(ApiResponse.ok("Reserva cancelada exitosamente", booking));
    }
}