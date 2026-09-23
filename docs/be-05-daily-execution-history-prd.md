# BE-05 — Daily Execution and History

Status: implemented. This document records the BE-05 execution requirements.
See the [daily execution integration guide](daily-execution.md) for the implemented
API contract, limits, and concurrency decisions; OpenAPI complements that guide.

## Outcome and boundary

BE-04 answers what is planned for a member and date. BE-05 records what happened.
It materializes the existing `ResolvedDailyPlan` into an independent daily
execution with snapshots, completion state, and history. Historical reads never
rebuild data from current routines or plans.

Keep the feature in `dailyexecution/` with responsibility subpackages as needed.
Reuse `FamilyAuthorizationService` for membership and role checks. Consume the
BE-04 resolved-plan boundary; do not query routine or daily-plan repositories
directly or introduce a chain of feature services. Use UUIDs, Flyway, explicit
DTOs, service transactions, injected `Clock`, and PostgreSQL constraints.

## Domain rules

| Concern | Decision |
| --- | --- |
| Execution identity | At most one execution per family, member, and `LocalDate`; enforce with a unique constraint. Store family and member IDs and validate their relationship. |
| Materialization | Create on demand from BE-04's resolved plan, including note and ordered items. Use `sourceType` (`ROUTINE`, `DAILY_PLAN`) plus `sourceId` for identity; enforce uniqueness per execution when source ID is present. Concurrent requests converge on one execution. |
| Snapshot | Copy title, nullable description, nullable `LocalTime`, and sort order. History uses snapshots only. Do not accept snapshot fields from clients. |
| Item state | `PENDING`, `COMPLETED`, or `CANCELLED`. Completion records `Instant completedAt` and nullable authenticated `completedByUserId`; uncompletion clears both. Repeating either operation in the same state changes no timestamps. |
| Open synchronization | Synchronize an `OPEN` execution against the current resolved plan: add new sources, update snapshots for pending sources, cancel removed pending sources, and reactivate a returned cancelled source. Never rewrite or cancel a completed item. |
| Finalization | `FINALIZED` freezes snapshots and item state. Pending items are allowed. Finalization records `finalizedAt`; finalized executions do not synchronize and reject completion changes. |
| Reopening | OWNER/ADMIN can reopen for correction. Reopening preserves all snapshots and does not synchronize with today's plan. Corrections use the normal item operations; finalization may be repeated. |
| Dates and timezone | Use `LocalDate` for execution date, `LocalTime` for planned time, and `Instant` for events. Family timezone determines today and past-date rules. Normal execution is available only for today; future completion is rejected. Do not infer timezone from server/device. |
| Inactive sources/members | Inactive routines/items are omitted by BE-04 and therefore stop contributing during open synchronization. Do not create executions for inactive members. Existing history remains readable. |
| Concurrency | Make materialization and state transitions transactional. Use optimistic locking on mutable execution state where needed; surface a safe conflict for reload. The database enforces uniqueness. |

Reads remain side-effect free. Once an execution date is earlier than the
family-local current date, treat it as closed: do not synchronize it and reject
completion changes unless an ADMIN/OWNER explicitly reopened it. Persist the
`FINALIZED` transition lazily on the next state-changing access; do not add a
scheduler. History reads report the closed state even if persistence has not
yet occurred.

## Access and API proposal

All routes require the existing Bearer authentication. Resolve the caller from
the security context and verify current family membership on every request.
MEMBER, ADMIN, and OWNER may view, complete, and undo completion. Only ADMIN and
OWNER may finalize or reopen. A child FamilyMember needs no linked User.
Validate family → member → execution/date → item using scoped queries. Missing
or cross-family resources return the established safe 404 behavior; insufficient
role returns `403 FORBIDDEN` after family access is established.

