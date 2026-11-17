package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
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
    private static final ThreadLocal<Long> CURRENT_AUTH_SEQ = new ThreadLocal<>();

    // Mapa de emisores indexado por userId -> lista de conexiones activas (pestañas o dispositivos múltiples)
    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final Map<String, Long> userRevokedSeq = new ConcurrentHashMap<>();
    private final Map<String, Instant> userRevokedAt = new ConcurrentHashMap<>();

    public static void setCurrentAuthSequence(Long seq) {
        if (seq != null) {
            CURRENT_AUTH_SEQ.set(seq);
        } else {
            CURRENT_AUTH_SEQ.remove();
        }
    }

    public static void clearCurrentAuthSequence() {
        CURRENT_AUTH_SEQ.remove();
    }

    public boolean isSessionRevoked(String key, long authSequence) {
        if (key == null || key.isBlank()) {
            return false;
        }
        Long revSeq = userRevokedSeq.get(key);
        return revSeq != null && authSequence <= revSeq;
    }

    public SseEmitter subscribe(String userId) {
        return subscribe(userId, new SseEmitter(SSE_TIMEOUT));
    }

    public SseEmitter subscribe(List<String> channels) {
        return subscribe(channels, new SseEmitter(SSE_TIMEOUT));
    }

    public SseEmitter subscribe(String userId, SseEmitter emitter) {
        return subscribe(userId != null ? List.of(userId) : List.of(), emitter);
    }

    public SseEmitter subscribe(List<String> channels, SseEmitter emitter) {
        if (channels == null || channels.isEmpty() || emitter == null) {
            return emitter;
        }
        Long authSeq = CURRENT_AUTH_SEQ.get();
        for (String channel : channels) {
            if (channel == null || channel.isBlank()) continue;
            userEmitters.compute(channel, (k, existing) -> {
                if (authSeq != null && isSessionRevoked(channel, authSeq)) {
                    throw new SecurityException("La sesión fue revocada antes de abrir el canal de eventos");
                }
                List<SseEmitter> list = existing != null ? existing : new CopyOnWriteArrayList<>();
                list.add(emitter);
                return list;
            });
        }

        Runnable cleanup = () -> removeEmitterFromAllChannels(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            // Evento inicial de handshake
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "message", "Conectado al flujo de eventos en tiempo real de Palacio del Mar",
                            "channels", channels
                    )));
        } catch (IOException e) {
            log.warn("Error enviando handshake SSE en canales {}: {}", channels, e.getMessage());
            cleanup.run();
        }

        return emitter;
    }

    public void emitBookingUpdate(String userId, BookingDTO booking) {
        emitBookingUpdate(userId, booking, null);
    }

    public void emitBookingUpdate(String userId, BookingDTO booking, String eventType) {
        if (userId == null || booking == null) return;

        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No hay subscriptores SSE activos para el usuario {}", userId);
            return;
        }

        String eventName = (eventType != null && !eventType.isBlank())
                ? eventType.replace('.', '_')
                : (booking.getStatus() != null ? "booking_" + booking.getStatus().toLowerCase() : "booking_updated");

        List<SseEmitter> deadEmitters = new ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
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

    public void removeEmitterFromAllChannels(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        for (String key : new ArrayList<>(userEmitters.keySet())) {
            removeEmitter(key, emitter);
        }
    }

    public int getActiveSubscribersCount() {
        return userEmitters.values().stream().mapToInt(List::size).sum();
    }

    public void closeUserConnections(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        long revSeq = UserPrincipal.nextAuthSequence();
        userRevokedSeq.put(userId, revSeq);
        userRevokedAt.put(userId, Instant.now());
        if (userRevokedAt.size() > 5000) {
            Instant cutoff = Instant.now().minus(java.time.Duration.ofHours(24));
            userRevokedAt.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
            userRevokedSeq.keySet().retainAll(userRevokedAt.keySet());
        }
        List<SseEmitter> removed = userEmitters.remove(userId);
        if (removed != null) {
            for (SseEmitter emitter : removed) {
                removeEmitterFromAllChannels(emitter);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
