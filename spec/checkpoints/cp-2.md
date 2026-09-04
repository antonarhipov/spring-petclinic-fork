# Checkpoint: phase-2 complete

## Summary

- Phase: phase-2, Owner automation and recovery — UC-1, UC-2, UC-3
- Tasks: 21/21 complete
- Commits: `811bc57..8b71c1c` (one commit per task, listed below)
- Phase acceptance criteria: 68/68 have passing test citations; grouped pointers are in AC Coverage and the task-level closure evidence is in `spec/status.md:67-107`.
- Phase rules: 27/27 have code/test pointers in the task-level closure evidence.

## Task Closure

| Task | Commit | Artifacts present (exact names) | Scope clean | ACs cited by tests | Validation bullets with file:line |
|---|---|---|---|---|---|
| task-2.1 | `811bc57` | yes | yes | n/a; supporting guard citations pass | 4/4 (`status.md:107`) |
| task-2.2 | `8d03027` | yes | yes | 5/5 | 6/6 (`status.md:105`) |
| task-2.3 | `9ed48a9` | yes | yes | 2/2 | 3/3 (`status.md:103`) |
| task-2.4 | `672ad14` | yes | yes | 2/2 | 2/2 (`status.md:101`) |
| task-2.5 | `1f4f16e` | yes | yes | 5/5 | 5/5 (`status.md:99`) |
| task-2.6 | `15992ce` | yes | yes | 5/5 | 5/5 (`status.md:97`) |
| task-2.7 | `3f88c98` | yes | yes | 5/5 | 5/5 (`status.md:95`) |
| task-2.8 | `17f4681` | yes | yes | 5/5 | 5/5 (`status.md:93`) |
| task-2.9 | `ed0a0a3` | yes | yes | 5/5 | 5/5 (`status.md:91`) |
| task-2.10 | `7982359` | yes | yes | 4/4 | 4/4 (`status.md:89`) |
| task-2.11 | `89a81c0` | yes | yes | 5/5 | 5/5 (`status.md:87`) |
| task-2.12 | `e150cdd` | yes | yes | 5/5 | 5/5 (`status.md:85`) |
| task-2.13 | `8572841` | yes | yes | 4/4 | 5/5 (`status.md:83`) |
| task-2.14 | `ad8357e` | yes | yes | 4/4; AC-58 regression also cited | 4/4 (`status.md:81`) |
| task-2.15 | `ad2c605` | yes | yes | 3/3 | 3/3 (`status.md:79`) |
| task-2.16 | `1f09c2b` | yes | yes | 5/5 | 5/5 (`status.md:77`) |
| task-2.17 | `b4c7656` | yes | yes | 2/2 | 2/2 (`status.md:75`) |
| task-2.18 | `1fe5b8b` | yes | yes | 2/2 | 2/2 (`status.md:73`) |
| task-2.19 | `a4efc36` | yes | yes | n/a; AC-138/27/28/32 validation citations pass | 3/3 (`status.md:71`) |
| task-2.20 | `28ca1f4` | yes | yes | n/a; AC-138 validation citation passes | 1/1 (`status.md:69`) |
| task-2.21 | `8b71c1c` | yes | yes | n/a; AC-138 validation citation passes | 1/1 (`status.md:67`) |

## Artifacts

