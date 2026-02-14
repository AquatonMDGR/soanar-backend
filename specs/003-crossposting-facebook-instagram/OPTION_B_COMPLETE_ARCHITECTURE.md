# Option B Deep-Dive: Multi-Image Crossposting Architecture

**Date**: February 14, 2026  
**Goal**: Implement complete multi-image support for Facebook crossposting  
**Pattern Reference**: Email notification system (which already handles multiple images correctly)  

---

## Data Flow Architecture

### Current System - How Email Handles Multiple Images

```
Announcement Created
  ├─ image_url: String (single, legacy)
  └─ image_urls: List<String> (array of URLs, stored in PostgreSQL jsonb)
       ├─ "https://storage.supabase.co/.../image1.jpg"
       ├─ "https://storage.supabase.co/.../image2.jpg"
       └─ "https://storage.supabase.co/.../image3.jpg"

         ↓

NotificationService.buildEmailHtmlBody() [WORKING REFERENCE]
  1. Get announcement from database
  2. Check if imageUrls exists and not empty
  3. FOR EACH imageUrl in imageUrls:
     └─ Build HTML: <img src="{imageUrl}" />
  4. IF imageUrls empty, fall back to imageUrl
  5. Return HTML with all images embedded

         ↓

Email Sent
  ├─ Title: announcement.title
  ├─ Description: announcement.description
  ├─ All images from imageUrls array
  └─ Recipient: distribution group members

```

**Key Code** [NotificationService.java:174-201]:
```java
// Include all images from imageUrls array
if (announcement.getImageUrls() != null && !announcement.getImageUrls().isEmpty()) {
    System.out.println("DEBUG buildEmailHtmlBody: Adding " + announcement.getImageUrls().size() + " images to email");
    for (String imageUrl : announcement.getImageUrls()) {
        if (imageUrl != null && !imageUrl.isBlank()) {
            html.append("<p><img src=\"").append(imageUrl).append("\" alt=\"Announcement image\" style=\"max-width:600px;height:auto;margin:10px 0;\"/></p>");
        }
    }
} else if (announcement.getImageUrl() != null) {
    // Fallback to single imageUrl
    html.append("<p><img src=\"").append(announcement.getImageUrl()).append("\" alt=\"Announcement image\" style=\"max-width:600px;height:auto;\"/></p>");
}
```

---

## How It SHOULD Work for Facebook Crossposting

### Problem: Current Flow

```
User creates announcement with 3 images
  ↓
Frontend sends all files to backend
  ↓
AnnouncementController.create()
  ├─ Uploads ALL 3 images to Supabase
  ├─ Stores URLs in:
  │   ├─ imageUrl: "https://.../image1.jpg" (first image)
  │   └─ imageUrls: ["https://.../image1.jpg", "https://.../image2.jpg", "https://.../image3.jpg"]
  └─ Saves to database
  ↓
User clicks "Crosspost to Facebook"
  ↓
AnnouncementController.crosspost()
  ├─ Gets announcement from DB
  ├─ Calls CrosspostService.crosspostAnnouncement()
  └─ Passes `file` parameter (ONLY FIRST FILE from multipart)
  ↓
CrosspostServiceImpl.postToPlatform()
  ├─ Calls FacebookService.postAnnouncement(announcement, caption, ONE_FILE, orgId)
  └─ This is the problem! 🔴
```

### The Issue

**Line in CrosspostServiceImpl.postToPlatform():**
```java
postId = retryService.executeWithRetry(() ->
    facebookService.postAnnouncement(announcement, caption, image, organizationId)
    //                                                        ^^^^^ THIS IS NULL or SINGLE FILE
);
```

When user clicks crosspost:
1. Frontend sends **ONE file** (frontend only sends first attachment)
2. Backend passes that **ONE file** to FacebookService
3. FacebookService has access to **BOTH**:
   - The `image` parameter (single file from frontend)
   - `announcement.getImageUrls()` (all URLs from database) ← USE THIS!

