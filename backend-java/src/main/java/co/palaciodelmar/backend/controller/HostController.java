package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/host")
@RequiredArgsConstructor
public class HostController {

    private final BookingRepository bookingRepository;
    private final SuiteRepository suiteRepository;
    private final BookingService bookingService;

    @Data
    @Builder
    public static class HostDashboardStats {
        private long totalSuites;
        private long totalBookings;
        private BigDecimal totalRevenue;
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<Page<BookingDTO>>> getHostBookings(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        
        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Page.empty()));
        }

        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, limit);
        Page<BookingDTO> bookings = bookingRepository.findByBranchIn(branches, pageable)
                .map(bookingService::toDTO);
        
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @GetMapping({"/metrics", "/stats"})
    public ResponseEntity<ApiResponse<HostDashboardStats>> getHostMetrics(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(
                    HostDashboardStats.builder()
                            .totalSuites(0)
                            .totalBookings(0)
                            .totalRevenue(BigDecimal.ZERO)
                            .build()
            ));
        }

        long totalSuites = suiteRepository.countByBranchIn(branches);
        
        List<Booking> hostBookings = bookingRepository.findByBranchIn(branches);
        long totalBookings = hostBookings.size();
        
        BigDecimal totalRevenue = hostBookings.stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getStatus()) || "completed".equalsIgnoreCase(b.getStatus()))
                .map(Booking::getTotalPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        HostDashboardStats stats = HostDashboardStats.builder()
                .totalSuites(totalSuites)
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
