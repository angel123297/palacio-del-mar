package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.SuiteNight;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SuiteNightRepository extends MongoRepository<SuiteNight, String> {
    @org.springframework.data.mongodb.repository.Query("{ 'suite': ?0, 'dates': { $elemMatch: { $gte: ?1, $lte: ?2 } } }")
    List<SuiteNight> findBySuiteAndDatesBetween(String suiteId, LocalDate startDate, LocalDate endDate);
    List<SuiteNight> findByBooking(String bookingId);
    void deleteByBooking(String bookingId);
    List<SuiteNight> findByStatusAndExpiresAtBefore(String status, java.time.Instant expiresAt);
    List<SuiteNight> findBySuite(String suiteId);
    List<SuiteNight> findBySuiteIn(List<String> suiteIds);
}
