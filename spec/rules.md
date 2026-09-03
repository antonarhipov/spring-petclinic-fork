# Technical Design and Constraints: Smart Appointment Scheduling

## Overview

Add smart appointment scheduling to the Spring PetClinic web app as a POC: authenticated owners describe a reason and
availability in free text, an Ollama-backed LLM (Spring AI) turns it into a persisted, owner-reviewed interpretation,
Timefold ranks feasible slots, and the system guides the owner one held suggestion at a time; staff work the full
calendar and a fallback queue. All access is role-scoped and owner-isolated across the whole application.

Tech stack (pinned): Java 21; Spring Boot 4.1.0 (`spring-boot-starter-parent`); Spring MVC + Thymeleaf; Spring Data JPA
(Hibernate); H2 (file at runtime, in-memory for tests); Spring Security (form login); Flyway; Spring AI 2.0.1
(`spring-ai-starter-model-ollama`, model tag default `gemma4:latest`); Timefold 2.5.0
(`timefold-solver-spring-boot-starter`); ArchUnit (test scope); 11 message bundles under `messages/`.

Source of truth: [spec/spec.md](spec.md) and [spec/criteria.md](criteria.md) (AC-1 … AC-141). Where a technical choice
was open, it was resolved with the stakeholder (see *External Dependencies* and the architecture decision recorded in
RULE-1/RULE-2/RULE-3).

## Design

Components (all under `org.springframework.samples.petclinic`, package-by-feature beside stock `owner`/`vet`):
- `security` — `SecurityConfig` (`SecurityFilterChain`, bcrypt), `User`/role model, role-based landing.
- `scheduling.request` — `SchedulingRequest` aggregate + lifecycle service (state guard), event log.
- `scheduling.interpretation` — `RequestInterpreter` interface with `ollama`/`stub` adapters, structured-output record,
  async executor, versioned `Interpretation` + windows.
- `scheduling.solver` — `SlotRanker` interface over a Timefold single-entity model; slot enumeration + hard/soft
  constraints.
- `scheduling.appointment` — `Appointment` aggregate + lifecycle service, `appointment_change` audit, visit creation.
- `scheduling.clinic` — clinic settings, opening hours, parts-of-day, vet weekly schedule / exceptions / leave /
  closures, effective-blocks calculation, injectable `Clock`.
- `scheduling.web` — MVC `@Controller`s + Thymeleaf views for `/my/**` (owner) and `/staff/**` (queue, calendar,
  settings, availability).

Boundaries: no REST/JSON endpoints and no JavaScript; the browser drives everything through server-rendered forms and a
meta-refresh polling page. Library types (`ai.timefold…`, `org.springframework.ai…`) never leak past the `SlotRanker` /
`RequestInterpreter` adapters. Owner scoping is enforced twice (URL matcher + service guard).

Flow: owner form → `Awaiting consent` → consent → async LLM interpretation → owner review → confirm → synchronous
Timefold ranking inside a per-vet-locked transaction → one held slot → accept/reject loop → `Accepted` +
`Confirmed` appointment → staff lifecycle → completion creates a `visits` row.

Key dependencies to add: `spring-boot-starter-security`, `flyway-core`, `spring-ai-starter-model-ollama` (+ Spring AI
BOM), `timefold-solver-spring-boot-starter`, `com.tngtech.archunit:archunit-junit5` (test). Ecosystem options
considered and declined: Liquibase (Flyway chosen), Spring Modulith / DDD module facades (stock layout kept), a
full-schedule Timefold optimization (single-request ranker kept), JS polling + JSON status endpoint (meta-refresh
kept).

## Codebase Alignment

Stock PetClinic conventions are inherited and MUST be followed: package-by-feature (`owner`, `vet`, `system`, `model`),
public JPA `@Entity` classes named by domain (`Owner`, `Vet`, `Pet`, `Visit`) extending `BaseEntity`, Spring Data JPA
repositories, Spring MVC `@Controller`s returning Thymeleaf view names, and the presentation stack: the layout fragment
`templates/fragments/layout.html` (which defines the `layout(template, menu)` + `menuItem` fragments and the navbar),
the form fragments `templates/fragments/inputField.html` and `selectField.html`, the compiled `resources/css/
petclinic.css` (built from `src/main/scss`), and the 11 `messages/messages*.properties` bundles
(`spring.messages.basename=messages/messages`). Code style is enforced by `spring-javaformat`, `checkstyle` and
`nohttp` at `validate`; `spring.jpa.open-in-view=false` stays. There is **no** existing security configuration and
**no** Flyway yet; both are introduced by this feature. Deviation from the bundled `spring-boot` skill's DDD layout
(`domain/`/`api/` sub-packages, `*Entity` naming, non-public entities, module facades, Spring Modulith) is deliberate
and recorded in RULE-1/RULE-3; the skill's boundary intent is preserved instead via ArchUnit (RULE-2).

## Security Surface

`permitAll` rows are explicit; every route in the codebase (pre-existing and new) is listed. Denial semantics (RULE-13):
anonymous → 302 to `/login`; authenticated-but-wrong-role → 403 page; other-owner resource id → 404 (identical to a
missing id). CSRF is enabled for all state-changing requests.

| Route (pattern) | Pre-existing? | Access | Rule |
|---|---|---|---|
| `/login` (GET, POST), `/error` | GET pre-existing / new | public (`permitAll`) | RULE-12 |
| `/resources/**`, `/webjars/**`, `/*.css`, favicon (static resources) | yes | public (`permitAll`) | RULE-12 |
| `/logout` (POST) | new | authenticated (any role) | RULE-12 |
| `/` | yes | authenticated; redirect by role (owner→`/my/appointments`, staff→`/staff/queue`) | RULE-12, RULE-39 |
| `/oups` | yes | staff only | RULE-12 |
| `/owners/**` (`/owners/new`, `/owners/find`, `/owners`, `/owners/{id}`, `/owners/{id}/edit`, `/owners/{id}/pets/**`, `/owners/{id}/pets/{petId}/visits/new`) | yes | staff only | RULE-12 |
| `/vets`, `/vets.html` | yes | staff only | RULE-12 |
| `/staff/**` (queue, calendar, clinic settings, vet availability) | no | staff only | RULE-12 |
| `/my/**` (`/my/pets`, `/my/appointments`, `/my/requests/**`) | no | owner only; owner id from session, never from URL | RULE-12, RULE-13 |
| `/actuator/**` | yes (exposed `*`) | staff only | RULE-12 |

## Testing Strategy

