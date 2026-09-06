# Convergence: cp-5 — Owner cancellation and terminal verification (UC-6)

## Summary

- Checkpoint: cp-5 (phase-5, 5/5 tasks claimed complete); executor report: `spec/checkpoints/cp-5.md` @ `da3e56f36973ddf843a2765fea89819826a2edc6`.
- Verdict: **APPROVE PENDING WALKTHROUGH**.
- Counts: **0 critical, 0 gaps, 0 protocol, 0 drift, 0 cosmetic**; carried forward: none from this phase. F-18 (AC-138) and F-21 (AC-118) from earlier checkpoints are verified closed.
- Suite (run by converge): **308 run / 0 failed / 0 errors / 0 skipped**; plan guards: **PASS** (6/6 passing from `RouteInventoryTest`, `ArtifactInventoryTest`, `AcTagCoverageTest`).
- Working tree after test run: **clean** (`git status --short` empty).

## Protocol Gate

All protocol items pass. The checkpoint report is committed in `HEAD`, one commit per task was delivered in exact plan order with exact matching file scope, plan review is PASS, all 10 declared artifacts exist by exact name, closure-gate evidence is present in `status.md`, no unapproved workarounds were introduced, and per-commit scope is clean.

| Task | Commit | Files in commit match artifact + supporting | Artifacts by exact name | Gate evidence in `status.md` | Blocker due / raised |
|---|---|---|---|---|---|
| task-5.1 | `434fa0a` | yes (exact declared artifacts and modifies) | yes, 3/3 | present (`status.md:90`) | none due; none raised |
| task-5.2 | `2acd58e` | yes (exact declared artifacts and modifies) | yes, 2/2 | present (`status.md:89`) | none due; none raised |
| task-5.3 | `fc3bb38` | yes (exact declared artifact and `status.md`) | yes, 1/1 | present (`status.md:88`) | none due; none raised |
| task-5.4 | `24f4fba` | yes (exact declared artifact and `status.md`) | yes, 1/1 | present (`status.md:87`) | none due; none raised |
| task-5.5 | `02ddd25` | yes (exact declared artifacts and modifies) | yes, 3/3 | present (`status.md:86`) | none due; none raised |

## Runtime Reproduction

The application was started independently by converge on port 18086 with `SCHEDULING_AI_PROVIDER=stub` and driven over HTTP via `curl` with cookies and CSRF protection across all actors (anonymous, staff, owner George Franklin, and other owner Betty Davis). Every observed response matched the executor's report and spec requirements with 100% fidelity.

