package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Email
    private String email;
    @jakarta.validation.constraints.NotBlank
    private String password;
}