Frameworks are inherited (JUnit 5, Spring Boot test slices, Mockito, AssertJ, `MockMvcTester`); the rules below fix
what the executor otherwise narrows. Full normative content is in RULE-44 (isolation, double contract) and the tables
here.

| AC pattern | Test level | Fixture / datasource | Assertion shape |
|---|---|---|---|
| Negative authz (AC-2, AC-8→AC-9, AC-11→AC-12, AC-118, AC-123, AC-124) | web slice with the **real** `SecurityFilterChain` over the whole URL space, + service test | mocked services for the web slice; state set up (not stubbed) for service refusal | denial status (302/403/404) **and** response body excludes the protected data **and** `never()` on the mutating collaborator / unchanged persisted state |
| State refusal (AC-57, AC-111, AC-117, AC-123, AC-124) | service | request/appointment persisted in the disallowed state on the isolated DB | named exception (`IllegalRequestTransitionException` / `IllegalAppointmentTransitionException`) **and** `never()` save **and** no event row |
| Lifecycle end-to-end (AC-138) | HTTP via `MockMvcTester` with the real filter chain, CSRF, interleaved owner+staff sessions | isolated in-memory H2 (same Flyway), pinned `Clock`, `stub` interpreter, synchronous executor | one test per UC main scenario (steps copied below) + one leg per state-changing extension; request/appointment state asserted after every step |
| Data exactness (AC-125→AC-134) | migration test on a fresh in-memory H2 | Flyway `V1..V4` only | every seeded row asserted **by value** (not count); each password verified with the `PasswordEncoder`; no extra rows |
| Fidelity (AC-53) | round-trip persistence test | isolated DB | every field (3 window lists, careType, specialty, duration, preferred vet, urgency, raw response, model tag, prompt version) equal to the produced value |
| Boundary (AC-42/43/44, AC-50/51/52, AC-104/105/106, AC-111/112/113) | unit / service | fixtures + pinned `Clock` | within / at-boundary / beyond, all three asserted |
| Concurrency (AC-22, AC-65, AC-141) | integration on real DB | two simultaneous transactions | at most one succeeds; the loser is refused with no second active request / no double booking |
| Smoke (AC-139) | one `@SpringBootTest(webEnvironment=RANDOM_PORT)` | full context | login page loads + exactly one authenticated page reachable |
| Localization (AC-140) | automated resource/AST check | all templates + `MessageSource` usages; feature-touched files enumerated in the test | every referenced key exists in **all** 11 bundles; no English literal in feature-introduced/modified templates or Java |

### End-to-end scenarios

Steps copied from [spec.md](spec.md) §Use cases. The single mandatory MockMvc lifecycle test (AC-138) MUST cover UC-1
main + UC-2 + UC-4 hand-off + UC-5 completion, plus the declined-consent→staff-book→no-show leg; the other rows below
are required legs.

| UC | Test name (suggested) | Steps (copied from spec.md §Use cases) | Actors |
|---|---|---|---|
| UC-1 | `SchedulingLifecycleE2eTests.ownerGuidedFlow` | 1. Owner starts a request, entering reason and availability text for a pet. 2. Owner reviews the request and grants explicit consent to interpret it. 3. System interprets the text asynchronously and shows a waiting page. 4. System presents the persisted interpretation for read-only review. 5. Owner confirms the interpretation. 6. System holds and offers exactly one ranked slot. 7. Owner accepts the suggestion. 8. System confirms the appointment and shows it under My appointments. (+ ext 1a emergency, 3a decline, 5a OTHER→staff, 6a no slots→staff, 8b concurrent confirm) | owner, staff |
| UC-2 | `SchedulingLifecycleE2eTests.ownerAsksForAnotherOption` | 1. Owner rejects the current suggestion and picks a scope chip. 2. System releases the hold, records the exclusion, and offers the next slot. (+ ext 2a exhausted→staff, 3a invalid hold on reopen) | owner |
| UC-3 | `SchedulingLifecycleE2eTests.ownerRephrases` | 1. Owner edits the reason/availability text, clearing prior rejections. 2. Owner grants fresh consent; System re-interprets and shows the review. (+ ext 1a model unavailable→staff, 1b not usable, 2a third-attempt recommendation, 3a route to staff) | owner |
| UC-4 | `SchedulingLifecycleE2eTests.staffResolveQueuedRequest` | 1. Staff open the request from the Needs staff queue and review its timeline. 2. Staff create or complete the structured interpretation (new STAFF version). 3. Staff place a suggestion via calendar pick or by running the solver. 4. Owner accepts or rejects the staff-placed suggestion like any other. (+ ext 2a clear emergency, 3a book+attach, 3b book+leave open) | staff, owner |
| UC-5 | `SchedulingLifecycleE2eTests.staffManageAppointment` | 1. Staff open the appointment on the day-view calendar. 2. Staff reschedule or cancel it before start, recording a reason. 3. After the start time passes, staff mark it completed or no-show. 4. On completion System creates a visit prefilled and editable. (+ ext 5a availability edit conflicts confirmed→refused, 5b conflicts only holds→invalidated) | staff |
| UC-6 | `SchedulingLifecycleE2eTests.ownerCancelsUpcoming` | 1. Owner opens My appointments and cancels an upcoming appointment. 2. System marks it Cancelled by owner and keeps it under past items. (+ ext 1a cancel past/no-show refused, 1b other-owner→404) | owner |

## Rules

### RULE-1
**Covers:** project-wide
**MUST** place all new code in package-by-feature packages under `org.springframework.samples.petclinic` (`security`,
`scheduling.request`, `scheduling.interpretation`, `scheduling.solver`, `scheduling.appointment`, `scheduling.clinic`,
`scheduling.web`), model persistence as **public** JPA `@Entity` classes extending `BaseEntity`/`NamedEntity` named by
domain (e.g. `SchedulingRequest`, `Appointment`, `Interpretation`, `User`), expose behaviour through Spring MVC
`@Controller`s returning Thymeleaf view names, and access data through Spring Data JPA repositories.
**Reason:** The feature extends stock PetClinic and must stay uniform with `owner`/`vet`; the stakeholder chose the
stock layout over the bundled skill's DDD layout (see External Dependencies / architecture decision).

### RULE-2
**Covers:** AC-13, project-wide
**MUST** add `archunit-junit5` (test scope) and ArchUnit tests that fail the build when: (a) any type under
`scheduling.solver`/`scheduling.interpretation` domain or service packages imports `ai.timefold..` or
`org.springframework.ai..` outside the `SlotRanker`/`RequestInterpreter` adapter classes; (b) any `@Controller` calls a
`*Repository` directly instead of a `@Service`; (c) any service or domain type under `scheduling..` imports
`jakarta.servlet..` or `org.springframework.web..`.
**Reason:** Preserves the skill's boundary intent (framework isolation, service-mediated access) without adopting its
package layout, and gives the double-enforcement of AC-13 an automated structural check.

