package com.soanar.controller;

import com.soanar.model.User;
import com.soanar.service.AdminService;
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
    private final JwtUtil jwtUtil;

    public AdminController(AdminService adminService, JwtUtil jwtUtil) {
        this.adminService = adminService;
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
        
        adminService.updateUserRole(email, newRole);
        return ResponseEntity.ok(Map.of("message", "Role updated successfully"));
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
