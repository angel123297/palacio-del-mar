package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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
            @RequestParam(required = false) String suiteType
    ) {
        AvailabilityService.MonthlyAvailabilityResponseData data = availabilityService.getMonthlyAvailability(
                year, month, guests, branch, suiteType
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/promotions")
    public ResponseEntity<ApiResponse<List<Promotion>>> getMonthlyPromotions(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String branch
    ) {
        List<Promotion> promotions = availabilityService.getMonthlyPromotions(year, month, branch);
        return ResponseEntity.ok(ApiResponse.ok(promotions));
    }
}
