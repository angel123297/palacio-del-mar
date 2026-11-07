package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.CalculatePriceRequest;
import co.palaciodelmar.backend.dto.CalculatePriceResponseDTO;
import co.palaciodelmar.backend.dto.SuiteResponseDTO;
import co.palaciodelmar.backend.dto.SuiteTypeDTO;
import co.palaciodelmar.backend.service.ICalService;
import co.palaciodelmar.backend.service.SuiteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ============================================================================
 * PRUEBAS UNITARIAS: "Controller Producto" (SuiteController - Catálogo Marketplace)
 * Tecnologías evaluadas: JUnit 5, Mockito, AssertJ, Spring Test (MockMvc)
 * ============================================================================
 * Esta clase prueba de forma unitaria y aislada los endpoints de gestión y
 * consulta de productos (Suites/Alojamientos):
 * 1. Consulta de producto por identificador o slug (GET /api/suites/{idOrSlug}).
 * 2. Listado de productos activos con filtros (GET /api/suites?branch=...).
 * 3. Cálculo dinámico de precio de producto (POST /api/suites/calculate-price).
 * 4. Tipos de productos disponibles (GET /api/suites/types).
 * 5. Verificación de códigos HTTP (200 OK), formato ApiResponse y aserciones con AssertJ.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Controller Producto: SuiteController (JUnit 5 + Mockito + AssertJ + Spring Test)")
class ProductoControllerUnitTest {

    @Mock
    private SuiteService suiteService;

    @Mock
    private ICalService icalService;

    @InjectMocks
    private SuiteController suiteController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private SuiteResponseDTO productoEjemplo;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(suiteController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        productoEjemplo = SuiteResponseDTO.builder()
                .id("prod-suite-101")
                .name("Suite Presidencial Vista al Mar")
                .slug("suite-presidencial-vista-mar")
                .type("Suite Imperial")
                .basePrice(new BigDecimal("650000"))
                .maxGuests(4)
                .size(75)
                .description("Alojamiento de lujo frente a las playas de Bocagrande")
                .build();
    }

    @Test
    @DisplayName("Debe retornar detalle de producto con HTTP 200 y DTO correcto vía ResponseEntity y AssertJ")
    void deberiaObtenerDetalleProductoPorIdConAssertJ() {
        // GIVEN: El servicio retorna el producto simulado
        when(suiteService.getSuiteByIdOrSlug("prod-suite-101")).thenReturn(productoEjemplo);

        // WHEN: Se invoca directamente el método del controlador
        ResponseEntity<ApiResponse<SuiteResponseDTO>> response = suiteController.getSuiteByIdOrSlug("prod-suite-101");

        // THEN: Validaciones expresivas con AssertJ (assertThat)
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        ApiResponse<SuiteResponseDTO> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSuccess()).isTrue();

        SuiteResponseDTO data = body.getData();
        assertThat(data.getId()).isEqualTo("prod-suite-101");
        assertThat(data.getName()).isEqualTo("Suite Presidencial Vista al Mar");
        assertThat(data.getBasePrice()).isEqualByComparingTo("650000");
        assertThat(data.getMaxGuests()).isEqualTo(4);

        // Verificamos interacción con el mock
        verify(suiteService, times(1)).getSuiteByIdOrSlug("prod-suite-101");
    }

    @Test
    @DisplayName("Debe listar todos los productos activos filtrados por sucursal")
    void deberiaListarProductosActivosFiltrados() {
        // GIVEN: Lista de 2 productos en el catálogo
        SuiteResponseDTO producto2 = SuiteResponseDTO.builder()
                .id("prod-suite-102")
                .name("Casona Colonial Getsemaní")
                .type("Villa")
                .basePrice(new BigDecimal("420000"))
                .maxGuests(6)
                .build();

        when(suiteService.getAllActiveSuites("cartagena-centro")).thenReturn(List.of(productoEjemplo, producto2));

        // WHEN
        ResponseEntity<ApiResponse<List<SuiteResponseDTO>>> response = suiteController.getAllSuites("cartagena-centro");

        // THEN: Aserciones con AssertJ
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
                .hasSize(2)
                .extracting(SuiteResponseDTO::getName)
                .containsExactly("Suite Presidencial Vista al Mar", "Casona Colonial Getsemaní");

        verify(suiteService).getAllActiveSuites("cartagena-centro");
    }

    @Test
    @DisplayName("Debe calcular precio de producto según fechas de estancia")
    void deberiaCalcularPrecioDeProductoSegunFechas() {
        // GIVEN: Request de cotización de 3 noches
        CalculatePriceRequest request = CalculatePriceRequest.builder()
                .suiteId("prod-suite-101")
                .checkIn(LocalDate.of(2026, 11, 10))
                .checkOut(LocalDate.of(2026, 11, 13))
                .build();

        CalculatePriceResponseDTO cotizacionSimulada = CalculatePriceResponseDTO.builder()
                .suiteId("prod-suite-101")
                .nightlyPrice(new BigDecimal("650000"))
                .subtotal(new BigDecimal("1950000"))
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("1950000"))
                .build();

        when(suiteService.calculatePrice(any(CalculatePriceRequest.class))).thenReturn(cotizacionSimulada);

        // WHEN
        ResponseEntity<ApiResponse<CalculatePriceResponseDTO>> response = suiteController.calculatePrice(request);

        // THEN: AssertJ
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getTotal()).isEqualByComparingTo("1950000");
        assertThat(response.getBody().getData().getNightlyPrice()).isEqualByComparingTo("650000");

        verify(suiteService).calculatePrice(any(CalculatePriceRequest.class));
    }

    @Test
    @DisplayName("Debe verificar contrato REST y JSON HTTP 200 usando Spring Test MockMvc")
    void deberiaVerificarContratoRestConSpringTestMockMvc() throws Exception {
        // GIVEN
        when(suiteService.getSuiteByIdOrSlug("prod-suite-101")).thenReturn(productoEjemplo);

        // WHEN & THEN: Usando MockMvc (Spring Test) + Assertions
        mockMvc.perform(get("/api/suites/prod-suite-101")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("prod-suite-101"))
                .andExpect(jsonPath("$.data.name").value("Suite Presidencial Vista al Mar"))
                .andExpect(jsonPath("$.data.maxGuests").value(4));
    }

    @Test
    @DisplayName("Debe obtener tipos de productos o categorías disponibles")
    void deberiaObtenerTiposDeProductos() {
        // GIVEN
        SuiteTypeDTO tipo1 = SuiteTypeDTO.builder()
                .name("Suite Presidencial")
                .count(5)
                .minPrice(new BigDecimal("650000"))
                .build();
        SuiteTypeDTO tipo2 = SuiteTypeDTO.builder()
                .name("Villa Colonial")
                .count(3)
                .minPrice(new BigDecimal("420000"))
                .build();
        when(suiteService.getSuiteTypes()).thenReturn(List.of(tipo1, tipo2));

        // WHEN
        ResponseEntity<ApiResponse<List<SuiteTypeDTO>>> response = suiteController.getSuiteTypes();

        // THEN
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getData()).hasSize(2);
        assertThat(response.getBody().getData()).extracting(SuiteTypeDTO::getName).contains("Suite Presidencial", "Villa Colonial");
        verify(suiteService).getSuiteTypes();
    }
}
