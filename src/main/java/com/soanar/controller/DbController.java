package com.soanar.controller;

import com.soanar.repository.AnnouncementRepository;
import com.soanar.repository.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/db")
@CrossOrigin(origins = "*")
public class DbController {

    private final Environment env;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;

    public DbController(Environment env,
                        UserRepository userRepository,
                        AnnouncementRepository announcementRepository) {
        this.env = env;
        this.userRepository = userRepository;
        this.announcementRepository = announcementRepository;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("time", Instant.now().toString());
        return resp;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> resp = new HashMap<>();
        String url = env.getProperty("spring.datasource.url", "${DB_URL}");
        String user = env.getProperty("spring.datasource.username", "${DB_USER}");

        resp.put("datasourceUrl", url);
        resp.put("datasourceUser", user);
        resp.put("usersCount", safeCountUsers());
        resp.put("announcementsCount", safeCountAnnouncements());
        return resp;
    }

    private long safeCountUsers() {
        try { return userRepository.count(); } catch (Exception e) { return -1; }
    }

    private long safeCountAnnouncements() {
        try { return announcementRepository.count(); } catch (Exception e) { return -1; }
    }
}