| Declared in | Exact artifact paths | Purpose |
|---|---|---|
| task-2.1 | `src/test/java/org/springframework/samples/petclinic/planning/ArtifactInventoryTest.java`; `src/test/java/org/springframework/samples/petclinic/planning/RouteInventoryTest.java`; `src/test/java/org/springframework/samples/petclinic/planning/AcTagCoverageTest.java` | Executable plan guards |
| task-2.2 | `src/main/java/org/springframework/samples/petclinic/system/CrashController.java`; `src/test/java/org/springframework/samples/petclinic/presentation/OwnerPresentationContractTests.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/web/SchedulingErrorHandlingTests.java` | cp-1 presentation/error remediation |
| task-2.3 | `src/test/java/org/springframework/samples/petclinic/scheduling/web/OwnerRequestContractTests.java` | Selector, first-ranked tuple, and refusal contract |
| task-2.4 | `src/main/java/org/springframework/samples/petclinic/scheduling/web/OwnerRequestActionController.java`; `src/main/java/org/springframework/samples/petclinic/scheduling/web/OwnerRequestEditForm.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/web/OwnerRequestRouteTests.java` | Owner edit/abandon/hand-off/another surface |
| task-2.5 | `src/main/java/org/springframework/samples/petclinic/scheduling/config/InterpretationExecutorConfiguration.java`; `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/AsyncInterpretationService.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/AsyncInterpretationTests.java` | Dedicated asynchronous interpretation |
| task-2.6 | `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationRecoveryRunner.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationRecoveryTests.java` | Recovery and outcome routing |
| task-2.7 | `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/ModelUnavailableException.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/OllamaAdapterContractTests.java` | Adapter failure/retry boundary |
| task-2.8 | `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationNormalizer.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationNormalizerTests.java` | Structured interpretation normalization |
| task-2.9 | `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretationPromptFactory.java`; `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/WindowMatcher.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/PromptAndWindowTests.java` | Prompt and availability windows |
| task-2.10 | `src/main/java/org/springframework/samples/petclinic/scheduling/solver/SlotBoundaryService.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/solver/SlotBoundaryTests.java` | Part-of-day and lead boundaries |
| task-2.11 | `src/test/java/org/springframework/samples/petclinic/scheduling/solver/TimefoldHardConstraintATests.java` | Solver hard constraints A |
| task-2.12 | `src/test/java/org/springframework/samples/petclinic/scheduling/solver/TimefoldHardConstraintBTests.java` | Solver hard constraints B and priority |
| task-2.13 | `src/test/java/org/springframework/samples/petclinic/scheduling/solver/SolverBudgetAndHorizonTests.java` | Solver budget and horizon |
| task-2.14 | `src/main/java/org/springframework/samples/petclinic/scheduling/request/HoldService.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/request/HoldLifecycleTests.java` | Hold lifecycle |
| task-2.15 | `src/test/java/org/springframework/samples/petclinic/scheduling/request/HoldRevalidationTests.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/ConcurrentConfirmationTests.java` | Hold recovery and simultaneous confirmation |
| task-2.16 | `src/main/java/org/springframework/samples/petclinic/scheduling/request/SuggestionRejection.java`; `src/main/java/org/springframework/samples/petclinic/scheduling/request/RejectionScope.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/request/AskAnotherOptionTests.java` | Exclusion scopes and edit reset |
| task-2.17 | `src/test/java/org/springframework/samples/petclinic/scheduling/web/RuledOutPresentationTests.java` | Ruled-out rendering and audit payload |
| task-2.18 | `src/test/java/org/springframework/samples/petclinic/scheduling/ConcurrentActiveRequestTests.java` | Simultaneous active-request guarantee |
| task-2.19 | `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerGuidedFlowE2eTests.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/InterpretingPageTests.java`; `src/test/java/org/springframework/samples/petclinic/scheduling/interpretation/LatchedRequestInterpreter.java` | UC-1 HTTP E2E and real-executor waiting page |
| task-2.20 | `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerAsksAnotherE2eTests.java` | UC-2 HTTP E2E |
| task-2.21 | `src/test/java/org/springframework/samples/petclinic/scheduling/OwnerRephrasesE2eTests.java` | UC-3 HTTP E2E |

## AC Coverage (this phase)

