# Implementation Plan: Crossposting to Facebook and Instagram

**Branch**: `003-crossposting-facebook-instagram`  
**Duration**: 5 weeks  
**Start Date**: February 1, 2026  
**Target Release**: March 7, 2026  
**Status**: 🚨 **BLOCKED - Critical Regression (Feb 14, 2026)**

---

## 🔴 CRITICAL BLOCKER - Service Outage

**Date**: February 14, 2026  
**Severity**: P0 - Production Down  
**Impact**: Crossposting completely non-functional for multi-image announcements

**Issue**: Method `postWithMultipleImages()` called but not implemented in `FacebookServiceImpl`  
**Incident Report**: See [INCIDENT_2026-02-14.md](./INCIDENT_2026-02-14.md)

**Immediate Action Required**:
1. **Option A**: Rollback multi-image detection (5 min) - Restores single-image functionality
2. **Option B**: Implement `postWithMultipleImages()` method (30-60 min) - Full fix
3. **Recommended**: Deploy Option A immediately, then Option B as follow-up

**Until Resolved**: 
- ❌ Multi-image crossposting: Broken
- ⚠️ Single-image crossposting: May work if fallback path is reached
- ⚠️ Text-only crossposting: May work if fallback path is reached

---

## Overview

Systematic implementation of crossposting feature with clear milestones, deliverables, and validation gates.

---

## Phase 1: Foundation & Infrastructure (Week 1)

### Goals
- Set up database schema
- Implement secure credential storage
- Configure OAuth integration
- Create service layer skeleton
- Align Meta App Review use cases and testing requirements

### Tasks

#### 1.1 Database Schema
- [ ] Create `social_media_credentials` table
  - Columns: id, organization_id, platform, page_id (encrypted), access_token (encrypted), token_expires_at, is_active, created_at, updated_at
  - Add unique constraint on (organization_id, platform)
  - Create indexes on status, platform

- [ ] Create `social_media_posts` table
  - Columns: id, announcement_id, platform, post_id, status, posted_at, scheduled_for, error_message, custom_caption, engagement_data (JSON), last_sync_at, created_at, updated_at
  - Foreign key: announcement_id → announcements.id
  - Add indexes on (announcement_id, platform), (status, scheduled_for)

- [ ] Run migrations in dev/staging

#### 1.2 Secure Credential Storage
- [ ] Add encryption/decryption service
  - Use Spring Security Crypto or Jasypt
  - Encrypt: page_id, access_token
  - Decrypt only when needed for API calls

- [ ] Create CredentialService
  ```java
  public interface CredentialService {
    void storeCredential(CredentialRequest req);
    SocialMediaCredential getCredential(String platform);
    void refreshToken(String platform);
    void revokeCredential(String platform);
  }
  ```

#### 1.3 OAuth Configuration
- [ ] Register SONAR app on Meta Developer Platform
  - Create Facebook app
  - Create Instagram business account connection
  - Get App ID and App Secret

- [ ] Configure App Settings → Advanced (Meta Dashboard)
  - Authorize callback URL: http://localhost:8080/api/auth/oauth/callback (dev)
  - Use deployed callback URL for production

- [ ] Configure Spring OAuth2
  - Add Facebook/Instagram client registration to `application.properties`
  - Set up OAuth2 redirect URI: `/api/auth/callback/{platform}`
  - Create OAuth controller

- [ ] Set environment variables for Instagram
  - instagram.client-id
  - instagram.client-secret
  - app.oauth.redirect-uri
  - encryption.key

- [ ] Implement OAuth flow
  - Login with Facebook button
  - Login with Instagram button
  - Handle callback and token storage

#### 1.5 Meta App Review Use Cases (NEW)
- [ ] Configure use cases in Meta App Dashboard
  - Use case: "Manage everything on your Page" (Pages API)
  - Use case: "Manage messaging & content on Instagram" (Instagram Graph API)
- [ ] Request required permissions for Pages use case
  - pages_manage_posts
  - pages_read_engagement
  - pages_show_list
- [ ] If Instagram is in scope, request required permissions
  - instagram_basic
  - instagram_content_publish
  - pages_show_list (required to fetch IG business accounts linked to a Page)
