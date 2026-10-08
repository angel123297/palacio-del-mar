package co.palaciodelmar.backend.dto;

import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Suite;
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
public class SuiteResponseDTO {

    private String id;
    private String _id;
    private Branch branch;
    private String name;
    private String slug;
    private String type;
    private String description;
    private String shortDescription;
    private BigDecimal basePrice;
    private BigDecimal originalPrice;
    private BigDecimal seasonalPrice;
    private String mainImage;
    private Integer maxGuests;
    private Integer size;
    private Integer bathrooms;
    private String view;
    private Boolean hasBalcony;
    private Boolean hasTerrace;
    private Boolean hasJacuzzi;
    private List<Suite.BedConfig> beds;
    private List<String> amenities;
    private List<String> images;
    private Integer totalUnits;
    private Boolean active;
    private Boolean available;
    private Boolean featured;
    private Integer order;
    private Instant createdAt;
    private Instant updatedAt;

    public static SuiteResponseDTO from(Suite suite, Branch branch) {
        if (suite == null) return null;
        return SuiteResponseDTO.builder()
                .id(suite.getId())
                ._id(suite.getId())
                .branch(branch)
                .name(suite.getName())
                .slug(suite.getSlug())
                .type(suite.getType())
                .description(suite.getDescription())
                .shortDescription(suite.getShortDescription())
                .basePrice(suite.getBasePrice())
                .originalPrice(suite.getOriginalPrice())
                .seasonalPrice(suite.getBasePrice())
                .mainImage(suite.getMainImage())
                .maxGuests(suite.getMaxGuests() != null ? suite.getMaxGuests() : 2)
                .size(suite.getSize() != null ? suite.getSize() : 30)
                .bathrooms(1)
                .view(suite.getView() != null ? suite.getView() : suite.getViewType())
                .hasBalcony(suite.getHasBalcony() != null ? suite.getHasBalcony() : (suite.getAmenities() != null && suite.getAmenities().stream().anyMatch(a -> a.toLowerCase().contains("balcón"))))
                .hasTerrace(suite.getHasTerrace() != null ? suite.getHasTerrace() : (suite.getAmenities() != null && suite.getAmenities().stream().anyMatch(a -> a.toLowerCase().contains("terraza"))))
                .hasJacuzzi(suite.getHasJacuzzi() != null ? suite.getHasJacuzzi() : (suite.getAmenities() != null && suite.getAmenities().stream().anyMatch(a -> a.toLowerCase().contains("jacuzzi"))))
                .beds(suite.getBeds())
                .amenities(suite.getAmenities())
                .images(suite.getImages())
                .totalUnits(suite.getTotalUnits())
                .active(suite.getActive())
                .available(suite.getAvailable())
                .featured(suite.getFeatured())
                .order(suite.getOrder())
                .createdAt(suite.getCreatedAt())
                .updatedAt(suite.getUpdatedAt())
                .build();
    }
}
