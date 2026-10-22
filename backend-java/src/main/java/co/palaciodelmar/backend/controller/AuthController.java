package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.dto.AuthRequest;
import co.palaciodelmar.backend.dto.AuthResponse;
import co.palaciodelmar.backend.dto.RegisterRequest;
import co.palaciodelmar.backend.dto.UserDTO;
import co.palaciodelmar.backend.model.User;
import co.palaciodelmar.backend.security.UserPrincipal;
import co.palaciodelmar.backend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final co.palaciodelmar.backend.security.TokenBlacklistService tokenBlacklistService;

    @org.springframework.beans.factory.annotation.Value("${security.cookie.secure:false}")
    private boolean cookieSecure;

    // SOLO DESARROLLO: permite verificar un correo sin el enlace. Apagado por defecto.
    @org.springframework.beans.factory.annotation.Value("${app.dev.force-verify.enabled:false}")
    private boolean forceVerifyEnabled;

    private void addJwtCookie(jakarta.servlet.http.HttpServletResponse response, String token, jakarta.servlet.http.HttpServletRequest request) {
        boolean isSecure = cookieSecure || (request != null && (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))));
        org.springframework.http.ResponseCookie springCookie = org.springframework.http.ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .maxAge(java.time.Duration.ofDays(7))
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, springCookie.toString());
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request, jakarta.servlet.http.HttpServletResponse httpResponse, jakarta.servlet.http.HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request);
        addJwtCookie(httpResponse, response.getToken(), httpRequest);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Usuario registrado exitosamente");
        // We still send the token in body for backwards compatibility or mobile clients,
        // but the web client will use the cookie.
        body.put("token", response.getToken());
        body.put("user", response.getUser());
        body.put("data", response);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest request, jakarta.servlet.http.HttpServletResponse httpResponse, jakarta.servlet.http.HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request);
        addJwtCookie(httpResponse, response.getToken(), httpRequest);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Inicio de sesión exitoso");
        body.put("token", response.getToken());
        body.put("user", response.getUser());
        body.put("data", response);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(
            @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpServletResponse httpResponse,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        String token = body != null ? body.get("token") : null;
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token es requerido");
        }

        AuthResponse authResponse = authService.verifyEmail(token);
        addJwtCookie(httpResponse, authResponse.getToken(), httpRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Correo verificado exitosamente");
        response.put("token", authResponse.getToken());
        response.put("user", authResponse.getUser());
        response.put("data", authResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email es requerido");
        }

        authService.forgotPassword(email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Si tu correo se encuentra registrado, recibirás una contraseña temporal para acceder.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/force-verify")
    public ResponseEntity<Map<String, Object>> forceVerify(@RequestBody Map<String, String> body) {
        if (!forceVerifyEnabled) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("success", false);
            notFound.put("message", "No encontrado");
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(notFound);
        }
        String email = body != null ? body.get("email") : null;
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email es requerido");
        }

        authService.forceVerify(email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Correo verificado manualmente");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "No autenticado");
            return ResponseEntity.status(401).body(err);
        }
        User user = principal.getUser();
        UserDTO userDTO = authService.toDTO(user);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("user", userDTO);
        body.put("data", Map.of("user", userDTO));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse
    ) {
        String token = extractToken(httpRequest);
        if (token != null && !token.isBlank()) {
            tokenBlacklistService.blacklistToken(token);
        }

        boolean isSecure = cookieSecure || (httpRequest != null && (httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto"))));
        org.springframework.http.ResponseCookie springCookie = org.springframework.http.ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .maxAge(java.time.Duration.ZERO)
                .sameSite("Lax")
                .build();
        httpResponse.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, springCookie.toString());

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Sesión cerrada exitosamente");
        return ResponseEntity.ok(body);
    }

    private String extractToken(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) return null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody User request
    ) {
        UserDTO updated = authService.updateProfile(principal.getUser().getId(), request);
        return ResponseEntity.ok(ApiResponse.ok("Perfil actualizado exitosamente", updated));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse
    ) {
        String currentPassword = body != null ? body.get("currentPassword") : null;
        String newPassword = body != null ? body.get("newPassword") : null;
        authService.changePassword(principal.getUser().getId(), currentPassword, newPassword);

        // Revocar token actual tras cambio exitoso de contraseña
        String token = extractToken(httpRequest);
        if (token != null && !token.isBlank()) {
            tokenBlacklistService.blacklistToken(token);
        }

        boolean isSecure = cookieSecure || (httpRequest != null && (httpRequest.isSecure() || "https".equalsIgnoreCase(httpRequest.getHeader("X-Forwarded-Proto"))));
        org.springframework.http.ResponseCookie springCookie = org.springframework.http.ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .maxAge(java.time.Duration.ZERO)
                .sameSite("Lax")
                .build();
        httpResponse.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, springCookie.toString());

        return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada exitosamente", null));
    }
}
