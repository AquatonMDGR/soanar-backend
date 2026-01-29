# Implementation Plan: Announcement Image Upload

**Branch**: `001-announcement-image-upload`  
**Spec**: [spec.md](./spec.md)  
**Estimated Effort**: 3-4 hours  
**Risk Level**: Medium

---

## Phase 1: Environment Setup (30 min)

### 1.1 Configure Supabase Storage
**Files**: Supabase Dashboard (manual)

**Steps**:
1. Go to Supabase project → Storage
2. Create bucket `Announcement-Media-Bucket` (public)
3. Create folder `Media-Files` inside bucket
4. Set permissions: Allow authenticated uploads
5. Copy project URL and service role key

### 1.2 Update Environment Variables
**Files**: `.env`, `application.properties`

**Changes**:
```env
# Add to .env
SUPABASE_URL=https://[project-ref].supabase.co
SUPABASE_SERVICE_ROLE_KEY=[key-here]
```

```properties
# Add to application.properties
supabase.url=${SUPABASE_URL}
supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY}
```

**Verification**: `echo $SUPABASE_URL` should print URL

---

## Phase 2: Service Layer Implementation (1.5 hours)

### 2.1 Add Supabase Configuration Fields
**File**: `AnnouncementService.java`

**Add at top of class**:
```java
@Value("${supabase.url}")
private String supabaseUrl;

@Value("${supabase.service-role-key}")
private String supabaseKey;
```

**Dependencies**: Already imported `@Value` from Spring

### 2.2 Implement Upload Method
**File**: `AnnouncementService.java`

**Add new method**:
```java
public String uploadToSupabase(MultipartFile file, String bucketName, String folderPath) throws IOException {
    // Validate file size
    if (file.getSize() > 5 * 1024 * 1024) {
        throw new IllegalArgumentException("File size exceeds 5MB limit");
    }
    
    // Validate file type
    String contentType = file.getContentType();
    if (!contentType.matches("image/(jpeg|jpg|png|gif)")) {
        throw new IllegalArgumentException("Invalid file type. Only JPG, PNG, GIF allowed");
    }
    
    // Generate unique filename
    String originalFilename = file.getOriginalFilename();
    String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
    String uniqueFilename = UUID.randomUUID().toString() + extension;
    String fullPath = folderPath + "/" + uniqueFilename;
    
    // Build Supabase Storage API URL
    String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fullPath;
    
    // Create HTTP request
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(uploadUrl))
        .header("Authorization", "Bearer " + supabaseKey)
        .header("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
        .timeout(java.time.Duration.ofSeconds(30))
        .build();
    
    try {
        // Send request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // Return public URL
            String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fullPath;
            return publicUrl;
        } else {
            throw new IOException("Supabase upload failed: " + response.statusCode() + " - " + response.body());
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Upload interrupted", e);
    }
}
```

**Error Handling**:
- File size validation before upload
- Content type validation (security)
- Network timeout (30s)
- Proper exception propagation

### 2.3 Update Imports
**File**: `AnnouncementService.java`

**Add if missing**:
```java
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;
```

---

## Phase 3: Controller Updates (30 min)

### 3.1 Verify Controller Logic
**File**: `AnnouncementController.java` (lines 72-78)

**Current code** (already correct):
```java
if (file != null && !file.isEmpty()) {
    try {
        String fileUrl = announcementService.uploadToSupabase(file, "Announcement-Media-Bucket", "Media-Files");
        announcement.setImageUrl(fileUrl);
    } catch (Exception e) {
        System.err.println("Warning: File upload failed, continuing without image: " + e.getMessage());
        // Continue without image
    }
}
```

**Status**: ✅ No changes needed - method will now exist

### 3.2 Improve Error Response
**File**: `AnnouncementController.java` (line 95)

**Current**:
```java
return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
```

**Improvement** (optional):
```java
String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
return ResponseEntity.status(500).body(Map.of("error", errorMsg, "timestamp", Instant.now().toString()));
```

---

