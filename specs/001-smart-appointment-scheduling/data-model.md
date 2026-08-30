# Data Model: Smart Appointment Scheduling

## Modeling Conventions

- Retain the existing integer identities for `Owner`, `Pet`, `Vet`, `Specialty`, and `Visit`.
- Use generated `Long` identities for new tables and a scheduling-specific mapped superclass containing `id`, `version`, `createdAt`, and `updatedAt`.
- Store enums as stable strings and instants as UTC-capable `Instant` values. Store the clinic IANA zone beside appointment/offer instants for historical display.
- Use half-open intervals `[start, end)`; adjacent intervals do not conflict.
- New associations are lazy by default. Application services create role-specific projections while a transaction is open; web views never receive entities.
- Mutable aggregate roots use optimistic versions. Calendar-affecting writes additionally use the singleton `CalendarState` pessimistic lock.
- Protected free text, AI artifacts, clinical detail, consent artifacts, and sensitive audit before/after payloads live in encrypted `ProtectedPayload` records.
- Owner-visible history is a sanitized projection/event stream separate from staff audit data.

## Existing Catalog Entities

### Owner

Existing fields remain: `id`, `firstName`, `lastName`, `address`, `city`, and `telephone`.

Relationships:

- One owner has many pets.
- One owner may have one owner-role account.
- One owner has many requests, offers, and appointments.

Rules:

- Owner-role application lookups always include the authenticated owner's ID.
- Deletion is blocked when any retained scheduling, appointment, or visit history exists.
- Profile changes append an audit event.

### Pet

Existing fields remain: `id`, `ownerId`, `typeId`, `name`, and `birthDate`.

Relationships:

- One pet has many historical visits and appointments.
- At most one `ActiveSchedulingRequest` may point to a pet.

Rules:

- Appointment/hold intervals for one pet cannot overlap.
- An owner cannot overlap across different pets either.

### Vet and Specialty

Existing veterinarian and specialty catalogs remain authoritative. The current many-to-many assignment determines specialty eligibility.

Relationships:

- A veterinarian has availability definitions, offers, appointments, and completion visits.
- A workflow revision may reference one preferred veterinarian and one required specialty.

### Visit

Existing columns remain and gain:

| Field | Type | Rule |
|---|---|---|
| `visitKind` | `LEGACY` or `APPOINTMENT_COMPLETION` | Existing rows backfill to `LEGACY` |
| `appointmentId` | nullable unique FK | Required for completion visits |
| `completedAt` | nullable instant | Required for completion visits |
| `vetId` | nullable FK | Required for completion visits |
| `ownerSummary` | nullable text | Required for completion visits |
| `clinicalPayloadId` | nullable FK | Encrypted staff-only detail |
| `outcomeEventId` | nullable unique FK | Identifies the completion event that created the visit |

Rules:

- A completion creates one new immutable visit for the current completion outcome event.
- Corrections retain prior completion visits and choose the owner-visible current visit through the latest outcome event; prior visits become superseded, not deleted.
- A reconciled legacy visit remains unchanged and is linked from its replacement appointment.
- Current legacy rows lack veterinarian, interval, and agreement evidence, so every current reconciliation records a new exact slot and fresh owner agreement.

## Identity and Security

### Account

| Field | Type | Rule |
|---|---|---|
| `id` | Long | Primary key |
| `username` | String | Trimmed, lowercase, unique |
| `passwordHash` | String | Never returned to a view |
| `role` | `OWNER` or `STAFF` | Required |
| `ownerId` | nullable unique FK | Required only for `OWNER` |
| `enabled` | boolean | Required |
| `temporaryPasswordExpiresAt` | nullable instant | Required while temporary |
| `passwordChangeRequired` | boolean | Restricts routes until cleared |
| `sessionVersion` | long | Increments on password reset |
| `version` | long | Optimistic locking |
| `createdAt`, `updatedAt` | instant | Audit metadata |

Rules:

- `OWNER` requires an owner link; `STAFF` forbids one.
- Cleartext temporary passwords are never stored and are displayed only in the successful provisioning response.
- A duplicate provisioning/reset command returns the original canonical status without redisplaying cleartext.

## Clinic Policy and Availability

### ClinicPolicy

Singleton aggregate (`id = 1`).