| AC | Test class.method | Level | Assertion pinned |
|---|---|---|---|
| AC-7, AC-19 | `OwnerPresentationContractTests.everyOwnerPageShowsIdentityLogoutAndBanner_AC7_AC19` (`:84-96`) | HTTP, real filter chain | Every owner page has identity, POST logout, and configured phone |
| AC-16, AC-17 | `OwnerPresentationContractTests.ownerPagesReuseStockLayoutWithoutEditControls_AC16_AC17` (`:108-129`) | HTTP | Stock layout only; owned pets are read-only |
| AC-18 | `OwnerPresentationContractTests.requestPageAloneExposesVetNamesAndSpecialties_AC18` (`:131-151`) | HTTP | Vet data appears only on request pages |
| AC-21 | `OwnerRequestContractTests.newRequestOmitsOnlyPetsWithActiveRequests_AC21` (`:108-118`) | HTTP | Rendered selector keeps eligible owned pets and omits active-request pets |
| AC-58 | `OwnerRequestContractTests.confirmationInvokesRankerAndHoldsItsFirstTuple_AC58` (`:120-146`) | HTTP | Production ranker invoked; vet/start/duration equals first tuple |
| AC-54 | `OwnerRequestRouteTests.editRequiresFreshConsent_AC54` (`:146-198`) | HTTP | Edit clears old review/hold and requires new consent |
| AC-122 | `OwnerRequestRouteTests.routeToStaffTransitionsAndRecordsEvent_AC122` (`:192-239`) | HTTP | Every allowed origin reaches WITH_STAFF with one complete event |
| AC-24, AC-26, AC-27, AC-28, AC-31 | `AsyncInterpretationTests` tagged methods (`:114-251`) | HTTP plus real executor | Commit-before-send, one job, interpreting restrictions, exact meta refresh, fresh transaction |
| AC-25, AC-29, AC-30, AC-32, AC-33 | `InterpretationRecoveryTests` tagged methods (`:100-187`) | HTTP/service integration | Decline, stale result discard, startup recovery, usable and failed outcomes |
| AC-34, AC-35, AC-36, AC-37, AC-38 | `OllamaAdapterContractTests` tagged methods (`:108-195`) | Adapter contract/HTTP | Retry/timeout, OTHER routing, and three-attempt recommendation boundary |
| AC-39, AC-40, AC-41, AC-42, AC-43 | `InterpretationNormalizerTests` tagged methods (`:39-92`) | Unit | All fields, preferred-vet resolution, and exact duration bounds |
| AC-44, AC-45, AC-46, AC-47, AC-48 | `PromptAndWindowTests` tagged methods (`:45-108`) | Unit | Clamp/defaults, complete prompt, recurrence and allowed-union semantics |
| AC-49, AC-50, AC-51, AC-52 | `SlotBoundaryTests` tagged methods (`:31-78`) | Unit | Weekday intersections and before/exact/after lead boundary |
| AC-73, AC-74, AC-75, AC-76, AC-77 | `TimefoldHardConstraintATests` tagged methods (`:57-139`) | Solver/constraint | Model cardinality and dual-layer hard rejection A |
| AC-78, AC-79, AC-80, AC-81, AC-82 | `TimefoldHardConstraintBTests` tagged methods (`:54-135`) | Solver/constraint | Window/horizon/cross-pet hard rejection and score priority |
| AC-83, AC-104, AC-105, AC-106 | `SolverBudgetAndHorizonTests` tagged methods (`:85-155`) | Solver/integration | Grid, one-second synchronous locked solve, and owner/staff horizon |
| AC-57, AC-59, AC-60, AC-61 | `HoldLifecycleTests` tagged methods (`:60-184`) | Service | Invalid-state refusal, overlap, timerless releases, exhausted hand-off |
| AC-62, AC-64 | `HoldRevalidationTests` tagged methods (`:78-128`) | HTTP, real filter chain | Invalid/lost holds reoffer or hand off without error |
| AC-65 | `ConcurrentConfirmationTests.simultaneousOwnersAtMostOneConfirmsSameVetTime_AC65` (`:101-145`) | Two real transactions | At most one vet/start winner and no overlap |
| AC-66, AC-67, AC-68, AC-69, AC-70 | `AskAnotherOptionTests` tagged methods (`:41-114`) | Service | Unlimited replacement, exact time/day/vet scopes, edit version reset |
| AC-71, AC-72 | `RuledOutPresentationTests` tagged methods (`:79-118`) | HTTP | One-value heading/no undo and full rejection event payload |
| AC-22, AC-23 | `ConcurrentActiveRequestTests` tagged methods (`:61-129`) | Two real transactions | One active row maximum; loser gets named refusal without leaked row/event |
| AC-138 | `OwnerGuidedFlowE2eTests.ownerGuidedFlowSteps1Through8` (`:97-157`); `OwnerAsksAnotherE2eTests.ownerAsksForAnotherOptionSteps1And2` (`:86-99`); `OwnerRephrasesE2eTests.ownerRephrasesSteps1And2` (`:87-137`) | HTTP, real filter chain and CSRF | UC-1, UC-2 and UC-3 main/state-changing legs with state after every step |

## Routes (this phase)

