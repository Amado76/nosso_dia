# Family integration — BE-02

This is the implemented family API contract. The [PRD](be-02-family-authorization-prd.md)
records the scope and selected policies. Authentication follows
[BE-01](authentication.md); base URLs and local startup follow the [README](../README.md).
Routes are relative to the configured backend origin, with no additional prefix.

## Access and scope

A `FamilyMembership` grants a login identity (`User`) a role in one family. A
future `FamilyMember` will describe a person, including a child without a login.
Creating a family creates only the family and its creator's OWNER membership.

Each family has one OWNER through the supported API. A user may belong to multiple
families and have different roles in each. Names need not be unique. All family
operations resolve the acting user from the authenticated JWT subject and verify
that the user still exists. Family permissions are read from persisted memberships,
not token claims or client-provided identifiers.

| Operation | No membership | MEMBER | ADMIN | OWNER |
| --- | --- | --- | --- | --- |
| Create family | Allowed with authentication | Allowed | Allowed | Allowed |
| List families | Only accessible families | Only accessible families | Only accessible families | Only accessible families |
| Read family | 404 | Allowed | Allowed | Allowed |
| Rename family | 404 | 403 | Allowed | Allowed |

There are no routes for deletion, archival, invitations, membership listing or
editing, leaving, ownership transfer, or person/child profiles. Only OWNER
memberships are created by public APIs today; ADMIN/MEMBER behavior is supported
and tested for future membership flows. A future role-management implementation
must coordinate permission changes with writes and preserve one OWNER per family.

## Headers and formats