---

## Correct Implementation Pattern

### Step 1: Modify FacebookServiceImpl.postAnnouncement()

**Change From** (Line 76-79 - BROKEN):
```java
List<String> imageUrls = announcement.getImageUrls();
if (imageUrls != null && imageUrls.size() > 1) {
    logger.info("Posting Facebook announcement with {} images", imageUrls.size());
    postId = postWithMultipleImages(pageId, token.get(), postCaption, imageUrls);
}
```

**Change To** (CORRECT - follows email pattern):
```java
// ALWAYS check imageUrls first (like NotificationService does)
List<String> storedImageUrls = announcement.getImageUrls();

if (storedImageUrls != null && storedImageUrls.size() > 1) {
    // Multiple images from database storage
    logger.info("Posting Facebook announcement with {} stored images", storedImageUrls.size());
    postId = postWithMultipleImages(pageId, token.get(), postCaption, storedImageUrls);
}
else if (storedImageUrls != null && storedImageUrls.size() == 1) {
    // Single image from database storage
    logger.info("Posting Facebook announcement with 1 stored image");
    postId = postWithImageUrl(pageId, token.get(), postCaption, storedImageUrls.get(0));
}
else if (announcement.getImageUrl() != null && !announcement.getImageUrl().isBlank()) {
    // Legacy single image field
    logger.info("Posting Facebook announcement with legacy imageUrl");
    postId = postWithImageUrl(pageId, token.get(), postCaption, announcement.getImageUrl());
}
else if (image != null && !image.isEmpty()) {
    // Uploaded file from crosspost endpoint
    logger.info("Posting Facebook announcement with uploaded file");
    imageService.validateImage(image);
    byte[] imageData = image.getBytes();
    byte[] resizedImage = imageService.resizeForFacebook(imageData);
    postId = uploadAndPostImage(pageId, token.get(), postCaption, resizedImage);
}
else {
    // Text-only post
    String feedUrl = String.format("%s/%s/feed", FACEBOOK_API_URL, pageId);
    logger.info("Posting Facebook announcement without image (text-only)");
    postId = postTextOnly(feedUrl, token.get(), postCaption);
}
```

**Why This Works**:
- ✅ Mimics NotificationService pattern (already proven)
- ✅ Uses database-stored images by default (not relying on frontend file parameter)
- ✅ Falls back gracefully (2+ images → 1 image → legacy image → file → text)
- ✅ No missing method errors

---

### Step 2: Implement postWithMultipleImages()

**New Method in FacebookServiceImpl** (Add after line 373):

