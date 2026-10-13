package com.intelliblog.controller;

import com.intelliblog.model.User;
import com.intelliblog.repository.UserRepository;
import com.intelliblog.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final JwtUtil jwt;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository users, JwtUtil jwt) { this.users = users; this.jwt = jwt; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> b) {
        String u = b.getOrDefault("username", "").trim(), p = b.getOrDefault("password", "");
        if (u.length() < 3 || p.length() < 4)
            return ResponseEntity.badRequest().body(Map.of("error", "Username min 3 characters, password min 4"));
        if (users.findByUsername(u).isPresent())
            return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
        User user = new User();
        user.username = u;
        user.password = encoder.encode(p);
        users.save(user);
        return ResponseEntity.ok(Map.of("message", "Registered"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> b) {
        var found = users.findByUsername(b.getOrDefault("username", "").trim());
        if (found.isEmpty() || !encoder.matches(b.getOrDefault("password", ""), found.get().password))
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        User u = found.get();
        return ResponseEntity.ok(Map.of("token", jwt.generate(u.id, u.username), "username", u.username));
    }
}
