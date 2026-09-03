# Convergence: cp-1 — Walking skeleton — auth, Flyway, lifecycle guards, thin UC-1 end to end

## Summary

- Checkpoint: cp-1 (phase-1, 7/7 tasks claimed complete)
- Verdict: REJECT
- Counts: 7 critical, 6 gaps, 0 drift, 0 cosmetic; carried forward: none
- Suite (run by converge): 128/0/0/0 — no skipped tests
- Working tree after test run: clean
- Baseline working tree: clean
- Command: `./mvnw test` on Java 25.0.1; Maven source level is Java 21

The migrations produce the normative seed values, but most of the walking skeleton is not reachable over HTTP. There
are no `/my/**`, `/staff/**`, or scheduling request controllers or templates. The test named end-to-end calls services
and repositories directly. The active ranker is a hand-written enumerator rather than Timefold. Several declared test
artifacts do not exist, and the substitutes do not make the required assertions.

## Evidence Ledger

| AC | Pattern | Claimed in | Evidence (file:line) | Strength | Verified |
|---|---|---|---|---|---|
| AC-1 | Unwanted behavior | task-1.3 | `SecurityMatrixTests.java:63-98`; `SecurityConfiguration.java:61-73` | WEAK — samples only; `/403` is public | No — C-2 |
| AC-2 | Authorization | task-1.3 | `SecurityMatrixTests.java:63-98` | WEAK — redirect only; no body or mutation assertion | No — G-2 |
| AC-3 | Event-driven | task-1.3 | `FormLoginTests.java:53-69` | WEAK — redirects asserted, session role not asserted | No — G-2 |
| AC-4 | Unwanted behavior | task-1.3 | `FormLoginTests.java:71-80` | WEAK — failure redirect asserted, absence of session not asserted | No — G-2 |
| AC-5 | Event-driven | task-1.3, task-1.4 | `WelcomeController.java:28-34`; `FormLoginTests.java:53-58` | WEAK — redirects to a route with no controller/page | No — C-3 |
| AC-6 | Event-driven | task-1.3, task-1.4 | `WelcomeController.java:33-34`; `FormLoginTests.java:60-69` | WEAK — redirects to a route with no controller/page | No — C-3 |
| AC-7 | Ubiquitous/event-driven | task-1.4 | `layout.html:121-134`; `FormLoginTests.java:98-103` | WEAK — logout is invoked without an authenticated session; feature pages are absent | No — C-3 |
| AC-8 | Authorization | task-1.3 | `SecurityMatrixTests.java:110-126` | WEAK — status-only samples, not the whole URL/action surface | No — G-2 |
| AC-9 | Authorization | task-1.3 | `SecurityMatrixTests.java:110-126` | WEAK — no disclosure or mutation assertion | No — G-2 |
| AC-10 | Ubiquitous | task-1.3 | `SecurityUtils.java:41-47` | ABSENT — helper exists but no owner controller consumes it | No — C-2 |
| AC-11 | Authorization | task-1.3 | `SecurityMatrixTests.java:150-158` | ABSENT — no other-owner/missing-id comparison | No — C-2 |
| AC-12 | Authorization | task-1.3 | `SecurityMatrixTests.java:150-158` | ABSENT — no owner resource path, disclosure check, or mutation check | No — C-2 |
| AC-13 | Ubiquitous | task-1.3 | `SecurityUtils.java:41-47`; full production controller scan | ABSENT — no service ownership guard | No — C-2 |
| AC-14 | State-driven | task-1.4 | `NavBarRenderingTests.java:65-80`; `layout.html:41-59` | WEAK — test requires a third owner entry, Schedule Appointment | No — C-3 |
| AC-15 | State-driven | task-1.4 | `NavBarRenderingTests.java:82-97`; `layout.html:61-89` | WEAK — Calendar is absent and unrelated Schedule Appointment is accepted | No — C-3 |
| AC-16 | Ubiquitous | task-1.4 | `layout.html:1-164`; template inventory | ABSENT — the declared owner/staff pages do not exist | No — C-3 |
| AC-17 | State-driven | task-1.4 | full route/template inventory | ABSENT — no My pets page | No — C-3 |
| AC-18 | State-driven | task-1.4 | full route/template inventory | ABSENT — no scheduling request page | No — C-3 |
| AC-19 | Ubiquitous | task-1.4 | full route/template inventory | ABSENT — no owner scheduling page or urgent-care banner | No — C-3 |
| AC-20 | Event-driven | task-1.6 | `InterpretationFidelityTests.java:156-168`; `SchedulingLifecycleE2eTests.java:95-105` | MISPLACED — service calls, not an owner form submission over HTTP | No — C-5 |
| AC-21 | State-driven | task-1.6 | `InterpretationFidelityTests.java:156-168` | ABSENT — second-create refusal is not a rendered pet selector | No — C-5 |
| AC-53 | Event-driven/round-trip | task-1.6 | `InterpretationFidelityTests.java:106-154` | WEAK — window count only; preferred vet and every window field are not compared | No — G-4 |
| AC-58 | Event-driven | task-1.6 | `InterpretationFidelityTests.java:170-195`; `DefaultSlotRanker.java:51-90` | WEAK — state/hold asserted, but the active ranker does not invoke Timefold | No — C-6 |
| AC-63 | Event-driven | task-1.6 | `SuggestionService.java:111-159`; `InterpretationFidelityTests.java:185-195` | WEAK — result asserted; lock acquisition and overlap re-check can be removed without failing | No — G-5 |
| AC-108 | Ubiquitous | task-1.5 | `ClockConfig.java:33-39`; `VisitController.java:85-100` | WEAK — constant zone and direct system-clock calls, not one configured source | No — C-4 |
| AC-123 | Unwanted behavior | task-1.5 | `RequestLifecycleRefusalTests.java:97-173` | WEAK — only partial action-by-state cells are exercised | No — G-3 |
| AC-124 | Unwanted behavior/boundary | task-1.5 | `AppointmentLifecycleRefusalTests.java:88-151` | WEAK — change rows are checked, but invalid completion is not proved to create no visit | No — G-3 |
| AC-125 | Normative data | task-1.2 | `SchemaValidationTest.java:71-106` | WEAK — linked owner names are never asserted | No — G-1 |
| AC-126 | Normative data | task-1.2 | `SchemaValidationTest.java:71-106` | STRONG | Yes |
| AC-127 | Normative data | task-1.2 | `SchemaValidationTest.java:89-100` | STRONG | Yes |
| AC-128 | Normative data | task-1.2 | `SchemaValidationTest.java:108-144` | WEAK — Clinic A is selected but its name is never asserted | No — G-1 |
| AC-129 | Normative data | task-1.2 | `SchemaValidationTest.java:146-150` | WEAK — count-only | No — G-1 |
| AC-130 | Normative data | task-1.2 | `SchemaValidationTest.java:146-150` | WEAK — a count cannot prove there are no different blocks | No — G-1 |
| AC-131 | Normative data | task-1.2 | `SchemaValidationTest.java:152-182` | WEAK — `unavailable` is asserted for only the first row | No — G-1 |
| AC-132 | Normative data | task-1.2 | `SchemaValidationTest.java:184-191` | STRONG | Yes |
| AC-133 | Normative data | task-1.2 | `SchemaValidationTest.java:193-204` | WEAK — parts of day omitted; emergency phone only non-null | No — G-1 |
| AC-134 | Normative data | task-1.2 | `SchemaValidationTest.java:193-204` | WEAK — no assertion against extra config/part-of-day rows | No — G-1 |
| AC-135 | Ubiquitous | task-1.2, task-1.5 | `application.properties:1-3`; fresh suite logs and before/after `git status` | STRONG | Yes |
| AC-136 | Ubiquitous | task-1.5 | `TestClockConfig.java:35-45`; scheduling tests import it | STRONG | Yes |
| AC-137 | Ubiquitous | task-1.5, task-1.6 | `InterpretationPersistenceTests.java:87-106`; `InterpretationFidelityTests.java:106-113` | ABSENT — tests construct the stub directly; there is no selected provider or synchronous executor | No — C-5 |
| AC-138 | Lifecycle/path | task-1.7 | `SchedulingLifecycleE2eTests.java:53-149` | MISPLACED — no MockMvc, filter chain, CSRF, HTTP, or staff session; UC-1 1–8 is simulated with direct services | No — C-7 |
| AC-139 | Lifecycle/path | task-1.7 | `SmokeTests.java:34-48` | WEAK — login is loaded, but no authenticated page is requested | No — G-6 |
| AC-140 | Ubiquitous | task-1.4 | `I18nPropertiesSyncTest.java:64-78,116-121`; `layout.html:96-109` | WEAK — scanner skips attributes/Java and explicitly excludes empty English bundle; literals are present | No — C-3 |

