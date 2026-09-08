# Convergence: UC-8 - Maintain established clinic records and walk-in visits

## Summary

- Submission: `spec/checkpoints/UC-8.md` at `2df715cabe8eb2ae3868c6dba640c923f8abc186`
- Verdict: PENDING WALKTHROUGH
- Findings: 0 critical, 0 gap, 0 protocol, 0 drift, 0 cosmetic
- Suite: 38 focused tests and 373 full-suite tests passed with 0 failures, 0 errors, and 0 skipped
- Working tree impact from verification: none; the tracked runtime database remained 126976 bytes with SHA-256 `6c80213d262edc022bdc86145788363da080cc2d03dd828717806410f7b66570`

## Protocol Gate

1. Exactly UC-8 was submitted, and `spec/status.md` named it `READY_FOR_CONVERGENCE` at the start of this audit.
2. The implementation, tests, checkpoint, and status are committed together at immutable submission `2df715cabe8eb2ae3868c6dba640c923f8abc186`, based on `b6ba23d4fef01d5c17580f3f14726430e5d8ef64`.
3. UC-8's only dependency, UC-1, is `APPROVED`; UC-2 through UC-7 are also approved.
4. No other use case was `IN_PROGRESS` or `READY_FOR_CONVERGENCE`, and the worktree was clean before verification.
5. The checkpoint contains evidence for all six main steps, every extension and guarantee, both postconditions, the Requires relation, every applicable rule, validation commands, changed files, and approved-UC regressions.
6. Inspection of all 12 submission files found only UC-8 clinic-record transaction boundaries, duplicate handling, walk-in separation, tests, and evidence. No unrelated or later-use-case behavior is present.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Staff | Main steps 1-4 | Find and display an owner, then create/edit owners and pets | Independent real-server tests passed seeded staff login, owner search/detail, owner create/edit, pet create/edit, redirects, exact rendered values, and database rows at `ClinicRecordsE2ETests.java:57`. |
| Staff | Main steps 5-6 | Open the veterinarian directory and see exact specialties | Independent real-server tests fetched both pages and matched all six veterinarians and their exact specialty associations at `ClinicRecordsE2ETests.java:94`. |
| Staff | Extensions 1a and 3a | No-match and invalid data explain the problem and save nothing | Responses contained the expected localized validation, omitted unrelated owners, and preserved complete owner/pet/visit/appointment snapshots at `ClinicRecordsE2ETests.java:106`. |
| Staff | Extension 3b and G2 | Same-owner duplicates lose, including concurrently; different owners may reuse a name | Sequential case-insensitive duplicate and two-thread HTTP races left one same-owner row and a validation response, while a different owner stored `LEO`, at `ClinicRecordsE2ETests.java:123` and `PetClinicConcurrencyTests.java:45`. |
| Staff | Extensions 2a and 2b | Walk-ins remain unlinked; scheduled completion remains linked and appointment-dated | Independent HTTP journeys asserted the exact visit fields, null versus populated `appointment_id`, appointment date, and `COMPLETED` status at `ClinicRecordsE2ETests.java:142`. |
| Owner | G4 | Established clinic-management pages deny reads and mutations | Seeded owner login received 403 across owner, pet, vet, and visit routes, and a full database snapshot remained unchanged at `ClinicRecordsE2ETests.java:176`. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| Main step 1 | Staff find or select an owner | Real staff form login and `Franklin` search redirected to owner 1 at `ClinicRecordsE2ETests.java:57` | STRONG | yes |
| Main step 2 | Owner contact, pets, and visit history are shown | The resulting real page contained George Franklin, exact address, Leo, and the seeded visit date at `ClinicRecordsE2ETests.java:64`; stock page/controller regressions passed | STRONG | yes |
| Main step 3 | Staff create or edit an owner or pet with valid values | Real CSRF forms created and edited both entities with exact submitted values at `ClinicRecordsE2ETests.java:67` | STRONG | yes |
| Main step 4 | Records save and return to owner detail | Each POST redirected to owner detail; re-GETs and SQL asserted the stored owner/pet values at `ClinicRecordsE2ETests.java:70` | STRONG | yes |
| Main step 5 | Staff open the veterinarian directory | The authenticated real client fetched `/vets.html?page=1` and `page=2` at `ClinicRecordsE2ETests.java:94` | STRONG | yes |
| Main step 6 | Veterinarians and specialties are listed | Assertions match all six exact names, `radiology`, `dentistry`, `surgery`, and `none` across both pages | STRONG | yes |
| Extension 1a | No owner match shows validation without unrelated disclosure | The response was 200, contained `has not been found`, excluded George and Betty, and preserved the full snapshot at `ClinicRecordsE2ETests.java:106` | STRONG | yes |
| Extension 3a | Invalid owner or pet values show field validation and save nothing | Invalid real POSTs preserved all clinic-record tables; controller tests prove no application-service invocation | STRONG | yes |
| Extension 3b | Same-owner duplicate, including concurrent save, reports duplicate and stores no duplicate | Case-insensitive duplicate preserved the full snapshot; the concurrent real-server race asserted one redirect, one localized validation response, no unexpected failure, and exactly one row | STRONG | yes |
| Extension 2a | Add visit saves date/description without appointment link and displays history | Real Add visit stored exact pet, date, description, and null link, then rendered date/description on owner detail at `ClinicRecordsE2ETests.java:145` | STRONG | yes |
| Extension 2b | Scheduled completion follows UC-5 and creates no unrelated walk-in | Real staff completion stored one visit linked to the appointment, dated it from the appointment, and set `COMPLETED` at `ClinicRecordsE2ETests.java:158` | STRONG | yes |
| G1 | Established owner, pet, vet, and walk-in behavior remains available to staff | Main and walk-in real-server journeys plus existing stock-controller tests exercise each named operation | STRONG | yes |
| G2 | Pet name is owner-local unique and reusable across owners | H2 `VARCHAR_IGNORECASE` owner/name unique constraint, sequential/raced same-owner tests, and different-owner `LEO` persistence prove both halves | STRONG | yes |
| G3 | Walk-in links are null; completion links and dates are retained | Exact four-field SQL comparisons cover both visit origins; stock seed tests assert every walk-in remains unlinked | STRONG | yes |
| G4 | Owners cannot read or mutate clinic-management records | Real owner session received 403 for GET and CSRF POST routes, disclosed no target content, and preserved the full snapshot | STRONG | yes |
| G5 | Presentation and messages satisfy UC-1 G5-G7 | Shared-layout, role-menu, identity/logout, CSRF, no-inline-style, visible-string scan, and exact eleven-bundle parity tests all passed; browser presentation walkthrough remains pending | STRONG | yes |
| Success postcondition | Valid record or walk-in is stored and visible through staff workflow | Real journeys re-read each created/edited record and visit through owner detail with exact values | STRONG | yes |
| Minimal guarantee | Invalid, duplicate, or unauthorized changes do not alter records | Whole-database snapshots plus concurrent one-winner counts prove no prohibited mutation | STRONG | yes |
| Requires UC-1 | Staff-only workflows consume approved authentication | Seeded staff and owner form-login sessions, full security matrix, CSRF, identity, and logout regressions passed | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | Controllers MUST NOT contain lifecycle or matching decisions | Owner, pet, and visit controllers delegate mutations to `ClinicRecordService`; duplicate persistence handling and walk-in link clearing are below MVC | PASS |
| RULE-2 | Each mutation MUST run in one transaction | `ClinicRecordService.java:21`, `:26`, `:35`, and `:50` annotate each owner, pet, and visit action with `@Transactional`; snapshot tests prove atomic refusal | PASS |
| RULE-5 | Appointment lifecycle MUST retain its declared states and refuse unlisted transitions | Extension 2b uses the approved `AppointmentService.complete` path; complete lifecycle matrix and linked-visit tests passed | PASS |
| RULE-7 | Flyway MUST own schema/data and local date/time values MUST round-trip | No schema bypass was introduced; all four migrations, seed fidelity, visit dates, links, restart, and round-trip tests passed | PASS |
| RULE-8 | Exact normative seed sets and BCrypt passwords MUST remain unchanged | Full `SeedMigrationTests` value/set/password suite passed, including all vets, specialties, owners, pets, and unlinked stock visits | PASS |
| RULE-9 | One security chain MUST enforce the complete role/CSRF surface | Full route inventory and anonymous/owner/staff matrix passed; UC-8 real owner checks prove denial and no mutation | PASS |
| RULE-16 | Pages MUST reuse the PetClinic shell and permitted role actions | All established templates retain the shared layout/forms/styles; presentation tests passed and Playwright walkthrough is pending | PASS |
| RULE-17 | Visible text MUST resolve from keys in all eleven bundles | No new visible template text was added; existing duplicate/flash keys pass source scans and exact bundle-key parity | PASS |
| RULE-18 | A real-server journey and direct evidence MUST cover the whole use case without repository residue | Four UC-8 HTTP journeys, one concurrent HTTP race, 38 focused tests, 373 full tests, clean tree, and unchanged runtime H2 satisfy the automated boundary | PASS |
| RULE-24 | Runtime support MUST remain Maven/H2-only and tests MUST be isolated | Repository inventory passed; all tests used in-memory H2 and the file-backed runtime H2 remained byte-for-byte unchanged | PASS |
| RULE-25 | Completion MUST create one linked appointment-dated visit and stock walk-ins MUST remain unlinked | UC-5 lifecycle tests plus UC-8's exact linked/unlinked HTTP journey and stock seed assertions passed | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required authentication, route security, shared presentation, localization, and seeds | Full 373-test suite plus UC-8 staff/owner real-login journeys | PASS |
| UC-2 | Shared owner/pet/visit records and owner read-only workspace | Full owner activity, isolation, and history regressions | PASS |
| UC-3 | Shared pet records, appointments, and request lifecycle | Full guided-scheduling, interpretation, matching, concurrency, and real-server regressions | PASS |
| UC-4 | Shared staff identity, pet records, and booking path | Full staff queue, authoring, validation, and concurrency regressions | PASS |
| UC-5 | Included completion behavior and linked visits | Appointment state matrix, calendar/detail, exact linked visit, no-show, and real-server regressions | PASS |
| UC-6 | Shared owner-visible appointment history | Owner cancellation, final-state, isolation, and real-server regressions | PASS |
| UC-7 | Shared staff navigation, configuration, veterinarian data, and runtime persistence | Configuration, seed, clock, presentation, and real-server regressions | PASS |

