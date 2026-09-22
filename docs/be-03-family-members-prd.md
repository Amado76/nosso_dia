# BE-03 — Family Members

Status: implemented. This document records the BE-03 scope and decisions.
See the [family members integration guide](family-members.md) for the implemented
UI–API contract.

## Outcome and boundaries

A `FamilyMember` represents a person within exactly one `Family`. `User` is a login
identity; `FamilyMembership` grants that user access to a family. An optional
`FamilyMember.linkedUserId` associates a person with an existing account but never
grants access. A child can have no account. Names can repeat within and across
families; UUIDs identify members. BE-03 supplies stable person IDs, classification,
birth dates, colors, and active state for future routines and historical records.
It introduces no routine, invitation, membership-management, or upload flow.

The existing BE-02 implementation keeps family authorization in `FamilyService`;
there is currently no `FamilyAuthorizationService`. Extract a focused collaborator
in the `family` feature for persisted membership/role checks and have both family
and family-member services use it. Preserve BE-02 behavior and avoid direct
`FamilyMembershipRepository` access from `familymember`. This is a focused reuse
of existing rules, not a generic permission framework.

## Decisions required for this increment

| Topic | BE-03 decision |
| --- | --- |
| Member types | Explicit `ADULT` or `CHILD`; birth date never changes type automatically |
| Creation and edits | OWNER/ADMIN; MEMBER can read and self-link |
| Deletion | No physical DELETE endpoint; use reversible deactivation |
| Other-account links | Domain and database support them; no public user-ID linking endpoint until membership management exists |
| Avatar | Reserve nullable `avatarReference` in persistence and responses. It is read-only and remains null in BE-03; the media feature will define and validate a server-owned reference before writes are enabled |
| List | Active by default; optional inactive inclusion and type filter; ordered `createdAt ASC, id ASC`, bounded offset pagination |
| Concurrent profile edits | Last committed update wins; no ETag or version in this increment |

The avatar decision intentionally narrows the original proposed PATCH field. A
client-provided string would have no real media object or ownership check today and
could become an unsafe URL/path contract. BE-03 prepares the schema and DTO; actual
avatar association is an acceptance criterion for the later media feature, not a
claim of functionality here.

## Domain and persistence

Create `com.nossodia.familymember` with responsibility subpackages for controller,
dto, entity, repository, service, and exception only where classes are needed.
Keep family and user persistence behind their owning feature boundaries. No JPA
collection on `Family`; use UUID references and scoped repository queries.

Add the next versioned Flyway migration after V5 in `nosso_dia`:

| Column | Type | Rule |
| --- | --- | --- |
| `id` | UUID | Primary key, server-generated |
| `family_id` | UUID | Required FK to `families.id` |
| `name` | VARCHAR(120) | Required, nonblank after normalization; no unique constraint |
| `member_type` | VARCHAR(10) | Required, check `ADULT`/`CHILD` |
| `birth_date` | DATE | Nullable |
| `color` | VARCHAR(7) | Nullable, canonical uppercase `#RRGGBB`; database format check |
| `avatar_reference` | VARCHAR(255) | Nullable; no BE-03 write path |
| `linked_user_id` | UUID | Nullable; composite FK with `family_id` to the user's FamilyMembership |
| `active` | BOOLEAN | Required, default true |
| `created_at`, `updated_at` | TIMESTAMP WITH TIME ZONE | Required UTC instants |

Enforce a unique `(family_id, linked_user_id)` pair for non-null links. PostgreSQL's
ordinary unique constraint permits multiple null values. Add a composite FK from
`(family_id, linked_user_id)` to the existing unique
`family_memberships(family_id, user_id)` so a link cannot survive without membership,
even if written outside the API. The composite FK implies user existence through
the membership's user FK; a separate user FK is redundant. A later
membership-removal feature must explicitly
unlink affected members before deleting a membership; do not cascade-delete people.

Index the family-scoped list query for its actual filters and deterministic order;
avoid redundant indexes already covered by the unique link constraint. Verify the
plan and constraint behavior on PostgreSQL. The application validates future dates,
while the database enforces stable structural rules. Use `LocalDate` for birth date,
`Instant` for audit times, and an injected `Clock` for repeatable date/time checks.
Do not persist age. Keep `ddl-auto=validate` and `open-in-view=false`.

## Authorization and disclosure

Every operation first resolves the authenticated user and verifies current
`FamilyMembership` in the path family. Never infer access from `linkedUserId` or
JWT role claims. For member-specific routes, then query the member by both
`familyId` and `memberId` before checking the role needed for mutation. Missing
or inaccessible families return the existing `404 FAMILY_NOT_FOUND`; a missing
member or a member belonging to another family returns an identical
`404 FAMILY_MEMBER_NOT_FOUND`. A caller with membership but insufficient role
receives `403 FORBIDDEN`, after the family boundary is established. Responses and
logs must not disclose another family's person or account data.

