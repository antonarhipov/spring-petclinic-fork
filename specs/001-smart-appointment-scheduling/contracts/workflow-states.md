# Workflow State Contract

## Owner Display Mapping

| Request state | Related state | Owner label | Primary action |
|---|---|---|---|
| `AWAITING_INTERPRETATION` | Job `PENDING`/`RUNNING` | Interpreting request | None; poll status |
| `AWAITING_REVIEW` | Valid interpretation | Review interpretation | Review interpreted details |
| `READY_TO_MATCH` | Matching job absent/terminal | Finding an appointment | Find another time when explicitly available |
| `READY_TO_MATCH` | Matching job `PENDING`/`RUNNING` | Finding an appointment | None; poll status |
| `OFFERED` | Offer `HELD` and active | Appointment offered | Review offer |
| `OFFERED` | Offer expired but not materialized | Appointment offered | Show expiry and next action after canonical GET |
| `STAFF_HANDLING` | Active queue item | With clinic staff | Revise when permitted or view contact guidance |
| `CONFIRMED` | Appointment confirmed | Confirmed | View appointment |
| `WITHDRAWN` or `CLOSED` | None | Closed | Return to dashboard |

The status GET performs no transition. Expiry is materialized by the next mutation or expiry worker; every read treats `expiresAt <= serverNow` as inactive.

## Request Transitions

```text
submit consented revision
  -> AWAITING_INTERPRETATION + INTERPRETATION job

submit declined revision
  -> STAFF_HANDLING + NEW queue item

AI valid
  AWAITING_INTERPRETATION -> AWAITING_REVIEW

AI unusable/timeout/failure
  AWAITING_INTERPRETATION -> STAFF_HANDLING + NEW queue item

owner confirms ROUTINE revision
  AWAITING_REVIEW -> READY_TO_MATCH + MATCHING job

owner confirms PRIORITY or EMERGENCY_SUSPECTED
  AWAITING_REVIEW -> STAFF_HANDLING + NEW/high-priority queue item

completed deterministic match + atomic hold
  READY_TO_MATCH -> OFFERED

no match, incomplete solve, or terminal matching failure
  READY_TO_MATCH -> STAFF_HANDLING + NEW queue item

automatic offer rejected/expired, attempt < 5
  OFFERED -> READY_TO_MATCH
  (no job until explicit Find another time)

automatic offer rejected/expired, attempt = 5
  OFFERED -> STAFF_HANDLING + NEW queue item

staff-assisted offer rejected/expired
  request remains STAFF_HANDLING
  same assigned queue item -> IN_REVIEW

offer accepted or staff direct booking
  OFFERED|STAFF_HANDLING -> CONFIRMED + Appointment

owner revision
  nonterminal state -> AWAITING_INTERPRETATION, AWAITING_REVIEW, or STAFF_HANDLING
  supersede prior workflow revision, release active hold, preserve history

owner withdrawal
  any pre-confirmation state -> WITHDRAWN

explicit staff closure
  STAFF_HANDLING -> CLOSED
```

Appointment cancellation never reopens the request.

## Text and Workflow Revisions

```text
TextRevision: immutable once inserted
Interpretation: authoritative valid result retained; later workflow corrections do not overwrite it
WorkflowRevision: DRAFT -> OWNER_CONFIRMATION_REQUIRED | CONFIRMED -> SUPERSEDED
```

- Editing structured fields creates a new workflow revision without a new AI invocation.
- Revising original text creates a new text revision, consent record, interpretation path, and later workflow revision.
- A staff emergency clearance creates `OWNER_CONFIRMATION_REQUIRED` with explicitly selected `ROUTINE` or `PRIORITY`.
- Owner reconfirmation of cleared `ROUTINE` enqueues matching. Reconfirmed `PRIORITY` returns the assigned queue item to `IN_REVIEW`.
- Any result targeting a superseded revision becomes stale and changes no current state.

## Background Job Transitions

```text
PENDING -> RUNNING -> SUCCEEDED | FAILED | STALE
RUNNING --lease expires--> PENDING
SUCCEEDED|FAILED --new permitted explicit run--> PENDING with runSequence + 1
```

Interpretation:

- At most two external attempts: initial plus one retry only if no validated result was durably committed.
- Once one valid result commits, the job cannot invoke AI again for that text revision.

Matching:

- Each explicit run captures one calendar revision.
- A changed revision before hold creation allows one new snapshot/solve.
- A second change or any incomplete five-second solve fails to staff fallback.

## Queue Transitions

```text
fallback -> NEW
NEW + claim -> IN_REVIEW + assignee
IN_REVIEW + unclaim -> NEW + no assignee
any active state + reassign -> same state + new assignee
owner revision -> NEW + current revision; retain assignee
IN_REVIEW + owner unreachable -> AWAITING_OWNER(CONTACT_REQUIRED), no hold
IN_REVIEW + confirmation requested -> AWAITING_OWNER(INTERPRETATION_CONFIRMATION)
IN_REVIEW + assisted hold -> AWAITING_OWNER(PORTAL_OFFER)
AWAITING_OWNER(PORTAL_OFFER) + accept -> RESOLVED
AWAITING_OWNER(PORTAL_OFFER) + reject/expire -> IN_REVIEW, same assignee
IN_REVIEW + direct booking -> RESOLVED
active + withdrawal/staff close -> CLOSED
```

Assignment and queue state are independent. Claims never expire automatically.

## Offer Transitions

```text
HELD -> ACCEPTED
HELD -> REJECTED
HELD -> EXPIRED
HELD -> RELEASED   owner revision, withdrawal, or audited staff release
```

- `HELD` blocks capacity only while `expiresAt > now`.
- Acceptance verifies active hold, workflow revision, current clock, conflicts, and calendar state under one lock.
- Rejection/expiry always releases and excludes the exact veterinarian/interval for the workflow revision.
- Only automatic rejection/expiry increments the automatic count.
- `ACCEPTED` points to exactly one appointment.

## Appointment Transitions

Booking state:

```text
CONFIRMED -> CANCELLED
```

Outcome state:

```text
PENDING -> COMPLETED | NO_SHOW
PENDING | COMPLETED | NO_SHOW --audited correction--> PENDING | COMPLETED | NO_SHOW
```

- Rescheduling changes veterinarian/interval on the same appointment and appends a change event.
- Only `CONFIRMED` booking state reserves capacity.
- Completion creates a new immutable completion visit. No-show and cancellation create none.
- A correction appends an outcome event; it may select a replacement completion visit or no current visit without deleting prior history.

## Atomic Cross-Aggregate Outcomes

The following groups commit or roll back together:

- Request submission: request, active-pet pointer, text/consent revision, job or queue item, command, audit, owner history.
- Hold creation: offer, request state, queue state when assisted, calendar revision, command/job result, audit/history.
- Acceptance: held offer, appointment, request, queue resolution, active-request removal, calendar revision, command, audit/history.
- Rejection/expiry: offer, exclusion, attempt count when automatic, request/queue state, calendar revision, command/job result, audit/history.
- Availability mutation: policy/availability change, calendar revision, command, audit; no writes on conflict.
- Appointment lifecycle: appointment, visit/current outcome projection, calendar revision when capacity changes, command, audit/history.

