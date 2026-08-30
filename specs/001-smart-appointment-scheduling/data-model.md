# Phase 1 Data Model: Smart Appointment Scheduling

## Modeling conventions

- Existing integer identifiers for `Owner`, `Pet`, `Vet`, `Specialty`, and `Visit` remain unchanged. New tables use generated `BIGINT` identifiers except guard and reservation tables, whose natural composite keys enforce invariants.
- Store timestamps as UTC `Instant` values. Retain the clinic `ZoneId` on request/policy snapshots used to resolve and display local values. Recurring schedules use `DayOfWeek` and `LocalTime`; closures and leave use clinic-local dates.
- Every mutable aggregate has an integer optimistic `version`. Every enumerated value is stored by stable string code.
- Canonical JSON is stored in portable large-text columns. It is never queried for business invariants; relational columns and child tables remain authoritative.
- Foreign keys to retained scheduling history use restrictive deletion. Owner/pet deletion or transfer requires a later explicit retention workflow and is outside this POC.

## Existing authoritative entities

### Owner, Pet, Veterinarian, and Specialty

The existing `Owner`, `Pet`, `Vet`, and `Specialty` records remain the authoritative identity, ownership, and care-eligibility catalogs. Scheduling stores foreign keys to them rather than copying mutable names or specialties. A solver input snapshot records the values used for a particular decision.

### Visit

Keep every legacy row intact and extend `visits` with nullable `appointment_id` and `vet_id` foreign keys. `appointment_id` is unique. A visit produced by appointment completion has both values, uses the actual completion local date, and stores the clinical notes in the existing description. Legacy rows keep both new columns null and never reserve future capacity.

## Accounts and sessions

### Account

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `username` | string(80) | Normalized lowercase, unique, nonblank |
| `passwordHash` | string(100) | BCrypt only; plaintext is never persisted |
| `role` | `OWNER` or `STAFF` | Exactly one role |
| `ownerId` | existing Owner ID, nullable | Required and unique for `OWNER`; null for `STAFF` |
| `mustChangePassword` | boolean | True for staff-provisioned/reset credentials |
| `temporaryCredentialExpiresAt` | instant, nullable | Seven days after issue; null for ordinary/demo credentials |
| `credentialVersion` | long | Incremented on password change/reset |
| `enabled` | boolean | Disabled accounts cannot authenticate |
| `createdAt`, `updatedAt` | instant | Audit timestamps |
| `version` | integer | Optimistic locking |

Spring Session JDBC owns its standard session and principal-index tables through the same Flyway migration set. A password reset deletes all sessions indexed by username. Local/demo/test bootstrap creates one account per synthetic owner plus `admin`; deployed bootstrap creates no predictable credentials.

## Request and interpretation aggregates

### SchedulingRequest

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `ownerId` | existing Owner ID | Original owner, immutable |
| `petId` | existing Pet ID | Immutable after creation and owned by `ownerId` at creation |
| `state` | `RequestState` | Owner-facing workflow state |
| `ownerStatusCode` | string enum | Maps state/outcome to approved plain-language copy |
| `activeTextRevisionId` | RequestTextRevision ID | Current exact prose revision |
| `activeRequestRevisionId` | RequestRevision ID, nullable | Current structured revision |
| `suspectedEmergency` | boolean | May only move from false to true automatically |
| `closureOutcome` | `WITHDRAWN`, `CONFIRMED`, `STAFF_CLOSED`, nullable | Set only on terminal transition |
| `createdAt`, `updatedAt`, `closedAt` | instants | `closedAt` only for terminal state |
| `version` | integer | Required by every mutating form |

### ActivePetRequest

| Field | Type | Rules |
|---|---|---|
| `petId` | existing Pet ID | Primary key; one guard per pet |
| `requestId` | SchedulingRequest ID | Unique |

The guard is inserted in the same transaction as request creation and deleted only when the request becomes `CONFIRMED` or `CLOSED`. It gives all three databases the same one-active-request guarantee without partial indexes.

