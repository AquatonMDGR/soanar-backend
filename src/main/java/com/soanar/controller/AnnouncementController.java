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
<<<<<<< Updated upstream
            @RequestBody AnnouncementRequest request) {
=======
<<<<<<< Updated upstream
            @RequestParam(value = "file", required = false) MultipartFile file,
=======
<<<<<<< Updated upstream
            @RequestBody AnnouncementRequest request) {
=======
            @RequestParam(value = "files", required = false) MultipartFile[] files,
>>>>>>> Stashed changes
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
<<<<<<< Updated upstream
=======
>>>>>>> Stashed changes
>>>>>>> Stashed changes
>>>>>>> Stashed changes
        
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
            
<<<<<<< Updated upstream
=======
<<<<<<< Updated upstream
            // Upload file to Supabase storage if provided
            if (file != null && !file.isEmpty()) {
                try {
                    String fileUrl = announcementService.uploadToSupabase(file, "Announcement-Media-Bucket", "Media-Files");
                    announcement.setImageUrl(fileUrl);
                } catch (Exception e) {
                    System.err.println("Warning: File upload failed, continuing without image: " + e.getMessage());
                    e.printStackTrace();
                    // Continue without image instead of failing the entire request
=======
<<<<<<< Updated upstream
>>>>>>> Stashed changes
            // Set distribution groups if provided
            if (request.getDistributionGroupIds() != null && !request.getDistributionGroupIds().isEmpty()) {
                Set<DistributionGroup> groups = new HashSet<>();
                for (Long groupId : request.getDistributionGroupIds()) {
                    distributionGroupRepository.findById(groupId).ifPresent(groups::add);
<<<<<<< Updated upstream
=======
=======
            // Upload files to Supabase storage if provided
            if (files != null && files.length > 0) {
                // Limit to 10 images max
                if (files.length > 10) {
                    return ResponseEntity.status(400).body(Map.of("error", "Maximum 10 images per announcement"));
                }
                
                List<String> fileUrls = announcementService.uploadMultipleToSupabase(files, "Announcement-Media-Bucket", "Media-Files");
                
                if (!fileUrls.isEmpty()) {
                    announcement.setImageUrl(fileUrls.get(0)); // Backward compatibility
                    announcement.setImageUrls(fileUrls); // Store all URLs
>>>>>>> Stashed changes
>>>>>>> Stashed changes
>>>>>>> Stashed changes
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
