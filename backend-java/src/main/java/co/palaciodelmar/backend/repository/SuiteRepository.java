package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Suite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuiteRepository extends MongoRepository<Suite, String> {
    Optional<Suite> findBySlug(String slug);
    List<Suite> findByBranchAndActiveTrue(String branchId);
    List<Suite> findByPropertyId(String propertyId);
    List<Suite> findByPropertyIdAndActiveTrue(String propertyId);
    Optional<Suite> findByPropertyIdAndExternalId(String propertyId, String externalId);
    List<Suite> findByActiveTrue();
    long countByBranchIn(List<String> branches);
    long countByPropertyIdIn(List<String> propertyIds);
}
