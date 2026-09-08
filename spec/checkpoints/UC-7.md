# Use-Case Checkpoint: UC-7 - Maintain clinic scheduling configuration

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `4ecf34f`
- Submission commit: HEAD at convergence
- Revision: C-1 derives named day parts from each weekday's opening hours and removes independent day-part mutation
- Relations verified: Requires UC-1 through seeded staff form login, staff-only route authorization, CSRF, and the shared authenticated PetClinic shell

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-3 | Every current configuration type renders and posts at `ClinicConfigurationWebTests.java:47`; real staff login and HTTP form submission run at `ClinicConfigurationE2ETests.java:63` | PASS |
| Main steps 4-6 | Protected appointments are checked under veterinarian locks at `ClinicConfigurationService.java:109`; confirmed conflicts reject before persistence, while hold-only conflicts save, delete holds, and route requests at `ClinicConfigurationServiceTests.java:62` and `ClinicConfigurationServiceTests.java:112` | PASS |
| Main step 7 | Saved settings drive AI clinic context, matching, staff validation, calendar rendering, and persisted suggestions at `ClinicConfigurationServiceTests.java:139`; weekday-specific day parts are derived at `PromptBuilder.java:123`; a saved closure renders through real HTTP at `ClinicConfigurationE2ETests.java:91` | PASS |
| Extension 3a | Malformed exact-format input, reversed hours, invalid duration bounds, and overlapping working blocks produce localized errors and unchanged snapshots at `ClinicConfigurationWebTests.java:90` and `ClinicConfigurationServiceTests.java:198` | PASS |
| Extension 4a | Both conflicting confirmed appointments are listed, the complete proposed change is rejected, and the held appointment/request remain unchanged at `ClinicConfigurationWebTests.java:112` and `ClinicConfigurationServiceTests.java:62` | PASS |
| Extension 5a | A clean change saves and reports no affected requests at `ClinicConfigurationWebTests.java:47` and `ClinicConfigurationServiceTests.java:139` | PASS |
| G1-G2 | Split shifts are intersected with clinic hours and suppressed by exceptions, leave, or closure in `EffectiveAvailabilityCalculatorTests.java:21`; `PromptBuilder.java:123` derives fixed-boundary parts for each weekday and emits `closed` for empty intervals. Value assertions cover changed Thursday, differently opened Friday, and closed Saturday at `ClinicConfigurationServiceTests.java:177`, while `ClinicConfigurationWebTests.java:53` proves staff cannot submit independent day-part values | PASS |
| G3-G4 | All confirmed conflicts block the whole transaction; hold-only conflicts atomically delete every hold and transition every request to `WITH_STAFF/SCHEDULE_CHANGED` at `ClinicConfigurationServiceTests.java:62` and `ClinicConfigurationServiceTests.java:112` | PASS |
| G5 | The runtime clock reads the saved zone dynamically, retains one instant, and yields the correct local date on both sides of midnight at `ClinicZoneClockTests.java:19` | PASS |
| G6-G7 | Exact migration seeds, BCrypt passwords, and fixed exception fixtures remain unchanged and pass the full regression suite | PASS |
| G8 | Shared-layout rendering, role actions, CSRF, no inline style, complete localization across eleven bundles, and full handler security inventory pass | PASS |
| Success postcondition | The real-server journey saves configuration, deletes the conflicting hold, records the reasoned staff hand-off, and observes the new closure in the calendar at `ClinicConfigurationE2ETests.java:63` | PASS |
| Minimal guarantee | Invalid and confirmed-conflict paths compare database snapshots and prove no partial configuration, request, appointment, or hold change | PASS |
| Requires UC-1 | Seeded staff login and full anonymous/owner/staff route checks use the approved UC-1 production path | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1, RULE-2 | `ClinicConfigurationController` delegates reads and the one transactional mutation to `ClinicConfigurationService`; the controller contains no lifecycle or conflict policy | PASS |
| RULE-3, RULE-20 | Framework-free `EffectiveAvailabilityCalculator` is the one calculation used through `AvailabilityService` by matching, validation, and calendar rendering, and directly by configuration conflict detection | PASS |
| RULE-4, RULE-5, RULE-6 | The established request lifecycle performs `SCHEDULE_CHANGED`, held appointments are deleted, all veterinarians are pessimistically locked before conflict scanning, and lifecycle/concurrency regressions pass | PASS |
| RULE-7, RULE-8 | Existing Flyway local date/time schema stores the configuration; exact seed and password tests pass without migration changes | PASS |
| RULE-9, RULE-10 | The single security chain protects `/staff/settings`; handler inventory, CSRF, staff access, owner 403, anonymous redirect, and existing principal-scope tests pass | PASS |
| RULE-15 | Conflict timing and transitions use the injected dynamic clinic-zone `Clock` | PASS |
| RULE-16, RULE-17 | The page uses the established layout and message keys present in all eleven bundles, with presentation and key-parity tests | PASS |
| RULE-18 | One real-server staff journey plus direct extension/guarantee tests and a clean 369-test full suite provide boundary evidence | PASS |
| RULE-23 | Whole-database comparisons cover invalid, confirmed-conflict, hold-conflict, and clean changes across every schedule type | PASS |
| RULE-24 | Maven/H2-only regression suite passes and the tracked runtime database remains unchanged | PASS |

## Validation

- Focused UC-7 C-1 revision: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -Dtest=ClinicConfigurationServiceTests,ClinicConfigurationWebTests,ClinicConfigurationE2ETests,InterpreterContractTests test` passed 16 tests with 0 failures, 0 errors, and 0 skipped.
- Full relevant suite: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true test` passed 369 tests with 0 failures, 0 errors, and 0 skipped.
- Formatting and hygiene: `spring-javaformat:validate` and `git diff --check` passed.
- Working tree impact from tests: none.
- Runtime database: unchanged at 126976 bytes with SHA-256 `6c80213d262edc022bdc86145788363da080cc2d03dd828717806410f7b66570`.
- Changed files: configuration record/form/service/settings model; prompt context builder; settings template; eleven locale bundles; configuration service/MVC/real-HTTP tests; interpreter contract test; status and this checkpoint.
- Approved UCs regression-tested: UC-1 through UC-6 in the 369-test suite.

## Notes

C-1 is addressed by making opening hours the sole mutable source for named day parts. The existing seeded day-part columns remain unchanged for normative migration compatibility but are no longer accepted through the form/service or consumed by AI context. Automated UI evidence is complete; the staff walkthrough remains for convergence.

READY FOR CONVERGENCE: UC-7
