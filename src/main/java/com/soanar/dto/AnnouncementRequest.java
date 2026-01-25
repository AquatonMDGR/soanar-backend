package com.soanar.dto;

import java.util.List;

public class AnnouncementRequest {
    private String title;
    private String description;
    private String imageUrl;
    private List<Long> distributionGroupIds;
    private String startDate; // YYYY-MM-DD
    private String endDate;   // YYYY-MM-DD
    private List<String> attachments; // URLs/base64 strings for prototype

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

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public List<String> getAttachments() { return attachments; }
    public void setAttachments(List<String> attachments) { this.attachments = attachments; }
}
