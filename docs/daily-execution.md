# Daily execution and history — UI integration

PDR-05 records the resolved PDR-04 plan as independent snapshots. All paths below
start with `/api/families/{familyId}/members/{memberId}/executions`.
Family/member/item IDs are UUIDs. Dates must be valid `YYYY-MM-DD` strings.
The family timezone determines today; never substitute the device timezone.
Existing [authentication](authentication.md), [family](families.md),
[member](family-members.md), and [planning](planning.md) configuration applies.
There are no new environment variables or external services.

## Authentication and routes

Send `Authorization: Bearer <accessToken>` and optionally `Accept-Language`
(`en`, `pt`, `es`, including regional variants). Responses are JSON; errors use
`application/problem+json`. These operations have no request body. Clients cannot
submit titles, notes, source IDs, completion actors, or timestamps to this API.

Every request checks current family membership and the nested member relationship.
Any MEMBER, ADMIN, or OWNER can read, materialize, complete, and undo completion.
Only ADMIN/OWNER can finalize or reopen. The target person may be a child without
a linked user account. The completion actor is the authenticated user.

| Method | Suffix | Success and behavior |
| --- | --- | --- |
| PUT | `/{date}` | 200 execution: materialize/synchronize today; return existing past history and persist its closure if needed |
| GET | `/{date}` | 200 execution: read existing snapshots without mutation; 404 if absent |
| POST | `/{date}/items/{itemId}/complete` | 200 execution: complete an item |
| POST | `/{date}/items/{itemId}/uncomplete` | 200 execution: return a completed item to pending |
| POST | `/{date}/finalize` | 200 execution: freeze the day, including pending items |
| POST | `/{date}/reopen` | 200 execution: enable corrections without synchronizing planning |
| GET | `?from=2026-09-01&to=2026-09-30&page=0&size=20` | 200 history page: inclusive dates, existing executions only |

`from` and `to` are both required, and `from <= to`. Page numbering starts at 0;
`size` defaults to 20 and must be 1–100. The offset must not exceed 2147483646.
History order is `date DESC, id DESC`. There is no customizable sort or total-page
count. Missing days are never materialized by history or GET. Future date ranges
are allowed for history reads; future state changes return 400.

## Execution response

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "date": "2026-09-21",
  "status": "OPEN",
  "reopened": false,
  "finalizedAt": null,
  "member": {
    "id": "22222222-2222-4222-8222-222222222222",
    "name": "Alex",
    "memberType": "CHILD",
    "color": "#123456"
  },
  "note": "Remember water",
  "items": [{
    "id": "33333333-3333-4333-8333-333333333333",
    "sourceType": "ROUTINE",
    "sourceId": "44444444-4444-4444-8444-444444444444",
    "title": "Breakfast",
    "description": null,
    "scheduledTime": "08:30",
    "sortOrder": 0,
    "status": "COMPLETED",
    "completedAt": "2026-09-21T11:35:00Z",
    "completedByUserId": "55555555-5555-4555-8555-555555555555"
  }],
  "summary": {
    "total": 1,
    "completed": 1,
    "pending": 0,
    "cancelled": 0,
    "completionPercentage": 100.0
  }
}
```

Every field shown is always present. `finalizedAt`, `note`, member `color`, item
`sourceId`, `description`, `scheduledTime`, `completedAt`, `completedByUserId`, and
`completionPercentage` may be null. All other fields are non-null.

Execution status is `OPEN` or `FINALIZED`. `reopened` indicates explicit correction
mode, and resets on finalization. `finalizedAt` is an ISO instant recording the
persisted finalization transition. A past execution reported as `FINALIZED` by a
read may still have null `finalizedAt` until the next state-changing access.
Reopening clears `finalizedAt`.

The member summary uses current name, type (`CHILD` or `ADULT`), and color; these
are not historical snapshots. Item color is not stored or returned. The note is
a nullable snapshot of at most 2000 characters. Titles are nonblank, at most 120
characters; descriptions are nullable, at most 2000 characters. Scheduled time is
nullable `HH:mm`, interpreted in the family's timezone, not an event timestamp.
Sort order is a nonnegative integer. Items are ordered by sort order, source type
(`ROUTINE` before `DAILY_PLAN`), source ID, and execution item ID. Equal sort orders
are valid. IDs remain stable through synchronization and cancellation/reactivation.

`sourceType` is `ROUTINE` or `DAILY_PLAN`; `sourceId` identifies the planning item,
not its parent routine/plan. Materialized sources always have IDs; the storage and
response allow null for historical compatibility. Sources are not foreign keys,
so source changes/deletion cannot destroy history. There is no manual-item API.

Item status is `PENDING`, `COMPLETED`, or `CANCELLED`. Completion sets the event
instant and authenticated actor; undo clears both. A completed record can have a
null actor in storage, though normal authenticated completion always sets it.
Repeated completion or undo in the same state leaves timestamps unchanged.
Cancelled items reject both operations with 409; restore the planning source and
synchronize the open day to reactivate them.

Summary counts are computed from snapshots. `total` includes cancelled items;
percentage is `100 * completed / (completed + pending)`, with no fixed rounding
contract. It is null if that denominator is zero. Empty executions are valid.

## Synchronization and corrections

1. Resolve planning using PDR-04 when displaying the plan before execution.
2. PUT today's execution when execution starts or when explicitly refreshing its
   planning snapshots. Repeating PUT preserves the execution identity.
3. Use the returned execution item IDs for complete/uncomplete. GET only reads;
   completion operations do not implicitly synchronize planning.
4. ADMIN/OWNER may finalize with pending items. All snapshots and item states then
   freeze. PUT and GET return frozen data.
5. ADMIN/OWNER may reopen a finalized execution, including a day implicitly closed
   by its date. Repeated reopen in correction mode is a no-op. Reopening an ordinary
   open day returns 409. Corrections use complete/uncomplete, followed by finalize.

An ordinary open day synchronizes new sources, updates pending snapshots and note,
cancels removed pending sources, and reactivates returned cancelled sources.
Completed snapshots are never rewritten or cancelled by synchronization. Undoing
completion makes an item pending and eligible for the next ordinary synchronization.

Reopened executions never synchronize, including today's reopened execution.
This preserves the evidence being corrected. A past execution is effectively
closed unless explicitly reopened. The first valid state-changing access persists
closure; an attempted completion returns 409 and still commits that closure.
GET/history never write. There is no scheduler. Changing the family timezone changes
the date boundary used for subsequent requests; persisted finalization stays closed.

Do not create executions for inactive members. Their existing executions remain
readable, including through PUT, which does not synchronize inactive members.
Existing items remain correctable under the same date/state rules. Missing past
executions return 404 and cannot be backfilled through this API.

## History response

```json
{
  "items": [{
    "id": "11111111-1111-4111-8111-111111111111",
    "date": "2026-09-21",
    "status": "FINALIZED",
    "reopened": false,
    "finalizedAt": "2026-09-21T23:00:00Z",
    "summary": {"total": 1, "completed": 1, "pending": 0, "cancelled": 0, "completionPercentage": 100.0}
  }],
  "page": 0,
  "size": 20,
  "hasNext": false
}
```

All page/day fields are present; only `finalizedAt` and summary percentage can be
null. Fetch the single-day endpoint for snapshots and member details. An empty
page has `items: []` and `hasNext: false`. Pagination reflects current committed
data; concurrent creation can shift offset pages.

## Errors, concurrency, and limits

Errors follow [ProblemDetail and localization](api.md#errors). Branch on `code`,
not the translated detail. Example:

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Daily execution is finalized.","instance":"/api/families/.../executions/2026-09-21/items/.../complete","code":"DAILY_EXECUTION_FINALIZED"}
```

