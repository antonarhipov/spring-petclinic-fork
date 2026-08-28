---

description: "Implementation tasks for Smart Appointment Scheduling"
---

# Tasks: Smart Appointment Scheduling

**Input**: Design documents from
`/specs/001-smart-appointment-scheduling/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md),
[research.md](research.md), [data-model.md](data-model.md),
[contracts/](contracts/), and [quickstart.md](quickstart.md)

**Tests**: Tests are required by the project constitution. Write the specified
test first, verify it fails for the missing behavior, then implement the
corresponding task.

**Organization**: Tasks are grouped by user story. Foundational tasks establish
the shared security, persistence, audit, queue, and configuration capabilities
needed by every story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with other tasks in the same phase once their
  dependencies are complete.
- **[US#]**: Maps a task to a user story in [spec.md](spec.md).
- Every task names the source, resource, test, or documentation file it changes.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Upgrade the runtime and add the dependency/configuration
foundations required by the approved technical design.

- [X] T001 [P] Upgrade Java to 21 and add Spring Security, Flyway, Spring AI 2.0.1, Timefold 2.5.0, and test dependencies in `pom.xml`
- [X] T002 [P] Upgrade Java to 21 and add matching Spring Security, Flyway, Spring AI 2.0.1, Timefold 2.5.0, and test dependencies in `build.gradle`
- [X] T003 Configure Flyway, local/demo profile behavior, `spring.ai.ollama.chat.model`, Ollama connection settings, and safe scheduling logger categories in `src/main/resources/application.properties`
- [X] T004 [P] Configure MySQL and PostgreSQL Flyway locations and disable legacy SQL initialization in `src/main/resources/application-mysql.properties` and `src/main/resources/application-postgres.properties`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish portable migrations, authentication, authorization,
auditability, scheduling data, and deterministic test infrastructure before any
owner or staff workflow.

**⚠️ CRITICAL**: Complete this phase before beginning user-story tasks.

- [X] T005 [P] Create the existing-schema and reference-data Flyway baseline in `src/main/resources/db/migration/h2/V1__petclinic_baseline.sql`
- [X] T006 [P] Create the existing-schema and reference-data Flyway baseline in `src/main/resources/db/migration/mysql/V1__petclinic_baseline.sql`
- [X] T007 [P] Create the existing-schema and reference-data Flyway baseline in `src/main/resources/db/migration/postgres/V1__petclinic_baseline.sql`
- [X] T008 [P] Create H2 account, audit, scheduling, availability, offer, reservation, queue, and appointment schema migrations in `src/main/resources/db/migration/h2/V2__smart_appointment_scheduling.sql`
- [X] T009 [P] Create MySQL account, audit, scheduling, availability, offer, reservation, queue, and appointment schema migrations in `src/main/resources/db/migration/mysql/V2__smart_appointment_scheduling.sql`
- [X] T010 [P] Create PostgreSQL account, audit, scheduling, availability, offer, reservation, queue, and appointment schema migrations in `src/main/resources/db/migration/postgres/V2__smart_appointment_scheduling.sql`
- [X] T011 Verify baseline data IDs, Flyway migration order, and portable schema constraints in `src/test/java/org/springframework/samples/petclinic/system/FlywayMigrationTests.java`
- [X] T012 [P] Create the account aggregate, role enum, and account repository in `src/main/java/org/springframework/samples/petclinic/security/Account.java`, `src/main/java/org/springframework/samples/petclinic/security/AccountRole.java`, and `src/main/java/org/springframework/samples/petclinic/security/AccountRepository.java`
- [X] T013 [P] Create immutable audit records, action types, repository, and actor/correlation context in `src/main/java/org/springframework/samples/petclinic/scheduling/audit/AuditRecord.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/audit/AuditAction.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/audit/AuditRecordRepository.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/audit/SchedulingAuditService.java`
- [X] T014 [P] Create clinic settings, named-period, availability, leave, and closure aggregates with repositories in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/ClinicSchedulingSettings.java`, `NamedDayPeriod.java`, `RecurringVetShift.java`, `VetAvailabilityException.java`, `VetLeave.java`, `ClinicClosure.java`, and their repositories
- [X] T015 [P] Create staff queue aggregate, priority/state enums, repository, and fallback creation service in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueItem.java`, `QueuePriority.java`, `QueueState.java`, `StaffQueueRepository.java`, and `FallbackQueueService.java`
- [X] T016 Implement account provisioning, temporary-password expiry, password reset, demo/local seeded accounts, and the required password-change gate in `src/main/java/org/springframework/samples/petclinic/security/AccountService.java` and `src/main/java/org/springframework/samples/petclinic/security/DemoAccountInitializer.java`
- [X] T017 Configure form login, CSRF, session fixation protection, idle timeout, throttled sign-in failure handling, role route rules, and server-side owner resolution in `src/main/java/org/springframework/samples/petclinic/security/SecurityConfiguration.java`, `AuthenticatedOwner.java`, and `OwnerAccessService.java`
- [X] T018 Implement the password-change controller and Thymeleaf form in `src/main/java/org/springframework/samples/petclinic/security/PasswordController.java` and `src/main/resources/templates/auth/changePassword.html`
- [X] T019 Secure the existing owner, pet, visit, and veterinarian operations for the `STAFF` role and add role-aware navigation in `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`, `PetController.java`, `VisitController.java`, `src/main/java/org/springframework/samples/petclinic/vet/VetController.java`, and `src/main/resources/templates/fragments/layout.html`
- [X] T020 Add deterministic clock, correlation-ID, fake-interpretation, and shared Testcontainers test configuration in `src/test/java/org/springframework/samples/petclinic/scheduling/SchedulingTestConfiguration.java`, `FixedClockConfiguration.java`, and `TestcontainersConfiguration.java`
- [X] T021 Verify login, temporary-password gating, session behavior, CSRF protection, and cross-owner access denial in `src/test/java/org/springframework/samples/petclinic/security/SecurityConfigurationTests.java` and `src/test/java/org/springframework/samples/petclinic/security/OwnerAccessMvcTests.java`
- [X] T022 Verify scheduling settings bounds, named-period overlap rules, availability precedence, queue persistence, and audit redaction in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/ClinicSchedulingSettingsTests.java`, `src/test/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueRepositoryTests.java`, and `src/test/java/org/springframework/samples/petclinic/scheduling/audit/SchedulingAuditServiceTests.java`

