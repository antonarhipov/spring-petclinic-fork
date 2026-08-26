# UC4: Guide Appointment Selection

## Summary

After an owner confirms an interpretation, the system finds one feasible veterinarian and time, holds it briefly, and lets the owner accept or reject it without exposing the clinic calendar. Every solve uses live availability and atomic overlap checks, so concurrency cannot double-offer or double-book time.

## Resolved ambiguities

### Solver boundary

- Timefold solves one request against an immutable snapshot of confirmed appointments, active guided and staff-offer holds, effective veterinarian availability, closures, owner windows, eligibility, and exact rejections.
- The prospective suggestion is the only movable assignment. Confirmed appointments and other requests are never re-optimized.
- Solving receives one attempt and a two-second hard timeout. Failure creates `STAFF_QUEUED(SOLVER_UNAVAILABLE)`.

### Feasibility

A candidate is feasible only when all of these are true:

- start and duration use 15-minute increments;
- start is a real, unambiguous clinic-local time at or after the next future grid boundary;
- start is inside the request's owner-horizon snapshot;
- the complete interval fits one contiguous effective veterinarian availability block;
- it does not overlap a confirmed appointment or active hold for the veterinarian, owner, or pet;
- it is outside closures and veterinarian leave;
- the veterinarian is active and eligible for general care or has the required active specialty;
- it lies in the owner's allowed space and outside excluded windows; and
- its exact `(veterinarian, start)` pair was not rejected for this request.

### Ranking and disclosure

- Feasible candidates are ordered lexicographically by preferred window, preferred veterinarian, earliest start, and then lighter veterinarian workload. Workload is the total intersecting minutes of non-cancelled appointments inside the request's owner horizon; temporary holds affect feasibility but not workload.
- Remaining ties use start instant and veterinarian identifier for stable results.
- Urgent requests never reach automated ranking.
- The owner sees only the current suggestion's veterinarian name, specialties, date, start, duration, and hold countdown.

### Hold, accept, reject, and expiry

- Creating a suggestion atomically reserves its full interval for the request's guided-hold snapshot duration, which defaults to five minutes. Overlap is checked by duration, not just equal start time.
- Accepting a current hold creates one `BOOKED` appointment and terminates the request as `CONFIRMED`.
- Rejecting releases the hold, permanently records the exact `(veterinarian, start)` rejection for that request, and returns the request to `MATCHING`.
- Asking for another option starts a new solve against the live calendar and accumulated exact rejections.
- Pure expiry releases the hold without recording a rejection and returns the request to `MATCHING`. No new hold is acquired until the owner explicitly asks.
- An expired slot may be offered again when it remains the highest-ranked feasible option.

### Concurrency and stale actions

- Reservation-changing transactions serialize affected owner, pet, and veterinarian records in stable order and recheck overlaps before commit.
- When another transaction wins, the losing request returns to `MATCHING` and tells the owner that the slot was taken.
- Repeated accept or reject submissions are idempotent and never create duplicate appointments or rejection records.
- Stale actions redirect to the current request state with a specific message rather than a generic error page.

## Explicit assumptions

- Owner, pet, and veterinarian availability are half-open intervals `[start, end)`, so adjacent appointments do not overlap.
- Duration is fixed for a confirmed request; therefore `(veterinarian, start)` identifies the exact rejected suggestion.
- Staff-offer holds participate in the same overlap checks as guided holds.

## Handled edge cases

- If a preferred veterinarian is unavailable, another qualified veterinarian may be suggested.
- If all feasible pairs are rejected or unavailable, the request moves to staff fallback instead of returning bare no-availability.
- If a held slot expires while the page is open, polling updates the state but does not reserve another slot.
- If two owners are eligible for overlapping intervals with one veterinarian, only one hold commits.
- If a retry arrives after a successful acceptance, it resolves to the existing appointment.

## Behaviors to verify

