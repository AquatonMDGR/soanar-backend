# 006 - Backend Cleanup and Targeting Implementation Plan

**Repository**: soanar-backend  
**Status**: Ready for execution  
**Owner**: Backend Team

## Implementation Status Snapshot (as of 2026-02-25)

### Already Implemented
- ✅ Public announcement endpoint exists (`GET /api/announcements/public/{id}`).
- ✅ Share endpoint exists (`GET /share/announcement/{id}`) with server-rendered Open Graph tags.
- ✅ `DevDataController` is dev-gated (`dev.mode=true`).

### Not Yet Complete (Required)
- ❌ `SecurityConfig` still permits `/api/db/**` publicly.
- ❌ Debug endpoint `/api/announcements/{id}/debug-images` is still exposed.
- ❌ Legacy crossposting artifacts remain (`CrossPostedAnnouncement`, repository, frontend crosspost calls).
- ❌ Targeting payload is not yet implemented end-to-end (year/school/groups/manual emails).
- ❌ Notification API contract cleanup is incomplete (mixed legacy/new patterns still active).

## Teammate Assignment Queue (Immediate)

### Backend Engineer A - Security + Hardening
1. BE-001
2. BE-002
3. BE-004
4. BE-040

### Backend Engineer B - Crossposting Cleanup
1. BE-010
2. BE-011
3. BE-012
4. BE-013

### Backend Engineer C - Targeting Core
1. BE-020
2. BE-021
3. BE-022
4. BE-023
5. BE-025

### Backend Engineer D - Notification Contract
1. BE-030
2. BE-031
3. BE-032
4. BE-033

## Definition of Done for Backend Plan
- All Phase 1-5 checkboxes complete.
- No crossposting API/model/repository path remains in active flow.
- Announcement targeting inputs produce correct recipient sets in production-like tests.
- Frontend team receives finalized endpoint contract after BE-042.

## Goal
Complete backend cleanup after crossposting removal and implement real recipient targeting for announcements.

## Success Criteria
- No dead/legacy crossposting endpoints or services remain active.
- Announcement creation supports real targeting filters and manual recipients.
- Dev/debug endpoints are not publicly exposed in production.
- All protected APIs are aligned with frontend usage.
- `./mvnw clean compile` passes.

## Scope
### In Scope
- Security hardening (`SecurityConfig`, dev/debug endpoint access)
- Crossposting backend cleanup (unused models/repos/services/controllers)
- Targeting API contract and filtering logic
- Notification recipient resolution updates
- API consistency for notification endpoints

### Out of Scope
- Frontend UI refactors (tracked in frontend plan)
- New analytics features beyond required cleanup

## Execution Order
1. Security and endpoint hardening
2. Crossposting artifact cleanup
3. Targeting model + API implementation
4. Notification API compatibility cleanup
5. Validation and handoff

## Task Board

### Phase 1 - Security and Endpoint Hardening
- [ ] BE-001 Review and lock down `permitAll` matchers in `SecurityConfig`.
- [ ] BE-002 Remove public access for `/api/db/**` in non-dev environments.
- [x] BE-003 Confirm `/share/**` and `/api/announcements/public/**` remain public by design.
- [ ] BE-004 Gate debug endpoint `/api/announcements/{id}/debug-images` behind dev profile or role.
- [x] BE-005 Verify `DevDataController` is disabled unless `dev.mode=true`.

**Acceptance**
- Non-authenticated access only works for explicitly public routes.
- Debug routes are unavailable in production mode.

### Phase 2 - Crossposting Backend Cleanup
- [ ] BE-010 Identify remaining crossposting classes and references.
- [ ] BE-011 Remove unused `CrossPostedAnnouncement` model/repository if not required for historical reads.
- [ ] BE-012 Remove stale social-media integration artifacts not used by current flow.
- [ ] BE-013 Delete any orphaned API contracts for `/crosspost` and status checks.
- [ ] BE-014 Ensure organization settings fields still needed by product are retained; remove only dead fields.

**Acceptance**
- No active code path references removed crossposting flow.
- Compile passes with no missing symbol errors.

### Phase 3 - Targeting Implementation (Backend Core)
- [ ] BE-020 Define request payload fields for targeting:
  - year levels (multi-select)
  - schools (multi-select)
  - distribution groups (multi-select IDs)
  - manual emails (list)
- [ ] BE-021 Update DTO/entity mapping for announcement creation request.
- [ ] BE-022 Implement recipient resolver service with OR logic across target types.
- [ ] BE-023 De-duplicate recipients and validate email format.
- [ ] BE-024 Exclude non-student recipients unless explicitly targeted by role rule.
- [ ] BE-025 Integrate resolver into announcement publish/notify flow.

**Acceptance**
- Targeting fields affect real recipients.
- Empty targeting still defaults to current global behavior.

### Phase 4 - Notification API Consistency
- [ ] BE-030 Standardize notification endpoints used by frontend (`/mark-read`, `/mark-all-read`, paginated fetch shape).
- [ ] BE-031 Keep backward-compatible aliases only if currently consumed.
- [ ] BE-032 Normalize error payload format (`{ error, message, code? }`).
- [ ] BE-033 Confirm read/unread count endpoints align with frontend polling.

**Acceptance**
- Frontend no longer gets 400 errors due to contract mismatch.

### Phase 5 - Validation and Handoff
- [ ] BE-040 Run `./mvnw clean compile`.
- [ ] BE-041 Smoke test: login, feed fetch, create announcement, notifications, public share endpoint.
- [ ] BE-042 Produce final endpoint matrix for frontend team.
- [ ] BE-043 Update backend docs (`README` or `docs/API_REFERENCE.md`) with changed contracts.

## Risks and Mitigations
- **Risk**: Removing legacy entities may break migrations/history.
  - **Mitigation**: Keep DB tables or add deprecation note before deletion.
- **Risk**: Targeting logic can accidentally over-notify.
  - **Mitigation**: Add preview logs/count before dispatch in initial rollout.

## Dependencies
- Frontend team must adopt new targeting payload format from BE-020.
- Shared agreement required on notification response contract in BE-030.

## Handoff Notes
- Keep commits grouped by phase.
- Include before/after endpoint list in PR description.
- Tag frontend team when BE-020 and BE-030 are merged.