**Checkpoint**: Flyway owns schema initialization; operational pages are secured;
shared scheduling settings, audit records, and fallback queue are available.

---

## Phase 3: User Story 1 - Requesting and Accepting a Guided Appointment (Priority: P1) 🎯 MVP

**Goal**: An owner can submit a consented request for an owned pet, confirm a
safe structured interpretation, receive one held suitable offer, and atomically
confirm it as an appointment.

**Independent Test**: With a preconfigured veterinarian shift, a signed-in owner
submits a valid request, reviews interpretation, receives one offer, accepts it
before expiry, and sees one upcoming appointment without viewing a calendar.

### Tests for User Story 1

- [X] T023 [P] [US1] Write interpretation consent, provider-schema, local-schema, semantic-validation, and safe-fallback tests in `src/test/java/org/springframework/samples/petclinic/scheduling/ai/OllamaInterpretationServiceTests.java`
- [X] T024 [P] [US1] Write hard/soft score and deterministic candidate-selection tests in `src/test/java/org/springframework/samples/petclinic/scheduling/solver/AppointmentConstraintProviderTests.java` and `CandidateSelectionServiceTests.java`
- [X] T025 [P] [US1] Write real-database reservation-block uniqueness, hold promotion, and concurrent same-slot acceptance tests in `src/test/java/org/springframework/samples/petclinic/scheduling/offer/ReservationBlockRepositoryTests.java` and `OfferAcceptanceConcurrencyTests.java`
- [X] T026 [P] [US1] Write secured MVC tests for request submission, consent, review, confirmation, single-offer display, and acceptance in `src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingControllerTests.java`

### Implementation for User Story 1

