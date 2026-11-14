package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.service.BookingSseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Booking Stream", description = "Endpoints de eventos en tiempo real (Server-Sent Events) para reservas")
public class BookingStreamController {

    private final BookingSseService bookingSseService;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    @Autowired
    public BookingStreamController(BookingSseService bookingSseService, UserRepository userRepository, @Autowired(required = false) PropertyRepository propertyRepository) {
        this.bookingSseService = bookingSseService;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
    }

    public BookingStreamController(BookingSseService bookingSseService, UserRepository userRepository) {
        this(bookingSseService, userRepository, null);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Suscripción SSE a actualizaciones de reservas del usuario en tiempo real")
    public SseEmitter streamBookings(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Debe estar autenticado para suscribirse al canal de eventos");
        }

        co.palaciodelmar.backend.security.UserPrincipal principal =
                (authentication.getPrincipal() instanceof co.palaciodelmar.backend.security.UserPrincipal up) ? up : null;
        long authSeq = principal != null
                ? principal.getAuthSequence()
                : co.palaciodelmar.backend.security.UserPrincipal.nextAuthSequence();

        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        String userId = user != null ? user.getId() : email;

        if (user != null) {
            if ("inactive".equalsIgnoreCase(user.getStatus()) || "suspended".equalsIgnoreCase(user.getStatus())) {
                throw new SecurityException("Cuenta inactiva o suspendida");
            }
            if (principal != null) {
                if (!java.util.Objects.equals(principal.getSnapshotSessionsRevokedAt(), user.getSessionsRevokedAt())
                        || !java.util.Objects.equals(principal.getSnapshotPasswordChangedAt(), user.getPasswordChangedAt())) {
                    throw new SecurityException("La sesión fue revocada antes de abrir el canal de eventos");
                }
                if (user.getSessionsRevokedAt() != null) {
                    if (principal.getTokenIssuedAtMs() != null
                            && principal.getTokenIssuedAtMs() <= user.getSessionsRevokedAt().toEpochMilli()) {
                        throw new SecurityException("La sesión fue revocada antes de abrir el canal de eventos");
                    }
                    if (!principal.getAuthenticatedAt().isAfter(user.getSessionsRevokedAt())) {
                        throw new SecurityException("La sesión fue revocada antes de abrir el canal de eventos");
                    }
                }
                if (user.getPasswordChangedAt() != null
                        && !principal.getAuthenticatedAt().isAfter(user.getPasswordChangedAt())) {
                    throw new SecurityException("La sesión fue revocada por cambio de contraseña");
                }
            } else if (user.getSessionsRevokedAt() != null) {
                throw new SecurityException("La sesión fue revocada antes de abrir el canal de eventos");
            }
        }

        if (bookingSseService.isSessionRevoked(userId, authSeq)
                || (email != null && bookingSseService.isSessionRevoked(email, authSeq))) {
            throw new SecurityException("La sesión fue revocada antes de abrir el canal de eventos");
        }

        log.info("Usuario {} ({}) suscrito a canal SSE de reservas", email, userId);
        BookingSseService.setCurrentAuthSequence(authSeq);
        try {
            SseEmitter emitter = bookingSseService.subscribe(userId);

            // Si el usuario es ADMIN, suscribir al canal global admin.
            // Si es HOST, suscribir ÚNICAMENTE a los canales de sus propiedades para aislar tenants (RN-SSE multi-inquilino)
            if (user != null && user.getRole() != null) {
                String roleLower = user.getRole().toLowerCase();
                if ("admin".equals(roleLower)) {
                    bookingSseService.subscribe("admin", emitter);
                } else if ("host".equals(roleLower) && propertyRepository != null && user.getId() != null) {
                    List<Property> properties = propertyRepository.findByOwnerId(user.getId());
                    if (properties != null) {
                        for (Property prop : properties) {
                            if (prop.getId() != null) {
                                bookingSseService.subscribe("prop_" + prop.getId(), emitter);
                                log.debug("Host {} suscrito al canal reactivo de propiedad {}", user.getId(), prop.getId());
                            }
                        }
                    }
                }
            }

            if (bookingSseService.isSessionRevoked(userId, authSeq)
                    || (email != null && bookingSseService.isSessionRevoked(email, authSeq))) {
                bookingSseService.closeUserConnections(userId);
                throw new SecurityException("La sesión fue revocada durante la apertura del canal de eventos");
            }

            return emitter;
        } finally {
            BookingSseService.clearCurrentAuthSequence();
        }
    }
}
