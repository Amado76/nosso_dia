# Time handling — UI integration

Families have no timezone field or configuration. Locale controls formatting
and language; it does not identify the device's UTC offset.

## Storage and display

- Event timestamps, including creation, completion, and study timer intervals,
  remain Java `Instant` values stored in PostgreSQL `TIMESTAMPTZ`. API timestamps
  use ISO-8601 UTC (`Z`). Parse them as instants and convert to device-local time
  before rendering; never remove the offset before parsing.
- Calendar dates, including birth dates and execution dates, remain `DATE` / ISO
  `YYYY-MM-DD`. They are calendar labels, not midnight UTC instants; do not shift
  them when displaying on another device.
- Recurring and daily scheduled times remain `TIME` / `HH:mm` local wall times.
  Display `08:30` as `08:30`; do not convert it as an event timestamp. There is
  no timezone-dependent scheduler in the backend.

## Automatic device offset

The frontend sends `X-Timezone-Offset` with the device's **current UTC offset in
whole minutes**, positive east of UTC, negative west. Examples: UTC `0`, UTC-3
`-180`, UTC+05:45 `345`. Flutter clients use
`DateTime.now().timeZoneOffset.inMinutes`; JavaScript clients must negate
`new Date().getTimezoneOffset()`.

Recompute on every request, including retries, so device setting and daylight
saving changes are picked up automatically. Users never enter this header or a
timezone. The header does not affect authentication or authorization.

Daily execution uses server UTC now plus this offset to determine today, reject
future dates, and close past days. The frontend supplies the same device-local
date in execution paths. Timer start uses the same rule to assign its calendar
date; pause/resume/finish retain the original start date and calculate elapsed
time using UTC instants. Reading execution/history remains free of writes.

A missing header defaults to UTC. On endpoints that use the local day, a blank,
noninteger, or out-of-range value (outside -1080..1080 minutes) returns HTTP 400
with `code=VALIDATION_ERROR`. The offset is request metadata and is never
persisted in the family. Authorized clients may supply different offsets;
the boundary follows the current request, not a shared family location.

Different devices can disagree about today near midnight. An execution already
persisted as finalized remains finalized even if another device's offset places
it on today; explicit authorized reopening retains its existing correction
semantics. Stored calendar dates are never reinterpreted or migrated when the
device offset changes.

## Migration and rollout

Flyway V21 drops `beehome.families.timezone`. Creation and editing accept only
`name`; family and resolved-plan responses no longer contain timezone. Old
clients sending timezone are rejected as unknown input. Deploy the updated
backend and frontend together. Back up before migrating if rollback must restore
the previous family settings. Existing audit instants, dates, wall times,
memberships, and historical records are preserved.
