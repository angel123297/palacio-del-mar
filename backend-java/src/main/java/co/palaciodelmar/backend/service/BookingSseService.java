package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servicio reactivo para la suscripción y despacho de eventos en tiempo real (Server-Sent Events)
 * hacia clientes frontend (Angular / Flutter) sobre reservas confirmadas o actualizadas.
 */
@Slf4j
@Service
public class BookingSseService {

    private static final Long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutos

    // Mapa de emisores indexado por userId -> lista de conexiones activas (pestañas o dispositivos múltiples)
    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        return subscribe(userId, new SseEmitter(SSE_TIMEOUT));
    }

    public SseEmitter subscribe(String userId, SseEmitter emitter) {
        if (userId == null || emitter == null) {
            return emitter;
        }
        userEmitters.compute(userId, (k, existing) -> {
            List<SseEmitter> list = existing != null ? existing : new CopyOnWriteArrayList<>();
            list.add(emitter);
            return list;
        });

        Runnable cleanup = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            // Evento inicial de handshake
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "message", "Conectado al flujo de eventos en tiempo real de Palacio del Mar",
                            "userId", userId
                    )));
        } catch (IOException e) {
            log.warn("Error enviando handshake SSE a usuario {}: {}", userId, e.getMessage());
            cleanup.run();
        }

        return emitter;
    }

    public void emitBookingUpdate(String userId, BookingDTO booking) {
        if (userId == null || booking == null) return;

        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No hay subscriptores SSE activos para el usuario {}", userId);
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("booking_confirmed")
                        .data(booking));
            } catch (Exception ex) {
                log.warn("Fallo al emitir evento SSE a cliente de {}: {}", userId, ex.getMessage());
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            userEmitters.compute(userId, (k, current) -> {
                if (current == null) {
                    return null;
                }
                current.removeAll(deadEmitters);
                return current.isEmpty() ? null : current;
            });
        }
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        if (userId == null || emitter == null) {
            return;
        }
        userEmitters.compute(userId, (k, current) -> {
            if (current == null) {
                return null;
            }
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }

    public int getActiveSubscribersCount() {
        return userEmitters.values().stream().mapToInt(List::size).sum();
    }
}
