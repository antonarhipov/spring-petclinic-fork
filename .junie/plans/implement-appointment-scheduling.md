---
sessionId: session-260827-203500-9cl0
---

# Requirements

### Goal / Outcome
Implement Slice 1 (foundation walking skeleton) of the `smart-appointment-scheduling` OpenSpec change: configure build and Flyway migrations across H2, MySQL, and PostgreSQL, implement Spring Security authentication with role-based scoping and demo seeding, provide the calendar availability domain with deterministic resolution and DST handling, persist core appointment entities, deliver staff direct booking against the availability grid, and maintain OpenSpec task checklist tracking.

### Scope
- **In Scope:** OpenSpec tasks 0.1–8.6 (39 items) covering build dependencies, multi-vendor Flyway V1 baseline, `app_user` identity and security config, demo account seeding, calendar settings and availability resolver, `Appointment`/`AppointmentRequest` entities, staff-direct booking UI, and unit/security tests.
- **Out of Scope:** Slices 2–4 (tasks 9.1–9.3: Timefold solver/holds, Spring AI Ollama interpreter/consent gate, staff fallback queue/reschedule/cancel reasons/lifecycle completion, and archiving the OpenSpec change).

### Done When
- Flyway V1 baseline initializes existing and new tables cleanly across H2, MySQL, and PostgreSQL with `spring.sql.init.mode=never`.
- Form-based authentication secures existing controllers, enforces owner data scoping and forced first-login password changes, and provides seeded demo accounts.
- Effective-availability resolver and grid generator deterministically compute slot availability with correct `Europe/Amsterdam` DST boundary handling.
- Staff can directly book scheduled appointments against valid grid slots without Timefold or AI dependencies.
- All 39 slice-1 tasks in `openspec/changes/smart-appointment-scheduling/tasks.md` are marked `- [x]` while tasks 9.1–9.3 remain `- [ ]` and the change remains unarchived.

# Technical Design

### Decisions
- **Chose consolidated `V1__baseline.sql` per vendor / not multi-step scripts** — cleanly transfers full schema and seed ownership from `spring.sql.init` to Flyway across H2, MySQL, and PostgreSQL with zero legacy race conditions.
- **Chose Spring Security method & route authorization / not custom interceptors** — standard `UserDetailsService` and `AuthenticationPrincipal` integration enables robust `@WithMockUser` testing and strict owner data scoping.
- **Chose pure deterministic resolver & start-time grid for staff booking / not early Timefold integration** — preserves the strict Slice-1 boundary (no Timefold or Spring AI dependencies, maintaining Java 17 baseline) while delivering an end-to-end runnable booking workflow.
- **Chose single DST conversion boundary in resolver / not pre-computed UTC shifts** — recurring shifts remain natural wall-clock times in `Europe/Amsterdam`, converted to UTC `Instant`s via `ZonedDateTime` during resolution to cleanly absorb spring-forward gaps and fall-back overlaps.

### Approach & Touches
- **Build & Migrations:** `pom.xml`, `build.gradle`, `src/main/resources/application*.properties`, `src/main/resources/db/{h2,mysql,postgres}/V1__baseline.sql`.
- **Security:** `org.springframework.samples.petclinic.security` (`AppUser`, `AppUserRepository`, `UserRole`, `AppUserDetailsService`, `SecurityConfig`, `PasswordChangeController`), `OwnerController.java`, `PetController.java`, `VetController.java`, `VisitController.java`.
- **Calendar:** `org.springframework.samples.petclinic.calendar` (`ClinicSettings`, `VetWeeklyShift`, `VetAvailabilityException`, `VetLeave`, `ClinicClosure`, `EffectiveAvailabilityResolver`, `GridGenerator`, `DeterministicTieBreakComparator`).
- **Appointment & Staff:** `org.springframework.samples.petclinic.appointment` (`Appointment`, `AppointmentRepository`, `AppointmentRequest`, `AppointmentRequestRepository`), `org.springframework.samples.petclinic.staff` (`StaffBookingController`), `src/main/resources/templates/staff/`.

