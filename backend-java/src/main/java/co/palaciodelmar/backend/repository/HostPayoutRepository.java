package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.HostPayout;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HostPayoutRepository extends MongoRepository<HostPayout, String> {
    List<HostPayout> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    List<HostPayout> findByPropertyIdOrderByCreatedAtDesc(String propertyId);
    List<HostPayout> findAllByOrderByCreatedAtDesc();
    Optional<HostPayout> findFirstByBookingIdsContaining(String bookingId);
}