## Findings

### Critical

#### C-1 — The architecture guard encodes a name-based exception that contradicts RULE-2

- Spec reference: RULE-2: “MUST add `archunit-junit5` (test scope) and ArchUnit tests that fail the build when: (a) any type under `scheduling.solver`/`scheduling.interpretation` domain or service packages imports `ai.timefold..` or `org.springframework.ai..` outside the `SlotRanker`/`RequestInterpreter` adapter classes.”
- Code evidence: `AppointmentAssignment.java:26-28`, `ScheduleSolution.java:23-28`, and `AppointmentConstraintProvider.java:19-24` import Timefold directly.
- Test evidence: `ArchitectureBoundaryTests.java:47-62` exempts every class whose name contains `ConstraintProvider`, `Solution`, or `Assignment`, so those imports pass.
- Why wrong: the test does not enforce the quoted MUST and can be bypassed by naming any unrelated class with one of those substrings. RULE-2 also conflicts with RULE-26's requirement for Timefold-annotated solver support types; that contradiction is recorded under Spec weaknesses.
- Owning task: task-1.1.
- Resolution: `REVISE: task-1.1 - Replace substring exemptions with an exact, documented adapter-implementation boundary; obtain the RULE-2/RULE-26 clarification recorded below and add a deliberately violating support-class fixture.`

