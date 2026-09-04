# Checkpoint: phase-1 complete

Written retroactively on 2026-09-04 from the committed state of the repository (legacy waiver, see `status.md` →
Deviations). Every claim below points at a commit, a `file:line`, a test `class.method` or an HTTP status that was
observed in this session; nothing is inferred from `status.md` prose.

## Summary

- Phase: phase-1, Walking skeleton — auth, Flyway, lifecycle guards, thin UC-1 end to end
- Tasks: 7/7 complete
- Commits: `056ab3b`..`7a5be39` — **batched; legacy waiver recorded in status.md** (`spec/status.md:33`).
  `056ab3b` "Implementation: Tasks 1.1-1.4", `7cb2696` "Implementation: Tasks 1.5-1.7", `7a5be39` "Checkpoint 1
  resolution as per task list" (resolves `spec/convergence/cp-1.md` findings C-1..C-7 and G-1..G-6). No per-task
  commits exist; per-task scope cannot be graded from history.

## Task Closure

"Artifacts present" counts the items declared in the task's `artifact` string (prose/globs in the legacy plan) that
exist on disk as exact files (see Artifacts). "ACs cited by tests" counts, for each AC in `covers.acs`, whether
`grep -rn "AC-n" src/test` returns at least one hit — it says nothing about the quality of the hit (see AC Coverage).

| Task     | Commit(s)                         | Artifacts present (exact names)                                                                                                                                                                   | Scope clean            | ACs cited by tests                                                          | Validation bullets with file:line             |
|----------|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------|-----------------------------------------------------------------------------|-----------------------------------------------|
| task-1.1 | `056ab3b`, `7cb2696`, `7a5be39`   | yes (3/3: `pom.xml`; deletions of `db/mysql`, `db/postgres`, `application-{mysql,postgres}.properties`, `gradle/wrapper`; `architecture/ArchitectureBoundaryTests.java`) + 4 violating fixtures     | n/a (batched, waived)  | n/a (`covers.acs` is empty)                                                 | legacy prose validation — see AC Coverage     |
| task-1.2 | `056ab3b`, `7a5be39`              | yes (5/5: `V1__stock_schema.sql`, `V2__stock_data.sql`, `V3__scheduling_schema.sql`, `V4__scheduling_seed.sql`, `migration/SeedMigrationTests.java`)                                              | n/a (batched, waived)  | 2/11 (AC-125, AC-127 — both hits are in `FormLoginTests` Javadoc, a login test, not a seed test) | legacy prose validation — see AC Coverage     |
| task-1.3 | `056ab3b`, `7a5be39`              | yes (5/5: `SecurityConfiguration.java` [declared as `SecurityConfig.java`], `User.java`, `UserRepository.java`, `scheduling/web/OwnerSchedulingAccessService.java`, `security/SecurityMatrixWebTests.java`) | n/a (batched, waived)  | 1/11 (AC-118 only, and only via the mis-scoped Javadoc range `AC-111..AC-118` in `AppointmentConstraintProviderTests:37`) | legacy prose validation — see AC Coverage     |
| task-1.4 | `056ab3b`, `7a5be39`              | yes (6/6: `templates/my/*.html` ×4, `templates/staff/*.html` ×3, `templates/login.html`, `fragments/layout.html`, `messages/messages*.properties` ×11, `presentation/LocalizationKeyTests.java`) | n/a (batched, waived)  | 0/10                                                                        | legacy prose validation — see AC Coverage     |
| task-1.5 | `7cb2696`, `7a5be39`              | yes (6/6: `RequestLifecycleService.java`, `AppointmentLifecycleService.java`, `clinic/ClockConfig.java`, `RequestLifecycleRefusalTests.java`, `AppointmentLifecycleRefusalTests.java`, `TestClockConfig.java` [pinned Clock; isolated H2 is the default in-memory datasource, no extra config file]) | n/a (batched, waived)  | 4/5 (AC-123, AC-124, AC-136, AC-137; AC-108 none)                           | legacy prose validation — see AC Coverage     |
| task-1.6 | `7cb2696`, `7a5be39`              | yes (7/7: `RequestInterpreter.java`, `OllamaRequestInterpreter.java`, `StubRequestInterpreter.java`, `Interpretation.java`, `solver/SlotRanker.java`, `request/SuggestionService.java`, `scheduling/InterpretationFidelityTests.java`) | n/a (batched, waived)  | 5/5 (AC-20, AC-21, AC-53, AC-58, AC-63)                                     | legacy prose validation — see AC Coverage     |
| task-1.7 | `7cb2696`, `7a5be39`              | yes (2/2: `scheduling/SchedulingLifecycleE2eTests.java`, `SmokeTests.java`)                                                                                                                       | n/a (batched, waived)  | 2/2 (AC-138, AC-139)                                                        | legacy prose validation — see AC Coverage     |

## Artifacts

Paths are relative to `src/main/java/org/springframework/samples/petclinic/` (**M:**), `src/test/java/org/springframework/samples/petclinic/` (**T:**) or `src/main/resources/` (**R:**). "Declared in" is the task whose description the file implements; the legacy plan's `artifact` strings are globs/prose, so the assignment is by description. `A` = added in phase-1, `Mod` = stock file modified in phase-1.

