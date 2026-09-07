# Use-Case Checkpoint: UC-2 - Review owned pets and scheduling activity

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `0538d4fec3f2135d594cced02ac09fa374e9291f`
- Submission commit: HEAD at convergence
- Relations verified: Requires UC-1; all real-server journeys authenticate through the approved form-login and role-scoped owner session

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-2 | `OwnerActivityE2ETests.uc2MainOwnerReviewsOnlyCompleteOwnedActivityThroughRealHttp` (`OwnerActivityE2ETests.java:58`) and exact rendered fields/no-edit-actions at `OwnerActivityWebTests.java:41` | PASS |
| Main steps 3-4 | Real-server past/future/final appointment, request, veterinarian, specialty, origin, and reason assertions at `OwnerActivityE2ETests.java:73`; focused exact DOM at `OwnerActivityWebTests.java:56` | PASS |
| Main step 5 | Start/resume empty/active branches and complete cancel-eligibility matrix at `OwnerActivityWebTests.java:73` and `OwnerActivityWebTests.java:92` | PASS |
| Extension 2a | Real authenticated no-pet owner and action-free empty states at `OwnerActivityE2ETests.java:88`; focused persistence check at `OwnerActivityWebTests.java:73` | PASS |
| Extension 4a | No-appointment/no-request empty labels plus only Start a request at `OwnerActivityWebTests.java:86` | PASS |
| Extension 4b | Rescheduled 2026-09-08 11:15-11:45 plus exact `Moved for emergency coverage` reason in focused and real-server main journeys | PASS |
| Extension 1a | Foreign/unknown equal 404, no foreign list data, and complete unchanged snapshots at `OwnerActivityE2ETests.java:103` and `OwnerActivityWebTests.java:107` | PASS |
| G1 | Owner-scoped repositories, negative page assertions for every foreign category, and before/after owner/pet/request/interpretation/appointment/visit equality | PASS |
| G2 | `Linda Douglas` with `dentistry, surgery` and `Helen Leary` with `radiology` render in activity without `/vets.html` | PASS |
| G3 | Immutable owner/pet query records render with no create/edit actions at `templates/my/pets.html:4` and `OwnerActivityE2ETests.java:67` | PASS |
| G4 | Listing and request detail agree on Ready for confirmation/AI interpretation/surgery/Helen Leary at `OwnerActivityWebTests.java:56` | PASS |
| G5 | Shared-layout templates plus exact eleven-bundle and hard-coded-visible-text scans | PASS |
| Success postcondition | Real authenticated main journey returns the complete owned activity view and leaves full affected-table snapshots unchanged | PASS |
| Minimal guarantee | Empty states fabricate no records; foreign and unknown paths disclose and change nothing | PASS |
| Requires UC-1 | Real form login, cookies, identity/Logout, owner-only routes, and the complete approved UC-1 regression suite | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1 | Thin controllers at `MyPetsController.java:19` and `MyAppointmentsController.java:17`; read-only query service at `OwnerActivityQueryService.java:24` | PASS |
| RULE-4 | No new mutation; complete request transition/refusal matrix passes in the full suite | PASS |
| RULE-7 | Approved Flyway, local date/time, immutable interpretation round-trip, and restart tests pass | PASS |
| RULE-8 | Exact normative seed and all credential assertions pass | PASS |
| RULE-9 | Single security chain, real owner login, and full route matrix pass | PASS |
| RULE-10 | Principal-derived owner at `OwnerActivityQueryService.java:44` and `OwnerActivityQueryService.java:50`; equal cross-owner/unknown 404 evidence | PASS |
| RULE-16 | Shared layout, role menu, identity/logout, PetClinic controls, DOM evidence; human walkthrough deferred to convergence | PASS |
| RULE-17 | All new text keyed in exact eleven-bundle parity; source/template scans pass | PASS |
| RULE-18 | Three real-server journeys, direct evidence for every contract element, 322/0/0/0 suite, unchanged tree/runtime database | PASS |
| RULE-24 | Maven/H2 repository, property, dependency, README, isolation, and restart checks pass | PASS |

## Validation

- Focused commands: JDK 21/Byte Buddy `-Dtest=OwnerActivityWebTests,I18nPropertiesSyncTest,AppointmentStateTransitionTests` - 39/0/0/0 PASS; `-Dtest=OwnerActivityE2ETests` - 3/0/0/0 PASS on dynamic localhost ports.
- Full relevant suite: JDK 21/Byte Buddy Maven test invocation - 322 tests run, 0 failures, 0 errors, 0 skipped.
- Working tree impact from tests: none; `data/petclinic.mv.db` timestamp remains `2026-09-07T22:56:54+0200`; `.agents/` excluded.
- Runtime evidence: real owner form login, cookie session, `/my/pets`, `/my/appointments`, owned request detail, empty-owner paths, and foreign/unknown 404 paths with exact rendered values and full table snapshots.
- Changed files: query service; two thin controllers; shared appointment eligibility predicate; two templates; eleven message bundles; two UC-2 test classes; status and this checkpoint.
- Approved UCs regression-tested: UC-1 and UC-3 pass within the 322-test full suite, including their real-server, security, lifecycle, concurrency, persistence, presentation, and localization coverage.

## Notes

None.

READY FOR CONVERGENCE: UC-2
