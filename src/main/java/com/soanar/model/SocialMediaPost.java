package com.soanar.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks social media posts created from announcements.
 * Stores platform-specific data, engagement metrics, and posting status.
 */
@Entity
@Table(name = "social_media_posts")
public class SocialMediaPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SocialMediaCredential.Platform platform; // FACEBOOK, INSTAGRAM

    @Column(name = "post_id", length = 500)
    private String postId; // Platform's post ID (e.g., Facebook post ID)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status = PostStatus.PENDING; // PENDING, SCHEDULED, POSTED, FAILED, DELETED

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "scheduled_for")
    private Instant scheduledFor; // If post should be scheduled for later

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage; // Error details if posting failed

    @Column(name = "custom_caption", columnDefinition = "TEXT")
    private String customCaption; // Platform-specific caption

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "engagement_data", columnDefinition = "jsonb")
    private Map<String, Object> engagementData; // {likes, comments, shares, reach, impressions, clicks}

    @Column(name = "last_sync_at")
    private Instant lastSyncAt; // Last time engagement metrics were synced

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SocialMediaPost() {}

    public SocialMediaPost(Announcement announcement, SocialMediaCredential.Platform platform) {
        this.announcement = announcement;
        this.platform = platform;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Announcement getAnnouncement() { return announcement; }
    public void setAnnouncement(Announcement announcement) { this.announcement = announcement; }

    public SocialMediaCredential.Platform getPlatform() { return platform; }
    public void setPlatform(SocialMediaCredential.Platform platform) { this.platform = platform; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public PostStatus getStatus() { return status; }
    public void setStatus(PostStatus status) { this.status = status; }

    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

    public Instant getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getCustomCaption() { return customCaption; }
    public void setCustomCaption(String customCaption) { this.customCaption = customCaption; }

    public Map<String, Object> getEngagementData() { return engagementData; }
    public void setEngagementData(Map<String, Object> engagementData) { this.engagementData = engagementData; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public enum PostStatus {
        PENDING,      // Waiting to be posted
        SCHEDULED,    // Scheduled for future posting
        POSTED,       // Successfully posted
        FAILED,       // Post failed
        DELETED       // Post was deleted from platform
    }
}
