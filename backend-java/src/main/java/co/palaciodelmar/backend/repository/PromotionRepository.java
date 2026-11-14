package co.palaciodelmar.backend.repository;

import co.palaciodelmar.backend.model.Promotion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public interface PromotionRepository extends MongoRepository<Promotion, String> {
    List<Promotion> findByActiveTrue();
    List<Promotion> findByBranchAndActiveTrue(String branchId);
    List<Promotion> findByBranchInAndActiveTrue(Collection<String> branches);
    List<Promotion> findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate endDate, LocalDate startDate);

    default List<Promotion> findActiveForSuite(String branch, String propertyId) {
        Set<String> targets = new LinkedHashSet<>();
        if (branch != null && !branch.isBlank()) {
            targets.add(branch);
        }
        if (propertyId != null && !propertyId.isBlank()) {
            targets.add(propertyId);
        }
        targets.add("all");

        Map<String, Promotion> unique = new LinkedHashMap<>();
        List<Promotion> batch = findByBranchInAndActiveTrue(targets);
        if (batch != null) {
            for (Promotion p : batch) {
                if (p != null) {
                    unique.put(p.getId() != null ? p.getId() : String.valueOf(System.identityHashCode(p)), p);
                }
            }
        }
        for (String target : targets) {
            List<Promotion> perTarget = findByBranchAndActiveTrue(target);
            if (perTarget != null) {
                for (Promotion p : perTarget) {
                    if (p != null) {
                        unique.put(p.getId() != null ? p.getId() : String.valueOf(System.identityHashCode(p)), p);
                    }
                }
            }
        }
        return new ArrayList<>(unique.values());
    }
}