#### C-2 — The exact security surface and service ownership boundary do not exist

- Spec references:
  - AC-1: “If an anonymous user requests any URL other than the login page or a static resource, then the system shall redirect the response to `/login`.”
  - AC-13: “The system shall enforce owner scoping in the service layer independently of the URL role matchers, such that ownership denial holds even when the URL matcher is bypassed.”
  - RULE-14: “MUST model accounts in a `users` table with roles limited to exactly `owner` and `staff` and a nullable `owner_id` FK linking owner accounts to `owners`; MUST NOT define any other role or authority.”
- Code evidence: `SecurityConfiguration.java:61-73` makes `/403` public, adds matrix entries not in the spec, and falls back to generic `authenticated()`; `User.java:64-70` accepts any role and `PetClinicUserDetails.java:43-50` turns it into any `ROLE_*`; the controller inventory has no `/my/**` controller and no service ownership guard.
- Test evidence: `SecurityMatrixTests.java:63-158` checks only statuses/redirects on samples and contains no other-owner/missing-id pair or direct service invocation.
- Why wrong: anonymous `/403` is reachable rather than redirected; owner resource isolation cannot be exercised; arbitrary stored roles become authorities; and redirects point to unmapped owner/staff routes.
- Owning task: task-1.3.
- Resolution: `REVISE: task-1.3 - Implement the exact route matrix and a service ownership guard, restrict the role model to owner/staff, make other-owner and missing ids identical, and prove the whole surface with denial plus no-disclosure plus no-mutation assertions.`

#### C-3 — The checkpoint UI is absent, navigation is wrong, and localization violates the contract

