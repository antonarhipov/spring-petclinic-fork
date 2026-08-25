# Technical Design and Constraints: Smart Appointment Scheduling

Pipeline position: proposal → spec → criteria → **rules** → review → plan

## Overview

AI-assisted, solver-guided appointment scheduling added to Spring PetClinic. Stack: Spring Boot 4.1.0, Java 21, Maven (primary) + Gradle (kept in sync), Spring Data JPA, Thymeleaf, H2/MySQL/Postgres. New: Spring Security (form login), Spring AI 2.0.1 `ChatClient` → Ollama, Timefold Solver, Flyway. Sources: `spec/spec.md`, `spec/criteria.md`.

## Design

**Components:**
- `petclinic.security` — `UserAccount`, roles OWNER/STAFF, `SecurityConfig`, account admin, first-login gate, startup bootstrap.
- `petclinic.scheduling` — `SchedulingRequest` (aggregate + state machine), `Interpretation` (structured LLM output), `Hold`, `Appointment`, `SlotOccupancy` (one row per taken `(vet, start)`, shared exclusivity anchor for holds + appointments); owner guided-flow controllers, staff-queue controller; `InterpretationService` (Spring AI), `SchedulingSolver` (Timefold), `BookingService`, `HoldSweeper`.
- `petclinic.clinic` — `ClinicSettings`, `PartOfDay`, `ClinicClosure`, `AvailabilityService`; per-vet `VetWeeklyShift` / `VetDateException` extend the `vet` package.

**Boundaries:** exposes only server-rendered Thymeleaf pages. Owners see one suggestion at a time; the full calendar and the solver/AI internals stay server-side. No REST/JSON API, no SPA.

**Flow:** owner free text + consent → `INTERPRETING` (async Spring AI) → `AWAITING_CONFIRMATION` → owner confirms → `SUGGESTING` (async Timefold single-request solve over a frozen snapshot) → `SLOT_HELD` (hold placed, countdown) → Accept ⇒ `Appointment` + `CONFIRMED`, or Reject ⇒ exclude + re-solve. Any failure/edge routes to `STAFF_QUEUED`. Request DB state drives the auto-refreshing status page.

**Key dependencies:** add Timefold Solver (Spring Boot starter if a Boot-4.1-compatible version exists, else `timefold-solver-core` + manual `SolverManager`), Spring AI 2.0.1 (Ollama), Spring Security, Flyway. Declined: Quartz (use `@Scheduled`), durable job queue/broker (use `@Async` + Timefold `SolverManager` + DB state), partial/filtered unique indexes (unsupported on MySQL — use single-table plain unique constraints instead).

## Codebase Alignment

Follows existing conventions: feature-based packages, entities extending `BaseEntity`/`NamedEntity`/`Person`, Spring Data `JpaRepository`, `@WebMvcTest`+MockMvc+`@MockitoBean` for controllers, Testcontainers for MySQL/Postgres, Apache-2.0 header on every file, Jakarta Bean Validation on forms. Deliberate deviations: (1) schema management moves from `spring.sql.init` scripts to **Flyway** (spec R-20) — the whole project's DDL/seed migrates, not just new tables; (2) the app gains **authentication** where it had none, so previously-open `/owners/**` becomes staff-only. Both were decided upstream in the spec.

## Rules

### RULE-1
**Covers:** project-wide
**MUST** place new code in `org.springframework.samples.petclinic.security`, `.scheduling`, and `.clinic`, with per-vet availability entities in the existing `.vet` package.
**Reason:** Matches the established feature-package layout; keeps the scheduling aggregate cohesive and the vet aggregate authoritative over its own availability.

### RULE-2
**Covers:** AC-25, AC-64
**MUST** implement new persistent types as JPA entities extending `BaseEntity` (or `NamedEntity`) with Spring Data `JpaRepository`, snake_case physical names, and **MUST NOT** enable Hibernate auto-DDL (`ddl-auto` stays `none`).
**Reason:** Consistent with the existing persistence style; schema is script/migration-owned, not Hibernate-generated.

### RULE-3
**Covers:** AC-64, AC-63
**MUST** introduce Flyway as the sole owner of schema and seed data, disable `spring.sql.init`, and organize migrations as `classpath:db/migration/common` + `classpath:db/migration/{vendor}` with V1 = the current per-vendor schema/data baseline (H2/MySQL/Postgres) preserved unchanged and new tables from V2 onward.
**Reason:** Versioned, consistent DDL across three dialects; preserves existing demo data (spec R-20).

