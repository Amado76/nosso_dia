# Family Members — UI–API Integration

PDR-03 represents people in a family. A person (FamilyMember) differs from a login
account (User) and an access grant (FamilyMembership). An account link never
grants access. A child can exist without an account. Each person belongs to
exactly one family; names may repeat and UUIDs identify people.

## Setup and scope

Use the configured application origin and the routes below. Normal application
startup applies Flyway migrations, including V6 for profiles and V13 for preferences.
No new environment variables, external services, or
feature-specific rate limits are required. Follow [authentication](authentication.md)
for login and bounded token refresh, and [families](families.md) to select an
accessible family. Authentication and existing family memberships are prerequisites.

Avatar references are read-only and always null in PDR-03. Upload/download and
avatar association await the media feature. Invitations, membership management,
public other-account linking, child login, parental controls, routines, family
relationships, physical deletion, reports, and audit history are not implemented.

## Authorization and disclosure

Every operation first resolves the authenticated user and verifies current
`FamilyMembership` in the path family. Access is never inferred from `linkedUserId` or
JWT role claims. Member-specific routes then query the member by both
`familyId` and `memberId` before checking the role needed for mutation. Missing
or inaccessible families return the existing `404 FAMILY_NOT_FOUND`; a missing
member or a member belonging to another family returns an identical
`404 FAMILY_MEMBER_NOT_FOUND`. A caller with membership but insufficient role
receives `403 FORBIDDEN`, after the family boundary is established. Responses and
logs do not disclose another family's person or account data.

| Operation | MEMBER | ADMIN | OWNER |
| --- | --- | --- | --- |
| List/read | Yes | Yes | Yes |
| Create/edit/deactivate/reactivate | No | Yes | Yes |
| Link own account to an unlinked active member | Yes | Yes | Yes |
| Unlink own account | Yes | Yes | Yes |
| Unlink another account | No | Yes | Yes |

Self-link uses the JWT subject and requires current membership in the same family.
It cannot create membership. There is no endpoint accepting another user's UUID.
The service verifies the target account's membership before linking. Row locks
serialize competing writes to a person, preventing one account from replacing
another and preventing profile edits from overwriting links. The composite foreign
key and unique constraint protect membership and same-user uniqueness.

## HTTP contract

All routes require `Authorization: Bearer <accessToken>`. Body requests require
`Content-Type: application/json`; successful bodies are JSON. Optional
`Accept-Language` follows the existing en/pt/es negotiation. Path IDs are UUIDs.
No request may assign `id`, `familyId`, `linkedUserId`, `active`, or audit fields.
Unknown JSON fields and wrong JSON types are rejected consistently with the Family API.

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
  "preferences":{},
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
`memberType`, `birthDate`, `color`, and `preferences`. Omitted fields retain their
values. Explicit
null clears `birthDate` or `color`, while null for `name`/`memberType` is invalid.
Field presence distinguishes omitted values from null. `avatarReference` is
rejected until the media contract exists. For example, `{"birthDate":null,"color":"#F6C1C7"}` clears
the date and changes the color without touching the name or type.

`PUT link-me` has no body. Linking an already-linked member to the same caller is
idempotent (`200`). An existing link to another account is `409
FAMILY_MEMBER_ALREADY_LINKED`; a caller already linked to a different member in
this family gets `409 USER_ALREADY_LINKED_TO_FAMILY_MEMBER`. An inactive member
must be reactivated before a new link (`409 FAMILY_MEMBER_INACTIVE`). Under a
concurrent race, unique constraint violations use the same 409
contract without exposing SQL or user details. A retry can fetch the member and
reconcile whether the link now exists.

`DELETE link` removes only the account association. OWNER/ADMIN may clear any
link; for them, clearing an already empty link is an idempotent 204. MEMBER may
clear only their own link; an empty or another user's link returns 403. Unlinking
does not delete the user, membership, or person and works for inactive members.
Deactivate/reactivate are idempotent assignments; repeating the current state
returns 200. `updatedAt` changes only when persisted member state changes.
No operation sends invitations or changes authentication tokens.

## UI preferences

Preferences belong to the family person, including children without login accounts.
They are shared across authorized viewers of that profile, rather than tied to the
viewing user or device. Both ADULT and CHILD profiles support preferences.
Linking/unlinking an account and deactivating/reactivating a person preserve them.

Every member response includes a non-null `preferences` JSON object, initially
`{}`. It contains explicitly selected values only. The existing `color` remains
the general member color; it is independent of `preferences.completedTaskColor`.
No value is copied from `color`, and the backend does not assign a default completed
task color. The UI uses its own default when the preference is absent.

Use the existing endpoint, with the usual Bearer token and JSON headers:

```http
PATCH /api/families/{familyId}/members/{memberId}
Content-Type: application/json
Authorization: Bearer <accessToken>
```

```json
{"preferences":{"completedTaskColor":"#86b8d9"}}
```

Success is `200` with the full member response, including:

```json
{"preferences":{"completedTaskColor":"#86B8D9"}}
```

This last example is a response excerpt. Obtain preferences through member detail
or list responses; execution responses still contain only their existing member
summary. Creation initializes preferences to `{}`; the POST request does not accept
`preferences`. Create the member first, then PATCH preferences if needed.

