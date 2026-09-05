# Checkpoint: phase-4 complete

## Summary

- Phase: phase-4, Calendar, availability, and appointment lifecycle — UC-5
- Tasks: 9/9 complete
- Commits: `bbd218f`..`5f03679`, one ordered commit per task
- Revision result: bundled commit `f88f59a` is not in the phase history; task-4.4 through task-4.9 are separate, ordered, and scoped commits.

## Task Closure

| Task | Commit | Artifacts present (exact names) | Scope clean | Phase ACs cited | Validation bullets with file:line |
|------|--------|---------------------------------|-------------|-----------------|-----------------------------------|
| task-4.1 | `bbd218f` | yes (9/9) | yes | 0/0 | 2/2 |
| task-4.2 | `481d6a8` | yes (3/3) | yes | 2/2 | 2/2 |
| task-4.3 | `5d8b6a3` | yes (2/2) | yes | 2/2 | 5/5 |
| task-4.4 | `8123fe5` | yes (2/2) | yes | 1/1 | 3/3 |
| task-4.5 | `6845215` | yes (7/7) | yes | 3/3 | 3/3 |
| task-4.6 | `e2a7a8a` | yes (2/2) | yes | 2/2 | 2/2 |
| task-4.7 | `9a8616e` | yes (3/3) | yes | 5/5 | 5/5 |
| task-4.8 | `841e860` | yes (2/2) | yes | 4/4 | 4/4 |
| task-4.9 | `5f03679` | yes (1/1) | yes | 0/0 | 1/1 |

The mechanical gate evidence, supporting-file scope, rule pointers, corrective attempts, and focused/full-suite results are recorded under each task in `spec/status.md`. After the first live walkthrough exposed detached lazy access on the reschedule form, the correction and a non-transactional rendered-form assertion were folded into task-4.7; its gate and the downstream gates were rechecked against the rewritten history.

## Ordered commits

| Task | Commit | Subject |
|------|--------|---------|
| task-4.1 | `bbd218f` | Dedicated staff controllers, forms, and route mapping transfer |
| task-4.2 | `481d6a8` | Full server-rendered day calendar |
| task-4.3 | `5d8b6a3` | Calendar picking mode and free-cell actions |
| task-4.4 | `8123fe5` | Editable clinic settings |
| task-4.5 | `6845215` | Vet availability precedence and warnings |
| task-4.6 | `e2a7a8a` | Availability conflict transaction |
| task-4.7 | `9a8616e` | Staff reschedule, cancel, audit, and owner markers |
| task-4.8 | `841e860` | Completion boundary and linked visit |
| task-4.9 | `5f03679` | UC-5 staff appointment management end to end |

## Artifacts

