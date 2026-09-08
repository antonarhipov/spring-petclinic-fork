# Use-Case Checkpoint: UC-6 - Cancel an upcoming owned appointment

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `d425fad023f9ba0554e41450cc5e0b8699c8449e`
- Submission commit: HEAD at convergence
- Relations verified: Requires UC-1 through seeded owner form login, principal-derived scope, CSRF, and owner-only routes

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-3 | Owner list-to-detail navigation, veterinarian/date/time, reason-free confirmation form, and CSRF-protected submission at `OwnerActivityWebTests.java:139` and the real-server journey at `OwnerActivityE2ETests.java:89` | PASS |
| Main steps 4-5 | Final Cancelled state, OWNER actor, trusted timestamp, removed action, retained history, and unchanged closed Accepted request at `OwnerActivityWebTests.java:160` and `OwnerActivityE2ETests.java:107` | PASS |
| Extension 1a | Foreign and unknown detail responses are identical standard 404 pages with no disclosure or mutation at `OwnerActivityWebTests.java:225`; GET and POST are repeated through the real server at `OwnerActivityE2ETests.java:151` | PASS |
| Extension 2a | Started Confirmed, Cancelled, Completed, and No-show details omit the cancellation action at `OwnerActivityWebTests.java:193` | PASS |
| Extension 3a | Forged/stale POSTs for every ineligible state return typed conflict responses and preserve complete database snapshots at `OwnerActivityWebTests.java:215` | PASS |
| G1 | A same-day appointment one minute after the trusted current time is cancellable and the owner form contains no reason input at `OwnerActivityWebTests.java:193` | PASS |
| G2 | Request, interpretation, and visit snapshots remain unchanged; the originating request remains Accepted with no active pet at `OwnerActivityWebTests.java:176` and `OwnerActivityE2ETests.java:113` | PASS |
| G3 | Principal-scoped detail and mutation queries plus indistinguishable foreign/unknown responses at `OwnerActivityWebTests.java:225` and `OwnerActivityE2ETests.java:151` | PASS |
| G4 | Staff reschedule reason survives owner cancellation, while staff cancellation actor/time/reason render on owner detail at `OwnerActivityWebTests.java:166` and `OwnerActivityWebTests.java:193` | PASS |
| G5 | Shared owner layout, state-dependent controls, and exact eleven-bundle localization parity in rendered tests and `I18nPropertiesSyncTest` | PASS |
| Success postcondition | Full real-server owner login, open, confirm, persistence audit, closed-request, final-detail, and history journey at `OwnerActivityE2ETests.java:89` | PASS |
| Minimal guarantee | Unauthorized and ineligible GET/POST paths compare standard responses and complete unchanged snapshots | PASS |
| Requires UC-1 | Seeded owner form login and full anonymous/staff/owner route-security matrix | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1, RULE-2 | `MyAppointmentsController` delegates the read to the read-only query service and the mutation to one transactional appointment service operation | PASS |
| RULE-4, RULE-5 | Accepted request state is unchanged; aggregate lifecycle checks refuse every non-Confirmed or started cancellation before mutation | PASS |
| RULE-7, RULE-8 | Local date/time audit fields, Flyway schema, exact seed, fidelity, and restart regressions pass without schema or seed changes | PASS |
| RULE-9 | Existing single security chain protects the added GET and POST shapes; CSRF refusal and full role route matrix pass | PASS |
| RULE-10 | Both detail and cancel resolve the owner from the authenticated principal and use owner-scoped repository lookup; foreign and unknown responses match | PASS |
| RULE-15 | Detail eligibility and cancellation audit values use the injected fixed clinic clock | PASS |
| RULE-16, RULE-17 | New page uses the established owner layout and only message keys present identically in all eleven bundles | PASS |
| RULE-18 | Real-server UC-6 journey, direct evidence for every extension/guarantee/postcondition, 358-test full suite, and unchanged runtime database | PASS |
| RULE-24 | Maven/H2-only inventory, in-memory test isolation, and unchanged file-backed runtime H2 regressions pass | PASS |

## Validation

- Focused commands: 49 owner activity, appointment lifecycle, route security, and localization tests passed with 0 failures, 0 errors, and 0 skipped; `OwnerActivityE2ETests` passed 4 real-server tests.
- Full relevant suite: 358 tests, 0 failures, 0 errors, 0 skipped across 46 suites.
- Formatting and hygiene: `spring-javaformat:validate` and `git diff --check` passed.
- Working tree impact from tests: none.
- Runtime database: unchanged at 122880 bytes with SHA-256 `86e6f652cd4f220a089378fba479946a055a9de04b5270d7f6d75be9199831b6`.
- Changed files: owner appointment controller/query/domain, owner list and detail templates, eleven locale bundles, focused/real-server owner tests, status, and this checkpoint.
- Approved UCs regression-tested: UC-1 through UC-5 in the 358-test suite.

## Notes

The first focused run exposed that owner cancellation erased a prior staff reschedule reason. The aggregate now preserves existing staff audit text when the owner supplies no reason, and all verification passes. The first real-server attempt could not bind localhost inside the sandbox; the identical permitted rerun passed. Automated UI evidence is complete; the owner walkthrough remains for convergence.

READY FOR CONVERGENCE: UC-6
