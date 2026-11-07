package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.PaymentIntent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentIntentRepository extends MongoRepository<PaymentIntent, String> {
    Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentIntent> findByTransactionId(String transactionId);
    Optional<PaymentIntent> findByBookingId(String bookingId);
    List<PaymentIntent> findAllByBookingId(String bookingId);
}
