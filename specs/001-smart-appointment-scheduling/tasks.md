# Tasks: Smart Appointment Scheduling

**Input**: Design documents from `specs/001-smart-appointment-scheduling/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Required. The specification defines independent tests, acceptance scenarios, concurrency guarantees, authorization outcomes, deadlines, and migration preservation criteria. Within every story, write the listed tests first and confirm they fail for the intended reason before implementation.

**Organization**: Tasks are grouped by user story. Shared infrastructure that every story requires is limited to Setup and Foundational phases.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes different files and has no dependency on another unfinished task in the same batch.
- **[Story]**: Maps the task to one of the nine user stories in `spec.md`.
- Every task names the exact file or files it creates or changes.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Align the runtime, builds, dependencies, and feature configuration before adding domain behavior.

- [ ] T001 Upgrade both Java toolchains to 21 and add synchronized Spring Security, Thymeleaf Security, Spring Session JDBC, Flyway database modules, Spring AI 2.0.1 Ollama, Timefold 2.5.0, JSON-schema validation, and test dependencies in `pom.xml` and `build.gradle`
- [ ] T002 [P] Replace SQL initializer settings with Flyway locations, add 30-minute session settings, disable Spring Session schema initialization and Spring AI automatic retries, and add Ollama/Timefold/deadline properties in `src/main/resources/application.properties`, `src/main/resources/application-mysql.properties`, `src/main/resources/application-postgres.properties`, `src/main/resources/application-demo.properties`, and `src/main/resources/application-migration.properties`
- [ ] T003 [P] Create typed scheduling, integration-deadline, and bounded-executor configuration in `src/main/java/org/springframework/samples/petclinic/scheduling/config/SchedulingProperties.java`, `ClockConfiguration.java`, and `AsyncExecutionConfiguration.java`
- [ ] T004 Add a build-parity test for Java, Spring AI, and Timefold coordinates and verify Boot dependency convergence in `src/test/java/org/springframework/samples/petclinic/system/BuildConfigurationParityTests.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish migration ownership, authentication, shared policy data, audit/job persistence, fallback persistence, and stale-write handling used by every story.

**Critical**: No user-story implementation begins until this phase is complete.

### Migration tests and baselines

- [ ] T005 [P] Add failing fresh-schema and legacy-data-preservation tests for H2 Flyway adoption in `src/test/java/org/springframework/samples/petclinic/scheduling/migration/H2SchedulingMigrationTests.java`
- [ ] T006 [P] Add failing fresh-schema and one-time-baseline tests using a reusable MySQL container in `src/test/java/org/springframework/samples/petclinic/scheduling/migration/MySqlSchedulingMigrationTests.java` and `src/test/java/org/springframework/samples/petclinic/support/MySqlContainerConfiguration.java`
- [ ] T007 [P] Add failing fresh-schema and one-time-baseline tests using a reusable PostgreSQL container in `src/test/java/org/springframework/samples/petclinic/scheduling/migration/PostgresSchedulingMigrationTests.java` and `src/test/java/org/springframework/samples/petclinic/support/PostgresContainerConfiguration.java`
- [ ] T008 [P] Convert the current H2 schema and synthetic catalog data into non-destructive Flyway baseline/demo migrations in `src/main/resources/db/migration/h2/V1__legacy_petclinic_schema.sql` and `src/main/resources/db/demo/h2/R__demo_data.sql`
- [ ] T009 [P] Convert the current MySQL schema and synthetic catalog data into Flyway baseline/demo migrations in `src/main/resources/db/migration/mysql/V1__legacy_petclinic_schema.sql` and `src/main/resources/db/demo/mysql/R__demo_data.sql`
- [ ] T010 [P] Convert the current PostgreSQL schema and synthetic catalog data into Flyway baseline/demo migrations in `src/main/resources/db/migration/postgres/V1__legacy_petclinic_schema.sql` and `src/main/resources/db/demo/postgres/R__demo_data.sql`
- [ ] T011 [P] Add all account, Spring Session, scheduling, policy, reservation, queue, job, integration-attempt, audit, uniqueness, and owner/pet retention-restricting constraints/indexes for H2 in `src/main/resources/db/migration/h2/V2__smart_appointment_scheduling.sql`
- [ ] T012 [P] Add the schema-equivalent scheduling and retention constraints for MySQL in `src/main/resources/db/migration/mysql/V2__smart_appointment_scheduling.sql`
- [ ] T013 [P] Add the schema-equivalent scheduling and retention constraints for PostgreSQL in `src/main/resources/db/migration/postgres/V2__smart_appointment_scheduling.sql`

### Shared domain and security

- [ ] T014 Add failing defaults, bounds, versioning, and repository tests for clinic policy and availability facts in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/ClinicPolicyRepositoryTests.java`
- [ ] T015 Create clinic policy, duration, hours, named-period, closure, veterinarian shift/exception/leave entities and repositories in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/ClinicSchedulingPolicy.java`, `AllowedDuration.java`, `ClinicHours.java`, `NamedPeriod.java`, `ClinicClosure.java`, `VetRecurringShift.java`, `VetDateException.java`, `VetDateExceptionInterval.java`, `VetLeave.java`, and `AvailabilityRepository.java`
- [ ] T016 [P] Add failing account mapping, username uniqueness, BCrypt, role, owner-link, and route-authorization tests in `src/test/java/org/springframework/samples/petclinic/account/AccountRepositoryTests.java` and `src/test/java/org/springframework/samples/petclinic/account/SecurityRouteMatrixTests.java`
- [ ] T017 [P] Create the account aggregate, repository, database-backed user details, and two-role security chain in `src/main/java/org/springframework/samples/petclinic/account/Account.java`, `AccountRole.java`, `AccountRepository.java`, `PetClinicUserDetailsService.java`, and `SecurityConfiguration.java`
- [ ] T018 Add failing demo-profile credential seeding, deployed-profile non-seeding, 30-minute session, and principal-index tests in `src/test/java/org/springframework/samples/petclinic/account/AccountBootstrapTests.java` and `src/test/java/org/springframework/samples/petclinic/account/SessionConfigurationTests.java`
- [ ] T019 Implement profile-gated synthetic owner/admin account bootstrap and Spring Session JDBC integration in `src/main/java/org/springframework/samples/petclinic/account/AccountBootstrap.java` and `src/main/java/org/springframework/samples/petclinic/account/SessionConfiguration.java`

