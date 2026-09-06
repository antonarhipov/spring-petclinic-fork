# Checkpoint: phase-5 complete

## Summary

- Phase: phase-5, Owner cancellation and terminal verification — UC-6
- Tasks: 5/5 complete
- Commits: `434fa0a`..`02ddd25`, one ordered commit per task

## Task Closure

| Task | Commit | Artifacts present (exact names) | Scope clean | Phase ACs cited | Validation bullets with file:line |
|------|--------|---------------------------------|-------------|-----------------|-----------------------------------|
| task-5.1 | `434fa0a` | yes (3/3) | yes | 0/0 (supporting AC-116, AC-118) | 2/2 |
| task-5.2 | `2acd58e` | yes (2/2) | yes | 4/4 (AC-95, AC-116, AC-117, AC-118) | 4/4 |
| task-5.3 | `fc3bb38` | yes (1/1) | yes | 0/0 (supporting AC-138) | 1/1 |
| task-5.4 | `24f4fba` | yes (1/1) | yes | 1/1 (AC-138) | 2/2 |
| task-5.5 | `02ddd25` | yes (3/3) | yes | 2/2 (AC-140, AC-141) | 6/6 |

The mechanical gate evidence, supporting-file scope, rule pointers, and test results are recorded under each task in `spec/status.md`.

## Ordered commits

| Task | Commit | Subject |
|------|--------|---------|
| task-5.1 | `434fa0a` | Dedicated owner appointment HTTP surface |
| task-5.2 | `2acd58e` | Owner cancel lifecycle and negative paths |
| task-5.3 | `fc3bb38` | UC-6 owner cancellation HTTP end to end |
| task-5.4 | `24f4fba` | Mandatory full interleaved lifecycle |
| task-5.5 | `02ddd25` | Terminal security matrix, concurrency and localization contracts |

## Artifacts

| File | Declared in | Purpose |
|------|-------------|---------|
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/OwnerAppointmentController.java` | task-5.1 | Dedicated owner appointment detail and cancellation web controller |
| `src/main/resources/templates/my/appointmentDetail.html` | task-5.1 | Localized owner appointment detail and cancellation view |
| `src/test/java/org/springframework/samples/petclinic/scheduling/web/OwnerAppointmentRouteTests.java` | task-5.1 | Route resolution, role-based access, and CSRF test suite |
| `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerCancelService.java` | task-5.2 | Cancellation domain logic enforcing status, timing, and request retention |
| `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerCancelTests.java` | task-5.2 | Positive and negative path cancellation verification |
| `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerCancelsAppointmentE2eTests.java` | task-5.3 | Full UC-6 HTTP end-to-end flow and extension tests |
| `src/test/java/org/springframework/samples/petclinic/scheduling/FullSchedulingLifecycleE2eTests.java` | task-5.4 | Full interleaved multi-actor lifecycle and declined consent tests |
| `src/test/java/org/springframework/samples/petclinic/scheduling/ConcurrencyGuaranteeTests.java` | task-5.5 | Concurrent creation and confirmation race tests |
| `src/test/java/org/springframework/samples/petclinic/presentation/LocalizationSweepTests.java` | task-5.5 | Exact feature universe manifest, 11-bundle key sync, and literal sweep |
| `src/test/resources/localization/feature-sources.txt` | task-5.5 | Computed feature source universe manifest |

## AC Coverage (this phase)

| AC | Test class.method | Level | Pinned assertion |
|----|-------------------|-------|------------------|
| AC-95 | `OwnerCancelTests.directBookedAppointmentVisibleAndCancellable_AC95` | HTTP/DB | Direct booking renders on owner login and follows the successful cancel path (`OwnerCancelTests.java:112-148`) |
| AC-116 | `OwnerCancelTests.upcomingOwnedAppointmentCancelsAndRequestStaysClosed_AC116`; `OwnerCancelsAppointmentE2eTests.ownerCancelsUpcomingSteps1And2` | Service/HTTP/DB | Upcoming owned appointment cancels to CANCELLED_BY_OWNER, appears in past list, request remains ACCEPTED (`OwnerCancelTests.java:151-185`, `OwnerCancelsAppointmentE2eTests.java:107-175`) |
| AC-117 | `OwnerCancelTests.pastCompletedAndNoShowRefusedWithoutSideEffect_AC117`; `OwnerCancelsAppointmentE2eTests.pastAndNoShowRefusedExtension` | Service/HTTP/DB | Cancelling past, completed, or no-show appointment is refused with flash notice and zero database mutations (`OwnerCancelTests.java:188-238`, `OwnerCancelsAppointmentE2eTests.java:178-222`) |
| AC-118 | `OwnerCancelTests.otherOwnerAndMissingViewCancelAreIdentical_AC118`; `OwnerAppointmentRouteTests.anonymousWrongRoleAndOtherOwnerDeniedWithoutDisclosureOrMutation_AC118`; `OwnerCancelsAppointmentE2eTests.otherOwnerCancelIsIndistinguishableExtension`; `SecurityMatrixWebTests.everyFinalRouteDeniedForAnonymousWrongRoleAndOtherOwner` | HTTP/DB | Other-owner requests return identical 404 to non-existent resources without disclosing data and with zero mutations (`OwnerCancelTests.java:241-285`, `OwnerAppointmentRouteTests.java:127-184`, `SecurityMatrixWebTests.java:234-265`) |
| AC-138 | `FullSchedulingLifecycleE2eTests.fullInterleavedOwnerStaffLifecycle_AC138`; `FullSchedulingLifecycleE2eTests.declinedConsentStaffBookingNoShowLeg_AC138`; `OwnerCancelsAppointmentE2eTests.ownerCancelsUpcomingSteps1And2` | HTTP E2E | Interleaved lifecycle across multiple pets, owner/staff hand-off, direct booking attach, visit completion, and cancellation with CSRF (`FullSchedulingLifecycleE2eTests.java:92-285`) |
| AC-140 | `LocalizationSweepTests.manifestMatchesFeatureSourceUniverse_AC140`; `LocalizationSweepTests.everyFeatureKeyExistsInAllElevenBundles_AC140`; `LocalizationSweepTests.noFeatureTemplateOrJavaEmitsEnglishLiteral_AC140` | Manifest/Bundles | Manifest matches computed universe, all keys present across all 11 bundles with identical English placeholders, zero un-keyed English literals (`LocalizationSweepTests.java:58-162`) |
| AC-141 | `ConcurrencyGuaranteeTests.simultaneousConfirmAndActiveRequestEachHaveOneWinner_AC141` | DB/Concurrency | Simultaneous confirmation and same-pet active request races yield at most one winner with zero leaked records (`ConcurrencyGuaranteeTests.java:113-168`) |

## Routes (this phase)

| Method/path | Owning task | Handler | Evidence |
|-------------|-------------|---------|----------|
| GET `/my/appointments/{appointmentId}` | task-5.1 | `OwnerAppointmentController.appointmentDetail` | live owner 200; anonymous 302; staff 403; other owner 404; route guard PASS |
| POST `/my/appointments/{appointmentId}/cancel` | task-5.1 | `OwnerAppointmentController.cancelAppointment` | live owner 302 with CSRF; anonymous 302; staff 403; other owner 404; missing CSRF 403; route guard PASS |

`SecurityMatrixWebTests.finalRouteTableCoversEveryMappedHandler` proves the test matrix covers all mapped handlers in the application context.

## Runtime Evidence

- App start command: `./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--server.port=18085 --spring.ai.ollama.base-url=http://127.0.0.1:1"`.
- Runtime: ready at 2026-09-06 04:07:15 Europe/Tallinn on `http://127.0.0.1:18085`.
- Authentication:
  - Staff login: GET `/login` → 200; POST `/login` with `staff` / `staff123` and CSRF → 302 to `/staff/queue`.
  - Owner login: GET `/login` → 200; POST `/login` with `george` / `george123` and CSRF → 302 to `/my/appointments`.
  - Other owner login: GET `/login` → 200; POST `/login` with `betty` / `betty123` and CSRF → 302 to `/my/appointments`.
