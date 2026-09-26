# PDR-10 — Profile Scratchpad

Status: proposed.

## Outcome and scope

PDR-10 gives each family member a persistent, editable freehand scratchpad on
their profile. The first release targets adult profiles in the UI. A person’s
scratchpad belongs to the `FamilyMember`, not to the authenticated account, so
it follows that profile across authorized devices. The storage model must not
prevent enabling the feature for children later.

The feature stores an editable vector document and synchronizes whole-document
updates through the backend. It covers simple handwritten notes and marks, not
a professional drawing application. It does not include real-time collaboration,
partial-stroke erasing, server rendering or previews, export/printing, or a
server-managed offline queue. Undo, redo, input capture, rendering, point
simplification, local caching, and autosave scheduling belong to Flutter.

## Core decisions

| Concern | Requirement |
| --- | --- |
| Ownership | One document per family member and drawing surface. The initial surface is `PROFILE_SCRATCHPAD`. |
| Representation | A platform-independent document of strokes and points; raster images are not the source of truth. |
| Coordinates | Normalize `x` and `y` to the logical drawing surface, inclusive range 0–1. Do not persist screen pixels. |
| Storage | Store the validated document as PostgreSQL JSONB; do not create per-point relational rows. |
| Persistence | Keep an empty document after clear. An empty read may be represented without creating a row; see API behavior below. |
| Editing | Clients replace the current document. A stroke eraser removes strokes; pixel erasing is out of scope. |
| Synchronization | Autosave sends a bounded whole-document update after local changes, never one request per pointer event. |
| Concurrency | Require the client’s current revision on update. Reject stale writes with a stable conflict error; do not silently overwrite. |
| Platform | The backend accepts the common format and has no Apple Pencil, PencilKit, S Pen, iOS, or Android-specific behavior. |

## Document format

The initial document format is version 1:

```json
{
  "formatVersion": 1,
  "strokes": [
    {
      "id": "8e9bb12d-8296-45a4-9c91-d17ddae12be3",
      "color": "#202124",
      "width": 3.0,
      "points": [
        { "x": 0.124, "y": 0.351 },
        { "x": 0.126, "y": 0.354, "pressure": 0.73 }
      ]
    }
  ]
}
```

A stroke is one continuous pointer contact. Each stroke has a UUID, color,
positive width, and an ordered list of points. Points contain normalized
coordinates; optional `pressure` is a normalized value from 0 to 1. Pressure
must be absent when the input device does not provide it. Future format versions
may add fields such as tilt or tool type, but version 1 does not require them.
Clients and server must reject unsupported format versions rather than
misinterpret them. The backend validates the known structure and must not treat
arbitrary JSON as a drawing document.

The frontend controls how width, color, and points are rendered. The backend
validates a supported color syntax and configured numeric/collection bounds;
exact palette, visual width units, smoothing, and simplification are client
concerns. Establish the version 1 color syntax and width range in the integration
contract before implementation.

## Persistence and limits

Create a `drawings` table with UUID ID, `family_id`, `member_id`, `surface`,
`format_version`, JSONB document data, monotonically increasing `revision`, and
creation/update timestamps. Add foreign keys, an index supporting authorized
member/surface lookup, and a unique constraint on
`(family_id, member_id, surface)`. Use a Flyway migration. A drawing is not a
Media object; media may later hold a generated preview or export, outside this
release.

Validate at least supported format version, point coordinate and pressure
ranges, stroke IDs, color syntax, width bounds, maximum strokes, maximum points
per stroke, and maximum serialized document size. Reject unknown or malformed
document fields. Keep limits configurable under `app.drawings` and document the
chosen defaults and resulting request-size limit in the integration guide.
Reasonable initial ceilings to evaluate during implementation are 5,000 strokes,
10,000 points per stroke, and 5 MiB per document; these are proposed safeguards,
not fixed product decisions. Return field validation through the established
ProblemDetail and localization conventions (`en`, `pt`, `es`).

