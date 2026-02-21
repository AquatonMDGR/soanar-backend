package com.soanar.service.impl;

import com.soanar.dto.CrosspostRequest;
import com.soanar.model.Announcement;
import com.soanar.model.SocialMediaCredential;
import com.soanar.model.SocialMediaPost;
import com.soanar.repository.SocialMediaPostRepository;
import com.soanar.service.CrosspostService;
import com.soanar.service.FacebookService;
import com.soanar.service.InstagramService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrator service for crossposting announcements to social media
 */
@Service
@Transactional
public class CrosspostServiceImpl implements CrosspostService {

    private static final Logger logger = LoggerFactory.getLogger(CrosspostServiceImpl.class);

    private final SocialMediaPostRepository postRepository;
    private final FacebookService facebookService;
    private final InstagramService instagramService;
    private final RetryService retryService;

    public CrosspostServiceImpl(SocialMediaPostRepository postRepository,
                              FacebookService facebookService,
                              InstagramService instagramService,
                              RetryService retryService) {
        this.postRepository = postRepository;
        this.facebookService = facebookService;
        this.instagramService = instagramService;
        this.retryService = retryService;
    }

    @Override
    @Async
    public void crosspostAnnouncement(Announcement announcement, CrosspostRequest request, List<MultipartFile> images, UUID organizationId) throws Exception {
        logger.info("Starting crosspost for announcement: {}", announcement.getId());

        if (request.getFacebook() != null && request.getFacebook().getEnabled()) {
            postToPlatform(announcement, request.getFacebook().getCaption(), images, "facebook", organizationId);
        }

        if (request.getInstagram() != null && request.getInstagram().getEnabled()) {
            postToPlatform(announcement, request.getInstagram().getCaption(), images, "instagram", organizationId);
        }

        logger.info("Crosspost completed for announcement: {}", announcement.getId());
    }

    @Override
    public SocialMediaPost postToPlatform(Announcement announcement, String caption,
                                         List<MultipartFile> images, String platform, UUID organizationId) throws Exception {
        logger.info("Posting to {}: {}", platform, announcement.getId());

        SocialMediaCredential.Platform platformEnum = SocialMediaCredential.Platform.valueOf(platform.toUpperCase());

        // Create database record
        SocialMediaPost post = new SocialMediaPost(announcement, platformEnum);
        post.setCustomCaption(caption);
        post.setStatus(SocialMediaPost.PostStatus.PENDING);
        post.setCreatedAt(Instant.now());
        post = postRepository.save(post);

        try {
            // Post to platform
            String postId;
            if (platformEnum == SocialMediaCredential.Platform.FACEBOOK) {
                postId = retryService.executeWithRetry(() ->
                        facebookService.postAnnouncement(announcement, caption, images, organizationId)
                );
            } else {
                postId = retryService.executeWithRetry(() ->
                        instagramService.postAnnouncement(announcement, caption, images, organizationId)
                );
            }

            // Update record
            post.setPostId(postId);
            post.setStatus(SocialMediaPost.PostStatus.POSTED);
            post.setPostedAt(Instant.now());
            post.setUpdatedAt(Instant.now());

            logger.info("Successfully posted to {}: {}", platform, postId);
        } catch (Exception e) {
            logger.error("Failed to post to {}: {}", platform, e.getMessage(), e);

            post.setStatus(SocialMediaPost.PostStatus.FAILED);
            post.setErrorMessage(e.getMessage());
            post.setUpdatedAt(Instant.now());
        }

        postRepository.save(post);
        return post;
    }

    @Override
    public void syncEngagementMetrics(Long announcementId) throws Exception {
        logger.info("Syncing engagement metrics for announcement: {}", announcementId);

        List<SocialMediaPost> posts = postRepository.findByAnnouncementId(announcementId);

        for (SocialMediaPost post : posts) {
            try {
                if (post.getPostId() == null || post.getStatus() != SocialMediaPost.PostStatus.POSTED) {
                    continue;
                }

                UUID orgId = resolveOrganizationId(post.getAnnouncement());
                Map<String, Object> engagement;
                if (post.getPlatform() == SocialMediaCredential.Platform.FACEBOOK) {
                    engagement = facebookService.getEngagement(post.getPostId(), orgId);
                } else {
                    engagement = instagramService.getEngagement(post.getPostId(), orgId);
                }

                post.setEngagementData(engagement);
                post.setLastSyncAt(Instant.now());
                post.setUpdatedAt(Instant.now());
                postRepository.save(post);

                logger.info("Synced engagement for {}: {}", post.getPlatform(), post.getPostId());
            } catch (Exception e) {
                logger.error("Failed to sync engagement for post: {}", post.getId(), e);
            }
        }
    }

