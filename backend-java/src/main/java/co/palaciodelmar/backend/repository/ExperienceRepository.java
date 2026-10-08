package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Experience;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperienceRepository extends MongoRepository<Experience, String> {
    List<Experience> findByActiveTrue();
    List<Experience> findByBranchAndActiveTrue(String branchId);
}
