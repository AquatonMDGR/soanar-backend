package com.soanar.controller;

import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.service.AnnouncementService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
@CrossOrigin(origins = "*")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AnnouncementController(AnnouncementService announcementService, 
                                   UserService userService,
                                   JwtUtil jwtUtil) {
        this.announcementService = announcementService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public List<Announcement> list(@RequestParam(required = false) String status) {
        if ("PUBLISHED".equals(status)) {
            return announcementService.getPublished();
        } else if ("PENDING".equals(status)) {
            return announcementService.getPending();
        }
        return announcementService.listAll();
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Announcement request) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            
            User poster = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            
            Announcement created = announcementService.create(request, poster);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String role = jwtUtil.extractRole(token);
            
            if (!"OSAS".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only OSAS can approve"));
            }
            
            Announcement a = announcementService.approve(id);
            
            // Send notifications in separate transaction
            try {
                announcementService.notifyAfterApproval(id, true);
            } catch (Exception e) {
                System.err.println("Notification failed but approval succeeded: " + e.getMessage());
            }
            
            return ResponseEntity.ok(a);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage(), "type", e.getClass().getName()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String role = jwtUtil.extractRole(token);
            
            if (!"OSAS".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only OSAS can reject"));
            }
            
            Announcement a = announcementService.reject(id);
            
            // Send notifications in separate transaction
            try {
                announcementService.notifyAfterApproval(id, false);
            } catch (Exception e) {
                System.err.println("Notification failed but rejection succeeded: " + e.getMessage());
            }
            
            return ResponseEntity.ok(a);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage(), "type", e.getClass().getName()));
        }
    }
}
