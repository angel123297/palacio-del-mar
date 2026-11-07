package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.service.ICalService;
import co.palaciodelmar.backend.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PropertyControllerTest {

    @Mock
    private PropertyService propertyService;

    @Mock
    private ICalService iCalService;

    private PropertyController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new PropertyController(propertyService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void approvedPropertyListingReturnsPropertiesForCity() throws Exception {
        when(propertyService.getApprovedProperties("Cartagena")).thenReturn(List.of(
                PropertyDTO.builder().id("property-1").name("Casa del Mar").city("Cartagena").build()));

        mockMvc.perform(get("/api/properties").param("city", "Cartagena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("property-1"))
                .andExpect(jsonPath("$.data[0].name").value("Casa del Mar"));
    }

    @Test
    void calendarRoutesReturnServiceUnavailableWhenIcalServiceIsMissing() throws Exception {
        mockMvc.perform(get("/api/properties/property-1/calendar.ics"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/properties/property-1/suites/suite-1/calendar.ics"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(iCalService);
    }

    @Test
    void propertyAndSuiteCalendarFeedsReturnCalendarHeadersAndContent() throws Exception {
        controller.setIcalService(iCalService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(iCalService.generatePropertyCalendarIcs("property-2")).thenReturn("BEGIN:VCALENDAR\nEND:VCALENDAR");
        when(iCalService.generateSuiteCalendarIcs("property-2", "suite-2"))
                .thenReturn("BEGIN:VCALENDAR\nBEGIN:VEVENT\nEND:VEVENT\nEND:VCALENDAR");

        mockMvc.perform(get("/api/properties/property-2/calendar.ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/calendar")))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"property-property-2.ics\""))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(content().string("BEGIN:VCALENDAR\nEND:VCALENDAR"));
        mockMvc.perform(get("/api/properties/property-2/suites/suite-2/calendar.ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/calendar")))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"suite-suite-2.ics\""))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
                .andExpect(content().string("BEGIN:VCALENDAR\nBEGIN:VEVENT\nEND:VEVENT\nEND:VCALENDAR"));
    }
}
