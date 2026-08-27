# Status: smart-appointment-scheduling

## Current

- Task: cp-6
- Status: AWAITING_APPROVAL

## Completed

- task-1.1
- task-1.2
- task-1.3
- task-1.4
- task-1.5
- task-1.6
- task-1.7
- task-2.1
- task-2.2
- task-2.3
- task-2.4
- task-2.5
- task-2.6
- task-2.7
- task-3.1
- task-3.2
- task-3.3
- task-3.4
- task-3.5
- task-3.6
- task-3.7
- task-4.1
- task-4.2
- task-4.3
- task-4.4
- task-4.5
- task-4.6
- task-4.7
- task-5.1
- task-5.2
- task-5.3
- task-5.4
- task-5.5
- task-5.6
- task-5.7
- task-6.1
- task-6.2
- task-6.3
- task-6.4
- task-6.5

## Phase Approvals

- phase-1: APPROVED
- phase-2: APPROVED
- phase-3: APPROVED
- phase-4: APPROVED
- phase-5: APPROVED
- phase-6: PENDING

## Blockers

(empty if none)

## Deviations

- Spring Boot 4 requires `spring-boot-starter-flyway` (not bare `flyway-core`) for Flyway auto-configuration.
- AuditService uses hand-rolled JSON allowlist serialization instead of Jackson ObjectMapper (jackson not reliably on compile classpath).
- Added `FilterInvocationSecurityExpressionHandler` for Thymeleaf `sec:authorize` under Spring Security 7.
- Scheduling LifecycleProcessor annotated with `@Service("schedulingLifecycleProcessor")` to prevent bean name collision with Spring framework's internal `lifecycleProcessor`.
- Follow-up request explicitly enables full Ollama prompt and response logging at INFO, overriding the original sensitive-log restriction for AI traffic.

## Notes

- Phase 1 verified across Maven and Gradle with 100 passing tests.
- Phase 2 verified across Maven and Gradle.
- Phase 3 verified across Maven (198 passing tests) and Gradle.
- Phase 4 verified across Maven (235 passing tests) and Gradle.
- Phase 5 verified across Maven (255 passing tests) and Gradle. Full test suite passing across all 28 tasks.
- task-1.5 revision: corrected `demo-data` owner usernames and passwords to match the specification; focused bootstrap tests and a live George Franklin login verified no forced password change.
- task-6.1: added portable V3 symbolic/materialized availability tables and request-owned JPA repositories; H2 fresh/baselined migration and repository replacement tests pass.
- task-6.2: replaced the lossy AI window with strict symbolic calendar fields, added clinic date/zone prompt context, persisted captured named-period bounds, and covered incompatible/invalid response shapes.
- task-6.3: added confirmation-anchored java.time expansion for relative weeks/months, named months, clipped ordinal calendar rows, weekday/time intersections, DST rejection, horizon clipping, and outside-horizon clarification.
- task-6.4: enforced materialized ALLOWED, EXCLUDED, and PREFERRED intervals through one half-open owner-window policy shared by candidate generation and live guided-hold revalidation; focused solver, reservation, weekend, and policy tests pass.
- task-6.5: exposed captured symbolic windows and clinic-local resolved intervals on request status, localized outside-horizon clarification, and verified the original next-week Thursday afternoon regression plus month, ordinal-week, DST, horizon, preference, exclusion, and weekend flows.
- Phase 6 validation: full Java 21 Maven and Gradle suites pass; Maven exercised H2, PostgreSQL, and MySQL V3 migrations. Formatter validation passes with the pre-existing `AccountBootstrapRunner` exclusion, locale bundles are synchronized, and `git diff --check` passes.
- Phase 6 validation corrections: made MySQL V3 request foreign keys unsigned to match `scheduling_requests.id`, constructor-injected the optional AI client through `ObjectProvider` for full application contexts, and restored mutually exclusive demo/non-demo account bootstrap branches.
- task-5.1 revision: normalized appointment-confirmation and staff-offer message keys to the existing bundles, added V4 data migrations for already-persisted invalid keys, and added rendering, producer, bundle-reference, and H2 migration regression coverage. The focused Java 21 suite passes all 35 tests.
