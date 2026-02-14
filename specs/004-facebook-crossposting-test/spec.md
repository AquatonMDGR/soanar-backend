# Feature Specification: Facebook Crossposting Test (Meta Use Case)

**Feature Branch**: `004-facebook-crossposting-test`
**Created**: February 14, 2026
**Status**: Draft
**Input**: Test Facebook-only crossposting with Meta use cases, app ID/secret, and Pages permissions

---

## User Scenarios & Testing

### Scenario 1: Connect Facebook Page (OAuth)
**Actor**: Authorized SONAR admin (Student Organization, OSAS, Academic)

**Steps**:
1. Open Social Media Settings in SONAR
2. Click "Connect" for Facebook
3. Complete Facebook OAuth login and grant requested permissions
4. Return to SONAR and verify connection status

**Expected Result**:
- OAuth callback returns success
- Connection status shows Facebook connected
- Stored credential includes Page ID and Page access token

---

### Scenario 2: Crosspost an Announcement to Facebook
**Actor**: Authorized SONAR admin

**Steps**:
1. Create an announcement (image optional)
2. Enable Facebook crossposting in the modal
3. Submit the announcement
4. System posts to Facebook Page

**Expected Result**:
- A SocialMediaPost record is created
- Facebook post appears on the Page within 5 minutes
- SONAR returns success response

---

### Scenario 3: Verify Required Permissions
**Actor**: Tester/Developer

**Steps**:
1. Use Graph API Explorer with the app
2. Call `GET /me/permissions`

**Expected Result**:
- `pages_manage_posts` granted
- `pages_read_engagement` granted
- `pages_show_list` granted

---

## Requirements

### Functional Requirements
- **FR-001**: System MUST complete Facebook OAuth and store Page access token
- **FR-002**: System MUST post announcements to a connected Facebook Page
- **FR-003**: System MUST allow Facebook-only crossposting
- **FR-004**: System MUST log success or failure for each Facebook post
- **FR-005**: System MUST handle missing/invalid tokens with clear error messages

### Non-Functional Requirements
- **NFR-001**: Facebook post creation MUST complete within 10 seconds
- **NFR-002**: Crossposting MUST NOT block announcement creation if posting fails
- **NFR-003**: Access tokens MUST be encrypted at rest
- **NFR-004**: Errors from Facebook API MUST be recorded and visible to admins

---

## Success Criteria

### Measurable Outcomes
- **SC-001**: Graph API Explorer test returns all required Pages permissions
- **SC-002**: At least 3 successful Facebook posts made via SONAR UI
- **SC-003**: Each successful post appears on the target Page within 5 minutes
- **SC-004**: At least 1 failed post test returns a user-friendly error message
- **SC-005**: social_media_posts records created for each crosspost attempt

---

## Test Preconditions

- Meta app configured with use case: "Manage everything on your Page"
- App Review permissions requested: pages_manage_posts, pages_read_engagement, pages_show_list
- App ID and App Secret set in backend config
- OAuth redirect URI matches Meta app settings
- Encryption key set for credential storage

---

## Notes

- Instagram is out of scope for this test specification
- Facebook-only testing is the MVP validation gate for crossposting
