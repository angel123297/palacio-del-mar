package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BankAccountDTO;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.BookingRepository;
import co.palaciodelmar.backend.repository.PromotionRepository;
import co.palaciodelmar.backend.repository.PropertyRepository;
import co.palaciodelmar.backend.repository.SuiteRepository;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HostBankAccountTest {

    private UserRepository userRepository;
    private HostController hostController;
    private User hostUser;
    private UserPrincipal hostPrincipal;

    @BeforeEach
    void setUp() {
        BookingRepository bookingRepo = mock(BookingRepository.class);
        SuiteRepository suiteRepo = mock(SuiteRepository.class);
        BookingService bookingService = mock(BookingService.class);
        PromotionRepository promotionRepo = mock(PromotionRepository.class);
        PropertyRepository propertyRepo = mock(PropertyRepository.class);
        userRepository = mock(UserRepository.class);

        hostController = new HostController(bookingRepo, suiteRepo, bookingService, promotionRepo, propertyRepo);
        ReflectionTestUtils.setField(hostController, "userRepository", userRepository);

        hostUser = User.builder()
                .id("host-123")
                .name("Carlos")
                .lastName("Host")
                .email("host@palacio.com")
                .role("HOST")
                .build();
        hostPrincipal = new UserPrincipal(hostUser);
    }

    @Test
    @DisplayName("GET /api/host/bank-account: Retorna plantilla por defecto si el anfitrión aún no configuró cuenta")
    void testGetBankAccountDefaultWhenEmpty() {
        when(userRepository.findById("host-123")).thenReturn(Optional.of(hostUser));

        ResponseEntity<ApiResponse<BankAccountDTO>> response = hostController.getBankAccount(hostPrincipal);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(Boolean.TRUE.equals(response.getBody().getSuccess()));
        BankAccountDTO dto = response.getBody().getData();
        assertNotNull(dto);
        assertEquals("Bancolombia", dto.getBankName());
        assertEquals("Ahorros", dto.getAccountType());
        assertTrue(dto.getAccountNumber() == null || dto.getAccountNumber().isBlank());
    }

    @Test
    @DisplayName("PUT /api/host/bank-account: Enmascara y persiste cuenta bancaria sin exponer texto claro")
    void testUpdateBankAccountMasksAndPersists() {
        when(userRepository.findById("host-123")).thenReturn(Optional.of(hostUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountDTO request = BankAccountDTO.builder()
                .bankName("Davivienda")
                .accountType("Ahorros")
                .accountNumber("45098234101")
                .holderName("Carlos Anfitrión SAS")
                .documentId("900123456")
                .documentType("NIT")
                .build();

        ResponseEntity<ApiResponse<BankAccountDTO>> response = hostController.updateBankAccount(hostPrincipal, request);

        assertEquals(200, response.getStatusCode().value());
        BankAccountDTO savedDTO = response.getBody().getData();
        assertNotNull(savedDTO);
        assertEquals("Davivienda", savedDTO.getBankName());
        assertEquals("***4101", savedDTO.getAccountNumberMasked());
        assertEquals("***4101", savedDTO.getAccountNumber());

        // Verificar que en el modelo User se guardó el número real para liquidaciones pero se tiene el enmascarado
        assertNotNull(hostUser.getBankAccount());
        assertEquals("45098234101", hostUser.getBankAccount().getAccountNumber());
        assertEquals("***4101", hostUser.getBankAccount().getAccountNumberMasked());
        verify(userRepository, times(1)).save(hostUser);
    }

    @Test
    @DisplayName("PUT /api/host/bank-account: Si el cliente reenvía el valor enmascarado, no sobreescribe el número real con asteriscos")
    void testUpdateBankAccountPreservesPlaintextWhenMaskedPassedBack() {
        User.BankAccount existing = User.BankAccount.builder()
                .bankName("Bancolombia")
                .accountType("Ahorros")
                .accountNumber("1234567890")
                .accountNumberMasked("***7890")
                .holderName("Carlos")
                .build();
        hostUser.setBankAccount(existing);
        when(userRepository.findById("host-123")).thenReturn(Optional.of(hostUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        BankAccountDTO request = BankAccountDTO.builder()
                .bankName("Bancolombia")
                .accountType("Corriente") // Cambió de ahorros a corriente
                .accountNumber("***7890") // Reenvió el enmascarado
                .holderName("Carlos Actualizado")
                .build();

        ResponseEntity<ApiResponse<BankAccountDTO>> response = hostController.updateBankAccount(hostPrincipal, request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("1234567890", hostUser.getBankAccount().getAccountNumber(), "El número real original debe conservarse intacto");
        assertEquals("Corriente", hostUser.getBankAccount().getAccountType());
        assertEquals("Carlos Actualizado", hostUser.getBankAccount().getHolderName());
    }
}
