package co.palaciodelmar.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
    private String branch; // Branch ObjectId reference (mantenido por compatibilidad)

    @Indexed
    private String propertyId; // Property ObjectId reference (marketplace)

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
    @Builder.Default
    private Boolean needsCrib = false;

    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal taxes;
    private BigDecimal totalPrice;
    private BigDecimal pricePerNight; // Retained for backward compatibility

    @Builder.Default
    private BigDecimal platformFee = BigDecimal.ZERO; // Comisión del marketplace (ej. 8%)

    @Builder.Default
    private BigDecimal hostPayout = BigDecimal.ZERO; // Monto a liquidar al anfitrión (ej. 92%)

    @Builder.Default
    private List<NightlyPrice> nightlyPrices = new ArrayList<>();

    @Field("amountPaid")
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Field("amountRefunded")
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Indexed
    @Builder.Default
    private String status = "pending"; // "pending", "confirmed", "checked_in", "checked_out", "cancelled", "completed", "expired"

    @Builder.Default
    private List<StatusHistoryEntry> statusHistory = new ArrayList<>();

    @Indexed
    @Builder.Default
    private String paymentStatus = "pending"; // "pending", "paid", "failed", "refunded", "partial"

    @Builder.Default
    private BigDecimal guaranteeDeposit = BigDecimal.ZERO;

    @Builder.Default
    private Boolean guaranteeRefunded = false;

    @Indexed(unique = true, sparse = true)
    private String bookingCode;

    private GuestDetails guestDetails;

    @Builder.Default
    private List<SelectedExperience> experiences = new ArrayList<>();

    private Boolean airportPickup;

    @Builder.Default
    private Boolean petFriendly = false;

    @Builder.Default
    private List<FoodOrder> foodOrders = new ArrayList<>();

    private String cancellationReason;
    private String notes;

    private String appliedPromotionId;
    private String appliedPromotionName;
    private BigDecimal appliedPromotionDiscountPercent;

    private Instant expiresAt;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    @org.springframework.data.annotation.Version
    private Long version;

    private String smartLockPin;

    @Builder.Default
    private Boolean preCheckInCompleted = false;

    private String documentNumber;

    private String companionGuests;

    /** Ajustes de tarifa dinámica aplicados al cotizar (ej. "2026-10-17: Alta demanda (ocupación 80%) (+15%)"). */
    @Builder.Default
    private List<String> dynamicPricingNotes = new ArrayList<>();

    /** Liquidación (HostPayout) que ya incluyó esta reserva; null = pendiente de liquidar. */
    @Indexed(sparse = true)
    private String payoutId;

    /** Momento en que se envió automáticamente la invitación a calificar la estadía. */
    private Instant reviewRequestSentAt;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodOrder {
        private String dishName;
        private String foodName;
        private String type; // "typical", "normal", "custom"
        private Boolean includesBreakfastCombo;
        private BigDecimal price;
        private String specialInstructions;

        public String getDishName() {
            return dishName != null ? dishName : foodName;
        }

        public void setFoodName(String foodName) {
            this.foodName = foodName;
            if (this.dishName == null) {
                this.dishName = foodName;
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NightlyPrice {
        private LocalDate date;
        private BigDecimal price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusHistoryEntry {
        private String status;
        private Instant timestamp;
        private String changedBy;
        private String reason;
    }
}
