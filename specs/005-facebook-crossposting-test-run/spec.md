# Feature Specification: Facebook Crossposting Test Run

**Feature Branch**: `005-facebook-crossposting-test-run`
**Created**: February 14, 2026
**Status**: Draft
**Input**: Run a full Facebook-only crossposting test with Meta use case approvals

---

## User Scenarios & Testing

### Scenario 1: Connect Facebook Page
**Actor**: Authorized SONAR admin (Student Organization, OSAS, Academic)

**Steps**:
1. Open Social Media Settings
2. Click "Connect" for Facebook
3. Complete OAuth login
4. Confirm connection status

**Expected Result**:
- Facebook connection status shows connected
- Page ID stored and accessible

---

### Scenario 2: Crosspost Announcement to Facebook
**Actor**: Authorized SONAR admin

**Steps**:
1. Create a new announcement (image optional)
2. Enable Facebook crossposting
3. Submit announcement
4. Wait for crosspost completion

**Expected Result**:
- SocialMediaPost record created (PENDING → POSTED)
- Facebook Page shows the post within 5 minutes
- SONAR logs show success

---

### Scenario 3: Verify Graph API Permissions
**Actor**: Tester

**Steps**:
1. Open Graph API Explorer
2. Run `GET /me/permissions`

**Expected Result**:
- `pages_manage_posts` granted
- `pages_read_engagement` granted
- `pages_show_list` granted

---

## Requirements

### Functional Requirements
- **FR-001**: System MUST connect Facebook via OAuth and store page access token
- **FR-002**: System MUST crosspost announcements to a Facebook Page
- **FR-003**: System MUST log each crosspost attempt
- **FR-004**: System MUST surface user-friendly errors when posting fails

### Non-Functional Requirements
- **NFR-001**: Crossposting MUST complete within 10 seconds for text-only posts
- **NFR-002**: Crossposting MUST not block announcement creation if it fails
- **NFR-003**: Access tokens MUST be stored encrypted

---

## Success Criteria

- **SC-001**: Facebook crosspost appears on Page within 5 minutes
- **SC-002**: Graph API Explorer confirms required permissions
- **SC-003**: At least 2 successful posts completed
- **SC-004**: At least 1 failed post produces readable error

---

## Test Preconditions

- Meta use case: "Manage everything on your Page" enabled
- Permissions approved: pages_manage_posts, pages_read_engagement, pages_show_list
- Backend env configured with Facebook client ID/secret
- OAuth redirect URI matches Meta configuration
- Encryption key configured

---

## Notes

- Instagram out of scope for this run
- If permissions are pending, testing can be done with Meta testers only