- [X] T027 [P] [US1] Create request, revision, interpretation, availability-window, and request-state entities with repositories in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequest.java`, `RequestRevision.java`, `RequestInterpretation.java`, `RequestAvailabilityWindow.java`, `SchedulingRequestState.java`, and their repository files
- [X] T028 [P] [US1] Create appointment, offer, reservation-block, source/state enums, and repositories in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/Appointment.java`, `AppointmentStatus.java`, `AppointmentSource.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/offer/AppointmentOffer.java`, `OfferState.java`, `ReservationBlock.java`, and their repository files
- [X] T029 [US1] Implement consent-gated interpretation commands, strict response DTOs, provider structured-output request, local schema/semantic validation, and fallback outcomes in `src/main/java/org/springframework/samples/petclinic/scheduling/ai/InterpretationPort.java`, `OllamaInterpretationService.java`, `InterpretationResponse.java`, `InterpretationValidator.java`, and `InterpretationFailure.java`
- [X] T030 [US1] Build the auto-configured baseline ChatClient and isolated per-request `mutate()` client options, with Ollama transport timeout and bounded transient retry policy, in `src/main/java/org/springframework/samples/petclinic/scheduling/ai/OllamaChatClientConfiguration.java` and `OllamaInterpretationService.java`
- [X] T031 [US1] Implement emergency-term screening, persistent urgent-care guidance lookup, and safety-critical review routing in `src/main/java/org/springframework/samples/petclinic/scheduling/request/EmergencyScreeningService.java`, `UrgentCareGuidanceService.java`, and `SchedulingRequestService.java`
- [X] T032 [US1] Model Timefold candidate planning, hard constraints, soft scoring, five-second termination, and result extraction in `src/main/java/org/springframework/samples/petclinic/scheduling/solver/AppointmentPlanningSolution.java`, `AppointmentPlanningEntity.java`, `CandidateSlot.java`, `AppointmentConstraintProvider.java`, and `CandidateSelectionService.java`
- [X] T033 [US1] Implement transactional reservation-block creation, held-offer expiry checks, offer promotion, and atomic appointment confirmation in `src/main/java/org/springframework/samples/petclinic/scheduling/offer/ReservationService.java`, `OfferService.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentService.java`
- [X] T034 [US1] Implement request submission, revision confirmation, matching orchestration, owner-safe lookup, and no-match/AI/solver fallback in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestService.java` and `SchedulingRequestQueryService.java`
- [X] T035 [US1] Add privacy-safe DEBUG diagnostics for interpretation, validation, retry, fallback, candidate generation, Timefold score, selected slot, and timing in `src/main/java/org/springframework/samples/petclinic/scheduling/ai/OllamaInterpretationService.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/solver/CandidateSelectionService.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferService.java`
- [X] T036 [US1] Implement owner request, review, confirmation, offer, acceptance, and dashboard routes from `contracts/owner-scheduling-ui.md` in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingController.java`
- [X] T037 [US1] Create the owner request, interpretation-review, offer, and dashboard templates in `src/main/resources/templates/scheduling/newRequest.html`, `reviewRequest.html`, `offer.html`, and `dashboard.html`
- [X] T038 [US1] Add owner request form validation messages and urgent-care copy in `src/main/resources/messages/messages.properties` and `src/main/resources/templates/fragments/layout.html`
- [X] T039 [US1] Replace the future-visit booking path with appointment-only scheduling and retain legacy visits as history in `src/main/java/org/springframework/samples/petclinic/owner/VisitController.java` and `src/main/resources/templates/pets/createOrUpdateVisitForm.html`
- [X] T040 [US1] Add request, offer, solver, and appointment audit events in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestService.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferService.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentService.java`
- [X] T041 [US1] Add MVC rendering, service fallback, reservation concurrency, and owner-journey integration coverage in `src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingControllerTests.java`, `SchedulingRequestServiceTests.java`, `src/test/java/org/springframework/samples/petclinic/scheduling/offer/OfferAcceptanceConcurrencyTests.java`, and `src/test/java/org/springframework/samples/petclinic/SchedulingIntegrationTests.java`
- [X] T042 [US1] Verify the P1 guided-booking scenario from `specs/001-smart-appointment-scheduling/quickstart.md` using `src/test/java/org/springframework/samples/petclinic/SchedulingIntegrationTests.java`

**Checkpoint**: An owner can complete a consented, safe, one-offer booking flow
against preconfigured clinic availability, with an atomically confirmed
appointment and no calendar exposure.

---

## Phase 4: User Story 2 - Managing Owner Requests and Appointments (Priority: P2)

**Goal**: Owners can manage the guided flow after the first offer by rejecting,
requesting another, revising, withdrawing, viewing history, and cancelling
their own upcoming appointment.

**Independent Test**: An owner rejects an offer, receives a non-repeated next
option, revises or withdraws an unscheduled request, and cancels a future
appointment without accessing another owner's data.

### Tests for User Story 2

- [X] T043 [P] [US2] Write request-revision, immutable-pet, offer-exclusion, five-offer, expiry, and withdrawal state tests in `src/test/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestLifecycleTests.java`
- [X] T044 [P] [US2] Write owner cancellation, multi-upcoming-appointment, owner-history privacy, and cross-owner MVC tests in `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerAppointmentControllerTests.java`

### Implementation for User Story 2

- [X] T045 [US2] Implement structured and prose revision, renewed-consent, immutable-pet, withdrawal, and per-revision offer-exclusion behavior in `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestService.java` and `RequestRevisionService.java`
- [X] T046 [US2] Implement rejection, expiry, release, invalidation, five-offer routing, and next-option behavior in `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferService.java` and `OfferExpiryService.java`
- [X] T047 [US2] Add the bounded held-offer expiration scheduler and testable time trigger in `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferExpirationScheduler.java`
- [X] T048 [US2] Implement owner appointment query and pre-start cancellation behavior in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentService.java` and `OwnerAppointmentQueryService.java`
- [X] T049 [US2] Add owner reject, next, revise, withdraw, history, and cancel routes in `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingController.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerAppointmentController.java`
- [X] T050 [US2] Add owner request-history, rejected-offer, cancellation, and upcoming-appointment templates in `src/main/resources/templates/scheduling/requestHistory.html`, `src/main/resources/templates/scheduling/appointmentDetails.html`, and `src/main/resources/templates/scheduling/dashboard.html`
- [X] T051 [US2] Extend owner-safe auditing for revisions, offer outcomes, withdrawal, and cancellation in `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestRevisionService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentService.java`
- [X] T052 [US2] Add owner-flow integration coverage for rejection, non-repetition, revision, withdrawal, expiry, cancellation, and history visibility in `src/test/java/org/springframework/samples/petclinic/SchedulingOwnerManagementIntegrationTests.java`

