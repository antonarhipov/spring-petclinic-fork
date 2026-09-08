# Convergence: UC-8 - Maintain established clinic records and walk-in visits

## Summary

- Submission: `spec/checkpoints/UC-8.md` at `2df715cabe8eb2ae3868c6dba640c923f8abc186`
- Verdict: REJECT
- Findings: 1 critical, 0 gap, 0 protocol, 0 drift, 0 cosmetic
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
| Staff | Main steps 1-4 | Find and display an owner, then create/edit owners and pets | Independent real-server tests passed, but Playwright exposed that their client manually copies the logout form's CSRF token into mutation requests. The rendered Add Owner form submitted no `_csrf` field and received 403, so browser steps 3-4 are not reachable. |
| Staff | Main steps 5-6 | Open the veterinarian directory and see exact specialties | Independent real-server tests fetched both pages and matched all six veterinarians and their exact specialty associations at `ClinicRecordsE2ETests.java:94`. |
| Staff | Extensions 1a and 3a | No-match and invalid data explain the problem and save nothing | GET no-match behavior passed. Invalid browser POST evidence is invalidated by C-1 because the stock mutation form cannot submit its CSRF token. |
| Staff | Extension 3b and G2 | Same-owner duplicates lose, including concurrently; different owners may reuse a name | The manual-token clients prove service/database behavior, but the stock browser form cannot reach it because of C-1. |
| Staff | Extensions 2a and 2b | Walk-ins remain unlinked; scheduled completion remains linked and appointment-dated | Scheduled completion uses a separate working form. The stock Add visit browser path is blocked by the same missing-CSRF form defect as Add Owner. |
| Owner | G4 | Established clinic-management pages deny reads and mutations | Seeded owner login received 403 across owner, pet, vet, and visit routes, and a full database snapshot remained unchanged at `ClinicRecordsE2ETests.java:176`. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| Main step 1 | Staff find or select an owner | Real staff form login and `Franklin` search redirected to owner 1 at `ClinicRecordsE2ETests.java:57` | STRONG | yes |
| Main step 2 | Owner contact, pets, and visit history are shown | Playwright rendered George Franklin, exact contact data, Leo's attributes, and the visit-history table through the stock page; later walk-in evidence is blocked by C-1 | STRONG | yes |
| Main step 3 | Staff create or edit an owner or pet with valid values | `createOrUpdateOwnerForm.html:8` has only plain `method="post"`; Playwright request 52 contained owner fields but no `_csrf` and returned 403 | IMPOSSIBLE | no |
| Main step 4 | Records save and return to owner detail | The real browser cannot cross step 3; the executor's custom client injects a token sourced elsewhere on the page | IMPOSSIBLE | no |
| Main step 5 | Staff open the veterinarian directory | The authenticated real client fetched `/vets.html?page=1` and `page=2` at `ClinicRecordsE2ETests.java:94` | STRONG | yes |
| Main step 6 | Veterinarians and specialties are listed | Assertions match all six exact names, `radiology`, `dentistry`, `surgery`, and `none` across both pages | STRONG | yes |
| Extension 1a | No owner match shows validation without unrelated disclosure | The response was 200, contained `has not been found`, excluded George and Betty, and preserved the full snapshot at `ClinicRecordsE2ETests.java:106` | STRONG | yes |
| Extension 3a | Invalid owner or pet values show field validation and save nothing | Controller/service behavior passes only with test-injected CSRF; the rendered stock forms cannot submit an invalid request past security | IMPOSSIBLE | no |
| Extension 3b | Same-owner duplicate, including concurrent save, reports duplicate and stores no duplicate | Service/database concurrency is proven, but the actor's rendered pet form at `createOrUpdatePetForm.html:11` omits a processed action and therefore its own CSRF field | IMPOSSIBLE | no |
| Extension 2a | Add visit saves date/description without appointment link and displays history | Exact persistence is proven below a manually constructed request, but `createOrUpdateVisitForm.html:30` has the same browser submission defect | IMPOSSIBLE | no |
| Extension 2b | Scheduled completion follows UC-5 and creates no unrelated walk-in | Real staff completion stored one visit linked to the appointment, dated it from the appointment, and set `COMPLETED` at `ClinicRecordsE2ETests.java:158` | STRONG | yes |
| G1 | Established owner, pet, vet, and walk-in behavior remains available to staff | Search/detail/vet reads work, but browser owner, pet, and walk-in mutations are forbidden because their forms omit CSRF submission | IMPOSSIBLE | no |
| G2 | Pet name is owner-local unique and reusable across owners | H2 `VARCHAR_IGNORECASE` owner/name unique constraint, sequential/raced same-owner tests, and different-owner `LEO` persistence prove both halves | STRONG | yes |
| G3 | Walk-in links are null; completion links and dates are retained | Exact four-field SQL comparisons cover both visit origins; stock seed tests assert every walk-in remains unlinked | STRONG | yes |
| G4 | Owners cannot read or mutate clinic-management records | Real owner session received 403 for GET and CSRF POST routes, disclosed no target content, and preserved the full snapshot | STRONG | yes |
| G5 | Presentation and messages satisfy UC-1 G5-G7 | Shared-layout, role-menu, identity/logout, CSRF, no-inline-style, visible-string scan, and exact eleven-bundle parity tests all passed; browser presentation walkthrough remains pending | STRONG | yes |
| Success postcondition | Valid record or walk-in is stored and visible through staff workflow | The stock browser cannot submit a valid owner, pet, or walk-in mutation, so the postcondition is unreachable | IMPOSSIBLE | no |
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
| RULE-9 | One security chain MUST enforce the complete role/CSRF surface | Security correctly rejects the Playwright POST, but the UC-8 forms do not carry their generated CSRF parameter, making authorized mutations unusable | FAIL |
| RULE-16 | Pages MUST reuse the PetClinic shell and permitted role actions | All established templates retain the shared layout/forms/styles; presentation tests passed and Playwright walkthrough is pending | PASS |
| RULE-17 | Visible text MUST resolve from keys in all eleven bundles | No new visible template text was added; existing duplicate/flash keys pass source scans and exact bundle-key parity | PASS |
| RULE-18 | A real-server journey and direct evidence MUST cover the whole use case without repository residue | The custom HTTP client extracts the navbar logout token and injects it into target form POSTs, so it does not prove the rendered mutation forms work as submitted by a browser | FAIL |
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