### Shared audit, execution, queue, and web safety

- [ ] T020 [P] Add failing append-only audit, canonical execution payload, per-attempt timing, unique trigger-key, and optimistic claim tests in `src/test/java/org/springframework/samples/petclinic/scheduling/audit/AuditAndExecutionRepositoryTests.java`
- [ ] T021 [P] Create audit and durable integration execution/attempt aggregates and repositories in `src/main/java/org/springframework/samples/petclinic/scheduling/audit/AuditEvent.java`, `AuditEventRepository.java`, `AuditService.java`, `IntegrationExecution.java`, `IntegrationAttempt.java`, and `IntegrationExecutionRepository.java`
- [ ] T022 Add failing after-commit dispatch, duplicate-claim, expired-running recovery, and superseded-completion tests in `src/test/java/org/springframework/samples/petclinic/scheduling/job/IntegrationExecutionDispatcherTests.java`
- [ ] T023 Implement after-commit bounded dispatch and startup recovery without holding database transactions across integrations in `src/main/java/org/springframework/samples/petclinic/scheduling/job/IntegrationExecutionDispatcher.java`, `IntegrationExecutionWorkerRegistry.java`, and `IntegrationExecutionRecovery.java`
- [ ] T024 [P] Create the unique-per-request staff queue aggregate, note aggregate, repository, and idempotent fallback entry service in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueItem.java`, `QueueNote.java`, `QueueState.java`, `StaffQueueRepository.java`, and `FallbackRoutingService.java`
- [ ] T025 Add shared optimistic-version conflict handling, safe-input preservation, Problem Detail mapping, and 409 HTML rendering tests/implementation in `src/main/java/org/springframework/samples/petclinic/system/StaleStateException.java`, `SchedulingExceptionHandler.java`, and `src/test/java/org/springframework/samples/petclinic/system/SchedulingExceptionHandlerTests.java`

**Checkpoint**: Fresh and adopted databases migrate; authenticated role boundaries work; shared policy, audit, durable operation, fallback, and stale-write infrastructure are ready.

---

## Phase 3: User Story 1 - Describe and Confirm an Appointment Need (Priority: P1) — MVP

**Goal**: An authenticated owner submits one owned pet's prose, explicitly agrees or declines LLM use, receives a resumable versioned structured interpretation, corrects permitted fields, and confirms a complete request without creating an appointment.

**Independent Test**: Submit valid prose for an owned pet, consent, leave/refresh/resume processing, review resolved dates and issues, edit an allowed field without a second LLM call, and confirm the structured request. Also verify declined consent and invalid/uncertain output enter staff handling.

### Tests for User Story 1

- [ ] T026 [P] [US1] Add failing request-state, immutable-pet, one-active-request, source-revision, consent-scope, editable-field, and confirmation tests in `src/test/java/org/springframework/samples/petclinic/scheduling/request/RequestWorkflowServiceTests.java`
- [ ] T027 [P] [US1] Add failing v1 JSON-schema tests for missing/invalid required fields, explicit nulls, unknown-field capture/removal, configured codes, resolved dates, uncertainty, and cross-field contradictions in `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationSchemaValidatorTests.java`
- [ ] T028 [P] [US1] Add failing Ollama adapter tests for native `outputSchema`, raw response capture, requested/resolved model IDs, framework retries disabled, two-call maximum, shared monotonic deadline, transient retry, and no retry for valid uncertainty in `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/SpringAiOllamaInterpretationAdapterTests.java`
- [ ] T029 [P] [US1] Add failing JPA tests for request/text/consent/interpretation/revision/window relationships and concurrent active-pet guard insertion in `src/test/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestRepositoryTests.java`
- [ ] T030 [P] [US1] Add failing secured MVC tests for request entry, upcoming appointments, unchecked consent, separate decline action, review allowlist, issue summary/inline messages, disabled confirmation, prose revision, and dashboard Resume in `src/test/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerInterpretationControllerTests.java`
- [ ] T031 [P] [US1] Add failing JSON contract tests for operation ownership, no-store caching, owner-safe status fields, no percentage/internal detail, and canonical next URL in `src/test/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerOperationStatusControllerTests.java`
- [ ] T032 [US1] Add failing full request-to-confirmed-interpretation and LLM audit journeys with deterministic LLM plus restart/resume behavior in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/OwnerInterpretationJourneyTests.java` and `src/test/java/org/springframework/samples/petclinic/scheduling/integration/InterpretationAuditTests.java`

### Implementation for User Story 1

