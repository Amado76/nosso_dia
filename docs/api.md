# API Design

Read this guide when changing endpoints, DTOs, validation, error handling,
pagination, or OpenAPI. Follow [AGENTS.md](../AGENTS.md) and the
[Security](security.md) guide for protected resources.

## Resources and methods

Expose REST APIs with JSON under `/api/...`. Use nouns rather than action names.
Illustrative routes, not implemented product requirements:

| Operation | Route | Typical success |
| --- | --- | --- |
| Create | `POST /api/children` | `201 Created`, response DTO and `Location` when addressable |
| Read | `GET /api/children/{childId}` | `200 OK` |
| List | `GET /api/children` | `200 OK`, bounded collection |
| Replace | `PUT /api/children/{childId}` | `200 OK` or `204 No Content` |
| Delete | `DELETE /api/children/{childId}` | `204 No Content` |

GET must not mutate state. PUT represents replacement of the editable representation;
use PATCH only when partial-update semantics are defined, including omitted versus
explicitly null values. Preserve method idempotency. A `204` response has no body.
Nested routes must validate the relationship between parent and child IDs.

Do not introduce `/api/v1` automatically. Version only when incompatible contracts
must coexist. OpenAPI document metadata does not by itself version route paths.

## DTOs and validation

Use separate request and response DTOs with only the fields needed for the contract.
Never bind client input directly to JPA entities or accept server-controlled ownership
and audit fields without an explicit authorized use case.

```java
public record CreateChildRequest(
        @NotBlank String name,
        @NotNull LocalDate birthDate
) {}

public record ChildResponse(UUID id, String name, LocalDate birthDate) {}
```

These examples do not establish actual domain fields or rules. Trigger Jakarta
Validation at the HTTP boundary and validate nested inputs where needed. Define
length, size, and numeric bounds from real requirements. Keep business rules in
services/domain code, including rules needed by non-HTTP callers. Standard validation
annotations may express a business constraint when appropriate; avoid unnecessary
duplication. Check authorization before revealing protected resource details.

Use ISO-8601 date/time representations. Calendar dates carry no timezone; event
timestamps identify an instant. Document nullability and required fields explicitly.

## Errors

Use centralized `@RestControllerAdvice` for MVC exception translation. Prefer
Spring's `ProblemDetail` for new error contracts, with a stable application `code`
extension and safe field errors where useful. Reuse an established consistent
contract if one already exists; do not introduce competing envelopes.

The following is an illustrative shape, not an existing endpoint response:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Child was not found",
  "code": "CHILD_NOT_FOUND"
}
```

Use meaningful application exceptions and translate them at the boundary. Typical
statuses are `400` for malformed or invalid input, `401` for missing/invalid
authentication, `403` for forbidden access, `404` for missing resources, and `409`
for a state conflict. An intentional `404` policy may conceal private resource
existence; apply it consistently. Unexpected failures return a generic `500`.

Security filter failures may occur before MVC advice; configure the security entry
point and access-denied handler when establishing a shared API error contract.
Do not leak stack traces, SQL, exception class names, credentials, or rejected
sensitive values. Clients should use stable codes, not parse human-readable messages.

## Collections and documentation

Bound collections that can grow indefinitely. Specify the default and maximum page
size, page numbering or cursor semantics, allowed sorts, and filters for each endpoint.
Use deterministic ordering with a unique tie-breaker. Prefer simple offset pagination
until the use case justifies cursors. Return a deliberate response DTO rather than
exposing JPA entities or an incidental framework serialization shape. Compute total
counts only when the contract needs them.

Update OpenAPI with actual DTOs, validation, status codes, pagination, and security
requirements. Do not advertise unimplemented authentication schemes. Test the
contract and relevant negative cases using [Testing](testing.md).