- Spec references:
  - AC-14: “While signed in as an owner, the system shall show only the My pets and My appointments navigation entries.”
  - AC-15: “While signed in as staff, the system shall show the stock navigation entries plus Scheduling queue, Calendar, and Clinic settings.”
  - AC-19: “The system shall display the urgent-care banner with the configured emergency phone on every owner scheduling page and on the request detail page.”
  - AC-140: “The system shall resolve every user-visible string introduced by the feature — page text, labels, placeholders, other attributes, and Java-produced status/flash messages — through a message key present in every shipped locale bundle, authored in English with identical English placeholder text in each non-English bundle, and shall emit no English literal directly from application code introduced or modified by this feature.”
- Code evidence: no `templates/my` or `templates/staff` directory exists; `layout.html:41-59` renders a third owner entry; `layout.html:61-89` has no Calendar entry; `layout.html:96-109` emits literal language names and `layout.html:44-84` emits literal English titles; `messages_en.properties` is empty and non-English bundles contain translated feature values rather than the specified identical English placeholders.
- Test evidence: `NavBarRenderingTests.java:65-97` explicitly expects the extra owner/staff schedule links; `I18nPropertiesSyncTest.java:64-78` scans only element text, and `I18nPropertiesSyncTest.java:116-121` excludes the English bundle.
- Why wrong: the owner and staff landing targets render no page, the menus contradict the ACs, no urgent banner can be shown, and the shipped resources violate both halves of AC-140.
- Owning tasks: task-1.4, with missing web endpoints owned by task-1.6/task-1.7.
- Resolution: `REVISE: task-1.4 - Add the declared owner/staff templates on the stock layout, correct both role menus, add the configured urgent banner, synchronize all 11 bundles exactly as specified, remove emitted literals, and replace the scanner with one covering text, attributes, inline expressions, and Java-produced messages.`

#### C-4 — Time is not sourced from one configured clinic time zone

- Spec reference: AC-108: “The system shall operate in one configured time zone, defaulting to `Europe/Amsterdam`.”
- Code evidence: `ClockConfig.java:33-39` hardcodes the zone instead of reading the clinic configuration; `VisitController.java:85-100` and `PetController.java:115,158` call `LocalDate.now()` directly.
- Test evidence: `TestClockConfig.java:35-45` pins only consumers that inject `Clock`; no test detects direct system-clock use or verifies the configured zone is the single source.
- Why wrong: changing the clinic time zone cannot change the clock, and stock flows modified/in scope can observe the machine zone.
- Owning task: task-1.5.
- Resolution: `REVISE: task-1.5 - Wire Clock from the configured clinic zone, remove direct now calls from in-scope flows, and add a structural/behavioral test proving the configured zone and pinned horizon are used everywhere.`

#### C-5 — Owner request initiation and selectable Ollama/stub interpretation are missing

- Spec references:
  - AC-20: “When an owner submits the start-request form for one of their pets that has no active request, providing reason text and availability text, the system shall create a request in Awaiting consent.”
  - AC-21: “While an owner is on the start-request form, the system shall offer in the pet selector only that owner's pets that have no active request.”
  - AC-137: “The automated tests shall use the `stub` deterministic `RequestInterpreter` with a synchronous executor and shall never call the live model.”
- Code evidence: production contains only `RequestInterpreter.java` and `StubRequestInterpreter.java`; there is no Ollama adapter, provider-selection configuration, executor, consent-to-interpreter orchestration, owner request controller, or pet-selector query. `RequestLifecycleService.java:50-82` is only a direct domain service entry point and a constraint race would not be translated into a refusal.
- Test evidence: `InterpretationFidelityTests.java:106-113,156-168` constructs the stub and invokes services directly; `SchedulingLifecycleE2eTests.java:95-125` manually constructs and saves the interpretation instead of exercising an interpreter.
- Why wrong: UC-1 cannot start through the application, consent never sends text to either provider, deterministic provider wiring is untested, and the pet selector does not exist.
- Owning task: task-1.6.
- Resolution: `REVISE: task-1.6 - Implement the owner start/consent path and pet selector, Ollama and stub adapters selected by configuration, deterministic synchronous test wiring, and constraint-race translation to a user refusal.`

#### C-6 — The suggestion used by the flow does not rank with Timefold or normative availability

