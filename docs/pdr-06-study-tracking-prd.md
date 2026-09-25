# PDR-06 — Study Tracking

Status: proposed.

## Outcome and boundaries

PDR-06 records what a family member actually studied, for how long, and with
which subject and notes. PDR-05 remains responsible for daily activity status
and completion. A study session is an independent historical record: it may
link to a daily execution item, but neither requires the other, and finishing a
session never completes an activity automatically.

The feature contains configurable family-wide `StudySubject` records and
`StudySession` records owned by one `FamilyMember`. Adults and children may
study; a member does not need a linked user. Do not add curriculum, grades,
scoring, reports, notifications, or file storage in this feature.

Keep both concepts in a `study/` feature with responsibility-based packages.
Use UUIDs, DTOs, Flyway, service transactions, injected `Clock`, existing
authorization and ProblemDetail infrastructure, and PostgreSQL constraints.
Validate links through the owning feature boundary; avoid service chains and
direct repository access into another feature.

## Domain rules

### Study subjects

A subject belongs to a family and has `id`, `familyId`, `name`, optional
`color`, `active`, `sortOrder`, `createdAt`, and `updatedAt`. Names are required,
trimmed, preserve user capitalization and accents, and have a reasonable API
length limit. Prevent duplicates within a family using a documented
locale-independent normalization: trim, Unicode NFC normalize, then compare
case-insensitively. Color is optional and must match `#RRGGBB`. Subjects are
ordered by `sortOrder` (then a stable ID tie-breaker), not creation time.
Deactivation preserves existing sessions; normal listing returns active
subjects, with an explicit option to include inactive ones.

### Study sessions

A session has `id`, `familyId`, `familyMemberId`, optional `subjectId`, optional
`dailyExecutionItemId`, optional `subjectNameSnapshot`, `date`, optional
`title` and `notes`, `entryMode` (`TIMER` or `MANUAL`), `status`
(`RUNNING`, `PAUSED`, `COMPLETED`, `VOIDED`), optional `startedAt`,
`currentRunStartedAt`, `endedAt`, integer `accumulatedDurationSeconds`,
`createdByUserId`, timestamps, and a version for optimistic concurrency.
Use `LocalDate` for `date` and `Instant` for event timestamps. `notes` should
use a text column with a reasonable API size limit.

Every session belongs to exactly one family member. The member, subject, and
optional daily execution item must belong to the same family; the execution
item must also belong to the same member. A session may have no subject, title,
or execution link. A daily execution item may have multiple sessions.
Capture the subject name when a session is created so history can retain its
context after a rename; retain `subjectId` for identity and aggregation.

Timer events are backend-authoritative. Start records the family's local date,
`startedAt`, and `currentRunStartedAt`, sets duration to zero, and enters
`RUNNING`. Pause adds the current run interval to accumulated seconds, clears
`currentRunStartedAt`, and enters `PAUSED`. Resume starts a new interval without
resetting accumulated time. Finish accepts `RUNNING` or `PAUSED`, includes the
current interval only when running, sets `endedAt`, clears the current run, and
enters `COMPLETED`. Paused time never counts. Keep the start date if a session
crosses local midnight. The client sends commands only; it may display elapsed
time locally and must not send periodic ticks.

Manual creation requires an explicit date and non-negative, bounded duration;
it creates a `COMPLETED` session with `entryMode=MANUAL`. For timer sessions,
the backend derives the date from the family's configured timezone. The
authenticated user supplies `createdByUserId`; clients cannot set it or timer
timestamps.

Allow at most one `RUNNING` session per member, enforced in PostgreSQL with an
appropriate partial unique constraint so concurrent starts/resumes cannot
violate the rule. Paused sessions do not block another running session.
Different members may study concurrently. Use optimistic locking for session
commands and corrections; return a conflict that lets clients reload. Do not
auto-close abandoned timers or infer an end time.

Members may create and operate sessions and view history. Only ADMIN/OWNER may
manage subjects, correct historical session details, or void a session. Voiding
preserves the record, removes it from normal history and all counts, and cannot
be reversed through timer commands. Small corrections use PATCH; do not allow
clients to patch timer state or internal timestamps. Manual date/duration may
be corrected. Completed timer timestamps require an explicitly defined
administrative correction rule before implementation.

## API contract

All routes require Bearer authentication and current family membership. Check
family, member, subject, session, and execution-item relationships on every
operation. Unknown or cross-family resources use the established safe 404
behavior; insufficient roles return 403. Reuse existing ProblemDetail,
localization (`en`, `pt`, `es`), and validation conventions. Add only stable
error codes needed by clients for invalid state, duplicate subject, missing
resources, and concurrency conflict.

