
package com.notes.controller;

import com.notes.model.User;
import com.notes.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/setup")
public class AdminSetupController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin-setup.enabled:false}")
    private boolean setupEnabled;

    @Value("${app.admin-setup.secret:}")
    private String setupSecret;

    public AdminSetupController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/admin")
    public ResponseEntity<Map<String, String>> createInitialAdmin(
            @RequestHeader(
                value = "X-Setup-Secret",
                required = false
            ) String suppliedSecret,
            @RequestBody AdminSetupRequest request) {

        if (!setupEnabled || setupSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Setup is disabled."));
        }

        if (suppliedSecret == null
                || !java.security.MessageDigest.isEqual(
                    suppliedSecret.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    ),
                    setupSecret.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    ))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Invalid setup secret."));
        }

        if (userRepository.hasAdmin()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "An admin account already exists."));
        }

        if (request == null
                || request.username() == null
                || !request.username().matches(
                    "^[A-Za-z][A-Za-z0-9@_.-]{2,29}$")
                || request.email() == null
                || !request.email().matches(
                    "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || request.password() == null
                || request.password().length() < 12
                || request.password().length() > 128) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "error",
                    "Provide a valid username, email, and a password of 12–128 characters."
                ));
        }

        String username = request.username();
        String email = request.email().trim().toLowerCase(
            java.util.Locale.ROOT
        );

        if (userRepository.existsByUsername(username)
                || userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                    "error",
                    "The username or email is already registered."
                ));
        }

        User admin = new User(
            null,
            username,
            email,
            passwordEncoder.encode(request.password()),
            "ADMIN",
            OffsetDateTime.now().toString()
        );

        userRepository.save(admin);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "message",
                "Initial admin account created successfully."
            ));
    }

    public record AdminSetupRequest(
        String username,
        String email,
        String password
    ) {}
}
