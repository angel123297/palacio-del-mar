package co.palaciodelmar.backend.service;

import co.palaciodelmar.backend.dto.AuthRequest;
import co.palaciodelmar.backend.dto.AuthResponse;
import co.palaciodelmar.backend.dto.RegisterRequest;
import co.palaciodelmar.backend.dto.UserDTO;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.repository.UserRepository;
import co.palaciodelmar.backend.security.JwtService;
import co.palaciodelmar.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private co.palaciodelmar.backend.security.UserSessionCacheService userSessionCacheService;

    public void setUserSessionCacheService(co.palaciodelmar.backend.security.UserSessionCacheService userSessionCacheService) {
        this.userSessionCacheService = userSessionCacheService;
    }

    @Autowired
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager,
                       EmailService emailService,
                       MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.mongoTemplate = mongoTemplate;
    }

    private String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : randomBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public String hashToken(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            User existing = userRepository.findByEmail(normalizedEmail).orElse(null);
            if (existing != null && (existing.getPassword() == null || existing.getPassword().isBlank())) {
                String rawToken = generateSecureToken();
                String hashedToken = hashToken(rawToken);

                existing.setName(request.getName());
                if (request.getLastName() != null) {
                    existing.setLastName(request.getLastName());
                }
                existing.setPassword(passwordEncoder.encode(request.getPassword()));
                existing.setEmailVerified(false);
                existing.setVerificationTokenHash(hashedToken);
                existing.setVerificationTokenExpiresAt(Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS));
                if (existing.getProfile() == null) {
                    existing.setProfile(User.Profile.builder().phone(request.getPhone()).build());
                } else if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
                    existing.getProfile().setPhone(request.getPhone());
                }
                existing.setUpdatedAt(Instant.now());
                userRepository.save(existing);
                if (userSessionCacheService != null) {
                    userSessionCacheService.evictUser(existing.getEmail());
                }

                try {
                    if (request.getReturnUrl() != null && !request.getReturnUrl().isBlank()) {
                        emailService.sendVerificationEmail(existing.getEmail(), rawToken, request.getReturnUrl());
                    } else {
                        emailService.sendVerificationEmail(existing.getEmail(), rawToken);
                    }
                } catch (Exception e) {
                    log.error("Failed to send verification email: {}", e.getMessage(), e);
                }

                return AuthResponse.builder()
                        .token(null)
                        .user(toDTO(existing))
                        .build();
            } else {
                throw new IllegalArgumentException("El email ya está registrado");
            }
        }
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty() && userRepository.existsByProfilePhone(request.getPhone())) {
            throw new IllegalArgumentException("El número de teléfono ya está registrado");
        }

        String assignedRole = "user";
        if (request.getRole() != null && !request.getRole().trim().isEmpty()) {
            String requested = request.getRole().trim();
            if ("admin".equalsIgnoreCase(requested)) {
                throw new IllegalArgumentException("No se permite el autorregistro con rol de administrador");
            }
            if ("host".equalsIgnoreCase(requested)) {
                assignedRole = "host";
            }
        }

        String rawToken = generateSecureToken();
        String hashedToken = hashToken(rawToken);

        User user = User.builder()
                .name(request.getName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .status("active")
                .emailVerified(false)
                .verificationTokenHash(hashedToken)
                .verificationTokenExpiresAt(Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS))
                .profile(User.Profile.builder()
                        .phone(request.getPhone())
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        userRepository.save(user);

        try {
            if (request.getReturnUrl() != null && !request.getReturnUrl().isBlank()) {
                emailService.sendVerificationEmail(user.getEmail(), rawToken, request.getReturnUrl());
            } else {
                emailService.sendVerificationEmail(user.getEmail(), rawToken);
            }
        } catch (Exception e) {
            // Log error, but don't fail registration
            log.error("Failed to send verification email: {}", e.getMessage(), e);
        }

        // No se entrega token hasta verificar el correo
        return AuthResponse.builder()
                .token(null)
                .user(toDTO(user))
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

        // Bloqueo de seguridad: exigir verificación de email (a menos que sea rol admin)
        if (!Boolean.TRUE.equals(user.getEmailVerified()) && !"admin".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("Debes verificar tu correo electrónico antes de iniciar sesión. Por favor revisa tu bandeja de entrada.");
        }

        UserPrincipal userPrincipal = new UserPrincipal(user);
        String jwtToken = jwtService.generateToken(userPrincipal);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(toDTO(user))
                .build();
    }

    public UserDTO updateProfile(String userId, User updateRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (updateRequest.getName() != null) user.setName(updateRequest.getName());
        if (updateRequest.getLastName() != null) user.setLastName(updateRequest.getLastName());
        if (updateRequest.getProfile() != null) {
            if (user.getProfile() == null) user.setProfile(new User.Profile());
            if (updateRequest.getProfile().getPhone() != null) {
                String newPhone = updateRequest.getProfile().getPhone();
                if (!newPhone.trim().isEmpty() && !newPhone.equals(user.getProfile().getPhone())) {
                    if (userRepository.existsByProfilePhone(newPhone)) {
                        throw new IllegalArgumentException("El número de teléfono ya está registrado por otro usuario");
                    }
                }
                user.getProfile().setPhone(newPhone);
            }
            if (updateRequest.getProfile().getDocumentId() != null) user.getProfile().setDocumentId(updateRequest.getProfile().getDocumentId());
            if (updateRequest.getProfile().getDocumentType() != null) user.getProfile().setDocumentType(updateRequest.getProfile().getDocumentType());
            if (updateRequest.getProfile().getAvatar() != null) user.getProfile().setAvatar(updateRequest.getProfile().getAvatar());
            if (updateRequest.getProfile().getAddress() != null) user.getProfile().setAddress(updateRequest.getProfile().getAddress());
        }
        user.setUpdatedAt(Instant.now());
        User saved = userRepository.save(user);
        if (userSessionCacheService != null) {
            userSessionCacheService.putCachedUser(saved);
        }
        return toDTO(saved);
    }

    public AuthResponse verifyEmail(String token) {
        String hashedToken = hashToken(token);
        User user = userRepository.findByVerificationTokenHash(hashedToken)
                .orElseThrow(() -> new IllegalArgumentException("Token inválido"));

        if (user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("El token ha expirado");
        }

        user.setEmailVerified(true);
        user.setVerificationTokenHash(null);
        user.setVerificationTokenExpiresAt(null);
        user.setUpdatedAt(Instant.now());
        User savedUser = userRepository.save(user);
        if (userSessionCacheService != null) {
            userSessionCacheService.putCachedUser(savedUser);
        }

        UserPrincipal userPrincipal = new UserPrincipal(savedUser);
        String jwtToken = jwtService.generateToken(userPrincipal);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(toDTO(savedUser))
                .build();
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
        Instant changedAt = Instant.now();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(changedAt);
        user.setSessionsRevokedAt(changedAt);
        user.setUpdatedAt(changedAt);
        userRepository.save(user);
        if (userSessionCacheService != null) {
            userSessionCacheService.recordGlobalRevocation(user.getEmail(), changedAt);
        }
    }

    /**
     * Cierra instantáneamente todas las sesiones activas del usuario en todos los dispositivos
     * combinando persistencia durable en MongoDB (sessionsRevokedAt) y marca de revocación en RAM/Redis (< 0.2 ms).
     */
    public void logoutAllDevices(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        Instant now = Instant.now();
        user.setSessionsRevokedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        if (userSessionCacheService != null) {
            userSessionCacheService.recordGlobalRevocation(user.getEmail(), now);
        }
        log.info("Revocación global de sesiones ejecutada en todos los dispositivos para {}", user.getEmail());
    }

    public void forgotPassword(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email es requerido");
        }
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            log.info("Solicitud de forgot-password para correo no registrado: {}", normalizedEmail);
            return;
        }

        // Flujo seguro y no destructivo: NO altera password actual ni emailVerified.
        // Genera token criptográficamente seguro con validez de 15 minutos almacenado con hash SHA-256.
        String rawToken = generateSecureToken();
        String hashedToken = hashToken(rawToken);

        user.setResetPasswordTokenHash(hashedToken);
        user.setResetPasswordTokenExpiresAt(Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        log.info("Enlace de restablecimiento de contraseña generado y enviado a {}", normalizedEmail);
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token de restablecimiento es requerido");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres");
        }

        String hashedToken = hashToken(token.trim());
        Instant now = Instant.now();

        // Operación 100% atómica con MongoTemplate para garantizar el consumo único e indivisible del token
        Query query = new Query(Criteria.where("resetPasswordTokenHash").is(hashedToken)
                .and("resetPasswordTokenExpiresAt").gt(now));

        Update update = new Update()
                .set("password", passwordEncoder.encode(newPassword))
                .set("passwordChangedAt", now)
                .set("sessionsRevokedAt", now)
                .set("updatedAt", now)
                .unset("resetPasswordTokenHash")
                .unset("resetPasswordTokenExpiresAt");

        User updatedUser = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                User.class
        );

        if (updatedUser == null) {
            // Si la actualización atómica no encontró coincidencia, verificamos si el token existía pero ya expiró
            java.util.Optional<User> expiredUser = userRepository.findByResetPasswordTokenHash(hashedToken);
            if (expiredUser.isPresent()) {
                throw new IllegalArgumentException("El enlace de restablecimiento ha expirado. Por favor solicita uno nuevo.");
            }
            throw new IllegalArgumentException("Enlace de restablecimiento inválido o ya utilizado");
        }

        if (userSessionCacheService != null) {
            userSessionCacheService.recordGlobalRevocation(updatedUser.getEmail(), now);
        }

        log.info("Contraseña restablecida exitosamente de forma atómica para el usuario {}", updatedUser.getEmail());
    }

    public void resendVerification(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email es requerido");
        }
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            log.info("Solicitud de reenvío de verificación para correo no registrado: {}", normalizedEmail);
            return;
        }

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("Tu cuenta ya se encuentra verificada. Puedes iniciar sesión directamente.");
        }

        String rawToken = generateSecureToken();
        String hashedToken = hashToken(rawToken);

        user.setVerificationTokenHash(hashedToken);
        user.setVerificationTokenExpiresAt(Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), rawToken);
        log.info("Nuevo correo de verificación reenviado exitosamente a {}", normalizedEmail);
    }


    public UserDTO toDTO(User user) {
        if (user == null) return null;
        return UserDTO.builder()
                .id(user.getId())
                ._id(user.getId())
                .name(user.getName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .branches(user.getBranches())
                .status(user.getStatus())
                .emailVerified(user.getEmailVerified())
                .profile(user.getProfile())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
