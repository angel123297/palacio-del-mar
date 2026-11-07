package co.palaciodelmar.backend.security;

import co.palaciodelmar.backend.controller.AdminController;
import co.palaciodelmar.backend.controller.HostPropertyController;
import co.palaciodelmar.backend.dto.PropertyDTO;
import co.palaciodelmar.backend.dto.PropertyRequest;
import co.palaciodelmar.backend.exception.GlobalExceptionHandler;
import co.palaciodelmar.backend.exception.ResourceNotFoundException;
import co.palaciodelmar.backend.model.Booking;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Suite;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.BranchRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
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

    @Test
    @DisplayName("Anti-IDOR PDM-03: Host con branches vacío no puede acceder a reserva si no es dueño de la propiedad")
    void hostWithoutBranchesCannotAccessBookingUnlessOwnerOfProperty() {
        BookingRepository bRepo = mock(BookingRepository.class);
        PropertyRepository pRepo = mock(PropertyRepository.class);
        UserRepository uRepo = mock(UserRepository.class);
        SuiteRepository sRepo = mock(SuiteRepository.class);
        BranchRepository brRepo = mock(BranchRepository.class);

        BookingService realBookingService = new BookingService(
                bRepo, sRepo, brRepo, mock(co.palaciodelmar.backend.repository.ExperienceRepository.class),
                mock(co.palaciodelmar.backend.repository.SuiteNightRepository.class), uRepo,
                mock(co.palaciodelmar.backend.repository.PromotionRepository.class),
                mock(co.palaciodelmar.backend.service.PricingService.class),
                mock(co.palaciodelmar.backend.service.PaymentGateway.class)
        );
        org.springframework.test.util.ReflectionTestUtils.setField(realBookingService, "propertyRepository", pRepo);

        Booking booking = Booking.builder()
                .id("bk-target-1")
                .propertyId("prop-target-1")
                .user("user-guest")
                .build();
        when(bRepo.findById("bk-target-1")).thenReturn(Optional.of(booking));

        Property property = Property.builder()
                .id("prop-target-1")
                .ownerId("real-host-owner")
                .build();
        when(pRepo.findById("prop-target-1")).thenReturn(Optional.of(property));

        // Host atacante sin sedes legacy
        User attackerHost = User.builder()
                .id("attacker-host")
                .role("HOST")
                .branches(List.of())
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                realBookingService.getBookingByIdSecure("bk-target-1", attackerHost)
        );

        // Host dueño legítimo de la propiedad
        User legitimateHost = User.builder()
                .id("real-host-owner")
                .role("HOST")
                .branches(List.of())
                .build();

        assertDoesNotThrow(() ->
                realBookingService.getBookingByIdSecure("bk-target-1", legitimateHost)
        );
    }

    @Test
    @DisplayName("HostController + PDM-07: Anfitrión del marketplace (propertyId) puede hacer check-in y regenerar PIN criptográfico de 6 dígitos; otro host recibe 403")
    void marketplaceHostCanCheckInAndRegenerateSecurePinOnOwnedPropertyOnly() throws Exception {
        co.palaciodelmar.backend.repository.PromotionRepository promoRepo = mock(co.palaciodelmar.backend.repository.PromotionRepository.class);
        co.palaciodelmar.backend.controller.HostController hostOpsController =
                new co.palaciodelmar.backend.controller.HostController(
                        bookingRepository, suiteRepository, bookingService, promoRepo, propertyRepository);

        MockMvc opsMvc = MockMvcBuilders.standaloneSetup(hostOpsController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Booking marketplaceBooking = Booking.builder()
                .id("bk-mkt-1")
                .propertyId("prop-mkt-1")
                .status("confirmed")
                .build();
        when(bookingRepository.findById("bk-mkt-1")).thenReturn(Optional.of(marketplaceBooking));

        Property ownedProperty = Property.builder()
                .id("prop-mkt-1")
                .ownerId("host-owner-1")
                .build();
        when(propertyRepository.findById("prop-mkt-1")).thenReturn(Optional.of(ownedProperty));

        // 1. Host atacante sin acceso a la propiedad -> 403 Forbidden
        authenticateUser("host-attacker", "attacker@palacio.co", "HOST");
        opsMvc.perform(post("/api/host/bookings/bk-mkt-1/checkin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        opsMvc.perform(post("/api/host/bookings/bk-mkt-1/regenerate-pin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // 2. Host propietario legítimo (branches vacío, dueño por propertyId) -> 200 OK y PIN de 6 dígitos >= 100000
        authenticateUser("host-owner-1", "owner@palacio.co", "HOST");
        opsMvc.perform(post("/api/host/bookings/bk-mkt-1/checkin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        opsMvc.perform(post("/api/host/bookings/bk-mkt-1/regenerate-pin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.smartLockPin").isString());

        assertNotNull(marketplaceBooking.getSmartLockPin());
        assertEquals(6, marketplaceBooking.getSmartLockPin().length());
        int pinValue = Integer.parseInt(marketplaceBooking.getSmartLockPin());
        assertTrue(pinValue >= 100000 && pinValue <= 999999, "El PIN debe estar en el rango [100000, 999999]");
    }

    @Test
    @DisplayName("PDM-08: AdminController.createPromotion usa PromotionRequest DTO validado e ignora mass assignment")
    void adminCreatePromotionValidatesDtoAndPreventsMassAssignment() throws Exception {
        authenticateUser("admin-1", "admin@palacio.co", "ADMIN");

        // Payload inválido (descuento fuera de rango y título vacío) -> 400 Bad Request
        adminMockMvc.perform(post("/api/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"discountPercent\":150}"))
                .andExpect(status().isBadRequest());

        // Payload válido con intento de inyectar id e isSample -> se ignoran campos internos
        co.palaciodelmar.backend.model.Promotion savedPromo = co.palaciodelmar.backend.model.Promotion.builder()
                .id("generated-id")
                .title("Promo Caribe")
                .branch("all")
                .discountPercent(20)
                .active(true)
                .isSample(false)
                .build();
        when(adminService.createPromotion(any())).thenReturn(savedPromo);

        adminMockMvc.perform(post("/api/admin/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"hacked-id\",\"isSample\":true,\"title\":\"Promo Caribe\",\"branchId\":\"all\",\"discountPercent\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Promo Caribe"))
                .andExpect(jsonPath("$.data.branch").value("all"))
                .andExpect(jsonPath("$.data.isSample").value(false));
    }

    @Test
    @DisplayName("Host iCal Sync: Propietario legítimo obtiene feeds e instrucciones, anfitrión ajeno recibe 404 (Anti-IDOR)")
    void hostIcalSyncAntiIdorProtection() throws Exception {
        authenticateUser("host-legit", "legit@palacio.co", "HOST");

        PropertyDTO myProp = PropertyDTO.builder()
                .id("prop-mine")
                .name("Villa Privada")
                .active(true)
                .build();

        Suite suiteA = Suite.builder()
                .id("suite-a")
                .name("Master Suite")
                .propertyId("prop-mine")
                .build();

        when(propertyService.getPropertyByOwner("prop-mine", "host-legit", false)).thenReturn(myProp);
        when(propertyService.getPropertySuitesForHost("prop-mine", "host-legit", false)).thenReturn(List.of(suiteA));

        // 1. Host legítimo accede con éxito
        hostMockMvc.perform(get("/api/host/properties/prop-mine/sync/ical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.propertyId").value("prop-mine"))
                .andExpect(jsonPath("$.data.propertyIcalUrl").value("/api/properties/prop-mine/calendar.ics"))
                .andExpect(jsonPath("$.data.suiteFeeds[0].suiteId").value("suite-a"))
                .andExpect(jsonPath("$.data.suiteFeeds[0].icalUrl").value("/api/suites/suite-a/calendar.ics"))
                .andExpect(jsonPath("$.data.instructions.airbnb").exists())
                .andExpect(jsonPath("$.data.instructions.booking").exists());

        // 2. Anfitrión ajeno intenta acceder -> 404 No encontrado (Anti-IDOR)
        authenticateUser("host-attacker", "attacker@palacio.co", "HOST");
        when(propertyService.getPropertyByOwner("prop-mine", "host-attacker", false))
                .thenThrow(new ResourceNotFoundException("Propiedad no encontrada"));

        hostMockMvc.perform(get("/api/host/properties/prop-mine/sync/ical"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