## Findings

None.

## Walkthrough

Automated evidence passed. Perform the following final staff presentation walkthrough with Playwright against an isolated migrated H2 database:

1. Sign in as `staff`; confirm the shared PetClinic shell shows `staff`, logout, **Find owners**, and **Veterinarians**.
2. Search for `Franklin`; confirm George Franklin's contact details, Leo, and visit history render without a second visual system.
3. Create an owner, edit that owner, add a pet, edit the pet, and confirm every successful action returns to owner detail with the saved values.
4. Submit an invalid pet and then a duplicate pet name for the same owner; confirm localized validation and no duplicate row appears.
5. Add a future walk-in visit and confirm its date and description appear in pet history.
6. Open **Veterinarians** and confirm names and specialties render across both pages.
7. Search for a missing owner and confirm the established not-found validation without an unrelated owner result.
8. Sign out, sign in as `george`, and confirm direct navigation to `/owners/find`, `/vets.html`, and the Add visit route is denied.

User result: pending Playwright execution.

## Status Update

`READY_FOR_CONVERGENCE` -> `PENDING_WALKTHROUGH`; no next use case is eligible because UC-8 is the final use case and still awaits the presentation gate.

## Response to execute

PENDING WALKTHROUGH: run the recorded UC-8 staff and owner presentation script with Playwright.