## API behavior

Use REST/JSON routes under `/api`, following existing family-member conventions:

| Method and route | Behavior |
| --- | --- |
| `GET /families/{familyId}/members/{memberId}/drawings/profile-scratchpad` | Return the saved document, or a virtual empty version-1 document at revision 0 when none exists. A read must not create a row. |
| `PUT /families/{familyId}/members/{memberId}/drawings/profile-scratchpad` | Create or replace the complete document, subject to validation and revision checks. Return the saved representation and incremented revision. |

The PUT body contains `formatVersion`, the client’s `revision`, and `strokes`.
For a document not yet persisted, the expected revision is 0; a successful first
write creates revision 1. For an existing document, the submitted revision must
match the stored revision. The update and revision increment must be atomic so
concurrent writes cannot both succeed against the same revision. A clear is a
PUT with an empty strokes list, preserving the document and advancing revision.
Define concrete status codes, DTO examples, content limits, and error payloads
in the integration document and OpenAPI when implemented.

The caller must be authenticated and authorized for the family. Validate that
the member belongs to the path family and enforce the existing family access
policy for reading and editing. A known family, member, or drawing UUID does not
grant access. Follow the existing safe not-found behavior for inaccessible
resources. Adult-only visibility is an initial product/UI rule, not a permanent
database restriction; do not invent an adult classification or new role policy.

Use stable application errors where existing errors do not suffice, including
unsupported format, invalid document/stroke/point, excessive document size, and
revision conflict. Candidate codes are `DRAWING_VERSION_UNSUPPORTED`,
`DRAWING_INVALID_FORMAT`, `DRAWING_INVALID_STROKE`, `DRAWING_INVALID_POINT`,
`DRAWING_TOO_LARGE`, and `DRAWING_VERSION_CONFLICT`. Reuse the established
validation and authorization codes where applicable.

## Client flow and responsibilities

Flutter captures stylus, touch, or mouse input when available; normalizes
coordinates against the logical scratchpad surface; renders strokes; and edits
local state immediately. It may simplify points before saving. After a
user-experience-tested debounce (initially around 1–2 seconds), it sends the
whole document with its current revision. It retains local changes while a save
is pending and handles a revision conflict by reloading and presenting or
applying a deliberate resolution; it must not silently discard either version.
Offline storage and retry scheduling are client-owned. The revision check allows
offline-capable clients to detect stale state when they reconnect, but does not
merge changes automatically.

The backend owns persistence, family/member authorization, structural and size
validation, format version support, and atomic concurrency control. It does not
capture input, render documents, or store per-point history. The frontend owns
stroke erasing, undo, redo, palette and tool UI, autosave timing, and local/offline
state. Clearing the page may prompt for confirmation in the UI.

## Documentation and verification for implementation

Implementation must add a human-readable UI/API integration guide under `docs/`
and link it from the README. Document authentication, ownership, routes, headers,
request/response schemas, nullability, limits, status/error codes, revision
handling, safe retries, empty reads, clear behavior, and a client synchronization
flow. Include the document and nested DTOs in OpenAPI. Use typed DTOs even though
the persistence column is JSONB.

Use unit and PostgreSQL/Testcontainers integration tests for empty reads, first
write, load, replace, multiple strokes, stroke removal, clear, field and size
validation, supported/unsupported versions, revision increments, stale and
concurrent updates, family/member authorization and isolation, and independent
member scratchpads. Follow the testing guide’s TDD cycle and run `./mvnw verify`
for the completed behavior change.

## Acceptance criteria

PDR-10 is complete when an authorized family member has an independent
scratchpad that clients can load, edit, clear, and synchronize across devices;
the backend persists validated, editable vector strokes with normalized
coordinates and optional pressure; updates detect stale revisions atomically;
family and member ownership are enforced; and migrations, API documentation,
OpenAPI, localization, and tests cover the implemented contract. The format and
backend have no platform-specific drawing dependency, and a clear operation
retains an empty versioned document.
