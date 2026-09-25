# Authentication and User Foundation (PDR-01)

PDR-01 establishes an internal UUID user identity. Family roles, membership,
ownership rules, and generic permissions are outside this implementation.
Authentication is enforced by default for future routes.

This is the UI–API integration contract for authentication and current-user
retrieval, for frontend developers and agents. Update it whenever these APIs
change. See [local setup](../README.md#run-locally) and the shared
[error contract](api.md#errors).

## Connection and headers

The default local base URL is `http://localhost:8080`; use the configured
application port and the deployment's API origin. Routes below include `/api`.
All POST bodies are JSON objects: send `Content-Type: application/json`.
Successful bodies use `application/json`; errors use `application/problem+json`.
The logout success response has no body. Clients must handle both JSON media
types and must not call a JSON parser on a 204 response.

Send `Accept-Language: en`, `pt`, or `es` for backend-owned messages; regional
preferences such as `pt-BR` work, and unsupported/missing preferences fall back
to English. Protected calls require `Authorization: Bearer <accessToken>`.
Omit Authorization on public calls, especially refresh: an expired/invalid
Bearer header can be rejected even on a public endpoint.

There are no cookies or server sessions. Browser cross-origin access is not
configured. Use a same-origin reverse proxy or agree on explicit backend CORS
configuration before connecting a UI on another origin; a successful curl call
does not establish browser CORS support. Use HTTPS outside local development.

## API

| Method and route | Request | Success | Authentication |
| --- | --- | --- | --- |
| `POST /api/auth/register` | `name`, `email`, `password` | 201: `id`, `name`, `email` | Public |
| `POST /api/auth/login` | `email`, `password` | 200: token response | Public |
| `POST /api/auth/refresh` | `refreshToken` | 200: new token response | Public |
| `POST /api/auth/logout` | `refreshToken` | 204 | Bearer; session must belong to caller |
| `GET /api/users/me` | None | 200: `id`, `name`, `email` | Bearer |
| `POST /api/auth/forgot-password` | `email` | 200: localized `message` | Public |
| `POST /api/auth/reset-password` | `token`, `newPassword` | 200: localized `message` | Public |
| `POST /api/auth/change-password` | `currentPassword`, `newPassword` | 200: localized `message` | Bearer; current user only |
| `GET /api/health` | None | 200: `status` | Public |

Token responses contain `accessToken`, `refreshToken`, `tokenType=Bearer`, and
`expiresIn` (access lifetime in seconds). Registration does not automatically log
in. Logout requires the current access token and the relevant refresh token.
Repeating logout on an already-revoked token owned by the caller succeeds.

Expected application and security errors use the existing RFC 9457 contract and
stable `code`. Login uses `INVALID_CREDENTIALS` for missing accounts, incorrect
passwords, and accounts without a local password. Unknown accounts still perform
a password verification against a dummy hash. Password reset uses
`INVALID_RESET_TOKEN` or `EXPIRED_RESET_TOKEN`; refresh uses
`INVALID_REFRESH_TOKEN`. Duplicate registration returns 409 with
`EMAIL_ALREADY_REGISTERED`. Missing/invalid Bearer authentication returns 401
`UNAUTHENTICATED`; access denial returns 403 `FORBIDDEN`.

Authentication errors, validation, and success messages support `en`, `pt`, `es`,
regional fallback, and English for unsupported languages. Use `Accept-Language`.
Never branch on translated text. Malformed JSON and other framework errors retain
the existing framework error contract.

## Request fields and validation

All fields listed below are required, non-null JSON strings. Blank values fail
validation. There are no optional fields or request defaults, path parameters,
query parameters, pagination, or sorting on these endpoints. `GET /api/users/me`
and `GET /api/health` take no request body.

| Endpoint | Field | Validation and normalization |
| --- | --- | --- |
| Register | `name` | Nonblank, maximum 120 characters before trimming; leading/trailing whitespace is stripped when saved |
| Register, login, forgot-password | `email` | Leading/trailing whitespace is stripped before validation; valid email, maximum 254 characters; lowercased for storage/lookup |
| Register | `password` | 8–128 characters, at least one uppercase letter, one digit (0–9), and one special character (Unicode punctuation or symbol); never trimmed |
| Login | `password` | Nonblank, maximum 128 characters; no minimum-length constraint beyond nonblank; never trimmed |
| Refresh, logout | `refreshToken` | Nonblank, maximum 256 characters; send the exact returned value |
| Reset-password | `token` | Nonblank, maximum 256 characters; send the exact delivered reset secret |
| Reset-password, change-password | `newPassword` | Same rules as registration password |
| Change-password | `currentPassword` | Nonblank, maximum 128 characters; never trimmed |

Invalid registration, reset, or new change passwords, including missing, null, and blank values,
produce one entry for the password field in `errors`, listing the complete password
requirements without exposing the submitted value. For English, the message is:
"Password must have 8 to 128 characters, including at least one uppercase letter,
one number, and one special character." The UI should display this field message;
`detail` retains the generic validation summary.

Length limits follow Java string length (UTF-16 code units). UI checks improve
feedback, but the backend remains authoritative. A reset token, refresh token,
and access token are different credentials and cannot be substituted.

## Request and response examples

The values below are examples; token placeholders are not usable credentials.
All success response fields shown are present and non-null. `id` is a UUID string;
names, emails, messages, and tokens are strings. `expiresIn` is an integer number
of seconds, not an absolute timestamp; `tokenType` is always `Bearer`.

### Register and retrieve the current user

`POST /api/auth/register` (public):

```json
{"name":"Alex Example","email":"alex@example.com","password":"ExamplePass123!"}
```

Returns `201 Created` with the following shape. `GET /api/users/me`, with a
valid Bearer token, returns the same shape with `200 OK` for the authenticated
user. The UI does not supply a user ID.

```json
{"id":"77e588c9-0200-4d28-a463-1b72610e1b0b","name":"Alex Example","email":"alex@example.com"}
```

Registration creates the account without tokens. Continue to login to establish
a session. No email-verification step or endpoint is implemented.

### Login and refresh

`POST /api/auth/login` (public):

```json
{"email":"alex@example.com","password":"ExamplePass123!"}
```

Returns `200 OK`:

```json
{"accessToken":"<access-jwt>","refreshToken":"<refresh-secret>","tokenType":"Bearer","expiresIn":900}
```

`900` reflects the default configuration; use the returned value. Login does not
return a user profile; call `/api/users/me` after login when needed.

`POST /api/auth/refresh` (public, without a Bearer header):

```json
{"refreshToken":"<refresh-secret>"}
```

Returns `200 OK` with the same token-response shape and a **new pair** of tokens.
Replace both stored values together. The old refresh token is consumed.

### Logout

`POST /api/auth/logout`, with the current Bearer access token:

```json
{"refreshToken":"<current-refresh-secret>"}
```

Returns `204 No Content`. The refresh session must belong to the authenticated
user. Logout revokes only that refresh session; other sessions remain active.
Repeating the call with an already-revoked token belonging to the same user
succeeds while Bearer authentication remains valid. Clear client credentials
after logout. Existing access JWTs remain valid until they expire.

### Password recovery

`POST /api/auth/forgot-password` (public):

```json
{"email":"alex@example.com"}
```

Returns `200 OK`; with `Accept-Language: en`:

```json
{"message":"If an account exists for this email, password reset instructions have been sent."}
```

Show the same confirmation for any valid email. This response does not prove
account existence or delivery. It never includes a reset token. The default
local delivery adapter discards notifications when SMTP is disabled. Enable and
configure [SMTP delivery](#password-reset-delivery) for a user-facing recovery flow.

`POST /api/auth/reset-password` (public):

```json
{"token":"<delivered-reset-secret>","newPassword":"AnotherPass123!"}
```

Returns `200 OK`; with `Accept-Language: en`:

```json
{"message":"Your password has been reset."}
```

Success consumes all outstanding reset tokens and revokes all refresh sessions
for that user. Clear the current UI session and direct the user to login again;
this call does not issue new tokens. Existing access JWTs expire naturally.

### Change the authenticated user's password

`POST /api/auth/change-password` requires `Authorization: Bearer <accessToken>`:

```json
{"currentPassword":"ExamplePass123!","newPassword":"AnotherPass123!"}
```

Both fields are required, non-null strings. The current password must match the
authenticated user's local password; accounts without a local password receive
`400 INVALID_CURRENT_PASSWORD` and must use password recovery. Identity comes
only from the Bearer token; the client does not supply an email or user ID.
A valid current password is required even with a valid access token.

Returns `200 OK`; with `Accept-Language: en`:

```json
{"message":"Your password has been changed."}
```

The new password is hashed and saved in PostgreSQL. The same transaction consumes
all outstanding reset links and revokes every refresh session for this user,
including the caller's session. Other users are unaffected. No new tokens are
issued. Clear stored credentials and ask the user to log in with the new password.
Existing access JWTs remain usable until expiry (15 minutes by default).

Invalid input or a wrong current password makes no changes. Reusing the current
password as the new password is allowed if it meets the password policy. Concurrent
changes recheck the original hash under the user lock: at most one succeeds.
Do not automatically retry after an ambiguous timeout: a committed change can
make the previous current password invalid. Try logging in with the new password
or use recovery. The same authentication POST rate limit applies; there are no
path/query parameters, pagination, or additional side effects.

### Health

`GET /api/health` is public and returns `200 OK` with `{"status":"UP"}`.
It reports application responsiveness, not database readiness or session status.

## Errors and UI handling

| HTTP | Stable `code` | Applies to / suggested UI handling |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Any POST: show field errors and allow correction |
| 409 | `EMAIL_ALREADY_REGISTERED` | Register: offer login or recovery |
| 401 | `INVALID_CREDENTIALS` | Login: show a generic email/password error |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh or logout: unknown token; refresh also rejects expired/revoked tokens, logout rejects another user's token; require login when renewal fails |
| 400 | `INVALID_RESET_TOKEN` | Reset: unknown/already-consumed token; offer a new recovery request |
| 400 | `INVALID_CURRENT_PASSWORD` | Change-password: ask for the current password again; offer recovery if unavailable |
| 400 | `EXPIRED_RESET_TOKEN` | Reset: offer a new recovery request |
| 401 | `UNAUTHENTICATED` | Missing/invalid/expired Bearer credentials or unavailable current user; attempt bounded renewal when appropriate |
| 403 | `FORBIDDEN` | Access denied: show an access error; do not loop through refresh |
| 429 | `RATE_LIMITED` | Auth POSTs: respect `Retry-After: 60` before another attempt |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected application failure: show a generic failure; do not assume a mutation was rolled back |

An English validation response has this shape (field errors can appear in any
order, with more than one error per field):

```json
{
  "type":"about:blank",
  "title":"Validation failed",
  "status":400,
  "detail":"Validation failed",
  "instance":"/api/auth/register",
  "code":"VALIDATION_ERROR",
  "errors":[{"field":"email","message":"Enter a valid email address of at most 254 characters"}]
}
```

The `errors` array is specific to validation failures. Other application/security
errors use the same common fields and their respective code, without that array.
Treat human-readable text as localized display text, not a stable identifier.
Framework failures such as malformed JSON, unsupported media types, or wrong
HTTP methods may omit `code` and `errors`; fall back to HTTP status and a generic
UI message. Network/proxy failures may have no JSON body at all.

## UI session flow and retries

1. Register if needed, then login and retain the returned token pair using the
   client's agreed secure storage strategy. This API does not prescribe browser
   persistence; keep secrets out of URLs, logs, and analytics.
2. Use the access token for protected requests and load `/api/users/me` for the
   profile. Use `expiresIn` to plan renewal, while treating server responses as
   authoritative.
3. Coordinate a single refresh request per session across concurrent requests
   and browser tabs. Rotation is single-use: two requests using the same refresh
   token cannot both succeed. Atomically replace the pair before releasing
   waiting requests; prevent an in-flight refresh from restoring a logged-out UI.
4. On a protected request's 401, attempt renewal once if a refresh token exists.
   A failed renewal requires login. Retry the protected call at most once when
   safe; never recursively refresh login/refresh failures.
5. For logout with an expired access token, renew first if possible and submit
   the newly returned refresh token with the new access token. If the network
   prevents server logout, local sign-out can clear credentials but must not be
   represented as confirmed server revocation.

There are no idempotency keys. Do not blindly retry registration, login, refresh,
or reset after an ambiguous timeout: registration may already have created the
account, login may have issued a session, and refresh/reset may have consumed
the credential. If a successful rotation's response is lost, the old refresh
token cannot recover the new pair; login again. A reset retry may return
`INVALID_RESET_TOKEN` even if the first call changed the password. Forgot-password
can be requested again subject to cooldown/rate limits, with the same generic
response. GET requests and same-user logout can be retried with valid credentials.

Default lifetimes are 15 minutes for access, 30 days for refresh (renewed on each
rotation), and 30 minutes for reset; the reset-request cooldown is 5 minutes.
These are configurable, and refresh/reset expiry timestamps are not returned.
All authentication POSTs share a default limit of 30 requests/minute/IP, including
logout and refresh; `/api/users/me` is outside this auth limiter.

There are no Google/Apple login routes, account-linking routes, profile editing,
user listing, or logout-all endpoint. Do not expose UI flows that assume them.

## Credentials and tokens

Email is stripped and lowercased with `Locale.ROOT` on registration and lookup.
A database unique constraint is the final concurrent-write guarantee. Names are
required and limited to 120 characters; email is limited to 254. New passwords
must contain 8–128 characters, including at least one uppercase letter, one digit
(0–9), and one special character (Unicode punctuation or symbol). Lowercase letters
are optional; whitespace does not count as a special character. Passwords are not
trimmed or otherwise normalized.

Spring Security's delegating password encoder uses its PBKDF2 v5.8 configuration
(salted PBKDF2-HMAC-SHA256, 310,000 iterations, 256-bit result). Password hashing
runs outside database locks. Tune its cost with deployment measurements before
changing it; the encoding identifier supports future upgrades.

Spring Security's OAuth2 resource server verifies HS256 signatures, issuer,
audience, expiry, and UUID subject before constructing the security context.
JWT claims are limited to internal subject, issuer, audience, issued/expiry times,
and token ID. No names, emails, provider identities, or business permissions are
included. Token verification uses zero expiry tolerance; keep server clocks synced.

Refresh and reset secrets are independent 256-bit values from `SecureRandom`.
Only their SHA-256 hashes are persisted. SHA-256 is appropriate for these random
secrets; user passwords use the dedicated slow password encoder.

Refresh rotation revokes the old token and records its replacement atomically.
Replays fail; they do not revoke the replacement session. Each rotation starts a
new configured refresh lifetime. Logout revokes one session. Password reset and
authenticated password change update the hash, consume every outstanding reset
token for that user, and revoke every refresh session in one transaction. Access tokens expire naturally.
There is no access-token denylist.

Login session issuance, rotation, logout, recovery issuance, reset, and password
change serialize through a user-row lock. Token rows are locked after the user row. Login rechecks
the password hash after taking that lock, so a concurrent reset cannot result in
a new session authenticated with an obsolete password. Concurrent refresh/reset
tests prove that at most one request consumes a token.

The API accepts credentials in JSON and explicit Authorization headers, never
cookies, Basic authentication, or server sessions. CSRF is disabled for this
transport. Adding cookie authentication later requires revisiting CSRF. CORS is
not opened globally. Use TLS in deployment and secure client storage.

## Password reset delivery

To finish provider and frontend configuration later, follow the
[email setup checklist](email-setup.md).

`PasswordResetDelivery` is called after the database transaction commits. The SMTP
adapter uses Spring Boot's mail starter and sends a plain-text English email with
subject `Reset your BeeHome password`, a single-use link, and the configured
token lifetime. API messages still follow `Accept-Language`; email translation
is not implemented. The SMTP provider is configurable and no vendor is required.

Configure these variables in the application environment (also forwarded by
Compose). Do not commit credentials:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AUTH_RESET_MAIL_ENABLED` | `false` | Set `true` to use real SMTP delivery, including in development |
| `AUTH_RESET_MAIL_FROM` | Empty | Required single sender email address, authorized by your provider |
| `AUTH_RESET_URL` | Empty | Required frontend reset page URL; HTTPS, no query, fragment, or embedded credentials; HTTP allowed only for localhost/127.0.0.1 |
| `MAIL_HOST` | Empty | Required SMTP server hostname when delivery is enabled |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Empty | Provider credentials, required when the provider requires authentication |
| `MAIL_SMTP_AUTH` | `true` | SMTP authentication; disable only for a local mail catcher or an explicitly unauthenticated relay |
| `MAIL_STARTTLS_ENABLED`, `MAIL_STARTTLS_REQUIRED` | `true` | Require STARTTLS by default |
| `MAIL_SSL_ENABLED` | `false` | For implicit TLS, typically port 465: set true and set both STARTTLS flags false |

Connection, read, and write timeouts are each five seconds. TLS server identity
verification is enabled; mail protocol debug output is disabled. Use your provider's
documented TLS mode and configure the authorized sender/domain with that provider.
Startup validates the host, sender, and reset URL; SMTP credentials and deliverability
are checked only when sending. The implementation follows
[Spring Boot email configuration](https://docs.spring.io/spring-boot/4.1/reference/io/email.html).

The link is `AUTH_RESET_URL?token=<URL-encoded-reset-secret>`. For example, a reset
URL of `https://app.example.com/reset-password` produces
`https://app.example.com/reset-password?token=<secret>`. This URL is deployment
configuration, never derived from request headers. The frontend must implement
that page: read the token, remove it from the visible URL/history, collect the new
password, and submit it to `POST /api/auth/reset-password`. Avoid logging or
sending the URL/token to analytics and use a no-referrer policy on that page.
Opening the link does not consume the token; only a successful reset does.
The backend does not serve the frontend reset page.

Submission is asynchronous with two workers and a queue of at most 100 messages
per process. Requests do not wait for SMTP. Queue saturation or provider failure
emits a fixed warning without addresses, tokens, credentials, or provider exception
details. These warning events can be counted by log monitoring; no metrics endpoint
is introduced. Queue entries are in memory: process termination can lose messages.
Graceful shutdown waits up to 15 seconds. There is no durable outbox or automatic
retry, so SMTP acceptance does not guarantee inbox delivery.

Known and unknown valid emails, queue rejection, and provider failure retain the
same HTTP status and message. Tokens are never returned in API responses. A failed
delivery still leaves the issued token and the database-backed cooldown in place;
wait for `AUTH_RESET_COOLDOWN` (five minutes by default) before requesting again.
Token expiry starts at issuance, so queue delays reduce the remaining link lifetime.
The integration test uses a loopback SMTP receiver and PostgreSQL to verify the
delivered link through a successful reset and subsequent login.

When `AUTH_RESET_MAIL_ENABLED=false`, only explicit local development
(`AUTH_ALLOW_EPHEMERAL_KEY=true`) has a discard adapter. Without that development
flag or SMTP delivery, startup fails. The development adapter never prints or
retains tokens. The example environment leaves SMTP disabled until the sender,
provider, and frontend URL are configured.

## Google and Apple preparation

`external_identities` links `provider + provider_subject` to an internal user ID,
with a database uniqueness constraint. Provider email is not stored as the identity
key. Multiple provider identities can reference one user; local password hashes
may be null. `ExternalIdentityService.resolve` accepts a Spring Security verified
`OidcUser`, verifies the expected issuer, and looks up the stable OIDC subject.
Unknown subjects return `EXTERNAL_IDENTITY_NOT_LINKED`; they never create, merge,
or link accounts based on email. Both provider mappings are tested offline.

The Spring OAuth2 client dependency provides the eventual protocol implementation.
Authorization redirects, callbacks, token exchange, automatic provisioning, and
account-linking endpoints are deliberately not enabled. Completing them requires
the client redirect/handoff and secure linking requirements. Use Spring Security
`oauth2Login`/client registration support, including its state/nonce validation;
do not expose `resolve` as an endpoint accepting arbitrary subjects or token claims.

Future Google client credentials belong in Spring client-registration configuration
backed by environment variables. Apple client IDs, team/key IDs, private keys, and
client-secret generation belong in authentication infrastructure backed by secret
storage. No provider credentials or network discovery are required at startup today.
Business modules depend only on the authenticated internal user ID.

## Abuse protection and retention

A bounded in-process limiter permits `AUTH_REQUESTS_PER_MINUTE` POST requests per
remote IP across authentication endpoints in a fixed minute. Excess requests
return localized 429 `RATE_LIMITED` and `Retry-After: 60`. The map holds at most
10,000 active IP buckets and rejects new buckets when full. Expired buckets are
removed. This includes registration to bound expensive password hashing.
Forgot-password additionally enforces `AUTH_RESET_COOLDOWN` per existing account
without changing the public response.

This limiter is suitable as a single-instance baseline, not a distributed attack
defense. Restarts reset the IP counters, fixed windows allow boundary bursts, and
clients behind NAT share a bucket. It ignores untrusted forwarding headers.
Behind a proxy, configure trusted client-address handling and apply coordinated
IP/request/body-size limits at the ingress, especially before running replicas.
Do not trust arbitrary `X-Forwarded-For` or add Redis solely for authentication.

Operational retention should delete expired/consumed reset records and old refresh
records in bounded batches. Delete refresh replacement chains in a way that
respects the replacement foreign key. No scheduled deletion job is introduced
before a retention policy exists. Indexed lookups remain bounded.

## Deployment

Configuration variables are listed in [.env.example](../.env.example) and forwarded
by [Compose](../compose.yaml). Defaults are 15-minute access, 30-day refresh,
30-minute reset tokens, a 5-minute reset cooldown, and 30 auth requests/minute/IP.
Access lifetime must be between one second and one hour; other durations must
be positive. HTTP Basic and the generated Spring development user are removed.

Local development explicitly sets `AUTH_ALLOW_EPHEMERAL_KEY=true`. Each restart
invalidates previous access tokens because the signing key changes. Persisted
refresh tokens still work after restart and can issue new access tokens.
Local reset delivery is discarded unless `AUTH_RESET_MAIL_ENABLED=true` is configured.

Outside development, configure `AUTH_SIGNING_KEY` as Base64 encoding of at least
32 cryptographically random bytes and enable/configure SMTP delivery as described
above. The application fails startup without the signing key or delivery configuration. Keep the same key across
replicas, keep it out of source control and logs, and rotate it deliberately:
replacing this single active key invalidates all existing access JWTs. Refresh
sessions remain valid. Multiple simultaneous signing keys are not implemented.

`AUTH_PUBLIC_DOCS` defaults to false; local `.env.example` opts in. Disable Swagger
and API-doc generation entirely in production if appropriate using Springdoc
configuration. Swagger advertises the Bearer scheme for `/api/users/me`, logout, and password change.

Flyway V2–V4 create the user and authentication tables. Hibernate validates them;
it never creates or updates the schema. All IDs are application-generated UUIDs,
cross-feature user references have foreign keys, and timestamps use timezone-aware
PostgreSQL columns. No migration changes existing foundation data.

## Verification

Run `./mvnw verify` with Docker available. PostgreSQL Testcontainers covers HTTP
flows, constraints, expiration, single-use/rotation, session ownership, concurrent
consumption, localization, and external identity mapping without provider calls.

Framework references: [Spring password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
and [Spring JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
