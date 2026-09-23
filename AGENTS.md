# BeeHome — Backend Constitution

Read this constitution before every task. Read only the guides relevant to the
area being changed; do not load unrelated guides for routine work. Do not read
the entire README for every task: consult only the specific section needed for
architecture or local execution, and only when the task depends on it. Treat
old PRDs/PDRs and other historical planning documents as reference material,
not required context; read one only when the task explicitly concerns it or
when a specific unresolved requirement cannot be established from current code,
the constitution, and relevant guides. Prefer the current implementation and
maintained guides over historical plans.

## Task-specific guides

| Task | Required guide |
| --- | --- |
| Features, Java, module boundaries, storage, reports | [Architecture](docs/architecture.md) |
| Endpoints, DTOs, validation, errors, pagination | [API](docs/api.md) |
| Entities, queries, migrations, transactions | [Persistence](docs/persistence.md) |
| Authentication, authorization, private data, uploads, configuration | [Security](docs/security.md) |
| Behavior changes, bug fixes, tests | [Testing](docs/testing.md) |

## Scope and language

- Write documentation, code, comments, configuration messages, and API text in English. Keep BeeHome as the product name.
- The product domain and final authentication flow are not defined. Do not invent them. Feature examples are illustrative.
- Follow these rules unless the task explicitly requires an exception. Explain architectural deviations and their consequences.

## Architecture

- Use Java 25, Spring Boot, and the committed Maven Wrapper.
- Build a modular monolith organized by feature; do not introduce microservices.
- Default flow: Controller → Service → Repository. Keep business logic out of controllers.
- Feature packages MUST use responsibility-based subpackages, following `auth`: `controller`, `service`, `repository`, `entity`, `dto`, and `exception`, plus feature-specific infrastructure packages such as `security` and `mail` when needed.
- Keep top-level feature classes in their responsibility subpackages, not directly in the feature root. Create only packages and layers that have actual responsibilities; do not add empty packages or artificial services. Keep technical layers inside their owning feature, never in global application-wide layer packages.
- Keep modules loosely coupled. Prefer IDs for cross-module references when practical; avoid large bidirectional JPA graphs.
- Avoid dependency cycles and unnecessary service chains. Do not bypass another feature's business rules through direct repository access.
- Keep `shared` small. Prefer existing structures over parallel implementations.

## API and Java

- Use REST/JSON, resource URLs, explicit request/response DTOs, and appropriate HTTP methods.
- Every new API MUST have a Markdown integration document under `docs/`, readable by humans and other agents, and linked from the README. Every API change MUST update its document in the same change. Cover all details needed for UI–API integration: routes and methods, authentication/authorization, headers, request/response schemas and examples, required fields and nullability, validation, status/error codes, client flows, side effects, retries, pagination where applicable, and configuration or limitations. OpenAPI complements this document; it does not replace it. See [API documentation requirements](docs/api.md#mandatory-uiapi-integration-documentation).
- Never expose JPA entities directly. Validate external input and centralize consistent API errors.
- Bound growing collections and avoid N+1 queries.
- Prefer constructor injection, explicit mapping, suitable Java records, and Java Time.
- Do not introduce Lombok initially, mapping frameworks without demonstrated repetition, automatic service interfaces, or generic CRUD base classes.
- Use clear business names. Comments explain why rather than repeat the code.

## Persistence

- Use PostgreSQL, Spring Data JPA/Hibernate, and UUID domain identifiers by default.
- Apply every schema change through versioned Flyway migrations; do not edit migrations already applied to shared environments.
- Keep `ddl-auto=validate` and `open-in-view=false`.
- Put transactions primarily in services; keep them short and avoid external network calls inside them.
- Use database constraints for persisted invariants; account for concurrent writes where relevant.

## Security

- Use Spring Security. Enforce authentication, authorization, and resource ownership in the backend.
- Implement access control alongside any private resource, including lists and downloads. Never rely on UUID secrecy or frontend checks.
- Do not invent the final authentication flow or weaken security defaults without a concrete requirement.
- Keep secrets out of version control and logs; document configuration variables in `.env.example`.
- Do not log sensitive personal data. Keep environment-specific values outside business code.

## Dependencies and verification

- Stack: Spring Web MVC, Spring Data JPA/Hibernate, PostgreSQL, Flyway, Jakarta Validation, Spring Security, OpenAPI/Swagger, JUnit, Mockito, Testcontainers, and Docker Compose.
- Do not add dependencies without a clear reason. Prefer Java/Spring functionality and Spring Boot-managed versions where supported.
- Test behavior, not implementation details. Add/update tests for meaningful behavior changes.
- Use TDD for new features and bug fixes: write a behavior test first, run it and confirm the expected failure, implement the minimum solution, then refactor while keeping tests green. Follow [Testing](docs/testing.md) for the full cycle.
- Documentation-only and formatting-only changes do not require TDD. For behavior-preserving refactors, establish passing regression coverage before changing production code.
- Use PostgreSQL Testcontainers for database integration tests.
- Run `./mvnw verify` for changes affecting application behavior; Docker must be available. Report any check that could not run.
- Documentation-only changes require link, consistency, and diff checks; they do not require starting Docker or running the application suite.

## Command output hygiene

- For verbose commands such as Maven, Docker, test suites, and application runs, redirect full output to a log file under `/private/tmp` by default and inspect only the relevant summary or failure excerpts.
- Prefer focused test commands during TDD and run the full verification only when the change is ready.
- Keep user-facing reports concise: include the command, the log path when useful, the result, and the meaningful error lines instead of pasting long logs.

## Before changing code

1. Identify the business feature and layer that own the behavior.
2. Inspect existing implementation, conventions, and relevant guides.
3. Determine whether existing structures suffice and another module is actually needed.
4. Establish authorization rules; ask for missing product decisions rather than inventing them.
5. Determine persistence, migration, and concurrency requirements.
6. Choose tests for meaningful behavior and failure paths; for features and bug fixes, begin with a failing test before implementing the behavior.
7. Keep changes scoped, preserve boundaries, and avoid unrelated refactors.
8. Explain concrete reasons for necessary complexity and report verification results.