- UC4-B1: The system solves only the current request against an immutable snapshot of all other scheduling inputs.
- UC4-B2: The system never moves or re-optimizes a confirmed appointment while solving.
- UC4-B3: The system limits each solve to one attempt and two seconds.
- UC4-B4: The system creates `STAFF_QUEUED(SOLVER_UNAVAILABLE)` when solving errors or times out.
- UC4-B5: The system considers only starts and durations aligned to 15-minute increments.
- UC4-B6: The system excludes starts before the next future clinic-local grid boundary.
- UC4-B7: The system excludes nonexistent and ambiguous DST local starts.
- UC4-B8: The system excludes starts outside the request's owner-horizon snapshot.
- UC4-B9: The system requires the full appointment interval to fit one contiguous effective veterinarian availability block.
- UC4-B10: The system excludes veterinarian intervals overlapping a confirmed appointment or active hold.
- UC4-B11: The system excludes owner intervals overlapping a confirmed appointment or active hold.
- UC4-B12: The system excludes pet intervals overlapping a confirmed appointment or active hold.
- UC4-B13: The system excludes intervals covered by clinic closure or veterinarian leave.
- UC4-B14: The system requires an active veterinarian with the request's required specialty for specialty care.
- UC4-B15: The system permits any active veterinarian for general care.
- UC4-B16: The system excludes time outside owner-allowed space or inside an excluded owner window.
- UC4-B17: The system excludes an exact veterinarian/start pair previously rejected for the request.
- UC4-B18: The system ranks a feasible preferred-window candidate above an otherwise comparable allowed-window candidate.
- UC4-B19: The system ranks the preferred veterinarian above another qualified veterinarian after window preference.
- UC4-B20: The system ranks earlier starts before load balancing after higher-priority preferences.
- UC4-B21: The system uses veterinarian workload only after window, veterinarian, and start preferences.
- UC4-B22: The system applies stable start-instant and veterinarian-identifier tie-breakers.
- UC4-B23: The system exposes at most one current suggestion to an owner.
- UC4-B24: The system exposes only veterinarian name, specialties, date, start, duration, and hold countdown for that suggestion.
- UC4-B25: The system atomically holds the suggestion's complete interval for the request's guided-hold snapshot duration.
- UC4-B26: The system prevents concurrent overlapping veterinarian, owner, or pet holds and bookings.
- UC4-B27: The system creates one `BOOKED` appointment when the owner accepts a valid current hold.
- UC4-B28: The system moves the request to terminal `CONFIRMED` after successful acceptance.
- UC4-B29: The system releases the hold when the owner rejects the suggestion.
- UC4-B30: The system records the rejected exact veterinarian/start pair for the request.
- UC4-B31: The system returns a rejected request to `MATCHING` for another explicit solve.
- UC4-B32: The system solves again against the live calendar when the owner asks for another option.
- UC4-B33: The system releases an expired guided hold without adding a rejection.
- UC4-B34: The system returns an expired guided request to `MATCHING` without automatically acquiring another hold.
- UC4-B35: The system may offer the same expired pair again when it remains highest-ranked and feasible.
- UC4-B36: The system returns a concurrency loser to `MATCHING` with a slot-taken message.
- UC4-B37: The system resolves repeated acceptance to the existing confirmed appointment without duplication.
- UC4-B38: The system resolves repeated rejection without duplicating the exact rejection.
- UC4-B39: The system redirects a stale hold action to the current request state with a specific message.
- UC4-B40: The system creates `STAFF_QUEUED(SUGGESTIONS_EXHAUSTED)` when no feasible non-rejected candidate remains.
- UC4-B41: The system creates `STAFF_QUEUED(NO_SPECIALTY_VET)` before solving when no active veterinarian has the required specialty.

## Out of scope

- Whole-clinic re-optimization
- Multiple simultaneous suggestions
- Owner access to the full availability calendar
- Rooms, equipment, or resources other than veterinarian time
- Waitlists or automatic notification when a rejected slot later becomes free
