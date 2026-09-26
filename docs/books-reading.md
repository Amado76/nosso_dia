# Books and reading API

PDR-08 provides BeeHome family books, individual child reading journeys, and dated
reading sessions. Base URL: `/api`. This is a private reading log, not a digital
library, catalog lookup, or competition. No new configuration or dependencies are
required. Flyway V12 creates `books`, `child_books`, and `reading_sessions`.

## Authentication and ownership

Send `Authorization: Bearer <access-token>` on every request and
`Content-Type: application/json` for JSON bodies. Use the existing
[authentication flow](authentication.md); this module does not issue tokens.
All family roles can read. OWNER and ADMIN can create, change, and delete reading
resources, following the family editor policy. Every child write requires an
active family member of type CHILD. Inactive children retain readable history.
`childId` is the ID returned by the [family members API](family-members.md).

All IDs are UUIDs. The complete family/child/resource relationship is checked in
the backend. Missing or inaccessible families, children, books, journeys, and
sessions return safe 404 responses. A book may be shared by different children
in the same family. Ownership and audit fields cannot be supplied by clients.

## Routes

In the table, `F` means `/api/families/{familyId}` and `C` means
`F/children/{childId}`.

| Method | Route | Success | Purpose |
| --- | --- | --- | --- |
| POST | `F/books` | 201 + Location | Create a family book. |
| GET | `F/books` | 200 | Page the family catalog. |
| GET | `F/books/{bookId}` | 200 | Read book metadata. |
| PUT | `F/books/{bookId}` | 200 | Replace all editable metadata. |
| DELETE | `F/books/{bookId}` | 204 | Delete an unused book. |
| POST | `C/books` | 201 + Location | Start a child reading journey. |
| GET | `C/books` | 200 | Page journeys, optionally filtered by status. |
| GET | `C/books/{childBookId}` | 200 | Read a journey and derived progress. |
| PATCH | `C/books/{childBookId}` | 200 | Change status and/or dates. |
| POST | `C/reading-sessions` | 201 + Location | Record a reading activity. |
| GET | `C/reading-sessions` | 200 | Page sessions by dates and book. |
| GET | `C/reading-sessions/{sessionId}` | 200 | Read a session. |
| PUT | `C/reading-sessions/{sessionId}` | 200 | Replace editable session details. |
| DELETE | `C/reading-sessions/{sessionId}` | 204 | Remove a session. |
| GET | `C/reading-summary` | 200 | Calculate period totals. |

DELETE responses have no body. All other successful responses use
`application/json`. POST returns the address of the created resource in `Location`.
Unknown request fields and incorrect scalar types are rejected. Integers must be
JSON integers, not strings or fractional numbers. Dates use ISO `yyyy-MM-dd`
without timezones; technical timestamps use ISO UTC instants.

## Books and covers

Create and replace accept:

| Field | Type | Required / nullable | Rules |
| --- | --- | --- | --- |
| title | string | Required, non-null | Trimmed, nonblank, maximum 300 characters. |
| author | string | Optional, nullable | Trimmed, maximum 200 characters. |
| isbn | string | Optional, nullable | Trimmed, maximum 32 characters; no catalog lookup or ISBN checksum validation. |
| totalPages | integer | Optional, nullable | Positive, at most 2147483647. |
| coverMediaId | UUID | Optional, nullable | Existing IMAGE in the same family. |
| tagIds | UUID array | Optional, non-null when supplied | Up to 100 unique IDs of tags in this family; `[]` clears links. |

Blank optional text becomes null. PUT requires title and replaces every optional
field: omission or null clears it. Reducing totalPages below existing session
page counts or recorded positions is rejected. Removing the limit is allowed.
Book PUT clears tag links when `tagIds` is omitted or `[]` is supplied. A supplied
array replaces all links atomically; a missing or foreign-family tag returns 404
`TAG_NOT_FOUND`.

Minimal request: `{"title":"School storybook"}`. Full request:

```json
{
  "title": "The Hobbit",
  "author": "J. R. R. Tolkien",
  "isbn": "9780261103344",
  "totalPages": 310,
  "coverMediaId": "5481374b-c772-4c7e-a726-7f08326bd6a1"
}
```

A book response contains all five metadata fields above (optional values can be null), plus
non-null `id`, `familyId`, `createdBy` (UUIDs), `createdAt`, and `updatedAt` (instants).
It also contains `tags`, an array of `{id,name,color}` with nullable color; it is
empty when the book has no tags. See [global tags](global-tags.md) for normalization,
validation, and the tag management API.
Only the authenticated user supplies `createdBy` internally.

