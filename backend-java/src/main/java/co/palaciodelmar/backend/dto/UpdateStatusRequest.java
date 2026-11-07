package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "El status no puede ser nulo")
    @NotBlank(message = "El status no puede estar en blanco")
    private String status;
}
