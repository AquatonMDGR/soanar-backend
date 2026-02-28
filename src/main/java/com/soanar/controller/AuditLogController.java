package com.soanar.controller;

import com.soanar.model.AuditLog;
import com.soanar.service.AuditLogService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<?> getOrganizationLogs(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Only OSAS or Super Admin can view audit logs
            if (!"OSAS".equals(user.getRole()) && !"Super Admin".equals(user.getRole())) {
                return ResponseEntity.status(403).body("Only OSAS or Super Admin users can view audit logs");
            }
            
            Page<AuditLog> logs = auditLogService.getLogsByOrganization(organizationId, page, size);
            
            Map<String, Object> response = new HashMap<>();
            response.put("logs", logs.getContent());
            response.put("currentPage", logs.getNumber());
            response.put("totalPages", logs.getTotalPages());
            response.put("totalItems", logs.getTotalElements());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            Page<AuditLog> logs = auditLogService.getLogsByUser(user, page, size);
            
            Map<String, Object> response = new HashMap<>();
            response.put("logs", logs.getContent());
            response.put("currentPage", logs.getNumber());
            response.put("totalPages", logs.getTotalPages());
            response.put("totalItems", logs.getTotalElements());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<?> getEntityLogs(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Only OSAS or Super Admin can view entity audit logs
            if (!"OSAS".equals(user.getRole()) && !"Super Admin".equals(user.getRole())) {
                return ResponseEntity.status(403).body("Only OSAS or Super Admin users can view audit logs");
            }
            
            Page<AuditLog> logs = auditLogService.getLogsByEntity(entityType, entityId, page, size);
            
            Map<String, Object> response = new HashMap<>();
            response.put("logs", logs.getContent());
            response.put("currentPage", logs.getNumber());
            response.put("totalPages", logs.getTotalPages());
            response.put("totalItems", logs.getTotalElements());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/analytics/{organizationId}")
    public ResponseEntity<?> getAnalytics(
            @PathVariable String organizationId,
            @RequestParam(defaultValue = "30") int days,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.substring(7);
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Only OSAS or Super Admin can view analytics
            if (!"OSAS".equals(user.getRole()) && !"Super Admin".equals(user.getRole())) {
                return ResponseEntity.status(403).body("Only OSAS or Super Admin users can view analytics");
            }
            
            Instant startDate = Instant.now().minus(days, ChronoUnit.DAYS);
            Instant endDate = Instant.now();
            
            List<AuditLog> logs = auditLogService.getLogsInTimeRange(organizationId, startDate, endDate);
            
            // Calculate statistics
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("totalActions", logs.size());
            analytics.put("timeRange", Map.of("start", startDate, "end", endDate, "days", days));
            
            // Group by action type
            Map<String, Long> actionCounts = new HashMap<>();
            for (AuditLog log : logs) {
                actionCounts.merge(log.getActionType(), 1L, Long::sum);
            }
            analytics.put("actionCounts", actionCounts);
            
            // Group by entity type
            Map<String, Long> entityCounts = new HashMap<>();
            for (AuditLog log : logs) {
                entityCounts.merge(log.getEntityType(), 1L, Long::sum);
            }
            analytics.put("entityCounts", entityCounts);
            
            // Top users
            Map<String, Long> userCounts = new HashMap<>();
            for (AuditLog log : logs) {
                if (log.getUser() != null) {
                    String userName = log.getUser().getName();
                    userCounts.merge(userName, 1L, Long::sum);
                }
            }
            analytics.put("topUsers", userCounts);
            
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
