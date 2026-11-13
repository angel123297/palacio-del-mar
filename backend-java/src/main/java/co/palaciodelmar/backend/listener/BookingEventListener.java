package co.palaciodelmar.backend.listener;

import co.palaciodelmar.backend.event.BookingDomainEvent;
import co.palaciodelmar.backend.service.BookingSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final BookingSseService bookingSseService;

    @EventListener
    public void handleBookingEvent(BookingDomainEvent event) {
        if (event == null || event.getBooking() == null) {
            return;
        }

        log.info("Despachando evento reactivo SSE para tipo: {} reserva: {}", event.getEventType(), event.getBookingId());
        
        // 1. Emitir al usuario huésped titular
        if (event.getBooking().getUser() != null) {
            bookingSseService.emitBookingUpdate(event.getBooking().getUser(), event.getBooking());
        }

        // 2. Broadcast a los canales de monitorización en tiempo real (admin y canal específico de la propiedad del anfitrión)
        bookingSseService.emitBookingUpdate("admin", event.getBooking());
        if (event.getPropertyId() != null) {
            bookingSseService.emitBookingUpdate("prop_" + event.getPropertyId(), event.getBooking());
        }
    }
}
