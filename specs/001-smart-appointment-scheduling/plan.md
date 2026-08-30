# Implementation Plan: Smart Appointment Scheduling

**Branch**: `001-smart-appointment-scheduling` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-smart-appointment-scheduling/spec.md`

## Summary

Extend the existing Spring PetClinic server-rendered application with secure owner scheduling, staff calendar/direct booking, durable AI interpretation, deterministic Timefold matching, held offers, staff fallback, and complete appointment lifecycle management.

Implementation remains one modular Spring Boot application. Transactional application services own authorization-sensitive state changes. Flyway-managed H2 persists the domain, database-backed workers recover asynchronous jobs, a singleton calendar revision lock serializes capacity changes, and Timefold 2.5.0 exhaustively solves one request against a fixed snapshot. Delivery starts with platform, migration, security, encryption/audit/idempotency, and calendar foundations; staff availability and direct booking are the first functional slice. AI and automatic matching arrive only after the manual and staff-assisted workflows are proven.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1; Spring MVC and Thymeleaf; Spring Data JPA; Spring Security; Bean Validation; Flyway; H2; Spring AI 2.0.1 with Ollama; Timefold Solver 2.5.0; existing Bootstrap/Caffeine where still used

**Storage**: Flyway-managed file-backed H2 for normal/demo execution; in-memory H2 for most tests; file-backed temporary H2 for migration, restart, and concurrency tests; application-level AES-256-GCM envelope encryption for protected payloads

**Testing**: JUnit Platform/JUnit 6, AssertJ, Mockito with explicit Byte Buddy Java agent, Timefold `ConstraintVerifier`, secured `@WebMvcTest`/`MockMvcTester`, `@DataJpaTest` with Flyway and H2, focused `@SpringBootTest` smoke/end-to-end tests

**Target Platform**: JVM web application on a Java 21-capable development/demo host; one running application instance with exclusive access to its H2 file

**Project Type**: Single-module, server-rendered modular monolith

**Performance Goals**: Matching completes in at most five seconds; AI interpretation has a ten-second application deadline; at least 95% of valid consented requests reach an offer or explicit staff fallback within 20 seconds; status polling defaults to approximately two seconds without extending session inactivity

**Constraints**: Timefold is mandatory; deterministic completed solve only; Maven only; H2 only for phase one; Flyway owns the whole schema; Hibernate validates; no transaction spans AI/solver work; one application instance; 30-minute interactive inactivity; synthetic/demo data only; English only; no owner-visible full calendar; no native image; no externally exposed Actuator surface; every workflow available through browser routes; CSRF and idempotent commands required

**Scale/Scope**: One clinic, one veterinarian and pet per appointment, owner conflict across pets, 15-minute start grid, configurable 1-365 day horizon, one active request per pet, one held offer per request, at most five automatic offers per workflow revision, multiple distinct staff identities

## Constitution Check

*GATE: Evaluated before research and re-evaluated after Phase 1 design.*

The repository constitution is still an unratified placeholder and defines no enforceable project principles. There is therefore no constitutional violation to block planning. The following feature-specific gates are taken from the clarified spec and confirmed technical decisions:

| Gate | Pre-research result | Evidence |
|---|---|---|
| Timefold, Java 21, Maven-only | PASS | Required technology and build baseline are explicit |
| H2-only phase-one persistence | PASS | Additional databases are deferred in the clarified spec |
| Flyway baseline and Hibernate validation | PASS | Research selects one clean baseline and ordered migrations |
| Role separation before owner feature work | PASS | Plan starts with account/security and disjoint route families |
| Durable/idempotent asynchronous workflow | PASS | Database jobs, leases, command records, versions, and audit are foundational |
| Deterministic five-second matching | PASS | Exhaustive reproducible Timefold solve; partial timeout result is rejected |
| Sensitive-data protection and rotation | PASS | Envelope encryption and resumable key rewrap are designed before payload storage |
| Browser-complete workflow | PASS | Contracts define owner/staff HTML routes plus narrow read-only polling JSON |

No exception or complexity waiver is required.

## Project Structure

### Documentation (this feature)

```text
specs/001-smart-appointment-scheduling/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── spec.md
├── checklists/
│   └── requirements.md
├── contracts/
│   ├── http-ui.md
│   ├── interpretation-output.schema.json
│   ├── request-status.schema.json
│   └── workflow-states.md
└── tasks.md                         # Created later by /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/org/springframework/samples/petclinic/
├── PetClinicApplication.java
├── account/                         # Accounts, provisioning, password lifecycle
├── appointment/                     # Booking lifecycle, outcomes, legacy reconciliation
├── audit/                           # Append-only audit/history and protected payloads
├── availability/                    # Clinic policy, shifts, exceptions, leave, closures
├── scheduling/
│   ├── request/                     # Requests, text/workflow revisions, consent
│   ├── interpretation/              # AI port, validation, emergency screen, Ollama adapter
│   ├── matching/                    # Timefold model, constraints, snapshots
│   ├── offer/                       # Holds, accept/reject/expiry
│   ├── queue/                       # Staff fallback, claims, contact, assistance
│   └── job/                         # Durable worker, leases, stale-result recovery
├── security/                        # Route policy, principal, inactivity/session enforcement
├── shared/                          # Clock, command/idempotency, interval/value types
├── config/                          # Cross-cutting application configuration
├── owner/                           # Existing owner/pet/visit catalog adapted for staff/owner use
├── vet/                             # Existing veterinarian/specialty catalog
└── system/                          # Public landing and safe error/web configuration

