package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.AuthRequest;
import co.palaciodelmar.backend.dto.AuthResponse;
import co.palaciodelmar.backend.dto.RegisterRequest;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .name("Juan")
                .lastName("Pérez")
                .email("juan.perez@example.com")
                .password("password123")
                .phone("3001234567")
                .build();

        testUser = User.builder()
                .id("user-123")
                .name("Juan")
                .lastName("Pérez")
                .email("juan.perez@example.com")
                .password("hashedPassword")
                .role("user")
                .emailVerified(false)
                .build();
    }

    @Test
    void registerSavesUserWithUnverifiedEmailAndDoesNotReturnToken() {
        when(userRepository.existsByEmail("juan.perez@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");

        AuthResponse response = authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertNotNull(saved);
        assertEquals("user", saved.getRole(), "Por defecto el rol debe ser user");
        // Se verifica que emailVerified sea consistente con la configuracion del servicio
        assertNotNull(saved.getEmailVerified());
        assertNotNull(saved.getVerificationTokenHash(), "Debe guardarse el hash del token de verificación");
        assertNotNull(saved.getVerificationTokenExpiresAt());
        assertNull(response.getToken(), "No debe entregarse un token JWT hasta que se verifique el correo");
        assertNotNull(response.getUser());
        assertEquals("juan.perez@example.com", response.getUser().getEmail());
        verify(emailService).sendVerificationEmail(eq("juan.perez@example.com"), any());
    }

    @Test
    void registerAllowsHostRole() {
        when(userRepository.existsByEmail("juan.perez@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");

        registerRequest.setRole("host");
        authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertNotNull(saved);
        assertEquals("host", saved.getRole(), "Debe permitir el registro explícito con rol host");
    }

    @Test
    void registerRejectsShortPassword() {
        registerRequest.setPassword("short");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        assertTrue(ex.getMessage().contains("al menos 8 caracteres"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsAdminRole() {
        when(userRepository.existsByEmail("juan.perez@example.com")).thenReturn(false);

        registerRequest.setRole("admin");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        assertTrue(ex.getMessage().contains("administrador"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginThrowsIllegalStateExceptionIfEmailNotVerified() {
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));

        AuthRequest loginRequest = AuthRequest.builder()
                .email("juan.perez@example.com")
                .password("password123")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.login(loginRequest));
        assertTrue(ex.getMessage().contains("verificar tu correo electrónico"));
    }

    @Test
    void loginThrowsIllegalStateExceptionIfHostEmailNotVerified() {
        testUser.setRole("host");
        testUser.setEmailVerified(false);
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));

        AuthRequest loginRequest = AuthRequest.builder()
                .email("juan.perez@example.com")
                .password("password123")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.login(loginRequest));
        assertTrue(ex.getMessage().contains("verificar tu correo electrónico"));
    }

    @Test
    void loginSucceedsWhenEmailIsVerified() {
        testUser.setEmailVerified(true);
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any())).thenReturn("valid.jwt.token");

        AuthRequest loginRequest = AuthRequest.builder()
                .email("juan.perez@example.com")
                .password("password123")
                .build();

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response.getToken());
        assertEquals("valid.jwt.token", response.getToken());
        assertEquals("juan.perez@example.com", response.getUser().getEmail());
    }

    @Test
    void loginAllowedForAdminEvenIfEmailNotExplicitlyVerified() {
        testUser.setRole("admin");
        testUser.setEmailVerified(false);
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any())).thenReturn("admin.jwt.token");

        AuthRequest loginRequest = AuthRequest.builder()
                .email("juan.perez@example.com")
                .password("password123")
                .build();

        AuthResponse response = authService.login(loginRequest);
        assertNotNull(response.getToken());
    }

    @Test
    void verifyEmailSetsEmailVerifiedToTrueAndClearsToken() {
        String token = "my-secret-token";
        String hashedToken = authService.hashToken(token);

        testUser.setVerificationTokenHash(hashedToken);
        testUser.setVerificationTokenExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));

        when(userRepository.findByVerificationTokenHash(hashedToken)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any())).thenReturn("mock-jwt-token");

        AuthResponse res = authService.verifyEmail(token);

        assertNotNull(res);
        assertEquals("mock-jwt-token", res.getToken());
        assertTrue(testUser.getEmailVerified());
        assertNull(testUser.getVerificationTokenHash());
        assertNull(testUser.getVerificationTokenExpiresAt());
        verify(userRepository).save(testUser);
    }

    @Test
    void verifyEmailFailsForExpiredToken() {
        String token = "expired-token";
        String hashedToken = authService.hashToken(token);

        testUser.setVerificationTokenHash(hashedToken);
        testUser.setVerificationTokenExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(userRepository.findByVerificationTokenHash(hashedToken)).thenReturn(Optional.of(testUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail(token));
        assertTrue(ex.getMessage().contains("expirado"));
    }

    @Test
    void verifyEmailFailsForInvalidToken() {
        when(userRepository.findByVerificationTokenHash(any())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail("invalid-token"));
        assertTrue(ex.getMessage().contains("inválido"));
    }

    @Test
    void forgotPasswordGeneratesSecureTokenAndSendsEmailWithoutModifyingPasswordOrVerification() {
        testUser.setEmailVerified(false);
        String originalPassword = testUser.getPassword();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        authService.forgotPassword("TEST@example.com");

        // NO debe verificar el email automáticamente
        assertFalse(testUser.getEmailVerified());
        // NO debe sobreescribir la contraseña actual
        assertEquals(originalPassword, testUser.getPassword());
        // Debe generar token y fecha de expiración
        assertNotNull(testUser.getResetPasswordTokenHash());
        assertNotNull(testUser.getResetPasswordTokenExpiresAt());
        assertTrue(testUser.getResetPasswordTokenExpiresAt().isAfter(java.time.Instant.now()));

        verify(userRepository).save(testUser);
        verify(emailService).sendPasswordResetEmail(eq(testUser.getEmail()), anyString());
    }

    @Test
    void forgotPasswordHandlesUnknownEmailGracefully() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword("unknown@example.com"));

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void resetPasswordAtomicSuccessUpdatesPasswordAndRevokesTokens() {
        String rawToken = "my-secure-token-123";
        testUser.setResetPasswordTokenHash(authService.hashToken(rawToken));
        testUser.setResetPasswordTokenExpiresAt(java.time.Instant.now().plusSeconds(600));

        when(passwordEncoder.encode("newPassword123")).thenReturn("hashedNewPassword");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(testUser);

        assertDoesNotThrow(() -> authService.resetPassword(rawToken, "newPassword123"));

        verify(mongoTemplate).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class));
    }

    @Test
    void resetPasswordAtomicPreventsConcurrentRaceCondition() {
        String rawToken = "race-token-123";
        // Simula que una solicitud concurrente ya consumió el token en MongoDB
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(null);
        when(userRepository.findByResetPasswordTokenHash(anyString())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(rawToken, "newPassword123"));
        assertTrue(ex.getMessage().contains("inválido o ya utilizado"));
    }

    @Test
    void resetPasswordAtomicFailsWhenTokenExpired() {
        String rawToken = "expired-token-123";
        testUser.setResetPasswordTokenHash(authService.hashToken(rawToken));
        testUser.setResetPasswordTokenExpiresAt(java.time.Instant.now().minusSeconds(10));

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(null);
        when(userRepository.findByResetPasswordTokenHash(anyString())).thenReturn(Optional.of(testUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword(rawToken, "newPassword123"));
        assertTrue(ex.getMessage().contains("expirado"));
    }

    @Test
    void resetPasswordFailsWhenTokenInvalid() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(User.class)))
                .thenReturn(null);
        when(userRepository.findByResetPasswordTokenHash(anyString())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword("nonexistent-token", "newPassword123"));
        assertTrue(ex.getMessage().contains("inválido"));
    }

    @Test
    void resetPasswordFailsWhenPasswordTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.resetPassword("valid-token", "123"));
    }

    @Test
    void resendVerificationRegeneratesTokenAndSendsEmailWhenUserUnverified() {
        testUser.setEmailVerified(false);
        String oldHash = "old-hash-12345";
        testUser.setVerificationTokenHash(oldHash);
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        authService.resendVerification("juan.perez@example.com");

        assertNotNull(testUser.getVerificationTokenHash());
        assertNotEquals(oldHash, testUser.getVerificationTokenHash(), "El hash del token anterior debe ser invalidado y reemplazado");
        assertNotNull(testUser.getVerificationTokenExpiresAt());
        verify(userRepository).save(testUser);
        verify(emailService).sendVerificationEmail(eq("juan.perez@example.com"), anyString());
    }

    @Test
    void resendVerificationThrowsIllegalStateExceptionIfUserAlreadyVerified() {
        testUser.setEmailVerified(true);
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> authService.resendVerification("juan.perez@example.com"));
        assertTrue(ex.getMessage().contains("ya se encuentra verificada"));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void resendVerificationHandlesUnknownEmailGracefully() {
        when(userRepository.findByEmail("desconocido@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.resendVerification("desconocido@example.com"));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void registerAllowsClaimingStubUserWithoutPassword() {
        testUser.setPassword(null);
        testUser.setEmailVerified(false);
        String oldHash = "old-hash-99999";
        testUser.setVerificationTokenHash(oldHash);

        when(userRepository.existsByEmail("juan.perez@example.com")).thenReturn(true);
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        registerRequest.setPassword("newSecretPassword123");
        AuthResponse res = authService.register(registerRequest);

        assertNotNull(res);
        assertNull(res.getToken());
        assertNotEquals(oldHash, testUser.getVerificationTokenHash());
        verify(emailService).sendVerificationEmail(eq("juan.perez@example.com"), anyString());
        verify(userRepository).save(testUser);
    }

    @Test
    void registerRejectsWhenUserExistsWithPasswordEvenIfUnverified() {
        testUser.setEmailVerified(false);
        testUser.setPassword("existingHashedPassword");
        when(userRepository.existsByEmail("juan.perez@example.com")).thenReturn(true);
        when(userRepository.findByEmail("juan.perez@example.com")).thenReturn(Optional.of(testUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(registerRequest));
        assertEquals("El email ya está registrado", ex.getMessage());
        verify(userRepository, never()).save(any());
    }
}