- [ ] Confirm business verification status (required for pages_manage_posts)
- [ ] Capture screenshots of use case selections for App Review submission

#### 1.4 Service Layer Skeleton
- [ ] Create `FacebookService` interface
  ```java
  public interface FacebookService {
    String postAnnouncement(Announcement ann, String caption);
    void deletePost(String postId);
    EngagementMetrics getEngagement(String postId);
    boolean validateToken();
  }
  ```

- [ ] Create `InstagramService` interface (same methods)

- [ ] Create `CrosspostService` (orchestrator)
  ```java
  public interface CrosspostService {
    void crosspostAnnouncement(Announcement ann, CrosspostRequest req);
    void syncEngagementMetrics(UUID announcementId);
    void executeScheduledPosts();
  }
  ```

### Deliverables
- Database migrations (flyway/liquibase)
- CredentialService implementation
- OAuth2 configuration
- Service interfaces (no implementation)

### Validation
- [ ] Database schema matches spec
- [ ] Credentials can be encrypted/decrypted
- [ ] OAuth flow redirects to external platform
- [ ] Services compile without errors
- [ ] Graph API Explorer tests completed for Pages use case
  - GET /me/accounts returns managed Pages
  - POST /{page_id}/feed succeeds with test post
  - GET /me/permissions includes pages_manage_posts, pages_read_engagement, pages_show_list

---

## Phase 2: Core Posting Logic (Week 2)

### Goals
- Implement Facebook and Instagram posting
- Handle errors and retries
- Track post status
- Image processing

### Tasks

#### 2.1 Image Processing
- [ ] Add image dependency
  - Maven: `org.springframework.boot:spring-boot-starter-data-rest`
  - Or: ImageMagick integration

- [ ] Create ImageService
  ```java
  public class ImageService {
    public byte[] resizeForFacebook(byte[] image);    // 1200x628
    public byte[] resizeForInstagram(byte[] image);   // 1080x1350
    public String getImageFormat(MultipartFile file);
  }
  ```

- [ ] Add validation
  - Max size: 8MB per platform
  - Supported formats: JPG, PNG
  - Auto-convert if needed

#### 2.2 Facebook Posting Implementation
- [ ] Create FacebookServiceImpl
  ```java
  @Service
  public class FacebookServiceImpl implements FacebookService {
    private final RestTemplate restTemplate;
    private final CredentialService credentialService;
    
    @Override
    public String postAnnouncement(Announcement ann, String caption) {
      // 1. Get credentials
      // 2. Resize image
      // 3. Upload image to FB
      // 4. Create post with image + caption
      // 5. Return post ID
      // 6. Log success/failure
    }
    
    @Override
    public void deletePost(String postId) {
      // DELETE /postId
    }
    
    @Override
    public EngagementMetrics getEngagement(String postId) {
      // GET /postId?fields=likes,comments,shares
    }
  }
  ```

- [ ] Handle Facebook Graph API calls
  - Base URL: `https://graph.facebook.com/v18.0`
  - Endpoints: POST /me/feed, DELETE /{post_id}, GET /{post_id}/insights

#### 2.3 Instagram Posting Implementation
- [ ] Create InstagramServiceImpl
  - Similar to Facebook
  - Use Instagram Graph API (via Meta)
  - Handle hashtag recommendations

#### 2.4 Error Handling & Retry
- [ ] Create RetryService
  ```java
  @Service
  public class RetryService {
    @Retryable(
      value = {ClientException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void executeWithRetry(Supplier<Void> action) { ... }
  }
  ```

- [ ] Handle specific errors
  - 401 Unauthorized → Refresh token, retry
  - 429 Rate limit → Backoff and queue
  - 5xx Server error → Retry later
  - 4xx Client error → Log and fail

#### 2.5 Post Status Tracking
- [ ] Create SocialMediaPostRepository
  ```java
  public interface SocialMediaPostRepository extends JpaRepository<SocialMediaPost, UUID> {
    List<SocialMediaPost> findByStatus(PostStatus status);
    List<SocialMediaPost> findScheduledForBefore(LocalDateTime time);
  }
  ```

- [ ] Update AnnouncementService.create()
  - After announcement published, call CrosspostService
  - Create SocialMediaPost records with status PENDING
  - Update to POSTED/FAILED based on response

