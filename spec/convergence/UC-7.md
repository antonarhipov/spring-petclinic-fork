# Convergence: UC-7 - Maintain clinic scheduling configuration

## Summary

- Submission: `spec/checkpoints/UC-7.md` at `cd347911ad5904e97c79435b11b47f76eda51d97`, revising initial submission `6c1a1a9b4f4ba940fefd6a018f72c10cff44bf93`
- Verdict: PENDING WALKTHROUGH
- Findings: 0 critical, 0 gap, 0 protocol, 0 drift, 0 cosmetic
- Suite: 16 focused tests and 369 full-suite tests passed with 0 failures, 0 errors, and 0 skipped
- Working tree impact from verification: none; the tracked runtime database remained 126976 bytes with SHA-256 `6c80213d262edc022bdc86145788363da080cc2d03dd828717806410f7b66570`

## Protocol Gate

1. Exactly UC-7 was submitted and `spec/status.md` named it `READY_FOR_CONVERGENCE` at the start of this audit.
2. The C-1 revision, checkpoint, and status are committed together at immutable submission `cd347911ad5904e97c79435b11b47f76eda51d97`, based on rejection commit `4ecf34f`.
3. UC-7's only dependency, UC-1, is `APPROVED`; UC-2 through UC-6 are also approved.
4. No other use case was `IN_PROGRESS` or `READY_FOR_CONVERGENCE`, and the working tree was clean before verification.
5. The checkpoint contains evidence for the complete main scenario, every extension and guarantee, both postconditions, the Requires relation, every applicable rule, validation commands, changed files, and approved-UC regressions.
6. Inspection of all 23 revision files found only the C-1 correction and its evidence. No unrelated change or UC-8 actor behavior is present.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Staff | Main steps 1-3 | Open every configuration area and submit a valid change | The real-server test passed seeded staff form login, GET, CSRF POST, current-value rendering, the fixed derivation notice, and absence of independent day-part inputs at `ClinicConfigurationE2ETests.java:63`. |
| Staff | Main steps 4-7 | Save a closure, release its hold, route the request, and observe the new calendar | The real-server test persisted the exact closure, deleted the held appointment, stored `WITH_STAFF/SCHEDULE_CHANGED`, rendered the affected request, and rendered the calendar closed at `ClinicConfigurationE2ETests.java:77`. |
| Staff | Extension 3a | Explain invalid values and save nothing | Production MVC tests rejected malformed `HH:mm` and overlapping blocks with localized errors and unchanged rows; service tests rejected reversed hours and invalid duration bounds against complete snapshots. |
| Staff | Extension 4a | List every confirmed conflict, reject all changes, and retain holds | Production MVC and service tests listed both confirmed appointments, rendered the reschedule instruction, and kept configuration, appointments, holds, and request state unchanged. |
| Staff | Extension 5a | Save clean configuration and report no affected requests | Production MVC and service tests saved the exact changed values and rendered the explicit no-affected-requests result. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| Main step 1 | Staff can choose settings, closures, and veterinarian availability | Staff navigation and the settings page expose all three areas; real staff GET at `ClinicConfigurationE2ETests.java:67` | STRONG | yes |
| Main step 2 | Current values of every named configuration type are shown | Value assertions cover limits, seven weekdays, derived day-part policy, working blocks, exceptions, leave, closures, veterinarian ids, zone, identity, and logout at `ClinicConfigurationWebTests.java:47` | STRONG | yes |
| Main step 3 | Staff change one or more values and submit | CSRF form submissions through MockMvc and a real server at `ClinicConfigurationWebTests.java:70` and `ClinicConfigurationE2ETests.java:77` | STRONG | yes |
| Main step 4 | Every conflicting Confirmed and Held appointment is identified | Five effective-schedule changes enumerate both confirmed conflicts and retain the held conflict at `ClinicConfigurationServiceTests.java:62`; hold-only tests identify both held requests | STRONG | yes |
| Main step 5 | Configuration saves only when no Confirmed conflict exists | Rejection precedes persistence at `ClinicConfigurationService.java:122`; clean and hold-only paths persist exact rows | STRONG | yes |
| Main step 6 | Conflicting holds are removed and requests become reasoned staff work | Exact appointment deletion, request state/reason, affected-request links, and real HTTP rendering at `ClinicConfigurationServiceTests.java:112` and `ClinicConfigurationE2ETests.java:79` | STRONG | yes |
| Main step 7 | Matching, validation, calendar, and AI context consume saved values | Cross-consumer assertions at `ClinicConfigurationServiceTests.java:139`; `PromptBuilder.java:123` derives weekday-specific day parts from the same saved opening-hour rows | STRONG | yes |
| Extension 3a | Invalid values explain the problem and save nothing | Localized response plus complete/no-row-change assertions at `ClinicConfigurationWebTests.java:90` and `ClinicConfigurationServiceTests.java:198` | STRONG | yes |
| Extension 4a | Confirmed conflicts reject the whole change, list all, instruct, and release no holds | Exact rendered links/instruction and snapshot preservation at `ClinicConfigurationWebTests.java:112` and `ClinicConfigurationServiceTests.java:62` | STRONG | yes |
| Extension 5a | Clean save reports no affected requests | Exact rendered notice and persisted values at `ClinicConfigurationWebTests.java:47` | STRONG | yes |
| G1 | Closed weekdays, split shifts, exceptions, leave, and closures alter the correct availability | Exact interval calculation at `EffectiveAvailabilityCalculatorTests.java:21` plus conflict cases for all five schedule forms | STRONG | yes |
| G2 | Day parts derive from each day's opening and closing times | `PromptBuilder.java:123` calculates each weekday with fixed 12:00/17:00 boundaries and explicit `closed` intervals; assertions cover changed Thursday, shorter Friday, and closed Saturday at `ClinicConfigurationServiceTests.java:177` and all seed weekdays at `InterpreterContractTests.java:88`; independent UI/form/service values no longer exist | STRONG | yes |
| G3 | Saved configuration cannot invalidate Confirmed care | All five proposed restrictions return every confirmed conflict, preserve complete snapshots, and retain holds at `ClinicConfigurationServiceTests.java:62` | STRONG | yes |
| G4 | Holds never block a valid change and become actionable Needs staff work | Two holds are deleted atomically and both requests become `WITH_STAFF/SCHEDULE_CHANGED` at `ClinicConfigurationServiceTests.java:112` | STRONG | yes |
| G5 | One saved clinic zone determines today's local date and preserves local values | Dynamic zone clock crosses opposite local dates at one instant in `ClinicZoneClockTests.java:19`; local-field persistence and DST regressions passed | STRONG | yes |
| G6 | Normative defaults and passwords are exact | `SeedMigrationTests` passed field-by-field set equality and every configured BCrypt password check in the full run | STRONG | yes |
| G7 | Fixed exception fixtures have only horizon-local effects | Exact seeded exceptions and time-dependent matching regressions passed without fixture changes | STRONG | yes |
| G8 | Presentation and messages retain UC-1 guarantees | Shared layout, role navigation, identity/logout, CSRF, no-inline-style, and eleven-bundle parity tests passed | STRONG | yes |
| Success postcondition | Valid configuration is current and every invalidated hold is a reasoned hand-off | Real-server closure journey and cross-consumer test prove persisted values, derived day parts, and hold hand-off | STRONG | yes |
| Minimal guarantee | Invalid or confirmed-conflicting input causes no partial save or hold change | Complete before/after snapshots, retained request states, and rendered negative results at service and MVC boundaries | STRONG | yes |
| Requires UC-1 | Approved staff authentication/authorization is consumed | Seeded real form login, full route matrix, owner 403, anonymous redirect, CSRF, identity, and logout regressions passed | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | MUST separate responsibilities; controllers MUST NOT decide lifecycle or matching | The controller delegates to the transactional configuration service; shared calculation and lifecycle services retain decisions | PASS |
| RULE-2 | MUST transact each mutation atomically and use read-only query transactions | `change` is one transaction; `current` and `veterinarians` are read-only; refusal snapshots prove no partial mutation | PASS |
| RULE-3 | MUST keep deterministic feasibility independent of Spring and persistence | `EffectiveAvailabilityCalculator` remains framework-free and exact interval tests pass | PASS |
| RULE-4 | MUST enforce exact request transitions below MVC with no effects on refusal | Configuration invalidation consumes the existing `SCHEDULE_CHANGED` transition; lifecycle matrix and snapshot regressions pass | PASS |
| RULE-5 | MUST use one appointment aggregate and delete released holds | The existing release path deletes held appointments; exact absence and lifecycle regressions pass | PASS |
| RULE-6 | MUST use the declared locks, overlap recheck, uniqueness, and optimistic request version | Configuration change locks all veterinarians before conflict scanning; concurrency and stale-version regressions pass | PASS |
| RULE-7 | MUST use Flyway and lossless local date/time persistence | No schema bypass was introduced; migration, round-trip, restart, and DST tests pass | PASS |
| RULE-8 | MUST preserve every exact normative seed and BCrypt password | `SeedMigrationTests` passed all set/value comparisons; retained day-part columns remain at their normative values | PASS |
| RULE-9 | MUST use one complete role/CSRF security chain | `/staff/settings` is inventoried; anonymous, owner, staff, mutation, and CSRF checks pass | PASS |
| RULE-10 | MUST derive `/my/**` scope from the principal | UC-7 adds no owner id or `/my/**` operation; principal-scope regressions pass | PASS |
| RULE-15 | MUST obtain scheduling time only through injected `Clock` | Conflict cut-off and transitions use the dynamic clinic-zone clock; no new unclocked `now()` was introduced | PASS |
| RULE-16 | MUST reuse PetClinic layout/navigation/forms with permitted role/state actions | Settings uses `fragments/layout`, standard components, staff navigation, and no inline style; human presentation walkthrough is pending | PASS |
| RULE-17 | MUST localize all visible text through keys in eleven bundles | Source/template scans and exact bundle-key parity pass for the new derivation notice | PASS |
| RULE-18 | MUST provide a real-server journey and complete evidence without repository residue | Real-server journey, 16 focused tests, 369 full tests, clean tree, and unchanged runtime H2 pass | PASS |
| RULE-20 | MUST use one effective-availability function across four consumers | Matching, staff validation, and calendar delegate through `AvailabilityService`; conflict detection calls the same pure calculator | PASS |
| RULE-23 | MUST atomically handle invalid, Confirmed-conflict, Held-conflict, and clean changes | Full snapshots and exact state assertions cover every branch across all configuration forms | PASS |
| RULE-24 | MUST retain Maven/H2-only support and isolated runtime/test data | Repository inventory, in-memory tests, full Maven suite, and unchanged file-backed H2 pass | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required staff authentication, authorization, shared layout, localization, and seeds | Full 369-test suite | PASS |
| UC-2 | Owner appointment visibility and shared configuration disclosure | Full owner surface/history regressions | PASS |
| UC-3 | AI clinic context, matching, effective availability, clock, and holds | Prompt/interpreter/matching/lifecycle/real-server regressions plus exact derived context | PASS |
| UC-4 | Staff validation, request transitions, and hold release | Full staff-request and lifecycle regressions | PASS |
| UC-5 | Calendar rendering, effective availability, confirmed conflicts, and appointment lifecycle | Full calendar/concurrency/lifecycle regressions | PASS |
| UC-6 | Owner view of appointment outcomes | Full owner activity and real-server regressions | PASS |

