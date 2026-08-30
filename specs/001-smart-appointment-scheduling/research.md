# Phase 0 Research: Smart Appointment Scheduling

## Source of Truth

**Decision**: Use `specs/001-smart-appointment-scheduling/spec.md`, including its 2026-08-30 clarification session, as the authoritative scope for this plan. The earlier proposal remains background only where it does not conflict with the clarified spec.

**Rationale**: The user explicitly deferred MySQL and PostgreSQL after the proposal was written, removed care type, required owner-level conflict prevention, and resolved five later ambiguities. Planning against the older conflicting statements would knowingly reverse those decisions.

**Alternatives considered**: Updating `proposal/proposal.md` during planning was rejected because this command owns design artifacts, not the source proposal. Treating both documents as equally authoritative was rejected because their database scopes conflict.

## Java, Build, and Boot Alignment

**Decision**: Move the single Maven module to Java 21 and Spring Boot 4.1.1. Keep Maven Wrapper 3.9.16 and remove Gradle files, wrapper, CI, badge, documentation, and dev-container references. Import Spring AI BOM 2.0.1, add `spring-ai-starter-model-ollama`, and add `timefold-solver-spring-boot-starter` 2.5.0.

**Rationale**: Timefold 2.5.0 targets Java 21 bytecode. The current project targets Java 17 in Maven, Gradle, CI, documentation, and development-container configuration. Spring AI Ollama 2.0.1 declares Spring Boot 4.1.1 dependencies, so upgrading the project from Boot 4.1.0 avoids a mixed Boot patch level. One build definition prevents dependency drift.

**Alternatives considered**: Java 17 cannot load Timefold 2.5.0. Downgrading Spring AI's Boot dependencies creates avoidable framework skew. Keeping both Maven and Gradle contradicts the confirmed constraint.

**Validation required during implementation**: Run a Java 21 dependency tree and context smoke test with Boot 4.1.1, Spring AI 2.0.1, and Timefold 2.5.0 before feature code. Boot 4.1.1 was not cached during planning, so this combination was not executed here.

## Runtime Surface Cleanup

**Decision**: Remove the MySQL/PostgreSQL drivers, profiles, scripts, container/deployment assets, and database-specific tests. Remove native-image/AOT support and runtime hints. Remove Actuator runtime/test starters and the wildcard endpoint exposure. Remove `CrashController` and its tests/navigation. Keep only English messages.

**Rationale**: H2, JVM deployment, English UI, and no externally exposed management endpoints are explicit phase-one boundaries. Dormant unsupported paths would create false compatibility claims and continuing maintenance.

**Alternatives considered**: Leaving unused database, native-image, and Actuator configuration in place was rejected because it would still affect dependency resolution, CI, route security, and documentation.

## H2 and Schema Evolution

**Decision**: Use file-backed H2 for normal and demonstration runs and in-memory H2 for tests. Use a separate demo database. Do not enable H2 `AUTO_SERVER`; a second application instance must fail. Replace startup SQL with Flyway: `V1__legacy_petclinic_baseline.sql` preserves the current schema and catalog/sample records, later migrations add the feature, `spring.sql.init.mode=never`, and `spring.jpa.hibernate.ddl-auto=validate`. Do not enable Flyway baseline-on-migrate.

**Rationale**: The current H2 startup script drops all tables, which violates restart recovery. A clean baseline is safe because upgrading an unknown pre-Flyway installation is outside this phase. Flyway plus Hibernate validation makes schema ownership explicit.

**Alternatives considered**: Continuing `schema.sql`/`data.sql` cannot provide ordered upgrades. In-memory H2 for the running application loses durable jobs and offers. A demo-only Flyway location risks validation drift when profiles change.

**Data setup**: Common Flyway migrations own schema and stable catalog/sample data. A demo-only transactional seeder, guarded by a seed-version record, creates workflow scenarios and predictable accounts. Tests load explicit fixtures. Runtime database files and key material are ignored by version control.

## Modular Monolith and Transaction Boundaries