**Checkpoint**: Owners can recover from an unsuitable offer and manage their own
future appointments without a duplicate request, data leak, or untracked hold.

---

## Phase 5: User Story 3 - Handling Exceptions Through Staff (Priority: P2)

**Goal**: Staff can safely resolve fallback and emergency requests, claim queue
work, correct interpretations, offer a slot, or directly book with recorded
owner agreement.

**Independent Test**: A declined-consent, failed, no-match, or urgent request
appears in priority order; staff claim it, complete its interpretation, and
create an audited owner offer or direct appointment.

### Tests for User Story 3

- [X] T053 [P] [US3] Write fallback creation, emergency-first ordering, claim/reassign, and terminal queue-state tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueServiceTests.java`
- [X] T054 [P] [US3] Write staff queue authorization, manual interpretation, staff-offer, and direct-booking MVC tests in `src/test/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueControllerTests.java`

### Implementation for User Story 3

- [X] T055 [US3] Implement queue listing, claim, unclaim, reassignment, resolution, and owner-safe queue status in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueService.java` and `StaffQueueQueryService.java`
- [X] T056 [US3] Implement staff manual interpretation completion and safety review resolution without resubmitting declined text to AI in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffInterpretationService.java`
- [X] T057 [US3] Implement staff-assisted held offers and direct appointments with recorded owner agreement in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffSchedulingService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentService.java`
- [X] T058 [US3] Implement secured staff queue and fallback routes from `contracts/staff-calendar-ui.md` in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueController.java`
- [X] T059 [US3] Create queue list, queue detail, manual interpretation, and staff booking templates in `src/main/resources/templates/staff/queue.html`, `queueDetail.html`, `manualInterpretation.html`, and `bookAppointment.html`
- [X] T060 [US3] Record queue claims, reassignments, manual interpretation, staff offers, agreements, and resolutions in `src/main/java/org/springframework/samples/petclinic/scheduling/queue/StaffQueueService.java` and `StaffSchedulingService.java`
- [X] T061 [US3] Add emergency, declined-consent, AI-timeout, solver-timeout, missing-specialty, no-match, and exhausted-offer integration coverage in `src/test/java/org/springframework/samples/petclinic/SchedulingFallbackIntegrationTests.java`
- [X] T062 [US3] Verify the staff-fallback and emergency scenarios from `specs/001-smart-appointment-scheduling/quickstart.md` using `src/test/java/org/springframework/samples/petclinic/SchedulingFallbackIntegrationTests.java`

**Checkpoint**: No owner request reaches a bare dead end; staff can claim and
resolve every fallback path with an auditable, owner-safe outcome.

---

## Phase 6: User Story 4 - Managing the Clinic Calendar (Priority: P3)

**Goal**: Staff can configure calendar rules and veterinarian availability,
directly manage appointments, and maintain accurate visit history without
invalidating holds or confirmed bookings.

**Independent Test**: Staff configure an eligible veterinarian shift, book and
manage an appointment, and see a conflicting availability change rejected until
the affected booking or hold is deliberately resolved.

### Tests for User Story 4

- [X] T063 [P] [US4] Write settings bounds, immutable time-zone, named-period snapshot, and availability-precedence tests in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/ClinicSchedulingSettingsServiceTests.java`
- [X] T064 [P] [US4] Write shift, exception, leave, closure, confirmed-appointment conflict, and held-offer conflict tests in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/VeterinarianAvailabilityServiceTests.java`
- [X] T065 [P] [US4] Write staff appointment booking, rescheduling, cancellation, completion, no-show, visit-link, and correction tests in `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/StaffAppointmentServiceTests.java`
- [X] T066 [P] [US4] Write staff calendar, configuration, lifecycle, and audit MVC tests in `src/test/java/org/springframework/samples/petclinic/scheduling/availability/StaffCalendarControllerTests.java` and `src/test/java/org/springframework/samples/petclinic/scheduling/audit/StaffAuditControllerTests.java`

### Implementation for User Story 4

- [X] T067 [US4] Implement clinic settings, fixed grid, named-period, time-zone immutability, and urgent-guidance management in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/ClinicSchedulingSettingsService.java`
- [X] T068 [US4] Implement recurring shift, date-exception, leave, and closure management with configured precedence in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/VeterinarianAvailabilityService.java` and `AvailabilityQueryService.java`
- [X] T069 [US4] Enforce availability-change conflict checks against confirmed appointments and held offers, including explicit audited hold release, in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/AvailabilityConflictService.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/offer/OfferService.java`
- [X] T070 [US4] Implement staff direct booking, rescheduling, cancellation, completion, no-show, lifecycle correction, and required reason categories in `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentService.java`, `AppointmentLifecycleService.java`, and `AppointmentChangeReason.java`
- [X] T071 [US4] Extend completed visit history with appointment linkage, veterinarian, completion time, and clinical notes while preserving legacy visits in `src/main/java/org/springframework/samples/petclinic/owner/Visit.java` and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/VisitHistoryService.java`
- [X] T072 [US4] Implement secured staff calendar, settings, availability, closure, appointment lifecycle, and audit routes from `contracts/staff-calendar-ui.md` in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/StaffCalendarController.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/StaffAppointmentController.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/audit/StaffAuditController.java`
- [X] T073 [US4] Create staff calendar, scheduling settings, availability, appointment lifecycle, and audit templates in `src/main/resources/templates/staff/calendar.html`, `schedulingSettings.html`, `availability.html`, `appointment.html`, and `audit.html`
- [X] T074 [US4] Add staff validation messages for settings, availability, conflicts, reasons, and lifecycle actions in `src/main/resources/messages/messages.properties`
- [X] T075 [US4] Record settings, availability, hold release, direct booking, reschedule, cancellation, lifecycle, and visit-history audit events in `src/main/java/org/springframework/samples/petclinic/scheduling/availability/ClinicSchedulingSettingsService.java`, `VeterinarianAvailabilityService.java`, and `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentLifecycleService.java`
- [X] T076 [US4] Add calendar-management integration coverage for shifts, closures, conflicts, holds, direct booking, rescheduling, cancellation, completion, no-show, and correction in `src/test/java/org/springframework/samples/petclinic/SchedulingCalendarIntegrationTests.java`

