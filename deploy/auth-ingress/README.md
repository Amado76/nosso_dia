# Authentication ingress for multiple app replicas

This Compose example runs one TLS NGINX ingress in front of any number of BeeHome
app replicas. NGINX applies one authentication POST limit across those replicas;
the app retains its bounded local limiter. PostgreSQL and app ports are not
published. The ingress binds to `127.0.0.1:8443` by default so it can be checked
locally before changing the bind address.

The app pins Flyway's history schema to `public`, so another replica finds the
same migration history after the `beehome` schema is created.

## Configure and start

Provide these values through the shell or an environment file kept out of source
control:

- `POSTGRES_PASSWORD`: database password.
- `AUTH_SIGNING_KEY`: one stable Base64 value containing at least 32 random bytes,
  shared by every app replica.
- `INGRESS_TLS_DIR`: absolute path to a directory containing `tls.crt` and
  `tls.key` for the ingress hostname. Protect the private key on the host.
- `AUTH_RESET_MAIL_FROM`, `AUTH_RESET_URL`, and `MAIL_HOST`: production password
  reset delivery settings. Configure the other `MAIL_*` settings for the SMTP
  provider; see the [email setup checklist](../../docs/email-setup.md).
- `INGRESS_BIND_ADDRESS` and `INGRESS_PORT`: optional host bind, default
  `127.0.0.1:8443`. Set a public bind only after TLS, firewall, and client IP
  handling are verified.

From the repository root, start one app to complete initial migrations, then
scale it. Subsequent starts can retain the chosen scale:

```sh
docker compose -f deploy/auth-ingress/compose.yaml up -d --build --wait
docker compose -f deploy/auth-ingress/compose.yaml up -d --scale app=2 --wait
curl --fail --cacert "$INGRESS_TLS_DIR/tls.crt" https://localhost:8443/api/health
python3 deploy/auth-ingress/verify_ingress.py https://localhost:8443 "$INGRESS_TLS_DIR/tls.crt"
```

Use a certificate with `localhost` in its subject alternative names for the
local curl example; use the deployment hostname for a public endpoint. Docker
Compose reads values from the shell or the repository `.env`. Never commit a
production `.env` or TLS key. Run
`docker compose -f deploy/auth-ingress/compose.yaml down` to stop the stack;
this preserves volumes.

The verification script sends 50 concurrent invalid login requests with forged,
different client-address headers. It expects both application 400 responses and
ingress 429 responses, showing that those headers cannot create new edge buckets.

## Client address and rate limit

NGINX uses the direct TCP peer IP for its rate key. For each `POST /api/auth/*`,
it allows a burst of 30 requests, then refills at 30 requests per minute. The
10 MiB zone is shared by NGINX workers for traffic to every app replica behind
this ingress. Rejected requests return 429 `RATE_LIMITED` with `Retry-After: 60`,
`X-BeeHome-Limit: ingress`, and an English `application/problem+json` body.
Other methods do not consume the auth bucket. The ingress also caps auth request
bodies at 1 MiB and other request bodies at 11 MiB; align the latter with
application upload settings if changed.

NGINX overwrites `X-BeeHome-Client-IP` with its observed peer IP and removes
incoming forwarding headers. The app accepts that header only when its TCP peer
is the ingress IP `172.30.40.254`. If the Docker subnet conflicts with your
network, change the subnet, ingress IP, and
`AUTH_TRUSTED_PROXY_ADDRESS` together. Keep the app port unpublished and restrict
access to the Docker edge network. Direct app access or a proxy that forwards
client-supplied address headers defeats the intended boundary.

This deployment disables generic forwarded-header rewriting in Spring so the
filter sees the actual ingress peer address before checking the custom header.

If a layer 7 load balancer sits before NGINX, its IP is otherwise the rate key
and all clients share one bucket. Configure NGINX `set_real_ip_from` for **only**
that load balancer's exact source address or controlled CIDR, and
`real_ip_header` for a header the load balancer overwrites. Verify that a client
cannot alter the resulting `$remote_addr` by sending its own forwarding headers.
A layer 4 load balancer that preserves source IP needs no forwarded-header trust.
Check the observed peer address from two external networks in the target
environment before exposing authentication routes; published Docker ports or
other network address translation can collapse distinct clients to one peer IP.
For this check, temporarily log only NGINX `$remote_addr` and status, then remove
that diagnostic log. If the addresses collapse, restore source IP at a trusted
network boundary before relying on per-client limits.

Clients behind the same NAT still share an IP bucket; choose edge thresholds and
monitoring for the deployment's traffic. Do not run multiple independent ingress
replicas without a shared edge counter or a single upstream enforcement point.

The app's local fixed-window limit remains `AUTH_REQUESTS_PER_MINUTE`, defaults to
30 per minute per resolved client IP, and holds at most 10,000 buckets per app
replica. It can return a localized 429 independently of NGINX. See
[authentication abuse protection](../../docs/authentication.md#abuse-protection-and-retention)
for the API behavior and limitations.
