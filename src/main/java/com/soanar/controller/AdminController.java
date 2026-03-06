package com.soanar.controller;

import com.soanar.model.User;
import com.soanar.service.AdminService;
import com.soanar.service.AuditLogService;
import com.soanar.service.OrganizationSettingsService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final OrganizationSettingsService organizationSettingsService;
    private final JwtUtil jwtUtil;

    public AdminController(AdminService adminService,
                           UserService userService,
                           AuditLogService auditLogService,
                           OrganizationSettingsService organizationSettingsService,
                           JwtUtil jwtUtil) {
        this.adminService = adminService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.organizationSettingsService = organizationSettingsService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);
        
        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        List<User> users = adminService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/roles")
    public ResponseEntity<?> updateUserRole(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        
        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);
        
        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        String email = body.get("email");
        String newRole = body.get("role");

        try {
            String actorEmail = jwtUtil.extractEmail(token);
            User actor = userService.findByEmail(actorEmail)
                .orElseThrow(() -> new RuntimeException("Acting user not found"));
            User targetBefore = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

            String oldRole = targetBefore.getRole();
            User updatedUser = adminService.updateUserRole(email, newRole);

            String organizationId = organizationSettingsService.resolveDefaultOrganizationId();
            auditLogService.log(
                actor,
                organizationId,
                "UPDATE",
                "USER",
                updatedUser.getId(),
                "role=" + oldRole,
                "role=" + updatedUser.getRole(),
                "Updated role for " + updatedUser.getSchoolEmail(),
                null
            );
            return ResponseEntity.ok(Map.of("message", "Role updated successfully"));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/users/status")
    public ResponseEntity<?> updateUserStatus(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {

        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);

        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        String email = body.get("email") != null ? body.get("email").toString() : null;
        Object isActiveRaw = body.get("isActive");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }
        if (isActiveRaw == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "isActive is required"));
        }
        boolean isActive = Boolean.parseBoolean(String.valueOf(isActiveRaw));

        try {
            String actorEmail = jwtUtil.extractEmail(token);
            User actor = userService.findByEmail(actorEmail)
                .orElseThrow(() -> new RuntimeException("Acting user not found"));
            User targetBefore = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

            Boolean oldValue = targetBefore.getIsActive();
            User updatedUser = adminService.updateUserActiveStatus(email, isActive);

            String organizationId = organizationSettingsService.resolveDefaultOrganizationId();
            auditLogService.log(
                actor,
                organizationId,
                "UPDATE",
                "USER",
                updatedUser.getId(),
                "isActive=" + oldValue,
                "isActive=" + updatedUser.getIsActive(),
                (isActive ? "Activated" : "Deactivated") + " user " + updatedUser.getSchoolEmail(),
                null
            );
            return ResponseEntity.ok(Map.of("message", "User status updated successfully"));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getAuditLogs(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);
        
        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        return ResponseEntity.ok(adminService.getAuditLogs());
    }
}