### RequestTextRevision

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `requestId` | SchedulingRequest ID | Parent |
| `sequence` | integer | Unique and increasing within request |
| `sourceText` | string(2000) | Plain English text, length 10–2,000 |
| `sourceHash` | string(64) | SHA-256 for idempotency/evidence, not identity |
| `submittedAt` | instant | Resolution anchor |
| `clinicZoneId` | string(64) | Zone used for relative dates |
| `emergencyScreenVersion` | string(40) | Version of fixed term set |
| `emergencyMatchedTermsJson` | text | Audited matches; never exposed to owners |

Rows are append-only. Editing prose creates a new row, invalidates prior consent for operational use, and moves the request to `AWAITING_CONSENT`.

### ConsentRecord

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `textRevisionId` | RequestTextRevision ID | Scope of decision |
| `decision` | `AGREED` or `DECLINED` | Append-only |
| `actorAccountId` | Account ID | Must be the owning account |
| `dataUseCopyVersion` | string(40) | Copy shown at decision time |
| `decidedAt` | instant | Server time |

A later agreement may follow a decline for the same unchanged text, but staff cannot invoke the LLM unless an effective `AGREED` record exists.

### InterpretationRecord

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `textRevisionId` | RequestTextRevision ID | Interpreted source |
| `origin` | `LLM` or `STAFF` | Immutable provenance |
| `schemaVersion` | string(20), nullable | Required for LLM origin |
| `recognizedOutputJson` | text | Normalized recognized fields |
| `unknownFieldsJson` | text | JSON pointers and values ignored operationally |
| `uncertaintiesJson` | text | Field codes/messages from structured result |
| `validationIssuesJson` | text | Deterministic validation result |
| `outcome` | `VALID`, `VALID_NEEDS_STAFF`, `INVALID` | No numeric confidence |
| `createdByAccountId` | Account ID, nullable | Required for manual staff origin; null for system |
| `createdAt` | instant | Immutable timestamp |

The source output is immutable. Owner edits and staff corrections create a `RequestRevision`; they never rewrite the original model response.

### RequestRevision

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `requestId` | SchedulingRequest ID | Parent |
| `sequence` | integer | Unique and increasing within request |
| `interpretationId` | InterpretationRecord ID | Provenance |
| `status` | `DRAFT_REVIEW`, `CONFIRMED`, `SUPERSEDED` | Only one active confirmed revision |
| `visitReason` | string(500) | Required |
| `durationMinutes` | integer | Member of current clinic duration set |
| `careType` | `GENERAL` or `SPECIALTY` | Unresolved values cannot be confirmed |
| `specialtyId` | existing Specialty ID, nullable | Required exactly when care type is specialty |
| `urgency` | `POSSIBLE_EMERGENCY` or `NO_CONCERN_IDENTIFIED` | Unresolved cannot be confirmed; negative code is never reassurance copy |
| `preferredVeterinarianId` | existing Vet ID, nullable | Must be specialty-eligible when supplied |
| `veterinarianPreferenceStrength` | `NONE`, `PREFERRED`, `REQUIRED` | `REQUIRED` may only be set by staff |
| `clinicPolicyVersion` | long | Policy snapshot identifier |
| `clinicZoneId` | string(64) | Resolution/display zone |
| `rejectionExpiryCount` | integer | 0–5, reset for new revision |
| `confirmedByAccountId`, `confirmedAt` | nullable account/instant | Both set together when confirmed |
| `createdAt` | instant | Audit timestamp |
| `version` | integer | Protects review edits |

### RequestWindow

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `requestRevisionId` | RequestRevision ID | Parent |
| `kind` | `ALLOWED`, `PREFERRED`, `EXCLUDED` | Required |
| `startAt`, `endAt` | instants | `startAt < endAt`; concrete and unambiguous |
| `sourcePhrase` | string(300), nullable | Explanation evidence |
| `namedPeriodCode` | string(40), nullable | Period used during resolution |
| `fallbackAllowed` | boolean | Marks lower-ranked “if necessary” window |

Confirmed revisions retain concrete windows even when named-period configuration changes.

## Durable integration execution

### IntegrationExecution

