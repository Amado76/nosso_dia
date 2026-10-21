# Profile notes UI–API integration

Profile notes are titled vector drawings belonging to an adult or child profile. Multiple notes coexist. Existing saved profile scratchpads are copied to notes by migration V22, retaining document, ID, revision, and timestamps with title `Note`. Legacy scratchpad routes remain available but are independent after migration; new clients must use notes routes.

## Routes and authorization

Base: `/api/families/{familyId}/members/{memberId}/notes`. All IDs are UUIDs. Send `Authorization: Bearer <access token>`, `Accept: application/json`, optional `Accept-Language`, and JSON `Content-Type` on writes. The existing authentication refresh flow applies.

| Method | Path | Result |
| --- | --- | --- |
| GET | base | 200 paginated note collection |
| GET | base`/{noteId}` | 200 one note; 404 if missing |
| PUT | base`/{noteId}` | 200 created or replaced note |
| DELETE | base`/{noteId}?revision={revision}` | 204 deleted or already absent; empty body |

Any persisted family membership may read. Only OWNER/ADMIN may create, replace, or delete. Authorization and active profile ownership are checked on every route, including child profiles. Foreign families return 404 `FAMILY_NOT_FOUND`; missing, inactive, or mismatched profiles return 404 `FAMILY_MEMBER_NOT_FOUND`. A missing note returns 404 `NOTE_NOT_FOUND`. Anonymous requests return 401 `UNAUTHENTICATED`; read-only members attempting writes receive 403 `FORBIDDEN`. Existing per-user authentication limits apply; no separate note rate limit is introduced.

## Request and response

PUT replaces the entire editable representation. Generate a fresh UUID for a new note and send revision 0. To edit, GET the note and send its current revision. Optional `title` is a string of at most 120 Java UTF-16 units after trimming; omitted, null, blank, or empty values are stored and returned as an empty string. Required non-null request fields: `formatVersion` integer 1; `revision` nonnegative integer; `strokes` array. Unknown and duplicate fields are rejected. Stroke and point validation and configured size limits match [profile scratchpads](profile-scratchpad.md): default 5 MiB request, 5000 strokes, 10000 points per stroke. Clearing strokes retains the note and title.

```json
{"title":"Call Bruno","formatVersion":1,"revision":0,"strokes":[]}
```

A response contains the note's non-null `id`, `title`, `formatVersion`, `revision`, `strokes`, `createdAt`, and `updatedAt`. Dates are UTC-compatible ISO-8601 instants. Ownership is derived from the route, never from request fields.

```json
{"id":"8e9bb12d-8296-45a4-9c91-d17ddae12be3","title":"Call Bruno","formatVersion":1,"revision":1,"strokes":[],"createdAt":"2026-10-20T23:00:00Z","updatedAt":"2026-10-20T23:00:00Z"}
```

GET collection accepts zero-based `page` (default 0, minimum 0) and `size` (default 20, 1–50). Returns `{"items":[...],"page":0,"size":20,"hasNext":false,"total":1}`. Empty collection returns `items: []`. Ordering is `createdAt DESC, id DESC`. Each item includes its drawing for local previews. Offset pagination can shift when another device creates notes; de-duplicate IDs when appending or refresh from page 0. `total` is the total number of active notes for the authorized profile across all pages (0 for an empty collection). It is computed by a scoped count query and decreases after deletion. Show it in the board footer; do not infer the total from the loaded page length.

## Concurrency, retries, errors, and client flow

Writes lock the owning profile and compare the expected revision. A successful create has revision 1; every replacement increments it. Stale revisions or a UUID collision return 409 `DRAWING_VERSION_CONFLICT`. A retry must keep the same UUID: after a lost response, GET that note to reconcile before another write. Never generate another UUID merely to retry an uncertain create. A missing note with nonzero expected revision conflicts. No automatic merge is performed.

DELETE requires query parameter `revision` (integer, minimum 1), using the revision last read or saved. It locks the profile and rejects a changed note with 409 `DRAWING_VERSION_CONFLICT`; a missing note returns 204, so retry the same DELETE safely after an uncertain response. Invalid or missing revisions return 400. Reads after deletion return 404 `NOTE_NOT_FOUND`; other notes are unaffected. A pending PUT with nonzero revision cannot recreate a deleted note. UUIDs must not be reused for new notes. No request body is sent.

Malformed title, document, or unknown fields return 400 `DRAWING_INVALID_FORMAT`; unsupported formats return 400 `DRAWING_VERSION_UNSUPPORTED`; stroke/point failures return `DRAWING_INVALID_STROKE` / `DRAWING_INVALID_POINT`. Oversized requests return 413 `DRAWING_TOO_LARGE`, including chunked requests. Invalid pagination returns 400 `VALIDATION_ERROR` with field errors. Errors use `application/problem+json` and stable `code`; display localized detail but branch on code. Framework malformed path/query responses retain their normal 400 handling.

On profile selection, load page 0. Show horizontally scrollable cards with note title, local drawing preview, and profile name. “New note” creates an untitled empty note using its own UUID and opens the vector editor directly. A small optional title field in the editor shares the drawing autosave and revision handling; it opens no additional dialog and does not focus automatically. Clicking an existing card opens that ID for editing. Autosave uses full replacements and handles conflicts through re-read. Returning refreshes the current profile's notes. The editor exposes a small delete icon to editors, confirms removal, pauses autosave while deleting, and cancels pending autosave permanently after success. It returns to the refreshed list only after a successful DELETE; errors keep the editor open for retry. On a version conflict, re-read and resolve the newer version before deleting again. Clear visible notes on profile/family changes and ignore responses from stale selections. Read-only members see cards without a create action. Titles can be added, changed, or removed inline in the editor through PUT. Title-only changes mark the note dirty and use the same conflict checks as drawing changes. Cards omit the heading when the title is empty. There are no text-body, attachment, export, or live-collaboration endpoints.

Migration V22 adds a table and copies existing rows without modifying legacy data. Migration V23 permits empty titles without changing existing titles or drawings. Deploy backend before frontend. Recovery: roll back the frontend/backend code while retaining the additive table; never drop notes created after deployment. Existing scratchpad clients will not see changes written through the notes API.
