package com.soanar.controller;

import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.repository.AnnouncementRepository;
import com.soanar.repository.UserRepository;
import com.soanar.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

@RestController
@RequestMapping("/api/dev")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "dev.mode", havingValue = "true")
public class DevDataController {

    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;

    public DevDataController(UserRepository userRepository,
                             AnnouncementRepository announcementRepository,
                             NotificationService notificationService) {
        this.userRepository = userRepository;
        this.announcementRepository = announcementRepository;
        this.notificationService = notificationService;
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "");
        String role = body.getOrDefault("role", "Student");
        String name = body.getOrDefault("name", "Unnamed");

        Map<String, Object> resp = new HashMap<>();
        if (email.isBlank()) {
            resp.put("error", "email is required");
            return resp;
        }

        Optional<User> existing = userRepository.findBySchoolEmail(email);
        if (existing.isPresent()) {
            resp.put("id", existing.get().getId());
            resp.put("message", "user already exists");
            return resp;
        }

        User u = new User(email, role, name);
        User saved = userRepository.save(u);
        resp.put("id", saved.getId());
        resp.put("message", "user created");
        return resp;
    }

    @PostMapping("/announcements")
    public Map<String, Object> createAnnouncement(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "");
        String description = body.getOrDefault("description", "");
        String posterEmail = body.getOrDefault("postedByEmail", "");
        String status = body.getOrDefault("status", "PENDING");

        Map<String, Object> resp = new HashMap<>();
        if (title.isBlank() || posterEmail.isBlank()) {
            resp.put("error", "title and postedByEmail are required");
            return resp;
        }

        Optional<User> poster = userRepository.findBySchoolEmail(posterEmail);
        if (poster.isEmpty()) {
            resp.put("error", "postedByEmail not found: " + posterEmail);
            return resp;
        }

        Announcement a = new Announcement();
        a.setTitle(title);
        a.setDescription(description);
        a.setPostedBy(poster.get());
        a.setStatus(status);
        Announcement saved = announcementRepository.save(a);

        resp.put("id", saved.getId());
        resp.put("message", "announcement created");
        return resp;
    }

    /**
     * Backfill notifications for already-approved/published announcements (e.g., after CSV import).
     * Dev-only: guarded by dev.mode=true
     */
    @PostMapping("/backfill-notifications")
    public Map<String, Object> backfillNotifications() {
        var statuses = java.util.Arrays.asList("PUBLISHED", "APPROVED");
        var announcements = announcementRepository.findByStatusIn(statuses);

        int created = 0;
        for (Announcement a : announcements) {
            if (a.getPublishedAt() != null) {
                try {
                    notificationService.notifyStudentsOfPublishedAnnouncement(a);
                    created++;
                } catch (Exception e) {
                    // continue with next
                }
            }
        }

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("processed", announcements.size());
        resp.put("created", created);
        return resp;
    }

    @PostMapping("/login")
    public Map<String, Object> devLogin(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "demo@iacademy.edu.ph");
        String role = body.getOrDefault("role", "Student");
        String name = body.getOrDefault("name", "Demo User");

        // Create or get user
        Optional<User> existing = userRepository.findBySchoolEmail(email);
        if (existing.isEmpty()) {
            User u = new User(email, role, name);
            userRepository.save(u);
        } else {
            role = existing.get().getRole();
            name = existing.get().getName();
        }

        // Generate JWT token
        com.soanar.util.JwtUtil jwtUtil = new com.soanar.util.JwtUtil();
        String token = jwtUtil.generateToken(email, role);

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("email", email);
        resp.put("role", role);
        resp.put("name", name);
        return resp;
    }
}
