package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.Notification;
import com.soanar.model.User;
import com.soanar.model.DistributionGroup;
import com.soanar.model.DistributionGroupMember;
import com.soanar.model.NotificationPreference;
import com.soanar.repository.NotificationRepository;
import com.soanar.repository.UserRepository;
import com.soanar.repository.DistributionGroupMemberRepository;
import com.soanar.repository.NotificationPreferenceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DistributionGroupMemberRepository distributionGroupMemberRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final EmailService emailService;
    private final RecipientResolverService recipientResolverService;

    public NotificationService(NotificationRepository notificationRepository, 
                               UserRepository userRepository,
                               DistributionGroupMemberRepository distributionGroupMemberRepository,
                               NotificationPreferenceRepository notificationPreferenceRepository,
                               EmailService emailService,
                               RecipientResolverService recipientResolverService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.distributionGroupMemberRepository = distributionGroupMemberRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.emailService = emailService;
        this.recipientResolverService = recipientResolverService;
    }

    @Transactional
    public void createNotification(Announcement announcement, String recipientEmail, String type, String title, String message) {
        Notification notification = new Notification(announcement, recipientEmail, type, title, message);
        if (announcement != null && announcement.getId() != null) {
            notification.setActionUrl("/announcement/" + announcement.getId());
            notification.setEntityType("ANNOUNCEMENT");
            notification.setEntityId(announcement.getId());
        }
        userRepository.findBySchoolEmail(recipientEmail).ifPresent(notification::setUser);
        notificationRepository.save(notification);
    }

    @Transactional
    public void createNotification(Announcement announcement, String recipientEmail) {
        Notification notification = new Notification();
        notification.setAnnouncement(announcement);
        notification.setRecipientEmail(recipientEmail);
        notification.setType("announcement");
        if (announcement != null) {
            notification.setTitle("New Announcement: " + announcement.getTitle());
            notification.setBody(announcement.getDescription());
        }
        if (announcement != null && announcement.getId() != null) {
            notification.setActionUrl("/announcement/" + announcement.getId());
            notification.setEntityType("ANNOUNCEMENT");
            notification.setEntityId(announcement.getId());
        }
        notification.setCreatedAt(Instant.now());
        userRepository.findBySchoolEmail(recipientEmail).ifPresent(notification::setUser);
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyStudentsOfPublishedAnnouncement(Announcement announcement) {
        Set<String> recipientEmails = recipientResolverService.resolveWithFallback(announcement);

        for (String email : recipientEmails) {
            createNotification(
                announcement,
                email,
                "announcement",
                "New Announcement: " + announcement.getTitle(),
                announcement.getDescription()
            );
        }

        if (!recipientEmails.isEmpty()) {
            String subject = "New Announcement: " + announcement.getTitle();
            String html = buildEmailHtmlBody(announcement);
            emailService.sendTargetedEmail(new ArrayList<>(recipientEmails), subject, html);
        }
    }
    
    /**
     * Notify distribution group members when a Student Organization publishes
     * Only emails students in the announcement's distribution groups
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyDistributionGroupMembers(Announcement announcement) {
        Set<String> recipientEmails = recipientResolverService.resolveWithFallback(announcement);

        for (String email : recipientEmails) {
            createNotification(
                announcement,
                email,
                "announcement",
                "New Announcement: " + announcement.getTitle(),
                announcement.getDescription()
            );
        }

        if (!recipientEmails.isEmpty()) {
            String subject = "New Announcement: " + announcement.getTitle();
            String html = buildEmailHtmlBody(announcement);
            emailService.sendTargetedEmail(new ArrayList<>(recipientEmails), subject, html);
        } else {
            System.out.println("No recipients found for announcement: " + announcement.getId());
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
     * Build an HTML email body for announcements (includes all images from imageUrls array).
     */
    private String buildEmailHtmlBody(Announcement announcement) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<p>Hello,</p>");
        html.append("<p>A new announcement has been posted:</p>");
        html.append("<p><strong>Title:</strong> ").append(announcement.getTitle()).append("</p>");
        
        // DEBUG: Log image details
        System.out.println("DEBUG buildEmailHtmlBody: imageUrl = " + announcement.getImageUrl());
        System.out.println("DEBUG buildEmailHtmlBody: imageUrls size = " + (announcement.getImageUrls() != null ? announcement.getImageUrls().size() : "NULL"));
        if (announcement.getImageUrls() != null) {
            for (int i = 0; i < announcement.getImageUrls().size(); i++) {
                System.out.println("DEBUG buildEmailHtmlBody: imageUrls[" + i + "] = " + announcement.getImageUrls().get(i));
            }
        }
        
        // Include all images from imageUrls array
        if (announcement.getImageUrls() != null && !announcement.getImageUrls().isEmpty()) {
            System.out.println("DEBUG buildEmailHtmlBody: Adding " + announcement.getImageUrls().size() + " images to email");
            for (String imageUrl : announcement.getImageUrls()) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    System.out.println("DEBUG buildEmailHtmlBody: Adding image URL: " + imageUrl);
                    html.append("<p><img src=\"").append(imageUrl).append("\" alt=\"Announcement image\" style=\"max-width:600px;height:auto;margin:10px 0;\"/></p>");
                }
            }
        } else if (announcement.getImageUrl() != null && !announcement.getImageUrl().isBlank()) {
            // Fallback to single imageUrl for backward compatibility
            System.out.println("DEBUG buildEmailHtmlBody: Fallback - using single imageUrl: " + announcement.getImageUrl());
            html.append("<p><img src=\"").append(announcement.getImageUrl()).append("\" alt=\"Announcement image\" style=\"max-width:600px;height:auto;\"/></p>");
        } else {
            System.out.println("DEBUG buildEmailHtmlBody: NO IMAGES FOUND - imageUrls is " + (announcement.getImageUrls() == null ? "NULL" : "EMPTY") + ", imageUrl is " + (announcement.getImageUrl() == null ? "NULL" : "EMPTY"));
        }
        
        html.append("<p><strong>Description:</strong><br/>").append(announcement.getDescription() != null ? announcement.getDescription() : "").append("</p>");
        if (announcement.getPostedBy() != null) {
            html.append("<p>Posted by: ").append(announcement.getPostedBy().getName()).append("</p>");
        }
        html.append("<p>Log in to SONAR to view more details.</p>");
        html.append("<p>Best regards,<br/>SONAR - Student Organization & Notification Announcement Resource</p>");
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Notify OSAS when a Student Organization creates an announcement
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        // Send HTML email to the event creator including all event images if present
        try {
            String subject = title;
            StringBuilder html = new StringBuilder();
            html.append("<html><body>");
            html.append("<p>Hello,</p>");
            html.append("<p>").append(message).append("</p>");
            
            // Include all images from imageUrls array
            if (announcement.getImageUrls() != null && !announcement.getImageUrls().isEmpty()) {
                for (String imageUrl : announcement.getImageUrls()) {
                    if (imageUrl != null && !imageUrl.isBlank()) {
                        html.append("<p><img src=\"").append(imageUrl).append("\" alt=\"Event Image\" style=\"max-width:600px;height:auto;margin:10px 0;\"/></p>");
                    }
                }
            } else if (announcement.getImageUrl() != null && !announcement.getImageUrl().isBlank()) {
                // Fallback to single imageUrl for backward compatibility
                html.append("<p><img src=\"").append(announcement.getImageUrl()).append("\" alt=\"Event Image\" style=\"max-width:600px;height:auto;\"/></p>");
            }
            
            html.append("<p>Title: <strong>").append(announcement.getTitle()).append("</strong></p>");
            html.append("<p>Description:<br/>").append(announcement.getDescription() != null ? announcement.getDescription() : "").append("</p>");
            html.append("<p>Log in to SONAR to view more details.</p>");
            html.append("<p>Best regards,<br/>SONAR - Student Organization & Notification Announcement Resource</p>");
            html.append("</body></html>");

            emailService.sendTargetedEmail(Collections.singletonList(recipientEmail), subject, html.toString());
        } catch (Exception e) {
            System.err.println("Failed to send event-created email to " + recipientEmail + ": " + e.getMessage());
        }
    }
    
    // ========== Phase 6 Enhancements ==========
    
    /**
     * Create a generic notification (Phase 6)
     */
    @Transactional
    public Notification createNotification(User user, String type, String title, String body, String actionUrl, String entityType, Long entityId) {
        // Check user preferences
        NotificationPreference prefs = getOrCreatePreferences(user, "default-org");
        if (!prefs.getNotifyInApp()) {
            return null; // User disabled in-app notifications
        }
        
        Notification notification = new Notification(user, user.getSchoolEmail(), type, title, body, actionUrl, entityType, entityId);
        notification = notificationRepository.save(notification);
        
        // Send email if enabled
        if (prefs.getNotifyByEmail()) {
            try {
                String html = "<html><body><p>" + body + "</p></body></html>";
                emailService.sendTargetedEmail(Collections.singletonList(user.getSchoolEmail()), title, html);
            } catch (Exception e) {
                System.err.println("Failed to send notification email: " + e.getMessage());
            }
        }
        
        return notification;
    }
    
    /**
     * Get paginated notifications for a user
     * Filters out approval notifications for Student Organization users (they can't approve)
     */
    public Page<Notification> getNotificationsForUser(User user, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Notification> notifications = notificationRepository.findByUserOrRecipientEmailOrderByCreatedAtDesc(user, user.getSchoolEmail(), pageable);
        
        // Filter out approval notifications for users who should not act on approvals.
        if ("Student Organization".equals(user.getRole()) || "Student".equals(user.getRole())) {
            var filtered = notifications.getContent().stream()
                .filter(n -> !"approval".equals(n.getType()))
                .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }
        
        return notifications;
    }
    
    /**
     * Get unread notifications for a user
     * Filters out approval notifications for Student Organization users
     */
    public List<Notification> getUnreadNotifications(User user) {
        List<Notification> unread = notificationRepository.findUnreadByUserOrEmail(user, user.getSchoolEmail());
        
        // Filter out approval notifications for users who should not act on approvals.
        if ("Student Organization".equals(user.getRole()) || "Student".equals(user.getRole())) {
            return unread.stream()
                .filter(n -> !"approval".equals(n.getType()))
                .toList();
        }
        
        return unread;
    }
    
    /**
     * Get unread notification count
     * Excludes approval notifications for Student Organization users
     */
    public long getUnreadCount(User user) {
        long count = notificationRepository.countUnreadByUserOrEmail(user, user.getSchoolEmail());
        
        // For Student/Student Organization users, also subtract approval-type unread notifications.
        if ("Student Organization".equals(user.getRole()) || "Student".equals(user.getRole())) {
            List<Notification> allUnread = notificationRepository.findUnreadByUserOrEmail(user, user.getSchoolEmail());
            long approvalCount = allUnread.stream()
                .filter(n -> "approval".equals(n.getType()))
                .count();
            return count - approvalCount;
        }
        
        return count;
    }
    
    /**
     * Mark notification as read (Phase 6 - by ID)
     */
    @Transactional
    public void markNotificationAsRead(Long notificationId) {
        markAsRead(notificationId);
    }

    @Transactional
    public void markNotificationAsUnread(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setReadAt(null);
            notificationRepository.save(notification);
        });
    }
    
    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public void markAllNotificationsAsRead(User user) {
        markAllAsRead(user.getSchoolEmail());
    }
    
    /**
     * Get or create notification preferences for a user
     */
    private NotificationPreference getOrCreatePreferences(User user, String organizationId) {
        return notificationPreferenceRepository.findByUserAndOrganizationId(user, organizationId)
            .orElseGet(() -> {
                NotificationPreference prefs = new NotificationPreference();
                prefs.setUser(user);
                prefs.setOrganizationId(organizationId);
                return notificationPreferenceRepository.save(prefs);
            });
    }
    
    /**
     * Get notification preferences
     */
    public NotificationPreference getPreferences(User user, String organizationId) {
        return getOrCreatePreferences(user, organizationId);
    }
    
    /**
     * Update notification preferences
     */
    @Transactional
    public NotificationPreference updatePreferences(User user, String organizationId, NotificationPreference updates) {
        NotificationPreference prefs = getOrCreatePreferences(user, organizationId);
        
        if (updates.getNotifyByEmail() != null) prefs.setNotifyByEmail(updates.getNotifyByEmail());
        if (updates.getNotifyInApp() != null) prefs.setNotifyInApp(updates.getNotifyInApp());
        if (updates.getNotifyOnApproval() != null) prefs.setNotifyOnApproval(updates.getNotifyOnApproval());
        if (updates.getNotifyOnRejection() != null) prefs.setNotifyOnRejection(updates.getNotifyOnRejection());
        if (updates.getNotifyOnPublish() != null) prefs.setNotifyOnPublish(updates.getNotifyOnPublish());
        if (updates.getNotifyOnComment() != null) prefs.setNotifyOnComment(updates.getNotifyOnComment());
        if (updates.getNotifyOnMention() != null) prefs.setNotifyOnMention(updates.getNotifyOnMention());
        
        prefs.setUpdatedAt(Instant.now());
        return notificationPreferenceRepository.save(prefs);
    }
}