| Method | Path | Owning task | Handler | Observed status as owner |
|---|---|---|---|---|
| GET | `/my/pets` | task-1.4 | `OwnerPageController.pets` (`:27`) | 200 (`OwnerPresentationContractTests`) |
| GET | `/my/appointments` | task-1.4 | `OwnerPageController.appointments` (`:34`) | 200 (runtime UC-1) |
| GET | `/my/requests/new` | task-1.6 | `OwnerRequestController.newForm` (`:57`) | 200 (runtime UC-1/2/3) |
| POST | `/my/requests` | task-1.6 | `OwnerRequestController.create` (`:65`) | 302 (runtime UC-1/2/3) |
| GET | `/my/requests/{requestId}` | task-1.6 | `OwnerRequestController.detail` (`:81`) | 200 (runtime UC-1/2/3) |
| POST | `/my/requests/{requestId}/consent` | task-1.6 | `OwnerRequestController.consent` (`:94`) | 302 (runtime UC-1/2/3) |
| POST | `/my/requests/{requestId}/decline` | task-1.6 | `OwnerRequestController.decline` (`:108`) | 302 (`OwnerGuidedFlowE2eTests.declineConsentRoutesWithoutAi`) |
| POST | `/my/requests/{requestId}/confirm` | task-1.6 | `OwnerRequestController.confirm` (`:120`) | 302 (runtime UC-1/2/3) |
| POST | `/my/requests/{requestId}/accept` | task-1.6 | `OwnerRequestController.accept` (`:132`) | 302 to `/my/appointments` (runtime UC-1) |
| GET | `/my/requests/{requestId}/edit` | task-2.4 | `OwnerRequestActionController.editForm` (`:64`) | 200 (runtime UC-3) |
| POST | `/my/requests/{requestId}/edit` | task-2.4 | `OwnerRequestActionController.edit` (`:77`) | 302 (runtime UC-3) |
| POST | `/my/requests/{requestId}/abandon` | task-2.4 | `OwnerRequestActionController.abandon` (`:94`) | 302 (runtime UC-2) |
| POST | `/my/requests/{requestId}/route-to-staff` | task-2.4 | `OwnerRequestActionController.routeToStaff` (`:109`) | 302 (runtime UC-3) |
| POST | `/my/requests/{requestId}/another` | task-2.4 | `OwnerRequestActionController.another` (`:127`) | 302 (runtime UC-2/3) |
| GET | `/staff/calendar` | task-1.4 | `StaffPageController.calendar` (`:24`) | 403, protected body absent (runtime) |
| GET | `/oups` | task-2.2 | `CrashController.triggerException` (`:29`) | 403, protected body absent (runtime) |

The same runtime owner session also received 403 with protected content absent for `/staff/queue` and `/staff/settings`.

## Runtime Evidence

- App started with: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home SCHEDULING_AI_PROVIDER=stub ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=18080 --scheduling.interpretation.executor=synchronous"`
- Ready: 2026-09-04 19:22:40 Europe/Tallinn at `http://localhost:18080`; clean unique in-memory H2 migrated through Flyway V1..V4.
- Owner login: `GET /login` → 200; `POST /login` as `george` → 302 `/my/appointments`.
- Every 200 owner page below was checked for the stock navbar/layout, only My Pets/My Appointments navigation, `george` + Owner identity, POST Logout, `Need urgent care? Call 555-0199.`, and no `/staff/` navigation.