### RULE-3
**Covers:** project-wide
**MUST NOT** introduce Spring Modulith, rename entities to `*Entity`, make entities non-public, or add per-module
`*API` facade classes.
**Reason:** Surveyed as canonical modularity options; declined for a single-feature POC to keep consistency with stock
PetClinic. Reconsider only if the app grows into multiple bounded contexts.

### RULE-4
**Covers:** project-wide
**MUST** use Spring Data JPA (Hibernate, snake-case physical naming, `open-in-view=false`) for every new persistent
type; **MUST NOT** hand-roll DAOs or use `JdbcTemplate` except for the pessimistic-lock query of RULE-10 where an
explicit `SELECT … FOR UPDATE` is clearer as a locked repository query.
**Reason:** Matches the stock persistence approach; keeps one ORM in play.

### RULE-5
**Covers:** AC-135, AC-125…AC-134 (migration mechanism)
**MUST** add Flyway with migrations `V1__stock_schema`, `V2__stock_data`, `V3__scheduling_schema`, `V4__scheduling_seed`
under `db/migration`, targeting **H2 only**, and **MUST** disable stock init (`spring.sql.init.mode=never`, remove the
`schema.sql`/`data.sql` init properties). The identical migrations **MUST** run against the in-memory test H2.
**Reason:** Single, versioned, testable schema/seed path; the read-back and seed tests require the same schema at
runtime and in tests (RA-9, EA-2).

### RULE-6
**Covers:** project-wide
**MUST NOT** introduce Liquibase.
**Reason:** Surveyed as the alternative migration framework; Flyway chosen for its simpler single-file SQL model and no
XML. Recorded so the choice is not silently re-litigated.

### RULE-7
**Covers:** AC-53, AC-55, AC-56, AC-72, AC-114, AC-115, AC-119, AC-120, AC-121
**MUST** create exactly these scheduling tables in `V3`: `users`(username, password, role, nullable `owner_id`);
`scheduling_request`(pet, owner, state, reason_text, availability_text, urgent_owner, urgent_ai, active_pet_id nullable,
failed_attempts, held_vet_id nullable, held_start nullable, held_duration nullable, timestamps);
`scheduling_request_event`(request, from_state, to_state, actor, action, reason, payload, timestamp);
`interpretation`(request, version, provenance `AI|STAFF`, reason_summary, estimated_minutes, care_type, specialty,
preferred_vet_id nullable, urgent, cannot_interpret, raw_response CLOB, model_tag, prompt_version);
`interpretation_window`(interpretation, kind `PREFERRED|ALLOWED|EXCLUDED`, date/date-range/weekday, start_time,
end_time, tokens); `appointment`(pet, vet, start, duration, status, reason, nullable back-ref from `visits`);
`appointment_change`(appointment, actor, action, reason, timestamp); and clinic-config tables (opening hours per
weekday, parts-of-day, visit-duration bounds/default, booking horizon, emergency phone, time zone; vet weekly blocks,
vet exceptions, leave ranges, clinic closures). `visits` **MUST** gain a nullable `appointment_id`.
**Reason:** Normalises the interpretation for the fidelity test, keeps stock `visits` intact, and gives requests and
appointments their own audited lifecycles (RA-1..7, RA-39).

### RULE-8
**Covers:** AC-58, AC-59, AC-60
**MUST** represent the single hold as `held_vet_id`/`held_start`/`held_duration` fields on the `scheduling_request` row
(no `slot_hold` table, no `HELD` appointment status); calendar and overlap checks **MUST** union `appointment` rows
with requests whose hold fields are set; a hold **MUST** have no timer and no background expiry job.
**Reason:** "One hold per request" becomes structural and cannot drift (RA-3, RA-23).

### RULE-9
**Covers:** AC-22, AC-23, AC-141
**MUST** enforce "at most one active request per pet" with a nullable `active_pet_id` column set only while the request
is non-terminal plus a unique index on it (H2 treats NULLs as distinct); a second active-request creation **MUST** fail
on the constraint and be surfaced as a refusal, not a stack trace.
**Reason:** Overlapping-interval uniqueness is impossible as a DB constraint; a nullable unique column is the correct
H2-compatible guard (RA-6, Q13).

### RULE-10
**Covers:** AC-63, AC-65, AC-76, AC-141
**MUST** run every hold/confirm/book operation in a single `@Transactional` service method that **first** acquires a
pessimistic write lock on the veterinarian row (`@Lock(LockModeType.PESSIMISTIC_WRITE)` repository query =
`SELECT … FOR UPDATE`) and **then** re-checks overlaps against appointments and active holds before persisting; **MUST
NOT** rely on a unique constraint to prevent overlaps.
**Reason:** Serialises per vet so two owners can never confirm the same vet-and-time; overlap of varying durations can
only be validated in-transaction (RA-6, RA-33).

### RULE-11
**Covers:** AC-83, AC-67
**MUST** run the Timefold solve **synchronously** inside the locked transaction of RULE-10 with a 1-second termination
budget (`timefold.solver.solve.duration=1s`), placing all start times on the 15-minute grid; only the LLM call is
asynchronous.
**Reason:** The suggestion must be consistent with the slot just locked; the solve is fast enough to stay inline
(RA-32, RA-33).

### RULE-12
**Covers:** AC-1, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8, AC-125, AC-127
**MUST** add a single `SecurityFilterChain` with form login, CSRF enabled, a BCrypt `PasswordEncoder`, a role-based
authentication success handler (owner→`/my/appointments`, staff→`/staff/queue`, and `/` redirecting by role), and the
**exact** authorization matrix in §Security Surface — every route mapped, `permitAll` only for `/login`, `/error` and
static resources, `/actuator/**` staff-only. A wrong-role authenticated request **MUST** yield a 403 page; an anonymous
request to any protected URL **MUST** redirect to `/login`.
**Reason:** No security exists today; the whole URL space (stock routes included) must be locked down exactly, not just
the new prefixes (RA-45, RA-46, EA-3).

