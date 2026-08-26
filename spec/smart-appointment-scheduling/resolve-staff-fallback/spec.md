# UC5: Resolve a Request Through Staff

## Summary

Every request that automation cannot safely finish becomes durable, prioritized staff work. A staff member claims the request, completes its structured meaning without AI, and either offers one feasible slot for owner approval or books directly after offline coordination. Owners never receive a false dead end.

## Resolved ambiguities

### Fallback entry

The request moves to `STAFF_QUEUED` for these observable reasons:

- `UNSUPPORTED_LANGUAGE`
- `CONSENT_DECLINED`
- `AI_UNAVAILABLE`
- `INVALID_AI_OUTPUT`
- `CLINICAL_DISPUTE`
- `URGENCY`
- `NO_SPECIALTY_VET`
- `SOLVER_UNAVAILABLE`
- `SUGGESTIONS_EXHAUSTED`
- `RECOVERY_REQUIRED`

Ordinary lack of feasible availability is `SUGGESTIONS_EXHAUSTED`; it is never presented as a bare no-availability response. A missing required specialty is never downgraded to general care.

Every request reaches fallback with interpretation settings captured by UC3. When owner confirmation has not already materialized an absolute owner horizon, the first `STAFF_QUEUED` transition materializes it from `first_queued_at` using those captured settings. A horizon already materialized by confirmation is preserved. Staff offers and request-linked direct bookings use that owner horizon; unrelated direct bookings without a request use UC6's staff horizon.

### Queue ordering and claims

- `URGENCY` requests appear before all other reasons. Remaining queue entries are FIFO by initial queued time.
- Staff must atomically claim a request before editing or offering it. Claims are visible to all staff.
- The claimant may release a claim. A claim with no activity for 30 minutes becomes reclaimable by another staff member.
- Claiming, reclaiming, and each successful claimant mutation refresh claim activity. Viewing or polling, failed validation, and rejected or unauthorized commands do not refresh it.
- An active non-stale claim prevents another staff member from modifying the request.

### Staff completion and owner-approved offers

- The claimant may replace every structured interpretation field without AI or owner AI consent.
- Staff edits still use active veterinarians, active specialties, the request's captured duration rules and owner horizon, and the request's factual owner availability where provided.
- Staff may choose one feasible veterinarian and time against the full live calendar. The complete interval is held atomically for up to 24 hours, bounded by the request's seven-day fallback deadline.
- The owner sees one offer through the request status page and in-app notification, never the calendar.
- Acceptance creates a `BOOKED` appointment and terminal `CONFIRMED` request.
- Rejection releases the hold, records the exact veterinarian/start pair, and returns the request to `STAFF_QUEUED` for another offer.
- Offer expiry releases the hold without recording rejection and returns the request to `STAFF_QUEUED` when the overall fallback deadline has not passed.
- Staff may revoke an offer and issue a replacement. The original deadline is never extended in place.

### Direct staff completion and terminal outcomes

- After offline coordination, staff may directly book a feasible slot with a nonblank reason. The appointment records staff booking rather than owner approval and terminates the linked request as `CONFIRMED`.
- Direct staff completion bypasses AI, Timefold, consent, and the guided hold loop, but never bypasses hard overlap, availability, specialty, time, or DST constraints.
- Staff may cancel a nonterminal request with a nonblank reason. Cancellation releases its holds, removes its active claim, and creates an owner notification.
- The fallback deadline is seven days from the initial `STAFF_QUEUED` transition. At the deadline, any offer is released and the request becomes terminal `EXPIRED`.
- Staff cannot reopen an expired request. The owner must start a new request.

## Explicit assumptions

- Staff may use information learned offline to complete a structured interpretation or direct booking.
- The fallback request itself is the queue item; no second independent work item changes its lifecycle.
- Staff direct booking from a fallback remains linked to the request for traceability.
- Staff can view an active claim they do not own but cannot modify its request until the claim is released or stale.
- Request-linked fallback booking remains within the captured owner horizon; staff can cancel the request and use UC6 direct booking when offline coordination requires a later slot within the staff horizon.

## Handled edge cases

