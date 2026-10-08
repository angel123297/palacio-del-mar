package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.ExperienceCategoryDTO;
import co.palaciodelmar.backend.model.Experience;
import co.palaciodelmar.backend.service.ExperienceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/experiences")
@RequiredArgsConstructor
public class ExperienceController {

    private final ExperienceService experienceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Experience>>> getAllExperiences() {
        List<Experience> experiences = experienceService.getAllActiveExperiences();
        return ResponseEntity.ok(ApiResponse.ok(experiences));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<ExperienceCategoryDTO>>> getCategories() {
        List<ExperienceCategoryDTO> categories = experienceService.getCategories();
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<Experience>>> getFeaturedExperiences() {
        List<Experience> experiences = experienceService.getFeaturedExperiences();
        return ResponseEntity.ok(ApiResponse.ok(experiences));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<Experience>>> getExperiencesByBranch(@PathVariable String branchId) {
        List<Experience> experiences = experienceService.getExperiencesByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.ok(experiences));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Experience>> getExperienceById(@PathVariable String id) {
        Experience experience = experienceService.getExperienceById(id);
        return ResponseEntity.ok(ApiResponse.ok(experience));
    }
}
