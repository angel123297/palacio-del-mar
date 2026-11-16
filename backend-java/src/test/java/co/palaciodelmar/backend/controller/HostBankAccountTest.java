package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.BankAccountDTO;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostBankAccountTest {

    private UserRepository userRepository;
    private HostController hostController;
    private User hostUser;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        hostController = new HostController(null, null, null, null, null);
        ReflectionTestUtils.setField(hostController, "userRepository", userRepository);

        hostUser = User.builder()
                .id("host-id-1")
                .email("host@example.com")
                .name("Anfitrión")
                .role("host")
                .build();
        principal = new UserPrincipal(hostUser);

        when(userRepository.findById("host-id-1")).thenReturn(Optional.of(hostUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("B7: updateBankAccount rechaza cuenta enmascarada (***) si no existe cuenta previa")
    void updateBankAccountRejectsMaskedWhenNoPriorAccount() {
        hostUser.setBankAccount(null);

        BankAccountDTO request = BankAccountDTO.builder()
                .bankName("Bancolombia")
                .accountType("Ahorros")
                .accountNumber("***1234")
                .holderName("Pedro Perez")
                .documentId("12345678")
                .build();

        ResponseEntity<ApiResponse<BankAccountDTO>> response = hostController.updateBankAccount(principal, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Boolean.FALSE, response.getBody().getSuccess());
        assertTrue(response.getBody().getMessage().contains("No se puede registrar una cuenta bancaria con un valor enmascarado"));
    }

    @Test
    @DisplayName("B7: updateBankAccount preserva cuenta real cuando se reenvía enmascarada si ya existía")
    void updateBankAccountPreservesRealAccountWhenMaskedResubmitted() {
        hostUser.setBankAccount(User.BankAccount.builder()
                .bankName("Bancolombia")
                .accountType("Ahorros")
                .accountNumber("9876543210")
                .accountNumberMasked("***3210")
                .holderName("Pedro Perez")
                .build());

        BankAccountDTO request = BankAccountDTO.builder()
                .bankName("Davivienda")
                .accountType("Corriente")
                .accountNumber("***3210")
                .holderName("Pedro Perez Actualizado")
                .documentId("12345678")
                .build();

        ResponseEntity<ApiResponse<BankAccountDTO>> response = hostController.updateBankAccount(principal, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Boolean.TRUE, response.getBody().getSuccess());
        assertEquals("9876543210", hostUser.getBankAccount().getAccountNumber(), "Debe preservar el número real sin alterarlo a ***");
        assertEquals("Davivienda", hostUser.getBankAccount().getBankName());
    }
}
