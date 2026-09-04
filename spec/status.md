# Status: Smart Appointment Scheduling

## Current

- Task: phase-1 checkpoint
- Status: PENDING

## Completed

- task-1.1
- task-1.2
- task-1.3
- task-1.4
- task-1.5
- task-1.6
- task-1.7

## Phase Approvals

- phase-1: PENDING (cp-1 re-converged 2026-09-04: REJECT — 4 critical, 8 gaps, 2 protocol; see `convergence/cp-1.md`)
- phase-2: PENDING
- phase-3: PENDING
- phase-4: PENDING
- phase-5: PENDING

## Blockers

(empty if none)

## Deviations

- task-1.6: Timefold Solver 2.5.0 rejects the planned `timefold.solver.solve.duration` key; the equivalent supported key `timefold.solver.termination.spent-limit=1s` is used.
- Legacy waiver cp-1: items 2,3,5 of the converge Protocol Gate waived — phase executed before the protocol (commits 056ab3b, 7cb2696, 7a5be39); approved by the user on 2026-09-04. Phase-1 was built as two batched commits (tasks 1.1–1.4, tasks 1.5–1.7) plus one cp-1 resolution commit; history is not rewritten. `spec/checkpoints/cp-1.md` is written retroactively from the committed state (item 1 not waived); items 4, 6 and 7 are audited in full. The waiver covers cp-1 only; cp-2 onward is audited under the full gate.

- cp-1 resolution decisions (user, 2026-09-04): REVISE 1–3 of `convergence/cp-1.md` (C-1, C-2, C-3 and the E2E test fix) are applied now as executor revisions; the remaining findings (G-2..G-8, P-4, P-5, K-1..K-3) are carried into the `tasks` re-plan as `Checkpoint 1 Remediation` tasks (`source: converge/cp-1/<id>`) rather than fixed before re-planning.
- Waiver cp-1/C-4 (AC-139): the two stock `RANDOM_PORT` classes (`PetClinicIntegrationTests`, `PetClinicConcurrencyTests`) predate the feature; AC-139 is read as "exactly one *feature* random-port smoke test" (`SmokeTests`). Approved by the user on 2026-09-04.
- Waiver cp-1/G-1 (AC-138): phase-1 proves the thin UC-1 path only; AC-138's full interleaved lifecycle is a single claim of the later phase that completes it (re-plan removes AC-138 from phase-1 `covers`). Approved by the user on 2026-09-04.
- cp-1/D-2 (RULE-17 default provider): NOT accepted as Δ. User decision: restore the `ollama` default per RULE-17 (`SCHEDULING_AI_PROVIDER` defaults to `ollama`; `matchIfMissing` on the Ollama interpreter); tests pin `scheduling.ai.provider=stub` in the test profile. Applied as a REVISE on task-1.6 together with REVISE 1–3.
- task-1.3: `SecurityConfig.java` (declared) was built as `SecurityConfiguration.java`; rename recorded here per cp-1/P-4. The re-plan uses the as-built name.

## Notes

- task-1.1: Configured Flyway and ArchUnit dependencies; excluded generated build artifacts in checkstyle nohttp config. Revised after cp-1: RULE-2/RULE-26 is resolved as an exact boundary containing the three Timefold support types required by RULE-26 plus the named ranker/interpreter adapter implementations; substring/name-pattern exemptions are forbidden and a `ViolatingSolutionSupport` fixture proves the rule bites.
- task-1.2: Implemented V1-V4 migrations with BCrypt hashed seeded accounts and exact clinic configurations; validated active_pet_id partial unique constraint behavior. Revised after cp-1 with `SeedMigrationTests`, which migrates a unique in-memory H2 database and compares every normative account, owner link, password, opening hour, vet block, exception, part-of-day, and config field as closed sets.
- task-1.3: Implemented the exact SecurityFilterChain matrix, including authenticated-only logout, a closed `UserRole` enum plus database check, role-bearing/failed-session login assertions, and `OwnerSchedulingAccessService` whose pet/request/appointment lookups always include the authenticated owner and return the same 404 for missing and other-owner resources. `SecurityMatrixWebTests` enumerates the protected GET/POST surface and proves denial, no protected response data, no database mutation, and identical other-owner/missing outcomes.
- task-1.4: Added real stock-layout owner/staff landing pages, corrected owner navigation to exactly My Pets/My Appointments and staff navigation to stock entries plus Scheduling queue/Calendar/Clinic settings, keyed visible layout attributes and language names, and synchronized feature placeholders across all 11 bundles with structural localization tests.
- task-1.5: Wired the runtime Clock from the seeded clinic time zone, removed direct system-clock calls from pet/visit flows, added a structural/configured-zone check, added an exhaustive request action-by-state refusal matrix with no repository/event interaction, and proved refused appointment completion creates no visit or change row.
- task-1.6: Added configuration-selected stub/Ollama interpreters, property-driven model selection, bounded connect/read timeouts, synchronous consent-to-interpretation orchestration, owner request pages and active-request-aware pet selection. Interpretations now round-trip every scalar/window field and preferred vet. The production ranker enumerates migrated clinic/vet availability and invokes the Timefold single-slot model; confirmation and acceptance pessimistically lock the vet before overlap re-validation, with ordering evidence.
- task-1.7: Replaced the service-only scenario with an authenticated MockMvc UC-1 path through the real filter chain and CSRF, asserting rendered and persisted state from request creation through accepted appointment. The single scheduling smoke test now logs in with seeded credentials, retains cookies/CSRF, and loads `/my/appointments` on a random-port server.
- cp-1 convergence revisions (2026-09-04): All 7 critical and 6 gap findings in `convergence/cp-1.md` addressed. Java 21 full suite: 149 tests, 0 failures, 0 errors, 0 skipped. phase-1 remains PENDING for independent reconvergence.
- task-1.6 (revise cp-1/C-1,C-2), base 7644417: owner read models load every rendered association inside the repository query — `SchedulingRequestRepository.java:41` `@EntityGraph(LOAD: pet, owner, heldVet)` on `findByIdAndOwnerId`; `AppointmentRepository.java:38,43` `JOIN FETCH a.pet JOIN FETCH a.vet` on `findOwnedById`/`findAllOwnedBy`; `InterpretationRepository.java:39` `@EntityGraph(LOAD: preferredVet)` on `findTopByRequestIdOrderByVersionDesc` (`requestDetail.html:14`). `open-in-view` stays `false`. Gate: suite 149/0/0/0; tree clean. Red evidence before the fix: non-transactional `SchedulingLifecycleE2eTests` (revision 3, written first) errored 2/2 with `LazyInitializationException [Pet#1] - no session` at `my/requestDetail` line 6; after this fix every `/my/requests/{id}` render in that class returns 200.
