package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.exception.GlobalExceptionHandler;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.service.AvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityControllerTest {

    @Mock
    private AvailabilityService availabilityService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(availabilityService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void availabilityEndpointsReturnServiceResultsAndMapPromotionFields() throws Exception {
        when(availabilityService.checkAvailability(
                LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"), 2, "centro", null))
                .thenReturn(AvailabilityService.AvailabilityResponseData.builder()
                        .checkIn(LocalDate.parse("2026-11-01"))
                        .checkOut(LocalDate.parse("2026-11-03"))
                        .nights(2L)
                        .build());
        when(availabilityService.getMonthlyAvailability(2026, 11, null, null, null, null))
                .thenReturn(AvailabilityService.MonthlyAvailabilityResponseData.builder()
                        .year(2026).month(11).daysInMonth(30).totalSuites(4).build());
        when(availabilityService.getMonthlyPromotions(2026, 11, null))
                .thenReturn(List.of(Promotion.builder()
                        .id("promo-1")
                        .title("Noviembre")
                        .branch("centro")
                        .discountPercent(15)
                        .active(true)
                        .build()));
        when(availabilityService.getPeakDates(2026)).thenReturn(Map.of("year", 2026));

        mockMvc.perform(get("/api/availability")
                        .param("checkIn", "2026-11-01")
                        .param("checkOut", "2026-11-03")
                        .param("guests", "2")
                        .param("branch", "centro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nights").value(2));
        mockMvc.perform(get("/api/availability/monthly")
                        .param("year", "2026")
                        .param("month", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daysInMonth").value(30));
        mockMvc.perform(get("/api/availability/promotions")
                        .param("year", "2026")
                        .param("month", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].discountPercent").value(15));
        mockMvc.perform(get("/api/availability/peak-dates").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026));
    }

    @Test
    void missingRequiredCheckInReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/availability").param("checkOut", "2026-11-03"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(availabilityService);
    }

    @Test
    void malformedCheckInReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/availability")
                        .param("checkIn", "not-a-date")
                        .param("checkOut", "2026-11-03"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(availabilityService);
    }
}
