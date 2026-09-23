# BE-04 — Routines and Daily Planning

Status: proposed implementation scope. This document turns the product request
into implementation decisions consistent with the current backend. It is not an
implemented API contract; implementation must add a README-linked integration
guide and matching OpenAPI documentation.

## Outcome and boundaries

BE-04 lets OWNER and ADMIN configure recurring routines and date-specific plans
for any FamilyMember; every family member can read the resolved plan for a date.
A resolved plan combines current active routine configuration with optional daily
plan data. It does not record completion. BE-05 owns execution, snapshots, and
history.

Add routine and dailyplan feature packages with responsibility subpackages only
where classes are needed. Keep business rules and transactions in services, JPA
entities out of API contracts, and DTOs explicit. Reuse
FamilyAuthorizationService for persisted membership and OWNER/ADMIN checks.
Feature services must not query FamilyMembershipRepository directly. Do not add
generic recurrence, execution/history tables, jobs, caching, or service chains.

## Decisions for implementation

| Topic | Decision |
| --- | --- |
| Routine days | Every routine has one or more weekdays. Empty days are invalid; there is no implicit “every day.” |
| Text | Name/title: strip edges, reject blank, max 120 characters; preserve case, accents, and internal whitespace. Description/note: optional, strip edges, blank becomes null, max 2,000 characters. Duplicate names/titles are allowed. |
| Active state | Routine and items default active. Deactivation/reactivation are idempotent and return 200. No hard-delete routes. |
| Inactive members | New/edit assignments and daily-plan operations require an active member. Daily-plan read for inactive member returns 404. Existing plans are retained. |
| Ordering | sortOrder is a non-negative 32-bit integer, explicitly set on create/edit. Routine reorder replaces the complete set, including inactive items; IDs and sort orders must be unique. |
| Resolved ordering | sortOrder ASC, then source ASC (ROUTINE before DAILY_PLAN), then sourceId ASC. Do not sort by time. sourceId is unique only with source. |
| Daily plan creation | Create the unique member/date row on the first note or item mutation. Reading never creates rows. Empty note with no items is not persisted; clearing the last content may leave an empty unique row. |
| Daily item order | sortOrder is unique among all items in one plan. Reorder replaces every item in that plan, including inactive ones, with unique non-negative values. |
| Concurrent edits | Last committed write wins for edits. Do not add ETags/versions. Daily-plan uniqueness is database-enforced; creation races converge on the winning row. |
| Family timezone | Persist IANA ZoneId on Family. Existing families are backfilled to UTC, never inferred from account/device/IP. New family creation requires timezone; family PATCH may update it. |
| Date/time | Use LocalDate and LocalTime. Times are family-local wall times, not UTC. Supplied date is not shifted by timezone. No “today” default. |
| Audit | createdAt/updatedAt remain UTC Instants from injected Clock. Update only when persisted state changes, following BE-03. |

UTC is a deterministic neutral migration value, not a location claim. The UI
should ask the user to select the intended zone. Changing a family timezone
affects future local-day interpretation only; stored dates and times are not
rewritten.

## Domain and persistence

    Family (timezone)
    ├── FamilyMember
    ├── Routine (name, active, optional date range)
    │   ├── RoutineDay (MONDAY ... SUNDAY)
    │   └── RoutineItem (member, title, optional description/time, order, active)
    └── DailyPlan (member, LocalDate, optional note; unique per member/date)
        └── DailyPlanItem (title, optional description/time, order, active)

Use UUID identifiers. Prefer IDs over bidirectional JPA graphs. Persist weekdays
in routine_days with UNIQUE(routine_id, day_of_week) and a check constraint for
the seven Java weekday names. Weekday set is required and nonempty.

A routine belongs to one family. Its item references a routine and a FamilyMember
from the same family. DailyPlan references family and member, and its items
reference that plan. Enforce UNIQUE(family_id, family_member_id, plan_date).
Use composite foreign keys where practical to enforce same-family relationships;
also validate with family-scoped service/repository queries.

startDate and endDate are nullable DATE values. If both exist, endDate must be
greater than or equal to startDate. A routine contributes only when active, within
the inclusive date range, and on a selected weekday. Only active items assigned
to the requested member contribute.

Create DailyPlan on demand. Dates are calendar dates, not instants. Planning edits
and deactivation do not mutate or imply past execution. BE-05 must snapshot the
resolved content it needs when execution begins. BE-04 creates no execution
records.

## Family timezone change

