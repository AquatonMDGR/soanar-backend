package com.soanar.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "announcements")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne
    @JoinColumn(name = "posted_by")
    private User postedBy;

    @Column(name = "poster_role_snapshot")
    private String posterRoleSnapshot;

    @Column(name = "poster_name_snapshot")
    private String posterNameSnapshot;

    @Column(name = "poster_photo_snapshot")
    @JsonProperty("posterPhotoUrl")
    private String posterPhotoSnapshot;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, PUBLISHED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_notes", columnDefinition = "TEXT")
    private String approvalNotes;

    @Column(name = "image_url")
    @JsonProperty("imageUrl")
    private String imageUrl;
    
    @Column(name = "image_urls", columnDefinition = "jsonb DEFAULT '[]'::jsonb")
    @Type(JsonType.class)
    @JsonProperty("imageUrls")
    private List<String> imageUrls = new ArrayList<>();
    
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "target_year_levels", columnDefinition = "jsonb DEFAULT '[]'::jsonb")
    @Type(JsonType.class)
    @JsonProperty("targetYearLevels")
    private List<String> targetYearLevels = new ArrayList<>();

    @Column(name = "target_schools", columnDefinition = "jsonb DEFAULT '[]'::jsonb")
    @Type(JsonType.class)
    @JsonProperty("targetSchools")
    private List<String> targetSchools = new ArrayList<>();

    @Column(name = "target_manual_emails", columnDefinition = "jsonb DEFAULT '[]'::jsonb")
    @Type(JsonType.class)
    @JsonProperty("targetManualEmails")
    private List<String> targetManualEmails = new ArrayList<>();

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "announcement_distribution_groups",
        joinColumns = @JoinColumn(name = "announcement_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    private Set<DistributionGroup> distributionGroups = new HashSet<>();

    public Announcement() {}

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public User getPostedBy() { return postedBy; }
    public void setPostedBy(User postedBy) { this.postedBy = postedBy; }

    public String getPosterRoleSnapshot() { return posterRoleSnapshot; }
    public void setPosterRoleSnapshot(String posterRoleSnapshot) { this.posterRoleSnapshot = posterRoleSnapshot; }

    public String getPosterNameSnapshot() { return posterNameSnapshot; }
    public void setPosterNameSnapshot(String posterNameSnapshot) { this.posterNameSnapshot = posterNameSnapshot; }

    public String getPosterPhotoSnapshot() { return posterPhotoSnapshot; }
    public void setPosterPhotoSnapshot(String posterPhotoSnapshot) { this.posterPhotoSnapshot = posterPhotoSnapshot; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean deleted) { isDeleted = deleted; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public List<String> getImageUrls() { 
        // Ensure never null
        if (this.imageUrls == null) {
            this.imageUrls = new ArrayList<>();
        }
        return imageUrls; 
    }
    public void setImageUrls(List<String> imageUrls) { 
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>(); 
    }
    
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public List<String> getTargetYearLevels() {
        if (this.targetYearLevels == null) {
            this.targetYearLevels = new ArrayList<>();
        }
        return targetYearLevels;
    }

    public void setTargetYearLevels(List<String> targetYearLevels) {
        this.targetYearLevels = targetYearLevels != null ? targetYearLevels : new ArrayList<>();
    }

    public List<String> getTargetSchools() {
        if (this.targetSchools == null) {
            this.targetSchools = new ArrayList<>();
        }
        return targetSchools;
    }

    public void setTargetSchools(List<String> targetSchools) {
        this.targetSchools = targetSchools != null ? targetSchools : new ArrayList<>();
    }

    public List<String> getTargetManualEmails() {
        if (this.targetManualEmails == null) {
            this.targetManualEmails = new ArrayList<>();
        }
        return targetManualEmails;
    }

    public void setTargetManualEmails(List<String> targetManualEmails) {
        this.targetManualEmails = targetManualEmails != null ? targetManualEmails : new ArrayList<>();
    }

    public Set<DistributionGroup> getDistributionGroups() { return distributionGroups; }
    public void setDistributionGroups(Set<DistributionGroup> distributionGroups) {
        this.distributionGroups = distributionGroups != null ? distributionGroups : new HashSet<>();
    }

    public User getApprovedBy() { return approvedBy; }
    public void setApprovedBy(User approvedBy) { this.approvedBy = approvedBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public String getApprovalNotes() { return approvalNotes; }
    public void setApprovalNotes(String approvalNotes) { this.approvalNotes = approvalNotes; }
}

