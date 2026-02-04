# Tasks: Crossposting to Facebook and Instagram

**Branch**: `003-crossposting-facebook-instagram`  
**Status**: Not Started  
**Assignee**: TBD

---

## Phase 1: Foundation (Week 1)

### Database Setup
- [ ] **Task 1.1**: Create social_media_credentials table
  - File: `src/main/resources/db/migration/V*.sql`
  - Columns: id, organization_id, platform, page_id (encrypted), access_token (encrypted), token_expires_at, is_active, created_at, updated_at
  - Constraints: UNIQUE(organization_id, platform), FK to users table
  - **Acceptance**: Table exists with correct schema
  - **Effort**: 1 hour

- [ ] **Task 1.2**: Create social_media_posts table
  - Columns: id, announcement_id, platform, post_id, status, posted_at, scheduled_for, error_message, custom_caption, engagement_data (JSONB), last_sync_at, created_at, updated_at
  - Constraints: FK to announcements, indexes on (announcement_id, platform), (status, scheduled_for)
  - **Acceptance**: Table exists with correct schema and indexes
  - **Effort**: 1 hour

- [ ] **Task 1.3**: Create JPA entities
  - File: `src/main/java/com/soanar/entity/SocialMediaCredential.java`
  - File: `src/main/java/com/soanar/entity/SocialMediaPost.java`
  - With enums: Platform (FACEBOOK, INSTAGRAM), PostStatus (PENDING, SCHEDULED, POSTED, FAILED, DELETED)
  - **Acceptance**: Entities compile, annotations correct
  - **Effort**: 45 minutes

- [ ] **Task 1.4**: Create repositories
  - File: `src/main/java/com/soanar/repository/SocialMediaCredentialRepository.java`
  - File: `src/main/java/com/soanar/repository/SocialMediaPostRepository.java`
  - Custom query methods: findByPlatform, findScheduledForBefore, findByStatus
  - **Acceptance**: Repositories extend JpaRepository, custom methods defined
  - **Effort**: 30 minutes

### Credential Management
- [ ] **Task 1.5**: Create CredentialService interface
  - File: `src/main/java/com/soanar/service/CredentialService.java`
  - Methods: storeCredential, getCredential, refreshToken, revokeCredential, isConnected
  - **Acceptance**: Interface defined with JavaDoc
  - **Effort**: 15 minutes

- [ ] **Task 1.6**: Implement credential encryption/decryption
  - File: `src/main/java/com/soanar/service/impl/EncryptionService.java`
  - Use Spring Security Crypto or Jasypt
  - Methods: encrypt(String), decrypt(String)
  - **Acceptance**: Can encrypt and decrypt without loss
  - **Effort**: 1 hour

- [ ] **Task 1.7**: Implement CredentialService
  - File: `src/main/java/com/soanar/service/impl/CredentialServiceImpl.java`
  - Store encrypted credentials in database
  - Handle token expiration refresh
  - **Acceptance**: Credentials persisted and retrievable
  - **Effort**: 1.5 hours

### OAuth Configuration
- [ ] **Task 1.8**: Register apps on Meta platform
  - Create Facebook app: https://developers.facebook.com
  - Create Instagram business connection
  - Get App ID and App Secret
  - Set OAuth redirect URI: `http://localhost:8080/api/auth/callback/facebook`
  - **Acceptance**: App IDs and secrets obtained
  - **Effort**: 30 minutes (manual, outside code)

- [ ] **Task 1.9**: Configure Spring OAuth2
  - File: `src/main/resources/application.properties`
  - Add: spring.security.oauth2.client.registration.facebook.*
  - Add: spring.security.oauth2.client.registration.instagram.*
  - **Acceptance**: Properties set, no compilation errors
  - **Effort**: 30 minutes

- [ ] **Task 1.10**: Create OAuth controller
  - File: `src/main/java/com/soanar/controller/OAuthController.java`
  - Endpoints: GET /auth/login/{platform}, GET /auth/callback/{platform}
  - Handle token storage via CredentialService
  - Redirect to success/failure pages
  - **Acceptance**: OAuth flow completes, credentials stored
  - **Effort**: 1.5 hours