**Checkpoint**: Staff can maintain a conflict-safe clinic calendar and appointment
lifecycle, and owner-facing scheduling uses that authoritative calendar.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Complete portability, observability, navigation, documentation, and
end-to-end validation across every delivered story.

- [X] T077 [P] Add MySQL and PostgreSQL migration, reservation uniqueness, and same-slot concurrency suites in `src/test/java/org/springframework/samples/petclinic/MySqlSchedulingIntegrationTests.java` and `PostgresSchedulingIntegrationTests.java`
- [X] T078 [P] Add Timefold bounded-solver, deterministic score, and no-confirmed-appointment-movement coverage in `src/test/java/org/springframework/samples/petclinic/scheduling/solver/TimefoldSchedulingIntegrationTests.java`
- [X] T079 [P] Add AI diagnostic-log redaction, correlation continuity, timeout, retry, and fallback coverage in `src/test/java/org/springframework/samples/petclinic/scheduling/ai/OllamaInterpretationDiagnosticsTests.java`
- [X] T080 [P] Add navigation and no-calendar-exposure regression coverage in `src/test/java/org/springframework/samples/petclinic/system/NavigationSecurityTests.java`
- [X] T081 Reconcile existing owner, pet, visit, veterinarian, and concurrency tests with secured routes and Flyway fixtures in `src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java`, `PetControllerTests.java`, `VisitControllerTests.java`, and `src/test/java/org/springframework/samples/petclinic/PetClinicConcurrencyTests.java`
- [X] T082 Document Java 21, Ollama setup, configurable model property, local/demo accounts, database profiles, and scheduling validation commands in `README.md`
- [X] T083 Run every scenario in `specs/001-smart-appointment-scheduling/quickstart.md` and record any required command corrections in `specs/001-smart-appointment-scheduling/quickstart.md`
- [X] T084 Run the complete Maven and Gradle validation suites listed in `README.md` and resolve feature-caused failures in the files reported by those suites

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Starts immediately.
- **Foundational (Phase 2)**: Depends on setup. It blocks every user story.
- **US1 (Phase 3)**: Depends on foundational work and establishes the MVP guided
  booking flow.
