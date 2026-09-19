# Persistence and Transactions

Read this guide for entities, queries, migrations, transactions, and concurrency.
Follow [AGENTS.md](../AGENTS.md).

## Existing foundation

PostgreSQL is the database. `V1__initialize_schema.sql` creates `nosso_dia`;
Flyway history resides in `public`. Keep application objects in the intended schema,
`spring.jpa.hibernate.ddl-auto=validate`, and `spring.jpa.open-in-view=false`.
Use Spring Data JPA where sufficient, without generic repository wrappers.

## Migrations

- Add versioned SQL files under `src/main/resources/db/migration`, following `V<number>__description.sql` and the existing version sequence.
- Qualify application schema names where needed rather than relying on an accidental search path.
- Never edit a migration already applied in a shared environment; create a new migration.
- Include required constraints and indexes with schema changes.
- For existing data, plan backfills before adding incompatible constraints or removing columns.
- Consider locks, data volume, and deployment compatibility before expensive or destructive changes.
- Do not use automatic Hibernate updates or Flyway clean as an operational migration strategy.

Test migrations on PostgreSQL. For changes affecting populated tables, test
representative existing data as well as an empty database. Flyway startup success
alone does not prove a production upgrade is safe. Document rollout and recovery
steps when a change needs them.

## Entities and integrity

Use UUID domain identifiers by default and follow a consistent generation strategy.
Choose that strategy when the first domain entity is introduced; do not mix approaches
without a reason. Entities must not depend on controllers, HTTP responses, or API
serialization. Define nullability, uniqueness, and referential integrity deliberately.
An ID-based reference can still require a database foreign key.

Application validation improves feedback; database constraints protect invariants
against concurrent writes. A pre-insert existence check alone cannot guarantee
uniqueness. Translate expected constraint failures safely at the application/API
boundary without exposing SQL details.

## Transactions and concurrency

Put transaction boundaries primarily in services, around a complete database use
case. Keep them short. Do not hold a transaction open across slow external calls
unless the requirement makes it necessary. Consider Spring proxy boundaries:
self-invocation does not ordinarily apply a method's transactional advice.

Use read-only transactions where helpful for read use cases, without treating the
flag as access control. Fetch and map data needed by response DTOs within an
appropriate persistence scope; do not enable Open Session in View to conceal lazy
loading problems.

For read-modify-write operations, determine whether lost updates matter. Use
optimistic locking, an atomic update, or explicit locking when justified, and define
how conflicts reach callers. Do not add retries blindly to non-idempotent operations.

## Query behavior

Avoid N+1 queries, accidental eager loading, large bidirectional graphs, and
unbounded results. Use focused projections, fetch joins, or entity graphs when the
query needs them; avoid paginating collection fetch joins without checking behavior.
Use indexes based on filtering, joining, and ordering needs rather than indexing
every column. Scope private-resource queries to authorized ownership where practical.

Test constraints, custom queries, ordering, pagination, and relevant transaction
behavior against PostgreSQL Testcontainers. Do not substitute H2 for PostgreSQL
behavior. See [Testing](testing.md).
