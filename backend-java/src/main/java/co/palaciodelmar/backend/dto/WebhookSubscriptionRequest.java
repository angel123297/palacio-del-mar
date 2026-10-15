package co.palaciodelmar.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookSubscriptionRequest {

    @NotBlank(message = "propertyId es obligatorio")
    private String propertyId;

    @NotBlank(message = "url es obligatoria")
    private String url;

    private List<String> events;

    private String description;

    @Builder.Default
    private Boolean active = true;
}
