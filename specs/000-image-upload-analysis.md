# Analysis: Image Upload to Database Issue

## Problem Statement
The announcement creation is failing when users try to upload images because `AnnouncementController.java` calls a non-existent method `uploadToSupabase()` in `AnnouncementService`.

## Evidence
**File**: `AnnouncementController.java` (lines 72-78)
```java
if (file != null && !file.isEmpty()) {
    try {
        String fileUrl = announcementService.uploadToSupabase(file, "Announcement-Media-Bucket", "Media-Files");
        announcement.setImageUrl(fileUrl);
    } catch (Exception e) {
        System.err.println("Warning: File upload failed...");
    }
}
```

**File**: `AnnouncementService.java` - Method `uploadToSupabase()` does NOT exist

## Root Cause
Previous cleanup removed the Supabase upload functionality but the controller still references it.

## Impact
- Users cannot upload images with announcements
- Feature appears in UI but fails silently or with errors
- Database `imageUrl` field remains null

## Current State
- ✅ Database schema ready: `Announcement.imageUrl` column exists
- ✅ Frontend ready: File upload UI working, sends `FormData` with `file` field
- ✅ Controller ready: Receives `MultipartFile` parameter
- ❌ Service layer: Missing upload implementation

## Solution Options

### Option A: Store as Base64 in Database (Quick Fix)
**Pros**: No external storage needed, simple implementation
**Cons**: Database bloat, poor performance for large images, no CDN benefits
**Effort**: Low (1-2 hours)

### Option B: Use Supabase Storage (Recommended)
**Pros**: Proper file management, CDN delivery, database stores only URLs
**Cons**: Requires Supabase setup, environment variables
**Effort**: Medium (2-4 hours)

### Option C: Use Local File System
**Pros**: No external dependencies
**Cons**: Not scalable, difficult for cloud deployment
**Effort**: Low (1-2 hours)

## Recommendation
**Option B (Supabase Storage)** - Aligns with existing infrastructure and scalability needs.

## Next Steps
1. Create spec for image upload feature
2. Plan implementation with Supabase Storage API
3. Break down into tasks
4. Implement and test
