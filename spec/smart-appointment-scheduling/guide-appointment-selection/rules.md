# Technical Constraints: UC4 — Guide Appointment Selection

## Design

Matching builds one immutable `SchedulingProblem` containing the confirmed request snapshot, clinic-local candidate grid, veterinarian catalog, effective availability, appointments, active reservations, exact rejections, and committed workload. One planning entity selects one veterinarian/start candidate. Timefold owns hard feasibility and lexicographic score calculation; a separate reservation transaction revalidates the selected value against live state.

## Rules

### UC4-RULE1
**Covers:** UC4-AC1–UC4-AC23, UC4-AC40, UC4-AC46, UC4-AC47
**MUST** build a detached immutable planning snapshot in a read-only transaction and close that transaction before solving. Before creating a solver operation, the application MUST perform only the mandated active-specialty existence check and enter `NO_SPECIALTY_VET` when it fails. Otherwise, the candidate value range MUST contain every unique veterinarian and clinic-local 15-minute start pair inside the request horizon; candidate facts MUST retain invalid/ambiguous offset state so Timefold hard constraints, rather than Java pre-filtering, own DST, availability, overlap, eligibility, owner-window, and rejection feasibility.
**Reason:** The selected Timefold design requires the solver to own feasibility while ensuring the planning problem cannot mutate JPA state or reoptimize another request.

### UC4-RULE2
**Covers:** UC4-AC1–UC4-AC28, UC4-AC46, UC4-AC47
**MUST** use a Timefold Constraint Provider with a `BendableLongScore`: hard levels for every feasibility invariant and ordered soft levels for preferred window, preferred veterinarian, earlier start, lower committed workload minutes, stable start instant, and stable veterinarian identifier. Workload MUST equal the summed intersection minutes of non-CANCELLED Appointment intervals with the request horizon and MUST exclude reservations.
**Reason:** Separate score levels preserve the specified lexicographic order without fragile numeric weight magnitudes and record the resolved workload definition.

### UC4-RULE3
**Covers:** UC4-AC3–UC4-AC5
**MUST** run one exhaustive single-entity solve on the bounded solver executor and return a suggestion only when exhaustive solving completes normally within two seconds with a hard-feasible assigned candidate. A normally completed solve with no hard-feasible non-rejected assignment MUST create `SUGGESTIONS_EXHAUSTED`; the application deadline MUST terminate and ignore an incomplete solve rather than accept its best-so-far value; timeout, exception, or executor rejection MUST create `SOLVER_UNAVAILABLE`.
**Reason:** A completed exhaustive phase makes the highest lexicographic candidate deterministic; a time-limited best-so-far result would not prove the acceptance-criteria ranking.

### UC4-RULE4
**Covers:** UC4-AC23–UC4-AC34, UC4-AC36, UC4-AC42, UC4-AC48
**MUST** commit a solver winner through the shared reservation service under RULE-4 locks. That transaction MUST confirm the operation token and `MATCHING` state, reload live constraint facts, revalidate the complete interval, and insert one `GUIDED_HOLD` reservation with the request snapshot deadline. A conflict MUST leave no reservation and return the request to `MATCHING` with the typed slot-taken outcome.
**Reason:** Solver snapshots can become stale; only a short locked transaction can make the one-offer and no-overlap guarantees authoritative.

### UC4-RULE5
**Covers:** UC4-AC35–UC4-AC41, UC4-AC44
**MUST** persist exact rejections with a database unique constraint on `(request_id, veterinarian_id, start_instant)`. Rejection MUST insert-or-observe that row, release the reservation, and return to `MATCHING`; pure expiry MUST release without inserting and MUST leave the pair eligible.
**Reason:** A unique rejection record makes repeated rejection idempotent and preserves the specified distinction between rejection and expiry.

