package com.soanar.service;

import com.soanar.model.Notification;
import com.soanar.model.Announcement;
import com.soanar.model.User;
import com.soanar.repository.NotificationRepository;
import com.soanar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void createNotification(Announcement announcement, String recipientEmail, String type, String title, String message) {
        Notification notification = new Notification(announcement, recipientEmail, type, title, message);
        notificationRepository.save(notification);
    }

    @Transactional
    public void createNotification(Announcement announcement, String recipientEmail) {
        Notification notification = new Notification();
        notification.setAnnouncement(announcement);
        notification.setRecipientEmail(recipientEmail);
        notification.setCreatedAt(Instant.now());
        notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsForUser(String email) {
        return notificationRepository.findByRecipientEmail(email);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        });
    }

    @Transactional
    public void markAllAsRead(String email) {
        notificationRepository.markAllAsReadForUser(email, Instant.now());
    }

    @Transactional
    public void notifyDistributionGroup(Announcement announcement, List<String> emails) {
        for (String email : emails) {
            createNotification(announcement, email);
        }
    }

    /**
     * Notify all students when an announcement is published
     */
    @Transactional
    public void notifyStudentsOfPublishedAnnouncement(Announcement announcement) {
        List<User> students = userRepository.findByRole("Student");
        for (User student : students) {
            createNotification(
                announcement,
                student.getSchoolEmail(),
                "announcement",
                "New Announcement: " + announcement.getTitle(),
                announcement.getDescription()
            );
        }
    }

    /**
     * Notify OSAS when a Student Organization creates an announcement
     */
    @Transactional
    public void notifyOSASOfNewAnnouncement(Announcement announcement) {
        List<User> osasUsers = userRepository.findByRole("OSAS");
        for (User osasUser : osasUsers) {
            createNotification(
                announcement,
                osasUser.getSchoolEmail(),
                "approval",
                "New Announcement Awaiting Approval",
                "Student Organization '" + announcement.getPostedBy().getName() + "' has submitted an announcement: \"" + announcement.getTitle() + "\" for approval."
            );
        }
    }

    /**
     * Notify Student Organization when their announcement is approved/rejected
     */
    @Transactional
    public void notifyApprovalResult(Announcement announcement, boolean approved) {
        if (announcement.getPostedBy() == null) {
            System.err.println("Cannot notify approval result: postedBy is null for announcement " + announcement.getId());
            return;
        }
        
        String recipientEmail = announcement.getPostedBy().getSchoolEmail();
        if (recipientEmail == null || recipientEmail.isEmpty()) {
            System.err.println("Cannot notify approval result: recipientEmail is null or empty for user " + announcement.getPostedBy().getId());
            return;
        }
        
        String type = approved ? "approval" : "rejection";
        String title = approved ? "Announcement Approved ✓" : "Announcement Rejected ✗";
        String message = approved 
            ? "Your announcement \"" + announcement.getTitle() + "\" has been approved by OSAS and is now published."
            : "Your announcement \"" + announcement.getTitle() + "\" has been rejected by OSAS.";
        
        createNotification(announcement, recipientEmail, type, title, message);
    }

    /**
     * Notify event creator when their event has been successfully created
     * Provides feedback on whether event is published or pending approval
     */
    @Transactional
    public void notifyEventCreator(Announcement announcement) {
        if (announcement.getPostedBy() == null) {
            System.err.println("Cannot notify event creator: postedBy is null for announcement " + announcement.getId());
            return;
        }
        
        String recipientEmail = announcement.getPostedBy().getSchoolEmail();
        if (recipientEmail == null || recipientEmail.isEmpty()) {
            System.err.println("Cannot notify event creator: recipientEmail is null or empty for user " + announcement.getPostedBy().getId());
            return;
        }
        
        String status = announcement.getStatus();
        String title = "Event Created Successfully ✓";
        String message;
        
        if ("PUBLISHED".equals(status)) {
            message = "Your event \"" + announcement.getTitle() + "\" has been published and is now visible to all students.";
        } else if ("PENDING".equals(status)) {
            message = "Your event \"" + announcement.getTitle() + "\" has been submitted for approval. OSAS will review it shortly.";
        } else {
            message = "Your event \"" + announcement.getTitle() + "\" has been created with status: " + status;
        }
        
        createNotification(announcement, recipientEmail, "event-created", title, message);
    }
}
