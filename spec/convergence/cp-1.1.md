# Convergence: cp-1.1 — Phase 1 revisions after cp-1 (task mode)

## Summary
- Checkpoint: cp-1.1 (task mode over the four revision commits that answer `convergence/cp-1.md` REVISE 1–3 and the user decision on D-2): `8d35373` (task-1.6, C-1/C-2), `8b55b4a` (task-1.6, C-3), `6e32f14` (task-1.7, E2E without a test transaction), `df964f8` (task-1.6, D-2/P-5). Executor evidence: `spec/status.md` Notes lines 51–54 (one closure line per commit) and Deviations lines 35–39 (user decisions, 2026-09-04). No new `spec/checkpoints/` report is required in task mode.
- Previous verdict on this checkpoint: **REJECT** (`convergence/cp-1.md`, re-run after 7a5be39: 4 critical, 8 gaps, 2 protocol, 3 drift, 3 cosmetic).
- Verdict: **APPROVED WITH NOTES** — C-1, C-2, C-3 resolved and reproduced at runtime; D-2/P-5 resolved (RULE-17 default restored, tests pin `stub`); P-4 recorded; C-4 and G-1 waived by the user; G-3 closed by `df964f8` (AC-137 STRONG); G-2, G-4..G-8, K-1..K-3 (+ new K-4) carried into the `tasks` re-plan as `Checkpoint 1 Remediation` per `status.md:35`.
- Counts (this run): 0 critical, 0 new gaps (6 carried: G-2, G-4..G-8; G-3 closed), 0 protocol (P-4, P-5 resolved; P-1..P-3 remain waived), 0 new drift (D-1, D-3 folded at cp-1; D-2 resolved by code, not Δ), 1 new cosmetic (K-4) + 3 carried (K-1..K-3).
- Suite (run by converge, Java 25.0.1 runtime / source 21): `./mvnw -q test` → **152 run / 0 failed / 0 errors / 0 skipped** (summed from `target/surefire-reports/TEST-*.xml`; +3 vs cp-1: `SchedulingLifecycleE2eTests` 1→2, `SchedulingProviderPinningTests` 2 new). Plan guards (`RouteInventoryTest`, `ArtifactInventoryTest`, `AcTagCoverageTest`): absent (legacy plan; noted, not a finding).
- Working tree before and after the test run: clean (`git status --short` shows only the untracked tool logs `.output.txt` / `.background_output_*.txt`, which are not project files).

## Protocol Gate
Scope: one commit per revision, `git show --stat` read for each. Items 2, 3, 5 of the gate remain under the cp-1 legacy waiver (`status.md:33`, P-1..P-3 waived) — but the four revision commits themselves satisfy item 2 (one commit per revision, base named in each Notes line) and are audited in full for items 4, 6, 7.

