package com.soanar.controller;

import com.soanar.dto.CrosspostRequest;
import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.service.AnnouncementService;
import com.soanar.service.CrosspostService;
import com.soanar.service.UserService;
import com.soanar.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
@CrossOrigin(origins = "*")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final CrosspostService crosspostService;
    private final ObjectMapper objectMapper;

    public AnnouncementController(AnnouncementService announcementService, 
                                   UserService userService,
                                   JwtUtil jwtUtil,
                                   CrosspostService crosspostService,
                                   ObjectMapper objectMapper) {
        this.announcementService = announcementService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.crosspostService = crosspostService;
        this.objectMapper = objectMapper;
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
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            
            User poster = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found: " + email));
            
            // Create announcement entity
            Announcement announcement = new Announcement();
            announcement.setTitle(title);
            announcement.setDescription(description);
            
            // Upload file to Supabase storage if provided
            if (file != null && !file.isEmpty()) {
                try {
                    String fileUrl = announcementService.uploadToSupabase(file, "Announcement-Media-Bucket", "Media-Files");
                    announcement.setImageUrl(fileUrl);
                } catch (Exception e) {
                    System.err.println("Warning: File upload failed, continuing without image: " + e.getMessage());
                    e.printStackTrace();
                    // Continue without image instead of failing the entire request
                }
            }
            
            // Map dates if provided
            if (startDate != null && !startDate.isBlank()) {
                announcement.setStartDate(java.time.LocalDate.parse(startDate));
            }
            if (endDate != null && !endDate.isBlank()) {
                announcement.setEndDate(java.time.LocalDate.parse(endDate));
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
            String email = jwtUtil.extractEmail(token);
            
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
     * Crosspost an announcement to Facebook and/or Instagram
     * POST /api/announcements/{id}/crosspost
     */
    @PostMapping("/{id}/crosspost")
    public ResponseEntity<?> crosspost(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "crosspostRequest", required = false) String crosspostRequestJson,
            @RequestBody(required = false) CrosspostRequest request) {

        try {
            String token = authHeader.replace("Bearer ", "");
            String role = jwtUtil.extractRole(token);
            String email = jwtUtil.extractEmail(token);

                // Only Student Organization, OSAS, or Academic can crosspost
            Announcement announcement = announcementService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Announcement not found"));

                boolean isAuthorized = "Student Organization".equals(role)
                    || "OSAS".equals(role)
                    || "Academic".equals(role);

            if (!isAuthorized) {
                return ResponseEntity.status(403).body(Map.of("error", "Not authorized to crosspost"));
            }

            CrosspostRequest resolvedRequest = request;
            if (resolvedRequest == null && crosspostRequestJson != null && !crosspostRequestJson.isBlank()) {
                resolvedRequest = objectMapper.readValue(crosspostRequestJson, CrosspostRequest.class);
            }

            if (resolvedRequest == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing crosspost request"));
            }

            // Crosspost to selected platforms (user-scoped)
            UUID organizationId = UUID.nameUUIDFromBytes(email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            crosspostService.crosspostAnnouncement(announcement, resolvedRequest, file, organizationId);

            return ResponseEntity.ok(Map.of(
                    "message", "Crossposting initiated",
                    "announcementId", id
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get crossposting status for an announcement
     * GET /api/announcements/{id}/crosspost-status
     */
    @GetMapping("/{id}/crosspost-status")
    public ResponseEntity<?> getCrosspostStatus(
            @PathVariable Long id) {

        try {
            List<?> posts = crosspostService.getPostsForAnnouncement(id);
            return ResponseEntity.ok(Map.of(
                    "announcementId", id,
                    "crosspostStatus", posts
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
