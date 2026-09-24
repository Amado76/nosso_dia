# Study tracking — UI integration

BE-06 records study subjects and study sessions independently of daily execution.
All routes require `Authorization: Bearer <accessToken>` and accept optional
`Accept-Language: en|pt|es` (regional variants work). JSON bodies use
`Content-Type: application/json`. Errors use `application/problem+json` with a
stable `code`. All IDs are UUIDs, dates are `YYYY-MM-DD`, and event times are ISO
instants in UTC. The family timezone, not the device timezone, determines timer
session dates. No new configuration or external service is required.

Any current family member account (MEMBER, ADMIN, OWNER) may read subjects,
create and operate sessions for any person in that family, and view history and
summary. The target person may be an adult or child without a linked account.
ADMIN/OWNER alone may manage subjects, correct completed sessions, or void them.
Inaccessible families and resources return safe 404 responses.

## Subjects

Prefix: `/api/families/{familyId}/study-subjects`.

| Method | Suffix | Success | Access |
| --- | --- | --- | --- |
| POST | base | 201 subject | ADMIN/OWNER |
| GET | base, optional `?includeInactive=true` | 200 array ordered by `sortOrder`, then ID | Any member |
| GET | `/{subjectId}` | 200 subject, including inactive | Any member |
| PATCH | `/{subjectId}` | 200 subject | ADMIN/OWNER |
| POST | `/{subjectId}/deactivate` | 200 subject | ADMIN/OWNER |
| POST | `/{subjectId}/reactivate` | 200 subject | ADMIN/OWNER |

POST example: `{"name":" Mathematics ","color":"#3366CC","sortOrder":2}`.
`name` is required, nonblank after trimming, and at most 120 UTF-16 code units.
The stored name preserves capitalization and accents. Names are unique per family
after trim, Unicode NFC normalization, and locale-independent lowercase conversion.
`color` is optional or null and must match `#RRGGBB`; `sortOrder` is optional,
defaults to 0, and must be a nonnegative integer. PATCH accepts a nonempty subset
of `name`, `color`, `sortOrder`; null clears only color. The `active` field cannot
be patched. Deactivation preserves historical sessions. Normal lists include only
active subjects; `includeInactive=true` includes all. Subject IDs remain valid in
old sessions after deactivation, but new sessions cannot select inactive subjects.
Each family can create up to 1,000 subjects, including inactive subjects. Concurrent
creates are serialized per family so the limit also holds for simultaneous requests;
creation at the limit returns 400 `VALIDATION_ERROR`. Updates to the same subject
are serialized, preserving unrelated fields when edits overlap with deactivation.

Subject response example:

```json
{"id":"11111111-1111-4111-8111-111111111111","familyId":"22222222-2222-4222-8222-222222222222","name":"Mathematics","color":"#3366CC","active":true,"sortOrder":2,"createdAt":"2026-09-21T12:00:00Z","updatedAt":"2026-09-21T12:00:00Z"}
```

All fields are present; only `color` may be null. Repeating deactivate or
reactivate succeeds with the requested final state. Subject names remain unique
even after deactivation.

## Sessions and timer commands

Prefix: `/api/families/{familyId}/members/{memberId}/study-sessions`.

| Method | Suffix | Success | Access |
| --- | --- | --- | --- |
| POST | `/start` | 201 running timer | Any member |
| GET | `/current` | 200 running timer or 204 empty | Any member |
| POST | base | 201 completed manual session | Any member |
| GET | `/{sessionId}` | 200 non-voided session | Any member |
| POST | `/{sessionId}/pause` | 200 paused session | Any member |
| POST | `/{sessionId}/resume` | 200 running session | Any member |
| POST | `/{sessionId}/finish` | 200 completed session | Any member |
| PATCH | `/{sessionId}` | 200 corrected completed session | ADMIN/OWNER |
| POST | `/{sessionId}/void` | 200 voided session | ADMIN/OWNER |
| GET | base, filters below | 200 page | Any member |

Start request example:

```json
{"subjectId":"11111111-1111-4111-8111-111111111111","dailyExecutionItemId":null,"title":"Algebra","notes":"Chapter 3"}
```

All start fields are optional or nullable. Manual creation adds required `date`
and `durationSeconds`: `{"date":"2026-09-21","durationSeconds":1800,"subjectId":null}`.
Duration is an integer from 0 through 31,536,000 seconds. `title` is at most 120
UTF-16 code units, `notes` at most 10,000; blank text is stored as null. A supplied
subject must be active and in the family. A supplied execution item must belong
to the same family and target member; it need not be completed. Multiple sessions
can refer to the same item. Neither starting nor finishing a session changes the
daily execution item.

Response example:

```json
{"id":"33333333-3333-4333-8333-333333333333","familyId":"22222222-2222-4222-8222-222222222222","familyMemberId":"44444444-4444-4444-8444-444444444444","subjectId":"11111111-1111-4111-8111-111111111111","dailyExecutionItemId":null,"subjectNameSnapshot":"Mathematics","date":"2026-09-21","title":"Algebra","notes":"Chapter 3","entryMode":"TIMER","status":"RUNNING","startedAt":"2026-09-21T12:00:00Z","currentRunStartedAt":"2026-09-21T12:00:00Z","endedAt":null,"accumulatedDurationSeconds":0,"createdByUserId":"55555555-5555-4555-8555-555555555555","createdAt":"2026-09-21T12:00:00Z","updatedAt":"2026-09-21T12:00:00Z","version":0}
```

Every field is present. `subjectId`, `dailyExecutionItemId`, snapshot, title,
notes, and timer timestamps may be null. Manual sessions have null timer
timestamps, `entryMode=MANUAL`, and `status=COMPLETED`. The subject name is captured
at creation and survives renames. The authenticated user supplies
`createdByUserId`; clients cannot supply actor, status, timer date or timestamps.

Timer flow: start → pause → resume → finish, or start → finish, or paused → finish.
Only running intervals count. Start fixes the date in the family's timezone and
it never changes when midnight passes. The server measures elapsed seconds;
clients may display an estimated live counter from `currentRunStartedAt` but must
send commands only, without ticks. Paused sessions do not block a new timer.
At most one running session exists per member, including concurrent requests.
GET `/current` reads that running session only; a paused session yields 204.
No background process closes abandoned timers.

PATCH accepts a nonempty subset of `subjectId`, `dailyExecutionItemId`, `title`,
`notes`, `date`, `durationSeconds`. Null clears the optional reference and text
fields. Date and duration require non-null values and are allowed only for
completed manual sessions. Completed timer dates, durations, and timestamps are
immutable; no administrative timer time reconstruction is defined. PATCH does not
accept state, actor, or timestamps. Only completed sessions can be corrected.
Voiding preserves the row and excludes it from normal reads, history, and totals;
timer commands cannot reverse it. A running timer void includes its current
interval and records its end. If its total duration exceeds 31,536,000 seconds,
voiding caps the stored duration at that limit and releases the running slot.
Pause and finish still return 409 `STUDY_INVALID_STATE` above the limit; an
ADMIN/OWNER must void the abandoned timer before a new timer can start.
Voiding twice returns 404 after the first void.

## History and summary

GET base requires inclusive `from` and `to`; `from <= to`. Optional `subjectId`
filters to that subject, including inactive subjects. `page` defaults to 0 and
`size` to 20; size must be 1–100 and the offset must fit the supported integer
range. Example: `?from=2026-09-01&to=2026-09-30&subjectId=<uuid>&page=0&size=20`.
History includes running, paused, and completed sessions, excludes voided ones,
and sorts by date descending, timer start descending (manual sessions last within
each date), then ID descending. The response is
`{"items":[<session response>],"page":0,"size":20,"hasNext":false}`.
There is no total count. Offset pages can shift during concurrent writes.

GET `/api/families/{familyId}/members/{memberId}/study-summary?from=2026-09-01&to=2026-09-30`
requires the same inclusive date bounds. Response example:

```json
{"from":"2026-09-01","to":"2026-09-30","totalDurationSeconds":3600,"subjects":[{"subjectId":"11111111-1111-4111-8111-111111111111","durationSeconds":1800,"sessionCount":1}]}
```

Only completed, non-voided sessions count. Subjectless sessions contribute to
`totalDurationSeconds` and are omitted from `subjects`. Subject rows sort by ID.
The summary is scoped to the addressed family member.

## Errors, retries, and storage

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid body, range, size, or duration |
| 401 | `UNAUTHENTICATED` | Refresh or sign in |
| 403 | `FORBIDDEN` | ADMIN/OWNER required |
| 404 | `FAMILY_NOT_FOUND`, `FAMILY_MEMBER_NOT_FOUND` | Inaccessible family or member |
| 404 | `STUDY_SUBJECT_NOT_FOUND`, `STUDY_SESSION_NOT_FOUND`, `STUDY_EXECUTION_ITEM_NOT_FOUND` | Missing or mismatched resource |
| 409 | `STUDY_SUBJECT_DUPLICATE` | Rename or choose another subject |
| 409 | `STUDY_INVALID_STATE` | Reload the session and choose a valid command |
| 409 | `STUDY_CONFLICT` | Reload after concurrent start, resume, or edit |

Example: `{"type":"about:blank","title":"Conflict","status":409,"detail":"Study session changed concurrently. Reload before retrying.","instance":"/api/families/...","code":"STUDY_CONFLICT"}`.
Malformed JSON, scalar field types, UUIDs, and missing query parameters use
framework 400 responses and do not promise an application code. Error details
are localized for application codes; branch on `code` when present.
Commands are state transitions, not idempotent retries. After a network timeout,
GET the session or `/current` before sending a command again. Session writes use
optimistic locking (`version` in the response); simultaneous changes can return
409. PostgreSQL also enforces one running timer per person. The schema is added by
Flyway V10; it does not rewrite earlier study or execution data.
