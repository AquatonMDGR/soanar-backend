package com.soanar.service.impl;

import com.soanar.service.CrosspostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled jobs for social media posting and retries.
 */
@Component
public class SocialMediaScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SocialMediaScheduler.class);

    private final CrosspostService crosspostService;

    public SocialMediaScheduler(CrosspostService crosspostService) {
        this.crosspostService = crosspostService;
    }

    @Scheduled(fixedDelayString = "${socialmedia.scheduled-posts.interval-ms:300000}")
    public void runScheduledPosts() {
        try {
            crosspostService.executeScheduledPosts();
        } catch (Exception e) {
            logger.error("Scheduled social media posting failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${socialmedia.retry-posts.interval-ms:900000}")
    public void retryFailedPosts() {
        try {
            crosspostService.retryFailedPosts();
        } catch (Exception e) {
            logger.error("Retrying failed social media posts failed", e);
        }
    }
}