This record is both the persisted work item and the audit root for one interpretation or match trigger.

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Publicly opaque operation identifier |
| `kind` | `LLM_INTERPRETATION` or `TIMEFOLD_MATCH` | Worker type |
| `requestId` | SchedulingRequest ID | Parent |
| `textRevisionId` | RequestTextRevision ID, nullable | LLM scope |
| `requestRevisionId` | RequestRevision ID, nullable | Solver scope |
| `triggerKey` | string(160) | Unique idempotency key |
| `state` | `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `SUPERSEDED` | Durable job state |
| `triggeredAt`, `deadlineAt`, `startedAt`, `finishedAt` | instants | Deadline includes queue time |
| `requestedModelId` | string(120), nullable | LLM alias requested, such as `gemma4:latest` |
| `resolvedModelId` | string(200), nullable | Ollama-resolved model identifier/digest |
| `solverConfigurationVersion` | string(120), nullable | Timefold configuration ID |
| `promptTemplateVersion` | string(40), nullable | LLM only |
| `schemaVersion` | string(40) | Input/output contract version |
| `inputJson`, `normalizedOutputJson` | text | Canonical reconstructible evidence |
| `inputHash`, `outputHash` | string(64), nullable | Integrity/comparison aids |
| `attemptCount` | integer | Maximum 2 for either clarified retry flow |
| `outcome` | string enum, nullable | Stable business outcome |
| `errorClassification` | string enum, nullable | Staff/audit only |
| `scoreJson`, `scoreExplanationJson` | text, nullable | Timefold only |
| `version` | integer | Atomic worker claim/completion |

### IntegrationAttempt

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `executionId` | IntegrationExecution ID | Parent |
| `sequence` | integer | Unique within execution; 1 or 2 |
| `startedAt`, `finishedAt` | instants | Per-attempt timing |
| `inputJson` | text | Required; refreshed snapshot for solver retry |
| `rawOutput` | text, nullable | Exact LLM response or serialized solver result |
| `normalizedOutputJson` | text, nullable | Recognized validated result |
| `unknownFieldsJson` | text, nullable | LLM unknown field evidence |
| `scoreJson`, `scoreExplanationJson` | text, nullable | Timefold result/explanation |
| `outcome`, `errorClassification` | string enums | Required at finish |

The rendered LLM prompt is not stored when `sourceText + promptTemplateVersion + inputJson` reconstruct it.

## Offers, reservations, and appointments

### Offer

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `requestRevisionId` | RequestRevision ID | Confirmed revision |
| `executionId` | IntegrationExecution ID, nullable | Required for automated offer |
| `veterinarianId` | existing Vet ID | Required |
| `startAt`, `endAt` | instants | 15-minute start; end matches duration |
| `durationMinutes` | integer | Copied from revision |
| `source` | `TIMEFOLD` or `STAFF_SELECTED` | Owner-facing automation must be Timefold |
| `classification` | `STANDARD`, `FALLBACK`, `STAFF` | Owner-visible label category |
| `publicExplanationCode` | string enum | Approved, non-sensitive copy only |
| `status` | `HELD`, `ACCEPTED`, `REJECTED`, `EXPIRED`, `RELEASED`, `UNAVAILABLE` | History retained |
| `expiresAt` | instant | Clinic policy applied at creation |
| `ownerRejectionReason` | string(500), nullable | Staff-only |
| `createdAt`, `resolvedAt` | instants | Lifecycle timestamps |
| `version` | integer | Stale-action protection |

Only an offer with an acquired active hold may enter `HELD`. A stale Timefold result is recorded on the integration attempt but never as an offered `Offer` row.

### Hold

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `offerId` | Offer ID | Unique, one-to-one |
| `requestId` | SchedulingRequest ID | Holder |
| `state` | `ACTIVE`, `CONSUMED`, `RELEASED`, `EXPIRED` | Required |
| `expiresAt` | instant | Server-authoritative |
| `releaseReason` | string enum, nullable | Required when released |
| `createdAt`, `resolvedAt` | instants | Lifecycle timestamps |
| `version` | integer | Locked on acceptance/release |

### ReservationBlock

| Field | Type | Rules |
|---|---|---|
| `resourceType` | `VETERINARIAN` or `PET` | Composite primary key |
| `resourceId` | existing record ID | Composite primary key |
| `blockStartAt` | instant | Composite primary key; 15-minute boundary |
| `holdId` | Hold ID, nullable | Exactly one of hold/appointment is set |
| `appointmentId` | Appointment ID, nullable | Exactly one of hold/appointment is set |

Every duration creates matching veterinarian and pet rows for each 15-minute block. The composite primary key is the cross-database overlap guard. Hold acceptance updates all rows to appointment ownership in the appointment transaction; release/expiry/cancellation deletes the active blocks while the parent history remains.

### Appointment

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key, retained across reschedule |
| `petId`, `veterinarianId` | existing IDs | Exactly one of each |
| `requestId`, `offerId` | nullable IDs | Set for request-originated booking |
| `startAt`, `endAt` | instants | Future at booking; grid-aligned; no resource overlap |
| `status` | `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW` | Required |
| `authorizationBasis` | `OWNER_AGREEMENT`, `CLINIC_FOLLOW_UP`, `CLINIC_RECHECK` | Required |
| `agreementRecordedBy`, `agreementAt`, `agreementMethod` | nullable | Required for owner-agreement staff booking |
| `supportingVisitId` | existing Visit ID, nullable | Required for follow-up/recheck without agreement |
| `staffReasonCategory`, `staffReasonNote` | nullable | Category required for staff booking/reschedule/cancel and no-show as applicable |
| `createdAt`, `updatedAt`, `cancelledAt`, `completedAt` | instants | State-dependent |
| `version` | integer | Stale-action protection |

Rescheduling keeps the ID, atomically replaces reservation blocks, and writes old/new times to the audit event. Completion creates exactly one linked `Visit`; no-show/cancellation create none. Corrections are the only operation allowed to reconcile a terminal status and its visit link.

## Staff queue and clinic capacity

### StaffQueueItem and QueueNote

`StaffQueueItem` has a unique `requestId`, `priority` (`EMERGENCY`, `NORMAL`), `state` (`NEW`, `IN_REVIEW`, `AWAITING_OWNER`, `RESOLVED`, `CLOSED`), nullable `assigneeAccountId`, `reasonCode`, timestamps, resolution code, and optimistic `version`. Open ordering is priority first and request creation time second. `QueueNote` is append-only staff-only text with author and timestamp. Claim, unclaim, and reassignment never expire and always produce audit events.

### ClinicSchedulingPolicy

A singleton row stores `zoneId`, fixed `gridMinutes = 15`, `bookingHorizonDays` (1–365), `holdDurationMinutes` (1–60), `ownerMinimumNoticeMinutes = 120`, urgent-care guidance, consent-copy version, monotonically increasing `configurationVersion`, timestamps, and optimistic `version`. `zoneId` becomes immutable when the first request or appointment exists.

Related entities:

- `AllowedDuration`: unique duration minutes; defaults 15, 30, 45, 60.
- `ClinicHours`: weekday/start/end interval; non-overlapping and within one day; defaults weekdays 09:00–17:00.
- `NamedPeriod`: stable code, label, start/end local time; periods cannot overlap or cross midnight.
- `EmergencyTerm`: normalized term and rule-set version; automated screening can only raise urgency.
- `ClinicClosure`: inclusive start/end local dates; no partial-day closure.
- `VetRecurringShift`: veterinarian, weekday, local start/end; multiple non-overlapping intervals allowed.
- `VetDateException` and child intervals: veterinarian and local date replacing recurring shifts for that date; intervals are same-day, non-overlapping, and grid-aligned.
- `VetLeave`: veterinarian and inclusive local-date range.

Availability precedence is closure, leave, date exception, then recurring shift. Every capacity mutation increments `configurationVersion` and is rejected while it would invalidate a confirmed appointment or active hold.

## AuditEvent

| Field | Type | Rules |
|---|---|---|
| `id` | long | Primary key |
| `actorType` | `OWNER`, `STAFF`, `SYSTEM` | Required |
| `actorAccountId` | Account ID, nullable | Null only for system |
| `occurredAt` | instant | Server time |
| `action` | stable string enum | Required |
| `targetType`, `targetId` | string, string | Required |
| `requestId` | SchedulingRequest ID, nullable | Correlation |
| `beforeJson`, `afterJson`, `reasonJson` | text, nullable | Relevant values only; no password hashes or session secrets |

Audit rows are append-only and written in the same transaction as the protected change. Staff can read them; owner projections never expose these rows, staff notes, reasons, solver scores, or internal error classes.

## State transitions

### Scheduling request

```text
AWAITING_CONSENT
  -> INTERPRETING              owner agrees; one persisted LLM operation
  -> STAFF_HANDLING            owner declines or emergency screen requires staff

