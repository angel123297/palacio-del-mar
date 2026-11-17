package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BookingMessageDTO;
import co.palaciodelmar.backend.dto.SendMessageRequest;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/bookings/{bookingId}/messages")
@RequiredArgsConstructor
public class BookingMessageController {

    private final BookingMessageService bookingMessageService;

    private String requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUser() == null || principal.getUser().getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión para acceder a la mensajería");
        }
        return principal.getUser().getId();
    }

    private String resolveUserName(UserPrincipal principal) {
        if (principal != null && principal.getUser() != null && principal.getUser().getName() != null) {
            return principal.getUser().getName();
        }
        return "Usuario";
    }

    private boolean resolveIsAdmin(UserPrincipal principal) {
        if (principal == null || principal.getUser() == null || principal.getUser().getRole() == null) {
            return false;
        }
        return "ADMIN".equalsIgnoreCase(principal.getUser().getRole());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BookingMessageDTO>>> getMessages(
            @PathVariable String bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {
        String userId = requireUserId(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        List<BookingMessageDTO> messages = bookingMessageService.getMessages(bookingId, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingMessageDTO>> sendMessage(
            @PathVariable String bookingId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SendMessageRequest request) {
        String userId = requireUserId(principal);
        String userName = resolveUserName(principal);
        boolean isAdmin = resolveIsAdmin(principal);
        BookingMessageDTO message = bookingMessageService.sendMessage(bookingId, userId, userName, request.getMessage(), isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Mensaje enviado exitosamente", message));
    }
}
