package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Review;
import co.palaciodelmar.backend.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public List<Review> getSuiteReviews(String suiteId) {
        return reviewRepository.findBySuiteAndApprovedTrueOrderByCreatedAtDesc(suiteId);
    }

    public Review addReview(String suiteId, String userId, String userName, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("La calificación debe estar entre 1 y 5 estrellas");
        }

        Review review = Review.builder()
                .suite(suiteId)
                .user(userId)
                .userName(userName != null ? userName : "Huésped")
                .rating(rating)
                .comment(comment)
                .approved(true)
                .createdAt(Instant.now())
                .build();

        return reviewRepository.save(review);
    }
}
