---

description: "Dependency-ordered implementation tasks for smart appointment scheduling"
---

# Tasks: Smart Appointment Scheduling

**Input**: Design documents from `/specs/001-smart-appointment-scheduling/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Tests are included because the specification defines independently testable stories, concurrency/security boundaries, and measurable acceptance outcomes. Write each listed test before its corresponding implementation and confirm that it fails for the expected reason.

**Organization**: Setup and foundational work precede six user-story phases. The three P1 stories are ordered by technical dependency: US3 establishes availability/direct booking, US1 adds owner automation, and US2 adds staff fallback.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after the phase prerequisites are satisfied because it touches different files and does not depend on another incomplete task in the same parallel group.
- **[Story]**: Maps the task to one user story from `spec.md`.
- Every task names its implementation or verification path.

## Phase 1: Setup and Platform Alignment

**Purpose**: Establish the supported Java/Maven/Boot/H2 baseline and remove explicitly deferred surfaces.

- [ ] T001 Update Java 21, Spring Boot 4.1.1, Spring AI 2.0.1 BOM/Ollama starter, Timefold 2.5.0 starter, Security, Flyway, H2-only dependencies, Surefire Byte Buddy agent, JaCoCo argument composition, and owned-source NoHttp scope in `pom.xml`
- [ ] T002 [P] Remove Gradle build and wrapper files `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`, and `.github/workflows/gradle-build.yml`
- [ ] T003 [P] Update Java 21 and Maven-only setup in `.github/workflows/maven-build.yml`, `.devcontainer/Dockerfile`, `.devcontainer/devcontainer.json`, and `README.md`
- [ ] T004 [P] Remove deferred MySQL/PostgreSQL support from `src/main/resources/application-mysql.properties`, `src/main/resources/application-postgres.properties`, `src/main/resources/db/mysql/`, `src/main/resources/db/postgres/`, `docker-compose.yml`, `k8s/`, `.github/workflows/deploy-and-test-cluster.yml`, `src/test/java/org/springframework/samples/petclinic/MySqlIntegrationTests.java`, `src/test/java/org/springframework/samples/petclinic/MysqlTestApplication.java`, and `src/test/java/org/springframework/samples/petclinic/PostgresIntegrationTests.java`
- [ ] T005 [P] Remove native-image, exposed management, crash-demo, and non-English code/resources from `src/main/java/org/springframework/samples/petclinic/PetClinicRuntimeHints.java`, `src/main/java/org/springframework/samples/petclinic/PetClinicApplication.java`, `src/main/java/org/springframework/samples/petclinic/system/CrashController.java`, `src/test/java/org/springframework/samples/petclinic/system/CrashControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/system/CrashControllerIntegrationTests.java`, `src/main/resources/application.properties`, and `src/main/resources/messages/messages_*.properties`
- [ ] T006 Convert the current H2 schema/catalog data into `src/main/resources/db/migration/V1__legacy_petclinic_baseline.sql`, disable SQL initialization, enable Flyway, set Hibernate validation, and define file-backed default/demo plus in-memory test data sources in `src/main/resources/application.properties`, `src/main/resources/application-local.properties`, `src/main/resources/application-demo.properties`, and `src/test/resources/application-test.properties`
- [ ] T007 [P] Ignore runtime H2 files and local key material and document their locations in `.gitignore` and `README.md`
- [ ] T008 [P] Add a Java 21 combined dependency/context smoke test for Boot, Spring AI, Timefold, Flyway, and H2 in `src/test/java/org/springframework/samples/petclinic/system/PlatformCompatibilityTests.java`
- [ ] T009 Run the setup gate with `./mvnw -B dependency:tree` and `./mvnw -B verify`, recording any intentional exclusions and resolved version alignment in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: Java 21 Maven verification is green; Boot/Spring AI/Timefold start together; only H2/JVM/English/no-management surfaces remain.

---

## Phase 2: Foundational Security, Durability, and Transaction Infrastructure

**Purpose**: Build shared prerequisites that block every user-story implementation.

**⚠️ CRITICAL**: No user-story work begins until this phase passes its checkpoint.

### Foundation Tests

- [ ] T010 [P] Add clean-baseline, in-memory migration, file-backed restart, and Hibernate-validation tests in `src/test/java/org/springframework/samples/petclinic/system/FlywayH2PersistenceTests.java`
- [ ] T011 [P] Add public/owner/staff/password-change/CSRF route-matrix tests plus owner-profile field restriction and cross-owner denial tests in `src/test/java/org/springframework/samples/petclinic/security/SecurityRouteMatrixTests.java`
- [ ] T012 [P] Add envelope-encryption, tamper, missing-key, and key-selection tests in `src/test/java/org/springframework/samples/petclinic/audit/ProtectedPayloadCipherTests.java`
- [ ] T013 [P] Add issued/completed/duplicate/token-reuse/rollback command tests in `src/test/java/org/springframework/samples/petclinic/shared/command/CommandServiceTests.java`
- [ ] T014 [P] Add interactive inactivity, polling non-extension, warning, safe request-cache, and reset-session invalidation tests in `src/test/java/org/springframework/samples/petclinic/security/SessionLifecycleTests.java`

### Foundation Implementation

- [ ] T015 Add account, protected-payload/envelope, key-rotation-run, audit/history, command, seed-version, and singleton infrastructure tables with constraints/indexes in `src/main/resources/db/migration/V2__security_and_shared_foundation.sql`
- [ ] T016 [P] Implement scheduling identity/version/timestamp base mapping and half-open interval/time abstractions in `src/main/java/org/springframework/samples/petclinic/shared/persistence/SchedulingEntity.java`, `src/main/java/org/springframework/samples/petclinic/shared/time/TimeInterval.java`, and `src/main/java/org/springframework/samples/petclinic/config/TimeConfiguration.java`
- [ ] T017 [P] Implement `ProtectedPayload` and `PayloadKeyEnvelope` mappings/repositories in `src/main/java/org/springframework/samples/petclinic/audit/ProtectedPayload.java`, `src/main/java/org/springframework/samples/petclinic/audit/PayloadKeyEnvelope.java`, and `src/main/java/org/springframework/samples/petclinic/audit/ProtectedPayloadRepository.java`
- [ ] T018 Implement AES-256-GCM envelope encryption, authenticated metadata, key-ring validation, fail-closed reads, and active-key selection in `src/main/java/org/springframework/samples/petclinic/audit/ProtectedPayloadCipher.java`, `src/main/java/org/springframework/samples/petclinic/audit/KeyRingProperties.java`, and `src/main/java/org/springframework/samples/petclinic/audit/ProtectedPayloadService.java`
- [ ] T019 [P] Implement append-only staff audit and sanitized owner-history mappings/repositories in `src/main/java/org/springframework/samples/petclinic/audit/AuditEvent.java`, `src/main/java/org/springframework/samples/petclinic/audit/OwnerHistoryEvent.java`, `src/main/java/org/springframework/samples/petclinic/audit/AuditEventRepository.java`, and `src/main/java/org/springframework/samples/petclinic/audit/OwnerHistoryRepository.java`
- [ ] T020 Implement transactional audit/history append services without update/delete APIs in `src/main/java/org/springframework/samples/petclinic/audit/AuditService.java` and `src/main/java/org/springframework/samples/petclinic/audit/OwnerHistoryService.java`
- [ ] T021 [P] Implement issued command mapping/repository, canonical request hashing, and command result types in `src/main/java/org/springframework/samples/petclinic/shared/command/CommandRecord.java`, `src/main/java/org/springframework/samples/petclinic/shared/command/CommandRepository.java`, and `src/main/java/org/springframework/samples/petclinic/shared/command/CommandResult.java`
- [ ] T022 Implement pessimistic issue/execute/complete/replay semantics for browser and system commands in `src/main/java/org/springframework/samples/petclinic/shared/command/CommandService.java`
- [ ] T023 [P] Implement account mapping/repository and authenticated principal containing account, owner, role, and session version in `src/main/java/org/springframework/samples/petclinic/account/Account.java`, `src/main/java/org/springframework/samples/petclinic/account/AccountRepository.java`, and `src/main/java/org/springframework/samples/petclinic/security/PetClinicPrincipal.java`
- [ ] T024 Implement account loading, BCrypt verification, temporary-password restriction, and principal refresh in `src/main/java/org/springframework/samples/petclinic/account/AccountAuthenticationService.java`
- [ ] T025 Implement public/owner/staff/shared route authorization, form login, CSRF, safe GET-only request caching, role landing, and logout in `src/main/java/org/springframework/samples/petclinic/security/SecurityConfiguration.java` and `src/main/java/org/springframework/samples/petclinic/security/AuthenticationSuccessHandler.java`
- [ ] T026 Implement authoritative interactive inactivity checks, polling exclusion, two-minute warning, explicit extension, and account session-version invalidation in `src/main/java/org/springframework/samples/petclinic/security/InteractiveSessionFilter.java`, `src/main/java/org/springframework/samples/petclinic/security/SessionStatusController.java`, and `src/main/java/org/springframework/samples/petclinic/security/SessionVersionService.java`
- [ ] T027 [P] Create public, authentication, owner, and staff Thymeleaf layouts with role-scoped navigation and no-store form conventions in `src/main/resources/templates/public/layout.html`, `src/main/resources/templates/auth/layout.html`, `src/main/resources/templates/owner/layout.html`, `src/main/resources/templates/staff/layout.html`, and `src/main/resources/templates/fragments/branding.html`
- [ ] T028 Move existing owner/pet/veterinarian/visit administration behind staff routes and transaction-owning services, retire anonymous `/owners/**`, `/vets*`, and future-visit mutation routes, and add owner-scoped profile reads plus permitted contact-field updates with audit in `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/VisitController.java`, `src/main/java/org/springframework/samples/petclinic/vet/VetController.java`, and `src/main/java/org/springframework/samples/petclinic/owner/OwnerAccessService.java`
- [ ] T029 Implement safe validation, stale-state, expired-session, conflict, and technical-failure rendering in `src/main/java/org/springframework/samples/petclinic/system/WebExceptionHandler.java` and `src/main/resources/templates/error.html`
- [ ] T030 [P] Seed environment-appropriate synthetic owner/staff accounts idempotently, including two demo staff identities and no predictable deployed credentials, in `src/main/java/org/springframework/samples/petclinic/account/SyntheticAccountSeeder.java` and `src/main/resources/application-demo.properties`
- [ ] T031 [P] Add capability-boundary tests preventing web-to-repository and owner-to-staff package access in `src/test/java/org/springframework/samples/petclinic/architecture/ModuleBoundaryTests.java`
- [ ] T032 Make all foundation tests green and re-run `./mvnw -B verify`, documenting the green foundation checkpoint in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: Flyway persistence survives restart; protected payloads fail closed; commands replay canonically; security, ownership, CSRF, password-change restriction, and non-polling inactivity are enforced.

---

## Phase 3: User Story 3 - Staff Maintains Availability and Books Directly (Priority: P1)

**Goal**: Give staff an atomic, auditable calendar and direct-booking workflow that enforces effective availability and every owner, pet, veterinarian, and hold conflict.

**Independent Test**: Configure a recurring shift, replace one date, add leave and a closure, and direct-book inside the resulting effective availability. Verify every conflicting or out-of-hours attempt rolls back and identifies all blocking records.

### Tests for User Story 3

- [ ] T033 [P] [US3] Add half-open interval, clinic-zone, recurring-shift, replacement-day, leave, closure, and precedence unit tests in `src/test/java/org/springframework/samples/petclinic/availability/EffectiveAvailabilityServiceTests.java`
- [ ] T034 [P] [US3] Add Flyway/JPA mapping, uniqueness, calendar-revision locking, and conflict-query tests in `src/test/java/org/springframework/samples/petclinic/availability/AvailabilityPersistenceTests.java`
- [ ] T035 [P] [US3] Add secured staff policy, shift, exception, leave, closure, week-calendar, and table-calendar MVC tests in `src/test/java/org/springframework/samples/petclinic/availability/AvailabilityControllerTests.java`
- [ ] T036 [P] [US3] Add direct-book review/commit, agreement metadata, validation, stale command, and role-boundary MVC tests in `src/test/java/org/springframework/samples/petclinic/appointment/StaffDirectBookingControllerTests.java`
- [ ] T037 [P] [US3] Add file-backed H2 transaction tests for competing bookings, owner-across-pets overlap, pet/veterinarian conflicts, active-hold blockers through the capacity source, out-of-availability booking, and availability-update rollback in `src/test/java/org/springframework/samples/petclinic/appointment/DirectBookingConcurrencyTests.java`

### Implementation for User Story 3

- [ ] T038 [US3] Add clinic-policy, calendar-state, availability, confirmed-appointment, and appointment-change-event tables with H2 constraints and indexes in `src/main/resources/db/migration/V3__availability_and_appointments.sql`
- [ ] T039 [P] [US3] Implement clinic policy and pessimistically locked singleton calendar revision mappings/repositories in `src/main/java/org/springframework/samples/petclinic/availability/ClinicPolicy.java`, `src/main/java/org/springframework/samples/petclinic/availability/CalendarState.java`, `src/main/java/org/springframework/samples/petclinic/availability/ClinicPolicyRepository.java`, and `src/main/java/org/springframework/samples/petclinic/availability/CalendarStateRepository.java`
- [ ] T040 [P] [US3] Implement recurring shift, replacement day, veterinarian leave, and clinic closure mappings/repositories in `src/main/java/org/springframework/samples/petclinic/availability/RecurringShift.java`, `src/main/java/org/springframework/samples/petclinic/availability/AvailabilityExceptionDay.java`, `src/main/java/org/springframework/samples/petclinic/availability/VeterinarianLeave.java`, `src/main/java/org/springframework/samples/petclinic/availability/ClinicClosure.java`, `src/main/java/org/springframework/samples/petclinic/availability/RecurringShiftRepository.java`, `src/main/java/org/springframework/samples/petclinic/availability/AvailabilityExceptionDayRepository.java`, `src/main/java/org/springframework/samples/petclinic/availability/VeterinarianLeaveRepository.java`, and `src/main/java/org/springframework/samples/petclinic/availability/ClinicClosureRepository.java`
- [ ] T041 [US3] Implement clinic-zone interval expansion and closure-over-leave-over-replacement-over-recurring effective-availability precedence in `src/main/java/org/springframework/samples/petclinic/availability/EffectiveAvailabilityService.java`
- [ ] T042 [US3] Implement calendar-lock orchestration, a capacity-blocker source port, and complete blocking-record detection for veterinarian, pet, owner, appointment, and active-hold overlaps in `src/main/java/org/springframework/samples/petclinic/availability/CalendarMutationCoordinator.java`, `src/main/java/org/springframework/samples/petclinic/availability/CapacityBlockerSource.java`, and `src/main/java/org/springframework/samples/petclinic/availability/CapacityConflictService.java`
- [ ] T043 [US3] Implement validated policy, shift, replacement-day, leave, and closure mutations with conflict preview, command completion, audit append, and all-or-nothing revision increment in `src/main/java/org/springframework/samples/petclinic/availability/AvailabilityAdministrationService.java`
- [ ] T044 [US3] Implement staff policy and availability forms/routes using explicit DTOs, expected versions, and issued command IDs in `src/main/java/org/springframework/samples/petclinic/availability/AvailabilityController.java` and `src/main/java/org/springframework/samples/petclinic/availability/AvailabilityForms.java`
- [ ] T045 [P] [US3] Create clinic-policy and recurring-shift list/edit views with conflict rendering in `src/main/resources/templates/staff/clinic-policy.html`, `src/main/resources/templates/staff/availability/shifts.html`, and `src/main/resources/templates/staff/availability/shift-form.html`
- [ ] T046 [P] [US3] Create complete replacement-day, veterinarian-leave, and clinic-closure views with conflict rendering in `src/main/resources/templates/staff/availability/exception-day.html`, `src/main/resources/templates/staff/availability/leave-form.html`, and `src/main/resources/templates/staff/availability/closure-form.html`
- [ ] T047 [US3] Implement veterinarian-filtered week and accessible table projections with appointments, holds, leave, and closures in `src/main/java/org/springframework/samples/petclinic/availability/StaffCalendarQueryService.java` and `src/main/java/org/springframework/samples/petclinic/availability/StaffCalendarController.java`
- [ ] T048 [P] [US3] Create staff week-calendar and tabular-alternative views in `src/main/resources/templates/staff/calendar/week.html` and `src/main/resources/templates/staff/calendar/table.html`
- [ ] T049 [P] [US3] Implement appointment and append-only change-event mappings/repositories with confirmed-capacity queries in `src/main/java/org/springframework/samples/petclinic/appointment/Appointment.java`, `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentChangeEvent.java`, `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentRepository.java`, and `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentChangeEventRepository.java`
- [ ] T050 [US3] Implement direct-book review and atomic commit with fresh owner agreement, internal reason, ownership checks, calendar lock, conflict checks, audit, history, and command replay in `src/main/java/org/springframework/samples/petclinic/appointment/DirectBookingService.java`
- [ ] T051 [US3] Implement staff direct-book review/commit routes and validated command form in `src/main/java/org/springframework/samples/petclinic/appointment/StaffDirectBookingController.java` and `src/main/java/org/springframework/samples/petclinic/appointment/DirectBookingForm.java`
- [ ] T052 [US3] Create direct-book selection, review, blocking-conflict, and success views in `src/main/resources/templates/staff/appointments/direct-book.html` and `src/main/resources/templates/staff/appointments/direct-book-review.html`
- [ ] T053 [US3] Make the US3 unit, MVC, persistence, and file-backed concurrency tests green and record the first-functional-slice result in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: Staff can maintain effective availability and direct-book one valid appointment; conflicting calendar or capacity mutations commit no partial state.

---

## Phase 4: User Story 1 - Owner Books a Routine Appointment (Priority: P1)

**Goal**: Persist a consented owner request, safely interpret it, deterministically select one eligible slot with mandatory Timefold, hold that slot, and atomically confirm it.

**Independent Test**: Submit one routine request for an owned pet, confirm the structured interpretation, repeatedly solve the same snapshot, and accept the one held offer. Verify one appointment is created and neither ineligible slots nor the clinic-wide calendar are exposed.

### Tests for User Story 1

- [ ] T054 [P] [US1] Add active-request uniqueness, encrypted text/consent persistence, revision, state-machine, ownership, and request-submission transaction tests in `src/test/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestServiceTests.java`
- [ ] T055 [P] [US1] Add secured owner dashboard, request submission/detail/status-schema, interpretation review/confirm, foreign-owner 404, plain-text/length validation, CSRF, and no-store MVC tests in `src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingRequestControllerTests.java`
- [ ] T056 [P] [US1] Add emergency-keyword, strict structured-output schema, relative-date/DST, specialty, urgency, payload-minimization, malformed-output, and safe-logging tests in `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationValidationTests.java`
- [ ] T057 [P] [US1] Add interpretation job lease, one-retry, ten-second deadline, crash reclaim, stale revision, first-valid-result, and technical-fallback tests in `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationJobCoordinatorTests.java`
- [ ] T058 [P] [US1] Add Timefold ConstraintVerifier tests for eligibility and all six lexicographic soft levels plus reproducible same-snapshot solving in `src/test/java/org/springframework/samples/petclinic/scheduling/matching/AppointmentSchedulingConstraintProviderTests.java`
- [ ] T059 [P] [US1] Add file-backed H2 matching/hold tests for exhaustive completion, five-second failure, one calendar retry, stale result, competing holds, and atomic acceptance in `src/test/java/org/springframework/samples/petclinic/scheduling/matching/MatchingCoordinatorConcurrencyTests.java`
- [ ] T060 [P] [US1] Add a controlled full-context owner routine-request-to-confirmed-appointment journey with deterministic AI, clock, and solver facts in `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerRoutineSchedulingJourneyTests.java`

### Implementation for User Story 1

- [ ] T061 [US1] Add scheduling request, active-pet pointer, immutable revisions, interpretations, availability windows, background jobs, offers, and exclusions with required H2 constraints/indexes in `src/main/resources/db/migration/V4__smart_request_and_offer_workflow.sql`
- [ ] T062 [P] [US1] Implement scheduling request, active-request pointer, and immutable text-revision mappings/repositories in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequest.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/ActiveSchedulingRequest.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/TextRevision.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestRepository.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/ActiveSchedulingRequestRepository.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/request/TextRevisionRepository.java`
- [ ] T063 [P] [US1] Implement interpretation, workflow revision, availability window, and offer-exclusion mappings/repositories in `src/main/java/org/springframework/samples/petclinic/scheduling/request/Interpretation.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/WorkflowRevision.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/AvailabilityWindow.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/OfferExclusion.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/InterpretationRepository.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/WorkflowRevisionRepository.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/AvailabilityWindowRepository.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/request/OfferExclusionRepository.java`
- [ ] T064 [US1] Implement request and workflow transition policies, availability-window consistency checks, active-pet collision handling, and canonical owner display-state projection in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestPolicy.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerRequestProjection.java`
- [ ] T065 [US1] Implement atomic owner submission with owned-pet verification, encrypted exact prose/consent, active-request reuse, emergency screening, first job-or-fallback-port action, command result, audit, and owner history in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestService.java`
- [ ] T066 [US1] Implement owner dashboard, request submission, and canonical detail routes with principal-derived ownership in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingRequestController.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestForm.java`
- [ ] T067 [P] [US1] Create owner dashboard and request submission/detail views with permanent urgent guidance, upcoming pet appointments, character count, unchecked consent, no draft storage, and one primary action in `src/main/resources/templates/owner/dashboard.html`, `src/main/resources/templates/owner/requests/new.html`, and `src/main/resources/templates/owner/requests/detail.html`
- [ ] T068 [P] [US1] Implement deterministic emergency phrase screening and configured clinic guidance without lowering urgency in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/EmergencyKeywordScreen.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/UrgentCareGuidance.java`
- [ ] T069 [P] [US1] Implement the interpretation port, staff-fallback port, minimized prompt input, strict schema-shaped output DTOs, and result categories in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationClient.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffFallbackPort.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationPrompt.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationCandidate.java`
- [ ] T070 [US1] Implement JSON-schema, business-rule, relative-date, named-period, DST, veterinarian, specialty, duration, and urgency validation in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationOutputValidator.java`
- [ ] T071 [P] [US1] Implement leased background-job mapping/repository and target-revision compare-and-apply operations in `src/main/java/org/springframework/samples/petclinic/scheduling/job/BackgroundJob.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/job/BackgroundJobRepository.java`
- [ ] T072 [US1] Implement single-instance database job claiming, lease recovery, bounded execution, sanitized outcome recording, and post-transaction dispatch in `src/main/java/org/springframework/samples/petclinic/scheduling/job/BackgroundJobWorker.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/job/JobExecutionConfiguration.java`
- [ ] T073 [US1] Implement runtime-configured Spring AI/Ollama structured-output calls with no model download and an application-enforced ten-second deadline in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/OllamaInterpretationClient.java` and `src/main/java/org/springframework/samples/petclinic/config/OllamaConfiguration.java`
- [ ] T074 [US1] Implement durable interpretation orchestration with initial-plus-one retry, first-valid commit, encrypted raw/validated artifacts, stale completion, emergency routing, and staff fallback in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationJobCoordinator.java`
- [ ] T075 [US1] Implement owner interpretation review/confirmation transactions and routes with editable structured fields, read-only urgency/specialty, issued commands, and no raw AI output in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerInterpretationService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerInterpretationController.java`
- [ ] T076 [P] [US1] Create owner interpretation review and confirmation views that show original prose, concrete dates, validated fields, plain-language urgency/specialty, and urgent guidance in `src/main/resources/templates/owner/requests/interpretation.html`
- [ ] T077 [P] [US1] Implement detached Timefold solution, single assignment entity, candidate planning value, bendable score, and stable tie-break key in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/AppointmentSchedulingSolution.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/matching/AppointmentAssignment.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/matching/CandidateSlot.java`
- [ ] T078 [US1] Implement fixed-revision calendar snapshotting and stable eligible candidate generation from effective availability, conflicts, policy, workflow windows, specialties, and exclusions in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/MatchingSnapshotFactory.java`
- [ ] T079 [US1] Implement Timefold hard eligibility plus ordered owner-time, preferred-veterinarian, fallback-window, earliest-time, clinic-efficiency, and stable tie-break constraints in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/AppointmentSchedulingConstraintProvider.java`
- [ ] T080 [US1] Configure single-thread reproducible exhaustive Timefold solving and reject any solve lacking proven completion within five seconds in `src/main/java/org/springframework/samples/petclinic/config/TimefoldConfiguration.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/matching/AppointmentSchedulingSolver.java`
- [ ] T081 [US1] Implement matching job orchestration outside transactions, one changed-calendar retry, incomplete/stale failure, explicit fallback, and atomic delegation to the hold service in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/MatchingJobCoordinator.java`
- [ ] T082 [US1] Implement offer mapping/repository, persistent offer capacity-blocker adapter, and calendar-locked automatic hold/acceptance transactions with expiry, capacity recheck, attempt number, request closure, appointment creation, audit/history, and command replay in `src/main/java/org/springframework/samples/petclinic/scheduling/offer/Offer.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferRepository.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferCapacityBlockerSource.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferService.java`
- [ ] T083 [US1] Implement owner-safe status JSON, offer review/countdown/accept routes, and scoped views without full-calendar or internal diagnostics in `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OwnerOfferController.java`, `src/main/resources/templates/owner/requests/offer.html`, and `src/main/resources/static/resources/js/request-status.js`
- [ ] T084 [US1] Make the US1 unit, MVC, Timefold, job, concurrency, and end-to-end tests green and record deterministic matching and request timing evidence in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: A routine consented request survives navigation/restart, produces a validated interpretation, receives exactly one deterministic Timefold-selected hold, and becomes exactly one confirmed appointment on acceptance.

---

## Phase 5: User Story 2 - Staff Resolves a Request Requiring Fallback (Priority: P1)

**Goal**: Provide a durable, ownership-aware staff queue for every manual or safety fallback and support manual interpretation, contact, assisted offers, direct booking, and reasoned closure without invoking AI when consent is declined.

**Independent Test**: Submit without AI consent, claim the resulting queue item, manually interpret it, record owner agreement, and direct-book a compliant appointment while proving the declined text never reaches the interpretation client.

### Tests for User Story 2

- [ ] T085 [P] [US2] Add declined-consent, technical/priority/emergency/no-match fallback persistence and no-AI-invocation tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/FallbackRoutingTests.java`
- [ ] T086 [P] [US2] Add queue ordering/filtering, claim, unclaim, reassign, assignee-only editing, version conflict, and concurrent claim tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/QueueAssignmentServiceTests.java`
- [ ] T087 [P] [US2] Add manual interpretation, owner-confirmation, emergency clearance to ROUTINE/PRIORITY, stale revision, and required-reason tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/StaffInterpretationServiceTests.java`
- [ ] T088 [P] [US2] Add staff-assisted offer accept/reject/expiry, same-assignee return, exclusion, zero automatic-attempt, and acceptance-versus-expiry tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/AssistedOfferServiceTests.java`
- [ ] T089 [P] [US2] Add staff queue list/detail/claim/contact/interpretation/emergency/offer/direct-book/close security and stale-form MVC tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueControllerTests.java`
- [ ] T090 [P] [US2] Add a no-AI full-context fallback journey through reassignment, contact, owner confirmation, assisted rejection, and direct booking in `src/test/java/org/springframework/samples/petclinic/scheduling/StaffFallbackJourneyTests.java`

### Implementation for User Story 2

- [ ] T091 [US2] Add unique per-request queue items and append-only contact attempts with assignment/filter indexes and encrypted-note references in `src/main/resources/db/migration/V5__staff_fallback_queue.sql`
- [ ] T092 [P] [US2] Implement queue item and contact-attempt mappings/repositories with optimistic versions and sortable/filterable projections in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/QueueItem.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/queue/ContactAttempt.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/queue/QueueItemRepository.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/queue/ContactAttemptRepository.java`
- [ ] T093 [US2] Implement the staff-fallback port with canonical idempotent queue creation and add staff queries ordered by urgency, age, assignment, and state in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/FallbackService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueQueryService.java`
- [ ] T094 [US2] Implement atomic claim, unclaim, and reasoned reassignment with assignee authorization, optimistic concurrency, commands, and audit in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/QueueAssignmentService.java`
- [ ] T095 [US2] Implement append-only contact attempts, unreachable-without-hold, explicit reasoned closure, and current owner contact projection in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/QueueContactService.java`
- [ ] T096 [US2] Implement assignee-only manual structured interpretation and request-owner-confirmation transitions against the exact current workflow revision in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffInterpretationService.java`
- [ ] T097 [US2] Implement audited staff emergency clearance with explicit replacement urgency/reason and owner-confirmation-required revision creation in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/EmergencyClearanceService.java`
- [ ] T098 [US2] Extend owner confirmation so cleared ROUTINE revisions enqueue matching and PRIORITY revisions return the assigned item to review in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerInterpretationService.java`
- [ ] T099 [US2] Implement staff-assisted calendar-locked hold creation after recorded contact and rejection/expiry return to the same assigned queue item without incrementing automatic attempts in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/AssistedOfferService.java`
- [ ] T100 [US2] Implement queue-context direct booking using the shared appointment transaction and atomically resolve the request and queue item in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/QueueDirectBookingService.java`
- [ ] T101 [US2] Implement staff queue list/detail, claim, unclaim, reassign, and contact routes with versioned command DTOs in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueController.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/queue/QueueActionForms.java`
- [ ] T102 [US2] Implement manual interpretation, owner-confirmation request, and emergency-clearance routes in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueInterpretationController.java`
- [ ] T103 [US2] Implement staff-assisted offer, queue direct-book, and explicit closure review/commit routes in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueResolutionController.java`
- [ ] T104 [P] [US2] Create sorted/filterable staff queue and protected detail views with current revision, owner contact, assignment, contact history, and stale-state recovery in `src/main/resources/templates/staff/queue/list.html` and `src/main/resources/templates/staff/queue/detail.html`
- [ ] T105 [P] [US2] Create manual interpretation, emergency clearance, assisted offer, direct-book, reassignment, and closure forms in `src/main/resources/templates/staff/queue/interpretation.html`, `src/main/resources/templates/staff/queue/emergency-clearance.html`, `src/main/resources/templates/staff/queue/offer.html`, `src/main/resources/templates/staff/queue/direct-book.html`, and `src/main/resources/templates/staff/queue/close.html`
- [ ] T106 [US2] Make the US2 queue, security, concurrency, no-AI, and full-context tests green and record the manual fallback demonstration in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: Every fallback is durable and actionable; only the current assignee edits; assisted and direct booking preserve capacity invariants; declined prose is never sent to AI.

---

## Phase 6: User Story 4 - Owner Manages an Active Request and Appointment (Priority: P2)

**Goal**: Let owners safely resume, revise, reject, retry, withdraw, and cancel while stale jobs/staff edits cannot alter the current revision and every released slot remains consistent.

**Independent Test**: Reject an offer, revise availability without another AI call, revise original prose with new consent, obtain and accept a replacement, then cancel the future appointment and verify retained history after every transition.

### Tests for User Story 4

- [ ] T107 [P] [US4] Add structured-edit versus prose-revision, consent renewal, pet immutability, active-hold release, count reset, and stale job/staff result tests in `src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerRequestRevisionServiceTests.java`
- [ ] T108 [P] [US4] Add automatic and assisted reject/expiry, exact-slot exclusion, explicit retry, five-offer ceiling, duplicate command, and restart-materialization tests in `src/test/java/org/springframework/samples/petclinic/scheduling/offer/OfferLifecycleServiceTests.java`
- [ ] T109 [P] [US4] Add withdrawal-from-every-preconfirmation-state, queue/hold release, irreversibility, and retained-history tests in `src/test/java/org/springframework/samples/petclinic/scheduling/request/RequestWithdrawalServiceTests.java`
- [ ] T110 [P] [US4] Add owner future-appointment cancellation, started-appointment rejection, no-request-reopen, conflict, and command replay tests in `src/test/java/org/springframework/samples/petclinic/appointment/OwnerAppointmentCancellationTests.java`
- [ ] T111 [P] [US4] Add owner revision/retry/reject/withdraw/appointment/history MVC tests for ownership, CSRF, no-store, validation, and canonical conflict rendering in `src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerRequestManagementControllerTests.java`
- [ ] T112 [P] [US4] Add a controlled full-context reject-revise-reoffer-confirm-cancel journey with owner-visible history assertions in `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerRequestManagementJourneyTests.java`

### Implementation for User Story 4

- [ ] T113 [US4] Add offer lifecycle, appointment cancellation, request-history, and expiry-worker indexes required for revision and release workflows in `src/main/resources/db/migration/V6__owner_request_lifecycle.sql`
- [ ] T114 [US4] Implement structured workflow edits without AI and original-prose revisions with fresh consent, hold release, workflow supersession, queue reset, and stale-work invalidation in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerRequestRevisionService.java`
- [ ] T115 [US4] Implement calendar-locked rejection and expiry with exact-slot exclusion, origin-specific transitions, five-automatic-offer ceiling, command replay, audit, and owner history in `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferLifecycleService.java`
- [ ] T116 [US4] Implement restart-safe expired-hold materialization while ensuring every read treats server-expired holds as inactive in `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferExpiryWorker.java`
- [ ] T117 [US4] Implement irreversible atomic withdrawal with active hold release, queue closure, active-pet pointer removal, command result, audit, and retained history in `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestWithdrawalService.java`
- [ ] T118 [US4] Implement owner-scoped cancellation before appointment start with calendar lock, no request reopening, append-only change event, command result, audit, and history in `src/main/java/org/springframework/samples/petclinic/appointment/OwnerAppointmentService.java`
- [ ] T119 [US4] Implement owner structured-edit, text-revision, explicit match, reject, and withdrawal review/commit routes in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerRequestManagementController.java`
- [ ] T120 [US4] Implement owner appointment detail/cancellation and paginated history routes with principal-derived scope in `src/main/java/org/springframework/samples/petclinic/appointment/OwnerAppointmentController.java` and `src/main/java/org/springframework/samples/petclinic/audit/OwnerHistoryController.java`
- [ ] T121 [P] [US4] Create structured-edit and original-text-revision views with fresh consent disclosure, loss warning, current-state conflicts, and no browser draft persistence in `src/main/resources/templates/owner/requests/interpretation-edit.html` and `src/main/resources/templates/owner/requests/text-revision.html`
- [ ] T122 [P] [US4] Create rejection and withdrawal confirmation views plus explicit find-another-time actions in `src/main/resources/templates/owner/requests/reject-offer.html` and `src/main/resources/templates/owner/requests/withdraw.html`
- [ ] T123 [P] [US4] Create owner appointment detail/cancel and paginated safe-history views in `src/main/resources/templates/owner/appointments/detail.html`, `src/main/resources/templates/owner/appointments/cancel.html`, and `src/main/resources/templates/owner/history.html`
- [ ] T124 [US4] Implement owner dashboard/history queries that expose current actions and sanitized request, offer, appointment, and visit events only in `src/main/java/org/springframework/samples/petclinic/audit/OwnerHistoryQueryService.java`
- [ ] T125 [US4] Make the US4 revision, offer, cancellation, MVC, restart, and journey tests green and record the recoverable-lifecycle demonstration in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: Owners can recover and change course without duplicate AI or stale writes; released capacity is immediately reusable; cancellation never reopens a request.

---

## Phase 7: User Story 5 - Staff Completes and Corrects Appointment Outcomes (Priority: P2)

**Goal**: Complete the appointment lifecycle with audited rescheduling/cancellation, completion/no-show, immutable corrections, owner-visible visit projection, and safe legacy reconciliation.

**Independent Test**: Complete an ended appointment, confirm its linked visit, correct the outcome, and verify both old and new visit/outcome events remain while only the current safe projection is owner-visible.

### Tests for User Story 5

- [ ] T126 [P] [US5] Add booking-state, outcome-state, time-boundary, reschedule, staff-cancel, complete, no-show, and transition-policy unit tests in `src/test/java/org/springframework/samples/petclinic/appointment/AppointmentLifecyclePolicyTests.java`
- [ ] T127 [P] [US5] Add completion-to-visit linkage, no-show-without-visit, encrypted clinical detail, and owner-safe history persistence tests in `src/test/java/org/springframework/samples/petclinic/appointment/AppointmentOutcomeServiceTests.java`
- [ ] T128 [P] [US5] Add correction preview/commit, required reason, immutable prior event/visit retention, current-visit projection, stale version, and duplicate command tests in `src/test/java/org/springframework/samples/petclinic/appointment/AppointmentCorrectionServiceTests.java`
- [ ] T129 [P] [US5] Add secured staff appointment detail/reschedule/cancel/complete/no-show/correction MVC tests with CSRF, validation, and conflict outcomes in `src/test/java/org/springframework/samples/petclinic/appointment/StaffAppointmentControllerTests.java`
- [ ] T130 [P] [US5] Add future legacy-visit detection, insufficient-evidence, fresh agreement, exact-slot, idempotent reconciliation, and retained-source tests in `src/test/java/org/springframework/samples/petclinic/appointment/LegacyVisitReconciliationTests.java`
- [ ] T131 [P] [US5] Add file-backed H2 restart and concurrent lifecycle tests proving append-only events, atomic visit projection, and capacity revision consistency in `src/test/java/org/springframework/samples/petclinic/appointment/AppointmentLifecyclePersistenceTests.java`

### Implementation for User Story 5

- [ ] T132 [US5] Add appointment outcome events, current-visit projection links, legacy reconciliation links, and required lifecycle indexes/constraints in `src/main/resources/db/migration/V7__appointment_outcomes_and_legacy_reconciliation.sql`
- [ ] T133 [P] [US5] Implement append-only appointment outcome-event mapping/repository and extend visit mapping with immutable outcome provenance in `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentOutcomeEvent.java`, `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentOutcomeEventRepository.java`, and `src/main/java/org/springframework/samples/petclinic/owner/Visit.java`
- [ ] T134 [US5] Implement booking/outcome transition validation against the injected clock and explicit preview models for lifecycle impact in `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentLifecyclePolicy.java`
- [ ] T135 [US5] Implement atomic completion and no-show with protected clinical detail, immutable visit creation, current projection, audit/history, expected version, and command replay in `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentOutcomeService.java`
- [ ] T136 [US5] Implement audited outcome correction that appends rather than rewrites and atomically reconciles replacement/no current visit projections in `src/main/java/org/springframework/samples/petclinic/appointment/AppointmentCorrectionService.java`
- [ ] T137 [US5] Implement staff rescheduling with fresh owner agreement, calendar lock, full conflict checks, retained appointment identity, and append-only change event in `src/main/java/org/springframework/samples/petclinic/appointment/StaffAppointmentReschedulingService.java`
- [ ] T138 [US5] Implement reasoned staff cancellation with owner-facing explanation, calendar revision, change event, audit/history, and no request reopening in `src/main/java/org/springframework/samples/petclinic/appointment/StaffAppointmentCancellationService.java`
- [ ] T139 [US5] Implement staff appointment detail and immutable audit/history projections with protected-field access limited to staff in `src/main/java/org/springframework/samples/petclinic/appointment/StaffAppointmentQueryService.java`
- [ ] T140 [US5] Implement future-dated legacy visit discovery and atomic appointment reconciliation requiring exact slot and fresh owner agreement when evidence is absent in `src/main/java/org/springframework/samples/petclinic/appointment/LegacyVisitReconciliationService.java`
- [ ] T141 [US5] Implement staff appointment detail, reschedule, and cancellation preview/commit routes with versioned forms in `src/main/java/org/springframework/samples/petclinic/appointment/StaffAppointmentController.java`
- [ ] T142 [US5] Implement completion, no-show, and correction preview/commit routes with explicit clinical and reason DTOs in `src/main/java/org/springframework/samples/petclinic/appointment/StaffAppointmentOutcomeController.java`
- [ ] T143 [US5] Implement legacy reconciliation list and create-appointment review/commit routes in `src/main/java/org/springframework/samples/petclinic/appointment/LegacyVisitReconciliationController.java`
- [ ] T144 [P] [US5] Create staff appointment detail, reschedule, and cancellation review views in `src/main/resources/templates/staff/appointments/detail.html`, `src/main/resources/templates/staff/appointments/reschedule.html`, and `src/main/resources/templates/staff/appointments/cancel.html`
- [ ] T145 [P] [US5] Create completion, no-show, and correction impact-review views in `src/main/resources/templates/staff/appointments/complete.html`, `src/main/resources/templates/staff/appointments/no-show.html`, and `src/main/resources/templates/staff/appointments/correct-outcome.html`
- [ ] T146 [P] [US5] Create legacy visit reconciliation list and review views in `src/main/resources/templates/staff/legacy-visits/list.html` and `src/main/resources/templates/staff/legacy-visits/create-appointment.html`
- [ ] T147 [US5] Make the US5 lifecycle, MVC, legacy, concurrency, and restart tests green and record outcome/correction evidence in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: Appointment lifecycle changes are time-valid, atomic, and append-only; corrections preserve prior visits/events; legacy visits are reconciled without invented evidence.

---

## Phase 8: User Story 6 - Staff Provisions Secure Owner Access (Priority: P3)

**Goal**: Let staff provision or reset owner credentials while displaying a random temporary password once, forcing replacement, and invalidating every prior session.

**Independent Test**: Provision an owner, sign in with the temporary password, prove only the password-change surface is reachable, change it, reset it as staff, and verify the old password and all prior sessions fail.

### Tests for User Story 6

- [ ] T148 [P] [US6] Add username normalization/suggestion, uniqueness, cryptographic temporary-password, one-time display, seven-day expiry, and duplicate-command tests in `src/test/java/org/springframework/samples/petclinic/account/AccountProvisioningServiceTests.java`
- [ ] T149 [P] [US6] Add required password-change access restriction, expiry, BCrypt replacement, session-ID rotation, and old-temporary-password rejection tests in `src/test/java/org/springframework/samples/petclinic/account/PasswordChangeServiceTests.java`
- [ ] T150 [P] [US6] Add password-reset session-version increment, all-session invalidation, one-time response, and canonical duplicate-result tests in `src/test/java/org/springframework/samples/petclinic/account/PasswordResetServiceTests.java`
- [ ] T151 [P] [US6] Add secured staff provision/reset and authenticated password-change MVC tests for roles, ownership, CSRF, no-store, validation, and duplicate commands in `src/test/java/org/springframework/samples/petclinic/account/AccountControllerTests.java`

### Implementation for User Story 6

- [ ] T152 [P] [US6] Implement normalized username suggestion/validation and cryptographically secure temporary-password generation in `src/main/java/org/springframework/samples/petclinic/account/UsernamePolicy.java` and `src/main/java/org/springframework/samples/petclinic/account/TemporaryPasswordGenerator.java`
- [ ] T153 [US6] Implement staff account provisioning with unique owner link, BCrypt hash, seven-day expiry, password-change requirement, one-time cleartext result, command completion, and audit in `src/main/java/org/springframework/samples/petclinic/account/AccountProvisioningService.java`
- [ ] T154 [US6] Implement staff password reset with fresh temporary credential, account session-version increment, one-time cleartext result, command completion, and audit in `src/main/java/org/springframework/samples/petclinic/account/PasswordResetService.java`
- [ ] T155 [US6] Implement authenticated password replacement with current-credential verification, policy validation, temporary-password invalidation, session-version refresh, and session-ID rotation in `src/main/java/org/springframework/samples/petclinic/account/PasswordChangeService.java`
- [ ] T156 [US6] Implement staff owner-account provision/reset review and commit routes using issued command DTOs in `src/main/java/org/springframework/samples/petclinic/account/StaffOwnerAccountController.java`
- [ ] T157 [US6] Implement required and voluntary password-change routes that preserve only safe GET destinations in `src/main/java/org/springframework/samples/petclinic/account/PasswordChangeController.java`
- [ ] T158 [P] [US6] Create staff account provision/reset and one-time credential result views with no-store headers and no redisplay path in `src/main/resources/templates/staff/owners/account-form.html` and `src/main/resources/templates/staff/owners/account-result.html`
- [ ] T159 [P] [US6] Create required/voluntary owner password-change view with safe expiry and validation messages in `src/main/resources/templates/auth/password-change.html`
- [ ] T160 [US6] Make the US6 service, security, MVC, and session invalidation tests green and record provisioning/reset evidence in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: Temporary credentials are random, bounded, shown once, and unusable after replacement/reset; forced password change and global session invalidation are enforced server-side.

---

## Phase 9: Polish and Cross-Cutting Acceptance

**Purpose**: Finish key rotation, operational safety, architecture enforcement, synthetic demonstration data, measurable outcomes, and whole-feature acceptance.

- [ ] T161 [P] Add resumable key-rotation, lease-reclaim, active-envelope switch, ciphertext-stability, missing-key, and safe-retirement tests in `src/test/java/org/springframework/samples/petclinic/audit/KeyRotationServiceTests.java`
- [ ] T162 Implement key-rotation run mapping/repository and resumable data-key rewrap worker without protected-payload ciphertext changes in `src/main/java/org/springframework/samples/petclinic/audit/KeyRotationRun.java`, `src/main/java/org/springframework/samples/petclinic/audit/KeyRotationRunRepository.java`, and `src/main/java/org/springframework/samples/petclinic/audit/KeyRotationService.java`
- [ ] T163 [P] Add cache-control, security-header, safe-error, sensitive-log redaction, and protected-field serialization regression tests in `src/test/java/org/springframework/samples/petclinic/security/OperationalSecurityRegressionTests.java`
- [ ] T164 Implement correlation-only workflow logging and redact prose, consent, AI output, clinical data, credentials, command hashes, and encryption material in `src/main/java/org/springframework/samples/petclinic/config/SensitiveLoggingConfiguration.java`
- [ ] T165 [P] Extend capability-boundary and append-only repository architecture rules across scheduling, availability, appointment, account, audit, owner, and staff adapters in `src/test/java/org/springframework/samples/petclinic/architecture/ModuleBoundaryTests.java`
- [ ] T166 Seed idempotent synthetic policies, shifts, owners, pets, veterinarians, requests, queue cases, offers, and appointments for all demonstrations in `src/main/java/org/springframework/samples/petclinic/system/SchedulingDemoDataSeeder.java`
- [ ] T167 [P] Add deterministic test adapters for interpretation success/failure/timeout, clock control, solver completion/incompletion, lease crash, and calendar-race injection in `src/test/java/org/springframework/samples/petclinic/support/SchedulingTestFixtures.java`
- [ ] T168 Add one full-context suite covering all 15 specification demonstration journeys with only synthetic data in `src/test/java/org/springframework/samples/petclinic/SchedulingAcceptanceJourneyTests.java`
- [ ] T169 Measure deterministic matching under five seconds and at least 95 percent offer-or-fallback completion within 20 seconds using controlled synthetic workloads in `src/test/java/org/springframework/samples/petclinic/scheduling/SchedulingPerformanceAcceptanceTests.java`
- [ ] T170 [P] Add an opt-in tagged live-Ollama schema-or-safe-fallback integration test excluded from the deterministic default suite in `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/LiveOllamaInterpretationTests.java`
- [ ] T171 Reconcile route/state/schema contracts and document every accepted deviation or correction in `specs/001-smart-appointment-scheduling/contracts/http-ui.md`, `specs/001-smart-appointment-scheduling/contracts/workflow-states.md`, and `specs/001-smart-appointment-scheduling/contracts/request-status.schema.json`
- [ ] T172 Update Java 21 Maven/H2-only setup, stable key handling, demo credentials, optional Ollama, synthetic-data restriction, troubleshooting, and acceptance commands in `README.md` and `specs/001-smart-appointment-scheduling/quickstart.md`
- [ ] T173 Run clean file-backed restart demonstrations and `./mvnw -B verify`, then record all 15 journeys, security/concurrency gates, version alignment, timing outcomes, and H2-only scope in `specs/001-smart-appointment-scheduling/quickstart.md`

**Checkpoint**: The full deterministic suite and all 15 synthetic demonstration journeys pass; measured targets, persistence restart, concurrency, security, contract, and key-rotation evidence are recorded.

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 - Setup**: No feature dependency; establishes the only supported build/runtime baseline.
- **Phase 2 - Foundation**: Depends on Phase 1 and blocks all user stories.
- **Phase 3 - US3**: Depends on Phase 2; establishes availability, capacity locking, and direct booking.
- **Phase 4 - US1**: Depends on US3 for effective availability, conflicts, and appointment creation.
- **Phase 5 - US2**: Depends on US1 request/revision/offer primitives and US3 direct booking; its independent no-consent path does not depend on live AI or successful Timefold solving.
- **Phase 6 - US4**: Depends on US1 offer/request lifecycle and US2 queue transitions.
- **Phase 7 - US5**: Depends on US3 appointments; may start after US3 for core outcomes, but final integration depends on US4 cancellation/history behavior.
- **Phase 8 - US6**: Depends only on Phase 2 account/session foundations and may proceed in parallel with Phases 3-7 after that gate.
- **Phase 9 - Polish**: Depends on every user story selected for the release.

### User Story Dependency Graph

```text
Setup -> Foundation -> US3 -> US1 -> US2 -> US4
                       |             |      |
                       +-----------> US5 <-+

Foundation --------------------------------> US6

US1 + US2 + US3 + US4 + US5 + US6 -> Polish
```

### Within Each User Story

1. Write the listed tests and confirm they fail for the expected missing behavior.
2. Apply that phase's Flyway migration before persistence mappings.
3. Implement mappings/repositories before transaction-owning services.
4. Implement domain/application services before MVC controllers.
5. Implement templates against the controller contract, then make the phase gate green.

---

## Parallel Opportunities

- After T001, Maven-only cleanup/documentation tasks T002-T005 and T007-T008 can proceed independently; foundation tests T010-T014 can also be written in parallel.
- **US3**: T033-T037 can be written in parallel; after T038, T039, T040, and T049 can proceed together, as can view tasks T045, T046, and T048 once their route contracts are fixed.
- **US1**: T054-T060 can be written in parallel; after T061, request mappings T062-T063, emergency/interpretation types T068-T069, job mapping T071, and Timefold model T077 can proceed independently.
- **US2**: T085-T090 can be written in parallel; after T091, queue mapping T092 and route/view contract work T101-T105 can be split once service DTOs are fixed.
- **US4**: T107-T112 can be written in parallel; owner revision, offer, withdrawal, cancellation, and view work can be divided after T113, with integration deferred until their services are green.
- **US5**: T126-T131 can be written in parallel; after T132, event mapping T133 and view tasks T144-T146 can proceed independently of the service implementations.
- **US6**: T148-T151 can be written in parallel; policy/generator T152 and view tasks T158-T159 can proceed independently before controller integration.
- In Polish, T161, T163, T165, T167, and T170 touch separate test/support paths and can proceed concurrently after their prerequisite stories; T168 follows the shared fixtures in T167.

### Parallel Example: User Story 3

```text
Task T033: Unit-test effective availability precedence.
Task T035: Test secured staff calendar and policy routes.
Task T036: Test the direct-book browser contract.
Task T037: Test file-backed H2 capacity races.
```

### Parallel Example: User Story 1

```text
Task T054: Test request persistence and active-pet uniqueness.
Task T056: Test interpretation and emergency validation.
Task T058: Test Timefold constraints and determinism.
Task T059: Test matching/hold transaction races.
```

### Parallel Example: User Story 2

```text
Task T085: Test no-consent and technical fallback routing.
Task T086: Test queue assignment concurrency.
Task T087: Test manual interpretation and emergency clearance.
Task T089: Test the secured staff queue route family.
```

### Parallel Example: User Story 4

```text
Task T107: Test structured and prose revisions.
Task T108: Test rejection, expiry, and automatic-offer limits.
Task T109: Test withdrawal atomicity.
Task T110: Test owner cancellation boundaries.
```

### Parallel Example: User Story 5

```text
Task T126: Test appointment transition policy.
Task T127: Test completion and visit projection.
Task T128: Test append-only correction.
Task T130: Test legacy reconciliation.
```

### Parallel Example: User Story 6

```text
Task T148: Test provisioning and one-time credentials.
Task T149: Test forced password replacement.
Task T150: Test reset-driven session invalidation.
Task T151: Test the secured account route family.
```

---

## Implementation Strategy

### MVP First

The smallest deployable scheduling increment is **Phase 1 + Phase 2 + User Story 3**. It delivers secure staff-maintained availability and direct booking while proving H2 persistence, calendar locking, audit, idempotency, and conflict handling before AI, Timefold matching, or owner request automation is introduced.

1. Complete platform and foundation gates.
2. Complete US3 and validate the first functional slice against file-backed H2.
3. Demonstrate staff availability and direct booking independently.

### Incremental Delivery

1. Add **US1** to deliver the primary smart owner flow using Spring AI and mandatory Timefold through already-proven capacity services.
2. Add **US2** to complete the manual and safety fallback path for every automation failure or consent choice.
3. Add **US4** to support safe revision, rejection, retry, withdrawal, and owner cancellation.
4. Add **US5** to complete and correct appointment outcomes and reconcile legacy visits.
5. Add **US6** independently after the foundation whenever staff-driven account provisioning is required.
6. Complete cross-cutting acceptance only for the stories included in the release candidate.

### Delivery Guardrails

- Do not begin story implementation until the foundation checkpoint is green.
- Do not run AI or Timefold inside a database transaction.
- Do not create a separate slot ranker; Timefold is the only matching engine.
- Do not add MySQL, PostgreSQL, Gradle, native-image, exposed management, non-English, or real-data paths in phase one.
- Do not accept an incomplete Timefold solve or retry more than once after a calendar revision change.
- Do not expose full clinic availability, raw AI output, internal notes, diagnostics, or protected data to owners.

---

## Notes

- `[P]` means the task can proceed concurrently after its phase prerequisites and any explicitly named migration or service contract are complete.
- All automated tests use in-memory or temporary file-backed H2; no containerized database is part of phase one.
- Live Ollama validation is opt-in. The default suite uses deterministic adapters and must pass without Ollama.
- Every mutation uses a server-issued command, expected versions, CSRF, transaction-owned authorization, audit/history, and canonical replay behavior.
- Every date/time rule uses the configured clinic zone, injected clock, half-open intervals, and stored instants.
- Only synthetic and demonstration data may be used for development, validation, or acceptance.

## Phase 10: Convergence

- [X] T174 CRITICAL: Enforce authentication, disjoint OWNER/STAFF route authorization, owner-record scoping, foreign-owner denial, and removal of public operational/crash routes with regression coverage per FR-001–FR-004 and SC-010 (contradicts)
- [X] T175 CRITICAL: Make staff-assisted offer rejection and expiry create one exclusion, release the hold, preserve the assignee, and atomically return the same queue item to `IN_REVIEW` per FR-064 and US2/AC4 (contradicts)
- [X] T176 Restrict predictable synthetic-account seeding to local/demo/test profiles and require safe deployed staff bootstrap configuration per FR-010 (contradicts)
- [X] T177 Remove committed default key material, bind documented external key-ring configuration, fail startup without the active key, and fail closed for unavailable historical keys per FR-097 and FR-098 (contradicts)
- [X] T178 Allow owners to edit and validate their first name and last name alongside contact fields while preventing account, role, credential, and pet edits and auditing the change per FR-005 (contradicts)
- [X] T179 Rebuild the owner dashboard into items-needing-action, other active requests, upcoming confirmed appointments, recent history, full-history access, and one primary scheduling action per FR-015 (partial)
- [X] T180 Show the selected pet's upcoming appointments before submission and reopen its existing active request instead of returning an error per FR-016 and FR-017 (partial)
- [X] T181 Enforce 10–2,000 character plain-text prose without markup on initial and revised submissions and warn before navigating away from changed unsaved prose without browser storage per FR-020 and FR-021 (partial)
- [X] T182 Expand unchecked-by-default AI consent copy to identify exact-revision scope, data, purpose, retained records, authorized viewers, and the manual alternative per FR-023 (partial)
- [X] T183 Preserve read-only specialty and urgency during owner edits and support list-based allowed, preferred, excluded, and fallback availability without silently resetting fields per FR-026 and FR-039 (contradicts)
- [X] T184 Provide one canonical, state-appropriate owner primary action for interpreting, review, matching, offered, staff-handled, confirmed, expired, withdrawn, and closed requests across refresh and reauthentication per FR-031 (partial)
- [X] T185 Append a protected audit event for every deterministic emergency screen with the source revision and outcome but no copied prose per FR-035 (missing)
- [X] T186 Render configured clinic phone, contact hours, urgent-care guidance, and the explicit portal/queue non-emergency-response warning on every relevant owner surface per FR-037 (partial)
- [X] T187 Stop logging raw Ollama output and unredacted internal errors, integrate the sensitive-data redaction policy, and verify owner-safe/profile-safe diagnostics per FR-040 and plan: diagnostic logging (contradicts)
- [X] T188 Enforce the complete interpretation schema and automation-suitability rules, including contradictions and unresolved items, and apply one total ten-second deadline across the permitted retry per FR-041 and FR-044 (partial)
- [X] T189 Produce a fixed request/calendar snapshot, include eligible same-day slots after minimum notice, implement every lexicographic ranking tier including clinic efficiency, and reject incomplete or stale Timefold solutions per FR-048–FR-055 and plan: deterministic solve (contradicts)
- [X] T190 Show pet, veterinarian specialty, full clinic-local date and interval, duration, zone, safe explanation, exact expiry, server-derived countdown, remaining automatic attempts, and an explicit post-expiry action on the offer workflow per FR-057, FR-060, and FR-079 (partial)
- [X] T191 Enforce claim-before-edit and current-assignee mutation rules and complete queue detail with current revision, history, audit history, protected fields, and only valid actions per FR-067 and FR-069 (partial)
- [X] T192 Require a protected audit reason for unclaim and reassignment in the browser, service, and audit event per FR-068 (missing)
- [X] T193 Carry expected request/workflow/queue versions through every staff edit and reject stale saves without partial changes while returning revised work to marked `NEW` state per FR-070 (missing)
- [X] T194 Require a recorded owner contact before an assisted offer and prohibit staff interpretation from bypassing owner confirmation before any portal/automatic offer per FR-072 and FR-073 (contradicts)
- [X] T195 Add active holds with expiries and explicit recurring shifts, date exceptions, leave, and closures to the veterinarian-filtered week calendar and tabular alternative per FR-074 (partial)
- [X] T196 Add a Timefold-backed ranked staff suggestion path and apply the same availability, specialty, horizon, hold, owner, pet, and veterinarian validation as manual selection per FR-075 (missing)
- [X] T197 Make every availability mutation preview appointments and active holds atomically, save nothing on conflict, list every blocking record with its recovery path, and require audited confirmation before releasing another owner's hold per FR-076 and FR-082 (partial)
- [X] T198 Support and server-validate booking horizon, hold duration, contact guidance, permitted durations, operating intervals, and non-overlapping named periods, and consume configured values instead of hardcoded hold/matching values per FR-078 (partial)
- [X] T199 Apply future-time, effective-availability, specialty, horizon, duration, and conflict validation to staff booking/rescheduling, add reason categories and review-before-commit, and audit complete before/after values per FR-083–FR-085 (partial)
- [X] T200 Require and persist a real contact attempt when staff cancellation lacks prior agreement, enforce no-show reasons server-side, keep visit history consistently linked during corrections, and complete the owner-visible follow-up actions per FR-087 and FR-090–FR-091 (partial)
- [X] T201 Split job leasing, external AI/solver execution, and result commit into bounded transactions; increment and cap lease recovery attempts; and prove restart, stale-result, and crash-after-call safety per FR-092, FR-093, and plan: no transaction spans AI/solver (contradicts)
- [X] T202 Issue command IDs and expected versions before every owner, staff, and background mutation and return the persisted canonical result for duplicate, stale, expired, and concurrent submissions per FR-094 and SC-008 (missing)
- [X] T203 Complete immutable structured before/after audit coverage for security, request, AI, matching, queue, offer, availability, appointment, and lifecycle changes and prevent update/delete of audit records per FR-095 and SC-011 (partial)
- [X] T204 Make key rotation resume safely, retain authorized readability, refuse activation while any payload failed, verify every active envelope, and prevent unsafe source-key retirement per FR-097 and FR-098 (partial)
- [X] T205 Add service and persistence guards that block owner or pet deletion when scheduling history exists and return a safe explanatory recovery message per FR-099 (missing)
- [X] T206 Map validation, expiry, concurrency, interpretation, matching, encryption, and session failures to safe plain-language outcomes with a valid next action and no internal exception leakage per FR-100 (partial)
- [X] T207 Align the build and runtime integration with mandatory Timefold Solver 2.5.0 without disabling the planned supported auto-configuration path per plan: Timefold 2.5.0 (contradicts)
- [X] T208 Explicitly attach and compose the Byte Buddy and JaCoCo Java agents in Surefire so Java 21 `./mvnw -B verify` completes without Mockito mock-maker initialization errors per plan: Java 21 verification and explicit Byte Buddy agent (partial)
- [X] T209 Generate the editable owner-account username suggestion from the normalized owner name through `UsernamePolicy` while retaining uniqueness validation per FR-007 (partial)
- [X] T210 Redirect staff login, staff root, and post-password-change success to the fallback queue per FR-014 (contradicts)
- [X] T211 Add fallback-reason filtering plus safe request age and last-update columns to the urgency/age-sorted staff queue per FR-065 and FR-066 (partial)
- [X] T212 Show clinic contact guidance to owners while their request is `AWAITING_OWNER` and keep the queue item open without a hold until an explicit reasoned action per FR-071 (partial)
- [X] T213 Remove `AUTO_SERVER=TRUE` from normal/demo file-backed H2 URLs and verify one application instance has exclusive database-file access per plan: one-instance exclusive H2 storage (contradicts)
- [X] T214 Add the opt-in tagged `LiveOllamaInterpretationTests` schema-or-safe-fallback integration test and keep it excluded from the deterministic default suite per T170 (missing)
- [X] T215 Run clean file-backed restart checks and all 15 demonstration journeys, security/concurrency gates, version checks, and timing outcomes, then record evidence and limitations in the quickstart per T173 and SC-012 (missing)

## Phase 11: Convergence

- [X] T216 Render the anonymous landing page with the planned public layout, expose the canonical login action, remove legacy operational navigation, and add rendered-page coverage for the public landing/login contract per FR-001 and plan: public/browser-complete layout (partial)
- [X] T217 Complete the public/owner/staff route and layout migration, replace stale or nonexistent menu targets with the authorized canonical routes, make every planned browser workflow reachable through role-scoped navigation, and add link-target and cross-role navigation coverage per FR-002 and plan: split route/layout families (contradicts)
- [X] T218 Repair owner request status polling so completed interpretation and later nonterminal transitions replace the processing spinner with or navigate to the canonical current state, surface polling/session failures safely, and add a browser-level regression test covering the LLM-completion transition per FR-031, US1/AC3, SC-007, and plan: owner-safe non-interactive status polling (partial)
- [X] T219 Keep queue detail usable when a historical payload key is unavailable, expose owner and state-valid staff cancellation actions, preserve the FR-087 confirmation/contact procedure, enforce the scheduled-end boundary for completion and no-show, and add focused regression coverage per FR-086–FR-088 and FR-098 (partial)
