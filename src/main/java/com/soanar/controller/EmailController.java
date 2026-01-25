package com.soanar.controller;

import com.soanar.service.EmailService;
import com.soanar.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

        @SuppressWarnings("unchecked")
        List<String> recipients = (List<String>) body.get("recipients");
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
        
        if (!"OSAS".equals(role) && !"Academic".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        }
@SuppressWarnings("unchecked")
        
        List<String> recipients = (List<String>) body.get("recipients");
        String subject = (String) body.get("subject");
        String emailBody = (String) body.get("body");

        emailService.sendTermlyNewsletter(recipients, subject, emailBody);
        return ResponseEntity.ok(Map.of("message", "Newsletter sent successfully"));
    }
}
