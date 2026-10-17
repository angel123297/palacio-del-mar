package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.ApiKeyDTO;
import co.palaciodelmar.backend.dto.CreateApiKeyRequest;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HOST', 'ADMIN')")
public class HostApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyDTO>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String propertyId
    ) {
        User currentUser = principal != null ? principal.getUser() : null;
        List<ApiKeyDTO> keys = apiKeyService.listApiKeys(currentUser, propertyId);
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApiKeyDTO>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        User currentUser = principal != null ? principal.getUser() : null;
        ApiKeyDTO created = apiKeyService.createApiKey(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String id
    ) {
        User currentUser = principal != null ? principal.getUser() : null;
        apiKeyService.revokeApiKey(currentUser, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
