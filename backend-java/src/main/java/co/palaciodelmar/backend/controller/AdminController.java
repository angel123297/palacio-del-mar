package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.dto.PromotionDTO;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.service.AdminService;
import co.palaciodelmar.backend.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import co.palaciodelmar.backend.repository.BookingRepository;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final co.palaciodelmar.backend.service.PropertyService propertyService;

    @GetMapping("/properties")
    public ResponseEntity<ApiResponse<List<co.palaciodelmar.backend.dto.PropertyDTO>>> getAdminProperties(
            @RequestParam(required = false) String status) {
        List<co.palaciodelmar.backend.dto.PropertyDTO> list = propertyService.getAllPropertiesForAdmin(status);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PutMapping("/properties/{id}/status")
    public ResponseEntity<ApiResponse<co.palaciodelmar.backend.dto.PropertyDTO>> updatePropertyStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String status = body.getOrDefault("status", "APPROVED");
        co.palaciodelmar.backend.dto.PropertyDTO updated = propertyService.updatePropertyStatus(id, status);
        return ResponseEntity.ok(ApiResponse.ok("Estado de propiedad actualizado", updated));
    }

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<BookingDTO>>> getAllBookings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        int pageIndex = Math.max(0, page - 1);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(pageIndex, safeLimit);
        org.springframework.data.domain.Page<BookingDTO> bookings = adminService.getAllBookings(status, search, pageable)
                .map(bookingService::toDTO);
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @PutMapping("/bookings/{id}/status")
    public ResponseEntity<ApiResponse<BookingDTO>> updateBookingStatus(
            @PathVariable String id,
            @jakarta.validation.Valid @RequestBody co.palaciodelmar.backend.dto.UpdateStatusRequest body
    ) {
        Booking booking = adminService.updateBookingStatus(id, body.getStatus());
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado exitosamente", bookingService.toDTO(booking)));
    }

    @GetMapping("/promotions")
    public ResponseEntity<ApiResponse<List<PromotionDTO>>> getAllPromotions() {
        List<PromotionDTO> promotions = adminService.getAllPromotions().stream()
                .map(this::toPromotionDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(promotions));
    }

    @PostMapping("/promotions")
    public ResponseEntity<ApiResponse<PromotionDTO>> createPromotion(
            @jakarta.validation.Valid @RequestBody co.palaciodelmar.backend.dto.PromotionRequest request) {
        Promotion promotion = Promotion.builder()
                .title(request.getTitle())
                .branch(request.resolveBranch())
                .discountPercent(request.getDiscountPercent())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(true)
                .isSample(false)
                .build();
        Promotion created = adminService.createPromotion(promotion);
        return ResponseEntity.ok(ApiResponse.ok("Promoción creada exitosamente", toPromotionDTO(created)));
    }

    @DeleteMapping("/promotions/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePromotion(@PathVariable String id) {
        adminService.deletePromotion(id);
        return ResponseEntity.ok(ApiResponse.ok("Promoción eliminada exitosamente", null));
    }

    @GetMapping({"/stats", "/metrics"})
    public ResponseEntity<ApiResponse<AdminService.AdminDashboardStats>> getStats() {
        AdminService.AdminDashboardStats stats = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    @GetMapping("/payments/refunds")
    public ResponseEntity<ApiResponse<List<co.palaciodelmar.backend.dto.PaymentDTO>>> getPendingRefunds() {
        List<co.palaciodelmar.backend.dto.PaymentDTO> list = adminService.getPendingRefunds().stream()
                .map(this::toPaymentDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PutMapping("/payments/{id}/resolve-refund")
    public ResponseEntity<ApiResponse<co.palaciodelmar.backend.dto.PaymentDTO>> resolveRefund(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "mark_refunded");
        String notes = body.get("notes");
        co.palaciodelmar.backend.model.Payment p = adminService.resolveRefund(id, action, notes);
        return ResponseEntity.ok(ApiResponse.ok("Reembolso resuelto exitosamente", toPaymentDTO(p)));
    }

    private co.palaciodelmar.backend.dto.PaymentDTO toPaymentDTO(co.palaciodelmar.backend.model.Payment p) {
        if (p == null) return null;
        return co.palaciodelmar.backend.dto.PaymentDTO.builder()
                .id(p.getId())
                .bookingId(p.getBooking())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .provider(p.getProvider())
                .transactionId(p.getTransactionId())
                .paymentMethod(p.getPaymentMethod())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private PromotionDTO toPromotionDTO(Promotion promotion) {
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