- [ ] T033 [P] [US1] Create request, active-pet guard, text revision, consent, structured revision, window, interpretation provenance, and state enums in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequest.java`, `ActivePetRequest.java`, `RequestTextRevision.java`, `ConsentRecord.java`, `RequestRevision.java`, `RequestWindow.java`, `InterpretationRecord.java`, and `RequestState.java`
- [ ] T034 [P] [US1] Create owner-scoped request aggregate repositories and projections in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestRepository.java`, `RequestRevisionRepository.java`, and `OwnerRequestProjectionRepository.java`
- [ ] T035 [P] [US1] Copy the approved schema into runtime resources, create the versioned prompt, and define immutable structured DTOs in `src/main/resources/schemas/llm-interpretation-v1.schema.json`, `src/main/resources/prompts/appointment-interpretation-v1.st`, and `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/AppointmentInterpretationV1.java`
- [ ] T036 [US1] Implement raw-tree unknown-field extraction, JSON-schema binding, configured-value validation, deterministic uncertainty classification, and server-owned issue codes in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationSchemaValidator.java` and `InterpretationValidationResult.java`
- [ ] T037 [US1] Implement the non-streaming Spring AI Ollama port/adapter with native schema and raw response/model digest capture in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationPort.java` and `SpringAiOllamaInterpretationAdapter.java`
- [ ] T038 [US1] Implement the application-owned maximum-two-call retry/deadline coordinator and canonical execution evidence mapping in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationCoordinator.java` and `InterpretationExecutionEvidence.java`
- [ ] T039 [US1] Implement transactional request creation, consent/decline, owner-editable review, prose revision, deterministic confirmation, and staff-routing workflows in `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestWorkflowService.java`, `ConsentService.java`, and `RequestRevisionService.java`
- [ ] T040 [US1] Register the LLM durable-operation worker with version/supersession checks in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/LlmInterpretationExecutionWorker.java`
- [ ] T041 [P] [US1] Implement owner-safe operation and dashboard/resume projections in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerOperationStatusService.java` and `OwnerDashboardService.java`
- [ ] T042 [US1] Implement owner request, consent, interpretation, processing, status, and polling controllers/forms in `src/main/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerRequestController.java`, `OwnerInterpretationController.java`, `OwnerOperationStatusController.java`, `OwnerRequestForm.java`, and `InterpretationReviewForm.java`
- [ ] T043 [P] [US1] Create separate desktop dashboard, request, consent, processing, interpretation-review, ready, and staff-status templates with shared progress header in `src/main/resources/templates/scheduling/owner/dashboard.html`, `request-form.html`, `consent.html`, `processing.html`, `interpretation-review.html`, `ready.html`, `staff-status.html`, and `src/main/resources/templates/fragments/scheduling-progress.html`
- [ ] T044 [US1] Add polling/resume JavaScript, localized owner-safe status/validation copy, and LLM consent/outcome audit mapping in `src/main/resources/static/resources/js/scheduling-status.js`, `src/main/resources/messages/messages.properties`, and `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationAuditMapper.java`

**Checkpoint**: US1 works independently; no appointment or slot selection is required.

---

## Phase 4: User Story 2 - Receive and Accept One Safe Suggestion (Priority: P1)

**Goal**: A confirmed request invokes Timefold for one deterministic preferred or owner-authorized fallback slot, acquires an exclusive hold, and atomically confirms one non-conflicting appointment.

**Independent Test**: With configured capacity and a confirmed request, request one suggestion, verify only the top Timefold result is held/displayed, exercise no-preferred fallback choice, and accept before expiry. Simulated stale acquisition reruns once inside the same five-second budget and never exposes the stale result.

### Tests for User Story 2

- [ ] T045 [P] [US2] Add failing pure score-policy tests for every hard rule, preference level, earliest start, clinic-efficiency tie-break, stable ordinal, public explanation, and exact `BendableScore` components in `src/test/java/org/springframework/samples/petclinic/scheduling/matching/SlotScorePolicyTests.java`
- [ ] T046 [P] [US2] Add failing full Timefold tests for one nullable entity, `ALLOCATE_ENTITY_FROM_QUEUE`, `pickEarlyType=NEVER`, stable repeatability, preferred/fallback modes, no local search, and partial-deadline rejection in `src/test/java/org/springframework/samples/petclinic/scheduling/matching/TimefoldSlotSolverTests.java`
- [ ] T047 [P] [US2] Add failing transactional tests for veterinarian/pet reservation blocks, one active hold, expiry cleanup, atomic hold-to-appointment conversion, and losing uniqueness collisions in `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/ReservationServiceTests.java`
- [ ] T048 [P] [US2] Add failing coordinator/audit tests proving snapshot creation, Timefold-only selection on all automated paths, one stale refresh/rerun, shared five-second deadline, supersession, fallback to staff, and no stale Offer in `src/test/java/org/springframework/samples/petclinic/scheduling/matching/MatchingCoordinatorTests.java` and `src/test/java/org/springframework/samples/petclinic/scheduling/integration/TimefoldAuditAndStaleResultTests.java`
- [ ] T049 [P] [US2] Add failing secured MVC tests for request-suggestion, processing, no-preferred choice, single fallback offer, exact expiry/countdown seed, two-minute warning, accept, and owner-safe output in `src/test/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerSuggestionAndOfferControllerTests.java`
- [ ] T050 [US2] Add a failing end-to-end preferred/fallback/acceptance journey with a fixed clock and deterministic solver in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/OwnerSuggestionJourneyTests.java`

### Implementation for User Story 2

