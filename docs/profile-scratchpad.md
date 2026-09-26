# Profile scratchpad UI–API integration

BeeHome stores one editable vector scratchpad for each family member. The first UI release targets adult profiles, but the API and database also support children. The document belongs to the family member, so authorized devices see the same saved drawing. The backend stores complete documents; Flutter handles input, rendering, undo, stroke erasing, local caching, and autosave timing. There are no previews, exports, per-point updates, server offline queue, or live collaboration.

## Access and routes

Base URL: `/api`. Both routes require `Authorization: Bearer <access-token>` and accept optional `Accept-Language: en`, `pt`, or `es` (English default). PUT additionally requires `Content-Type: application/json`.

| Method | Path | Access | Success |
| --- | --- | --- | --- |
| GET | `/families/{familyId}/members/{memberId}/drawings/profile-scratchpad` | Any persisted member of the family | `200` drawing |
| PUT | Same path | Family `OWNER` or `ADMIN` | `200` saved drawing |

`familyId` and `memberId` are UUIDs. The member must belong to the path family; inactive profiles can still be read and edited. A nonmember receives a safe `404 FAMILY_NOT_FOUND`; a missing member or one belonging to a different family receives `404 FAMILY_MEMBER_NOT_FOUND`. A family `MEMBER` can read but receives `403 FORBIDDEN` on PUT. Anonymous requests receive `401 UNAUTHENTICATED`. A profile's account link does not change these role rules.

## Document contract

PUT replaces the *entire* current document. All top-level and nested fields shown below are required except `pressure`, which must be omitted when the device does not provide it. Null values, unknown or duplicate JSON fields, malformed UUIDs, duplicate stroke IDs, empty point lists, and unsupported versions are rejected. A stroke is one continuous pointer contact. Stroke order and point order are preserved. No color or coordinate normalization is applied by the server.

```json
{
  "formatVersion": 1,
  "revision": 0,
  "strokes": [
    {
      "id": "8e9bb12d-8296-45a4-9c91-d17ddae12be3",
      "color": "#202124",
      "width": 3.0,
      "points": [
        {"x": 0.124, "y": 0.351},
        {"x": 0.126, "y": 0.354, "pressure": 0.73}
      ]
    }
  ]
}
```

| Field | Type and limits |
| --- | --- |
| `formatVersion` | Integer, exactly `1`. Future versions require an API update. |
| `revision` | Integer from `0` through the signed 64-bit maximum. Must equal the last received revision. |
| `strokes` | Array, 0–5,000 items by default. Empty array clears the document. |
| `strokes[].id` | Canonical UUID string, unique within the document. |
| `strokes[].color` | Six-digit `#RRGGBB` hexadecimal string, either letter case. |
| `strokes[].width` | JSON number greater than `0` and at most `100`; visual units are chosen by the client. |
| `strokes[].points` | Ordered array of 1–10,000 points by default. |
| `x`, `y` | JSON numbers from `0` through `1`, inclusive, relative to the logical drawing surface. Never send screen pixels. |
| `pressure` | Optional JSON number from `0` through `1`, inclusive; omit rather than send null when absent. |

The default maximum raw PUT body and serialized stored document are each **5,242,880 bytes (5 MiB)**. The raw limit includes whitespace and JSON syntax. The configured ceilings are `DRAWINGS_MAX_STROKES=5000`, `DRAWINGS_MAX_POINTS_PER_STROKE=10000`, and `DRAWINGS_MAX_DOCUMENT_BYTES=5242880`; they map to `app.drawings.*` in `application.properties` and are listed in `.env.example`. Keep client payloads below all limits. The HTTP body is held in memory for validation, so clients should debounce and simplify strokes before sending.

## Read and save responses

Both routes return `application/json` with `familyId`, `memberId`, `surface` (`PROFILE_SCRATCHPAD`), `formatVersion`, `revision`, `strokes`, `createdAt`, and `updatedAt`. The timestamps are UTC-compatible ISO 8601 instants. No drawing ID is exposed because the route identifies the only document for the member and surface.

Before any write, GET returns a virtual empty document. It creates no database row:

```json
{
  "familyId": "242110d5-0957-464f-8924-41f8d7430f90",
  "memberId": "bce0f321-bdfe-49cc-b19b-775f576fb141",
  "surface": "PROFILE_SCRATCHPAD",
  "formatVersion": 1,
  "revision": 0,
  "strokes": [],
  "createdAt": null,
  "updatedAt": null
}
```

A successful first PUT with revision `0` creates revision `1`. Each later successful PUT advances the revision by one, even if the strokes are identical. Clearing uses `"strokes": []`, keeps the stored document, and advances the revision. Saved responses have non-null timestamps and include the complete saved strokes. Reads do not change the revision or timestamps.

## Errors and synchronization

Application errors use `application/problem+json` with localized `title`/`detail` and a stable `code`. For example, a stale PUT returns:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Drawing changed concurrently. Reload before retrying.",
  "instance": "/api/families/242110d5-0957-464f-8924-41f8d7430f90/members/bce0f321-bdfe-49cc-b19b-775f576fb141/drawings/profile-scratchpad",
  "code": "DRAWING_VERSION_CONFLICT"
}
```

| Status | Stable code | Meaning |
| --- | --- | --- |
| 400 | `DRAWING_VERSION_UNSUPPORTED` | Integral `formatVersion` other than 1. |
| 400 | `DRAWING_INVALID_FORMAT` | Missing/unknown top-level fields, invalid revision, malformed JSON, or invalid strokes container. |
| 400 | `DRAWING_INVALID_STROKE` | Invalid stroke fields, ID, color, width, duplicate ID, or empty points. |
| 400 | `DRAWING_INVALID_POINT` | Invalid point fields, coordinates, or pressure. |
| 409 | `DRAWING_VERSION_CONFLICT` | Submitted revision is stale or cannot be incremented. |
| 413 | `DRAWING_TOO_LARGE` | Raw body, stored document, stroke count, or point count exceeds a configured ceiling. |
| 401/403/404 | `UNAUTHENTICATED`, `FORBIDDEN`, `FAMILY_NOT_FOUND`, `FAMILY_MEMBER_NOT_FOUND` | Access or path ownership failure. |

Unsupported media types and invalid URL UUIDs can produce Spring framework errors; clients should not rely on an application `code` for those. The drawing routes have no pagination, filtering, sorting, uploads, or drawing-specific rate limit.

Client flow: GET on opening the profile, keep the returned revision alongside the local document, and edit local strokes immediately. After a suitable debounce (initially around 1–2 seconds), PUT the complete document with that revision. Keep newer unsent edits locally while the request is pending. On `200`, adopt the returned revision and document, then send any later local edits using the new revision. On `409`, GET the server version and let the user or client apply a deliberate resolution; never silently discard either version. If a response is lost, retrying the same PUT may return `409` because the first request succeeded. GET and compare the saved document before deciding whether another write is needed. Offline storage and retry scheduling belong to the client.
