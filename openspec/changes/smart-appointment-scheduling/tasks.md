## 0. Scope and delivery sequence

- [x] 0.1 Read `design.md` (D1–D20, Data Model, Migration Plan) and the six `specs/**` before starting; this checklist implements **slice 1 (walking-skeleton foundation) only**.
- [x] 0.2 Confirm slice-1 boundary per `design.md` D20: Spring Security identity + demo seeding, core entities via a Flyway V1 baseline, the effective-availability resolver + fixed start-time grid, and **staff booking directly against the grid** — a runnable end-to-end vertical with **no Timefold and no AI**.
- [x] 0.3 Do **not** archive this change until all four slices complete: the specs describe the full target and implementation tasks are appended one slice at a time.

## 1. Build & dependencies (slice 1 only; `pom.xml` canonical, mirror to Gradle)

- [x] 1.1 Add `org.springframework.boot:spring-boot-starter-security` (Boot-managed version) to `pom.xml` (D11/D19).
- [x] 1.2 Add Flyway to `pom.xml`: `org.springframework.boot:spring-boot-starter-flyway` plus `org.flywaydb:flyway-database-postgresql` and `org.flywaydb:flyway-mysql` (Boot-managed 12.4.0) (D18).
- [x] 1.3 Mirror the exact same coordinates/versions from tasks 1.1–1.2 into `build.gradle` so the dual Maven+Gradle build stays green (D19).
- [x] 1.4 Do **not** add Timefold or Spring AI here — they are slice 2/3 (with the Java 17→21 bump in slice 2); leave `pom.xml`/`build.gradle` Java baseline at 17 for slice 1 (see `design.md` Risks).
- [x] 1.5 Verify both builds resolve dependencies cleanly (`./mvnw -q dependency:resolve` and `./gradlew dependencies`).

## 2. Flyway V1 baseline (triple-vendor parity; migration ownership switch)

- [x] 2.1 Set `spring.sql.init.mode=never` in `application.properties` to hand schema/data ownership from `spring.sql.init` to Flyway (D18).
- [x] 2.2 Fold the existing per-vendor `db/{h2,mysql,postgres}` schema+data scripts into a `V1__baseline.sql` per vendor (existing PetClinic `owners`/`pets`/`types`/`vets`/`specialties`/`visits` tables + seed), configuring vendor-specific Flyway locations so H2/MySQL/Postgres stay at full parity.
- [x] 2.3 Add the slice-1 new tables to the V1 baseline (or an incremental `V2` per vendor) matching `design.md` Data Model: `app_user` (unique `username`, `role`, `enabled`, `must_change_password`, nullable `owner_id` FK), `clinic_settings` (singleton, all defaults), `vet_weekly_shift`, `vet_availability_exception`, `vet_leave`, `clinic_closure`, `appointment_request`, `appointment`.
- [x] 2.4 Include the `slot_hold` table with its **unique constraint `(vet_id, start_instant)`** and `rejected_suggestion` table in the baseline so the schema is complete, even though slice 1 does not exercise holds/solver (they land in slice 2).
- [x] 2.5 Store all instant columns as UTC and local wall-clock columns as `LocalTime`/`LocalDate` per `design.md` D14.
- [x] 2.6 Run `flyway migrate` (via app startup) against **H2, MySQL, and Postgres** and confirm each vendor migrates cleanly and the existing owner/pet/vet/visit pages still load.

## 3. Spring Security identity foundation (security capability)

- [x] 3.1 Create the `app_user` JPA entity + repository in `security/` mapping `id, username, password_hash, role (OWNER|STAFF), enabled, must_change_password, owner_id` (nullable one-to-one to `Owner`) per `specs/security` "Application user identity" and "Roles and owner linkage".
- [x] 3.2 Implement a single `UserDetailsService` that resolves both `OWNER` and `STAFF` credentials and exposes the role + `mustChangePassword` flag; use a strong `PasswordEncoder` (e.g. BCrypt) so cleartext is never persisted ("Password is never stored in clear text").
- [x] 3.3 Add the Spring Security config: form login + logout, secure **all** existing owner/pet/vet/visit controllers, and permit only the login page, error page, static assets, and static urgent-care guidance anonymously ("Existing controllers are secured", "Public resources remain reachable while anonymous").
- [x] 3.4 Enforce forced first-login password change: when `must_change_password` is set, redirect the user to a change-password screen and block all other screens until a new hashed password is set; clear the flag on success ("Staff-provisioned owner accounts").
- [x] 3.5 Implement owner data scoping so an authenticated `OWNER` can only view/act on their linked `Owner`'s data (plus vet names/specialties) and never another owner's records ("Owner data scoping").
- [x] 3.6 Implement staff act-on-behalf via `ownerId`-scoped screens (no credential impersonation; staff stays authenticated as themselves) and a staff password-reset action that re-arms `must_change_password` ("Staff act on behalf of owners", "Staff reset an owner's password"). Ensure no owner self-registration path exists.