- Spec reference: AC-58: “When an owner confirms the interpretation and feasible slots exist, the system shall enumerate feasible slots, rank them with Timefold, offer the top slot, move the request to Suggestion offered, and record the hold (held veterinarian, start, duration) on the request row.”
- Code evidence: `DefaultSlotRanker.java:51-90` hand-generates slots for seven days using fabricated weekday 08:30–17:30/Saturday hours and returns them in iteration order; it never calls Timefold or reads the seeded schedules. `AppointmentAssignment.java:62-66` models two planning variables rather than one slot variable. `SuggestionService.java:80-108` consumes this hand-written ranker.
- Test evidence: `InterpretationFidelityTests.java:170-195` asserts only state and non-null hold fields. `AppointmentConstraintProviderTests.java:39-318` tests a disconnected constraint provider with an AC numbering/model that does not prove the production path uses it.
- Why wrong: the happy path can offer a slot outside Clinic A hours or a veterinarian's working blocks, and Timefold does not participate in the result.
- Owning task: task-1.6.
- Resolution: `REVISE: task-1.6 - Make SlotRanker enumerate from migrated clinic/vet availability and invoke the Timefold single-slot model inside the locked transaction; add a production-path test that fails if Timefold or the normative availability checks are bypassed.`

#### C-7 — The claimed UC-1 lifecycle cannot traverse HTTP

- Spec reference: AC-138: “The test suite shall include at least one MockMvc test with the real filter chain that, interleaving an owner session and a staff session with CSRF, drives request → consent → interpretation → suggestion → ask again → staff hand-off → staff suggestion → accept → completed visit, plus a declined-consent request booked by staff and closed as no-show.”
- Code evidence: the full request-mapping inventory contains only stock owner/vet routes plus `/`, `/login`, and `/403`; there are no mapped feature endpoints.
- Test evidence: `SchedulingLifecycleE2eTests.java:53-149` is `@SpringBootTest` with autowired repositories/services, not MockMvc; lines 95–149 call those collaborators directly and include no authentication or CSRF.
- Why wrong: UC-1 fails at its first browser step, and the evidence crosses none of the HTTP/security/view boundaries named by the AC.
- Owning task: task-1.7 (and prerequisite missing endpoints in task-1.6).
- Resolution: `REVISE: task-1.7 - Replace the service-level scenario with the required MockMvc lifecycle through real routes, filter chain, owner/staff sessions, CSRF, rendered views, and persisted state assertions after every named step.`

### Gaps

#### G-1 — Normative seed tests are not by-value and closed-set proof

- Spec reference: RULE-42: “MUST seed in `V4` exactly the rows below — no more, no fewer — and a migration test on a fresh DB MUST assert each row by value and verify each password with the application's PasswordEncoder.”
- Code evidence: `V4__scheduling_seed.sql:1-57` contains the expected current values; converge independently migrated V1–V4 into fresh H2 and queried 12 users, 7 opening rows, 3 parts of day, 1 config, 17 blocks, 6 exceptions, and zero leave/closures, all matching the normative tables.
- Test evidence: `SchemaValidationTest.java:146-150` checks only the vet-block count; lines 193–204 omit all part-of-day values and use only non-null for the phone; lines 71–106 do not assert linked owner names.
- Why insufficient: materially different seed rows can keep all current tests green. The actual snapshot is correct, but RULE-42 requires durable by-value evidence.
- Owning task: task-1.2.
- Resolution: `REVISE: task-1.2 - Replace the substitute with the declared fresh-Flyway SeedMigrationTests and assert every normative field plus closed-set counts, owner names, all unavailable flags, all parts of day, and the exact placeholder phone.`

#### G-2 — Authentication and negative authorization evidence is status-only

- Spec references:
  - AC-2: “If an anonymous user requests any protected URL anywhere in the application, then the system shall disclose no protected data in the response body and shall not perform any database change before redirecting.”
  - AC-9: “If an owner requests any page or action anywhere in the application — including pre-existing pages — that displays or modifies staff-only data, then the system shall deny it, shall disclose no staff data, and shall not perform any database change.”
