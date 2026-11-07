package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {
    Optional<ApiKey> findByHashedKeyAndActiveTrue(String hashedKey);
    List<ApiKey> findByPropertyId(String propertyId);
    List<ApiKey> findByOwnerId(String ownerId);
    Optional<ApiKey> findByIdAndOwnerId(String id, String ownerId);
}
