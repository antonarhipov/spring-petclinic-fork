# Acceptance Criteria: UC5 — Resolve a Request Through Staff

## Functional

### UC5-AC1: Persist every defined fallback

**Covers:** UC5-B1

When automation reaches `UNSUPPORTED_LANGUAGE`, `CONSENT_DECLINED`, `AI_UNAVAILABLE`, `INVALID_AI_OUTPUT`, `CLINICAL_DISPUTE`, `URGENCY`, `NO_SPECIALTY_VET`, `SOLVER_UNAVAILABLE`, `SUGGESTIONS_EXHAUSTED`, or `RECOVERY_REQUIRED`, the system shall persist the request in `STAFF_QUEUED` with that reason.

### UC5-AC2: Represent ordinary exhaustion as fallback

**Covers:** UC5-B2

When automated matching has no feasible non-rejected candidate, the system shall represent the outcome as `STAFF_QUEUED(SUGGESTIONS_EXHAUSTED)`.

### UC5-AC3: Preserve specialty requirements

**Covers:** UC5-B3

If specialty care has no active veterinarian with the required active specialty, then the system shall create `STAFF_QUEUED(NO_SPECIALTY_VET)` and shall not assign a general-care-only veterinarian.

### UC5-AC4: Prioritize urgent work

**Covers:** UC5-B4

When the staff queue contains urgency and non-urgency requests, the system shall order every `URGENCY` request before every non-urgency request.

### UC5-AC5: Order equal-priority work FIFO

**Covers:** UC5-B5

When staff-queued requests have equal priority, the system shall order them by initial queued time from earliest to latest.

### UC5-AC6: Claim an available request atomically

**Covers:** UC5-B6

When a staff member claims an unclaimed or reclaimable request, the system shall atomically assign that claim to the staff member.

### UC5-AC7: Display claim ownership and activity

**Covers:** UC5-B7

When staff views a claimed request, the system shall display its current claimant and claim activity.

### UC5-AC8: Protect an active claim

**Covers:** UC5-B8

If a staff member other than the claimant attempts to modify a request whose claim has been inactive for less than 30 minutes, then the system shall reject the modification and shall preserve the request.

### UC5-AC9: Release a claim

**Covers:** UC5-B9

While a claim is active, when its claimant releases it, the system shall make the request available for another staff member to claim.

### UC5-AC10: Keep a claim protected before the inactivity boundary

**Covers:** UC5-B10

While a claim has been inactive for less than 30 minutes, the system shall keep the claim unavailable for reclamation by another staff member.

### UC5-AC11: Make a claim reclaimable at the inactivity boundary

**Covers:** UC5-B10

When a claim reaches 30 minutes without activity, the system shall permit another staff member to reclaim it without losing request history.

### UC5-AC12: Replace structured fields without AI

**Covers:** UC5-B11

When the claimant submits valid replacements for the structured interpretation fields, the system shall update those fields without invoking AI.

### UC5-AC13: Validate staff-edited structured fields

**Covers:** UC5-B12

If a staff-edited duration, veterinarian, specialty, or time violates an active clinic rule, then the system shall reject the edit and shall preserve the prior structured interpretation.

### UC5-AC14: Select one feasible staff slot

**Covers:** UC5-B13

When the claimant selects a feasible slot from the full staff calendar, the system shall use that single slot as the proposed staff offer.

### UC5-AC15: Hold a staff offer below the maximum

**Covers:** UC5-B14

When staff issues an offer with more than zero and less than 24 hours remaining before the fallback deadline, the system shall hold its complete interval until the fallback deadline.

### UC5-AC16: Hold a staff offer at the maximum

**Covers:** UC5-B14

When staff issues an offer with at least 24 hours remaining before the fallback deadline, the system shall hold its complete interval for 24 hours.

### UC5-AC17: Prevent a staff offer beyond 24 hours

**Covers:** UC5-B14

If a requested staff-offer deadline would exceed 24 hours from issuance, then the system shall limit the hold to 24 hours.

### UC5-AC18: Bound an offer by fallback expiry

**Covers:** UC5-B15

If a 24-hour staff offer would extend beyond the request's fallback deadline, then the system shall set the offer deadline to the fallback deadline.

### UC5-AC19: Expose one staff offer

**Covers:** UC5-B16

While a request has a current staff offer, the system shall expose only that one offer to the owner.

### UC5-AC20: Create one appointment from an accepted staff offer

**Covers:** UC5-B17

While a staff offer is current and valid, when the owner accepts it, the system shall create exactly one `BOOKED` appointment for the held interval.

### UC5-AC21: Confirm an accepted staff-offered request

**Covers:** UC5-B18

When acceptance of a valid staff offer creates the appointment, the system shall move the request to terminal `CONFIRMED`.

### UC5-AC22: Release a rejected staff offer

**Covers:** UC5-B19

While a staff offer is current, when the owner rejects it, the system shall release the held interval.

### UC5-AC23: Record the rejected staff pair

**Covers:** UC5-B20

When an owner rejects a current staff offer, the system shall record that offer's exact veterinarian and start pair for the request.

### UC5-AC24: Requeue a rejected staff offer

**Covers:** UC5-B21

