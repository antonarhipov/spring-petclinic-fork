# Status: Smart Appointment Scheduling

## Current

- Task: cp-6
- Status: NOT_STARTED

## Completed

- task-1.1
- task-1.2
- task-1.3
- task-1.4
- task-1.5
- task-2.1
- task-2.2
- task-2.3
- task-2.4
- task-3.1
- task-3.2
- task-3.3
- task-3.4
- task-3.5
- task-3.6
- task-4.1
- task-4.2
- task-4.3
- task-4.4
- task-5.1
- task-5.2
- task-5.3
- task-5.4
- task-5.5
- task-6.1
- task-6.2
- task-6.3

## Phase Approvals

- phase-1: APPROVED
- phase-2: APPROVED
- phase-3: APPROVED
- phase-4: APPROVED
- phase-5: APPROVED
- phase-6: PENDING

## Blockers

## Deviations

## Notes

- task-1.1: Used timefold-solver-core instead of starter due to Boot 4.1 compatibility as prescribed in RULE-4; added dual build definitions in pom.xml and build.gradle.
- task-1.2: Added spring-boot-starter-flyway and migrated H2, MySQL, and PostgreSQL baselines to V1; removed spring.sql.init references.
- task-1.3: Created V2 migrations with portable constraints and nullable active_pet_key unique index (RULE-22); verified optimistic locking (@Version).
- task-1.4: Configured Spring AI ChatClient with structured entity extraction and graceful fallback when model is unconfigured or unavailable.
- task-1.5: Configured Timefold SolverManager with single-request hard/soft constraints for non-overlap and availability.
- task-2.1: Configured bounded ThreadPoolTaskExecutor with @EnableAsync and implemented SchedulingOrchestrator for asynchronous interpretation and state transitions.
- task-2.2: Built request form with always-visible urgent care banner, explicit AI consent, auto-refreshing status view for intermediate states, and confirmation screens.
- task-2.3: Added V3 Flyway migration for slot_occupancy, holds, and appointments with UNIQUE(vet_id, start_time); implemented Hold entity, repositories, and HoldService with hold expiry checks.
- task-2.4: Implemented Appointment entity, BookingService accept-hold logic with transactional expiry re-validation, and confirmed state transitions.
- task-3.1: Created UserAccount entity, UserRole enum, UserAccountRepository, and V4 Flyway migrations for H2, MySQL, and PostgreSQL; verified BCrypt hashing.
- task-3.2: Configured Spring Security form login and URL authorization rules for STAFF and OWNER access zones.
- task-3.3: Implemented first-login forced password change interceptor and controller.
- task-3.4: Created StaffAccountBootstrap ApplicationRunner to bootstrap initial admin account at startup from properties without Flyway credentials.
- task-3.5: Implemented staff account administration controller and Thymeleaf templates for creating accounts and resetting passwords.
- task-3.6: Implemented OwnerSelfServiceController and added handler-level ownership checks preventing ID tampering on profile, pets, and scheduling requests.
- task-4.1: Created VetWeeklyShift and VetDateException entities, repositories, and V5 Flyway migrations for recurring weekly schedules and date overrides.
- task-4.2: Created ClinicClosure and ClinicSettings entities, repositories, and V6 Flyway migrations for clinic-wide holiday closures and configurable horizon/duration settings.
- task-4.3: Implemented AvailabilityService computing effective date-bounded availability windows from shifts, exceptions, closures, and clinic operating hours.
- task-4.4: Integrated dynamic AvailabilityService into AppointmentSolverService and Timefold constraint provider to restrict candidate slots strictly to active vet availability windows.
- task-5.1: Implemented RequestExclusion entity and repository with V7 migrations, Timefold noExcludedSlots hard constraint, and reject/ask-again loop wiring in AppointmentSolverService and SchedulingController.
- task-5.2: Implemented @Scheduled hold sweep job in HoldService, hold expiration reactive event publishing via Spring ApplicationEventPublisher, and async re-solving in SchedulingOrchestrator.
- task-5.3: Implemented cancel endpoint in SchedulingController releasing holds and slot occupancy and clearing active_pet_key on SchedulingRequest.
- task-5.4: Automated routing to STAFF_QUEUED for CONSENT_DECLINED, EMERGENCY, NO_SPECIALTY_VET, SUGGESTIONS_EXHAUSTED, and SOLVER_UNAVAILABLE with owner UI explanation.
- task-6.1: Added V8 Flyway migration for change_reason; implemented direct booking, rescheduling with re-validation of availability/occupancy, and staff cancellation with change reason recording.
- task-6.2: Implemented completeAppointment (creating a Visit on pet history) and markNoShow (terminal status with no visit created) in BookingService.
- task-6.3: Implemented OwnerAppointmentController and templates allowing owners to list upcoming appointments and cancel them strictly before start time, preventing past cancellations and enforcing ownership boundaries.