### Service Layer Skeleton
- [ ] **Task 1.11**: Create FacebookService interface
  - File: `src/main/java/com/soanar/service/FacebookService.java`
  - Methods: postAnnouncement, deletePost, getEngagement, validateToken
  - **Acceptance**: Interface compiles with correct signatures
  - **Effort**: 15 minutes

- [ ] **Task 1.12**: Create InstagramService interface
  - File: `src/main/java/com/soanar/service/InstagramService.java`
  - Same methods as FacebookService
  - **Acceptance**: Interface compiles
  - **Effort**: 10 minutes

- [ ] **Task 1.13**: Create CrosspostService interface
  - File: `src/main/java/com/soanar/service/CrosspostService.java`
  - Methods: crosspostAnnouncement, syncEngagementMetrics, executeScheduledPosts
  - **Acceptance**: Interface compiles
  - **Effort**: 10 minutes

---

## Phase 2: Core Posting (Week 2)

### Image Processing
- [ ] **Task 2.1**: Add image processing dependency
  - Add to pom.xml: ImageMagick or Java ImageIO
  - **Acceptance**: Dependency resolves without conflict
  - **Effort**: 15 minutes

- [ ] **Task 2.2**: Create ImageService
  - File: `src/main/java/com/soanar/service/ImageService.java`
  - Methods: resizeForFacebook (1200x628), resizeForInstagram (1080x1350), validate
  - Handle aspect ratio preservation
  - **Acceptance**: Images resized correctly, file size reduced
  - **Effort**: 1.5 hours

- [ ] **Task 2.3**: Add image validation
  - Max size: 8MB
  - Supported formats: JPG, PNG, GIF
  - Auto-convert WEBP to JPG if needed
  - **Acceptance**: Invalid images rejected with clear error
  - **Effort**: 45 minutes

### Facebook API Integration
- [ ] **Task 2.4**: Create FacebookServiceImpl - postAnnouncement
  - File: `src/main/java/com/soanar/service/impl/FacebookServiceImpl.java`
  - Get credentials from CredentialService
  - Resize image via ImageService
  - Upload image to Facebook
  - Create post: POST /me/feed?message=...&attached_media=...
  - Return post ID
  - Log success/failure
  - **Acceptance**: Test post appears on sandbox account
  - **Effort**: 2 hours

- [ ] **Task 2.5**: Create FacebookServiceImpl - deletePost
  - Method: DELETE /{postId}
  - Handle 404 (already deleted)
  - Update database status to DELETED
  - **Acceptance**: Posts deleted successfully
  - **Effort**: 30 minutes

- [ ] **Task 2.6**: Create FacebookServiceImpl - getEngagement
  - Method: GET /{postId}/insights?fields=engaged_users,post_impressions,post_clicks
  - Parse response into EngagementMetrics DTO
  - **Acceptance**: Metrics retrieved and mapped correctly
  - **Effort**: 45 minutes

- [ ] **Task 2.7**: Create FacebookServiceImpl - validateToken
  - Method: GET /me?access_token=...
  - Return true if valid, false if expired
  - **Acceptance**: Token validation works
  - **Effort**: 15 minutes

### Instagram API Integration
- [ ] **Task 2.8**: Create InstagramServiceImpl
  - File: `src/main/java/com/soanar/service/impl/InstagramServiceImpl.java`
  - Implement same methods as Facebook
  - Use Instagram Graph API endpoint variations
  - Handle Instagram-specific requirements (hashtags, captions)
  - **Acceptance**: Test post appears on sandbox account
  - **Effort**: 2 hours

### Error Handling & Retries
- [ ] **Task 2.9**: Create RetryService
  - File: `src/main/java/com/soanar/service/RetryService.java`
  - Use @Retryable annotation
  - Exponential backoff: delay 1s, multiplier 2.0, max 3 attempts
  - **Acceptance**: Failed calls retry automatically
  - **Effort**: 1 hour

