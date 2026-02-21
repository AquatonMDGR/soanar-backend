package com.soanar.controller;

import com.soanar.model.OrganizationSettings;
import com.soanar.service.OrganizationSettingsService;
import com.soanar.service.UserService;
import com.soanar.service.AuditLogService;
import com.soanar.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/organization-settings")
public class OrganizationSettingsController {

    @Autowired
    private OrganizationSettingsService settingsService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/{organizationId}")
    public ResponseEntity<?> getSettings(@PathVariable String organizationId, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        // Public endpoint - anyone can view org settings
        return settingsService.getSettingsByOrganizationId(organizationId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{organizationId}")
    public ResponseEntity<?> createOrUpdateSettings(
            @PathVariable String organizationId,
            @RequestBody OrganizationSettings settings,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        
        try {
            String token = authHeader.substring(7);
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Only OSAS can manage organization settings
            if (!"OSAS".equals(user.getRole())) {
                return ResponseEntity.status(403).body("Only OSAS users can manage organization settings");
            }
            
            settings.setOrganizationId(organizationId);
            OrganizationSettings saved = settingsService.createOrUpdateSettings(settings);
            
            // Audit log
            auditLogService.log(user, organizationId, "UPDATE", "ORGANIZATION_SETTINGS", saved.getId(), null, null, "Updated organization settings", request);
            
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{organizationId}")
    public ResponseEntity<?> updateSettings(
            @PathVariable String organizationId,
            @RequestBody OrganizationSettings updates,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest request) {
        
        try {
            String token = authHeader.substring(7);
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Only OSAS can manage organization settings
            if (!"OSAS".equals(user.getRole())) {
                return ResponseEntity.status(403).body("Only OSAS users can manage organization settings");
            }
            
            OrganizationSettings saved = settingsService.updateSettings(organizationId, updates);
            
            // Audit log
            auditLogService.log(user, organizationId, "UPDATE", "ORGANIZATION_SETTINGS", saved.getId(), null, null, "Partial update of organization settings", request);
            
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