- Code evidence: `SecurityConfiguration.java:61-73` applies the matchers, but matcher presence alone cannot prove no controller/service mutation or disclosure.
- Test evidence: `SecurityMatrixTests.java:63-158` asserts only status/location; `FormLoginTests.java:53-80` does not assert role-bearing or absent sessions.
- Why insufficient: production code could disclose a body, mutate before denial, or create a session and still pass.
- Owning task: task-1.3.
- Resolution: `REVISE: task-1.3 - Use the declared focused web slice with the real filter chain and mocked collaborators; enumerate every mapped method/path and assert denial, protected-data absence, never mutation, and session presence/absence with the expected role.`

#### G-3 — Lifecycle refusal evidence does not prove every disallowed cell and side effect

- Spec references:
  - AC-123: “If any request-lifecycle action is attempted while the request is not in a state whose transition table lists that action, then the system shall refuse it itself and shall not change the request's state or produce any side effect, regardless of whether the UI hides it.”
  - AC-124: “If any appointment-lifecycle action is attempted while the appointment is not in a state whose transition table lists that action, then the system shall refuse it and shall not change the appointment's state or produce any side effect.”
- Code evidence: `RequestLifecycleService.java:85-246` exposes more than twenty lifecycle actions across eight states.
- Test evidence: `RequestLifecycleRefusalTests.java:97-145` samples six invalid actions in only Awaiting consent and Interpreting; lines 147–173 sample seven actions in terminal states and never traverse all other disallowed cells. `AppointmentLifecycleRefusalTests.java:88-103` checks state/change rows after refused completion/no-show but not the absence of a `visits` side effect.
- Why insufficient: a missing request guard in an untested action/state cell remains green, and disallowed appointment completion could create a visit without failing the current assertion.
- Owning task: task-1.5.
- Resolution: `REVISE: task-1.5 - Generate the complete request action-by-state refusal matrix and assert named exception, unchanged persisted state/hold, and no event row for every disallowed cell; for appointments also assert no visit and no appointment-change row on refusal.`

#### G-4 — Interpretation round-trip compares a count, not every produced field

- Spec reference: AC-53: “When the interpreter yields an interpretation, the system shall persist and read it back such that all three window lists, care type, specialty, duration, preferred veterinarian, the raw model response, the model tag, and the prompt version are field-for-field identical to the produced values.”
- Code evidence: `Interpretation.java` and `InterpretationWindow.java` map the relevant fields.
- Test evidence: `InterpretationFidelityTests.java:129-154` persists window fields but asserts only the total window count; the stub result has no preferred veterinarian, so that association is not exercised.
- Why insufficient: window kind/date/range/weekday/time/tokens or preferred vet can be dropped or changed without a failure.
- Owning task: task-1.6.
- Resolution: `REVISE: task-1.6 - Produce preferred, allowed, and excluded windows plus a preferred vet, clear the persistence context, reload, and compare every scalar and every window field by value.`

#### G-5 — Accept success does not prove lock-first overlap re-validation

- Spec reference: AC-63: “When an owner accepts a suggestion, the system shall re-validate the hold inside a transaction that first takes `SELECT … FOR UPDATE` on the veterinarian row and re-checks overlaps, and if the hold is valid it shall confirm the appointment and move the request to Accepted.”
- Code evidence: `SuggestionService.java:111-159` contains a pessimistic `EntityManager.find` and overlap check.
- Test evidence: `InterpretationFidelityTests.java:185-195` asserts only the resulting appointment and request state.
- Why insufficient: removing or moving the lock and removing the overlap query leaves the test green.
- Owning task: task-1.6.
- Resolution: `REVISE: task-1.6 - Add focused evidence that observes lock-before-query ordering and a conflicting hold/appointment re-check inside the transaction, not only the success result.`

#### G-6 — The single random-port smoke test proves only the public login page

