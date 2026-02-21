package com.soanar.controller;

import com.soanar.model.SocialMediaPage;
import com.soanar.model.Announcement;
import com.soanar.service.SocialMediaService;
import com.soanar.service.AnnouncementService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/socmed")
@CrossOrigin(origins = "*")
public class SocialMediaController {

    private final SocialMediaService socialMediaService;
    private final AnnouncementService announcementService;
    private final JwtUtil jwtUtil;

    public SocialMediaController(SocialMediaService socialMediaService,
                                  AnnouncementService announcementService,
                                  JwtUtil jwtUtil) {
        this.socialMediaService = socialMediaService;
        this.announcementService = announcementService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/pages")
    public ResponseEntity<List<SocialMediaPage>> getPages() {
        return ResponseEntity.ok(socialMediaService.getAllPages());
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connectPage(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> body) {
        
        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);
        
        if (!"OSAS".equals(role) && !"Academic".equals(role) && !"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        String platform = body.get("platform");
        String pageName = body.get("pageName");
        String accessToken = body.get("accessToken");
        
        Instant expiresAt = Instant.now().plusSeconds(3600 * 24 * 60);
        SocialMediaPage page = socialMediaService.connectPage(platform, pageName, accessToken, expiresAt);
        
        return ResponseEntity.ok(page);
    }

    @PostMapping("/{id}/post")
    public ResponseEntity<?> postToPage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        
        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);
        
        if (!"OSAS".equals(role) && !"Academic".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        Long announcementId = body.get("announcementId");
        Announcement announcement = announcementService.findById(announcementId)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));

        socialMediaService.postToPage(id, announcement);
        return ResponseEntity.ok(Map.of("message", "Posted successfully"));
    }
}
