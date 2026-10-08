package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {
    List<Review> findBySuiteAndApprovedTrueOrderByCreatedAtDesc(String suiteId);
}
