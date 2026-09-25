# PDR-02 — Family & Authorization

Status: implemented baseline following the request to implement PDR-02.
The policies below were adopted for this increment, with DELETE deferred.
See [Family integration](families.md) for the implemented HTTP contract.
Creation quotas remain a rollout decision; no quota is enforced in this increment.

## Problem and outcome

Authentication identifies a BeeHome user. It does not establish which family's
data that user can access. Before introducing family members and routines, the
backend needs a family boundary enforced on every private operation.

PDR-02 lets an authenticated user create a family, become its OWNER automatically,
list their families, and access family details according to their membership.
Success means that a valid login never grants access to another family's data.

The first client flow is: login → list families → create or select a family →
load its details. This prepares the later Flutter flow through children, today's
routine, and task completion without implementing those features here.

## Scope and product decisions

The supplied product direction establishes `Family`, `FamilyMembership`, the roles
`OWNER`, `ADMIN`, and `MEMBER`, automatic ownership on creation, and protected family
operations. The specific policies below define the implemented baseline.

| Decision | Selected baseline | Why / consequence |
| --- | --- | --- |
| Families per user | Allow membership in multiple families; no one-family uniqueness rule | Fits the membership model and family list; any creation quota must be decided separately |
| Ownership | Exactly one OWNER per family | Makes authority clear; transfers and co-ownership require a later explicit flow |
| Rename permission | OWNER and ADMIN | MEMBER can read but cannot change family settings |
| Deletion | Defer DELETE until retention and dependent-data rules are defined | Avoids silently choosing irreversible deletion or archival semantics |
| Family name | Strip surrounding whitespace; require 1–120 characters after normalization; names need not be unique | Supports readable labels without treating a name as identity |
| Concurrent renames | Last committed write wins | Small initial scope; clients must not assume conflict detection |
| Joining and role management | Separate follow-up scope | PDR-02 creates only the creator's OWNER membership through public APIs |

Creation quotas and abuse controls still need a decision before public rollout;
the existing authentication POST limiter does not establish a family API limit.

Included in the selected baseline:

- Create a family and its initial OWNER membership atomically.
- List only families accessible to the authenticated user, with bounded pagination.
- Read family details and rename a family using the permission matrix below.
- Persist membership roles and enforce isolation with automated negative tests.
- Document the implemented API for Flutter and other clients, including OpenAPI.

Excluded: invitations, joining by identifier, membership listing or editing, role
promotion, ownership transfer, leaving a family, family deletion or archival,
account deletion, family people/children, routines, uploads, reports, and changes
to authentication. ADMIN and MEMBER authorization can be tested using fixtures;
there is no public flow for assigning these roles in this increment.

## Domain model

```text
User ──< FamilyMembership >── Family
              │
              └── role: OWNER | ADMIN | MEMBER

Future PDR-03:
Family ──< FamilyMember ── optional linkedUserId ──> User
```

`User` is a login identity. `FamilyMembership` grants that identity access to one
family. `FamilyMember` will represent a person in that family, including a child
without a login. A future `linkedUserId` is a profile association and must not,
by itself, grant permissions. PDR-02 does not create a person profile implicitly.

| Entity | Minimum selected fields |
| --- | --- |
| Family | `id: UUID`, `name: String`, `createdAt: Instant`, `updatedAt: Instant` |
| FamilyMembership | `id: UUID`, `familyId: UUID`, `userId: UUID`, `role: enum`, `createdAt: Instant` |

All fields above are non-null and identifiers/timestamps are server-generated.
Roles belong to a family membership, never to a global user role. A user may be
OWNER in one family and MEMBER in another under the selected multi-family policy.
The membership is the authority; do not add a second independently mutable
`ownerId` as a competing source of truth.

Persistence must enforce foreign keys, unique `(familyId, userId)`, valid roles,
and the agreed name bounds. Under single ownership, a partial unique index can
enforce at most one OWNER per family; atomic creation and the absence of owner
removal operations preserve the requirement to have at least one. A future
transfer/removal flow must explicitly preserve that invariant under concurrency.

## Authorization contract

