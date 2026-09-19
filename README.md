# Nosso Dia — Backend Constitution

Nosso Dia is a Java 25 and Spring Boot backend designed to remain simple,
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

## Current scope

The repository currently provides application startup, database migration,
OpenAPI documentation, temporary HTTP Basic authentication, and integration tests.
The product domain and final authentication flow are not defined yet.
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
in English. Keep Nosso Dia as the product name.

## Architecture

The application is a **modular monolith**, deployed as one Spring Boot application
and organized by business feature, with layers inside each feature as needed.
Do not introduce microservices or architectural patterns without a demonstrated
limitation of the existing design.

```text
Client → REST / JSON → Nosso Dia Backend → PostgreSQL

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
com.nossodia
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

Start a feature with a small, readable structure:

```text
children/
├── ChildController.java
├── ChildService.java
├── ChildRepository.java
├── Child.java
└── dto/
    ├── CreateChildRequest.java
    └── ChildResponse.java
```

Do not organize the whole application into global `controller`, `service`,
`repository`, `entity`, or `dto` packages. Introduce `api`, `application`, `domain`,
and `infrastructure` subpackages inside a feature only when its complexity
justifies them. Existing bootstrap configuration does not need an unrelated move.

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
failure context. The current development authentication is not the product design.

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

OpenAPI JSON: http://localhost:8080/v3/api-docs

These URLs use the default port from `.env.example`. Always use the port configured
in `.env`; for example, `APP_PORT=8081` changes the application URL to
http://localhost:8081.

All routes require HTTP Basic authentication. Use `APP_SECURITY_USERNAME` and
`APP_SECURITY_PASSWORD` from `.env`. Without a configured password, Spring generates
a temporary password and prints it in the application log. CSRF protection remains
enabled. This is a temporary development setup; the product authentication flow
has yet to be defined. This foundation does not include business endpoints.

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
of the Compose database. They verify the migration, API documentation and HTTP Basic
authentication. Docker and network access are required on first use to download
dependencies and images. The JAR is generated at
`target/nosso-dia-0.0.1-SNAPSHOT.jar`.

## Project structure

- `src/main/java/com/nossodia`: application and configuration.
- `src/main/resources/db/migration`: Flyway migrations.
- `src/test/java/com/nossodia`: integration tests and Testcontainers configuration.

The initial migration creates the `nosso_dia` schema; Flyway history is stored in
`public`. Hibernate validates mappings without creating or changing tables.
Entities and subsequent migrations will follow the product requirements.