Subject routes:

| Method | Route | Access and behavior |
| --- | --- | --- |
| `POST` | `/api/families/{familyId}/study-subjects` | ADMIN/OWNER create |
| `GET` | `/api/families/{familyId}/study-subjects?includeInactive=false` | Any member list, ordered by `sortOrder` |
| `GET` | `/api/families/{familyId}/study-subjects/{subjectId}` | Any member read |
| `PATCH` | `/api/families/{familyId}/study-subjects/{subjectId}` | ADMIN/OWNER edit name, color, or order |
| `POST` | `/api/families/{familyId}/study-subjects/{subjectId}/deactivate` | ADMIN/OWNER deactivate |
| `POST` | `/api/families/{familyId}/study-subjects/{subjectId}/reactivate` | ADMIN/OWNER reactivate |

Session routes share the prefix
`/api/families/{familyId}/members/{memberId}/study-sessions`:

| Method and suffix | Access and behavior |
| --- | --- |
| `POST /start` | Any member; optional `subjectId`, `dailyExecutionItemId`, `title`, and `notes`; creates a running timer session |
| `GET /current` | Any member; return `200` with the running session or `204 No Content` when none exists |
| `POST /{sessionId}/pause`, `/resume`, `/finish` | Any member; apply only valid state transitions |
| `POST` (base route) | Any member; create a completed manual session with `date`, `durationSeconds`, and optional subject, execution item, title, and notes |
| `GET /{sessionId}` | Any member; read a non-voided session |
| `GET` (base route) | Any member; paginated history filtered by inclusive `from`, `to`, and optional `subjectId` |
| `PATCH /{sessionId}` | ADMIN/OWNER; correct allowed historical content; manual date/duration may also be corrected |
| `POST /{sessionId}/void` | ADMIN/OWNER; invalidate a record while retaining it |

History uses the repository page shape (`items`, `page`, `size`, `hasNext`),
page 0 and size 20 by default, maximum size 100. Require or default date bounds
consistently in the integration contract; reject inverted ranges. Sort
deterministically by `date DESC`, then timer `startedAt DESC` with manual
sessions consistently ordered, then `id DESC`. Exclude voided sessions unless
an explicitly authorized administrative query opts in.

`GET /api/families/{familyId}/members/{memberId}/study-summary?from=&to=`
returns total completed duration and per-subject duration/session count for an
inclusive period. Exclude voided and still-running sessions. Define how
subjectless sessions contribute to the total (include them in total, omit them
from the subject breakdown). This is a domain summary, not a general reporting
engine.

Every implemented API must have a human-readable integration document under
`docs/`, linked from README, covering concrete schemas and examples, validation,
statuses/errors, authorization, pagination, command flow, concurrency, and
safe client behavior. OpenAPI complements that document.

## Persistence and verification

Add versioned migrations for `study_subjects` and `study_sessions`. Include
foreign keys, enum/state and non-negative duration checks, family-scoped
subject uniqueness using the agreed normalization, the one-running-session
partial unique constraint, and indexes for member/date history, member/subject
date filtering, and execution-item lookup. Preserve study history when a
member, subject, or plan item is deactivated; do not cascade-delete sessions.

Use PostgreSQL Testcontainers for constraints, queries, pagination, aggregates,
and concurrency. Use a controllable `Clock` for timer and family-timezone tests.
Verify authorization and family/member isolation, subject lifecycle and
normalization, timer transitions and accumulated time, manual creation and
correction, execution-item links, date boundaries and midnight crossing,
voided-session exclusion, subject snapshots, deterministic history, summaries,
and concurrent starts/resumes. Run `./mvnw verify` when implementation is
complete.

## Acceptance

1. Families can manage ordered, colored, active/inactive subjects with
   family-scoped normalized names.
2. Members of any type can have timer or manual study sessions, optionally
   linked to a same-family, same-member daily execution item and subject.
3. Backend-controlled timer commands preserve only running intervals, use the
   family timezone for the session date, and enforce one running session per
   member under concurrency.
4. Session history is durable, correctable by ADMIN/OWNER, voidable without
   deletion, paginated, filterable by date and subject, and summarized without
   counting voided or running sessions.
5. Security, migrations, localized errors, API integration documentation,
   OpenAPI, and PostgreSQL-backed tests protect the behavior.
