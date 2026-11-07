package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.WebhookSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookSubscriptionRepository extends MongoRepository<WebhookSubscription, String> {
    List<WebhookSubscription> findByPropertyId(String propertyId);
    List<WebhookSubscription> findByOwnerId(String ownerId);
    List<WebhookSubscription> findByPropertyIdAndActiveTrue(String propertyId);
}