| Operation | MEMBER | ADMIN | OWNER |
| --- | --- | --- | --- |
| List/read | Yes | Yes | Yes |
| Create/edit/deactivate/reactivate | No | Yes | Yes |
| Link own account to an unlinked active member | Yes | Yes | Yes |
| Unlink own account | Yes | Yes | Yes |
| Unlink another account | No | Yes | Yes |

Self-link uses the JWT subject and requires current membership in the same family.
It cannot create membership. There is no endpoint accepting another user's UUID.
The domain operation for a future authorized other-user link must require the
target's membership in the same family. Serialize competing links to the same
member with a row lock or conditional update so one account cannot silently
replace another. The composite FK and unique constraint protect membership and
same-user uniqueness under concurrent requests.

## HTTP contract

All routes require `Authorization: Bearer <accessToken>`. Body requests require
`Content-Type: application/json`; successful bodies are JSON. Optional
`Accept-Language` follows the existing en/pt/es negotiation. Path IDs are UUIDs.
No request may assign `id`, `familyId`, `linkedUserId`, `active`, or audit fields.
Reject unknown JSON fields and wrong JSON types consistently with the Family API.

| Method and route | Role | Success and behavior |
| --- | --- | --- |
| `POST /api/families/{familyId}/members` | OWNER/ADMIN | `201` with body and relative `Location: /api/families/{familyId}/members/{id}` |
| `GET /api/families/{familyId}/members` | Any member | `200` page object; `page=0`, `size=20`, `includeInactive=false` and no `type` by default |
| `GET /api/families/{familyId}/members/{memberId}` | Any member | `200` member body, including inactive members |
| `PATCH /api/families/{familyId}/members/{memberId}` | OWNER/ADMIN | `200` updated member body |
| `POST /api/families/{familyId}/members/{memberId}/deactivate` | OWNER/ADMIN | `200` member body with `active=false` |
| `POST /api/families/{familyId}/members/{memberId}/reactivate` | OWNER/ADMIN | `200` member body with `active=true` |
| `PUT /api/families/{familyId}/members/{memberId}/link-me` | Any member | `200` member body with `linkedUser=true` |
| `DELETE /api/families/{familyId}/members/{memberId}/link` | Own link or OWNER/ADMIN | `204` with no body |

`includeInactive` accepts only `true` or `false`; `type` accepts only `ADULT` or
`CHILD`. Invalid filters return 400. Both filters apply after family authorization.
Lists use zero-based offset pagination: optional integer `page` defaults to 0
and optional integer `size` defaults to 20. Require `page >= 0`, `1 <= size <= 100`,
and `page * size < 2147483647`; invalid bounds return `400 VALIDATION_ERROR`.
Malformed integer parameters return framework 400 responses. Family and optional
active/type filters apply before pagination. Ordering is stable by
`createdAt ASC, id ASC`; clients cannot select a sort or manual order.

The response is an object with required, non-null properties: `items` (array of
member responses), `page` (requested integer page), `size` (requested integer page
size), and `hasNext` (boolean). An empty or out-of-range page returns 200, for example:

```json
{"items":[],"page":2,"size":20,"hasNext":false}
```

No total count is calculated. Read `items` and request `page + 1` with the same
size and filters while `hasNext` is true. Restart at page 0 when filters change.
Concurrent creation or active/type changes can shift page boundaries; reload from
page 0 when refreshing the list. Database reads fetch at most `size + 1` rows.
This replaces the initial unbounded-array proposal: an expected small family size
cannot enforce a resource bound, and pagination avoids imposing a new creation limit.

Create body example:

```json
{"name":" Daniel Amado ","memberType":"CHILD","birthDate":"2019-03-22","color":"#a8d8ea"}
```

`name` and `memberType` are required non-null values. Normalize name with
`String.strip()`, then require nonblank and at most 120 UTF-16 code units. Preserve
case, accents, and internal whitespace. `birthDate` and `color` may be omitted or
null. Dates are ISO `YYYY-MM-DD` and must not be after the current UTC date from
the injected clock. Color accepts exactly `#` plus six hex digits, case insensitive;
persist and return uppercase. `avatarReference` and `linkedUserId` are rejected in
requests. `active` defaults to true. Duplicated names are valid.

The response shape for create, list elements, detail, edit, state changes, and link
is:

```json
{
  "id":"18a47f70-94e9-4e06-9150-29d69b177110",
  "familyId":"d6b68b76-283b-46f6-a65d-8d38ce6d1d13",
  "name":"Daniel Amado",
  "memberType":"CHILD",
  "birthDate":"2019-03-22",
  "color":"#A8D8EA",
  "avatarReference":null,
  "linkedUser":false,
  "active":true,
  "createdAt":"2026-09-22T01:00:00Z",
  "updatedAt":"2026-09-22T01:00:00Z"
}
```

Every response property is present. Only `birthDate`, `color`, and
`avatarReference` may be null. `linkedUser` is a boolean; account IDs, emails,
tokens, and membership details are never returned. Timestamps are UTC instants.

