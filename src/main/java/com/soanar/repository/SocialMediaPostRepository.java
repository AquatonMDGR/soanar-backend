package com.soanar.repository;

import com.soanar.model.SocialMediaPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialMediaPostRepository extends JpaRepository<SocialMediaPost, UUID> {

    /**
     * Find posts by announcement ID
     */
    List<SocialMediaPost> findByAnnouncementId(Long announcementId);

    /**
     * Find posts by announcement and platform
     */
    Optional<SocialMediaPost> findByAnnouncementIdAndPlatform(Long announcementId, SocialMediaPost.PostStatus platform);

    /**
     * Find pending posts (not yet posted)
     */
    List<SocialMediaPost> findByStatus(SocialMediaPost.PostStatus status);

    /**
     * Find scheduled posts ready to be posted
     */
    List<SocialMediaPost> findByStatusAndScheduledForLessThanEqual(SocialMediaPost.PostStatus status, Instant now);

    /**
     * Find scheduled posts by scheduled time
     */
    List<SocialMediaPost> findByScheduledForBefore(Instant time);

    /**
     * Find posted items for engagement sync
     */
    List<SocialMediaPost> findByStatusAndLastSyncAtBefore(SocialMediaPost.PostStatus status, Instant beforeTime);

    /**
     * Find failed posts for retry
     */
    List<SocialMediaPost> findByStatusAndCreatedAtAfter(SocialMediaPost.PostStatus status, Instant afterTime);
}
