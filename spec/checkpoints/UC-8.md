# Use-Case Checkpoint: UC-8 - Maintain established clinic records and walk-in visits

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `b6ba23d4fef01d5c17580f3f14726430e5d8ef64`
- Submission commit: HEAD at convergence
- Relations verified: Requires UC-1 through seeded staff form login, staff-only established routes, CSRF, and owner-denial checks

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-4 | A real staff HTTP journey searches and displays George Franklin, then creates/edits an owner and creates/edits a pet with exact rendered and database values at `ClinicRecordsE2ETests.java:57` | PASS |
| Main steps 5-6 | The same journey reads both veterinarian pages and asserts all six veterinarian names and exact specialties at `ClinicRecordsE2ETests.java:94` | PASS |
| Extension 1a | A no-match search renders the established not-found validation, omits unrelated owners, and preserves a full clinic-record snapshot at `ClinicRecordsE2ETests.java:106` | PASS |
| Extension 3a | Invalid owner and pet submissions render field validation, call no mutation service in MVC tests, and preserve owners, pets, visits, and appointments at `ClinicRecordsE2ETests.java:111` | PASS |
| Extension 3b | A same-owner case-insensitive duplicate renders the duplicate error and saves nothing at `ClinicRecordsE2ETests.java:123`; two concurrent real HTTP saves produce exactly one redirect, one validation response, and one row at `PetClinicConcurrencyTests.java:45` | PASS |
| Extension 2a | Add visit saves the exact date and description with null `appointment_id`, then renders it in pet history at `ClinicRecordsE2ETests.java:145` | PASS |
| Extension 2b | The approved UC-5 completion route creates a linked visit dated from the appointment and changes it to `COMPLETED` at `ClinicRecordsE2ETests.java:158` | PASS |
| G1-G3 | Main, duplicate/reuse, walk-in, and completion journeys assert every named record, specialty, date, description, and appointment-link value | PASS |
| G4 | An owner receives 403 for established owner, pet, veterinarian, and visit reads and mutations; the complete snapshot remains unchanged at `ClinicRecordsE2ETests.java:176` | PASS |
| G5 | Shared layout, role navigation, identity/logout, form/CSRF behavior, no inline style, and eleven-bundle key parity pass in the full suite | PASS |
| Success postcondition | Valid owner, pet, and walk-in records are persisted and re-read through the established staff pages in real-server journeys | PASS |
| Minimal guarantee | No-match, invalid, duplicate, concurrent-loser, and unauthorized cases prove no prohibited mutation | PASS |
| Requires UC-1 | Seeded staff form login gates every success journey; seeded owner login receives 403 and cannot mutate clinic records | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1 | Controllers delegate owner, pet, and walk-in mutations to `ClinicRecordService`; persistence, duplicate handling, and link clearing remain below MVC | PASS |
| RULE-2 | Each mutation is one `@Transactional` service operation; refused paths preserve complete snapshots | PASS |
| RULE-5 | Scheduled completion runs through the approved appointment aggregate and lifecycle; all lifecycle regressions pass | PASS |
| RULE-7 | Existing Flyway schema stores local visit dates and appointment links; fresh migration and round-trip regressions pass | PASS |
| RULE-8 | Exact owners, pets, veterinarians, specialties, visits, accounts, and passwords remain unchanged and pass value-level seed tests | PASS |
| RULE-9 | Whole-route authorization, seeded form login, CSRF, role matrix, owner 403, and no-mutation checks pass | PASS |
| RULE-16 | Established pages retain the PetClinic layout, navigation, forms, stylesheet, identity/logout shell, and staff-only actions | PASS |
| RULE-17 | Validation and flash text use existing localized keys; hard-coded-string scans and exact eleven-bundle parity pass | PASS |
| RULE-18 | Four UC-8 real-server journeys and one concurrent real-server race cover every contract branch and prohibited side effect | PASS |
| RULE-24 | Maven/H2-only inventory passes; tests use in-memory H2 and the file-backed runtime database is unchanged | PASS |
| RULE-25 | Walk-ins explicitly clear `appointment_id`; scheduled completion creates exactly one linked, appointment-dated visit through UC-5 | PASS |

## Validation

- Focused command: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ClinicRecordsE2ETests,PetClinicConcurrencyTests,OwnerControllerTests,PetControllerTests,VisitControllerTests test` passed 38 tests with 0 failures, 0 errors, and 0 skipped.
- Full relevant suite: the same Java and agent configuration with `test` passed 373 tests with 0 failures, 0 errors, and 0 skipped.
- Formatting and hygiene: `spring-javaformat:validate` and `git diff --check` passed.
- Working tree impact from tests: none.
- Runtime database: unchanged at 126976 bytes with SHA-256 `6c80213d262edc022bdc86145788363da080cc2d03dd828717806410f7b66570`.
- Changed files: the owner, pet, and visit controllers; transactional clinic-record service and typed duplicate exception; controller, concurrency, and real-server tests; status and this checkpoint.
- Approved UCs regression-tested: UC-1 through UC-7 in the 373-test suite.

## Notes

The first focused attempts were environment-only failures before UC behavior ran: Java 21 required the established Byte Buddy agent, and the restricted sandbox refused localhost server binding. The successful focused command used the agent and permitted local random ports. No production change was needed.

Automated UI evidence is complete. The final staff walkthrough will be performed with Playwright after convergence.

READY FOR CONVERGENCE: UC-8
