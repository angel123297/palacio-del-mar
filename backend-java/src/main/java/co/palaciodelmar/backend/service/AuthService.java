package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.AuthRequest;
import co.palaciodelmar.backend.dto.AuthResponse;
import co.palaciodelmar.backend.dto.RegisterRequest;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.security.JwtService;
import co.palaciodelmar.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        User user = User.builder()
                .name(request.getName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("user")
                .status("active")
                .emailVerified(true)
                .profile(User.Profile.builder()
                        .phone(request.getPhone())
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(user);

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String jwtToken = jwtService.generateToken(userPrincipal);

        // Hide password in response
        user.setPassword(null);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(user)
                .build();
    }

    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Email o contraseña inválidos"));

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String jwtToken = jwtService.generateToken(userPrincipal);

        user.setPassword(null);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(user)
                .build();
    }

    public User updateProfile(String userId, User updateRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (updateRequest.getName() != null) user.setName(updateRequest.getName());
        if (updateRequest.getLastName() != null) user.setLastName(updateRequest.getLastName());
        if (updateRequest.getProfile() != null) {
            if (user.getProfile() == null) user.setProfile(new User.Profile());
            if (updateRequest.getProfile().getPhone() != null) user.getProfile().setPhone(updateRequest.getProfile().getPhone());
            if (updateRequest.getProfile().getDocumentId() != null) user.getProfile().setDocumentId(updateRequest.getProfile().getDocumentId());
            if (updateRequest.getProfile().getDocumentType() != null) user.getProfile().setDocumentType(updateRequest.getProfile().getDocumentType());
            if (updateRequest.getProfile().getAvatar() != null) user.getProfile().setAvatar(updateRequest.getProfile().getAvatar());
            if (updateRequest.getProfile().getAddress() != null) user.getProfile().setAddress(updateRequest.getProfile().getAddress());
        }
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        saved.setPassword(null);
        return saved;
    }

    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }
}