| File | Declared in | Purpose |
|------|-------------|---------|
| `pom.xml` (Mod) | task-1.1 | Java 21, `spring-boot-starter-security`, `flyway-core`, `spring-ai-starter-model-ollama`, `timefold-solver-spring-boot-starter`, `archunit-junit5` (pom.xml:92-196) |
| deleted: `R:db/mysql/*`, `R:db/postgres/*`, `R:application-mysql.properties`, `R:application-postgres.properties`, `gradle/wrapper/*`, `T:MySqlIntegrationTests`, `T:MysqlTestApplication`, `T:PostgresIntegrationTests` | task-1.1 | H2 + Maven only (`056ab3b`) |
| `T:architecture/ArchitectureBoundaryTests.java` (A) | task-1.1 | Three RULE-2 ArchUnit rules + three "rule bites" fixture tests; exact adapter boundary set at :51-56 |
| `T:architecture/fixtures/scheduling/request/ViolatingSchedulingService.java`, `.../solver/ViolatingSolverClass.java`, `.../solver/ViolatingSolutionSupport.java`, `.../web/ViolatingSchedulingController.java` (A) | task-1.1 | Deliberately violating fixtures proving each ArchUnit rule fails |
| `R:db/migration/V1__stock_schema.sql`, `V2__stock_data.sql` (renamed from `db/h2/*`) | task-1.2 | Stock schema/data as Flyway V1/V2 |
| `R:db/migration/V3__scheduling_schema.sql` (A) | task-1.2 | `users`, `scheduling_request` (+ `uq_scheduling_request_active_pet`), `scheduling_request_event`, `interpretation`, `interpretation_window`, `appointment`, `appointment_change`, clinic/vet availability tables, `visits.appointment_id` (V3:1-160) |
| `R:db/migration/V4__scheduling_seed.sql` (A) | task-1.2 | Normative seed: 12 accounts (BCrypt), Clinic A hours, 3 parts of day, 1 config row, 17 vet blocks, 6 exceptions (V4:1-57) |
| `R:application.properties` (Mod) | task-1.2 / task-1.6 | `spring.sql.init.mode=never`, `spring.flyway.enabled=true` (:2-3); `scheduling.ai.provider` default `stub`, Ollama props, `timefold.solver.termination.spent-limit=1s` (:6-9) |
| `T:migration/SeedMigrationTests.java` (A) | task-1.2 | Migrates V1..V4 into a fresh in-memory H2 and asserts every seeded row by value as closed sets, passwords via `PasswordEncoder` |
| `T:schema/SchemaValidationTest.java` (A) | task-1.2 | Earlier (056ab3b) schema/seed/constraint checks, kept alongside `SeedMigrationTests` |
| `M:security/SecurityConfiguration.java` (A) | task-1.3 | Single `SecurityFilterChain`, CSRF on, BCrypt, URL→role matrix (:60-87), 403 forward to `/403` |
| `M:security/User.java`, `UserRepository.java`, `UserRole.java`, `UserRoleConverter.java` (A) | task-1.3 | `users` entity, closed `OWNER/STAFF` enum + JPA converter |
| `M:security/PetClinicUserDetails.java`, `PetClinicUserDetailsService.java` (A) | task-1.3 | DB-backed `UserDetailsService` exposing `ROLE_OWNER`/`ROLE_STAFF` + owner id |
| `M:security/RoleBasedAuthenticationSuccessHandler.java` (A) | task-1.3 | owner→`/my/appointments`, staff→`/staff/queue` (:42-46) |
| `M:security/SecurityUtils.java` (A) | task-1.3 | Current owner id from the session principal |
| `M:security/LoginController.java` (A) | task-1.3 | `GET /login` (:34), `POST /logout` (:39, manual logout), `/403` view (:45) |
| `M:system/WelcomeController.java` (Mod) | task-1.3 | `/` redirects by role, anonymous→`/login` |
| `M:scheduling/web/OwnerSchedulingAccessService.java`, `OwnerResourceNotFoundException.java` (A) | task-1.3 | Service-layer ownership guard: every pet/request/appointment lookup includes the session owner id; missing and other-owner → same 404 |
| `T:security/SecurityMatrixWebTests.java` (A) | task-1.3 | Whole protected GET/POST surface: anonymous 302 + no disclosure + no mutation; owner↔staff cross-denial; other-owner ≡ missing |
| `T:security/SecurityMatrixTests.java`, `FormLoginTests.java`, `OwnerSchedulingAccessServiceTests.java` (A), `T:system/WelcomeControllerTests.java`, `T:PetClinicConcurrencyTests.java` (Mod) | task-1.3 | Status-level matrix samples; login redirect/role-session/no-session; direct guard invocation; stock tests adapted to security |
| `R:templates/my/appointments.html`, `my/pets.html`, `my/requestForm.html`, `my/requestDetail.html` (A) | task-1.4 | Owner pages on `fragments/layout`; urgent-care banner on the two scheduling pages (`requestForm.html:4`, `requestDetail.html:4`) |
| `R:templates/staff/queue.html`, `staff/calendar.html`, `staff/settings.html` (A) | task-1.4 | Staff landing/placeholder pages on the stock layout |
| `R:templates/login.html`, `templates/403.html` (A) | task-1.4 | Login form with CSRF; access-denied page |
| `R:templates/fragments/layout.html` (Mod) | task-1.4 | Per-role menus (:42-49 owner, :57-79 staff), username badge + `POST /logout` form (:117-127), keyed labels |
| `R:templates/owners/createOrUpdateOwnerForm.html`, `pets/createOrUpdatePetForm.html`, `pets/createOrUpdateVisitForm.html` (Mod) | task-1.4 | Stock forms adjusted to the layout signature |
| `R:messages/messages.properties`, `messages_{de,en,es,fa,hi,ja,ko,pt,ru,tr}.properties` (Mod) | task-1.4 | Feature keys in all 11 bundles (English placeholders in non-English bundles) |
| `M:scheduling/web/OwnerPageController.java`, `StaffPageController.java` (A) | task-1.4 | `GET /my/pets` (:27), `GET /my/appointments` (:34); `GET /staff/{queue,calendar,settings}` (:19-29) |
| `T:presentation/LocalizationKeyTests.java`, `T:security/NavBarRenderingTests.java` (A) | task-1.4 | Key existence in default + 11 bundles, no unkeyed text/visible attributes in feature templates; navbar entries per role |
| `M:scheduling/request/SchedulingRequest.java`, `RequestState.java`, `SchedulingRequestEvent.java`, `SchedulingRequestRepository.java`, `SchedulingRequestEventRepository.java`, `IllegalRequestTransitionException.java`, `RequestLifecycleService.java` (A) | task-1.5 | Request aggregate (hold fields :66-73, `active_pet_id` :59-60), event log, service-owned transition guard (`RequestLifecycleService.java:258`) |
| `M:scheduling/appointment/Appointment.java`, `AppointmentStatus.java`, `AppointmentChange.java`, `AppointmentRepository.java`, `AppointmentChangeRepository.java`, `IllegalAppointmentTransitionException.java`, `AppointmentLifecycleService.java` (A) | task-1.5 | Appointment aggregate, audit rows, guard incl. completion-timing boundary (`AppointmentLifecycleService.java:149-165`) |
| `M:scheduling/clinic/ClockConfig.java`, `ClinicConfig.java`, `ClinicConfigRepository.java`, `ClinicConfigService.java` (A) | task-1.5 | Injectable `Clock` in the seeded clinic time zone (`ClockConfig.java:37-41`); clinic config incl. `emergency_phone` |
| `M:owner/Visit.java`, `PetController.java`, `VisitController.java` (Mod) | task-1.5 | `visits.appointment_id` back-ref; stock `LocalDate.now()` replaced by the injected `Clock` |
| `T:scheduling/TestClockConfig.java`, `ClinicClockTests.java`, `request/RequestLifecycleRefusalTests.java`, `request/RequestLifecycleMatrixTests.java`, `appointment/AppointmentLifecycleRefusalTests.java` (A); `T:owner/PetControllerTests.java`, `VisitControllerTests.java` (Mod) | task-1.5 | Pinned clock Monday 2026-09-07 09:00 Europe/Amsterdam; no direct `now()` scan; full action-by-state refusal matrix; appointment refusals incl. no visit/change row |
| `M:scheduling/interpretation/RequestInterpreter.java`, `StubRequestInterpreter.java`, `OllamaRequestInterpreter.java`, `RequestInterpretationService.java`, `InterpretationResult.java`, `Interpretation.java`, `InterpretationWindow.java`, `AvailabilityWindow.java`, `CareType.java`, `Provenance.java`, `WindowKind.java`, `InterpretationRepository.java`, `InterpretationWindowRepository.java` (A); `M:scheduling/config/OllamaChatConfiguration.java` (A) | task-1.6 | Interpreter port + `@ConditionalOnProperty(scheduling.ai.provider)` adapters (`StubRequestInterpreter.java:35`, `OllamaRequestInterpreter.java:24`); versioned persisted interpretation (`RequestInterpretationService.java:53`); Ollama client timeouts |
| `M:scheduling/solver/SlotRanker.java`, `DefaultSlotRanker.java`, `AppointmentAssignment.java`, `ScheduleSolution.java`, `AppointmentConstraintProvider.java`, `AppointmentSlot.java` (A) | task-1.6 | Timefold single-entity model (`AppointmentAssignment.java:40,62`, `ScheduleSolution.java:36`) solved synchronously via `SolverManager` (`DefaultSlotRanker.java:121`) over seeded availability |
| `M:scheduling/clinic/ClinicOpeningHour(+Repository).java`, `VetWeeklyBlock(+Repository).java`, `VetException(+Repository).java` (A) | task-1.6 | Seeded availability read by the ranker |
| `M:scheduling/request/SuggestionService.java`, `ActiveRequestExistsException.java` (A); `M:vet/VetRepository.java` (Mod) | task-1.6 | Per-vet `PESSIMISTIC_WRITE` lock before overlap re-check on confirm/accept (`VetRepository.java:49-51`, `SuggestionService.java:91,117`); constraint race → refusal (`RequestLifecycleService.java:76-77`) |
| `M:scheduling/web/OwnerRequestController.java` (A) | task-1.6 | `GET /my/requests/new` (:46), `POST /my/requests` (:54), `GET /my/requests/{id}` (:70), `POST …/{consent,decline,confirm,accept}` (:79-102); banner phone in model (:110) |
| `T:scheduling/InterpretationFidelityTests.java`, `interpretation/InterpretationPersistenceTests.java`, `request/SuggestionServiceOrderingTests.java`, `config/OllamaChatConfigurationTests.java`, `solver/AppointmentConstraintProviderTests.java`, `solver/StubSlotRanker.java` (A) | task-1.6 | Field-for-field round-trip; stub determinism; lock-before-recheck ordering; Ollama config; constraint provider scores; ranker test double |
| `T:scheduling/SchedulingLifecycleE2eTests.java` (A) | task-1.7 | MockMvc + real filter chain + CSRF UC-1 steps 1..8 as `george` |
| `T:SmokeTests.java` (A) | task-1.7 | Single `RANDOM_PORT` test: `/login` 200, form login, `/my/appointments` 200 |