### UC4-RULE6
**Covers:** UC4-AC29–UC4-AC30, UC4-AC33–UC4-AC45
**MUST** require the current reservation identifier and request version on accept/reject commands. Acceptance MUST atomically create or return the single Appointment linked to the request; a command for a non-current reservation MUST resolve the locked current request state and return a specific stale/expired/taken/already-accepted outcome. Owner projections MUST contain only the allowlisted suggestion fields.
**Reason:** Browser retries and countdown races must converge on current state without duplicate appointments or generic error pages.

## Cross-Reference

| AC | Rules |
|---|---|
| UC4-AC1 | RULE-5, UC4-RULE1, UC4-RULE2 |
| UC4-AC2 | UC4-RULE1 |
| UC4-AC3 | RULE-6, UC4-RULE3 |
| UC4-AC4 | RULE-6, UC4-RULE3 |
| UC4-AC5 | RULE-6, UC4-RULE3 |
| UC4-AC6 | RULE-2, UC4-RULE1, UC4-RULE2 |
| UC4-AC7 | UC4-RULE1, UC4-RULE2 |
| UC4-AC8 | UC4-RULE1, UC4-RULE2 |
| UC4-AC9 | UC4-RULE1, UC4-RULE2 |
| UC4-AC10 | RULE-2, UC4-RULE1, UC4-RULE2 |
| UC4-AC11 | UC4-RULE1, UC4-RULE2 |
| UC4-AC12 | UC4-RULE1, UC4-RULE2 |
| UC4-AC13 | UC4-RULE1, UC4-RULE2 |
| UC4-AC14 | RULE-5, UC4-RULE1, UC4-RULE2 |
| UC4-AC15 | RULE-5, UC4-RULE2 |
| UC4-AC16 | RULE-5, UC4-RULE2 |
| UC4-AC17 | RULE-5, UC4-RULE2 |
| UC4-AC18 | RULE-5, UC4-RULE2 |
| UC4-AC19 | RULE-5, UC4-RULE2 |
| UC4-AC20 | RULE-5, UC4-RULE2 |
| UC4-AC21 | RULE-5, UC4-RULE2 |
| UC4-AC22 | RULE-5, UC4-RULE2 |
| UC4-AC23 | UC4-RULE1, UC4-RULE2 |
| UC4-AC24 | UC4-RULE2 |
| UC4-AC25 | UC4-RULE2 |
| UC4-AC26 | UC4-RULE2 |
| UC4-AC27 | UC4-RULE2 |
| UC4-AC28 | UC4-RULE2 |
| UC4-AC29 | RULE-8, UC4-RULE6 |
| UC4-AC30 | RULE-8, RULE-10, UC4-RULE6 |
| UC4-AC31 | RULE-3, RULE-4, UC4-RULE4 |
| UC4-AC32 | RULE-4, UC4-RULE4 |
| UC4-AC33 | RULE-1, UC4-RULE6 |
| UC4-AC34 | RULE-1, UC4-RULE6 |
| UC4-AC35 | UC4-RULE5 |
| UC4-AC36 | UC4-RULE5 |
| UC4-AC37 | UC4-RULE5 |
| UC4-AC38 | RULE-6, UC4-RULE1 |
| UC4-AC39 | RULE-2, UC4-RULE5 |
| UC4-AC40 | RULE-2, UC4-RULE5 |
| UC4-AC41 | UC4-RULE5 |
| UC4-AC42 | RULE-4, RULE-12, UC4-RULE4 |
| UC4-AC43 | RULE-12, UC4-RULE6 |
| UC4-AC44 | RULE-12, UC4-RULE5 |
| UC4-AC45 | RULE-12, UC4-RULE6 |
| UC4-AC46 | UC4-RULE1, UC4-RULE2 |
| UC4-AC47 | UC4-RULE1, UC4-RULE2 |
| UC4-AC48 | RULE-4, UC4-RULE4 |

## Design Exclusions

- No Java pre-filter followed by Timefold-only ranking
- No best-so-far suggestion after the two-second deadline
- No solver mutation of existing appointments or staff direct-booking path
- No exposure of candidate lists, score details, workloads, or availability to owners
