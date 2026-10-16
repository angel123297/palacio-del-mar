package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDTO {
    private String id;
    private String _id;
    private String ownerId;
    private String name;
    private String slug;
    private String type;
    private String city;
    private String address;
    private String description;
    private String shortDescription;
    private String mainImage;
    private List<String> images;
    private BigDecimal commissionRate;
    private String status;
    private String externalSiteUrl;
    private String phone;
    private Boolean active;
    private Long suitesCount;
    private Instant createdAt;
    private Instant updatedAt;
}
