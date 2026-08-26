# Acceptance Criteria: UC4 — Guide Appointment Selection

## Functional

### UC4-AC1: Isolate the current solve

**Covers:** UC4-B1

When matching begins for a request, the system shall solve only that request against an immutable snapshot of all other scheduling inputs.

### UC4-AC2: Keep confirmed appointments pinned

**Covers:** UC4-B2

When a request is solved, the system shall preserve every confirmed appointment's veterinarian and interval.

### UC4-AC3: Limit solver attempts

**Covers:** UC4-B3

When matching begins for a request, the system shall make no more than one solver attempt.

### UC4-AC4: Enforce the solver deadline

**Covers:** UC4-B3, UC4-B4

If a solver attempt has not produced a result when its two-second deadline is reached, then the system shall create `STAFF_QUEUED(SOLVER_UNAVAILABLE)`.

### UC4-AC5: Handle solver errors

**Covers:** UC4-B4

If the solver returns an error, then the system shall create `STAFF_QUEUED(SOLVER_UNAVAILABLE)` without retrying automatically.

### UC4-AC6: Keep candidates on the scheduling grid

**Covers:** UC4-B5

When the system constructs a candidate, the system shall use a start and duration aligned to 15-minute increments.

### UC4-AC7: Include starts after the future grid boundary

**Covers:** UC4-B6

When a candidate starts after the next future clinic-local grid boundary and meets all other constraints, the system shall keep the candidate eligible.

### UC4-AC8: Include a start at the future grid boundary

**Covers:** UC4-B6

When a candidate starts exactly at the next future clinic-local grid boundary and meets all other constraints, the system shall keep the candidate eligible.

### UC4-AC9: Exclude starts before the future grid boundary

**Covers:** UC4-B6

If a candidate starts before the next future clinic-local grid boundary, then the system shall exclude the candidate.

### UC4-AC10: Exclude invalid DST starts

**Covers:** UC4-B7

If a candidate's clinic-local start is nonexistent or ambiguous because of a daylight-saving transition, then the system shall exclude the candidate.

### UC4-AC11: Include starts inside the owner horizon

**Covers:** UC4-B8

When a candidate starts inside the request's owner-horizon snapshot and meets all other constraints, the system shall keep the candidate eligible.

### UC4-AC12: Include the owner-horizon start boundary

**Covers:** UC4-B8

When a candidate starts at the request's next-future-grid horizon boundary and meets all other constraints, the system shall keep the candidate eligible.

### UC4-AC13: Exclude the owner-horizon end boundary

**Covers:** UC4-B8

If a candidate starts at or after the exclusive end of the request's owner-horizon snapshot, then the system shall exclude the candidate.

### UC4-AC14: Require one contiguous availability block

**Covers:** UC4-B9

When a candidate is evaluated, the system shall keep it eligible only when its complete interval fits within one contiguous effective veterinarian availability block.

### UC4-AC15: Exclude veterinarian conflicts

**Covers:** UC4-B10

If a candidate overlaps a confirmed appointment or active hold for its veterinarian, then the system shall exclude the candidate.

### UC4-AC16: Exclude owner conflicts

**Covers:** UC4-B11

If a candidate overlaps a confirmed appointment or active hold for its owner, then the system shall exclude the candidate.

### UC4-AC17: Exclude pet conflicts

**Covers:** UC4-B12

If a candidate overlaps a confirmed appointment or active hold for its pet, then the system shall exclude the candidate.

### UC4-AC18: Permit adjacent intervals

**Covers:** UC4-B10, UC4-B11, UC4-B12

When a candidate is adjacent to another interval without intersecting it under half-open interval semantics, the system shall not treat the adjacency as an overlap.

### UC4-AC19: Exclude closures and leave

**Covers:** UC4-B13

If any part of a candidate interval is covered by a clinic closure or veterinarian leave, then the system shall exclude the candidate.

### UC4-AC20: Require specialty eligibility

**Covers:** UC4-B14

When a specialty-care candidate is evaluated, the system shall keep it eligible only when the veterinarian and the required assigned specialty are active.

### UC4-AC21: Permit active veterinarians for general care

**Covers:** UC4-B15

When a general-care candidate uses an active veterinarian and meets all other constraints, the system shall keep the candidate eligible regardless of specialty assignments.

### UC4-AC22: Enforce owner windows

**Covers:** UC4-B16

If a candidate lies outside the owner's allowed space or intersects an excluded owner window, then the system shall exclude the candidate.

### UC4-AC23: Exclude exact rejected pairs

**Covers:** UC4-B17

If a candidate has an exact veterinarian and start pair already rejected for the request, then the system shall exclude the candidate.

### UC4-AC24: Rank preferred windows first

**Covers:** UC4-B18

When two otherwise-comparable feasible candidates differ in window preference, the system shall rank the preferred-window candidate first.

### UC4-AC25: Rank the preferred veterinarian second

**Covers:** UC4-B19

