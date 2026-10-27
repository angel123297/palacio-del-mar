package co.palaciodelmar.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoPlaceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String type;     // BRANCH, PROPERTY, POI, EXPERIENCE_HUB
    private String category; // beach, monument, plaza, dining, nature, shopping, nightlife, yacht, hotel
    private String zone;
    private String address;
    private String slug;
    private double lat;
    private double lng;
    private Double distanceKm;
    private Integer walkMinutes;
    private Integer driveMinutes;
}