```java
/**
 * Post announcement with multiple images as Facebook album/carousel
 * 
 * Pattern:
 * 1. Upload each image to /{pageId}/photos?published=false → collect photo IDs
 * 2. POST to /{pageId}/feed with message and attached_media=[{media_fbid: "123"}, ...]
 * 3. This creates an album post with all images
 */
private String postWithMultipleImages(String pageId, String token, String message, List<String> imageUrls) throws Exception {
    try {
        logger.info("Posting Facebook album with {} images", imageUrls.size());
        
        // Step 1: Upload each image unpublished and collect photo IDs
        List<String> photoIds = new ArrayList<>();
        
        for (int i = 0; i < imageUrls.size(); i++) {
            String imageUrl = imageUrls.get(i);
            logger.info("Uploading image {}/{}: {}", (i+1), imageUrls.size(), imageUrl);
            
            try {
                // Download image from Supabase URL
                byte[] imageData = restTemplate.getForObject(imageUrl, byte[].class);
                if (imageData == null || imageData.length == 0) {
                    logger.warn("Failed to download image from: {}", imageUrl);
                    continue;
                }
                
                // Resize for Facebook (1200x628)
                byte[] resizedImage = imageService.resizeForFacebook(imageData);
                logger.info("Resized image {}: {} bytes → {} bytes", (i+1), imageData.length, resizedImage.length);
                
                // Upload to Facebook with published=false (don't show until album is created)
                String photoId = uploadUnpublishedPhoto(pageId, token, resizedImage);
                photoIds.add(photoId);
                logger.info("Successfully uploaded image {}: photoId={}", (i+1), photoId);
                
            } catch (Exception e) {
                logger.warn("Failed to upload image {} ({}): {}", (i+1), imageUrl, e.getMessage());
                // Continue with remaining images instead of failing entire post
                // More robust: if 1 image fails, post what we have
            }
        }
        
        if (photoIds.isEmpty()) {
            logger.error("No images were successfully uploaded for album post");
            throw new RuntimeException("All image uploads failed");
        }
        
        logger.info("Successfully uploaded {} photos, creating feed post with attached_media", photoIds.size());
        
        // Step 2: Create feed post with all photo IDs as attached media
        return createFeedPostWithPhotos(pageId, token, message, photoIds);
        
    } catch (Exception e) {
        logger.error("Failed to post multiple images to Facebook", e);
        throw e;
    }
}

/**
 * Upload a single image to Facebook without publishing immediately
 * Returns the photo ID for use in album creation
 */
private String uploadUnpublishedPhoto(String pageId, String token, byte[] imageData) throws Exception {
    String photosUrl = String.format("%s/%s/photos", FACEBOOK_API_URL, pageId);
    
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("source", new ByteArrayResource(imageData) {
        @Override
        public String getFilename() {
            return "photo.jpg";
        }
    });
    body.add("published", "false");  // CRITICAL: Don't publish yet - we'll attach to feed post
    body.add("access_token", token);
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
    
    logger.debug("Uploading unpublished photo to: {}", photosUrl);
    
    ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
        photosUrl,
        HttpMethod.POST,
        request,
        new ParameterizedTypeReference<Map<String, Object>>() {}
    );
    
    if (!response.getStatusCode().is2xxSuccessful()) {
        throw new RuntimeException("Failed to upload unpublished photo: " + response.getStatusCode());
    }
    
    Map<String, Object> responseBody = response.getBody();
    String photoId = responseBody != null ? (String) responseBody.get("id") : null;
    
    if (photoId == null) {
        throw new RuntimeException("No photo ID returned from Facebook");
    }
    
    logger.info("Successfully uploaded unpublished photo: {}", photoId);
    return photoId;
}

/**
 * Create a feed post with multiple photos as attached media (album/carousel)
 * 
 * This Facebook API feature creates a single post with multiple images
 * visible as a carousel/album on the user's timeline
 */
private String createFeedPostWithPhotos(String pageId, String token, String message, List<String> photoIds) throws Exception {
    String feedUrl = String.format("%s/%s/feed", FACEBOOK_API_URL, pageId);
    
    // Build attached_media JSON array: [{"media_fbid":"123"},{"media_fbid":"456"}]
    StringBuilder attachedMedia = new StringBuilder("[");
    for (int i = 0; i < photoIds.size(); i++) {
        if (i > 0) attachedMedia.append(",");
        attachedMedia.append("{\"media_fbid\":\"").append(photoIds.get(i)).append("\"}");
    }
    attachedMedia.append("]");
    
    logger.debug("Creating feed post with attached_media: {}", attachedMedia.toString());
    
    Map<String, String> params = new LinkedHashMap<>();
    params.put("message", message);
    params.put("attached_media", attachedMedia.toString());
    params.put("access_token", token);
    
    String queryString = buildQueryString(params);
    String url = feedUrl + "?" + queryString;
    
    logger.info("Posting to feed with {} attached images: {}", photoIds.size(), feedUrl);
    
    ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
        url,
        HttpMethod.POST,
        null,
        new ParameterizedTypeReference<Map<String, Object>>() {}
    );
    
    if (!response.getStatusCode().is2xxSuccessful()) {
        throw new RuntimeException("Failed to create feed post with photos: " + response.getStatusCode());
    }
    
    Map<String, Object> responseBody = response.getBody();
    String postId = responseBody != null ? (String) responseBody.get("id") : null;
    
    if (postId == null) {
        throw new RuntimeException("No post ID returned from Facebook feed post");
    }
    
    logger.info("Successfully created Facebook feed post with {} photos: {}", photoIds.size(), postId);
    return postId;
}
```

