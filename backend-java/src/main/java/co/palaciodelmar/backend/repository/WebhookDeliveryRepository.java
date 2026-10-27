package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.WebhookDelivery;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface WebhookDeliveryRepository extends MongoRepository<WebhookDelivery, String> {
    List<WebhookDelivery> findByPropertyIdOrderByCreatedAtDesc(String propertyId);
    List<WebhookDelivery> findBySubscriptionIdOrderByCreatedAtDesc(String subscriptionId);
    List<WebhookDelivery> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    List<WebhookDelivery> findByStatusAndNextRetryAtBefore(String status, Instant beforeTime);
    List<WebhookDelivery> findByStatusAndNextRetryAtIsNullAndCreatedAtBefore(String status, Instant beforeTime);
}
