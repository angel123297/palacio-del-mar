package co.palaciodelmar.backend.dto;

import co.palaciodelmar.backend.model.Booking;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotBlank(message = "Por favor selecciona una habitación")
    private String suiteId;

    @NotNull(message = "Por favor selecciona la fecha de llegada (check-in)")
    private LocalDate checkIn;

    @NotNull(message = "Por favor selecciona la fecha de salida (check-out)")
    private LocalDate checkOut;

    private LocalDate newCheckIn;
    private LocalDate newCheckOut;

    @Min(value = 1, message = "Debe haber al menos 1 huésped")
    private Integer guests;

    @Min(value = 0, message = "El número de niños no puede ser negativo")
    private Integer children;
    private Boolean needsCrib;

    private String guestName;
    private String guestEmail;

    private String guestPhone;
    private String specialRequests;
    private Booking.GuestDetails guestDetails;
    private List<Object> experiences;

    private Boolean airportPickup;
    private Boolean petFriendly;
    private List<Booking.FoodOrder> foodOrders;

    public LocalDate getCheckIn() {
        return checkIn != null ? checkIn : newCheckIn;
    }

    public LocalDate getCheckOut() {
        return checkOut != null ? checkOut : newCheckOut;
    }
}
