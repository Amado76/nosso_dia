# Nosso Dia

Backend foundation built with Java 25, Spring Boot 4.1.1 and Maven Wrapper.

## Stack

Spring Web MVC, Spring Data JPA/Hibernate, PostgreSQL 18.3, Flyway,
Jakarta Validation, Spring Security and OpenAPI/Swagger (springdoc 3.1.1).
JUnit, Mockito and Testcontainers are test dependencies. Docker Compose
runs the database and, optionally, the application.

Dependency versions managed by Spring Boot are inherited from the Maven parent.
Springdoc provides the required OpenAPI/Swagger integration. Additional dependencies
require a clear reason; prefer functionality already provided by Java or Spring.
All project documentation, code, comments and messages must be in English.
The product name remains Nosso Dia.

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

These URLs use the default port from `.env.example`. The existing local environment
uses `APP_PORT=8081` because port 8080 was occupied; open
http://localhost:8081/swagger-ui/index.html. Always use the port configured in `.env`.

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
