# Routines and daily planning — PDR-04

This is the implemented UI integration contract for the
[PDR-04 scope](pdr-04-routines-daily-planning-prd.md). Startup and backend origin
follow the [README](../README.md); authentication follows
[Authentication](authentication.md). Planning has no completion state, execution,
snapshots, history, reminders, or automatic creation of future plans.

## Authentication and formats

Every route requires `Authorization: Bearer <accessToken>`. Persisted family
membership is checked on every request. MEMBER, ADMIN, and OWNER can read;
only ADMIN and OWNER can mutate. A person's classification or linked account
never grants access. New/edit routine assignments and all daily-plan operations
require an active same-family person. Inactive people return
`FAMILY_MEMBER_NOT_FOUND`; stored plans remain after deactivation.

Send JSON objects with `Content-Type: application/json` for body-bearing routes.
Success uses `application/json`; errors use `application/problem+json`.
`Accept-Language` supports English, Portuguese, and Spanish, including regional
preferences, with English fallback. User-authored text is not translated.
IDs are UUID strings; audit timestamps are UTC ISO instants.

Dates use strict `YYYY-MM-DD`; times use strict `HH:mm`, without seconds or
offsets. Times are wall times in the family's persisted IANA timezone. The date
is not shifted by timezone. There is no server “today” fallback; past dates are
allowed. Changing the family timezone does not rewrite stored dates/times.
See [family timezone](families.md#family-timezone).

## Routes

All routes return bodies. No DELETE routes exist. State routes have no body.

| Method | Complete route | Success and response |
| --- | --- | --- |
| POST | `/api/families/{familyId}/routines` | 201, routine detail; Location is its detail URI |
| GET | `/api/families/{familyId}/routines` | 200, routine page |
| GET | `/api/families/{familyId}/routines/{routineId}` | 200, routine detail including inactive items |
| PATCH | `/api/families/{familyId}/routines/{routineId}` | 200, routine detail |
| POST | `/api/families/{familyId}/routines/{routineId}/deactivate` | 200, routine detail |
| POST | `/api/families/{familyId}/routines/{routineId}/reactivate` | 200, routine detail |
| POST | `/api/families/{familyId}/routines/{routineId}/items` | 201, routine item; Location is its item mutation URI |
| PATCH | `/api/families/{familyId}/routines/{routineId}/items/{itemId}` | 200, routine item |
| POST | `/api/families/{familyId}/routines/{routineId}/items/{itemId}/deactivate` | 200, routine item |
| POST | `/api/families/{familyId}/routines/{routineId}/items/{itemId}/reactivate` | 200, routine item |
| PUT | `/api/families/{familyId}/routines/{routineId}/items/order` | 200, routine detail |
| GET | `/api/families/{familyId}/members/{memberId}/daily-plan?date=YYYY-MM-DD` | 200, resolved day; required date |
| PUT | `/api/families/{familyId}/members/{memberId}/daily-plan/{date}` | 200, resolved day after setting note |
| POST | `/api/families/{familyId}/members/{memberId}/daily-plan/{date}/items` | 201, daily item; Location is its item mutation URI |
| GET | `/api/families/{familyId}/members/{memberId}/daily-plan/{date}/items` | 200, array of all daily items, including inactive items |
| PATCH | `/api/families/{familyId}/members/{memberId}/daily-plan/{date}/items/{itemId}` | 200, daily item |
| POST | `/api/families/{familyId}/members/{memberId}/daily-plan/{date}/items/{itemId}/deactivate` | 200, daily item |
| POST | `/api/families/{familyId}/members/{memberId}/daily-plan/{date}/items/{itemId}/reactivate` | 200, daily item |
| PUT | `/api/families/{familyId}/members/{memberId}/daily-plan/{date}/items/order` | 200, array of all daily items, including inactive items |

Item Location URIs identify PATCH/state routes; there is no individual item GET.
Bodies reject unknown fields and writes to ownership, active state, and audit
fields. The reorder entry `id` identifies an existing item; other request bodies
cannot set resource IDs.

## Requests and validation

| Request | Fields |
| --- | --- |
| Create routine | Required `name`, `daysOfWeek`; optional nullable `startDate`, `endDate` |
| Patch routine | Nonempty subset of those four fields |
| Create routine item | Required `familyMemberId`, `title`, `sortOrder`; optional nullable `description`, `scheduledTime` |
| Patch routine item | Nonempty subset of those five fields |
| Set note | Required `note` property, string or null |
| Create daily item | Required `title`, `sortOrder`; optional nullable `description`, `scheduledTime` |
| Patch daily item | Nonempty subset of those four fields |
| Replace order | Required `items` array of `{id, sortOrder}` objects |

Name/title: non-null strings, stripped at both ends, nonblank, at most 120 UTF-16
code units. Internal whitespace, case, and accents are preserved. Duplicates are
allowed. Description/note: nullable strings, stripped, blank-to-null, at most
2,000 UTF-16 code units. Numbers are not coerced into strings.

Weekdays are unique uppercase names MONDAY through SUNDAY, with one to seven
entries. Empty arrays are invalid. Date bounds are inclusive; end must not
precede start when both exist. Missing bounds are open-ended. `sortOrder` is an
explicit integer from 0 through 2,147,483,647. Routine orders may tie outside a
reorder request; daily orders are unique within their plan, including inactive
items.

PATCH omission preserves. Explicit null clears only date bounds, description,
and scheduledTime. Required fields cannot be null. Empty patches are invalid.
Routine item edits validate the resulting assignment even when familyMemberId
is omitted. Reactivating a routine item requires an active assignee; deactivation
remains available.

Reorder contains exactly every item, including inactive items, with unique IDs
and unique nonnegative orders. Orders need not be consecutive. Missing/duplicate
entries are 400; IDs outside the parent return safe 404. Daily order swaps are
atomic. Empty arrays are valid only for existing empty parents.

Create routine example:

```json
{"name":"School morning","daysOfWeek":["MONDAY","TUESDAY"],"startDate":"2026-09-21","endDate":null}
```

Create routine item example:

```json
{
  "familyMemberId":"8a71a910-f6b7-4380-9b28-5ba75242e689",
  "title":"Breakfast",
  "description":"Prepare fruit",
  "scheduledTime":"08:30",
  "sortOrder":0
}
```

Create daily item: `{"title":"Library visit","scheduledTime":"15:00","sortOrder":1}`.
PATCH examples: `{"endDate":null}` clears a routine end bound;
`{"title":"Read together","scheduledTime":null}` changes an item title and
clears time while preserving other fields.

Set note: `{"note":"Bring the library card"}`. Clear: `{"note":null}`.
Reading never creates rows. The first nonempty note or item creates the unique
family/member/date plan. An empty note for a date without a plan does not create
one. Clearing the last content may retain an empty row.

Reorder example:

```json
{"items":[
  {"id":"8fdfc836-2824-451f-a2d7-842a2593f447","sortOrder":1},
  {"id":"65b49c07-0e41-4d29-8b7f-ed5f00a4a941","sortOrder":0}
]}
```

## Responses and pagination

Routine detail contains `id`, `familyId` (UUID strings), `name` (string), `active`
(boolean), `daysOfWeek` (weekday array), nullable `startDate`/`endDate`,
`createdAt`/`updatedAt` (UTC instants), and `items` (routine item array).
All fields are present; only date bounds may be null.

Routine items contain `id`, `familyMemberId`, `title`, nullable `description`,
nullable `scheduledTime`, `sortOrder`, `active`, `createdAt`, and `updatedAt`.
Daily items have the same fields except familyMemberId, which comes from the
route. All fields are present; only description/time may be null.
Creation defaults active to true. State assignments are idempotent. Audit times
change only when persisted row state changes; item edits do not change parent
routine/plan audit times. There are no completion, actor, or history properties.

The daily item GET returns these full daily item objects, including `active`,
ordered by sortOrder ASC then id ASC. It includes only date-specific items,
not recurring routine items. Any family membership may read it; the person must
be active and belong to the path's family. It returns `[]` for a date without a
plan or items and never creates rows. The collection is bounded to 500 items,
including inactive ones, without pagination or filters; exceeding that limit
returns 400. The same authentication, date validation, and safe 404 rules apply.

Example management response:

```json
[{"id":"65b49c07-0e41-4d29-8b7f-ed5f00a4a941","title":"Library visit","description":null,"scheduledTime":"15:00","sortOrder":1,"active":false,"createdAt":"2026-09-21T12:00:00Z","updatedAt":"2026-09-21T13:00:00Z"}]
```

Routine listing accepts `includeInactive=false`, `page=0`, `size=20`.
includeInactive accepts lowercase true/false; omitted or empty uses false.
Size is 1–100, page is nonnegative, and page times size must be below
2,147,483,647. Family/active filters run before pagination. Results sort by
createdAt ASC then id ASC and have no totals:

```json
{
  "items":[{
    "id":"1f79b38e-aeca-46bf-8019-9cd1c91575b1",
    "name":"School morning","active":true,
    "startDate":"2026-09-21","endDate":null,
    "createdAt":"2026-09-21T12:00:00Z","updatedAt":"2026-09-21T12:00:00Z"
  }],
  "page":0,"size":20,"hasNext":false
}
```

List rows omit items and weekdays; load detail to edit configuration. Empty or
out-of-range pages return an empty array and hasNext false. Pages are not a
snapshot across concurrent writes.

Resolved day example:

```json
{
  "date":"2026-09-21",
  "timezone":"America/Asuncion",
  "member":{"id":"8a71a910-f6b7-4380-9b28-5ba75242e689","name":"Daniel","memberType":"CHILD"},
  "note":"Bring the library card",
  "items":[{
    "source":"ROUTINE",
    "sourceId":"8fdfc836-2824-451f-a2d7-842a2593f447",
    "title":"Breakfast","description":"Prepare fruit",
    "scheduledTime":"08:30","sortOrder":0
  }]
}
```

All fields are present; note, description, and scheduledTime are nullable. The
safe member summary includes only ID, name, and ADULT/CHILD classification.
Source is ROUTINE or DAILY_PLAN; sourceId is the originating item ID. Use
(source, sourceId) as the UI key. Only active routines matching weekday and
inclusive bounds contribute active items assigned to this member. Inactive
daily items are omitted. Notes may exist without items.

Resolved order is sortOrder ASC, ROUTINE before DAILY_PLAN, then UUID string ASC.
Time is not a sort key. Configuration edits can change resolution for past dates;
this is not historical execution data.

## Errors

Use the shared [ProblemDetail contract](api.md#errors); codes are untranslated,
details localized. Framework parsing failures may lack an application code.

| Status | Code or condition |
| --- | --- |
| 400 | VALIDATION_ERROR: content, dates, order, pagination, or collection limits |
| 400 | Malformed JSON, wrong types, unknown fields, missing body/date, invalid UUID |
| 401 | UNAUTHENTICATED: missing/invalid token or unavailable user |
| 403 | FORBIDDEN: family membership cannot manage planning |
| 404 | FAMILY_NOT_FOUND: absent family or membership |
| 404 | FAMILY_MEMBER_NOT_FOUND: missing, other-family, or inactive person |
| 404 | ROUTINE_NOT_FOUND / ROUTINE_ITEM_NOT_FOUND: missing or mismatched nested resource |
| 404 | DAILY_PLAN_NOT_FOUND: item mutation/reorder on a date without a plan |
| 404 | DAILY_PLAN_ITEM_NOT_FOUND: item outside the path's plan |
| 405 / 415 | Unsupported method / non-JSON request media type |
| 500 | INTERNAL_SERVER_ERROR: reconcile uncertain writes before retry |

Missing and inaccessible nested IDs return the same safe 404. Valid MEMBER
mutations return 403 after family access is established. Malformed-body
validation can precede authorization without exposing data. Do not assume every
validation error has a field errors array.

## UI flow, retries, and limits

1. Authenticate and select an accessible family. Offer a timezone selector;
   migrated families use UTC until OWNER/ADMIN chooses another zone.
2. Load active people through [family members](family-members.md).
3. Page routines, load detail, then manage configuration/items. Keep inactive
   items in the reorder model.
4. Send an explicit selected calendar date to render the resolved plan and note.
5. Load `GET /api/families/{familyId}/members/{memberId}/daily-plan/{date}/items`
   for daily management, including after reopening the app or switching devices.
   Use inactive item IDs for restore controls and include every item in reorder
   requests. Reload this list and the resolved day after mutations. Concurrent
   additions can invalidate a reorder; reload the full list before retrying.
6. Clear cached private data on account/family changes or logout.

GET and idempotent state assignments are retryable. POST has no idempotency key;
reload after ambiguous timeouts before offering another create. PATCH/PUT use
last committed write wins; reload before retrying uncertain edits. No ETags.
Concurrent first writes converge on one daily plan. Writes serialize with member
deactivation; daily order uniqueness includes inactive items.

To bound detail/reorder payloads, each routine/plan permits 500 items including
inactive items. Resolution permits 1,000 combined active items. Exceeding a limit
returns 400 instead of truncating. These fixed implementation limits add no
configuration variables. There is no planning rate limiter, cache, or background
job. Resolution query count is constant as routine count grows.

## Migration and verification

Flyway V7 backfills existing family timezones to UTC and creates planning tables,
same-family foreign keys, weekday checks, deferred nonempty-weekday validation,
unique daily plans, and deferred daily order uniqueness for atomic swaps.
Deploy with this application version; family creation clients must add timezone.
Back up before rollout. Recover through a forward migration or coordinated
application/database restore; never edit applied SQL.

Run `./mvnw verify` with Docker. PostgreSQL Testcontainers covers populated V6
upgrade, HTTP validation, authorization, family boundaries, reordering,
concurrent first writes, resolution, query counts, constraints, and OpenAPI.