| Field | Type | Rule/default |
|---|---|---|
| `zoneId` | String | `Europe/Amsterdam`; immutable after first request/appointment |
| `bookingHorizonDays` | integer | Default 90; range 1-365 |
| `holdDurationMinutes` | integer | Default 10; range 1-60 |
| `ownerNoticeMinutes` | integer | Default 120 |
| `startGridMinutes` | integer | Fixed at 15 |
| `clinicPhone` | String | Required |
| `contactHours` | String | Required owner-facing text |
| `urgentCareGuidance` | text | Required, with safe seed default |
| `version` | long | Optimistic locking |

Children:

- `ClinicOperatingInterval`: weekday plus local start/end; defaults to Monday-Friday 09:00-17:00.
- `AllowedDuration`: unique duration minutes; initial values 15, 30, 45, and 60.
- `NamedPeriod`: unique normalized name plus local start/end; periods may not overlap and must remain within one local day.

### CalendarState

Singleton aggregate (`id = 1`).

| Field | Type | Rule |
|---|---|---|
| `revision` | long | Incremented after every capacity/reservation mutation |
| `version` | long | Optimistic version in addition to pessimistic locking |
| `updatedAt` | instant | Last mutation time |

This row is locked with `PESSIMISTIC_WRITE` for every availability, hold, or appointment-capacity mutation.

### RecurringShift

| Field | Type | Rule |
|---|---|---|
| `vetId` | FK | Required |
| `weekday` | enum | Required |
| `localStart`, `localEnd` | local time | Same day, start before end, 15-minute grid |
| `version` | long | Optimistic locking |

Intervals for the same veterinarian and weekday cannot overlap. Split shifts are allowed; overnight shifts are not.

### AvailabilityExceptionDay

| Field | Type | Rule |
|---|---|---|
| `vetId` | FK | Required |
| `localDate` | date | Unique with veterinarian |
| `version` | long | Optimistic locking |

Children are zero or more `AvailabilityExceptionInterval` rows on the 15-minute grid. Presence of the day record replaces the veterinarian's complete recurring schedule for that date; zero intervals means unavailable that date.

### VeterinarianLeave

Contains `vetId`, inclusive `startDate`, inclusive `endDate`, reason category, optional protected note, and version. The end date cannot precede the start date.

### ClinicClosure

Contains inclusive local `startDate`, inclusive local `endDate`, owner-facing explanation, optional protected internal note, and version. A closure removes all veterinarian availability for covered dates.

### Effective Availability

For each veterinarian/local date:

1. A clinic closure produces no availability.
2. Otherwise veterinarian leave produces no availability.
3. Otherwise an exception-day record supplies the complete interval set.
4. Otherwise recurring shifts supply the interval set.
5. Intersect intervals with clinic operating intervals and convert valid local grid starts to unambiguous instants in the clinic zone.

An availability mutation is rejected before commit when any confirmed appointment or active, unexpired offer falls outside the resulting schedule.

## Scheduling Requests and Revisions

### SchedulingRequest

Aggregate root for one owner/pet case.

| Field | Type | Rule |
|---|---|---|
| `id` | Long | Primary key |
| `ownerId`, `petId` | FK | Required and ownership-consistent |
| `state` | RequestState | Required |
| `currentTextRevisionId` | FK | Required after submission |
| `currentWorkflowRevisionId` | nullable FK | Set after structured details exist |
| `appointmentId` | nullable unique FK | Set on confirmation/direct booking |
| `version` | long | Optimistic locking |
| `submittedAt`, `updatedAt`, `closedAt` | instant | Lifecycle timestamps |

`RequestState`:

- `AWAITING_INTERPRETATION`
- `AWAITING_REVIEW`
- `READY_TO_MATCH`
- `OFFERED`
- `STAFF_HANDLING`
- `CONFIRMED`
- `WITHDRAWN`
- `CLOSED`

Processing labels such as **Interpreting request** and **Finding an appointment** are derived from the request plus current job state; the job lifecycle is not copied into the request.

### ActiveSchedulingRequest

| Field | Type | Rule |
|---|---|---|
| `petId` | integer FK | Primary key |
| `requestId` | Long unique FK | Active request |

Creation inserts this row in the request transaction. A primary-key conflict returns the existing active request. It is deleted only when the request becomes `CONFIRMED`, `WITHDRAWN`, or `CLOSED`.

### TextRevision

Immutable after insertion.

| Field | Type | Rule |
|---|---|---|
| `id` | Long | Primary key |
| `requestId` | FK | Required |
| `revisionNumber` | integer | Unique within request, starts at 1 |
| `submittedAt` | instant | Relative-date anchor |
| `prosePayloadId` | unique FK | Encrypted exact owner text |
| `consentPayloadId` | unique FK | Encrypted decision, disclosure version, and timestamp |

