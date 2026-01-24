package com.soanar.dto;

import java.util.List;

public class AnnouncementRequest {
    private String title;
    private String description;
    private String imageUrl;
    private List<Long> distributionGroupIds;

    public AnnouncementRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public List<Long> getDistributionGroupIds() { return distributionGroupIds; }
    public void setDistributionGroupIds(List<Long> distributionGroupIds) { 
        this.distributionGroupIds = distributionGroupIds; 
    }
}