### RULE-13
**Covers:** AC-2, AC-9, AC-10, AC-11, AC-12, AC-13, AC-118
**MUST** enforce owner scoping in the service layer independently of the URL matchers: owner pages take the owner id
from the authenticated session (never the URL), and any access to a pet/request/appointment not owned by the caller
**MUST** return 404 identical to a genuinely missing id, disclosing nothing and making no database change. This guard
**MUST** hold even if the URL matcher is bypassed.
**Reason:** Double enforcement; other-owner ids must be indistinguishable from missing ones (RA-45, RA-46, Q28).

### RULE-14
**Covers:** AC-3, AC-125, AC-126
**MUST** model accounts in a `users` table with roles limited to exactly `owner` and `staff` and a nullable `owner_id`
FK linking owner accounts to `owners`; **MUST NOT** define any other role or authority.
**Reason:** The normative account set is closed; extra roles are a defect (EA-3, Normative data).

### RULE-15
**Covers:** AC-24, AC-25, AC-32, AC-54, AC-57, AC-61, AC-86, AC-87, AC-88, AC-89, AC-90, AC-93, AC-94, AC-122, AC-123
**MUST** implement the request lifecycle as a service-owned guard that refuses, with `IllegalRequestTransitionException`
and **no side effect and no event row**, every transition not listed in the table below, and **MUST NOT** rely on the
UI hiding an action. Actions marked *(system)* are performed by the application. "Staff book (attach/leave open)" are
available from every non-terminal, non-*Accepted*, non-*Abandoned* state.

| State | Allowed action | Resulting state |
|---|---|---|
| **Awaiting consent** | consent | Interpreting |
| | decline consent | With staff |
| | edit text | Awaiting consent |
| | abandon | Abandoned |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Awaiting consent |
| **Interpreting** | abandon | Abandoned |
| | *(system)* interpretation usable | Interpreted |
| | *(system)* interpretation failed / contradiction / `cannotInterpret` | Interpretation failed |
| | *(system)* model unavailable / timeout / malformed-after-retry | With staff |
| | *(system)* restart while in flight | Interpretation failed (reason "interrupted") |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Interpreting |
| **Interpretation failed** | edit text / rephrase (fresh consent) | Awaiting consent |
| | route to staff | With staff |
| | abandon | Abandoned |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Interpretation failed |
| **Interpreted** | confirm — feasible slots exist | Suggestion offered |
| | confirm — no feasible slots | With staff |
| | edit text | Awaiting consent |
| | route to staff | With staff |
| | abandon | Abandoned |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Interpreted |
| **Suggestion offered** | accept — hold valid | Accepted |
| | accept — hold invalid, next slot exists | Suggestion offered |
| | accept — hold invalid, none left | With staff |
| | ask for another option — slots remain | Suggestion offered |
| | ask for another option — exhausted | With staff |
| | *(system)* view re-validates — hold invalid, next exists | Suggestion offered |
| | *(system)* view re-validates — hold invalid, none left | With staff |
| | route to staff | With staff |
| | edit text | Awaiting consent |
| | abandon | Abandoned |
| | staff release hold | With staff |
| | staff place a suggestion | Suggestion offered |
| | staff book (attach) | Accepted |
| **With staff** | view status | With staff |
| | abandon | Abandoned |
| | staff create/edit interpretation (new STAFF version) | With staff |
| | staff clear emergency flag | With staff |
| | staff place a suggestion | Suggestion offered |
| | staff book directly (attach) | Accepted |
| **Accepted** | *(terminal for the request)* | Accepted |
| **Abandoned** | *(terminal)* | Abandoned |

**Reason:** The lifecycle is the feature's core invariant; every disallowed transition must be refused by the system
itself, verifiably (B-101, RA-20, RA-31).

### RULE-16
**Covers:** AC-109, AC-110, AC-111, AC-112, AC-113, AC-116, AC-117, AC-120, AC-124
**MUST** implement the appointment lifecycle as a service-owned guard that refuses, with
`IllegalAppointmentTransitionException` and **no side effect**, every transition not listed below; completion/no-show
**MUST** be refused before the scheduled start (allowed at or after it).

| State | Allowed action | Resulting state |
|---|---|---|
| **Confirmed** | owner cancel (before start) | Cancelled by owner |
| | staff cancel (before start, with reason) | Cancelled by staff |
| | staff reschedule (before start, with reason; mutated in place) | Confirmed |
| | staff mark completed (at/after start) | Completed (creates a visit) |
| | staff mark no-show (at/after start) | No-show |
| **Cancelled by owner** | *(terminal)* | Cancelled by owner |
| **Cancelled by staff** | *(terminal)* | Cancelled by staff |
| **Completed** | *(terminal)* | Completed |
| **No-show** | *(terminal)* | No-show |

**Reason:** Symmetric guard for the appointment; completion-timing is a boundary that must be checked in the service
(B-102, RA-43).

### RULE-17
**Covers:** AC-24, AC-34, AC-39, AC-45, AC-137
**MUST** hide the model behind a `RequestInterpreter` interface with two adapters selected by
`scheduling.ai.provider=ollama|stub` (default `ollama`): the `ollama` adapter uses `spring-ai-starter-model-ollama`
with structured output (`BeanOutputConverter`) into a Java record carrying exactly `reasonSummary`, `estimatedMinutes`,
`careType`(`GENERAL|SPECIALTY`), `specialty`(closed list or `OTHER:<text>`), `preferredVetId`, `urgent`,
`cannotInterpret`, and preferred/allowed/excluded window lists; the prompt **MUST** include current date/time and
horizon end from an injectable `Clock`, weekday opening hours, part-of-day tokens, veterinarians (id, name,
specialties) and offered specialties (radiology, surgery, dentistry). Client timeout is `scheduling.ai.timeout`
(default 60 s); malformed output is retried **once**, a timeout is **not** retried. Text **MUST** be sent only after
consent.
**Reason:** Keeps tests off the live model and pins the prompt/output contract the interpretation ACs validate (RA-14,
RA-18, RA-22).

### RULE-18
**Covers:** AC-26, AC-27, AC-28, AC-29, AC-30, AC-31
**MUST** run interpretation with Spring `@Async` on a dedicated **single-thread** executor, passing the request id, with
exactly one in-flight interpretation per request; the result **MUST** be applied in a fresh transaction that re-checks
the request state, **MUST** be discarded if the request was abandoned, and on application startup every request still
in *Interpreting* **MUST** be moved to *Interpretation failed* (reason "interrupted"). While *Interpreting* the only
owner action allowed is abandon.
**Reason:** A local Ollama serialises anyway; crash recovery and abandon-discard prevent orphaned state (RA-21, RA-20).

