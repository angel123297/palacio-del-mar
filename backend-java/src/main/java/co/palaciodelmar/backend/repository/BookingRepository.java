package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Booking;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends MongoRepository<Booking, String> {
    List<Booking> findByUserOrderByCreatedAtDesc(String userId);
    Optional<Booking> findByBookingCode(String bookingCode);
    List<Booking> findBySuiteAndStatusIn(String suiteId, List<String> statuses);
    List<Booking> findByStatusAndExpiresAtBefore(String status, java.time.Instant now);
}