**Decision**: Retain one server-rendered Spring MVC/Thymeleaf application. Add capability packages for `account`, `availability`, `scheduling`, `appointment`, `audit`, `security`, `shared`, and `config`. Controllers bind command DTOs and call transactional application services; repositories are not called directly from controllers. New mutable aggregates use optimistic versions, lazy relationships, explicit mappings, and repositories only at aggregate roots.

**Rationale**: The feature requires authorization, state transition, durable job creation, command completion, and audit append to commit together. Capability packages extend PetClinic's existing owner/vet organization without adding a deployment boundary.

**Alternatives considered**: Separate frontend/backend or new Maven modules add coordination and transactions without value for one instance. Global controller/service/repository packages would scatter each workflow.

## Authentication, Authorization, and Sessions

**Decision**: Add Spring Security form login and security-test support. Use disjoint route families: public landing/login/static resources, `/owner/**` for `OWNER`, `/staff/**` for `STAFF`, and a small shared authenticated account/session surface. Staff acting for an owner continue through staff routes. Resolve owner identity from the authenticated principal; owner routes never accept `ownerId`. Return 404 for a foreign owner resource and 403 for a cross-role route.

**Rationale**: Existing anonymous ID-based routes allow direct access to any owner, pet, or visit. Route separation plus owner-scoped queries makes object ownership a default rather than a repeated controller convention.

**Alternatives considered**: Link hiding does not enforce authorization. One shared route family with scattered method checks is prone to object-level authorization gaps.

**Session decision**: Store authoritative `lastInteractiveAt` in the session, check it on every authenticated request, and update it only for explicit navigation or user action. Polling and static requests do not extend the 30-minute deadline. Use a configurable two-minute warning and a CSRF-protected explicit stay-signed-in action. Reset increments an account `sessionVersion`; a request filter invalidates principals with an older version.

## Browser Commands and Idempotency

**Decision**: Keep CSRF enabled. Every mutation is POST with an explicit Thymeleaf action, CSRF token, issued `commandId`, expected aggregate version, and current revision/version where relevant. Successful commands follow Post/Redirect/Get. A persisted `CommandRecord` is issued before form display, locked on submission, and completed in the same transaction as the business change and audit event. Duplicate submissions return its canonical outcome.

**Rationale**: CSRF validates request origin but does not make retries safe. Persisting an issued command before submission avoids concurrent unique-insert handling after a transaction is marked rollback-only and survives restart.

**Alternatives considered**: Browser-only double-submit prevention fails on retries and multiple tabs. Creating the command record only during mutation complicates concurrent duplicate handling.

## Atomic Calendar and Conflict Protocol

**Decision**: Use a singleton `CalendarState` row as a pessimistic H2 mutex and monotonic snapshot revision. Every hold, acceptance, release/expiry, direct booking, reschedule, cancellation, and availability mutation runs in a short transaction that locks this row, validates command/revision state, evaluates time from one injected `Clock`, checks effective availability and interval overlap, changes state, appends audit, increments the calendar revision where required, and commits.

Conflict uses half-open intervals: `existing.start < candidate.end AND existing.end > candidate.start` for veterinarian, pet, and owner. Expired holds cease blocking when `expiresAt <= now` even before cleanup is materialized.

**Rationale**: A query-then-insert under ordinary isolation cannot prevent variable-length overlaps. The singleton lock is intentionally conservative and sufficient for a one-clinic, one-instance H2 proof of concept.

**Alternatives considered**: Unique 15-minute resource segments scale better but add substantial schema and lifecycle complexity. An in-memory lock does not coordinate correctly with transaction rollback or restart.

## Availability and Time Semantics

**Decision**: Store business instants as `Instant` plus the snapshotted IANA clinic zone. Generate appointment starts on the fixed local 15-minute grid. Compute effective veterinarian availability in precedence order: clinic closure, veterinarian leave, date-specific replacement schedule, recurring shifts. A date exception replaces the entire recurring schedule for that veterinarian and local date. Named periods are copied as concrete local windows into each confirmed workflow revision.