The selected pet remains on the parent request and never changes across text revisions.

### Interpretation

| Field | Type | Rule |
|---|---|---|
| `id` | Long | Primary key |
| `textRevisionId` | unique FK | One authoritative result per text revision |
| `source` | `AI` or `STAFF` | Required |
| `modelIdentifier` | nullable String | Required for AI source |
| `rawPayloadId` | nullable unique FK | Encrypted raw output when any was received |
| `validatedPayloadId` | nullable unique FK | Encrypted validated candidate |
| `validationState` | enum | `VALID`, `UNUSABLE`, `SUPERSEDED` |
| `createdAt` | instant | Required |

The first durably committed valid AI result is authoritative for its text revision. Manual correction creates/updates the workflow revision; it does not overwrite the raw result.

### WorkflowRevision

Structured owner/staff scheduling intent. A confirmed revision is immutable except for its lifecycle marker.

| Field | Type | Rule |
|---|---|---|
| `id` | Long | Primary key |
| `requestId` | FK | Required |
| `revisionNumber` | integer | Unique within request |
| `textRevisionId`, `interpretationId` | nullable FK | Provenance |
| `state` | `DRAFT`, `OWNER_CONFIRMATION_REQUIRED`, `CONFIRMED`, `SUPERSEDED` | Required |
| `reasonPayloadId` | unique FK | Encrypted confirmed visit reason |
| `durationMinutes` | integer | Must be allowed by snapshotted policy |
| `preferredVetId` | nullable FK | Soft preference |
| `requiredSpecialtyId` | nullable FK | Hard requirement |
| `urgency` | `ROUTINE`, `PRIORITY`, `EMERGENCY_SUSPECTED` | Required |
| `automaticOfferCount` | integer | Range 0-5 |
| `confirmedAt` | nullable instant | Required for `CONFIRMED` |

Children:

- `AvailabilityWindow`: kind (`ALLOWED`, `PREFERRED`, `EXCLUDED`, `FALLBACK`), shape (`ONE_OFF`, `WEEKLY`), one-off date or bounded recurrence dates/weekday, local start/end, and snapshotted source/named-period information.
- `OfferExclusion`: unique workflow revision, veterinarian, start instant, and end instant, referencing the rejected/expired offer.

Rules:

- Preferred/fallback windows are subsets of allowed windows; exclusions subtract from allowed windows.
- Contradictory or empty effective allowed windows cannot be confirmed.
- Specialty and urgency are owner read-only.
- Owner revision supersedes the current workflow revision, releases a hold, clears counts/exclusions in the new revision, and invalidates stale staff/job results.

## Durable Work

### BackgroundJob

One reusable job record per target and kind; each explicit run increments `runSequence` and is retained in audit.

| Field | Type | Rule |
|---|---|---|
| `id` | Long | Primary key |
| `jobType` | `INTERPRETATION` or `MATCHING` | Required |
| `textRevisionId` | nullable FK | Required for interpretation |
| `workflowRevisionId` | nullable FK | Required for matching |
| `state` | JobState | Required |
| `runSequence` | integer | Increments for each explicit run |
| `attemptCount` | integer | Interpretation maximum 2 |
| `calendarRetryCount` | integer | Matching maximum 1 per run |
| `leaseToken` | nullable UUID | Set only while running |
| `leaseUntil` | nullable instant | Enables restart reclaim |
| `availableAt`, `startedAt`, `completedAt` | nullable instant | Scheduling and history |
| `calendarRevision` | nullable long | Matching snapshot |
| `outcomeCategory` | nullable enum | Sanitized result/failure |
| `version` | long | Optimistic locking |

Unique constraints prevent more than one interpretation job per text revision and more than one matching job per workflow revision.

`JobState`: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `STALE`.

Transitions:

```text
PENDING -> RUNNING -> SUCCEEDED | FAILED | STALE
RUNNING --expired lease--> PENDING
SUCCEEDED | FAILED -> PENDING   only for a new permitted explicit run
```

### KeyRotationRun

Contains source/target key IDs, state, last processed payload ID, counts, lease data, start/completion times, and failure category. It is resumable and rewraps data keys without changing protected payload ciphertext.

## Staff Queue

### QueueItem

One mutable queue aggregate per request.

