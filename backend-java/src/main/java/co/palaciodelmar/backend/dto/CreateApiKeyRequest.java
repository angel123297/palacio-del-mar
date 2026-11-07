package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRequest {
    @NotBlank(message = "El ID de la propiedad es obligatorio")
    private String propertyId;

    @NotBlank(message = "El nombre identificador de la API key es obligatorio")
    private String name;
}
