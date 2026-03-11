package com.soanar.service;

import com.soanar.model.Announcement;
import com.soanar.model.Notification;
import com.soanar.model.User;
import com.soanar.model.NotificationPreference;
import com.soanar.repository.NotificationRepository;
import com.soanar.repository.UserRepository;
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

    // Some deployed databases still use legacy VARCHAR sizes in notifications.
    private static final int LEGACY_RECIPIENT_MAX = 255;
    private static final int LEGACY_TITLE_MAX = 255;
    private static final int LEGACY_MESSAGE_MAX = 255;
    private static final int LEGACY_TYPE_MAX = 50;
    private static final int LEGACY_ENTITY_TYPE_MAX = 50;
    private static final int NOTIFICATION_DESCRIPTION_SNIPPET_MAX = 180;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final EmailService emailService;
    private final RecipientResolverService recipientResolverService;

    public NotificationService(NotificationRepository notificationRepository, 
                               UserRepository userRepository,
                               NotificationPreferenceRepository notificationPreferenceRepository,
                               EmailService emailService,
                               RecipientResolverService recipientResolverService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.emailService = emailService;
        this.recipientResolverService = recipientResolverService;
    }

    @Transactional
    public void createNotification(Announcement announcement, String recipientEmail, String type, String title, String message) {
        if (isKnownInactiveRecipient(recipientEmail)) {
            return;
        }
        Notification notification = new Notification(announcement, recipientEmail, type, title, message);
        if (announcement != null && announcement.getId() != null) {
            String actionUrl = "approval-request".equals(type)
                ? "/approval-queue"
                : "/announcement/" + announcement.getId();
            notification.setActionUrl(actionUrl);
            notification.setEntityType("ANNOUNCEMENT");
            notification.setEntityId(announcement.getId());
        }
        applyLegacyColumnSafety(notification);
        userRepository.findBySchoolEmail(recipientEmail).ifPresent(notification::setUser);
        notificationRepository.save(notification);
    }

    @Transactional
    public void createNotification(Announcement announcement, String recipientEmail) {
        if (isKnownInactiveRecipient(recipientEmail)) {
            return;
        }
        Notification notification = new Notification();
        notification.setAnnouncement(announcement);
        notification.setRecipientEmail(recipientEmail);
        notification.setType("announcement");
        if (announcement != null) {
            notification.setTitle("New Announcement: " + announcement.getTitle());
            notification.setBody(buildAnnouncementNotificationPreview(announcement));
        }
        if (announcement != null && announcement.getId() != null) {
            notification.setActionUrl("/announcement/" + announcement.getId());
            notification.setEntityType("ANNOUNCEMENT");
            notification.setEntityId(announcement.getId());
        }
        notification.setCreatedAt(Instant.now());
        applyLegacyColumnSafety(notification);
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
    public void notifyStudentsOfPublishedAnnouncement(Announcement announcement) {
        Set<String> recipientEmails = recipientResolverService.resolveWithFallback(announcement);
        String notificationPreview = buildAnnouncementNotificationPreview(announcement);

        for (String email : recipientEmails) {
            createNotification(
                announcement,
                email,
                "announcement",
                "New Announcement: " + announcement.getTitle(),
                notificationPreview
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
    public void notifyDistributionGroupMembers(Announcement announcement) {
        Set<String> recipientEmails = recipientResolverService.resolveWithFallback(announcement);
        String notificationPreview = buildAnnouncementNotificationPreview(announcement);

        for (String email : recipientEmails) {
            createNotification(
                announcement,
                email,
                "announcement",
                "New Announcement: " + announcement.getTitle(),
                notificationPreview
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
     * Emergency announcements must reach all active students regardless of targeting
     * and user notification preference toggles.
     */
    public void notifyAllStudentsEmergency(Announcement announcement) {
        List<User> activeStudents = userRepository.findByRoleAndIsActiveTrue("Student");
        String notificationPreview = buildAnnouncementNotificationPreview(announcement);
        Set<String> recipientEmails = activeStudents.stream()
                .map(User::getSchoolEmail)
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        for (String email : recipientEmails) {
            createNotification(
                announcement,
                email,
                "announcement",
                "Emergency Announcement: " + announcement.getTitle(),
                notificationPreview
            );
        }

        if (!recipientEmails.isEmpty()) {
            String subject = "[EMERGENCY] " + announcement.getTitle();
            String html = buildEmailHtmlBody(announcement);
            emailService.sendTargetedEmail(new ArrayList<>(recipientEmails), subject, html);
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
        
        body.append("\nLog in to SOANAR to view more details.\n\n");
        body.append("Best regards,\n");
        body.append("SOANAR - Student Organization & Notification Announcement Resource");
        
        return body.toString();
    }

    /**
     * Build an HTML email body for announcements (includes all images from imageUrls array).
     */
    private String buildEmailHtmlBody(Announcement announcement) {
        StringBuilder html = new StringBuilder();
        String title = escapeHtml(announcement.getTitle());
        String description = nl2br(escapeHtml(announcement.getDescription()));
        String posterName = escapeHtml(resolvePosterName(announcement));

        html.append("<html><body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">");
        html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f4f6;padding:24px 0;\">");
        html.append("<tr><td align=\"center\">");
        html.append("<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;\">");
        html.append("<tr><td style=\"padding:24px 28px 8px;color:#111827;font-size:18px;font-weight:700;\">New Announcement</td></tr>");
        html.append("<tr><td style=\"padding:0 28px 16px;color:#111827;font-size:28px;line-height:1.35;font-weight:700;\">")
            .append(title)
            .append("</td></tr>");
        html.append("<tr><td style=\"padding:0 28px 12px;color:#374151;font-size:15px;line-height:1.6;\">Hello,</td></tr>");
        html.append("<tr><td style=\"padding:0 28px 16px;color:#374151;font-size:15px;line-height:1.6;\">A new announcement has been posted:</td></tr>");

        List<String> imageUrls = announcement.getImageUrls() != null ? announcement.getImageUrls() : Collections.emptyList();
        if (!imageUrls.isEmpty()) {
            for (String imageUrl : imageUrls) {
                if (imageUrl != null && !imageUrl.isBlank()) {
                    html.append("<tr><td align=\"center\" style=\"padding:0 28px 14px;\">")
                        .append("<img src=\"")
                        .append(imageUrl)
                        .append("\" alt=\"Announcement visual\" style=\"display:block;width:100%;max-width:584px;height:auto;border-radius:10px;border:1px solid #e5e7eb;\"/>")
                        .append("</td></tr>");
                }
            }
        } else if (announcement.getImageUrl() != null && !announcement.getImageUrl().isBlank()) {
            html.append("<tr><td align=\"center\" style=\"padding:0 28px 14px;\">")
                .append("<img src=\"")
                .append(announcement.getImageUrl())
                .append("\" alt=\"Announcement visual\" style=\"display:block;width:100%;max-width:584px;height:auto;border-radius:10px;border:1px solid #e5e7eb;\"/>")
                .append("</td></tr>");
        }

        html.append("<tr><td style=\"padding:8px 28px 0;color:#111827;font-size:14px;font-weight:700;\">Description</td></tr>");
        html.append("<tr><td style=\"padding:8px 28px 0;color:#374151;font-size:15px;line-height:1.65;\">")
            .append(description)
            .append("</td></tr>");
        html.append("<tr><td style=\"padding:16px 28px 0;color:#4b5563;font-size:15px;\">Posted by: ")
            .append(posterName)
            .append("</td></tr>");
        html.append("<tr><td style=\"padding:20px 28px 0;color:#374151;font-size:15px;line-height:1.6;\">Log in to SOANAR to view more details.</td></tr>");
        html.append("<tr><td style=\"padding:24px 28px 28px;color:#374151;font-size:15px;line-height:1.6;\">Best regards,<br/>SOANAR</td></tr>");
        html.append("</table>");
        html.append("</td></tr></table>");
        html.append("</body></html>");
        return html.toString();
    }

    /**
     * Notify OSAS when a Student Organization creates an announcement
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyOSASOfNewAnnouncement(Announcement announcement) {
        List<User> osasUsers = userRepository.findByRoleAndIsActiveTrue("OSAS");
        for (User osasUser : osasUsers) {
            createNotification(
                announcement,
                osasUser.getSchoolEmail(),
                "approval-request",
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
            html.append("<html><body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">");
            html.append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f4f6;padding:24px 0;\">\n");
            html.append("<tr><td align=\"center\">\n");
            html.append("<table role=\"presentation\" width=\"640\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:640px;width:100%;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;overflow:hidden;\">\n");
            html.append("<tr><td style=\"padding:24px 28px 8px;color:#111827;font-size:24px;font-weight:700;\">Event Created Successfully</td></tr>");
            html.append("<tr><td style=\"padding:0 28px 16px;color:#374151;font-size:15px;line-height:1.6;\">Hello,</td></tr>");
            html.append("<tr><td style=\"padding:0 28px 16px;color:#374151;font-size:15px;line-height:1.6;\">")
                .append(escapeHtml(message))
                .append("</td></tr>");
            
            // Include all images from imageUrls array
            if (announcement.getImageUrls() != null && !announcement.getImageUrls().isEmpty()) {
                for (String imageUrl : announcement.getImageUrls()) {
                    if (imageUrl != null && !imageUrl.isBlank()) {
                        html.append("<tr><td align=\"center\" style=\"padding:0 28px 14px;\">")
                            .append("<img src=\"").append(imageUrl)
                            .append("\" alt=\"Event visual\" style=\"display:block;width:100%;max-width:584px;height:auto;border-radius:10px;border:1px solid #e5e7eb;\"/>")
                            .append("</td></tr>");
                    }
                }
            } else if (announcement.getImageUrl() != null && !announcement.getImageUrl().isBlank()) {
                // Fallback to single imageUrl for backward compatibility
                html.append("<tr><td align=\"center\" style=\"padding:0 28px 14px;\">")
                    .append("<img src=\"").append(announcement.getImageUrl())
                    .append("\" alt=\"Event visual\" style=\"display:block;width:100%;max-width:584px;height:auto;border-radius:10px;border:1px solid #e5e7eb;\"/>")
                    .append("</td></tr>");
            }
            
            html.append("<tr><td style=\"padding:8px 28px 0;color:#111827;font-size:14px;font-weight:700;\">Title</td></tr>");
            html.append("<tr><td style=\"padding:8px 28px 0;color:#374151;font-size:15px;line-height:1.65;\">")
                .append(escapeHtml(announcement.getTitle()))
                .append("</td></tr>");
            html.append("<tr><td style=\"padding:12px 28px 0;color:#111827;font-size:14px;font-weight:700;\">Description</td></tr>");
            html.append("<tr><td style=\"padding:8px 28px 0;color:#374151;font-size:15px;line-height:1.65;\">")
                .append(nl2br(escapeHtml(announcement.getDescription())))
                .append("</td></tr>");
            html.append("<tr><td style=\"padding:20px 28px 0;color:#374151;font-size:15px;line-height:1.6;\">Log in to SOANAR to view more details.</td></tr>");
            html.append("<tr><td style=\"padding:24px 28px 28px;color:#374151;font-size:15px;line-height:1.6;\">Best regards,<br/>SOANAR</td></tr>");
            html.append("</table></td></tr></table>");
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
        applyLegacyColumnSafety(notification);
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

    private boolean isKnownInactiveRecipient(String recipientEmail) {
        return userRepository.findBySchoolEmail(recipientEmail)
            .map(user -> Boolean.FALSE.equals(user.getIsActive()))
            .orElse(false);
    }

    private void applyLegacyColumnSafety(Notification notification) {
        notification.setRecipientEmail(limit(notification.getRecipientEmail(), LEGACY_RECIPIENT_MAX));
        notification.setType(limit(notification.getType(), LEGACY_TYPE_MAX));
        notification.setTitle(limit(notification.getTitle(), LEGACY_TITLE_MAX));
        notification.setEntityType(limit(notification.getEntityType(), LEGACY_ENTITY_TYPE_MAX));

        String fullBody = notification.getBody();
        if (fullBody != null) {
            String legacyMessage = limit(fullBody, LEGACY_MESSAGE_MAX);
            notification.setMessage(legacyMessage);
            notification.setBody(fullBody);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String buildAnnouncementNotificationPreview(Announcement announcement) {
        if (announcement == null) {
            return "A new announcement has been posted.";
        }

        String description = announcement.getDescription();
        if (description == null) {
            return "A new announcement has been posted.";
        }

        String normalized = description.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "A new announcement has been posted.";
        }

        if (normalized.length() <= NOTIFICATION_DESCRIPTION_SNIPPET_MAX) {
            return normalized;
        }

        return normalized.substring(0, NOTIFICATION_DESCRIPTION_SNIPPET_MAX - 1) + "...";
    }
    
    /**
     * Get notification preferences
     */
    public NotificationPreference getPreferences(User user, String organizationId) {
        return getOrCreatePreferences(user, organizationId);
    }

    private String resolvePosterName(Announcement announcement) {
        if (announcement.getPosterNameSnapshot() != null && !announcement.getPosterNameSnapshot().isBlank()) {
            return announcement.getPosterNameSnapshot();
        }
        if (announcement.getPostedBy() != null && announcement.getPostedBy().getName() != null && !announcement.getPostedBy().getName().isBlank()) {
            return announcement.getPostedBy().getName();
        }
        return "SOANAR Team";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String nl2br(String value) {
        return value == null ? "" : value.replace("\n", "<br/>");
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