    @Override
    public void executeScheduledPosts() throws Exception {
        logger.info("Executing scheduled posts");

        List<SocialMediaPost> scheduledPosts = postRepository.findByStatusAndScheduledForLessThanEqual(
                SocialMediaPost.PostStatus.SCHEDULED,
                Instant.now()
        );

        logger.info("Found {} scheduled posts to execute", scheduledPosts.size());

        for (SocialMediaPost post : scheduledPosts) {
            try {
                UUID orgId = resolveOrganizationId(post.getAnnouncement());
                String postId;
                if (post.getPlatform() == SocialMediaCredential.Platform.FACEBOOK) {
                    postId = facebookService.postAnnouncement(post.getAnnouncement(), post.getCustomCaption(), Collections.emptyList(), orgId);
                } else {
                    postId = instagramService.postAnnouncement(post.getAnnouncement(), post.getCustomCaption(), Collections.emptyList(), orgId);
                }

                post.setPostId(postId);
                post.setStatus(SocialMediaPost.PostStatus.POSTED);
                post.setPostedAt(Instant.now());
                post.setUpdatedAt(Instant.now());

                logger.info("Executed scheduled post to {}: {}", post.getPlatform(), postId);
            } catch (Exception e) {
                logger.error("Failed to execute scheduled post: {}", post.getId(), e);

                post.setStatus(SocialMediaPost.PostStatus.FAILED);
                post.setErrorMessage(e.getMessage());
                post.setUpdatedAt(Instant.now());
            }

            postRepository.save(post);
        }
    }

    @Override
    public void retryFailedPosts() throws Exception {
        logger.info("Retrying failed posts");

        // Find posts failed in last 24 hours
        Instant twentyFourHoursAgo = Instant.now().minusSeconds(86400);
        List<SocialMediaPost> failedPosts = postRepository.findByStatusAndCreatedAtAfter(
                SocialMediaPost.PostStatus.FAILED,
                twentyFourHoursAgo
        );

        logger.info("Found {} failed posts to retry", failedPosts.size());

        for (SocialMediaPost post : failedPosts) {
            try {
                UUID orgId = resolveOrganizationId(post.getAnnouncement());
                String postId;
                if (post.getPlatform() == SocialMediaCredential.Platform.FACEBOOK) {
                    postId = facebookService.postAnnouncement(post.getAnnouncement(), post.getCustomCaption(), Collections.emptyList(), orgId);
                } else {
                    postId = instagramService.postAnnouncement(post.getAnnouncement(), post.getCustomCaption(), Collections.emptyList(), orgId);
                }

                post.setPostId(postId);
                post.setStatus(SocialMediaPost.PostStatus.POSTED);
                post.setPostedAt(Instant.now());
                post.setUpdatedAt(Instant.now());

                logger.info("Retried post successful for {}: {}", post.getPlatform(), postId);
            } catch (Exception e) {
                logger.error("Retry failed for post: {}", post.getId(), e);
                post.setErrorMessage(e.getMessage());
                post.setUpdatedAt(Instant.now());
            }

            postRepository.save(post);
        }
    }

    @Override
    public void deleteFromAllPlatforms(Long announcementId) throws Exception {
        logger.info("Deleting announcement from all platforms: {}", announcementId);

        List<SocialMediaPost> posts = postRepository.findByAnnouncementId(announcementId);

        for (SocialMediaPost post : posts) {
            try {
                if (post.getPostId() == null) {
                    continue;
                }

                UUID orgId = resolveOrganizationId(post.getAnnouncement());
                if (post.getPlatform() == SocialMediaCredential.Platform.FACEBOOK) {
                    facebookService.deletePost(post.getPostId(), orgId);
                } else {
                    instagramService.deletePost(post.getPostId(), orgId);
                }

                post.setStatus(SocialMediaPost.PostStatus.DELETED);
                post.setUpdatedAt(Instant.now());

                logger.info("Deleted from {}: {}", post.getPlatform(), post.getPostId());
            } catch (Exception e) {
                logger.error("Failed to delete from {}: {}", post.getPlatform(), e.getMessage(), e);
            }

            postRepository.save(post);
        }
    }

    @Override
    public List<SocialMediaPost> getPostsForAnnouncement(Long announcementId) {
        logger.debug("Getting posts for announcement: {}", announcementId);
        return postRepository.findByAnnouncementId(announcementId);
    }

    @Override
    public SocialMediaPost getPost(UUID postId) {
        logger.debug("Getting post: {}", postId);
        return postRepository.findById(postId).orElse(null);
    }

    private UUID resolveOrganizationId(Announcement announcement) {
        if (announcement == null || announcement.getPostedBy() == null || announcement.getPostedBy().getSchoolEmail() == null) {
            throw new IllegalStateException("Unable to resolve user context for crossposting");
        }
        String email = announcement.getPostedBy().getSchoolEmail();
        return UUID.nameUUIDFromBytes(email.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }
}
