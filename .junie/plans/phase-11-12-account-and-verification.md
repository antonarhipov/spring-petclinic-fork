---
sessionId: session-260830-133658-1dyi
---

# Requirements

- **Goal / outcome**: Finish remaining speckit work for Smart Appointment Scheduling: Phase 11 (US9 account provision/protect) then Phase 12 (cross-cutting verification/polish), using `specs/001-smart-appointment-scheduling/tasks.md` T124–T144 as the ordered TDD list. Existing in-tree scheduling work stays; do not revert or rewrite US1–US8.
- **Scope**:
  - In: T124–T134 (account lifecycle, bootstrap, security, templates, audit) and T135–T144 (architecture, cross-DB races, scale, audit reconstruction, security matrix, E2E smoke, migration docs, opt-in Ollama probe, UX copy, dual-build).
  - Out: Self-registration, password recovery, staff-account admin, login throttling, mobile/WCAG, live Ollama as CI, new scheduling domain features.
- **Done when**:
  - Staff can provision/reset an owner; one-time password shows once, expires in seven days, forces first-use change; reset deletes all principal sessions; demo seeds are `admin/admin123` and lowercase first-name + `123` with no forced change; deployed startup has no predictable owner seeds and fails without protected initial staff config.
  - Temporary-password users can only change password and logout; CSRF, session-ID rotation, 30-minute inactivity, and credential redaction hold.
  - Named Phase 12 acceptance tests exist and pass (live Ollama excluded from default CI); `quickstart.md` records controlled adoption plus both-build outcomes.
  - T124–T144 are marked `[x]` only after their tests pass; `./mvnw test` and `./gradlew test` are both green.

# Technical Design

- **Decisions**:
  - chose relocate login view to `templates/account/login.html` / not keep `templates/login.html` — T133 is the template contract; keep public `GET /login`.
  - chose `OncePerRequestFilter` + `UserDetails` flags / not per-controller checks — contract is global temporary-password restriction.
  - chose ArchUnit test-only in both builds / not hand-rolled import scans — T135 is package-boundary enforcement; T144 owns dual-build parity.
  - chose retarget demo/journey tests to `george` (owner 1) / not keep `owner1` aliases — FR-011 and quickstart.
  - chose GET `/staff/owners/{ownerId}/account` form + POST provision/reset / not embed in `OwnerController` — dedicated `provision-owner.html`; catalog controller stays staff-only CRUD.

- **Approach & touches**: Follow tasks.md TDD inside each phase (listed tests fail for the intended reason, then implement). Look at `StaffQueueController` + `StaffQueueControllerTests` (`@WebMvcTest` + real `SecurityConfiguration`, CSRF, role 403) for account MVC; `AuditService` for redacted account audit; `ReservationServiceTests.concurrentBlockInsertsHaveSingleWinner` plus `MySqlContainerConfiguration`/`PostgresContainerConfiguration` for T136; `OwnerSuggestionJourneyTests` + `AppointmentLifecycleJourneyTests` + `OwnerSelfServiceJourneyTests` for T140; `H2SchedulingMigrationTests` for T141; `SpringAiOllamaInterpretationAdapter` for T142.
  - Phase 11 files: `account/Account.java` (fields already exist: `mustChangePassword`, `temporaryCredentialExpiresAt`, `credentialVersion`), `AccountBootstrap.java` (today seeds `admin`/`admin` and `owner{id}`/`password` — replace), `AccountRepository.java`, `PetClinicUserDetailsService.java`, `SecurityConfiguration.java`, `SessionConfiguration.java`, `LoginController.java`; add `UsernameSuggester`, `OneTimePasswordGenerator`, `AccountProvisioningService`, `OneTimeCredentialResult`, `PasswordService`, `AccountSessionService`, `InitialStaffProperties`, `AccountWebController`, `AccountProvisioningForm`, `PasswordChangeForm`, `AccountAuditService`; templates under `src/main/resources/templates/account/`; tests named in T124–T127.
  - Phase 12 files: named T135–T142 test classes (none exist); `messages.properties` + `quickstart.md` (T143–T144); `pom.xml`/`build.gradle` only if ArchUnit or drift fixes are required.
  - Config: `application.properties` (`petclinic.account.bootstrap-enabled=false`, `spring.session.timeout=30m`); `application-{local,demo,test}.properties` enable bootstrap; add `petclinic.account.initial-staff.username/password` for deployed fail-fast. Do not put secrets in committed default props.

