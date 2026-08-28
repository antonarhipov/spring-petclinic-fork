# Implementation Plan: Smart Appointment Scheduling

**Branch**: `appointment-scheduling-speckit` | **Date**: 2026-08-28 | **Spec**:
[spec.md](spec.md)

**Input**: Feature specification at
`specs/001-smart-appointment-scheduling/spec.md`

## Summary

Add secure owner and staff workflows for guided appointment scheduling. Owners
submit consented plain-English requests, confirm a structured interpretation,
and receive one atomically held offer at a time. Staff configure calendars,
manage fallback work, and manage appointments through completion or no-show.

The implementation introduces a scheduling module and server-side security,
migrates schema management to Flyway, uses Spring AI with local Ollama for
structured interpretation, and uses Timefold for single-request candidate
selection. Persistent 15-minute reservation blocks enforce hold and appointment
exclusivity independently of solver output.

## Technical Context

**Language/Version**: Java 21. The project upgrades from Java 17 because
Timefold Solver 2.5.0 requires Java 21+.

**Primary Dependencies**: Spring Boot 4.1.0; Spring MVC and Thymeleaf; Spring
Data JPA; Spring Security; Flyway; Spring AI 2.0.1 with the Ollama model
starter; Timefold Solver 2.5.0 Spring Boot starter.

**Storage**: Relational database via JPA and versioned Flyway migrations; H2 for
local development and MySQL/PostgreSQL for persistent profiles.

**Testing**: JUnit 5; MVC slices with Spring Security test support; JPA slices
and integration tests against Testcontainers databases; deterministic
interpretation stubs; Timefold constraint verification and bounded solver
integration tests.

**Target Platform**: Server-side Spring MVC web application for current desktop
and mobile browsers, deployed as one clinic instance.

**Project Type**: Modular monolithic web application with Thymeleaf-rendered
HTML forms and pages.

**Performance Goals**: Persist an owner request and show either an
interpretation or staff-handling status within 10 seconds; show one candidate
offer or staff-handling status within 5 seconds; preserve a single confirmed
booking in every same-slot concurrency attempt.

**Constraints**: One clinic in `Europe/Amsterdam` by default; English
interpretation only; 15-minute grid; 15/30/45/60-minute appointments; 1-365-day
horizon; 1-60-minute holds; two-hour owner booking notice; no owner calendar
exposure; no external notifications; no automatic movement of confirmed
appointments. Configure the Ollama model through
`spring.ai.ollama.chat.model` in `application.properties`; enforce provider and
local structural validation of every interpretation response; emit
correlation-safe debug logs for AI-to-solver hand-off without logging owner
prose or credentials.

**Scale/Scope**: POC for one clinic, one `STAFF` role, a small fixed
veterinarian/specialty catalog, one active request per pet, and a maximum of
five rejected or expired offers per request revision.

## Constitution Check

| Principle | Pre-design status | Plan evidence |
|-----------|-------------------|---------------|
| I. Specification-Driven Delivery | Pass | The approved feature spec defines 58 functional requirements, acceptance scenarios, scope boundaries, and measurable outcomes. |
| II. Domain and Access Integrity | Pass | Security, ownership enforcement, explicit request/appointment states, staff attribution, and audit history are planned as server-side rules. |
| III. Testable Behavior and Regression Coverage | Pass | The plan includes MVC security tests, service and solver tests, real-database persistence/concurrency tests, and full-context smoke coverage. |
| IV. Transactional Scheduling and Persistent Data | Pass | Flyway migrations, portable schemas, transaction boundaries, and unique reservation blocks make offers and bookings atomic. |
| V. Safe, Operable Simplicity | Pass | Local AI has bounded calls, safe staff fallback, configuration validation, no sensitive-text logging, and no out-of-scope resource scheduler. |

**Gate result (before research): PASS.** Java 21 is a deliberate runtime upgrade
needed to meet the specified Timefold 2.5.0 requirement; the constitution permits
Java 17+.

### Post-design Constitution Check

**Gate result: PASS.** The data model keeps authorization and state transitions
inside service-layer transaction boundaries. The UI contracts do not trust
client-provided owner or staff identities. Flyway and real-database tests cover
portable persistence and concurrency. Research-defined timeouts and fallback
states satisfy operational-safety requirements.

## Project Structure

### Documentation (this feature)

```text
specs/001-smart-appointment-scheduling/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── access-and-session.md
│   ├── owner-scheduling-ui.md
│   └── staff-calendar-ui.md
└── tasks.md                 # Created by /speckit-tasks
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/org/springframework/samples/petclinic/
│   │   ├── security/                    # Sign-in, accounts, authorization, session policy
│   │   ├── scheduling/
│   │   │   ├── request/                 # Requests, revisions, interpretation, owner flow
│   │   │   ├── appointment/             # Appointment lifecycle and visit integration
│   │   │   ├── availability/            # Clinic settings, shifts, exceptions, closures
│   │   │   ├── offer/                   # Offers, holds, reservation blocks, concurrency
│   │   │   ├── solver/                  # Timefold problem, constraints, candidate selection
│   │   │   ├── ai/                      # Consent-gated structured interpretation adapter
│   │   │   ├── queue/                   # Staff fallback queue
│   │   │   └── audit/                   # Auditable scheduling events
│   │   ├── owner/                       # Existing owner/pet/visit integration
│   │   ├── vet/                         # Existing veterinarian/specialty integration
│   │   └── system/                      # Shared configuration and navigation integration
│   └── resources/
│       ├── db/migration/                # Flyway baseline and forward migrations
│       ├── templates/
│       │   ├── auth/
│       │   ├── scheduling/
│       │   ├── staff/
│       │   └── fragments/
│       └── application*.properties
└── test/
    └── java/org/springframework/samples/petclinic/
        ├── security/
        └── scheduling/
            ├── request/
            ├── appointment/
            ├── availability/
            ├── offer/
            ├── solver/
            ├── ai/
            └── queue/
```

**Structure Decision**: Keep the existing single Spring Boot application and
organize new code by scheduling domain capability rather than adding a separate
frontend or service. Existing `owner`, `vet`, and `Visit` types are integrated
through narrow services; their current controller/repository pattern is not
expanded into a repository-wide refactor.
