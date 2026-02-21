package com.soanar.controller;

import com.soanar.model.Notification;
import com.soanar.model.NotificationPreference;
import com.soanar.service.NotificationService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    public NotificationController(NotificationService notificationService, JwtUtil jwtUtil) {
        this.notificationService = notificationService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<?> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String userEmail = jwtUtil.extractEmail(token);
            
            // Legacy support: return list if page=0 and size=20
            if (page == 0 && size == 20) {
                List<Notification> notifications = notificationService.getNotificationsForUser(userEmail);
                return ResponseEntity.ok(notifications);
            }
            
            // Phase 6: paginated response
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            Page<Notification> notifications = notificationService.getNotificationsForUser(user, page, size);
            
            Map<String, Object> response = new HashMap<>();
            response.put("notifications", notifications.getContent());
            response.put("currentPage", notifications.getNumber());
            response.put("totalPages", notifications.getTotalPages());
            response.put("totalItems", notifications.getTotalElements());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/unread")
    public ResponseEntity<?> getUnreadNotifications(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<Notification> unread = notificationService.getUnreadNotifications(user);
            
            return ResponseEntity.ok(Map.of("notifications", unread, "count", unread.size()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/unread/count")
    public ResponseEntity<?> getUnreadCount(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            long count = notificationService.getUnreadCount(user);
            
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/mark-read")
    public ResponseEntity<?> markAsRead(@RequestBody Map<String, Long> body) {
        Long notificationId = body.get("notificationId");
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsReadById(
            @PathVariable Long notificationId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            notificationService.markNotificationAsRead(notificationId);
            return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllAsRead(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtil.extractEmail(token);
        notificationService.markAllAsRead(email);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsReadNew(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            notificationService.markAllNotificationsAsRead(user);
            
            return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences(
            @RequestParam(defaultValue = "default-org") String organizationId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            NotificationPreference prefs = notificationService.getPreferences(user, organizationId);
            
            return ResponseEntity.ok(prefs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/preferences")
    public ResponseEntity<?> updatePreferences(
            @RequestParam(defaultValue = "default-org") String organizationId,
            @RequestBody NotificationPreference updates,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String userEmail = jwtUtil.extractEmail(token);
            
            var user = userService.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            NotificationPreference prefs = notificationService.updatePreferences(user, organizationId, updates);
            
            return ResponseEntity.ok(prefs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
