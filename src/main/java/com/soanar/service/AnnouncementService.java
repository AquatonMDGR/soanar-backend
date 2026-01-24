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
        return announcementRepository.findByStatusIn(java.util.Arrays.asList("PUBLISHED", "APPROVED"));
    }

    public List<Announcement> getPending() {
        return announcementRepository.findByStatus("PENDING");
    }

    @Transactional
    public Announcement create(Announcement a, User poster) {
        // Set the poster - Spring Data JPA will manage the relationship
        a.setPostedBy(poster);
        String posterRole = poster.getRole();
        
        if ("OSAS".equals(posterRole) || "Academic".equals(posterRole)) {
            a.setStatus("PUBLISHED");
            a.setPublishedAt(Instant.now());
        } else {
            a.setStatus("PENDING");
        }
        
        a.setCreatedAt(Instant.now());
        
        try {
            Announcement saved = announcementRepository.save(a);
            
            // Trigger notifications based on role
            if ("OSAS".equals(posterRole) || "Academic".equals(posterRole)) {
                // OSAS or Academic can post directly -> notify all students
                notificationService.notifyStudentsOfPublishedAnnouncement(saved);
            } else if ("Student Organization".equals(posterRole)) {
                // Student Organization -> notify OSAS for approval
                notificationService.notifyOSASOfNewAnnouncement(saved);
            }
            
            return saved;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create announcement: " + e.getMessage(), e);
        }
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
    
    @Transactional
    public void notifyAfterApproval(Long id, boolean approved) {
        Announcement a = announcementRepository.findById(id).orElseThrow();
        try {
            if (a.getPostedBy() != null) {
                notificationService.notifyApprovalResult(a, approved);
                notificationService.notifyStudentsOfPublishedAnnouncement(a);
            }
        } catch (Exception e) {
            System.err.println("Failed to send notifications for " + (approved ? "approval" : "rejection") + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