## Phase 4: Testing (1 hour)

### 4.1 Unit Tests
**File**: `AnnouncementServiceTest.java` (create if not exists)

**Tests to add**:
```java
@Test
void testUploadToSupabase_ValidFile() { }

@Test
void testUploadToSupabase_OversizedFile() { }

@Test
void testUploadToSupabase_InvalidFileType() { }
```

**Mock Strategy**: Use Mockito to mock HttpClient

### 4.2 Integration Test
**File**: Postman or curl

**Test Case 1**: Upload valid image
```bash
curl -X POST http://localhost:8080/api/announcements \
  -H "Authorization: Bearer [jwt-token]" \
  -F "title=Test Event" \
  -F "description=Testing image upload" \
  -F "file=@/path/to/test.jpg"
```

**Expected**: 200 OK, response includes `imageUrl`

**Test Case 2**: Upload without image
```bash
curl -X POST http://localhost:8080/api/announcements \
  -H "Authorization: Bearer [jwt-token]" \
  -F "title=No Image Event" \
  -F "description=No image attached"
```

**Expected**: 200 OK, `imageUrl` is null

**Test Case 3**: Upload oversized file (6MB)
**Expected**: 200 OK, warning logged, `imageUrl` is null

### 4.3 End-to-End Test
**Steps**:
1. Start backend: `./mvnw spring-boot:run`
2. Start frontend: `npm start`
3. Login as Student Organization
4. Navigate to Create Announcement
5. Fill form and select image
6. Click Submit
7. Verify announcement appears in feed with image
8. Check Supabase Storage → file exists
9. Check database → `imageUrl` populated

---

## Phase 5: Documentation (30 min)

### 5.1 Update API Reference
**File**: `docs/API_REFERENCE.md`

**Add to Announcements section**:
```markdown
### POST /api/announcements
**Content-Type**: `multipart/form-data`

**Parameters**:
- `title` (string, required)
- `description` (string, required)  
- `file` (file, optional) - Image file (JPG/PNG/GIF, max 5MB)
- `startDate` (string, optional) - Format: YYYY-MM-DD
- `endDate` (string, optional) - Format: YYYY-MM-DD

**Response**:
- Success: 200 OK with announcement object including `imageUrl`
- Error: 500 with error message
```

### 5.2 Update README
**File**: `soanar-backend/README.md`

**Add to Environment Setup**:
```markdown
## Supabase Storage Configuration
1. Create bucket `Announcement-Media-Bucket` in Supabase
2. Set bucket to public
3. Add environment variables:
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_ROLE_KEY`
```

### 5.3 Update Changelog
**File**: `docs/CHANGELOG.md`

**Add**:
```markdown
## [2026-01-29] Image Upload Feature
- Added image upload capability for announcements
- Images stored in Supabase Storage
- Supports JPG, PNG, GIF up to 5MB
- Backend stores public URL in database
```

---

## Rollback Plan

If issues arise:

1. **Remove `@Value` annotations** from `AnnouncementService.java`
2. **Remove `uploadToSupabase()` method**
3. **Controller gracefully handles missing method** (try-catch already in place)
4. **Announcements continue to work without images**

**Rollback Effort**: 5 minutes

---

## Success Criteria

- ✅ Backend starts without errors
- ✅ Image uploads work in 95%+ of cases
- ✅ Uploaded images display correctly in feed
- ✅ No secrets committed to Git
- ✅ All tests pass
- ✅ Documentation updated

---

## Dependencies & Prerequisites

**Before Starting**:
- ✅ Supabase project exists
- ✅ Frontend already sends `multipart/form-data`
- ✅ Database has `image_url` column
- ✅ `.env` file exists and gitignored

**During Implementation**:
- Internet connection (for Supabase API calls)
- Test images (< 5MB)
- Valid JWT token for testing

---

**Estimated Timeline**: 3-4 hours  
**Complexity**: Medium  
**Team Size**: 1 developer

---

**Next Steps**: Proceed to [tasks.md](./tasks.md) for actionable checklist