- [ ] **Task 2.10**: Implement error handlers
  - Handle 401 Unauthorized → Refresh token, retry
  - Handle 429 Rate Limited → Queue and backoff
  - Handle 5xx Server errors → Retry later
  - Handle 4xx Client errors → Log and fail
  - Custom exceptions: PlatformException, AuthException, RateLimitException
  - **Acceptance**: All error types handled gracefully
  - **Effort**: 1.5 hours

### Post Status Tracking
- [ ] **Task 2.11**: Update AnnouncementService.create()
  - After announcement published, create SocialMediaPost records
  - Call CrosspostService asynchronously
  - Update records with status: PENDING → POSTED/FAILED
  - **Acceptance**: Database records created for each platform
  - **Effort**: 1 hour

- [ ] **Task 2.12**: Implement CrosspostServiceImpl
  - File: `src/main/java/com/soanar/service/impl/CrosspostServiceImpl.java`
  - Orchestrate Facebook and Instagram posting
  - Save post details to database
  - Handle partial failures (FB succeeds, IG fails)
  - **Acceptance**: Posts created on both platforms, status tracked
  - **Effort**: 1.5 hours

---

## Phase 3: UI Integration (Week 3)

### Frontend Updates
- [ ] **Task 3.1**: Update CreateAnnouncement.js
  - Add crosspost checkbox section
  - Facebook: enable/disable, custom caption textarea
  - Instagram: enable/disable, custom caption textarea
  - **Acceptance**: Form renders correctly
  - **Effort**: 45 minutes

- [ ] **Task 3.2**: Add credential check
  - On component mount, check if platforms connected
  - Show "Connect Facebook" button if missing
  - Show "✓ Connected" if exists
  - **Acceptance**: UI reflects connection status
  - **Effort**: 30 minutes

- [ ] **Task 3.3**: Create OAuth flow UI
  - Create `/settings/social-media` page
  - "Connect Facebook" button → OAuth redirect
  - "Connect Instagram" button → OAuth redirect
  - List connected platforms with "Disconnect" button
  - **Acceptance**: Users can connect/disconnect platforms
  - **Effort**: 1.5 hours

### API Updates
- [ ] **Task 3.4**: Update POST /api/announcements endpoint
  - Accept crosspost config in request body
  - Pass to CrosspostService
  - Return success/failure for each platform
  - **Acceptance**: API accepts and processes crosspost data
  - **Effort**: 1 hour

- [ ] **Task 3.5**: Update GET /api/announcements/{id} endpoint
  - Include socialPosts array in response
  - Each post shows: platform, status, postUrl, engagement
  - **Acceptance**: Response includes social post data
  - **Effort**: 45 minutes

### Status Display
- [ ] **Task 3.6**: Add post status badges
  - Component: `<SocialMediaStatus announcement={ann} />`
  - Badge text: "✓ Posted to FB & IG", "⏳ Posting...", "⚠ Failed"
  - Clickable links to actual posts
  - **Acceptance**: Status shows correctly, links are valid
  - **Effort**: 1 hour

---

## Phase 4: Advanced Features (Week 4)

### Scheduled Posting
- [ ] **Task 4.1**: Add scheduling UI
  - DateTime picker for Facebook: `facebookScheduledFor`
  - DateTime picker for Instagram: `instagramScheduledFor`
  - Optional fields (only if user wants to schedule)
  - **Acceptance**: Form accepts datetime values
  - **Effort**: 45 minutes

- [ ] **Task 4.2**: Create ScheduledPostJob
  - File: `src/main/java/com/soanar/job/ScheduledPostJob.java`
  - @Scheduled(fixedRate = 300000) // Every 5 minutes
  - Find posts with status SCHEDULED and scheduledFor <= now()
  - Execute posting for each
  - Update status to POSTED/FAILED
  - **Acceptance**: Posts execute at scheduled time
  - **Effort**: 1.5 hours

- [ ] **Task 4.3**: Update CrosspostService
  - If scheduledFor is provided, set status to SCHEDULED instead of PENDING
  - Store scheduledFor timestamp
  - **Acceptance**: Scheduled posts don't post immediately
  - **Effort**: 30 minutes

