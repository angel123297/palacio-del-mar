package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.model.Review;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suites")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{suiteId}/reviews")
    public ResponseEntity<ApiResponse<List<Review>>> getSuiteReviews(@PathVariable String suiteId) {
        List<Review> reviews = reviewService.getSuiteReviews(suiteId);
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    @PostMapping("/{suiteId}/reviews")
    public ResponseEntity<ApiResponse<Review>> addReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String suiteId,
            @RequestBody Map<String, Object> body
    ) {
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");
        String userName = principal != null ? principal.getUser().getName() : "Huésped";
        String userId = principal != null ? principal.getUser().getId() : "anonymous";

        Review review = reviewService.addReview(suiteId, userId, userName, rating, comment);
        return ResponseEntity.ok(ApiResponse.ok("Reseña agregada exitosamente", review));
    }
}
