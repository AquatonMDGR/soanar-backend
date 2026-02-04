package com.soanar.dto;

/**
 * Response DTO for social media post
 */
public class SocialMediaPostResponse {

    private String id;
    private String platform;
    private String status;
    private String postUrl;
    private String postedAt;
    private Object engagementData;
    private String errorMessage;

    public SocialMediaPostResponse() {}

    public SocialMediaPostResponse(String id, String platform, String status, String postUrl) {
        this.id = id;
        this.platform = platform;
        this.status = status;
        this.postUrl = postUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPostUrl() { return postUrl; }
    public void setPostUrl(String postUrl) { this.postUrl = postUrl; }

    public String getPostedAt() { return postedAt; }
    public void setPostedAt(String postedAt) { this.postedAt = postedAt; }

    public Object getEngagementData() { return engagementData; }
    public void setEngagementData(Object engagementData) { this.engagementData = engagementData; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
