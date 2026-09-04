# Checkpoint: phase-3 complete

## Summary

- Phase: phase-3, Staff request resolution and direct booking — UC-4
- Tasks: 6/6 complete
- Original task commits: `e96b0f9`, `df4a516`, `0b56548`, `f959d24`, `760c363`, `3a110a0`
- cp-3 remediation commits: `4758b8e`, `b53bc0f`, `50aa90e`, `e4c72ce`, `812d0e0`, `be08f17`,
  `a477524`, `dec192f`, `8a382e2`

## Task Closure

| Task | Commit chain | Artifacts present | Scope clean | Phase ACs cited | Validation bullets |
|------|--------------|-------------------|-------------|-----------------|--------------------|
| task-3.1 | `fd35ec1 -> e96b0f9`; revise `b53bc0f -> 50aa90e` | yes (6/6) | yes | 0/0 | 2/2 |
| task-3.2 | `e96b0f9 -> df4a516`; revise `e4c72ce -> 812d0e0` | yes (2/2) | yes | 3/3 | 3/3 |
| task-3.3 | `df4a516 -> 0b56548`; revise `50aa90e -> e4c72ce`, `812d0e0 -> be08f17` | yes (4/4) | yes | 4/4 | 5/5 |
| task-3.4 | `0b56548 -> f959d24`; revise `be08f17 -> a477524` | yes (2/2) | yes | 1/1 | 2/2 |
| task-3.5 | `f959d24 -> 760c363`; revise `4758b8e -> b53bc0f`, `a477524 -> dec192f` | yes (3/3) | yes | 3/3 | 4/4 |
| task-3.6 | `760c363 -> 3a110a0`; revise `dec192f -> 8a382e2` | yes (1/1) | yes | 0/0 | 1/1 |

The closure-gate assertions, rule pointers, supporting-file justifications, and corrective attempts are recorded under
the corresponding task revision notes in `spec/status.md`. The superseded records with incorrect bases and artifact
names were removed.

## Artifacts

| File | Task | Purpose |
|------|------|---------|
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffQueueController.java` | task-3.1 | Staff queue HTTP surface |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffRequestController.java` | task-3.1 | Request detail, interpretation, suggestion, and hold actions |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffBookingController.java` | task-3.1 | Direct-booking HTTP surface |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffInterpretationForm.java` | task-3.1 | Structured interpretation form |
| `src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffBookingForm.java` | task-3.1 | Direct-booking form |
| `src/test/java/org/springframework/samples/petclinic/scheduling/web/StaffRouteSurfaceTests.java` | task-3.1 | Route/security matrix |
| `src/main/java/org/springframework/samples/petclinic/scheduling/request/StaffQueueService.java` | task-3.2 | Queue aggregation and hold release |
| `src/test/java/org/springframework/samples/petclinic/scheduling/StaffQueueTests.java` | task-3.2 | Rendered queue contracts |
| `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/StaffInterpretationService.java` | task-3.3 | STAFF versioning and timeline queries |
| `src/main/resources/templates/staff/requestDetail.html` | task-3.3 | Provenance and complete timeline view |
| `src/main/resources/templates/staff/interpretationForm.html` | task-3.3 | Localized interpretation form |
| `src/test/java/org/springframework/samples/petclinic/scheduling/StaffInterpretationTests.java` | task-3.3 | Version, provenance, structured-field, and timeline evidence |
| `src/main/java/org/springframework/samples/petclinic/scheduling/request/StaffSuggestionService.java` | task-3.4 | Solver/manual staff hold placement |
| `src/test/java/org/springframework/samples/petclinic/scheduling/StaffSuggestionTests.java` | task-3.4 | Accept and three-scope rejection parity |
| `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/StaffBookingService.java` | task-3.5 | Attach and non-mutating leave-open booking |
| `src/main/resources/templates/staff/bookingForm.html` | task-3.5 | Localized attach/leave-open selection |
| `src/test/java/org/springframework/samples/petclinic/scheduling/StaffDirectBookingTests.java` | task-3.5 | Direct-booking state matrices |
| `src/test/java/org/springframework/samples/petclinic/scheduling/StaffResolveRequestE2eTests.java` | task-3.6 | UC-4 authenticated HTTP path and extensions |

Supporting scope: task-3.1 owns all 11 phase message bundles and the read-only `StaffOperationsQueryService`; task-3.2
retains the `SchedulingRequestRepository` queue fetch plans required by `spring.jpa.open-in-view=false`.

