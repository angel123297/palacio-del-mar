package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BranchResponseDTO;
import co.palaciodelmar.backend.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponseDTO>>> getAllBranches() {
        List<BranchResponseDTO> branches = branchService.getAllActiveBranches();
        return ResponseEntity.ok(ApiResponse.ok(branches));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<BranchResponseDTO>> getBranchBySlug(@PathVariable String slug) {
        BranchResponseDTO branch = branchService.getBranchBySlug(slug);
        return ResponseEntity.ok(ApiResponse.ok(branch));
    }
}
