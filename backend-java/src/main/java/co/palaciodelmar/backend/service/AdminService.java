package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.repository.*;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final BookingRepository bookingRepository;
    private final SuiteRepository suiteRepository;
    private final UserRepository userRepository;
    private final PromotionRepository promotionRepository;
    private final SuiteNightRepository suiteNightRepository;

    @Data
    @Builder
    public static class AdminDashboardStats {
        private long totalSuites;
        private long totalBookings;
        private long totalUsers;
        private BigDecimal totalRevenue;
    }

    public org.springframework.data.domain.Page<Booking> getAllBookings(org.springframework.data.domain.Pageable pageable) {
        return getAllBookings(null, null, pageable);
    }

    public org.springframework.data.domain.Page<Booking> getAllBookings(String status, String search, org.springframework.data.domain.Pageable pageable) {
        if ((status != null && !status.isBlank() && !status.equalsIgnoreCase("all")) || (search != null && !search.isBlank())) {
            List<Booking> all = bookingRepository.findAll();
            List<Booking> filtered = all.stream()
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
                    .toList();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<Booking> subList = start <= end && start < filtered.size() ? filtered.subList(start, end) : java.util.Collections.emptyList();
            return new org.springframework.data.domain.PageImpl<>(subList, pageable, filtered.size());
        }
        return bookingRepository.findAll(pageable);
    }

    public Booking updateBookingStatus(String bookingId, String newStatus) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada: " + bookingId));

        booking.setStatus(newStatus != null ? newStatus.toLowerCase() : "pending");
        booking.setUpdatedAt(Instant.now());

        if ("confirmed".equalsIgnoreCase(newStatus)) {
            List<co.palaciodelmar.backend.model.SuiteNight> nights = suiteNightRepository.findByBooking(bookingId);
            for (co.palaciodelmar.backend.model.SuiteNight sn : nights) {
                sn.setExpiresAt(null);
                sn.setStatus("confirmed");
                suiteNightRepository.save(sn);
            }
        } else if ("cancelled".equalsIgnoreCase(newStatus) || "completed".equalsIgnoreCase(newStatus)) {
            suiteNightRepository.deleteByBooking(bookingId);
        }

        return bookingRepository.save(booking);
    }

    public List<Promotion> getAllPromotions() {
        return promotionRepository.findAll();
    }

    public Promotion createPromotion(Promotion promotion) {
        promotion.setCreatedAt(Instant.now());
        promotion.setUpdatedAt(Instant.now());
        return promotionRepository.save(promotion);
    }

    public void deletePromotion(String promotionId) {
        promotionRepository.deleteById(promotionId);
    }

    public AdminDashboardStats getDashboardStats() {
        long totalSuites = suiteRepository.count();
        long totalBookings = bookingRepository.count();
        long totalUsers = userRepository.count();

        BigDecimal totalRevenue = bookingRepository.findAll().stream()
                .filter(b -> "confirmed".equalsIgnoreCase(b.getStatus()) || "completed".equalsIgnoreCase(b.getStatus()))
                .map(Booking::getTotalPrice)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AdminDashboardStats.builder()
                .totalSuites(totalSuites)
                .totalBookings(totalBookings)
                .totalUsers(totalUsers)
                .totalRevenue(totalRevenue)
                .build();
    }
}
