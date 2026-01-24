package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.Notification;
import com.soanar.model.User;
import com.soanar.model.DistributionGroup;
import com.soanar.model.DistributionGroupMember;
import com.soanar.repository.NotificationRepository;
import com.soanar.repository.UserRepository;
import com.soanar.repository.DistributionGroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DistributionGroupMemberRepository distributionGroupMemberRepository;
    private final EmailService emailService;

    public NotificationService(NotificationRepository notificationRepository, 
                               UserRepository userRepository,
                               DistributionGroupMemberRepository distributionGroupMemberRepository,
                               EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.distributionGroupMemberRepository = distributionGroupMemberRepository;
        this.emailService = emailService;
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
     * This is for OSAS and Academic announcements
     */
    @Transactional
    public void notifyStudentsOfPublishedAnnouncement(Announcement announcement) {
        List<User> students = userRepository.findByRole("Student");
        List<String> studentEmails = students.stream()
                .map(User::getSchoolEmail)
                .collect(Collectors.toList());
        
        // Create in-app notifications
        for (User student : students) {
            createNotification(
                announcement,
                student.getSchoolEmail(),
                "announcement",
                "New Announcement: " + announcement.getTitle(),
                announcement.getDescription()
            );
        }
        
        // Send emails to all students
        if (!studentEmails.isEmpty()) {
            String subject = "New Announcement: " + announcement.getTitle();
            String body = buildEmailBody(announcement);
            emailService.sendTargetedEmail(studentEmails, subject, body);
        }
    }
    
    /**
     * Notify distribution group members when a Student Organization publishes
     * Only emails students in the announcement's distribution groups
     */
    @Transactional
    public void notifyDistributionGroupMembers(Announcement announcement) {
        if (announcement.getDistributionGroups() == null || announcement.getDistributionGroups().isEmpty()) {
            System.out.println("No distribution groups specified for announcement: " + announcement.getId());
            return;
        }
        
        List<String> recipientEmails = new ArrayList<>();
        
        // Get all members from all distribution groups
        for (DistributionGroup group : announcement.getDistributionGroups()) {
            List<DistributionGroupMember> members = distributionGroupMemberRepository.findByGroupId(group.getId());
            for (DistributionGroupMember member : members) {
                if (!recipientEmails.contains(member.getStudentEmail())) {
                    recipientEmails.add(member.getStudentEmail());
                    
                    // Create in-app notification
                    createNotification(
                        announcement,
                        member.getStudentEmail(),
                        "announcement",
                        "New Announcement: " + announcement.getTitle(),
                        announcement.getDescription()
                    );
                }
            }
        }
        
        // Send emails to distribution group members
        if (!recipientEmails.isEmpty()) {
            String subject = "New Announcement: " + announcement.getTitle();
            String body = buildEmailBody(announcement);
            emailService.sendTargetedEmail(recipientEmails, subject, body);
        } else {
            System.out.println("No recipients found in distribution groups for announcement: " + announcement.getId());
        }
    }
    
    /**
     * Build a formatted email body for announcements
     */
    private String buildEmailBody(Announcement announcement) {
        StringBuilder body = new StringBuilder();
        body.append("Hello,\n\n");
        body.append("A new announcement has been posted:\n\n");
        body.append("Title: ").append(announcement.getTitle()).append("\n\n");
        body.append("Description:\n").append(announcement.getDescription()).append("\n\n");
        
        if (announcement.getPostedBy() != null) {
            body.append("Posted by: ").append(announcement.getPostedBy().getName()).append("\n");
        }
        
        body.append("\nLog in to SONAR to view more details.\n\n");
        body.append("Best regards,\n");
        body.append("SONAR - Student Organization & Notification Announcement Resource");
        
        return body.toString();
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