## Findings

None. C-1 is resolved: opening hours are now the sole mutable source of named day parts, each weekday is derived independently, empty intervals are explicit, and value-level coverage includes different opening patterns and closed days.

## Walkthrough

1. Sign in as `staff` and open **Clinic settings**.
2. Confirm the page shows scheduling limits, all weekday opening hours, veterinarian working blocks/exceptions/leave, closures, identity, and logout.
3. Under **Named parts of day**, confirm the fixed derivation rule is shown and there are no editable morning, afternoon, or evening time fields.
4. Change a valid value that does not conflict with confirmed care, save, and confirm the success result and the no-affected-requests message.
5. Enter a working-block time without the required leading zero, submit, and confirm the `HH:mm` explanation appears and the previously saved configuration is unchanged.
6. Confirm the page remains in the established PetClinic layout with no missing or unlocalized labels. The Confirmed-conflict and Held-conflict branches require prepared appointment state and were reproduced through the automated production-boundary tests above.

User result: pending.

## Status Update

`READY_FOR_CONVERGENCE` -> `PENDING_WALKTHROUGH`; UC-8 remains ineligible until the walkthrough passes and UC-7 is approved.

## Response to execute

PENDING WALKTHROUGH: complete the UC-7 staff configuration script and report PASS or findings.