| Field | Type | Rule |
|---|---|---|
| `requestId` | unique FK | Required |
| `workflowRevisionId` | nullable FK | Current staff basis |
| `state` | QueueState | Required |
| `awaitingReason` | nullable enum | `CONTACT_REQUIRED`, `INTERPRETATION_CONFIRMATION`, `PORTAL_OFFER` |
| `fallbackReason` | enum | Manual choice, technical failure, clinical uncertainty, unsupported specialty, no match, priority, or emergency |
| `urgency` | enum | Denormalized current urgency for sorting |
| `assigneeAccountId` | nullable staff FK | Independent of state |
| `lastContactAt` | nullable instant | Summary only |
| `version` | long | Optimistic locking |
| `createdAt`, `updatedAt`, `resolvedAt`, `closedAt` | instant | Lifecycle timestamps |

### ContactAttempt

Append-only child containing queue item, staff actor, time, outcome category, and optional encrypted note.

Queue transitions:

```text
fallback                       -> NEW
claim NEW                      -> IN_REVIEW + assignee
owner revision                -> NEW + current revision; retain assignee
unclaim                       -> NEW + no assignee
reassign                      -> same state + new assignee
owner unreachable             -> AWAITING_OWNER(CONTACT_REQUIRED), no hold
interpretation confirmation   -> AWAITING_OWNER(INTERPRETATION_CONFIRMATION)
staff offer                   -> AWAITING_OWNER(PORTAL_OFFER)
staff offer accepted          -> RESOLVED
staff offer rejected/expired  -> IN_REVIEW, same assignee
direct booking                -> RESOLVED
withdrawal/staff closure      -> CLOSED
```

Emergency clearance creates a new `OWNER_CONFIRMATION_REQUIRED` workflow revision. Owner reconfirmation sends `ROUTINE` to matching and resolves the safety queue outcome; `PRIORITY` returns the same assigned queue item to `IN_REVIEW`.

## Offers and Appointments

### Offer

The offer row is also the capacity hold.

| Field | Type | Rule |
|---|---|---|
| `requestId`, `workflowRevisionId` | FK | Required |
| `ownerId`, `petId`, `vetId` | FK | Required, ownership-consistent |
| `origin` | `AUTOMATIC` or `STAFF_ASSISTED` | Required |
| `startAt`, `endAt` | instant | End after start |
| `zoneId` | String | Snapshotted clinic zone |
| `expiresAt` | instant | Server authoritative |
| `state` | OfferState | Required |
| `automaticAttemptNumber` | nullable integer | Required only for automatic offers, range 1-5 |
| `calendarRevision` | long | Snapshot used to propose it |
| `matchExplanation` | String | Non-sensitive owner-facing text |
| `appointmentId` | nullable unique FK | Set on acceptance |
| `version` | long | Optimistic locking |

`OfferState`: `HELD`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `RELEASED`.

Only one offer may be active per request. A held offer blocks capacity only while `expiresAt > now`. Rejection/expiry creates an exclusion for that workflow revision. Only automatic offers increment `automaticOfferCount`.

### Appointment

| Field | Type | Rule |
|---|---|---|
| `ownerId`, `petId`, `vetId` | FK | Required, ownership-consistent |
| `startAt`, `endAt` | instant | End after start |
| `zoneId` | String | Snapshotted clinic zone |
| `bookingState` | `CONFIRMED` or `CANCELLED` | Capacity state |
| `outcomeState` | `PENDING`, `COMPLETED`, or `NO_SHOW` | Visit outcome |
| `originatingRequestId` | nullable unique FK | Smart/manual request provenance |
| `originatingOfferId` | nullable unique FK | Offer provenance |
| `legacyVisitId` | nullable unique FK | Reconciliation source |
| `version` | long | Optimistic locking |
| `createdAt`, `updatedAt` | instant | Lifecycle metadata |

Rules:

- `CONFIRMED` appointments block capacity; cancelled appointments do not.
- Owner cancellation is allowed only before `startAt`.
- Completion/no-show is allowed only at or after `endAt`.
- Rescheduling retains appointment identity and appends an event with old/new interval and veterinarian.
- Cancellation, rescheduling, completion, no-show, and correction require audit metadata and atomic conflict/state checks.

### AppointmentChangeEvent

Append-only event for confirmation, reschedule, owner/staff cancellation, and contact/agreement metadata. It references protected reason/note payloads rather than duplicating them.

### AppointmentOutcomeEvent

Append-only event with type `COMPLETED`, `NO_SHOW`, or `CORRECTION`, actor, time, reason, previous/new outcome, and optional resulting visit ID. The latest event determines the appointment's current outcome and current owner-visible visit. Corrections never delete earlier events or visits.

