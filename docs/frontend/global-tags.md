# Global family tags — UI–API integration

BeeHome tags are editable labels shared by books, study sessions, routine items, and daily plan items. They classify those resources without changing their state or ownership. Base URL: `/api/families/{familyId}/tags`. No extra environment variables are required. Clients send `Authorization: Bearer <access-token>` and `Accept: application/json`; writes send `Content-Type: application/json`. `Accept-Language` supports `en`, `pt`, and `es` for errors.

## Authorization and routes

Every route requires current membership in the path family. Any member may read tags. Only OWNER or ADMIN may create, patch, and delete them. Inaccessible families return safe 404 `FAMILY_NOT_FOUND`; a tag outside the path family returns 404 `TAG_NOT_FOUND`. Members may assign tags only when the owning resource API permits that resource mutation. See [books](books-reading.md), [study](study-tracking.md), and [planning](planning.md).

| Method | Route | Success |
| --- | --- | --- |
| POST | `/api/families/{familyId}/tags` | 201, tag body and relative `Location` header |
| GET | `/api/families/{familyId}/tags` | 200, page of tags |
| GET | `/api/families/{familyId}/tags/{tagId}` | 200, tag body |
| PATCH | `/api/families/{familyId}/tags/{tagId}` | 200, updated tag body |
| DELETE | `/api/families/{familyId}/tags/{tagId}` | 204, empty body |

`familyId` and `tagId` are UUIDs. GET list accepts optional `query` (up to 120 UTF-16 code units), `page` (zero based, default 0), and `size` (default 20, 1–100). The offset must fit a signed 32-bit integer. Query matches a case-insensitive substring of normalized names; an empty query matches all. Tags sort by normalized name ascending, then ID ascending. Response: `{"items":[<tag>],"page":0,"size":20,"hasNext":false}`. There is no total count. Offset pages can shift under concurrent changes.

## Fields and examples

A tag has required UUID `id`, UUID `familyId`, string `name`, UTC `createdAt` and `updatedAt`, and nullable string `color`. IDs, ownership, and timestamps are server controlled. POST requires a non-null string `name`; `color` may be omitted or null. PATCH accepts a nonempty subset of `name` and `color`: omitted fields stay unchanged, `color:null` clears color, and `name:null` is invalid. Unknown fields and wrong JSON types return 400.

Names are stripped at the ends, normalized to Unicode NFC for display, must be nonblank, and are at most 120 UTF-16 code units. The normalized comparison form must also fit 120 code units. Internal capitalization and accents are retained. Uniqueness uses the NFC display spelling lowercased with Java `Locale.ROOT` and normalized to NFC again; the database has a unique constraint on `(family_id, normalized_name)`, which protects concurrent writes through the application. Color must be `#RRGGBB` with six hexadecimal digits; its letter case is retained.

POST request: `{"name":" Nature ","color":"#33AA66"}`. Response:

```json
{"id":"18442193-40ce-4816-a62d-ed1a7c5ea0dc","familyId":"361c38e3-6eb0-4ea8-a2dd-3105999cf102","name":"Nature","color":"#33AA66","createdAt":"2026-09-25T12:00:00Z","updatedAt":"2026-09-25T12:00:00Z"}
```

PATCH examples: `{"name":"Outdoors"}`, `{"color":null}`, or both. A normalized duplicate name within one family returns 409 `TAG_NAME_CONFLICT`; another family may use the same name. Renaming or recoloring a tag updates the one tag row, so attached resources show the new display values when fetched again.

## Resource assignments

Supported create requests accept optional `tagIds`, an array of at most 100 unique UUIDs. Omission or `[]` creates no links. Book PUT clears current links when `tagIds` is omitted or `[]` is supplied. Resource PATCH preserves current links when `tagIds` is omitted; `[]` clears them. A null array, null element, duplicate ID, invalid UUID, or more than 100 IDs returns 400 `VALIDATION_ERROR`. A missing or foreign-family tag returns 404 `TAG_NOT_FOUND`. The entire resource mutation rolls back when tag validation fails. Responses expose `tags` as an array of `{id,name,color}` objects, sorted by name then ID; `color` may be null. Clients may use IDs from GET list and should refresh resource responses after a tag rename, recolor, or deletion.

Book list and study history support repeated `tagIds` query parameters, for example `?tagIds=<uuid-1>&tagIds=<uuid-2>`. Both tags must be assigned. Tag filters combine with existing filters using AND and are applied before pagination. Empty filter means no tag restriction. Duplicate IDs return 400; malformed query UUIDs receive a framework 400 response without a promised application code; unknown or foreign-family IDs return 404. Routine and daily item collections have no tag filter because their current APIs have no item search contract. The resolved daily plan includes tag details for both item sources. Daily execution history items are not tagged.

Deleting a tag removes its links from all supported resources and preserves the resources. The first DELETE returns 204; a repeated DELETE returns 404. No automatic starter tags are seeded. Clients may suggest labels and create the selected ones with POST.

## Errors, retries, and UI flow

Errors use `application/problem+json` with stable `code`, localized `detail`, and framework status. Expect 400 `VALIDATION_ERROR` for invalid JSON fields, names, colors, arrays, filters or pagination; 401 `UNAUTHENTICATED`; 403 `FORBIDDEN` for an unauthorized write; 404 `FAMILY_NOT_FOUND` or `TAG_NOT_FOUND`; 409 `TAG_NAME_CONFLICT`; and 500 `INTERNAL_SERVER_ERROR` for unexpected failures. The UI should branch on `code`, not translated text. Authentication token renewal follows [authentication](authentication.md).

A UI can load the tag page, create a label if needed, then submit selected IDs with a resource request. After an ambiguous POST timeout, reload tags before retrying because POST has no idempotency key. PATCH is a last committed write wins update; reload after an uncertain response. GET is safe to retry. DELETE may be retried, treating a subsequent 404 as already removed only after reconciling the first attempt. There are no ETags or tag specific rate limits; general authentication limits still apply.

Tags have dedicated foreign keyed join tables for books, study sessions, routine items, and daily plan items. Each join row is constrained to one family and unique per resource/tag pair. Existing daily plan items receive their family ID during migration V15. No global cross-domain search or tag report is exposed. If reporting by tag is added later, overlapping tag buckets must not be summed into a global total.
