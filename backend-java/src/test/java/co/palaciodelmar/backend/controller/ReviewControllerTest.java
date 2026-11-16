package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.ReviewDTO;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewControllerTest {

    private ReviewService reviewService;
    private ReviewController reviewController;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        reviewController = new ReviewController(reviewService);

        User user = User.builder().id("guest-1").name("Carlos").email("carlos@example.com").build();
        principal = new UserPrincipal(user);
    }

    @Test
    @DisplayName("B10: addReview maneja calificación como String o Number sin ClassCastException")
    void addReviewHandlesStringAndNumberRatingsWithoutClassCast() {
        when(reviewService.addReview(eq("suite-1"), eq("guest-1"), eq("Carlos"), eq(5), anyString()))
                .thenReturn(ReviewDTO.builder().id("rev-1").rating(5).build());

        // Caso 1: String "5"
        ResponseEntity<ApiResponse<ReviewDTO>> respString = reviewController.addReview(
                principal, "suite-1", Map.of("rating", "5", "comment", "Excelente")
        );
        assertEquals(HttpStatus.OK, respString.getStatusCode());

        // Caso 2: Double 5.0
        ResponseEntity<ApiResponse<ReviewDTO>> respDouble = reviewController.addReview(
                principal, "suite-1", Map.of("rating", 5.0, "comment", "Excelente")
        );
        assertEquals(HttpStatus.OK, respDouble.getStatusCode());

        // Caso 3: String inválido arroja IllegalArgumentException (400), no ClassCastException (500)
        assertThrows(IllegalArgumentException.class, () -> reviewController.addReview(
                principal, "suite-1", Map.of("rating", "invalido", "comment", "Malo")
        ));
    }
}
