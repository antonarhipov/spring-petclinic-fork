<!--
Sync Impact Report
- Version change: unversioned scaffold -> 1.0.0
- Modified principles:
  - Template principle 1 -> I. Specification-Driven Delivery
  - Template principle 2 -> II. Domain and Access Integrity
  - Template principle 3 -> III. Testable Behavior and Regression Coverage
  - Template principle 4 -> IV. Transactional Scheduling and Persistent Data
  - Template principle 5 -> V. Safe, Operable Simplicity
- Added sections: Technology and Security Constraints; Development Workflow
- Removed sections: none
- Follow-up TODOs: none
-->
# Spring PetClinic Fork Constitution

## Core Principles

### I. Specification-Driven Delivery
Every feature or behavior change MUST have a written specification with
testable acceptance criteria and explicit scope boundaries before implementation
begins. Plans and tasks MUST trace to those criteria. Changes outside the
approved scope require an explicit specification amendment. This prevents
accidental product expansion and keeps behavior reviewable.

### II. Domain and Access Integrity
Domain invariants, ownership checks, authorization, and state transitions MUST
be enforced on the server, not only in views or clients. A user MUST access or
mutate only records they are authorized to handle; staff actions that affect
appointments, availability, or account state MUST be attributable. This
protects clinic data and makes scheduling outcomes trustworthy.

### III. Testable Behavior and Regression Coverage
Every changed behavior MUST have automated regression coverage at the narrowest
appropriate layer. Controller changes require MVC tests, persistence rules
require data tests, and cross-layer, transaction, or database-specific behavior
requires integration tests. Tests MUST cover authorization, validation, failure,
and concurrency paths whenever the changed behavior has them. This preserves
the PetClinic application's established test-first feedback loop.

### IV. Transactional Scheduling and Persistent Data
Booking, cancellation, rescheduling, and hold transitions MUST be atomic and
must preserve calendar consistency under concurrent requests. Persistent schema
changes MUST be versioned through Flyway once migration-based schema management
is introduced; migrations and scheduling behavior MUST remain compatible with
H2, MySQL, and PostgreSQL. This prevents double booking, data loss, and
environment-specific correctness defects.

### V. Safe, Operable Simplicity
Implement the smallest solution that satisfies the specified behavior. New
external services MUST have explicit timeouts, observable failures, and a
user-safe fallback; sensitive owner, pet, credential, and AI-request data MUST
not be exposed in logs or unauthorized views. New configuration MUST use
documented defaults and validation. This keeps a POC reliable without turning
it into an unbounded platform.

## Technology and Security Constraints

The application MUST remain compatible with Java 17+ and the established Spring
Boot, Spring MVC, Thymeleaf, and Spring Data JPA architecture unless a versioned
design decision explicitly replaces part of that stack. Maven and Gradle remain
supported build paths.

Credentials, tokens, and other secrets MUST be supplied through secure runtime
configuration and MUST NOT be committed. Passwords MUST be stored only as
one-way hashes. Authentication, session protection, CSRF protection, and
server-side authorization are mandatory for authenticated capabilities.

Database behavior MUST be portable across supported profiles. H2 may support
fast local feedback but MUST NOT be the sole proof of persistence or
concurrency-sensitive behavior.

## Development Workflow

Feature work MUST begin with a specification, then a plan and dependency-ordered
tasks when the change is substantial. Design review MUST identify affected
roles, state transitions, persistence, failure handling, and test strategy.

Implementers MUST run the smallest existing relevant test command before
expanding validation to broader suites when needed. A change that modifies
database semantics, authorization, request lifecycles, or external integration
failure behavior MUST include focused tests for those contracts.

Reviews MUST reject unrelated refactors, unversioned schema changes after
Flyway adoption, exposed sensitive data, and behavior that bypasses domain or
authorization rules. Contributions MUST follow repository formatting and
contribution requirements, including the required Signed-off-by trailer.

## Governance

This constitution supersedes conflicting development practices for this
repository. Amendments require a documented rationale, impact assessment,
updated version, and review of affected specifications, plans, tasks, tests, and
documentation.

Constitution versions use semantic versioning: MAJOR for incompatible principle
removal or redefinition, MINOR for a new principle or materially expanded
governance, and PATCH for non-semantic clarifications. Every feature plan and
code review MUST assess compliance with these principles. Exceptions require a
documented, time-bounded rationale and explicit approval before implementation.

**Version**: 1.0.0 | **Ratified**: 2026-08-28 | **Last Amended**: 2026-08-28
