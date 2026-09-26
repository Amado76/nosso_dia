# Extracurricular activities — UI integration

An extracurricular activity is a reusable official family catalog entry. A dated occurrence belongs to one child and references its activity by ID. A free-form [tag](global-tags.md) is separate: a tag with the same name never becomes the official activity or changes activity totals. Data is stored in PostgreSQL by Flyway V17. No extra environment variable or external service is needed.

## Access, headers, and errors

All routes start with `/api/families/{familyId}` and require `Authorization: Bearer <access-token>`. Send `Content-Type: application/json` for POST and PATCH; responses use `application/json`. Clients may send `Accept-Language: en`, `pt`, or `es` for localized errors. All IDs are UUIDs and dates are strict ISO `YYYY-MM-DD`. No client user ID is accepted.

Any authenticated family member can read the catalog and records. Only ADMIN or OWNER can create, edit, deactivate, or reactivate catalog activities. Any family member can create, correct, or delete an occurrence for an active child in that family. Read access remains available for an inactive child. A family or child outside the caller's membership, an adult in a child route, a foreign activity, a foreign tag, or a foreign record is hidden with a safe 404. Activity names are not authorization keys.

| Status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Invalid JSON, date, field length, duration, page range, or pagination. |
| 401 | `UNAUTHENTICATED` | Sign in or refresh the Bearer token. |
| 403 | `FORBIDDEN` | Catalog write requires ADMIN or OWNER. |
| 404 | `FAMILY_NOT_FOUND`, `FAMILY_MEMBER_NOT_FOUND`, `ACTIVITY_NOT_FOUND`, `ACTIVITY_RECORD_NOT_FOUND`, `TAG_NOT_FOUND` | Inaccessible resource. |
| 409 | `ACTIVITY_DUPLICATE` | Normalized name already exists in this family. |

Errors use `application/problem+json` with `status`, localized `detail`, and stable `code`. Branch on `code`; framework conversion errors may lack an application code. There is no activity-specific rate limit.

## Catalog routes

Prefix: `/api/families/{familyId}/extracurricular-activities`.

| Method | Suffix | Success | Purpose |
| --- | --- | --- | --- |
| POST | base | 201 | Create an active activity. |
| GET | base, `?includeInactive=false` | 200 array | List by `sortOrder`, then ID. |
| GET | `/{activityId}` | 200 | Read one, including inactive. |
| PATCH | `/{activityId}` | 200 | Edit name, description, or order. |
| POST | `/{activityId}/deactivate` | 200 | Hide from new occurrences. |
| POST | `/{activityId}/reactivate` | 200 | Offer for new occurrences again. |

Create example: `{"name":"Museum Visit","description":"Field trips","sortOrder":0}`. `name` is required, trimmed, nonblank, and at most 120 characters. Family-wide uniqueness uses Unicode NFC and case-insensitive normalization. `description` is optional, nullable, trimmed, and at most 2000 characters; blank becomes null. `sortOrder` defaults to 0 and must be nonnegative. PATCH accepts a nonempty subset; `null` clears only `description`. An empty PATCH, `null` name, or `null` order is invalid. Deactivation preserves historical links and GET visibility. Repeated deactivate/reactivate requests are safe.

Response example:

```json
{"id":"88888888-8888-4888-8888-888888888888","familyId":"11111111-1111-4111-8111-111111111111","name":"Museum Visit","description":"Field trips","sortOrder":0,"active":true,"createdAt":"2026-09-26T15:00:00Z","updatedAt":"2026-09-26T15:00:00Z"}
```

Catalog list is limited to 1000 activities per family. It is an ordered array, with no page query parameters.

## Dated occurrence routes

Prefix: `/api/families/{familyId}/children/{childId}/extracurricular-records`.

| Method | Suffix | Success | Purpose |
| --- | --- | --- | --- |
| POST | base | 201 | Create one occurrence. |
| GET | `/{recordId}` | 200 | Read one occurrence. |
| PATCH | `/{recordId}` | 200 | Correct a nonempty subset of fields. |
| DELETE | `/{recordId}` | 204, empty body | Remove one occurrence and its tag links. |
| GET | `?from=2026-09-01&to=2026-09-30&page=0&size=20` | 200 page | List within inclusive dates, newest first. |

Create example:

```json
{"activityId":"88888888-8888-4888-8888-888888888888","date":"2026-09-21","topic":"Dinosaurs","durationMinutes":180,"description":"Visited the natural history gallery.","comments":"Asked about fossils.","material":"Museum guide","startPage":2,"endPage":4,"tagIds":["99999999-9999-4999-8999-999999999999"]}
```

`activityId` and `date` are required and cannot be null. The activity must be active for a new record or a reassignment. `topic` is optional, trimmed, at most 120 characters, and distinct from the activity's name. `durationMinutes` is optional, nullable, and strictly positive when present. `description` and `comments` are optional, trimmed, and at most 10000 characters each; `material` is optional free text up to 1000 characters. `startPage` and `endPage` are optional positive integers; either may appear alone, and when both appear `endPage >= startPage`. `tagIds` is optional, 0–100 unique family tag IDs; omitted on POST means `[]`. On PATCH, omitted fields retain their value, explicit `null` clears optional fields, and omitted `tagIds` preserves tags while `[]` clears them. Foreign or missing tags return `TAG_NOT_FOUND`.

Response example:

```json
{"id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","childId":"22222222-2222-4222-8222-222222222222","date":"2026-09-21","activity":{"id":"88888888-8888-4888-8888-888888888888","name":"Museum Visit"},"topic":"Dinosaurs","durationMinutes":180,"description":"Visited the natural history gallery.","comments":"Asked about fossils.","material":"Museum guide","startPage":2,"endPage":4,"tags":[{"id":"99999999-9999-4999-8999-999999999999","name":"Science","color":null}],"createdByUserId":"33333333-3333-4333-8333-333333333333","createdAt":"2026-09-26T15:00:00Z","updatedAt":"2026-09-26T15:00:00Z","version":0}
```

Nullable optional values appear as JSON `null`; `tags` is always an array. `activity.name` is the current catalog name, so a rename changes display text without changing record identity. Page defaults are 0 and 20; size must be 1–100. Response shape is `{"items":[...],"page":0,"size":20,"hasNext":false}`. Sorting is date descending and ID descending. The offset must fit a signed integer. No total count is calculated. Date bounds have no defaults and cannot be reversed. Multiple pages may shift during concurrent writes.

For UI flow, load the active catalog, allow the user to choose an activity ID, submit the occurrence, then refresh the day's [history](calendar-history-reports.md) or period report. POST is not idempotent; after a lost response, refresh the dated list before retrying. PATCH and DELETE can race with other edits; the response contains a version for display, but the request does not currently enforce a client-supplied version. DELETE removes only the occurrence and its tag links; the catalog entry and tags remain. All history and totals are live.
