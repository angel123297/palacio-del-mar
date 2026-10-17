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
}