INTERPRETING
  -> INTERPRETATION_REVIEW     valid structured result
  -> STAFF_HANDLING            invalid, uncertain, failed, timed out, or emergency

INTERPRETATION_REVIEW
  -> READY_FOR_SUGGESTION      owner confirms complete validated revision
  -> AWAITING_CONSENT          owner changes prose
  -> STAFF_HANDLING            owner selects staff handling

READY_FOR_SUGGESTION
  -> MATCHING                  owner requests one suggestion

MATCHING
  -> OFFER_HELD                Timefold result acquires authoritative hold
  -> AWAITING_FALLBACK_CHOICE  allowed slot exists but no preferred match
  -> STAFF_HANDLING            no match, failure, timeout, or stale retry failure

AWAITING_FALLBACK_CHOICE
  -> MATCHING                  owner selects Find an alternative
  -> STAFF_HANDLING            owner forwards to staff

OFFER_HELD
  -> CONFIRMED                 atomic hold acceptance creates appointment
  -> READY_FOR_SUGGESTION      rejection, expiry, or audited release below limit
  -> STAFF_HANDLING            fifth rejection/expiry or staff intervention

Any non-CONFIRMED state -> CLOSED on explicit withdrawal.
```

Workers complete only if the request/text/revision versions still match their trigger. Otherwise the operation becomes `SUPERSEDED` and cannot overwrite newer or withdrawn state.

### Staff queue

```text
NEW -> IN_REVIEW -> AWAITING_OWNER -> RESOLVED
IN_REVIEW -> NEW                    explicit unclaim with reason
IN_REVIEW -> IN_REVIEW              explicit reassignment with reason
Any open state -> CLOSED            withdrawal or deliberate non-scheduling closure
```

### Offer and hold

```text
Offer HELD / Hold ACTIVE
  -> Offer ACCEPTED / Hold CONSUMED
  -> Offer REJECTED / Hold RELEASED
  -> Offer EXPIRED / Hold EXPIRED
  -> Offer RELEASED or UNAVAILABLE / Hold RELEASED
