package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.BookingMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingMessageRepository extends MongoRepository<BookingMessage, String> {
    List<BookingMessage> findByBookingIdOrderByCreatedAtAsc(String bookingId);
}
