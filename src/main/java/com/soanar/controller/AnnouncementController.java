package com.soanar.controller;

import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.model.DistributionGroup;
import com.soanar.dto.TargetingRequest;
import com.soanar.service.AnnouncementService;
import com.soanar.service.UserService;
import com.soanar.repository.DistributionGroupRepository;
import com.soanar.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
@CrossOrigin(origins = "*")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final DistributionGroupRepository distributionGroupRepository;

    public AnnouncementController(AnnouncementService announcementService, 
                                   UserService userService,
                                   JwtUtil jwtUtil,
                                   ObjectMapper objectMapper,
                                   DistributionGroupRepository distributionGroupRepository) {
        this.announcementService = announcementService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.distributionGroupRepository = distributionGroupRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User currentUser = null;
            String role = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.replace("Bearer ", "");
                String email = jwtUtil.extractEmail(token);
                currentUser = userService.findByEmail(email).orElse(null);
                role = currentUser != null ? currentUser.getRole() : jwtUtil.extractRole(token);
            }

            if ("PUBLISHED".equals(status) || "APPROVED".equals(status)) {
                if (currentUser != null && "Student".equals(role)) {
                    return ResponseEntity.ok(announcementService.getPublishedForUser(currentUser));
                }
                return ResponseEntity.ok(announcementService.getPublished());
            }

            if ("PENDING".equals(status)) {
                if (!"OSAS".equals(role) && !"Academic".equals(role) && !"Super Admin".equals(role)) {
                    return ResponseEntity.status(403).body(Map.of("error", "Not authorized to view pending announcements"));
                }
                return ResponseEntity.ok(announcementService.getPending());
            }

            if ("OSAS".equals(role) || "Academic".equals(role) || "Super Admin".equals(role)) {
                return ResponseEntity.ok(announcementService.listAll());
            }

            if (currentUser != null && "Student".equals(role)) {
                return ResponseEntity.ok(announcementService.getPublishedForUser(currentUser));
            }

            return ResponseEntity.ok(announcementService.getPublished());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "targeting", required = false) String targeting) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            
            User poster = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            
            // Create announcement entity
            Announcement announcement = new Announcement();
            announcement.setTitle(title);
            announcement.setDescription(description);
            
            // Upload files to Supabase storage if provided
            if (files != null && files.length > 0) {
                // Limit to 10 images max
                if (files.length > 10) {
                    return ResponseEntity.status(400).body(Map.of("error", "Maximum 10 images per announcement"));
                }
                
                System.out.println("DEBUG Controller: Uploading " + files.length + " files to Supabase");
                List<String> fileUrls = announcementService.uploadMultipleToSupabase(files, "Announcement-Media-Bucket", "Media-Files");
                System.out.println("DEBUG Controller: Got " + fileUrls.size() + " URLs back");
                for (int i = 0; i < fileUrls.size(); i++) {
                    System.out.println("DEBUG Controller: fileUrls[" + i + "] = " + fileUrls.get(i));
                }
                
                if (!fileUrls.isEmpty()) {
                    announcement.setImageUrl(fileUrls.get(0)); // Backward compatibility
                    announcement.setImageUrls(fileUrls); // Store all URLs
                    System.out.println("DEBUG Controller: Set imageUrl = " + fileUrls.get(0));
                    System.out.println("DEBUG Controller: Set imageUrls with " + fileUrls.size() + " items");
                }
            }
            
            // Map dates if provided
            if (startDate != null && !startDate.isBlank()) {
                announcement.setStartDate(java.time.LocalDate.parse(startDate));
            }
            if (endDate != null && !endDate.isBlank()) {
                announcement.setEndDate(java.time.LocalDate.parse(endDate));
            }

            if (targeting != null && !targeting.isBlank()) {
                TargetingRequest targetingRequest = objectMapper.readValue(targeting, TargetingRequest.class);
                announcement.setTargetYearLevels(targetingRequest.getYearLevels());
                announcement.setTargetSchools(targetingRequest.getSchools());
                announcement.setTargetManualEmails(targetingRequest.getManualEmails());

                if (targetingRequest.getDistributionGroupIds() != null && !targetingRequest.getDistributionGroupIds().isEmpty()) {
                    List<DistributionGroup> groups = distributionGroupRepository.findAllById(targetingRequest.getDistributionGroupIds());
                    announcement.setDistributionGroups(new HashSet<>(groups));
                }
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
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String role = jwtUtil.extractRole(token);
            String email = jwtUtil.extractEmail(token);
            
            if (!"OSAS".equals(role) && !"Super Admin".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only OSAS or Super Admin can approve"));
            }
            
            User approver = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String notes = (payload != null && payload.containsKey("notes")) ? payload.get("notes") : "";
            Announcement a = announcementService.approve(id, approver, notes);
            
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
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String role = jwtUtil.extractRole(token);
            String email = jwtUtil.extractEmail(token);
            
            if (!"OSAS".equals(role) && !"Super Admin".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only OSAS or Super Admin can reject"));
            }
            
            User rejector = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String reason = (payload != null && payload.containsKey("reason")) ? payload.get("reason") : "";
            Announcement a = announcementService.reject(id, rejector, reason);
            
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            
            // Only the poster or OSAS can delete
            Announcement announcement = announcementService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Announcement not found"));
            
            User poster = announcement.getPostedBy();
            if (poster == null || (!poster.getSchoolEmail().equals(email) && !"OSAS".equals(role))) {
                return ResponseEntity.status(403).body(Map.of("error", "Only the poster or OSAS can delete this announcement"));
            }
            
            announcementService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Announcement deleted successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get public announcement (no authentication required)
     * GET /api/announcements/public/{id}
     * Used for: AddToAny social sharing
     */
    @GetMapping("/public/{id}")
    public ResponseEntity<?> getPublicAnnouncement(@PathVariable Long id) {
        try {
            Announcement announcement = announcementService.findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));
            
            // Only return published announcements
            if (!"PUBLISHED".equals(announcement.getStatus()) && !"APPROVED".equals(announcement.getStatus())) {
                return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
            }
            
            return ResponseEntity.ok(announcement);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "Announcement not found"));
        }
    }

    /**
     * DEBUG: Check imageUrls in database for specific announcement
     * GET /api/announcements/{id}/debug-images
     */
    @GetMapping("/{id}/debug-images")
    public ResponseEntity<?> debugImages(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String role = jwtUtil.extractRole(token);
            if (!"OSAS".equals(role) && !"Super Admin".equals(role)) {
                return ResponseEntity.status(403).body(Map.of("error", "Only OSAS or Super Admin can access debug images"));
            }

            Announcement a = announcementService.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
            
            System.out.println("DEBUG /debug-images: Announcement ID " + id);
            System.out.println("DEBUG /debug-images: imageUrl = " + a.getImageUrl());
            System.out.println("DEBUG /debug-images: imageUrls = " + a.getImageUrls());
            System.out.println("DEBUG /debug-images: imageUrls size = " + (a.getImageUrls() != null ? a.getImageUrls().size() : "NULL"));
            
            return ResponseEntity.ok(Map.of(
                    "id", a.getId(),
                    "title", a.getTitle(),
                    "imageUrl", a.getImageUrl(),
                    "imageUrls", a.getImageUrls(),
                    "imageUrlsSize", a.getImageUrls() != null ? a.getImageUrls().size() : 0
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

