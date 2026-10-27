package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Review;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.ReviewRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private co.palaciodelmar.backend.repository.SuiteRepository suiteRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private co.palaciodelmar.backend.repository.PropertyRepository propertyRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private co.palaciodelmar.backend.webhook.WebhookDispatcher webhookDispatcher;

    public List<ReviewDTO> getSuiteReviews(String suiteId) {
        return reviewRepository.findBySuiteAndApprovedTrueOrderByCreatedAtDesc(suiteId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public ReviewDTO addReview(String suiteId, String userId, String userName, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("La calificación debe estar entre 1 y 5 estrellas");
        }

        if (userId != null && !"anonymous".equalsIgnoreCase(userId) && suiteRepository != null && propertyRepository != null) {
            suiteRepository.findById(suiteId).ifPresent(s -> {
                if (s.getPropertyId() != null) {
                    propertyRepository.findById(s.getPropertyId()).ifPresent(p -> {
                        if (userId.equals(p.getOwnerId())) {
                            throw new IllegalArgumentException("Un anfitrión no puede calificar su propia propiedad");
                        }
                    });
                }
            });
        }

        AnalysisResult analysis = analyzeTextSentiment(comment, rating);

        Review review = Review.builder()
                .suite(suiteId)
                .user(userId)
                .userName(userName != null ? userName : "Huésped")
                .rating(rating)
                .comment(comment)
                .sentiment(analysis.sentiment())
                .sentimentScore(analysis.score())
                .keyHighlights(analysis.highlights())
                .maintenanceAlert(analysis.maintenanceAlert())
                .verifiedStay(false)
                .approved(true)
                .createdAt(Instant.now())
                .build();

        return toDTO(reviewRepository.save(review));
    }

    /**
     * Crea una reseña verificada vinculada a una reserva completada.
     * Solo permite 1 reseña por reserva.
     */
    public ReviewDTO addVerifiedReview(String bookingId, String userId, Integer rating, String comment) {
        if (bookingId == null || bookingId.isBlank()) {
            throw new IllegalArgumentException("El ID de la reserva es obligatorio para verificar la estadía");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("La calificación debe estar entre 1 y 5 estrellas");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (userId != null && !userId.equals(booking.getUser())) {
            throw new IllegalArgumentException("Solo el huésped titular puede calificar esta reserva");
        }

        if (booking.getStatus() != null && "cancelled".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("No se pueden enviar reseñas para reservas canceladas");
        }

        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new IllegalStateException("Ya has enviado una reseña para esta reserva");
        }

        String userName = "Huésped Verificado";
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getName() != null) userName = user.getName();
        } else if (booking.getGuestDetails() != null && booking.getGuestDetails().getFullName() != null) {
            userName = booking.getGuestDetails().getFullName();
        }

        AnalysisResult analysis = analyzeTextSentiment(comment, rating);

        Review review = Review.builder()
                .bookingId(bookingId)
                .suite(booking.getSuite())
                .user(userId != null ? userId : booking.getUser())
                .userName(userName)
                .rating(rating)
                .comment(comment)
                .verifiedStay(true)
                .sentiment(analysis.sentiment())
                .sentimentScore(analysis.score())
                .keyHighlights(analysis.highlights())
                .maintenanceAlert(analysis.maintenanceAlert())
                .approved(true)
                .createdAt(Instant.now())
                .build();

        log.info("Reseña verificada registrada con sentimiento {}: {}", analysis.sentiment(), analysis.highlights());
        Review saved = reviewRepository.save(review);
        ReviewDTO dto = toDTO(saved);

        // Si se detectó alerta de mantenimiento, despachar webhook automático al anfitrión
        if (analysis.maintenanceAlert() != null && webhookDispatcher != null) {
            String propId = booking.getPropertyId() != null ? booking.getPropertyId() : booking.getBranch();
            if (propId != null) {
                Map<String, Object> alertPayload = new java.util.LinkedHashMap<>();
                alertPayload.put("reviewId", saved.getId() != null ? saved.getId() : "");
                alertPayload.put("bookingId", booking.getId() != null ? booking.getId() : bookingId);
                alertPayload.put("suiteId", saved.getSuite() != null ? saved.getSuite() : "");
                alertPayload.put("alert", analysis.maintenanceAlert());
                alertPayload.put("comment", saved.getComment() != null ? saved.getComment() : "");
                alertPayload.put("rating", saved.getRating());
                alertPayload.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : Instant.now().toString());
                webhookDispatcher.dispatchToProperty(propId, "maintenance.alert", alertPayload);
            }
        }

        return dto;
    }

    /**
     * Obtiene la reseña de una reserva verificando permisos (huésped titular, anfitrión de la propiedad o admin).
     */
    public ReviewDTO getReviewByBookingId(String bookingId, User user) {
        if (bookingId == null || bookingId.isBlank()) return null;
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) return null;

        if (user != null) {
            boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole());
            boolean isGuest = user.getId() != null && user.getId().equals(booking.getUser());
            boolean isHost = false;
            if (("HOST".equalsIgnoreCase(user.getRole()) || "ROLE_HOST".equalsIgnoreCase(user.getRole())) && booking.getPropertyId() != null && propertyRepository != null) {
                isHost = propertyRepository.findById(booking.getPropertyId())
                        .map(p -> user.getId().equals(p.getOwnerId()))
                        .orElse(false);
            }
            if (!isAdmin && !isGuest && !isHost) {
                return null;
            }
        }

        return reviewRepository.findByBookingId(bookingId)
                .map(this::toDTO)
                .orElse(null);
    }

    public ReviewDTO resolveMaintenanceAlert(String reviewId, String userId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Reseña no encontrada: " + reviewId));

        if (review.getMaintenanceAlert() == null || review.getMaintenanceAlert().isBlank()) {
            throw new IllegalStateException("La reseña no tiene ninguna alerta de mantenimiento activa");
        }

        // Validación Anti-IDOR: Host debe ser dueño de la propiedad (o ser Administrador)
        User user = (userId != null && userRepository != null) ? userRepository.findById(userId).orElse(null) : null;
        boolean isAdmin = user != null && ("ADMIN".equalsIgnoreCase(user.getRole()) || "ROLE_ADMIN".equalsIgnoreCase(user.getRole()));

        if (!isAdmin) {
            co.palaciodelmar.backend.model.Suite suite = (review.getSuite() != null && suiteRepository != null)
                    ? suiteRepository.findById(review.getSuite()).orElse(null)
                    : null;
            if (suite == null || suite.getPropertyId() == null) {
                throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para gestionar alertas de esta habitación");
            }
            co.palaciodelmar.backend.model.Property property = (propertyRepository != null)
                    ? propertyRepository.findById(suite.getPropertyId()).orElse(null)
                    : null;
            if (property == null || property.getOwnerId() == null || !property.getOwnerId().equals(userId)) {
                throw new org.springframework.security.access.AccessDeniedException("No tienes permiso para gestionar alertas de una propiedad ajena");
            }
        }

        review.setMaintenanceResolvedAt(Instant.now());
        review.setMaintenanceResolvedBy(userId);
        return toDTO(reviewRepository.save(review));
    }

    /**
     * Métricas de satisfacción y alertas de mantenimiento para el Administrador.
     */
    public Map<String, Object> getSentimentMetrics() {
        List<Review> allReviews = reviewRepository.findAll();
        long total = allReviews.size();
        if (total == 0) {
            return Map.of(
                    "totalReviews", 0,
                    "positivePercentage", 100.0,
                    "averageRating", 5.0,
                    "highlights", List.of(),
                    "maintenanceAlerts", List.of()
            );
        }

        long positiveCount = allReviews.stream().filter(r -> "POSITIVE".equals(r.getSentiment())).count();
        double avgRating = allReviews.stream().mapToInt(Review::getRating).average().orElse(5.0);
        List<String> alerts = allReviews.stream()
                .map(Review::getMaintenanceAlert)
                .filter(a -> a != null && !a.isBlank())
                .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("totalReviews", total);
        result.put("positivePercentage", (double) Math.round((positiveCount * 100.0 / total) * 10) / 10);
        result.put("averageRating", (double) Math.round(avgRating * 10) / 10);
        result.put("maintenanceAlerts", alerts);
        return result;
    }

    private record AnalysisResult(String sentiment, Double score, List<String> highlights, String maintenanceAlert) {}

    private AnalysisResult analyzeTextSentiment(String text, int rating) {
        if (text == null) text = "";
        String lower = text.toLowerCase();

        List<String> highlights = new ArrayList<>();
        if (lower.contains("piscina")) highlights.add("Piscina");
        if (lower.contains("desayuno") || lower.contains("comida")) highlights.add("Gastronomía");
        if (lower.contains("vista") || lower.contains("balcon") || lower.contains("balcón")) highlights.add("Vistas");
        if (lower.contains("cama") || lower.contains("comoda") || lower.contains("cómoda")) highlights.add("Confort");
        if (lower.contains("servicio") || lower.contains("atencion") || lower.contains("atención") || lower.contains("amable")) highlights.add("Servicio al cliente");
        if (lower.contains("ubicacion") || lower.contains("ubicación") || lower.contains("centro")) highlights.add("Ubicación privilegiada");

        String maintenanceAlert = null;
        if (lower.contains("aire") && (lower.contains("ruido") || lower.contains("calor") || lower.contains("suena") || lower.contains("fallo"))) {
            maintenanceAlert = "Revisión preventiva: Posible fallo o ruido en aire acondicionado";
        } else if (lower.contains("ducha") || lower.contains("agua") && (lower.contains("fria") || lower.contains("fría") || lower.contains("presion"))) {
            maintenanceAlert = "Revisión preventiva: Presión o temperatura de agua en ducha";
        } else if (lower.contains("olor") || lower.contains("humedad")) {
            maintenanceAlert = "Revisión preventiva: Alerta de humedad o ventilación";
        }

        String sentiment;
        double score;
        if (rating >= 4) {
            sentiment = "POSITIVE";
            score = 0.85 + (rating == 5 ? 0.10 : 0.0);
        } else if (rating == 3) {
            sentiment = "NEUTRAL";
            score = 0.50;
        } else {
            sentiment = "NEGATIVE";
            score = 0.20;
        }

        return new AnalysisResult(sentiment, score, highlights, maintenanceAlert);
    }

    public ReviewDTO toDTO(Review review) {
        if (review == null) return null;
        return ReviewDTO.builder()
                .id(review.getId())
                .suite(review.getSuite())
                .user(review.getUser())
                .bookingId(review.getBookingId())
                .userName(review.getUserName())
                .rating(review.getRating())
                .comment(review.getComment())
                .verifiedStay(review.getVerifiedStay())
                .sentiment(review.getSentiment())
                .sentimentScore(review.getSentimentScore())
                .keyHighlights(review.getKeyHighlights())
                .maintenanceAlert(review.getMaintenanceAlert())
                .maintenanceResolvedAt(review.getMaintenanceResolvedAt())
                .maintenanceResolvedBy(review.getMaintenanceResolvedBy())
                .approved(review.getApproved())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
