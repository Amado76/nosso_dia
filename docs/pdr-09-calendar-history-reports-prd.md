# PDR-09 — Daily History, Reports, Studies, Activities, and PDF Export

Status: proposed.

## 1. Goal and scope

Provide an authorized, live view of a child's records for a date or period. The feature supports daily reports, calendar/history views, period and annual summaries, and on-demand PDF export of a daily report.

Daily history is assembled at read time from the owning domains. It is not a stored copy or snapshot. The initial sources are study records, reading sessions, extracurricular activity records, routine records, and photo records. Use the existing implementation and feature boundaries wherever they fit; add only missing capabilities. Do not create parallel source entities or generic activity tables to make presentation uniform.

```text
Source domains → DailyHistoryService → DailyHistoryResponse → client and PDF
```

The client and PDF renderer must use the same aggregate and business rules. PDF generation is on demand and server-side; it must not use a screenshot, Flutter capture, WebView, or headless browser.

## 2. Official records and free-form tags

These concepts have distinct purposes and must never be conflated:

| Concept | Meaning | Official identity |
| --- | --- | --- |
| `StudySubject` | A family's reusable study subject | `StudySubject.id` |
| `ExtracurricularActivity` | A family's reusable extracurricular activity | `ExtracurricularActivity.id` |
| `Tag` | A family's free-form, cross-domain classification | `Tag.id` |

Examples include the official study subject “Science,” the official activity “Museum Visit,” and tags such as “Science,” “Museum,” “Nature,” and “Outdoors.” A tag named “Science” is not the “Science” study subject.

Tags support classification, filtering, search, organization, and secondary analysis. They never determine a study subject or extracurricular activity, and they never contribute to official study or activity totals. Records may have zero or more tags, including tags that share a name with an official subject or activity.

## 3. Study subjects and study records

Study subjects have their own family-scoped catalog, using the existing equivalent if one exists. Conceptually, a subject has an ID, family ID, name, position, active state, and creation/update timestamps. Names are not enums; each family can maintain its own subjects. Prevent equivalent duplicate names within a family using the project's established name-normalization policy. Grouping and relationships use the subject ID, never the subject's text name. Renaming a subject therefore preserves historical associations. Prefer deactivation (`active = false`) when a subject with history should no longer be offered for new records; it remains available in historical reports.

Each study record must reference a `StudySubject` and the relevant family, child, and date. Reuse `StudyRecord`, `StudySession`, or the existing suitable entity; do not create a duplicate. A record supports:

- `topic` (the specific topic for this record, distinct from the subject)
- optional `durationMinutes`; when present it must be greater than zero
- optional `description` (what was done)
- optional `comments` (observations about how it went)
- optional free-text `material`
- optional `startPage` and `endPage`
- zero or more family-scoped tags
- creator and creation/update metadata, as appropriate to the existing model

The frontend owns timing and submits only the final duration. Page numbers, when present, must be greater than zero; if both are present, `endPage >= startPage`. Either page endpoint may be recorded on its own. Material is not a required catalog.

## 4. Extracurricular catalog and records

Extracurricular activities have their own family-scoped catalog, using an existing equivalent where available. Conceptually, an activity has an ID, family ID, name, optional description, position, active state, and audit metadata. Names are not enums. Renames preserve historical associations because records reference activity IDs. Prefer deactivation for activities with history; inactive activities remain visible in historical reports.

Each occurrence is an `ExtracurricularActivityRecord` (or existing equivalent), linked to its family, child, activity ID, and date. It supports the same record details as a study record: optional topic, positive duration, description, comments, free-text material, page endpoints with the same validation, and zero or more family-scoped tags. These similar fields are a presentation convention only: study and extracurricular records remain separate domain records and need not share a table.

## 5. Daily history aggregate

Provide the conceptual route:

```http
GET /families/{familyId}/children/{childId}/history/{date}
```

Dates use ISO `YYYY-MM-DD` and `LocalDate`; timestamp-backed sources are interpreted in the family's configured timezone. A day with no records is a successful response with empty collections, never `404` solely because it is empty. The aggregate includes the requested date, child identity/name when available, photo entries, study and extracurricular entries, reading entries, and routine entries. Use only fields that the source domains actually provide. Use empty arrays for sources with no entries.

Study and extracurricular entries may be normalized into a common response shape for presentation, for example:

```json
{
  "id": "record-id",
  "type": "STUDY",
  "source": { "id": "subject-id", "name": "Mathematics" },
  "topic": "Equivalent fractions",
  "durationMinutes": 45,
  "description": "Practiced equivalent fractions.",
  "comments": "Solved the final exercises independently.",
  "material": "Singapore Math 3A",
  "startPage": 42,
  "endPage": 46,
  "tags": [{ "id": "tag-id", "name": "Review" }]
}
```

`source` is a DTO representation, not a new persistence entity. A response may retain separate `studies` and `extracurricularActivities` arrays if that is already the established contract; do not break a sound API solely to unify them. For study entries, the display title comes from `StudySubject.name`; for extracurricular entries, it comes from `ExtracurricularActivity.name`. The topic remains a distinct record-level field.

The aggregator composes through owning feature service/query boundaries. It must not call controllers or bypass another feature's rules through direct repository access. Query only the requested date for daily detail. Do not persist daily history or report snapshots.

### Included sources

- **Reading:** include that date's existing `ReadingSession` data and book details available through the reading domain. Keep books independent. Do not invent fields; an example may include book ID/title, author, pages read, minutes, and notes only where supported.
- **Routine:** include that date's existing routine execution data, such as task name and completion state where available.
- **Photos:** include the `PhotoRecord` for the child and date, preserving date, description, tags, and position. Images are stored through `Media`; daily history returns appropriate references/metadata and must not load media bytes for ordinary history or calendar queries.

The daily report supports zero to four photos. Four is a product rule for this report experience, not merely a layout limit. Enforce the cap when records are created or updated, according to the existing photo-record ownership model, and ensure the report never returns more than four. Photo tags remain free classification and do not affect study metrics.

## 6. Calendar, history, and period reports

Retain the existing calendar and paginated history capabilities and their contracts where implemented. Calendar responses are lightweight: return only dates with activity and per-source flags, without full records or media. History pages are concise, date-descending, deterministic, and use the established page shape and bounds. Daily detail and period queries must respect family timezone semantics.

The established API routes use the `/api/families/{familyId}/children/{childId}` base:

| Method and route | Purpose |
| --- | --- |
| `GET /history/{date}` | Daily detail; an empty day succeeds. |
| `GET /calendar?year={year}&month={month}` | Lightweight activity flags for a month. |
| `GET /history?from={date}&to={date}&page=0&size=20` | Paginated, date-descending history. |
| `GET /reports?from={date}&to={date}` | Live totals for an inclusive period. |
| `GET /history/{date}/pdf` | On-demand daily PDF export. |

All routes require the established Bearer authentication and family/child authorization. Preserve current validation and pagination contracts. The existing integration guide defines current source-specific semantics and configuration; update it in the same change when these contracts change.

Period reports accept inclusive `from` and `to` dates and support monthly, quarterly, semiannual, annual, and arbitrary custom periods. Apply the configured maximum period length and established validation/error conventions. Use date-range queries and suitable database aggregation for annual and other long-period reports; do not load an unbounded set of source rows just to aggregate it in application memory.

Official totals are grouped by stable catalog IDs:

- Studies: `StudySubject.id`, with record count and total duration where durations exist.
- Extracurricular activities: `ExtracurricularActivity.id`, with record count and total duration where durations exist.

Tags must not affect these totals. A tag-filtered view is a secondary, overlapping classification view: one record can appear under multiple tags, so tag totals must not be summed to calculate official totals.

**Required separation example:** a 60-minute Science study record and a 180-minute Museum Visit record tagged “Science” produce 60 study minutes for the Science subject. They must never produce 240 minutes for Science.

## 7. PDF export

Provide on-demand PDF export using the existing export route convention where one exists; otherwise use:

```http
GET /families/{familyId}/children/{childId}/history/{date}/pdf
```

Return `application/pdf` with an appropriate `Content-Disposition` filename. Generate the PDF from the same `DailyHistoryService` result used by the API; the renderer must not repeat source queries or aggregate rules. Do not persist the PDF by default.

Include, when available, the child's name, date, up to four photographs, studies, extracurricular activities, reading, and routine. Study titles come from the subject; extracurricular titles come from the activity. Render topic, duration, description, comments, material, and pages only when they have values. Omit empty labels and values. The layout must support any number of records and multiple pages, avoiding illegible cuts through text, images, or sections.