- Staff booking: POST `/staff/appointments` with pet 1 (Leo), vet 1, `2026-09-10 10:00`, duration 30, reason `cp5 live walkthrough checkup` → 302.
- Owner detail view:
  - George GET `/my/appointments` → 200; renders Leo with link `/my/appointments/1`.
  - George GET `/my/appointments/1` → 200; renders `Appointment details`, pet `Leo`, status `CONFIRMED`, and `Cancel appointment` button.
- Owner cancellation:
  - George POST `/my/appointments/1/cancel` with CSRF → 302 to `/my/appointments`.
  - George GET `/my/appointments` → 200; renders `Appointment cancelled` notice, and appointment 1 appears under `#past-appointments` with status `CANCELLED_BY_OWNER`.
- Cross-owner and non-existent denial:
  - George GET `/my/appointments/999999` → 404.
  - Betty GET `/my/appointments/1` → 404 without disclosure of George or Leo.
  - Betty POST `/my/appointments/1/cancel` with Betty's authenticated CSRF → 404 without mutation.
  - Missing CSRF on POST `/my/appointments/1/cancel` → 403 Forbidden.

## Validation

- Full-suite command: `./mvnw test`
- Compilation: PASS
- Tests: 308/0/0/0; skipped: none
- Plan guards: PASS (`ArtifactInventoryTest`, `RouteInventoryTest`, `AcTagCoverageTest`)
- Formatting: PASS (`./mvnw spring-javaformat:validate`)
- Constraints: 6/6 phase rules satisfied — RULE-9, RULE-10, RULE-12, RULE-13, RULE-16, RULE-32, RULE-36, RULE-41, RULE-44; exact file/line evidence is in the task notes in `spec/status.md`
- Skeleton test: n/a
- `git status --short`: clean

## Notes

- All 5 tasks of phase-5 are complete and individually committed.
- Full suite passes 308/308 automated tests with zero failures or errors.

CHECKPOINT REACHED. AWAITING APPROVAL.
