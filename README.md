# BeeHome — Backend Constitution

BeeHome is a Java 25 and Spring Boot backend designed to remain simple,
maintainable, testable, and loosely coupled. Architecture exists to make change
easier. Choose the simplest solution that preserves clear responsibilities and
allows the codebase to evolve safely.

This README explains the architecture and how to run the project. The concise
[agent constitution](AGENTS.md) contains mandatory working rules. Detailed guides
expand those rules for specific tasks:

| Guide | Read when working on |
| --- | --- |
| [Architecture and Java](docs/architecture.md) | Features, module boundaries, Java code, storage, or reports |
| [API design](docs/api.md) | Endpoints, DTOs, validation, errors, pagination, or OpenAPI |
| [Persistence](docs/persistence.md) | Entities, queries, migrations, transactions, or concurrency |
| [Security](docs/security.md) | Authentication, authorization, private data, configuration, or uploads |
| [Testing](docs/testing.md) | Behavior changes, bug fixes, or verification |

These documents apply to developers and AI agents. MUST means required; SHOULD
means the default unless a concrete reason justifies a deviation; MAY means optional.
An explicit task can require an exception; explain its reason and consequences.
Keep the constitution and relevant guides aligned when changing a convention.

Explore documentation relationships in the [interactive documentation graph](docs/knowledge-graph.html).
Regenerate it after changing Markdown links with `python3 scripts/generate-doc-graph.py`.

For task context, read the constitution and only the guide(s) relevant to the
work. Do not reread the whole README on every task; consult only the relevant
section when needed. Historical PRDs/PDRs are not mandatory reading. Consult
them only when a task explicitly refers to one or when current code and
maintained guidance do not resolve a specific requirement.

## Current scope

