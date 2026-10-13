package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.dto.PromotionRequest;
import co.palaciodelmar.backend.dto.PromotionDTO;
import java.util.Optional;
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
    private final PromotionRepository promotionRepository;

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
                .map(co.palaciodelmar.backend.service.AdminService::netCollected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingCheckins = hostBookings.stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getStatus()) && (b.getPreCheckInCompleted() == null || !b.getPreCheckInCompleted()))
                .count();

        long activeBookings = hostBookings.stream()
                .filter(b -> co.palaciodelmar.backend.service.AdminService.isActiveStatus(b.getStatus()))
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

    @PostMapping("/promotions")
    public ResponseEntity<ApiResponse<PromotionDTO>> createPromotion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody PromotionRequest request) {

        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty() || !branches.contains(request.getBranchId())) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta sede"));
        }

        Promotion promo = Promotion.builder()
                .title(request.getTitle())
                .branch(request.getBranchId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .discountPercent(request.getDiscountPercent())
                .active(true)
                .isSample(false)
                .build();

        Promotion saved = promotionRepository.save(promo);

        PromotionDTO dto = PromotionDTO.builder()
                .id(saved.getId())
                .branch(saved.getBranch())
                .title(saved.getTitle())
                .discountPercent(saved.getDiscountPercent())
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .active(saved.getActive())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @DeleteMapping("/promotions/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePromotion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {

        Optional<Promotion> promoOpt = promotionRepository.findById(id);
        if (promoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Promotion promo = promoOpt.get();
        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty() || !branches.contains(promo.getBranch())) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta promoción"));
        }

        promotionRepository.delete(promo);
        return ResponseEntity.ok(ApiResponse.ok("Promoción eliminada", null));
    }

    @PostMapping("/bookings/{id}/checkin")
    public ResponseEntity<ApiResponse<Void>> checkIn(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty() || !branches.contains(booking.getBranch())) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta reserva"));
        }

        bookingService.changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CHECKED_IN, userPrincipal.getUser().getId(), "Check-in by host");
        bookingRepository.save(booking);
        return ResponseEntity.ok(ApiResponse.ok("Check-in registrado", null));
    }

    @PostMapping("/bookings/{id}/checkout")
    public ResponseEntity<ApiResponse<Void>> checkOut(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty() || !branches.contains(booking.getBranch())) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta reserva"));
        }

        // Late check-out: el check-out oficial es a las 12:00 (ver FAQ y Branch.checkOutTime).
        java.time.ZoneId bogota = java.time.ZoneId.of("America/Bogota");
        java.time.LocalDate today = java.time.LocalDate.now(bogota);
        java.time.LocalTime nowTime = java.time.LocalTime.now(bogota);
        boolean lateCheckout = booking.getCheckOut() != null
                && (today.isAfter(booking.getCheckOut())
                    || (today.isEqual(booking.getCheckOut()) && nowTime.isAfter(java.time.LocalTime.NOON)));
        BigDecimal currentTotal = booking.getTotalPrice() != null ? booking.getTotalPrice() : BigDecimal.ZERO;
        BigDecimal currentBalance = booking.getBalanceDue() != null ? booking.getBalanceDue() : BigDecimal.ZERO;
        if (lateCheckout && booking.getPricePerNight() != null) {
            BigDecimal recargo = booking.getPricePerNight().multiply(new BigDecimal("0.50")).setScale(2, java.math.RoundingMode.HALF_UP);
            booking.setTotalPrice(currentTotal.add(recargo));
            booking.setBalanceDue(currentBalance.add(recargo));
            booking.setPaymentStatus("partial");
            booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Recargo Late Check-out aplicado: $" + recargo);
        }
        // Depósito de garantía: se devuelve al hacer check-out (se refleja en lo devuelto, no como ingreso).
        BigDecimal deposit = booking.getGuaranteeDeposit() != null ? booking.getGuaranteeDeposit() : BigDecimal.ZERO;
        if (deposit.signum() > 0 && !Boolean.TRUE.equals(booking.getGuaranteeRefunded())) {
            BigDecimal paid = booking.getPaidAmount() != null ? booking.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal refunded = booking.getRefundedAmount() != null ? booking.getRefundedAmount() : BigDecimal.ZERO;
            BigDecimal refundable = paid.subtract(refunded).min(deposit).max(BigDecimal.ZERO);
            booking.setRefundedAmount(refunded.add(refundable));
            booking.setGuaranteeRefunded(true);
            booking.setNotes((booking.getNotes() != null ? booking.getNotes() + " | " : "") + "Depósito de garantía devuelto: $" + refundable);
        }
        bookingService.changeBookingStatus(booking, co.palaciodelmar.backend.model.BookingStatus.CHECKED_OUT, userPrincipal.getUser().getId(), "Check-out by host");
        bookingRepository.save(booking);
        return ResponseEntity.ok(ApiResponse.ok("Check-out registrado", null));
    }

    @PostMapping("/bookings/{id}/regenerate-pin")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> regeneratePin(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable String id) {
        Optional<Booking> bookingOpt = bookingRepository.findById(id);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = bookingOpt.get();
        List<String> branches = userPrincipal.getUser().getBranches();
        if (branches == null || branches.isEmpty() || !branches.contains(booking.getBranch())) {
            return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta reserva"));
        }

        String newPin = String.format("%06d", new java.util.Random().nextInt(999999));
        booking.setSmartLockPin(newPin);
        bookingRepository.save(booking);

        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("smartLockPin", newPin)));
    }
}
