package co.palaciodelmar.backend.dto;

import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Branch;
import co.palaciodelmar.backend.model.Suite;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDTO {
    private String id;
    private String _id;
    private String user;
    private Suite suite;
    private Branch branch;
    private Integer unitSlot;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer nights;
    private Integer guests;
    private Integer children;

    private String guestName;
    private String guestEmail;
    private String guestPhone;
    private String specialRequests;

    private BigDecimal pricePerNight;
    private BigDecimal subtotal;
    private BigDecimal totalPrice;
    private BigDecimal paidAmount;
    private BigDecimal refundedAmount;
    private BigDecimal balanceDue;

    private String status;
    private String paymentStatus;
    private String bookingCode;
    private Boolean isModifiable;
    private Boolean isCancelable;
    private Boolean isCancellable;

    private Booking.GuestDetails guestDetails;
    private List<Booking.SelectedExperience> experiences;

    private String cancellationReason;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
