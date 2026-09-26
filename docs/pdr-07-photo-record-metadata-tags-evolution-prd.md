# PDR-07 Evolution — Photo Record Metadata, Tags, and Discovery

Status: proposed.

## Purpose

Extend the child photo record feature so families can classify and find records
by their own date, optional description, and reusable family tags. The resulting
records can later be considered when preparing reports. This proposal does not
define report selection or change how image files are stored.

This document extends [PDR-07 — Photos and Media](pdr-07-photos-media-prd.md)
and adopts the family-wide tag model in [PDR-11 — Global Family Tags](pdr-11-global-tags-prd.md).
The existing [Photos and Media API guide](photos-media.md) remains the contract
for media upload, private content access, authorization, pagination, and current
photo-record routes. Any implementation that changes that contract must update
that guide and OpenAPI in the same change.

## Scope and ownership

`Media` remains generic stored-file infrastructure. It contains storage and
ownership metadata such as `id`, `familyId`, `uploadedByUserId`, `type`,
`storageKey`, `originalFilename`, `mimeType`, `sizeBytes`, and `createdAt`.
Photo-specific date, description, and tags belong to `PhotoRecord`; never add
them to `Media`. A media asset may later be referenced by another feature, and
deleting a photo record must not delete the underlying asset.

The existing `PhotoRecord` owns its date, optional description, creator,
timestamps, and ordered `PhotoRecordMedia` associations. This evolution adds an
optional many-to-many association between `PhotoRecord` and the existing global
family `Tag`. It does not add `PhotoTag`, `PhotoCategory`, or a photo-specific
tag vocabulary. A family tag may be reused by any other supported feature with
its own explicit association table.

Out of scope: changing media storage, video or audio, public links, image
processing, automatic report inclusion, report selection, global search across
features, and report-generation behavior.

## Photo record behavior

Each record represents an event on a child-specific calendar date. `date` is a
required `LocalDate` serialized as ISO `yyyy-MM-dd`; it is the date the record
represents and may be earlier than the upload or record creation date. Do not
derive it from `Media.createdAt`.

`description` is optional and nullable. Trim surrounding whitespace, store a
blank value as null, and retain the existing 2,000-character limit unless a
separately approved configuration requirement changes it. Editing date,
description, or tags must not require uploading or replacing images.

A record has one or more image associations, ordered by zero-based `position`,
and no more than the existing limit of 20. Preserve the order supplied on create
or replacement. The record lifecycle must also support image-level changes
(add, remove, replace, and reorder) without recreating the record. Define the
request semantics explicitly when implementing image-level operations; do not
silently change the existing full-replacement `PUT` contract. Reject duplicate
media IDs in one record. Media remains reusable by multiple records where
permitted by the existing media rules.

The record may have zero or more family tags. Tags are classification and
discovery aids only. A `Leitura` tag does not imply that a photo is selected for
a report, and a report flow must allow explicit selection of an image regardless
of its tags.

## Tag association and family integrity

Use the shared `Tag` model (`id`, `familyId`, `name`, optional `color`,
`createdAt`, `updatedAt`) and create a dedicated `photo_record_tags` association
table with real foreign keys to `photo_records` and `tags`. Enforce uniqueness
for each `(photo_record_id, tag_id)` pair. Where the existing schema permits,
use family-aware composite foreign keys so the database also prevents a
cross-family link. Keep service-level family-scoped validation for useful,
safe API errors.

All tags assigned to a record must belong to the record's family. Validate all
requested tag IDs as one set before changing associations; if any ID is missing,
duplicated, or outside the family, reject the whole mutation and preserve the
previous state. Follow PDR-11 for tag normalization, naming, lifecycle, and
family permissions. Removing a tag removes only its association rows and never
deletes photo records or media. Removing a photo record removes its own tag and
media association rows while retaining media files and metadata.

## API requirements

Use the existing nested resource:

```text
/api/families/{familyId}/children/{childId}/photo-records
```

The create request accepts required `date`, optional nullable `description`,
required ordered `mediaIds`, and optional `tagIds`. The record response includes
`id`, `childId`, `date`, nullable `description`, `tags`, ordered `media`, and
creation/update timestamps. Each tag summary contains `id`, `name`, and nullable
`color`; each media summary contains `id`, `type`, and `position`. Do not expose
JPA entities or storage keys.

Metadata and tag updates must not require media IDs to be resent. Define
omitted-versus-empty semantics explicitly for tag updates, consistent with the
established global-tag integration: on partial updates omission preserves
current tags and an empty array clears them. Preserve the current full-replacement
semantics of `PUT` for fields already in that contract; add a clearly defined
metadata update method or request shape if necessary instead of making omission
ambiguous.

