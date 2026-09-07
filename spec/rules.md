# Technical Rules: Smart Appointment Scheduling

## Design overview

The scheduling feature is a domain-oriented module split into request, appointment, matching, interpretation,
configuration, security, and web boundaries. MVC controllers resolve the authenticated actor and delegate to
transactional application services. Request and appointment aggregates enforce their state machines; repositories
provide persistence and locking, while a framework-free matcher computes feasible and ranked slots. Spring AI is
hidden behind an interpreter port and runs asynchronously. The production adapter uses Ollama; automated tests use a
deterministic substitute. Flyway owns the H2 schema and normative seed data. Thymeleaf pages reuse PetClinic's layout,
form fragments, stylesheet, and message bundles. Owner routes are isolated under `/my/**`; staff routes use
`/staff/**` and the established clinic-management routes.

## Codebase alignment

The implementation keeps the existing Java 17, Spring Boot 4.1, package-by-domain, constructor-injection, JPA,
Thymeleaf, message-bundle, Spring Java Format, Checkstyle, and Maven conventions. It introduces a transactional service
layer because lifecycle guards and locking cannot safely live in controllers. Database initialization moves from
`spring.sql.init` scripts to Flyway. Matching remains plain Java. The specification's H2-only runtime and isolated H2
test contract deliberately overrides generic multi-database and Testcontainers guidance.

## Security surface

An owner result in the table always means data is resolved from the authenticated account's linked owner. A foreign or
unknown owner-scoped identifier produces the same standard 404 response.

| Route | Anonymous | Owner | Staff |
|---|---|---|---|
| `GET/POST /login` | Allowed | Allowed | Allowed |
| `/resources/**`, `/webjars/**`, favicon and error assets | Allowed | Allowed | Allowed |
| `GET /actuator/health` | Allowed | Allowed | Allowed |
| `GET /`, `POST /logout`, `/error` | Login required | Allowed | Allowed |
| `/my/pets`, `/my/appointments`, `/my/appointments/**`, `/my/requests/**` | Login required | Own data | 403 |
| `/owners/**`, `/vets`, `/vets.html`, `/vets/**`, `/oups` | Login required | 403 | Allowed |
| `/staff/**` | Login required | 403 | Allowed |
| `/actuator/**` except health, `/h2-console/**` | Login required | 403 | Allowed |
| Any other route | Login required | 404 after login | 404 after login |

## Verification strategy

Tests use a dedicated in-memory H2 datasource initialized by the production Flyway migrations and must never open or
modify the file-backed runtime database. Pure matching and lifecycle decisions receive unit tests. Persistence and
service tests use real H2 state. MVC slices import the real security chain and mock only application boundaries. Each
use case has a `RANDOM_PORT` HTTP journey with real form login, session cookies, and CSRF; state is checked after every
consequential response. Negative paths assert the response, absence of protected data, unchanged database state, and
absence of forbidden collaborator calls. Fresh-database tests compare every normative row and password by value.
Concurrency tests race real transactions. Time and the interpreter are deterministic substitutes. UI-bearing use
cases also require the use-case-derived human walkthrough during convergence.

## Rules

### RULE-1 - Domain boundaries

- Applies to: all use cases
- Constraint: MUST organize scheduling code by the request, appointment, matching, interpretation, configuration,
  security, and web responsibilities described above; controllers MUST NOT contain lifecycle or matching decisions.
- Reason: The feature has multiple actors and shared invariants that must not diverge by route.
- Verification: Package dependency inspection and controller/service tests prove delegation through one production
  path.

### RULE-2 - Transactional application services

- Applies to: UC-3, UC-4, UC-5, UC-6, UC-7, UC-8
- Constraint: MUST run each state-changing use-case action in one `@Transactional` service operation and MUST use
  read-only transactions for detached query models that traverse lazy relationships.
- Reason: The scenario postconditions and no-partial-change guarantees are atomic units of work.
- Verification: Service annotations plus rollback tests compare all affected rows before and after refused actions.

### RULE-3 - Framework-free deterministic matching

- Applies to: UC-3, UC-4, UC-5, UC-7
- Constraint: MUST keep candidate enumeration, feasibility, and lexicographic comparison independent of Spring and
  persistence, and MUST implement UC-3 G2-G6 in the stated comparison order without weights.
- Reason: Matching must be reproducible and independently testable.
- Verification: Plain-Java boundary tests compare exact candidates and ordering for every feasibility and tie-break
  dimension.

### RULE-4 - Request lifecycle enforcement

- Applies to: UC-2, UC-3, UC-4, UC-5, UC-6, UC-7
- Constraint: MUST implement exactly the scheduling-request states and exits in `spec.md`; every unlisted action MUST
  raise one typed refusal result or exception below MVC and MUST change no request, interpretation, rejection, hold,
  appointment, or visit.
