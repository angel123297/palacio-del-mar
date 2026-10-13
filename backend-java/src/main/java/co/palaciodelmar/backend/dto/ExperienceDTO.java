package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceDTO {
    private String id;
    private String name;
    private String description;
    private String shortDescription;
    private BigDecimal price;
    private String branch;
    private String category;
    private String image;
    private String mainImage;
    private Boolean available;
}
