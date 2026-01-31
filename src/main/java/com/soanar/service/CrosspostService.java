package com.soanar.service;

import com.soanar.dto.CrosspostRequest;
import com.soanar.model.Announcement;
import com.soanar.model.SocialMediaPost;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrator service for crossposting announcements to multiple social media platforms
 */
public interface CrosspostService {

    /**
     * Post announcement to selected platforms (Facebook and/or Instagram)
     * Creates SocialMediaPost records for tracking
     * @param announcement The announcement to post
     * @param request Contains platform selection and custom captions
     */
    void crosspostAnnouncement(Announcement announcement, CrosspostRequest request, MultipartFile image, UUID organizationId) throws Exception;

    /**
     * Post announcement immediately to a specific platform
     */
    SocialMediaPost postToPlatform(Announcement announcement, String caption,
                                   MultipartFile image, String platform, UUID organizationId) throws Exception;

    /**
     * Sync engagement metrics from all platforms for a specific announcement
     */
    void syncEngagementMetrics(UUID announcementId) throws Exception;

    /**
     * Execute all scheduled posts that are due
     * Called by scheduled job every 5 minutes
     */
    void executeScheduledPosts() throws Exception;

    /**
     * Retry failed posts (exponential backoff)
     */
    void retryFailedPosts() throws Exception;

    /**
     * Delete announcement from all platforms
     * Called when announcement is deleted from SONAR
     */
    void deleteFromAllPlatforms(UUID announcementId) throws Exception;

    /**
     * Get all social media posts for an announcement
     */
    List<SocialMediaPost> getPostsForAnnouncement(Long announcementId);

    /**
     * Get post details including engagement metrics
     */
    SocialMediaPost getPost(UUID postId);
}
