# Research: Smart Appointment Scheduling

## Java and Timefold Compatibility

**Decision**: Upgrade the runtime and toolchain from Java 17 to Java 21, then
use Timefold Solver 2.5.0.

**Rationale**: The requested Timefold 2.5.0 release requires Java 21 or later.
Spring Boot 4.1.0 supports Java 17 through Java 26, so Java 21 satisfies both
the existing application and the solver requirement.

**Alternatives considered**:

- Retain Java 17 and use Timefold 1.x. Rejected because the feature explicitly
  calls for Timefold 2.5.0.
- Replace Timefold with custom candidate ranking. Rejected because it would not
  meet the requested solver integration.

**Sources**:

- [Spring Boot system requirements](https://github.com/spring-projects/spring-boot/blob/main/documentation/spring-boot-docs/src/docs/antora/modules/ROOT/pages/system-requirements.adoc)
- [Timefold 1.x to 2.x upgrade guide](https://github.com/TimefoldAI/timefold-solver/blob/main/docs/src/modules/ROOT/pages/upgrading-timefold-solver/upgrade-from-v1.adoc)

## Dependency Integration

**Decision**: Import the Spring AI 2.0.1 and Timefold 2.5.0 BOMs, use their
Spring Boot starters, and let the Spring Boot 4.1.0 BOM manage Spring Security
and Flyway versions.

**Rationale**: Spring AI 2.0.x supports Spring Boot 4.0.x and 4.1.x. The
starters provide maintained auto-configuration while BOMs keep transitive
versions coherent across Maven and Gradle.

**Dependencies**:

- `org.springframework.boot:spring-boot-starter-security`
- `org.flywaydb:flyway-core`
- `org.springframework.ai:spring-ai-bom:2.0.1`
- `org.springframework.ai:spring-ai-starter-model-ollama:2.0.1`
- `ai.timefold.solver:timefold-solver-bom:2.5.0`
- `ai.timefold.solver:timefold-solver-spring-boot-starter:2.5.0`
- `org.springframework.security:spring-security-test` for tests

**Alternatives considered**:

- Pinning individual transitive versions. Rejected because it creates
  unnecessary incompatibility risk.
- Calling Ollama directly over ad-hoc HTTP. Rejected because the supported
  Spring AI integration provides structured output and test seams.

**Sources**:

- [Spring AI getting started](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/getting-started.adoc)
- [Spring Security dependency guidance](https://docs.spring.io/spring-security/reference/getting-spring-security.html)
- [Spring Boot 4.1 dependency management](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.0/spring-boot-dependencies-4.1.0.pom)
- [Timefold Spring Boot integration](https://github.com/TimefoldAI/timefold-solver/blob/main/docs/src/modules/ROOT/pages/running-timefold-solver/library/spring-boot.adoc)

## AI Interpretation and Ollama Operations

**Decision**: Encapsulate the AI call behind a scheduling interpretation port.
Require recorded consent before invoking it, ask for a strict structured
response, validate all returned fields server-side, and use a 10-second timeout.
Configure the Ollama base URL and model in application configuration, with the
model set through `spring.ai.ollama.chat.model=gemma4:latest` by default.

**Rationale**: A port allows deterministic test doubles and makes
unavailability a normal, safe fallback path. The model cannot establish its own
confidence or bypass validation; only validated data may reach matching.

**Request-specific options**: Build the baseline client from the
auto-configured `ChatClient.Builder`. When an interpretation needs
request-specific options, clone the baseline with `ChatClient.mutate()` and
create a new client with the relevant `OllamaChatOptions`; do not mutate shared
client state. Put transport timeout and bounded retry policy at the Ollama
transport/model boundary, and preserve the 10-second end-to-end request
deadline. Retrying must be limited to transient failures before any successful
response is persisted.

**Structural response enforcement**: Request provider-level JSON Schema output
and invoke `entity(Interpretation.class, spec ->
spec.useProviderStructuredOutput().validateSchema())`. Follow it with
application validation for required fields, configured duration, known
specialties/veterinarians, time-window consistency, and safety-critical
uncertainty. A failure at either layer routes the request to staff; it never
creates an offer.

**Diagnostic logging**: At `DEBUG`, log a generated correlation ID, request and
revision identifiers, configured model name, option overrides, call start/end,
elapsed time, retry count, response metadata, structural-validation result,
business-validation outcome, fallback reason, solver candidate count, selected
slot identifier, score, and solver elapsed time. Never log raw owner prose,
full model output, passwords, or session identifiers. At `INFO`/`WARN`, emit
only aggregated or failure-safe operational events.

**Operational choice**: Use `gemma4:latest` for the POC as specified. Pre-pull
the model in deployment environments; do not rely on runtime auto-pull.

**Alternatives considered**:

- Treat model output as directly schedulable. Rejected because it violates the
  confirmed-owner and safety-critical review rules.
- Automatically retry all successful interpretations. Rejected because it
  breaks consent and audit reproducibility.
- Pinning a concrete Gemma tag now. Deferred: `latest` is an explicit POC
  choice; pin a digest/tag before production release.

**Sources**:

- [Spring AI Ollama chat integration](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/ollama-chat.adoc)
- [Spring AI ChatClient structured output](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chatclient.adoc)
- [Spring AI Testcontainers support](https://github.com/spring-projects/spring-ai/blob/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/testcontainers.adoc)
- [Ollama Gemma 4 model page](https://ollama.com/library/gemma4:latest)

## Schema Migration and Seed Data

**Decision**: Replace startup SQL initialization with Flyway migrations. Create
one vendor-specific Flyway baseline for the existing schema and seed IDs, then
add forward migrations for security and scheduling. Keep future migrations
portable where possible and profile-select vendor-specific baseline locations.

**Rationale**: The existing application has separate H2, MySQL, and PostgreSQL
SQL scripts and tests depend on seed identities. A one-time baseline preserves
those contracts while allowing all later schema changes to be ordered and
reviewed.

**Alternatives considered**:

- Continue `spring.sql.init` alongside Flyway. Rejected because two schema
  owners create non-deterministic initialization order.
- Recreate seed data with new identifiers. Rejected because it breaks existing
  test and demo contracts.
- Use a single untested cross-database baseline SQL script. Rejected because
  the existing schema contains dialect-specific syntax.

## Atomic Holds and Bookings

**Decision**: Represent every held or confirmed appointment as persistent
15-minute reservation blocks for both its veterinarian and pet. Enforce a
unique database constraint on each resource/time block. Create, promote, and
release blocks inside a transaction.

**Rationale**: The solver selects a candidate but cannot alone guarantee that
two concurrent requests do not claim the same time. Reservation blocks make
overlap prevention and lease ownership explicit, database-enforced, and
portable across supported stores.

**Alternatives considered**:

- Check availability before saving an appointment. Rejected because concurrent
  requests can pass the same read check.
- Lock the complete appointments table. Rejected because it harms concurrency
  and does not naturally represent unconfirmed holds.
- Use only optimistic version checks on appointments. Rejected because a
  candidate interval may not yet have an appointment row and can overlap more
  than one existing interval.

## Single-Request Timefold Model

**Decision**: Solve one confirmed request at a time. Generate candidates from
the configured 15-minute grid and valid veterinarian availability, then let a
single planning entity choose one candidate. Hard constraints reject invalid
slots; soft constraints rank owner preference, earliest time, and a stable
calendar-efficiency tie-breaker. Terminate solving after five seconds.

**Rationale**: It directly implements a one-offer owner experience and leaves
confirmed appointments immutable. Reservation blocks are the source of truth
for conflicts before an offer is persisted.

**Alternatives considered**:

- Reoptimize all confirmed appointments on every request. Rejected because
  confirmed appointments must not move automatically.
- Batch waiting requests. Rejected because the POC does not auto-offer new
  capacity and must present one owner-controlled offer at a time.

**Sources**:

- [Timefold Spring Boot quickstart](https://github.com/TimefoldAI/timefold-solver/blob/main/docs/src/modules/ROOT/pages/quickstart/spring-boot/spring-boot-quickstart.adoc)
- [Timefold constraint streams and scoring](https://github.com/TimefoldAI/timefold-solver/blob/main/docs/src/modules/ROOT/pages/constraints-and-score/score-calculation.adoc)

## Security, Ownership, and MVC Integration

**Decision**: Add form-login session security with `OWNER` and `STAFF` roles.
Resolve the signed-in owner server-side for every owner route, require CSRF
protection for state-changing forms, and render role-aware Thymeleaf navigation.
Use a temporary-password gate that allows only password change and sign-out
until the change completes.

**Rationale**: Existing controllers receive owner IDs from routes and have no
security layer. Server-side ownership resolution prevents URL manipulation from
exposing other owners' records.

**Alternatives considered**:

- Trust owner IDs posted by forms. Rejected because it violates owner isolation.
- Build a separate JavaScript frontend. Rejected because the existing
  application is a server-rendered Thymeleaf application.

## Test Strategy

**Decision**: Test pure scoring and state transitions as unit tests; test
repositories, unique reservation constraints, and migrations on real
Testcontainers databases; test pages and authorization with secured MVC slices;
and add full-context smoke plus request-level end-to-end coverage.

**Rationale**: H2 alone can mask SQL and locking behavior. A layered suite
isolates fast business rules while proving the security and persistence wiring.

**Alternatives considered**:

- H2-only persistence tests. Rejected because holds, unique constraints, and
  Flyway behavior must work on persistent database profiles.
- Full-context tests for every rule. Rejected because they are slower and make
  failure diagnosis harder.

**Sources**:

- [Spring Security servlet testing](https://docs.spring.io/spring-security/reference/servlet/test/index.html)
- [Timefold constraint verification](https://github.com/TimefoldAI/timefold-solver/blob/main/docs/src/modules/ROOT/pages/constraints-and-score/score-calculation.adoc)