| Status | Code | Client action |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Fix date, range, pagination, or excessive snapshots |
| 400 | `DAILY_EXECUTION_FUTURE_DATE` | Use the family's current date |
| 401 | `UNAUTHENTICATED` | Follow the authentication refresh/login flow |
| 403 | `FORBIDDEN` | Finalize/reopen requires ADMIN/OWNER |
| 404 | `FAMILY_NOT_FOUND` | Family is missing or inaccessible |
| 404 | `FAMILY_MEMBER_NOT_FOUND` | Member is absent, outside the family, or inactive when creating |
| 404 | `DAILY_EXECUTION_NOT_FOUND` | No execution for this member/date |
| 404 | `DAILY_EXECUTION_ITEM_NOT_FOUND` | Item does not belong to the addressed execution |
| 409 | `DAILY_EXECUTION_FINALIZED` | Reload; an authorized editor must reopen before corrections |
| 409 | `DAILY_EXECUTION_CONFLICT` | Reload and resolve the conflicting state before retrying |

Malformed UUIDs, missing required parameters, and parameter type errors use Spring's
framework 400 ProblemDetail and do not promise a stable application code.
No request DTO validation fields are involved in these bodyless operations.

PostgreSQL enforces one execution per family/member/date and unique non-null source
identity within it. Transactions and execution row locks serialize synchronization,
completion, finalization, and reopening. This deliberately uses pessimistic locking
instead of a redundant optimistic version: a transition reads current committed
state after acquiring the same execution lock. New materialization also locks the
member to coordinate deactivation. Database lock failures/timeouts return the safe `DAILY_EXECUTION_CONFLICT` response.
Concurrent materialization converges on one row;
concurrent repeated completion preserves its original actor and timestamp.

A successful identical retry is idempotent. If the date, planning, or state changed
between attempts, re-evaluate the returned state. PUT may synchronize newer planning
on retry. No offline merge or complete audit log is provided. There is no new feature
rate limit. Existing authentication limits still apply.

The resolved plan is bounded to 1000 items by PDR-04. Execution accumulation is
bounded to 2000 snapshots, including cancelled/completed ones; reaching that cap
rejects a synchronization that needs additional items and rolls back the whole
operation. History uses bounded pages and one grouped summary query, avoiding item
loading per day. No reports, notifications, rewards, or background jobs are included.

## Migration

V9 creates execution tables and constraints after V8's schema rename. No existing
planning/member data is rewritten or executions backfilled. Deploy with the usual
Flyway migration and Hibernate schema validation. If the application release is
rolled back, retain the additive tables to preserve recorded history; do not drop
or cascade-delete them as a rollback procedure.
