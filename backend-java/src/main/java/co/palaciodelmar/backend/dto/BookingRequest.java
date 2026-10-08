package co.palaciodelmar.backend.dto;

import co.palaciodelmar.backend.model.Booking;
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
    private String suiteId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private LocalDate newCheckIn;
    private LocalDate newCheckOut;
    private Integer guests;
    private Integer children;
    private String guestName;
    private String guestEmail;
    private String guestPhone;
    private String specialRequests;
    private Booking.GuestDetails guestDetails;
    private List<Object> experiences;

    public LocalDate getCheckIn() {
        return checkIn != null ? checkIn : newCheckIn;
    }

    public LocalDate getCheckOut() {
        return checkOut != null ? checkOut : newCheckOut;
    }
}