---

## Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│    ANNOUNCEMENT CREATION (Frontend → Backend → Supabase)            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  User selects 3 images in CreateAnnouncement.js                     │
│           ↓                                                          │
│  Frontend: formData.append('files', file1, file2, file3)            │
│           ↓                                                          │
│  POST /api/announcements → AnnouncementController.create()          │
│           ↓                                                          │
│  AnnouncementService.uploadMultipleToSupabase()                     │
│    ├─ Upload to: storage.supabase.co/announcements/image1.jpg      │
│    ├─ Upload to: storage.supabase.co/announcements/image2.jpg      │
│    └─ Upload to: storage.supabase.co/announcements/image3.jpg      │
│           ↓                                                          │
│  Backend: Save to PostgreSQL                                        │
│    INSERT INTO announcements (                                      │
│      title = "Event",                                               │
│      image_url = "https://.../image1.jpg",                          │
│      image_urls = '["https://.../image1.jpg",                       │
│                     "https://.../image2.jpg",                       │
│                     "https://.../image3.jpg"]'::jsonb               │
│    );                                                               │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│    EMAIL NOTIFICATION (Reference Implementation - WORKING)           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  NotificationService.notifyDistributionGroupMembers()               │
│           ↓                                                          │
│  buildEmailHtmlBody(announcement)                                   │
│    1. Check: announcement.getImageUrls() != null ?                 │
│    2. Loop: FOR EACH imageUrl in imageUrls:                        │
│             <img src="imageUrl" />                                  │
│    3. Fallback: if empty, use announcement.getImageUrl()           │
│           ↓                                                          │
│  EmailService.sendTargetedEmail(recipients, html)                  │
│           ↓                                                          │
│  Email arrives with all 3 images embedded                           │
│                                                                       │
│  ✅ THIS PATTERN WORKS - Use for crossposting!                      │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│    FACEBOOK CROSSPOSTING (Option B Implementation)                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  User clicks "Crosspost to Facebook"                                │
│           ↓                                                          │
│  POST /announcements/{id}/crosspost → AnnouncementController        │
│           ↓                                                          │
│  CrosspostService.crosspostAnnouncement(announcement, ...)          │
│           ↓                                                          │
│  CrosspostService.postToPlatform(announcement, ..., "facebook")     │
│           ↓                                                          │
│  FacebookService.postAnnouncement(announcement, caption, ...)       │
│           ↓                                                          │
│  ┌─ SAME PATTERN AS EMAIL ─────────────────────────────────────┐   │
│  │ List<String> imageUrls = announcement.getImageUrls()        │   │
│  │ if (imageUrls != null && imageUrls.size() > 1) {           │   │
│  │     postWithMultipleImages(pageId, token, imageUrls)       │   │
│  │ }                                                            │   │
│  └──────────────────────────────────────────────────────────────┘   │
│           ↓                                                          │
│  postWithMultipleImages(pageId, token, message, imageUrls)          │
│    1. FOR EACH imageUrl in imageUrls:                              │
│       a. Download: byte[] = restTemplate.getForObject(imageUrl)    │
│       b. Resize: byte[] = imageService.resizeForFacebook()         │
│       c. Upload: POST /{pageId}/photos?published=false             │
│       d. Collect: photoId from response                            │
│    2. Create: POST /{pageId}/feed                                  │
│              with attached_media=[{media_fbid: "id1"}, ...]        │
│           ↓                                                          │
│  Facebook creates album post with all 3 images                      │
│           ↓                                                          │
│  ✅ Returns post ID to caller                                       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Key Insights from Email Pattern

