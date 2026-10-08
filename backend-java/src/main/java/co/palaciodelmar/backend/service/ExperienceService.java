package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ExperienceCategoryDTO;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.repository.ExperienceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final ExperienceRepository experienceRepository;

    public List<Experience> getAllActiveExperiences() {
        return experienceRepository.findByActiveTrue();
    }

    public List<Experience> getExperiencesByBranch(String branchId) {
        return experienceRepository.findByBranchAndActiveTrue(branchId);
    }

    public List<ExperienceCategoryDTO> getCategories() {
        List<Experience> activeExperiences = experienceRepository.findByActiveTrue();
        Map<String, List<Experience>> grouped = activeExperiences.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(Experience::getCategory));

        return grouped.entrySet().stream()
                .map(e -> {
                    String category = e.getKey();
                    List<Experience> list = e.getValue();
                    BigDecimal minPrice = list.stream()
                            .map(Experience::getPrice)
                            .filter(Objects::nonNull)
                            .min(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    return ExperienceCategoryDTO.builder()
                            .category(category)
                            .count(list.size())
                            .minPrice(minPrice)
                            .build();
                })
                .sorted(Comparator.comparing(ExperienceCategoryDTO::getCategory))
                .toList();
    }

    public List<Experience> getFeaturedExperiences() {
        return experienceRepository.findByActiveTrue();
    }

    public Experience getExperienceById(String id) {
        return experienceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Experiencia no encontrada con id: " + id));
    }
}