- [ ] T051 [P] [US2] Create Offer, Hold, ReservationBlock, Appointment aggregates and lifecycle enums in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/Offer.java`, `Hold.java`, `ReservationBlock.java`, `Appointment.java`, `OfferStatus.java`, `HoldStatus.java`, and `AppointmentStatus.java`
- [ ] T052 [P] [US2] Create offer/hold/reservation/appointment repositories with locking and owner-scoped queries in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OfferRepository.java`, `HoldRepository.java`, `ReservationBlockRepository.java`, and `AppointmentRepository.java`
- [ ] T053 [P] [US2] Create the immutable Timefold solution, entity, candidate, snapshot, mode, and result types in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/SlotSelectionSolution.java`, `SlotAssignment.java`, `CandidateSlot.java`, `SlotSelectionSnapshot.java`, `MatchingMode.java`, and `SlotSelectionResult.java`
- [ ] T054 [US2] Implement the single-source pure scoring/explanation policy and `EasyScoreCalculator<SlotSelectionSolution,BendableScore>` in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/SlotScorePolicy.java`, `SlotScoreComponents.java`, and `SlotEasyScoreCalculator.java`
- [ ] T055 [US2] Configure the deterministic construction-only solver and deadline watchdog that discards partial/late results without Enterprise APIs in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/TimefoldSolverConfiguration.java` and `TimefoldSlotSolver.java`
- [ ] T056 [US2] Implement availability precedence resolution, 15-minute candidate generation, affected-fact versioning, and immutable snapshot construction in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/AvailabilityResolver.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/matching/CandidateSlotFactory.java`, and `SlotSelectionSnapshotFactory.java`
- [ ] T057 [US2] Implement portable all-block atomic hold acquisition, current-fact revalidation, expiry cleanup, and staff-safe conflict outcomes in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/ReservationService.java`
- [ ] T058 [US2] Implement preferred/fallback matching orchestration, one permitted stale-snapshot rerun, execution evidence, and staff routing in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/MatchingCoordinator.java` and `TimefoldMatchingExecutionWorker.java`
- [ ] T059 [US2] Implement offer creation and atomic active-hold acceptance that creates an appointment and transfers reservation blocks in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OfferService.java` and `OfferAcceptanceService.java`
- [ ] T060 [US2] Implement suggestion/fallback/offer/accept controllers and versioned forms in `src/main/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerSuggestionController.java`, `OwnerOfferController.java`, `SuggestionActionForm.java`, and `OfferActionForm.java`
- [ ] T061 [P] [US2] Create suggestion, no-preferred, offer, and unavailable templates plus authoritative countdown/warning behavior in `src/main/resources/templates/scheduling/owner/suggestion.html`, `fallback-choice.html`, `offer.html`, `offer-unavailable.html`, and `src/main/resources/static/resources/js/hold-countdown.js`
- [ ] T062 [US2] Persist snapshot/result/domain score-component/timing/outcome evidence without enterprise-only analysis APIs in `src/main/java/org/springframework/samples/petclinic/scheduling/matching/TimefoldExecutionEvidence.java`

**Checkpoint**: US1 + US2 form the first complete smart-scheduling demo.

---

## Phase 5: User Story 3 - Reject, Revise, or Withdraw Before Booking (Priority: P1)

**Goal**: Owners can explicitly reject/exclude a held offer, recover from expiry, start a clean request revision, resume the correct page, or permanently withdraw before booking without losing history.

**Independent Test**: Reject an offer through the inline confirmation, receive a different candidate, revise availability/duration and verify the counter/exclusion reset, exercise expiry, then withdraw an unscheduled request and verify no hold/queue work remains.

### Tests for User Story 3

- [ ] T063 [P] [US3] Add failing offer rejection/exclusion, optional staff-only reason, expiry, fifth-offer limit, and no-repeat tests in `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/OfferDecisionServiceTests.java`
- [ ] T064 [P] [US3] Add failing revision, active-hold release, history retention, counter reset, source re-consent, withdrawal, superseded-worker, and audit tests in `src/test/java/org/springframework/samples/petclinic/scheduling/request/RequestRevisionAndWithdrawalTests.java` and `src/test/java/org/springframework/samples/petclinic/scheduling/integration/RequestRecoveryAuditTests.java`
- [ ] T065 [P] [US3] Add failing MVC tests for inline two-step rejection, confirmation-dialog withdrawal, no undo, expired offer next actions, safe stale input, and dashboard Resume routing in `src/test/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerRequestRecoveryControllerTests.java`
- [ ] T066 [US3] Add a failing end-to-end reject → different offer → revise → withdraw journey in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/OwnerRecoveryJourneyTests.java`

### Implementation for User Story 3

- [ ] T067 [US3] Implement explicit rejection, exact veterinarian/time exclusion, reason privacy, offer counting, and next-state selection in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OfferDecisionService.java`
- [ ] T068 [US3] Implement authoritative hold expiry scanning and on-read expiry resolution without client-side mutation in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/HoldExpiryService.java` and `HoldExpiryScheduler.java`
- [ ] T069 [US3] Extend revision and withdrawal transactions to release holds/queue work, retain history, reset new-revision exclusions, and block reopen in `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestRevisionService.java` and `RequestWithdrawalService.java`
- [ ] T070 [P] [US3] Implement owner-safe request/offer history and canonical Resume route projection in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerRequestHistoryService.java` and `OwnerResumeRouteResolver.java`
- [ ] T071 [US3] Add reject, revise, withdraw, history, and resume endpoints/forms in `src/main/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerRequestRecoveryController.java`, `OfferRejectionForm.java`, `RequestRevisionForm.java`, and `RequestWithdrawalForm.java`
- [ ] T072 [P] [US3] Add inline rejection confirmation, revision, withdrawal dialog, request history, and expiry-next-action UI in `src/main/resources/templates/scheduling/owner/offer.html`, `revise-request.html`, `withdraw-dialog.html`, `request-history.html`, and `src/main/resources/static/resources/js/offer-rejection.js`
- [ ] T073 [US3] Wire fifth rejection/expiry routing and append-only recovery audit events in `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestAutomationLimitService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestRecoveryAuditService.java`

**Checkpoint**: The complete owner pre-booking lifecycle is independently recoverable and auditable.

---

## Phase 6: User Story 4 - Recover Through Staff Assistance (Priority: P1)

**Goal**: Staff can prioritize, claim, reassign, and resolve fallback requests through manual interpretation, one manually held offer, or eligible direct booking with valid agreement/follow-up evidence.

**Independent Test**: Force a request to staff, claim it, reassign/unclaim with audit reasons, complete a manual interpretation without sending declined text to the LLM, and either offer one held slot or book directly with valid authorization evidence.

### Tests for User Story 4

- [ ] T074 [P] [US4] Add failing queue ordering, unique request item, claim, visibility, no-expiry, unclaim/reassign reason, sub-state, note privacy, audit, and owner-projection tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueServiceTests.java` and `src/test/java/org/springframework/samples/petclinic/scheduling/integration/StaffQueueAuditVisibilityTests.java`
- [ ] T075 [P] [US4] Add failing direct-book authorization tests for recorded owner agreement and documented same-pet follow-up/recheck only, including declined-consent LLM prohibition in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/StaffAssistedSchedulingServiceTests.java`
- [ ] T076 [P] [US4] Add failing STAFF-only MVC tests for persistent navigation, queue/detail pages, claim/reassign/unclaim, manual interpretation, notes, manual offer, and direct booking forms in `src/test/java/org/springframework/samples/petclinic/scheduling/web/staff/StaffQueueControllerTests.java`
- [ ] T077 [US4] Add a failing fallback-to-staff end-to-end journey with owner-safe status and staff-only data assertions in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/StaffAssistanceJourneyTests.java`