| Task / revision | Commit | Files in commit match artifact + supporting | Artifacts by exact name | Gate evidence in status.md | Blocker due / raised |
|---|---|---|---|---|---|
| task-1.6 revise C-1, C-2 | `8d35373` | PASS — `AppointmentRepository.java`, `InterpretationRepository.java`, `SchedulingRequestRepository.java` (all under the task-1.6 `scheduling/**` glob) + `spec/status.md`; nothing outside scope | PASS — repositories exist by the names cited; `@EntityGraph(LOAD: pet, owner, heldVet)` at `SchedulingRequestRepository.java:41`, `JOIN FETCH a.pet JOIN FETCH a.vet` at `AppointmentRepository.java:38,43`, `@EntityGraph(preferredVet)` at `InterpretationRepository.java:39` — verified in the diff and the tree | PASS — Notes line 51 names base `7644417`, file:line per change, red evidence (`LazyInitializationException [Pet#1]` 2/2 in the non-transactional E2E) and gate `149/0/0/0`, tree clean | none due; none raised |
| task-1.6 revise C-3 | `8b55b4a` | PASS — `OwnerRequestController.java`, 11 `messages*.properties`, `templates/my/requestDetail.html`, `LocalizationKeyTests.java` (supporting: the new key must be in the 11-bundle identity set) + `spec/status.md` | PASS — all files exist; controller catches `IllegalRequestTransitionException \| IllegalStateException` on consent/decline/confirm/accept (`OwnerRequestController.java:94,106,118,130`) and `refused()` (`:140-143`) redirects with flash `actionNotAllowed`; `requestDetail.html:6` renders `#{requestActionNotAllowed}`; key present in all 11 bundles (12 hits incl. template). Notes line cites `:83-140` — off by three lines from the tree (`:86-143`); content matches | PASS — Notes line 52: base `8d35373`, file:line, red evidence (500 `IllegalRequestTransitionException … ACCEPTED via 'decline consent'` before the commit), gate `149/0/0/0`, tree clean | none due; none raised |
| task-1.7 revise (E2E) | `6e32f14` | PASS — `SchedulingLifecycleE2eTests.java` + `spec/status.md` only | PASS — class is the declared E2E artifact; `@SpringBootTest @Import(TestClockConfig) @DirtiesContext(AFTER_CLASS)` at `:66-68`, no `@Transactional` anywhere in the file | PASS — Notes line 53: base `8b55b4a`, method:line for both tests, red-then-green ladder across 7644417 / 8d35373 / 8b55b4a, gate `150/0/0/0`, tree clean | none due; none raised |
| task-1.6 revise D-2, P-5 | `df964f8` | PASS — `OllamaChatConfiguration.java`, `OllamaRequestInterpreter.java`, `StubRequestInterpreter.java`, `src/main/resources/application.properties`, new `SchedulingProviderPinningTests.java`, new `src/test/resources/application.properties` + `spec/status.md`. The two new files are undeclared in the legacy plan (no exact artifact list exists for task-1.6); they are supporting the user decision recorded at `status.md:38` | PASS — `application.properties:6` `scheduling.ai.provider=${SCHEDULING_AI_PROVIDER:ollama}`; `OllamaRequestInterpreter.java:24` and `OllamaChatConfiguration.java:25` `matchIfMissing = true`; `StubRequestInterpreter.java:35` `havingValue = "stub"` only; `diff` of main vs test properties shows the provider line (and its comment) as the only delta | PASS — Notes line 54: base `6e32f14`, file:line, boot check with unreachable Ollama (`Started PetClinicApplication`, `GET /login` 200), gate `152/0/0/0`, tree clean | none due (user decision recorded at `status.md:38` before the change); none raised |

Item 6 (Blocker/workaround scan over the four commits and `status.md`): no "worked around", "temporarily", "for now" or equivalent in the four Notes lines or the diffs. The previous P-5 workaround (stub default) is now reverted rather than recorded, and the user decision that authorised the earlier state is at `status.md:38`. Item 4 (rename): P-4 is recorded at `status.md:39` (`SecurityConfig.java` declared → `SecurityConfiguration.java` built; re-plan uses the as-built name) → resolved.

Protocol result: **PASS** (P-1..P-3 waived per `status.md:33`; P-4, P-5 resolved).

## Runtime Reproduction
App started by converge with `SCHEDULING_AI_PROVIDER=stub ./mvnw -q spring-boot:run` (port 8080, H2 in-memory, fresh seed), driven with `curl` + cookie jar + CSRF token from the `/login` and `/my/requests/new` forms, then killed (verified: `GET /login` → connection refused). The app log contains no `LazyInitializationException`, no `IllegalRequestTransitionException` and no stack trace (the only `ERROR` string is inside the stock Spring Security `InitializeUserDetailsBeanManagerConfigurer` WARN text).