Add a versioned Flyway migration after V6: add required timezone, backfill
existing families to UTC, then add a nonblank constraint. Validate input with
ZoneId.of; offset-only values are invalid. Update Family create/response/PATCH
DTOs and the family integration guide/OpenAPI in the same implementation.
Creation requires a valid IANA identifier. PATCH timezone is optional; omission
preserves, null is invalid, and an empty PATCH is invalid. OWNER/ADMIN only, like
family rename. The server does not infer a timezone.

## Authorization and disclosure

Every endpoint requires existing Bearer authentication. Resolve the trusted user
from SecurityContext and verify current FamilyMembership on every request. MEMBER
may read; OWNER and ADMIN may manage. linkedUserId and memberType grant no access.

Validate full nested relationships with scoped queries: family → routine → item,
or family → member → plan/date → item. Cross-family or mismatched nested IDs
return the same safe 404 as a missing resource. Missing/inaccessible family
reuses FAMILY_NOT_FOUND. A family member lacking the required role gets 403
after family access is established. Do not disclose other-family data in errors
or logs.

| Operation | MEMBER | ADMIN | OWNER |
| --- | --- | --- | --- |
| List/read routines and details | Yes | Yes | Yes |
| Read resolved daily plan | Yes | Yes | Yes |
| Manage routines and routine items | No | Yes | Yes |
| Manage notes and daily-plan items | No | Yes | Yes |

Reuse FamilyAuthorizationService; do not create a parallel authorization system.

## HTTP contract proposal

Routes are under /api, use JSON bodies where applicable, UUID path IDs, and
existing Accept-Language and ProblemDetail behavior.

| Method and route | Access | Behavior |
| --- | --- | --- |
| POST /families/{familyId}/routines | OWNER/ADMIN | Create routine with nonempty daysOfWeek; 201 and Location. |
| GET /families/{familyId}/routines?includeInactive=false&page=0&size=20 | Any member | Bounded page; active only by default. |
| GET /families/{familyId}/routines/{routineId} | Any member | Detail including items and inactive state. |
| PATCH /families/{familyId}/routines/{routineId} | OWNER/ADMIN | Partial edit name, date bounds, weekday set. Omitted preserves; null clears nullable date bounds; required values cannot be null. Empty object invalid. |
| POST .../{routineId}/deactivate or .../reactivate | OWNER/ADMIN | Idempotent state assignment; 200. |
| POST .../{routineId}/items | OWNER/ADMIN | Create item assigned to active same-family member; 201 and Location. |
| PATCH .../{routineId}/items/{itemId} | OWNER/ADMIN | Partial edit; null clears description/time; required title/member/order cannot be null. |
| POST .../{routineId}/items/{itemId}/deactivate or .../reactivate | OWNER/ADMIN | Idempotent state assignment; 200. |
| PUT .../{routineId}/items/order | OWNER/ADMIN | Replace order for every item, including inactive items. IDs and sortOrder values are unique; all IDs belong to this routine. |
| GET /families/{familyId}/members/{memberId}/daily-plan?date=YYYY-MM-DD | Any member | Resolve routines plus daily plan; never creates a row. Required ISO date. |
| PUT .../members/{memberId}/daily-plan/{date} | OWNER/ADMIN | Set note; {"note":null} clears it. Creates plan on demand. |
| POST .../members/{memberId}/daily-plan/{date}/items | OWNER/ADMIN | Create item; 201 and Location. |
| PATCH .../members/{memberId}/daily-plan/{date}/items/{itemId} | OWNER/ADMIN | Partial edit; null clears description/time. |
| PUT .../members/{memberId}/daily-plan/{date}/items/order | OWNER/ADMIN | Replace order for every plan item, including inactive items; IDs and sortOrder values are unique and all IDs belong to this plan. |
| POST .../members/{memberId}/daily-plan/{date}/items/{itemId}/deactivate or .../reactivate | OWNER/ADMIN | Cancel/restore idempotently; item must match path member/date. |

Ellipses inherit the immediately preceding family/routine prefix; the integration
guide must show every complete route. Missing/malformed date is 400. There is no
default-to-today behavior.

### Request and response rules

- Routine create requires name and nonempty daysOfWeek. active is server-defaulted
  true. Date bounds are nullable. Unknown fields are rejected per existing policy.
- Routine item create requires familyMemberId, title, and sortOrder.
  description and scheduledTime are nullable. Time uses strict HH:mm. IDs,
  ownership, active, and audit fields are server controlled.
