# Testing and Verification

Read this guide for behavior changes, bug fixes, and tests.
Follow [AGENTS.md](../AGENTS.md).

## Required TDD workflow

New features and bug fixes MUST follow test-driven development (TDD), in small
Red → Green → Refactor cycles:

1. **Red:** Write or update a focused test describing the next observable behavior.
   Run it before implementing the behavior and confirm that it fails for the
   expected reason. For a bug fix, first reproduce the bug with a regression test.
2. **Green:** Implement the smallest maintainable change that makes the test pass.
   Run the focused test and relevant existing tests. Do not weaken assertions just
   to obtain a passing result.
3. **Refactor:** Improve naming, structure, or duplication only where useful, keeping
   behavior unchanged. Rerun the affected tests and keep them green.
4. Repeat for the next behavior or failure case, then run `./mvnw verify` before
   completing the change.

Use the test level that demonstrates the behavior: TDD may start with a unit,
HTTP integration, or PostgreSQL integration test. It does not require mocking
persistence behavior or writing every test before any implementation.

A missing tool, unavailable Docker engine, or unrelated startup failure does not
establish the Red step. If new types require minimal declarations for the test to
compile, add only those declarations, then confirm a behavior failure before
implementing the logic. Tests written only after implementation do not satisfy TDD.

Documentation-only and formatting-only changes do not require this cycle.
For behavior-preserving refactors, first run relevant regression tests and add
characterization coverage if needed; do not manufacture a failing test when the
behavior should remain unchanged. Configuration and migration changes that alter
application behavior require appropriate behavior tests using the same cycle.

Report the observed failing behavior and subsequent passing checks concisely.
If a step could not run, state the blocker rather than claiming TDD was completed.
Exceptions follow the explicit-task exception rule in [AGENTS.md](../AGENTS.md).

## Choose tests by behavior

Use JUnit for tests and Mockito when isolating a collaborator makes the test useful.
Prefer plain unit tests for domain rules and service decisions that do not require
Spring. Do not start the full application merely to test a calculation, and do not
mock every call just to achieve coverage.

Use focused Spring tests for framework behavior and integration tests for interactions
among HTTP, security, application code, and persistence. Whenever database behavior
matters, use PostgreSQL Testcontainers and actual Flyway migrations. Reuse the existing
Testcontainers configuration. Do not replace PostgreSQL with an in-memory database
for query, constraint, migration, or transaction confidence.

## Meaningful coverage

Cover the success path and failures relevant to the change:

- Structural validation and important business invariants.
- Not-found and conflicting-state behavior.
- Anonymous requests and authenticated access to another owner's resource.
- Ownership scoping for lists, counts, nested resources, and downloads.
- Query filtering, deterministic pagination, constraints, and concurrency where relevant.
- Migration behavior with existing data when schema evolution requires it.
- Observable API status codes, DTO fields, and safe error responses.

Do not assert private method calls or incidental implementation details. A regression
test should reproduce the bug and fail without the fix. Avoid tests that merely
repeat getters, framework behavior, or low-impact documentation changes.

## Naming and fixtures

Name tests for behavior, for example `shouldReturnNotFoundWhenChildDoesNotExist()`.
Existing descriptive names need not be renamed merely to add a `should` prefix.
Use minimal fixtures with synthetic data, deterministic time when relevant, and
independent tests. Avoid sleeps, execution-order dependencies, and real external
services. Mock external integrations at their boundary unless testing that integration.

Transactional test rollback can hide commit-time constraints and effects. Flush or
commit deliberately when the behavior under test depends on them; use a real
transaction boundary for tests of service transaction semantics.

## Required verification

For application behavior changes, ensure Docker is available and run the committed
Maven Wrapper from the repository root:

```sh
./mvnw verify
```

Tests start a disposable PostgreSQL container independently of the local Compose
database. Dependencies and images may require network access on first use. A focused
test may help during development but does not replace the required verification.

For documentation-only changes, check local links, consistency with configuration
and code, and `git diff --check`; do not start Docker solely for documentation.

Report what was verified and any failures or blocked checks. Never report a test
suite as passing if it was skipped, could not start, or only ran partially. Broaden
checks when new evidence warrants it, not by repeatedly rerunning a passing suite.
