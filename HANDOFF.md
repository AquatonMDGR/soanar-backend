# Backend Handoff - February 28, 2026

## Scope Completed

- Added student-specific published visibility filtering so dashboard/feed only returns announcements in target audience.
- Enforced year+school combined matching as logical AND when both are provided.
- Added school-year cutoff behavior for published visibility in student-facing feed logic.
- Added force-send upcoming-term digest endpoint: `POST /api/emails/send-termly-upcoming`.
- Restricted termly send operations to `OSAS` and `Super Admin`.
- Standardized year-level compatibility to support both formats during transition:
  - Primary format: `1st`, `2nd`, `3rd`, `4th`
  - Legacy-compatible aliases: `1st Year`, `2nd Year`, `3rd Year`, `4th Year`

## Key Files Updated

- `src/main/java/com/soanar/service/RecipientResolverService.java`
- `src/main/java/com/soanar/service/AnnouncementService.java`
- `src/main/java/com/soanar/controller/AnnouncementController.java`
- `src/main/java/com/soanar/repository/AnnouncementRepository.java`
- `src/main/java/com/soanar/service/EmailService.java`
- `src/main/java/com/soanar/controller/EmailController.java`

## Operational Notes

- SMTP sender migration requires `MAIL_*` environment variable updates only.
- Google Cloud OAuth settings are not required for SMTP sender change.

## Validation Snapshot

- Backend compile (`mvnw.cmd -DskipTests compile`): PASS

## Recommended Next Checks

1. Role smoke test: Student, Student Organization, OSAS, Super Admin.
2. Targeting smoke test combinations:
   - year only
   - school only
   - year + school (AND)
   - manual email + distribution groups
3. Force-send termly digest test as OSAS and Super Admin.
