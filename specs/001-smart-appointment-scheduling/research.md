# Phase 0 Research: Smart Appointment Scheduling

## 1. Runtime and synchronized builds

**Decision**: Upgrade the Maven `java.version` and Gradle Java toolchain from 17 to 21. Import the Spring AI 2.0.1 BOM in Maven and its Gradle equivalent, and pin Timefold Solver 2.5.0 in both builds. Add matching Security, Spring Session JDBC, Flyway, Spring AI Ollama, Timefold, and test dependencies to both build files in the same implementation task. Keep the application on Spring Boot 4.1.0, whose dependency management must override the Spring AI Ollama starter's explicit Boot 4.1.1 web-client transitive declarations; verify resolved graphs and both test suites before implementation is accepted.

**Rationale**: Timefold 2.5.0 is aligned with Spring Boot 4.1.0 but its published manifest and build parent require Java 21. The repository deliberately supports both Maven and Gradle, so a change that compiles in only one build is incomplete.

**Alternatives considered**: Retaining Java 17 with an older Timefold version contradicts the proposal's explicit 2.5.0 choice. Calling Timefold as another service would add an unnecessary deployment and still require planning-model compatibility.

## 2. Application and package architecture

**Decision**: Keep a single Spring Boot modular monolith. Add a top-level `account` module and a `scheduling` module split into request, interpretation, matching, availability, appointment, queue, audit, job, and web subpackages. Controllers bind and validate web input, transactional application services own state changes, and adapters isolate Ollama and Timefold.

**Rationale**: The feature has several cohesive subdomains but shares authoritative PetClinic owner, pet, veterinarian, specialty, and visit data. Module-based packages preserve those boundaries without introducing network transactions or another deployment for a POC.

**Alternatives considered**: A flat controller/service/repository layout would obscure ownership and allow accidental cross-layer coupling. Microservices would create distributed consistency problems around holds and appointments without a scale requirement that justifies them.

## 3. Authentication and account bootstrap

**Decision**: Use Spring Security form login, BCrypt hashes, two authorities (`OWNER`, `STAFF`), CSRF protection, and a database-backed `Account` linked one-to-zero-or-one with `Owner`. Use Spring Session JDBC so a password reset can delete every session for that principal. Configure 30-minute inactivity expiry and session-fixation protection, and explicitly rotate the current session identifier after a password change. Seed predictable accounts only under `local`, `demo`, and `test`. Require initial staff username/password configuration in deployed profiles and fail startup when it is absent. Do not add failed-login throttling in this POC.

**Rationale**: This satisfies the clarified POC boundary while keeping authorization enforceable at both route and service/repository ownership checks. Profile-gated bootstrap prevents demo credentials from escaping into deployed environments, and JDBC session lookup makes reset invalidation deterministic.

**Alternatives considered**: In-memory users cannot support provisioning, resets, or owner linkage. A process-local session registry cannot guarantee invalidation after topology changes. A separate identity provider is outside the proposal.

## 4. Flyway adoption across H2, MySQL, and PostgreSQL

**Decision**: Disable `spring.sql.init` and set Flyway locations to `classpath:db/migration/${database}`. For each database, create `V1__legacy_petclinic_schema.sql` containing the equivalent existing schema and `V2__smart_appointment_scheduling.sql` for new tables, constraints, indexes, and seed policy. Keep demo catalog data in profile-selected repeatable migrations or bootstrap fixtures. Use a one-time migration profile with `baseline-on-migrate=true` and baseline version 1 for existing non-empty schemas, then disable it: a pre-existing schema receives a version-1 marker and V2, while a fresh database executes V1 then V2.

**Rationale**: The current scripts already contain vendor-specific identity, case-insensitive, and idempotency syntax. Vendor-specific migrations are clearer and safer than pretending all DDL is portable. Version 1 preserves existing IDs and records; version 2 only adds or explicitly extends them.

**Alternatives considered**: Running Flyway alongside `spring.sql.init` creates two schema owners. A single common SQL directory cannot express the current dialect differences safely. Permanently enabling automatic baselining could silently accept an unexpected schema. Rebuilding existing schemas would violate the preservation requirement.

## 5. Persisted asynchronous interpretation and matching

