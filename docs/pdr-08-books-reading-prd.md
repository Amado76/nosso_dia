# PDR-08 — Books, Reading, Catalog Search, and Family Tags

## Status and purpose

The books and reading source module and family catalog search are implemented.
This document records their behavior and acceptance criteria. It does not call
for rebuilding the book domain or reimplementing global tags.

BeeHome families own books that can be reused by multiple children. A `Book`
holds family-level metadata; each `ChildBook` represents one child's reading
journey for that book; `ReadingSession` records dated reading activity. The
catalog supports finding a family book so it can be linked to another child
without creating a duplicate book.

For future changes, compare this document with the current code and integration
guide before identifying gaps. The current API contract is documented in
[Books and reading API](books-reading.md), and the shared tag contract is
documented in [Global family tags](global-tags.md).

## Existing implementation inventory

The following capabilities are already present in the source module and must
be preserved:

- `Book` belongs to a family and stores title, optional author, ISBN, total page
  count, and optional family image (`coverMediaId`).
- `ChildBook` links a book to a child. Different children in the same family
  may reuse one book. Existing journey statuses and their lifecycle remain
  unchanged by this extension.
- `GET /api/families/{familyId}/books` lists the family catalog with bounded
  pagination (`page`, default 0; `size`, default 20, maximum 100), ordered by
  creation time and ID.
- Family authorization and ownership scoping are enforced by the backend.
- Global `Tag` records and the `book_tags` many-to-many association already
  exist. The join table has real family-aware foreign keys and a composite key
  preventing duplicate book/tag links.
- Book create and replace accept `tagIds`; book responses include tag `id`,
  `name`, and nullable `color`. Tags are validated against the book's family,
  and book response mapping loads links in batches rather than one query per
  book.
- Catalog listing accepts repeated `tagIds` parameters. Multiple IDs use AND
  semantics and combine with pagination. Tag changes do not recreate a book
  or alter its child journeys.
- The family book list accepts an optional `query` for partial,
  case-insensitive title or author search. Text and tag filters combine before
  pagination.
- PostgreSQL-backed tests cover catalog search, family isolation, tag filters,
  pagination, and book reuse across children alongside existing reading tests.

Confirm this inventory against the implementation before changing code; code
and maintained API documentation take precedence if the repository evolves.

## Goals

1. Let a user search all books in a family before creating a new book.
2. Let a user reuse a matching `Book` for another child by creating only a new
   `ChildBook` relationship.
3. Add partial, case-insensitive search across book title and author to the
   existing family catalog endpoint.
4. Preserve the existing global family tag integration and its AND filters.

## Out of scope

- Recreating or restructuring the book, child journey, or reading session
  domains.
- Introducing a separate book category or tag model, including
  `BookCategory`, `BookGenre`, or a book-only tag entity.
- Changing `ChildBook` statuses, completion rules, reading metrics, or session
  behavior.
- Automatically creating a book when a search returns no results.
- Cross-family catalog search, external book lookup, ISBN lookup, or advanced
  full-text ranking.
- Adding fields that duplicate query context to the `Book` entity, such as
  whether it is already assigned to a particular child.

## Catalog search requirements

The existing endpoint supports search:

```http
GET /api/families/{familyId}/books?query=tolkien&page=0&size=20
```

`query` is optional. When provided, it must match a substring of either the
book's title or author, case-insensitively. For example, `hob`, `HOBBIT`, and
`Tolkien` can all find *The Hobbit* by J. R. R. Tolkien. A missing or blank
query follows the existing list behavior. A nonmatching query returns an empty
page using the existing page response shape.

Search results are always scoped to the requested family and to the caller's
authorized family access. A matching book owned by another family must never
appear. Search covers the family catalog regardless of whether a book already
has a `ChildBook` for the child who will receive it.

Search and tag filters combine using AND: a result must match the text query
and carry every requested tag. Repeated `tagIds` continue to use the existing
API convention. Preserve current pagination defaults, size bounds, stable
ordering, response DTOs, duplicate-ID validation, and safe handling of invalid
or foreign-family tags.

Use the simplest query strategy consistent with the existing PostgreSQL and
repository patterns. Case-insensitive partial matching is required; advanced
full-text search, ranking, and speculative indexes are not. Evaluate indexes
against the actual family-scoped query before adding any migration. Do not
change or remove existing book fields or add a migration for schema already
present.

## Reuse flow

The supported flow is:

1. Request the family catalog with `query` and optional `tagIds`.
2. Select a matching existing book.
3. Create a `ChildBook` for the target child using that existing `bookId`.
4. If no suitable result exists, use the existing book creation flow, then
   create the child's `ChildBook`.

The family catalog must not be restricted to books already associated with the
target child. Reuse must preserve one `Book` row with separate child journeys.
The current `ChildBook` contract and active-journey uniqueness rules remain in
force.

## Global tag behavior already established

Books use the shared family `Tag` entity. Each tag remains free-form and may
also be used by other supported domains. A book can have zero or more tags, and
a tag can be associated with multiple books and other supported resources.

The existing behavior must remain intact:

- Create and replace accept optional `tagIds`; the existing PUT replacement
  semantics for omitted optional fields, including tags, are documented in the
  integration guide.
- A supplied tag set is validated as a whole against the same family as the
  book. A cross-family or inaccessible tag is rejected using the established
  safe not-found response.
- Book responses include display information for their tags without an N+1
  query pattern.
- Repeated tag filters require all requested tags. Tag filters combine with
  text search using AND semantics.
- Removing an association does not delete the book or tag. Existing foreign
  keys, cascades, and uniqueness constraints govern association lifecycle.

Do not add a new tag table, duplicate a global tag feature, or change global
tag behavior as part of catalog search.

## Authorization and data integrity

Every list and search request requires authentication and current membership in
the requested family, following existing family authorization behavior. Book
and tag IDs supplied in mutations or filters are validated within that family.
Never rely on UUID secrecy or frontend filtering. Preserve existing safe 404
behavior for inaccessible resources and the current editor permissions for
mutations.

## API and documentation

[Books and reading API](books-reading.md) and OpenAPI document the `query`
parameter, including partial, case-insensitive title and author matching,
family scoping, and combination with repeated `tagIds` using AND semantics.
The integration guide also describes reusing a family book across children
through `ChildBook`.

Do not create a second book-search endpoint or a separate integration guide.

## Coverage and acceptance

PostgreSQL-backed tests cover the search behavior:

- List a family's books and retain existing pagination behavior.
- Find a book by full title, partial title, full author, and partial author.
- Match case-insensitively.
- Do not return another family's book, even when its title or author matches.
- Return an empty page when nothing matches.
- Combine query with one or multiple tag filters; tag filters retain AND
  semantics, including author matches.
- Find a book through the family catalog when it is linked to a different
  child, then create a second child's journey using the same `bookId`.
- Preserve existing tag assignment, response, family validation, and tag
  filtering behavior; extend existing coverage only where a required case is
  missing.

The catalog search is implemented: users can search by partial,
case-insensitive title or author; results obey family access, pagination, and
combined tag filters; and an existing book can be reused by another child
without creating a duplicate `Book`. Existing metadata, tags, journey statuses,
and API behavior remain intact.

## PDR-09 follow-up

The history, calendar, and report API consumes reading data as described in the
[implemented PDR-09 follow-up](pdr-09-calendar-history-reports-prd.md#prd-08-follow-up-connect-the-reading-source).