- Spec reference: AC-139: “The test suite shall include exactly one random-port smoke test that loads the login page and one authenticated page to prove wiring.”
- Code evidence: `SmokeTests.java:34-48` is the single random-port test class.
- Test evidence: `SmokeTests.java:43-48` sends only `GET /login`.
- Why insufficient: authentication, session handling, secured routing, and a rendered authenticated page can all be broken while the test passes.
- Owning task: task-1.7.
- Resolution: `REVISE: task-1.7 - Keep exactly one random-port smoke test, authenticate with a seeded account, retain the session/CSRF as required, and load one real authenticated page in addition to /login.`

### Drift (Δ candidates)

None.

### Cosmetic

None.

## Category Notes

1. Task Closure — FAIL (C-1..C-7, G-1..G-6). task-1.1 cleanup/dependencies exist but its guard is unsound; task-1.2 lacks `migration/SeedMigrationTests`; task-1.3 lacks the ownership guard and declared web-slice test; task-1.4 lacks all declared my/staff templates and `LocalizationKeyTests`; task-1.5 has guards/clock but incomplete request-matrix evidence; task-1.6 lacks Ollama/web initiation and a Timefold-backed production ranker; task-1.7's files exist but do not perform their declared boundaries.
2. AC Evidence Audit — FAIL. Ledger covers all 43 phase-1 ACs; only AC-126, AC-127, AC-132, AC-135, and AC-136 have STRONG evidence.
3. Normative Data by Value — IMPLEMENTATION SNAPSHOT PASS, TEST CONTRACT FAIL (G-1). Fresh H2 query after V1–V4 matched every normative row and all password assertions passed; durable tests remain weak.
4. Lifecycle and State Guards — FAIL (G-3, C-7). Appointment action/state cells are broadly covered but the no-visit side effect is not; request refusal coverage is partial; UC-1 cannot be walked through controllers because none exist.
5. Fidelity and Round-trips — FAIL (G-4). Scalar fields are partly asserted, window values and preferred vet are not.
6. Security Surface Walk — FAIL (C-2, G-2). Actual mapped surface: `GET /`, `GET /login`, all-method `/403`, `GET /owners/new`, `POST /owners/new`, `GET /owners/find`, `GET /owners`, `GET /owners/{ownerId}`, `GET/POST /owners/{ownerId}/edit`, `GET/POST /owners/{ownerId}/pets/new`, `GET/POST /owners/{ownerId}/pets/{petId}/edit`, `GET/POST /owners/{ownerId}/pets/{petId}/visits/new`, `GET /vets.html`, `GET /vets`, plus filter/static/system routes `/login` POST, `/logout` POST, `/error`, `/resources/**`, `/webjars/**`, CSS/favicon and actuator endpoints. No `/my/**`, `/staff/**`, or scheduling feature mappings exist; `/oups` is absent although the matrix says it is pre-existing.
7. Loaded Words — FAIL through cited findings. “Every/any URL” is defined by the Security Surface but `/403` is omitted; “exactly/no other” seed values are defined by the normative tables and the snapshot matches while tests do not; “every new page” and “all three window lists” are defined but not delivered/proven.
8. Presentation, Navigation and Localization — FAIL (C-3, C-7). No feature pages exist, role menus are non-conformant, literals are emitted, and the walkthrough stops at the first request route.
9. Test Hygiene — FAIL on evidence quality (G-1..G-6) but PASS on isolation. Full suite has no skips, no introduced `@Disabled` execution, no live network/model calls observed, and leaves the tree clean. Several names/Javadocs cite wrong ACs, including `AppointmentConstraintProviderTests.java:35-38` and `InterpretationPersistenceTests.java:44-47`.
10. Constraint Conformance — FAIL. RULE-3/4/5/6/7/8/14/16 structurally conform in the reviewed scope; RULE-1 is incomplete because no scheduling MVC controllers exist; RULE-2 (C-1), RULE-9/10/11/17/25/26 (C-5, C-6, G-4, G-5), RULE-12/13 (C-2, G-2), RULE-15 (G-3), RULE-37 (C-4), RULE-38/39/41 (C-3), RULE-42 (G-1), and RULE-44 (C-5, C-7, G-2, G-3, G-6) do not conform.

## Walkthrough Script

