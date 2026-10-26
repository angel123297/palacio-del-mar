package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.AuthResponse;
import co.palaciodelmar.backend.dto.RegisterRequest;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ============================================================================
 * PRUEBAS UNITARIAS: Caso de Uso "CrearUsuario" (TDD / Clean Architecture)
 * Tecnologías evaluadas: JUnit 5, Mockito, AssertJ
 * ============================================================================
 * Esta clase prueba de forma aislada la lógica de negocio al crear un nuevo
 * usuario en el sistema, asegurando que:
 * 1. Se valide la unicidad del email y teléfono.
 * 2. La contraseña sea encriptada mediante PasswordEncoder (BCrypt).
 * 3. Se persista el usuario en la base de datos con estado activo y rol de usuario.
 * 4. Se envíe el correo de verificación vía EmailService.
 * 5. Se apliquen aserciones fluidas y expresivas con AssertJ (assertThat).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Caso de Uso: CrearUsuario (JUnit 5 + Mockito + AssertJ)")
class CrearUsuarioUnitTest {

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

    @InjectMocks
    private AuthService authService;

    private RegisterRequest requestValido;

    @BeforeEach
    void setUp() {
        requestValido = RegisterRequest.builder()
                .name("Carlos")
                .lastName("Mendoza")
                .email("carlos.mendoza@palacio.co")
                .password("Secreto#2026")
                .phone("3009876543")
                .build();
    }

    @Test
    @DisplayName("Debe crear un usuario exitosamente cuando los datos son válidos")
    void deberiaCrearUsuarioExitosamenteCuandoDatosSonValidos() {
        // GIVEN (Dado): El email y teléfono no están registrados en el sistema
        when(userRepository.existsByEmail("carlos.mendoza@palacio.co")).thenReturn(false);
        when(userRepository.existsByProfilePhone("3009876543")).thenReturn(false);
        when(passwordEncoder.encode("Secreto#2026")).thenReturn("$2a$10$hashSeguroBCryptEjemplo");

        // WHEN (Cuando): Se ejecuta el registro de usuario
        AuthResponse respuesta = authService.register(requestValido);

        // THEN (Entonces): Validamos interacción con mocks y estado persistido usando AssertJ
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User usuarioGuardado = userCaptor.getValue();
        assertThat(usuarioGuardado).isNotNull();
        assertThat(usuarioGuardado.getEmail()).isEqualTo("carlos.mendoza@palacio.co");
        assertThat(usuarioGuardado.getName()).isEqualTo("Carlos");
        assertThat(usuarioGuardado.getLastName()).isEqualTo("Mendoza");
        assertThat(usuarioGuardado.getPassword()).isEqualTo("$2a$10$hashSeguroBCryptEjemplo");
        assertThat(usuarioGuardado.getRole()).isEqualTo("user");
        assertThat(usuarioGuardado.getStatus()).isEqualTo("active");
        assertThat(usuarioGuardado.getEmailVerified()).isFalse();
        assertThat(usuarioGuardado.getVerificationTokenHash()).isNotBlank();
        assertThat(usuarioGuardado.getProfile().getPhone()).isEqualTo("3009876543");

        // Verificamos que se despachó el correo de confirmación
        verify(emailService, times(1)).sendVerificationEmail(eq("carlos.mendoza@palacio.co"), anyString());

        // Verificamos la respuesta retornada
        assertThat(respuesta).isNotNull();
        assertThat(respuesta.getUser()).isNotNull();
        assertThat(respuesta.getUser().getEmail()).isEqualTo("carlos.mendoza@palacio.co");
    }

    @Test
    @DisplayName("Debe lanzar excepción y no guardar cuando el email ya está registrado")
    void deberiaLanzarExcepcionCuandoEmailYaExiste() {
        // GIVEN: El email ya existe y está verificado
        User usuarioExistente = User.builder()
                .email("carlos.mendoza@palacio.co")
                .password("$2a$10$hashPrevio")
                .emailVerified(true)
                .build();
        when(userRepository.existsByEmail("carlos.mendoza@palacio.co")).thenReturn(true);
        when(userRepository.findByEmail("carlos.mendoza@palacio.co")).thenReturn(Optional.of(usuarioExistente));

        // WHEN & THEN: AssertJ valida que se lance IllegalArgumentException con mensaje esperado
        assertThatThrownBy(() -> authService.register(requestValido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El email ya está registrado");

        // Verificamos que NUNCA se guardó un nuevo usuario ni se envió email
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el número de teléfono ya está registrado")
    void deberiaLanzarExcepcionCuandoTelefonoYaExiste() {
        // GIVEN: Email libre pero teléfono duplicado en la base de datos
        when(userRepository.existsByEmail("carlos.mendoza@palacio.co")).thenReturn(false);
        when(userRepository.existsByProfilePhone("3009876543")).thenReturn(true);

        // WHEN & THEN
        assertThatThrownBy(() -> authService.register(requestValido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El número de teléfono ya está registrado");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Debe normalizar el email a minúsculas al registrar el nuevo usuario")
    void deberiaNormalizarEmailAMinusculasAlRegistrar() {
        // GIVEN: Request con email en mayúsculas
        RegisterRequest requestMayusculas = RegisterRequest.builder()
                .name("Ana")
                .lastName("Gómez")
                .email("ANA.GOMEZ@HOTELPALACIO.COM")
                .password("Password123*")
                .phone("3110009988")
                .build();

        when(userRepository.existsByEmail("ana.gomez@hotelpalacio.com")).thenReturn(false);
        when(userRepository.existsByProfilePhone("3110009988")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$mockEncoded");

        // WHEN
        authService.register(requestMayusculas);

        // THEN: Capturamos el usuario y validamos que el email guardado sea 100% minúscula
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ana.gomez@hotelpalacio.com");
    }
}