## AC Coverage (this phase)

Rule applied: the "Test class.method" column is filled only from `grep -rn "AC-n" src/test` hits. Where the hit is a
class-level Javadoc, the method(s) whose assertions relate to the AC are named and the citation scope is stated. "none
found" means no test file mentions the AC id. Uncited tests that appear to exercise an AC are listed in Notes → "Uncited
candidates" and are **not** counted here.

| AC | Test class.method | Level (HTTP / web-slice / service / migration) | Assertion (one line, what is pinned) |
|----|-------------------|-----------------------------------------------|--------------------------------------|
| AC-1 | none found | — | — |
| AC-2 | none found | — | — |
| AC-3 | none found | — | — |
| AC-4 | none found | — | — |
| AC-5 | none found | — | — |
| AC-6 | none found | — | — |
| AC-7 | none found | — | — |
| AC-8 | none found | — | — |
| AC-9 | none found | — | — |
| AC-10 | none found | — | — |
| AC-11 | none found | — | — |
| AC-12 | none found | — | — |
| AC-13 | none found | — | — |
| AC-14 | none found | — | — |
| AC-15 | none found | — | — |
| AC-16 | none found | — | — |
| AC-17 | none found | — | — |
| AC-18 | none found | — | — |
| AC-19 | none found | — | — |
| AC-20 | `InterpretationFidelityTests.startRequestEnforcesSingleActiveRequestPerPet` (inline comment :159; class Javadoc :61) | `@SpringBootTest` service, `@Transactional` | `createRequest(owner, pet1, …)` → `getState() == AWAITING_CONSENT` and `getActivePetId() == pet1.id` (:162-163); service call, not the start-request form |
| AC-21 | `InterpretationFidelityTests.startRequestEnforcesSingleActiveRequestPerPet` (inline comment :165) | `@SpringBootTest` service | second `createRequest` for the same pet throws `IllegalStateException` (:166-168); does **not** assert the rendered pet selector |
| AC-53 | `InterpretationFidelityTests.interpretationFidelityRoundTrip` (class Javadoc :61 only) | `@SpringBootTest` service + JPA read-back after `flush()`/`clear()` | read-back equals produced `reasonSummary`, `estimatedMinutes`, `careType`, `specialty`, `preferredVet.id`, `cannotInterpret`, `rawResponse`, `modelTag`, `promptVersion`, and windows `containsExactlyElementsOf(result.windows())` (:138-154) |
| AC-58 | `InterpretationFidelityTests.confirmAndAcceptFlow` (inline comment :179) | `@SpringBootTest` service | `suggestionService.confirm` → `SUGGESTION_OFFERED`, `hasHold()`, `heldVet`/`heldStart` not null (:180-184); does not assert that Timefold ran |
| AC-63 | `InterpretationFidelityTests.confirmAndAcceptFlow` (inline comment :186) | `@SpringBootTest` service | `accept` → appointment `CONFIRMED` for pet1; request `ACCEPTED`, `activePetId` null, `heldVet` null (:187-195); lock ordering is not asserted here |
| AC-108 | none found | — | — |
| AC-118 | `AppointmentConstraintProviderTests` (class Javadoc :37, range `AC-111..AC-118`) | plain unit test of `AppointmentConstraintProvider` | Nine methods score hard/medium/soft constraints (double booking, opening hours, specialty, hold collision, preferred window, …); **none concerns another owner's appointment** — the AC range in the Javadoc is a mis-citation |
| AC-123 | `RequestLifecycleRefusalTests.{awaitingConsentRefusesInvalidTransitions, interpretingRefusesInvalidTransitions, terminalStatesRefuseAllTransitions}` (class Javadoc :40) | `@SpringBootTest` service | each disallowed action `isInstanceOf(IllegalRequestTransitionException)`; re-read state unchanged; event count unchanged (:102-119, :127-144, :153-172) — samples only; the full matrix lives in the uncited `RequestLifecycleMatrixTests` |
| AC-124 | `AppointmentLifecycleRefusalTests.{confirmedBeforeStartRefusesCompletionAndNoShow, confirmedAfterStartRefusesCancellationAndReschedule, terminalStatesRefuseAllActions}` (class Javadoc :40) | `@SpringBootTest` service with pinned `Clock` | `markCompleted`/`markNoShow` before start → `IllegalAppointmentTransitionException`, status still `CONFIRMED`, no `appointment_change` row, `pet.getVisits()` size unchanged (:95-105) |
| AC-125 | `FormLoginTests` (class Javadoc :42) | MockMvc with `springSecurity()` | `formLogin("george","george123")` → 302 `/my/appointments` (:58-61); this pins login, **not** the seeded account values — mis-scoped citation |
| AC-126 | none found | — | — |
| AC-127 | `FormLoginTests.ownerLoginRedirectsToMyAppointments` (class Javadoc :42) | MockMvc with `springSecurity()` | login of a seeded account succeeds through the encoder and the session carries `ROLE_OWNER` (:58-66); per-password verification of all 12 accounts is in the uncited `SeedMigrationTests` |
| AC-128 | none found | — | — |
| AC-129 | none found | — | — |
| AC-130 | none found | — | — |
| AC-131 | none found | — | — |
| AC-132 | none found | — | — |
| AC-133 | none found | — | — |
| AC-134 | none found | — | — |
| AC-135 | none found | — | — |
| AC-136 | `TestClockConfig` (Javadoc :29; fixture, not a test) + class Javadocs of `RequestLifecycleRefusalTests:40`, `AppointmentLifecycleRefusalTests:41` | test configuration | `Clock.fixed(2026-09-07T09:00 Europe/Amsterdam)` as `@Primary` bean (:35-45); imported by the scheduling tests |
| AC-137 | `InterpretationPersistenceTests.stubRequestInterpreterReturnsExpectedResults` (class Javadoc :46) | plain unit call on `new StubRequestInterpreter()` | deterministic outputs for "Routine checkup"/"Knee surgery"/"cannot_interpret" (:88-106); does **not** assert provider selection, the synchronous executor, or that no live model is called |
| AC-138 | `SchedulingLifecycleE2eTests.ownerGuidedFlowMainScenario` (class Javadoc :53) | HTTP MockMvc, real `SecurityFilterChain` (`springSecurity()`), CSRF, `@Transactional` test | as `george`: login 302→`/my/appointments`; `GET /my/requests/new` 200 with pet name + banner; `POST /my/requests` → `AWAITING_CONSENT`; consent → `INTERPRETED` + persisted interpretation; confirm → `SUGGESTION_OFFERED` with hold; accept → `ACCEPTED`, one `CONFIRMED` appointment rendered on `/my/appointments` (:91-167). Thin UC-1 only — no staff session / ask-again / hand-off / no-show leg |
| AC-139 | `SmokeTests.seededOwnerCanLoadLoginAndAuthenticatedPage` (class Javadoc :36) | `@SpringBootTest(RANDOM_PORT)` + `java.net.http` | `GET /login` 200 with `_csrf`; `POST /login` lands on `/my/appointments` 200 containing "My Appointments" and "george"; `GET /my/appointments` 200 (:47-73) |
| AC-140 | none found | — | — |

