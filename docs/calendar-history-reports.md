# Calendar, history, and reports API

This read-only API combines a child's existing daily execution, study sessions, reading sessions, and photo records at request time. It creates no snapshots and does not materialize missing routine days. Reading facts come from [Books and reading](books-reading.md). Media bytes are never returned.

## Access and configuration

Base URL: `/api/families/{familyId}/children/{childId}`. All four routes require `Authorization: Bearer <access-token>` and an authenticated membership in the specified family. The child must be a family member whose `memberType` is `CHILD`; inactive children remain readable. An unknown family, inaccessible family, missing child, cross-family child, or adult ID returns a safe `404`. Clients may send `Accept-Language: en`, `pt`, or `es` for errors. Successful responses use `application/json`; errors use `application/problem+json`. There is no request body.

Dates use strict ISO `YYYY-MM-DD` format. `from` and `to` are required, inclusive, and have no implicit defaults. Periods cannot be reversed or exceed `app.reports.max-period-days`, which defaults to 366 and can be configured with `REPORTS_MAX_PERIOD_DAYS`. The maximum counts inclusive days: a 366-day period is accepted with the default. Routine, study, reading, and photo records use their stored local dates; timer study dates are assigned in the family's configured timezone when the source record is created. A later timezone change does not move existing dated records.

## Routes and responses

| Method and route suffix | Success | Behavior |
| --- | --- | --- |
| `GET /history/{date}?readingPage=0&readingSize=20` | `200` | One day, including an empty day. |
| `GET /calendar?year=2026&month=9` | `200` | Active dates in ascending order. |
| `GET /history?from=2026-09-01&to=2026-09-30&page=0&size=20` | `200` | Active dates in descending order. |
| `GET /reports?from=2026-09-01&to=2026-09-30` | `200` | Live totals over the requested period. |

`familyId` and `childId` are UUIDs. Calendar `year` and `month` are required integers: year 1–9999, month 1–12. The period history defaults to page 0 and size 20; page must be at least 0, size 1–100. `items` contains at most `size` entries and `hasNext` indicates another page. Dates with no source records are omitted. There is one row per date, so the date itself is the unique ordering key.

### Day detail

```json
{
  "date": "2026-09-21",
  "childId": "11111111-1111-1111-1111-111111111111",
  "routine": null,
  "studies": [],
  "reading": [
    {"id": "55555555-5555-5555-5555-555555555555",
     "childBookId": "66666666-6666-6666-6666-666666666666",
     "bookId": "77777777-7777-7777-7777-777777777777",
     "bookTitle": "A story", "minutes": 10, "pagesRead": 20}
  ],
  "readingPage": 0, "readingSize": 20, "readingHasNext": false,
  "photos": [
    {"id": "22222222-2222-2222-2222-222222222222",
     "media": [{"id": "33333333-3333-3333-3333-333333333333", "position": 0}]}
  ]
}
```

Every top-level field is present. `routine` is `null` when no daily execution exists; otherwise it follows the existing [execution response](daily-execution.md), including its summary and ordered items. `studies` follows the existing [study session response](study-tracking.md), excluding voided sessions. `photos` contains photo-record IDs and ordered media IDs and positions only. Empty sources are arrays. An empty day still returns `200` with `routine: null` and empty arrays. Routine records are never created by a history read.

`reading` contains typed session entries: `id`, `childBookId`, and `bookId` are
UUIDs, `bookTitle` is the current catalog title, and `minutes` and `pagesRead`
are nullable nonnegative integers. Null means unmeasured, not zero. Page ranges
use the source's `endPage - startPage` calculation. Session date is the requested
day; other session fields are available through the reading API.

Only the reading list is paginated by `readingPage` (default 0, minimum 0) and
`readingSize` (default 20, range 1–100). `readingPage`, `readingSize`, and
`readingHasNext` are always present, including on empty days and pages. Sessions
are ordered by creation timestamp descending, then session UUID descending.
Increment `readingPage` while `readingHasNext` is true to fetch all entries;
other sources remain the same for that day. Offsets above 2,147,483,646 are
rejected. The response does not silently truncate reading activity.