| Field/key | Accepted values and behavior |
| --- | --- |
| `preferences` omitted | Preserve all preferences. |
| `preferences: {}` | No preference changes; valid as the sole PATCH field. |
| `preferences: null` or a non-object | `400 VALIDATION_ERROR`. |
| `completedTaskColor` | String matching exactly `#[0-9a-fA-F]{6}`; stored and returned uppercase. |
| `completedTaskColor: null` | Remove the key; the UI resumes its default. |
| Omitted preference key | Preserve its stored value. |
| Unsupported key | Reject the entire PATCH with `400 VALIDATION_ERROR`, including when its value is null. |

Color names, three/eight-digit hex colors, whitespace, numbers, booleans, arrays,
and nested objects are invalid completed-task colors. Key names are case-sensitive.
An invalid preference rejects all changes in that request, including profile edits.
Domain validation uses localized ProblemDetail with `VALIDATION_ERROR`; a field
errors array is not guaranteed. Malformed JSON retains framework error handling.

Any current family member may read preferences. Only OWNER/ADMIN may update them,
including for inactive profiles. A linked account does not grant edit permission.
Existing 401, 403, and family/member 404 disclosure rules apply unchanged.

Updates merge keys under the same transactional row lock as other profile writes.
Concurrent changes to different fields/keys are preserved; the last serialized
update to the same key wins. There are no ETags or conflict detection. Repeating
an unchanged value or removing an absent key does not change `updatedAt`.
After an uncertain PATCH outcome, reload before retrying because a retry may
overwrite another editor's newer value.

Only documented UI preferences belong here. Tasks, completion status, permissions,
ownership, and other business data must remain in dedicated domain structures.
New keys require backend validation, API documentation, and tests, usually without
a database migration. Unknown stored keys are preserved during partial updates for
rolling deployment compatibility, but clients cannot submit unsupported keys.
Clients should ignore response keys they do not understand.

V13 adds `preferences JSONB NOT NULL DEFAULT '{}'` with an object-type constraint.
Existing profiles receive an empty object and keep their existing color and account
links. No JSON index, new dependency, environment variable, or endpoint is required.
Deploy through Flyway; older application code can leave the additive column in
place on rollback. Preserve its data and never edit an applied migration.
Validate the upgrade on populated data and plan for PostgreSQL's ALTER TABLE lock
when deploying to large or busy tables.

## Errors, localization, and client retries

Errors use the shared [ProblemDetail contract](api.md#errors), with these domain codes:
`FAMILY_MEMBER_NOT_FOUND` (404), `FAMILY_MEMBER_ALREADY_LINKED` (409),
`USER_ALREADY_LINKED_TO_FAMILY_MEMBER` (409), and `FAMILY_MEMBER_INACTIVE` (409).
The API also uses `FAMILY_NOT_FOUND`, `FORBIDDEN`, `VALIDATION_ERROR`, and
`UNAUTHENTICATED`. Structural validation returns 400; framework errors for
malformed JSON, unknown fields, invalid UUIDs, and invalid enum/filter values may
omit an application code, as in PDR-02. English, Portuguese, and Spanish translations cover domain and validation
messages; stable codes remain untranslated.

GET and idempotent state/link operations can be retried after checking current
state. POST creation has no idempotency key: an ambiguous timeout can leave a
created member, and duplicate names cannot identify it. Reload the list before
offering another creation. PATCH can overwrite another editor's later change under
last-write-wins; reload before retrying an uncertain edit. On 401, follow the
existing bounded token renewal flow. On 403/404, reselect an accessible family or
stop offering that operation. No new configuration variables or rate limiter are
part of PDR-03.


Jakarta request validation includes an errors array of objects with field and
message properties. Domain validation (for example future dates, invalid colors,
or invalid PATCH values) returns a localized detail and VALIDATION_ERROR without
a guaranteed field-error array. Rejected values are never echoed. Malformed dates
and wrong JSON types also use framework 400 responses. Unsupported methods and
media types return framework 405/415. Unexpected failures return a safe 500 with
INTERNAL_SERVER_ERROR. Error responses use application/problem+json.

## Client sequence

1. Authenticate and select an accessible family through the Family API.
2. Load the first page of active people; optionally include inactive people or filter
   by type. Read `items` and follow `hasNext` to load subsequent pages.
3. OWNER/ADMIN can create, edit, deactivate, or reactivate people.
4. Any member can link their own account to an eligible person. Reload after a
   conflict; the linkedUser boolean does not identify whose account is associated.
5. Unlink before changing an association. Keep the stable person ID for future
   routines and historical records.

The client must use HTTP status and stable codes, never translated detail text.
Token renewal follows the existing authentication flow; no operation changes tokens,
sends mail, or sends invitations.

## Persistence and deployment

V6 adds a table and indexes without modifying PDR-02 data. UUID references preserve
feature boundaries. Birth dates use calendar dates; audit times use UTC instants.
Age is not stored, and birth date never changes the explicit member type.

Account links reference the existing family/user membership pair. Removing a
membership with a link is rejected by PostgreSQL; a later membership-removal
feature must unlink affected people first. The database never cascade-deletes
people. One account may be linked once per family, including inactive people.

Profile updates use last committed update wins per field/preference key, without
ETags or version fields.
All writes lock the person row so profile/state edits cannot overwrite concurrent
links. Omitted PATCH fields remain unchanged.

Deploy with normal Flyway migration enabled. A rollback to PDR-02 can leave the
additive V6 table in place; preserve its data. Do not edit applied migration files;
any schema correction needs a new versioned migration.

The PostgreSQL Testcontainers suite covers a populated V5-to-V6 upgrade, schema
constraints, indexed list plans, family isolation, roles, strict JSON, localization,
UTC dates, idempotency, and concurrent links. Run ./mvnw verify with Docker.