### Nuances, Risks & Corners
- **Triple-Vendor SQL Parity:** H2, MySQL, and PostgreSQL have syntactic DDL variations (e.g. `VARCHAR_IGNORECASE` in H2 vs `VARCHAR` in MySQL vs `TEXT` in Postgres; auto-increment vs identity columns). Keep column types and constraints equivalent across all three `V1__baseline.sql` files.
- **Slot Hold Unique Constraint:** The `slot_hold` table must include a unique index on `(vet_id, start_instant)` in the baseline schema to guarantee concurrency protection for Slice 2.
- **DST Transition Gaps and Overlaps:** In `Europe/Amsterdam`, a 02:00–03:00 spring gap must normalize forward without phantom availability, and fall duplicate hours must resolve continuously without duplicate elapsed time.
- **Checkstyle Rules:** When executing Maven builds/tests during verification, pass `-Dcheckstyle.skip` to prevent false failures from markdown docs in skill directories.

# Testing

- **Resolver Precedence & Merging:** Must verify precedence order (`clinic_closure` > `vet_leave` > `vet_availability_exception` > `vet_weekly_shift`), split shift splitting/merging, and deterministic sorting.
- **DST Boundary Transitions:** Must verify `Europe/Amsterdam` spring-forward (1-hour gap) and fall-back (1-hour overlap) dates produce continuous instant intervals without duplicate or phantom slots.
- **Grid Generator Bounds & Continuous Fit:** Must verify candidate slots conform to configured granularity and horizon bounds, excluding starts that cross shift breaks or closures.
- **Security Scoping & Isolation:** Must verify anonymous requests redirect to `/login`, permitted public routes remain accessible, authenticated owners cannot access other owners' pets/records, and staff can operate across owners via `ownerId`.
- **Password Enforcement & Demo Seeding:** Must verify staff-provisioned owners are redirected to change password on first login, while demo accounts (`george`/`george123`, staff) skip forced change and log in directly.
- **Staff Direct Booking:** Must verify staff can book an appointment directly from a valid grid slot, excluding existing appointment overlaps, and that pet `Visit` history remains isolated and functional.
- **Regression Suite:** Run `PetClinicIntegrationTests`, `ClinicServiceTests`, `OwnerControllerTests`, `PetControllerTests`, `VetControllerTests`, `VisitControllerTests` to guarantee existing PetClinic functionality remains green.

# Assumptions & Open Questions

- **Significant Assumption (Flyway V1 Baseline Packaging):** Assumed existing `db/${database}/schema.sql` and `data.sql` are folded directly into vendor-specific `db/${database}/V1__baseline.sql` files with Flyway configured to look in `classpath:db/{vendor}`. Rationale: Maintains existing per-vendor directory structure while disabling `spring.sql.init`. Alternative: Consolidate into a single ANSI SQL migration file, which would risk compatibility issues with vendor-specific types.
- **Significant Assumption (Java 17 Baseline for Slice 1):** Assumed Java 17 remains the active build baseline across `pom.xml` and `build.gradle` throughout Slice 1; the bump to Java 21 is deferred to Slice 2 when Timefold is added.

# Delivery Steps

### ✓ Step 1: Build Configuration and Multi-Vendor Flyway Baseline
Goal: Configure build dependencies for Spring Security and Flyway, disable `spring.sql.init`, and create baseline migrations across H2, MySQL, and PostgreSQL for existing and new domain tables.
Scope: `pom.xml`, `build.gradle`, `src/main/resources/application*.properties`, `src/main/resources/db/h2/`, `src/main/resources/db/mysql/`, `src/main/resources/db/postgres/`, `openspec/changes/smart-appointment-scheduling/tasks.md`.
Acceptance Criteria:
- [ ] Add `spring-boot-starter-security`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, and `flyway-mysql` (Boot-managed) to `pom.xml` and mirror identical coordinates to `build.gradle` on Java 17.
- [ ] Omit Timefold and Spring AI dependencies; verify `./mvnw -q dependency:resolve` and `./gradlew dependencies` succeed.
- [ ] Set `spring.sql.init.mode=never` across `application.properties`, `application-mysql.properties`, and `application-postgres.properties`.
- [ ] Create `V1__baseline.sql` for H2, MySQL, and PostgreSQL incorporating existing PetClinic tables (`vets`, `specialties`, `vet_specialties`, `types`, `owners`, `pets`, `visits`) and seed data.
- [ ] Add all Slice-1 tables in `V1__baseline.sql`: `app_user`, `clinic_settings`, `vet_weekly_shift`, `vet_availability_exception`, `vet_leave`, `clinic_closure`, `appointment_request`, `rejected_suggestion`, `appointment`, and `slot_hold` with unique constraint `(vet_id, start_instant)` and UTC timestamp columns.
- [ ] Configure Flyway locations per vendor profile so application startup runs migrations cleanly.
- [ ] Mark tasks 0.1–0.3, 1.1–1.5, and 2.1–2.6 complete (`- [x]`) in `openspec/changes/smart-appointment-scheduling/tasks.md`.
Verification: `./mvnw test -Dtest=PetClinicIntegrationTests,ClinicServiceTests -Dcheckstyle.skip` → green

