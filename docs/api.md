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

Expected application errors extend `shared.exception.ApiException` and carry an
HTTP status, stable machine-readable code, message key, and optional arguments.
Keep concrete exceptions in the owning feature. A single handler translates all
subclasses to RFC 9457 `ProblemDetail` with `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Localized description",
  "instance": "/api/example",
  "code": "EXAMPLE_NOT_FOUND"
}
```

This example illustrates the contract; it does not define a product endpoint.
Clients MUST branch on `code`, never on translated text. Controllers let exceptions
propagate; they do not repeat exception mapping or resolve messages themselves.
Business logic remains independent of the current language.

Request DTO and MVC method parameter validation return HTTP 400 with
`code=VALIDATION_ERROR`, a localized title/detail, and an `errors` array of
`{ "field": "...", "message": "..." }`. Error order is not contractual. Object-level
constraints use the object name as the field. Never include rejected values.
Use bundle keys in Jakarta annotations, for example
`@NotBlank(message = "{validation.required}")`. Avoid interpolating sensitive
input into constraint messages.

Unexpected errors return HTTP 500 with `code=INTERNAL_SERVER_ERROR` and a safe,
localized detail. Server logs retain the exception class and original stack frames;
exception messages and causes are omitted because they may contain secrets or
personal data. Expected errors do not produce error-level stack traces.

The advice extends Spring's `ResponseEntityExceptionHandler` to preserve framework
status handling (including 404, 405, 415, and malformed JSON). These framework
responses do not promise application codes or localized messages. Authentication
and authorization failures retain Spring Security handling; this PRD does not
standardize security filter responses or change HTTP Basic/CSRF rules.

### Localization

Use `shared.localization.LocalizationService.get(key, arguments...)` at the
presentation boundary for backend-owned text. It delegates to Spring
`MessageSource` and the request locale. Application code must not select languages
or access resource bundles directly. The same boundary can serve future success
messages, labels, or content without introducing a CMS or Server-Driven UI now.

Clients use the standard `Accept-Language` header. Supported languages are English
(`en`), Portuguese (`pt`), and Spanish (`es`). Regional preferences such as
`pt-BR`, `pt-PT`, and `es-PY` resolve to the base language. Spring negotiates
weighted preferences. Missing or unsupported preferences default to English,
independently of the server locale. No custom language headers are used.

Translations live in `messages.properties` (English fallback) and
`messages_en.properties`, `messages_pt.properties`, `messages_es.properties`.
Keep default and English entries aligned. Portuguese and Spanish user-facing
resources are the explicit localization exception to the repository's English-only
text convention. Keep code, keys, comments, and documentation in English.

Add only keys required by implemented behavior, with stable names such as
`error.<feature>.<reason>` or `validation.<feature>.<field>.<constraint>`.
Supply all three translations. Parameterized messages use Spring's
`MessageFormat` syntax (`{0}`, `{1}`); escape literal apostrophes as `''` in
parameterized patterns. Missing translations fall back to the English bundle;
an unknown key is a programming/configuration error, not text to expose to users.

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