### Implementation for User Story 4

- [ ] T078 [US4] Implement queue sorting, claims, unclaim/reassign, explicit sub-state transitions, resolution, and staff-only notes in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueService.java`
- [ ] T079 [US4] Implement staff manual interpretation/correction with full clinical fields and declined-consent enforcement in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/ManualInterpretationService.java`
- [ ] T080 [P] [US4] Implement owner-agreement and supporting-Visit authorization policy/value types in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/BookingAuthorization.java` and `BookingAuthorizationPolicy.java`
- [ ] T081 [US4] Implement staff exact-slot offer and direct booking through shared eligibility/reservation services in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffAssistedSchedulingService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/StaffBookingService.java`
- [ ] T082 [US4] Implement queue/detail/action controllers and versioned forms in `src/main/java/org/springframework/samples/petclinic/scheduling/web/staff/StaffQueueController.java`, `QueueAssignmentForm.java`, `ManualInterpretationForm.java`, `StaffOfferForm.java`, and `StaffBookingForm.java`
- [ ] T083 [P] [US4] Create shared staff navigation, queue list, full detail, manual interpretation, offer, and booking templates in `src/main/resources/templates/fragments/staff-navigation.html`, `src/main/resources/templates/scheduling/staff/queue.html`, `queue-detail.html`, `manual-interpretation.html`, `offer-form.html`, and `booking-form.html`
- [ ] T084 [US4] Project only plain staff-handling status to owners and audit every staff queue/assisted-scheduling change in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/OwnerStaffHandlingProjection.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueAuditService.java`

**Checkpoint**: Every automated dead end has one visible staff work item and an eligible resolution path.

---

## Phase 7: User Story 5 - Handle Possible Emergencies Safely (Priority: P1)

**Goal**: Fixed urgent-care guidance is always visible; a versioned term screen or LLM urgency flag can only raise concern, immediately prioritize staff handling, and never reassure the owner.

**Independent Test**: Submit text matching a configured emergency term while the LLM is unavailable and verify immediate guidance plus a high-priority queue item. Verify a negative screen produces no non-urgent reassurance.

### Tests for User Story 5

- [ ] T085 [P] [US5] Add failing normalized-term, version, match-audit, raise-only, false-to-true-only urgency, and negative-copy tests in `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/EmergencyScreeningServiceTests.java`
- [ ] T086 [P] [US5] Add failing integration/audit tests for keyword matches and schema-valid LLM urgency when Ollama succeeds, fails, or times out in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/EmergencyFallbackTests.java` and `src/test/java/org/springframework/samples/petclinic/scheduling/integration/EmergencyAuditTests.java`
- [ ] T087 [P] [US5] Add failing MVC/queue tests for guidance on the initial form, immediate urgent state, emergency-first sorting, staff-safe details, and no reassurance in `src/test/java/org/springframework/samples/petclinic/scheduling/web/owner/UrgentRequestExperienceTests.java`

### Implementation for User Story 5

- [ ] T088 [P] [US5] Create versioned emergency-term entity/repository and default safe guidance seed access in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/EmergencyTerm.java` and `EmergencyTermRepository.java`
- [ ] T089 [US5] Implement audited local term matching and idempotent high-priority staff routing in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/EmergencyScreeningService.java` and `UrgentRequestService.java`
- [ ] T090 [US5] Invoke the independent screen before LLM dispatch and allow validated LLM urgency only to raise the retained request flag in `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestWorkflowService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/LlmInterpretationExecutionWorker.java`
- [ ] T091 [P] [US5] Render persistent guidance and urgent owner-safe status without non-urgent claims in `src/main/resources/templates/scheduling/owner/request-form.html`, `src/main/resources/templates/scheduling/owner/staff-status.html`, and `src/main/resources/messages/messages.properties`
- [ ] T092 [US5] Persist emergency-screen version/matches and retain staff priority when later integrations fail in `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/EmergencyAuditMapper.java`

**Checkpoint**: Urgent guidance and staff escalation work independently of Ollama and Timefold.

---

## Phase 8: User Story 6 - Manage Clinic Capacity and Scheduling Policy (Priority: P2)

**Goal**: Staff manage bounded clinic policy, named periods, closures, recurring split shifts, date exceptions, and leave with documented precedence and protection for appointments/holds.

**Independent Test**: Configure split shifts and a date exception, verify closure/leave/exception/shift precedence, block changes affecting reservations, and prove confirmed named-period windows/time zone remain stable.

### Tests for User Story 6

