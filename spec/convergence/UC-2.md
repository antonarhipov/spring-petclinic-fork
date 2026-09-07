# Convergence: UC-2 - Review owned pets and scheduling activity

## Summary

- Submission: `spec/checkpoints/UC-2.md` at `048dcdc2175dbd207609a39211e0552819c38a5b`
- Verdict: REJECT
- Findings: 1 critical, 0 gaps, 0 protocol, 0 drift, 0 cosmetic
- Suite: 322 run, 0 failed, 0 errors, 0 skipped
- Working tree impact from verification: none

## Protocol Gate

1. PASS - UC-2 is the only target and was `READY_FOR_CONVERGENCE` before this report.
2. PASS - `spec/checkpoints/UC-2.md` and the implementation are committed together at `048dcdc2175dbd207609a39211e0552819c38a5b`, based on approved UC-3 convergence commit `0538d4f`.
3. PASS - UC-2 requires only UC-1, which is `APPROVED` at `0261b04` with its walkthrough passed.
4. PASS - UC-3 is `APPROVED`; UC-4 through UC-8 are `NOT_STARTED`; no other use case is active or ready.
5. PASS - The checkpoint contains rows for the scenario, every extension and guarantee, both postconditions, the UC-1 relationship, every applicable rule, commands, changed files, and approved-UC regressions.
6. PASS - The submission diff is attributable to the UC-2 read surface, presentation, localization, and verification. It contains no `.agents/` or later-UC change.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Owner `george` | Main steps 1-4 | Authenticated owner sees the complete, read-only owner/pet view and owned scheduling activity. | The three-test real-server suite used form login, cookies, rendered HTML, scoped queries, and table snapshots; all three journeys passed. |
| Owner `george` | Main step 5 | Only valid start, resume, and cancellation actions are shown. | Start and resume resolve through implemented owner request routes. The one displayed cancellation link resolves to `/my/appointments/{id}/cancel`, for which no controller mapping exists. |
| Owner `nopets` | Extension 2a | An owner with no pets sees action-free empty states. | Real HTTP rendered the owner and both empty states without pet or scheduling actions and left the database unchanged. |
| Owner `george` | Extensions 4a-4b | No-activity and rescheduled-appointment branches render their exact states. | Focused rendering asserted the sole new-request action for an inactive pet and the exact rescheduled time and staff reason. |
| Owner `george` | Extension 1a | Foreign and unknown resources are indistinguishable and unchanged. | Real HTTP returned equal standard 404 pages for foreign and unknown identifiers; full scoped pages and database snapshots excluded foreign data and mutation. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| UC-2 main step 1 | Owner opens My pets. | Real form login and `GET /my/pets` at `OwnerActivityE2ETests.java:58`. | STRONG | yes |
| UC-2 main step 2 | Exact owner and pets render read-only. | Field-perfect rendered assertions and absence of create/edit controls at `OwnerActivityWebTests.java:41`. | STRONG | yes |
| UC-2 main step 3 | Owner opens My appointments. | Authenticated real-server `GET /my/appointments` at `OwnerActivityE2ETests.java:73`. | STRONG | yes |
| UC-2 main step 4 | Every owned past/future appointment and active request renders. | Exact DOM, foreign-data exclusion, and unchanged-table assertions at `OwnerActivityWebTests.java:56` and `OwnerActivityE2ETests.java:73`. | STRONG | yes |
| UC-2 main step 5 | Only currently valid start, resume, and cancellation actions render. | Eligibility and href assertions pass, but the cancellation href in `templates/my/appointments.html:19` targets a route production cannot handle: `MyAppointmentsController.java:17` maps only the listing. | IMPOSSIBLE | no |
| UC-2 extension 2a | No-pet owner receives an action-free empty state. | Real owner session and focused DOM/persistence assertions at `OwnerActivityE2ETests.java:88` and `OwnerActivityWebTests.java:73`. | STRONG | yes |
| UC-2 extension 4a | Pet without appointments or request gets the absence and new-request action. | Exact empty labels and sole action assertion at `OwnerActivityWebTests.java:86`. | STRONG | yes |
| UC-2 extension 4b | Rescheduled appointment shows new time and staff reason. | Exact time and reason assertions through focused and real HTTP paths at `OwnerActivityWebTests.java:41` and `OwnerActivityE2ETests.java:58`. | STRONG | yes |
| UC-2 extension 1a | Foreign and unknown pet, request, and appointment access is indistinguishable. | Equal 404 bodies, absent foreign content, and unchanged full snapshots at `OwnerActivityE2ETests.java:103` and `OwnerActivityWebTests.java:107`. | STRONG | yes |
| UC-2 G1 | No foreign owner activity is disclosed. | Owner-scoped repositories plus identity, contact, pet, request, appointment, visit, and reason negative assertions. | STRONG | yes |
| UC-2 G2 | Veterinarian names and specialties render without directory access. | Exact veterinarian/specialty lists and absent directory link at `OwnerActivityWebTests.java:56`. | STRONG | yes |
| UC-2 G3 | Owner and pet representation remains read-only. | Immutable query records and no create/edit actions in rendered owner pages. | STRONG | yes |
| UC-2 G4 | Listing state and interpretation origin agree with request detail. | Both pages render the same request, state, origin, specialty, and veterinarian in focused and real-server tests. | STRONG | yes |
| UC-2 G5 | Presentation and messages retain UC-1 guarantees. | Shared layout and exact eleven-bundle parity passed in the 322-test suite. | STRONG | yes |
| UC-2 success postcondition | Owner sees a complete role-scoped activity view. | Real owner journey compares all expected owned rows and excludes foreign rows. | STRONG | yes |
| UC-2 minimal guarantee | Missing data is empty without disclosure or fabrication. | No-pet/no-activity pages and cross-owner snapshots prove the declared minimum. | STRONG | yes |
| UC-2 Requires UC-1 | Approved identity boundary scopes the reads. | All journeys consume the production form-login session and principal-to-owner mapping; UC-1 regressions pass. | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | Domain packages and thin controllers. | Read-only query service and two presentation-only GET controllers. | PASS |
| RULE-4 | Exact request lifecycle and side-effect-free refusals. | UC-2 adds no request mutation; full lifecycle regression suite passes. | PASS |
| RULE-7 | Flyway-only, lossless persistence. | Migration, mapping, field-fidelity, and restart suites pass. | PASS |
| RULE-8 | Exact normative data and BCrypt credentials. | Seed and credential regression tests pass. | PASS |
| RULE-9 | Exact role security and CSRF-protected mutations. | Listing routes are owner-scoped, but the advertised cancellation route has no production handler to authorize or protect. | FAIL (C-1) |
| RULE-10 | `/my/**` scope derives from the principal with indistinguishable 404. | Principal-scoped reads and cross-owner/unknown equality pass; the missing cancellation boundary prevents proof for the displayed appointment action. | FAIL (C-1) |
| RULE-16 | Shared layout with only state-valid role actions. | Shared layout and visibility predicates pass, but the displayed cancellation action is unusable. | FAIL (C-1) |
| RULE-17 | Visible text is keyed in all eleven bundles. | Bundle parity and hard-coded-text scans pass. | PASS |
| RULE-18 | Direct real-boundary evidence covers the complete UC. | 322 tests and three real-server journeys pass, but they assert only the cancellation href and never drive its boundary or consequence. | FAIL (C-1) |
| RULE-24 | Maven/H2-only build, isolated tests, durable runtime data. | Full Maven suite used isolated H2 databases; `data/petclinic.mv.db` stayed at `2026-09-07T22:56:54+0200`. | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required authentication, owner scoping, shared navigation, localization, Flyway, and repository boundary. | The 322-test full suite includes the approved UC-1 route, presentation, seed, isolation, and real-server journeys. | PASS |
| UC-3 | Shared owner request detail, actions, scheduling lifecycle, appointment aggregate, and presentation. | The 322-test full suite includes the approved UC-3 real-server, lifecycle, matching, concurrency, persistence, and presentation evidence. | PASS |

