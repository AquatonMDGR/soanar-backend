package com.soanar.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user", columnList = "user_id"),
    @Index(name = "idx_notification_recipient", columnList = "recipient_email"),
    @Index(name = "idx_notification_read", columnList = "read_at"),
    @Index(name = "idx_notification_created", columnList = "created_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String type;  // ANNOUNCEMENT_APPROVED, ANNOUNCEMENT_REJECTED, ANNOUNCEMENT_PUBLISHED, COMMENT_ADDED, MENTION

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", name = "body")
    private String body;

    @Column(name = "message")
    private String message;  // Legacy field, kept for backward compatibility

    @Column(name = "action_url")
    private String actionUrl;  // URL to navigate to when clicked

    @Column(name = "entity_type")
    private String entityType;  // ANNOUNCEMENT, COMMENT, DISTRIBUTION_GROUP

    @Column(name = "entity_id")
    private Long entityId;

    @ManyToOne
    @JoinColumn(name = "announcement_id")
    private Announcement announcement;  // Legacy field, kept for backward compatibility

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}

    // Legacy constructor for backward compatibility
    public Notification(Announcement announcement, String recipientEmail, String type, String title, String message) {
        this.announcement = announcement;
        this.recipientEmail = recipientEmail;
        this.type = type;
        this.title = title;
        this.message = message;
        this.body = message;
        this.createdAt = Instant.now();
    }

    // New constructor for Phase 6
    public Notification(User user, String recipientEmail, String type, String title, String body, String actionUrl, String entityType, Long entityId) {
        this.user = user;
        this.recipientEmail = recipientEmail;
        this.type = type;
        this.title = title;
        this.body = body;
        this.message = body;
        this.actionUrl = actionUrl;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { 
        this.body = body;
        this.message = body;  // Keep message in sync
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { 
        this.message = message;
        this.body = message;  // Keep body in sync
    }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public Announcement getAnnouncement() { return announcement; }
    public void setAnnouncement(Announcement announcement) { this.announcement = announcement; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isRead() { return readAt != null; }
}
