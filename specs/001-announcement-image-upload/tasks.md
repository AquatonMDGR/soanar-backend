# Tasks: Announcement Image Upload

**Branch**: `001-announcement-image-upload`  
**Status**: Ready to Start  
**Assignee**: TBD

---

## 📋 Task Checklist

### Phase 1: Environment Setup
- [ ] **Task 1.1**: Create Supabase Storage bucket
  - Go to Supabase Dashboard → Storage
  - Create public bucket: `Announcement-Media-Bucket`
  - Create folder: `Media-Files`
  - **Acceptance**: Bucket visible in dashboard
  - **Effort**: 10 min

- [ ] **Task 1.2**: Configure environment variables
  - Add `SUPABASE_URL` to `.env`
  - Add `SUPABASE_SERVICE_ROLE_KEY` to `.env`
  - Add properties to `application.properties`
  - Verify `.env` is gitignored
  - **Acceptance**: `echo $SUPABASE_URL` prints URL
  - **Effort**: 5 min

- [ ] **Task 1.3**: Test environment configuration
  - Restart Spring Boot app
  - Check logs for successful property injection
  - **Acceptance**: No "property not found" errors
  - **Effort**: 5 min

---

### Phase 2: Service Layer Implementation
- [ ] **Task 2.1**: Add configuration fields to AnnouncementService
  - File: `src/main/java/com/soanar/service/AnnouncementService.java`
  - Add `@Value("${supabase.url}") private String supabaseUrl;`
  - Add `@Value("${supabase.service-role-key}") private String supabaseKey;`
  - Add import: `import org.springframework.beans.factory.annotation.Value;`
  - **Acceptance**: Code compiles without errors
  - **Effort**: 5 min

- [ ] **Task 2.2**: Implement file validation logic
  - Add method signature: `public String uploadToSupabase(MultipartFile file, String bucketName, String folderPath) throws IOException`
  - Add file size check (5MB limit)
  - Add content type validation (JPG/PNG/GIF only)
  - Throw `IllegalArgumentException` for invalid files
  - **Acceptance**: Validation logic covers all edge cases
  - **Effort**: 15 min

- [ ] **Task 2.3**: Implement Supabase HTTP upload
  - Generate unique filename with UUID
  - Build Supabase Storage API URL
  - Create `HttpClient` and `HttpRequest`
  - Set Authorization header with service role key
  - Set 30-second timeout
  - **Acceptance**: HTTP request structure correct
  - **Effort**: 20 min

- [ ] **Task 2.4**: Implement response handling
  - Check HTTP status code (200-299 = success)
  - Build public URL from response
  - Handle error responses (throw IOException)
  - Handle `InterruptedException` properly
  - **Acceptance**: All response scenarios handled
  - **Effort**: 15 min

- [ ] **Task 2.5**: Add logging
  - Log upload attempts with filename
  - Log success with public URL
  - Log failures with error details
  - Use `System.out.println` or logger
  - **Acceptance**: Useful logs for debugging
  - **Effort**: 5 min

- [ ] **Task 2.6**: Compile and fix errors
  - Run `./mvnw compile`
  - Fix any compilation errors
  - Ensure no missing imports
  - **Acceptance**: Clean compile
  - **Effort**: 10 min

---

### Phase 3: Testing
- [ ] **Task 3.1**: Manual test - valid image upload
  - Use Postman or curl
  - Upload JPG file < 5MB
  - Verify 200 OK response
  - Check Supabase Storage for file
  - Check database for `imageUrl`
  - **Acceptance**: Image stored and URL returned
  - **Effort**: 15 min

- [ ] **Task 3.2**: Manual test - no image
  - Create announcement without file
  - Verify 200 OK response
  - Verify `imageUrl` is null
  - **Acceptance**: Announcement created successfully
  - **Effort**: 5 min

- [ ] **Task 3.3**: Manual test - oversized file
  - Upload 6MB file
  - Verify error logged
  - Verify announcement still created (warning mode)
  - **Acceptance**: Graceful degradation
  - **Effort**: 5 min

- [ ] **Task 3.4**: Manual test - invalid file type
  - Upload .txt or .pdf file
  - Verify validation error
  - Verify announcement created without image
  - **Acceptance**: Invalid files rejected
  - **Effort**: 5 min

- [ ] **Task 3.5**: End-to-end test from frontend
  - Start backend and frontend
  - Login as Student Organization
  - Create announcement with image
  - Verify image displays in feed
  - **Acceptance**: Full workflow works
  - **Effort**: 10 min

- [ ] **Task 3.6**: Test error scenarios
  - Test with invalid Supabase credentials
  - Test with network timeout (disconnect internet)
  - Verify error messages are clear
  - **Acceptance**: Errors handled gracefully
  - **Effort**: 10 min

---

### Phase 4: Documentation
- [ ] **Task 4.1**: Update API Reference
  - File: `docs/API_REFERENCE.md`
  - Document multipart/form-data format
  - Document file parameter requirements
  - Document response format with imageUrl
  - **Acceptance**: API docs complete
  - **Effort**: 10 min

- [ ] **Task 4.2**: Update Backend README
  - File: `soanar-backend/README.md`
  - Add Supabase Storage setup instructions
  - Document environment variables
  - **Acceptance**: Setup instructions clear
  - **Effort**: 5 min

- [ ] **Task 4.3**: Update Changelog
  - File: `docs/CHANGELOG.md`
  - Add entry for image upload feature
  - Include date and description
  - **Acceptance**: Changelog reflects changes
  - **Effort**: 3 min

---

### Phase 5: Code Review & Cleanup
- [ ] **Task 5.1**: Code review checklist
  - No hardcoded credentials
  - Proper error handling
  - Consistent logging
  - No debug code left in
  - **Acceptance**: Code quality standards met
  - **Effort**: 15 min

- [ ] **Task 5.2**: Verify gitignore
  - Check `.env` is ignored
  - Check no secrets in committed files
  - Run `git status` to confirm
  - **Acceptance**: No secrets exposed
  - **Effort**: 3 min

- [ ] **Task 5.3**: Final integration test
  - Restart both backend and frontend
  - Test full workflow one more time
  - Check logs for any warnings
  - **Acceptance**: Everything works smoothly
  - **Effort**: 10 min

---

## 📊 Progress Tracking

**Total Tasks**: 23  
**Completed**: 0  
**In Progress**: 0  
**Blocked**: 0

**Estimated Total Effort**: 3-4 hours

---

## 🚨 Blockers & Issues

| Issue | Impact | Resolution | Status |
|-------|--------|------------|--------|
| _None yet_ | - | - | - |

---

## ✅ Completion Criteria

Feature is complete when:
- ✅ All 23 tasks checked off
- ✅ Images upload successfully
- ✅ No secrets in Git
- ✅ Documentation updated
- ✅ End-to-end test passes
- ✅ Code reviewed and merged

---

**Start Date**: 2026-01-29  
**Target Completion**: 2026-01-29 (same day)
