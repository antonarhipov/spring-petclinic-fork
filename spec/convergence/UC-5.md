# Convergence: UC-5 - Operate the clinic calendar and appointment lifecycle

## Summary

- Submission: `spec/checkpoints/UC-5.md` at `509578bd75470b47b4b9293c910129b4e91d8fd8`
- Verdict: APPROVE
- Findings: 0 critical, 0 gap, 0 protocol
- Suite: 355 run, 0 failed, 0 errors, 0 skipped across 46 suites
- Working tree impact from verification: none; the tracked runtime database remained unchanged

## Protocol Gate

1. Exactly UC-5 was submitted and `spec/status.md` named it `READY_FOR_CONVERGENCE` at the start of the audit.
2. `spec/checkpoints/UC-5.md` and the implementation are committed together at immutable submission `509578bd75470b47b4b9293c910129b4e91d8fd8` from base `27a45aa35d004d2d413a07ee2dcb810356881a8f`.
3. Its only dependency, UC-1, is `APPROVED` at `0261b04`; UC-2 through UC-4 are also approved.
4. No other use case was `IN_PROGRESS` or `READY_FOR_CONVERGENCE`, and the working tree was clean before verification.
5. The checkpoint covers the main scenario, every extension and guarantee, both postconditions, the Requires relation, all cross-referenced rules, commands, changed files, and approved-UC regressions.
6. Inspection of all 26 changed files found one UC-5 vertical slice plus the necessary shared extraction of staff-slot validation; no unrelated or later-UC actor behavior is included.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Staff | Main steps 1-3, open a clinic-local date | Six-veterinarian 15-minute calendar with capacity and navigation | Focused tests and a fresh temporary H2 server rendered 2026-09-10 with 09:00-17:00 hours, all six veterinarians, unavailable/free cells, date navigation, and direct-booking selection. |
| Staff and owner | Main steps 4-8 | Open detail, reschedule with reason, and expose the result immediately to the owner | `SchedulingE2eTests.uc5RealServerStaffViewsBooksAndReschedulesWhileOwnerImmediatelySeesTheChange` passed through real form-login, CSRF, HTTP, persistence, and owner rendering. |
| Staff | Extensions 3a-3b | Inspect/release Held and directly book free capacity | Focused HTTP tests rendered owner, pet, request, hold age, and request link; release deleted the hold and routed the request with its reason. Direct booking redirected to a Confirmed detail. |
| Staff and owner | Extension 5a | Cancel with staff actor/time/reason and show the result to the owner | On the isolated server, POST `/staff/appointments/1/cancel` redirected to detail showing Cancelled, `Clinic-closure-test`, `Cancelled by clinic staff`, and `2026-09-08 17:21:24`; George's owner page immediately showed the same appointment and reason. |
| Staff | Extensions 5b-5c | Complete or mark No-show after start | The real-server E2E path posted both actions and observed Completed plus its linked visit fields, or No-show with zero linked visits; focused tests additionally pinned prefill truncation and exact appointment-date linkage. |
| Staff | Extensions 5d-5e | Premature and final-state actions | Forged HTTP completion and No-show against future appointment 2 each returned the localized refusal and left it Confirmed with its prior reason; forged completion of Cancelled appointment 1 returned the same refusal and left it Cancelled. |
| Staff | Extension 7a | Constraint conflict | A second HTTP booking for veterinarian 5 at 2026-09-10 09:00 returned `That time is no longer available`; the calendar retained only appointment 2 at that capacity. Focused snapshots cover every affected table and exact remaining staff constraints. |
| Staff | Extensions 7b and 8a | Concurrent winner and specialty warning | The two-thread reschedule race produced one moved appointment and one unchanged loser; the HTTP reschedule test displayed mismatch information and allowed the otherwise-valid change. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| Main step 1 | Date selection opens a clinic-local day | `StaffCalendarWebTests.java:100`; temporary-server date input rendered `2026-09-10` | STRONG | yes |
| Main step 2 | Six veterinarian columns and 15-minute rows across opening hours with navigation | Exact 6 columns, 32 rows, `09:00-17:00`, previous/next/date-picker assertions at `StaffCalendarWebTests.java:102`; reproduced on temporary server | STRONG | yes |
| Main step 3 | Closed/unavailable, Confirmed, Held, and remaining capacity are visible | Exact cell counts, statuses, owner/pet blocks, unavailable veterinarian, free cells, and closed-day query at `StaffCalendarWebTests.java:104` | STRONG | yes |
| Main step 4 | Staff open a Confirmed appointment | Real-server redirect and detail at `SchedulingE2eTests.java:547`; temporary-server direct-book redirect to `/staff/appointments/1` | STRONG | yes |
| Main step 5 | Detail shows all named fields and permitted actions | Real-server assertions at `SchedulingE2eTests.java:550`, state-dependent DOM at `StaffCalendarWebTests.java:289`, and reproduced Confirmed detail | STRONG | yes |
| Main step 6 | Staff enter veterinarian/date/time and reason | Real HTTP POST with all fields at `SchedulingE2eTests.java:554` and focused MVC POST at `StaffCalendarWebTests.java:130` | STRONG | yes |
| Main step 7 | Current calendar state is rechecked against staff constraints | Shared validation plus locked overlap recheck at `StaffCalendarService.java:39` and `AppointmentService.java:107`; conflict snapshots and temporary-server overlap refusal | STRONG | yes |
| Main step 8 | Immediate reschedule without owner acceptance | Exact appointment row, still-Accepted request, and owner page at `StaffCalendarWebTests.java:144`; real-server owner result at `SchedulingE2eTests.java:554` | STRONG | yes |
| Extension 3a | Held detail and reasoned release | Staff HTTP detail and release with exact hold deletion/request fields at `StaffCalendarWebTests.java:163` | STRONG | yes |
| Extension 3b | Direct booking from free capacity | MVC direct-book path and exact detail at `StaffCalendarWebTests.java:186`; temporary-server direct booking and owner-visible lifecycle result | STRONG | yes |
| Extension 5a | Reasoned staff cancellation | Exact audit row and owner rendering at `StaffCalendarWebTests.java:220`; independently reproduced through form login, CSRF, HTTP, staff detail, and owner page | STRONG | yes |
| Extension 5b | Completion creates a final appointment and linked visit | Exact prefill, status/finality matrix, linked visit fields, and duplicate refusal at `StaffCalendarWebTests.java:251`; real HTTP completion at `SchedulingE2eTests.java:248` | STRONG | yes |
| Extension 5c | No-show is final and creates no visit | Exact status/zero-visit/owner rendering at `StaffCalendarWebTests.java:278`; real HTTP no-show at `SchedulingE2eTests.java:279` | STRONG | yes |
| Extension 5d | Premature completion/no-show is refused without change | Full snapshots at `StaffCalendarWebTests.java:241`; independently reproduced both forged HTTP actions with localized refusal and retained Confirmed detail | STRONG | yes |
| Extension 5e | Final-state actions are refused without change | Lifecycle matrix and snapshots at `StaffCalendarWebTests.java:233`; independently reproduced forged completion of a Cancelled appointment with localized refusal and unchanged detail | STRONG | yes |
| Extension 7a | Staff-constraint conflict is identified with no change | Full appointment/request/visit snapshots at `StaffCalendarWebTests.java:203`, boundary matrix at `StaffSchedulingWebTests.java:166`, and reproduced overlapping HTTP booking refusal | STRONG | yes |
| Extension 7b | Concurrent loser is reported and remains at prior time | Two-thread real-transaction outcome and both persisted appointments at `ConcurrencyInvariantTests.java:189` | STRONG | yes |
| Extension 8a | Specialty mismatch warns but does not block | Rendered candidate warnings, post-action flash, persisted mismatching veterinarian, and owner-visible result at `StaffCalendarWebTests.java:116` | STRONG | yes |
| G1 | Full calendar contains all named capacity inputs and outputs | Value-level grid/status/owner/pet/availability assertions at `StaffCalendarWebTests.java:90` plus real rendered calendar | STRONG | yes |
| G2 | Held owner/pet data is staff-only with age and request path | Staff detail at `StaffCalendarWebTests.java:170`; owner/anonymous denials with disclosure and database checks at `SecurityMatrixWebTests.java:63` and `SecurityMatrixWebTests.java:85` | STRONG | yes |
| G3 | Staff actions enforce only staff constraints | Shared `StaffSlotValidator.java:23`, locked no-overlap path, exact override/boundary regressions in `StaffSchedulingWebTests`, and UC-5 snapshots | STRONG | yes |
| G4 | Staff changes/cancellation require reasons and reschedule immediately | Below-MVC reason validation in `AppointmentService.java:42`; blank-reason snapshots and owner-visible HTTP outcomes at `StaffCalendarWebTests.java:116` and `StaffCalendarWebTests.java:211` | STRONG | yes |
| G5 | Cancelled, Completed, and No-show are final; only completion links a visit | Complete lifecycle matrix, exact linked/absent visits, duplicate refusal, and final-action DOM at `StaffCalendarWebTests.java:211` and `StaffCalendarWebTests.java:251` | STRONG | yes |
| G6 | Calendar and actions use trusted clinic-local date/time | Injected-clock code at `StaffAppointmentController.java:27`, `StaffCalendarQueryService.java:49`, and `AppointmentService`; local field/restart/DST regressions passed | STRONG | yes |
| G7 | Concurrent capacity claims recheck and yield one winner | Pessimistic veterinarian lock/overlap recheck plus two-thread reschedule evidence at `ConcurrencyInvariantTests.java:189`; approved hold/confirm/direct-book races also passed | STRONG | yes |
| G8 | Shared localized presentation and mandatory reasons | Both templates use the common layout and message keys; rendered action tests and exact eleven-bundle parity passed; reasons are enforced below MVC | STRONG | yes |
| Success postcondition | Complete day and valid change are immediately visible to staff and owner | Real-server UC-5 journey plus independent direct-book/cancel staff-to-owner reproduction | STRONG | yes |
| Minimal guarantee | Conflict, premature/final, and losing actions preserve prior rows and states | Full database snapshots, lifecycle matrices, concurrency assertions, and independent HTTP refusals above | STRONG | yes |
| Requires UC-1 | UC-5 consumes approved staff/owner authentication and authorization | Real form-login sessions, full role/CSRF route matrix, and approved UC-1 regressions | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | MUST separate scheduling responsibilities; controllers MUST NOT decide lifecycle/matching | `StaffAppointmentController` delegates queries and mutations to `StaffCalendarQueryService` and `StaffCalendarService`; feasibility remains under matching | PASS |
| RULE-2 | MUST transact each mutation atomically and use read-only query transactions | Read-only service annotation at `StaffCalendarQueryService.java:31`; transactional commands at `StaffCalendarService.java:28`; snapshot refusals prove no partial rows | PASS |
| RULE-3 | MUST keep deterministic feasibility framework-free | `StaffSlotValidator.java:23` delegates pure interval/grid decisions to `FeasibilityChecker`; its plain-Java suite passed | PASS |
| RULE-4 | MUST enforce exact request exits below MVC without side effects | UC-5 hold release consumes approved `RequestService` transition; full request-state matrix and release snapshots passed | PASS |
| RULE-5 | MUST use one appointment aggregate and refuse unlisted transitions without effects | `AppointmentService` uses `requireInStatus`/`requireAfterStart`; 32-state lifecycle tests and UC-5 snapshots passed | PASS |
| RULE-6 | MUST lock veterinarian/recheck overlap; staff request actions MUST use optimistic versioning | `AppointmentService.lockAvailableVet` and `VetRepository.findByIdForUpdate`; real two-thread races and UC-4 optimistic-version regressions passed | PASS |
| RULE-7 | MUST use Flyway, local dates/times, and lossless persistence | No schema bypass was added; migration, mapping, restart, and data-fidelity suites passed | PASS |
| RULE-8 | MUST seed exact normative sets with BCrypt passwords | `SeedMigrationTests` passed every row/set/password value in the full suite | PASS |
| RULE-9 | MUST enforce one complete path-based role/CSRF security surface | All UC-5 GET/POST shapes are enumerated at `SecurityMatrixWebTests.java:46`; anonymous and owner denials prove no disclosure or mutation | PASS |
| RULE-10 | MUST derive `/my/**` owner scope from the principal and normalize foreign/unknown results | UC-5 adds no owner identifier under `/my/**`; principal-scoped owner result and isolation regressions passed | PASS |
| RULE-15 | MUST obtain current scheduling date/time only from injected `Clock` | Static scan found no unclocked `now()`; calendar, hold age, finalization, and audit services use injected clock | PASS |
| RULE-16 | MUST use the shared layout/navigation/forms and show only role/state actions | Both UC-5 templates use `fragments/layout`; rendered tests prove role/state actions; human visual check remains pending | PASS |
| RULE-17 | MUST resolve visible text from keys present in all eleven bundles | Template/Java scans and `I18nPropertiesSyncTest` passed exact key equality; all twelve new keys exist in eleven bundles | PASS |
| RULE-18 | MUST provide real-server actor evidence and leave tracked/runtime data unchanged | Focused real-server journey, independent temporary-server reproduction, mapped ledger, 355-test full suite, clean tree, and unchanged runtime H2 | PASS |
| RULE-19 | MUST enforce hours/effective blocks/grid/duration/no-overlap but not owner windows/specialty/horizon/lead | Shared validator plus locked overlap check; exact UC-4 boundary matrix, UC-5 snapshots, mismatch success, and HTTP conflict refusal passed | PASS |
| RULE-20 | MUST use one effective-availability calculation across matching, staff validation, calendar, and conflicts | `AvailabilityService.getEffectiveAvailability` is consumed by `DefaultSlotSuggestionPort`, `StaffSlotValidator`, and `StaffCalendarQueryService` | PASS |
| RULE-23 | MUST make configuration changes atomic under invalid, Confirmed-conflict, and Held-conflict outcomes | UC-5 exposes no configuration mutation and reads settings/availability only; no mutation path was duplicated or weakened, leaving the conditional behavior to UC-7 | PASS |
| RULE-24 | MUST support Maven/H2 only with isolated runtime/test databases | Repository-scope, README, restart, and isolation tests passed; verification used temporary H2 and left tracked runtime H2 unchanged | PASS |
| RULE-25 | MUST create exactly one linked appointment-date visit only on completion with bounded prefill | Exact 255-character/empty prefill, linked row values, duplicate refusal, stock walk-in regression, and zero No-show visit passed | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required authentication, authorization, navigation, localization, and normative data | Full 355-test suite and direct seeded staff/owner form-login reproduction | PASS |
| UC-2 | Owner appointment rendering and immediate staff-change visibility | Full owner activity suites plus real-server owner result | PASS |
| UC-3 | Appointment lifecycle, matching, effective availability, hold/confirm concurrency, completion/no-show | Full interpretation/matching/lifecycle/E2E suites | PASS |
| UC-4 | Shared staff constraints, hold release, request actions, and extracted validator | Focused `StaffSchedulingWebTests` plus full request/concurrency suites | PASS |

