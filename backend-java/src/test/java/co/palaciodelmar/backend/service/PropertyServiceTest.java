package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.HostSuiteRequest;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.dto.PropertyRequest;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private SuiteRepository suiteRepository;

    private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        propertyService = new PropertyService(propertyRepository, suiteRepository);
    }

    @Test
    @DisplayName("Host puede crear una propiedad con estado PENDING y comisión del 8%")
    void hostCanCreateProperty() {
        PropertyRequest req = PropertyRequest.builder()
                .name("Hotel Santa Clara Boutique")
                .city("Cartagena")
                .address("Calle del Torno # 39-29")
                .type("HOTEL")
                .build();

        when(propertyRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> {
            Property p = invocation.getArgument(0);
            p.setId("prop-123");
            return p;
        });

        PropertyDTO result = propertyService.createProperty("host-1", req, false);

        assertNotNull(result);
        assertEquals("prop-123", result.getId());
        assertEquals("host-1", result.getOwnerId());
        assertEquals("PENDING", result.getStatus(), "Propiedad de anfitrión nuevo debe iniciar en PENDING");
        assertEquals(new BigDecimal("0.08"), result.getCommissionRate(), "Comisión debe ser 8%");
    }

    @Test
    @DisplayName("Anti-IDOR: Host A no puede acceder a propiedad de Host B (lanza ResourceNotFoundException / 404)")
    void antiIdorHostCannotAccessOtherHostProperty() {
        Property propB = Property.builder()
                .id("prop-B")
                .ownerId("host-B")
                .name("Cabaña de Host B")
                .build();

        when(propertyRepository.findById("prop-B")).thenReturn(Optional.of(propB));

        // Intento de acceso de Host A sobre la propiedad de Host B
        assertThrows(ResourceNotFoundException.class, () -> {
            propertyService.getPropertyByOwner("prop-B", "host-A", false);
        });

        // Intento de actualización de Host A sobre propiedad de Host B
        assertThrows(ResourceNotFoundException.class, () -> {
            PropertyRequest updateReq = PropertyRequest.builder().name("Hackeado").build();
            propertyService.updateProperty("prop-B", "host-A", updateReq, false);
        });
    }

    @Test
    @DisplayName("Admin puede acceder a cualquier propiedad sin restricción Anti-IDOR")
    void adminCanAccessAnyProperty() {
        Property propB = Property.builder()
                .id("prop-B")
                .ownerId("host-B")
                .name("Hotel de Host B")
                .build();

        when(propertyRepository.findById("prop-B")).thenReturn(Optional.of(propB));

        PropertyDTO result = propertyService.getPropertyByOwner("prop-B", "admin-1", true);
        assertNotNull(result);
        assertEquals("prop-B", result.getId());
    }

    @Test
    @DisplayName("Anti-IDOR: Host A no puede crear o editar suites en propiedad de Host B")
    void antiIdorHostCannotManageSuitesInOtherHostProperty() {
        Property propB = Property.builder()
                .id("prop-B")
                .ownerId("host-B")
                .name("Hotel de Host B")
                .build();

        when(propertyRepository.findById("prop-B")).thenReturn(Optional.of(propB));

        HostSuiteRequest suiteReq = HostSuiteRequest.builder()
                .name("Suite Intruso")
                .basePrice(new BigDecimal("150000"))
                .build();

        assertThrows(ResourceNotFoundException.class, () -> {
            propertyService.createSuiteForProperty("prop-B", "host-A", suiteReq, false);
        });
    }

    @Test
    @DisplayName("Consulta pública solo lista propiedades con estado APPROVED")
    void publicListingOnlyReturnsApprovedProperties() {
        Property approved = Property.builder()
                .id("prop-appr")
                .name("Aprobado")
                .status("APPROVED")
                .city("Cartagena")
                .active(true)
                .build();

        when(propertyRepository.findByCityIgnoreCaseAndStatus("Cartagena", "APPROVED"))
                .thenReturn(List.of(approved));

        List<PropertyDTO> result = propertyService.getApprovedProperties("Cartagena");

        assertEquals(1, result.size());
        assertEquals("APPROVED", result.get(0).getStatus());
    }

    @Test
    @DisplayName("Crear suite con imagen vacía usa la imagen de la propiedad o fallback")
    void createSuiteWithBlankImageUsesPropertyImageOrFallback() {
        Property prop = Property.builder()
                .id("prop-img")
                .ownerId("host-1")
                .name("Hotel Vista Mar")
                .mainImage("https://example.com/hotel.jpg")
                .build();

        when(propertyRepository.findById("prop-img")).thenReturn(Optional.of(prop));
        when(suiteRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(suiteRepository.save(any(Suite.class))).thenAnswer(i -> i.getArgument(0));

        HostSuiteRequest req = HostSuiteRequest.builder()
                .name("Habitacion 101")
                .basePrice(new BigDecimal("200000"))
                .mainImage("") // vacío
                .build();

        Suite created = propertyService.createSuiteForProperty("prop-img", "host-1", req, false);

        assertNotNull(created);
        assertEquals("https://example.com/hotel.jpg", created.getMainImage());
        assertTrue(created.getActive());
        assertTrue(created.getAvailable());
    }

    @Test
    @DisplayName("Desactivar suite marca active=false y available=false")
    void deleteSuiteSetsActiveAndAvailableFalse() {
        Property prop = Property.builder()
                .id("prop-del")
                .ownerId("host-1")
                .build();

        Suite suite = Suite.builder()
                .id("suite-1")
                .propertyId("prop-del")
                .active(true)
                .available(true)
                .build();

        when(propertyRepository.findById("prop-del")).thenReturn(Optional.of(prop));
        when(suiteRepository.findById("suite-1")).thenReturn(Optional.of(suite));

        propertyService.deleteSuiteForProperty("prop-del", "suite-1", "host-1", false);

        assertFalse(suite.getActive());
        assertFalse(suite.getAvailable());
        verify(suiteRepository).save(suite);
    }
}
