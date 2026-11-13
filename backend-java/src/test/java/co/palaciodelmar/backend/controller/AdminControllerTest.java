package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.service.AdminService;
import co.palaciodelmar.backend.service.BookingService;
import co.palaciodelmar.backend.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminControllerTest {

    private AdminService adminService;
    private BookingRepository bookingRepository;
    private BookingService bookingService;
    private PropertyService propertyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        bookingRepository = mock(BookingRepository.class);
        bookingService = mock(BookingService.class);
        propertyService = mock(PropertyService.class);

        AdminController controller = new AdminController(
                adminService,
                bookingRepository,
                bookingService,
                propertyService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("R7b: GET /api/admin/bookings/export-csv retorna status 200, Content-Type text/csv;charset=UTF-8 y encabezado attachment")
    void testExportBookingsCsvEndpoint() throws Exception {
        byte[] sampleCsv = ("\uFEFFID,Código Reserva\n1,PDM-101").getBytes(StandardCharsets.UTF_8);
        when(adminService.exportBookingsCsv()).thenReturn(sampleCsv);

        mockMvc.perform(get("/api/admin/bookings/export-csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reservas-palacio-del-mar.csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(content().bytes(sampleCsv));

        verify(adminService).exportBookingsCsv();
    }
}
