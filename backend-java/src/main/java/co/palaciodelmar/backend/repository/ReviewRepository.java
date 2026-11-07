package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {
    List<Review> findBySuiteAndApprovedTrueOrderByCreatedAtDesc(String suiteId);
    boolean existsByBookingId(String bookingId);
    Optional<Review> findByBookingId(String bookingId);
    List<Review> findByMaintenanceAlertIsNotNull();
}
