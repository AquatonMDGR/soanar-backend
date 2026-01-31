package com.soanar.service;

import com.soanar.model.Announcement;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Service for posting announcements to Facebook
 */
public interface FacebookService {

    /**
     * Post announcement to Facebook
     * @param announcement The announcement to post
     * @param caption Custom caption for Facebook
     * @param image Optional image to attach (will be resized if provided)
     * @return Platform-specific post ID
     */
    String postAnnouncement(Announcement announcement, String caption, MultipartFile image) throws Exception;

    /**
     * Delete a post from Facebook
     */
    void deletePost(String postId) throws Exception;

    /**
     * Get engagement metrics for a post (likes, comments, shares, reach)
     */
    Map<String, Object> getEngagement(String postId) throws Exception;

    /**
     * Validate if credentials are still valid
     */
    boolean validateToken() throws Exception;

    /**
     * Get page ID from stored credentials
     */
    String getPageId() throws Exception;
}