### ✓ Step 2: Spring Security Authentication, Access Control, and Demo Seeding
Goal: Implement application user identity, authentication, owner-data scoping, staff act-on-behalf, forced first-login password change, and demo account seeding.
Scope: `src/main/java/org/springframework/samples/petclinic/security/`, `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/VisitController.java`, `src/main/java/org/springframework/samples/petclinic/vet/VetController.java`, `src/main/resources/templates/`, `src/test/java/org/springframework/samples/petclinic/security/`, `openspec/changes/smart-appointment-scheduling/tasks.md`.
Acceptance Criteria:
- [ ] Create `AppUser` entity and repository in `security/` mapping `id`, unique `username`, `password_hash`, `role` (`OWNER|STAFF`), `enabled`, `must_change_password`, and nullable `owner_id` FK.
- [ ] Implement `UserDetailsService` resolving credentials for both `OWNER` and `STAFF` using BCrypt password encoding.
- [ ] Configure Spring Security form login/logout, protecting existing owner/pet/vet/visit controllers while permitting login, error, static assets, and urgent-care guidance anonymously.
- [ ] Enforce forced password change redirection when `must_change_password=true`, blocking other screens until the password is changed.
- [ ] Implement data scoping so authenticated `OWNER` users can only access their linked `Owner`'s records (plus vet names and specialties).
- [ ] Implement staff act-on-behalf via `ownerId`-scoped screens without credential impersonation, and provide a staff password-reset endpoint that re-arms `must_change_password`. Ensure no public owner self-registration exists.
- [ ] Seed demo owner accounts with username = lowercase first name (`george` / `george123`) with `must_change_password=false`, plus a demo staff account, guarded behind a demo profile/flag.
- [ ] Add security unit and integration tests covering anonymous redirects, permitted public resources, owner isolation, staff act-on-behalf, password change enforcement, and demo account authentication.
- [ ] Mark tasks 3.1–3.6, 4.1–4.2, and 8.5–8.6 complete (`- [x]`) in `openspec/changes/smart-appointment-scheduling/tasks.md`.
Verification: `./mvnw test -Dtest=*Security*,OwnerControllerTests,VetControllerTests,VisitControllerTests -Dcheckstyle.skip` → green

### ✓ Step 3: Calendar Domain, Effective-Availability Resolver, and Grid Generator
Goal: Implement calendar entities, default clinic settings, deterministic availability resolution with DST conversion, candidate grid generation, and tie-break comparator scaffold.
Scope: `src/main/java/org/springframework/samples/petclinic/calendar/`, `src/test/java/org/springframework/samples/petclinic/calendar/`, `openspec/changes/smart-appointment-scheduling/tasks.md`.
Acceptance Criteria:
- [ ] Create `ClinicSettings` entity and repository with defaults: 15-min grid, 10-min hold duration, 60-day horizon, 15–120 min visit duration bounds (30-min default), morning 08:00–12:00 / afternoon 12:00–17:00 / evening 17:00–20:00 day-parts, and `Europe/Amsterdam` zone.
- [ ] Create `VetWeeklyShift`, `VetAvailabilityException` (`ADD|REMOVE|REPLACE`), `VetLeave`, and `ClinicClosure` entities and repositories.
- [ ] Implement `EffectiveAvailabilityResolver` pure function `resolve(vetId, date, clinicSettings)` enforcing precedence: closure > leave > exceptions > recurring shifts, returning merged and sorted intervals.
- [ ] Perform local-to-instant conversion at the single `ZoneId` boundary in the resolver, normalizing spring-forward gaps and fall-back overlaps.
- [ ] Implement candidate grid generator producing valid slot starts at configured granularity within the booking horizon, requiring the entire visit duration to fit within one continuous availability block.
- [ ] Scaffold pure deterministic tie-break comparator ordering candidate slots by ascending `vet_id`, then ascending `start_instant`.
- [ ] Add unit tests for split shifts, off-days, exception types, leave, closure precedence, Europe/Amsterdam spring-forward and fall-back DST dates, grid continuous-fit exclusion across breaks, and tie-break ordering.
- [ ] Mark tasks 5.1–5.5 and 8.1–8.4 complete (`- [x]`) in `openspec/changes/smart-appointment-scheduling/tasks.md`.
Verification: `./mvnw test -Dtest=*Calendar*,*Resolver*,*Grid* -Dcheckstyle.skip` → green