### RULE-4
**Covers:** AC-26, AC-27, AC-66
**MUST** integrate Timefold — preferring `timefold-solver-spring-boot-starter` where a Spring Boot 4.1-compatible version exists, otherwise `timefold-solver-core` with a manually configured `SolverManager` bean — model each request as a single-request solve whose confirmed appointments and active holds are immovable problem facts, run it through `SolverManager`, and bound it with a configured spent-time termination (~1s).
**Reason:** Idiomatic Timefold async solving; keeps confirmed bookings immutable and the owner's wait bounded. The starter's Boot 4.1 compatibility is unconfirmed (Boot 4.1 is a new baseline), so the core + manual `SolverManager` fallback keeps the solver ACs satisfiable regardless (review MAJOR-3).

### RULE-5
**Covers:** AC-27, AC-28, AC-29, AC-30, AC-31, AC-32, AC-33, AC-34, AC-35
**MUST** express all hard rules (availability, no-overlap, contiguous fit, required specialty, horizon/not-past, excluded windows, previously-rejected pair) and soft preferences (preferred≫allowed windows, preferred vet, earlier-weighted-by-urgency, load-balancing) as Timefold `ConstraintProvider` constraints, and **MUST NOT** enforce hard feasibility only by post-filtering solver output.
**Reason:** Feasibility and ranking belong in the score so the "next best" slot is correct and exclusions are honored.

### RULE-6
**Covers:** AC-26
**MUST** generate candidate start times aligned to `ClinicSettings.gridMinutes` (15) and occupy `ceil(duration/grid)` contiguous units.
**Reason:** Fixed grid makes holds, exclusions, and overlap checks tractable (spec R-2).

### RULE-7
**Covers:** AC-15, AC-24
**MUST** call the LLM through Spring AI `ChatClient` (Spring AI 2.0.1) with the Ollama model configurable via a property (default `gemma4:latest`), binding output to a structured `Interpretation` record, and resolve its symbolic windows to concrete ranges server-side via `PartOfDay` + booking horizon.
**Reason:** Stable structured contract; the clinic defines what "afternoon" means (spec R-3, R-21).

### RULE-8
**Covers:** AC-20, AC-21, AC-22, AC-23
**MUST** clamp the interpreted visit duration to `ClinicSettings` min/max and substitute the clinic default when duration is absent, before solving.
**Reason:** Keeps durations within clinic policy regardless of LLM output.

### RULE-9
**Covers:** AC-15, AC-16, AC-65
**MUST** run interpretation and solving on a bounded `ThreadPoolTaskExecutor` (Spring `@Async` / `SolverManager`), persist request state as the single source of truth, and **MUST NOT** block the controller thread on the LLM or solver; the status view polls via meta-refresh.
**Reason:** Ollama latency is seconds; the request thread must return immediately (spec R-6).

### RULE-10
**Covers:** AC-14, AC-19, AC-41, AC-42, AC-43, AC-45, AC-46, AC-47, AC-48
**MUST** centralize `SchedulingRequest` state transitions in the domain/service layer, guard illegal transitions, and record the `queue_reason` on every transition into `STAFF_QUEUED`.
**Reason:** One enforced state machine prevents inconsistent request lifecycles.

### RULE-11
**Covers:** AC-36, AC-38, AC-39, AC-40, AC-43, AC-44
**MUST** enforce slot exclusivity through a single `slot_occupancy` table holding one row per taken `(vet_id, start_time)` — referenced by both the hold and the confirmed appointment — with a plain (non-filtered) `UNIQUE (vet_id, start_time)` constraint; acquire the occupancy row transactionally when a hold is placed, release it on reject/expiry, compute expiry from `ClinicSettings.holdDurationMinutes`, validate expiry lazily on each owner action, and reclaim expired holds/occupancy with a `@Scheduled` sweeper. **MUST NOT** rely on a partial/filtered unique index (unsupported on MySQL).
**Reason:** A single-table plain unique constraint is enforceable and portable across H2/MySQL/Postgres — a constraint cannot span two tables and MySQL has no filtered indexes; one occupancy row per slot gives the DB-level double-booking guarantee AC-40 needs (review MAJOR-1, MAJOR-2; spec R-10).

### RULE-12
**Covers:** project-wide, AC-44
**MUST NOT** introduce Quartz or an external scheduler for hold reclamation; use Spring `@Scheduled`.
**Reason:** Single-node reference app; the canonical heavy scheduler is unjustified for one periodic sweep.

### RULE-13
**Covers:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-67
**MUST** add Spring Security form login with roles OWNER/STAFF, store passwords with `BCryptPasswordEncoder`, gate `must_change_password` users to the change-password page via a filter/interceptor, bootstrap the initial staff account from config properties in an `ApplicationRunner`, and **MUST NOT** seed staff credentials in Flyway, log passwords, or render them.
**Reason:** Establishes authentication safely; keeps secrets out of migrations and logs (spec R-12, R-13).