### RULE-19
**Covers:** AC-32, AC-33, AC-34, AC-35, AC-36, AC-37, AC-38, AC-61
**MUST** route interpretation outcomes as: usable (well-formed, allowed-universe-minus-excluded non-empty in horizon) →
*Interpreted*; contradictory windows or `cannotInterpret` → *Interpretation failed* (a counted failed attempt);
"model unavailable" (second malformed / transport error / timeout) → *With staff* with the reason recorded;
`OTHER:<text>` specialty no vet offers → *With staff*, never downgraded to general care; no feasible slots on
confirm/ask-again → *With staff*. From the **third** failed attempt onward the system **MUST** recommend routing to
staff while still allowing rephrasing (and **MUST NOT** recommend it before the third).
**Reason:** Deterministic, boundary-tested branching for every interpretation edge case (RA-15, RA-16, E-2..E-7).

### RULE-20
**Covers:** AC-40 (duration), AC-42, AC-43, AC-44
**MUST** clamp the estimated visit duration into the configured bounds (default 15–60): values in `[15,60]` unchanged,
below 15 → 15, above 60 → 60, and a missing estimate → the configured default (30).
**Reason:** Three-point boundary behaviour the ACs assert explicitly (RA-41).

### RULE-21
**Covers:** AC-40 (vet resolution), AC-41
**MUST** take the preferred veterinarian only as an id chosen by the model from the prompt list, validate that the id
exists, show the resolved name in the read-only review, and record `null` for unknown/ambiguous cases; **MUST NOT**
fuzzy-match veterinarian names in Java.
**Reason:** Avoids brittle name matching; wrong matches are caught by the owner on review (RA-15, EA-4).

### RULE-22
**Covers:** AC-46, AC-47, AC-48
**MUST** model a window as {weekday | specific date | date range} × {start–end time} with weekday windows recurring
across the whole horizon; when any preferred or allowed window is named, slots outside the union of preferred∪allowed
are **hard-excluded**; with no preferred and no allowed windows the whole horizon is allowed.
**Reason:** "Allowed defines the universe, preferred ranks within it" (RA-11).

### RULE-23
**Covers:** AC-49
**MUST** resolve each part-of-day token (`MORNING`/`AFTERNOON`/`EVENING`) per weekday against that day's opening hours
and drop a token whose resolved interval is empty.
**Reason:** Tokens are relative to daily opening hours (e.g. Monday EVENING is empty and dropped) (RA-13).

### RULE-24
**Covers:** AC-50, AC-51, AC-52
**MUST** treat the earliest bookable slot as the first 15-minute grid point at least 2 hours after the reference time
from the injectable `Clock`; a candidate starting less than 2 hours out **MUST** be excluded.
**Reason:** Deterministic lead time, testable against the pinned clock (RA-12, RA-48).

### RULE-25
**Covers:** AC-53, AC-54, AC-55, AC-56
**MUST** persist the complete interpretation (all three window lists, care type, specialty, duration, preferred vet,
urgency) plus raw model response, model tag and prompt version, such that a read-back is field-for-field identical;
interpretations **MUST** be versioned per request with provenance `AI`/`STAFF` (a STAFF version never overwrites the AI
version); the read-only owner review is verbatim and changed only by editing the text (fresh consent + re-interpret);
raw response, model tag and prompt version are visible to **staff only**.
**Reason:** Satisfies the fidelity round-trip and staff-editing without losing raw output; provenance is not
owner-facing (RA-2, RA-7, RA-35).

### RULE-26
**Covers:** AC-73, AC-74, AC-75, AC-76, AC-77, AC-78, AC-79, AC-80, AC-81, AC-82

This rule covers 10 ACs; per-AC validation:

| AC | Constraint fixed | How validated |
|---|---|---|
| AC-73 | single planning entity, one planning variable (slot) over enumerated feasible slots; hard constraints applied at enumeration **and** as Timefold hard constraints | solver unit test asserts model shape + a candidate rejected at both layers |
| AC-74 | hard: within clinic opening hours | slot outside hours excluded |
| AC-75 | hard: within one vet's continuous working block after exceptions/leave/closures | slot spanning a gap excluded |
| AC-76 | hard: no overlap with appointments or active holds | overlapping slot excluded |
| AC-77 | hard: required specialty present | vet without specialty excluded |
| AC-78 | hard: not inside owner's excluded windows | excluded-window slot excluded |
| AC-79 | hard: within preferred∪allowed union | outside-union slot excluded |
| AC-80 | hard: within booking horizon | beyond-horizon slot excluded |
| AC-81 | hard: no overlap with the same owner's other confirmed appointments/holds across all pets | overlapping owner slot excluded |
| AC-82 | soft `HardMediumSoft`: preferred window (medium) > preferred vet (soft, high weight) > earliest (soft, per-minute tie-breaker) | ranking test orders three candidates by the priority |

**MUST** hide Timefold behind a `SlotRanker` interface (`timefold-solver-spring-boot-starter`, auto-configured
`SolverManager`) so tests never touch the library.
**Reason:** The ranker is the matching core; every hard/soft constraint is an AC that must be individually checkable
(RA-32, RA-34).

### RULE-27
**Covers:** project-wide
**MUST NOT** implement a full-schedule or multi-request Timefold optimization.
**Reason:** Surveyed; the guided flow is best-of-N ranking for a single request. A global optimizer would add planning
complexity with no requirement behind it (RA-32, Q10).

### RULE-28
**Covers:** AC-66, AC-67, AC-68, AC-69, AC-70, AC-71, AC-72
**MUST** implement "ask for another option" as: reject current suggestion, release the hold, permanently exclude for the
current interpretation only (with no cap), and offer the next best slot; the scope chip fixes the exclusion — "not this
time" = this vet at this start, "not this day" = this calendar date for all vets, "not this vet" = this vet for the
whole horizon; each rejection **MUST** be recorded as a `scheduling_request_event`; editing the text **MUST** clear
rejections from application while retaining them in the event log; the request page **MUST** show a compact "You have
ruled out: …" list with no undo control.
**Reason:** Exact-slot exclusion with scope chips keeps one-suggestion-at-a-time meaningful on a 15-minute grid (RA-24,
RA-25, RA-26, RA-27).

### RULE-29
**Covers:** AC-84, AC-85, AC-86
**MUST** present the queue as two tabs: **Needs staff** (default) listing *With staff* requests with emergencies pinned
first then oldest first and each hand-off trigger shown; **All open** listing every non-terminal request with state,
held slot and age plus a release-hold action that moves the request to *With staff* with a reason and releases the hold.
**Reason:** Staff need both the actionable queue and oversight of stuck owner-flow requests (RA-36).