When an owner rejects a current staff offer, the system shall return the request to `STAFF_QUEUED`.

### UC5-AC25: Release an expired staff offer without rejection

**Covers:** UC5-B22

When a staff offer reaches its deadline, the system shall release the held interval without recording its veterinarian and start pair as rejected.

### UC5-AC26: Requeue an expired offer before fallback expiry

**Covers:** UC5-B23

When a staff offer expires before the fallback deadline, the system shall return the request to `STAFF_QUEUED`.

### UC5-AC27: Replace a revoked offer without in-place extension

**Covers:** UC5-B24

When the claimant revokes a staff offer and submits a feasible replacement, the system shall create one replacement offer rather than extend the revoked offer's deadline in place.

### UC5-AC28: Directly book after offline coordination

**Covers:** UC5-B25

When staff submits a feasible direct-booking slot with a nonblank offline-coordination reason, the system shall create the appointment.

### UC5-AC29: Reject a direct booking without a reason

**Covers:** UC5-B25

If staff submits a fallback direct booking with a blank offline-coordination reason, then the system shall reject the booking and shall not reserve the slot.

### UC5-AC30: Record staff booking source

**Covers:** UC5-B26

When staff directly books a fallback request, the system shall record the booking as staff-booked rather than owner-approved.

### UC5-AC31: Confirm a directly booked fallback request

**Covers:** UC5-B27

When direct staff booking succeeds for a fallback request, the system shall move the request to terminal `CONFIRMED`.

### UC5-AC32: Enforce hard constraints on direct fallback booking

**Covers:** UC5-B28

If a fallback direct booking violates an overlap, availability, specialty, time-grid, horizon, closure, leave, or DST constraint, then the system shall reject the booking and shall not create an appointment.

### UC5-AC33: Cancel a request with a reason

**Covers:** UC5-B29

While a request is nonterminal, when staff cancels it with a nonblank reason, the system shall move the request to `CANCELLED`.

### UC5-AC34: Reject staff cancellation without a reason

**Covers:** UC5-B29

If staff attempts to cancel a nonterminal request with a blank reason, then the system shall reject cancellation and shall preserve the request state.

### UC5-AC35: Clear temporary request state on cancellation

**Covers:** UC5-B30

When staff cancels a nonterminal request, the system shall release all request-associated temporary reservations.

### UC5-AC36: Keep fallback active before its deadline

**Covers:** UC5-B31

While fewer than seven days have elapsed since the initial `STAFF_QUEUED` transition, the system shall keep an unresolved fallback request nonterminal.

### UC5-AC37: Expire fallback at its deadline

**Covers:** UC5-B31

When seven days have elapsed since a request's initial `STAFF_QUEUED` transition, the system shall move the unresolved request to terminal `EXPIRED`.

### UC5-AC38: Preserve expiry after the deadline

**Covers:** UC5-B31

While an unresolved request is past its seven-day fallback deadline, the system shall keep the request terminal `EXPIRED`.

### UC5-AC39: Release an offer at fallback expiry

**Covers:** UC5-B32

When the fallback deadline is reached while a staff offer is active, the system shall release the held interval.

### UC5-AC40: Prevent reopening an expired request

**Covers:** UC5-B33

If staff attempts to reopen an `EXPIRED` request, then the system shall reject the action and shall preserve the terminal state.

### UC5-AC41: Preserve queued state after a booking conflict

**Covers:** UC5-B34

If a concurrent conflict prevents a submitted staff offer or direct booking from reserving its interval, then the system shall leave the request in `STAFF_QUEUED` without creating a reservation.

### UC5-AC42: Remove a cancelled request's claim

**Covers:** UC5-B30

When staff cancels a nonterminal request, the system shall remove its active work claim.

### UC5-AC43: Release a revoked offer

**Covers:** UC5-B24

When the claimant revokes a current staff offer, the system shall release its held interval.

### UC5-AC45: Refresh only on successful claim activity

**Covers:** UC5-B35

When a claim is created or reclaimed, or its current claimant commits a successful request mutation, the system shall set claim activity to that commit time; viewing, polling, failed validation, and rejected or unauthorized commands shall leave claim activity unchanged.

### UC5-AC46: Materialize a missing fallback horizon

**Covers:** UC5-B36

When a request first enters `STAFF_QUEUED` without a materialized owner horizon, the system shall materialize its absolute owner-horizon boundaries from `first_queued_at` using the request's captured settings.

### UC5-AC47: Preserve a confirmed owner horizon

**Covers:** UC5-B37

When a request with an owner horizon already materialized by confirmation enters `STAFF_QUEUED`, the system shall preserve the existing horizon boundaries.

### UC5-AC48: Apply the owner horizon to linked staff slots

**Covers:** UC5-B38

When staff validates an offer or request-linked direct booking, the system shall require its start to fall within the request's materialized owner horizon.

## Non-functional

### UC5-AC44: Serialize competing claims

**Covers:** UC5-B6

When multiple staff members concurrently claim the same available request, the system shall commit exactly one claimant.

## Coverage exclusions

- Performance: the specification defines queue ordering and deadlines but no measurable queue-page or staff-action latency contract.
- Accessibility: the specification defines owner-visible offer content but no measurable accessibility contract for the queue or offer screens.
