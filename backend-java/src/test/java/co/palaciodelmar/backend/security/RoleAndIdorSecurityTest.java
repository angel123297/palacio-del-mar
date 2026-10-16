package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.controller.AdminController;
import co.palaciodelmar.backend.controller.HostPropertyController;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.dto.PropertyRequest;
import co.palaciodelmar.backend.exception.GlobalExceptionHandler;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.service.AdminService;
import co.palaciodelmar.backend.service.BookingService;
import co.palaciodelmar.backend.service.PropertyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoleAndIdorSecurityTest {

    private MockMvc hostMockMvc;
    private MockMvc adminMockMvc;

    @Mock
    private PropertyService propertyService;

    @Mock
    private AdminService adminService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingService bookingService;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private SuiteRepository suiteRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        HostPropertyController hostController = new HostPropertyController(propertyService);
        AdminController adminController = new AdminController(adminService, bookingRepository, bookingService, propertyService);

        hostMockMvc = MockMvcBuilders.standaloneSetup(hostController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        adminMockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(String id, String email, String role) {
        User user = User.builder()
                .id(id)
                .email(email)
                .role(role)
                .status("active")
                .build();
        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // =========================================================================
    // 1. PRUEBAS ANTI-IDOR EN CAPA CONTROLADOR HTTP (MockMvc)
    // =========================================================================

    @Test
    @DisplayName("Anti-IDOR: Host malicioso intenta leer propiedad ajena y recibe 404 Not Found (no revela existencia)")
    void hostCannotReadOtherHostProperty() throws Exception {
        authenticateUser("host-attacker", "attacker@palacio.co", "HOST");

        when(propertyService.getPropertyByOwner(eq("victim-prop-1"), eq("host-attacker"), eq(false)))
                .thenThrow(new ResourceNotFoundException("Propiedad no encontrada"));

        hostMockMvc.perform(get("/api/host/properties/victim-prop-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Propiedad no encontrada"));
    }

    @Test
    @DisplayName("Anti-IDOR: Host malicioso intenta modificar propiedad ajena y recibe 404 Not Found")
    void hostCannotUpdateOtherHostProperty() throws Exception {
        authenticateUser("host-attacker", "attacker@palacio.co", "HOST");

        PropertyRequest updateReq = PropertyRequest.builder()
                .name("Nombre Vulnerado")
                .city("Cartagena")
                .address("Calle Hack")
                .build();

        when(propertyService.updateProperty(eq("victim-prop-1"), eq("host-attacker"), any(), eq(false)))
                .thenThrow(new ResourceNotFoundException("Propiedad no encontrada"));

        hostMockMvc.perform(put("/api/host/properties/victim-prop-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Anti-IDOR: Host malicioso no puede acceder a las habitaciones de un alojamiento ajeno (404)")
    void hostCannotReadSuitesOfOtherHostProperty() throws Exception {
        authenticateUser("host-attacker", "attacker@palacio.co", "HOST");

        when(propertyService.getPropertySuitesForHost(eq("victim-prop-1"), eq("host-attacker"), eq(false)))
                .thenThrow(new ResourceNotFoundException("Propiedad no encontrada"));

        hostMockMvc.perform(get("/api/host/properties/victim-prop-1/suites"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Acceso legítimo: Host propietario consulta exitosamente su propio alojamiento (200 OK)")
    void hostCanReadOwnProperties() throws Exception {
        authenticateUser("host-legitimate", "owner@palacio.co", "HOST");

        PropertyDTO prop = PropertyDTO.builder()
                .id("my-prop-1")
                .name("Mi Hotel Boutique")
                .ownerId("host-legitimate")
                .city("Cartagena")
                .build();

        when(propertyService.getPropertiesByOwner("host-legitimate")).thenReturn(List.of(prop));

        hostMockMvc.perform(get("/api/host/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("my-prop-1"))
                .andExpect(jsonPath("$.data[0].name").value("Mi Hotel Boutique"));
    }

    @Test
    @DisplayName("Acceso legítimo: Administrador del sistema tiene bypass y puede auditar cualquier propiedad")
    void adminCanAccessAnyHostProperty() throws Exception {
        authenticateUser("admin-global", "admin@palacio.co", "ADMIN");

        PropertyDTO prop = PropertyDTO.builder()
                .id("victim-prop-1")
                .name("Hotel Auditado por Admin")
                .ownerId("host-victim")
                .build();

        when(propertyService.getPropertyByOwner(eq("victim-prop-1"), eq("admin-global"), eq(true)))
                .thenReturn(prop);

        hostMockMvc.perform(get("/api/host/properties/victim-prop-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Hotel Auditado por Admin"));
    }

    @Test
    @DisplayName("Panel Admin: Administrador actualiza estado de aprobación de propiedad en marketplace")
    void adminCanUpdatePropertyStatus() throws Exception {
        PropertyDTO updated = PropertyDTO.builder()
                .id("prop-waos")
                .name("Hotel Waos")
                .status("APPROVED")
                .build();

        when(propertyService.updatePropertyStatus("prop-waos", "APPROVED")).thenReturn(updated);

        adminMockMvc.perform(put("/api/admin/properties/prop-waos/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    // =========================================================================
    // 2. PRUEBAS DE AISLAMIENTO ESTRICTO EN SERVICIO (PropertyService Anti-IDOR)
    // =========================================================================

    @Test
    @DisplayName("Servicio Anti-IDOR: getPropertyEntityWithOwnership arroja ResourceNotFoundException si ownerId no coincide")
    void serviceEnforcesAntiIdorOwnershipValidation() {
        PropertyService realService = new PropertyService(propertyRepository, suiteRepository);

        Property victimProperty = Property.builder()
                .id("prop-victim-100")
                .name("Villa Privada")
                .ownerId("real-owner-456")
                .build();

        when(propertyRepository.findById("prop-victim-100")).thenReturn(Optional.of(victimProperty));

        // Intento de acceso por host no propietario (sin rol admin)
        assertThrows(ResourceNotFoundException.class, () ->
                realService.getPropertyEntityWithOwnership("prop-victim-100", "attacker-host-999", false)
        );

        // Intento de acceso por el dueño legítimo (éxito)
        Property legitimateAccess = realService.getPropertyEntityWithOwnership("prop-victim-100", "real-owner-456", false);
        assertNotNull(legitimateAccess);
        assertEquals("prop-victim-100", legitimateAccess.getId());

        // Intento de acceso por el administrador (éxito vía bypass administrativo)
        Property adminAccess = realService.getPropertyEntityWithOwnership("prop-victim-100", "admin-123", true);
        assertNotNull(adminAccess);
        assertEquals("prop-victim-100", adminAccess.getId());
    }
}