| Actor | URL | Executor reported | Converge observed |
|---|---|---|---|
| anonymous | `GET /` | 302 `/login` | 302 `/login` |
| anonymous | `GET /login` | 200 | 200 |
| anonymous | `GET /my/appointments` | 302 `/login` | 302 `/login` |
| anonymous | `GET /my/appointments/1` | 302 `/login` | 302 `/login` |
| anonymous | `POST /my/appointments/1/cancel` | 302 `/login` | 302 `/login` |
| staff (`staff`/`staff123`) | `POST /login` | 302 `/staff/queue` | 302 `/staff/queue` |
| staff | `GET /staff/queue` | 200 | 200 |
| staff | `GET /my/appointments` | 403 Forbidden | 403 Forbidden |
| staff | `GET /my/appointments/1` | 403 Forbidden | 403 Forbidden |
| staff | `POST /my/appointments/1/cancel` | 403 Forbidden | 403 Forbidden |
| staff | `POST /staff/appointments` (book Leo, 2026-09-10 10:00) | 302 | 302 `/staff/calendar?date=2026-09-10` |
| owner George (`george`/`george123`) | `POST /login` | 302 `/my/appointments` | 302 `/my/appointments` |
| owner George | `GET /my/appointments` | 200 (Leo in `#upcoming-appointments`) | 200 (Leo in upcoming appointments with link `/my/appointments/1`) |
| owner George | `GET /my/appointments/1` | 200 (`CONFIRMED`, cancel button) | 200 (`Appointment details`, `CONFIRMED`, `Cancel appointment` button) |
| owner George | `GET /my/appointments/999999` | 404 | 404 |
| owner George | `POST /my/appointments/1/cancel` (with CSRF) | 302 `/my/appointments` | 302 `/my/appointments` |
| owner George | `GET /my/appointments` (after cancel) | 200 (`CANCELLED_BY_OWNER` in past) | 200 (`Appointment cancelled` banner, appt 1 in `#past-appointments` with `CANCELLED_BY_OWNER`) |
| owner George | `POST /my/appointments/1/cancel` (second cancel attempt) | 302 refused | 302 `/my/appointments/1` (redirect with `appointmentActionNotAllowed` banner) |
| other owner Betty (`betty`/`betty123`) | `POST /login` | 302 `/my/appointments` | 302 `/my/appointments` |
| other owner Betty | `GET /my/appointments/1` (George's appt) | 404 (no disclosure) | 404 (identical to non-existent 999999; no pet/reason/vet disclosure) |
| other owner Betty | `POST /my/appointments/1/cancel` (with Betty's CSRF) | 404 (no mutation) | 404 (no mutation across any database table) |

## Evidence Ledger

| AC | Pattern | Claimed in | Evidence (file:line) | Strength | Verified |
|---|---|---|---|---|---|
| AC-95 | Event-driven / State-driven | `OwnerCancelTests.directBookedAppointmentVisibleAndCancellable_AC95` | `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerCancelTests.java:112-146` (renders upcoming, cancels via POST, moves to past) | **STRONG** | yes |
| AC-116 | Event-driven / State-driven | `OwnerAppointmentRouteTests.detailAndCancelResolveForOwner`, `OwnerCancelTests.upcomingOwnedAppointmentCancelsAndRequestStaysClosed_AC116`, `OwnerCancelsAppointmentE2eTests.ownerCancelsUpcomingSteps1And2` | `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerCancelsAppointmentE2eTests.java:107-174` (UC-6 1, 2); `OwnerCancelTests.java:150-183`; `OwnerAppointmentRouteTests.java:95-123` | **STRONG** | yes |
| AC-117 | Unwanted behavior / Boundary | `OwnerCancelTests.pastCompletedAndNoShowRefusedWithoutSideEffect_AC117`, `OwnerCancelsAppointmentE2eTests.pastAndNoShowRefusedExtension` | `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerCancelTests.java:187-238`; `OwnerCancelsAppointmentE2eTests.java:178-237` (UC-6 ext 1a) | **STRONG** | yes |
| AC-118 | Authorization / Unwanted behavior | `OwnerAppointmentRouteTests.anonymousWrongRoleAndOtherOwnerDeniedWithoutDisclosureOrMutation_AC118`, `OwnerCancelTests.otherOwnerAndMissingViewCancelAreIdentical_AC118`, `OwnerCancelsAppointmentE2eTests.otherOwnerDeniedNonDisclosureExtension`, `SecurityMatrixWebTests.everyFinalRouteDeniedForAnonymousWrongRoleAndOtherOwner` | `src/test/java/org/springframework/samples/petclinic/scheduling/web/OwnerAppointmentRouteTests.java:127-184`; `OwnerCancelTests.java:242-284`; `OwnerCancelsAppointmentE2eTests.java:244-282` (UC-6 ext 1b); `SecurityMatrixWebTests.java:234-275` | **STRONG** | yes |
| AC-138 | Lifecycle / path | `FullSchedulingLifecycleE2eTests.fullInterleavedOwnerStaffLifecycle_AC138`, `FullSchedulingLifecycleE2eTests.declinedConsentStaffBookingNoShowLeg_AC138`, `OwnerCancelsAppointmentE2eTests.ownerCancelsUpcomingSteps1And2` | `src/test/java/org/springframework/samples/petclinic/scheduling/FullSchedulingLifecycleE2eTests.java:92-204` (UC-1 1-8, UC-2 1-4, UC-3 ext 3a, UC-4 1-4, UC-5 1-4); `FullSchedulingLifecycleE2eTests.java:208-285` (UC-2 ext 2a, UC-4 ext 3a, UC-5 1-4) | **STRONG** | yes |
| AC-140 | Ubiquitous / Invariant | `LocalizationSweepTests.manifestMatchesFeatureSourceUniverse_AC140`, `LocalizationSweepTests.everyFeatureKeyExistsInAllElevenBundles_AC140`, `LocalizationSweepTests.noFeatureTemplateOrJavaEmitsEnglishLiteral_AC140`, `OwnerPresentationContractTests.urgentCareBannerRenderedOnEveryOwnerPage_AC18` | `src/test/java/org/springframework/samples/petclinic/presentation/LocalizationSweepTests.java:58-82, 94-125, 129-163`; `OwnerPresentationContractTests.java:62-88` | **STRONG** | yes |
| AC-141 | Ubiquitous / Invariant / Event-driven | `ConcurrencyGuaranteeTests.simultaneousConfirmAndActiveRequestEachHaveOneWinner_AC141`, `OwnerGuidedFlowE2eTests.concurrentConfirmAndSecondRequestHaveOneWinner` | `src/test/java/org/springframework/samples/petclinic/scheduling/ConcurrencyGuaranteeTests.java:114-172`; `OwnerGuidedFlowE2eTests.java:310-380` | **STRONG** | yes |

## Findings

### Critical
(empty)

### Gaps
(empty)

### Protocol
(empty)

### Drift (Δ candidates)
(empty)

### Cosmetic
(empty)

## Category Notes

- **1. Task Closure** — PASS: All 10 declared artifacts exist at their exact paths, all validation bullets resolve to real assertions at the cited `file:line`, all covered ACs are tagged in tests, and all task commits have strictly contained scope without bleed.
- **2. AC Evidence Audit** — PASS: All 7 covered ACs in the phase (AC-95, AC-116, AC-117, AC-118, AC-138, AC-140, AC-141) are graded STRONG.
- **3. Normative Data by Value** — PASS: `SeedMigrationTests` verifies normative accounts, opening hours, schedules, exceptions, and defaults by value; all passwords verify against the password encoder.
- **4. Lifecycle and State Guards** — PASS: `OwnerCancelService` enforces guards in the domain service refusing past start times and non-`CONFIRMED` statuses with `IllegalAppointmentTransitionException`, handled gracefully without 500s.
- **5. Fidelity and Round-trips** — PASS: Owner cancellation persists `AppointmentChange` audit record, transitions appointment to `CANCELLED_BY_OWNER`, retains the row, keeps associated request closed in `ACCEPTED`, and correctly renders in past appointments table.
- **6. Security Surface Walk** — PASS: Whole security surface is covered; `FINAL_ROUTES` in `SecurityMatrixWebTests` covers all 43 mapped endpoints; anonymous redirected to `/login`, staff forbidden (403), and cross-owner access denied with 404 without disclosure and zero mutations.
- **7. Loaded Words** — PASS: "Upcoming", "past", "closed", and "no side effect" are explicitly defined and enforced in domain models and test assertions.
- **8. Presentation, Navigation and Localization** — PASS: Layout fragment reused, urgent care banner rendered on every owner page, `LocalizationSweepTests` asserts all 11 bundles have identical English placeholder text for feature keys, and zero unkeyed literals exist in feature sources.
- **9. Test Hygiene** — PASS: 308 tests pass with 0 failures, 0 errors, 0 skipped; clean working tree before and after test run; `ArchitectureBoundaryTests` passes (6/6); wall-clock dependencies avoided via injectable/mutable clocks.
- **10. Constraint Conformance** — PASS: RULE-12, RULE-13, RULE-16, RULE-32, RULE-36, RULE-38, RULE-41, and RULE-44 verified satisfied.

## Walkthrough Script (UI phases only)

- **Use Case:** UC-6 (Owner cancels upcoming appointment)
- **Actors:** Pet owner George Franklin (`george` / `george123`), Staff (`staff` / `staff123`), Other owner Betty Davis (`betty` / `betty123`).
- **Steps:**
  1. Staff logs in at `/login` (`staff` / `staff123`) and books an appointment for George's pet Leo (`petId=1`, `vetId=1`, date `2026-09-10 10:00`, duration 30, reason "Checkup").
  2. George logs in at `/login` (`george` / `george123`) and lands on `/my/appointments`.
  3. Under "Upcoming", George sees the appointment for Leo with status `CONFIRMED` and actions "Details" and "Cancel".
  4. George clicks "Details" (`GET /my/appointments/{id}`).
  5. George verifies pet name, veterinarian name, date/time, estimated duration, status, and the urgent-care banner.
  6. George clicks "Cancel appointment" (`POST /my/appointments/{id}/cancel` with CSRF).
  7. George is redirected to `/my/appointments` with alert "Appointment cancelled".
  8. Under "Past", the appointment is now displayed with status `CANCELLED_BY_OWNER`.
- **Extensions to try:**
  - **Extension 1a (Past / terminal refusal):** George attempts to cancel an appointment whose start time is in the past, or an appointment with status `COMPLETED` or `NO_SHOW`. The system redirects to the appointment detail page with error alert "That action is not available for this appointment right now." and makes no changes.
  - **Extension 1b (Cross-owner denial):** Betty Davis logs in (`betty` / `betty123`) and attempts `GET /my/appointments/{georgeApptId}` or `POST /my/appointments/{georgeApptId}/cancel`. The system returns HTTP 404 Not Found, disclosing no information about George or Leo, and produces zero database mutations.
- **URLs that must be denied:**
  - Anonymous `GET /my/appointments` -> 302 redirect to `/login`
  - Anonymous `GET /my/appointments/{id}` -> 302 redirect to `/login`
  - Anonymous `POST /my/appointments/{id}/cancel` -> 302 redirect to `/login`
  - Staff `GET /my/appointments` -> 403 Forbidden
  - Staff `GET /my/appointments/{id}` -> 403 Forbidden
  - Staff `POST /my/appointments/{id}/cancel` -> 403 Forbidden
  - Other owner `GET /my/appointments/{georgeApptId}` -> 404 Not Found
  - Other owner `POST /my/appointments/{georgeApptId}/cancel` -> 404 Not Found

## Spec Reconciliation

- **Δ folded in:** None new.
- **F-n closed:**
  - **F-21 (cp-1/G-4) — CLOSED at cp-5 by `434fa0a`, `2acd58e`, `fc3bb38`, `02ddd25`:** AC-118 owner appointment view/cancel routes are fully implemented (`GET /my/appointments/{id}`, `POST /my/appointments/{id}/cancel`), cross-owner access returns identical 404 without disclosure or database mutations, and is verified at route, service, E2E, and whole-surface security matrix levels.
  - **F-18 (cp-1/G-1, ex F-7) — CLOSED at cp-5 by `24f4fba`:** AC-138 full interleaved owner/staff lifecycle is implemented in `FullSchedulingLifecycleE2eTests` with separate owner/staff sessions, real filter chain, CSRF, and mutable clock progression.
- **F-n open:** None from phase-5 (prior open remediation findings from cp-1/cp-3 remain tracked in the re-plan).
- **Spec weaknesses exposed:** None.
- **Plan weaknesses exposed:** None.

## Resolution

None required for phase-5.

## Approval response

APPROVED PENDING WALKTHROUGH