- **Nuances / risks / corners**:
  - `uk_accounts_username` and `uk_accounts_owner` already exist — provision is one account per owner; second provision is reset or 409, not a second row.
  - Username suggestion: normalize lowercase first name; numeric suffixes on collision (`george`, `george2`). Demo first names are unique; collision tests must fabricate duplicates.
  - One-time password: `SecureRandom`, length ≥ 6; persist BCrypt only; seven-day expiry via injected `Clock` (`ClockConfiguration.utcClock`); flash/PRG so plaintext is not re-readable; `Cache-Control: no-store`; never echo passwords on validation failure (`forms.md`).
  - Reset: same OTP rules + `FindByIndexNameSessionRepository` delete by principal (index `SPRING_SESSION_IX3` already asserted in `SessionConfigurationTests`). Increment `credentialVersion`.
  - Password change: min 6, confirm match, verify current/temporary, clear `mustChangePassword` and expiry, `HttpServletRequest.changeSessionId()` (login already uses session-fixation changeSessionId).
  - Expired temporary credential must not authenticate (`PetClinicUserDetailsService`).
  - Temporary filter allowlist: `/account/password/change` and logout only; `/login` remains public.
  - No failed-login throttling (assert absence).
  - Deployed path (`bootstrap-enabled=false`): create no owner seeds; require non-blank initial staff credentials that are not demo `admin123`; fail startup if missing. Test profile keeps bootstrap so existing `@SpringBootTest` stays bootable.
  - Bootstrap change breaks `@WithMockUser(username = "owner1")` because `CurrentOwnerAccount` loads by username — update those journeys in the same Phase 11 step (`EmergencyFallbackTests`, `OwnerInterpretationJourneyTests`, `OwnerRecoveryJourneyTests`, `OwnerSelfServiceJourneyTests`, `OwnerSuggestionJourneyTests`, `StaffAssistanceJourneyTests`).
  - `AccountAuditService` may use `AuditService` but before/after JSON must omit username-existence leaks, plaintext, hashes, session IDs.
  - Phase 12 T137 (10k/25 users) stays in default `./mvnw test` with deterministic LLM/Timefold adapters; T142 must be `@EnabledIfEnvironmentVariable` (or equivalent) so default CI does not call Ollama.
  - T136/T141 need Docker Testcontainers (same as existing MySQL/Postgres tests). Preserve Flyway V1/V2; do not re-own schema.
  - Mark `tasks.md` checkboxes only when green. Do not rewrite US1–US8 packages except account-username test retargets and Phase 12 test additions.

- **Contracts**:
  - Routes: `GET/POST /account/password/change`; `POST /logout`; `POST /staff/owners/{ownerId}/account`; `POST /staff/owners/{ownerId}/account/reset`; plus GET form for provision. Public: `GET /`, `GET /login`, login POST, static assets (`mvc-routes.md`).
  - Forms: `currentPassword`/`newPassword`/`confirmPassword`; provision `username` + owner `expectedVersion`; reset account version + confirmation (`forms.md`).

# Testing

- Repro/gap: demo bootstrap currently seeds `admin`/`admin` and `owner1`/`password`; expected `admin`/`admin123` and `george`/`george123` with `mustChangePassword=false`; no OTP/provision/reset/change-password services or `templates/account/` yet.
- Provision: unique/collision usernames, one-display OTP, seven-day expiry, BCrypt-only persistence.
- Password/session: min length, temporary route restriction, session-ID rotation, principal session delete on reset, 30-minute timeout.
- Bootstrap: first-name collisions, sole demo staff `admin/admin123`, no deployed owner seeds, fail-fast without initial staff config.
- MVC/audit: provision/reset/change/logout, CSRF, no throttling, credential redaction.
- Phase 12: web must not skip to repositories; owner slot choice only via Timefold port; one winner on H2/MySQL/PostgreSQL hold/accept/book races; 10k/25-user deadlines without lost/duplicate reservations; FR-083 + both integration attempt types reconstructable without logs; full 401/403/404/success/CSRF/stale matrix; one deterministic owner→offer→appointment→staff complete→owner history smoke; fresh + baselined migration counts on three DBs.
- Regression: existing account tests (`AccountRepositoryTests`, `SecurityRouteMatrixTests`, `SessionConfigurationTests`) and owner journeys after username retarget.
- New tests: only the T124–T127 and T135–T142 class names already listed in `tasks.md`.

# Assumptions & Open Questions

- **Significant assumption**: GET `/staff/owners/{ownerId}/account` is allowed even though the route table lists only POSTs — dedicated provision template needs a form GET, same pattern as other staff workspaces. Alternative: form-only on owner details (rejected; T133).
- **Significant assumption**: Deployed initial staff is created from `petclinic.account.initial-staff.*` without forced password change, but demo password `admin123` is rejected — FR-012 requires protected config, not owner OTP semantics. Alternative: force change on first deployed staff login.
- **Source conflict**: T133 `templates/account/login.html` vs current `templates/login.html` + view `"login"`. Chosen: relocate to match tasks.md.