- **US2 (Phase 4)**: Depends on US1 offer/request/appointment aggregates.
- **US3 (Phase 5)**: Depends on foundational queue support and US1 fallback
  integration points; it can begin after US1 service contracts stabilize.
- **US4 (Phase 6)**: Depends on foundational settings/availability aggregates and
  US1 appointment/offer services; it can proceed after US1 service contracts
  stabilize.
- **Polish (Phase 7)**: Depends on all user stories selected for delivery.

### User Story Dependencies

```text
Setup -> Foundational -> US1
US1 -> US2
US1 -> US3
US1 -> US4
US2 + US3 + US4 -> Polish
```

US1 is the recommended MVP. US2, US3, and US4 add owner control, guaranteed
staff recovery, and operational calendar administration respectively.

### Parallel Opportunities

- T001 and T002 update separate build descriptors.
- T005-T010 are vendor-specific migration files once Flyway configuration is in
  place.
- T012-T015 are independent foundational aggregates after schema work.
- The test tasks marked `[P]` within each user-story phase can be written in
  parallel before their implementation tasks.
- US3 and US4 can be assigned to separate developers after US1 establishes the
  shared request, offer, appointment, and fallback contracts.
- T077-T080 are independent cross-cutting test suites after their feature code
  exists.

---

## Parallel Example: User Story 1

```text
Task: "Write AI validation tests in
src/test/java/org/springframework/samples/petclinic/scheduling/ai/OllamaInterpretationServiceTests.java"

Task: "Write solver tests in
src/test/java/org/springframework/samples/petclinic/scheduling/solver/AppointmentConstraintProviderTests.java"

Task: "Write reservation concurrency tests in
src/test/java/org/springframework/samples/petclinic/scheduling/offer/OfferAcceptanceConcurrencyTests.java"

Task: "Write owner MVC tests in
src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerSchedulingControllerTests.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 to make Java 21, dependencies, Flyway, and Ollama
   configuration available.
2. Complete Phase 2 so authentication, ownership, migrations, audit, queue, and
   test infrastructure are reliable.
3. Complete Phase 3 to deliver a one-offer owner scheduling flow for a
   preconfigured veterinarian schedule.
4. Run the P1 independent test and quickstart guided-booking scenario before
   starting the owner-management and staff-management increments.

### Incremental Delivery

1. Deliver US1 for the secure guided booking MVP.
2. Deliver US2 for owner recovery, revisions, history, and cancellation.
3. Deliver US3 so every automation failure is operationally recoverable.
4. Deliver US4 so staff can configure and manage the authoritative calendar.
5. Complete Phase 7 before treating the feature as ready across all supported
   database profiles.