| Flow | Request | Status | Rendered/persisted result checked |
|---|---|---|---|
| UC-1 | `GET /my/requests/new` | 200 | Start form |
| UC-1 | `POST /my/requests` | 302 `/my/requests/1` | Request created |
| UC-1 | `GET /my/requests/1` | 200 | AWAITING_CONSENT, Annual checkup |
| UC-1 | `POST /my/requests/1/consent` | 302 | Consent applied |
| UC-1 | `GET /my/requests/1` | 200 | INTERPRETED, review and confirm control |
| UC-1 | `POST /my/requests/1/confirm` | 302 | Suggestion generated |
| UC-1 | `GET /my/requests/1` | 200 | SUGGESTION_OFFERED and appointment tuple |
| UC-1 | `POST /my/requests/1/accept` | 302 `/my/appointments` | Suggestion accepted |
| UC-1 | `GET /my/appointments` | 200 | Leo CONFIRMED |
| UC-2 | `GET /my/requests/new` | 200 | Start form |
| UC-2 | `POST /my/requests` | 302 `/my/requests/2` | Request created |
| UC-2 | `GET /my/requests/2` | 200 | AWAITING_CONSENT |
| UC-2 | `POST /my/requests/2/consent` | 302 | Consent applied |
| UC-2 | `GET /my/requests/2` | 200 | INTERPRETED |
| UC-2 | `POST /my/requests/2/confirm` | 302 | First suggestion generated |
| UC-2 | `GET /my/requests/2` | 200 | First SUGGESTION_OFFERED |
| UC-2 | `POST /my/requests/2/another` (`NOT_THIS_TIME`) | 302 | First complete tuple rejected |
| UC-2 | `GET /my/requests/2` | 200 | Replacement SUGGESTION_OFFERED; one-value `You have ruled out:` heading and NOT_THIS_TIME entry |
| UC-2 | `POST /my/requests/2/abandon` | 302 | Walkthrough request closed |
| UC-2 | `GET /my/requests/2` | 200 | ABANDONED |
| UC-3 | `GET /my/requests/new` | 200 | Start form |
| UC-3 | `POST /my/requests` | 302 `/my/requests/3` | Request created |
| UC-3 | `GET /my/requests/3` | 200 | AWAITING_CONSENT, Initial checkup |
| UC-3 | `POST /my/requests/3/consent` | 302 | First consent applied |
| UC-3 | `GET /my/requests/3` | 200 | INTERPRETED first review |
| UC-3 | `POST /my/requests/3/confirm` | 302 | Suggestion generated |
| UC-3 | `GET /my/requests/3` | 200 | SUGGESTION_OFFERED |
| UC-3 | `POST /my/requests/3/another` (`NOT_THIS_TIME`) | 302 | Exclusion applied |
| UC-3 | `GET /my/requests/3` | 200 | Replacement suggestion and ruled-out list |
| UC-3 | `GET /my/requests/3/edit` | 200 | Existing reason and availability prefilled |
| UC-3 | `POST /my/requests/3/edit` | 302 | New skin concern / Friday saved |
| UC-3 | `GET /my/requests/3` | 200 | AWAITING_CONSENT, new text, Grant consent |
| UC-3 | `POST /my/requests/3/consent` | 302 | Fresh consent applied |
| UC-3 | `GET /my/requests/3` | 200 | INTERPRETED new review, Confirm interpretation |
| UC-3 | `POST /my/requests/3/route-to-staff` | 302 | Owner hand-off applied |
| UC-3 | `GET /my/requests/3` | 200 | WITH_STAFF and edited reason |
| denial | `GET /staff/queue` | 403 | Protected queue body absent |
| denial | `GET /staff/calendar` | 403 | Protected calendar body absent |
| denial | `GET /staff/settings` | 403 | Protected settings body absent |
| denial | `GET /oups` | 403 | Protected staff error body absent |

The walkthrough completed with `WALKTHROUGH PASS`; the application then shut down cleanly through Spring Boot's graceful shutdown.

## Validation

- Full-suite command: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test`
- Compilation: PASS
- Tests: 246/0/0/0 (run/failures/errors/skipped)
- Plan guards: PASS, 6/0/0/0 with `ArtifactInventoryTest`, `RouteInventoryTest`, and `AcTagCoverageTest`, including deliberate broken fixtures
- Skeleton test: green; Phase 2 UC E2E classes are all green
- Concurrent guarantees: `ConcurrentConfirmationTests` and `ConcurrentActiveRequestTests` use two simultaneous real transactions and prove one winner maximum with no leaked row (`status.md:73,79`)
- Constraints: 27/27 phase rules satisfied; each has code/test pointers in `status.md:67-107`
- `git diff --check`: PASS
- `git status --short` after suite, guards, and runtime walkthrough: clean

## Notes

- Approved task-2.17 rendering decision: the localized ruled-out heading and colon are rendered as one `th:text` value (`requestDetail.html:43-46`).
- Approved task-2.19 event names: `CONSENT_GRANTED` and `INTERPRETATION_APPLIED` (`status.md:61,71`).
- Approved task-2.19 lost-hold identity: compare the complete `(vet, start)` tuple; the same time with another veterinarian is a valid replacement (`status.md:62,71`).
- No unapproved deviations were introduced in Phase 2.

CHECKPOINT REACHED. AWAITING APPROVAL.
