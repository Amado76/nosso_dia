# Architecture and Java

Read this guide for feature design, module boundaries, Java conventions, storage,
or reporting. [AGENTS.md](../AGENTS.md) defines the mandatory constitution;
[README.md](../README.md) describes the current foundation.

## Feature ownership

Organize code under `com.nossodia` by business capability. Names such as `children`,
`family`, and `routine` are examples until product requirements establish them.
Do not generate empty modules in anticipation of future work.

Feature packages MUST follow the responsibility-based layout of `auth`. Place
HTTP controllers in `controller`, use-case services in `service`, persistence
interfaces in `repository`, entities and domain enums in `entity`, request/response
contracts and their validation in `dto`, and top-level application exceptions in
`exception`. Feature-specific infrastructure belongs in focused packages such as
`auth.security` and `auth.mail`.

Apply this layout consistently to every feature, including `family`, `user`, and
`health`. Create only the packages needed by existing classes: a health endpoint
may have just `controller` and `dto`. Do not create artificial services or
repositories merely to match a template. Controllers still delegate business
decisions and persistence access to services. Do not introduce application-wide
technical layer packages or place top-level feature classes in the feature root.
See the [package structure](../README.md#package-by-feature).

Cross-package access requires public collaborator types and the constructors or
methods called by another responsibility package. Keep entity fields private,
retain protected JPA no-argument constructors, and expose focused domain operations
rather than mutable public state. Tests local to a responsibility package mirror
its production package when they need package-private access; application-wide
integration tests may remain in the application test package.

## Dependencies between features

Before adding a dependency, consider in order:

1. Whether an identifier is sufficient.
2. Whether the operation belongs to the other feature.
3. Whether a focused query in the owning feature can provide the required data.
4. Whether a coordinator would clarify a use case spanning several features.
5. Whether an event suits a genuinely asynchronous side effect.

A simple direct call is acceptable when its responsibility is clear and stable.
Avoid cycles and long service chains. Do not inject another feature's repository
as a shortcut around its invariants or authorization. Read queries spanning
features may be justified; define their owner and keep persistence details internal.
Use package visibility where practical, without adding a module framework by default.

Prefer IDs across features when practical. IDs reduce object coupling but do not
remove referential integrity requirements. Introduce JPA associations intentionally,
with explicit ownership, fetch behavior, cascade behavior, and deletion semantics.

## Java conventions

- Prefer constructor injection and final dependency fields in application code.
- Use records for simple immutable DTOs when appropriate, not for JPA entities.
- Map DTOs explicitly using ordinary Java; extract a feature-local mapper if repetition justifies it.
- Allow domain behavior in entities when it naturally belongs there.
- Do not automatically introduce interfaces, service implementation pairs, factories, adapters, strategies, builders, events, or generic base classes.
- Do not add Lombok initially. Reconsider it or MapStruct only for a demonstrated maintenance problem.
- Name classes after their business responsibility. Avoid vague names such as `Manager`, `Helper`, or `Utils` unless accurate.
- Explain non-obvious decisions in comments; do not narrate obvious code.

Use `LocalDate` for calendar dates, `Instant` for absolute event timestamps, and
`OffsetDateTime` when an offset is part of the contract. Avoid legacy date APIs.
When local scheduling matters, explicitly establish the relevant timezone with
product requirements. Inject a Java `Clock` when time-dependent logic needs
repeatable tests; do not build a custom time framework.

## Shared code

Keep reusable exception handling, security infrastructure, configuration, or
pagination support in `shared` only when genuinely shared. Keep feature-specific
rules in the feature. Existing bootstrap configuration may remain where it is;
do not move it as an unrelated cleanup.

## Files and reports

Use object storage for binary files unless a requirement explicitly justifies
relational storage. Persist metadata such as ID, owner ID, storage key, content type,
size, and creation timestamp. Introduce a small storage interface when a real
integration needs it; keep provider SDK details out of business logic.

Database and object-store writes are not one atomic transaction. When implementing
uploads or deletion, define failure handling and cleanup for partial completion.
See [Security](security.md) for upload and download authorization.

Reports read domain/application data. They must not become the primary store of
source information. Select synchronous generation or background processing based
on actual duration and requirements, not assumed future scale.

## Evolution

Prefer a little duplication to an abstraction coupling unrelated features.
Optimize measured bottlenecks while avoiding obvious unbounded queries and N+1 reads.
For a significant architectural decision, document the problem, chosen solution,
relevant alternatives, and consequences alongside the affected guide. Do not add
a decision framework or infrastructure solely to document a small implementation choice.
