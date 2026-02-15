# Specification: Crossposting to Facebook and Instagram

**Feature**: Announcement crossposting to social media  
**Status**: 🚨 **BROKEN - Critical Regression** (Previously: In Progress)  
**Version**: 1.0.0  
**Created**: January 31, 2026  
**Last Updated**: February 14, 2026

---

## ⚠️ CRITICAL ISSUE - Service Currently Non-Functional

**Date Reported**: February 14, 2026  
**Status**: P0 - Blocking all multi-image crossposting

**Problem**: 
Crossposting feature has stopped working entirely. No new posts are being created on Facebook/Instagram when announcements contain multiple images.

**Root Cause**:
- `FacebookServiceImpl.postAnnouncement()` was modified to detect multiple images in `announcement.getImageUrls()`
- When `imageUrls.size() > 1`, code calls `postWithMultipleImages()` method
- **This method does not exist** - it was referenced but never implemented
- Execution fails when trying to call non-existent method, blocking entire crosspost flow

**Affected Scenarios**:
- ❌ Announcements with 2+ images: **Cannot crosspost** (method missing)
- ✅ Announcements with 1 image: **May work** (uses single-image path)
- ✅ Text-only announcements: **May work** (uses text-only path)

**Fix Options**:
1. **Emergency Rollback**: Remove multi-image detection logic to restore single-image functionality
2. **Complete Implementation**: Implement `postWithMultipleImages()` method per Facebook album API pattern

**Code Location**: 
- File: `src/main/java/com/soanar/service/impl/FacebookServiceImpl.java`
- Line: 78 - calls non-existent `postWithMultipleImages()`
- Missing: Method implementation (lines 200-373 end without this method)

---

## Overview

Enable OSAS announcements to be automatically or manually posted to Facebook and Instagram, extending audience reach beyond the SONAR platform while maintaining a single source of truth in the database.

---

## Goals

1. **Reach**: Extend announcement visibility to Facebook and Instagram audiences
2. **Consistency**: Single announcement creation → multi-platform posting
3. **Control**: Allow selective platform posting (post to FB only, or both, etc.)
4. **Scheduling**: Support immediate and scheduled posts
5. **Tracking**: Monitor engagement and post performance
6. **Safety**: Prevent duplicate posts and handle platform failures gracefully

---

## User Stories

### Story 1: Manual Facebook/Instagram Posting
**As** an OSAS admin  
**I want to** post announcements to Facebook and Instagram from SONAR  
**So that** I can reach students on their preferred social platforms

**Acceptance Criteria:**
- Create announcement with checkbox "Post to Facebook" and "Post to Instagram"
- Upon publication, simultaneously post to selected platforms
- Show platform posting status (pending, success, failed)
- Display social media post URLs for verification

### Story 2: Scheduled Crossposting
**As** an OSAS admin  
**I want to** schedule announcement posting to social media  
**So that** I can post at optimal times for engagement

**Acceptance Criteria:**
- Allow scheduling posts for specific date/time
- Post automatically at scheduled time
- Show scheduled posting status in UI
- Allow cancellation of scheduled posts

### Story 3: Post Customization
**As** an OSAS admin  
**I want to** customize post content per platform  
**So that** I can optimize messaging for each audience

**Acceptance Criteria:**
- Custom text for Facebook (different from Instagram)
- Instagram hashtags and captions
- Automatic image resize for platform requirements
- Preview before posting

### Story 4: Engagement Tracking
**As** an OSAS admin  
**I want to** see social media engagement metrics  
**So that** I can understand audience reach and interaction

**Acceptance Criteria:**
- Display likes, comments, shares counts
- Show post reach and impressions
- Track engagement over time
- Export engagement reports

---

## Non-Functional Requirements

### Performance
- Post to FB/IG should complete in < 10 seconds
- Async processing to not block announcement creation
- Handle rate limiting from platform APIs

### Reliability
- Retry failed posts (exponential backoff)
- Queue posts if platforms temporarily unavailable
- No data loss if platform posting fails

### Security
- Store API tokens securely (encrypted)
- Never expose tokens in logs or UI
- Validate platform credentials before posting
- Audit trail of posts created

### Compliance
- Respect announcement approval status
- Honor student privacy settings
- Allow content moderation before platform posting
- Delete from platforms when announcement deleted

---

## Technical Architecture

### Components

```
┌─────────────────┐
│   Frontend UI   │  (Announcement creation + checkboxes)
└────────┬────────┘
         │
┌────────▼────────────────────┐
│   Backend API               │
│   (AnnouncementController)  │
└────────┬────────────────────┘
         │
┌────────▼──────────────────────┐
│  Crosspost Service            │
│  - FacebookService            │
│  - InstagramService           │
│  - PostQueueService           │
└────────┬──────────────────────┘
         │
┌────────┴──────┬──────────────────┐
│               │                  │
▼               ▼                  ▼
Facebook     Instagram          Queue
Graph API    Graph API       (Scheduled)
```

