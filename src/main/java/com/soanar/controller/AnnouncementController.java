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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
            @RequestParam(value = "files", required = false) MultipartFile[] files,
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
    @PostMapping(value = "/{id}/crosspost", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crosspost(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "crosspostRequest", required = false) String crosspostRequestJson) {

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

            CrosspostRequest resolvedRequest = null;
            if (crosspostRequestJson != null && !crosspostRequestJson.isBlank()) {
                resolvedRequest = objectMapper.readValue(crosspostRequestJson, CrosspostRequest.class);
            }

            if (resolvedRequest == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing crosspost request"));
            }

            List<MultipartFile> images = new ArrayList<>();
            if (files != null) {
                for (MultipartFile image : files) {
                    if (image != null && !image.isEmpty()) {
                        images.add(image);
                    }
                }
            }
            if (file != null && !file.isEmpty()) {
                images.add(file);
            }

            // Crosspost to selected platforms (user-scoped)
            UUID organizationId = UUID.nameUUIDFromBytes(email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            crosspostService.crosspostAnnouncement(announcement, resolvedRequest, images, organizationId);

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

    /**
     * DEBUG: Check imageUrls in database for specific announcement
     * GET /api/announcements/{id}/debug-images
     */
    @GetMapping("/{id}/debug-images")
    public ResponseEntity<?> debugImages(@PathVariable Long id) {
        try {
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

