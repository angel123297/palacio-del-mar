package co.palaciodelmar.backend.dto;

import co.palaciodelmar.backend.model.Branch;
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
public class BranchResponseDTO {

    private String id;
    private String _id;
    private String name;
    private String slug;
    private String zone;
    private String tagline;
    private String description;
    private String address;
    private Branch.GeoLocation location;
    private String mainImage;
    private List<String> vibe;
    private List<Branch.PointOfInterest> highlights;
    private String phone;
    private String checkInTime;
    private String checkOutTime;
    private Boolean active;
    private Integer order;
    private BigDecimal fromPrice;
    private Integer roomTypes;
    private Instant createdAt;
    private Instant updatedAt;

    public static BranchResponseDTO from(Branch branch, BigDecimal fromPrice, Integer roomTypes) {
        if (branch == null) return null;
        return BranchResponseDTO.builder()
                .id(branch.getId())
                ._id(branch.getId())
                .name(branch.getName())
                .slug(branch.getSlug())
                .zone(branch.getZone())
                .tagline(branch.getTagline())
                .description(branch.getDescription())
                .address(branch.getAddress())
                .location(branch.getLocation())
                .mainImage(branch.getMainImage())
                .vibe(branch.getVibe())
                .highlights(branch.getHighlights())
                .phone(branch.getPhone())
                .checkInTime(branch.getCheckInTime())
                .checkOutTime(branch.getCheckOutTime())
                .active(branch.getActive())
                .order(branch.getOrder())
                .fromPrice(fromPrice)
                .roomTypes(roomTypes)
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }
}