### ✓ Step 4: Appointment Core Persistence and Staff Direct Booking
Goal: Implement `Appointment` and `AppointmentRequest` persistence models, deliver staff direct booking against the availability grid, and preserve existing visit history.
Scope: `src/main/java/org/springframework/samples/petclinic/appointment/`, `src/main/java/org/springframework/samples/petclinic/staff/`, `src/main/resources/templates/staff/`, `src/test/java/org/springframework/samples/petclinic/appointment/`, `src/test/java/org/springframework/samples/petclinic/staff/`, `openspec/changes/smart-appointment-scheduling/tasks.md`.
Acceptance Criteria:
- [ ] Create `Appointment` entity and repository with `id`, nullable `request_id`, `pet_id`, `vet_id`, `start_instant` (UTC), `duration_min`, status (`SCHEDULED|COMPLETED|NO_SHOW|CANCELLED`), and optional `reason`.
- [ ] Create `AppointmentRequest` entity and repository mapping all state-machine and linking fields (`owner_id`, `pet_id`, `free_text`, `status`, `consent_flag`, `consent_at`, `consent_text_snapshot`, `interpretation_json`, `resulting_appointment_id`, `active_hold_id`) for later slice compatibility without implementing AI/solver transitions in Slice 1.
- [ ] Implement staff booking controller and UI: select owner/pet and vet, query resolver and grid generator, filter out existing appointment overlaps, and book a `SCHEDULED` `Appointment` (with null `request_id`) directly.
- [ ] Keep the staff booking flow pure and deterministic without Timefold, AI, ranking, holds, or consent dependencies.
- [ ] Verify existing pet-history `Visit` UI and endpoints remain functional and unaffected by new appointments.
- [ ] Add tests for staff direct booking creation, slot collision avoidance, and visit history isolation.
- [ ] Mark tasks 6.1–6.3 and 7.1–7.3 complete (`- [x]`) in `openspec/changes/smart-appointment-scheduling/tasks.md`.
Verification: `./mvnw test -Dtest=*Staff*,*Appointment*,VisitControllerTests -Dcheckstyle.skip` → green

### ✓ Step 5: Full Slice-1 Regression, OpenSpec Task Verification, and Slice-2+ Preservation
Goal: Execute the complete test suite, verify OpenSpec apply progress (39/42 complete), and ensure Slice 2–4 tasks and capability specs remain preserved and unarchived.
Scope: `openspec/changes/smart-appointment-scheduling/tasks.md`, project test suite.
Acceptance Criteria:
- [ ] Run full project test suite and verify all unit, integration, and security tests pass.
- [ ] Verify all 39 slice-1 tasks (0.1–0.3, 1.1–1.5, 2.1–2.6, 3.1–3.6, 4.1–4.2, 5.1–5.5, 6.1–6.3, 7.1–7.3, 8.1–8.6) in `tasks.md` are marked complete (`- [x]`).
- [ ] Verify tasks 9.1–9.3 (slices 2–4: Timefold solver/holds, Spring AI Ollama interpreter/consent, staff fallback/lifecycle) remain unchecked (`- [ ]`).
- [ ] Confirm OpenSpec status reflects 39/42 tasks completed and that the change is NOT archived.
Verification: `./mvnw test -Dcheckstyle.skip` → green