## Audit, Commands, and Protected Data

### CommandRecord

| Field | Type | Rule |
|---|---|---|
| `commandId` | UUID | Primary key, issued before submission |
| `actorAccountId` | nullable FK | Null only for a system job |
| `action`, `targetType`, `targetId` | String | Immutable scope |
| `requestHash` | bytes/string | Detects token reuse with changed input |
| `state` | `ISSUED` or `COMPLETED` | Required |
| `canonicalResultType`, `canonicalResultId`, `canonicalLocation` | nullable | Filled on completion |
| `issuedAt`, `completedAt` | instant | Required by state |

The mutation transaction pessimistically locks the issued row. Reusing the ID with another scope/hash is rejected; a completed duplicate returns its stored canonical result.

### AuditEvent

Append-only fields: event ID, actor or system identity, occurred-at instant, action, target type/ID, outcome, correlation ID, optional command ID, and optional protected payload reference. No application code exposes update/delete operations for this table.

### OwnerHistoryEvent

Append-only sanitized event linked to owner, request/appointment/visit, display type, occurred-at instant, and safe display parameters. It contains no internal reason, raw AI data, solver diagnostics, or detailed clinical notes.

### ProtectedPayload

| Field | Type | Rule |
|---|---|---|
| `id` | Long | Primary key |
| `artifactUuid` | UUID unique | Stable authenticated identity |
| `artifactType` | String | Authenticated context |
| `schemaVersion`, `contentType` | String | Authenticated context |
| `algorithm` | `AES-256-GCM` | Versioned value |
| `nonce` | 12-byte value | Unique per data key encryption |
| `ciphertext` | binary large object | Immutable |
| `activeEnvelopeId` | FK | Current wrapped data key |
| `createdAt` | instant | Immutable |

### PayloadKeyEnvelope

Contains protected payload ID, key ID, wrapping algorithm/version, unique wrapping nonce, wrapped data key, created time, and rotation-run ID. Old envelopes remain until rotation completion and explicit safe retirement.

## Timefold Planning Model

The planning model is detached from persistence.

- `AppointmentSchedulingSolution`: fixed request constraints, fixed calendar snapshot/revision, stable candidate-slot list, one planning assignment, and bendable score.
- `AppointmentAssignment`: the single planning entity whose planning variable is one `CandidateSlot`.
- `CandidateSlot`: veterinarian, interval, specialty set, effective availability facts, owner/pet/veterinarian conflict facts, preference classifications, and stable tie-break key.
- Score: one hard level for all eligibility constraints and six ordered soft levels for owner time preference, preferred veterinarian, fallback-window penalty, earliest time, clinic efficiency, and stable veterinarian/time tie-break.

Candidate ordering, single-thread reproducible mode, and exhaustive search make a completed solve deterministic. A timeout without proven completion yields no offer.

## Required Database Constraints and Indexes

- Unique normalized `account.username`; unique nullable owner account link.
- Primary-key `active_scheduling_request.pet_id`; unique request link.
- Unique `(request_id, revision_number)` for text and workflow revisions.
- Unique interpretation per text revision.
- Unique background job per `(job_type, target revision)`.
- Unique queue item per request.
- Unique appointment per accepted offer and unique completion visit per appointment.
- Unique date exception per `(vet_id, local_date)`.
- Unique duration value and named-period name.
- Index queue sort/filter fields: state, urgency, assignee, fallback reason, created/updated time.
- Index active offers and appointments by veterinarian/pet/owner plus start/end and state.
- Index jobs by state, available time, and lease expiry.
- Index audit/history by target and occurred time.

Interval non-overlap cannot be represented by a normal H2 unique constraint; it is enforced under the locked `CalendarState` transaction and verified by concurrent service tests.

## Transactional Invariants

1. A request is persisted with its active-pet pointer, text revision, consent payload, first job or queue item, command result, and audit/history event in one transaction.
2. A capacity mutation holds the `CalendarState` lock until offer/appointment, request/queue state, command result, and audit/history are consistent.
3. AI and Timefold never run inside a database transaction.
4. A job result applies only when its lease, target revision, and expected aggregate version remain current.
5. Acceptance converts a held offer to an appointment without first releasing capacity.
6. Expiry is evaluated from the injected server clock during every capacity check; sweeper lag never revives or extends a hold.
7. Staff saves require the current workflow/queue version; stale edits commit no partial business state.
8. Every lifecycle correction appends an outcome event and reconciles current visit projection in one transaction.
