# Security and Configuration

Read this guide for authentication, authorization, private data, configuration,
uploads, and downloads. Follow [AGENTS.md](../AGENTS.md).

## Current authentication boundary

The foundation requires HTTP Basic authentication for every route, including API
documentation, and retains CSRF protection. Credentials come from configuration.
This is a temporary development setup, not the final product authentication flow.
Do not assume JWT, OAuth, sessions, registration, or a membership model before
requirements define them. Production authentication remains an open decision.

Do not disable CSRF or broadly allow CORS simply to make a client call pass.
Choose CSRF behavior from the actual credential transport and browser threat model;
HTTP Basic alone does not eliminate CSRF concerns. Configure CORS only for needed
origins, methods, and credentials when the integration is known.

## Authorization

Authentication establishes identity; authorization decides whether that identity
may perform a specific operation on a specific resource. Enforce both on the backend.
Implement authorization together with private resources, not as later hardening.
If ownership or membership rules are missing, establish them before exposing data.

- Obtain the acting identity from trusted security context, not a client-supplied user ID.
- Validate access to individual resources, nested parent/child relationships, lists, updates, deletes, reports, and file downloads.
- Apply ownership filters before pagination or aggregation so counts and lists cannot leak other users' data.
- Prevent input from reassigning ownership or privileges outside explicitly authorized operations.
- A UUID is an identifier, not authorization. Frontend checks are only a user experience feature.
- Use a consistent forbidden/not-found policy to avoid disclosing private resource existence.

Test anonymous access, valid access, and access by another authenticated user.
Keep application-level rules usable outside HTTP controllers.

## Secrets, configuration, and logs

Keep deployment-specific configuration outside business logic. Use Spring
configuration, environment variables, and profiles when useful; do not create
unused profiles in advance. Document added variables and safe placeholders in
`.env.example`. Existing example credentials are local-only.

Never commit real credentials, tokens, API keys, or private files. Never log them
or sensitive personal information. Avoid logging complete request bodies by default.
Log meaningful events using safe resource identifiers and actionable failure context;
avoid routine entering/leaving-method noise. Error responses must omit internal
details. The generated Spring development password is a bootstrap mechanism,
not a pattern to reproduce in application logging.

## Files and external integrations

When implementing uploads, define size limits, allowed types, and ownership rules.
Do not trust filenames or declared content types. Generate storage keys on the
server and prevent path traversal. Keep private objects private and authorize
downloads, including any signed-link issuance; choose bounded link lifetimes.
Additional content inspection depends on the actual use case.

Keep provider-specific SDK calls behind a small boundary when warranted. Set
external-call timeouts and decide retry behavior from idempotency requirements.
Do not hold database transactions open during uploads by default. Define cleanup
for partial failures as described in [Architecture](architecture.md).