- [ ] T093 [P] [US6] Add failing command/effective-capacity tests for closure > leave > exception > recurring-shift precedence, split shifts, zero-interval exceptions, same-day/grid rules, and DST-safe resolution in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/AvailabilityCommandServiceTests.java`
- [ ] T094 [P] [US6] Add failing transactional tests that block every policy/availability mutation conflicting with an appointment or hold until explicit release/reschedule in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/AvailabilityConflictGuardTests.java`
- [ ] T095 [P] [US6] Add failing policy tests for defaults, duration/horizon/hold bounds, fixed grid/notice, zone immutability, non-overlapping named periods, and concrete revision snapshots in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/ClinicPolicyServiceTests.java`
- [ ] T096 [P] [US6] Add failing STAFF-only MVC and integration tests for separate Availability/Settings pages, versioned forms, conflict presentation, deliberate hold release, stale submissions, audit reconstruction, and no unsolicited offers in `src/test/java/org/springframework/samples/petclinic/scheduling/web/staff/StaffAvailabilityAndSettingsControllerTests.java` and `src/test/java/org/springframework/samples/petclinic/scheduling/integration/ClinicCapacityJourneyTests.java`

### Implementation for User Story 6

- [ ] T097 [US6] Implement bounded clinic policy, duration, hours, named-period, urgent-guidance, emergency-term, and time-zone command service in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/ClinicPolicyService.java`
- [ ] T098 [US6] Implement recurring shift, date exception, leave, and closure command service with overlap/grid/same-day validation in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/AvailabilityCommandService.java`
- [ ] T099 [US6] Implement affected-reservation discovery and mandatory appointment/hold conflict blocking in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/AvailabilityConflictGuard.java`
- [ ] T100 [US6] Implement Availability and Settings controllers plus versioned policy/rule forms in `src/main/java/org/springframework/samples/petclinic/scheduling/web/staff/StaffAvailabilityController.java`, `StaffSettingsController.java`, `ClinicPolicyForm.java`, and `AvailabilityRuleForm.java`
- [ ] T101 [P] [US6] Create separate availability and settings workspaces with conflict/hold-release flows in `src/main/resources/templates/scheduling/staff/availability.html`, `availability-rule-form.html`, `settings.html`, and `capacity-conflict.html`
- [ ] T102 [US6] Increment configuration versions, snapshot confirmed named periods as concrete windows, and propagate only new versions to new/revised requests and solver snapshots in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/ConfigurationVersionService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/request/NamedPeriodSnapshotService.java`
- [ ] T103 [US6] Publish configuration-version and capacity audit events without triggering waiting requests or creating unsolicited offers in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/CapacityAuditService.java`

**Checkpoint**: Staff-controlled capacity is safe, reproducible, and usable by existing request/matching services.

---

## Phase 9: User Story 7 - Manage Appointments Through Their Lifecycle (Priority: P2)

**Goal**: Staff directly book, reschedule, cancel, complete, mark no-show, and correct appointments while preserving identity, reservations, reasons, and exactly one consistent Visit link.

**Independent Test**: Book and reschedule an eligible appointment, reject conflicts/out-of-availability booking, complete it after end to create one linked Visit, mark another no-show without a Visit, and reconcile an error only through correction.

### Tests for User Story 7

- [ ] T104 [P] [US7] Add failing lifecycle tests for staff reason categories, any-future-time exemption from owner notice, availability/conflict and other-owner-hold checks, identity-preserving reschedule, cancel, completion/no-show timing, and correction-only transitions in `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentLifecycleServiceTests.java`
- [ ] T105 [P] [US7] Add failing persistence tests for nullable legacy Visit links, unique appointment Visit, assigned veterinarian, actual completion date, and transactional correction consistency in `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentVisitPersistenceTests.java`
- [ ] T106 [P] [US7] Add failing STAFF-only MVC tests for Calendar, booking, detail, reschedule, cancel, complete, no-show, and correction forms in `src/test/java/org/springframework/samples/petclinic/scheduling/web/staff/StaffCalendarAndAppointmentControllerTests.java`
- [ ] T107 [US7] Add a failing full staff appointment lifecycle journey with audit old/new values in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/AppointmentLifecycleJourneyTests.java`

### Implementation for User Story 7

- [ ] T108 [P] [US7] Extend historical Visit mapping with optional appointment/veterinarian links and prevent the legacy Visit form from scheduling future care in `src/main/java/org/springframework/samples/petclinic/owner/Visit.java`, `VisitController.java`, and `src/main/resources/templates/pets/createOrUpdateVisitForm.html`
- [ ] T109 [US7] Implement direct booking, atomic reschedule/block replacement, staff/owner cancellation primitives, completion, and no-show transitions in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentLifecycleService.java` and `AppointmentReasonCategory.java`
- [ ] T110 [US7] Implement audited terminal-state correction with atomic Visit create/remove/update reconciliation in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentCorrectionService.java`
- [ ] T111 [P] [US7] Implement bounded clinic-zone calendar and appointment-detail projections in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/StaffCalendarQueryService.java`
- [ ] T112 [US7] Implement staff calendar and lifecycle controllers/forms in `src/main/java/org/springframework/samples/petclinic/scheduling/web/staff/StaffCalendarController.java`, `StaffAppointmentController.java`, `StaffAppointmentForm.java`, and `AppointmentCorrectionForm.java`
- [ ] T113 [P] [US7] Create Calendar, appointment detail, booking/reschedule, completion/no-show, and correction templates in `src/main/resources/templates/scheduling/staff/calendar.html`, `appointment-detail.html`, `appointment-form.html`, `appointment-outcome-form.html`, and `appointment-correction.html`
- [ ] T114 [US7] Record before/after times, actor, reason, status, and Visit linkage for every lifecycle mutation in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentAuditService.java`

**Checkpoint**: Future care uses Appointment exclusively and completed care produces consistent historical Visit records.

---

## Phase 10: User Story 8 - Use Owner Appointment Self-Service (Priority: P2)

**Goal**: Owners see only their own profile/pets, requests, offers, appointments, and completed visits and can cancel an upcoming appointment before start without reopening its request.

**Independent Test**: View one owner's complete safe history, cancel that owner's future appointment through explicit confirmation, and prove another owner's objects/actions plus staff-only details are inaccessible.

### Tests for User Story 8

- [ ] T115 [P] [US8] Add failing ownership/IDOR tests for request, pet, profile, offer, appointment, and visit reads/actions including 404 cross-owner behavior in `src/test/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerSchedulingAuthorizationTests.java`
- [ ] T116 [P] [US8] Add failing projection tests that include allowed status/time/veterinarian/specialty/history fields and exclude queue, note, audit, solver, calendar, and error detail in `src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingProjectionTests.java`
- [ ] T117 [P] [US8] Add failing MVC tests for read-only profile/pets, upcoming appointments before new request, cancellation dialog/optional reason/no undo, start boundary, and no request reopen in `src/test/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerSelfServiceControllerTests.java`
- [ ] T118 [US8] Add a failing owner history-and-cancellation journey with two owners in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/OwnerSelfServiceJourneyTests.java`