- Reason: UI hiding alone cannot protect the lifecycle under stale or forged requests.
- Verification: A real-state action-by-state matrix tests every allowed transition and every refusal, including hold
  deletion and `active_pet_id` clearing side effects.

### RULE-5 - Appointment lifecycle enforcement

- Applies to: UC-3, UC-4, UC-5, UC-6, UC-7, UC-8
- Constraint: MUST store `HELD`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, and `NO_SHOW` in one appointment aggregate;
  MUST delete rather than status-change a released hold; and MUST refuse every transition not listed in `spec.md`
  without side effects.
- Reason: Appointment finality and the one-live-hold invariant are product guarantees.
- Verification: Real-state lifecycle matrix tests assert statuses, audit fields, linked visits, deleted holds, and
  unchanged snapshots for refusals.

### RULE-6 - Concurrency control

- Applies to: UC-3, UC-4, UC-5, UC-7
- Constraint: MUST enforce one active request with a nullable `active_pet_id` equal to `pet_id` while active and a
  unique constraint; MUST lock the veterinarian row pessimistically for hold, accept, book, and reschedule and recheck
  overlap inside the transaction; MUST use an optimistic version for staff request actions.
- Reason: These database mechanisms implement the observable one-winner and stale-action guarantees from D11, D24,
  and D31.
- Verification: Two-thread integration tests observe one active-request winner and one overlapping slot winner; stale
  version tests prove no mutation.

### RULE-7 - Flyway and data fidelity

- Applies to: all use cases
- Constraint: MUST let Flyway exclusively create the stock and scheduling schema and seed data; MUST store clinic
  dates and times as `LocalDate` and `LocalTime`; MUST persist interpretation versions as immutable rows with origin,
  raw AI JSON, model tag, and prompt version as applicable.
- Reason: Restart survival, DST stability, and interpretation fidelity depend on one migration path and lossless local
  values.
- Verification: Fresh migration, restart, mapping, and field-by-field round-trip tests.

### RULE-8 - Exact normative seeds

- Applies to: UC-1, UC-2, UC-3, UC-4, UC-5, UC-6, UC-7, UC-8
- Constraint: MUST seed exactly the accounts, roles, owner links, specialties, veterinarian associations, opening
  hours, 17 working blocks, six exceptions, zero leave, zero closures, and configuration defaults in `spec.md`, with
  no additional row in those normative sets; passwords MUST be BCrypt hashes.
- Reason: The normative data is part of the behavioral contract.
- Verification: A fresh-database test compares every value, proves set equality, and verifies all 12 passwords through
  the configured encoder.

### RULE-9 - Whole-route authorization

- Applies to: UC-1, UC-2, UC-3, UC-4, UC-5, UC-6, UC-7, UC-8
- Constraint: MUST configure a single path-based security chain implementing the Security surface exactly, form login
  with generic failure, CSRF on mutations, BCrypt-backed seeded accounts, and logout session invalidation.
- Reason: The existing and new application surfaces must share one enforceable boundary.
- Verification: Route-inventory tests exercise every mapped handler as anonymous, owner, and staff and assert response,
  disclosure absence, and mutation absence.

### RULE-10 - Principal-derived owner scope

- Applies to: UC-1, UC-2, UC-3, UC-6
- Constraint: MUST derive the owner from the authenticated username on every `/my/**` operation, MUST NOT accept an
  owner id there, and MUST render foreign and unknown resource identifiers through the same standard 404 mechanism.
- Reason: This prevents enumeration and confused-deputy access.
- Verification: Cross-owner tests compare status and body with unknown-id responses and prove no collaborator or
  persistence side effect.

### RULE-11 - Spring AI Ollama adapter

- Applies to: UC-3
- Constraint: MUST use Spring AI 2.0.1's Ollama starter and auto-configured `ChatClient.Builder`, flattened
  `spring.ai.ollama.chat.*` properties, default model `ministral-3:14b`, temperature zero, provider structured output
  with schema validation, five-second connect timeout, 120-second overall deadline, and no retry; automated tests MUST
  NOT contact Ollama.
- Reason: This is the selected external-integration contract and keeps model choice configurable.
- Verification: Property-binding, actual JDK request-factory timeout, generated-schema, prompt, call-count, timeout,
  and deterministic-adapter tests.

### RULE-12 - Minimal interpreter disclosure

- Applies to: UC-3, UC-4
- Constraint: MUST place interpretation behind an application port and MUST send only owner free text plus the
  enumerated clinic context in UC-3; MUST NOT send or log owner or pet identifiers, names, credentials, or model raw
  output.
