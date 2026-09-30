# Frontend Integration Context

Use this directory as the entry point when implementing or changing frontend
features that call BeeHome APIs. Read the relevant feature guide before building
its screens or client calls. These guides describe the backend's implemented
contract; they do not define frontend architecture or authorize missing API
behavior.

## Feature contracts

- [Authentication and current user](authentication.md)
- [Families](families.md)
- [Family members](family-members.md)
- [Planning](planning.md)
- [Daily execution and history](daily-execution.md)
- [Study tracking](study-tracking.md)
- [Photos and media](photos-media.md)
- [Books and reading](books-reading.md)
- [Calendar, history, and reports](calendar-history-reports.md)
- [Extracurricular activities](extracurricular-activities.md)
- [Profile scratchpad](profile-scratchpad.md)
- [Global tags](global-tags.md)

## How to use the contracts

1. Read the feature guide and follow its links to related API contracts.
2. Confirm routes, fields, authorization, validation, errors, and side effects
   against the guide and the backend implementation when details matter.
3. Inspect the frontend's existing routing, state, and HTTP-client conventions
   before making UI changes; frontend conventions belong with the frontend code.
4. Do not infer an endpoint or behavior that the backend does not document. If
   the UI needs unsupported behavior, identify the API gap for the product/backend
   work instead of fabricating a request.

Shared backend rules live in [API design](../api.md), [security](../security.md),
and [authentication](authentication.md). OpenAPI is useful for schema discovery,
while the feature guides remain the integration context, including client flows
and behavior that schemas alone do not express.
