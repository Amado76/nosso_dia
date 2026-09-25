# PDR-09 — Calendar, History, and Reports

Status: proposed.

## Outcome and boundaries

PDR-09 provides a read-only, time-based view of a child's existing family
records: daily routine execution, studies, reading, and photo records. It
supports daily detail, a lightweight calendar, paginated history, and
period-based summaries.

This feature aggregates source data at query time. It must not copy records or
persist report snapshots. Changes to source records therefore appear in later
history and report responses. Do not add narrative generation, AI, PDF or other
file exports, or new source-domain behavior in this release.

Keep the feature in `history/` with responsibility-based packages. Compose
through owning feature service/query boundaries; controllers must not call
other controllers, and this feature must not bypass source-domain rules through
direct repository access. Reuse existing DTOs where their contracts fit, and
map only the fields needed by history and reports. Do not load media bytes.

## Date, access, and query rules

- Use `LocalDate` for history dates and inclusive period boundaries. When a
  source stores timestamps, interpret them in the family's configured timezone;
  use source `LocalDate` values directly where available.
- Every request must verify authenticated family access and that the requested
  child belongs to that family. A known UUID does not grant access. Follow the
  established safe not-found behavior for missing or cross-family resources.
- Query each source by date or date range and aggregate in memory where
  appropriate. Do not issue per-day source queries for a period. Calendar
  queries must return only activity flags, not full records or media.
- Accept arbitrary inclusive periods, subject to configurable
  `app.reports.max-period-days` (default: 366). Reject `from > to` and ranges
  exceeding the configured limit using the existing validation and
  ProblemDetail/localization conventions (`en`, `pt`, `es`).
- Keep reports live: calculate them from current source records on every read.
  Do not create report tables or snapshots. Add indexes only when supported by
  the implemented query patterns; use Flyway for any schema changes.

## API proposal

All endpoints require the existing Bearer authentication and use `/api`.
Concrete schemas, examples, status codes, validation, and errors must be
specified in the API integration document and OpenAPI when implemented.

| Method and route | Behavior |
| --- | --- |
| `GET /families/{familyId}/children/{childId}/history/{date}` | Return the day's detail. An empty day is a successful response, not `404`. |
| `GET /families/{familyId}/children/{childId}/calendar?year={year}&month={month}` | Return only dates with activity and per-source flags. Validate year and month. |
| `GET /families/{familyId}/children/{childId}/history?from={date}&to={date}&page=0&size=20` | Return a concise, date-descending history page for an inclusive period. |
| `GET /families/{familyId}/children/{childId}/reports?from={date}&to={date}` | Return structured totals for an inclusive period. |

The detail response contains `date`, `childId`, routine summary and items (or
`null` when no routine record exists), studies, reading, and photo references.
Use empty arrays for sources with no entries. Photo entries contain media IDs
and ordering metadata only.

Calendar responses contain `year`, `month`, and dates with boolean flags
`hasRoutine`, `hasStudies`, `hasReading`, and `hasPhotos`. Omit completely
empty dates. Do not load descriptions, full study or reading details, or media
content for this endpoint.

Period history is a compact summary per active date, ordered by date descending
with a deterministic tie-breaker. It uses the repository page shape
(`items`, `page`, `size`, `hasNext`), defaults to page 0 and size 20, and caps
size at 100. Empty dates may be omitted. The implementation document must
define date-bound defaults and the summary fields from data actually available.

Reports include `childId`, the requested period, and source sections for
routine, studies, reading, and photos. Include only metrics supported by source
data. Candidate metrics are:

- Routine: days with a routine, planned items, completed items.
- Studies: session count, total minutes, and minutes grouped by subject.
- Reading: session count, distinct books, and pages read.
- Photos: photo-record count and image count.

Do not infer unavailable values or count voided/inactive records contrary to
their owning feature's rules. Define how unclassified or subjectless records
contribute based on source-domain behavior.

## Errors and documentation

Use established validation, authorization, safe not-found, ProblemDetail, and
localization behavior. Add stable codes only where existing codes do not cover
invalid ranges, excessive periods, or missing child/family resources. The
integration guide must document all four routes, authentication and ownership,
ISO date parameters, response schemas and examples, pagination, empty-day
behavior, period limits/configuration, errors, ordering, and client flow.
OpenAPI complements but does not replace that guide.

## Verification and acceptance

Use unit and PostgreSQL/Testcontainers integration tests for aggregation and
source query behavior. Cover empty days and months, each source independently
and together, calendar flags, timezone date boundaries, period ordering and
pagination, report totals and groupings, invalid and excessive ranges, family
and child isolation, and bounded query counts (no per-day N+1 behavior).
Run `./mvnw verify` when implementation is complete.

PDR-09 is complete when authorized clients can retrieve daily detail, a light
calendar, paginated period history, and live structured reports; all results
respect family timezone and ownership; source facts are not duplicated; and
queries, documentation, OpenAPI, errors, and tests meet the rules above.
