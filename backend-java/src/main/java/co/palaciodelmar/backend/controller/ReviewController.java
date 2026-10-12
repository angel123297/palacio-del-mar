package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/suites/{suiteId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDTO>>> getSuiteReviews(@PathVariable String suiteId) {
        List<ReviewDTO> reviews = reviewService.getSuiteReviews(suiteId);
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    @PostMapping("/suites/{suiteId}/reviews")
    public ResponseEntity<ApiResponse<ReviewDTO>> addReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String suiteId,
            @RequestBody Map<String, Object> body
    ) {
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");
        String userName = principal != null ? principal.getUser().getName() : "Huésped";
        String userId = principal != null ? principal.getUser().getId() : "anonymous";

        ReviewDTO review = reviewService.addReview(suiteId, userId, userName, rating, comment);
        return ResponseEntity.ok(ApiResponse.ok("Reseña agregada exitosamente", review));
    }

    /**
     * Endpoint para reseña verificada post-estadía.
     */
    @PostMapping("/reviews/verified")
    public ResponseEntity<ApiResponse<ReviewDTO>> addVerifiedReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body
    ) {
        if (principal == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Debes iniciar sesión para calificar tu estadía"));
        }

        String bookingId = (String) body.get("bookingId");
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");

        ReviewDTO review = reviewService.addVerifiedReview(bookingId, principal.getUser().getId(), rating, comment);
        return ResponseEntity.ok(ApiResponse.ok("¡Gracias por compartir tu experiencia en Palacio del Mar!", review));
    }

    /**
     * Endpoint administrativo con métricas de sentimiento y alertas de mantenimiento.
     */
    @GetMapping("/admin/reviews/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReviewMetrics() {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getSentimentMetrics()));
    }
}
