package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Branch;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends MongoRepository<Branch, String> {
    Optional<Branch> findBySlug(String slug);
    List<Branch> findByActiveTrueOrderByOrderAsc();
}