### Engagement Metrics Sync
- [ ] **Task 4.4**: Create EngagementSyncJob
  - File: `src/main/java/com/soanar/job/EngagementSyncJob.java`
  - @Scheduled(cron = "0 0 * * * ?") // Daily at midnight
  - Find all POSTED posts
  - Call FacebookService.getEngagement() and InstagramService.getEngagement()
  - Update engagement_data JSON column
  - Update last_sync_at timestamp
  - **Acceptance**: Engagement metrics update daily
  - **Effort**: 1.5 hours

- [ ] **Task 4.5**: Create EngagementMetrics DTO
  - File: `src/main/java/com/soanar/dto/EngagementMetrics.java`
  - Fields: likes, comments, shares, reach, impressions, clicks
  - Serialize to JSON for database storage
  - **Acceptance**: Metrics stored and retrievable
  - **Effort**: 30 minutes

### Analytics (Optional)
- [ ] **Task 4.6**: Create analytics API endpoint
  - GET /api/analytics/social-media
  - Return: total posts by platform, engagement summary, top posts
  - **Acceptance**: Analytics data returned correctly
  - **Effort**: 1.5 hours

- [ ] **Task 4.7**: Create analytics dashboard page
  - File: `src/components/Analytics/SocialMediaAnalytics.js`
  - Charts: Posts over time, engagement by platform, top performers
  - **Acceptance**: Dashboard renders with data
  - **Effort**: 1.5 hours

---

## Phase 5: Testing & Polish (Week 5)

### Security
- [ ] **Task 5.1**: Security audit - credentials
  - Verify no credentials logged
  - Verify no tokens in error messages
  - Verify encryption working correctly
  - **Acceptance**: Audit report clean
  - **Effort**: 1 hour

- [ ] **Task 5.2**: Security audit - API endpoints
  - Verify CORS properly configured
  - Verify CSRF tokens present
  - Test unauthorized access attempts
  - **Acceptance**: All endpoints properly secured
  - **Effort**: 1 hour

### Testing
- [ ] **Task 5.3**: Write unit tests
  - ImageService tests
  - CredentialService tests (encryption/decryption)
  - RetryService tests
  - **Acceptance**: 90%+ code coverage, all tests pass
  - **Effort**: 2 hours

- [ ] **Task 5.4**: Write integration tests
  - Credential storage/retrieval
  - Post creation and database persistence
  - Status transitions
  - **Acceptance**: All integration tests pass
  - **Effort**: 2 hours

- [ ] **Task 5.5**: Write E2E tests
  - Complete flow: Create announcement → Post FB → Post IG
  - Test scheduled posting
  - Test engagement sync
  - **Acceptance**: E2E test suite passes
  - **Effort**: 2 hours

### Documentation
- [ ] **Task 5.6**: Update README
  - Facebook/Instagram app registration steps
  - Environment variables needed
  - OAuth setup instructions
  - **Acceptance**: Clear instructions for setup
  - **Effort**: 1 hour

- [ ] **Task 5.7**: Write API documentation
  - New endpoints: POST /announcements (with crosspost), GET /announcements/{id}
  - New endpoints: POST /auth/login/{platform}, GET /auth/callback/{platform}
  - Request/response examples
  - Error codes and handling
  - **Acceptance**: Documentation complete and examples work
  - **Effort**: 1.5 hours

- [ ] **Task 5.8**: Create runbook
  - Token expiration handling
  - Rate limiting recovery
  - Troubleshooting guide
  - Common errors and fixes
  - **Acceptance**: Runbook complete
  - **Effort**: 1 hour

### Deployment
- [ ] **Task 5.9**: Prepare production deployment
  - Production Facebook page
  - Production Instagram account
  - Verify all credentials
  - Rollback plan documented
  - **Acceptance**: Ready for production
  - **Effort**: 1 hour

---

## Summary

- **Total Tasks**: 59
- **Total Effort**: ~40-45 hours
- **Timeline**: 5 weeks (8-9 hours/week)
- **Dependencies**: Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5

---

## Notes

- Use sandbox accounts for development
- Test with test business accounts before production
- Monitor first 100 posts in production
- Keep rollback procedure handy
