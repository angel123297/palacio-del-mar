package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/bookings")
    public ResponseEntity<ApiResponse<List<Booking>>> getAllBookings() {
        List<Booking> bookings = adminService.getAllBookings();
        return ResponseEntity.ok(ApiResponse.ok(bookings));
    }

    @PutMapping("/bookings/{id}/status")
    public ResponseEntity<ApiResponse<Booking>> updateBookingStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.get("status");
        Booking booking = adminService.updateBookingStatus(id, status);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado exitosamente", booking));
    }

    @GetMapping("/promotions")
    public ResponseEntity<ApiResponse<List<Promotion>>> getAllPromotions() {
        List<Promotion> promotions = adminService.getAllPromotions();
        return ResponseEntity.ok(ApiResponse.ok(promotions));
    }

    @PostMapping("/promotions")
    public ResponseEntity<ApiResponse<Promotion>> createPromotion(@RequestBody Promotion promotion) {
        Promotion created = adminService.createPromotion(promotion);
        return ResponseEntity.ok(ApiResponse.ok("Promoción creada exitosamente", created));
    }

    @DeleteMapping("/promotions/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePromotion(@PathVariable String id) {
        adminService.deletePromotion(id);
        return ResponseEntity.ok(ApiResponse.ok("Promoción eliminada exitosamente", null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminService.AdminDashboardStats>> getStats() {
        AdminService.AdminDashboardStats stats = adminService.getDashboardStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