The phase's only use-case script is copied from UC-1. It was not user-confirmed and cannot be executed because the first
feature endpoint is absent.

- UC-1, owner: (1) start a request with reason and availability for a pet; must see Awaiting consent. (2) review and grant explicit consent; must see Interpreting. (3) system interprets asynchronously; must see the waiting page. (4) see the persisted read-only interpretation. (5) confirm it. (6) see exactly one held, ranked slot. (7) accept. (8) see a Confirmed appointment under My appointments.
- Phase-1 presentation checks while walking UC-1: stock layout; owner menu contains only My pets and My appointments; username and Logout on every page; configured urgent-care banner on every scheduling/detail page; owner receives 403 for `/owners/**`, `/vets*`, `/staff/**`, `/oups`, `/actuator/**`; another owner's ids are indistinguishable 404s.
- Staff checkpoint check: sign in as `admin`; land on Scheduling queue; see stock entries plus Scheduling queue, Calendar, and Clinic settings.

## Spec Reconciliation

- Δ folded in: none.
- F-n open: cp-1/C-1, cp-1/C-2, cp-1/C-3, cp-1/C-4, cp-1/C-5, cp-1/C-6, cp-1/C-7, cp-1/G-1, cp-1/G-2, cp-1/G-3, cp-1/G-4, cp-1/G-5, cp-1/G-6.
- Spec weaknesses exposed:
  - RULE-2 literally forbids Timefold imports outside the `SlotRanker` adapter classes while RULE-26 requires Timefold-annotated planning entity/solution/provider support classes. Recommended decision: treat an exact set of private solver implementation types as part of the adapter boundary, never a substring/name wildcard. Upstream: rules.
  - Security Surface says every route is listed but omits the explicit `/403` controller needed by the current access-denied forward and simultaneously limits `permitAll` to login/error/static. Upstream: rules.
  - phase-1 claims AC-138 in full coverage while task-1.7 explicitly implements only thin UC-1 and defers the AC's mandatory interleaved flow to phase-5. Upstream: tasks/criteria.
  - phase-1's artifact map assigns no controller artifact for the required owner/staff HTTP skeleton, and `/oups` is called pre-existing although it was removed before phase execution. Upstream: tasks/review.

## Resolution

No remediation phase was appended because every missing artifact or assertion already belongs to task-1.1 through
task-1.7. Apply these revisions in dependency order, rerun the full suite, and reconverge cp-1:

1. `REVISE: task-1.1 - Resolve cp-1/C-1 with an exact adapter-support boundary and a biting ArchUnit fixture; record the RULE-2/RULE-26 decision before closing the task.`
2. `REVISE: task-1.2 - Resolve cp-1/G-1 with a fresh-Flyway, fully by-value and closed-set seed test.`
3. `REVISE: task-1.3 - Resolve cp-1/C-2 and cp-1/G-2 with the exact whole-route security matrix, closed roles, service ownership guard, and strong negative assertions.`
4. `REVISE: task-1.4 - Resolve cp-1/C-3 with the missing stock-layout pages, exact role menus, urgent banner, all-bundle English placeholders, and complete no-literal verification.`
5. `REVISE: task-1.5 - Resolve cp-1/C-4 and cp-1/G-3 with one configured Clock source, the complete request action-by-state refusal matrix, and no-visit/no-change evidence for refused appointment completion.`
6. `REVISE: task-1.6 - Resolve cp-1/C-5, cp-1/C-6, cp-1/G-4, and cp-1/G-5 with real request/interpreter wiring, Timefold-backed availability ranking, full fidelity, and observable lock/re-validation evidence.`
7. `REVISE: task-1.7 - Resolve cp-1/C-7 and cp-1/G-6 with the real HTTP/MockMvc UC-1 leg and one authenticated random-port smoke request.`

Only the first directive is issued to the executor now; the remaining ordered directives are already recorded for the
subsequent revision loop.

## Approval response

REVISE: task-1.1 - Resolve cp-1/C-1 with an exact adapter-support boundary and a biting ArchUnit fixture; record the RULE-2/RULE-26 decision before closing the task.
