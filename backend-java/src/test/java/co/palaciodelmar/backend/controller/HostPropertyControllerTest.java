package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.PropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostPropertyControllerTest {

    private PropertyService propertyService;
    private HostPropertyController controller;
    private UserPrincipal hostPrincipal;

    @BeforeEach
    void setUp() {
        propertyService = mock(PropertyService.class);
        controller = new HostPropertyController(propertyService);
        User host = User.builder().id("host-1").email("host@example.com").role("HOST").build();
        hostPrincipal = new UserPrincipal(host);
    }

    @Test
    void getPropertyIcalSyncInfoUsesFallbacksForNullSuiteFields() {
        PropertyDTO property = PropertyDTO.builder().id("property-1").name("Casa del Mar").build();
        Suite suiteWithNullFields = Suite.builder().build();
        when(propertyService.getPropertyByOwner("property-1", "host-1", false)).thenReturn(property);
        when(propertyService.getPropertySuitesForHost("property-1", "host-1", false))
                .thenReturn(List.of(suiteWithNullFields));

        var response = controller.getPropertyIcalSyncInfo(hostPrincipal, "property-1");

        assertEquals(200, response.getStatusCode().value());
        var feeds = (List<?>) response.getBody().getData().get("suiteFeeds");
        assertEquals(1, feeds.size());
        @SuppressWarnings("unchecked")
        var feed = (java.util.Map<String, String>) feeds.get(0);
        assertEquals("", feed.get("suiteId"));
        assertEquals("Suite", feed.get("suiteName"));
        assertFalse(feed.get("icalUrl").contains("/suites//"),
                "una habitación sin ID no debe generar un enlace iCal inválido");
    }

    @Test
    void getPropertyIcalSyncInfoDoesNotExposeAnotherHostsFeed() {
        when(propertyService.getPropertyByOwner("victim-property", "host-1", false))
                .thenThrow(new ResourceNotFoundException("Propiedad no encontrada"));

        assertThrows(ResourceNotFoundException.class,
                () -> controller.getPropertyIcalSyncInfo(hostPrincipal, "victim-property"));

        verify(propertyService, never()).getPropertySuitesForHost(anyString(), anyString(), anyBoolean());
    }
}