```

If hold duration is two minutes or less, the warning state is immediate. Browser countdown state never changes these server transitions.

### Appointment

```text
CONFIRMED -> CONFIRMED               audited reschedule
CONFIRMED -> CANCELLED               owner before start or staff with reason
CONFIRMED -> COMPLETED               staff at/after scheduled end; create Visit
CONFIRMED -> NO_SHOW                 staff at/after scheduled end; no Visit
COMPLETED/NO_SHOW -> corrected state audited correction only
```

## Cross-aggregate invariants and transactional boundaries

1. Owner-facing queries derive `ownerId` from the authenticated account. Cross-owner identifiers resolve as not found.
2. Request creation and `ActivePetRequest` insertion are one transaction.
3. Consent recording, request-state change, and operation creation are one transaction; external calls occur after commit and outside a database transaction.
4. Queue creation is idempotent through unique `requestId`; every fallback path creates or reopens the same item.
5. Timefold output is advisory. Offer + hold + all veterinarian/pet blocks are committed together only after current-fact revalidation.
6. A failed block insert rolls back the complete hold or booking. No partial duration can remain reserved.
7. Hold acceptance locks request/offer/hold, checks expiry, creates the appointment, converts every block, closes queue work, releases the active-request guard, and writes audit in one transaction.
8. Reject, expiry, release, withdrawal, and cancellation release all relevant blocks and write history atomically.
9. Availability mutations lock/version the affected configuration, check appointments and active holds, then apply and increment the configuration version in one transaction.
10. A stale form version causes HTTP 409 with current state and safe submitted values for review; it never retries a state-changing command silently.
