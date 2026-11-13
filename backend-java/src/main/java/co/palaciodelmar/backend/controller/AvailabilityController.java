package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.PromotionDTO;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<ApiResponse<AvailabilityService.AvailabilityResponseData>> checkAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String suiteType
    ) {
        AvailabilityService.AvailabilityResponseData data = availabilityService.checkAvailability(
                checkIn, checkOut, guests, branch, suiteType
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<AvailabilityService.MonthlyAvailabilityResponseData>> getMonthlyAvailability(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Integer guests,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String suiteType,
            @RequestParam(required = false) String suiteId
    ) {
        AvailabilityService.MonthlyAvailabilityResponseData data = availabilityService.getMonthlyAvailability(
                year, month, guests, branch, suiteType, suiteId
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/promotions")
    public ResponseEntity<ApiResponse<List<PromotionDTO>>> getMonthlyPromotions(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String branch
    ) {
        List<PromotionDTO> promotions = availabilityService.getMonthlyPromotions(year, month, branch).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(promotions));
    }

    @GetMapping("/peak-dates")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getPeakDates(
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        java.util.Map<String, Object> data = availabilityService.getPeakDates(targetYear);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private PromotionDTO toDTO(Promotion promotion) {
        if (promotion == null) return null;
        return PromotionDTO.builder()
                .id(promotion.getId())
                .branch(promotion.getBranch())
                .title(promotion.getTitle())
                .discountPercent(promotion.getDiscountPercent())
                .startDate(promotion.getStartDate())
                .endDate(promotion.getEndDate())
                .active(promotion.getActive())
                .isSample(promotion.getIsSample())
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }
}
