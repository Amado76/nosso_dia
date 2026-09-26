# PDR-11 — Global Family Tags

Status: proposed.

## Outcome and scope

PDR-11 introduces user-defined tags shared across a family. A tag is a
classification label that can be attached to records in multiple features;
each feature remains responsible for its own data, lifecycle, authorization,
and business rules. The same family tag can classify a book, study session,
planned task, or extracurricular activity. Tags do not change the resource's
domain type and must not replace fields that already represent state, time,
ownership, or other business facts.

Tags are free-form records, not enums or per-feature vocabularies. Families can
create, rename, recolor, and delete their tags without a code change or deploy.
Future features can adopt tags by adding their own explicit association table
and integration; the tag model itself remains independent of those features.

Initial integration targets are the existing book catalog (`Book`), study
records (`StudySession`), and the planning records that represent user-created
tasks. Before implementation, confirm whether `RoutineItem`, `DailyPlanItem`,
or both are the correct task records to tag. Do not tag `DailyExecutionItem`
automatically: it is an execution/history record, and tagging it requires an
explicit product decision. There is no current `Plan` or extracurricular
activity entity in the codebase. Add those integrations only when their domain
features exist, using their actual entity names. Do not invent entities to
satisfy this PDR. Study subjects (`StudySubject`) remain subject configuration,
not study records; tags should not replace them.

This PDR defines domain and persistence requirements, not an implemented API
contract. Implementation must add a README-linked UI–API integration guide and
matching OpenAPI documentation for every new or changed endpoint.

## Domain model and rules

### Tag

A `Tag` has UUID `id`, UUID `familyId`, required `name`, optional `color`, and
UTC `createdAt` and `updatedAt` timestamps. Its family is immutable. Names are
trimmed and nonblank, preserve the user's capitalization and accents, and have
a documented API length limit. Color is optional and, when present, must match
`#RRGGBB`.

Names are unique within a family after locale-independent normalization:
trim, normalize to Unicode NFC, then compare case-insensitively. Preserve the
original normalized display spelling chosen by the user. Enforce uniqueness in
the database so concurrent requests cannot create equivalent tags. Renaming a
tag changes only the tag row; associations use its ID and remain intact.

Tags belong to exactly one family. Members with access to that family may use
its tags on resources they are authorized to access. A resource and every tag
assigned to it must belong to the same family. Never rely on UUID secrecy or
client-side checks for this rule.

### Tag associations

Each supported domain uses a dedicated many-to-many join table with real
foreign keys, for example `book_tags`, `study_session_tags`, and the join table
for the chosen planning item entity. Future domains add their own association
table when implemented. Do not use JSON/JSONB arrays of tag names or a
polymorphic `(entity_type, entity_id)` assignment table without a real foreign
key to each resource.

Each join table has the resource ID and `tag_id`, a composite primary key or
equivalent uniqueness constraint, and foreign keys to both records. Prefer
database constraints that also enforce same-family ownership, such as
composite foreign keys over `(family_id, resource_id)` and `(family_id, tag_id)`
where the existing schema supports them. Services must still validate the
complete relationship using family-scoped queries. Deleting a tag cascades to
its join rows only; it never deletes the associated resource. Deleting a
resource removes its own join rows according to that feature's lifecycle.

Associations store IDs, never a copy of the tag name or color. A resource can
have zero or more tags, and a tag can be reused across any number of supported
resource types in the same family.

### Starter suggestions

The product may offer starter tag suggestions (for example Nature, History,
Arts, Sports, Music, Science, Technology, Outdoors, Physical Activity, Social,
and Religion). Suggestions are templates only. Once created for a family, each
is an ordinary editable family tag. The implementation must not require a
closed list or enum, and creating a tag must not require a migration or deploy.
Whether suggestions are seeded automatically or shown for user selection is a
product/UI choice; do not make automatic seeding a prerequisite for custom
tags.

## API behavior proposal

All routes require Bearer authentication and current membership in the path
family. Reuse `FamilyAuthorizationService`, existing roles, ProblemDetail,
localization, and safe cross-family 404 behavior. Follow existing conventions
for mutation roles; proposed default is OWNER/ADMIN manage tags and any family
member read them. A resource mutation may assign tags only when the caller is
authorized to mutate that resource.

Proposed tag routes:

| Method | Route | Behavior |
| --- | --- | --- |
| POST | `/api/families/{familyId}/tags` | Create a tag; return 201 and Location |
| GET | `/api/families/{familyId}/tags` | List family tags; optional `query` filters names |
| GET | `/api/families/{familyId}/tags/{tagId}` | Read one tag |
| PATCH | `/api/families/{familyId}/tags/{tagId}` | Rename and/or change color |
| DELETE | `/api/families/{familyId}/tags/{tagId}` | Delete tag and its associations; preserve resources |