### Implementation for User Story 8

- [ ] T119 [P] [US8] Implement owner-scoped profile, pet, request, offer, appointment, and visit projections in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingQueryService.java`
- [ ] T120 [US8] Implement before-start owner cancellation with optional private reason, reservation release, audit, and no request reopen in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerAppointmentService.java`
- [ ] T121 [US8] Implement owner appointments/history/cancel controllers and form in `src/main/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerAppointmentController.java`, `OwnerHistoryController.java`, and `OwnerCancellationForm.java`
- [ ] T122 [P] [US8] Create owner appointment list/detail, safe history, and cancellation confirmation templates in `src/main/resources/templates/scheduling/owner/appointments.html`, `appointment-detail.html`, `history.html`, and `cancel-appointment-dialog.html`
- [ ] T123 [US8] Add read-only owner profile/pet endpoints/templates while retaining staff-only edit routes in `src/main/java/org/springframework/samples/petclinic/scheduling/web/owner/OwnerProfileController.java` and `src/main/resources/templates/scheduling/owner/profile.html`

**Checkpoint**: Owner visibility and cancellation work without exposing another owner or staff internals.

---

## Phase 11: User Story 9 - Provision and Protect Accounts (Priority: P3)

**Goal**: Staff provision/reset owner accounts with one-time credentials; first-use password change and session invalidation work; predictable accounts remain demo-only; deployed startup requires protected staff credentials.

**Independent Test**: Provision a unique owner username, use the one-time password once within seven days, force password change/session rotation, reset and invalidate all sessions, and verify deployed startup creates no predictable credentials.

### Tests for User Story 9

- [ ] T124 [P] [US9] Add failing normalized username suggestion/collision, cryptographically random one-time password, one-display-only, seven-day expiry, and BCrypt-only persistence tests in `src/test/java/org/springframework/samples/petclinic/account/AccountProvisioningServiceTests.java`
- [ ] T125 [P] [US9] Add failing password-change, minimum length, temporary restriction, session-ID rotation, principal-session deletion on reset, and 30-minute inactivity tests in `src/test/java/org/springframework/samples/petclinic/account/PasswordAndSessionServiceTests.java`
- [ ] T126 [P] [US9] Extend bootstrap tests for lowercase first-name collisions, sole demo staff `admin/admin123`, no forced demo change, no deployed owner seeds, and required protected initial staff configuration in `src/test/java/org/springframework/samples/petclinic/account/AccountBootstrapTests.java`
- [ ] T127 [P] [US9] Add failing MVC/security/audit tests for provision/reset/change-password/logout, temporary-user route restriction, CSRF, absence of failed-login throttling, and credential redaction in `src/test/java/org/springframework/samples/petclinic/account/AccountControllerTests.java` and `src/test/java/org/springframework/samples/petclinic/account/AccountAuditTests.java`

### Implementation for User Story 9

- [ ] T128 [P] [US9] Implement normalized collision-safe username suggestion and secure one-time password generation in `src/main/java/org/springframework/samples/petclinic/account/UsernameSuggester.java` and `OneTimePasswordGenerator.java`
- [ ] T129 [US9] Implement staff owner-account provisioning/reset with single-display credential results and seven-day expiry in `src/main/java/org/springframework/samples/petclinic/account/AccountProvisioningService.java` and `OneTimeCredentialResult.java`
- [ ] T130 [US9] Implement password change, explicit session rotation, credential-version update, and all-principal-session invalidation in `src/main/java/org/springframework/samples/petclinic/account/PasswordService.java` and `AccountSessionService.java`
- [ ] T131 [US9] Complete demo/test collision-safe seeding and deployed initial-staff fail-fast configuration in `src/main/java/org/springframework/samples/petclinic/account/AccountBootstrap.java` and `InitialStaffProperties.java`
- [ ] T132 [US9] Implement provision/reset/change-password controllers and validated forms in `src/main/java/org/springframework/samples/petclinic/account/AccountWebController.java`, `AccountProvisioningForm.java`, and `PasswordChangeForm.java`
- [ ] T133 [P] [US9] Create login, forced-password-change, staff provision/reset, and non-cacheable one-time credential templates in `src/main/resources/templates/account/login.html`, `change-password.html`, `provision-owner.html`, and `one-time-credential.html`
- [ ] T134 [US9] Audit account actions without username enumeration, plaintext/password hashes, session IDs, or credential material in `src/main/java/org/springframework/samples/petclinic/account/AccountAuditService.java`

**Checkpoint**: Account lifecycle and environment-specific credential safeguards satisfy the POC scope without self-registration, password recovery, staff administration, or login throttling.

---

## Phase 12: Polish and Cross-Cutting Verification

**Purpose**: Prove architectural boundaries, portability, scale, audit reconstruction, and the full desktop POC without adding excluded functionality.