- Daily item create requires title and sortOrder; description/time are nullable.
  Family/member/date come from path, never body.
- PATCH distinguishes omitted from explicit null using the presence-aware pattern
  established in BE-03. Reject null for required fields and empty patches.
- Responses are explicit DTOs. Routine detail includes items; list rows do not.
  Daily response includes date, timezone, safe member summary, nullable note, and
  items.
- Resolved items include source (ROUTINE or DAILY_PLAN), sourceId, title,
  nullable description/time, and sortOrder. They never include completion state.
  Routine sourceId is routine-item ID; daily sourceId is daily-plan-item ID.
- Resolved output includes only active applicable routines/items and active
  daily-plan items. A note can be returned without active items. Inactive rows
  are omitted from normal resolved output.
- Lists use zero-based page (default 0), size (default 20, maximum 100),
  hasNext without total count, deterministic createdAt ASC/id ASC ordering.
  includeInactive defaults false. Detail may include inactive items for
  management; resolved day never does.

### Errors and retries

Reuse UNAUTHENTICATED, FORBIDDEN, VALIDATION_ERROR, FAMILY_NOT_FOUND,
FAMILY_MEMBER_NOT_FOUND, and shared ProblemDetail. Add only necessary stable
resource codes: ROUTINE_NOT_FOUND, ROUTINE_ITEM_NOT_FOUND, DAILY_PLAN_NOT_FOUND,
DAILY_PLAN_ITEM_NOT_FOUND. Cross-family IDs must not reveal validity. Do not
create a code per field. Add English, Portuguese, and Spanish localization keys;
codes remain stable and untranslated.

GET and idempotent state changes can be retried. POST has no idempotency key; on
ambiguous timeout reload before offering another create. PATCH/PUT are last-write
wins; reload before retrying uncertain edits. Daily-plan creation races must
converge on one member/date row.

## Query and performance requirements

Add versioned migrations after V6; do not edit old migrations. Tables are
routines, routine_days, routine_items, daily_plans, and daily_plan_items. Define
UUID keys, audit/nullability rules, foreign keys, checks, uniqueness, and indexes.
Keep ddl-auto=validate and open-in-view=false.

Index family routine listing and routine-item lookup by routine/member. Enforce
daily-plan uniqueness and index items by daily_plan_id. Avoid redundant indexes.
Daily resolution uses a fixed, bounded set of family-scoped queries, not queries
per routine/member/item. Fetch applicable routine data/items and daily plan/items
in focused queries, map within a read-only service transaction, and avoid
paginated collection fetch joins. No cache.

## Tests and acceptance

Use Red → Green → Refactor. Add PostgreSQL Testcontainers coverage for migration,
constraints, authorization, family scoping, and HTTP behavior. Inject Clock for
technical timestamps. Test ZoneId validation and UTC backfill. No business
“today” rule exists in this increment.

1. Existing families migrate with UTC; new family creation requires valid IANA
   timezone; authorized family PATCH updates it; invalid zones fail. Family API
   documentation and OpenAPI match.
2. OWNER/ADMIN manage routines; MEMBER reads only. Duplicate names are allowed.
   Empty weekdays and reversed date ranges fail.
3. Routine items support ADULT and CHILD, optional description/time, explicit
   order, edits, reorder, and active state. Cross-family/mismatched IDs are safe
   404.
4. Daily note is unique per family/member/date, created on demand, editable and
   clearable. Members and dates have independent plans.
5. Daily items can be created, edited, cancelled, reactivated, with optional
   local time, and reordered. Wrong member/date/family item paths are safe 404.
6. Resolver combines applicable active routine and daily items, includes note,
   respects weekdays and inclusive range, omits inactive rows, and uses the
   documented deterministic order.
7. Resolved data has no completion state. Planning edits create no history.
8. MEMBER reads but mutations return 403; OWNER/ADMIN manage; anonymous requests
   return 401; cross-family calls disclose no data.
9. Collections are bounded; resolver query count does not grow with routines;
   constraints/indexes are present; OpenAPI and README-linked integration guide
   match implementation; ./mvnw verify passes with Docker.

## Out of scope

Completion, execution actor/timestamps, snapshots/history tables, streaks,
rewards, reports, notifications/reminders, child login/session, RRULE, holidays,
calendar UI, uploads, AI plans, and pre-created future daily plans are excluded.
BE-05 must define snapshot and execution semantics; BE-04 provides stable source
IDs and planning data only.
