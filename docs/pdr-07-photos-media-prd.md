# PDR-07 — Photos and Media

Status: proposed.

## Outcome and boundaries

PDR-07 adds private image storage and child photo records. `Media` represents a
stored file independently from the feature that uses it. The initial supported
type is `IMAGE`; video, audio, documents, image editing, advanced thumbnails,
public sharing, comments, likes, reports, and social integrations are out of
scope. Store file bytes outside PostgreSQL and persist only metadata and a
server-generated storage key.

Photo records are dated entries for one child, with an optional description and
an ordered, bounded collection of images. Uploading creates a media asset; it
does not create a photo record. A later feature may use the same media storage
boundary through its own association model. Keep PDR-07 in `media/` and
`photorecord/` feature packages using responsibility-based subpackages. Use
UUIDs, explicit DTOs, Flyway, service transactions, existing family
authorization, ProblemDetail/localization, and PostgreSQL constraints.

## Domain rules

### Media

Each media row has `id`, `familyId`, `uploadedByUserId`, `type`, `storageKey`,
`originalFilename`, `mimeType`, `sizeBytes`, and `createdAt`. `type` is `IMAGE`
for this release. The authenticated user supplies the uploader identity; never
accept it from the request. Store normalized MIME type and actual byte count.
The original filename is optional display metadata only and must never be used
to build a storage path or returned as a trusted path.

Generate opaque storage keys on the server, scoped to the family and media ID.
Do not expose storage keys in API responses. Keep the filesystem root outside
the web root and repository, prevent path traversal, and never serve the
directory statically. `MediaStorage` is a small feature-local boundary for
storing, loading, and deleting content. The development implementation uses the
local filesystem; business rules must not depend on a storage vendor.

Only JPEG, PNG, and WebP images are accepted. Enforce the configured byte limit
before and during the copy (default 10 MiB), reject empty files, and validate
the file signature/content against the accepted image formats rather than
trusting extension or multipart `Content-Type`. Derive the response MIME type
from validated content. Do not add a general-purpose media processing library
unless implementation proves existing platform support insufficient. Use a
configurable private storage directory and document all new settings in
`.env.example`; do not commit uploaded files.

Every asset belongs to one family. Family membership is checked for upload,
listing, content reads, and deletion. A media ID alone grants no access.
Unknown and cross-family resources follow the established safe not-found
behavior. No public or permanent direct file URLs are returned.

An orphan is an active media row with no active association. For PDR-07, this
means no `photo_record_media` row; future consumers must include their active
association tables in the same reference check. The family media listing can
filter to unattached assets, making orphaned uploads discoverable. No cleanup
scheduler is included.

### Photo records

A photo record has `id`, `familyId`, `childId`, `date` (`LocalDate`), optional
`description`, `createdByUserId`, `createdAt`, and `updatedAt`. It owns one or
more `PhotoRecordMedia` links; each link stores `photoRecordId`, `mediaId`, and
zero-based `position`. For database-enforced family integrity, the association
also stores `familyId` as an internal integrity column; it is never accepted
from clients. The response returns media summaries in position order.

The family, child, photo record, and media must all belong to the same family.
Only `IMAGE` media can be attached. A media ID may be referenced by multiple
photo records; attaching an asset does not transfer ownership. Reject duplicate
media IDs within one record. Bound the record to 20 images so request and
response sizes remain predictable. Descriptions are optional, trimmed, and
limited to 2,000 characters; an empty description is stored as null.

Members with access to the family may create, read, update, and delete photo
records, subject to the existing family authorization rules. Do not invent a
separate photo role model. Obtain `createdByUserId` from the authenticated
identity. No record may be created without at least one image.

Updates replace the complete editable state (`date`, `description`, `mediaIds`)
and the supplied media ID order becomes the persisted order. Apply updates
atomically: if any child, record, or media validation fails, keep the existing
record and links unchanged. Add a version column only if optimistic locking is
used to protect concurrent replacements; if used, return the established
conflict response so the client reloads.

Deleting a photo record removes only its association rows and record. It does
not delete the media assets. Deleting media is allowed only when no active
feature association refers to it. Check references and association changes
under a consistent media-row lock so a concurrent attach cannot race a delete.
If storage deletion fails, retain the database row and return a safe upload or
storage failure; do not leave a successful API response pointing to a missing
file. The implementation must handle a database failure after object deletion
as a consistency limitation and document its recovery behavior; do not add a
distributed transaction or scheduler in this release.

## API proposal

All routes use the `/api` prefix and existing Bearer authentication. Resolve
the caller from the security context and check current family membership on
each operation, including content downloads and family-scoped listings. Verify
the complete family → child → record → media chain on nested photo-record
operations. Do not expose cross-family resource existence. Use the existing
ProblemDetail contract, `Accept-Language` localization (`en`, `pt`, `es`), and
stable machine-readable error codes. OpenAPI must describe multipart upload,
content responses, parameters, validation, and statuses.