### Why Email Implementation is the Reference

**Email does this correctly** [NotificationService:195-201]:
```java
if (announcement.getImageUrls() != null && !announcement.getImageUrls().isEmpty()) {
    // ✅ Loops through ALL images stored in database
    for (String imageUrl : announcement.getImageUrls()) {
        html.append("<img src=\"").append(imageUrl).append("\" />");
    }
} else if (announcement.getImageUrl() != null) {
    // ✅ Graceful fallback to single image
    html.append("<img src=\"").append(announcement.getImageUrl()).append("\" />");
}
```

**Why This Pattern Should Apply to Facebook**:
1. Images are **already stored** in Supabase by the time crossposting happens
2. Images are **accessible via public URLs** stored in `imageUrls` array
3. No need to depend on the `file` parameter from crosspost endpoint
4. Just like email doesn't depend on frontend, Facebook shouldn't either
5. **Same loop pattern** - iterate through stored URLs instead of embedding in HTML

---

## Implementation Checklist

- [ ] **Step 1**: Update `FacebookServiceImpl.postAnnouncement()` logic (priority check imageUrls)
- [ ] **Step 2**: Implement `postWithMultipleImages()` method
- [ ] **Step 3**: Implement `uploadUnpublishedPhoto()` method  
- [ ] **Step 4**: Implement `createFeedPostWithPhotos()` method
- [ ] **Step 5**: Test compilation: `mvn clean compile`
- [ ] **Step 6**: Build: `mvn package -DskipTests`
- [ ] **Step 7**: Test single-image post (verify fallback still works)
- [ ] **Step 8**: Test multi-image post (verify album appears on Facebook)
- [ ] **Step 9**: Test error handling (image URL invalid, FB credentials missing, etc.)
- [ ] **Step 10**: Verify logs show correct flow

---

## Testing Guide

### Test Case 1: Single Image
```
Create announcement with 1 image
Crosspost to Facebook
Expected: Post appears with image
Actual Result: ?
```

### Test Case 2: Multiple Images
```
Create announcement with 3 images
Crosspost to Facebook
Expected: Album post appears with all 3 images
Actual Result: ?
```

### Test Case 3: Fallback to Legacy
```
Create announcement with imageUrl (not imageUrls)
Crosspost to Facebook
Expected: Single image post appears
Actual Result: ?
```

### Test Case 4: Text-Only
```
Create announcement with no images
Crosspost to Facebook
Expected: Text-only post appears
Actual Result: ?
```

### Test Case 5: Error Handling
```
Create announcement with 3 images
Disable Facebook credentials
Crosspost to Facebook
Expected: Graceful error, post marked as FAILED in DB
Actual Result: ?
```

---

## Why This Fix is Correct

1. **Pattern Proven in Production**: Email already uses this exact pattern with `imageUrls`
2. **Database Isn't the Bottleneck**: Images are already persisted in Supabase during announcement creation
3. **No Missing Dependencies**: All pieces are available:
   - `imageUrls` array populated during creation
   - `imageService.resizeForFacebook()` already exists
   - `restTemplate` available for downloading images
   - Facebook API endpoints documented
4. **Graceful Degradation**: Falls back smoothly from multi→single→legacy→file→text
5. **Error Resilient**: If one image fails, continues with others instead of failing entire post

---

## Summary

The implementation should follow the **email notification pattern** that's already working:

```
Announcement(imageUrls) 
  → Check size (2+? 1? 0?)
  → IF 2+: Loop and process each
  → ELSE: Use single fallback
  → ELSE: Use legacy field
  → ELSE: Use file parameter
  → ELSE: Text-only
```

This is **not a new concept** - it's applying existing proven patterns to a new platform (Facebook instead of email).