Extend the record collection query with these optional parameters:

| Parameter | Meaning |
| --- | --- |
| `date` | One exact record date. |
| `from` | Inclusive lower date bound. |
| `to` | Inclusive upper date bound. |
| `query` | Case-insensitive substring search in description. |
| repeated `tagIds` | Family tag IDs that must all be assigned (AND). |
| `page`, `size` | Existing bounded pagination contract. |

`date` cannot be combined with `from` or `to`; reject an invalid combination
with the established validation error. `from` and `to` are inclusive and may be
used individually or together; reject `from > to`. An empty or omitted `query`
does not restrict results. Define a maximum query length in the API contract
using the project's established bounded-input conventions. Repeated tag IDs
follow PDR-11: every requested tag must be attached, duplicate IDs are invalid,
and unknown or foreign-family tags use safe not-found behavior. Combine all
active filters using AND before pagination.

Keep existing page response shape, size bounds, and deterministic order from the
photo integration guide. If query semantics or mutation routes require API
changes, document exact routes, method, authorization, headers, request and
response examples, nullability, validation, status/error codes, retry behavior,
and client flow in `docs/photos-media.md`, then update OpenAPI. Document that
date is the event date, tags are family-wide classifications, multiple tag
filters use AND, and tags do not select report photos.

## Authorization and errors

Every request requires an authenticated user with access to the path family.
Validate the family → child → photo record relationship and ensure every media
and tag belongs to that same family. Apply existing family-member permissions;
do not invent a photo-specific role. Scope list filters before pagination, and
do not reveal whether a foreign-family child, record, media item, or tag exists.

Use the established `ProblemDetail`, stable error codes, and `LocalizationService`
with English, Portuguese, and Spanish messages. Reuse `VALIDATION_ERROR`,
`PHOTO_RECORD_NOT_FOUND`, and the existing safe family/media errors where they
fit. Add a specific stable error only when clients need to distinguish a new
domain failure; a proposed tag-family mismatch code is
`PHOTO_RECORD_TAG_FAMILY_MISMATCH`. Do not return internal SQL or ownership
details.

## Persistence and query behavior

Add a new versioned Flyway migration; never edit an applied migration. Create
`photo_record_tags` with foreign keys, duplicate prevention, and indexes that
support lookups by both `photo_record_id` and `tag_id`. Ensure the record history
query has an index suitable for child plus date ordering, reviewing existing
indexes before adding duplicates. Preserve `photo_record_media` ordering and
existing family constraints. Do not store tag names or colors in association
rows.

Implement filtering in the photo-record repository so all filters apply before
pagination and records are not duplicated by joins. Use `EXISTS`, grouped
matching counts, or an equivalent query that correctly implements AND across
tag IDs. Load tag and media summaries for a page without N+1 queries or
collection-fetch pagination. Map DTOs within the appropriate persistence scope.
Keep association replacement atomic within the service transaction.

## Future report selection boundary

This evolution prepares discovery only. It does not implement report selection.
If report composition later needs an individual image, represent selection as
an explicit report-owned association to `mediaId` with the applicable family,
child, period/year, section, and position constraints. A selected media item
may have been discovered through tags, but tags do not enforce eligibility and
must not become a report-selection relationship. Do not duplicate media bytes
or make `Media` depend on reports.

## Verification and acceptance

Use behavior-first tests and PostgreSQL Testcontainers for migrations, foreign
keys, duplicate constraints, query semantics, and pagination. Cover:

1. Creating a dated record with and without a description, including a past date.
2. One or multiple images, preserved order, and image add/remove/replace/reorder
   while keeping the record ID stable.
3. Assigning one and multiple existing family tags; removing tags without
   changing media or deleting the record.
4. Rejecting cross-family tags and media atomically, including duplicate IDs.
5. Exact date, inclusive period, description query, one-tag filter, multi-tag
   AND filter, and combinations of date bounds, query, tags, and pagination.
6. Updating date, description, or tags without changing media associations.
7. Deleting a tag while preserving records and deleting a record while
   preserving media assets.
8. Authorized family access and safe handling of foreign-family IDs.
9. Responses containing tag and ordered media summaries without N+1 queries.

The evolution is complete when photo records can be created and edited with an
independent event date and optional description; images remain ordered and
editable without record recreation; family tags can be attached safely; clients
can combine date, description, and AND-style tag filters; report discovery does
not imply selection; and the migration, API guide, OpenAPI, localization, and
PostgreSQL-backed tests describe and protect the implemented behavior.
