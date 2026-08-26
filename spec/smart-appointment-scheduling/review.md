# Spec Review: Smart Appointment Scheduling

## Summary

- Feature: Smart Appointment Scheduling
- Verdict: PASS
- Counts: 0 blockers, 0 majors, 0 minors
- Action: The feature is ready for implementation planning.

## Discipline Check

Pass. All 288 behavior IDs resolve from 357 acceptance criteria. Every behavior is covered, every criterion points to an owned behavior, all feature and local rule references resolve, local rules stay within their use case, and every criterion has a rule or `(none needed)` cross-reference. Every criterion starts with a recognized EARS form.

## Conflicts

Pass. The composed state model now includes consent waiting, valid-result review, clarification completion, replacement-text invalidation, and cancellation cleanup. Pre-confirmation fallback uses the captured request settings and a horizon materialized from `first_queued_at`; confirmed horizons are preserved. Appointment clinical routing is durable across creation and rescheduling. Calendar-conflict resolution, absolute claim deadlines, retention triggers, and the global lock order are consistent across feature and use-case rules.

## Codebase Grounding

Pass. The design has a viable home in the current Spring Boot 4.1 modular monolith. Spring MVC, Thymeleaf, JPA, message bundles, H2/MySQL/PostgreSQL profiles, MockMvc, Testcontainers, application properties, and transactional repositories support the prescribed boundaries. The existing date-only `Visit` entity and `/owners/{ownerId}/pets/{petId}/visits/new` controller confirm the UC6 migration target. The current Maven and Gradle builds use Java 17, and the explicit shared rule upgrades both to Java 21. Spring AI 2.0.1 and Timefold 2.5.0 artifacts are available in the resolved local Maven repository.

Baseline verification ran `./mvnw test` with JBR 21: 71 of 73 tests passed. Both PostgreSQL test errors occurred before test execution because Docker Compose could not bind local port 5432. H2, MySQL, MVC, integration, and concurrency tests passed; the port collision is an environment issue rather than a codebase incompatibility.

## EARS ↔ Test Strategy

Pass. Event-driven, state-driven, unwanted-behavior, combined, boundary, concurrency, and explicit negative criteria have matching test seams. Injected time, persisted absolute deadlines, operation tokens, deterministic transition and feasibility policies, pessimistic locks, unique constraints, transactional cleanup, and the three-database matrix support the added consent, replacement, fallback-horizon, clinical-routing, and conflict-resolution criteria.

## Risk Hotspots

1. Flyway baseline migration / three vendor-specific V1 scripts must reproduce current schema and sample-data semantics / compare fresh and explicitly baselined databases in the Maven matrix.
2. Cross-resource concurrency / replacement, cancellation, reservation, reschedule, and conflict cleanup share ordered locks and terminal-state checks / centralize lock acquisition and exercise opposing operations on H2, MySQL, and PostgreSQL.
3. Async AI and solver completion / text replacement, deadlines, executor rejection, restart, and late results meet at operation-token commits / use deterministic fake adapters, injected clocks, and startup-recovery tests.
4. Horizon materialization / confirmation and first fallback use different anchor instants but one captured settings model / test every fallback reason before and after confirmation at time-zone and grid boundaries.
5. Security retrofit / adding form login and CSRF changes every legacy controller test and direct route / maintain an explicit public, owner, and staff route matrix while updating legacy MVC tests.