**Decision**: Persist an `IntegrationExecution` as the durable job record in the same transaction that advances a request to `INTERPRETING` or `MATCHING`, then dispatch its ID after commit to a bounded `ThreadPoolTaskExecutor`. A unique trigger key makes repeated POSTs and page refreshes idempotent. Workers claim executions transactionally, append attempts, and finish by advancing the request or creating the single queue item. The absolute deadline begins at trigger time, so queue delay consumes the budget. On startup, pending executions are redispatched; abandoned running work whose deadline has passed is failed safely to staff. The processing page polls a read-only operation endpoint and never starts work.

**Rationale**: HTTP requests return promptly, refresh/resume does not duplicate expensive work, and failures never lose the retained request. Persisted job state is sufficient for the POC without adding a broker.

**Alternatives considered**: Holding the servlet request open conflicts with the resumable full-page UX. Plain `@Async` without a durable job record loses work and idempotency on restart. Kafka or RabbitMQ is unnecessary at the required scale.

## 6. Spring AI and Ollama structured output

**Decision**: Place a versioned JSON Schema and prompt template in application resources. The Spring AI adapter passes the schema through `OllamaChatOptions.outputSchema`, captures the raw response, and disables Spring AI's framework retry and `.validateSchema()` retry behavior. Application validation then parses a Jackson 3 tree, records unknown property paths and values, removes them from the recognized view, binds known fields to an immutable DTO, applies schema/type/format and Jakarta validation, and applies deterministic clinic-value and cross-field rules. Only this normalized validated DTO can create an interpretation. An application-owned loop uses one monotonic 10-second deadline across at most two calls; it retries only transient transport failures or invalid/no structured result, never a valid but uncertain result. Retain both the requested alias and Ollama's resolved model identifier/digest because `gemma4:latest` is mutable.

**Rationale**: Provider-side formatting improves conformance but is not a trust boundary. The second validation layer enforces required fields and configured values while meeting the special rule that unknown fields are retained for audit but ignored operationally.

**Alternatives considered**: Prompt-only JSON instructions do not enforce structure. Directly binding while ignoring unknown fields would lose evidence. Failing the entire response on unknown fields contradicts the clarification. Spring AI's built-in schema validation can repeat more than once, and its general retry defaults can add further calls; either would violate the exact attempt budget. Giving each retry a fresh 10 seconds would violate the total deadline.

## 7. Timefold planning model and deterministic selection

**Decision**: Model one request as one nullable planning entity whose planning variable is a generated 15-minute-grid `CandidateSlot`. Supply request facts, clinic/veterinarian availability, appointments, active holds, and revision exclusions from an immutable, versioned snapshot. A stateless `EasyScoreCalculator<SlotSelectionSolution, BendableScore>` delegates to one pure `SlotScorePolicy`; that policy returns both the exact lexicographic score and a domain-owned component/explanation object. Configure a construction heuristic with `ALLOCATE_ENTITY_FROM_QUEUE`, stable candidate order, reproducible mode, fixed seed, default `moveThreadCount=NONE`, and `pickEarlyType=NEVER`, with no local-search phase. This evaluates the best value for the single entity deterministically. Hard score is zero only for eligible candidates; null or ineligible values are negative. A shared deadline watchdog calls `terminateEarly()` at expiry and marks the attempt expired; because Timefold may return a partial best without a public termination cause, only a naturally completed solve returned before the monotonic deadline is offerable. Use separate `PREFERRED_ONLY` and owner-authorized `ALLOWED_FALLBACK` solve modes.

**Rationale**: Every owner-visible automated choice is made by Timefold, including fallback and subsequent offers. Evaluating one planning variable without local search avoids wall-clock-dependent heuristic results and meets repeatability. The shared score policy produces reproducible audit evidence because Timefold 2.5 Community Edition does not expose the former score-explanation API; enterprise-only `SolutionManager.analyze` is not used. Staff direct bookings deliberately bypass Timefold but reuse the same eligibility service.

**Alternatives considered**: Sorting and choosing a slot in application code would violate the Timefold-only decision. A time-limited local-search result may differ between identical runs. A `ConstraintProvider` plus duplicated explanation formulas risks drift, and Community Edition score analysis cannot be used to derive the required explanation. Solving multiple owners together could move or delay existing commitments and is outside the one-request scope.

## 8. Stale solver results and hold acquisition

**Decision**: Treat the Timefold result as advisory. Within the original five-second deadline, a transactional hold service rechecks request state and all current policy/availability facts affecting the chosen candidate, then attempts to reserve its veterinarian and pet blocks. If the snapshot is stale or blocks cannot be acquired because capacity changed, discard the result without creating an offer, refresh affected facts, and invoke Timefold once more. Failure, a second acquisition conflict, or deadline exhaustion creates or retains staff work. Unrelated calendar changes do not invalidate an otherwise valid candidate.