Cited: 14 of 43 phase-1 ACs (of which AC-118 and AC-125 are mis-citations). None found: 29.

## Routes (this phase)

Every `@GetMapping/@PostMapping/@RequestMapping` under `src/main/java`. "New" = added in phase-1. Statuses are those
observed in the runtime walkthrough (Runtime Evidence); `anon` = no session, `owner` = `george`, `staff` = `admin`.
`/oups` has no handler (the stock `CrashController` is absent from the tree) although the matrix lists it.

| Method | Path | Owning task | Handler (class.method) | New | Observed status anon / owner / staff |
|--------|------|-------------|------------------------|-----|--------------------------------------|
| GET | `/` | task-1.3 | `WelcomeController.welcome` | Mod | 302→`/login` / 302→`/my/appointments` / 302→`/staff/queue` |
| GET | `/login` | task-1.3 | `LoginController.login` | yes | 200 (h2 "Login") / – / – |
| POST | `/login` (filter) | task-1.3 | `UsernamePasswordAuthenticationFilter` + `RoleBasedAuthenticationSuccessHandler` | yes | george→302 `/my/appointments`; admin, staff→302 `/staff/queue`; bad password→302 `/login?error` |
| POST | `/logout` | task-1.3 | `LoginController.logout` | yes | – / 302→`/login?logout`, then `GET /my/appointments` 302→`/login` / – |
| ANY | `/403` | task-1.3 | `LoginController.accessDenied` | yes | 302→`/login` (authenticated-only) / rendered on forward (403 "Access Denied") / same |
| GET | `/my/pets` | task-1.4 | `OwnerPageController.pets` | yes | 302→`/login` / 200 (h2 "My Pets", "Owner Information", 0 edit controls) / 403 |
| GET | `/my/appointments` | task-1.4 | `OwnerPageController.appointments` | yes | 302→`/login` / 200 (h2 "My Appointments") before accept, **500** after an appointment exists (see Notes) / 403 |
| GET | `/my/requests/new` | task-1.6 | `OwnerRequestController.newRequest` | yes | 302→`/login` / 200 (h2 "Start a scheduling request", pet selector "Leo", banner "Call 555-0199.") / 403 |
| POST | `/my/requests` | task-1.6 | `OwnerRequestController.create` | yes | – / 302→`/my/requests/1` / – |
| GET | `/my/requests/{requestId}` | task-1.6 | `OwnerRequestController.detail` | yes | – / **500** (`LazyInitializationException`, see Notes) ; other-owner id and missing id as `betty` → 404, bodies identical modulo masked CSRF token / 403 |
| POST | `/my/requests/{requestId}/consent` | task-1.6 | `OwnerRequestController.consent` | yes | – / 302→`/my/requests/1` ; as `betty` other-owner and missing → 404 / 403 |
| POST | `/my/requests/{requestId}/decline` | task-1.6 | `OwnerRequestController.decline` | yes | not exercised |
| POST | `/my/requests/{requestId}/confirm` | task-1.6 | `OwnerRequestController.confirm` | yes | – / 302→`/my/requests/1` / – |
| POST | `/my/requests/{requestId}/accept` | task-1.6 | `OwnerRequestController.accept` | yes | – / 302→`/my/appointments` / – |
| GET | `/staff/queue` | task-1.4 | `StaffPageController.queue` | yes | 302→`/login` / 403 / 200 (h2 "Scheduling queue") |
| GET | `/staff/calendar` | task-1.4 | `StaffPageController.calendar` | yes | 302→`/login` / 403 / 200 (h2 "Calendar") |
| GET | `/staff/settings` | task-1.4 | `StaffPageController.settings` | yes | 302→`/login` / 403 / 200 (h2 "Clinic settings") |
| GET | `/owners/new` | stock | `OwnerController.initCreationForm` | no | – / 403 / – |
| POST | `/owners/new` | stock | `OwnerController.processCreationForm` | no | not exercised |
| GET | `/owners/find` | stock | `OwnerController.initFindForm` | no | 302→`/login` / 403 / 200 (h2 "Find Owners") |
| GET | `/owners` | stock | `OwnerController.processFindForm` | no | 302→`/login` / 403 / 200 (h2 "Owners") |
| GET | `/owners/{ownerId}` | stock | `OwnerController.showOwner` | no | – / 403 (`/owners/1`) / 200 (h2 "Owner Information") |
| GET, POST | `/owners/{ownerId}/edit` | stock | `OwnerController.initUpdateOwnerForm` / `processUpdateOwnerForm` | no | not exercised (covered by `/owners/**` matcher) |
| GET, POST | `/owners/{ownerId}/pets/new` | stock | `PetController.initCreationForm` / `processCreationForm` | no | not exercised |
| GET, POST | `/owners/{ownerId}/pets/{petId}/edit` | stock | `PetController.initUpdateForm` / `processUpdateForm` | no | not exercised |
| GET, POST | `/owners/{ownerId}/pets/{petId}/visits/new` | stock | `VisitController.initNewVisitForm` / `processNewVisitForm` | no | not exercised |
| GET | `/vets.html` | stock | `VetController.showVetList` | no | 302→`/login` / 403 / 200 (h2 "Veterinarians") |
| GET | `/vets` | stock | `VetController.showResourcesVetList` (JSON) | no | – / 403 / 200 (406 when `Accept: text/html`) |
| GET | `/actuator/**` | stock (exposed `*`) | actuator | no | `/actuator/health` 302→`/login` / 403 / 200 (406 with `Accept: text/html`); `/actuator` owner → 403 |
| GET | `/oups` | (matrix lists as pre-existing) | **no handler** | — | 302→`/login` / 403 / **404** |

