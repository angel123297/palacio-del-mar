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
        private long pendingCheckins;
        private long activeBookings;
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<Page<BookingDTO>>> getHostBookings(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        
        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Page.empty()));
        }

        int pageIndex = Math.max(0, page - 1);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        Pageable pageable = PageRequest.of(pageIndex, safeLimit);
        
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all") && (search == null || search.isBlank())) {
            Page<BookingDTO> statusPage = bookingRepository.findByBranchInAndStatusIgnoreCase(branches, status, pageable)
                    .map(bookingService::toDTO);
            return ResponseEntity.ok(ApiResponse.ok(statusPage));
        }

        List<Booking> allHostBookings = bookingRepository.findByBranchIn(branches);
        if ((status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) || (search != null && !search.isBlank())) {
            List<BookingDTO> filtered = allHostBookings.stream()
                    .filter(b -> {
                        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) {
                            if (b.getStatus() == null || !b.getStatus().equalsIgnoreCase(status)) return false;
                        }
                        if (search != null && !search.isBlank()) {
                            String q = search.toLowerCase();
                            boolean codeMatch = b.getBookingCode() != null && b.getBookingCode().toLowerCase().contains(q);
                            boolean guestMatch = b.getGuestDetails() != null && (
                                    (b.getGuestDetails().getFullName() != null && b.getGuestDetails().getFullName().toLowerCase().contains(q)) ||
                                    (b.getGuestDetails().getEmail() != null && b.getGuestDetails().getEmail().toLowerCase().contains(q))
                            );
                            if (!codeMatch && !guestMatch) return false;
                        }
                        return true;
                    })
                    .map(bookingService::toDTO)
                    .toList();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<BookingDTO> subList = start <= end && start < filtered.size() ? filtered.subList(start, end) : java.util.Collections.emptyList();
            return ResponseEntity.ok(ApiResponse.ok(new org.springframework.data.domain.PageImpl<>(subList, pageable, filtered.size())));
        }

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
                            .pendingCheckins(0)
                            .activeBookings(0)
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

        long pendingCheckins = hostBookings.stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getStatus()) && (b.getPreCheckInCompleted() == null || !b.getPreCheckInCompleted()))
                .count();

        long activeBookings = hostBookings.stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getStatus()) || "pending".equalsIgnoreCase(b.getStatus()))
                .count();

        HostDashboardStats stats = HostDashboardStats.builder()
                .totalSuites(totalSuites)
                .totalBookings(totalBookings)
                .totalRevenue(totalRevenue)
                .pendingCheckins(pendingCheckins)
                .activeBookings(activeBookings)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
