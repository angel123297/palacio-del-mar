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

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request, jakarta.servlet.http.HttpServletResponse httpResponse) {
        AuthResponse response = authService.register(request);
        
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt", response.getToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Should be true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        httpResponse.addCookie(cookie);

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
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest request, jakarta.servlet.http.HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request);
        
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt", response.getToken());
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Should be true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        httpResponse.addCookie(cookie);

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Inicio de sesión exitoso");
        body.put("token", response.getToken());
        body.put("user", response.getUser());
        body.put("data", response);
        return ResponseEntity.ok(body);
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
    public ResponseEntity<Map<String, Object>> logout(jakarta.servlet.http.HttpServletResponse httpResponse) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0); // Clear cookie
        httpResponse.addCookie(cookie);
        
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("message", "Sesión cerrada");
        return ResponseEntity.ok(body);
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
            @RequestBody Map<String, String> body
    ) {
        String currentPassword = body != null ? body.get("currentPassword") : null;
        String newPassword = body != null ? body.get("newPassword") : null;
        authService.changePassword(principal.getUser().getId(), currentPassword, newPassword);
        return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada exitosamente", null));
    }
}