Use explicit DTOs. Create accepts required `name` and optional nullable `color`.
PATCH accepts a nonempty subset; omission preserves a value and explicit null
clears color. Required name cannot be null. Reject unknown fields and client
writes to IDs, family ownership, and audit timestamps. A normalized duplicate
returns a stable conflict error. Document pagination and limits for tag listing
according to the existing API conventions.

Resource create/update requests accept optional `tagIds`, an array of UUIDs.
Omission preserves existing assignments on PATCH and means no assignments on
create. An explicit empty array clears assignments. Validate every ID in one
family-scoped operation before persisting any changes; reject the whole request
if any tag is missing, duplicated, or belongs to another family. Resource
responses include `tags` with `id`, `name`, and nullable `color`, so clients can
render tags without per-tag requests. Map these DTOs without N+1 queries.

Each adopted resource collection may accept `tagIds` filters using its existing
route and pagination shape. Multiple tag IDs mean AND: a resource must have
every requested tag. Reject duplicate or invalid IDs consistently. Combine tag
filters with existing text, date, member, and status criteria using AND where
those filters exist. Do not add search routes to domains that have no current
search contract solely for this PDR.

The exact resource routes, request schemas, roles, page limits, status codes,
stable error codes, and retry behavior must be specified in each integration
guide after checking the owning feature's existing API. Do not create parallel
generic CRUD endpoints for resources.

## Persistence and query requirements

Evaluate existing migrations and entity ownership before adding schema. Add a
versioned Flyway migration; do not alter migrations already applied to shared
environments. Create a family-scoped `tags` table with UUID primary key, family
foreign key, name, optional color, timestamps, and database-enforced normalized
uniqueness. The exact normalization index/expression must match the chosen
Unicode and case-folding behavior and be documented. Keep
`ddl-auto=validate` and `open-in-view=false`.

Create join tables only for resource entities that exist and are approved for
tagging. Each needs foreign keys, duplicate prevention, and indexes for both
resource-to-tags and tag-to-resources queries. Add family-aware constraints
where practical. Keep transactions in services and replace a resource's full
tag set atomically after validating all IDs.

Collection queries must filter by all requested tag IDs before pagination,
avoid duplicate resource rows, and use deterministic ordering. Use `EXISTS`,
grouping, or equivalent queries appropriate to the repository. Fetch associated
tags efficiently for response pages; do not introduce N+1 selects or unbounded
collection loads. The schema should support future tag-based cross-domain
search and reporting, but this PDR does not require a global search or report
endpoint.

Tag-based report buckets can overlap. Summing metrics across tags must not be
used to derive a global total because one resource can carry multiple tags.
Existing totals must count underlying resources/events according to their
domain rules.

## Authorization and isolation

Every tag operation checks authenticated user → current family membership →
required role. Every association change also checks resource ownership and
tag ownership against the same family. Cross-family or inaccessible IDs use
the established safe not-found behavior and must not disclose whether another
family's tag or resource exists. Validate both sides on create, replacement,
and update, including when only one side is supplied in a PATCH.

## Testing and acceptance

Use PostgreSQL Testcontainers for migrations, constraints, uniqueness, joins,
and filtering. Tests must use only domain entities that exist and are approved
for tagging. Cover:

1. Family tag creation, listing/search, rename, recolor, and deletion.
2. Equivalent names within one family are rejected, while the same name in
   different families is allowed; concurrent duplicate creation is protected.
3. One resource can have several tags, and one tag can be shared by resources
   in different supported domains.
4. Cross-family tag assignment and unauthorized family/resource access are
   rejected without disclosure.
5. Deleting a tag removes associations but preserves resources; renaming keeps
   associations intact.
6. Resource responses include tag details without N+1 queries.
7. Single-tag filtering, multi-tag AND filtering, and text-plus-tag filtering
   work with pagination and existing filters.
8. Empty `tagIds` clears assignments, invalid sets are atomic, and duplicate
   association rows cannot be persisted.
9. Overlapping tag metrics do not inflate domain-level totals if tag-based
   reporting is later added.

The global tag system is complete for an implementation increment when:

- Tags are user-defined, family-owned records with normalized family-scoped
  names; there is no required enum or closed vocabulary.
- Every implemented integration uses explicit many-to-many tables with real
  foreign keys and prevents cross-family associations.
- Tags can be shared across the implemented domain types, filtered with AND
  semantics, renamed without breaking links, and removed without deleting the
  resources.
- Responses provide tag display data efficiently, and future domains can add
  an explicit join table without changing the `Tag` concept.
- The integration guide, OpenAPI, migrations, authorization, and PostgreSQL
  tests cover the implemented scope.

Before implementation, resolve the exact routine/planning entity or entities
to tag and confirm the membership roles permitted to manage family tags.
Integrations for studies, books, plans, and extracurricular activities must
use entities that exist in their respective implementation increments.