- [ ] T135 [P] Add package-boundary tests preventing web-to-repository shortcuts and direct owner-facing slot selection outside the Timefold port in `src/test/java/org/springframework/samples/petclinic/scheduling/architecture/SchedulingArchitectureTests.java`
- [ ] T136 [P] Run barrier-based competing hold/accept/staff-book races against H2, MySQL, and PostgreSQL with exactly one winner in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/CrossDatabaseReservationConcurrencyTests.java`
- [ ] T137 [P] Add the 10,000-record/25-concurrent-user acceptance fixture for retained requests, 10-second interpretation outcomes, five-second match outcomes, and no lost/duplicate reservations in `src/test/java/org/springframework/samples/petclinic/scheduling/performance/SchedulingPocScaleTests.java`
- [ ] T138 [P] Add an audit reconstruction acceptance test covering every FR-083 event and both integration attempt types without runtime logs in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/AuditReconstructionAcceptanceTests.java`
- [ ] T139 [P] Add a complete 401/403/404/authorized-success/CSRF/stale-version matrix for all contract routes in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/SchedulingSecurityAcceptanceTests.java`
- [ ] T140 Add one deterministic full-context desktop owner → Timefold offer → appointment → staff completion → owner history smoke journey in `src/test/java/org/springframework/samples/petclinic/scheduling/integration/SmartSchedulingEndToEndTests.java`
- [ ] T141 Verify fresh and one-time-baselined migration record counts/constraints on all three databases and document the controlled adoption procedure in `src/test/java/org/springframework/samples/petclinic/scheduling/migration/LegacyDataPreservationAcceptanceTests.java` and `specs/001-smart-appointment-scheduling/quickstart.md`
- [ ] T142 [P] Add an opt-in prewarmed `gemma4:latest` live structured-output/deadline probe that is excluded from deterministic CI in `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/OllamaLiveProbeTests.java`
- [ ] T143 Review all owner/staff desktop pages at the POC viewport, status/error copy, text-plus-icon field issues, confirmation behavior, and absence of mobile/formal-accessibility claims in `src/main/resources/messages/messages.properties` and `specs/001-smart-appointment-scheduling/quickstart.md`
- [ ] T144 Run `./mvnw test` and `./gradlew test`, resolve dependency/build drift without weakening tests, and record final validation commands/outcomes in `specs/001-smart-appointment-scheduling/quickstart.md`

---

## Dependencies and Execution Order

### Phase dependencies

1. Phase 1 Setup has no dependency.
2. Phase 2 Foundational depends on Setup and blocks every user story.
3. User-story phases follow the graph below; tests in each phase precede implementation.
4. Phase 12 Polish depends on every story included in the intended release.

### User-story dependency graph

```text
Foundation
├── US1 Describe and confirm request
│   ├── US2 Receive and accept suggestion
│   │   ├── US3 Reject/revise/withdraw
│   │   ├── US4 Staff assistance
│   │   │   └── US7 Appointment lifecycle
│   │   └── US8 Owner self-service (also depends on US7)
│   └── US5 Emergency handling
└── US9 Account provisioning/protection

US6 Capacity and policy management depends on both US2 reservation primitives and US5 emergency-term/guidance support.
US7 consumes appointment primitives from US2 and direct-book authorization from US4.
```

### Story dependencies and independent checkpoints

| Story | Depends on | Independent completion evidence |
|---|---|---|
| US1 | Foundation | Confirmed structured request with consent/review/resume; no appointment |
| US2 | US1 + foundational capacity | One Timefold-selected held slot accepted atomically; fallback and stale rerun covered |
| US3 | US2 | Reject/exclude, expire, revise/reset, resume, and withdraw without lost history |
| US4 | US1 + US2 | Claimed fallback manually interpreted and offered/booked with authorization evidence |
| US5 | US1 | Guidance and emergency-first queue work while LLM is unavailable |
| US6 | US2 + US5 | Staff policy/availability CRUD with precedence, reservation guards, and no unsolicited offers |
| US7 | US2 + US4 | Full staff appointment lifecycle and consistent Visit correction |
| US8 | US2 + US7 | Owner-safe history and before-start cancellation with cross-owner denial |
| US9 | Foundation | Provision/reset/change-password/session/bootstrap safeguards |

## Parallel Execution Examples

After the prerequisite phase/test batch is complete, these are safe examples:

| Story | Parallel test batch | Parallel implementation batch |
|---|---|---|
| US1 | T026–T031 | T033, T034, T035, T041, T043 |
| US2 | T045–T049 | T051, T052, T053, then T061 alongside service integration |
| US3 | T063–T065 | T070 and T072 alongside T067–T069 |
| US4 | T074–T076 | T080 and T083 alongside queue/manual workflow work |
| US5 | T085–T087 | T088 and T091 alongside screening orchestration |
| US6 | T093–T096 | T101 alongside T097–T100 |
| US7 | T104–T106 | T108, T111, and T113 |
| US8 | T115–T117 | T119 and T122 |
| US9 | T124–T127 | T128 and T133 |

At story level, US9 can begin immediately after Foundation. US5 can proceed after US1 while US2 is underway. After US2, US3 and US4 can proceed in parallel; US6 begins when US2 and US5 are complete, US7 follows US4, and US8 follows US7.

## Implementation Strategy

### Smallest independently testable MVP

1. Complete Setup and Foundational phases.
2. Complete US1.
3. Stop and validate prose → consent → persisted interpretation → owner review/confirmation, including manual fallback.

This is the minimum independently testable increment. It deliberately creates no appointment.

### First useful scheduling POC

1. Complete the MVP above.
2. Complete US2.
3. Stop and validate one Timefold-selected, exclusively held, atomically accepted appointment.

### Incremental delivery

1. Add US3 for owner recovery and control.
2. Add US4 and US5 for operational/safety fallback.
3. Add US6 for staff-managed capacity.
4. Add US7 and US8 for lifecycle and owner self-service.
5. Add US9 for complete provisioning/reset/deployed bootstrap behavior.
6. Run Phase 12 before claiming the full POC success criteria.

## Notes

- Do not add mobile support, formal accessibility conformance work, real personal/clinical data, notifications, waitlists, multi-clinic support, or login throttling.
- Keep owner-visible integration failure/status copy generic; retain detailed error classes only in staff/audit records.
- A live Ollama test never replaces deterministic adapter tests.
- No owner-facing automated slot may bypass Timefold, including fallback or a future staff-triggered automatic suggestion.
- Community Edition uses the domain-owned `SlotScorePolicy` explanation; do not call enterprise-only Timefold analysis/recommendation APIs.
- Commit after each task or coherent task group and re-run the story checkpoint before proceeding.
