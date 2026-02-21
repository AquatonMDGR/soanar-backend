package com.soanar.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores encrypted social media credentials (Facebook/Instagram access tokens)
 * for authorized posting and engagement tracking.
 */
@Entity
@Table(name = "social_media_credentials")
public class SocialMediaCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId; // References OSAS org (for now, hardcoded to one org)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform; // FACEBOOK, INSTAGRAM

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private TokenType tokenType = TokenType.USER_TOKEN; // USER_TOKEN (OAuth) or SYSTEM_USER_TOKEN (Business Portfolio)

    @Column(name = "page_id", nullable = false, length = 500)
    private String pageId; // Encrypted page ID

    @Column(name = "access_token", nullable = false, length = 2000)
    private String accessToken; // Encrypted access token

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SocialMediaCredential() {}

    public SocialMediaCredential(UUID organizationId, Platform platform, String pageId, String accessToken) {
        this.organizationId = organizationId;
        this.platform = platform;
        this.pageId = pageId;
        this.accessToken = accessToken;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }

    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }

    public TokenType getTokenType() { return tokenType; }
    public void setTokenType(TokenType tokenType) { this.tokenType = tokenType; }

    public String getPageId() { return pageId; }
    public void setPageId(String pageId) { this.pageId = pageId; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public enum Platform {
        FACEBOOK, INSTAGRAM
    }

    public enum TokenType {
        USER_TOKEN,        // Personal OAuth token (expires, requires refresh)
        SYSTEM_USER_TOKEN  // Meta Business System User token (long-lived, 90+ days)
    }
}
