# Convergence: UC-8 - Maintain established clinic records and walk-in visits

## Summary

- Submission: `spec/checkpoints/UC-8.md` revision at `35fecaa`
- Verdict: APPROVE WITH NOTES
- Findings: 0 critical, 0 gap, 0 protocol, 0 drift, 1 cosmetic
- Suite: 38 focused tests and 373 full-suite tests passed with 0 failures, 0 errors, and 0 skipped
- Working tree impact from verification: none; the tracked runtime database remained 126976 bytes with SHA-256 `6c80213d262edc022bdc86145788363da080cc2d03dd828717806410f7b66570`

## Protocol Gate

1. Exactly UC-8 was submitted, and `spec/status.md` named it `READY_FOR_CONVERGENCE` at the start of this audit.
2. The C-1 implementation, tests, checkpoint, and status are committed together at immutable revision submission `35fecaa`, based on rejection commit `2251a9c`.
3. UC-8's only dependency, UC-1, is `APPROVED`; UC-2 through UC-7 are also approved.
4. No other use case was `IN_PROGRESS` or `READY_FOR_CONVERGENCE`, and the worktree was clean before verification.
5. The checkpoint contains evidence for all six main steps, every extension and guarantee, both postconditions, the Requires relation, every applicable rule, validation commands, changed files, and approved-UC regressions.
6. Inspection of all eight revision files found only the three corrected form actions, target-form-only CSRF proof, MVC fixture IDs required to render those actions, and UC-8 evidence. No unrelated or later-use-case behavior is present.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Staff | Main steps 1-4 | Find and display an owner, then create/edit owners and pets | Independent real-server tests passed with CSRF extracted only from the submitted owner or pet form; a missing action, missing target form, or missing target-form token now fails the journey. |
| Staff | Main steps 5-6 | Open the veterinarian directory and see exact specialties | Independent real-server tests fetched both pages and matched all six veterinarians and their exact specialty associations at `ClinicRecordsE2ETests.java:94`. |
| Staff | Extensions 1a and 3a | No-match and invalid data explain the problem and save nothing | No-match and invalid owner/pet journeys passed; invalid POSTs use the rendered target form's CSRF token and preserve the complete clinic-record snapshot. |
| Staff | Extension 3b and G2 | Same-owner duplicates lose, including concurrently; different owners may reuse a name | Sequential duplicate submission through the corrected pet form and the concurrent real-server race passed with exact one-winner state; reuse by another owner remains proven. |
| Staff | Extensions 2a and 2b | Walk-ins remain unlinked; scheduled completion remains linked and appointment-dated | The corrected Add visit form supplied its own token and stored an unlinked walk-in; scheduled completion retained the exact linked, appointment-dated behavior. |
| Owner | G4 | Established clinic-management pages deny reads and mutations | Seeded owner login received 403 across owner, pet, vet, and visit routes, and a full database snapshot remained unchanged at `ClinicRecordsE2ETests.java:176`. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| Main step 1 | Staff find or select an owner | Real staff form login and `Franklin` search redirected to owner 1 at `ClinicRecordsE2ETests.java:57` | STRONG | yes |
| Main step 2 | Owner contact, pets, and visit history are shown | The real-server journey rendered George Franklin, exact contact data, Leo's attributes, and visit history through the established page; the corrected walk-in path also re-renders its new visit there | STRONG | yes |
| Main step 3 | Staff create or edit an owner or pet with valid values | Exact `th:action` values at `createOrUpdateOwnerForm.html:8` and `createOrUpdatePetForm.html:11` cause each target form to render its own CSRF input; the independent real-server journey submits only that token | STRONG | yes |
| Main step 4 | Records save and return to owner detail | The target-form-only journey receives redirects, follows them to owner detail, and asserts every submitted owner and pet value in both rendered output and database state | STRONG | yes |
| Main step 5 | Staff open the veterinarian directory | The authenticated real client fetched `/vets.html?page=1` and `page=2` at `ClinicRecordsE2ETests.java:94` | STRONG | yes |
| Main step 6 | Veterinarians and specialties are listed | Assertions match all six exact names, `radiology`, `dentistry`, `surgery`, and `none` across both pages | STRONG | yes |
| Extension 1a | No owner match shows validation without unrelated disclosure | The response was 200, contained `has not been found`, excluded George and Betty, and preserved the full snapshot at `ClinicRecordsE2ETests.java:106` | STRONG | yes |
| Extension 3a | Invalid owner or pet values show field validation and save nothing | Target-form CSRF POSTs render field validation, controller tests prove no mutation-service call, and real-server snapshots remain identical | STRONG | yes |
| Extension 3b | Same-owner duplicate, including concurrent save, reports duplicate and stores no duplicate | The corrected pet form reaches duplicate validation; sequential and two-thread real-server evidence proves no duplicate row and exactly one race winner | STRONG | yes |
| Extension 2a | Add visit saves date/description without appointment link and displays history | `createOrUpdateVisitForm.html:30` declares the exact action, the target-form token POST succeeds, and exact SQL/rendered checks prove the unlinked walk-in | STRONG | yes |
| Extension 2b | Scheduled completion follows UC-5 and creates no unrelated walk-in | Real staff completion stored one visit linked to the appointment, dated it from the appointment, and set `COMPLETED` at `ClinicRecordsE2ETests.java:158` | STRONG | yes |
| G1 | Established owner, pet, vet, and walk-in behavior remains available to staff | All read paths and every owner, pet, and walk-in mutation pass through real HTTP using only the corresponding rendered form token | STRONG | yes |
| G2 | Pet name is owner-local unique and reusable across owners | H2 `VARCHAR_IGNORECASE` owner/name unique constraint, sequential/raced same-owner tests, and different-owner `LEO` persistence prove both halves | STRONG | yes |
| G3 | Walk-in links are null; completion links and dates are retained | Exact four-field SQL comparisons cover both visit origins; stock seed tests assert every walk-in remains unlinked | STRONG | yes |
| G4 | Owners cannot read or mutate clinic-management records | Real owner session received 403 for GET and CSRF POST routes, disclosed no target content, and preserved the full snapshot | STRONG | yes |
| G5 | Presentation and messages satisfy UC-1 G5-G7 | Shared-layout, role-menu, identity/logout, CSRF, no-inline-style, visible-string scan, and exact eleven-bundle parity tests all passed; browser presentation walkthrough remains pending | STRONG | yes |
| Success postcondition | Valid record or walk-in is stored and visible through staff workflow | Corrected target forms submit successfully; redirects, rendered detail/history, and exact database rows prove each result | STRONG | yes |
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
| RULE-9 | One security chain MUST enforce the complete role/CSRF surface | Exact Thymeleaf actions at the owner, pet, and visit forms render their own CSRF inputs; the real-server journey resolves the token only inside the submitted form, while owner denials and no-mutation checks remain green | PASS |
| RULE-16 | Pages MUST reuse the PetClinic shell and permitted role actions | All established templates retain the shared layout/forms/styles; presentation tests passed and Playwright walkthrough is pending | PASS |
| RULE-17 | Visible text MUST resolve from keys in all eleven bundles | No new visible template text was added; existing duplicate/flash keys pass source scans and exact bundle-key parity | PASS |
| RULE-18 | A real-server journey and direct evidence MUST cover the whole use case without repository residue | The real-server helper matches the submitted POST action, searches only that form body for `_csrf`, and fails if either is absent; independent focused 38/38 and full 373/373 runs left no residue | PASS |
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

