# Feature Spec: Announcement Image Upload

**Branch**: `001-announcement-image-upload`  
**Status**: In Progress  
**Created**: 2026-01-29  
**Owner**: Backend Team

---

## Overview

Enable users to upload images when creating announcements. Images will be stored in Supabase Storage and the announcement record will store the public URL in the database.

## User Stories

### As a Student Organization
> I want to upload event posters and promotional images with my announcements  
> So that students can see visual content about our events

**Acceptance Criteria**:
- ✅ Can select image file (JPG, PNG, GIF) up to 5MB
- ✅ Image uploads to Supabase Storage
- ✅ Database stores the public URL
- ✅ Image displays in announcement feed
- ✅ Upload failures show clear error messages

### As OSAS/Academic Staff
> I want to attach official graphics and notices to announcements  
> So that information is clear and properly branded

**Acceptance Criteria**:
- ✅ Same upload capability as student organizations
- ✅ No waiting for approval to see uploaded image (direct publish)

## Technical Requirements

### Backend Changes

#### 1. Environment Configuration
**File**: `.env`
```env
SUPABASE_URL=https://[project-ref].supabase.co
SUPABASE_SERVICE_ROLE_KEY=[service-role-key]
```

**File**: `application.properties`
```properties
supabase.url=${SUPABASE_URL}
supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY}
```

#### 2. Service Layer
**File**: `AnnouncementService.java`

**New Method**:
```java
public String uploadToSupabase(MultipartFile file, String bucketName, String folderPath) throws IOException
```

**Functionality**:
- Validate file size (max 5MB)
- Validate file type (JPG, PNG, GIF only)
- Generate unique filename using UUID
- Upload to Supabase Storage via REST API
- Return public URL

**Error Handling**:
- `IOException` if upload fails
- `IllegalArgumentException` if file invalid
- Log all errors for debugging

#### 3. Controller Updates
**File**: `AnnouncementController.java`

**Current Issue**: Calls non-existent method  
**Solution**: Method will now exist in service layer

**Error Handling**:
- Continue announcement creation if upload fails (log warning)
- Return clear error response if entire request fails

### API Contract

**Endpoint**: `POST /api/announcements`  
**Content-Type**: `multipart/form-data`

**Request Parameters**:
```
title: string (required)
description: string (required)
file: multipart file (optional, max 5MB)
startDate: string YYYY-MM-DD (optional)
endDate: string YYYY-MM-DD (optional)
```

**Response** (Success - 200):
```json
{
  "id": 123,
  "title": "Campus Event",
  "description": "Join us...",
  "imageUrl": "https://[project].supabase.co/storage/v1/object/public/Announcement-Media-Bucket/Media-Files/uuid.jpg",
  "status": "PUBLISHED",
  "createdAt": "2026-01-29T10:00:00Z"
}
```

**Response** (Error - 500):
```json
{
  "error": "Failed to upload image: File size exceeds 5MB"
}
```

### Database Schema
**Table**: `announcements`  
**Column**: `image_url` (VARCHAR, nullable) - ✅ Already exists

### Security Considerations
- ✅ JWT authentication required
- ✅ Role-based access (existing)
- ✅ File size limits prevent DoS
- ✅ File type validation prevents malicious uploads
- ✅ Service role key stored in environment (not in code)

## Out of Scope
- Image resizing/compression (future enhancement)
- Multiple image upload (future enhancement)
- Image editing in UI (future enhancement)
- Video upload (future enhancement)

## Success Metrics
- ✅ Images upload successfully 95%+ of time
- ✅ Upload latency < 3 seconds
- ✅ Zero exposed secrets in codebase
- ✅ Clear error messages for failures

## Testing Requirements

### Unit Tests
- ✅ `uploadToSupabase()` with valid file
- ✅ `uploadToSupabase()` with oversized file
- ✅ `uploadToSupabase()` with invalid type
- ✅ `uploadToSupabase()` with network error

### Integration Tests
- ✅ Full announcement creation with image
- ✅ Full announcement creation without image
- ✅ Upload fails but announcement still created

### Manual Testing
- ✅ Upload from CreateAnnouncement.js
- ✅ View uploaded image in feed
- ✅ Verify image persists after page refresh

## Dependencies
- ✅ Supabase project with Storage enabled
- ✅ Storage bucket named "Announcement-Media-Bucket"
- ✅ Environment variables configured
- ✅ Frontend already sends `FormData` with `file` field

## Rollout Plan
1. Add environment variables to `.env`
2. Implement `uploadToSupabase()` method
3. Test with local Supabase setup
4. Deploy to staging
5. Test end-to-end with real images
6. Deploy to production

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Supabase credentials leak | High | Store in `.env`, add to `.gitignore` |
| Storage quota exceeded | Medium | Monitor usage, set up alerts |
| Upload timeout | Low | Set 30s timeout, show user feedback |
| Malicious file upload | Medium | Validate file type server-side |

---

**Version**: 1.0  
**Last Updated**: 2026-01-29
