package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.PromotionDTO;
import co.palaciodelmar.backend.dto.PromotionRequest;
import co.palaciodelmar.backend.model.Property;
import co.palaciodelmar.backend.model.Promotion;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HostPromotionControllerTest {

    private BookingRepository bookingRepository;
    private SuiteRepository suiteRepository;
    private BookingService bookingService;
    private PromotionRepository promotionRepository;
    private PropertyRepository propertyRepository;

    private HostController hostController;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        suiteRepository = mock(SuiteRepository.class);
        bookingService = mock(BookingService.class);
        promotionRepository = mock(PromotionRepository.class);
        propertyRepository = mock(PropertyRepository.class);

        hostController = new HostController(
                bookingRepository,
                suiteRepository,
                bookingService,
                promotionRepository,
                propertyRepository
        );
    }

    @Test
    @DisplayName("Host puede crear promoción para una propiedad que posee")
    void hostCanCreatePromotionForOwnedProperty() {
        User hostUser = User.builder()
                .id("host-1")
                .role("HOST")
                .branches(List.of())
                .build();
        UserPrincipal principal = new UserPrincipal(hostUser);

        Property ownedProp = Property.builder()
                .id("prop-mine")
                .ownerId("host-1")
                .name("Mi Hotel")
                .build();
        when(propertyRepository.findByOwnerId("host-1")).thenReturn(List.of(ownedProp));

        PromotionRequest req = PromotionRequest.builder()
                .title("Descuento Verano")
                .branchId("prop-mine")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .discountPercent(15)
                .build();

        when(promotionRepository.save(any(Promotion.class))).thenAnswer(i -> {
            Promotion p = i.getArgument(0);
            p.setId("promo-1");
            return p;
        });

        ResponseEntity<ApiResponse<PromotionDTO>> response = hostController.createPromotion(principal, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Descuento Verano", response.getBody().getData().getTitle());
        assertEquals("prop-mine", response.getBody().getData().getBranch());
    }

    @Test
    @DisplayName("Anti-IDOR: Host no puede crear promoción para propiedad ajena (retorna 403)")
    void hostCannotCreatePromotionForOtherHostProperty() {
        User hostUser = User.builder()
                .id("host-1")
                .role("HOST")
                .branches(List.of())
                .build();
        UserPrincipal principal = new UserPrincipal(hostUser);

        when(propertyRepository.findByOwnerId("host-1")).thenReturn(List.of());

        PromotionRequest req = PromotionRequest.builder()
                .title("Descuento Hack")
                .branchId("prop-other")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .discountPercent(15)
                .build();

        ResponseEntity<ApiResponse<PromotionDTO>> response = hostController.createPromotion(principal, req);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Host puede listar las promociones de sus propiedades")
    void hostCanListPromotionsOfOwnedProperties() {
        User hostUser = User.builder()
                .id("host-1")
                .role("HOST")
                .branches(List.of("branch-1"))
                .build();
        UserPrincipal principal = new UserPrincipal(hostUser);

        Property ownedProp = Property.builder()
                .id("prop-mine")
                .ownerId("host-1")
                .name("Mi Hotel")
                .build();
        when(propertyRepository.findByOwnerId("host-1")).thenReturn(List.of(ownedProp));

        Promotion promo1 = Promotion.builder()
                .id("promo-1")
                .title("Promo 1")
                .branch("branch-1")
                .discountPercent(10)
                .build();

        Promotion promo2 = Promotion.builder()
                .id("promo-2")
                .title("Promo 2")
                .branch("prop-mine")
                .discountPercent(20)
                .build();

        when(promotionRepository.findByBranchIn(anyList())).thenReturn(List.of(promo1, promo2));

        ResponseEntity<ApiResponse<List<PromotionDTO>>> response = hostController.getPromotions(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<PromotionDTO> promotions = response.getBody().getData();
        assertEquals(2, promotions.size());
        assertEquals("Promo 1", promotions.get(0).getTitle());
        assertEquals("Promo 2", promotions.get(1).getTitle());
    }

    @Test
    @DisplayName("Host no puede crear promoción con fecha fin anterior a fecha inicio (retorna 400)")
    void hostCannotCreatePromotionWithInvalidDates() {
        User hostUser = User.builder()
                .id("host-1")
                .role("HOST")
                .branches(List.of("branch-1"))
                .build();
        UserPrincipal principal = new UserPrincipal(hostUser);

        PromotionRequest req = PromotionRequest.builder()
                .title("Oferta Error")
                .branchId("branch-1")
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(2))
                .discountPercent(10)
                .build();

        ResponseEntity<ApiResponse<PromotionDTO>> response = hostController.createPromotion(principal, req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(promotionRepository, never()).save(any());
    }
}
