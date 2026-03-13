package com.soanar.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "organization_settings")
public class OrganizationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "facebook_enabled")
    private Boolean facebookEnabled = false;

    @Column(name = "instagram_enabled")
    private Boolean instagramEnabled = false;

    @Column(name = "term1_start_md")
    private String term1StartMonthDay;

    @Column(name = "term1_end_md")
    private String term1EndMonthDay;

    @Column(name = "term2_start_md")
    private String term2StartMonthDay;

    @Column(name = "term2_end_md")
    private String term2EndMonthDay;

    @Column(name = "term3_start_md")
    private String term3StartMonthDay;

    @Column(name = "term3_end_md")
    private String term3EndMonthDay;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    public OrganizationSettings() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public Boolean getFacebookEnabled() { return facebookEnabled; }
    public void setFacebookEnabled(Boolean facebookEnabled) { this.facebookEnabled = facebookEnabled; }

    public Boolean getInstagramEnabled() { return instagramEnabled; }
    public void setInstagramEnabled(Boolean instagramEnabled) { this.instagramEnabled = instagramEnabled; }

    public String getTerm1StartMonthDay() { return term1StartMonthDay; }
    public void setTerm1StartMonthDay(String term1StartMonthDay) { this.term1StartMonthDay = term1StartMonthDay; }

    public String getTerm1EndMonthDay() { return term1EndMonthDay; }
    public void setTerm1EndMonthDay(String term1EndMonthDay) { this.term1EndMonthDay = term1EndMonthDay; }

    public String getTerm2StartMonthDay() { return term2StartMonthDay; }
    public void setTerm2StartMonthDay(String term2StartMonthDay) { this.term2StartMonthDay = term2StartMonthDay; }

    public String getTerm2EndMonthDay() { return term2EndMonthDay; }
    public void setTerm2EndMonthDay(String term2EndMonthDay) { this.term2EndMonthDay = term2EndMonthDay; }

    public String getTerm3StartMonthDay() { return term3StartMonthDay; }
    public void setTerm3StartMonthDay(String term3StartMonthDay) { this.term3StartMonthDay = term3StartMonthDay; }

    public String getTerm3EndMonthDay() { return term3EndMonthDay; }
    public void setTerm3EndMonthDay(String term3EndMonthDay) { this.term3EndMonthDay = term3EndMonthDay; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
