package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {
    List<Payment> findByBooking(String bookingId);
    List<Payment> findByUser(String userId);
}
