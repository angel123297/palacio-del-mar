package com.palaciodelmar.controller;

import com.palaciodelmar.model.User;
import com.palaciodelmar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(cleanEmail)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("El correo ya se encuentra registrado.");
        }

        User newUser = new User();
        newUser.setEmail(cleanEmail);
        newUser.setPassword(request.getPassword());
        userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body("Usuario registrado exitosamente.");
    }
}

class AuthRequest {
    private String email;
    private String password;
    public String getEmail() { return email; }
    public String getPassword() { return password; }
}