| File | Declared in | Purpose |
|------|-------------|---------|
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffCalendarController.java` | task-4.1 | Staff calendar HTTP surface |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/ClinicSettingsController.java` | task-4.1 | Clinic-settings HTTP surface |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/VetAvailabilityController.java` | task-4.1 | Vet-availability HTTP surface |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffAppointmentController.java` | task-4.1 | Appointment action HTTP surface |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffVisitController.java` | task-4.1 | Linked-visit edit HTTP surface |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/ClinicSettingsForm.java` | task-4.1 | Clinic settings form |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/VetAvailabilityForm.java` | task-4.1 | Availability form |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/AppointmentActionForm.java` | task-4.1 | Reschedule/cancel action form |
| `src/test/java/org/springframework/samples/petclinic/scheduling/web/StaffManagementRouteTests.java` | task-4.1 | Route and denial matrix |
| `src/main/java/org/springframework/samples/petclinic/scheduling/calendar/CalendarDayService.java` | task-4.2 | Calendar-day aggregation |
| `src/main/java/org/springframework/samples/petclinic/scheduling/calendar/CalendarDayView.java` | task-4.2 | Calendar render model |
| `src/test/java/org/springframework/samples/petclinic/scheduling/CalendarRenderingTests.java` | task-4.2 | Grid and capacity-layer evidence |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/CalendarPickController.java` | task-4.3 | Calendar-pick GET/POST flow |
| `src/test/java/org/springframework/samples/petclinic/scheduling/CalendarPickingTests.java` | task-4.3 | Pick/free-cell contracts |
| `src/main/java/org/springframework/samples/petclinic/scheduling/clinic/ClinicSettingsService.java` | task-4.4 | Atomic settings update boundary |
| `src/test/java/org/springframework/samples/petclinic/scheduling/ClinicSettingsTests.java` | task-4.4 | Seeded/edit/atomic settings evidence |
| `src/main/java/org/springframework/samples/petclinic/scheduling/clinic/VetLeave.java` | task-4.5 | Inclusive vet leave |
| `src/main/java/org/springframework/samples/petclinic/scheduling/clinic/VetLeaveRepository.java` | task-4.5 | Leave persistence |
| `src/main/java/org/springframework/samples/petclinic/scheduling/clinic/ClinicClosure.java` | task-4.5 | Clinic closure |
| `src/main/java/org/springframework/samples/petclinic/scheduling/clinic/ClinicClosureRepository.java` | task-4.5 | Closure persistence |
| `src/main/java/org/springframework/samples/petclinic/scheduling/clinic/EffectiveAvailabilityService.java` | task-4.5 | Availability precedence boundary |
| `src/main/resources/templates/staff/vetAvailability.html` | task-4.5 | Availability editor |
| `src/test/java/org/springframework/samples/petclinic/scheduling/AvailabilityPrecedenceTests.java` | task-4.5 | Shape, precedence, and warning evidence |
| `src/main/java/org/springframework/samples/petclinic/scheduling/clinic/AvailabilityConflictService.java` | task-4.6 | Atomic confirmed/hold conflict handling |
| `src/test/java/org/springframework/samples/petclinic/scheduling/AvailabilityConflictTests.java` | task-4.6 | Refusal and invalidation evidence |
| `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentManagementService.java` | task-4.7 | Staff appointment query/action boundary |
| `src/main/resources/templates/staff/appointmentReschedule.html` | task-4.7 | Reschedule/cancel/audit view |
| `src/test/java/org/springframework/samples/petclinic/scheduling/AppointmentManagementTests.java` | task-4.7 | Lifecycle and owner-marker evidence |
| `src/main/resources/templates/staff/visitEdit.html` | task-4.8 | Linked-visit editor |
| `src/test/java/org/springframework/samples/petclinic/scheduling/CompletionBoundaryTests.java` | task-4.8 | Completion/no-show boundary evidence |
| `src/test/java/org/springframework/samples/petclinic/scheduling/StaffManageAppointmentE2eTests.java` | task-4.9 | UC-5 and conflict extensions over HTTP |

## AC Coverage (this phase)

| AC | Test class.method | Level | Pinned assertion |
|----|-------------------|-------|------------------|
| AC-90 | `CalendarPickingTests.pickingModeRendersFreeCellsAsPostForms_AC90`; `pickedFreeCellAndSuggestButtonCreateIdenticalHold_AC90`; `pickedNonFreeCellIsRefusedWithoutMutation_AC90` | HTTP/rendered DB | Only free cells post with CSRF; pick and Suggest persist identical hold/event tuples; non-free picks leave request, hold, and event snapshots unchanged (`CalendarPickingTests.java:119-268`). |
| AC-96 | `CalendarRenderingTests.dayGridHasVetColumnsRowsFiveCellStatesAndNavigation_AC96` | rendered HTTP | Exact vet columns, 15-minute rows, five cell classes, navigation, and bounded picker (`CalendarRenderingTests.java:92-139`). |
| AC-97 | `CalendarPickingTests.normalModeFreeCellPrefillsBookingForm_AC97` | rendered HTTP | Normal free cell links carry exact vet/start values; picking mode posts a hold (`CalendarPickingTests.java:272-283`). |
| AC-98 | `CalendarRenderingTests.eachVetDayShowsAllFiveCapacityLayers_AC98` | service + rendered HTTP | Opening/closure, effective blocks, bookings, holds, and remaining capacity compare per vet (`CalendarRenderingTests.java:142-186`). |
| AC-99 | `ClinicSettingsTests.formStartsWithEverySeededDefault_AC99`; `validEditPersistsEveryFieldAndUpdatesClock_AC99`; `invalidEditPersistsNothing_AC99` | HTTP/DB | Every seeded value renders, every valid value and Clock zone reloads, and invalid input preserves the complete DB snapshot (`ClinicSettingsTests.java:71-209`). |
| AC-100 | `AvailabilityPrecedenceTests.allAvailabilityShapesPersist_AC100` | HTTP/DB | Split weekly blocks, replacement/empty exceptions, inclusive leave, and closure round-trip by value (`AvailabilityPrecedenceTests.java:76-115`). |
| AC-101 | `AvailabilityPrecedenceTests.closureLeaveExceptionWeeklyPrecedence_AC101` | service/DB | Closure > leave > exception > weekly, intersected with opening hours (`AvailabilityPrecedenceTests.java:119-138`). |
| AC-102 | `AvailabilityConflictTests.eachEditTypeWithConfirmedConflictRefusesAndListsAll_AC102` | service/DB | All four edit kinds return all conflict IDs and preserve the database snapshot (`AvailabilityConflictTests.java:110-136`). |
| AC-103 | `AvailabilityConflictTests.holdOnlyConflictInvalidatesHoldsAndApplies_AC103` | service/DB | All affected holds clear with complete events, unaffected holds stay unchanged, and each edit commits (`AvailabilityConflictTests.java:139-174,267-317`). |
| AC-107 | `AvailabilityPrecedenceTests.outsideHoursAcceptedWarnedAndIntersected_AC107` | HTTP/DB | Out-of-hours row persists, warning renders, and the effective block is clipped (`AvailabilityPrecedenceTests.java:142-159`). |
| AC-109 | `AppointmentManagementTests.rescheduleMutatesInPlaceAndRecordsOldTime_AC109` | HTTP/DB, real filter chain | Detached GET renders exact form values; POST keeps the ID, updates time/duration/vet, and records old time/reason (`AppointmentManagementTests.java:104-144`). |
| AC-110 | `AppointmentManagementTests.cancelBeforeStartRecordsReason_AC110` | HTTP/DB | Before-start cancellation becomes CANCELLED_BY_STAFF with the exact action and reason (`AppointmentManagementTests.java:148-165`). |
| AC-111 | `CompletionBoundaryTests.beforeStartRefusedWithoutSideEffect_AC111` | service/DB | Both terminal actions throw the named exception and preserve appointment/audit/visit state (`CompletionBoundaryTests.java:111-129`). |
| AC-112 | `CompletionBoundaryTests.exactlyAtStartAllowed_AC112` | service/DB | Completion and no-show both succeed exactly at start (`CompletionBoundaryTests.java:133-142`). |
| AC-113 | `CompletionBoundaryTests.afterStartAllowed_AC113` | service/DB | Completion and no-show both succeed after start (`CompletionBoundaryTests.java:146-155`). |
| AC-114 | `CompletionBoundaryTests.completionCreatesLinkedEditableVisit_AC114` | HTTP/DB, real filter chain | One linked visit has exact appointment/date/source description; GET prefills and POST edits both source variants by value (`CompletionBoundaryTests.java:159-209`). |
| AC-115 | `AppointmentManagementTests.everyStaffActionRequiresAndWritesReason_AC115` | service + rendered HTTP | Blank book/reschedule/cancel refuses without mutation; successful actions record actor/action/reason/timestamp (`AppointmentManagementTests.java:169-207`). |
| AC-119 | `AppointmentManagementTests.ownerSeesClinicChangeReasonAndOriginalTime_AC119` | HTTP/rendered DB | Owner HTML contains clinic-change marker, exact reason, and original time (`AppointmentManagementTests.java:211-227`). |
| AC-120 | `AppointmentManagementTests.cancelledRowsRemainUnderPast_AC120` | HTTP/rendered DB | Cancelled row remains persisted and renders under Past (`AppointmentManagementTests.java:231-243`). |

Supporting AC-138 is tagged at `StaffManageAppointmentE2eTests.staffManageAppointmentSteps1Through4`; the task-4.9 scenario drives the main UC-5 path plus both availability-conflict extensions over the real filter chain. `StaffResolveRequestE2eTests.staffResolveQueuedRequestSteps1Through4` also drives the phase-4 calendar-pick branch of UC-4.

## Routes (this phase)

| Method/path | Owning task | Handler | Evidence |
|-------------|-------------|---------|----------|
| GET `/staff/calendar` | task-4.1 | `StaffCalendarController.calendar` | live staff 200; anonymous 302; owner 403 |
| GET `/staff/calendar/pick/{requestId}` | task-4.3 | `CalendarPickController.pickCalendar` | `CalendarPickingTests`; route guard PASS |
| POST `/staff/calendar/pick/{requestId}` | task-4.3 | `CalendarPickController.handlePick` | authenticated CSRF HTTP in `CalendarPickingTests`; route guard PASS |
| GET `/staff/settings` | task-4.1 | `ClinicSettingsController.showSettings` | single-handler assertion and route guard PASS |
| POST `/staff/settings` | task-4.1 | `ClinicSettingsController.saveSettings` | authenticated CSRF HTTP in `ClinicSettingsTests`; route guard PASS |
| GET `/staff/vets/{vetId}/availability` | task-4.1 | `VetAvailabilityController.showAvailability` | live staff 200 |
| POST `/staff/vets/{vetId}/availability` | task-4.1 | `VetAvailabilityController.saveAvailability` | live staff 302 with CSRF; follow-up 200 |
| GET `/staff/appointments/{appointmentId}/reschedule` | task-4.1 | `StaffAppointmentController.showRescheduleForm` | live staff 200 with exact form values |
| POST `/staff/appointments/{appointmentId}/reschedule` | task-4.1 | `StaffAppointmentController.rescheduleAppointment` | live staff 302 with CSRF |
| POST `/staff/appointments/{appointmentId}/cancel` | task-4.1 | `StaffAppointmentController.cancelAppointment` | live staff 302 with CSRF |
| POST `/staff/appointments/{appointmentId}/complete` | task-4.1 | `StaffAppointmentController.completeAppointment` | live staff 302 with CSRF; linked visit GET 200 |
| POST `/staff/appointments/{appointmentId}/no-show` | task-4.1 | `StaffAppointmentController.noShowAppointment` | authenticated CSRF HTTP in `StaffManageAppointmentE2eTests` |
| GET `/staff/visits/{visitId}/edit` | task-4.1 | `StaffVisitController.showVisitEditForm` | live staff 200 before and after edit |
| POST `/staff/visits/{visitId}/edit` | task-4.1 | `StaffVisitController.updateVisit` | live staff 302 with CSRF |
| GET `/my/appointments` | task-1.4 | `OwnerPageController.appointments` | owner-marker and Past-section assertions in `AppointmentManagementTests` |

`StaffManagementRouteTests.allManagementRoutesResolveForStaff` proves every phase-4 management route resolves without 403/404/500 and that GET `/staff/calendar` and GET `/staff/settings` each have exactly one handler. The same class proves the complete anonymous 302 and owner 403 matrix with no disclosure or mutation. `StaffPageController.java` is absent, and all three plan guards pass.

## Runtime Evidence

- App start command: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--server.port=18084 --spring.ai.ollama.base-url=http://127.0.0.1:1"`.
- Runtime: Java 21.0.8; ready at 2026-09-06 02:34:22 Europe/Tallinn on `http://127.0.0.1:18084`; stopped cleanly at 02:35:47. The deliberately unreachable Ollama URL was not contacted during the staff-only flow.
- Authentication: GET `/login` → 200; POST `/login` as `staff` with cookie and CSRF → 302 to `/staff/queue`.
- Calendar: GET `/staff/calendar?date=2026-09-07` → 200; rendered staff identity, Scheduling queue/Calendar/Clinic settings navigation, bounded date value `2026-09-07`, and free cells.
- Book: POST `/staff/appointments` with pet 1, vet 1, `2026-09-07 10:00`, duration 30, and reason `cp4 future wellness exam` → 302 to `/staff/calendar`; the dated calendar rendered appointment 1 and its reason.
- Reschedule: GET `/staff/appointments/1/reschedule` → 200 and rendered Leo plus exact `2026-09-07`, `10:00`, and `30` form values. POST the same route with CSRF, `10:30`, duration 45, and reason `cp4 clinic reschedule` → 302; follow-up calendar 200 rendered the 10:30 cell and reason.
- Cancel: POST `/staff/appointments/1/cancel` with CSRF and reason `cp4 veterinarian unavailable` → 302 to `/staff/calendar`.
- Availability: GET `/staff/vets/1/availability` → 200; POST an unavailable exception for `2026-09-20` with CSRF → 302 to the same page; follow-up GET → 200 and rendered `Availability saved`, `2026-09-20`, and `unavailable`.
- Completion: POST `/staff/appointments` created past appointment 2 (`2026-09-05 10:00`, reason `cp4 past dental check`) → 302. POST `/staff/appointments/2/complete` with CSRF → 302. GET `/staff/visits/5/edit` → 200 and rendered visit date `2026-09-05` plus exact prefilled description `cp4 past dental check`.
- Visit edit: POST `/staff/visits/5/edit` with CSRF and description `cp4 dental check completed` → 302; follow-up GET → 200 with that exact updated textarea value.
- Denials: anonymous GET `/staff/calendar` → 302 to `/login`; owner `george` login → 302 to `/my/appointments`, then GET `/staff/calendar` → 403 with the stock Access Denied page.
- Initial corrective evidence: before task-4.7 was rewritten, live GET `/staff/appointments/1/reschedule` returned 500 at `appointment.pet.name` because the entity was detached with `spring.jpa.open-in-view=false`. The final task-4.7 commit fetches pet/vet explicitly (`AppointmentRepository.java:33-35`, `AppointmentManagementService.java:51-54`) and its non-transactional HTTP assertion plus the repeated live GET both return 200.

## Validation

- Full-suite command: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test`
- Compilation: PASS
- Tests: 294/0/0/0; skipped: none
- Focused task-4.7 correction: `AppointmentManagementTests` 5/0/0/0, including the non-transactional reschedule-page render
- Plan guards: PASS (`ArtifactInventoryTest`, `RouteInventoryTest`, `AcTagCoverageTest`)
- Formatting: PASS (`./mvnw -q spring-javaformat:validate`)
- Diff validation: PASS (`git diff --check`)
- Constraints: 9/9 unique phase rules satisfied — RULE-2, RULE-12, RULE-16, RULE-31, RULE-33, RULE-34, RULE-36, RULE-37, RULE-44; exact file/line evidence is in the task notes in `spec/status.md`
- Skeleton test: n/a
- `git status --short` immediately after the full suite and plan-guard rerun: clean; validation changed no tracked source or data file

## Notes

- Recovery branch `recovery/cp4-before-rewrite-f88f59a` and stash `task-4.4-reconstruction-wip` remain available; neither is part of the checkpoint history.
- No executor approval is implied. Independent convergence must audit this committed report and the rewritten commits.

CHECKPOINT REACHED. AWAITING APPROVAL.
