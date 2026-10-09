package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.ExperienceCategoryDTO;
import co.palaciodelmar.backend.dto.ExperienceDTO;
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
    public ResponseEntity<ApiResponse<List<ExperienceDTO>>> getAllExperiences() {
        List<ExperienceDTO> experiences = experienceService.getAllActiveExperiences();
        return ResponseEntity.ok(ApiResponse.ok(experiences));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<ExperienceCategoryDTO>>> getCategories() {
        List<ExperienceCategoryDTO> categories = experienceService.getCategories();
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }

    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<List<ExperienceDTO>>> getFeaturedExperiences() {
        List<ExperienceDTO> experiences = experienceService.getFeaturedExperiences();
        return ResponseEntity.ok(ApiResponse.ok(experiences));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<ExperienceDTO>>> getExperiencesByBranch(@PathVariable String branchId) {
        List<ExperienceDTO> experiences = experienceService.getExperiencesByBranch(branchId);
        return ResponseEntity.ok(ApiResponse.ok(experiences));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExperienceDTO>> getExperienceById(@PathVariable String id) {
        ExperienceDTO experience = experienceService.getExperienceById(id);
        return ResponseEntity.ok(ApiResponse.ok(experience));
    }
}
