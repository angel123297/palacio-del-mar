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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<ApiResponse<BookingDTO>> createBooking(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody BookingRequest request
    ) {
        String userId = principal != null ? principal.getUser().getId() : null;
        BookingDTO booking = bookingService.createBooking(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Reserva creada exitosamente", booking));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingDTO>>> getUserBookings(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        List<BookingDTO> bookings = bookingService.getUserBookings(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<ApiResponse<List<BookingDTO>>> getMyBookings(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        List<BookingDTO> bookings = bookingService.getUserBookings(principal.getUser().getId());
        return ResponseEntity.ok(ApiResponse.ok(bookings));
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
        BookingDTO booking = bookingService.modifyBooking(id, principal.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Reserva modificada exitosamente", booking));
    }

    @PostMapping("/{id}/experiences")
    public ResponseEntity<ApiResponse<BookingDTO>> addExperience(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String experienceId = body != null ? body.get("experienceId") : null;
        BookingDTO booking = bookingService.addExperienceToBooking(id, principal.getUser().getId(), experienceId);
        return ResponseEntity.ok(ApiResponse.ok("Experiencia agregada exitosamente", booking));
    }

    @DeleteMapping("/{id}/experiences/{experienceId}")
    public ResponseEntity<ApiResponse<BookingDTO>> removeExperience(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id,
            @PathVariable String experienceId
    ) {
        BookingDTO booking = bookingService.removeExperienceFromBooking(id, principal.getUser().getId(), experienceId);
        return ResponseEntity.ok(ApiResponse.ok("Experiencia eliminada exitosamente", booking));
    }

    @GetMapping("/{id}/voucher")
    public ResponseEntity<ApiResponse<BookingDTO>> getVoucher(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        BookingDTO booking = bookingService.getBookingByIdSecure(id, principal != null ? principal.getUser() : null);
        return ResponseEntity.ok(ApiResponse.ok("Comprobante de reserva generado", booking));
    }

    @RequestMapping(value = {"/{id}/cancel"}, method = {RequestMethod.POST, RequestMethod.PUT})
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