### RULE-30
**Covers:** AC-87, AC-88
**MUST** pin a request to the top of the staff queue and skip the automated loop when either the AI `urgent` flag or the
owner's "this is urgent" checkbox is set; staff clearing the flag **MUST** keep the request in *With staff* and **MUST
NOT** return it to the automated loop.
**Reason:** Emergencies must never wait in the automated loop and must not silently re-enter it (RA-17, E-16).

### RULE-31
**Covers:** AC-89, AC-90, AC-91, AC-55 (STAFF version)
**MUST** let staff create or edit a structured interpretation stored as a new `STAFF` version (required for a
declined-consent request that has none) and place a suggestion either by clicking a free calendar cell in picking mode
or by pressing *Suggest* to run the solver; both **MUST** create a hold and move the request to *Suggestion offered*
under the same rules (RULE-8/10/11), and an owner accept/reject of a staff-placed suggestion follows exactly the same
rules as any suggestion.
**Reason:** Staff fallback must reuse, not fork, the suggestion machinery (RA-35, RA-38).

### RULE-32
**Covers:** AC-92, AC-93, AC-94, AC-95
**MUST** let staff book directly for any pet at any time with no interpretation; when the pet has an open request in any
non-terminal state the booking form **MUST** offer *attach* (→ *Accepted*, hold released, event logged) or *leave open*
(request untouched); a directly booked appointment **MUST** appear under the owner's *My appointments* and be
cancellable by the owner.
**Reason:** Direct booking is the staff escape hatch and must integrate cleanly with the request lifecycle (RA-31).

### RULE-33
**Covers:** AC-96, AC-97, AC-98
**MUST** render the staff calendar as a server-side day view, one column per veterinarian, one row per 15-minute grid
step, cells coloured closed/off-shift/free/booked/held, with prev/next-day navigation and a date picker over a range
covering the horizon; clicking a free cell starts a direct booking (vet/start prefilled) or, in picking mode for a
queued request, places the hold; per vet per day it **MUST** show opening hours or closure, effective working blocks,
booked appointments, held slots and remaining free capacity.
**Reason:** "Full calendar" is defined concretely as these five layers, not a list of appointments (RA-37, RA-38).

### RULE-34
**Covers:** AC-100, AC-101, AC-102, AC-103, AC-107
**MUST** support per vet a recurring weekly schedule with split shifts, date exceptions (a date with zero or more
replacement blocks; zero = unavailable all day), leave (inclusive whole-day date range) and clinic-wide closures
(single date); effective blocks for a day = `closure ? none : leave ? none : exception ? its blocks : weekly blocks`,
all intersected with opening hours, precedence closure > leave > exception > weekly. An availability edit that conflicts
with a **confirmed** appointment **MUST** be refused with the conflict list (staff reschedule/cancel first); one that
conflicts **only** with holds **MUST** invalidate those holds and apply. Weekly blocks entered outside opening hours
**MUST** be accepted with a page warning and intersected at runtime.
**Reason:** The precedence rule and conflict semantics are exact and drive the solver's per-day capacity (RA-28, RA-39,
EA-5).

### RULE-35
**Covers:** AC-104, AC-105, AC-106, AC-87 (bounds scope)
**MUST** apply the booking horizon and the 15–60 duration bounds to **owners and the solver only**: an owner slot
strictly within or on the last horizon day is allowed, beyond it is excluded; staff may book any future date with any
grid-multiple duration that fits the vet's block, and past days are viewable but not bookable.
**Reason:** Horizon/bounds are owner-flow guards, not staff limits (RA-40, RA-41).

### RULE-36
**Covers:** AC-109, AC-110, AC-113, AC-114, AC-115, AC-116, AC-119, AC-120
**MUST** implement appointment management as: staff reschedule mutates the appointment in place and records the old time
in `appointment_change`; staff cancel sets `CANCELLED_BY_STAFF` with a reason; owner cancel (before start) sets
`CANCELLED_BY_OWNER`, keeps the row under past items and leaves the request closed; marking completed creates a `visits`
row linked by `appointment_id`, description prefilled from the owner's reason (or staff booking reason) and editable,
visit date = appointment date; every staff book/reschedule/cancel **MUST** write an `appointment_change` row shown per
appointment on the calendar; rescheduled/cancelled items **MUST** show owners a "changed by the clinic" marker with the
staff reason and original time.
**Reason:** Owner-facing reasons and the audit table are part of the contract; cancelled rows are retained, not deleted
(RA-4, RA-42, RA-44, EA-6).

### RULE-37
**Covers:** AC-99, AC-108, AC-45 (clock), AC-136
**MUST** expose an injectable `Clock` bean (runtime = system clock in the configured zone) used everywhere a
"now"/date/time is needed; operate in one configured time zone (default `Europe/Amsterdam`); and make clinic settings
(opening hours per weekday, duration bounds/default, booking horizon, parts of day, emergency phone, time zone)
staff-editable in the UI, seeded with the normative defaults.
**Reason:** A single injectable clock makes the whole flow deterministic under the pinned test clock (RA-12, RA-48).

### RULE-38
**Covers:** AC-19
**MUST** render the urgent-care banner from message keys with the configured `emergency_phone` on every owner
scheduling page and the request detail page.
**Reason:** Owners must always see what to do in an emergency; the phone is configurable (RA-8, ED-2).

### RULE-39
**Covers:** AC-14, AC-15, AC-16, AC-17, AC-18, AC-5, AC-6
**MUST** render every new page with the existing `fragments/layout.html`, navbar and form fragments and the stock
stylesheet (no second layout/stylesheet, no inline styling); show per role only its menu entries (owner: *My pets*,
*My appointments*; staff: the stock pages plus *Scheduling queue*, *Calendar*, *Clinic settings*), the signed-in
username and a *Logout* action on every page; land owners on *My appointments* and staff on *Scheduling queue*; keep
*My pets* read-only; and expose veterinarian names/specialties to owners only inside the scheduling request page.
**Reason:** Coherent look and role-scoped navigation are acceptance requirements, not cosmetics (RA-45, presentation
section).

### RULE-40
**Covers:** AC-28
**MUST** poll the request detail page with `<meta http-equiv="refresh" content="3">` only; **MUST NOT** add JavaScript
or a JSON status endpoint. The request detail URL is the single entry point and renders whatever state the request is
in.
**Reason:** Stock templates carry no custom JS; meta-refresh keeps the async flow within the server-rendered model
(RA-19).

