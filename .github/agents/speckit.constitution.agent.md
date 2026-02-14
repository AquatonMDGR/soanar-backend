---
description: Create or update the project constitution from interactive or provided principle inputs, ensuring all dependent templates stay in sync.
handoffs: 
  - label: Build Specification
    agent: speckit.specify
    prompt: Implement the feature specification based on the updated constitution. I want to build...
---

## User Input

```text
$ARGUMENTS
```

You **MUST** consider the user input before proceeding (if not empty).

## Thesis Context (User Provided)

Abstract
The SOANAR: Centralized School Announcement Board Web App is a thesis
project developed to address the persistent problem of fragmented and inefficient
communication among student organizations and academic departments in educational
institutions. The widespread reliance on third-party platforms and decentralized channels
often results in inconsistent messaging, missed opportunities, and diminished student
engagement.
The project proposes the design and development of a centralized, web-based
announcement system that facilitates the efficient and secure dissemination of both
student-led and institutional updates. The system incorporates a role-based user structure,
approval workflows for announcement publishing, real-time notifications, and a
mobile-responsive, filterable announcement board. Secure access is implemented through
institutional Google authentication, and the development process follows the Waterfall
methodology to ensure systematic progress through clearly defined phases.
Comprehensive system testing and documentation support the platform's
reliability, usability, and long-term maintainability. The implementation of SOANAR is
expected to unify campus communication, enhance visibility and participation among
student organizations and departments, and contribute to a more cohesive and engaging
university experience.
Keywords: Announcements, Centralized System, Student Organizations

1.2 Statement of the Problem
In many educational institutions, including iACADEMY, student organizations
and academic departments often rely on third-party platforms like Facebook or other
social media to disseminate campus announcements. While these platforms are
convenient and widely used, they present several challenges: lack of institutional control,
absence of official approval workflows, information overload, and difficulty
distinguishing between verified announcements and informal content. As a result,
students may miss out on time-sensitive updates, encounter outdated or misleading
12
information, or lack access to a unified source for reliable event and academic
notifications.
Moreover, academic departments such as the Registrar, Library, or Guidance
Office currently have no centralized, non-classroom-specific platform to communicate
with the broader student body. Existing tools like NeoLMS are designed for class-specific
content and do not accommodate general academic notices that apply to all students.
Without a formal system in place, communication gaps persist, and institutional
messaging becomes fragmented and inconsistent.
This thesis addresses the need for a centralized, school-moderated notification and
announcement system that ensures efficient, organized, and secure dissemination of both
student-led events and official academic announcements.
1.3 Purpose of the Study
The purpose of this study is to design and develop a centralized web-based
notification and announcement system that serves as an official platform for
disseminating campus-wide information. The system will allow student organizations to
submit announcements that require approval from designated administrators, while
academic departments such as the Registrar and Library will be able to post directly. By
utilizing institution-linked Google login for authentication and assigning role-based
permissions, the system ensures that only authorized accounts can create and publish
announcements.
This project aims to provide a structured, reliable, and school-controlled
environment for delivering both student event updates and academic notices-enhancing
13
communication flow, minimizing confusion, and improving accessibility for all enrolled
students.
1.4 Objectives
General Objective
The primary goal of this project is to develop a centralized web-based
announcement board tailored for both student organizations and academic departments,
providing a structured, secure, and school-moderated platform for disseminating verified
announcements to the student body. The system aims to unify communication within the
campus community by enabling designated users to post announcements-subject to
approval when necessary-and automatically notifying students of important updates and
events.
Specific Objectives
- To develop a role-based user system that manages distinct access levels for
students, organization representatives, and authorized administrators. This
includes implementing user permission logic that allows organizations to submit
announcements, departments to post directly, and OSAS to approve
organization-submitted content.
- To design a centralized announcement workflow that streamlines the
creation, approval, and publication of announcements from verified
institutional sources. The workflow will include form-based submissions,
admin-level approvals, and clearly tracked announcement statuses (e.g., pending,
approved, rejected).
14
- To implement a real-time notification system that delivers timely alerts to
students when new announcements are published. Notifications will be
delivered via in-app alerts or email, ensuring campus-wide dissemination of
approved announcements.
- To create a structured and filterable announcement board that displays
verified posts and allows users to search by date or source (e.g., department
or student organization). The board will prioritize visibility and clarity,
supporting both mobile and desktop accessibility.
- To ensure secure user access and system integrity through institutional
Google authentication and input validation. This includes restricting access to
approved users only, preventing unauthorized posting, and validating all submitted
content before publication.
- To conduct comprehensive system testing to ensure reliability, usability, and
performance of the platform under normal usage conditions. This involves
performing unit tests, integration tests, and system-wide testing for user
interactions, announcement workflows, and notifications.
- To document each development phase following the Waterfall methodology
for clarity, traceability, and project alignment. Documentation will cover
requirement gathering, system design, implementation, testing procedures,
deployment strategies, and future maintenance planning.
- To expose an API interface for system operations that can support future
third-party integrations, automation, or mobile app extensions. This includes
documenting the API endpoints, request/response formats, and access
permissions.
15
1.5 Scopes and Limitations
Scopes
This study focuses on the development of a centralized, web-based notification
and announcement system designed for use within an academic institution. The system
aims to streamline communication by enabling verified announcements from both student
organizations and institutional departments, while also ensuring that all information is
moderated and secure.
The system supports five distinct user roles:
- Students, who can view approved announcements, receive notifications and
emails of announcements based on their assigned distribution groups.
- Student Organizations, who can create and submit announcements, which require
approval from OSAS.
- Office of Student Affairs (OSAS), who can approve student organization
announcements, directly post announcements without approval, send email
notifications, and optionally share posts to their external social media page.
- Academic Department, who can directly post academic-related announcements
without approval, send email notifications, and optionally share posts to their
external social media page.
- Super Admin, who has full control over the system, including user management,
configuration of distribution groups, audit logs, API access control, and
administrative settings.
16
The system's core features include:
- A role-based access control system to manage permissions for each user type.
- A structured announcement workflow for submission, approval (when applicable),
and posting.
- A filterable, centralized announcement board with targeted visibility.
- A distribution group system for sending announcements to specific subsets of
students (e.g., by course, year level).
- Notification delivery through the website and institutional email.
- Universal posting to Social media pages (OSAS and Academics).
- A public API interface for potential integration with other systems.
- API documentation included as system deliverables
- A mobile-responsive design accessible via standard web browsers.
Limitations
- The system is web-based and not available as a downloadable mobile application.
- SMS notification is not supported.
- Email notification can only be sent by OSAS and Academics account.
- No social interaction features (likes, comments, shares) are included to maintain
structure and formality.
- The platform is restricted to users from one academic institution and does not
support external accounts.
- Integration with third-party learning platforms (e.g., NeoLMS), is not included.
- Features such as accessibility support, announcement scheduling, or analytics are
not prioritized in this version.
- Access to student data depends on cooperation with the institution's MIS