## Findings

No critical, gap, protocol, drift, or cosmetic finding remains. The user confirmed the complete UI walkthrough.

## Walkthrough

1. Sign in as staff, open Calendar, choose an open date, and confirm six veterinarian columns, 15-minute rows, opening hours, unavailable shading, free capacity, and previous/next/date-picker navigation.
2. Open one Confirmed block and confirm owner, pet, veterinarian, local date/time, status, origin, reason, and only the permitted actions. Open one Held block and confirm request text/link, hold age, and reason-required release.
3. Select free capacity, directly book an owner/pet with a reason, then sign in as that owner and confirm the appointment is immediately visible.
4. As staff, reschedule a Confirmed appointment with a reason; choose a specialty-mismatching veterinarian once and confirm the warning does not block a valid slot. Verify the owner immediately sees the new veterinarian/time/reason without accepting again.
5. Cancel a Confirmed appointment with a reason and confirm the detail shows Cancelled, staff as actor, time, and reason; verify the owner sees the same outcome and no further lifecycle actions appear.
6. For appointments whose start has passed, complete one using the prefilled description and mark another No-show. Confirm final statuses and no further actions; confirm the owner sees both outcomes.
7. On a future Confirmed appointment, confirm completion and No-show actions are absent. On each final appointment, confirm reschedule/cancel/complete/no-show actions are absent.

User result: PASS on 2026-09-08.

## Status Update

`PENDING_WALKTHROUGH` -> `APPROVED`; UC-6, UC-7, and UC-8 are eligible.

## Response to execute

APPROVED
