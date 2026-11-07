package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.ProcessedWebhookEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedWebhookEventRepository extends MongoRepository<ProcessedWebhookEvent, String> {
    boolean existsByEventId(String eventId);
    Optional<ProcessedWebhookEvent> findByEventId(String eventId);
}