| Actor | URL | Executor reported (status.md:51–54) | Converge observed |
|---|---|---|---|
| george (george/george123) | `POST /login` | 302 `/my/appointments` | 302 `/my/appointments` |
| george | `GET /my/appointments` (no appointment yet) | 200 | 200 |
| george | `GET /my/pets` | — | 200 |
| george | `GET /my/requests/new` | 200 | 200 (pet selector, `petId=1` Leo) |
| george | `POST /my/requests` (petId=1) | 302 `/my/requests/{id}` | 302 `/my/requests/1` |
| george | `GET /my/requests/1` after create (AWAITING_CONSENT) | 200 (was 500 at cp-1, C-1) | **200**, "Grant consent" present |
| george | `POST /my/requests/1/consent` | 302 | 302 `/my/requests/1` |
| george | `GET /my/requests/1` after consent (INTERPRETED) | 200 | **200**, "Confirm interpretation" present |
| george | `POST /my/requests/1/confirm` | 302 | 302 `/my/requests/1` (Timefold solve logged: 1 entity, 243 values, 1 s) |
| george | `GET /my/requests/1` after confirm (SUGGESTION_OFFERED) | 200 | **200**, "Accept suggestion" present |
| george | `POST /my/requests/1/accept` | 302 `/my/appointments` | 302 `/my/appointments` |
| george | `GET /my/appointments` with one appointment | 200 (was 500 at cp-1, C-2) | **200**, `CONFIRMED` and vet `Rafael Ortega` rendered |
| george | `POST /my/requests/1/decline` in state ACCEPTED | 302 + notice (was 500 at cp-1, C-3) | **302 `/my/requests/1`**; followed with `-L`: 200 and "not available for this request" rendered once |
| george | `POST /my/requests/1/{consent,confirm,accept}` in state ACCEPTED | 302 | 302 / 302 / 302 `/my/requests/1` (refused, no error page) |
| george | `GET /my/requests/9999` | 404 | 404 |
| george | `POST /my/requests` second pet (petId=1 again → active) | — | 302 `/my/requests/2` (Leo's request 1 is ACCEPTED, `active_pet_id` cleared, so a new request is legal) |
| betty (betty/betty123) | `GET /my/requests/1`, `POST /my/requests/1/consent` (george's request) | 404 / 404 | 404 / 404 (`requireRequest` still runs before the try) |
| betty | `GET /my/appointments` | — | 200 (empty) |
| admin (admin/admin123) | `POST /login`, `GET /staff/queue`, `GET /my/appointments` | 302 `/staff/queue`, 200, 403 | 302 `/staff/queue`, 200, 403 |

## Evidence Ledger (re-graded rows only; all other rows stand as graded in `convergence/cp-1.md`)
| AC | Pattern | Claimed in | Evidence (file:line) | Strength | Verified |
|---|---|---|---|---|---|
| AC-123 | unwanted-behavior (refuse, no side effect) | task-1.5, task-1.6, task-1.7 | `RequestLifecycleMatrixTests.java:42-60` (exhaustive state×action, `verifyNoInteractions`) **and now HTTP-level** `SchedulingLifecycleE2eTests.java:121-148` (`UC-1 1–8` then decline in ACCEPTED → 302 to the request, flash `actionNotAllowed`, page 200 with the keyed notice, state still ACCEPTED, event/change/appointment counts unchanged); runtime: decline/consent/confirm/accept in ACCEPTED → 302 | **STRONG** (was STRONG at service level only) | yes — read + suite + runtime |
| AC-137 | ubiquitous (stub in tests, synchronous, never live model) | task-1.6, task-1.7 | `SchedulingProviderPinningTests.java:57-64` (`scheduling.ai.provider`=`stub`, `RequestInterpreter` bean is `StubRequestInterpreter`, exactly one interpreter bean, no `OllamaRequestInterpreter`/`OllamaChatConfiguration`/`ChatClient` bean), `:68-79` (main default `${SCHEDULING_AI_PROVIDER:ollama}`, test copy `stub`, every other key identical); `src/test/resources/application.properties:8`; synchronous: `SchedulingLifecycleE2eTests.java:195-202` (state INTERPRETED and interpretation row present right after the consent redirect) | **STRONG** (was WEAK; closes G-3 / F-20) | yes — read + suite |
| AC-138 | ubiquitous (interleaved owner/staff MockMvc lifecycle) | task-1.7 (and phase-5) | `SchedulingLifecycleE2eTests.java:102-119, 153-237`: real filter chain (`springSecurity()`), `formLogin`, CSRF, `UC-1 1–8` with every page asserted 200 + content, **no test transaction** (`:66-68`); ask-again / staff hand-off / staff suggestion / completed visit / no-show still absent | **WEAK — waived** (`status.md:37`, user 2026-09-04; re-plan removes AC-138 from phase-1 `covers`). Was MISPLACED (transactional test masked the runtime 500) | yes — read + suite |
| AC-139 | ubiquitous (exactly one random-port smoke test) | task-1.7 | `SmokeTests.java` (login + `/my/appointments`), plus stock `PetClinicIntegrationTests`, `PetClinicConcurrencyTests` on `RANDOM_PORT` | **WEAK — waived** (`status.md:36`: AC-139 read as exactly one *feature* smoke test) | yes — unchanged since cp-1 |
| AC-7 / AC-16 / AC-140 | ubiquitous (layout reuse, keyed strings, banner) | task-1.4 | evidence moved: every owner page of UC-1 now renders through the stock layout in `SchedulingLifecycleE2eTests.java:169-234`; `requestDetail.html:6` notice is keyed (`#{requestActionNotAllowed}`), key in all 11 bundles, `LocalizationKeyTests` identity set updated (`8b55b4a`); banner text asserted on `/my/requests/new` only (`:172`) | **WEAK** (unchanged grade; G-7 carried — banner on *every* owner page, session invalidation, read-only My pets and Java literals remain unasserted) | yes |
| AC-21 | event-driven (pet selector excludes active pets) | task-1.6 | `SchedulingLifecycleE2eTests.java:156-160,169-172` picks a pet without an active request and asserts it *appears*; exclusion still unasserted | **WEAK** (unchanged; G-2 carried) | yes |

## Findings
### Critical
- **C-1 (cp-1) — RESOLVED** by `8d35373`: `SchedulingRequestRepository.java:41` `@EntityGraph(attributePaths = {"pet","owner","heldVet"}, type = LOAD)` on `findByIdAndOwnerId`; `InterpretationRepository.java:39` `@EntityGraph("preferredVet")` on `findTopByRequestIdOrderByVersionDesc`. Runtime: `GET /my/requests/1` → 200 after create, consent, confirm and accept; test: `SchedulingLifecycleE2eTests.java:189-193,204-208,218-222,232-234` with no test transaction.
- **C-2 (cp-1) — RESOLVED** by `8d35373`: `AppointmentRepository.java:38,43` `JOIN FETCH a.pet JOIN FETCH a.vet` on `findOwnedById`/`findAllOwnedBy`. Runtime: `GET /my/appointments` → 200 with vet name rendered; test: `SchedulingLifecycleE2eTests.java:114-118`.
- **C-3 (cp-1) — RESOLVED** by `8b55b4a`: `OwnerRequestController.java:94,106,118,130` catch `IllegalRequestTransitionException | IllegalStateException` (the latter is the supertype of `ActiveRequestExistsException`, `ActiveRequestExistsException.java:8`), `refused()` at `:140-143`; `requestDetail.html:6`; key in 11 bundles. Runtime: decline/consent/confirm/accept in ACCEPTED → 302 with the notice rendered; test: `SchedulingLifecycleE2eTests.java:121-148`.
- **C-4 (cp-1, AC-139) — WAIVED** by the user (`status.md:36`, 2026-09-04): the two stock `RANDOM_PORT` classes predate the feature; AC-139 read as "exactly one *feature* random-port smoke test". No code change expected.

### Gaps
- **G-1 (cp-1, AC-138) — WAIVED** by the user (`status.md:37`): phase-1 proves the thin UC-1 path; the interleaved scenario is a single claim of the later phase; re-plan removes AC-138 from phase-1 `covers`.
- **G-2..G-8 (cp-1) — CARRIED** unchanged into the `tasks` re-plan as `Checkpoint 1 Remediation` tasks (`source: converge/cp-1/<id>`, `status.md:35`). Note for the re-plan: G-8 (no HTTP-level illegal-state refusal test) is now *partially* covered by `SchedulingLifecycleE2eTests.java:121-148` (one route, one state — decline in ACCEPTED); the remediation task should extend it to the other owner action routes/states, not create it from scratch. G-3 is closed (see AC-137 above) and must not be re-planned.

### Protocol
- **P-1..P-3 (waived)** — `status.md:33`.
- **P-4 (cp-1) — RESOLVED**: rename `SecurityConfig.java` → `SecurityConfiguration.java` recorded at `status.md:39`; re-plan uses the as-built name.
- **P-5 (cp-1) — RESOLVED**: the unrecorded `stub` default is reverted by `df964f8` (RULE-17 `ollama` default restored) under the user decision at `status.md:38`; no Blocker remained to raise.

### Drift (Δ candidates)
- **D-1, D-3** — folded at cp-1 (`rules.md` *As built* notes under RULE-11, RULE-2; `spec.md` § As-built convergence). Unchanged.
- **D-2 (RULE-17 default provider) — RESOLVED, not a Δ**: shipped default is `ollama` again (`application.properties:6`, `OllamaRequestInterpreter.java:24`, `OllamaChatConfiguration.java:25` `matchIfMissing = true`; `StubRequestInterpreter.java:35` `havingValue = "stub"`); the suite pins `stub` through `src/test/resources/application.properties` guarded by `SchedulingProviderPinningTests.java:68-79`. The spec text of RULE-17 now matches the build; the "Open decision" line in `spec.md` is closed below.

### Cosmetic
- **K-1..K-3 (cp-1) — CARRIED** into the re-plan (`status.md:35`).
- **K-4 (new, task-1.6, `8b55b4a`)** — *Loaded catch:* `OwnerRequestController.java:94,106,118,130` catch the broad `IllegalStateException` (needed only for `ActiveRequestExistsException`) around `lifecycleService`, `interpretationService.interpret(...)`, `suggestionService.confirm/accept(...)`. Any programming-error `IllegalStateException` raised inside interpretation or ranking (e.g. an Ollama client misconfiguration, a solver state error) would be reported to the owner as "That action is not available for this request right now." and logged nowhere. Spec text: AC-123 requires the *lifecycle* refusal to be surfaced as a refusal, not that every `IllegalStateException` be swallowed. Fix (re-plan, with K-1..K-3): catch `IllegalRequestTransitionException | ActiveRequestExistsException` only, or give the lifecycle exceptions a common supertype. Severity: COSMETIC (no AC violated; behavior on the spec'd paths is correct).

## Category Notes
- **1. Task Closure** — PASS for the four revisions: each has one commit, a base hash, file:line closure evidence, red-then-green evidence and a suite/tree gate in `status.md` Notes 51–54; the previous C-1..C-3 are closed with reproduction above; C-4/G-1 waived at `status.md:36-37`; the test-citation gap (G-5) remains carried by user decision. The new tests cite their ACs in method names (`_AC138`, `_AC123`, `_AC137`).
- **2. AC Evidence Audit** — re-graded: AC-123 STRONG (HTTP-level now), AC-137 STRONG (was WEAK), AC-138 WEAK-waived (was MISPLACED), AC-139 WEAK-waived, AC-7/16/140 and AC-21 WEAK (unchanged; G-7, G-2 carried). Phase-1 ledger after this run: STRONG 33 / WEAK 9 (2 of them waived) / MISPLACED 0 / IMPOSSIBLE 1 (AC-118, G-4 carried) / ABSENT 0.
- **6. Security Surface Walk** — PASS: the C-3 handlers call `accessService.requireRequest(ownerId(), requestId)` *before* the `try` (`OwnerRequestController.java:88,102,114,126`), so other-owner/missing still yields 404 (runtime: betty → 404/404; `SecurityMatrixWebTests` green); the fetch-joins keep the owner join in the JPQL (`AppointmentRepository.java:38,43` `FROM Owner o JOIN o.pets p … WHERE o.id = :ownerId`) and `findByIdAndOwnerId` keeps the owner predicate; no new route, no new permitted matcher; CSRF still enforced (all POSTs carried `_csrf`); `/my/**` still 403 for staff (admin → 403).
- **10. Constraint Conformance** — PASS: RULE-17 default `ollama` restored (D-2); RULE-4 `open-in-view=false` kept (`application.properties`, both copies identical apart from the provider line); RULE-2/26 boundary intact (`ArchitectureBoundaryTests` green in the 152); RULE-15 refusal surfaced without a 500; no new dependency, no profile introduced (the test copy of `application.properties` is a full shadow file, drift-guarded by `SchedulingProviderPinningTests.java:68-79`). Reservation: K-4 (broad catch) is a code-quality deviation, not a rule violation.

## Spec Reconciliation
- Δ folded in: none new (D-1, D-3 already folded at cp-1). D-2 closed by code, RULE-17 text stands.
- F-n: **F-14, F-15, F-16 closed** (`8d35373`, `8b55b4a`, `6e32f14`); **F-17, F-18 waived** (`status.md:36-37`); **F-20 closed** (`df964f8`, AC-137 STRONG); F-19, F-21..F-25 remain open and are carried into the re-plan. `spec.md` § As-built convergence (cp-1) updated accordingly; `status.md` phase-1 line set to APPROVED WITH NOTES.
- Spec weaknesses exposed: none new. The "RULE-17 default vs runnable checkout" weakness recorded at cp-1 is now answered by the shadow test properties (a fresh checkout runs `./mvnw test` offline; `spring-boot:run` needs Ollama or `SCHEDULING_AI_PROVIDER=stub`, documented in `application.properties:5`).
- Plan weaknesses exposed: for the re-plan (`tasks`): make the remediation task for G-8 *extend* `SchedulingLifecycleE2eTests`, drop G-3 from the remediation list, and add K-4 to the K-1..K-3 cosmetic task; declare `src/test/resources/application.properties` and `SchedulingProviderPinningTests` as exact artifacts owned by the task that owns RULE-17.

## Resolution
None required for cp-1. Carried into the `tasks` re-plan as `Checkpoint 1 Remediation` (user decision `status.md:35`): G-2, G-4, G-5, G-6, G-7, G-8 (narrowed), K-1, K-2, K-3, K-4.

## Approval response
APPROVED WITH NOTES: Δ D-1 (RULE-11 `timefold.solver.termination.spent-limit`), Δ D-3 (RULE-2/26 exact adapter boundary); waived C-4 (AC-139), G-1 (AC-138); carried to re-plan G-2, G-4, G-5, G-6, G-7, G-8, K-1, K-2, K-3, K-4