- Reason: Consent and privacy depend on the adapter boundary rather than presentation wording.
- Verification: A recording interpreter compares the complete prompt and negative log/static scans reject prohibited
  fields.

### RULE-13 - Asynchronous interpretation

- Applies to: UC-3
- Constraint: MUST accept at most one job per consented request, execute at most two jobs concurrently with additional
  jobs queued, make no retry, classify semantic failure separately from unavailability, discard late results, and
  sweep startup `INTERPRETING` requests to `WITH_STAFF/AI_UNAVAILABLE` without resubmission.
- Reason: D13 and UC-3 G19 define both lifecycle and capacity behavior.
- Verification: Deterministic latch-based integration tests cover duplicate consent, two-running-plus-waiting,
  timeout/transport, every semantic failure, abandonment, and startup recovery.

### RULE-14 - State-only polling

- Applies to: UC-1, UC-3
- Constraint: MUST return exactly `{"state":"<STATE>"}` from the owner-scoped request status route and MUST use an
  external static polling script while retaining a visible working Refresh link.
- Reason: Polling must not leak request data and must work without scripting.
- Verification: Exact JSON comparison, owner isolation tests, and rendered DOM/script-resource checks.

### RULE-15 - Trusted clinic clock

- Applies to: UC-3, UC-4, UC-5, UC-6, UC-7
- Constraint: MUST obtain current date and time only through an injected `Clock`; tests MUST pin the stated
  `2026-09-07 09:00 Europe/Amsterdam` instant where normative exceptions matter.
- Reason: Horizon, hold age, finalization, cancellation, and DST behavior must be deterministic.
- Verification: Static scan rejects direct unclocked `now()` calls and boundary tests use fixed or mutable clocks.

### RULE-16 - Shared PetClinic presentation

- Applies to: all use cases
- Constraint: MUST render every page through the established layout, navigation, form fragments, and stylesheet with
  no inline styles or second visual system; authenticated pages MUST show identity and logout and exactly the actions
  permitted to the role and current state.
- Reason: The feature is part of PetClinic rather than a separate application shell.
- Verification: Rendered-page DOM tests plus the convergence walkthrough.

### RULE-17 - Complete localization

- Applies to: all use cases
- Constraint: MUST resolve all user-visible template text and attributes, validation, flash/status, reasons, rank
  explanations, and clamp notes from message keys present in all eleven bundles; Java MUST NOT emit hard-coded English.
- Reason: Localization is an explicit system-wide guarantee.
- Verification: Source/template scans and exact message-key-set equality across all bundles.

### RULE-18 - Use-case boundary evidence

- Applies to: all use cases
- Constraint: MUST provide one real-server actor journey per use case, direct evidence for every extension, guarantee,
  postcondition, and relation, and negative evidence that includes prohibited-side-effect checks; tests MUST leave the
  tracked tree and runtime database unchanged.
- Reason: Internal green tests alone cannot prove the behavioral contract.
- Verification: Checkpoint evidence maps every contract element to executable tests and convergence reruns them.

### RULE-19 - Staff constraints

- Applies to: UC-4, UC-5
- Constraint: MUST validate staff booking, suggestion, and reschedule against clinic hours, continuous effective
  working time, 15-minute grid, duration bounds, and no overlap; MUST NOT block on owner windows, specialty, horizon,
  or lead time, but MUST display specialty mismatch information.
- Reason: Staff override authority is deliberately bounded.
- Verification: At/inside/outside boundary tests compare database snapshots for each accepted and refused input.

### RULE-20 - One effective-availability calculation

- Applies to: UC-3, UC-4, UC-5, UC-7
- Constraint: MUST compute opening hours intersected with weekly working blocks and reduced by exceptions, leave, and
  closures in one shared production function used by matching, staff validation, calendar rendering, and conflict
  detection.
- Reason: Separate calculations could offer slots the calendar calls unavailable.
- Verification: Cross-boundary parity tests feed one fixture to all consumers and compare exact blocks.

### RULE-21 - Interpretation fidelity and duration policy

- Applies to: UC-3, UC-4
- Constraint: MUST preserve every accepted structured value verbatim, including absent values, `OTHER` label, all
  windows, and out-of-range raw duration; defaults and clamping MUST occur only while building a match or staff default.
- Reason: Stored interpretation is an immutable record of what the interpreter or staff authored.
- Verification: Field-by-field persistence round trips plus below/at/inside/at/above duration boundary tests.

### RULE-22 - Durable rejections

- Applies to: UC-3, UC-4
- Constraint: MUST persist each rejected veterinarian and start time for the request lifetime, preserve it across text
  edits and interpretation versions, and exclude it from every later candidate set.
