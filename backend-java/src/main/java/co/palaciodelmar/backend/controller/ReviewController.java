package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.model.Review;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/suites")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{suiteId}/reviews")
    public ResponseEntity<ApiResponse<List<ReviewDTO>>> getSuiteReviews(@PathVariable String suiteId) {
        List<ReviewDTO> reviews = reviewService.getSuiteReviews(suiteId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    @PostMapping("/{suiteId}/reviews")
    public ResponseEntity<ApiResponse<ReviewDTO>> addReview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String suiteId,
            @RequestBody Map<String, Object> body
    ) {
        Integer rating = (Integer) body.get("rating");
        String comment = (String) body.get("comment");
        String userName = principal != null ? principal.getUser().getName() : "Huésped";
        String userId = principal != null ? principal.getUser().getId() : "anonymous";

        Review review = reviewService.addReview(suiteId, userId, userName, rating, comment);
        return ResponseEntity.ok(ApiResponse.ok("Reseña agregada exitosamente", toDTO(review)));
    }

    private ReviewDTO toDTO(Review review) {
        if (review == null) return null;
        return ReviewDTO.builder()
                .id(review.getId())
                .suite(review.getSuite())
                .user(review.getUser())
                .userName(review.getUserName())
                .rating(review.getRating())
                .comment(review.getComment())
                .approved(review.getApproved())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