### RULE-14
**Covers:** AC-2, AC-7, AC-8, AC-9, AC-10, AC-11, AC-58, AC-68
**MUST** configure URL zones (`/staff/**` and `/owners/**` → ROLE_STAFF; owner self-service → ROLE_OWNER; login/change-password/welcome public) **and** re-check resource ownership inside each owner-facing handler (`@PreAuthorize` or explicit service check) before disclosing or modifying a request, pet, profile, or appointment.
**Reason:** URL rules alone don't stop ID tampering; ownership must be verified at the handler (spec R-22, R-23).

### RULE-15
**Covers:** AC-13, AC-25
**MUST** persist the raw free text and structured interpretation on the request, **MUST NOT** log the raw free text, and **MUST NOT** send it to any service other than the configured LLM, only after recorded consent.
**Reason:** PII-adjacent content: retained for staff use but not leaked to logs or third parties (spec R-8, R-9).

### RULE-16
**Covers:** AC-17
**MUST** treat an absent `ChatModel` bean, or an LLM error/timeout, as a routing condition to `STAFF_QUEUED(AI_UNAVAILABLE)` rather than a startup or request failure.
**Reason:** App must run and stay usable with no configured/reachable model (spec R-21, D-1).

### RULE-17
**Covers:** AC-17, AC-46, AC-47
**MUST** catch AI and solver failures at the async service boundary, map them to the corresponding `STAFF_QUEUED` reason, and **MUST NOT** surface stack traces or internal errors to the user.
**Reason:** The automated flow never dead-ends; failures degrade to the staff queue.

### RULE-18
**Covers:** AC-41, AC-50, AC-51, AC-52, AC-53, AC-54, AC-55, AC-56, AC-57
**MUST** route all booking lifecycle operations (owner accept, staff book-on-behalf, staff direct book/reschedule/cancel, owner cancel, complete, no-show) through a single `BookingService` that re-validates availability and non-overlap on create/reschedule, records `changeReason` for staff-initiated changes, creates a `Visit` transactionally on completion, and blocks owner cancellation at/after start time.
**Reason:** One transactional authority keeps the calendar consistent and the Appointment↔Visit rule intact (spec R-14, R-25).

### RULE-19
**Covers:** AC-54
**MUST NOT** add scheduling fields to the existing `Visit` entity; `Appointment` is a distinct entity and completion creates a new `Visit`.
**Reason:** Preserves `Visit` as the historical record; avoids overloading a shared entity (spec R-14).

### RULE-20
**Covers:** AC-31, AC-61
**MUST** perform all temporal computation (now, booking horizon, availability, slot times) in `ClinicSettings.timeZone` and **MUST NOT** rely on the JVM default zone.
**Reason:** Single-timezone clinic; JVM default would make availability non-deterministic across environments (spec R-18).

### RULE-21
**Covers:** AC-49
**MUST** model the staff fallback queue as a query/view over `SchedulingRequest` where `state=STAFF_QUEUED`, ordered emergency-first then by `queued_at`, and **MUST NOT** introduce a separate queue table.
**Reason:** The request is the queue item; a parallel table would duplicate state (spec R-19).

### RULE-22
**Covers:** AC-59, AC-60
**MUST** enforce "at most one active `SchedulingRequest` per pet" with both a transactional service check and a portable database constraint — a nullable `active_pet_key` column set to `pet_id` while the request is non-terminal and `NULL` once terminal, under a plain `UNIQUE (active_pet_key)` index. **MUST NOT** use a partial/filtered unique index (unsupported on MySQL).
**Reason:** Prevents an owner holding two slots for one animal under concurrent submits; the nullable-key trick yields the same "one active per pet" guarantee on H2/MySQL/Postgres because all three treat NULLs as distinct in unique indexes (review MAJOR-2; spec R-24).

### RULE-23
**Covers:** AC-16, AC-37, project-wide
**MUST** implement the UI as server-rendered Thymeleaf only, render at most the current suggestion's details (vet name, specialty, date, start, duration) to owners, and **MUST NOT** add a REST/JSON API, SPA, or client-side scheduling logic.
**Reason:** Matches the app's idiom and the "never expose the calendar" rule (spec R-6, A-6).

