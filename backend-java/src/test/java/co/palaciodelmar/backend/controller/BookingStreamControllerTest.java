package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.service.BookingSseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingStreamController SSE Security & Subscription Tests")
class BookingStreamControllerTest {

    @Mock
    private BookingSseService bookingSseService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private BookingStreamController bookingStreamController;

    @Test
    @DisplayName("Lanza SecurityException si la autenticación es nula o no autenticada")
    void testRejectsUnauthenticatedStream() {
        assertThrows(SecurityException.class, () -> bookingStreamController.streamBookings(null));

        when(authentication.isAuthenticated()).thenReturn(false);
        assertThrows(SecurityException.class, () -> bookingStreamController.streamBookings(authentication));
    }

    @Test
    @DisplayName("Suscribe exitosamente al usuario registrado devolviendo SseEmitter")
    void testSubscribesAuthenticatedUser() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("guest@palaciodelmar.co");

        User user = User.builder().id("usr_999").email("guest@palaciodelmar.co").build();
        when(userRepository.findByEmail("guest@palaciodelmar.co")).thenReturn(Optional.of(user));

        SseEmitter mockEmitter = new SseEmitter();
        when(bookingSseService.subscribe("usr_999")).thenReturn(mockEmitter);

        SseEmitter result = bookingStreamController.streamBookings(authentication);
        assertNotNull(result);
        assertEquals(mockEmitter, result);
        verify(bookingSseService, times(1)).subscribe("usr_999");
    }
}