When equally preferred-window candidates differ by veterinarian preference, the system shall rank the preferred veterinarian first.

### UC4-AC26: Rank earlier starts before workload

**Covers:** UC4-B20

When candidates are tied on window and veterinarian preference, the system shall rank the earlier start before comparing veterinarian workload.

### UC4-AC27: Rank lighter workload last among preferences

**Covers:** UC4-B21

When candidates are tied on window preference, veterinarian preference, and start ordering, the system shall rank first the veterinarian with fewer total minutes from non-cancelled appointments intersecting the request's owner horizon; temporary holds shall not contribute to that total.

### UC4-AC28: Apply stable final tie-breakers

**Covers:** UC4-B22

When feasible candidates remain tied after workload ranking, the system shall order them by start instant and then veterinarian identifier.

### UC4-AC29: Expose one current suggestion

**Covers:** UC4-B23

While an owner request has a current automated suggestion, the system shall expose no more than that one suggestion to the owner.

### UC4-AC30: Limit suggestion details

**Covers:** UC4-B24

When the system presents an automated suggestion, the system shall expose only veterinarian name, specialties, date, start, duration, and hold countdown.

### UC4-AC31: Hold the complete suggestion interval

**Covers:** UC4-B25

When a suggestion is created, the system shall atomically reserve its complete interval until the guided-hold deadline captured for the request.

### UC4-AC32: Prevent concurrent overlapping reservations

**Covers:** UC4-B26

If a concurrent hold or booking has already reserved overlapping veterinarian, owner, or pet time, then the system shall reject the losing reservation and shall not create an overlap.

### UC4-AC33: Create one appointment on acceptance

**Covers:** UC4-B27

While a guided hold is current and valid, when the owner accepts it, the system shall create exactly one `BOOKED` appointment for the held interval.

### UC4-AC34: Confirm the accepted request

**Covers:** UC4-B28

When acceptance of a valid guided hold creates the appointment, the system shall move the scheduling request to terminal `CONFIRMED`.

### UC4-AC35: Release a rejected hold

**Covers:** UC4-B29

While a guided hold is current, when the owner rejects it, the system shall release the held interval.

### UC4-AC36: Record the exact rejected pair

**Covers:** UC4-B30

When an owner rejects a current guided suggestion, the system shall record that suggestion's exact veterinarian and start pair for the request.

### UC4-AC37: Return a rejected request to matching

**Covers:** UC4-B31

When an owner rejects a current guided suggestion, the system shall move the request to `MATCHING` without starting a new solve automatically.

### UC4-AC38: Re-solve on explicit request

**Covers:** UC4-B32

While a request is `MATCHING`, when the owner asks for another option, the system shall solve against the live calendar and accumulated exact rejections.

### UC4-AC39: Release an expired hold without rejection

**Covers:** UC4-B33

When a guided hold reaches its deadline, the system shall release the interval without recording its veterinarian and start pair as rejected.

### UC4-AC40: Stop after guided-hold expiry

**Covers:** UC4-B34

When a guided hold expires, the system shall return the request to `MATCHING` without acquiring another hold automatically.

### UC4-AC41: Keep an expired pair eligible

**Covers:** UC4-B35

When a guided hold expires without rejection, the system shall keep its veterinarian and start pair eligible for a later solve if it remains feasible.

### UC4-AC42: Recover a concurrency loser

**Covers:** UC4-B36

When another transaction wins a reservation conflict, the system shall return the losing request to `MATCHING` with a slot-taken message.

### UC4-AC43: Make acceptance idempotent

**Covers:** UC4-B37

When acceptance is repeated after the request was successfully confirmed, the system shall return the existing confirmed appointment without creating another appointment.

### UC4-AC44: Make rejection idempotent

**Covers:** UC4-B38

When rejection is repeated for the same suggestion, the system shall preserve a single exact rejection record.

### UC4-AC45: Redirect stale hold actions

**Covers:** UC4-B39

If an owner submits an action for a hold that is no longer current, then the system shall redirect to the request's current state with a message specific to the stale action.

### UC4-AC46: Escalate exhausted suggestions

**Covers:** UC4-B40

When no feasible candidate remains after exact rejections are applied, the system shall create `STAFF_QUEUED(SUGGESTIONS_EXHAUSTED)`.

### UC4-AC47: Escalate missing specialty capacity

**Covers:** UC4-B41

When a specialty-care request has no active veterinarian with the required active specialty, the system shall create `STAFF_QUEUED(NO_SPECIALTY_VET)` before invoking the solver.

## Non-functional

### UC4-AC48: Preserve booking consistency under concurrency

**Covers:** UC4-B26

When concurrent reservation-changing operations target overlapping veterinarian, owner, or pet intervals, the system shall commit at most one overlapping hold or booking.

## Coverage exclusions

- Authorization: owner and staff route authorization is owned by UC1; this use case covers only the data disclosed by an authorized suggestion flow.
- Accessibility: the specification defines the suggestion fields and countdown but no measurable accessibility contract for their presentation.
