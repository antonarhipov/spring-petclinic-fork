# Implementation Plan: Smart Appointment Scheduling

**Branch**: `001-smart-appointment-scheduling` | **Date**: 2026-08-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-smart-appointment-scheduling/spec.md`

## Summary

Extend the Spring PetClinic modular monolith with authenticated owner and staff scheduling. Owners submit one pet-specific request, explicitly consent to an LLM interpretation, confirm deterministic structured data, and receive one exclusively held slot selected by Timefold. Persisted background work and a polling status endpoint make interpretation and solving resumable. Staff configure capacity, handle fallbacks, and manage appointment/visit lifecycles. A portable 15-minute reservation-block ledger provides atomic hold and appointment conflict protection on H2, MySQL, and PostgreSQL, while Flyway adopts the existing schema and owns all future migrations.

## Technical Context

**Language/Version**: Java 21. The repository currently targets Java 17; both build toolchains must move together because Timefold Solver 2.5.0 publishes Java 21 bytecode.

**Primary Dependencies**: Spring Boot 4.1.0; Spring MVC and Thymeleaf; Spring Data JPA; Spring Security with BCrypt; Spring Session JDBC; Spring AI 2.0.1 with the Ollama chat model `gemma4:latest`; Timefold Solver 2.5.0; Flyway; Jackson 3; Jakarta Bean Validation.

**Storage**: Existing H2, MySQL, and PostgreSQL deployments through JPA. Flyway uses vendor-specific baseline and scheduling migrations where SQL dialects differ. Versioned LLM/solver payloads and audit before/after values are stored as portable text containing canonical JSON.

**Testing**: JUnit 5, AssertJ, Mockito, `@WebMvcTest`/`MockMvcTester`, `@DataJpaTest`, Spring Security test support, pure score-policy and full Timefold solver tests, deterministic fake LLM/solver gateways, Testcontainers for MySQL and PostgreSQL, H2 migration smoke tests, and thin request-level end-to-end tests.

**Target Platform**: Single-node Spring Boot web application on a Java 21 JVM; desktop browsers only for the POC. Local Ollama is the LLM backend.

**Project Type**: Server-rendered web application with form-based MVC pages and one authenticated JSON polling interface.

**Performance Goals**: Complete or safely route every interpretation within 10 seconds total; complete or safely route every matching attempt within 5 seconds total; preserve those limits with 10,000 scheduling records and 25 concurrent authenticated users.

**Constraints**: Synthetic/demo data only; one clinic and one immutable clinic time zone after scheduling begins; exactly one owner-facing offer at a time; every automated owner suggestion is selected by Timefold; at most one LLM retry and one stale-snapshot solver rerun within their original deadlines; no conflicting veterinarian or pet reservations; no mobile or formal accessibility conformance requirement; Maven and Gradle dependency/build behavior remain synchronized.

**Scale/Scope**: One clinic, the existing veterinarian/specialty catalog, four desktop owner flow workspaces plus owner history, four staff workspaces plus account and audit actions, 25 concurrent users, and 10,000 scheduling records. External notifications, waitlists, self-registration, multiple clinics, and resources other than veterinarian and pet are excluded.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

The constitution at `.specify/memory/constitution.md` is an unratified placeholder and defines no enforceable project principles or gates. The plan therefore has no constitution violation. Repository-derived safeguards are nevertheless explicit: preserve all existing records, support every current database profile, keep both build definitions aligned, keep controllers thin, use transactional services for mutations, and verify security and concurrency at the framework/database boundaries.

**Post-design re-check**: PASS. The Phase 1 model and contracts remain inside the existing single-application architecture, introduce no extra deployable service, preserve the supported databases, and contain no unresolved clarification or constitution conflict.

## Project Structure

### Documentation (this feature)

```text
specs/001-smart-appointment-scheduling/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── forms.md
│   ├── llm-boundary.md
│   ├── llm-interpretation-v1.schema.json
│   ├── mvc-routes.md
│   ├── polling-api.yaml
│   └── solver-boundary.md
└── tasks.md                              # created later by /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/org/springframework/samples/petclinic/
├── account/
│   ├── Account.java
│   ├── AccountRepository.java
│   ├── AccountService.java
│   ├── AccountWebController.java
│   ├── AccountBootstrap.java
│   └── SecurityConfiguration.java
├── owner/                                # existing Owner, Pet, Visit catalogs
├── vet/                                  # existing Vet and Specialty catalogs
├── scheduling/
│   ├── request/                          # request, text/revision, consent, workflow services
│   ├── interpretation/                   # Spring AI gateway, schema validation, emergency screen
│   ├── matching/                         # Timefold model, scoring policy, snapshot and orchestration
│   ├── availability/                     # clinic policy, shifts, exceptions, leave, closures
│   ├── appointment/                      # offers, holds, reservation blocks, appointments, visits
│   ├── queue/                            # staff fallback queue and claims
│   ├── audit/                            # audit and integration execution records
│   ├── job/                              # persisted background-work dispatcher
│   └── web/
│       ├── owner/                        # full-page owner controllers and polling controller
│       └── staff/                        # queue, calendar, availability and settings controllers
└── system/                               # existing shared web configuration

src/main/resources/
├── db/migration/
│   ├── h2/
│   ├── mysql/
│   └── postgres/
├── prompts/appointment-interpretation-v1.st
├── schemas/llm-interpretation-v1.schema.json
├── templates/
│   ├── account/
│   ├── scheduling/owner/
│   └── scheduling/staff/
└── application*.properties

src/test/java/org/springframework/samples/petclinic/
├── account/
├── scheduling/
│   ├── unit/                             # workflow, validation, scoring and time-zone rules
│   ├── web/                              # MVC/security/JSON slice tests
│   ├── persistence/                      # JPA and Flyway profile tests
│   └── integration/                      # AI adapter, solver, races and lifecycle tests
└── support/                              # shared Testcontainers and deterministic fakes
```

**Structure Decision**: Keep one Spring Boot deployment and organize new code by scheduling subdomain rather than global technical layers. The `account` module owns authentication and owner linkage; `scheduling` owns all feature workflows and accesses existing owner/veterinarian catalogs through repositories or narrow services. Integration adapters stay behind `interpretation` and `matching` boundaries so tests never need a live model or nondeterministic solver execution.

## Complexity Tracking

No constitution violations require justification. The persisted work queue and reservation-block ledger are necessary domain safeguards for resumable processing and cross-database overlap prevention, not additional deployable components.
