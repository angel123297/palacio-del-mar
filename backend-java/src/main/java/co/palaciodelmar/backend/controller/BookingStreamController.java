package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.service.BookingSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Stream", description = "Endpoints de eventos en tiempo real (Server-Sent Events) para reservas")
public class BookingStreamController {

    private final BookingSseService bookingSseService;
    private final UserRepository userRepository;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Suscripción SSE a actualizaciones de reservas del usuario en tiempo real")
    public SseEmitter streamBookings(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Debe estar autenticado para suscribirse al canal de eventos");
        }

        String email = authentication.getName();
        String userId = userRepository.findByEmail(email)
                .map(User::getId)
                .orElse(email);

        log.info("Usuario {} ({}) suscrito a canal SSE de reservas", email, userId);
        return bookingSseService.subscribe(userId);
    }
}
