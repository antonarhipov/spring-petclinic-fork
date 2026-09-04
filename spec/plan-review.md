# Plan Review: Smart Appointment Scheduling

## Summary
- Feature: Smart Appointment Scheduling
- Plan: `spec/tasks.yaml` (5 phases, 48 tasks, organizing principle `walking_skeleton then feature_slice`, `replanned_after: cp-1.1`)
- Round: 2 (round 1 on 2026-09-04 was **FAIL** — 5 blockers, 4 majors; its findings are listed under *Round-1 resolution status* below)
- Verdict: **PASS**
- Counts: 0 blockers, 0 majors, 0 minors
- Action: Round-1 FAIL items are resolved in the current plan and upstream as-built notes. `execute` may start at task-2.1 (the known red `SchedulingProviderPinningTests` is pre-declared and closed there).

## Checkpoint Reachability

- **phase-1 / cp-1:** Immutable as-built record; reachability remains via cp-1.1. Controllers, templates, security, migrations and HTTP evidence are present. Legacy waivers still apply only here.
- **phase-2 / cp-2:** Owner edit/abandon/route/another routes owned by task-2.4; UC-1/2/3 HTTP tests and concurrency tests named. task-2.1 restores the green baseline (model-tag sync) before guards. UC-1 waiting step is satisfiable via the RULE-44 clarification + `InterpretingPageTests` / `LatchedRequestInterpreter` (task-2.19).
- **phase-3 / cp-3:** Queue/detail/interpretation/suggest/booking surface in task-3.1; queue, interpretation, Suggest-button and direct-book tasks present. UC-4 e2e is Suggest + attach/leave-open only; calendar-pick explicitly deferred to cp-4. Mapping transfer for `/staff/queue` is in task-3.1.
- **phase-4 / cp-4:** Calendar/settings/availability/appointment controllers in task-4.1 with `StaffPageController` removal/deletion; full calendar (4.2); picking mode + free-cell + UC-4 calendar branch (4.3); settings/availability/conflicts/lifecycle/UC-5 e2e follow. Ambiguous-mapping risk closed in-task.
- **phase-5 / cp-5:** Owner detail/cancel (5.1–5.3), full lifecycle (5.4), terminal `SecurityMatrixWebTests` expansion + concurrency + localization manifest (5.5) own every cp-5 criterion.

## Artifact Exactness
Confirmed clean. Artifacts are exact paths (no globs/ellipsis/prose). Tests name classes and methods. `StaffPageController` carries a superseded/deleted-by marker on task-1.4; task-3.1/task-4.1 own the transfer. Localization universe is the checked-in manifest `src/test/resources/localization/feature-sources.txt` (task-5.5). New packages fit the existing feature layout.

## Route Inventory
- **phase-1:** Historical inventory including `GET /403 → task-1.3`; the rules matrix now has `/403` (GET, authenticated any role).
- **phase-2:** New owner action routes → task-2.4; existing owner request routes retained.
- **phase-3:** Staff queue/request/booking routes → task-3.1; no `/staff/calendar` claim (moved out).
- **phase-4:** Calendar/settings/availability/appointment/visit routes → task-4.1; pick routes → task-4.3; covered by rules `/staff/**`.
- **phase-5:** Owner appointment detail/cancel → task-5.1; lifecycle reuses prior routes.

Every phase that adds mappings has `routes` with controller-owning tasks. The skeleton HTTP surface remains the as-built phase-1 record under the re-plan contract.

## Coverage Consistency
Algebra clean both ways for all five phases (33+68+11+19+7 task-union ACs). 138 active ACs claimed exactly once; AC-139 is the sole `coverage_deferrals` entry (approved waiver). No deferral contradictions. AC-90/AC-97 live only in phase-4/task-4.3.

All five `review.md` hotspots are task `risk`s: Ollama/default (task-2.1, also 2.7), H2 locking (2.15), real async (2.5), structured-output (2.7), layout/localization (2.2, 5.5).

## Task Shape and Validation Literalness
New tasks are S/M, ≤ 5 ACs, list-shaped `TestClass.method → AC-n → shape` validations; every `covers.acs` entry appears in a bullet. Phase-1 L tasks / missing intermediate checkpoints remain legacy under dec-3 / the cp-1 waiver.

Round-1 under-specified bullets are sharpened: AC-34 timeout/`scheduling.ai.timeout` (2.7); AC-83 synchronous-in-lock (2.13); AC-89 create/edit by value (3.3); AC-93/94 full non-terminal matrices (3.5); AC-103 four edit kinds (4.6); AC-114 both prefill sources (4.8). The RULE-44 waiting-step fixture is literally encoded in task-2.19.

## Skeleton Ordering and Plan Guards
Re-plan after approved as-built phase-1. task-2.1 is the first new work: property sync, then `ArtifactInventoryTest` / `RouteInventoryTest` / `AcTagCoverageTest`, including `GET /403` matrix backing and superseded-marker semantics. The missing phase-1 `skeleton_test` / outside-in order remain waived legacy, not new findings. No new unsplit L tasks; UC HTTP tasks sit at phase ends with checkpoints after each phase.

## Findings

### Round-1 resolution status
| ID | Title | Status |
|---|---|---|
| BLOCKER-1 | Unresolved model default / red pinning test | **RESOLVED** — Δ D-4 in `spec.md`/`rules.md` (Overview + ED-2); assumption + task-2.1 sync the test copy to `ministral-3:14b`; known red pre-declared |
| BLOCKER-2 | Duplicate staff mappings | **RESOLVED** — task-3.1 removes the queue mapping; task-4.1 removes calendar/settings and deletes `StaffPageController`; one-handler assertions |
| BLOCKER-3 | Phase-3 calendar picking without surface | **RESOLVED** — AC-90/AC-97 and pick routes moved to task-4.3; phase-3 demo/cp-3 defer calendar-pick to cp-4 |
| BLOCKER-4 | Terminal `SecurityMatrixWebTests` unowned | **RESOLVED** — task-5.5 `modifies` the class; names final whole-surface methods; cp-5 criterion maps to it |
| BLOCKER-5 | `/403` missing from role matrix | **RESOLVED** — Security Surface row `/403` (GET, authenticated any role); assumption + route-guard validation |
| MAJOR-1 | Hotspots not on tasks | **RESOLVED** — all five hotspots have `risk` annotations |
| MAJOR-2 | Weaker validation than ACs | **RESOLVED** — six named dimensions sharpened (see *Task Shape*) |
| MAJOR-3 | UC-1 waiting-step fixture | **RESOLVED** — RULE-44 item 2 clarification + task-2.19 latch/real-executor page test |
| MAJOR-4 | AC-140 source universe | **RESOLVED** — manifest artifact + drift-fail validations in task-5.5 |

### New findings
None.

## Codebase grounding notes (informational)
HEAD still has `StaffPageController` mapping queue/calendar/settings and the test copy `spring.ai.ollama.chat.model=…gemma3` vs main `ministral-3:14b` — both are planned remediation targets (3.1/4.1 and 2.1), not plan defects. `LoginController` still uses `@RequestMapping("/403")` while the matrix inventories GET; access is matrix-backed for the inventoried method (task-2.2 may tighten it to `@GetMapping` while touching `SecurityConfiguration`; not a condition).

## Conditions for execute
None (PASS).

*(No Fix Plan — verdict is PASS.)*