PATCH accepts a nonempty JSON object containing any subset of `name`,
`memberType`, `birthDate`, and `color`. Omitted fields retain their values. Explicit
null clears `birthDate` or `color`, while null for `name`/`memberType` is invalid.
Use a presence-aware request model so omitted and explicit null differ; plain
nullable record fields alone are insufficient. Reject `avatarReference` until the
media contract exists. For example, `{"birthDate":null,"color":"#F6C1C7"}` clears
the date and changes the color without touching the name or type.

`PUT link-me` has no body. Linking an already-linked member to the same caller is
idempotent (`200`). An existing link to another account is `409
FAMILY_MEMBER_ALREADY_LINKED`; a caller already linked to a different member in
this family gets `409 USER_ALREADY_LINKED_TO_FAMILY_MEMBER`. An inactive member
must be reactivated before a new link (`409 FAMILY_MEMBER_INACTIVE`). Under a
concurrent race, translate the unique constraint violation to the same 409
contract; never expose SQL or user details. A retry can fetch the member and
reconcile whether the link now exists.

`DELETE link` removes only the account association. OWNER/ADMIN may clear any
link; for them, clearing an already empty link is an idempotent 204. MEMBER may
clear only their own link; an empty or another user's link returns 403. Unlinking
does not delete the user, membership, or person and works for inactive members.
Deactivate/reactivate are idempotent assignments; repeating the current state
returns 200. `updatedAt` changes only when persisted member state changes.
No operation sends invitations or changes authentication tokens.

## Errors, localization, and client retries

Use the shared `ProblemDetail` contract. Add only these domain codes:
`FAMILY_MEMBER_NOT_FOUND` (404), `FAMILY_MEMBER_ALREADY_LINKED` (409),
`USER_ALREADY_LINKED_TO_FAMILY_MEMBER` (409), and `FAMILY_MEMBER_INACTIVE` (409).
Reuse `FAMILY_NOT_FOUND`, `FORBIDDEN`, `VALIDATION_ERROR`, and
`UNAUTHENTICATED`. Structural validation returns 400; framework errors for
malformed JSON, unknown fields, invalid UUIDs, and invalid enum/filter values may
omit an application code, as in BE-02. Do not create a distinct code for every
field error. Add English, Portuguese, and Spanish message keys for all new
user-facing domain and validation messages; keep stable codes untranslated.

GET and idempotent state/link operations can be retried after checking current
state. POST creation has no idempotency key: an ambiguous timeout can leave a
created member, and duplicate names cannot identify it. Reload the list before
offering another creation. PATCH can overwrite another editor's later change under
last-write-wins; reload before retrying an uncertain edit. On 401, follow the
existing bounded token renewal flow. On 403/404, reselect an accessible family or
stop offering that operation. No new configuration variables or rate limiter are
part of BE-03.

## Delivery and verification

Implementation must use Red → Green → Refactor for each behavior slice. Add
PostgreSQL Testcontainers integration coverage for schema constraints, family
scoping, roles, filters, link uniqueness, and HTTP contract. Use focused tests
first, then `./mvnw verify` with Docker available. Keep tests behavioral and
exercise real commit/flush boundaries for constraint failures. Update OpenAPI and
write `docs/family-members.md` as the implemented integration guide, linked from
the README in the same change.

Acceptance scenarios:

1. Create ADULT and CHILD members with/without optional date/color; names are
   normalized, duplicates allowed, colors canonicalized, and birth dates are
   calendar dates. Future dates, invalid types/colors, wrong types, and unknown
   fields fail without persisting changes.
2. List active members by default, include inactive members on request, filter
   by either type, and verify deterministic ordering, page bounds, and `hasNext`. Detail can read inactive
   members in the authorized family.
3. PATCH changes each writable field independently, clears optional values with
   explicit null, rejects null for required fields, and preserves omitted fields.
4. OWNER and ADMIN can create, edit, deactivate, and reactivate. MEMBER can list
   and read but receives 403 for those mutations. Anonymous callers receive 401.
5. Self-link and own unlink work for every role. MEMBER cannot unlink another
   account. Same-account repeated link and privileged repeated unlink follow the
   stated idempotent behavior.
6. One user cannot link to two members in one family, including under concurrent
   requests, but may be represented once in each of two families. A link without
   matching membership fails at the database boundary; linking never grants
   family access.
7. A user from family A cannot list, read, edit, deactivate, reactivate, or link
   a member of family B. A family-A path with a family-B member ID returns the
   same safe 404 as an unknown member ID.
8. Domain errors keep stable codes and safe details across `en`, `pt`, `es`,
   `pt-BR`, `es-PY`, unsupported language, and no language header.
9. OpenAPI describes all routes, roles, request/response schemas, filters, and
   errors. The integration guide documents actual behavior. The migration
   validates against existing BE-02 data and `./mvnw verify` passes.

Out of scope: invitations, membership management, linking another account by
public API, child login, parental controls, routine/tasks, school or medical data,
family relationships, media upload/download, physical deletion, custom display
order, reports, and audit history.
