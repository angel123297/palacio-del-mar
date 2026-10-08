package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bookings")
public class Booking {

    @Id
    private String id;

    @Indexed
    private String user; // User ObjectId reference

    @Indexed
    private String suite; // Suite ObjectId reference

    @Indexed
    private String branch; // Branch ObjectId reference

    @Builder.Default
    private Integer unitSlot = 1;

    private LocalDate checkIn;
    private LocalDate checkOut;

    @Builder.Default
    private Integer nights = 1;

    @Builder.Default
    private Integer guests = 1;

    @Builder.Default
    private Integer children = 0;

    private BigDecimal totalPrice;
    private BigDecimal pricePerNight;

    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Indexed
    @Builder.Default
    private String status = "pending"; // "pending", "confirmed", "checked_in", "checked_out", "cancelled", "completed", "expired"

    @Indexed
    @Builder.Default
    private String paymentStatus = "pending"; // "pending", "paid", "failed", "refunded", "partial"

    @Indexed(unique = true, sparse = true)
    private String bookingCode;

    private GuestDetails guestDetails;

    @Builder.Default
    private List<SelectedExperience> experiences = new ArrayList<>();

    private String cancellationReason;

    private Instant expiresAt;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuestDetails {
        private String fullName;
        private String email;
        private String phone;
        private String specialRequests;
        private String arrivalTime;
        private String bedPreference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectedExperience {
        private String experienceId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
    }
}