| Method and route | Access | Behavior |
| --- | --- | --- |
| `PUT /api/families/{familyId}/members/{memberId}/executions/{date}` | Any member | Idempotently materialize or synchronize today's execution from BE-04 and return it. Past dates return existing history without synchronization; future dates are rejected. |
| `GET /api/families/{familyId}/members/{memberId}/executions/{date}` | Any member | Read an existing execution without mutation. Return 404 if it has not been materialized. |
| `POST .../executions/{date}/items/{itemId}/complete` | Any member | Idempotently complete an item in today's open execution, or an explicitly reopened historical execution. |
| `POST .../executions/{date}/items/{itemId}/uncomplete` | Any member | Idempotently return an item to pending under the same rules. |
| `POST .../executions/{date}/finalize` | ADMIN/OWNER | Finalize an open execution, including one with pending items. |
| `POST .../executions/{date}/reopen` | ADMIN/OWNER | Reopen finalized history for correction without resynchronization. |
| `GET /api/families/{familyId}/members/{memberId}/executions?from=&to=&page=0&size=20` | Any member | Return existing executions in the inclusive date range, ordered `date DESC, id DESC`, with bounded offset pagination and per-day summary. |

The ellipsis routes share the full prefix shown in the first route. The daily
response includes execution status, date, member summary (including current
member color), note snapshot, and ordered item snapshots/state. Do not persist a
member color on execution items. History does not materialize missing days. Use
the repository's page shape (`items`, `page`, `size`, `hasNext`); defaults are
page 0 and size 20, maximum 100. Define whether `from` and `to` may be omitted
and reject inverted ranges in the implementation contract.

Summary counts are derived from snapshots: total, completed, pending, cancelled.
Cancelled items are excluded from completion percentage's denominator; omit or
return null percentage when there are no applicable items. Do not persist the
summary. Empty executions are valid.

Use existing ProblemDetail, localization, and validation conventions. Reuse
`UNAUTHENTICATED`, `FORBIDDEN`, `FAMILY_NOT_FOUND`, and `FAMILY_MEMBER_NOT_FOUND`.
Add only needed stable execution/item codes, such as `DAILY_EXECUTION_NOT_FOUND`,
`DAILY_EXECUTION_ITEM_NOT_FOUND`, `DAILY_EXECUTION_FINALIZED`,
`DAILY_EXECUTION_FUTURE_DATE`, and `DAILY_EXECUTION_CONFLICT`; localize details in
English, Portuguese, and Spanish. Do not expose cross-family existence.

## Persistence and verification

Add versioned migrations after V7 for `daily_executions` and
`daily_execution_items`. Include unique execution identity, foreign keys to
family/member/user, status/source checks, unique non-null source identity per
execution, and indexes supporting member/date history and item loading. Keep
`ddl-auto=validate` and `open-in-view=false`; do not cascade-delete history when
a member, routine, or plan is deactivated or removed. Store audit fields as
UTC instants. Add `@Version` only where used to prevent lost state updates.

Use TDD and PostgreSQL Testcontainers. Cover concurrent materialization and
completion, snapshots, open synchronization (including removals and completed
items), idempotent complete/uncomplete, role and family isolation, finalize and
reopen without resync, family-timezone day boundaries, future-date rejection,
history pagination/order/summary, empty days, and inactive-member history.
Run `./mvnw verify` with Docker when implementation is complete.

## Out of scope

Child authentication, direct/manual execution items, complete audit log,
scheduler, background jobs, offline conflict resolution, reports, streaks,
points, rewards, notifications, uploads, and planning CRUD are not part of BE-05.

## Acceptance

1. A single execution per family/member/date is materialized idempotently from
   the BE-04 resolved plan with note and item snapshots.
2. Completion and uncompletion are authorized, idempotent, actor/timestamp aware,
   and constrained by family-local date and execution state.
3. Open executions synchronize only as specified; completed facts and finalized
   history are never silently rewritten.
4. OWNER/ADMIN can finalize and reopen; reopening permits correction without
   importing current planning.
5. Family timezone governs day boundaries; future execution is not created by
   the normal flow; stale open days cannot change and are lazily persisted as
   finalized without a scheduler. GET requests remain read-only.
6. Authorized clients can page history with deterministic order and accurate
   summaries; empty days and inactive members' existing history are supported.
7. PostgreSQL constraints, family isolation, API documentation, OpenAPI, and
   Testcontainers coverage enforce these rules.
