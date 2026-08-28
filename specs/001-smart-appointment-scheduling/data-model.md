# Data Model: Smart Appointment Scheduling

## Modeling Conventions

- Store instants for appointments, offers, holds, audit events, and expiration.
  Render them using the immutable clinic time zone.
- Store configured local times for recurring shifts and named time periods.
- Keep owner-entered prose, consent, AI output, and the confirmed structured
  interpretation by request revision for auditability.
- Use explicit string-backed enums for all lifecycle and role values.
- Apply optimistic versioning to mutable aggregate roots. Use database-enforced
  reservation-block uniqueness for competing holds and bookings.

## Entities

### Account

| Field | Rules |
|-------|-------|
| id | Stable identifier. |
| username | Required and unique, normalized for lookup. |
| password hash | Required; never exposed. |
| role | `OWNER` or `STAFF`. |
| owner | Required for `OWNER`; absent for `STAFF`. |
| must change password | Required gate for newly provisioned or reset accounts. |
| temporary password expiry | Required while a temporary password is active; expires after seven days. |
| failed sign-in state | Supports throttling repeated failed attempts. |
| version | Prevents silent concurrent updates. |

**Relationships**: An owner has at most one account. A staff account is the
actor for auditable staff actions.

### Scheduling Request

| Field | Rules |
|-------|-------|
| id | Stable identifier. |
| pet | Required and immutable after submission. Its owner determines request ownership. |
| state | `INTERPRETATION_REVIEW`, `READY_FOR_SUGGESTION`, `OFFER_HELD`, `STAFF_HANDLING`, `CONFIRMED`, or `CLOSED`. |
| current revision | Required link to the active request revision. |
| emergency priority | Set by validated interpretation or emergency-term screening; never asserts non-urgency. |
| staff queue item | Optional; required while staff handling is active. |
| version | Protects lifecycle updates. |

**Relationships**: One request has one or more revisions and offers, at most one
active offer, and at most one staff queue item.

### Request Revision and Interpretation

| Field | Rules |
|-------|-------|
| id / revision number | Revision number is unique within a request. |
| source text | 10-2,000 characters; stored as submitted. |
| consent decision and timestamp | Required before an automated interpretation attempt. |
| interpretation result | Retained raw structured output and its model identifier when AI ran. |
| correlation identifier | Diagnostic identifier shared by the interpretation, validation, and solver hand-off; it contains no owner text. |
| visit reason | Owner-editable prior to confirmation. |
| duration | One configured value: 15, 30, 45, or 60 minutes. |
| care type / required specialty | Staff-reviewed when uncertain or changed. |
| preferred veterinarian | Optional soft preference. |
| urgency | Requires staff review when changed or uncertain. |
| confirmation time | Required before automated matching. |
| status | `DRAFT`, `AWAITING_CONFIRMATION`, `CONFIRMED`, `NEEDS_STAFF_REVIEW`, `SUPERSEDED`, or `WITHDRAWN`. |

**Relationships**: A revision owns one or more availability windows and receives
offers. A request points to its current revision.

**Interpretation validity**: A persisted usable interpretation must satisfy both
the provider-supplied JSON Schema and server-side semantic validation. Structural
or semantic failure is recorded as an auditable fallback outcome, not treated as
a confirmed interpretation.

### Request Availability Window

| Field | Rules |
|-------|-------|
| id | Stable identifier. |
| revision | Required parent. |
| kind | `ALLOWED`, `PREFERRED`, or `EXCLUDED`. |
| applicability | Specific local date or recurring day-of-week occurrence. |
| local start / end | Required, 15-minute aligned, end after start. |
| source | Captures explicit owner input or a resolved named period. |

**Relationships**: Many windows belong to one request revision. The confirmed
windows are a snapshot and are not changed by later named-period configuration
changes.

### Appointment Offer

| Field | Rules |
|-------|-------|
| id | Stable identifier. |
| request revision | Required parent. |
| veterinarian | Required. |
| start instant / duration | Required; duration must match the revision. |
| offered at / expires at | Required; expiry derives from clinic settings. |
| state | `HELD`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `RELEASED`, or `INVALIDATED`. |
| owner-facing rationale | Required, non-sensitive explanation. |
| optional rejection reason | Visible to staff only. |

**Relationships**: A held offer owns reservation blocks. An accepted offer links
to its confirmed appointment.

### Reservation Block

| Field | Rules |
|-------|-------|
| id | Stable identifier. |
| resource type / resource id | `VETERINARIAN` or `PET`, and the protected resource. |
| slot start | One 15-minute instant. |
| owner type / owner id | Links to an offer while held or an appointment after confirmation. |
| state | `HELD` or `CONFIRMED`. |
| expires at | Required for a held block; absent for confirmed blocks. |

**Constraints**:

- Unique `(resource type, resource id, slot start)` prevents overlapping
  veterinarian and pet bookings.
- A 30-minute booking owns two blocks per resource, a 45-minute booking three,
  and a 60-minute booking four.