- Reason: Removing a hold must not erase an owner's irreversible rejection.
- Verification: End-to-end edit/reinterpret/rematch tests prove the prior exact slot never returns.

### RULE-23 - Atomic configuration changes

- Applies to: UC-5, UC-7
- Constraint: MUST validate internal ranges and block overlap before saving; MUST reject the complete change and list
  all future confirmed conflicts without releasing holds; when only holds conflict, MUST save and atomically delete all
  such holds and route their requests to `WITH_STAFF/SCHEDULE_CHANGED`.
- Reason: Confirmed care is protected while suggestions are recoverable.
- Verification: Whole-database before/after comparisons for invalid, confirmed-conflict, hold-conflict, and clean
  changes across every configuration type.

### RULE-24 - H2 and Maven support boundary

- Applies to: all use cases
- Constraint: MUST support Maven and H2 only; MUST remove Gradle, MySQL, PostgreSQL, Docker Compose, Kubernetes, and
  related dependencies/tests/resources; MUST use a gitignored file-backed H2 runtime database and in-memory H2 tests;
  README MUST document this boundary, English input, Ollama version, model pull, and local endpoint.
- Reason: D35-D36 and the POC scope prohibit shipping configurations known not to work.
- Verification: Repository inventory, dependency-tree, properties, README, restart, and test-isolation checks.

### RULE-25 - Linked completion visit

- Applies to: UC-5, UC-8
- Constraint: MUST create exactly one visit only when a confirmed appointment is completed, linked to that appointment
  and dated with the appointment date; MUST prefill description from linked request text truncated to 255 characters
  or empty for a direct booking; MUST retain unlinked stock walk-in visits.
- Reason: Scheduled completion and walk-in entry are distinct production paths with a shared visit record.
- Verification: Lifecycle integration tests compare every visit field and stock-controller regression tests prove a
  null appointment link for walk-ins.

## Use-case cross-reference

| Use case | Rules |
|---|---|
| UC-1 | RULE-1, RULE-7, RULE-8, RULE-9, RULE-10, RULE-14, RULE-16, RULE-17, RULE-18, RULE-24 |
| UC-2 | RULE-1, RULE-2, RULE-4, RULE-7, RULE-8, RULE-9, RULE-10, RULE-16, RULE-17, RULE-18, RULE-24 |
| UC-3 | RULE-1, RULE-2, RULE-3, RULE-4, RULE-5, RULE-6, RULE-7, RULE-8, RULE-9, RULE-10, RULE-11, RULE-12, RULE-13, RULE-14, RULE-15, RULE-16, RULE-17, RULE-18, RULE-20, RULE-21, RULE-22, RULE-24 |
| UC-4 | RULE-1, RULE-2, RULE-3, RULE-4, RULE-5, RULE-6, RULE-7, RULE-8, RULE-9, RULE-10, RULE-12, RULE-15, RULE-16, RULE-17, RULE-18, RULE-19, RULE-20, RULE-21, RULE-22, RULE-24 |
| UC-5 | RULE-1, RULE-2, RULE-3, RULE-4, RULE-5, RULE-6, RULE-7, RULE-8, RULE-9, RULE-10, RULE-15, RULE-16, RULE-17, RULE-18, RULE-19, RULE-20, RULE-23, RULE-24, RULE-25 |
| UC-6 | RULE-1, RULE-2, RULE-4, RULE-5, RULE-7, RULE-8, RULE-9, RULE-10, RULE-15, RULE-16, RULE-17, RULE-18, RULE-24 |
| UC-7 | RULE-1, RULE-2, RULE-3, RULE-4, RULE-5, RULE-6, RULE-7, RULE-8, RULE-9, RULE-10, RULE-15, RULE-16, RULE-17, RULE-18, RULE-20, RULE-23, RULE-24 |
| UC-8 | RULE-1, RULE-2, RULE-5, RULE-7, RULE-8, RULE-9, RULE-16, RULE-17, RULE-18, RULE-24, RULE-25 |

## Design exclusions

- No second database, build system, deployment packaging, notification channel, waitlist, hold timer, weighted solver,
  multi-clinic abstraction, translation, urgency classification, account administration, or public scheduling API.
- No Spring Batch, retry/resilience framework, Quartz, or optimization engine is needed for one asynchronous
  interpretation and deterministic single-request ranking.
- Accessibility work beyond established PetClinic markup and localized semantic attributes is not separately scoped.

## External dependencies

Ollama 0.13.1 or newer with `ministral-3:14b` is required only for live interpretation. It is not a build or automated
test dependency; unavailable and untimely model behavior is fully specified by UC-3.