- Every endpoint requires `Authorization: Bearer <accessToken>`.
- POST and PATCH require `Content-Type: application/json` and a JSON object body.
- Successful bodies use `application/json`; errors use `application/problem+json`.
- Optional `Accept-Language` selects English, Portuguese, or Spanish as described
  in [API localization](api.md#localization). Missing/unsupported language defaults
  to English. Names are user content and are not translated.
- Path identifiers are UUIDs. Response timestamps are ISO-8601 UTC instants.

## Create a family

`POST /api/families`

```json
{"name":"Amado Family"}
```

The only accepted field is `name`: a required, non-null JSON string. Surrounding
whitespace is stripped using Java `String.strip()`. The normalized value must not
be blank and must contain at most 120 UTF-16 code units (supplementary characters
such as many emoji count as two). Capitalization, accents, and internal whitespace
are preserved. No default is supplied. Numbers, arrays, objects, unknown fields,
and server-controlled fields such as `userId`, `ownerId`, or `role` are rejected.

Returns `201 Created`, with `Location: /api/families/{id}` and this representation:

```json
{
  "id":"d6b68b76-283b-46f6-a65d-8d38ce6d1d13",
  "name":"Amado Family",
  "myRole":"OWNER",
  "createdAt":"2026-09-21T12:00:00Z",
  "updatedAt":"2026-09-21T12:00:00Z"
}
```

| Response field | Type and meaning |
| --- | --- |
| `id` | Server-generated UUID identifying the family |
| `name` | Normalized family name |
| `myRole` | Caller's role in this family: `OWNER`, `ADMIN`, or `MEMBER` |
| `createdAt` | Creation instant |
| `updatedAt` | Most recent successful create/rename instant |

All five fields are required and non-null. No user identities, emails, people, or
membership collections are included. Creation persists the family and initial
OWNER membership in one transaction: failure rolls back both. It neither issues
tokens nor sends invitations or modifies other families.

## List accessible families

`GET /api/families?page=0&size=20`

| Query parameter | Contract |
| --- | --- |
| `page` | Optional integer, zero-based, minimum 0, default 0 |
| `size` | Optional integer, minimum 1, maximum 100, default 20 |

Missing or empty values use the defaults. The offset `page * size` must be below
2,147,483,647; unsupported offsets return 400. There are no supported filters or
client-selectable sort parameters. Results are always ordered by `createdAt ASC,
id ASC`, with membership filtering performed before pagination. No total counts
are returned. Ordering is deterministic, but pages are not a snapshot across
requests when records or memberships change.

Returns `200 OK`:

```json
{
  "items":[{
    "id":"d6b68b76-283b-46f6-a65d-8d38ce6d1d13",
    "name":"Amado Family",
    "myRole":"OWNER",
    "createdAt":"2026-09-21T12:00:00Z",
    "updatedAt":"2026-09-21T12:00:00Z"
  }],
  "page":0,
  "size":20,
  "hasNext":false
}
```

All fields are required and non-null. `items` contains the family representation
above; `page`/`size` reflect the requested or default integer values. `hasNext` is a
boolean indicating another slice. Users without memberships and pages beyond the
available results receive `items: []` and `hasNext: false`.

## Read or rename a family

`GET /api/families/{familyId}` requires a valid UUID and a membership in that
family. It returns `200 OK` with the same family representation as creation,
including the caller's current `myRole`.

`PATCH /api/families/{familyId}` requires OWNER or ADMIN in the specified family:

```json
{"name":"Updated Family Name"}
```

Only `name` is writable, using the creation rules. Although this is a partial
update of the family resource, its only editable field must be present: omitted
`name`, null, `{}`, and a missing body are invalid. Success returns `200 OK` with
the updated representation. `id`, `createdAt`, and memberships are preserved;
`updatedAt` records the rename time, including assignment of the existing name.
No ETags or conflict preconditions are supported. Concurrent renames use last
committed write wins. All four endpoints return bodies on success; none returns 204.

## Errors and client handling

| HTTP | Stable code / condition | Client action |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR`: name constraints or page/size bounds | Correct the input; show safe field feedback when available |
| 400 | Invalid JSON, wrong value type, unknown body field, invalid UUID, malformed query syntax, missing body | Fix the request; framework responses may omit an application code |
| 401 | `UNAUTHENTICATED`: missing/invalid/expired access token or unavailable current user | Follow BE-01 renewal/login flow |
| 403 | `FORBIDDEN`: MEMBER attempting to rename its family | Show insufficient permissions; refreshing does not grant access |
| 404 | `FAMILY_NOT_FOUND`: missing family or no membership | Clear unavailable selection and reload accessible families |
| 405 | Unsupported method on a mapped route, including DELETE | Do not offer unimplemented operations |
| 415 | Unsupported request media type | Send JSON |
| 500 | `INTERNAL_SERVER_ERROR` | Show a generic failure and reconcile uncertain mutation outcomes |

Unknown and inaccessible families use the same safe 404 contract. Validation may
reject a malformed request before membership lookup; it never includes protected
family data. Responses for valid MEMBER rename attempts return 403. UI code must
branch on stable codes, not localized text; framework/proxy failures can lack
codes or JSON altogether.

Example 404:

```json
{
  "type":"about:blank",
  "title":"Not Found",
  "status":404,
  "detail":"Family not found",
  "instance":"/api/families/d6b68b76-283b-46f6-a65d-8d38ce6d1d13",
  "code":"FAMILY_NOT_FOUND"
}
```

Example invalid name:

```json
{
  "type":"about:blank",
  "title":"Validation failed",
  "status":400,
  "detail":"Validation failed",
  "instance":"/api/families",
  "code":"VALIDATION_ERROR",
  "errors":[{"field":"name","message":"Enter a family name of 1 to 120 characters"}]
}
```

Field errors may occur in any order and contain multiple entries for a field.
Service-level validation, including an unsupported pagination offset, returns
`VALIDATION_ERROR` without a field `errors` array. Never depend on that array being
present for every 400 response.

## UI flow, retries, and limitations

1. Authenticate through BE-01 and load the family list using the access token.
2. If empty, offer family creation. Otherwise show a selector; users may have
   multiple families. After creation, select the returned family ID.
3. Load details when selecting a family. Use `myRole` to present rename controls,
   while treating backend permission responses as authoritative.
4. Store a selected ID only as navigation state; it is neither a token claim nor
   a field on the user. Revalidate stored selections and clear unavailable ones.
5. Clear cached family data when switching accounts or logging out. Future child
   and routine screens must use their own family-scoped APIs.

GET can be retried. There are no idempotency keys: a POST retry after a timeout
may create a duplicate family. Reload and let the user reconcile before creating
again; duplicate names mean a matching name alone is not proof of the outcome.
PATCH assigns a name but retrying can overwrite another writer's change; reload
and confirm the intended edit first. Follow BE-01's bounded refresh behavior for
401 and do not automatically repeat ambiguous mutations or refresh on 403/404.

No additional dependencies, configuration variables, family count quotas, or
family-specific rate limiter are introduced. The auth POST limiter does not cover
these routes. Creation quotas and deployment abuse controls remain a product and
rollout decision. No migration changes existing user/authentication data. Flyway
V5 adds families, memberships, foreign keys, valid-role/name constraints, unique
membership pairs, an at-most-one-OWNER index, and a user-membership lookup index.
Atomic creation and the absence of owner removal maintain an OWNER through the
supported API; the index alone does not enforce at least one OWNER after direct SQL.

## Verification

Run `./mvnw verify` with Docker available. `FamilyTests` uses PostgreSQL Testcontainers
to cover role scoping, cross-family access, pagination, validation, localization,
transaction rollback, uniqueness/reference constraints, concurrent membership
inserts, and OpenAPI. No external identity providers or mail delivery are required.
