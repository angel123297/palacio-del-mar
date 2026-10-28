package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.BookingDTO;
import co.palaciodelmar.backend.exception.GlobalExceptionHandler;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import co.palaciodelmar.backend.service.IdempotencyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private IdempotencyService idempotencyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BookingController controller = new BookingController(bookingService);
        ReflectionTestUtils.setField(controller, "idempotencyService", idempotencyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBookingUsesAuthenticatedUserId() throws Exception {
        authenticate("guest-1");
        when(bookingService.createBooking(eq("guest-1"), any())).thenReturn(
                BookingDTO.builder().id("booking-1").build());

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suiteId":"suite-1","checkIn":"2026-11-01","checkOut":"2026-11-03","guests":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("booking-1"));

        verify(bookingService).createBooking(eq("guest-1"), any());
    }

    @Test
    void getBookingsScopesResultsToAuthenticatedUser() throws Exception {
        authenticate("guest-2");
        when(bookingService.getUserBookings("guest-2")).thenReturn(List.of(
                BookingDTO.builder().id("booking-2").build()));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("booking-2"));

        verify(bookingService).getUserBookings("guest-2");
    }

    @Test
    void upcomingBookingsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/upcoming"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingService);
    }

    @Test
    void modifyBookingRejectsReversedDatesBeforeCallingService() throws Exception {
        authenticate("guest-3");

        mockMvc.perform(put("/api/bookings/booking-3/modify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suiteId":"suite-1","checkIn":"2026-11-04","checkOut":"2026-11-03"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    void modifyBookingRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/bookings/booking-3/modify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suiteId":"suite-1","checkIn":"2026-11-03","checkOut":"2026-11-04"}
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingService);
    }

    @Test
    void replayedCreateBookingSetsReplayHeaderWithoutCreatingAnotherBooking() throws Exception {
        authenticate("guest-4");
        when(idempotencyService.execute(anyString(), eq("guest-4"), eq("booking-key"), any(),
                eq(BookingDTO.class), any()))
                .thenReturn(new IdempotencyService.IdempotentResult<>(
                        BookingDTO.builder().id("booking-cached").build(), true));

        mockMvc.perform(post("/api/bookings")
                        .header("Idempotency-Key", "booking-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suiteId":"suite-1","checkIn":"2026-11-01","checkOut":"2026-11-03","guests":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.data.id").value("booking-cached"));

        verifyNoInteractions(bookingService);
    }

    private void authenticate(String userId) {
        User user = User.builder().id(userId).email(userId + "@example.com").role("USER").build();
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
