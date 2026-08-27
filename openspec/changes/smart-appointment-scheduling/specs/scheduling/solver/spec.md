## Purpose

Finds, per request, the single best next appointment slot over a fixed start-time grid using a constraint solver, with deterministic ranking and permanent exclusion of rejected slots so the guided "ask again" loop provably progresses toward a slot or a definite no-fit outcome.

## ADDED Requirements

### Requirement: Per-request single-slot solve over a fixed grid

For a confirmed request the system SHALL solve for exactly one suggested `(veterinarian, start)` slot at a time. Candidate start instants SHALL be drawn from a fixed start-time grid whose granularity is the clinic's configured grid granularity. Existing appointments and currently active holds SHALL be treated as immovable facts during the solve.

#### Scenario: Solver returns a single slot from the grid

- **GIVEN** a request in state `CONFIRMED` with a validated interpretation
- **WHEN** the solver runs
- **THEN** it returns at most one `(veterinarian, start)` slot whose start lies on the configured start-time grid

#### Scenario: Existing bookings and holds are immovable during the solve

- **GIVEN** existing appointments and active holds on the calendar
- **WHEN** the solver evaluates candidate slots
- **THEN** it treats those appointments and holds as fixed and never proposes a slot that conflicts with them

### Requirement: Hard feasibility constraints

A candidate slot SHALL be feasible only if all hard constraints hold: the full visit duration fits inside one continuous block of the veterinarian's effective availability; the slot does not overlap any existing appointment or active hold; the start is not inside an excluded window from the interpretation; the `(veterinarian, start)` pair has not been previously rejected for this request; and, when the interpretation requires a specialty, the veterinarian has that specialty. A candidate violating any hard constraint SHALL NOT be suggested.

#### Scenario: Duration must fit one continuous availability block

- **GIVEN** a veterinarian whose availability has a gap that is shorter than the required visit duration
- **WHEN** the solver considers a start whose full duration would span that gap
- **THEN** the candidate is infeasible and is not suggested

#### Scenario: Overlap with an appointment or active hold is infeasible

- **GIVEN** a candidate `(vet V, start T)` whose interval overlaps an existing appointment or an active hold
- **WHEN** the solver evaluates it
- **THEN** the candidate is infeasible and is not suggested

#### Scenario: Excluded windows are never suggested

- **GIVEN** an interpretation that excludes a particular window
- **WHEN** the solver evaluates a start inside that excluded window
- **THEN** the candidate is infeasible and is not suggested

#### Scenario: Specialty is required when the interpretation needs it

- **GIVEN** an interpretation that requires a specialty
- **WHEN** the solver evaluates a veterinarian who lacks that specialty
- **THEN** every candidate for that veterinarian is infeasible for this request

### Requirement: Ordered soft-score ranking

Among feasible candidates the system SHALL rank slots by an ordered soft score. A start inside a preferred window SHALL rank above a start that is only inside an allowed window; a soft bonus SHALL be applied when the veterinarian matches the interpretation's preferred veterinarian; and, all else equal, sooner starts SHALL rank above later starts. The preferred-veterinarian bonus SHALL be a soft preference only and SHALL NEVER make a request infeasible or dead-end when the preferred veterinarian is unavailable.

#### Scenario: Preferred window beats allowed window

- **GIVEN** two feasible candidates, one whose start is inside a preferred window and one only inside an allowed window
- **WHEN** the solver ranks them
- **THEN** the candidate inside the preferred window is chosen first

#### Scenario: Preferred veterinarian is a soft bonus, not a gate

- **GIVEN** an interpretation naming a preferred veterinarian who has no feasible slot
- **WHEN** the solver ranks feasible candidates from other veterinarians
- **THEN** a feasible slot from another veterinarian is still suggested and the preferred veterinarian only contributes a soft bonus when available

#### Scenario: Sooner is better when all else is equal

- **GIVEN** two otherwise equally-scored feasible candidates at different start instants
- **WHEN** the solver ranks them
- **THEN** the earlier start is chosen first

### Requirement: Deterministic tie-break

Ranking SHALL be fully deterministic and unit-testable. When two candidates are otherwise equally scored, the system SHALL break the tie by ascending veterinarian identifier and then by ascending start instant. The same inputs SHALL always yield the same suggested slot.

#### Scenario: Equal scores break by veterinarian id then start instant

- **GIVEN** two feasible candidates with identical soft scores
- **WHEN** the solver breaks the tie
- **THEN** the candidate with the lower veterinarian identifier wins, and if identifiers are equal the earlier start instant wins

#### Scenario: Ranking is reproducible for identical inputs

- **GIVEN** the same request, calendar, and set of prior rejections
- **WHEN** the solver runs twice
- **THEN** it returns the identical suggested slot both times

### Requirement: Provable progression and termination

Because each rejected `(veterinarian, start)` pair is permanently excluded from future solves for the request, each successive "ask again" SHALL yield a strictly different slot than any previously rejected one, and the candidate set SHALL strictly shrink. The loop SHALL therefore terminate: it either returns a new feasible slot or reports that no feasible slot remains.

#### Scenario: Ask again yields a strictly progressing next slot

- **GIVEN** an owner who rejected the suggested slot `(vet V, start T)`
- **WHEN** the owner asks for another option
- **THEN** the solver returns a different feasible slot and never re-offers `(vet V, start T)` for this request

#### Scenario: Exhausted candidates report no feasible slot

- **GIVEN** a request whose every feasible `(veterinarian, start)` candidate has been rejected
- **WHEN** the owner asks for another option
- **THEN** the solver reports that no feasible slot remains and the request is routed to the staff fallback queue rather than looping forever

### Requirement: No feasible slot is a definite outcome, not an error

When a variable visit length or the owner's constraints cannot fit any continuous availability block, the solver SHALL report a definite no-feasible-slot outcome that routes the request to the staff fallback queue, rather than surfacing a bare error to the owner.

#### Scenario: Duration cannot fit any availability block

- **GIVEN** a required visit duration longer than any continuous availability block across all eligible veterinarians within the booking horizon
- **WHEN** the solver runs
- **THEN** it returns no feasible slot and the request is routed to the staff fallback queue

### Requirement: Solver unavailability routes to staff

If the solver is unavailable or fails to run, the system SHALL route the request to the staff fallback queue so the owner is never dead-ended.

#### Scenario: Solver failure routes to staff

- **GIVEN** a confirmed request
- **WHEN** the solver is unavailable or fails to produce a result
- **THEN** the request is routed to the staff fallback queue instead of surfacing an error to the owner