- Expiring, rejecting, withdrawing, or invalidating an offer removes its held
  blocks transactionally.
- Accepting an offer promotes its held blocks and creates the appointment in one
  transaction.

### Appointment

| Field | Rules |
|-------|-------|
| id | Stable identifier. |
| pet / veterinarian | Required. |
| start instant / duration | Required and represented by confirmed reservation blocks. |
| source | `OWNER_OFFER`, `STAFF_ASSISTED`, or `STAFF_OPERATIONAL`. |
| request / revision / offer | Required for owner-originated appointments; optional for staff operational appointments. |
| owner agreement record | Required for a staff-assisted direct booking. |
| status | `CONFIRMED`, `CANCELLED`, `COMPLETED`, or `NO_SHOW`. |
| cancellation/reschedule reason | Required staff category for staff action; owner cancellation reason is optional. |
| version | Prevents silent concurrent lifecycle updates. |

**Relationships**: One appointment has its reservation blocks and zero or one
linked visit-history entry.

### Visit History Entry

The existing `Visit` record becomes the historical-care entry for a completed
appointment. Extend it with a required appointment link, assigned veterinarian,
and actual completion information while preserving legacy entries without an
appointment link.

**Constraint**: A completed appointment has exactly one linked visit. Cancelled
and no-show appointments have none.

### Clinic Scheduling Settings and Named Day Period

| Field | Rules |
|-------|-------|
| clinic time zone | Defaults to `Europe/Amsterdam`; mutable only before the first request or appointment. |
| booking horizon | 1-365 days; default 90. |
| owner minimum notice | Default two hours. |
| offer hold duration | 1-60 minutes; default 10. |
| duration choices | Fixed POC set: 15, 30, 45, and 60 minutes. |
| scheduling grid | Fixed at 15 minutes. |
| urgent-care guidance | Required, safe default, staff-editable. |

`NamedDayPeriod` has unique name, local start, and local end. Its range must
fall in one local day and not overlap another named period.

### Veterinarian Availability

| Entity | Fields and rules |
|--------|------------------|
| Recurring veterinarian shift | Veterinarian, day of week, local start/end; multiple same-day shifts allowed when non-overlapping and 15-minute aligned. |
| Veterinarian exception | Veterinarian, date or date range, available/unavailable type, optional local start/end for date-specific availability. |
| Veterinarian leave | Veterinarian and full local date range; overrides shifts and exceptions. |
| Clinic closure | Full local date or date range and optional staff reason; overrides all veterinarian availability. |

**Precedence**: clinic closure, veterinarian leave, date-specific exception,
then recurring shift. Any change that conflicts with a confirmed appointment is
rejected until staff resolve the appointment. A change that conflicts with a
hold requires explicit release of that hold.

### Staff Queue Item

| Field | Rules |
|-------|-------|
| request | Required and unique. |
| state | `NEW`, `IN_REVIEW`, `AWAITING_OWNER`, `RESOLVED`, or `CLOSED`. |
| priority | `EMERGENCY` or `STANDARD`; emergency items sort first, then oldest request. |
| claimed by | Optional staff account; required while actively in review. |
| resolution details | Required when resolved or closed. |

**Relationships**: One queue item belongs to one scheduling request. Staff may
claim, reassign, or unclaim it with an audit record.

### Audit Record

| Field | Rules |
|-------|-------|
| id / timestamp | Immutable event identity and time. |
| correlation identifier | Optional non-sensitive link to AI and solver diagnostic events. |
| actor | Required account or trusted system actor. |
| action / target | Required controlled action and target identity. |
| prior and resulting values | Required where state or configuration changes; redact credentials and sensitive text. |
| reason | Required for staff actions that require a reason. |

## State Transitions

### Scheduling Request

```text
INTERPRETATION_REVIEW -> READY_FOR_SUGGESTION
INTERPRETATION_REVIEW -> STAFF_HANDLING
READY_FOR_SUGGESTION  -> OFFER_HELD
READY_FOR_SUGGESTION  -> STAFF_HANDLING
OFFER_HELD            -> READY_FOR_SUGGESTION  (reject, expiry, invalidation)
OFFER_HELD            -> CONFIRMED             (accept)
OFFER_HELD            -> STAFF_HANDLING        (fifth offer exhausted)
STAFF_HANDLING         -> OFFER_HELD            (staff-assisted offer)
STAFF_HANDLING         -> CONFIRMED             (staff direct booking)
any pre-confirmation   -> CLOSED                (owner withdrawal)
```

A revision supersedes the prior current revision and restarts matching with its
own offer exclusions and count. A confirmed request is not revised.

### Appointment

```text
CONFIRMED -> CANCELLED
CONFIRMED -> COMPLETED
CONFIRMED -> NO_SHOW
COMPLETED <-> NO_SHOW  (staff audited correction only)
```

Owner cancellation is allowed only before start. Staff lifecycle transitions are
allowed only after scheduled end, except staff cancellation/rescheduling of a
future appointment.