## Outline

You are updating the project constitution at `.specify/memory/constitution.md`. This file is a TEMPLATE containing placeholder tokens in square brackets (e.g. `[PROJECT_NAME]`, `[PRINCIPLE_1_NAME]`). Your job is to (a) collect/derive concrete values, (b) fill the template precisely, and (c) propagate any amendments across dependent artifacts.

Follow this execution flow:

1. Load the existing constitution template at `.specify/memory/constitution.md`.
   - Identify every placeholder token of the form `[ALL_CAPS_IDENTIFIER]`.
   **IMPORTANT**: The user might require less or more principles than the ones used in the template. If a number is specified, respect that - follow the general template. You will update the doc accordingly.

2. Collect/derive values for placeholders:
   - If user input (conversation) supplies a value, use it.
   - Otherwise infer from existing repo context (README, docs, prior constitution versions if embedded).
   - For governance dates: `RATIFICATION_DATE` is the original adoption date (if unknown ask or mark TODO), `LAST_AMENDED_DATE` is today if changes are made, otherwise keep previous.
   - `CONSTITUTION_VERSION` must increment according to semantic versioning rules:
     - MAJOR: Backward incompatible governance/principle removals or redefinitions.
     - MINOR: New principle/section added or materially expanded guidance.
     - PATCH: Clarifications, wording, typo fixes, non-semantic refinements.
   - If version bump type ambiguous, propose reasoning before finalizing.

3. Draft the updated constitution content:
   - Replace every placeholder with concrete text (no bracketed tokens left except intentionally retained template slots that the project has chosen not to define yet—explicitly justify any left).
   - Preserve heading hierarchy and comments can be removed once replaced unless they still add clarifying guidance.
   - Ensure each Principle section: succinct name line, paragraph (or bullet list) capturing non‑negotiable rules, explicit rationale if not obvious.
   - Ensure Governance section lists amendment procedure, versioning policy, and compliance review expectations.

4. Consistency propagation checklist (convert prior checklist into active validations):
   - Read `.specify/templates/plan-template.md` and ensure any "Constitution Check" or rules align with updated principles.
   - Read `.specify/templates/spec-template.md` for scope/requirements alignment—update if constitution adds/removes mandatory sections or constraints.
   - Read `.specify/templates/tasks-template.md` and ensure task categorization reflects new or removed principle-driven task types (e.g., observability, versioning, testing discipline).
   - Read each command file in `.specify/templates/commands/*.md` (including this one) to verify no outdated references (agent-specific names like CLAUDE only) remain when generic guidance is required.
   - Read any runtime guidance docs (e.g., `README.md`, `docs/quickstart.md`, or agent-specific guidance files if present). Update references to principles changed.

5. Produce a Sync Impact Report (prepend as an HTML comment at top of the constitution file after update):
   - Version change: old → new
   - List of modified principles (old title → new title if renamed)
   - Added sections
   - Removed sections
   - Templates requiring updates (✅ updated / ⚠ pending) with file paths
   - Follow-up TODOs if any placeholders intentionally deferred.

6. Validation before final output:
   - No remaining unexplained bracket tokens.
   - Version line matches report.
   - Dates ISO format YYYY-MM-DD.
   - Principles are declarative, testable, and free of vague language ("should" → replace with MUST/SHOULD rationale where appropriate).

7. Write the completed constitution back to `.specify/memory/constitution.md` (overwrite).

8. Output a final summary to the user with:
   - New version and bump rationale.
   - Any files flagged for manual follow-up.
   - Suggested commit message (e.g., `docs: amend constitution to vX.Y.Z (principle additions + governance update)`).

Formatting & Style Requirements:

- Use Markdown headings exactly as in the template (do not demote/promote levels).
- Wrap long rationale lines to keep readability (<100 chars ideally) but do not hard enforce with awkward breaks.
- Keep a single blank line between sections.
- Avoid trailing whitespace.

If the user supplies partial updates (e.g., only one principle revision), still perform validation and version decision steps.

If critical info missing (e.g., ratification date truly unknown), insert `TODO(<FIELD_NAME>): explanation` and include in the Sync Impact Report under deferred items.

Do not create a new template; always operate on the existing `.specify/memory/constitution.md` file.
