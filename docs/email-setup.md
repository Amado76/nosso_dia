# Complete password recovery email setup

The backend already implements SMTP delivery and password updates in PostgreSQL.
Real email delivery is pending provider configuration and a working frontend
reset page. No provider, sender address, or frontend URL has been chosen yet.

Use this checklist when those decisions are available. The
[authentication contract](authentication.md#password-reset-delivery) is the
reference for API behavior and all delivery settings.

## 1. Collect the missing information

| Information | Current status | Where it will be used |
| --- | --- | --- |
| SMTP provider | Pending | Obtain SMTP access and sending requirements |
| SMTP hostname and port | Pending | `MAIL_HOST`, `MAIL_PORT` |
| SMTP username and password | Pending; store privately | `MAIL_USERNAME`, `MAIL_PASSWORD` |
| Provider TLS mode | Pending | STARTTLS or implicit TLS settings below |
| Authorized sender address | Pending | `AUTH_RESET_MAIL_FROM` |
| Sender/domain verification | Pending | Complete the chosen provider's verification process and required DNS records |
| Frontend reset page URL | Pending | `AUTH_RESET_URL` |
| Deployment environment | Pending | Store settings and secrets where the backend runs |

Choose a provider with SMTP support. Use its current instructions to authorize
the sender/domain and obtain credentials. SMTP credentials may differ from the
provider dashboard login. Check any sandbox restrictions on recipients before
testing with real accounts.

Do not put credentials in this document, source control, tickets, or chat.
Keep them in an untracked local `.env` for development or the deployment's secret
store. The sender value must be a single email address without a display name.

## 2. Implement and publish the frontend reset page

The backend sends a link in this format:

```text
https://<frontend-host>/<reset-page>?token=<reset-secret>
```

The frontend must:

- Read the `token` query parameter, retain it only as needed, and remove it from
  the visible URL/history. Do not send it to analytics or logs; use a no-referrer
  policy on the page.
- Ask for the new password and submit `token` and `newPassword` to
  `POST /api/auth/reset-password`, without a Bearer header.
- Follow the password policy and display validation errors from the API.
- Handle expired/used links by offering a new recovery request.
- On success, clear locally stored session credentials and direct the user to login.

See the [password recovery API](authentication.md#password-recovery) for JSON,
status codes, and token handling. Opening a link must not itself reset the password.
The backend does not serve this frontend page.

Set `AUTH_RESET_URL` to the page's base URL, without `?token=`, other query
parameters, a fragment, or embedded credentials. HTTPS is required except for
local development at `http://localhost` or `http://127.0.0.1`.

If the frontend and API use different origins, configure the agreed CORS origins
or a same-origin reverse proxy before testing in a browser. CORS is not globally
enabled by the backend.

## 3. Configure the application environment

Start from the mail variables in [.env.example](../.env.example).
Keep `AUTH_RESET_MAIL_ENABLED=false` during local development until the required
values are available. With that flag disabled, local recovery requests still
return a generic success response, but no email is sent.

Replace every placeholder below before using this example:

```dotenv
AUTH_RESET_MAIL_ENABLED=true
AUTH_RESET_MAIL_FROM=<authorized-sender-address>
AUTH_RESET_URL=https://<frontend-host>/<reset-page>
MAIL_HOST=<smtp-hostname>
MAIL_PORT=587
MAIL_USERNAME=<smtp-username>
MAIL_PASSWORD=<smtp-password>
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLED=true
MAIL_STARTTLS_REQUIRED=true
MAIL_SSL_ENABLED=false
```

Use the provider's specified port and TLS mode:

| Mode | Typical port | `MAIL_STARTTLS_ENABLED` | `MAIL_STARTTLS_REQUIRED` | `MAIL_SSL_ENABLED` |
| --- | --- | --- | --- | --- |
| STARTTLS | 587 | `true` | `true` | `false` |
| Implicit TLS | 465 | `false` | `false` | `true` |

Keep authentication enabled when the provider requires credentials. Connection,
read, and write timeouts are already configured to five seconds.

For local execution, update the existing `.env` without overwriting unrelated
settings, export its variables, and restart the application using the
[local execution instructions](../README.md#run-locally). A Java process does not
automatically load `.env`.

For Docker Compose, the variables are already forwarded to the app. Recreate the
app container after updating them using the
[Docker instructions](../README.md#run-everything-in-docker); restarting an existing
container alone does not apply changed environment values.

For a deployed environment, set the variables in its configuration/secret store
and redeploy or restart the application with the new environment. Also complete
the [authentication deployment requirements](authentication.md#deployment),
including a persistent `AUTH_SIGNING_KEY` and `AUTH_ALLOW_EPHEMERAL_KEY=false`.
Disabling SMTP is not a production fallback: without the explicit development
flag or an enabled delivery adapter, application startup fails.

## 4. Validate the complete flow

Use a test account and mailbox you control in the target environment:

1. Register the account with `POST /api/auth/register`, then log in.
2. Request recovery with `POST /api/auth/forgot-password` using its email address.
3. Confirm that the email arrives, including checking the spam folder. Verify
   the sender and that the link points to the intended frontend environment.
4. Open the link and submit a valid new password through the frontend.
5. Confirm that login works with the new password and rejects the previous one.
6. Confirm that submitting the same recovery token again fails and that old
   refresh tokens can no longer renew sessions.

A `200` recovery response does not prove delivery: unknown accounts and delivery
failures receive the same response. The default cooldown is five minutes
(`AUTH_RESET_COOLDOWN`); wait before requesting another email for the same account.
Reset links expire 30 minutes after issuance by default (`AUTH_RESET_TTL`).

The authenticated change-password API works independently of email delivery.
Use `POST /api/auth/change-password` with a Bearer token, `currentPassword`, and
`newPassword`; see the [authentication API](authentication.md) for the full contract.

## 5. If an email does not arrive

| Observation | What to check |
| --- | --- |
| Startup fails after enabling email | Nonempty host, valid sender, valid reset URL, and authentication deployment settings |
| Recovery returns success with no message | SMTP enabled in the running process; account registered; cooldown elapsed |
| `Password reset email delivery failed` warning | Provider credentials, port/TLS mode, authorized sender, outbound SMTP connectivity, and provider delivery status |
| `Password reset email queue is full or shutting down` warning | Provider availability and application load; wait before requesting again |
| Provider accepts the message but inbox is empty | Spam folder, sender/domain verification, provider sandbox restrictions, and provider delivery status |
| Email arrives but the page does not work | Frontend deployment, configured URL, browser-to-API connectivity, and token expiry |

Do not enable protocol debug output or log email contents/tokens to diagnose
delivery. The queue is in memory and has no automatic retry; restarts can lose
queued messages. After resolving delivery problems, wait for the cooldown and
request a new recovery email.

## Completion checklist

- [ ] Provider chosen and SMTP access available.
- [ ] Sender/domain authorized by the provider.
- [ ] Frontend reset page deployed and its URL confirmed.
- [ ] Environment settings and secrets configured.
- [ ] Application restarted/recreated with the new settings.
- [ ] Email received in a controlled real mailbox.
- [ ] Password reset, login, and token invalidation verified through the frontend.

Automated PostgreSQL and loopback SMTP tests already cover the backend flow.
They do not validate an external provider account, real inbox delivery, or the
deployed frontend. Mark this setup complete only after the real environment
checks above succeed.
