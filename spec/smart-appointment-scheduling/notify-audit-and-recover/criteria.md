# Acceptance Criteria: UC7 — Notify, Audit, and Recover

## Functional

### UC7-AC1: Notify a new staff offer

**Covers:** UC7-B1

When staff issues a new owner-approved offer, the system shall create a persistent in-app notification for the owner.

### UC7-AC2: Notify staff-offer expiry

**Covers:** UC7-B2

When a staff offer expires, the system shall create a persistent in-app expiry notification for the owner.

### UC7-AC3: Notify appointment confirmation

**Covers:** UC7-B3

When an appointment is confirmed, the system shall create a persistent in-app confirmation notification for the owner.

### UC7-AC4: Notify appointment rescheduling

**Covers:** UC7-B4

When staff reschedules an appointment, the system shall create a persistent in-app rescheduling notification for the owner.

### UC7-AC5: Notify appointment cancellation

**Covers:** UC7-B5

When an appointment is cancelled, the system shall create a persistent in-app cancellation notification for the owner.

### UC7-AC6: Notify request cancellation by staff

**Covers:** UC7-B6

When staff cancels a nonterminal scheduling request, the system shall create a persistent in-app cancellation notification for the owner.

### UC7-AC7: Display a privacy-safe unread count

**Covers:** UC7-B7

When authenticated navigation is rendered for an owner, the system shall display the owner's unread-notification count without request, pet, clinical, or appointment text.

### UC7-AC8: Render only authorized notification links

**Covers:** UC7-B8

When a notification target is authorized for the current identity, the system shall render the notification's target link.

### UC7-AC9: Omit unauthorized notification links

**Covers:** UC7-B8

If a notification target is not authorized for the current identity, then the system shall omit its link and shall not disclose the target.

### UC7-AC10: Change only notification read state

**Covers:** UC7-B9

When an owner marks a notification read, the system shall update only that notification's read state without accepting an offer or changing a request or appointment.

### UC7-AC11: Open the current target state

**Covers:** UC7-B10

When an authorized notification target no longer has its original action available, the system shall open the target's current authorized state.

### UC7-AC12: Append audit metadata for defined events

**Covers:** UC7-B11

When a defined security, consent, AI-metadata, interpretation, request, claim, reservation, offer, rejection, appointment, calendar, settings, or account event commits, the system shall append its actor, event type, target identifier, timestamp, safe reason code, and applicable safe before-and-after metadata.

### UC7-AC13: Exclude sensitive audit payloads

**Covers:** UC7-B12

When an audit event is written, the system shall omit passwords, password hashes, raw or purged free text, full AI output, clinical descriptions, and sensitive staff notes from the audit payload.

### UC7-AC14: Retain safe audit metadata

**Covers:** UC7-B13

When privacy-safe audit metadata is appended, the system shall retain it indefinitely.

### UC7-AC15: Show complete audit to staff

**Covers:** UC7-B14

When authenticated staff requests audit history, the system shall make the complete privacy-safe audit history available.

### UC7-AC16: Limit owner timeline visibility

**Covers:** UC7-B15

When an owner requests timeline history, the system shall show only request and appointment events for that owner's resources.

### UC7-AC17: Retain linked sensitive data before its deadline

**Covers:** UC7-B16

While fewer than 30 days have elapsed since a linked appointment first became `COMPLETED`, `NO_SHOW`, or `CANCELLED`, the system shall retain the request's raw text and full AI output.

### UC7-AC18: End linked retention at 30 days

**Covers:** UC7-B16, UC7-B18

When 30 days have elapsed since a linked appointment first became `COMPLETED`, `NO_SHOW`, or `CANCELLED`, the system shall purge the linked request's sensitive AI data.

### UC7-AC19: Keep linked sensitive data purged after its deadline

**Covers:** UC7-B16, UC7-B18

While a linked request is past its sensitive-data retention deadline, the system shall keep its purge-target fields removed.

### UC7-AC20: Retain unlinked sensitive data before its deadline

**Covers:** UC7-B17

While fewer than 30 days have elapsed since an unlinked request became `CANCELLED` or `EXPIRED`, the system shall retain the request's raw text and full AI output.

### UC7-AC21: End unlinked retention at 30 days

**Covers:** UC7-B17, UC7-B18

When 30 days have elapsed since an unlinked request became `CANCELLED` or `EXPIRED`, the system shall purge the request's sensitive AI data.

### UC7-AC22: Keep unlinked sensitive data purged after its deadline

**Covers:** UC7-B17, UC7-B18

While an unlinked request is past its sensitive-data retention deadline, the system shall keep its purge-target fields removed.

### UC7-AC23: Remove every purge-target field

**Covers:** UC7-B18

When a request reaches its sensitive-data retention deadline, the system shall remove raw free text, full AI input and output, and the owner-facing factual AI summary.

