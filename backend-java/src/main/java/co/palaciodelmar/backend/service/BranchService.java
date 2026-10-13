package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.BranchResponseDTO;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final SuiteRepository suiteRepository;

    public List<BranchResponseDTO> getAllActiveBranches() {
        List<Branch> branches = branchRepository.findByActiveTrueOrderByOrderAsc();
        List<Suite> activeSuites = suiteRepository.findByActiveTrue();

        return branches.stream().map(b -> {
            List<Suite> branchSuites = activeSuites.stream()
                    .filter(s -> b.getId().equals(s.getBranch()))
                    .toList();
            BigDecimal minPrice = branchSuites.stream()
                    .map(Suite::getBasePrice)
                    .filter(p -> p != null)
                    .min(BigDecimal::compareTo)
                    .orElse(null);
            int count = branchSuites.size();
            return BranchResponseDTO.from(b, minPrice, count);
        }).toList();
    }

    public BranchResponseDTO getBranchBySlug(String slug) {
        Branch branch = branchRepository.findBySlug(slug.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada: " + slug));

        List<Suite> branchSuites = suiteRepository.findByBranchAndActiveTrue(branch.getId());
        BigDecimal minPrice = branchSuites.stream()
                .map(Suite::getBasePrice)
                .filter(p -> p != null)
                .min(BigDecimal::compareTo)
                .orElse(null);
        int count = branchSuites.size();

        return BranchResponseDTO.from(branch, minPrice, count);
    }

    public Branch getBranchById(String id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada con id: " + id));
    }
}
