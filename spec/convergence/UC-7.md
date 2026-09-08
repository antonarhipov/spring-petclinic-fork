# Convergence: UC-7 - Maintain clinic scheduling configuration

## Summary

- Submission: `spec/checkpoints/UC-7.md` at `6c1a1a9b4f4ba940fefd6a018f72c10cff44bf93`
- Verdict: REJECT
- Findings: 1 critical, 0 gap, 0 protocol
- Suite: 60 focused tests and 369 full-suite tests passed with 0 failures, 0 errors, and 0 skipped in their final runs
- Working tree impact from verification: none; the tracked runtime database remained unchanged

## Protocol Gate

1. Exactly UC-7 was submitted and `spec/status.md` named it `READY_FOR_CONVERGENCE` at the start of the audit.
2. The checkpoint and implementation are committed together at immutable submission `6c1a1a9b4f4ba940fefd6a018f72c10cff44bf93` from clean base `c0ffe098c38c1b3176a4913e0e519e1ca986621d`.
3. UC-7's only dependency, UC-1, is `APPROVED`; UC-2 through UC-6 are also approved.
4. No other use case was `IN_PROGRESS` or `READY_FOR_CONVERGENCE`, and the working tree was clean before verification.
5. The checkpoint contains rows for the main scenario, all extensions and guarantees, both postconditions, the Requires relation, all applicable rules, test commands, changed files, and approved-UC regressions.
6. Inspection of all 34 changed files found one UC-7 vertical slice plus the shared effective-availability extraction required by RULE-20. No unrelated or UC-8 actor behavior is present.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Staff | Main steps 1-3 | Open every configuration type and submit a valid change | `ClinicConfigurationE2ETests.uc7MainStaffMaintainsConfigurationAndReceivesAffectedRequestsThroughRealHttp` passed seeded staff form login, GET, CSRF POST, and rendered current settings through a real random-port server. |
| Staff | Main steps 4-7 | Save a closure, release its hold, route the request, and observe the new calendar | The real-server test persisted the exact closure, deleted the held appointment, stored `WITH_STAFF/SCHEDULE_CHANGED`, rendered the affected request, and then rendered the calendar closed. |
| Staff | Extension 3a | Explain invalid values and save nothing | Production MVC tests rejected malformed `HH:mm` input and overlapping blocks with localized errors and unchanged configuration rows. Service tests rejected reversed hours and invalid duration bounds against complete snapshots. |
| Staff | Extension 4a | List every confirmed conflict, reject all changes, and retain holds | Production MVC and service tests listed both confirmed appointments, rendered the reschedule instruction, and kept configuration, appointments, holds, and request state unchanged. |
| Staff | Extension 5a | Save clean configuration and report no affected requests | Production MVC and service tests saved the changed values and rendered the explicit no-affected-requests result. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| Main step 1 | Staff can choose settings, closures, and veterinarian availability | Staff navigation and one settings page expose all three areas; real staff GET at `ClinicConfigurationE2ETests.java:67` | STRONG | yes |
| Main step 2 | Current values of every named type are shown | Field/value assertions for limits, seven weekdays, working blocks, exceptions, leave, closures, veterinarian ids, zone, identity, and logout at `ClinicConfigurationWebTests.java:47` | STRONG | yes |
| Main step 3 | Staff change one or more values and submit | CSRF form submissions through MockMvc and a real server at `ClinicConfigurationWebTests.java:70` and `ClinicConfigurationE2ETests.java:74` | STRONG | yes |
| Main step 4 | Every conflicting Confirmed and Held appointment is identified | Five effective-schedule changes enumerate both confirmed conflicts and retain the held conflict at `ClinicConfigurationServiceTests.java:62`; hold-only tests identify both held requests | STRONG | yes |
| Main step 5 | Configuration saves only when no Confirmed conflict exists | Rejection occurs before `persist` at `ClinicConfigurationService.java:124`; clean and hold-only paths persist exact rows | STRONG | yes |
| Main step 6 | Conflicting holds are removed and requests become reasoned staff work | Exact appointment deletion, request state/reason, affected-request links, and real HTTP rendering at `ClinicConfigurationServiceTests.java:112` and `ClinicConfigurationE2ETests.java:76` | STRONG | yes |
| Main step 7 | Matching, validation, calendar, and AI context consume saved values | Cross-consumer assertions at `ClinicConfigurationServiceTests.java:139`; however the AI day-part value is observably inconsistent with its saved Thursday closing time, producing C-1 | STRONG | no |
| Extension 3a | Invalid values explain the problem and save nothing | Localized response plus complete/no-row-change assertions at `ClinicConfigurationWebTests.java:90` and `ClinicConfigurationServiceTests.java:198` | STRONG | yes |
| Extension 4a | Confirmed conflicts reject the whole change, list all, instruct, and release no holds | Exact rendered links/instruction and full snapshot preservation at `ClinicConfigurationWebTests.java:112` and `ClinicConfigurationServiceTests.java:62` | STRONG | yes |
| Extension 5a | Clean save reports no affected requests | Exact rendered notice and persisted values at `ClinicConfigurationWebTests.java:47` | STRONG | yes |
| G1 | Closed weekdays, split shifts, exceptions, leave, and closures alter the correct availability | Exact interval calculation at `EffectiveAvailabilityCalculatorTests.java:21` plus conflict cases for all five schedule forms | STRONG | yes |
| G2 | Day parts derive from that day's opening and closing times | `ClinicConfigurationServiceTests.java:139` instead proves Thursday closes at 18:00 while the AI context says evening ends at 19:00; the form and service accept independently contradictory ranges | STRONG | no |
| G3 | Saved configuration cannot invalidate Confirmed care | All five proposed schedule restrictions return every confirmed conflict, preserve complete snapshots, and retain holds at `ClinicConfigurationServiceTests.java:62` | STRONG | yes |
| G4 | Holds never block a valid change and become actionable Needs staff work | Two holds are deleted atomically and both requests become `WITH_STAFF/SCHEDULE_CHANGED` at `ClinicConfigurationServiceTests.java:112` | STRONG | yes |
| G5 | One saved clinic zone determines today's local date and preserves local values | Dynamic zone clock crosses opposite local dates at one instant in `ClinicZoneClockTests.java:19`; local-field persistence and DST regressions passed | STRONG | yes |
| G6 | Normative defaults and passwords are exact | `SeedMigrationTests` passed field-by-field set equality and all configured BCrypt password checks in focused and full runs | STRONG | yes |
| G7 | Fixed exception fixtures have only horizon-local effects | Exact seeded exceptions and time-dependent matching regressions passed without fixture changes | STRONG | yes |
| G8 | Presentation and messages retain UC-1 guarantees | Shared layout, role navigation, identity/logout, CSRF, no-inline-style, and eleven-bundle parity tests passed | STRONG | yes |
| Success postcondition | Valid configuration is current and every invalidated hold is a reasoned hand-off | Real-server closure journey and cross-consumer service test prove persisted values and hold hand-off, subject to C-1 for day-part validity | STRONG | no |
| Minimal guarantee | Invalid or confirmed-conflicting input causes no partial save or hold change | Complete before/after snapshots, retained request states, and rendered negative results at service and MVC boundaries | STRONG | yes |
| Requires UC-1 | Approved staff authentication/authorization is consumed | Seeded real form login, full route matrix, owner 403, anonymous redirect, CSRF, identity, and logout regressions passed | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | MUST separate scheduling responsibilities; controllers MUST NOT decide lifecycle or matching | Controller delegates to configuration service; pure matching calculator and existing lifecycle services retain decisions | PASS |
| RULE-2 | MUST transact mutations atomically and use read-only detached queries | `change` is one transaction; `current` and `veterinarians` are read-only; snapshot failures prove rollback/no mutation | PASS |
| RULE-3 | MUST keep deterministic feasibility framework-free | `EffectiveAvailabilityCalculator` has no Spring/persistence dependency and exact interval tests pass | PASS |
| RULE-4 | MUST enforce exact request transitions below MVC without effects on refusal | Configuration invalidation consumes the existing `SCHEDULE_CHANGED` transition; request lifecycle matrix and snapshot regressions pass | PASS |
| RULE-5 | MUST use one appointment aggregate and delete released holds | Existing release path deletes held appointments; exact absence and lifecycle regressions pass | PASS |
| RULE-6 | MUST lock veterinarians/recheck overlaps and use optimistic request versions | Configuration change locks all veterinarians before scanning; shared appointment concurrency and optimistic-version regressions pass | PASS |
| RULE-7 | MUST use Flyway and local dates/times with lossless persisted values | No schema bypass was introduced; migration, round-trip, restart, and DST tests pass | PASS |
| RULE-8 | MUST preserve every exact normative seed and BCrypt password | `SeedMigrationTests` passed all set and value comparisons | PASS |
| RULE-9 | MUST enforce one complete path-based role/CSRF security chain | `/staff/settings` is inventoried; anonymous, owner, staff, and mutation/CSRF checks pass | PASS |
| RULE-10 | MUST derive `/my/**` owner scope from the principal | UC-7 adds no owner identifier or `/my/**` operation; complete principal-scope regressions pass | PASS |
| RULE-15 | MUST obtain scheduling time only from injected `Clock` | Conflict cut-off and transitions use the injected dynamic clinic-zone clock; no new unclocked `now()` was introduced | PASS |
| RULE-16 | MUST reuse the PetClinic layout/navigation/forms and role/state actions | Settings uses `fragments/layout`, standard components, staff-only navigation, and no inline style; walkthrough remains pending after revision | PASS |
| RULE-17 | MUST localize visible text through keys in all eleven bundles | Template/Java scans and exact bundle-key parity pass | PASS |
| RULE-18 | MUST provide a real-server journey and complete direct evidence without repository residue | Real-server UC-7 journey, 60 focused tests, 369 full tests, clean tree, and unchanged runtime H2 pass; the journey cannot override C-1 | PASS |
| RULE-20 | MUST use one effective-availability calculation across four consumers | Matching, staff validation, and calendar call `AvailabilityService`, which delegates to the pure calculator; conflict detection calls the same calculator | PASS |
| RULE-23 | MUST atomically handle invalid, Confirmed-conflict, Held-conflict, and clean configuration changes | Full snapshots and exact state assertions cover each branch across settings and every schedule form | PASS |
| RULE-24 | MUST retain Maven/H2-only support and isolated runtime/test data | Repository inventory, in-memory tests, full Maven suite, and unchanged file-backed H2 pass | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required staff authentication, authorization, shared layout, localization, and seeds | Full 369-test suite plus focused security/presentation/seed suites | PASS |
| UC-2 | Owner appointment visibility and shared configuration disclosure | Full owner surface/history regressions | PASS |
| UC-3 | AI clinic context, matching, effective availability, clock, and holds | Full prompt/interpreter/matching/lifecycle/E2E regressions; C-1 affects the new configurable day-part behavior and must be revised in UC-7 | PASS |
| UC-4 | Staff validation, request transitions, and hold release | Focused `StaffSchedulingWebTests` and full request/lifecycle regressions | PASS |
| UC-5 | Calendar rendering, effective availability, confirmed conflicts, and appointment lifecycle | Focused `StaffCalendarWebTests` plus full calendar/concurrency/lifecycle regressions | PASS |
| UC-6 | Owner view of appointment outcomes | Full owner activity and real-server regressions | PASS |

