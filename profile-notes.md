# Profile notes UI–API integration

Profile notes are titled vector drawings belonging to an adult or child profile. Multiple notes coexist. Existing saved profile scratchpads are copied to notes by migration V22, retaining document, ID, revision, and timestamps with title `Note`. Legacy scratchpad routes remain available but are independent after migration; new clients must use notes routes.

## Routes and authorization

Base: `/api/families/{familyId}/members/{memberId}/notes`. All IDs are UUIDs. Send `Authorization: Bearer <access token>`, `Accept: application/json`, optional `Accept-Language`, and JSON `Content-Type` on writes. The existing authentication refresh flow applies.

| Method | Path | Result |
| --- | --- | --- |
| GET | base | 200 paginated note collection |
| GET | base`/{noteId}` | 200 one note; 404 if missing |
| PUT | base`/{noteId}` | 200 created or replaced note |

Any persisted family membership may read. Only OWNER/ADMIN may create or replace. Authorization and active profile ownership are checked on every route, including child profiles. Foreign families return 404 `FAMILY_NOT_FOUND`; missing, inactive, or mismatched profiles return 404 `FAMILY_MEMBER_NOT_FOUND`. A missing note returns 404 `NOTE_NOT_FOUND`. Anonymous requests return 401 `UNAUTHENTICATED`; read-only members attempting writes receive 403 `FORBIDDEN`. Existing per-user authentication limits apply; no separate note rate limit is introduced.

## Request and response

PUT replaces the entire editable representation. Generate a fresh UUID for a new note and send revision 0. To edit, GET the note and send its current revision. Required request fields (all non-null): `title` string, trimmed and nonblank, at most 120 Java UTF-16 units; `formatVersion` integer 1; `revision` nonnegative integer; `strokes` array. Unknown and duplicate fields are rejected. Stroke and point validation and configured size limits match [profile scratchpads](profile-scratchpad.md): default 5 MiB request, 5000 strokes, 10000 points per stroke. Clearing strokes retains the note and title.

```json
{"title":"Call Bruno","formatVersion":1,"revision":0,"strokes":[]}
```

A response contains the note's non-null `id`, `title`, `formatVersion`, `revision`, `strokes`, `createdAt`, and `updatedAt`. Dates are UTC-compatible ISO-8601 instants. Ownership is derived from the route, never from request fields.

```json
{"id":"8e9bb12d-8296-45a4-9c91-d17ddae12be3","title":"Call Bruno","formatVersion":1,"revision":1,"strokes":[],"createdAt":"2026-10-20T23:00:00Z","updatedAt":"2026-10-20T23:00:00Z"}
```

GET collection accepts zero-based `page` (default 0, minimum 0) and `size` (default 20, 1–50). Returns `{"items":[...],"page":0,"size":20,"hasNext":false}`. Empty collection returns `items: []`. Ordering is `createdAt DESC, id DESC`. Each item includes its drawing for local previews. Offset pagination can shift when another device creates notes; de-duplicate IDs when appending or refresh from page 0. No total count is returned.

## Concurrency, retries, errors, and client flow

Writes lock the owning profile and compare the expected revision. A successful create has revision 1; every replacement increments it. Stale revisions or a UUID collision return 409 `DRAWING_VERSION_CONFLICT`. A retry must keep the same UUID: after a lost response, GET that note to reconcile before another write. Never generate another UUID merely to retry an uncertain create. A missing note with nonzero expected revision conflicts. No automatic merge is performed.

Malformed title, document, or unknown fields return 400 `DRAWING_INVALID_FORMAT`; unsupported formats return 400 `DRAWING_VERSION_UNSUPPORTED`; stroke/point failures return `DRAWING_INVALID_STROKE` / `DRAWING_INVALID_POINT`. Oversized requests return 413 `DRAWING_TOO_LARGE`, including chunked requests. Invalid pagination returns 400 `VALIDATION_ERROR` with field errors. Errors use `application/problem+json` and stable `code`; display localized detail but branch on code. Framework malformed path/query responses retain their normal 400 handling.

On profile selection, load page 0. Show horizontally scrollable cards with note title, local drawing preview, and profile name. “New note” asks for a title, creates an empty note using its own UUID, then opens the vector editor. Clicking an existing card opens that ID for editing. Autosave uses full replacements and handles conflicts through re-read. Returning refreshes the current profile's notes. Clear visible notes on profile/family changes and ignore responses from stale selections. Read-only members see cards without a create action. Titles can be changed through PUT; the current editor keeps the title selected at creation. There are no text-body, attachment, deletion, export, or live-collaboration endpoints.

Migration V22 adds a table and copies existing rows without modifying legacy data. Deploy backend before frontend. Recovery: roll back the frontend/backend code while retaining the additive table; never drop notes created after deployment. Existing scratchpad clients will not see changes written through the notes API.