## AC Coverage (this phase)

Only the phase-3 `covers` ledger from `tasks.yaml` appears here.

| AC | Test class.method | Level | Pinned assertion |
|----|-------------------|-------|------------------|
| AC-55 | `StaffInterpretationTests.staffEditCreatesNewVersionWithoutOverwrite_AC55` | service/DB | Every AI scalar, ID, version, provenance, and persisted window field remains exactly unchanged after the STAFF save (`:115-183`). |
| AC-56 | `StaffInterpretationTests.provenanceVisibleOnlyToStaff_AC56` | HTTP, real filter chain | Staff sees raw/model/prompt provenance and the owner response excludes each value (`:188-227`). |
| AC-84 | `StaffQueueTests.needsStaffOldestFirstWithTrigger_AC84` | service + rendered HTTP | Needs-staff rows render oldest first and pair every request with its exact hand-off trigger (`:94-176`). |
| AC-85 | `StaffQueueTests.allOpenContainsEveryNonTerminalStateHoldAndAge_AC85` | service + rendered HTTP | Every non-terminal row renders state, exact held slot, clock-derived age, and its sole release action; terminal rows are absent (`:180-276`). |
| AC-86 | `StaffQueueTests.releaseHoldMovesToStaffAndClearsTuple_AC86` | service/DB | Release clears vet/start/duration, moves to WITH_STAFF, and records actor/reason (`:280-319`). |
| AC-89 | `StaffInterpretationTests.staffCreatesAndEditsStructuredInterpretation_AC89`; `declinedConsentRequiresStaffInterpretation_AC89` | HTTP/DB + service | Every structured field round-trips by value with version increment; suggestion is refused until a complete STAFF version exists (`:230-345`). |
| AC-91 | `StaffSuggestionTests.ownerAcceptRejectUsesSameRules_AC91` | HTTP/DB, real filter chain | Accept clears all hold fields and creates exactly one linked appointment; all three rejection scopes persist exact parsed exclusions and produce a valid replacement or WITH_STAFF with no appointment (`:159-259`). |
| AC-92 | `StaffDirectBookingTests.bookingNeedsNoInterpretation_AC92` | HTTP/DB | Direct booking persists the exact requested appointment without an interpretation/request (`:108-137`). |
| AC-93 | `StaffDirectBookingTests.attachAcceptsReleasesAndLogsFromEveryNonTerminalState_AC93` | service/DB matrix | Every non-terminal source becomes ACCEPTED, all hold fields are null, and exactly one attach event is added (`:140-198`). |
| AC-94 | `StaffDirectBookingTests.leaveOpenPreservesRequestInEveryNonTerminalState_AC94` | service/DB matrix | Every persisted request field, hold tuple, and request-event count is unchanged while an independent appointment is created (`:202-247`, `:355-365`). |
| AC-121 | `StaffInterpretationTests.timelineShowsEveryTransitionAndAction_AC121` | DB + rendered HTTP | Exact seven-field tuples and timestamp order for all covered events equal the seven rendered cells per row; owner provenance is absent (`:348-438`). |

## Supporting / out-of-phase evidence

- AC-90: `StaffSuggestionTests.suggestButtonCreatesOneStaffHold` supports the later phase-4 claim; it is not counted in
  phase-3 coverage.
- AC-95: `StaffDirectBookingTests.directBookingVisibleToOwner` is supporting owner-list evidence; it is not in
  phase-3 `covers`.
- AC-138: `StaffResolveRequestE2eTests.staffResolveQueuedRequestSteps1Through4` is the task-3.6 validation scenario;
  task-3.6 declares no phase AC and this tag is not counted in the phase ledger.

## Routes

| Method/path | Handler | Evidence |
|-------------|---------|----------|
| GET `/staff/queue` | `StaffQueueController.queue` | live staff 200; live anonymous 302 to `/login`; complete role matrix in `StaffRouteSurfaceTests` |
| GET `/staff/requests/{requestId}` | `StaffRequestController.showRequestDetail` | live staff 200; live owner 403 |
| GET `/staff/requests/{requestId}/interpretation` | `StaffRequestController.showInterpretationForm` | live staff 200 |
| POST `/staff/requests/{requestId}/interpretation` | `StaffRequestController.saveInterpretation` | live staff 302 with CSRF |
| POST `/staff/requests/{requestId}/suggest` | `StaffRequestController.runSolverSuggestion` | live staff 302 with CSRF |
| POST `/staff/requests/{requestId}/release-hold` | `StaffRequestController.releaseHold` | authenticated/denial matrix in `StaffRouteSurfaceTests`; domain result in `StaffQueueTests` |
| GET `/staff/appointments/new` | `StaffBookingController.showBookingForm` | live staff 200 for attach and leave-open requests |
| POST `/staff/appointments` | `StaffBookingController.createBooking` | live staff 302 with CSRF for both decisions |