The repository provides email/password registration and login, JWT access tokens,
rotating refresh sessions, SMTP password recovery, authenticated password changes,
current-user retrieval, and a
Google/Apple external identity mapping foundation. It also includes database
migrations, OpenAPI, centralized API errors, English/Portuguese/Spanish localization,
and automated tests. See [authentication](docs/authentication.md) and the
[API error and localization contract](docs/api.md#errors).
BE-02 provides family creation, membership-scoped listing and details, and
OWNER/ADMIN renaming. See the [family integration guide](docs/families.md) and
[BE-02 PRD](docs/be-02-family-authorization-prd.md). Family deletion, invitations,
membership administration, and the remaining product domain are not implemented.
BE-03 provides family people profiles, active-state management, and self-account
links. See the [family members integration guide](docs/family-members.md) and
[BE-03 PRD](docs/be-03-family-members-prd.md). Avatar writes await the media feature.
BE-04 provides recurring routines, date-specific notes/items, resolved daily plans,
and family timezones. See the [planning integration guide](docs/planning.md) and
[Routines and Daily Planning PRD](docs/be-04-routines-daily-planning-prd.md).
BE-05 provides daily execution snapshots, completion, finalization, corrections,
and paginated history. See the [execution integration guide](docs/daily-execution.md) and
[Daily Execution and History PRD](docs/be-05-daily-execution-history-prd.md).
BE-06 provides family-wide study subjects, timer/manual study sessions, and
history and summary queries. See the [study tracking integration guide](docs/study-tracking.md)
and [Study Tracking PRD](docs/be-06-study-tracking-prd.md).
Feature names and API examples below illustrate organization; they are not approved
requirements and do not authorize implementing business features.

## Technology stack

- Java 25, Spring Boot 4.1.1, and the committed Maven Wrapper.
- Spring Web MVC, Spring Data JPA, Hibernate, and PostgreSQL 18.3.
- Flyway, Jakarta Validation, and Spring Security.
- OpenAPI/Swagger through springdoc 3.1.1.
- JUnit, Mockito, PostgreSQL Testcontainers, and Docker Compose.

The Maven POM and Compose file define the installed versions. Let Spring Boot
manage dependency versions where supported. Add a dependency only for a clear
need that existing Java or Spring functionality cannot adequately meet.
All documentation, code, comments, configuration messages, and API text MUST be
in English. Keep BeeHome as the product name.

## Architecture

The application is a **modular monolith**, deployed as one Spring Boot application
and organized by business feature, with layers inside each feature as needed.
Do not introduce microservices or architectural patterns without a demonstrated
limitation of the existing design.

```text
Client → REST / JSON → BeeHome Backend → PostgreSQL

HTTP request → Controller → Service → Repository → Database
```

| Responsibility | Owns |
| --- | --- |
| Controller | Routes, HTTP input/output, status codes, and validation triggering |
| Service | Use-case coordination, business rules, authorization decisions, and transactions |
| Repository | Persistence and database queries, preferably through Spring Data JPA |
| Entity | Persisted domain state and behavior that naturally belongs to it |
| DTO | Explicit public request/response contract, independent of persistence |

Business logic MUST NOT live in controllers. JPA entities MUST NOT be exposed as
public API contracts. Domain behavior may live in entities; do not force it into
services merely to follow a diagram.

### Package by feature

The following packages are illustrative, not a scaffold to create in advance:

```text
com.beehome
├── auth
├── family
├── children
├── routine
├── homeschool
├── books
├── photos
├── reports
└── shared
```

Feature packages MUST use responsibility-based subpackages, following `auth`.
For example, the implemented family feature is organized as:

```text
family/
├── controller/
│   └── FamilyController.java
├── service/
│   ├── FamilyService.java
│   └── FamilyAuthorizationService.java
├── repository/
│   ├── FamilyRepository.java
│   └── FamilyMembershipRepository.java
├── entity/
│   ├── Family.java
│   ├── FamilyMembership.java
│   └── FamilyRole.java
├── exception/
│   └── FamilyException.java
└── dto/
    ├── CreateFamilyRequest.java
    ├── PatchFamilyRequest.java
    ├── FamilyPage.java
    └── FamilyResponse.java
```

Keep top-level feature classes in their responsibility subpackages, not directly
in the feature root. Create only packages required by existing responsibilities;
`health`, for example, needs only `controller` and `dto`. Do not introduce empty
packages or artificial layers to match the example. Do not organize the whole
application into global `controller`, `service`, `repository`, `entity`, or `dto`
packages. Existing bootstrap configuration and shared cross-feature infrastructure
retain their separate packages.

Authentication is the reference for this organization:

```text
auth/
├── controller/  # Authentication HTTP endpoints
├── dto/         # Request/response contracts and input validation
├── entity/      # Persisted tokens, external identities, and providers
├── repository/  # Authentication persistence queries
├── service/     # Authentication, session, and password use cases
├── security/    # Token cryptography, configuration, rate limits, and security errors
├── mail/        # Password-reset delivery and email configuration
└── exception/   # Authentication application errors
```

These packages remain part of one authentication feature. Cross-package access
uses public collaborator types and focused entity methods; persisted fields remain
private. Apply the same convention to every feature, adding infrastructure
packages such as `security` and `mail` only where those responsibilities exist.

### Boundaries and simplicity

Keep modules loosely coupled. Prefer identifiers for cross-feature references
when practical, and introduce JPA relationships deliberately. Avoid large
bidirectional entity graphs and long service dependency chains. Simple, stable
cross-feature calls are acceptable; events and coordinators require a real use case.
Keep `shared` small and limited to responsibilities used across features.

Use constructor injection, explicit Java mapping, and records for suitable immutable
DTOs. Do not initially add Lombok, mapping frameworks, automatic `ServiceImpl`
pairs, generic CRUD base classes, or interfaces without a meaningful boundary.
Use consistent business names and comments that explain why. Prefer Java Time:
`LocalDate` for calendar dates and `Instant` for events, without assuming the
server's timezone represents the user.

## Engineering rules

### API contracts

Use REST, JSON, resource nouns, and HTTP method semantics. Start at `/api/...`;
version routes only when incompatible contracts must coexist. Use explicit DTOs,
Jakarta Validation for input structure, and domain/application validation for
business rules. Centralize exception handling and keep errors consistent without
exposing implementation details. Document contracts through OpenAPI. Bound growing
collections with pagination and define stable ordering.
Every new API also requires a human- and agent-readable Markdown integration
document under `docs/`, linked from this README. Every API change MUST update
that document in the same change; follow the
[UI–API documentation requirements](docs/api.md#mandatory-uiapi-integration-documentation).

### Persistence and transactions

Use PostgreSQL, UUID domain identifiers by default, and versioned Flyway migrations
for every schema change. Keep `spring.jpa.hibernate.ddl-auto=validate` and
`spring.jpa.open-in-view=false`. Never use Hibernate to update schemas automatically.
Keep transactions primarily in services and avoid slow network calls inside them.
Enforce invariants with appropriate database constraints as well as application
validation. Avoid N+1 queries, unbounded reads, and loading tables into memory.
Measure before introducing complex optimizations.

### Security and configuration

Spring Security handles authentication infrastructure; application rules determine
authorization. Implement ownership/membership enforcement when introducing private
resources, including lists, exports, and files. A UUID and frontend checks never
substitute for backend authorization. Do not postpone access control on exposed
resources until a later feature.

Keep environment-specific configuration outside business code. Document variables
in `.env.example`; never commit real secrets or log tokens, credentials, or sensitive
personal data. Logs should explain meaningful events with safe identifiers and
failure context. Authentication deployment limits are documented in
[Authentication](docs/authentication.md).

### Testing and evolution

Use **test-driven development (TDD)** for new features and bug fixes: write a behavior
test, run it and confirm the expected failure (Red), implement the minimum solution
(Green), then refactor while keeping tests passing. Repeat in small increments and
finish with `./mvnw verify`. Documentation-only and formatting-only changes are exempt;
behavior-preserving refactors start with passing regression coverage. See the
[required TDD workflow](docs/testing.md#required-tdd-workflow) for details.

Test behavior with JUnit and Mockito where isolation helps. Use PostgreSQL
Testcontainers for database behavior, including queries, constraints, migrations,
and transactions. Do not mock everything for coverage. Use descriptive test names
and add or update tests for meaningful behavior changes.

Before implementation, identify the owning feature, inspect existing conventions,
locate the responsibility, assess module dependencies and authorization, determine
persistence and migration needs, and choose meaningful tests. Keep changes scoped.
Explain necessary architectural deviations and avoid unrelated refactors.

Photos and files should use object storage with database metadata unless a specific
requirement justifies otherwise. Introduce a small provider boundary when needed.
Reports consume domain data; they must not become its source of truth. These are
design constraints for future work, not requirements to add storage or reporting now.

## Run locally

Prerequisites: JDK 25 and a running Docker engine with Docker Compose.
The Wrapper downloads Maven on first use; a global Maven installation is unnecessary.

```sh
cp .env.example .env
docker compose up -d --wait postgres
set -a
. ./.env
set +a
export DB_URL="jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB}"
export DB_USERNAME="$POSTGRES_USER"
export DB_PASSWORD="$POSTGRES_PASSWORD"
export SERVER_PORT="$APP_PORT"
./mvnw spring-boot:run
```

Compose reads `.env` automatically. Running Java directly requires the exported
variables above. The example passwords are for local development only.

Swagger UI: http://localhost:8080/swagger-ui/index.html

Swagger UI shortcut: http://localhost:8080/swagger

OpenAPI JSON: http://localhost:8080/v3/api-docs

These URLs use the default port from `.env.example`. Always use the port configured
in `.env`; for example, `APP_PORT=8081` changes the application URL to
http://localhost:8081.

The example environment enables public Swagger for local development. Register
with `POST /api/auth/register`, log in at `POST /api/auth/login`, and paste the
returned access token into Swagger's **Authorize** dialog. Protected endpoints
require `Authorization: Bearer <access-token>`. HTTP Basic is disabled.
Swagger is protected unless `AUTH_PUBLIC_DOCS=true` is explicitly configured.
The example environment uses ephemeral signing keys and discards password-reset
delivery until SMTP is enabled. To send recovery emails, configure the provider,
sender, and frontend reset URL using the variables in `.env.example`; see
[email setup checklist](docs/email-setup.md),
[password reset delivery](docs/authentication.md#password-reset-delivery), and
[deployment requirements](docs/authentication.md#deployment).

### Application health check

`GET /api/health` returns HTTP `200` with `{"status":"UP"}` when the application
can respond. It is public. This checks application responsiveness,
not database or external dependency readiness.

After exporting the local variables above:

```sh
curl --fail \
  "http://localhost:${SERVER_PORT:-8080}/api/health"
```

## Run everything in Docker

After creating `.env`:

```sh
docker compose --profile app up -d --build --wait
docker compose logs -f app
docker compose --profile app down
```

The application runs on Java 25 in a separate runtime image as an unprivileged user.
PostgreSQL data persists in a volume, which `down` preserves.
Ports are published on localhost only. Change `POSTGRES_PORT` and `APP_PORT`
in `.env` if the default ports are occupied.

## Test and package

```sh
./mvnw verify
```

Tests start a disposable PostgreSQL 18.3 instance with Testcontainers, independently
of the Compose database. They verify migrations, health, API documentation,
authentication, recovery, session concurrency, and localization.
Docker and network access are required on first use to download
dependencies and images. The JAR is generated at
`target/beehome-0.0.1-SNAPSHOT.jar`.

## Project structure

- `src/main/java/com/beehome`: application and configuration.
- `src/main/resources/db/migration`: Flyway migrations.
- `src/test/java/com/beehome`: integration tests and Testcontainers configuration.

Migration V1 creates the legacy `nosso_dia` schema, and V8 renames it to `beehome`; Flyway history is stored in
`public`. Hibernate validates mappings without creating or changing tables.
Subsequent migrations create users, refresh/reset tokens, external identities,
families, memberships, family members, family timezones, routines, and daily plans.