- If staff offer duration would pass the seven-day fallback deadline, the offer deadline is shortened to the fallback deadline.
- If a slot becomes unavailable while staff submits an offer or direct booking, the operation reports a conflict and leaves the request queued.
- If an owner rejects several staff offers, each exact pair remains excluded for that request.
- If an offer simply expires, its pair remains eligible for a later staff offer.
- If a staff claimant disappears, another staff member can reclaim after 30 minutes without losing request history.
- If the seven-day deadline passes while an offer is active, expiry wins and releases the slot.

## Behaviors to verify

- UC5-B1: The system creates a durable `STAFF_QUEUED` request for every defined automation fallback reason.
- UC5-B2: The system represents ordinary automated exhaustion as `SUGGESTIONS_EXHAUSTED` instead of bare no-availability.
- UC5-B3: The system creates `NO_SPECIALTY_VET` fallback rather than assigning a general-care veterinarian to specialty care.
- UC5-B4: The system orders `URGENCY` queue entries before non-urgency entries.
- UC5-B5: The system orders equal-priority queue entries by their initial queued time.
- UC5-B6: The system permits one staff member to atomically claim an unclaimed request.
- UC5-B7: The system displays the current claimant and claim activity to staff.
- UC5-B8: The system prevents a second staff member from modifying a request with a non-stale claim.
- UC5-B9: The system permits the claimant to release a claim.
- UC5-B10: The system permits another staff member to reclaim a claim after 30 minutes without activity.
- UC5-B11: The system permits the claimant to replace all structured interpretation fields without invoking AI.
- UC5-B12: The system validates staff-edited duration, veterinarian, specialty, and time fields against active clinic rules.
- UC5-B13: The system permits the claimant to select one feasible slot from the full staff calendar.
- UC5-B14: The system atomically holds the complete staff-offer interval for no more than 24 hours.
- UC5-B15: The system limits a staff offer deadline to the request's remaining fallback lifetime.
- UC5-B16: The system exposes only the one current staff offer to the owner.
- UC5-B17: The system creates one `BOOKED` appointment when the owner accepts a valid staff offer.
- UC5-B18: The system moves an accepted staff-offered request to terminal `CONFIRMED`.
- UC5-B19: The system releases the staff-offer hold when the owner rejects it.
- UC5-B20: The system records the rejected staff-offer veterinarian/start pair for the request.
- UC5-B21: The system returns a rejected staff-offered request to `STAFF_QUEUED`.
- UC5-B22: The system releases an expired staff-offer hold without recording rejection.
- UC5-B23: The system returns an expired staff offer to `STAFF_QUEUED` when fallback time remains.
- UC5-B24: The system replaces a staff-revoked offer without extending the revoked offer's deadline.
- UC5-B25: The system permits staff to book a feasible slot directly after recording an offline-coordination reason.
- UC5-B26: The system records a fallback direct booking as staff-booked rather than owner-approved.
- UC5-B27: The system moves a directly booked fallback request to terminal `CONFIRMED`.
- UC5-B28: The system applies every hard booking constraint to a direct staff booking.
- UC5-B29: The system permits staff to cancel a nonterminal request only with a nonblank reason.
- UC5-B30: The system clears all request-associated temporary reservation and work-claim state when staff cancels a request.
- UC5-B31: The system moves an unresolved fallback request to terminal `EXPIRED` seven days after its initial queue time.
- UC5-B32: The system releases an active offer when the overall fallback deadline expires.
- UC5-B33: The system treats an expired request as non-reopenable.
- UC5-B34: The system leaves the request queued when a concurrent conflict prevents the submitted staff offer or booking.
- UC5-B35: The system refreshes claim activity only for claiming, reclaiming, or a successful mutation by the current claimant.
- UC5-B36: The system materializes a missing owner horizon from the initial staff-queue instant using the request's captured settings.
- UC5-B37: The system preserves an owner horizon already materialized by confirmation when the request enters fallback.
- UC5-B38: The system applies the request's materialized owner horizon to staff offers and request-linked direct bookings.

## Out of scope

- Guaranteeing that staff can find a slot when the clinic has no capacity
- External contact or notification channels
- Overriding hard scheduling conflicts
- Reopening an expired request
- Multiple simultaneous offers to one owner