`RouteInventoryTest` confirms `/staff/queue` resolves to exactly one handler and the complete mapped surface matches the
security inventory.

## Runtime Evidence

- Start command: Java 21, default `scheduling.ai.provider=${SCHEDULING_AI_PROVIDER:ollama}`, with
  `SPRING_AI_OLLAMA_BASE_URL=http://127.0.0.1:1 SERVER_PORT=18080 ./mvnw -q -Dspring-javaformat.skip=true spring-boot:run`.
  The deliberately unreachable Ollama endpoint proves no live model is needed at startup. The log reached
  `Started PetClinicApplication` at 21:59:55 Europe/Tallinn. An initial sandboxed bind was denied by the environment;
  the same command started normally with localhost binding allowed.
- George login: `GET /login` 200; `POST /login` with cookie and CSRF 302 to `/my/appointments`.
- Staff login: `GET /login` 200; `POST /login` with cookie and CSRF 302 to `/staff/queue`.
- Accept scenario, request 1: George `POST /my/requests` 302, `GET /my/requests/1` 200, and `POST /decline` 302.
  Staff `GET /staff/queue` 200 rendered Needs staff, All open, the request text, and Declined consent;
  `GET /staff/requests/1` 200 rendered WITH_STAFF and its timeline; interpretation GET 200/POST 302 rendered the STAFF
  version; Suggest POST 302 and detail GET 200 rendered SUGGESTION_OFFERED. George detail GET 200 rendered Suggested
  appointment; Accept POST 302 to `/my/appointments`; appointments GET 200 rendered Leo and CONFIRMED.
- Reject scenario, request 2: staff interpretation and Suggest each returned 302; George detail returned 200; Another
  with `scope=NOT_THIS_TIME` returned 302; the follow-up 200 rendered NOT_THIS_TIME, You have ruled out, and a new
  SUGGESTION_OFFERED state. The replacement was accepted afterward so the pet could continue the walkthrough.
- Attach scenario, request 3: staff booking-form GET 200 rendered the open request and Attach choice; booking POST with
  `decision=attach` returned 302 to `/staff/calendar`; owner request GET 200 rendered ACCEPTED; owner appointments GET
  200 rendered CONFIRMED.
- Leave-open scenario, request 4: staff booking-form GET 200; booking POST with `decision=leave_open` returned 302 to
  `/staff/calendar`; owner request GET 200 still rendered the original text and WITH_STAFF; owner appointments GET 200
  rendered four CONFIRMED rows.
- Denials: anonymous GET `/staff/queue` returned 302 to `/login`; George GET `/staff/requests/4` returned 403. The full
  security test proves protected-body absence and no mutation for the whole surface.
- The runtime was stopped cleanly after the walkthrough. The default-runtime flow intentionally did not invoke Ollama;
  structured interpretation was entered by staff.

## Validation

- Full-suite command: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test`
- Compilation: PASS
- Tests: 269/0/0/0; skipped: none
- Plan guards: PASS (`RouteInventoryTest`, `ArtifactInventoryTest`, `AcTagCoverageTest`); focused post-status rerun 6/0/0/0
- Localization: PASS in the full suite (`LocalizationKeyTests`, `I18nPropertiesSyncTest`)
- Formatting: PASS (`spring-javaformat:validate`)
- Constraints: 11/11 — RULE-2, RULE-12, RULE-13, RULE-15, RULE-16, RULE-25, RULE-29, RULE-31, RULE-32, RULE-43, RULE-44;
  exact file/line evidence is in `spec/status.md` task notes
- `git status --short` after the suite: only the task-attributable `spec/status.md`; runtime/test execution changed no
  tracked source or data file
- Skeleton test: n/a

## Notes

- The default application-start failure and all four previously failing tests are resolved.
- All 11 phase message bundles are synchronized and the staff templates contain no unkeyed phase literals found by the
  localization tests.
- Calendar picking remains explicitly deferred to cp-4.

CHECKPOINT REACHED. AWAITING APPROVAL.