**Rationale**: The clarified replacement rule removes ambiguity between calendar views. Instant plus zone makes daylight-saving transitions and historical display unambiguous.

**Alternatives considered**: Layered add/remove exceptions were rejected during clarification. Storing only local timestamps cannot distinguish repeated or skipped daylight-saving times.

## Durable Interpretation and Matching Work

**Decision**: Persist database-backed jobs and process them with one scheduled worker. Claim an eligible job in a short transaction, assign a random lease token and expiry, perform external or solver work without a database transaction, then complete only if the lease and target revision are still current. Reclaim expired leases on restart. Start offer-expiry recovery before normal job processing.

External AI invocation is at-least-once around a process crash; business effects are exactly-once through job, version, and command checks. "No usable result" means no validated result was durably committed. Only that condition permits the one AI retry. Matching permits one retry only after a changed calendar revision.

**Rationale**: An in-memory asynchronous method can lose a persisted request after a crash. No local transaction can atomically include an Ollama call, so exact-once invocation is impossible; exact-once committed effect is achievable.

**Alternatives considered**: A memory executor was rejected for durability. Holding database transactions during AI or solving was rejected for contention and failure behavior.

## AI Interpretation Boundary

**Decision**: Put Spring AI/Ollama behind an application `InterpretationClient` port. Use the schema in `contracts/interpretation-output.schema.json`, runtime-configured base URL/model, and an application-enforced ten-second deadline. Do not pull a model at startup. Ordinary tests use deterministic stubs; an opt-in tagged integration test may use a locally installed model.

**Rationale**: Model availability and hardware must not make the normal suite nondeterministic. Deterministic post-validation remains authoritative for configured values, contradictions, dates, specialty, and urgency. A timeout still satisfies the 20-second user outcome by routing to staff.

**Alternatives considered**: Direct Ollama HTTP calls lose Spring AI integration. Live-model calls in every test are slow and non-repeatable. A compiled-in model identifier contradicts configurability.

**Demo prerequisite**: The model property is required when AI is enabled. Quickstart uses a documented example only; any installed model must pass the structured-output smoke scenario before the successful-AI demo is claimed.

## Timefold Matching

**Decision**: Timefold 2.5.0 is the matching engine; do not introduce a separate slot-ranker abstraction. Model one routine request as a planning solution with one assignment and a finite, stable-ordered set of candidate slots from the fixed calendar snapshot. Use a Timefold constraint provider with one hard level and lexicographically ordered soft levels for owner preference, preferred veterinarian, lower-ranked fallback windows, earliest time, clinic efficiency, and stable veterinarian/time tie-break. Use single-threaded reproducible mode and deterministic exhaustive search. Apply a five-second wall-clock limit; if exhaustive completion cannot be proven, discard the partial best solution and route to staff.

After solving, reacquire the `CalendarState` lock and create a hold only if the snapshot revision is unchanged. If changed, take one new snapshot and solve once more; another change routes to staff.

**Rationale**: One planning variable makes exhaustive candidate evaluation tractable for the single-clinic scope and yields a deterministic optimum. Rejecting partial timeout results reconciles determinism with the five-second maximum.

**Alternatives considered**: A time-limited local-search best solution can vary with machine speed. A custom slot ranker would violate the Timefold requirement. Accepting a stale solution would race calendar mutations.

## Queue, Assisted Offers, and Emergency Clearance

**Decision**: Keep request, job, queue, offer, and appointment states separate. Queue assignment is independent of state and persists across owner revision unless explicitly removed. A staff-assisted offer moves the item to `AWAITING_OWNER`; rejection/expiry releases and excludes the slot, returns the same assigned item to `IN_REVIEW`, and does not consume an automatic attempt. Emergency clearance creates a new owner-unconfirmed workflow revision with a staff-selected `ROUTINE` or `PRIORITY` urgency and recorded reason. After owner reconfirmation, only routine work is enqueued for matching.

