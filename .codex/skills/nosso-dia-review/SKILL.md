---
name: nosso-dia-review
description: Review the current branch diff for a Nosso Dia backend change against the project's constitution, architecture, API, persistence, security, performance, and testing expectations.
---

# Nosso Dia Review

Use this skill for a code or documentation review of a Nosso Dia backend branch, patch, or pull request. Review the branch diff as the primary scope; do not assume or embed a local repository path.

## Repository Context

Nosso Dia is a Java 25 Spring Boot modular monolith. It uses Spring Web MVC, Spring Data JPA/Hibernate, PostgreSQL, Flyway, Jakarta Validation, Spring Security, OpenAPI/Swagger, JUnit, Mockito, Testcontainers, Docker Compose, and the committed Maven Wrapper.

The product domain and final authentication flow are not defined. Do not invent product behavior while reviewing; flag missing decisions where the change depends on them.

All documentation, code, comments, configuration messages, API text, and review text should be in English, except when speaking conversationally with the user outside artifacts.

## Required Reading

Before every review, read `AGENTS.md` from the repository root.

Read only the guides that match files in the diff:

- `docs/architecture.md` for features, Java code, module boundaries, storage, and reports.
- `docs/api.md` for endpoints, DTOs, validation, errors, and pagination.
- `docs/persistence.md` for entities, queries, migrations, and transactions.
- `docs/security.md` for authentication, authorization, private data, uploads, and configuration.
- `docs/testing.md` for behavior changes, bug fixes, and tests.

## Review Scope

Use the target branch supplied by the user or pull request as the diff base. When no base is supplied, identify the merge base with the tracked target branch when available. Review the resulting branch diff, staged changes, and uncommitted changes as applicable. Do not review unrelated historical code unless it is needed to understand a changed behavior.

Inspect the full changed files and nearby implementation before judging. Use the diff's file list to select relevant guides and to determine the owning feature and layer.

## Review Focus

Check the change against these expectations:

- Architecture: Controller -> Service -> Repository flow, business logic outside controllers, responsibility-based subpackages within each feature following `auth` and the current architecture guide, no empty packages or artificial layers, loose module coupling, no unnecessary service chains or dependency cycles, and a small `shared` package.
- API: REST/JSON resource URLs, explicit request and response DTOs, proper HTTP methods, external input validation, centralized API errors, bounded collections, and no direct exposure of JPA entities.
- Java style: constructor injection, explicit mapping, suitable records, Java Time, clear business names, and no Lombok, mapping framework, automatic service interface, or generic CRUD base class without a clear demonstrated need.
- Persistence: PostgreSQL, UUID domain identifiers by default, versioned Flyway migrations for schema changes, no edits to migrations already applied to shared environments, `ddl-auto=validate`, `open-in-view=false`, short service transactions, database constraints for persisted invariants, and concurrency handling where relevant.
- Security: Spring Security enforcement in the backend, resource ownership checks for private data, no reliance on UUID secrecy or frontend checks, no invented final auth flow, no secrets in source or logs, documented configuration variables in `.env.example`, and no sensitive personal data in logs.
- Performance: bounded reads and writes, pagination for growing collections, no obvious N+1 queries, appropriate indexes and query shapes, no unbounded uploads or payloads, short transactions, no blocking external work in request transactions, and no avoidable repeated I/O or computation.
- Tests: behavior-focused tests for meaningful behavior changes and failure paths; TDD evidence for new features and bug fixes when available; PostgreSQL Testcontainers for database integration tests; `./mvnw verify` for behavior changes when Docker is available.

For documentation-only changes, review links, consistency, terminology, and diff scope. Do not require Docker, application startup, or the full application suite for documentation-only work.

## Review Method

Lead with concrete findings ordered by severity: blocker, high, medium, low. Each finding must include the changed file and line when possible, explain the user-visible or operational impact, and give a focused correction. Do not report speculative concerns as findings; label them as open questions or residual risk.

Assess security and performance independently even when no findings exist. Use these statuses:

- `OK`: the diff and relevant context provide sufficient evidence that the area is handled correctly.
- `Needs changes`: a concrete issue in the diff requires correction.
- `Not applicable`: the change does not affect the area.
- `Unable to verify`: evidence is missing or a required check could not run.

Do not infer `OK` merely because no problem was found. Include concise evidence for each status, such as authorization checks, ownership enforcement, query/pagination behavior, indexes, test coverage, or verification commands.

Do not propose broad refactors unless they fix a concrete issue in the reviewed change. If a product decision is missing, state the decision point and consequence instead of inventing behavior.

When commands are needed, prefer `rg` for search and the committed `./mvnw` for Maven checks. Redirect verbose command output to a temporary log and report the path and relevant result. Report every check that could not run and why.

## Output Shape

Write the review in English and use this order:

1. `Findings`: actionable findings first, ordered by severity, with file and line references.
2. `Security`: status (`OK`, `Needs changes`, `Not applicable`, or `Unable to verify`) plus concise evidence.
3. `Performance`: status using the same values plus concise evidence.
4. `What is good`: a brief summary of solid implementation choices and coverage.
5. `What needs to change`: a brief grouped summary of required fixes, or `Nothing identified` when there are no required changes.
6. `Open questions and assumptions`: only when relevant.
7. `Verification`: commands run, results, and skipped checks with reasons.

If there are no findings, say so clearly before the summaries. Keep the report concise and distinguish verified facts from residual risk.
