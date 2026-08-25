# Spec Review: Smart Appointment Scheduling

## Summary
- Feature: Smart Appointment Scheduling
- Verdict: **PASS** (rerun after the initial PASS WITH CONDITIONS; all 3 majors resolved in `rules.md`)
- Counts: 0 blockers, 0 majors, 1 minor
- Action: None — the plan step (`05-tasks`) is unblocked.

## Discipline Check

Clean. Criteria `Covers:` lines reference real behaviors (B-1..B-45; every B-N in ≥1 AC). Rules `Covers:` lines reference real criteria (AC-1..AC-68; AC-12 explicitly "(none needed)"). Cross-Reference spot-checks pass (AC-1→RULE-13/14, AC-54→RULE-18/19, AC-66→RULE-4). All ACs match an EARS template. The rules fixes changed rule *mechanisms* only; no AC/B-N coverage changed, so the trace chain is intact.

## Conflicts

None. Scope exclusions hold; negative decisions (no Quartz, no REST/SPA, no job broker, and now no partial/filtered indexes) are respected across all rules.

## Codebase Grounding

Design fits the codebase. Packages are creatable under `org.springframework.samples.petclinic`; test strategy matches existing infra; dual build files honored. The three prior grounding findings are resolved:

- **MAJOR-1 (resolved):** RULE-11 now enforces exclusivity through a single `slot_occupancy` table with a plain `UNIQUE (vet_id, start_time)` — enforceable in one table rather than an impossible cross-table constraint.
- **MAJOR-2 (resolved):** RULE-11 and RULE-22 now use plain unique constraints (single-table occupancy row; nullable `active_pet_key` for one-active-request-per-pet), portable to H2/MySQL/Postgres, with an explicit `MUST NOT` on partial/filtered indexes.
- **MAJOR-3 (resolved):** RULE-4 now allows `timefold-solver-core` + a manual `SolverManager` when no Boot 4.1-compatible starter exists, keeping the solver ACs satisfiable.

## EARS ↔ Test Strategy

Adequate. RULE-24 now names three-point tests for each boundary triple, per-transition tests for state-machine transitions, and prohibited-outcome assertions for negative authz ACs — matching the EARS patterns criteria uses. Prior MINOR-1 addressed.

## Risk Hotspots

Carried forward from the initial review — still the areas most likely to bite during implementation even with a clean spec:

1. **Timefold "next-best-slot" semantics** — re-solve per rejection with accumulated `(vet,start)` exclusions can loop on near-identical slots or declare infeasible early. *Mitigation:* rejected pairs as hard constraints; assert convergence-to-exhaustion in a small-fixture solver test.
2. **Spring AI 2.0.1 ↔ Boot 4.1 ↔ Ollama coordinate drift** — three fast-moving versions must align; a wrong BOM/coordinate fails the build. *Mitigation:* pin the Spring AI BOM; one mocked-`ChatModel` integration test; confirm the no-`ChatModel` fallback (RULE-16) at startup.
3. **Async status/state races** — meta-refresh polling reads request state while `@Async` work writes it. *Mitigation:* transactional state writes; a status view that maps every intermediate/transient state to a defined screen.
4. **Hold-expiry vs. accept race** — a hold can lapse between Accept-click and commit. *Mitigation:* re-validate expiry inside the accept transaction; let the `slot_occupancy` unique constraint arbitrate ties.
5. **Timezone correctness of availability** — shifts/closures resolved in `ClinicSettings.timeZone` while JVM/DB may differ. *Mitigation:* compute exclusively in clinic zone (RULE-20); add DST-boundary availability tests.

## Minors (do not affect verdict)

- **MINOR-2:** `rules.md` Design names the sweeper component `HoldSweeper` while RULE-11 calls it "a `@Scheduled` sweeper." Harmless naming drift.