## Findings

### C-1 CRITICAL - Displayed cancellation action has no production boundary

- Contract: UC-2 main step 5 requires that the system show only actions currently valid, including cancellation of an upcoming appointment. RULE-9, RULE-10, RULE-16, and RULE-18 require the real owner mutation to be secured, principal-scoped, state-valid, and exercised at its actor boundary.
- Code: `src/main/resources/templates/my/appointments.html:19` links eligible rows to `/my/appointments/{id}/cancel`, while `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/MyAppointmentsController.java:17` maps only `GET /my/appointments`.
- Tests: `OwnerActivityWebTests.java:102` and `OwnerActivityE2ETests.java:79` merely assert that the dead href is present; neither follows or submits it. A repository-wide controller scan found no matching handler.
- Failure: the owner sees an apparently valid cancellation action that cannot cancel anything. Thus main step 5 is observably incomplete even though the page-rendering tests are green.
- Revision outcome: make the displayed cancellation action resolve through an implemented, principal-scoped, CSRF-protected boundary; enforce appointment eligibility below the UI; and exercise success, wrong-owner/unknown, wrong-state, and no-side-effect behavior through that boundary.

## Status Update

`READY_FOR_CONVERGENCE` -> `NEEDS_REVISION`; UC-2 remains current and no later UC is eligible until it is approved.

## Response to execute

REVISE UC-2: make the displayed owner cancellation action resolve through an implemented, principal-scoped boundary and exercise that boundary.
