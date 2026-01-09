package com.soanar.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "socialmediatokens")
public class SocialMediaToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private SocialMediaPage page;

    @Column(name = "access_token", nullable = false, length = 2000)
    private String accessToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    public SocialMediaToken() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SocialMediaPage getPage() { return page; }
    public void setPage(SocialMediaPage page) { this.page = page; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
