package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingSseMultiChannelTest {

    @Test
    @DisplayName("B18: Suscripción multicanal y limpieza única para todos los canales en SseEmitter")
    void multiChannelSubscriptionCleansAllChannelsOnCompletion() {
        BookingSseService service = new BookingSseService();
        SseEmitter emitter = new SseEmitter(60000L);

        service.subscribe(List.of("user_1", "prop_alpha"), emitter);

        assertEquals(2, service.getActiveSubscribersCount(), "Debe estar registrado en ambos canales");

        // Al invocarse la limpieza (registrada en onCompletion/onTimeout/onError), debe removerse de TODOS los canales
        service.removeEmitterFromAllChannels(emitter);

        assertEquals(0, service.getActiveSubscribersCount(), "Al completarse, debe removerse de TODOS los canales sin fugar memoria");
    }

    @Test
    @DisplayName("B18: emitBookingUpdate utiliza el estado real o eventType como nombre del evento")
    void emitBookingUpdateUsesRealStatusOrEventType() throws IOException {
        BookingSseService service = new BookingSseService();
        SseEmitter mockEmitter = mock(SseEmitter.class);

        service.subscribe(List.of("user_cancel"), mockEmitter);
        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));

        BookingDTO cancelledBooking = BookingDTO.builder()
                .id("b-cancelled")
                .status("cancelled")
                .build();

        service.emitBookingUpdate("user_cancel", cancelledBooking);

        // Debe haberse enviado 2 veces en total: 1 handshake inicial + 1 evento booking_cancelled
        verify(mockEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }
}