### UC7-AC24: Preserve the minimal scheduling record

**Covers:** UC7-B19

When sensitive request data is purged, the system shall preserve care type, specialty, duration, consent metadata, state history, appointment linkage, and privacy-safe audit metadata.

### UC7-AC25: Exclude secrets and sensitive request text from logs

**Covers:** UC7-B20

When the application writes a log event, the system shall omit passwords and sensitive request text.

### UC7-AC26: Persist deadlines as instants

**Covers:** UC7-B21

When a hold, offer, fallback, claim, lockout, or retention deadline is created, the system shall persist the deadline as an absolute instant.

### UC7-AC27: Apply overdue deadlines before user actions

**Covers:** UC7-B22

When a user action targets a record with an overdue deadline, the system shall apply the deadline effect in the action's transaction before evaluating the requested action.

### UC7-AC28: Process overdue deadlines periodically

**Covers:** UC7-B23

When lifecycle processing encounters an overdue deadline without a user action, the system shall apply the specified deadline effect.

### UC7-AC29: Release a recovered overdue guided hold

**Covers:** UC7-B24

When startup recovery finds an overdue guided hold, the system shall release its interval without recording rejection.

### UC7-AC30: Recover a guided request to matching

**Covers:** UC7-B25

When startup recovery releases an overdue guided hold, the system shall return its request to `MATCHING` without acquiring another hold.

### UC7-AC31: Release a recovered overdue staff offer

**Covers:** UC7-B26

When startup recovery finds an overdue staff offer, the system shall release its held interval.

### UC7-AC32: Requeue a recovered offer before fallback expiry

**Covers:** UC7-B27

When startup recovery releases an overdue staff offer whose fallback deadline has not passed, the system shall return the request to `STAFF_QUEUED`.

### UC7-AC33: Expire a recovered fallback

**Covers:** UC7-B28

When startup recovery finds a request whose fallback deadline has passed, the system shall move the request to terminal `EXPIRED`.

### UC7-AC34: Recover interrupted automation to staff

**Covers:** UC7-B29

When startup recovery finds `INTERPRETING` or active automated `MATCHING` beyond its operation deadline, the system shall create `STAFF_QUEUED(RECOVERY_REQUIRED)`.

### UC7-AC35: Do not retry interrupted automation

**Covers:** UC7-B30

When recovery processes an interrupted AI or solver operation, the system shall not dispatch an automatic AI or solver retry.

### UC7-AC36: Preserve unexpired reservation deadlines

**Covers:** UC7-B31

When the application restarts with an unexpired hold or offer, the system shall preserve its original deadline without granting additional time.

### UC7-AC37: Keep a claim protected before inactivity expiry

**Covers:** UC7-B32

While a claim has been inactive for less than 30 minutes, the system shall keep the claim unavailable for reclamation.

### UC7-AC38: Make a claim reclaimable at inactivity expiry

**Covers:** UC7-B32

When a claim reaches 30 minutes without activity, the system shall make the claim reclaimable without discarding request history.

### UC7-AC39: Apply lifecycle processing idempotently

**Covers:** UC7-B33

When lifecycle processing runs repeatedly for the same overdue record, the system shall preserve the same final state and side-effect count as the first successful run.

### UC7-AC40: Show detailed diagnostics only to staff

**Covers:** UC7-B34

When authenticated staff requests diagnostics, the system shall show Ollama connectivity, configured model identifier, solver availability, lifecycle-worker health, and database migration status.

### UC7-AC41: Deny detailed diagnostics to non-staff users

**Covers:** UC7-B34

If a non-staff user requests detailed diagnostics, then the system shall deny access and shall not disclose dependency, model, worker, or migration details.

### UC7-AC42: Keep diagnostics free of sensitive content

**Covers:** UC7-B35

When the system renders diagnostics, the system shall omit prompts, request text, and other sensitive dependency details.

### UC7-AC43: Report Ollama unavailability without startup failure

**Covers:** UC7-B36

When Ollama is unavailable, the system shall report that status in staff diagnostics without failing application startup.

### UC7-AC45: Preserve the first linked purge deadline

**Covers:** UC7-B37

When an appointment that already fixed its linked request's purge deadline as `NO_SHOW` is corrected to `COMPLETED`, the system shall preserve the existing purge deadline without extending it.

## Non-functional

### UC7-AC44: Couple notifications to state changes transactionally

**Covers:** UC7-B1, UC7-B2, UC7-B3, UC7-B4, UC7-B5, UC7-B6

When a state change requiring an owner notification commits, the system shall commit its notification in the same transaction.

## Coverage exclusions

- Performance: the specification defines lifecycle deadlines but no measurable notification, audit-query, purge-job, or diagnostics latency contract.
- Accessibility: the specification defines notification content boundaries but no measurable accessibility contract for notification or timeline presentation.