Upload through [Media](photos-media.md) first, retain the returned ID, then create
or replace the book. Display the cover using the authenticated media content
endpoint. There is no separate book upload or public cover URL. A used cover is
excluded from `unattached=true` and its deletion returns `MEDIA_IN_USE`. Clearing
a cover or deleting an unused book retains its media. A book referenced by **any**
journey cannot be deleted, including journeys without sessions; use status changes
to retain history. Titles and covers in historical responses reflect current book
metadata, not snapshots.

## Child reading journeys

Create accepts required non-null `bookId`, optional `status` (default PLANNED when
omitted; explicit null invalid), and optional nullable `startedOn` / `completedOn`.

```json
{"bookId":"15f6f533-d82e-49aa-a145-ec7f88b42842","status":"READING","startedOn":"2026-09-01"}
```

Statuses are `PLANNED`, `READING`, `COMPLETED`, and `ABANDONED`. PLANNED and READING
are active: only one such journey per child/book is allowed. After completion or
abandonment, a new journey can represent a reread. Different children can use the
same book concurrently. The database also enforces active uniqueness.

PATCH accepts a nonempty subset of `status`, `startedOn`, and `completedOn`.
Omission preserves the existing value; null clears dates but cannot clear status.
`bookId` cannot be included in PATCH. COMPLETED always requires explicit
`completedOn`; other statuses require it to be null. When reopening a completed
journey, clear `completedOn` in the same PATCH. If both dates exist,
`completedOn >= startedOn`. Dates are not inferred from today or from sessions.

```json
{"status":"COMPLETED","completedOn":"2026-09-24"}
```

A journey response contains non-null `id`, `familyId`, `childId`, `bookId`, `status`,
`createdAt`, `updatedAt`, and `book` (the complete book response). It also contains
nullable `startedOn`, `completedOn`, `currentPage`, and `progressPercentage`.

`currentPage` is the endPage from the latest positioned session ordered by
`date DESC, createdAt DESC, id DESC`. Sessions without positions do not overwrite
it. A later positioned session may lower it during a reread. When position and
positive totalPages are known, progress is `100 * currentPage / totalPages`, rounded
to two decimal places. Otherwise progress is null. Completion does not fabricate
100% or any sessions. Page sums never determine current position. Edits and
deletions change progress naturally; no counter or percentage is stored.

## Sessions

POST requires non-null `childBookId` and `date`. PUT requires `date` and replaces
all optional fields; `childBookId` must be omitted even if unchanged. To correct
an association, delete the wrong session and create one for the correct journey.

| Field | Type | Rules |
| --- | --- | --- |
| minutes | nullable integer | Optional, 0–2147483647; no timer timestamps. |
| pagesRead | nullable integer | Optional, nonnegative; cannot exceed known totalPages. |
| startPage | nullable integer | Optional position, zero-based boundary allowed; requires endPage. |
| endPage | nullable integer | Requires startPage, at least startPage, at most known totalPages. |
| notes | nullable string | Optional; trimmed, blank becomes null, maximum 2000 characters. |

When both positions are supplied, `pagesRead = endPage - startPage`: 45 → 57 means
12 pages; 0 → 310 means 310 pages. An explicitly supplied count must match the
calculated value. Positions must be supplied together. Without positions, a
standalone pagesRead is valid. The combination of journey and date is itself a
meaningful activity, so a date-only session is also valid. Zero values are allowed.
There is no inferred status change, requirement to be READING, or requirement that
the session fit journey dates: historical corrections can be entered for any
status. Future dates are not rejected.

```json
{
  "childBookId": "7571aff3-df9d-4c0e-a924-9b9b326dd2f3",
  "date": "2026-09-24",
  "minutes": 25,
  "pagesRead": 14,
  "notes": "Read most of the chapter independently."
}
```

Response fields are `id`, `familyId`, `childId`, `childBookId`, `book` (complete
book response), `date`, `minutes`, `pagesRead`, `startPage`, `endPage`, `notes`,
`createdBy`, `createdAt`, and `updatedAt`. Measurements and notes are nullable;
all other fields are non-null. Creator and createdAt survive replacement.

## Lists, calendar source, and metrics

All list responses use `{"items":[],"page":0,"size":20,"hasNext":false}` with
resource response objects in items. No total count is calculated. `page` defaults
to 0 and must be nonnegative; `size` defaults to 20 and must be 1–100. Offsets above
2147483646 are invalid. Ordering is fixed, with UUID as the final tie-breaker:

- Books and journeys: `createdAt DESC, id DESC`. Journeys accept optional `status`.
- Books accept repeated `tagIds` query parameters; all requested tags must be
  assigned. The filter runs before pagination. Duplicate IDs return 400, while
  missing or foreign-family tag IDs return 404.
