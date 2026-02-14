package com.soanar.service;

import com.soanar.model.Announcement;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for posting announcements to Instagram
 */
public interface InstagramService {

    /**
     * Post announcement to Instagram
     * @param announcement The announcement to post
     * @param caption Custom caption with hashtags for Instagram
    * @param images Optional images to attach (first image used when required)
     * @return Platform-specific post ID
     */
    String postAnnouncement(Announcement announcement, String caption, List<MultipartFile> images, UUID organizationId) throws Exception;

    /**
     * Delete a post from Instagram
     */
    void deletePost(String postId, UUID organizationId) throws Exception;

    /**
     * Get engagement metrics for a post (likes, comments, reach)
     */
    Map<String, Object> getEngagement(String postId, UUID organizationId) throws Exception;

    /**
     * Validate if credentials are still valid
     */
    boolean validateToken(UUID organizationId) throws Exception;

    /**
     * Get account ID from stored credentials
     */
    String getAccountId(UUID organizationId) throws Exception;
}