Selected permissions:

| Operation | Authenticated, no membership | MEMBER | ADMIN | OWNER |
| --- | --- | --- | --- | --- |
| Create a family | Allowed | Allowed | Allowed | Allowed |
| List families | Own memberships only | Own memberships only | Own memberships only | Own memberships only |
| Read this family | Denied | Allowed | Allowed | Allowed |
| Rename this family | Denied | Denied | Allowed | Allowed |

All family endpoints require the existing Bearer authentication. Resolve the
internal user ID from the trusted SecurityContext. Request bodies, query parameters,
or headers must never select the acting user, initial owner, or membership role.

Use persisted membership for each authorization decision; do not embed family
roles in access JWTs. A role in family A never grants authority in family B.
Keep the rules in the family service boundary so non-controller callers cannot
bypass them. Filter lists before pagination and before any aggregate calculation.

Selected disclosure policy: unauthenticated requests return `401 UNAUTHENTICATED`;
an unknown family and a family inaccessible to the caller both return
`404 FAMILY_NOT_FOUND` with the same safe response. A known member with insufficient
permissions receives `403 FORBIDDEN`. Never disclose family names or membership
details in denial responses. UUID knowledge is not proof of access.

Future nested resources must match both the authorized family and the requested
resource ID. Linking a child from another family must never bypass this boundary.
That is a design requirement for later features, not a request to scaffold them now.

## Selected HTTP contract

Paths are relative to the configured backend origin. All requests use
`Authorization: Bearer <accessToken>`. Bodies use `Content-Type: application/json`;
successful bodies use JSON and errors follow the existing ProblemDetail contract.
Optional `Accept-Language` follows the existing English/Portuguese/Spanish behavior.

| Method and route | Input | Success |
| --- | --- | --- |
| `POST /api/families` | Required body with `name` | `201 Created`, family response and `Location: /api/families/{id}` |
| `GET /api/families` | Optional `page` and `size` | `200 OK`, bounded page of accessible families |
| `GET /api/families/{familyId}` | Required UUID path parameter | `200 OK`, family response |
| `PATCH /api/families/{familyId}` | Required UUID and body with `name` | `200 OK`, updated family response |

`DELETE /api/families/{familyId}` was suggested in the original outline. It is
pending the deletion decision and has no committed contract in this increment.
If included, define retention, restoration, dependent records, concurrent writes,
retry behavior, and ownership permissions before implementing it.

Create and rename request:

```json
{"name":"Amado Family"}
```

Only `name` is writable. It must be a non-null string satisfying the agreed
normalization and length rules. For this initial PATCH, missing `name`, explicit
null, and an empty body are invalid; additional editable fields would require
documented omitted-versus-null semantics. Reject unsupported body fields so
attempts to submit `userId`, `ownerId`, or `role` cannot silently appear successful.
Preserve capitalization, accents, and internal whitespace in accepted names.

Family response for create, detail, rename, and list items:

```json
{
  "id":"d6b68b76-283b-46f6-a65d-8d38ce6d1d13",
  "name":"Amado Family",
  "myRole":"OWNER",
  "createdAt":"2026-09-21T12:00:00Z",
  "updatedAt":"2026-09-21T12:00:00Z"
}
```

All response fields are required and non-null. `id` is a UUID, `myRole` is the
caller's role in this family, and timestamps are ISO-8601 UTC instants. Responses
do not include other users, emails, or membership collections. `myRole` supports
UI controls; the backend remains authoritative on every request.

Selected pagination: zero-based `page` (default 0), `size` (default 20, range 1–100),
ordered by `createdAt ASC, id ASC`. No filters or client-selectable sort initially.
Negative, malformed, or unsupported pagination values return 400. Return an
explicit slice DTO without total counts:

```json
{"items":[],"page":0,"size":20,"hasNext":false}
```

`items` contains family responses, `page` and `size` reflect the request/defaults,
and `hasNext` indicates another slice. All fields are required and non-null. A user
without memberships and a page beyond the available results return empty `items`.
Ordering is deterministic, but offset pagination is not a snapshot across requests.