- Sessions: `date DESC, createdAt DESC, id DESC`. Optional `from`, `to`, and `bookId`
  filter inclusive dates and book across all its journeys. Either date bound can
  be omitted; a reversed range is invalid. A supplied book must belong to the family.

`GET C/reading-summary?from=2026-09-01&to=2026-09-30` requires both dates, with an
inclusive maximum configured by `app.reports.max-period-days`
(`REPORTS_MAX_PERIOD_DAYS`, default 366), shared with history and reports:

```json
{"from":"2026-09-01","to":"2026-09-30","sessions":16,"totalMinutes":430,"pagesRead":184,"books":3,"booksCompleted":1}
```

All counts are non-null 64-bit integers. Null measurements contribute zero.
`sessions` counts all activities, even date-only ones. `books` counts distinct
Book IDs with sessions in the period, including multiple journeys of the same
book only once. `booksCompleted` counts COMPLETED journeys whose completedOn is
in the period, even with no sessions; two completed rereads count twice. Pages are
recorded activity, so overlaps/rereads add to totals and can exceed totalPages
across sessions. Empty periods return zeros. Every metric is calculated from
source rows on demand, without persisted counters.

The authorized `ReadingService.historyDates` provides distinct activity dates for
calendar `hasReading`, querying only dates rather than loading sessions. Summary
and paginated sessions are also available through the owning service boundary.
These sources power the [calendar/history/report integration](calendar-history-reports.md).

## Errors, concurrency, and client workflow

Errors use RFC 9457 `application/problem+json` and the global localized handler.
`Accept-Language` supports `en`, `pt`, and `es`, defaulting to English. Branch on
stable `code`, never on translated text. For example:

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"This child already has an active journey for this book.","code":"BOOK_ALREADY_ACTIVE","instance":"/api/families/15f6f533-d82e-49aa-a145-ec7f88b42842/children/7571aff3-df9d-4c0e-a924-9b9b326dd2f3/books"}
```

| Status | Codes | Client action |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | Correct required fields, types, unknown fields, lengths, filters, or pagination. |
| 400 | INVALID_PAGE_RANGE | Correct negative, inconsistent, incomplete, or over-limit pages. |
| 400 | INVALID_READING_DURATION | Correct negative minutes. |
| 400 | INVALID_READING_SESSION | Supply session date or consistent status/completion dates. |
| 400 | BOOK_MEDIA_INVALID | Choose an existing image in this family. |
| 401 | UNAUTHENTICATED | Follow the existing token refresh/login flow. |
| 403 | FORBIDDEN | Reading writes require OWNER or ADMIN. |
| 404 | FAMILY_NOT_FOUND, FAMILY_MEMBER_NOT_FOUND, BOOK_NOT_FOUND, CHILD_BOOK_NOT_FOUND, READING_SESSION_NOT_FOUND | Refresh the current authorized resource selection. |
| 404 | TAG_NOT_FOUND | Select tags from the current family. |
| 409 | BOOK_ALREADY_ACTIVE | Reuse the current journey or close it before a reread. |
| 409 | BOOK_IN_USE | Retain the book and change journey status. |

Malformed JSON and framework errors follow the global API contract. Semantic
validation uses stable codes and does not promise a field-errors array. Never send
ownership/audit fields or the hidden internal `fields` property.

Typical flow: upload optional cover → create book → create journey → add sessions
→ PATCH completion with its historical date → read current/history lists and
summary. No external services are called inside reading transactions. Child writes
serialize with child updates; book writes and session page validation serialize
on the book row. Image attachment serializes with media deletion. Simultaneous
edits are applied in lock order, with the later replacement winning; no ETags or
optimistic version token are exposed. Refresh after a concurrent edit.

POST is not idempotent. After a timeout, inspect lists before retrying to avoid
duplicate books/sessions or reread journeys. PUT/PATCH can be retried with the same
body for the same intended state, but updatedAt changes and a concurrent editor
may have changed the resource. Repeated DELETE returns 404 after the first 204.
No reading-specific rate limit or idempotency key is introduced; existing security
rules apply. Deletes never synchronize counters because counters do not exist.

## Calendar, history, and reports integration

[Calendar, history, and reports](calendar-history-reports.md) reads sessions and
summary totals through the reading service. Date-only sessions create calendar
activity and count in history. Day detail paginates typed reading entries.
Completions contribute to report totals on their completion date even without
sessions, but do not create calendar or history dates on their own. Reads remain
live after edits and deletions; the reading module owns all source calculations.