| Method | Route | Access and behavior |
| --- | --- | --- |
| `POST` | `/families/{familyId}/media` | Any family member; multipart field `file`; validate, store, persist metadata, and return media metadata. |
| `GET` | `/families/{familyId}/media?unattached=false&page=0&size=20` | Any family member; paginated media metadata, optionally only unattached assets. |
| `GET` | `/families/{familyId}/media/{mediaId}/content` | Any family member; authorized private content with validated MIME type and safe inline disposition. |
| `DELETE` | `/families/{familyId}/media/{mediaId}` | Any family member; reject with `MEDIA_IN_USE` while referenced, otherwise remove metadata and stored bytes. |
| `POST` | `/families/{familyId}/children/{childId}/photo-records` | Any family member; create record with date, optional description, and ordered media IDs. |
| `GET` | `/families/{familyId}/children/{childId}/photo-records?from=&to=&page=0&size=20` | Any family member; paginated history in inclusive optional date bounds. |
| `GET` | `/families/{familyId}/children/{childId}/photo-records/{photoRecordId}` | Any family member; read one record with ordered media metadata. |
| `PUT` | `/families/{familyId}/children/{childId}/photo-records/{photoRecordId}` | Any family member; replace date, description, and ordered media IDs. |
| `DELETE` | `/families/{familyId}/children/{childId}/photo-records/{photoRecordId}` | Any family member; remove record and links, retain media assets. |

Media listing and photo history use the repository page shape (`items`, `page`,
`size`, `hasNext`), page 0 and size 20 by default, maximum size 100. Media is
ordered by `createdAt DESC, id DESC`. Photo records are ordered by
`date DESC, createdAt DESC, id DESC`. `from` and `to` are inclusive ISO
`yyyy-MM-dd` dates; each may be omitted, but inverted ranges return validation
error. Do not return total counts unless the existing page contract requires
them.

Successful upload and create return `201 Created`; reads and updates return
`200 OK`; deletes return `204 No Content`. Invalid multipart content or
oversized data returns a stable application error (`MEDIA_INVALID_TYPE` or
`MEDIA_TOO_LARGE`); request field errors use `VALIDATION_ERROR`. Missing or
cross-family resources use the existing safe 404 convention. Use
`MEDIA_NOT_FOUND`, `MEDIA_IN_USE`, `MEDIA_UPLOAD_FAILED`,
`PHOTO_RECORD_NOT_FOUND`, and `PHOTO_RECORD_INVALID_MEDIA` where appropriate.
Authentication and authorization retain the shared `UNAUTHENTICATED` and
`FORBIDDEN` codes. Translate messages in all supported locales; clients branch
on codes, never translated text.

Upload is not idempotent: a retry may create another media row, so the client
should retain the returned media ID and retry photo-record creation separately.
Photo-record `PUT` is replacement semantics and may be retried with the same
payload. Define and document behavior for interrupted content downloads and
storage errors in the integration guide. Do not hold a database transaction
open during file upload. If file storage succeeds but metadata persistence
fails, attempt best-effort file cleanup and log only safe identifiers and
failure details. Never log image contents, personal descriptions, or secrets.

The response for media includes `id`, `type`, `mimeType`, `sizeBytes`, and
`createdAt`, but not `storageKey`. A photo record response includes `id`,
`childId`, `date`, nullable `description`, ordered `media` entries (`id`,
`type`, `position`), and `createdAt`/`updatedAt`. Include realistic request and
response examples in the implementation integration document; do not serialize
JPA entities.

## Persistence and storage consistency

Add a versioned Flyway migration for `media`, `photo_records`, and
`photo_record_media`. Use UUID identifiers, non-null ownership and metadata,
positive size checks, a type check, unique `(photo_record_id, media_id)`,
unique `(photo_record_id, position)`, and indexes for family media creation
order, child/date history, and reverse media-reference lookup. Enforce the
same-family child and media relationships in PostgreSQL, not only with
pre-insert checks: add composite unique keys on `(id, family_id)` for the
referenced parent rows and composite foreign keys from `photo_records` to its
child and from `photo_record_media` to both its record and media using
`family_id`. Avoid cascading deletion of media when a photo record is deleted.

Keep transactions short and separate storage operations from database
transactions where possible. Store first, persist metadata second, and attempt
to delete the stored object if persistence fails. For deletion and attachment,
serialize operations through the media row and check references immediately
before removal. Local storage must use safe generated keys, stream bounded
content, create directories safely, and fail closed when a resolved path falls
outside the configured root. Storage providers remain private; only the
authorized content endpoint loads a resource.

## Verification and acceptance

Use PostgreSQL Testcontainers for migration, constraints, query ordering,
pagination, family integrity, and reference checks. Exercise storage with an
isolated temporary directory or a focused test implementation; tests must not
depend on external services.

Acceptance requires that:

1. Authorized family members can upload supported images, receive metadata,
   and read private content through an authorization-checked endpoint.
2. Invalid, empty, spoofed, and over-limit files are rejected; generated
   storage keys and private filesystem paths are not exposed.
3. Media metadata is persisted in PostgreSQL while bytes remain in external
   storage, with cleanup attempted after metadata persistence failure.
4. A child can have dated photo records with optional descriptions and up to 20
   ordered images; create, read, update, list, filter, paginate, and delete work.
5. Cross-family children and media, missing resources, and non-image media
   cannot be attached, and family isolation applies to every route.
6. Deleting a photo record keeps its media; deleting media in use is rejected,
   and unattached media can be identified through the family media list.
7. OpenAPI, the human-readable API integration document linked from README,
   Flyway, localized errors, and PostgreSQL-backed tests cover the implemented
   contract.

Implement with behavior-first tests and run `./mvnw verify` when the feature is
implemented. No scheduler, public URL, signed URL, video/audio support, or
advanced image processing is part of PDR-07.