**Rationale**: The authoritative database, not the solver snapshot, decides whether capacity is reserved. A single bounded retry closes the expected race while preventing an unbounded solve/acquire loop.

**Alternatives considered**: Showing the slot before acquiring the hold exposes stale offers. Reserving capacity before solving would hold undisclosed alternatives. Repeated retries could exceed the five-second UX guarantee.

## 9. Portable concurrency and transaction boundaries

**Decision**: Represent every active hold or appointment as 15-minute `ReservationBlock` rows for both `VETERINARIAN` and `PET`, keyed by `(resource_type, resource_id, block_start)`. In one transaction, remove expired target blocks, insert every required block, and create the hold; any uniqueness collision rolls back the entire attempt. Acceptance locks the active hold and request, checks expiry, creates the appointment, and converts its blocks from hold ownership to appointment ownership atomically. Staff booking uses the same block acquisition. A separate unique active-request guard keyed by pet prevents database-portability problems with partial indexes. Mutable aggregates carry JPA `@Version`, and every form mutation posts `expectedVersion` so stale actions are rejected explicitly.

**Rationale**: PostgreSQL exclusion and partial-index constraints do not exist equivalently in MySQL or H2. Fixed 15-minute starts and durations allow common uniqueness mechanisms that prevent veterinarian and pet overlaps and duplicate active requests under concurrency.

**Alternatives considered**: Check-then-insert queries alone race. Serializable isolation for every scheduling action is unnecessarily broad. Database-specific range constraints would produce different correctness guarantees between profiles.

## 10. Time, audit, and retained integration evidence

**Decision**: Store appointment, hold, and concrete-window instants in UTC and the clinic `ZoneId` used to resolve/display them; store recurring shifts and named periods as local weekday/time rules. Snapshot named periods into concrete instants on confirmed revisions. Append durable `AuditEvent` rows in the same transaction as protected mutations. Store one `IntegrationExecution` plus per-attempt rows, canonical input/output JSON, version identifiers, timing, outcome/error class, and the domain-owned Timefold score-component explanation; reconstruct the full prompt from source text and prompt-template version instead of duplicating it.

**Rationale**: Explicit instants plus the retained zone avoid daylight-saving ambiguity, while immutable snapshots keep confirmed intent stable. Transactional audit rows cannot drift from the changes they explain.

**Alternatives considered**: Local timestamps alone are ambiguous at DST transitions. Runtime logs are not durable enough for acceptance reconstruction. Storing only a final attempt would not explain retry behavior.

## 11. Test architecture and database coverage

**Decision**: Use pure unit tests for deterministic validators, workflow transitions, `SlotScorePolicy` components/explanations, and deadline logic; full-solver tests for candidate selection, preferred/fallback modes, stable repeatability, and incomplete-termination rejection; `@WebMvcTest` with imported security configuration for owner/staff authorization, forms, stale responses, and polling JSON; persistence slices and transactional service tests against reusable MySQL/PostgreSQL Testcontainers; H2 plus both container profiles for Flyway migration verification; and thin full-context request-level tests with deterministic AI and matching adapters. Add barrier-based race tests for competing holds, hold acceptance, expiry, staff booking, and stale-snapshot reruns. The live Ollama check is opt-in and is not part of deterministic CI.

**Rationale**: The mix catches domain mistakes, framework wiring mistakes, migration drift, and real database races without making every test a slow full-context test.

**Alternatives considered**: H2-only tests cannot validate MySQL/PostgreSQL locking and SQL. Mock-only tests cannot prove security filters or transaction behavior. Requiring a live nondeterministic LLM in CI would make acceptance flaky.

## 12. POC UX and data boundary

**Decision**: Build separate desktop Thymeleaf pages with a shared progress header and a single dashboard resume link. JavaScript only polls status and maintains the hold countdown; server time and server-side expiry remain authoritative. No responsive/mobile acceptance target or formal accessibility conformance suite is included, while explicit functional cues required by the spec (text plus icon, no color-only issue state) remain part of web tests. Seed and demonstrate only synthetic data.

**Rationale**: This implements the clarified POC scope without weakening the concrete interaction and safety requirements already in the specification.

**Alternatives considered**: A single-page application adds state synchronization work without a requirement. Client-only expiry or progress state would be unsafe and non-resumable. Adding formal WCAG acceptance would contradict the chosen POC scope.