src/main/resources/
├── application.properties
├── application-local.properties
├── application-demo.properties
├── db/migration/
│   ├── V1__legacy_petclinic_baseline.sql
│   └── V*.sql
├── messages/messages.properties     # English only
├── templates/
│   ├── public/
│   ├── auth/
│   ├── owner/
│   ├── staff/
│   └── fragments/
└── static/resources/

src/test/java/org/springframework/samples/petclinic/
├── architecture/
├── account/
├── appointment/
├── audit/
├── availability/
├── scheduling/
├── security/
├── owner/
├── vet/
└── support/                         # Fixtures, clocks, AI/solver stubs, H2 helpers
```

**Structure Decision**: Keep one deployable application and organize new code by business capability. Each capability contains its own domain, persistence, application service, and web adapter code as needed; do not introduce repository-wide technical-layer packages. Existing owner/vet packages remain catalogs but their routes and direct repository mutations are adapted behind staff/owner application services.

## Phase 0 Research Decisions

All planning unknowns are resolved in [research.md](research.md). Important outcomes:

- The clarified spec supersedes conflicting MySQL/PostgreSQL text in the older proposal.
- Spring Boot moves to 4.1.1 to align Spring AI 2.0.1 while retaining Timefold 2.5.0 compatibility; implementation starts with a combined dependency/context smoke test.
- A runtime-configured Ollama model is required only when AI is enabled. The application never pulls models automatically, and deterministic tests stub the interpretation port.
- Current legacy visits cannot prove prior agreement, veterinarian, or exact interval; all currently stored future visits require fresh agreement during reconciliation.
- The placeholder constitution adds no gate; the spec, clarification record, and this plan supply the binding constraints for this feature.

## Phase 1 Design

### Domain and Persistence

[data-model.md](data-model.md) defines the physical/domain model, validation, independent request/job/queue/offer/appointment states, database constraints, Timefold planning model, and atomic invariants.

Key architecture:

1. `CalendarState` is both the monotonic snapshot number and pessimistic mutex for capacity-changing transactions.
2. `ActiveSchedulingRequest` makes one active request per pet a database invariant.
3. `CommandRecord` is issued before a mutation form and completed with the business transaction, making duplicate submissions restart-safe.
4. `BackgroundJob` uses database leases and target-revision checks; AI/Timefold execute outside database transactions.
5. `ProtectedPayload` plus versioned key envelopes protects text, AI, consent, clinical, and sensitive audit artifacts and supports resumable rewrap.
6. `Appointment` separates booking state from visit outcome so audited corrections do not invent invalid booking transitions.

### Browser and Integration Contracts

- [http-ui.md](contracts/http-ui.md) defines route authorization, owner scoping, CSRF/idempotency fields, browser response behavior, and all owner/staff workflow surfaces.
- [workflow-states.md](contracts/workflow-states.md) defines state transitions and atomic cross-aggregate outcomes.
- [interpretation-output.schema.json](contracts/interpretation-output.schema.json) is the only accepted structured AI result shape; it intentionally has no care type or numeric confidence.
- [request-status.schema.json](contracts/request-status.schema.json) defines the owner-safe, read-only polling response.

### Validation Guide

[quickstart.md](quickstart.md) defines Java/Maven verification, stable key configuration, optional Ollama setup, file-backed restart checks, security and workflow demonstrations, Timefold determinism, concurrency/idempotency tests, and appointment/legacy lifecycle validation.

## Staged Delivery Plan

### Stage 1 - Required Platform Pre-work

- Upgrade Java, Maven CI/docs/dev-container, and Spring Boot alignment.
- Delete Gradle, alternate-database, native-image, cluster/PostgreSQL, CrashController, non-English resource, and Actuator exposure paths.
- Add Spring Security, Flyway, Spring AI, Timefold, and their focused test support.
- Fix Maven validation scope and Mockito agent configuration, then prove the combined dependency/context baseline.

**Gate**: Java 21 `./mvnw verify` and dependency tree are green with no unsupported database/native/management surface.

### Stage 2 - Persistence and Security Pre-work

- Replace destructive startup SQL with the clean Flyway baseline and profile-specific file/in-memory H2 configuration.
- Add clock, account/session, encryption/key envelopes, audit/history, command/idempotency, and optimistic-version foundations.
- Split public, owner, and staff route/layout families; secure/relocate legacy owner/pet/vet/visit routes; retain CSRF.
- Seed only synthetic environment-appropriate accounts and require stable external keys.

**Gate**: Clean install, restart, schema validation, role matrix, cross-owner denial, password lifecycle, session expiry, key continuity, audit, and duplicate command tests are green.

### Stage 3 - First Functional Slice: Staff Availability and Direct Booking

- Implement clinic policy, effective-availability precedence, recurring shifts, date replacements, leave, closures, week/table calendar, and conflict previews.
- Implement `CalendarState` locking, veterinarian/pet/owner overlap checks, appointments, direct-book review/commit, and owner-agreement audit.

**Gate**: Staff can configure availability and direct-book a valid appointment; all competing/conflicting operations fail atomically in file-backed H2 tests.

### Stage 4 - Owner Requests and Manual Staff Handling

- Implement owner dashboard, active-request invariant, encrypted prose/consent revisions, structured editor, owner history, withdrawal, and fallback queue.
- Add deterministic emergency keyword screening, manual interpretation, claims/reassignment/contact, owner confirmation, and explicit emergency clearance.

**Gate**: Declined-consent, priority, emergency, stale-staff-edit, owner-revision, queue ownership, and manual booking paths pass without AI or Timefold.

### Stage 5 - Generic Held Offers and Staff Assistance

- Add one active offer/hold, expiry recovery, accept/reject/release/exclusion, owner countdown, and staff-assisted queue transitions.
- Reuse the same atomic calendar/appointment services as direct booking.

**Gate**: Assisted accept/reject/expiry, acceptance-versus-expiry, revision release, duplicate command, and restart cases pass.

### Stage 6 - Durable AI Interpretation

- Add leased job processing, owner-safe non-interactive status polling, Spring AI/Ollama adapter, structured schema validation, one permitted retry, stale-result handling, and profile-safe diagnostic logging.

**Gate**: Success, declined consent, no result, malformed output, timeout, crash/reclaim, stale revision, and staff fallback meet the ten/20-second boundaries.

### Stage 7 - Deterministic Timefold Matching

- Implement fixed calendar snapshots, candidate facts, planning solution/entity, bendable constraint score, reproducible exhaustive solve, five-second fail-closed termination, and one calendar-change retry.
- Create automatic offers only by invoking the already-proven hold service.

**Gate**: Repeated same-snapshot solves return the same eligible slot; preference order is lexicographic; incomplete/stale solves never create offers; the five-offer limit routes to staff.

### Stage 8 - Full Lifecycle and Demonstration Completion

- Add owner/staff cancellation, rescheduling, completion, no-show, append-only correction, visit projection, legacy reconciliation, and key rotation worker.
- Complete demo seeding/controlled failures and all 15 required demonstration journeys.

**Gate**: The quickstart, contracts, all measurable outcomes, restart/concurrency suites, and synthetic-data-only demo pass together.

## Testing Strategy

- Prefer framework-free tests for interval/time-zone logic, state transition policies, encryption, deterministic interpretation validation, and Timefold constraints.
- Use secured MVC slices for each controller family; explicitly import the security configuration, mock only application boundaries, and assert unauthenticated, wrong-role, foreign-owner, CSRF, validation, and successful paths.
- Use in-memory H2 JPA slices with Flyway for repository mappings/queries. The explicit H2-only product decision overrides the generic recommendation to use Testcontainers.
- Use file-backed H2 outside a shared test transaction for concurrency, restart, lease recovery, migration, and key-continuity evidence.
- Keep full-context tests to startup plus representative browser journeys; use deterministic AI/clock/failure stubs. Keep live Ollama testing opt-in.
- Add package-boundary architecture tests so owner/staff web adapters do not access repositories or other capability internals directly.

## Post-Design Constitution Check

| Gate | Post-design result | Design evidence |
|---|---|---|
| Timefold, Java 21, Maven-only | PASS | Technical context, research, Stage 1, Timefold planning model |
| H2-only phase-one persistence | PASS | Common Flyway H2 model and H2-specific test strategy; other databases removed/deferred |
| Flyway baseline and Hibernate validation | PASS | Research and Stages 1-2 define clean baseline and validation |
| Role separation before owner feature work | PASS | Route contract and Stage 2 precede owner request work |
| Durable/idempotent asynchronous workflow | PASS | Job leases, command records, versions, state contract, restart tests |
| Deterministic five-second matching | PASS | Exhaustive single-thread Timefold design rejects incomplete results |
| Sensitive-data protection and rotation | PASS | Protected-payload/key-envelope model and key-rotation lifecycle |
| Browser-complete workflow | PASS | HTML route matrix covers every owner/staff operation; JSON is read-only polling only |

No new constitutional violation or unjustified complexity was introduced by Phase 1 design.