**Rationale**: These are explicit clarification results and prevent one overloaded status field from driving unrelated behavior.

**Alternatives considered**: Collapsing queue and request states loses ownership/contact meaning. Automatically downgrading emergency suspicion is unsafe.

## Protected Data and Key Rotation

**Decision**: Use application-level envelope encryption. Each immutable protected artifact is canonical UTF-8 JSON encrypted with a random data key using AES-256-GCM and a unique 96-bit nonce. Authenticate artifact identity/type/schema as additional data. Wrap each data key with a configured key-encryption key and store versioned envelopes separately. Configuration supplies a stable key ring and active key ID; secrets never enter source, migrations, the database, logs, or error pages.

New payloads use the active key. A resumable job rewraps data keys under a new active key without rewriting ciphertext or append-only audit events. An old key cannot be removed until no current envelope references it. Missing active keys fail startup; a missing historical key makes only affected content unavailable and must never fall back to plaintext.

**Rationale**: Encrypting the H2 file alone cannot provide per-record versioned rotation. Envelope rewrapping limits write volume and preserves immutable audit payloads.

**Alternatives considered**: Re-encrypting every protected column is simpler but rewrites immutable records and makes interruption recovery harder.

## Legacy Visit Reconciliation

**Decision**: Preserve existing visits as `LEGACY`. Appointment completion creates a separate `APPOINTMENT_COMPLETION` visit linked uniquely to the appointment. A reconciled appointment points to its legacy source without altering that visit. Disable direct creation of future visits when scheduling becomes the only future-care path.

Current visits contain only date and description, not veterinarian, exact interval, or proof of owner agreement. Therefore every currently stored future visit must collect a new exact slot and fresh owner agreement during reconciliation. The clarification's no-fresh-agreement exception applies only if a future source supplies and proves unchanged veterinarian, interval, and prior agreement.

**Rationale**: This preserves historical data while avoiding invented agreement metadata.

**Alternatives considered**: Treating a date-only visit as an appointment would fabricate capacity and consent. Reusing the legacy row on completion would erase its provenance.

## Testing Strategy

**Decision**: Use plain JUnit tests for interval algebra, states, encryption, AI validation, and Timefold constraints; Spring MVC slices with imported security configuration for routes/views; JPA slices using in-memory H2 plus Flyway; file-backed H2 tests for clean migration, Hibernate validation, restart recovery, and concurrent service calls; and a small number of full-context and request-level demonstration tests. Inject `Clock`, randomness, `InterpretationClient`, and matching boundaries.

Configure Mockito/Byte Buddy as an explicit Maven Surefire Java agent while preserving JaCoCo's argument line. Restrict NoHttp checks to product-owned files so tool-managed `.agents` documents do not fail the build.

**Rationale**: Complementary unit, slice, persistence, and end-to-end tests isolate failures while proving framework wiring. The project's explicit H2-only target overrides generic guidance to use a containerized production database.

**Alternatives considered**: Only full-context tests would be slow and opaque. Only unit tests would miss security, persistence mappings, migrations, and transaction behavior.

## Delivery Sequence

**Decision**: Implement in eight stages:

1. Java/Maven/Boot/H2/Flyway cleanup and compatibility smoke test.
2. Encryption, audit, command, clock, and account/security foundations.
3. Clinic policy, availability, atomic conflict service, and staff direct booking.
4. Owner requests, immutable consent/revisions, queue, manual interpretation, and emergency clearance.
5. Generic staff-assisted held offers and owner acceptance/rejection/expiry.
6. Durable interpretation jobs, Spring AI/Ollama adapter, and non-interactive status polling.
7. Timefold deterministic matching and automatic offers.
8. Rescheduling/cancellation, completion/no-show/correction, legacy reconciliation, demo seeding, and all end-to-end journeys.

**Rationale**: This proves security, persistence, availability, conflict handling, and the complete manual path before adding external AI and automated optimization.

**Alternatives considered**: Starting with AI or Timefold would build on absent security, migration, transaction, and calendar foundations and create avoidable rework.
