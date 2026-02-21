package com.soanar.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_preferences", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "organization_id"}),
    indexes = {
        @Index(name = "idx_notif_pref_user", columnList = "user_id"),
        @Index(name = "idx_notif_pref_org", columnList = "organization_id")
    }
)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "notify_by_email", nullable = false)
    private Boolean notifyByEmail = true;

    @Column(name = "notify_in_app", nullable = false)
    private Boolean notifyInApp = true;

    @Column(name = "notify_on_approval", nullable = false)
    private Boolean notifyOnApproval = true;

    @Column(name = "notify_on_rejection", nullable = false)
    private Boolean notifyOnRejection = true;

    @Column(name = "notify_on_publish", nullable = false)
    private Boolean notifyOnPublish = true;

    @Column(name = "notify_on_comment", nullable = false)
    private Boolean notifyOnComment = true;

    @Column(name = "notify_on_mention", nullable = false)
    private Boolean notifyOnMention = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public NotificationPreference() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public Boolean getNotifyByEmail() { return notifyByEmail; }
    public void setNotifyByEmail(Boolean notifyByEmail) { this.notifyByEmail = notifyByEmail; }

    public Boolean getNotifyInApp() { return notifyInApp; }
    public void setNotifyInApp(Boolean notifyInApp) { this.notifyInApp = notifyInApp; }

    public Boolean getNotifyOnApproval() { return notifyOnApproval; }
    public void setNotifyOnApproval(Boolean notifyOnApproval) { this.notifyOnApproval = notifyOnApproval; }

    public Boolean getNotifyOnRejection() { return notifyOnRejection; }
    public void setNotifyOnRejection(Boolean notifyOnRejection) { this.notifyOnRejection = notifyOnRejection; }

    public Boolean getNotifyOnPublish() { return notifyOnPublish; }
    public void setNotifyOnPublish(Boolean notifyOnPublish) { this.notifyOnPublish = notifyOnPublish; }

    public Boolean getNotifyOnComment() { return notifyOnComment; }
    public void setNotifyOnComment(Boolean notifyOnComment) { this.notifyOnComment = notifyOnComment; }

    public Boolean getNotifyOnMention() { return notifyOnMention; }
    public void setNotifyOnMention(Boolean notifyOnMention) { this.notifyOnMention = notifyOnMention; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
