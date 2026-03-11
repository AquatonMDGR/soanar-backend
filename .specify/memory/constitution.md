# SONAR Backend Constitution

## Core Principles

### I. Security & Role Integrity
All endpoints MUST enforce role-based access and JWT validation. Any change to
auth flows requires updating security tests and API documentation.

### II. Stable API Contracts
Public endpoints MUST remain backward compatible unless explicitly planned and
documented. Contract changes require synchronized spec updates.

### III. Data Integrity & Validation
All inputs MUST be validated and mapped through DTOs. Entities are not exposed
directly from controllers.

### IV. Observability & Auditability
Critical actions (approvals, announcements, role changes) MUST be logged with
traceable identifiers and timestamps.

### V. Reliable Notifications & Email
Email and notification dispatch MUST include failure handling and clear error
responses to avoid silent delivery failures.

## Thesis Specific Objectives (Traceability)

Status legend:
- `ACHIEVED`: Implemented and evidenced in runtime code/docs.
- `PARTIAL`: Implemented in core flow, but remaining scope/testing exists.

1. Role-based user system with distinct access levels and approval permissions.
- Status: `ACHIEVED`
- Evidence: JWT role-based access, role-restricted controllers/routes, Student Organization -> pending, OSAS approval, OSAS/Academic direct publish.

2. Centralized announcement workflow with tracked states (pending/approved/rejected/published).
- Status: `ACHIEVED`
- Evidence: Announcement lifecycle with approval/rejection endpoints and status transitions, plus audit logging.

3. Real-time notification system with in-app and/or email campus dissemination.
- Status: `PARTIAL`
- Evidence: In-app notifications and SMTP email delivery are implemented; true real-time push transport (WebSocket/SSE) is not implemented.

4. Structured, filterable announcement board with source/date usability and responsive access.
- Status: `PARTIAL`
- Evidence: Published feed and audience filtering exist; frontend docs indicate responsive UI and role-based boards. Date/source filtering is implemented in core flows but should remain part of thesis validation checks.

5. Secure institutional authentication and input validation.
- Status: `ACHIEVED`
- Evidence: Google OAuth + JWT, institutional domain restrictions, role-based authorization and request validation in service/controller flow.

6. Comprehensive testing (unit/integration/system reliability/usability/performance).
- Status: `PARTIAL`
- Evidence: Backend unit/controller tests exist and were expanded for notification regressions. Full end-to-end, performance, and broad integration test coverage is still incomplete.

7. Waterfall-aligned phase documentation (requirements -> maintenance).
- Status: `PARTIAL`
- Evidence: Extensive specs/plans/runbooks/changelogs exist across `specs/` and root docs; formal maintenance/deployment closure remains documentation-dependent per defense package.

8. Exposed and documented API interface for future integrations.
- Status: `ACHIEVED`
- Evidence: REST API endpoints are implemented and documented in `docs/API_REFERENCE.md`, with role/permission behavior described.

## Technology Stack (Authoritative Spec)

- Java 17
- Spring Boot 3.x (stable release, non-snapshot)
- Spring Web
- Spring Data JPA
- PostgreSQL (Supabase cloud)
- Hibernate ORM
- OAuth2 (Google Sign-In)
- RESTful API architecture

## Backend Responsibilities

- Enforce all business rules
- Enforce role-based permissions
- Act as the single source of truth
- Abstract external services (email, social media)
- Provide a documented API interface

## Data Persistence Strategy

- Use JPA entities as authoritative schema definition
- Supabase acts as managed PostgreSQL, not as business logic
- No frontend-to-database direct access
- ORM used to reduce SQL exposure and injection risks

## Core Entities (Mandatory)

- User
- Announcement
- DistributionGroup
- DistributionGroupMember
- SocialMediaPage
- SocialMediaToken
- CrossPostedAnnouncement
- Notification
- EmailLog
- SystemLog

## Approval Logic

- Student Organization announcements default to PENDING
- OSAS may approve or reject with reason
- Approved announcements become visible and trigger notifications
- OSAS and Academics bypass approval (AUTO_APPROVED)

## Notification Logic

- Notification creation is automatic upon publication
- Students receive notifications based on:
	- Announcement visibility
	- Distribution group membership
- Notifications are not a separate user action

## Email Logic

- Targeted email delivery uses distribution groups
- Term-based email aggregates approved announcements within date range
- Email sending is integrated into posting workflows, not standalone

## Social Media Cross-Posting

- Backend handles platform selection
- Uses platform-agnostic adapter pattern
- Tokens stored securely and rotated
- Posting status tracked per platform

## API Design Rules

- REST-based
- Role-guarded endpoints
- JSON payloads
- Stateless requests
- Explicit endpoints for approval, posting, notification triggers

## Super Admin Capabilities

- Manage user roles
- Audit logs
- Manage distribution groups
- Audit social media tokens
- Override system-level settings

## Explicit Backend Constraints

- No business logic in controllers
- Services must be reusable and testable
- External APIs isolated in adapter classes
- Minimal exception handling for prototype phase

## Current Implementation Analysis (2026-01-29)

- Actual runtime stack: Java 21, Spring Boot 3.5.6, JPA, Spring Security, OAuth2, jjwt.
- REST controllers expose announcement creation, approval, and notifications.
- `Announcement` entity includes `imageUrl`, `startDate`, `endDate`, and distribution group mapping.
- Role-based flow: OSAS/Academic auto-publish, Student Organization routes to approval.
- Email dispatch is integrated into announcement creation/approval via `NotificationService`.
- Supabase is used as managed PostgreSQL; storage uploads require `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`.
- Controllers should remain thin; some upload logic currently sits at controller/service boundary and should be kept isolated in service methods.

## Workflow & Quality Gates

- Update docs/API_REFERENCE.md for any endpoint changes.
- Add or update Spring Boot tests for auth, approvals, and email flows.
- Validate error handling consistency (status codes and error payloads).

## Governance

This constitution is subordinate to the global SONAR Spec Kit constitution.
Repo-specific rules may add constraints but MUST NOT conflict with the global
principles. Amendments require updating this file and relevant templates.

**Version**: 1.2.0 | **Ratified**: 2026-01-29 | **Last Amended**: 2026-03-08
