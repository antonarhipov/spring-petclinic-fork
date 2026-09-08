# Convergence: UC-2 - Review owned pets and scheduling activity

## Summary

- Submission: `spec/checkpoints/UC-2.md` at `72f152ed4c11545020d14d0627ec9024f8b80d50`
- Verdict: APPROVE
- Findings: 0 critical, 0 gaps, 0 protocol, 0 drift, 0 cosmetic
- Suite: 325 run, 0 failed, 0 errors, 0 skipped
- Working tree impact from verification: none

## Protocol Gate

1. PASS - UC-2 is the only target and is `READY_FOR_CONVERGENCE`.
2. PASS - The revised checkpoint and implementation are committed together at `72f152ed4c11545020d14d0627ec9024f8b80d50`, based on the immutable rejection boundary `94b320eb56f02e13b135ac1a1d1cf5558d5f5a2f`.
3. PASS - UC-2 requires only UC-1, which is `APPROVED` at `0261b04` with its walkthrough passed.
4. PASS - UC-3 is `APPROVED`; UC-4 through UC-8 are `NOT_STARTED`; no other use case is active or ready.
5. PASS - The checkpoint has evidence for the main scenario, every extension and guarantee, both postconditions, the UC-1 relationship, every applicable rule, test commands, changed files, and approved-UC regressions.
6. PASS - The revision changes only the UC-2 cancellation boundary, presentation, focused and real-server evidence, and checkpoint/status artifacts. It contains no `.agents/`, unrelated, or later-UC change.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Owner `george` | Main steps 1-4 | Authenticated owner sees the complete read-only owner/pet view and owned scheduling activity. | Real form login, cookies, `GET /my/pets`, `GET /my/appointments`, owned request detail, exact rendered values, foreign-data exclusion, and unchanged read snapshots passed in `OwnerActivityE2ETests`. |
| Owner `george` | Main step 5 | Only valid start, resume, and cancellation actions are shown, and cancellation is consequential. | The rendered cancellation control is a POST form with a CSRF token. Submitting it returned 302, persisted `CANCELLED/OWNER/2026-09-07/09:00`, retained all unrelated rows, rendered the cancelled history row, and removed the action. |
| Owner `nopets` | Extension 2a | No-pet owner sees action-free empty states. | Real HTTP rendered both empty owner pages without pet or scheduling actions and retained zero pets. |
| Owner `george` | Extensions 4a-4b | No-activity and rescheduled branches render exact states. | Focused rendering showed only Start a request for a pet without activity and rendered the new date/time plus `Moved for emergency coverage`. |
| Owner `george` | Extension 1a | Foreign and unknown resources are indistinguishable and unchanged. | Foreign and unknown pet/request reads and cancellation POSTs returned equal normalized 404 pages, disclosed no foreign values, and left complete table snapshots unchanged. |
| Owner `george` | Forged or stale cancellation | Missing CSRF and state-invalid requests are refused. | Missing CSRF returned 403 unchanged; a started appointment returned the localized shared error page with 409 and an unchanged database snapshot. |

