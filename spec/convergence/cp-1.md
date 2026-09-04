# Convergence: cp-1 — Walking skeleton (phase-1)

## Summary
- Checkpoint: cp-1 (phase-1, 7/7 tasks claimed complete); executor report: spec/checkpoints/cp-1.md @ c363bb1 (retroactive; phase built as 056ab3b, 7cb2696, 7a5be39)
- Previous verdict (this file, 2026-09-04, before 7a5be39): **REJECT** — C-1..C-7, G-1..G-6. Re-converged 2026-09-04 after 7a5be39: C-1, C-3, C-4, C-5, C-6, G-1, G-3, G-4, G-5, G-6 verified resolved; C-2 resolved except the `/oups` matcher-without-handler (now K-2); C-7 resolved for the *thin* UC-1 only (remaining AC-138 gap carried as G-1); G-2 resolved (SecurityMatrixWebTests now asserts disclosure and mutation). No previous finding is carried forward at its original severity except C-7 → G-1 (re-graded: HTTP path now exists, scenario is partial).
- Verdict: **REJECT**
- Counts: 4 critical, 8 gaps, 2 protocol (+3 waived), 3 drift, 3 cosmetic; carried forward: cp-1(prev)/C-7 as G-1
- Suite (run by converge, Java 25.0.1 / source 21): 149/0/0/0 — no skipped; plan guards (`RouteInventoryTest`, `ArtifactInventoryTest`, `AcTagCoverageTest`): absent (legacy plan has no `routes`/guards; noted, not a finding)
- Working tree after test run: clean (only untracked tool logs `.background_output_*.txt`, not project files)

## Protocol Gate
Legacy waiver applies (`status.md` → Deviations, 2026-09-04): items 2, 3, 5 waived for cp-1 only. Item 1 satisfied by c363bb1 (all template sections present incl. Runtime Evidence). Items 4, 6, 7 audited in full.

| Task | Commit | Files in commit match artifact + supporting | Artifacts by exact name | Gate evidence in status.md | Blocker due / raised |
|---|---|---|---|---|---|
| task-1.1 | 056ab3b (batched 1.1–1.4) + 7a5be39 | not gradable per task (56-file batch); `git show --stat` shows pom.xml, deleted db/mysql, db/postgres, k8s/, docker-compose.yml, Gradle files, `ArchitectureBoundaryTests.java` present | `ArchitectureBoundaryTests.java` ✓; pom ✓ | Notes line present; no per-task gate run recorded (P-3 waived) | none due |
| task-1.2 | 056ab3b + 7a5be39 | as above; `V1__…V4__*.sql`, `SeedMigrationTests.java` present | ✓ (`SeedMigrationTests.java`) | Notes present (P-3 waived) | none due |
| task-1.3 | 056ab3b + 7a5be39 | as above | ✗ declared `security/SecurityConfig.java`, built `security/SecurityConfiguration.java` — rename with no Deviation (**P-4**); `User.java`, `UserRepository.java`, `SecurityMatrixWebTests.java`, ownership guard (`OwnerSchedulingAccessService.java`) ✓ | Notes present | none due |
| task-1.4 | 056ab3b + 7a5be39 | as above; `templates/my/*` ×4, `templates/staff/*` ×3, `login.html`, `fragments/layout.html`, 11 bundles, `LocalizationKeyTests.java` | ✓ (glob artifact — plan weakness) | Notes present | none due |
| task-1.5 | 7cb2696 (batched 1.5–1.7) + 7a5be39 | 44-file batch; 7a5be39 also edits stock `PetController`/`VisitController` (clock removal, in RULE-37 scope) | `RequestLifecycleService`, `AppointmentLifecycleService`, `ClockConfig`, `RequestLifecycleRefusalTests`, `AppointmentLifecycleRefusalTests` ✓; "test config for isolated H2 + pinned Clock" = `TestClockConfig.java` (prose artifact) | Notes present | none due |
| task-1.6 | 7cb2696 + 7a5be39 | 7a5be39 touches solver `DefaultSlotRanker`/`AppointmentAssignment`/`ScheduleSolution` (in scope) | `RequestInterpreter`, `OllamaRequestInterpreter`, `StubRequestInterpreter`, `Interpretation`, `SlotRanker`, `SuggestionService`, `InterpretationFidelityTests` ✓; no controller artifact declared although `OwnerRequestController`/`OwnerPageController`/`StaffPageController` were built (plan weakness) | Notes present; RULE-11 key deviation recorded ✓; **RULE-17 default `stub` NOT recorded as Deviation (P-5)** | Blocker due for RULE-17 default change — worked around instead (**P-5**) |
| task-1.7 | 7cb2696 + 7a5be39 | as above | `SchedulingLifecycleE2eTests.java`, `SmokeTests.java` ✓ | Notes present | none due |

Item 7 (per-commit scope): cannot be graded per task — three batched commits (56/44/80 files). Nothing outside phase-1 scope was seen in `git show --stat`; the stock-controller edits are justified by RULE-37/AC-108.

## Runtime Reproduction
App started with `./mvnw spring-boot:run` (default `scheduling.ai.provider=stub`), curl + cookie jar + CSRF from `/login`. Executor table taken from `spec/checkpoints/cp-1.md` § Runtime Evidence.

