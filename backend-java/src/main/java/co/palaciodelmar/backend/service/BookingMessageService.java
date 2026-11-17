package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BookingMessageDTO;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.BookingMessage;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BookingMessageRepository;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingMessageService {

    private final BookingMessageRepository bookingMessageRepository;
    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final SuiteRepository suiteRepository;

    private boolean isHostOfBooking(Booking booking, String userId) {
        if (booking == null || userId == null) return false;
        String propId = booking.getPropertyId();
        if (propId == null && booking.getSuite() != null) {
            Suite suite = suiteRepository.findById(booking.getSuite()).orElse(null);
            if (suite != null) {
                propId = suite.getPropertyId();
            }
        }
        if (propId != null) {
            Property prop = propertyRepository.findById(propId).orElse(null);
            return prop != null && userId.equals(prop.getOwnerId());
        }
        return false;
    }

    public List<BookingMessageDTO> getMessages(String bookingId, String userId, boolean isAdmin) {
        return getMessages(bookingId, userId, isAdmin, 1, 50);
    }

    public List<BookingMessageDTO> getMessages(String bookingId, String userId, boolean isAdmin, int page, int limit) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        boolean isGuest = booking.getUser() != null && booking.getUser().equals(userId);
        boolean isHost = isHostOfBooking(booking, userId);

        if (!isGuest && !isHost && !isAdmin) {
            throw new AccessDeniedException("No tienes autorización para consultar los mensajes de esta reserva");
        }

        int cleanPage = Math.max(1, page);
        int cleanLimit = Math.min(100, Math.max(1, limit));
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(cleanPage - 1, cleanLimit);
        org.springframework.data.domain.Page<BookingMessage> pageResult = bookingMessageRepository.findByBookingIdOrderByCreatedAtAsc(bookingId, pageable);
        List<BookingMessage> messages = pageResult.getContent();
        List<BookingMessage> toUpdateRead = new ArrayList<>();
        Instant now = Instant.now();

        for (BookingMessage msg : messages) {
            if (msg.getSenderId() != null && !msg.getSenderId().equals(userId) && msg.getReadAt() == null) {
                msg.setReadAt(now);
                toUpdateRead.add(msg);
            }
        }

        if (!toUpdateRead.isEmpty()) {
            try {
                bookingMessageRepository.saveAll(toUpdateRead);
            } catch (Exception e) {
                log.warn("No se pudo actualizar readAt para mensajes de reserva {}: {}", bookingId, e.getMessage());
            }
        }

        return messages.stream()
                .map(m -> BookingMessageDTO.builder()
                        .id(m.getId())
                        .bookingId(m.getBookingId())
                        .senderId(m.getSenderId())
                        .senderName(m.getSenderName())
                        .senderRole(m.getSenderRole())
                        .message(m.getMessage())
                        .createdAt(m.getCreatedAt())
                        .readAt(m.getReadAt())
                        .me(m.getSenderId() != null && m.getSenderId().equals(userId))
                        .build())
                .toList();
    }

    public BookingMessageDTO sendMessage(String bookingId, String userId, String userName, String text, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));

        boolean isGuest = booking.getUser() != null && booking.getUser().equals(userId);
        boolean isHost = isHostOfBooking(booking, userId);

        String role;
        if (isAdmin) {
            role = "ADMIN";
        } else if (isGuest) {
            role = "GUEST";
        } else if (isHost) {
            role = "HOST";
        } else {
            throw new AccessDeniedException("No tienes autorización para enviar mensajes en esta reserva");
        }

        String bookingStatus = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "";
        if (bookingStatus.equals("cancelled") || bookingStatus.equals("expired")) {
            throw new IllegalStateException("No se pueden enviar mensajes en una reserva cancelada o expirada");
        }

        String sanitizedText = (text != null) ? text.trim() : "";
        if (sanitizedText.isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío");
        }
        if (sanitizedText.length() > 2000) {
            sanitizedText = sanitizedText.substring(0, 2000);
        }

        String displayName = (userName != null && !userName.isBlank()) ? userName : (role.equals("GUEST") ? "Huésped" : "Anfitrión");

        BookingMessage msg = BookingMessage.builder()
                .bookingId(bookingId)
                .senderId(userId)
                .senderName(displayName)
                .senderRole(role)
                .message(sanitizedText)
                .createdAt(Instant.now())
                .build();

        BookingMessage saved = bookingMessageRepository.save(msg);

        return BookingMessageDTO.builder()
                .id(saved.getId())
                .bookingId(saved.getBookingId())
                .senderId(saved.getSenderId())
                .senderName(saved.getSenderName())
                .senderRole(saved.getSenderRole())
                .message(saved.getMessage())
                .createdAt(saved.getCreatedAt())
                .readAt(saved.getReadAt())
                .me(true)
                .build();
    }
}
