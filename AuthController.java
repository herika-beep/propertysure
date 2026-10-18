package com.pbl.propertyauth.controller;

import com.pbl.propertyauth.model.AppUser;
import com.pbl.propertyauth.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final BCryptPasswordEncoder encoder =
        new BCryptPasswordEncoder();

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody Map<String, String> body) {

        String name = body.getOrDefault("fullName", "").trim();
        String email = body.getOrDefault("email", "")
            .trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        if (name.isBlank() || email.isBlank()
                || password.length() < 8) {
            return ResponseEntity.badRequest().body(
                Map.of("message",
                    "Enter your name, email and a password of at least 8 characters."));
        }

        if (users.existsByEmailIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(
                Map.of("message", "Email already registered."));
        }

        AppUser user = new AppUser();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        users.save(user);

        return ResponseEntity.ok(
            Map.of("message", "Account created successfully."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> body,
            HttpSession session) {

        String email = body.getOrDefault("email", "")
            .trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        AppUser user = users.findByEmailIgnoreCase(email).orElse(null);

        if (user == null ||
                !encoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(401).body(
                Map.of("message", "Incorrect email or password."));
        }

        session.setAttribute("userId", user.getId());
        session.setAttribute("userName", user.getFullName());

        return ResponseEntity.ok(Map.of(
            "message", "Login successful.",
            "fullName", user.getFullName()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        Object id = session.getAttribute("userId");

        if (id == null) {
            return ResponseEntity.status(401).body(
                Map.of("message", "Please log in."));
        }

        return ResponseEntity.ok(Map.of(
            "id", id,
            "fullName", session.getAttribute("userName")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out."));
    }
}