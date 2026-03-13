package com.soanar.controller;

import com.soanar.service.EmailService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/emails")
@CrossOrigin(origins = "*")
public class EmailController {

    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    public EmailController(EmailService emailService, 
                           JwtUtil jwtUtil) {
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/send-targeted")
    public ResponseEntity<?> sendTargetedEmail(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        
        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);
        
        if (!"OSAS".equals(role) && !"Academic".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        List<String> recipients = extractRecipients(body);
        String subject = (String) body.get("subject");
        String emailBody = (String) body.get("body");

        emailService.sendTargetedEmail(recipients, subject, emailBody);
        return ResponseEntity.ok(Map.of("message", "Emails sent successfully"));
    }

    @PostMapping("/send-termly")
    public ResponseEntity<?> sendTermlyNewsletter(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> body) {
        
        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);
        
        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        List<String> recipients = extractRecipients(body);
        String subject = (String) body.get("subject");
        String emailBody = (String) body.get("body");

        emailService.sendTermlyNewsletter(recipients, subject, emailBody);
        return ResponseEntity.ok(Map.of("message", "Newsletter sent successfully"));
    }

    @PostMapping("/send-termly-upcoming")
    public ResponseEntity<?> forceSendUpcomingTermNewsletter(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);

        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        try {
            Map<String, Object> result = emailService.forceSendUpcomingTermNewsletter();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/term-coverage")
    public ResponseEntity<?> getTermCoverageSettings(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);

        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        return ResponseEntity.ok(emailService.getTermCoverageSettings());
    }

    @PutMapping("/term-coverage")
    public ResponseEntity<?> updateTermCoverageSettings(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> payload) {

        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);

        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        try {
            return ResponseEntity.ok(emailService.updateTermCoverageSettings(payload));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/preview-termly-upcoming")
    public ResponseEntity<?> previewUpcomingTermNewsletter(
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.replace("Bearer ", "");
        String role = jwtUtil.extractRole(token);

        if (!"Super Admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }

        try {
            Map<String, Object> result = emailService.getUpcomingTermNewsletterPreview();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    private List<String> extractRecipients(Map<String, Object> body) {
        Object recipientsValue = body.get("recipients");
        if (recipientsValue instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }
}