### Calendar

```json
{"year": 2026, "month": 9, "days": [
  {"date": "2026-09-21", "hasRoutine": true, "hasStudies": true,
   "hasReading": true, "hasPhotos": false}
]}
```

Only dates with at least one source record appear. `days` is empty for an empty month. Flags indicate record presence, including a routine with no items. Voided study sessions do not create activity. Any reading session creates activity, including date-only sessions with no measurements; completed journeys alone do not. Reading dates are fetched with a distinct-date query, without loading sessions. Calendar months remain available even when the configured report limit is shorter than a month. The calendar does not return record descriptions, session details, or media content.

### Period history

```json
{"items": [
  {"date": "2026-09-21", "hasRoutine": true, "plannedItems": 1,
   "completedItems": 1, "studySessions": 1, "studyDurationSeconds": 120, "readingSessions": 1,
   "photoRecords": 1, "images": 2}
], "page": 0, "size": 20, "hasNext": false}
```

`plannedItems` excludes cancelled snapshots. `completedItems` counts completed snapshots. `studySessions` counts non-voided sessions, including running and paused ones; `studyDurationSeconds` sums persisted duration for completed sessions only. `readingSessions` counts all sessions on that date, including date-only sessions and rereads, through a grouped query. Reading-only dates participate in pagination. `images` counts media links attached to photo records. All counts are nonnegative. The response has no total count.

### Report

```json
{
  "childId": "11111111-1111-1111-1111-111111111111",
  "from": "2026-09-01", "to": "2026-09-30",
  "routine": {"days": 1, "plannedItems": 1, "completedItems": 1},
  "studies": {"sessions": 1, "totalMinutes": 2.00,
    "subjects": [{"subjectId": "44444444-4444-4444-4444-444444444444", "minutes": 2.00}]},
  "reading": {"sessions": 1, "totalMinutes": 10, "pagesRead": 20, "books": 1, "booksCompleted": 0},
  "photos": {"records": 1, "images": 2}
}
```

Routine days count stored daily executions, including ones with no items. Study totals count completed, non-voided sessions only. `totalMinutes` includes subjectless sessions; `subjects` omits them and is sorted by subject ID. Seconds are summed first, then converted to minutes with two decimal places and half-up rounding. Subject IDs refer to the original session association even if a subject is now inactive. Photo records and their attached images are counted from current links. Reading totals follow the reading summary: `sessions` counts all dated sessions,
`totalMinutes` and `pagesRead` sum known measurements (null contributes zero),
`books` counts distinct Book IDs with sessions, and `booksCompleted` counts
journeys currently marked completed whose `completedOn` falls within the period,
even without a session in that period. Rereads of one book count as one `books`
but may count as multiple completions. Overlapping page ranges are added, not
deduplicated. These five fields are always present, non-null, nonnegative integers.
The former placeholder field `distinctBooks` is replaced by `books`; clients
must update that property name. The reading summary shares
`REPORTS_MAX_PERIOD_DAYS`, including when configured above 366 days. All metrics are recalculated on every request, so later edits, voids, and deletions are reflected on the next read.

## Errors and client flow

`400 VALIDATION_ERROR` covers malformed or missing dates, reversed or excessive periods, invalid year/month, and invalid page/size or readingPage/readingSize. `401 UNAUTHENTICATED` means no valid Bearer token. `404 FAMILY_NOT_FOUND` or `FAMILY_MEMBER_NOT_FOUND` means the family or child is missing or inaccessible. Error `detail` is localized from `Accept-Language`; clients should use `code`. Framework parameter-conversion errors may use Spring's standard ProblemDetail without an application code.

After obtaining the family and child IDs, a client can fetch the calendar for visible months, then detail for a selected date. Use history pages for a timeline and reports for totals. These GETs are idempotent and safe to retry after network failures. Since results are live, a later request can differ after another authorized client changes source records. There are no write side effects, special headers, export formats, or report-specific rate limits. Normal Bearer token renewal follows [authentication](authentication.md).
