# Notification System Implementation

## Overview

The notification system has been fully implemented to handle three distinct notification flows:

1. **Approved Announcements → Students**: When an announcement is approved by OSAS, all students receive a notification
2. **Student Organization Submission → OSAS**: When a student organization creates an announcement, OSAS receives a notification for approval
3. **Approval/Rejection Decision → Student Organization**: When OSAS approves or rejects an announcement, the student organization receives a notification

---

## Architecture

### Data Model

#### Notification Entity
Enhanced with new fields to support multiple notification types:

```java
@Entity
@Table(name = "notification_logs")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "announcement_id")
    private Announcement announcement;
    
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "read_at")
    private Instant readAt;
    
    @Column(name = "type")
    private String type;  // 'announcement', 'approval', 'rejection'
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "message")
    private String message;
}
```

**New Fields:**
- `type`: Notification category ('announcement', 'approval', 'rejection')
- `title`: Human-readable notification title
- `message`: Detailed notification message

---

## Flow Implementation

### 1. Student Organization Creates Announcement

**Trigger:** Student Organization submits announcement via `/api/announcements` POST

**What Happens:**
1. Announcement is created with status `PENDING`
2. `AnnouncementService.create()` triggers notification flow
3. `NotificationService.notifyOSASOfNewAnnouncement()` is called
4. All OSAS users receive notification with type `approval`

**Notification Details:**
- **Type**: `approval`
- **Title**: "New Announcement Awaiting Approval"
- **Message**: "Student Organization '[Name]' has submitted an announcement: \"[Title]\" for approval."
- **Recipients**: All users with role `OSAS`

---

### 2. OSAS Approves Announcement

**Trigger:** OSAS approves via `/api/announcements/{id}/approve` POST

**What Happens:**
1. Announcement status changes to `PUBLISHED`
2. `AnnouncementService.approve()` is called
3. Two notifications are sent:
   - One to the Student Organization (approval confirmation)
   - One to all Students (new announcement published)

**Notification Details:**

**For Student Organization:**
- **Type**: `approval`
- **Title**: "Announcement Approved ✓"
- **Message**: "Your announcement \"[Title]\" has been approved by OSAS and is now published."
- **Recipients**: Posting Student Organization

**For All Students:**
- **Type**: `announcement`
- **Title**: "New Announcement: [Title]"
- **Message**: "[Announcement Description]"
- **Recipients**: All users with role `Student`

---

### 3. OSAS Rejects Announcement

**Trigger:** OSAS rejects via `/api/announcements/{id}/reject` POST

**What Happens:**
1. Announcement status changes to `REJECTED`
2. `AnnouncementService.reject()` is called
3. Notification sent to Student Organization (rejection notice)

**Notification Details:**
- **Type**: `rejection`
- **Title**: "Announcement Rejected ✗"
- **Message**: "Your announcement \"[Title]\" has been rejected by OSAS."
- **Recipients**: Posting Student Organization

---

### 4. OSAS/Academic Creates Announcement

**Trigger:** OSAS or Academic submits announcement via `/api/announcements` POST

**What Happens:**
1. Announcement is created with status `PUBLISHED` (immediate publication)
2. `AnnouncementService.create()` triggers notification flow
3. `NotificationService.notifyStudentsOfPublishedAnnouncement()` is called
4. All students receive notification

**Notification Details:**
- **Type**: `announcement`
- **Title**: "New Announcement: [Title]"
- **Message**: "[Announcement Description]"
- **Recipients**: All users with role `Student`

---

## Service Methods

### NotificationService

```java
/**
 * Notify all students when an announcement is published
 */
@Transactional
public void notifyStudentsOfPublishedAnnouncement(Announcement announcement)

/**
 * Notify OSAS when a Student Organization creates an announcement
 */
@Transactional
public void notifyOSASOfNewAnnouncement(Announcement announcement)

/**
 * Notify Student Organization when their announcement is approved/rejected
 */
@Transactional
public void notifyApprovalResult(Announcement announcement, boolean approved)

/**
 * Get notifications for a user
 */
public List<Notification> getNotificationsForUser(String email)

/**
 * Mark a notification as read
 */
@Transactional
public void markAsRead(Long notificationId)
```

### AnnouncementService

```java
/**
 * Create announcement - triggers notifications based on poster role
 */
@Transactional
public Announcement create(Announcement a, User poster)

/**
 * Approve announcement - notifies Student Org and all Students
 */
@Transactional
public Announcement approve(Long id)

/**
 * Reject announcement - notifies Student Org of rejection
 */
@Transactional
public Announcement reject(Long id)
```

---

## Repository Updates

### UserRepository

Added method to find users by role:
```java
List<User> findByRole(String role);
```

This enables efficient queries to find all students or all OSAS users for notification distribution.

---

## API Endpoints

### Get Notifications
```
GET /api/notifications
Authorization: Bearer {token}
```

Returns all notifications for the authenticated user.

### Mark as Read
```
POST /api/notifications/mark-read
Authorization: Bearer {token}
Content-Type: application/json

{
  "notificationId": 123
}
```

---

## Database Changes

The following columns were added to the `notification_logs` table:

```sql
ALTER TABLE notification_logs ADD COLUMN type VARCHAR(50);
ALTER TABLE notification_logs ADD COLUMN title VARCHAR(255);
ALTER TABLE notification_logs ADD COLUMN message TEXT;
```

These are automatically created by JPA/Hibernate migration when you run the backend.

---

## Testing the Notification System

### Test Scenario 1: Student Organization Creates Announcement
1. Login as Student Organization (e.g., `org@iacademy.edu.ph`)
2. Go to "Create Announcement"
3. Create a new announcement and submit
4. Login as OSAS (e.g., `osas@iacademy.edu.ph`)
5. Go to "Notifications" - should see approval request

### Test Scenario 2: OSAS Approves Announcement
1. From OSAS account in Approval Queue
2. Click "Approve" on Student Organization's announcement
3. Check Notifications page
4. Login as Student Organization
5. Go to "Notifications" - should see approval confirmation
6. Login as Student
7. Go to "Notifications" - should see new announcement notification

### Test Scenario 3: OSAS Rejects Announcement
1. From OSAS account in Approval Queue
2. Click "Reject" on Student Organization's announcement
3. Login as Student Organization
4. Go to "Notifications" - should see rejection notice

### Test Scenario 4: OSAS Creates Direct Announcement
1. Login as OSAS
2. Go to "Create Announcement"
3. Create and submit new announcement (appears published immediately)
4. Login as Student
5. Go to "Notifications" - should see the new announcement

---

## File Changes Summary

### Modified Files
1. **NotificationService.java** - Added four new notification methods
2. **AnnouncementService.java** - Integrated notification triggers in create/approve/reject
3. **Notification.java** - Added type, title, message fields and constructor
4. **UserRepository.java** - Added findByRole() method

### Notification Types
- `announcement` - General announcement for students
- `approval` - Approval/rejection message for student organizations
- `rejection` - Rejection message for student organizations

---

## Compilation Status
✅ **All changes compile successfully**

The backend is ready to run and test the complete notification system.

---

**Last Updated:** January 17, 2026
