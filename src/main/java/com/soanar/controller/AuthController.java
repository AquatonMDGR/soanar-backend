package com.soanar.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.soanar.service.UserService;
import com.soanar.service.OrganizationSettingsService;
import com.soanar.util.JwtUtil;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;
    private final OrganizationSettingsService organizationSettingsService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, OrganizationSettingsService organizationSettingsService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.organizationSettingsService = organizationSettingsService;
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
            @SuppressWarnings("unchecked")
            Map<String, Object> googleResponse = restTemplate.getForObject(googleApiUrl, Map.class);

            if (googleResponse == null || !googleResponse.containsKey("email")) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid Google token"));
            }

            String email = (String) googleResponse.get("email");
            String name = (String) googleResponse.getOrDefault("name", "");
            String picture = (String) googleResponse.getOrDefault("picture", "");

            // Email domain validation - only @iacademy.edu.ph emails allowed
            if (!email.toLowerCase().endsWith("@iacademy.edu.ph")) {
                return ResponseEntity.status(403).body(Map.of("error", "Only @iacademy.edu.ph emails are allowed"));
            }

            // Get or create user
            String role = "Student";
            var user = userService.findByEmail(email);
            String resolvedPhotoUrl = picture;
            if (user.isPresent()) {
                if (Boolean.FALSE.equals(user.get().getIsActive())) {
                    return ResponseEntity.status(403).body(Map.of("error", "Your account has been deactivated. Please contact an administrator."));
                }
                role = user.get().getRole();
                if (resolvedPhotoUrl == null || resolvedPhotoUrl.isBlank()) {
                    resolvedPhotoUrl = user.get().getPhotoUrl();
                }
            } else {
                role = "Student";
            }

            // Keep profile data synced with latest Google account metadata.
            var savedUser = userService.createOrUpdate(email, role, name, resolvedPhotoUrl);

            // Generate JWT
            String token = jwtUtil.generateToken(email, role);
            String organizationId = organizationSettingsService.resolveDefaultOrganizationId();

            return ResponseEntity.ok(Map.of(
                "token", token,
                "email", email,
                "role", role,
                "name", name,
                "picture", savedUser.getPhotoUrl(),
                "photoUrl", savedUser.getPhotoUrl(),
                "organizationId", organizationId
            ));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid Google token: " + e.getMessage()));
        } catch (org.springframework.web.client.RestClientException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to verify token with Google: " + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Login failed: " + e.getMessage()));
        }
    }

    @GetMapping("/user/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            String organizationId = organizationSettingsService.resolveDefaultOrganizationId();

            var existingUser = userService.findByEmail(email).orElse(null);
            if (existingUser != null && Boolean.FALSE.equals(existingUser.getIsActive())) {
                return ResponseEntity.status(403).body(Map.of("error", "Your account has been deactivated. Please contact an administrator."));
            }
            String name = existingUser != null ? existingUser.getName() : "";
            String photoUrl = existingUser != null ? existingUser.getPhotoUrl() : "";

            return ResponseEntity.ok(Map.of(
                "email", email,
                "role", role,
                "name", name,
                "photoUrl", photoUrl,
                "organizationId", organizationId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }
}
