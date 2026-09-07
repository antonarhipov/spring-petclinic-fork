# Use-Case Checkpoint: UC-2 - Review owned pets and scheduling activity

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `94b320eb56f02e13b135ac1a1d1cf5558d5f5a2f`
- Submission commit: HEAD at convergence
- Relations verified: Requires UC-1; all real-server journeys authenticate through the approved form-login and role-scoped owner session

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-2 | `OwnerActivityE2ETests.uc2MainOwnerReviewsOnlyCompleteOwnedActivityThroughRealHttp` (`OwnerActivityE2ETests.java:58`) and exact rendered fields/no-edit-actions at `OwnerActivityWebTests.java:44` | PASS |
| Main steps 3-4 | Real-server past/future/final appointment, request, veterinarian, specialty, origin, and reason assertions at `OwnerActivityE2ETests.java:73`; focused exact DOM at `OwnerActivityWebTests.java:56` | PASS |
| Main step 5 | Start/resume and cancel visibility matrix plus the principal-scoped CSRF cancellation boundary at `OwnerActivityWebTests.java:96` and `OwnerActivityWebTests.java:111`; real submitted form, durable `CANCELLED/OWNER` state, and action removal at `OwnerActivityE2ETests.java:89` | PASS |
| Extension 2a | Real authenticated no-pet owner and action-free empty states at `OwnerActivityE2ETests.java:120`; focused persistence check at `OwnerActivityWebTests.java:77` | PASS |
| Extension 4a | No-appointment/no-request empty labels plus only Start a request at `OwnerActivityWebTests.java:77` | PASS |
| Extension 4b | Rescheduled 2026-09-08 11:15-11:45 plus exact `Moved for emergency coverage` reason in focused and real-server main journeys | PASS |
| Extension 1a | Foreign/unknown pet, request, and cancellation POSTs return equal 404 pages with complete unchanged snapshots at `OwnerActivityE2ETests.java:135` and `OwnerActivityWebTests.java:146` | PASS |
| G1 | Principal-scoped repositories and cancellation lookup, negative page assertions for every foreign category, equal foreign/unknown mutation responses, and complete no-side-effect snapshots | PASS |
| G2 | `Linda Douglas` with `dentistry, surgery` and `Helen Leary` with `radiology` render in activity without `/vets.html` | PASS |
| G3 | Immutable owner/pet query records render with no create/edit actions at `templates/my/pets.html:4` and `OwnerActivityE2ETests.java:67` | PASS |
| G4 | Listing and request detail agree on Ready for confirmation/AI interpretation/surgery/Helen Leary at `OwnerActivityWebTests.java:56` | PASS |
| G5 | Shared-layout templates plus exact eleven-bundle and hard-coded-visible-text scans | PASS |
| Success postcondition | Real authenticated journeys return the complete owned activity view and execute its displayed cancellation action with only the eligible appointment changing | PASS |
| Minimal guarantee | Empty states fabricate no records; foreign, unknown, and state-invalid cancellation paths disclose and change nothing | PASS |
| Requires UC-1 | Real form login, cookies, identity/Logout, owner-only routes, and the complete approved UC-1 regression suite | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1 | `MyAppointmentsController.java:34` delegates the mutation to the transactional owner-scoped appointment service; read assembly remains in `OwnerActivityQueryService.java:24` | PASS |
| RULE-2 | `AppointmentService.java:115` performs owned lookup, eligibility enforcement, cancellation metadata, and flush in one transaction; refused boundary tests compare all affected rows | PASS |
| RULE-4 | Cancellation changes no request state; complete request and appointment transition/refusal matrices pass in the full suite | PASS |
| RULE-7 | Approved Flyway, local date/time, immutable interpretation round-trip, and restart tests pass | PASS |
| RULE-8 | Exact normative seed and all credential assertions pass | PASS |
| RULE-9 | Cancellation is a POST form with an actual CSRF token; the same owner POST without it returns 403 unchanged; anonymous/staff denial and the full route matrix pass | PASS |
| RULE-10 | Controller derives owner from `Principal`; `AppointmentService.java:116` performs the owned lookup and equal foreign/unknown cancellation 404s have no side effect | PASS |
| RULE-16 | Shared layout, role menu, identity/logout, PetClinic controls, state-valid form action, and post-cancellation removal are DOM-tested; human walkthrough deferred to convergence | PASS |
| RULE-17 | All new text keyed in exact eleven-bundle parity; source/template scans pass | PASS |
| RULE-18 | Four real-server journeys include successful and refused cancellation POSTs; direct evidence covers every contract element; 325/0/0/0 suite leaves the tree/runtime database unchanged | PASS |
| RULE-24 | Maven/H2 repository, property, dependency, README, isolation, and restart checks pass | PASS |

## Validation

- Focused commands: JDK 21/Byte Buddy `-Dtest=OwnerActivityWebTests,I18nPropertiesSyncTest,AppointmentStateTransitionTests` - 41/0/0/0 PASS; `-Dtest=OwnerActivityE2ETests` - 4/0/0/0 PASS on dynamic localhost ports.
- The first revision-focused run had 1/41 assertion failure because a JDBC assertion observed the outer test transaction before JPA flushed; `cancelByOwner` now flushes the completed mutation and the identical suite passes. The first real-server rerun produced 4 context errors because the sandbox denied localhost binding; the identical command passed when dynamic localhost binding was allowed.
- One later full run had 1/325 failure in the pre-existing duplicate-pet-name race; its isolated real-server rerun passed 1/0/0/0 and the identical complete suite rerun passed 325/0/0/0 without a production change.
- Full relevant suite: JDK 21/Byte Buddy Maven test invocation - 325 tests run, 0 failures, 0 errors, 0 skipped.
- Working tree impact from tests: none; `data/petclinic.mv.db` timestamp remains `2026-09-07T22:56:54+0200`; `.agents/` excluded.
- Runtime evidence: real owner form login, cookie session, `/my/pets`, `/my/appointments`, owned request detail, submitted CSRF cancellation, exact durable cancellation metadata, empty-owner paths, and foreign/unknown 404 paths with exact rendered values and full table snapshots.
- Revision changed files: appointment controller/service/owner-boundary exception response, appointments template, two UC-2 test classes, status, and this checkpoint.
- Approved UCs regression-tested: UC-1 and UC-3 pass within the 325-test full suite, including their real-server, security, lifecycle, concurrency, persistence, presentation, and localization coverage.

## Notes

Convergence finding C-1 is resolved: the displayed action is now a POST form backed by a principal-scoped transactional handler, with real-server success and foreign/unknown refusal evidence plus focused state-invalid and no-side-effect checks.

READY FOR CONVERGENCE: UC-2