The first real-server command inside the restricted sandbox produced four context errors because dynamic localhost binding was denied before a server started. The identical command with localhost binding allowed passed 4/0/0/0; this is environment evidence, not a product finding.

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| UC-2 main step 1 | Owner opens My pets. | Real authenticated `GET /my/pets` at `OwnerActivityE2ETests.java:66-71`. | STRONG | yes |
| UC-2 main step 2 | Exact owner and pets render read-only. | Exact identity/contact/pet values and absence of create/edit actions at `OwnerActivityWebTests.java:44-53` and the real-server journey. | STRONG | yes |
| UC-2 main step 3 | Owner opens My appointments. | Real authenticated `GET /my/appointments` at `OwnerActivityE2ETests.java:73-80`. | STRONG | yes |
| UC-2 main step 4 | Every owned past/future appointment and active request renders. | Exact date, time, status, veterinarian, specialties, staff reason, request state/origin, request detail parity, foreign exclusion, and unchanged snapshots at `OwnerActivityWebTests.java:56-73`. | STRONG | yes |
| UC-2 main step 5 | Only state-valid start, resume, and cancel actions render and resolve. | Visibility matrix at `OwnerActivityWebTests.java:96-107`; CSRF POST and durable consequence at `OwnerActivityWebTests.java:110-134`; real form submission and action removal at `OwnerActivityE2ETests.java:88-117`. | STRONG | yes |
| UC-2 extension 2a | No-pet owner receives an action-free empty state. | Exact empty owner and activity pages plus persisted zero-pet check at `OwnerActivityWebTests.java:76-87` and `OwnerActivityE2ETests.java:119-131`. | STRONG | yes |
| UC-2 extension 4a | Pet without appointments or request gets the absence and new-request action. | Exact empty labels and sole Start action at `OwnerActivityWebTests.java:89-93`. | STRONG | yes |
| UC-2 extension 4b | Rescheduled appointment shows new time and staff reason. | Exact `2026-09-08`, `11:15-11:45`, and `Moved for emergency coverage` assertions in both main rendered journeys. | STRONG | yes |
| UC-2 extension 1a | Foreign and unknown pet, request, and appointment access is indistinguishable. | Equal normalized 404 bodies, absent foreign values, and complete unchanged snapshots at `OwnerActivityWebTests.java:149-177` and `OwnerActivityE2ETests.java:134-166`. | STRONG | yes |
| UC-2 G1 | No foreign owner activity is disclosed. | Principal-scoped repository queries, category-specific negative assertions, equal cancellation 404s, and complete snapshots. | STRONG | yes |
| UC-2 G2 | Veterinarian names and specialties render without directory access. | Exact veterinarian/specialty values and absent `/vets.html` at `OwnerActivityWebTests.java:59-70`. | STRONG | yes |
| UC-2 G3 | Owner and pet representation remains read-only. | Immutable query records and rendered absence of create/edit actions in the focused and real-server pages. | STRONG | yes |
| UC-2 G4 | Listing state and interpretation origin agree with request detail. | Both pages render the same request, state, AI origin, specialty, and veterinarian at `OwnerActivityWebTests.java:59-73`. | STRONG | yes |
| UC-2 G5 | Presentation and messages retain UC-1 G5-G7. | Shared-layout template, exact role controls, localization scanner, and eleven-bundle parity passed in the full suite. | STRONG | yes |
| UC-2 success postcondition | Owner has the complete role-scoped activity view. | Real owner journeys compare all expected owned rows, exclude foreign rows, and prove the displayed state-valid actions. | STRONG | yes |
| UC-2 minimal guarantee | Missing data is empty without disclosure or fabrication. | No-pet/no-activity pages and cross-owner/unknown snapshots prove the declared minimum. | STRONG | yes |
| UC-2 Requires UC-1 | Approved identity boundary scopes all reads and actions. | Every real journey consumes form login and principal mapping; UC-1 security, presentation, seed, and isolation regressions pass. | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | Scheduling responsibilities are domain-oriented and controllers contain no lifecycle decisions. | `MyAppointmentsController.java:34-38` resolves identity and delegates; `AppointmentService.java:115-130` owns lookup and lifecycle enforcement; `OwnerActivityQueryService.java:24-88` owns read assembly. | PASS |
| RULE-2 | State-changing actions are transactional and detached lazy query models use read-only transactions. | Owner cancellation is one `@Transactional` service operation at `AppointmentService.java:115-130`; `OwnerActivityQueryService.java:24-25` is read-only; refusal snapshots are unchanged. | PASS |
| RULE-4 | Request lifecycle transitions and refusals are enforced below MVC without side effects. | UC-2 cancellation changes no request state; started/foreign/unknown refusal snapshots are unchanged; the complete lifecycle matrix passes. | PASS |
| RULE-7 | Flyway exclusively owns the schema and local temporal values round-trip losslessly. | Migration, mapping, field-fidelity, and restart suites pass; cancellation stores the pinned local date and time exactly. | PASS |
| RULE-8 | Normative seed sets and BCrypt credentials are exact. | Fresh-database value and password verification pass in the full suite. | PASS |
| RULE-9 | One route chain enforces role access and CSRF on mutations. | The cancellation POST is in the whole-route inventory; anonymous and staff requests are denied unchanged; an owner POST without CSRF returns 403 unchanged; the CSRF form succeeds. | PASS |
| RULE-10 | Every `/my/**` action derives owner scope from the principal and uses equal foreign/unknown 404s. | `MyAppointmentsController.java:35-37` derives the owner; `AppointmentRepository.java:43-44` scopes the lookup; focused and real-server POSTs prove equal 404 bodies and no mutation. | PASS |
| RULE-16 | Pages use the shared PetClinic presentation and expose only state-valid role actions. | `templates/my/appointments.html:2-39` uses the shared layout; the cancellation form is conditional on the same clocked aggregate rule enforced in the service; rendered DOM tests pass. Walkthrough remains pending. | PASS |
| RULE-17 | All visible text resolves through keys present in all eleven bundles. | The error uses the shared localized error page; source/template scans and exact bundle-key parity pass. | PASS |
| RULE-18 | Real-server evidence covers the UC and tests leave runtime state unchanged. | Four real-server journeys plus focused negative evidence cover every contract row; full suite is 325/0/0/0; the tree is clean and `data/petclinic.mv.db` remains `2026-09-07T22:56:54+0200`. | PASS |
| RULE-24 | Runtime support remains Maven/H2-only with isolated tests. | Repository-scope and datasource-isolation tests pass; all convergence tests used in-memory H2 and did not modify the file-backed runtime database. | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required form login, owner scoping, route security, shared layout, localization, Flyway, and isolation. | The 325-test suite includes approved UC-1 focused and real-server evidence. | PASS |
| UC-3 | Shared request detail, request lifecycle, appointment aggregate, owner presentation, concurrency, and persistence. | The 325-test suite includes approved UC-3 focused and real-server evidence. | PASS |

## Findings

None. C-1 from the previous convergence report is resolved by the principal-scoped CSRF POST boundary and its success, refusal, ownership, and no-side-effect evidence.

## Walkthrough

Result: PASS confirmed by the user on 2026-09-08.

1. Sign in as `george` / `george123`. Confirm the shared PetClinic layout shows `george`, Logout, My pets, and My appointments, with no staff navigation.
2. Open My pets. Confirm George Franklin's owner details and Leo appear read-only, with no create or edit controls.
3. Open My appointments. For each pet, confirm past/upcoming appointment history and active-request status appear; veterinarian names and specialties are visible, but there is no veterinarian-directory link.
4. Confirm a pet without activity shows No appointments and No active request with Start a request; an active request shows Resume instead.
5. Confirm Cancel appointment appears only on a future Confirmed appointment. Submit it, then confirm the row remains as Cancelled in history and the Cancel action is gone.
6. Confirm an appointment carrying a staff reschedule reason shows its new local date/time and the reason.
7. Sign out. Confirm the owner pages return to the login boundary and no protected owner data remains visible.

## Status Update

`PENDING_WALKTHROUGH` -> `APPROVED`; UC-4, UC-5, UC-6, UC-7, and UC-8 are eligible.

## Response to execute

APPROVED