No blocking findings. Earlier C-1 is resolved by the exact form actions at `createOrUpdateOwnerForm.html:8`, `createOrUpdatePetForm.html:11`, and `createOrUpdateVisitForm.html:30`, plus target-form-only token extraction at `ClinicRecordsE2ETests.java:258` and `:291`.

### K-1 COSMETIC - Stock owner-detail message timer logs a console error

- Reference: UC-8 G5 carries UC-1 G5-G7 presentation behavior into the established owner-detail workflow.
- Evidence: the pre-existing script at `ownerDetails.html:84-85` dereferences both optional message elements without checking whether they exist. Playwright logged `TypeError: Cannot read properties of null (reading 'style')` after the timer ran.
- Impact: every UC-8 rendered result, form submission, validation response, visit history, and role denial remained correct. Git history places the script in the stock application before this feature, and UC-8 does not change that template.
- Note: guard each optional element before changing its display state in a separate stock-template cleanup.

## Walkthrough

The final staff presentation walkthrough ran in headed Chromium through Playwright CLI session `uc8-final` against port 18080 and a fresh `jdbc:h2:mem:uc8walkthrough` database:

1. Sign in as `staff`; confirm the shared PetClinic shell shows `staff`, logout, **Find owners**, and **Veterinarians**.
2. Search for `Franklin`; confirm George Franklin's contact details, Leo, and visit history render without a second visual system.
3. Create an owner, edit that owner, add a pet, edit the pet, and confirm every successful action returns to owner detail with the saved values.
4. Submit an invalid pet and then a duplicate pet name for the same owner; confirm localized validation and no duplicate row appears.
5. Add a future walk-in visit and confirm its date and description appear in pet history.
6. Open **Veterinarians** and confirm names and specialties render across both pages.
7. Search for a missing owner and confirm the established not-found validation without an unrelated owner result.
8. Sign out, sign in as `george`, and confirm direct navigation to `/owners/find`, `/vets.html`, and the Add visit route is denied.

Playwright result: PASS.

- Staff login landed on Scheduling queue with the exact staff navigation, `staff` identity, and Logout action.
- Franklin search opened George Franklin and displayed exact contact data, Leo, and visit history in the shared PetClinic layout.
- The corrected browser forms created owner 11, edited its address and city, created pet 14, edited its name/date/type, and returned to owner detail with every saved value.
- Blank pet submission showed required-field messages. Case-insensitive duplicate `pixelprime` showed `is already in use`, and owner detail still contained only `PixelPrime`.
- Add Visit stored and displayed `2026-09-15` and `Walk-in vaccination` for pet 14.
- Veterinarian pages displayed James Carter, Helen Leary/radiology, Linda Douglas/dentistry and surgery, Rafael Ortega/surgery, Henry Stevens/radiology, and Sharon Jenkins.
- Missing-owner search displayed `has not been found` and no owner result.
- After logout and owner login as `george`, `/owners/find`, `/vets.html`, and `/owners/1/pets/1/visits/new` each returned HTTP 403 with only the owner navigation visible.

## Status Update

`PENDING_WALKTHROUGH` -> `APPROVED`; no next use case is eligible because UC-8 is the final use case and the feature is complete.

## Response to execute

APPROVED WITH NOTES: K-1 records a pre-existing stock owner-detail console error that did not affect any UC-8 outcome.