# Delivery Steps

### ✓ Step 1: Phase 11 — provision and protect accounts
Goal: US9 account lifecycle, demo/deployed bootstrap, and security/audit behavior match spec FR-007–FR-012; T124–T134 complete.
Scope: `src/main/java/org/springframework/samples/petclinic/account/**`, `src/test/java/org/springframework/samples/petclinic/account/**`, `src/main/resources/templates/account/**`, `templates/login.html` (remove after move), `application*.properties` for initial-staff, `owners/ownerDetails.html` link only, and owner-journey tests still using `owner1`.
Acceptance Criteria:
- [ ] T124–T127 tests exist first and cover username collision, OTP one-display/seven-day/BCrypt, password-change/session rotation/reset invalidation/30m timeout, bootstrap first-name/`admin123`/no deployed owner seeds/fail-fast initial staff, and MVC CSRF/temporary-route/no-throttling/redaction.
- [ ] T128–T134 implementation: suggester + OTP generator; provision/reset with `OneTimeCredentialResult`; `PasswordService` + `AccountSessionService`; bootstrap + `InitialStaffProperties`; `AccountWebController` + forms; login/change-password/provision/one-time-credential templates (`no-store` on credential page); `AccountAuditService` with no credential material.
- [ ] Demo/test: `admin/admin123` only staff seed; owners use lowercase first name + `123`, `mustChangePassword=false`; no `owner1`/`password` seeds; journey tests use `george` where they need the owner-1 account.
- [ ] Temporary users cannot reach `/owner/**` or `/staff/**` until password change; reset deletes Spring Session rows for that principal; login and password change rotate session IDs.
- [ ] T124–T134 marked `[x]` in `tasks.md` only when green. Do not start T135–T144 in this step.
Verification: `./mvnw test -Dtest=AccountProvisioningServiceTests,PasswordAndSessionServiceTests,AccountBootstrapTests,AccountControllerTests,AccountAuditTests,AccountRepositoryTests,SecurityRouteMatrixTests,SessionConfigurationTests,OwnerInterpretationJourneyTests,OwnerSuggestionJourneyTests,OwnerRecoveryJourneyTests,OwnerSelfServiceJourneyTests,EmergencyFallbackTests,StaffAssistanceJourneyTests` → green

### * Step 2: Phase 12 — polish and cross-cutting verification
Goal: Named acceptance tests and quickstart validation prove architecture, portability, scale, audit, security, E2E, and dual-build; T135–T144 complete.
Scope: new tests under `scheduling/architecture`, `scheduling/integration`, `scheduling/performance`, `scheduling/migration`, `scheduling/interpretation/OllamaLiveProbeTests.java`; ArchUnit test dep in `pom.xml` and `build.gradle` if added; `messages.properties`; `specs/001-smart-appointment-scheduling/quickstart.md` and `tasks.md`. Do not add excluded product features.
Acceptance Criteria:
- [ ] T135: web packages cannot depend on repositories; owner-facing slot selection cannot bypass the Timefold port.
- [ ] T136: barrier hold/accept/staff-book races on H2, MySQL, and PostgreSQL — exactly one winner, no partial reservations (extend `ReservationService` uniqueness pattern).
- [ ] T137: 10,000-record / 25-user fixture meets 10s interpretation and 5s match outcomes with no lost/duplicate reservations; uses deterministic adapters.
- [ ] T138: FR-083 events plus both integration attempt types reconstruct from durable rows without logs (SC-010, SC-014).
- [ ] T139: 401/403/404/success/CSRF/stale-version matrix for contract routes, including temporary-password and cross-owner 404.
- [ ] T140: one deterministic full-context smoke: owner request → Timefold offer → accept → staff completion → owner history.
- [ ] T141: fresh and one-time-baseline record-count/constraint checks on three DBs; controlled adoption documented in `quickstart.md`.
- [ ] T142: opt-in prewarmed `gemma4:latest` probe excluded from default CI.
- [ ] T143: owner/staff copy reviewed (text-plus-icon, confirmations, no mobile/a11y claims) in `messages.properties` and `quickstart.md`.
- [ ] T144: `./mvnw test` and `./gradlew test` both green with no weakened tests; outcomes recorded in `quickstart.md`; T135–T144 marked `[x]`.
Verification: `./mvnw test && ./gradlew test` → green