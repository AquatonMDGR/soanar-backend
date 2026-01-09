package com.soanar.controller;

import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String googleToken = request.get("token");
        
        if (googleToken == null || googleToken.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "Token is required"));
        }

        try {
            // Verify token with Google
            RestTemplate restTemplate = new RestTemplate();
            String googleApiUrl = "https://oauth2.googleapis.com/tokeninfo?id_token=" + googleToken;
            Map<String, Object> googleResponse = restTemplate.getForObject(googleApiUrl, Map.class);

            if (googleResponse == null || !googleResponse.containsKey("email")) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid Google token"));
            }

            String email = (String) googleResponse.get("email");
            String name = (String) googleResponse.getOrDefault("name", "");

            // Validate email domain
            if (!email.toLowerCase().endsWith("@iacademy.edu.ph")) {
                return ResponseEntity.status(403).body(Map.of("error", "Only @iacademy.edu.ph emails are allowed"));
            }

            // Get or create user
            String role = "Student";
            var user = userService.findByEmail(email);
            if (user.isPresent()) {
                role = user.get().getRole();
            } else {
                userService.createOrUpdate(email, role, name);
            }

            // Generate JWT
            String token = jwtUtil.generateToken(email, role);

            return ResponseEntity.ok(Map.of(
                "token", token,
                "email", email,
                "role", role,
                "name", name
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @GetMapping("/user/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);

            return ResponseEntity.ok(Map.of(
                "email", email,
                "role", role
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }
}