## 4. Demo account seeding (security capability)

- [x] 4.1 Seed a demo account per sample owner: username = lowercase first name (`George` → `george`), password `<username>123`, `enabled=true`, and `must_change_password=false` so demo accounts **skip** the forced first-login change ("Demo account seeding").
- [x] 4.2 Seed a staff/admin account that can log in and manage the clinic; keep seeding demo-only (guard behind a demo profile/flag) so it does not run in production.

## 5. Calendar core: settings, availability rows, and the effective-availability resolver

- [x] 5.1 Create the `ClinicSettings` entity (singleton) in `calendar/` with the baked-in defaults: grid granularity 15 min, hold duration 10 min, booking horizon 60 days, visit duration 15/120/30 (min/max/default), day-parts morning 08:00–12:00 / afternoon 12:00–17:00 / evening 17:00–20:00, `zone_id=Europe/Amsterdam` ("Clinic settings with defaults").
- [x] 5.2 Create the availability-row entities: `VetWeeklyShift` (split shift = multiple rows), `VetAvailabilityException` (`ADD|REMOVE|REPLACE`), `VetLeave`, and `ClinicClosure`, with repositories ("Per-veterinarian recurring weekly schedule", "Date-specific availability exceptions", "Per-veterinarian leave", "Clinic-wide closures").
- [x] 5.3 Implement the **effective-availability resolver** as a pure, deterministic function `resolve(vetId, date, clinicSettings) -> List<instant interval>` following `design.md` "Effective-Availability Resolver": precedence closure > leave > exception (REPLACE/REMOVE define the day, ADD unions) > recurring shifts, then merge+sort.
- [x] 5.4 Perform local→instant conversion at the single DST-aware boundary in the resolver (clinic `ZoneId`), correctly handling the spring-forward gap and fall-back overlap so resolved availability stays continuous (D14; `specs/calendar` "Single configured time zone").
- [x] 5.5 Add a fixed start-time grid generator that enumerates candidate starts at the configured granularity within the booking horizon, where a start is valid only if the full visit duration fits **one continuous** resolved availability block (D2).

## 6. Appointment/Visit core entities (no request-flow logic yet)

- [x] 6.1 Create the `Appointment` entity in `appointment/` (`id, request_id ◇, pet_id, vet_id, start_instant UTC, duration_min, status SCHEDULED|COMPLETED|NO_SHOW|CANCELLED, reason ◇`) distinct from the existing `Visit` ("Appointment entity distinct from visit"), with a repository.
- [x] 6.2 Create the `AppointmentRequest` entity in `appointment/` with the status column and links (`owner_id, pet_id, free_text, status, consent_flag, consent_at ◇, consent_text_snapshot ◇, interpretation_json ◇, resulting_appointment_id ◇, active_hold_id ◇`) so slices 2–4 can drive the state machine; slice 1 only persists the entity and does not implement consent/AI/solver transitions.
- [x] 6.3 Confirm the existing pet-history `Visit` UI is untouched and keeps working ("Existing pet history keeps working").

## 7. Staff booking directly against the grid (no Timefold, no AI)

- [x] 7.1 Add a staff-only booking screen/controller in `staff/` that, for a chosen `ownerId`/pet and vet, lists the valid grid starts from the resolver+grid (task 5.5) filtered to exclude overlap with existing `Appointment`s.
- [x] 7.2 Let staff pick one grid slot and create a `SCHEDULED` `Appointment` (with `request_id` null for a pure staff-direct booking) directly, bypassing the guided owner flow (D10 escape hatch, slice-1 subset — booking only).
- [x] 7.3 Keep this path free of any Timefold/AI dependency: it uses only the deterministic resolver + fixed grid + overlap check; ranking/holds/consent are explicitly deferred to later slices.

## 8. Slice-1 tests