### RULE-24
**Covers:** project-wide, AC-64, AC-67
**MUST** cover new code with tests matching project conventions: `@WebMvcTest`+MockMvc+`@MockitoBean` for controllers, `spring-security-test` for authz paths (asserting the prohibited outcome for negative ACs), mocked `ChatClient`/`ChatModel` (no live Ollama), small in-memory datasets for solver/constraint tests, three-point service unit tests for each boundary triple (duration, horizon, hold expiry), per-transition service tests for each state-machine transition, and Flyway migrations validated on H2 plus Testcontainers MySQL/Postgres.
**Reason:** Keeps the suite deterministic and hermetic, matches the existing test style, and shapes tests to the EARS pattern of each AC (review MINOR-1).

### RULE-25
**Covers:** project-wide
**MUST** declare every new dependency in **both** `pom.xml` and `build.gradle`, and add the Apache-2.0 license header to every new source file.
**Reason:** The project maintains dual build files in sync and a uniform license header.

## Cross-Reference

| AC | Rules |
|----|-------|
| AC-1 | RULE-13, RULE-14 |
| AC-2 | RULE-14 |
| AC-3 | RULE-13 |
| AC-4 | RULE-13 |
| AC-5 | RULE-13 |
| AC-6 | RULE-13 |
| AC-7 | RULE-14 |
| AC-8 | RULE-14 |
| AC-9 | RULE-14 |
| AC-10 | RULE-14 |
| AC-11 | RULE-14 |
| AC-12 | (none needed — static view content) |
| AC-13 | RULE-7, RULE-15 |
| AC-14 | RULE-10 |
| AC-15 | RULE-7, RULE-9 |
| AC-16 | RULE-9, RULE-23 |
| AC-17 | RULE-16, RULE-17 |
| AC-18 | RULE-10, RULE-23 |
| AC-19 | RULE-10 |
| AC-20 | RULE-8 |
| AC-21 | RULE-8 |
| AC-22 | RULE-8 |
| AC-23 | RULE-8 |
| AC-24 | RULE-7 |
| AC-25 | RULE-2, RULE-15 |
| AC-26 | RULE-4, RULE-6 |
| AC-27 | RULE-4, RULE-5 |
| AC-28 | RULE-5 |
| AC-29 | RULE-5 |
| AC-30 | RULE-5 |
| AC-31 | RULE-5, RULE-20 |
| AC-32 | RULE-5 |
| AC-33 | RULE-5 |
| AC-34 | RULE-5 |
| AC-35 | RULE-5 |
| AC-36 | RULE-11 |
| AC-37 | RULE-23 |
| AC-38 | RULE-11 |
| AC-39 | RULE-11 |
| AC-40 | RULE-11 |
| AC-41 | RULE-10, RULE-18 |
| AC-42 | RULE-10 |
| AC-43 | RULE-10, RULE-11 |
| AC-44 | RULE-11, RULE-12 |
| AC-45 | RULE-10, RULE-17 |
| AC-46 | RULE-10, RULE-17 |
| AC-47 | RULE-17 |
| AC-48 | RULE-10 |
| AC-49 | RULE-21 |
| AC-50 | RULE-18 |
| AC-51 | RULE-18 |
| AC-52 | RULE-18 |
| AC-53 | RULE-18 |
| AC-54 | RULE-18, RULE-19 |
| AC-55 | RULE-18 |
| AC-56 | RULE-18 |
| AC-57 | RULE-18 |
| AC-58 | RULE-14 |
| AC-59 | RULE-22 |
| AC-60 | RULE-22 |
| AC-61 | RULE-20 |
| AC-62 | RULE-2, RULE-14 |
| AC-63 | RULE-3 |
| AC-64 | RULE-2, RULE-3, RULE-24 |
| AC-65 | RULE-9 |
| AC-66 | RULE-4 |
| AC-67 | RULE-13, RULE-24 |
| AC-68 | RULE-14 |

## Design Exclusions

- **Distributed/multi-node concurrency:** single-node deployment assumed; slot exclusivity relies on a DB constraint, not distributed locks.
- **Observability/metrics/tracing:** no metrics or structured audit beyond `changeReason`; not required by any AC.
- **Rate limiting / login lockout:** out of scope per spec A-5.
- **Retention/purge of stored free text:** kept indefinitely (spec R-9); no lifecycle rule.
- **API versioning/back-compat:** no external API introduced (RULE-23), so not applicable.

## External Dependencies

- **D-1 — Ollama runtime.** Question: which Ollama host/model is available at deploy time? Blocker: runtime environment. Default in use: model configurable via property (`gemma4:latest`); when no `ChatModel` is configured or reachable, interpretation is skipped and requests route to `STAFF_QUEUED(AI_UNAVAILABLE)` (RULE-16), so the app remains fully functional without it.