## Findings

### C-1 CRITICAL - Named day parts are independent of the applicable weekday's opening hours

- Contract: UC-7 G2 says, "Named day parts resolve from the day's applicable opening hours: morning from opening to 12:00, afternoon from 12:00 to 17:00, and evening from 17:00 to closing" (`spec/spec.md:674`). Main step 7 requires the saved values to drive AI clinic context (`spec/spec.md:660`).
- Code evidence: `staff/settings.html:69` exposes six independently editable day-part boundaries. `ClinicConfigurationService.java:210` accepts any ordered, non-overlapping ranges and `ClinicConfigurationService.java:172` persists them without deriving them from weekday opening hours. `PromptBuilder.java:58` sends those global stored ranges directly to the LLM.
- Test evidence: `ClinicConfigurationServiceTests.java:143` changes Thursday opening hours to 08:00-18:00, `ClinicConfigurationServiceTests.java:155` independently sets evening to 17:00-19:00, and `ClinicConfigurationServiceTests.java:178` asserts that contradictory value is sent in AI context. This is direct evidence of the wrong behavior, not merely missing coverage.
- Why it fails: for a Thursday request the LLM is told the clinic closes at 18:00 and that evening continues until 19:00. Other seeded weekdays likewise receive global day-part limits rather than ranges derived from their own opening/closing time. That violates G2 and can produce interpretations outside the named day's clinic hours.
- Required revision outcome: derive each named day part from the applicable weekday opening interval with the fixed 12:00 and 17:00 boundaries, represent closed/empty parts unambiguously in AI context, and prevent the settings UI/service from saving independent values that contradict the derived ranges. Add value-level coverage for differently opened weekdays and a closed weekday.

## Status Update

`READY_FOR_CONVERGENCE` -> `NEEDS_REVISION`; no next use case is eligible until C-1 is revised and UC-7 converges again.

## Response to execute

REVISE UC-7: C-1 derive named day parts from each weekday's opening hours and prevent contradictory independent values.
