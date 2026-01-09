package com.soanar.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cross_posted_announcements")
public class CrossPostedAnnouncement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private SocialMediaPage page;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "status")
    private String status; // SUCCESS, FAILED, PENDING

    public CrossPostedAnnouncement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Announcement getAnnouncement() { return announcement; }
    public void setAnnouncement(Announcement announcement) { this.announcement = announcement; }

    public SocialMediaPage getPage() { return page; }
    public void setPage(SocialMediaPage page) { this.page = page; }

    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
