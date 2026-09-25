# Calendar, history, and reports API

This read-only API combines a child's existing daily execution, study sessions, and photo records at request time. It creates no snapshots and does not materialize missing routine days. Reading records are not yet stored by BeeHome, so reading arrays, flags, and totals are empty or zero. Media bytes are never returned.

## Access and configuration

Base URL: `/api/families/{familyId}/children/{childId}`. All four routes require `Authorization: Bearer <access-token>` and an authenticated membership in the specified family. The child must be a family member whose `memberType` is `CHILD`; inactive children remain readable. An unknown family, inaccessible family, missing child, cross-family child, or adult ID returns a safe `404`. Clients may send `Accept-Language: en`, `pt`, or `es` for errors. Successful responses use `application/json`; errors use `application/problem+json`. There is no request body.

Dates use strict ISO `YYYY-MM-DD` format. `from` and `to` are required, inclusive, and have no implicit defaults. Periods cannot be reversed or exceed `app.reports.max-period-days`, which defaults to 366 and can be configured with `REPORTS_MAX_PERIOD_DAYS`. The maximum counts inclusive days: a 366-day period is accepted with the default. Routine, study, and photo records use their stored local dates; timer study dates are assigned in the family's configured timezone when the source record is created. A later timezone change does not move existing dated records.

## Routes and responses

| Method and route suffix | Success | Behavior |
| --- | --- | --- |
| `GET /history/{date}` | `200` | One day, including an empty day. |
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
  "reading": [],
  "photos": [
    {"id": "22222222-2222-2222-2222-222222222222",
     "media": [{"id": "33333333-3333-3333-3333-333333333333", "position": 0}]}
  ]
}
```

Every top-level field is present. `routine` is `null` when no daily execution exists; otherwise it follows the existing [execution response](daily-execution.md), including its summary and ordered items. `studies` follows the existing [study session response](study-tracking.md), excluding voided sessions. `photos` contains photo-record IDs and ordered media IDs and positions only. Empty sources are arrays. An empty day still returns `200` with `routine: null` and empty arrays. Routine records are never created by a history read.

### Calendar

```json
{"year": 2026, "month": 9, "days": [
  {"date": "2026-09-21", "hasRoutine": true, "hasStudies": true,
   "hasReading": false, "hasPhotos": false}
]}
```

Only dates with at least one source record appear. `days` is empty for an empty month. Flags indicate record presence, including a routine with no items. Voided study sessions do not create activity. The calendar does not return record descriptions, session details, or media content.

### Period history

```json
{"items": [
  {"date": "2026-09-21", "hasRoutine": true, "plannedItems": 1,
   "completedItems": 1, "studySessions": 1, "studyDurationSeconds": 120,
   "photoRecords": 1, "images": 2}
], "page": 0, "size": 20, "hasNext": false}
```

`plannedItems` excludes cancelled snapshots. `completedItems` counts completed snapshots. `studySessions` counts non-voided sessions, including running and paused ones; `studyDurationSeconds` sums persisted duration for completed sessions only. `images` counts media links attached to photo records. All counts are nonnegative. The response has no total count.

### Report

```json
{
  "childId": "11111111-1111-1111-1111-111111111111",
  "from": "2026-09-01", "to": "2026-09-30",
  "routine": {"days": 1, "plannedItems": 1, "completedItems": 1},
  "studies": {"sessions": 1, "totalMinutes": 2.00,
    "subjects": [{"subjectId": "44444444-4444-4444-4444-444444444444", "minutes": 2.00}]},
  "reading": {"sessions": 0, "distinctBooks": 0, "pagesRead": 0},
  "photos": {"records": 1, "images": 2}
}
```

Routine days count stored daily executions, including ones with no items. Study totals count completed, non-voided sessions only. `totalMinutes` includes subjectless sessions; `subjects` omits them and is sorted by subject ID. Seconds are summed first, then converted to minutes with two decimal places and half-up rounding. Subject IDs refer to the original session association even if a subject is now inactive. Photo records and their attached images are counted from current links. Reading totals remain zero until a reading source is implemented. All metrics are recalculated on every request, so later edits, voids, and deletions are reflected on the next read.

## Errors and client flow

`400 VALIDATION_ERROR` covers malformed or missing dates, reversed or excessive periods, invalid year/month, and invalid page/size. `401 UNAUTHENTICATED` means no valid Bearer token. `404 FAMILY_NOT_FOUND` or `FAMILY_MEMBER_NOT_FOUND` means the family or child is missing or inaccessible. Error `detail` is localized from `Accept-Language`; clients should use `code`. Framework parameter-conversion errors may use Spring's standard ProblemDetail without an application code.

After obtaining the family and child IDs, a client can fetch the calendar for visible months, then detail for a selected date. Use history pages for a timeline and reports for totals. These GETs are idempotent and safe to retry after network failures. Since results are live, a later request can differ after another authorized client changes source records. There are no write side effects, special headers, export formats, or report-specific rate limits. Normal Bearer token renewal follows [authentication](authentication.md).