Load images through the authorized `Media` path, not public URLs. Resize only for rendering and preserve the original media. A failed image may be omitted when a safe fallback exists, without losing the rest of the report. Sanitize the filename, for example `daily-report-elias-2026-09-03.pdf`. A child's name is never an authorization key; access checks use IDs and ownership.

## 8. Authorization, privacy, and data boundaries

Every request verifies the authenticated user's access to the family and that the child belongs to that family. Apply the same ownership checks to PDF downloads and media retrieval. Ensure all included records and references belong to the requested family/child. In particular:

- `StudySubject.familyId == familyId`
- `ExtracurricularActivity.familyId == familyId`
- `Tag.familyId == familyId`
- `PhotoRecord.familyId == familyId`

Follow established safe not-found behavior for missing or cross-family resources. Never rely on UUID secrecy or frontend checks.

## 9. Performance and persistence

Avoid N+1 access across study records/subjects/tags, extracurricular records/activities/tags, reading sessions/books, photo records/media/tags, and routine records/tasks. Use bounded queries and batch/fetch or projection strategies that fit the owning modules. Daily detail reads only the requested date. Period reports use suitable range queries and database aggregation.

Before changing persistence, inspect existing subjects and records, extracurricular catalogs and records, tags, photo records, media, reading, routine, history aggregation, PDF/export infrastructure, and applied Flyway migrations. Reuse what exists; do not recreate it. Apply required schema changes only through new Flyway migrations. Do not edit migrations already applied to shared environments.

If an implementation uses `categoryTagId` as the official subject or activity identity, remove that source of truth and use `StudySubject` or `ExtracurricularActivity` IDs. Tags remain free-form classifications.

## 10. API and OpenAPI documentation

Document every new or changed API in the human-readable integration guide under `docs/` and link it from the README. Update OpenAPI as well. Specify routes and methods, authentication/ownership, headers, request and response schemas/examples, required and nullable fields, validation, status/error codes, ordering and pagination, empty-day behavior, date and period limits, configuration, side effects, retry expectations where relevant, and client flows. Document the distinction between official study subjects, official extracurricular activities, and free-form tags. The integration guide is required; OpenAPI does not replace it.

## 11. Required tests

Cover behavior and isolation across the owning modules. At minimum:

- Daily history: empty day; study only; multiple studies and subjects; extracurricular only; multiple activities; study plus extracurricular; reading; routine; photos; one and four photos; mixed sources; records from another date excluded; other child/family excluded.
- Photo cap: reject or otherwise prevent a fifth report photo according to the chosen persistence rule.
- Study/activity fields: topic, duration, description, comments, material, pages, omitted optional values, multiple tags, foreign-family tag rejection, and invalid duration/page ranges.
- Official grouping: studies group by subject ID and extracurricular records by activity ID; tags do not change either count or duration.
- Explicit tag-contamination case: Science study = 60 minutes; Museum Visit = 180 minutes and tagged Science; official Science study total remains 60 minutes.
- Period reports: date boundaries, totals, grouping, tag overlap semantics, ordering, configured limits, and bounded query behavior.
- Authorization: family, child, records, tags, photos, and PDF are isolated.
- PDF: `application/pdf`, filename sanitization, child/date, empty day, study, extracurricular, both together, reading, photos up to four, multiple pages, omitted empty fields, and data equivalence with daily history.

Use PostgreSQL/Testcontainers for database integration behavior as required by the project. Do not add tests that only mirror implementation details.

## 12. Acceptance criteria

PDR-09 is complete when:

- Families can manage reusable study subjects and extracurricular activities independently of free-form tags.
- Study and extracurricular records reference their official catalog IDs and support the specified optional record details and tags.
- Daily history is assembled dynamically from current source records, returns empty days successfully, includes the supported domains, and enforces the four-photo report cap.
- Display titles come from the official subject/activity catalogs; tags never determine official identity or alter official metrics.
- Period and annual totals group by subject/activity IDs and support inclusive custom periods within configured limits.
- Daily API and on-demand PDF use the same aggregate, with family ownership enforced for all included data and downloads.
- PDF output supports variable content and pagination, loads images through authorized media access, and omits empty fields.
- Queries avoid N+1 behavior and use bounded/range-appropriate aggregation.
- Integration documentation, OpenAPI, migrations where needed, and required tests are updated.
