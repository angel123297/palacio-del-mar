package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.SuiteNight;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SuiteNightRepository extends MongoRepository<SuiteNight, String> {
    List<SuiteNight> findBySuiteAndDateBetween(String suiteId, LocalDate startDate, LocalDate endDate);
    List<SuiteNight> findByBooking(String bookingId);
    void deleteByBooking(String bookingId);
}