### Database Schema

**announcements** table (relevant fields for crossposting):
- id (bigint, PK)
- title (varchar)
- description (text)
- status (varchar: PENDING, APPROVED, REJECTED, PUBLISHED)
- image_url (varchar) - Primary/first image URL (legacy, for backwards compatibility)
- **image_urls (jsonb)** - Array of image URLs for multi-image support
  - Stored as PostgreSQL jsonb type
  - Default value: `'[]'::jsonb` (empty array)
  - Example: `["https://storage.url/img1.jpg", "https://storage.url/img2.jpg"]`
  - Used for Facebook albums/carousels and Instagram carousels
  - Java model: `List<String> imageUrls`
- published_at (timestamp)
- start_date (date)
- end_date (date)
- attachments_json (text) - Additional file attachments

**social_media_posts** table:
- id (UUID)
- announcement_id (FK)
- platform (enum: FACEBOOK, INSTAGRAM)
- post_id (platform's post ID)
- status (enum: PENDING, SCHEDULED, POSTED, FAILED, DELETED)
- posted_at (timestamp)
- scheduled_for (timestamp, nullable)
- error_message (text, nullable)
- custom_caption (text, nullable)
- engagement_data (JSON: likes, comments, shares, reach)
- last_sync_at (timestamp)
- created_at (timestamp)
- updated_at (timestamp)

**social_media_credentials** table:
- id (UUID)
- organization_id (FK to users/OSAS)
- platform (enum: FACEBOOK, INSTAGRAM)
- page_id (encrypted)
- access_token (encrypted, stored securely)
- token_expires_at (timestamp)
- is_active (boolean)
- created_at (timestamp)
- updated_at (timestamp)

---

## API Endpoints

### Create Announcement with Crossposting
```
POST /api/announcements
Content-Type: multipart/form-data

Form Data:
  title: "Campus Event"
  description: "Event details..."
  image: <file> (optional, for single image)
  images[]: <file1> (optional, for multiple images)
  images[]: <file2>
  images[]: <file3>
  crosspost: {
    "facebook": {
      "enabled": true,
      "caption": "Join us on campus!"
    },
    "instagram": {
      "enabled": true,
      "caption": "Campus Event 📸 #event",
      "scheduledFor": "2026-02-01T10:00:00Z"
    }
  }

Response:
{
  "id": 123,
  "title": "Campus Event",
  "imageUrl": "https://storage.url/primary.jpg",
  "imageUrls": [
    "https://storage.url/img1.jpg",
    "https://storage.url/img2.jpg",
    "https://storage.url/img3.jpg"
  ],
  "socialPosts": [
    {
      "platform": "FACEBOOK",
      "status": "PENDING"
    }
  ]
}
```

### Crosspost Existing Announcement
```
POST /api/announcements/{id}/crosspost
Content-Type: multipart/form-data

Form Data:
  file: <optional file upload>
  crosspostRequest: {
    "platforms": ["FACEBOOK", "INSTAGRAM"],
    "customCaption": "Optional custom message"
  }

Note: If file is not provided, uses existing announcement.imageUrls or announcement.imageUrl
```

### Get Announcement with Social Posts
```
GET /api/announcements/{id}
Response includes:
{
  "id": "...",
  "title": "...",
  "socialPosts": [
    {
      "platform": "FACEBOOK",
      "status": "POSTED",
      "postId": "123456789",
      "postUrl": "https://facebook.com/...",
      "engagement": {
        "likes": 45,
        "comments": 12,
        "shares": 8,
        "reach": 2341
      }
    }
  ]
}
```

### Sync Engagement Data
```
POST /api/announcements/{id}/sync-engagement
Response: Updated engagement metrics
```

### Manage Credentials
```
POST /api/social-media/connect/{platform}
  - Redirect to platform OAuth
  - Store encrypted token

GET /api/social-media/credentials
  - List connected platforms

DELETE /api/social-media/credentials/{platform}
  - Revoke access
```

---

## Multi-Image Support

### Facebook Album/Carousel Posts
When an announcement contains multiple images in the `image_urls` array:

**Implementation Pattern:**
1. Upload each image to `/{page-id}/photos` with `published=false` parameter
2. Collect photo IDs from each upload response
3. Create a feed post to `/{page-id}/feed` with:
   - `message`: Post caption/description
   - `attached_media`: JSON array of photo objects `[{media_fbid: "123"}, {media_fbid: "456"}]`
   - `access_token`: Page access token

**API Endpoints:**
```
POST /{page-id}/photos
  ?published=false
  &access_token={token}
  
Form Data:
  source: <binary image data>
  
Response:
  { "id": "photo_id_123" }

POST /{page-id}/feed
  ?message={caption}
  &attached_media=[{"media_fbid":"photo_id_123"},{"media_fbid":"photo_id_456"}]
  &access_token={token}
  
Response:
  { "id": "post_id_789" }
```

**Service Logic:**
- If `announcement.getImageUrls().size() > 1`: Use multi-image album flow
- If `announcement.getImageUrls().size() == 1`: Post single photo to `/{page-id}/photos`
- If `announcement.getImageUrl()` exists (legacy): Use single photo flow
- If no images: Post text-only to `/{page-id}/feed`

### Instagram Carousel Posts
For Instagram multi-image posts (carousels):

**Implementation Pattern:**
1. Create media containers for each image via `/{instagram-account-id}/media`
2. Upload carousel container referencing all media container IDs
3. Publish the carousel container

**API Endpoints:**
```
POST /{ig-user-id}/media
  ?image_url={image1_url}
  &is_carousel_item=true
  &access_token={token}
  
Response:
  { "id": "container_id_1" }

POST /{ig-user-id}/media
  ?media_type=CAROUSEL
  &caption={caption}
  &children={container_id_1},{container_id_2}
  &access_token={token}
  
Response:
  { "id": "carousel_container_id" }

POST /{ig-user-id}/media_publish
  ?creation_id={carousel_container_id}
  &access_token={token}
  
Response:
  { "id": "published_media_id" }
```

**Image Requirements:**
- Facebook: Minimum 200x200px, recommended 1200x628px for optimal display
- Instagram: Minimum 320x320px, recommended 1080x1080px (square) or 1080x1350px (portrait)
- All images resized and compressed by `ImageService` before upload

---

## Implementation Phases

### Phase 1: Foundation (Week 1)
- Database schema for social posts and credentials
- Secure credential storage (encryption)
- OAuth integration setup (Facebook/Instagram)
- Basic service layer (FacebookService, InstagramService)

### Phase 2: Core Posting (Week 2)
- Manual posting to Facebook and Instagram
- Image upload and resizing
- Error handling and retry logic
- Post status tracking

### Phase 3: UI Integration (Week 3)
- Announcement creation form modifications
- Platform selection checkboxes
- Custom caption editors
- Post status display

### Phase 4: Advanced Features (Week 4)
- Scheduled posting with background job
- Engagement sync (likes, comments, reach)
- Analytics dashboard
- Post deletion sync

### Phase 5: Polish & Testing (Week 5)
- Rate limiting handling
- Performance optimization
- Security audit
- E2E testing

---

## Dependencies

### External APIs
- Facebook Graph API (Meta Business SDK)
- Instagram Graph API (via Meta)
- OAuth 2.0 for authentication

### Libraries
- `facebook4j` or `restfb` (Facebook API client) OR use HTTP REST
- `instagram4j` OR custom REST client
- `spring-security-oauth2-client` (already in use)
- Encryption library (Spring Security Crypto or Jasypt)

### Services
- Background job scheduler (Spring Scheduler or Quartz)
- Message queue (optional: Redis/RabbitMQ for reliability)
- Image processing (ImageMagick or Java ImageIO)

---

## Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Platform API rate limiting | Posts fail | Implement queue + exponential backoff |
| Token expiration | Posts can't send | Refresh tokens before expiry |
| Image format incompatibility | Upload fails | Auto-convert to platform specs |
| Data sync issues | Engagement metrics stale | Scheduled sync jobs |
| Privacy/compliance | FERPA violations | Disable for student data posts |
| Duplicate posts | Content duplication | Idempotency keys + uniqueness checks |

---

## Success Criteria

- ✅ Announcements posted to Facebook within 5 seconds
- ✅ Announcements posted to Instagram within 5 seconds
- ✅ 99% post delivery rate (with retries)
- ✅ Engagement metrics synced daily
- ✅ Zero credential leaks in logs/errors
- ✅ UI shows clear post status and links
- ✅ Users can schedule posts for future times
- ✅ Scheduled posts execute on time ±5 minutes

---

## Open Questions

1. Should we post immediately or wait for OSAS approval?
   - *Proposed: Post when announcement status = PUBLISHED*

2. Should deleted SONAR announcements also delete from FB/IG?
   - *Proposed: Yes, maintain sync*

3. What image dimensions for each platform?
   - *Proposed: Auto-resize with aspect ratio preservation*

4. How often to sync engagement metrics?
   - *Proposed: Daily, or on-demand via button*

5. Should comments on social posts sync back to SONAR?
   - *Proposed: Future phase (out of scope)*