### Deliverables
- FacebookServiceImpl
- InstagramServiceImpl
- ImageService
- RetryService
- SocialMediaPostRepository

### Validation
- [ ] Manually test posting to Facebook sandbox
- [ ] Manually test posting to Instagram sandbox
- [ ] Verify image sizes are correct
- [ ] Verify retries work (simulate failures)
- [ ] Database records created correctly

---

## Phase 3: UI Integration (Week 3)

### Goals
- Update CreateAnnouncement form
- Show crossposting options
- Display post status
- Handle custom captions

### Tasks

#### 3.1 Frontend Modifications
- [ ] Update `CreateAnnouncement.js`
  ```jsx
  <div className="crosspost-section">
    <label>
      <input type="checkbox" name="facebook" />
      Post to Facebook
    </label>
    <textarea placeholder="Facebook caption..." />
    
    <label>
      <input type="checkbox" name="instagram" />
      Post to Instagram
    </label>
    <textarea placeholder="Instagram caption with #hashtags..." />
  </div>
  ```

- [ ] Add platform connection flow
  - Check if credentials exist
  - Show "Connect Facebook" button if missing
  - Show "Connected ✓" if exists

#### 3.2 API Integration
- [ ] Update `/api/announcements` POST endpoint
  ```json
  {
    "title": "...",
    "description": "...",
    "image": <file>,
    "crosspost": {
      "facebook": {
        "enabled": true,
        "caption": "..."
      },
      "instagram": {
        "enabled": true,
        "caption": "..."
      }
    }
  }
  ```

- [ ] Update `/api/announcements/{id}` GET
  ```json
  {
    "id": "...",
    "socialPosts": [
      {
        "platform": "FACEBOOK",
        "status": "POSTED",
        "postUrl": "https://...",
        "engagement": { "likes": 45, ... }
      }
    ]
  }
  ```

#### 3.3 Post Status Display
- [ ] Show badges in announcement list
  - "✓ Posted to FB & IG"
  - "⏳ Posting in progress"
  - "⚠ FB post failed (retry)"
  - "🔄 Syncing engagement"

- [ ] Show post links
  - Clickable links to actual FB/IG posts
  - Allow users to preview on platforms

#### 3.4 Credential Management UI
- [ ] Create `/settings/social-media` page
  - Show connected platforms
  - "Connect Facebook" button
  - "Connect Instagram" button
  - "Disconnect" with confirmation
  - Last synced timestamp

### Deliverables
- Updated CreateAnnouncement component
- Settings page for social credentials
- Updated API endpoints
- Status badges and post links

### Validation
- [ ] Form sends correct data structure
- [ ] Credentials properly displayed
- [ ] Post links are valid and clickable
- [ ] No credentials exposed in UI

---

## Phase 4: Advanced Features (Week 4)

### Goals
- Scheduled posting
- Engagement metrics sync
- Background jobs
- Analytics

### Tasks

#### 4.1 Scheduled Posting
- [ ] Add scheduling UI
  ```jsx
  <input type="datetime-local" name="facebookScheduledFor" />
  <input type="datetime-local" name="instagramScheduledFor" />
  ```

- [ ] Create ScheduledPostJob
  ```java
  @Component
  public class ScheduledPostJob {
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void executeScheduledPosts() {
      List<SocialMediaPost> scheduled = repo.findScheduledForBefore(now());
      for (SocialMediaPost post : scheduled) {
        crosspostService.executePost(post);
      }
    }
  }
  ```

- [ ] Update SocialMediaPost status flow
  - PENDING → SCHEDULED (if scheduledFor is in future)
  - SCHEDULED → POSTED (after job executes)

#### 4.2 Engagement Metrics Sync
- [ ] Create EngagementSyncJob
  ```java
  @Scheduled(cron = "0 0 * * * ?") // Daily at midnight
  public void syncEngagementMetrics() {
    List<SocialMediaPost> posted = repo.findByStatus(POSTED);
    for (SocialMediaPost post : posted) {
      EngagementMetrics metrics = facebookService.getEngagement(post.getPostId());
      post.setEngagementData(metrics);
      post.setLastSyncAt(now());
      repo.save(post);
    }
  }
  ```

