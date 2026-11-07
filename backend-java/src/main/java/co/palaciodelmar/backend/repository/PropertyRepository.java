package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Property;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends MongoRepository<Property, String> {

    List<Property> findByOwnerId(String ownerId);

    Optional<Property> findByIdAndOwnerId(String id, String ownerId);

    boolean existsByIdAndOwnerId(String id, String ownerId);

    List<Property> findByStatus(String status);

    List<Property> findByCityIgnoreCaseAndStatus(String city, String status);

    Optional<Property> findBySlug(String slug);
}
