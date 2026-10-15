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
public class PropertyRequest {

    @NotBlank(message = "El nombre de la propiedad es obligatorio")
    private String name;

    private String type; // "HOTEL", "ROOM", "APARTMENT"

    @NotBlank(message = "La ciudad es obligatoria")
    private String city;

    @NotBlank(message = "La dirección es obligatoria")
    private String address;

    private String description;
    private String shortDescription;
    private String mainImage;
    private List<String> images;
    private String phone;
    private String externalSiteUrl;
}