| Actor | URL | Executor reported | Converge observed |
|---|---|---|---|
| anonymous | `/`, `/my/pets`, `/my/appointments`, `/my/requests/new`, `/staff/queue`, `/owners/find`, `/vets.html`, `/403`, `/oups`, `/actuator/health` | 302 → /login | 302 → `/login` (all) |
| anonymous | `/login` | 200 | 200 |
| george (owner) | `POST /login` (george123) | 302 → /my/appointments | 302 → `/my/appointments` |
| george | `/` | 302 → /my/appointments | 302 → `/my/appointments` |
| george | `/my/pets`, `/my/appointments` (no appointment yet), `/my/requests/new` | 200 | 200 |
| george | `/my/requests/9999` | 404 | 404 |
| george | `/staff/queue`, `/staff/calendar`, `/staff/settings`, `/owners/find`, `/owners/1`, `/owners/2`, `/vets.html`, `/oups` | 403 | 403 |
| george | `/403` | 200 | 200 |
| george | `POST /my/requests` (petId=1) | 302 → /my/requests/1 | 302 → `/my/requests/1` |
| george | `POST /my/requests` again for pet 1 | — | 302 → `/my/requests/new` (active-request flash) |
| george | `GET /my/requests/1` | **500** LazyInitializationException Pet#1 | **500** — `LazyInitializationException: Could not initialize proxy [Pet#1] - no session`, `my/requestDetail` line 6 col 40 (`request.pet.name`) — **reproduced** |
| george | `POST /my/requests/1/consent` | 302 | 302 → `/my/requests/1` (then 500 again) |
| george | `POST /my/requests/1/confirm` | 302 | 302 → `/my/requests/1` |
| george | `POST /my/requests/1/accept` | 302 → /my/appointments | 302 → `/my/appointments` |
| george | `POST /my/requests/1/decline` (request now ACCEPTED) | not reported | **500** — unhandled `IllegalRequestTransitionException: Illegal transition from state ACCEPTED via action 'decline consent'` |
| george | `POST /my/requests/9999/consent` | — | 404 |
| george | `GET /my/appointments` (one appointment) | **500** LazyInitializationException Vet#4 | **500** — `LazyInitializationException: Could not initialize proxy [Vet#4] - no session`, `my/appointments` line 8 col 147 — **reproduced** |
| george | `GET /logout` / `POST /logout` | — | 405 / 302 → `/login?logout` |
| betty (owner 2) | `GET /my/requests/1`, `POST /my/requests/1/consent` (george's request) | 404 | 404 / 404 |
| betty | `POST /my/requests` petId=1 (george's pet) | — | 404 |
| admin (staff) | `POST /login` (admin123), `/` | 302 → /staff/queue | 302 → `/staff/queue` (both) |
| admin | `/staff/queue`, `/staff/calendar`, `/staff/settings`, `/owners/find`, `/owners/1`, `/vets.html`, `/403` | 200 | 200 |
| admin | `/my/pets`, `/my/appointments`, `/my/requests/new`, `/my/requests/1` | 403 | 403 |
| george | `POST /login` wrong password | 302 → /login?error | 302 → `/login?error` |

Side observation: the 500 on the POST path logged `HttpMessageNotWritableException: No converter for LinkedHashMap with preset Content-Type 'text/html'` — the error view itself failed to render for that request (K-3).

## Evidence Ledger
(44 rows = union of task `covers.acs`; AC-118 appears in task-1.3 but not in the phase `covers` list — plan weakness)

| AC | Pattern | Claimed in | Evidence (file:line) | Strength | Verified |
|---|---|---|---|---|---|
| AC-1 | If-then | task-1.3 | `SecurityMatrixWebTests.java:115-124` 21 GET + 11 POST routes → 3xx, `Location endsWith("/login")` | STRONG | runtime ✓ |
| AC-2 | If-then | task-1.3 | `SecurityMatrixWebTests.java:116,123` `databaseSnapshot()` equality over 9 mutable tables; `:191-194` `noProtectedData()` on body | STRONG | ✓ |
| AC-3 | When | task-1.3 | `FormLoginTests.java:58-67` redirect + session `SPRING_SECURITY_CONTEXT` has `ROLE_OWNER`; `:70-77` staff/admin; encoder use proven by `SeedMigrationTests.java:64-…` `PasswordEncoder.matches` | STRONG | ✓ |
| AC-4 | If-then | task-1.3 | `FormLoginTests.java:81-93` `/login?error` + `sessionAttributeDoesNotExist(SPRING_SECURITY_CONTEXT_KEY)` for wrong pw and unknown user | STRONG | ✓ |
| AC-5 | When | task-1.4 | `FormLoginTests.java:58-61` login → `/my/appointments`; `:97-100` `/` → `/my/appointments` | STRONG | ✓ |
| AC-6 | When | task-1.4 | `FormLoginTests.java:70-77`, `:105-108` → `/staff/queue` | STRONG | ✓ |
| AC-7 | Ubiquitous | task-1.4 | `NavBarRenderingTests.java:72-74,91-93` username + `action="/logout"` on `/login` and `/vets.html` only; `FormLoginTests.java:113-115` POST /logout → `/login?logout`; no assertion that the session is invalidated, no assertion on owner feature pages | WEAK | G-7 |
| AC-8 | If-then | task-1.3 | `SecurityMatrixWebTests.java:73-78,127-137` owner on `/owners/**`, `/vets`, `/vets.html`, `/staff/**` → 403 | STRONG | runtime ✓ |
| AC-9 | If-then | task-1.3 | `SecurityMatrixWebTests.java:129-136` snapshot equality + `noProtectedData()` incl. stock POST routes | STRONG | ✓ |
| AC-10 | Ubiquitous | task-1.3 | route inventory: no owner id in any `/my/**` URL (`OwnerPageController.java:27-39`, `OwnerRequestController.java:46-107`); `OwnerSchedulingAccessServiceTests.java:77-89` queries always carry the authenticated owner id, `never() findById` | STRONG | ✓ |
| AC-11 | If-then | task-1.3 | `SecurityMatrixWebTests.java:161-169` other-owner vs missing: both 404 and **identical bodies** | STRONG | runtime ✓ |
| AC-12 | If-then | task-1.3 | `SecurityMatrixWebTests.java:171-177` consent on Betty's request → 404, no protected text, state unchanged, event count unchanged; stock pages via `:127-137` | STRONG | runtime ✓ (betty → 404 ×3) |
| AC-13 | Ubiquitous | task-1.3 | `OwnerSchedulingAccessServiceTests.java:64-73,81-89` service invoked directly (no MVC), same `OwnerResourceNotFoundException("Resource not found")`, `never().save` | STRONG | ✓ |
| AC-14 | While | task-1.4 | `NavBarRenderingTests.java:67-78` owner: `/my/appointments`, `/my/pets` present; staff/stock links absent | STRONG | ✓ |
| AC-15 | While | task-1.4 | `NavBarRenderingTests.java:83-95` staff: find owners, vets, queue, calendar, settings; owner links absent | STRONG | ✓ |
| AC-16 | Ubiquitous | task-1.4 | templates `my/*.html:2`, `staff/*.html` use `~{fragments/layout :: layout(...)}` (read); no test asserts layout reuse / no second stylesheet | WEAK | G-7 |
| AC-17 | While | task-1.4 | `my/pets.html` (read: no edit controls); runtime 200; no test asserts absence of edit controls | WEAK | G-7 |
| AC-18 | While | task-1.4 | `NavBarRenderingTests.java:75-78` menu absence only; no test asserts vet names/specialties shown on the request page | WEAK | G-7 |
| AC-19 | Ubiquitous | task-1.4 | `SchedulingLifecycleE2eTests.java:99-101` banner "Call 555-0199." on `/my/requests/new` only; `my/requestDetail.html:4`, `my/requestForm.html:4` have banner; `/my/appointments` banner not asserted | WEAK | G-7 |
| AC-20 | When | task-1.6 | `InterpretationFidelityTests.java:158-163` service creates AWAITING_CONSENT; `SchedulingLifecycleE2eTests.java:103-…` HTTP POST `/my/requests` → 302 and persisted state | STRONG | runtime ✓ |
| AC-21 | While | task-1.6 | `InterpretationFidelityTests.java:165-167` proves *second request refused* (single-active-request rule), not the selector; `SchedulingLifecycleE2eTests.java:100` shows pet name present; no test that a pet **with** an active request is **absent** from the selector | WEAK | G-2 |
| AC-53 | When | task-1.6 | `InterpretationFidelityTests.java:119-155` field-for-field incl. three window lists `containsExactlyElementsOf` after `flush()/clear()`, model tag, prompt version, raw response, preferred vet | STRONG | ✓ |
| AC-58 | When | task-1.6 | `InterpretationFidelityTests.java:179-184` SUGGESTION_OFFERED + `heldStart` not null; nothing asserts Timefold ranked or that the **top** slot was offered (`DefaultSlotRanker` uses `SolverManager` — read) | WEAK | G-6 |
| AC-63 | When | task-1.6 | `SuggestionServiceOrderingTests.java:71-75` `InOrder` `vets.findByIdForUpdate` **before** overlap re-check, `never() bookAppointment`, `acceptSlotLostNoneLeft`; `InterpretationFidelityTests.java:186-188` success path → ACCEPTED | STRONG | ✓ (mock-based ordering; lock annotation on `VetRepository.findByIdForUpdate` read) |
| AC-108 | Ubiquitous | task-1.5 | `ClinicClockTests.java:34-36` runtime `Clock` zone = seeded `Europe/Amsterdam` (`ClockConfig.java:37-41` reads `clinic_config`); `:39-49` no `*.now()` in `src/main/java` | STRONG | ✓ |
| AC-118 | If-then | task-1.3 | `OwnerSchedulingAccessServiceTests.java:83-89` service-level 404 for another owner's appointment; **no owner appointment view/cancel route exists in phase-1** (`/my/appointments/{id}` not mapped) → HTTP outcome cannot be asserted; `AppointmentConstraintProviderTests.java:37` cites AC-118 wrongly | IMPOSSIBLE (in phase-1) | G-4 |
| AC-123 | If-then | task-1.5 | `RequestLifecycleMatrixTests.java:42-60` exhaustive state×action, `verifyNoInteractions(requests, events)`, snapshot equality | STRONG | ✓ (runtime: refusal reaches the browser as 500 → C-3) |
| AC-124 | If-then | task-1.5 | `AppointmentLifecycleRefusalTests.java:89-154` status unchanged, change-row count, `pet.getVisits()` size | STRONG | ✓ |
| AC-125 | Ubiquitous | task-1.2 | `SeedMigrationTests.java:64-…` every account by username/role/name/owner link, `containsExactly` | STRONG | ✓ |
| AC-126 | Ubiquitous | task-1.2 | `SeedMigrationTests.java` closed set (`containsExactly`) + `UserRole` enum / DB CHECK `V3:5` | STRONG | ✓ |
| AC-127 | Ubiquitous | task-1.2 | `SeedMigrationTests.java` `PasswordEncoder.matches` per account | STRONG | runtime ✓ (george/betty/admin log in) |
| AC-128 | Ubiquitous | task-1.2 | `SeedMigrationTests.java` 7 opening-hour rows by value (Sat/Sun closed) | STRONG | ✓ |
| AC-129 | Ubiquitous | task-1.2 | `SeedMigrationTests.java` 17 blocks by value | STRONG | ✓ |
| AC-130 | Ubiquitous | task-1.2 | same, `containsExactly` (closed set) | STRONG | ✓ |
| AC-131 | Ubiquitous | task-1.2 | `SeedMigrationTests.java` 6 exception rows by value | STRONG | ✓ |
| AC-132 | Ubiquitous | task-1.2 | `SeedMigrationTests.java` 0 leave / 0 closure rows | STRONG | ✓ |
| AC-133 | Ubiquitous | task-1.2 | `SeedMigrationTests.java:164-166` `containsExactly(new ClinicConfig(30,15,60,30,15,"Europe/Amsterdam","555-0199"))` | STRONG | ✓ |
| AC-134 | Ubiquitous | task-1.2 | same `containsExactly` (single row) | STRONG | ✓ |
| AC-135 | Ubiquitous | task-1.2 | `SeedMigrationTests.java:55-60` unique in-memory URL; `git status` clean before/after full suite | STRONG | ✓ |
| AC-136 | Ubiquitous | task-1.5 | `TestClockConfig.java:35-44` `@Primary` `Clock.fixed(2026-09-07T07:00Z, Europe/Amsterdam)`; horizon end 2026-10-07 not asserted (horizon ACs are phase-2) | STRONG | ✓ |
| AC-137 | Ubiquitous | task-1.5 | no test config pins `scheduling.ai.provider=stub` — tests inherit the **production** default `application.properties:6` (`stub`, itself the RULE-17 deviation); `InterpretationPersistenceTests.java:46` cites AC-137 (stub bean present); no "never calls the live model" / no synchronous-executor assertion | WEAK | G-3 |
| AC-138 | Ubiquitous | task-1.7 (and phase-2/5) | `SchedulingLifecycleE2eTests.java:89-168` MockMvc + real filter chain + CSRF + formLogin, UC-1 1–8 as owner; **no staff session, no ask-again, no hand-off, no declined-consent/no-show**; class-level `@Transactional` (`:56`) hides the lazy-load 500s | MISPLACED (partial; UC-1 1–8 only) | G-1 |
| AC-139 | Ubiquitous | task-1.7 | `SmokeTests.java:38-74` random port, `/login` 200 + authenticated `/my/appointments` 200; **but** `PetClinicIntegrationTests.java:34` and `PetClinicConcurrencyTests.java:32` are also `RANDOM_PORT` tests → not "exactly one" | WEAK | C-4 |
| AC-140 | Ubiquitous | task-1.4 | `LocalizationKeyTests.java:49-61` every `#{key}` exists; `:64-80` identical in 11 bundles; `:83-107` no unkeyed text/attribute/inline literal in feature templates; Java-produced messages not scanned (`OwnerResourceNotFoundException("Resource not found")` reaches the 404 page) | WEAK | G-7 |

Tally: STRONG 32, WEAK 10, MISPLACED 1, IMPOSSIBLE 1, ABSENT 0.

## Findings
### Critical
- **C-1 — `GET /my/requests/{id}` returns 500 (LazyInitializationException) on the UC-1 main path**
  Spec: UC-1 step 2 "Owner reviews the request and grants explicit consent", step 4 "System presents the persisted interpretation for read-only review"; cp-1 criterion 1 "pages render inside the stock fragments/layout.html".
  Evidence (code): `SchedulingRequest.java:41-47` `@ManyToOne(fetch = FetchType.LAZY)` pet/owner; `OwnerSchedulingAccessService.java:56-72` `@Transactional(readOnly = true)` returns detached entity; `application.properties:16` `spring.jpa.open-in-view=false`; `templates/my/requestDetail.html:6` `${request.pet.name}`. Runtime: 500 reproduced twice (before and after consent).
  Evidence (test that let it pass): `SchedulingLifecycleE2eTests.java:56` class-level `@Transactional` keeps the persistence context open across the MockMvc calls, so the template renders in the test but not in the running app.
  Wrong: every owner request page is an error page; the walkthrough cannot pass steps 2–7. Owning: task-1.6 (controller/access service), task-1.4 (template), task-1.7 (masking test).
  Resolution: `REVISE: task-1.6` (see Resolution #1) + `REVISE: task-1.7` (#3).
- **C-2 — `GET /my/appointments` returns 500 once an appointment exists**
  Spec: UC-1 step 8 "System confirms the appointment and shows it under My appointments"; AC-5 owner landing page.
  Evidence (code): `AppointmentRepository.java:38-40` `findAllOwnedBy` no fetch join; `templates/my/appointments.html:8` `appointment.vet.firstName + ' ' + appointment.vet.lastName`; `OwnerSchedulingAccessService.java:68-72`. Runtime: 500 `Vet#4 - no session` after accept; the owner's landing page (and the login success redirect) is now an error page.
  Evidence (tests that let it pass): `SmokeTests.java:69-73` loads `/my/appointments` with **zero** appointments; `SchedulingLifecycleE2eTests.java:56` `@Transactional`.
  Owning: task-1.6 / task-1.4 / task-1.7. Resolution: Resolution #1, #3.
- **C-3 — Refused lifecycle transition surfaces as an unhandled 500 on an owner action route**
  Spec: AC-123 "the system shall refuse it itself and shall not change the request's state or produce any side effect, regardless of whether the UI …"; spec.md E-8/E-9 "never an error page".
  Evidence (code): `OwnerRequestController.java:79-107` consent/decline/confirm/accept call `lifecycleService`/`suggestionService` with no `catch` and no `@ControllerAdvice` (only `create` catches `ActiveRequestExistsException`, `:64-67`). Runtime: `POST /my/requests/1/decline` in state ACCEPTED → 500 `IllegalRequestTransitionException`.
  Evidence (test): `RequestLifecycleMatrixTests.java:42-60` proves the service refuses; no HTTP-level refusal test exists (`SecurityMatrixWebTests` only exercises 404/403 paths).
  Owning: task-1.6 (no controller artifact in the plan — plan weakness, fix is still a code fix). Resolution #2, #3.
- **C-4 — AC-139 "exactly one random-port smoke test" is violated**
  Spec: AC-139 "The test suite shall include exactly one random-port smoke test that loads the login page and one authenticated page to prove wiring."
  Evidence: `SmokeTests.java:38` plus stock `PetClinicIntegrationTests.java:34` and `PetClinicConcurrencyTests.java:32` all use `WebEnvironment.RANDOM_PORT` (three server boots per suite). Executor report claims 1.
  Owning: task-1.7. Resolution #7; **waiver candidate** if the user reads "smoke test" as excluding the stock integration tests (recommend: fold or delete the stock random-port tests — they were kept only by omission).

### Gaps
- **G-1 — AC-138 delivered as thin UC-1 only (carried from cp-1(prev)/C-7, re-graded)**
  Spec: AC-138 "…interleaving an owner session and a staff session with CSRF, drives request → consent → interpretation → suggestion → ask again → staff hand-off → staff suggestion → accept → completed visit, plus a declined-consent request booked by staff and closed as no-show."
  Evidence: `SchedulingLifecycleE2eTests.java:89-168` owner session only, UC-1 1–8; task-1.7 `validation` itself says "thin". The plan claims AC-138 in phase-1 **and** phase-2/5 (double claim). MISPLACED.
  Owning: task-1.7 / plan. Resolution: plan fix — `tasks` re-plan must claim AC-138 only in the phase that completes it; for cp-1 the thin scenario is acceptable **only** as a recorded waiver (`status.md` Deviations, user decision).
- **G-2 — AC-21 pet-selector exclusion not asserted on the rendered form**
  Spec: AC-21 "…shall offer in the pet selector only that owner's pets that have no active request."
  Evidence: `InterpretationFidelityTests.java:165-167` asserts a second `createRequest` throws (that is AC-20's rule); `OwnerRequestController.java:49` calls `findPetsWithoutActiveRequest` but no test renders `/my/requests/new` after a request exists and asserts the pet's `<option>` is absent. WEAK. Owning: task-1.6. Resolution #4.
- **G-3 — AC-137 deterministic AI not pinned by the tests**
  Spec: AC-137 "The automated tests shall use the `stub` deterministic `RequestInterpreter` with a synchronous executor and shall never call the live model."
  Evidence: no `src/test/resources/application*.properties`, no `@TestPropertySource`/`properties=` setting `scheduling.ai.provider=stub`; tests inherit `application.properties:6` `${SCHEDULING_AI_PROVIDER:stub}` — an environment variable flips the whole suite to Ollama. No assertion that `OllamaRequestInterpreter` is absent/never invoked; no synchronous-executor assertion (phase-1 orchestration is synchronous by construction, `OwnerRequestController.java:83-84`). WEAK. Owning: task-1.5. Resolution #5.
- **G-4 — AC-118 cannot be verified at HTTP level in phase-1**
  Spec: AC-118 "If an owner attempts to cancel or view another owner's appointment, then the system shall respond with 404, shall make no database change, and shall disclose nothing about that appointment."
  Evidence: `OwnerSchedulingAccessServiceTests.java:83-89` (`requireAppointment` → not found, mock repos) — no route to view/cancel a single appointment exists (`OwnerPageController.java:34-39` lists only). IMPOSSIBLE in this phase; `AppointmentConstraintProviderTests.java:37` cites AC-118 for an unrelated solver test (mis-citation). Owning: task-1.3 / plan. Resolution: plan fix — move AC-118 to the phase that adds the owner appointment view/cancel route (keep the service-level test as supporting evidence).
- **G-5 — 29 of 43 phase-1 ACs have no `AC-n` citation in any test; two citations are mis-scoped (cross-cutting, Task Closure gate item 3)**
  Evidence: grep over `src/test` finds citations only for AC-20, 21, 53, 58, 63, 109, 110, 111, 118, 123, 124, 125, 127, 136, 137, 138, 139; `FormLoginTests.java:42` cites AC-125/AC-127 (seed ACs) for login tests; `AppointmentConstraintProviderTests.java:37` cites AC-118. The uncited assertions exist (ledger above) but the traceability the protocol requires does not. Owning: all phase-1 tasks (single cross-cutting GAP, not one per task). Resolution #9.
- **G-6 — AC-58 "rank them with Timefold, offer the top slot" not proven**
  Spec: AC-58 "…enumerate feasible slots, rank them with Timefold, offer the top slot, move the request to *Suggestion offered*, and record the hold…".
  Evidence: `InterpretationFidelityTests.java:179-184` asserts state and non-null hold only; `DefaultSlotRanker` (read) drives `SolverManager`, but no test asserts the solver ran, that the offered slot equals the ranker's first result, or the held vet/duration values. WEAK. Owning: task-1.6. Resolution #4.
- **G-7 — Presentation ACs proven on two pages only (AC-7, AC-16, AC-17, AC-18, AC-19, AC-140 Java literals)**
  Evidence: `NavBarRenderingTests.java:54-96` renders `/login` and `/vets.html` only; banner asserted once (`SchedulingLifecycleE2eTests.java:101`, `/my/requests/new`); nothing asserts: session invalidation after `POST /logout`, layout fragment reuse / no second stylesheet, absence of edit controls on `/my/pets`, vet names + specialties on the request page, banner on `/my/requests/{id}` and `/my/appointments`, Java-side literal `OwnerResourceNotFoundException("Resource not found")` (`OwnerSchedulingAccessService`, shown on the 404 page). WEAK. Owning: task-1.4. Resolution #6.
- **G-8 — No HTTP-level refusal/negative test for the owner action routes**
  Spec: AC-123 (as above), RULE-15 service-owned guard "regardless of whether the UI …".
  Evidence: `SecurityMatrixWebTests.java:83-84` posts to `/my/requests/999999/*` (404 path) only; no test posts a *legal-URL, illegal-state* action and asserts a non-error response. This is the test-side twin of C-3. Owning: task-1.7. Resolution #3.

### Protocol
- **P-1 (waived)** — item 2: no one-commit-per-task history (056ab3b, 7cb2696, 7a5be39). Waived by `status.md` Deviations (2026-09-04).
- **P-2 (waived)** — item 3: no `spec/plan-review.md` existed before execution. Waived.
- **P-3 (waived)** — item 5: no per-task closure-gate evidence in `status.md` (Notes only). Waived.
- **P-4 — Renamed artifact without a Deviation**: task-1.3 declares `security/SecurityConfig.java`; built `security/SecurityConfiguration.java` (`SecurityConfiguration.java:1-…`). No entry in `status.md` → Deviations. Owning: task-1.3. Resolution: `REVISE: task-1.3 - record the rename in status.md Deviations` (or re-plan names the built file).
- **P-5 — Workaround where a Blocker was due**: RULE-17 "default `ollama`" vs shipped `application.properties:6` `${SCHEDULING_AI_PROVIDER:stub}` and `StubRequestInterpreter.java:35` `matchIfMissing = true`. The executor flagged it in `spec/checkpoints/cp-1.md` but did not record it under Deviations nor raise a Blocker; the spec's default was changed unilaterally. Owning: task-1.6. Resolution: user decision (see D-2), then record in Deviations.

### Drift (Δ candidates)
- **D-1 — Timefold termination key** (accepted Δ): RULE-11 `timefold.solver.solve.duration=1s` is not a valid Timefold 2.5 key; built `timefold.solver.termination.spent-limit=1s` (recorded in `status.md` Deviations). Same observable behavior (1 s spent limit). Folded into `rules.md` RULE-11 and `spec.md` § As-built.
- **D-2 — Default interpreter provider `stub` instead of `ollama`** (NOT accepted; user decision): RULE-17 says default `ollama`. Options: (a) restore `ollama` default and require `SCHEDULING_AI_PROVIDER=stub` in the test profile (spec-conformant; demo needs a running Ollama); (b) accept `stub` default as Δ and pin `stub` explicitly in tests (G-3) — recommend (a) for RULE-17 fidelity, with tests pinning `stub` either way. Record the decision under Deviations.
- **D-3 — RULE-2/RULE-26 exact adapter boundary** (accepted Δ): the contradiction noted at the previous cp-1 is resolved as-built by `ArchitectureBoundaryTests.java:51-57` `FRAMEWORK_ADAPTER_BOUNDARY` — an exact class set (`DefaultSlotRanker`, `AppointmentAssignment`, `ScheduleSolution`, `AppointmentConstraintProvider`, `OllamaRequestInterpreter`, …) instead of substring exemptions; `ViolatingSolutionSupport` fixture (`:113-120`) proves the rule bites. Folded into `rules.md` RULE-2 and `spec.md` § As-built.

### Cosmetic
- **K-1** — `ArchitectureBoundaryTests.java:51-57` boundary set names `TimefoldSlotRanker`, a class that does not exist (dead entry).
- **K-2** — `SecurityConfiguration.java:70` has a `/oups` matcher but no handler exists (stock `CrashController` removed); staff would get 404, owner 403. Either drop the matcher or restore the handler; the rules Security Surface still lists `/oups` as pre-existing.
- **K-3** — Error view for a failing POST logged `HttpMessageNotWritableException: No converter for [LinkedHashMap] with preset Content-Type 'text/html'`: the `/error` path does not render for `Accept: */*` on POST. Becomes moot once C-3 removes the 500, but worth a `@ControllerAdvice` check.

## Category Notes
1. Task Closure — 7/7 tasks claimed; artifacts present (one renamed, P-4; glob/prose artifacts — plan weakness); AC citations missing for 29/43 (G-5); `validation` bullets mostly satisfied literally except task-1.7's "one Confirmed appointment visible under My appointments" (visible only inside the test transaction → C-2).
2. AC Evidence Audit — 44 rows; 32 STRONG; non-STRONG rows → C-4, G-1, G-2, G-3, G-4, G-6, G-7.
3. Normative Data by Value — pass: `SeedMigrationTests.java:64-167` `containsExactly` for accounts, hours, blocks, exceptions, config; previous G-1 resolved.
4. Lifecycle and State Guards — service guards pass (`RequestLifecycleMatrixTests`, `AppointmentLifecycleRefusalTests`); web layer turns refusals into 500 (C-3, G-8).
5. Fidelity and Round-trips — pass: `InterpretationFidelityTests.java:119-155` (previous G-4 resolved).
6. Security Surface Walk — pass at HTTP level for anonymous/wrong-role/other-owner incl. stock routes with disclosure + snapshot assertions (`SecurityMatrixWebTests`); runtime confirms; `/oups` K-2; AC-118 route absent (G-4). `/403` is `authenticated()` (previous C-2 item resolved).
7. Loaded Words — "thin" (task-1.7) vs AC-138 "interleaving … no-show" (G-1); "exactly one random-port smoke test" (C-4); "every page" satisfied on two pages (G-7).
8. Presentation, Navigation and Localization — menus/landing/logout pass (AC-5/6/14/15); banner/layout/read-only/Java literals under-proven (G-7); pages themselves 500 (C-1, C-2).
9. Test Hygiene — clean tree ✓, isolated H2 ✓, pinned clock ✓; class-level `@Transactional` on an "end-to-end" MockMvc test masks production behavior (C-1/C-2 root); three `RANDOM_PORT` boots (C-4); provider not pinned (G-3).
10. Constraint Conformance — RULE-2/26 exact boundary ✓ (D-3); RULE-5 Flyway ✓; RULE-11 key Δ (D-1); RULE-12/13/14 matrix ✓; RULE-15/16 guards ✓ (web surfacing ✗ C-3); RULE-17 default ✗ (P-5/D-2); RULE-37 no `now()` ✓; RULE-44 MockMvc real filter chain ✓ but transactional (G-1).

## Walkthrough Script (UI phases only)
UC-1 (spec.md § UC-1 main success scenario), actor Owner `george/george123`, precondition: Leo has no active request:
1. Start a request for Leo entering reason and availability text (`/my/requests/new` → POST) — must land on `/my/requests/{id}` showing pet, reason, availability, status *Awaiting consent* (**currently 500 — C-1**).
2. Review the request and grant explicit consent — status moves on; page re-renders.
3. (Phase-1: synchronous) interpretation runs — no waiting page yet.
4. Persisted interpretation shown read-only (care type, minutes, specialty, preferred vet).
5. Confirm the interpretation.
6. Exactly one held slot offered (vet, start, duration) — status *Suggestion offered*.
7. Accept the suggestion.
8. Redirect to *My appointments* showing one *Confirmed* appointment with vet name (**currently 500 — C-2**).
Must see on every page: stock layout, owner menu = My pets / My appointments only, username `george`, Logout, urgent-care banner "Call 555-0199.".
Extensions to try: 3a decline consent → *With staff* (phase-1: decline handler exists; after acceptance decline must be refused **without** an error page — currently 500, C-3).
Must be denied: george → `/staff/queue`, `/owners/find`, `/vets.html` = 403 page; betty → george's `/my/requests/{id}` = 404 identical to a missing id.
Staff (`admin/admin123`, cp-1 criterion 2): login lands on `/staff/queue`; navbar = Find owners, Veterinarians, Scheduling queue, Calendar, Clinic settings; `/my/**` = 403.

## Spec Reconciliation
- Δ folded in: RULE-11 (*As built:* `timefold.solver.termination.spent-limit=1s`), RULE-2 (*As built:* exact `FRAMEWORK_ADAPTER_BOUNDARY` class set incl. RULE-26 support types) — `rules.md`; `spec.md` § As-built convergence (cp-1) rewritten.
- F-n open: F-14 (C-1), F-15 (C-2), F-16 (C-3), F-17 (C-4), F-18 (G-1, ex F-7), F-19 (G-2), F-20 (G-3), F-21 (G-4), F-22 (G-5), F-23 (G-6), F-24 (G-7), F-25 (G-8); F-1..F-13 closed by 7a5be39 as verified above.
- Spec weaknesses exposed: (1) Security Surface omits explicit `/403` and lists `/oups` as pre-existing although its controller was removed → `rules`; (2) RULE-17 fixes `ollama` as default while the tasks assumptions (`tasks.yaml:8`) admit the model is unlikely to exist → `rules` must choose a default that a fresh checkout can run, or spec a test profile → `rules`; (3) AC-118 belongs to the owner cancel/view flow but no spec use case in phase-1 delivers that route → `criteria`/`spec` should tie AC-118 to UC-6; (4) AC-139 "exactly one random-port smoke test" conflicts with the stock `PetClinicIntegrationTests`/`PetClinicConcurrencyTests` the spec never told the executor to remove → `criteria`/`rules`; (5) spec has no rendering/persistence-context rule (open-in-view off + lazy associations) — a RULE stating "views render from fully-loaded read models / fetch joins; no lazy access in templates" would have caught C-1/C-2 at plan-review → `rules`.

### Plan weaknesses exposed by cp-1
(owner: `tasks` / `review (plan)` — the plan is about to be regenerated; absorb these there, no remediation phase appended)
- No controller/web artifact in phase-1 although cp-1 criterion 1 requires owner pages, and tasks 1.3/1.4/1.6 all touch controllers (`OwnerPageController`, `OwnerRequestController`, `StaffPageController`, `AccessDeniedController`) → C-1/C-2/C-3 had no owning artifact.
- Glob/prose artifacts (`templates/my/*.html`, `V1..V4*.sql`, "test config for isolated H2 + pinned Clock", "scheduling ownership guard service") — cannot be checked by exact name.
- Single-line ~2.8k-char `description`/`validation` strings (tasks.yaml:57, 87 …) — unreviewable in a diff.
- AC-138 claimed by phase-1 (task-1.7 "thin") and by phase-2/5 — double claim; phase `covers` must be disjoint per AC.
- AC-118 in task-1.3 `covers.acs` but absent from the phase-1 `covers` list (43 vs 44) and unverifiable without an owner appointment route.
- Five `L` tasks (1.2–1.6) with no intermediate checkpoint; task-1.6 alone spans interpreter, fidelity, solver and locked confirm.
- No `routes` inventory → `/oups` matcher-without-handler and the absence of an owner appointment route went unnoticed.
- `SecurityConfig.java` declared vs `SecurityConfiguration.java` built (P-4) — re-plan should name the existing file.
- `validation` bullets are prose without assertion shape for AC-16/17/18/19/21/58/137 → G-2, G-3, G-6, G-7.
- RULE-11 property key in the plan (`timefold.solver.solve.duration`) was invalid; plan-review should have grounded it (D-1).
- task-1.7 `validation` "one Confirmed appointment visible under My appointments" was satisfiable inside a test transaction only; the plan should require a non-transactional MockMvc (or random-port) rendering assertion.

## Resolution
REJECT. Ordered `REVISE:` directives (data → domain → web → tests); send one at a time. No remediation phase appended (plan regeneration pending; see Plan weaknesses).
1. `REVISE: task-1.6 - make owner read models render outside a persistence context: fetch-join (or @EntityGraph) pet, pet.owner and heldVet on SchedulingRequestRepository.findByIdAndOwnerId, and pet + vet on AppointmentRepository.findAllOwnedBy / findOwnedById (or map to view DTOs inside OwnerSchedulingAccessService); GET /my/requests/{id} and GET /my/appointments must return 200 in the running app with open-in-view=false`
2. `REVISE: task-1.6 - handle IllegalRequestTransitionException, ActiveRequestExistsException and IllegalStateException from the consent/decline/confirm/accept handlers (controller catch or a scheduling.web @ControllerAdvice): redirect to /my/requests/{id} with a keyed flash notice, never a 500; add the notice key to all 11 bundles`
3. `REVISE: task-1.7 - remove class-level @Transactional from SchedulingLifecycleE2eTests; after each step assert the rendered page (200, pet name; after accept /my/appointments 200 with vet name and status Confirmed); add a legal-URL/illegal-state POST (decline after accept) asserting 302 + notice, no state change, no event row`
4. `REVISE: task-1.6 - add a rendered test that /my/requests/new omits the <option> of a pet with an active request and keeps the others (AC-21); assert in the confirm test that SlotRanker/SolverManager was invoked and that the held vet/start/duration equal the ranker's first result (AC-58)`
5. `REVISE: task-1.5 - pin scheduling.ai.provider=stub in a test profile (src/test/resources/application.properties or @TestPropertySource) and assert no OllamaRequestInterpreter bean exists in the test context / ChatClient is never invoked (AC-137); record the RULE-17 default decision (D-2) in status.md Deviations`
6. `REVISE: task-1.4 - render /my/pets, /my/requests/new, /my/requests/{id}, /my/appointments as owner and assert: banner with 555-0199 on each, no edit/visit controls on /my/pets, vet names + specialties on the request form, layout fragment used (single stylesheet), username + POST /logout form present, and that POST /logout invalidates the session (AC-7/16/17/18/19); scan Java for user-visible literals (AC-140)`
7. `REVISE: task-1.7 - keep exactly one RANDOM_PORT test class (fold or delete stock PetClinicIntegrationTests / PetClinicConcurrencyTests) or obtain a recorded waiver for AC-139 (C-4)`
8. `REVISE: task-1.3 - record the SecurityConfig→SecurityConfiguration rename in status.md Deviations (P-4); drop the /oups matcher or restore its handler (K-2); remove the dead TimefoldSlotRanker entry (K-1)`
9. `REVISE: all phase-1 tasks - cite the AC-n ids each test class proves (ledger above) and fix the mis-scoped citations in FormLoginTests:42 and AppointmentConstraintProviderTests:37 (G-5)`
Plan-level (no REVISE, for the `tasks` re-plan): AC-138 single-phase claim (G-1, waiver candidate for cp-1), AC-118 moved to the owner cancel/view phase (G-4), web artifacts named per task, `routes` inventory, non-transactional rendering assertion for the E2E.

## Approval response
REVISE: task-1.6 - make owner read models render outside a persistence context: fetch-join (or @EntityGraph) pet, pet.owner and heldVet on SchedulingRequestRepository.findByIdAndOwnerId, and pet + vet on AppointmentRepository.findAllOwnedBy / findOwnedById (or map to view DTOs inside OwnerSchedulingAccessService); GET /my/requests/{id} and GET /my/appointments must return 200 in the running app with open-in-view=false
