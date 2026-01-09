package com.soanar.model;

import jakarta.persistence.*;

@Entity
@Table(name = "socialmediapages")
public class SocialMediaPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "page_name", nullable = false)
    private String pageName;

    public SocialMediaPage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPageName() { return pageName; }
    public void setPageName(String pageName) { this.pageName = pageName; }
}
