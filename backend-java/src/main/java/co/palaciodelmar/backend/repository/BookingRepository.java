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
    List<Booking> findByStatusIn(List<String> statuses);
    List<Booking> findByStatusAndExpiresAtBefore(String status, java.time.Instant now);
    List<Booking> findByStatusAndExpiresAtBeforeAndPaidAmount(String status, java.time.Instant now, java.math.BigDecimal paidAmount);
    org.springframework.data.domain.Page<Booking> findByBranchIn(List<String> branches, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Booking> findByStatusIgnoreCase(String status, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Booking> findByBranchInAndStatusIgnoreCase(List<String> branches, String status, org.springframework.data.domain.Pageable pageable);
    List<Booking> findByBranchIn(List<String> branches);
    List<Booking> findByPropertyIdIn(List<String> propertyIds);
    List<Booking> findByPropertyId(String propertyId);
    List<Booking> findByPropertyIdAndStatusIn(String propertyId, List<String> statuses);
    List<Booking> findBySuite(String suiteId);
    org.springframework.data.domain.Page<Booking> findByPropertyIdIn(List<String> propertyIds, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<Booking> findByPropertyIdInAndStatusIgnoreCase(List<String> propertyIds, String status, org.springframework.data.domain.Pageable pageable);
}
