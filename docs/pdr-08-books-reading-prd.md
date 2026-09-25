# PDR-08 — Books and Reading

Status: implemented as a source module. PDR-09 consumption is a tracked follow-up.

BeeHome families can register a book with only its title, optionally attach an
existing family image, and reuse the book across their children. Book metadata,
child reading journeys, and actual reading sessions remain distinct entities.

The implementation uses responsibility-based packages in `reading/`, UUID IDs,
LocalDate domain dates, Instant audit timestamps, and Flyway V12. It reuses family
membership, child identity, media storage, global ProblemDetail, and en/pt/es
localization. No external libraries or new upload mechanism are introduced.

Journeys support PLANNED, READING, COMPLETED, and ABANDONED. Active uniqueness is
protected in PostgreSQL, with completed/abandoned journeys retained for rereads.
Completion requires an explicit historical date. Used books cannot be deleted.
Sessions allow date-only activity, minutes, page counts, paired page positions,
and notes. Positions calculate pagesRead as endPage minus startPage. Progress is
based on the latest recorded end position, never the sum of recorded pages.

The API provides bounded catalog/journey/session lists, period filters, session
replacement/deletion, derived progress, and live metrics. Metrics distinguish
unique books with reading activity from completed journeys. An authorized
DISTINCT-date query supplies future calendar hasReading flags without fetching
session payloads. Family isolation and editor permissions apply in the backend.

See [Books and reading API](books-reading.md) for the complete executable contract,
limits, examples, error codes, pagination, concurrency, and retry semantics.

## PRD-09 remaining work

The existing history/calendar/report API still returns reading placeholders.
Complete the [explicit PRD-09 follow-up](pdr-09-calendar-history-reports-prd.md#prd-08-follow-up-connect-the-reading-source)
to consume the reading data created here. Do not assume this wiring is already
implemented merely because PDR-08 sessions and summaries are available.
