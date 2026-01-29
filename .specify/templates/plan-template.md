# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21 (Spring Boot 3.5.6)  
**Primary Dependencies**: Spring Web, Spring Security, JPA, jjwt, spring-boot-starter-mail  
**Storage**: PostgreSQL (Supabase)  
**Testing**: Spring Boot Test + JUnit 5 + H2  
**Target Platform**: JVM server (local/dev + cloud)  
**Project Type**: Web application (backend API)  
**Performance Goals**: <200ms p95 for core endpoints  
**Constraints**: OAuth 2.0 with @iacademy.edu.ph; role-based access; no secrets in repo  
**Scale/Scope**: Campus-scale usage (10k users, multiple orgs)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

[Gates determined based on constitution file]

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
src/
└── main/
  ├── java/
  │   └── com/soanar/
  │       ├── config/
  │       ├── controller/
  │       ├── dto/
  │       ├── model/
  │       ├── repository/
  │       ├── service/
  │       └── util/
  └── resources/
    ├── application.properties
    └── templates/

src/
└── test/
  └── java/
    └── com/soanar/
```

**Structure Decision**: Single Spring Boot service with layered packages under
src/main/java/com/soanar and tests under src/test/java.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
