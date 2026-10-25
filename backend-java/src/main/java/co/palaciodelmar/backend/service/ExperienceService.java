package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.ExperienceCategoryDTO;
import co.palaciodelmar.backend.dto.ExperienceDTO;
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

    public List<ExperienceDTO> getAllActiveExperiences() {
        return getAllActiveExperiences(null, null, null);
    }

    public List<ExperienceDTO> getAllActiveExperiences(BigDecimal minPrice, BigDecimal maxPrice, String category) {
        List<Experience> list = experienceRepository.findByActiveTrue();
        if (category != null && !category.isBlank()) {
            String cat = category.trim().toLowerCase();
            list = list.stream().filter(e -> e.getCategory() != null && e.getCategory().toLowerCase().contains(cat)).toList();
        }
        if (minPrice != null) {
            list = list.stream().filter(e -> e.getPrice() != null && e.getPrice().compareTo(minPrice) >= 0).toList();
        }
        if (maxPrice != null) {
            list = list.stream().filter(e -> e.getPrice() != null && e.getPrice().compareTo(maxPrice) <= 0).toList();
        }
        return list.stream().map(this::toDTO).toList();
    }

    public List<ExperienceDTO> getExperiencesByBranch(String branchId) {
        return experienceRepository.findByBranchAndActiveTrue(branchId).stream().map(this::toDTO).toList();
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

    public List<ExperienceDTO> getFeaturedExperiences() {
        return experienceRepository.findByActiveTrue().stream().map(this::toDTO).toList();
    }

    public ExperienceDTO getExperienceById(String id) {
        return toDTO(experienceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Experiencia no encontrada con id: " + id)));
    }

    public ExperienceDTO toDTO(Experience experience) {
        if (experience == null) return null;
        return co.palaciodelmar.backend.dto.ExperienceDTO.builder()
                .id(experience.getId())
                .name(experience.getName())
                .description(experience.getDescription())
                .shortDescription(experience.getShortDescription())
                .price(experience.getPrice())
                .branch(experience.getBranch())
                .category(experience.getCategory())
                .image(experience.getImage())
                .mainImage(experience.getMainImage())
                .available(experience.getAvailable())
                .build();
    }
}
