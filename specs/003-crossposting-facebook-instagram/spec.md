# Specification: Crossposting to Facebook and Instagram

**Feature**: Announcement crossposting to social media  
**Status**: Planned  
**Version**: 1.0.0  
**Created**: January 31, 2026

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
{
  "title": "Campus Event",
  "description": "...",
  "image": <file>,
  "crosspost": {
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
}
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