### RULE-41
**Covers:** AC-140
**MUST** resolve every user-visible string introduced by the feature (page text, labels, placeholders, other
attributes, and Java-produced status/flash messages) through a message key present in **all 11** bundles
(`de,en,es,fa,hi,ja,ko,pt,ru,tr` + default), authored in English with identical English placeholder text in every
non-English bundle; **MUST NOT** emit an English literal directly from feature-introduced or feature-modified templates
or Java classes.
**Reason:** Localization is verified by an automated key-existence + no-literal check (RA-49, B-19/B-20).

### RULE-42
**Covers:** AC-125, AC-126, AC-127, AC-128, AC-129, AC-130, AC-131, AC-132, AC-133, AC-134
**MUST** seed in `V4` **exactly** the rows below — no more, no fewer — and a migration test on a fresh DB **MUST**
assert each row **by value** and verify each password with the application's `PasswordEncoder`.

Accounts (username / password / role / linked owner): george/george123/owner/George Franklin;
betty/betty123/owner/Betty Davis; eduardo/eduardo123/owner/Eduardo Rodriquez; harold/harold123/owner/Harold Davis;
peter/peter123/owner/Peter McTavish; jean/jean123/owner/Jean Coleman; jeff/jeff123/owner/Jeff Black;
maria/maria123/owner/Maria Escobito; david/david123/owner/David Schroeder; carlos/carlos123/owner/Carlos Estaban;
admin/admin123/staff/—; staff/staff123/staff/—. No other accounts or roles.

Clinic A opening hours: Mon 9:00–17:00, Tue 9:00–17:00, Wed 9:00–18:00, Thu 9:00–17:00, Fri 10:00–16:00, Sat closed,
Sun closed.

Vet weekly schedules (17 blocks): James Carter Mon 9–17, Tue 9–17, Wed 9–12, Fri 11–12; Helen Leary Mon 9–17, Tue 9–17,
Wed 9–12; Linda Douglas Mon 9–17, Tue 9–17, Wed 9–12; Rafael Ortega Thu 9–17, Fri 10–16; Henry Stevens Thu 9–17, Fri
10–16; Sharon Jenkins Mon 13–14, Thu 9–17, Fri 10–16. No other blocks.

Vet exceptions (all unavailable): James Carter 2026-09-15; Henry Stevens 2026-09-15, 2026-09-17, 2026-09-21,
2026-10-22; Sharon Jenkins 2026-10-22. No leave and no clinic closures seeded.

Config defaults: booking horizon 30 days; duration bounds/default 15–60/30; start-time grid 15 min; parts of day
morning 09:00–12:00 / afternoon 12:00–17:00 / evening 17:00–18:00; time zone Europe/Amsterdam; emergency phone =
placeholder (staff-editable). No other defaults.
**Reason:** Seed data is normative and closed; only by-value assertion (not counts) and encoder verification prove it
(B-103, RA-8).

### RULE-43
**Covers:** AC-121, AC-100 (event backbone shared), AC-72 (event rows)
**MUST** record every request state transition and staff/owner action in `scheduling_request_event` (from-state,
to-state, actor, action, reason, payload, timestamp) and show the timeline to staff on the request detail page (owners
do not see the raw timeline).
**Reason:** Auditability and the staff timeline are explicit requirements (RA-5, B-100).

### RULE-44
**Covers:** AC-135, AC-136, AC-137, AC-138, AC-139, AC-141
**MUST** satisfy all of the following (see §Testing Strategy tables for level/fixture per AC pattern):
1. **Isolation** — tests run only against an isolated in-memory H2 built by the same Flyway `V1..V4`; the runtime H2
   file is never read or written by tests, is not committed, and the working tree is unchanged after the suite.
2. **Pinned clock & determinism** — "now" pinned to Monday 2026-09-07 09:00 Europe/Amsterdam via the injectable `Clock`
   (horizon 2026-09-07..2026-10-07); the `stub` `RequestInterpreter` with a synchronous executor; the live model is
   never called.
3. **End-to-end** — the mandatory MockMvc lifecycle test (real filter chain, CSRF, interleaved owner+staff sessions)
   drives request → consent → interpretation → suggestion → ask again → staff hand-off → staff suggestion → accept →
   completed visit, plus a declined-consent request booked by staff and closed as no-show, asserting state after each
   step; exactly one random-port smoke test proves wiring.
4. **Negative assertion shape** — every negative authz/state AC asserts denial status **and** absence of disclosure
   (body excludes the protected data) **and** absence of mutation (`never()` on the collaborator / unchanged state);
   status-only assertions are insufficient.
5. **Test double contract** — the `stub` interpreter and any test doubles conform to the production interface (same
   exceptions, nullability, `Optional` semantics); a double **MUST NOT** return a value production cannot produce.
**Reason:** These are the categories an executor most often narrows; fixing them makes the ACs verifiable exactly as
written (RA-47, RA-48, RA-21/22).

### RULE-45
**Covers:** project-wide (observability / PII)
**MUST NOT** log the owner's free-text reason/availability, the raw model response, or seeded passwords at
`INFO`/`DEBUG`; traceability of scheduling actions **MUST** use the `scheduling_request_event` / `appointment_change`
tables rather than application logs.
**Reason:** The domain event/audit tables are the intended record; free text and raw model output are PII/sensitive and
staff-only, so they must not leak into logs.

## Cross-Reference