- [ ] Store engagement data as JSON
  ```json
  {
    "likes": 45,
    "comments": 12,
    "shares": 8,
    "reach": 2341,
    "impressions": 5230,
    "shares": 8,
    "clicks": 123
  }
  ```

#### 4.3 Analytics Dashboard (Optional)
- [ ] Create `/analytics/social-media` page
  - Total posts by platform
  - Engagement over time (chart)
  - Top performing posts
  - Reach vs impressions
  - Export metrics to CSV

### Deliverables
- Scheduled posting logic
- Engagement sync job
- Updated UI with scheduled datetime pickers
- Analytics dashboard (optional)

### Validation
- [ ] Scheduled posts execute on time
- [ ] Engagement metrics update correctly
- [ ] Analytics data is accurate
- [ ] Jobs don't interfere with each other

---

## Phase 5: Polish & Testing (Week 5)

### Goals
- Security audit
- Performance testing
- End-to-end testing
- Documentation

### Tasks

#### 5.1 Security Audit
- [ ] Verify credentials are encrypted at rest
- [ ] Verify no credentials in logs
- [ ] Verify no tokens in error messages
- [ ] Verify CORS/CSRF protection
- [ ] Audit all API endpoints
- [ ] Test privilege escalation attempts

#### 5.2 Performance Testing
- [ ] Load test: 100 simultaneous posts
- [ ] Verify no database deadlocks
- [ ] Check API response times
- [ ] Verify image resizing doesn't block requests
- [ ] Benchmark credential encryption/decryption

#### 5.3 End-to-End Testing
- [ ] Test full flow: Create → Post FB → Post IG
- [ ] Test scheduled posting
- [ ] Test failure and retry
- [ ] Test engagement sync
- [ ] Test credential revocation
- [ ] Test deletion cascading

#### 5.4 Documentation
- [ ] Update backend README
  - Social media setup instructions
  - Facebook/Instagram app registration
  - Environment variables needed
  - Token refresh strategy

- [ ] Update API documentation
  - New endpoints
  - Request/response examples
  - Error codes

- [ ] Create runbook
  - Troubleshooting guide
  - Common errors and fixes
  - Token expiration handling

### Deliverables
- Security audit report
- Performance test results
- E2E test suite
- Updated documentation

### Validation
- [ ] All security issues resolved
- [ ] Performance meets targets
- [ ] All tests pass
- [ ] Documentation complete

---

## Testing Strategy

### Unit Tests
- ImageService (resize correctness)
- FacebookService (API call structure)
- InstagramService (API call structure)
- CredentialService (encryption/decryption)
- RetryService (retry logic)

### Integration Tests
- Post creation flow
- Credential storage and retrieval
- Database transaction handling
- Job execution

### E2E Tests (with sandbox accounts)
- Complete announcement creation with crossposting
- Scheduled posting execution
- Engagement sync
- Error scenarios

### Manual Testing
- Facebook sandbox account
- Instagram test account
- Real image uploads
- Platform-specific features

---

## Risk Management

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Facebook API changes | Medium | High | Use Graph API v18.0 (stable), monitor deprecations |
| Token expiration | High | Medium | Implement refresh 7 days before expiry |
| Rate limiting | Medium | Low | Implement exponential backoff + queue |
| Image format issues | Low | Low | Test with multiple formats, auto-convert |
| Privacy violations | Low | Critical | Require approval before posting, audit logs |

---

## Success Criteria

- ✅ 95% of posts successful (with retries)
- ✅ Posts appear within 5 seconds
- ✅ Scheduled posts execute within ±5 minutes
- ✅ Zero credential leaks
- ✅ UI shows clear post status
- ✅ Engagement metrics update daily
- ✅ All security tests pass
- ✅ Documentation complete

---

## Deployment Strategy

### Development
- Use Facebook/Instagram sandbox
- Test with internal OSAS account
- Verify all flows work

### Staging
- Use test business accounts
- End-to-end testing with real accounts
- Performance testing
- Security audit

### Production
- Use official OSAS Facebook page
- Use official OSAS Instagram account
- Monitor first 100 posts
- Rollback plan ready
