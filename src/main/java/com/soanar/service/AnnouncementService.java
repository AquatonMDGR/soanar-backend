package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.repository.AnnouncementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                                NotificationService notificationService) {
        this.announcementRepository = announcementRepository;
        this.notificationService = notificationService;
    }

    public List<Announcement> listAll() {
        return announcementRepository.findAll();
    }

    public List<Announcement> getPublished() {
        return announcementRepository.findByStatus("PUBLISHED");
    }

    public List<Announcement> getPending() {
        return announcementRepository.findByStatus("PENDING");
    }

    @Transactional
    public Announcement create(Announcement a, User poster) {
        a.setPostedBy(poster);
        String posterRole = poster.getRole();
        
        if ("OSAS".equals(posterRole) || "Academic".equals(posterRole)) {
            a.setStatus("PUBLISHED");
            a.setPublishedAt(Instant.now());
        } else {
            a.setStatus("PENDING");
        }
        
        a.setCreatedAt(Instant.now());
        return announcementRepository.save(a);
    }

    public Optional<Announcement> findById(Long id) {
        return announcementRepository.findById(id);
    }

    @Transactional
    public Announcement approve(Long id) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        a.setStatus("PUBLISHED");
        a.setPublishedAt(Instant.now());
        return announcementRepository.save(a);
    }

    @Transactional
    public Announcement reject(Long id) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        a.setStatus("REJECTED");
        return announcementRepository.save(a);
    }
}