| AC | Rules | | AC | Rules |
|---|---|---|---|---|
| AC-1 | RULE-12 | | AC-72 | RULE-28, RULE-43 |
| AC-2 | RULE-12, RULE-13, RULE-44 | | AC-73 | RULE-26 |
| AC-3 | RULE-12, RULE-14 | | AC-74 | RULE-26 |
| AC-4 | RULE-12 | | AC-75 | RULE-26 |
| AC-5 | RULE-12, RULE-39 | | AC-76 | RULE-26, RULE-10 |
| AC-6 | RULE-12, RULE-39 | | AC-77 | RULE-26 |
| AC-7 | RULE-12, RULE-39 | | AC-78 | RULE-26 |
| AC-8 | RULE-12, RULE-44 | | AC-79 | RULE-26, RULE-22 |
| AC-9 | RULE-13, RULE-44 | | AC-80 | RULE-26, RULE-35 |
| AC-10 | RULE-13 | | AC-81 | RULE-26 |
| AC-11 | RULE-13, RULE-44 | | AC-82 | RULE-26 |
| AC-12 | RULE-13, RULE-44 | | AC-83 | RULE-11 |
| AC-13 | RULE-13, RULE-2 | | AC-84 | RULE-29 |
| AC-14 | RULE-39 | | AC-85 | RULE-29 |
| AC-15 | RULE-39 | | AC-86 | RULE-29, RULE-15 |
| AC-16 | RULE-39 | | AC-87 | RULE-30, RULE-35 |
| AC-17 | RULE-39 | | AC-88 | RULE-30 |
| AC-18 | RULE-39 | | AC-89 | RULE-31, RULE-15 |
| AC-19 | RULE-38 | | AC-90 | RULE-31, RULE-15 |
| AC-20 | RULE-15 | | AC-91 | RULE-31 |
| AC-21 | RULE-9, RULE-39 | | AC-92 | RULE-32 |
| AC-22 | RULE-9, RULE-44 | | AC-93 | RULE-32, RULE-15 |
| AC-23 | RULE-9, RULE-15 | | AC-94 | RULE-32, RULE-15 |
| AC-24 | RULE-17, RULE-15 | | AC-95 | RULE-32, RULE-36 |
| AC-25 | RULE-15, RULE-17 | | AC-96 | RULE-33 |
| AC-26 | RULE-18 | | AC-97 | RULE-33 |
| AC-27 | RULE-18 | | AC-98 | RULE-33 |
| AC-28 | RULE-40 | | AC-99 | RULE-37 |
| AC-29 | RULE-18 | | AC-100 | RULE-34 |
| AC-30 | RULE-18 | | AC-101 | RULE-34 |
| AC-31 | RULE-18 | | AC-102 | RULE-34 |
| AC-32 | RULE-19, RULE-15 | | AC-103 | RULE-34 |
| AC-33 | RULE-19 | | AC-104 | RULE-35 |
| AC-34 | RULE-19, RULE-17 | | AC-105 | RULE-35 |
| AC-35 | RULE-19 | | AC-106 | RULE-35 |
| AC-36 | RULE-19 | | AC-107 | RULE-34 |
| AC-37 | RULE-19 | | AC-108 | RULE-37 |
| AC-38 | RULE-19 | | AC-109 | RULE-36, RULE-16 |
| AC-39 | RULE-17, RULE-25 | | AC-110 | RULE-16, RULE-36 |
| AC-40 | RULE-20, RULE-21 | | AC-111 | RULE-16 |
| AC-41 | RULE-21 | | AC-112 | RULE-16 |
| AC-42 | RULE-20 | | AC-113 | RULE-16, RULE-36 |
| AC-43 | RULE-20 | | AC-114 | RULE-36 |
| AC-44 | RULE-20 | | AC-115 | RULE-36 |
| AC-45 | RULE-17, RULE-37 | | AC-116 | RULE-36, RULE-16 |
| AC-46 | RULE-22 | | AC-117 | RULE-16 |
| AC-47 | RULE-22 | | AC-118 | RULE-13 |
| AC-48 | RULE-22 | | AC-119 | RULE-36 |
| AC-49 | RULE-23 | | AC-120 | RULE-36, RULE-16 |
| AC-50 | RULE-24 | | AC-121 | RULE-43 |
| AC-51 | RULE-24 | | AC-122 | RULE-15, RULE-43 |
| AC-52 | RULE-24 | | AC-123 | RULE-15 |
| AC-53 | RULE-25, RULE-7 | | AC-124 | RULE-16 |
| AC-54 | RULE-25, RULE-15 | | AC-125 | RULE-42, RULE-12, RULE-14 |
| AC-55 | RULE-25, RULE-31 | | AC-126 | RULE-42, RULE-14 |
| AC-56 | RULE-25, RULE-7 | | AC-127 | RULE-42, RULE-12 |
| AC-57 | RULE-15 | | AC-128 | RULE-42 |
| AC-58 | RULE-8, RULE-10 | | AC-129 | RULE-42 |
| AC-59 | RULE-8 | | AC-130 | RULE-42 |
| AC-60 | RULE-8, RULE-28 | | AC-131 | RULE-42 |
| AC-61 | RULE-19, RULE-15 | | AC-132 | RULE-42 |
| AC-62 | RULE-33, RULE-40 | | AC-133 | RULE-42 |
| AC-63 | RULE-10 | | AC-134 | RULE-42 |
| AC-64 | RULE-19, RULE-10 | | AC-135 | RULE-5, RULE-44 |
| AC-65 | RULE-10, RULE-44 | | AC-136 | RULE-37, RULE-44 |
| AC-66 | RULE-28 | | AC-137 | RULE-17, RULE-44 |
| AC-67 | RULE-28 | | AC-138 | RULE-44 |
| AC-68 | RULE-28 | | AC-139 | RULE-44 |
| AC-69 | RULE-28 | | AC-140 | RULE-41 |
| AC-70 | RULE-28 | | AC-141 | RULE-9, RULE-10, RULE-44 |
| AC-71 | RULE-28 | | | |

## Design Exclusions

- **API contracts / versioning** — out of scope: the feature is server-rendered MVC with no public REST/JSON API; the
  only non-HTML endpoint (`/vets`) is stock and unchanged.
- **Performance budgets** — none beyond the solver's 1-second termination budget (RULE-11); no throughput/latency
  target is specified (criteria §Coverage exclusions).
- **Accessibility / browser matrix** — not specified; presentation coherence via stock layout (RULE-39) is the only
  requirement.
- **Distributed concurrency / messaging / streaming** — not applicable; a single H2 instance with per-vet DB locking
  (RULE-10) is sufficient for the POC.
- **Non-H2 databases, Gradle, docker-compose, k8s** — deleted/unmaintained per spec *Out of scope*; no rules govern
  them.

## External Dependencies

- **ED-1 Architecture style decision.** *Question:* stock package-by-feature MVC vs the bundled `spring-boot` skill's
  DDD layout. *Resolution:* stakeholder chose **stock layout + an ArchUnit boundary guard** (RULE-1, RULE-2, RULE-3);
  no Spring Modulith. No blocker.
- **ED-2 Local Ollama model availability.** The runtime default provider `ollama` (model `gemma4:latest`) may be absent
  in CI or on a reviewer's machine. *Default in use:* the selectable `stub` provider (`scheduling.ai.provider=stub`,
  RULE-17) gives a deterministic offline interpreter; tests always use it. *Resolution path:* install/point Ollama at
  the model for a live demo — no code change.
- **ED-3 Emergency phone number.** The real clinic phone for the urgent-care banner is a business value not provided.
  *Default in use:* seed `emergency_phone` with a placeholder, staff-editable in Clinic settings (RULE-37, RULE-38).
  *Resolution path:* staff update it in the UI.
