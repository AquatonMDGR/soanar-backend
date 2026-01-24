package com.soanar.controller;

import com.soanar.dto.AnnouncementRequest;
import com.soanar.model.Announcement;
import com.soanar.model.DistributionGroup;
import com.soanar.model.User;
import com.soanar.repository.DistributionGroupRepository;
import com.soanar.service.AnnouncementService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/announcements")
@CrossOrigin(origins = "*")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final DistributionGroupRepository distributionGroupRepository;

    public AnnouncementController(AnnouncementService announcementService, 
                                   UserService userService,
                                   JwtUtil jwtUtil,
                                   DistributionGroupRepository distributionGroupRepository) {
        this.announcementService = announcementService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.distributionGroupRepository = distributionGroupRepository;
    }

    @GetMapping
    public List<Announcement> list(@RequestParam(required = false) String status) {
        if ("PUBLISHED".equals(status) || "APPROVED".equals(status)) {
            return announcementService.getPublished();
        } else if ("PENDING".equals(status)) {
            return announcementService.getPending();
        }
        return announcementService.listAll();
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AnnouncementRequest request) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            
            User poster = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            
            // Create announcement entity from request
            Announcement announcement = new Announcement();
            announcement.setTitle(request.getTitle());
            announcement.setDescription(request.getDescription());
            announcement.setImageUrl(request.getImageUrl());
            
            // Set distribution groups if provided
            if (request.getDistributionGroupIds() != null && !request.getDistributionGroupIds().isEmpty()) {
                Set<DistributionGroup> groups = new HashSet<>();
                for (Long groupId : request.getDistributionGroupIds()) {
                    distributionGroupRepository.findById(groupId).ifPresent(groups::add);
                }
                announcement.setDistributionGroups(groups);
            }
            
            Announcement created = announcementService.create(announcement, poster);
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