## Runtime Evidence

- App started with: `./mvnw -q spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8080"` (default profile;
  in-memory H2 via Flyway V1..V4; `scheduling.ai.provider` defaults to `stub`, so no Ollama needed); log:
  `Tomcat started on port 8080`, `Started PetClinicApplication in 10.876 seconds` at 2026-09-04 11:22:24 (+03:00).
  JVM: Corretto 25.0.1 (Maven source level 21). Stopped after the walkthrough (`kill`, `GET /login` → connection refused).
- Method: `curl -c/-b <jar>`; CSRF token extracted from `name="_csrf" … value="…"` in the `GET /login` form (and, for
  later POSTs, from the logout form of an authenticated page — the token is per session).

### Actor `george` / `george123` (owner) — `POST /login` → 302 `/my/appointments`

| Step | Request | Observed | Rendered |
|------|---------|----------|----------|
| landing | `GET /` | 302 → `/my/appointments` | — |
| | `GET /my/appointments` | 200 | h2 "My Appointments"; navbar hrefs only `/my/appointments`, `/my/pets`; username `george` + Owner badge; `POST /logout` form present |
| | `GET /my/pets` | 200 | h2 "My Pets" / "Owner Information", pet "Leo"; 0 edit controls / `/owners` links |
| UC-1 step 1 | `GET /my/requests/new` | 200 | h2 "Start a scheduling request"; pet selector offers exactly `1=Leo`; banner "Need urgent care? Call 555-0199."; no vet list rendered on the form |
| | `POST /my/requests` `petId=1&reasonText=…&availabilityText=…` | 302 → `/my/requests/1` | — |
| | `GET /my/requests/1` | **500** | stock `error.html` "Something happened…"; server log: `TemplateInputException … my/requestDetail line 6, col 40 … request.pet.name … LazyInitializationException: Could not initialize proxy [Pet#1] - no session` |
| step 2 (consent) | `POST /my/requests/1/consent` (token from `/my/appointments`) | 302 → `/my/requests/1` | handler ran (no 4xx/5xx) |
| steps 3–4 | `GET /my/requests/1` | **500** | same `LazyInitializationException`; waiting page / read-only interpretation could not be viewed |
| step 5 (confirm) | `POST /my/requests/1/confirm` | 302 → `/my/requests/1` | handler ran |
| step 6 | `GET /my/requests/1` | **500** | held slot could not be viewed |
| step 7 (accept) | `POST /my/requests/1/accept` | 302 → `/my/appointments` | handler ran |
| step 8 | `GET /my/appointments` | **500** | log: `my/appointments line 8, col 147 … appointment.vet.firstName … LazyInitializationException: Could not initialize proxy [Vet#4] - no session` — the Confirmed appointment exists (Vet#4 was assigned) but the page cannot render it |
| after | `GET /my/requests/new` | 200 | pet selector again offers `Leo` (request left the active state, `active_pet_id` cleared) |
| denied | `GET /owners`, `/owners/find`, `/owners/1`, `/owners/new`, `/vets.html`, `/vets`, `/staff/queue`, `/staff/calendar`, `/staff/settings`, `/oups`, `/actuator/health`, `/actuator` | **403** each | h2 "Access Denied" (`403.html` inside the stock layout) |
| logout | `POST /logout` | 302 → `/login?logout`; then `GET /my/appointments` → 302 `/login` | session ended |

### Actor `betty` / `betty123` (other owner) — `POST /login` → 302 `/my/appointments`

| Request | Observed | Note |
|---------|----------|------|
| `GET /my/requests/1` (george's request) | 404 | `error.html` "Something happened… Resource not found"; body contains no "george", "Leo" or reason text |
| `GET /my/requests/999999` (missing) | 404 | byte-identical to the other-owner body except the per-request masked `_csrf` value (`diff` shows only line 87) |
| `POST /my/requests/1/consent`, `POST /my/requests/999999/consent` | 404 / 404 | no state change visible to george afterwards (his later POSTs still progressed) |
| `GET /my/pets` | 200 | own pet only: "Basil / 2012-08-06 / hamster" |
| Without `Accept: text/html` the 404 is the Boot JSON error body; with `spring-boot-devtools` on the classpath it includes a `trace` field (dev-only concern, not part of the matrix) |

### Actor `admin` / `admin123` (staff) — `POST /login` → 302 `/staff/queue` (also `staff`/`staff123` → 302 `/staff/queue`)

| Request | Observed | Rendered |
|---------|----------|----------|
| `GET /` | 302 → `/staff/queue` | — |
| `GET /staff/queue` | 200 | h2 "Scheduling queue"; navbar hrefs `/owners/find`, `/vets.html`, `/staff/queue`, `/staff/calendar`, `/staff/settings`; username `admin` + Staff badge; `POST /logout` form |
| `GET /staff/calendar`, `GET /staff/settings` | 200 / 200 | h2 "Calendar" / "Clinic settings" |
| `GET /owners/find`, `/owners`, `/owners/1`, `/vets.html` | 200 each | "Find Owners", "Owners", "Owner Information", "Veterinarians" |
| `GET /vets`, `GET /actuator/health` | 200 (JSON); 406 with `Accept: text/html` | — |
| `GET /oups` | **404** | no handler in the tree |
| `GET /my/appointments`, `/my/pets`, `/my/requests/new`, `/my/requests/1` | **403** each | "Access Denied" |
| `POST /my/requests/1/consent` | 403 | — |

### Anonymous

`GET /`, `/my/appointments`, `/my/pets`, `/my/requests/new`, `/owners`, `/owners/find`, `/vets.html`, `/staff/queue`,
`/staff/calendar`, `/staff/settings`, `/oups`, `/actuator/health`, `/403` → **302 `/login`** each; `GET /login` → 200
(h2 "Login"). `POST /login` with a wrong password → 302 `/login?error`.

### Denied URLs per role (summary)

- owner on `/owners/**`, `/vets*`, `/staff/**`, `/oups`, `/actuator/**` → 403 ✔; owner on another owner's / missing request id → 404, identical ✔
- staff on `/my/**` → 403 ✔
- anonymous on every protected URL → 302 `/login` ✔
- **cp-1 criterion 1 (owner UC-1 by hand) is NOT met at runtime**: the request detail page (steps 1 redirect target, 3, 4, 6) and the post-accept `/my/appointments` (step 8) return 500. Criterion 2 (staff landing + navbar) is met.

## Validation

- Test command: `./mvnw -q test` (no Maven profile; Java 25.0.1 runtime, `<java.version>21</java.version>`)
- Compilation: PASS
- Tests: 149/0/0/0 (run/failed/errors/skipped) — totals from `target/surefire-reports/TEST-*.xml` (`tests=` attribute
  sum; the `*.txt` summaries sum to 129 because `PetControllerTests` and `PetValidatorTests` report their `@Nested`
  cases only in the XML: 14 and 6). Skipped: none.
  Per class (n): ArchitectureBoundaryTests 6, SeedMigrationTests 6, SchemaValidationTest 9, SecurityMatrixWebTests 4,
  SecurityMatrixTests 10, FormLoginTests 6, OwnerSchedulingAccessServiceTests 2, NavBarRenderingTests 3,
  LocalizationKeyTests 3, I18nPropertiesSyncTest 2, ClinicClockTests 2, RequestLifecycleRefusalTests 4,
  RequestLifecycleMatrixTests 1, AppointmentLifecycleRefusalTests 5, InterpretationFidelityTests 3,
  InterpretationPersistenceTests 3, SuggestionServiceOrderingTests 1, OllamaChatConfigurationTests 2,
  AppointmentConstraintProviderTests 9, SchedulingLifecycleE2eTests 1, SmokeTests 1, WelcomeControllerTests 3,
  PetClinicIntegrationTests 3, PetClinicConcurrencyTests 1, stock owner/vet/model/service tests 53.
- Plan guard tests: not in this phase (legacy plan)
- Skeleton test: n/a
- Constraints (this phase's RULES): 24/25 satisfied with a `file:line` (union of `covers.rules` of task-1.1..1.7 =
  RULE-1..17, 25, 26, 37, 38, 39, 41, 42, 44); RULE-17's default-provider clause deviates — see Notes.
- `git status --short` after the suite: clean (only this session's own untracked shell-output logs appeared and were
  removed; no tracked file modified).

## Notes

### Deviations

1. **Timefold property key** — RULE-11 names `timefold.solver.solve.duration=1s`; Timefold 2.5.0 rejects it, so
   `timefold.solver.termination.spent-limit=1s` is used (`application.properties:9`; recorded in `status.md:32`).
2. **RULE-2 / RULE-26 boundary** — RULE-2 forbids Timefold imports outside the `SlotRanker`/`RequestInterpreter`
   adapters while RULE-26 requires Timefold-annotated support types. Resolved (cp-1/C-1) as an **exact** set:
   `AppointmentAssignment`, `ScheduleSolution`, `AppointmentConstraintProvider`, `DefaultSlotRanker`,
   `TimefoldSlotRanker` (name reserved, class does not exist), `OllamaRequestInterpreter`
   (`ArchitectureBoundaryTests.java:44-56`); substring exemptions removed; `ViolatingSolutionSupport` fixture proves the
   rule bites (`:113-120`).
3. **RULE-17 default provider** — the rule says `scheduling.ai.provider` defaults to `ollama`; the shipped default is
   `${SCHEDULING_AI_PROVIDER:stub}` (`application.properties:6`) so tests and the local run stay offline. Not recorded
   in `status.md` → Deviations; flagged here for converge.
4. **Artifact name** — task-1.3 declared `security/SecurityConfig.java`; the file is `SecurityConfiguration.java`.
5. **`/oups`** — rules.md §Security Surface lists it as pre-existing staff-only; the matcher exists
   (`SecurityConfiguration.java:70`) but no controller does (stock `CrashController` is absent), so staff get 404.

### Legacy waiver

Per-task commits do not exist (`status.md:33`, approved 2026-09-04): phase-1 landed as `056ab3b` (1.1–1.4),
`7cb2696` (1.5–1.7) and `7a5be39` (cp-1 resolution of C-1..C-7, G-1..G-6). This report is written from the committed
state; Task Closure "Scope clean" is therefore `n/a`, and "Validation bullets with file:line" cannot be graded because
the legacy `validation` fields are prose without pointers.

### What the runtime walkthrough exposed

- **Blocker for cp-1 criterion 1**: `GET /my/requests/{id}` → 500 and `GET /my/appointments` (once an appointment
  exists) → 500. Root cause: `SchedulingRequest.pet` / `Appointment.vet` are `@ManyToOne(fetch = LAZY)`
  (`SchedulingRequest.java:41-45`, `Appointment.java:41-45`), `spring.jpa.open-in-view=false`
  (`application.properties:16`), and `OwnerSchedulingAccessService` returns detached entities from
  `@Transactional(readOnly = true)` methods (`:42-75`); `my/requestDetail.html:6` (`request.pet.name`) and
  `my/appointments.html:8` (`appointment.vet.firstName`) then throw `LazyInitializationException`.
  The green tests mask it: `SchedulingLifecycleE2eTests` is class-level `@Transactional` (`:56`), which keeps one
  persistence context open across all MockMvc calls; `SmokeTests` loads `/my/appointments` while george has zero
  appointments, so no lazy association is touched. The UC-1 **state machine itself** does progress at runtime (the
  consent/confirm/accept POSTs all returned 302 and the pet selector re-offered Leo afterwards) — it is the two owner
  pages that fail to render.
- Other-owner vs missing id: 404 with byte-identical bodies except the masked CSRF token; no data disclosed.
- Owner navbar contains exactly `/my/pets` and `/my/appointments`; staff navbar contains the stock entries plus
  queue/calendar/settings; username and `POST /logout` on every page checked.
- Urgent-care banner "Need urgent care? Call 555-0199." renders on `/my/requests/new`; on `/my/requests/{id}` it could
  not be observed because the page 500s (the template carries it at `requestDetail.html:4`).
- With devtools on the classpath, non-HTML 404 responses include a stack `trace` field.

### Uncited candidates (not counted in AC Coverage; listed so converge can grade them)

- `SecurityMatrixWebTests.{anonymousProtectedSurfaceRedirectsWithoutDisclosureOrMutation:115, ownerCannotReachAnyStaffSurfaceOrMutateStaffData:127, staffCannotReachOwnerSurfaceOrMutateOwnerData:140, otherOwnerAndMissingRequestAreIdenticalAndCannotBeMutated:153}` → AC-1, AC-2, AC-8, AC-9, AC-11, AC-12, AC-118
- `OwnerSchedulingAccessServiceTests.{otherOwnersPetAndMissingPetHaveTheSameOutcomeWithoutMutation:55, requestAndAppointmentQueriesAlwaysIncludeAuthenticatedOwnerId:77}` → AC-10, AC-13
- `FormLoginTests.{staffLoginRedirectsToStaffQueue:70, badCredentialsRedirectsToLoginError:81, authenticatedOwnerAccessingRootRedirectsToMyAppointments:97, authenticatedStaffAccessingRootRedirectsToStaffQueue:105, logoutRedirectsToLoginWithLogoutParam:112}` → AC-3, AC-4, AC-5, AC-6, AC-7
- `NavBarRenderingTests.{ownerNavBarRendersOwnerLinksAndSessionWidget:67, staffNavBarRendersStaffLinksAndSessionWidget:83}` → AC-7, AC-14, AC-15
- `LocalizationKeyTests.{everyReferencedTemplateKeyExistsInTheDefaultBundle:49, featurePlaceholdersArePresentAndIdenticalInAllElevenBundles:64, featureTemplatesEmitNoUnkeyedTextOrVisibleAttributes:83}` → AC-140
- `SeedMigrationTests.{usesOnlyAnIsolatedInMemoryDatabase:55, seedsExactlyTheNormativeAccountsAndRoles:64, seedsExactlyClinicAOpeningHours:100, seedsExactlyAllVetWeeklyBlocks:118, seedsExactlyAllUnavailableExceptionsAndNoLeaveOrClosures:136, seedsExactlyAllPartsOfDayAndTheSingleConfigRow:153}` → AC-125..AC-135
- `ClinicClockTests.{runtimeClockUsesTheSeededClinicTimeZone:34, productionCodeHasNoDirectSystemNowCalls:39}` → AC-108
- `RequestLifecycleMatrixTests.everyDisallowedActionRefusesBeforeMutationOrEventPersistence:42` → AC-123 (full matrix)
- `SuggestionServiceOrderingTests.acceptLocksVetBeforeRecheckingAndRefusesAConflictingAppointment:37` → AC-63 (lock ordering)
- `SchedulingLifecycleE2eTests.ownerGuidedFlowMainScenario:98-101` → AC-16, AC-17, AC-18, AC-19 partially (banner text and pet name on the request form)
- No test cites or exercises AC-21 as a rendered pet selector beyond the E2E form check at `:98-100`.

### Constraint pointers (RULEs in phase-1 `covers.rules`)

- RULE-1 package-by-feature, public `@Entity`, MVC controllers, Spring Data — `scheduling/{request,interpretation,solver,appointment,clinic,web}`, `security/`; `SchedulingRequest.java`, `Appointment.java`, `Interpretation.java`, `User.java` are public entities; `OwnerRequestController.java`, `StaffPageController.java` return view names.
- RULE-2 — `ArchitectureBoundaryTests.java:98-135` (three rules + three biting fixtures).
- RULE-3 — no `spring-modulith` in `pom.xml`; no `*Entity`/`*API` types under `src/main`.
- RULE-4 — `application.properties:15-17` (`ddl-auto=none`, `open-in-view=false`, snake-case naming); repositories are Spring Data; the only explicit lock query is `VetRepository.java:49-51`.
- RULE-5 — `application.properties:2-3`; `db/migration/V1..V4`; tests run the same migrations (`SeedMigrationTests.migrateFreshDatabase:47`).
- RULE-6 — no Liquibase dependency in `pom.xml`.
- RULE-7 — `V3__scheduling_schema.sql:1-160` (all listed tables; `visits.appointment_id` at :159-160).
- RULE-8 — `SchedulingRequest.java:66-73` hold fields; no `slot_hold` table in V3; no timer/scheduler bean.
- RULE-9 — `V3:28` unique `active_pet_id`; `RequestLifecycleService.java:57,76-77` → `ActiveRequestExistsException`; `OwnerRequestController.java:64` surfaces it as a redirect, not a stack trace.
- RULE-10 — `VetRepository.java:49-51` `@Lock(PESSIMISTIC_WRITE)`; `SuggestionService.java:91,117` lock first, then overlap re-check.
- RULE-11 — `DefaultSlotRanker.java:121` synchronous `SolverManager.solve` inside the locked transaction; budget `application.properties:9` (key deviation above).
- RULE-12 — `SecurityConfiguration.java:60-87`; `RoleBasedAuthenticationSuccessHandler.java:42-46`; `WelcomeController.java:28`.
- RULE-13 — `OwnerSchedulingAccessService.java:42-75`; `SecurityUtils.getCurrentOwnerId` used by `OwnerPageController.java:42`, `OwnerRequestController.java:114` (owner id from the session, never the URL).
- RULE-14 — `V3:5` `CHECK (role IN ('owner','staff'))`; `UserRole.java:16`; `UserRoleConverter.java`.
- RULE-15 — `RequestLifecycleService.java:258` (`IllegalRequestTransitionException` before any mutation/event).
- RULE-16 — `AppointmentLifecycleService.java:149-165` (`IllegalAppointmentTransitionException`; completion refused before start).
- RULE-17 — `RequestInterpreter.java`; `StubRequestInterpreter.java:34-36` / `OllamaRequestInterpreter.java:23-25` selected by `scheduling.ai.provider`; default value deviates (Notes 3).
- RULE-25 — `Interpretation.java` (all scalar fields + `rawResponse`, `modelTag`, `promptVersion`), `InterpretationWindow.java`; versioning `RequestInterpretationService.java:53`.
- RULE-26 — `AppointmentAssignment.java:40,62` (single `@PlanningEntity`, one `@PlanningVariable`), `ScheduleSolution.java:36,50`, `AppointmentConstraintProvider.java:32`, behind `SlotRanker.java`.
- RULE-37 — `ClockConfig.java:37-41` (zone from `clinic_config.time_zone`, seeded `Europe/Amsterdam`, `V4`); `ClinicClockTests.productionCodeHasNoDirectSystemNowCalls:39`.
- RULE-38 — `my/requestForm.html:4`, `my/requestDetail.html:4`; phone from `OwnerRequestController.java:110`.
- RULE-39 — every `templates/my/*.html` and `templates/staff/*.html` line 2 uses `fragments/layout :: layout`; menus `layout.html:42-49` (owner), `:57-79` (staff); username/logout `:117-127`; `my/pets.html` has no edit controls (0 observed at runtime).
- RULE-41 — 11 bundles under `resources/messages/`; `LocalizationKeyTests.java:49-83`.
- RULE-42 — `V4__scheduling_seed.sql:1-57`; `SeedMigrationTests.java:55-153` (by value, closed sets, `PasswordEncoder.matches`).
- RULE-44 — isolation: default in-memory H2, `SeedMigrationTests.usesOnlyAnIsolatedInMemoryDatabase:55`, tree clean after the suite; pinned clock `TestClockConfig.java:35-45`; stub provider `application.properties:6`; MockMvc lifecycle `SchedulingLifecycleE2eTests.java:80-167`; single random-port smoke `SmokeTests.java:38-73`.

CHECKPOINT REACHED. AWAITING APPROVAL.
