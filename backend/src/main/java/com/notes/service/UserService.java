package com.notes.service;

import com.notes.model.User;
import com.notes.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class UserService {

    private static final String USERNAME_REGEX =
            "^[A-Za-z][A-Za-z0-9@_.-]{2,29}$";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(
            String username,
            String email,
            String password) {

        if (username == null || !username.matches(USERNAME_REGEX)) {
            throw new IllegalArgumentException(
                    "Username can contain only letters, numbers, ., @, _, and -, and must start with a letter."
            );
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }

        String normalizedEmail = email.toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException(
                    "This username is already taken. Please choose another username."
            );
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "This email is already registered."
            );
        }

        String passwordHash = passwordEncoder.encode(password);

        User user = new User(
                null,
                username,
                normalizedEmail,
                passwordHash,
                "USER",
                OffsetDateTime.now().toString()
        );

        userRepository.save(user);
    }
}