### C-1 CRITICAL - Established mutation forms cannot submit CSRF tokens

- Contract: UC-8 main steps 3-4 require staff to create/edit an owner or pet and save it; extension 2a requires the established Add visit form; G1 requires those workflows to remain available. RULE-9 requires CSRF on mutations.
- Code evidence: `owners/createOrUpdateOwnerForm.html:8`, `pets/createOrUpdatePetForm.html:11`, and `pets/createOrUpdateVisitForm.html:30` declare plain POST forms without a Thymeleaf-processed action. Consequently Spring's request-data processor does not add each form's CSRF hidden input.
- Runtime evidence: Playwright request 52 posted `firstName=Playwright&lastName=Walker&address=8+Browser+Lane&city=Utrecht&telephone=0612345678` with no `_csrf` parameter and received 403 as authenticated `staff`.
- Test-evidence defect: `ClinicRecordsE2ETests.Browser.post` extracts the first `_csrf` anywhere in the page, which is supplied by the navbar logout form, and manually injects it into the target POST. It can therefore pass while the actor-visible form is broken.
- Revision outcome: make every owner, pet, and visit mutation form use a correct Thymeleaf `th:action` so its CSRF field is rendered; add DOM/real-browser-conformant assertions that the target form contains `_csrf`; rerun UC-8 and the final Playwright walkthrough.

## Walkthrough

The final staff presentation walkthrough ran with Playwright against an isolated migrated H2 database:

1. Sign in as `staff`; confirm the shared PetClinic shell shows `staff`, logout, **Find owners**, and **Veterinarians**.
2. Search for `Franklin`; confirm George Franklin's contact details, Leo, and visit history render without a second visual system.
3. Create an owner, edit that owner, add a pet, edit the pet, and confirm every successful action returns to owner detail with the saved values.
4. Submit an invalid pet and then a duplicate pet name for the same owner; confirm localized validation and no duplicate row appears.
5. Add a future walk-in visit and confirm its date and description appear in pet history.
6. Open **Veterinarians** and confirm names and specialties render across both pages.
7. Search for a missing owner and confirm the established not-found validation without an unrelated owner result.
8. Sign out, sign in as `george`, and confirm direct navigation to `/owners/find`, `/vets.html`, and the Add visit route is denied.

Playwright result: FAIL at step 3. Staff login, navigation, Franklin search, and George's details rendered correctly. The Add Owner form submitted without `_csrf` and received 403. Steps 4-8 were not continued because all three established mutation templates share the defect.

## Status Update

`PENDING_WALKTHROUGH` -> `NEEDS_REVISION`; no next use case is eligible because UC-8 is the final use case and C-1 blocks its actor-visible mutations.

## Response to execute

REVISE UC-8: C-1 requires owner, pet, and Add visit forms to render and submit their own CSRF field.