- [x] 8.1 Unit-test the effective-availability resolver exhaustively: split shifts, off-days, `ADD`/`REMOVE`/`REPLACE` exceptions, leave, clinic closures, precedence order, and merge/sort output determinism.
- [x] 8.2 Unit-test the resolver's DST boundary handling for a spring-forward and a fall-back date inside the 60-day horizon (`Europe/Amsterdam`), asserting continuous, correct instant intervals.
- [x] 8.3 Unit-test the fixed start-time grid generator: correct granularity, horizon bounds, and the continuous-fit rule (a start whose duration would span a break is excluded).
- [x] 8.4 Scaffold the deterministic ranking/tie-break as a pure comparator (no Timefold) and unit-test the `(vet id, start instant)` ordering + reproducibility, so slice 2's solver reuses a tested ordering (`specs/scheduling/solver` "Deterministic tie-break"); full soft-score/feasibility ranking stays in slice 2.
- [x] 8.5 Add Spring Security access-control tests (e.g. `@WithMockUser`/MockMvc) for the newly secured controllers: anonymous access to owner/pet/vet/visit pages redirects to login; login/error/static/urgent-care guidance stay anonymous; an owner cannot reach another owner's data; staff can act on behalf via `ownerId`.
- [x] 8.6 Add tests for forced first-login password change (staff-provisioned owner is forced; demo-seeded accounts skip it) and that demo seeding creates `george`/`george123` plus a staff account.

## 9. Slice 2 — solver + holds

- [x] 9.1 Bump the canonical Maven build and mirrored Gradle toolchain from Java 17 to Java 21; add `ai.timefold.solver:timefold-solver-spring-boot-starter:2.5.0` to both builds and configure a bounded solve duration (D19/D20).
- [x] 9.2 Add triple-vendor V2 migrations and JPA mappings for the request's persisted `suggested_vet_id`/`suggested_start_instant` snapshot and `slot_hold.duration_min`. The snapshot survives expired-hold cleanup so accept can revalidate the exact offered slot after its hold row is gone; hold duration enables variable-length interval-overlap checks (D4/D5).
- [x] 9.3 Implement the app-owned solver input/output model and candidate enumeration across the booking horizon, enforcing continuous availability, appointment/active-hold overlap, excluded windows, rejected pairs, and required-specialty feasibility.
- [x] 9.4 Implement the Timefold single-slot solve with lexicographic preferred-window, preferred-vet, and sooner ranking plus deterministic ascending `(vet id, start instant)` tie-break; return a definite no-fit outcome.
- [x] 9.5 Implement atomic hold acquisition with the database unique constraint, configured TTL, lazy expired-row cleanup, and immediate re-solve when acquisition loses a race.
- [x] 9.6 Implement guarded `CONFIRMED → SUGGESTING → HELD` suggestion, reject/ask-again with permanent `rejected_suggestion` persistence, and finite progression to a different slot or `QUEUED_FOR_STAFF`.
- [x] 9.7 Implement transactional accept revalidation: confirm a valid hold; re-acquire and confirm an expired-but-free suggestion; or report loss and automatically offer the next slot when taken.
- [x] 9.8 Add the five-minute expired-hold sweep and tests for ranking/feasibility, deterministic progression, hold TTL/lazy cleanup, unique-race recovery, accept recovery, guarded transitions, and persistence mappings.
- [x] 9.9 Verify Maven and Gradle dependency resolution/compilation plus the complete Maven test suite on Java 21.

## 10. Follow-up slices

- [x] 10.1 **Slice 3 (AI + consent):** consent gate, Spring AI 2.0.1 Ollama interpreter behind the `AppointmentInterpreter` port (model via `spring.ai.ollama.chat.model`, options via `mutate()` only), structured-output validation/clamping/normalization, and the full `AppointmentRequest` state-machine transitions — see `specs/scheduling/interpretation` and `design.md` D6–D8/D16–D17.
- [x] 10.2 **Slice 4 (staff fallback + lifecycle + emergencies):** `QUEUED_FOR_STAFF` fallback queue and staff-unblock-then-owner-resumes, reschedule/cancel with recorded reason, complete (→ records a `Visit`)/no-show lifecycle, owner self-service view/cancel (24h window), and advisory-only urgency + unconditional urgent-care guidance — see `specs/staff`, `specs/appointment`, and `design.md` D9–D10/D15.