Reuse existing error handling and localization; introduce only the family-specific
code needed for inaccessible/missing families:

| HTTP | Code / condition | Client handling |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` for field constraints | Show field feedback |
| 400 | Malformed JSON, invalid UUID, unsupported fields or invalid query syntax | Correct input; framework errors may omit a stable code |
| 401 | `UNAUTHENTICATED` | Use the existing bounded renewal/login flow |
| 403 | `FORBIDDEN` | Show insufficient permissions; do not refresh in a loop |
| 404 | `FAMILY_NOT_FOUND` | Clear unavailable selection and reload accessible families |
| 415 | Unsupported content type | Send JSON |
| 500 | `INTERNAL_SERVER_ERROR` | Show a generic failure; mutation outcome may be uncertain |

Error examples, field-level details, and implemented statuses appear in the
[integration guide](families.md), consistent with [API errors](api.md#errors).

## Transactions, retries, and client behavior

Creating the family and OWNER membership is one transaction: both persist or
neither persists. A failure must never leave an ownerless family. Creation changes
neither authentication tokens nor other memberships and sends no invitations.

No idempotency keys are supported. Retrying a POST after an ambiguous timeout
may create a second family, including one with the same name. The client should
reload its families and let the user reconcile the outcome before creating again.
GET can be retried. PATCH assigns an absolute name, but an automatic retry may
overwrite another rename under the selected last-write policy; reload before retry.
Concurrent membership changes in a later feature must coordinate authorization
checks and writes so revoked privileges cannot be reused for a new operation.

After login, load the family list. Show family creation when it is empty; otherwise
let the user select a family. Retain a selected ID only as client navigation state,
and revalidate it through the API. Clear cached family data when switching accounts
or logging out. PDR-02 does not add an active-family field to the user or JWT.

## Acceptance criteria and delivery

1. An authenticated creator receives 201 and becomes OWNER of exactly the newly
   created family; persisted family and membership agree with the response.
2. A simulated membership insert failure rolls back the family insert.
3. Anonymous or invalid-token callers cannot use any included family endpoint.
4. Users A and B can create separate families; neither can read or rename the
   other's family by supplying its UUID, and neither sees it in any list page.
5. A missing UUID and another user's family produce the same 404 contract.
6. MEMBER can read but cannot rename; ADMIN and OWNER can rename under the selected
   matrix. Roles in another family cannot satisfy the permission check.
7. Invalid names and client-supplied identity/role fields are rejected without
   creating or changing a family or membership.
8. Listing filters by membership before pagination, respects size bounds, and
   uses deterministic ordering without leaking totals or unrelated data.
9. PostgreSQL constraints reject duplicate memberships and invalid references;
   the agreed ownership rule is preserved, including concurrent writes.
10. Creation does not grant membership to other users, create a FamilyMember,
    issue tokens, or change the authentication contract.

Implementation belongs to a small `family` feature with explicit DTOs, service
transactions, focused repository queries, and new versioned Flyway migrations.
Reuse the existing internal user identity and shared API error handling. No new
infrastructure dependency is required by this proposal.

Follow [TDD](testing.md#required-tdd-workflow): establish failing behavior tests,
implement in small increments, then refactor. Use PostgreSQL Testcontainers for
constraints, rollback, scoping, and concurrency; finish with `./mvnw verify`.
Deliver a README-linked family integration guide describing actual implemented
behavior and matching OpenAPI. Keep this PRD's decisions and delivery status current.

## Next increments

- PDR-03 — Family Members / Children: model people independently of login identities.
  Decide person fields, privacy, relationships, and optional user linking there.
  Login invitations and membership administration need explicit scope of their own.
- PDR-04 — Daily Routine / Tasks: define daily plans, scheduled versus unscheduled
  items, completion, timezone/day boundaries, and child color behavior before coding.
- Flutter vertical slice: login → family → children → today's routine → complete
  task, including empty/loading/error states and unauthorized access handling.

Homeschool, books, photos, and reports can build on this boundary after the first
usable flow. Their storage, data model, and permissions are outside PDR-02.
