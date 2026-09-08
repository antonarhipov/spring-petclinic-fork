# Use-Case Checkpoint: UC-5 - Operate the clinic calendar and appointment lifecycle

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `27a45aa35d004d2d413a07ee2dcb810356881a8f`
- Submission commit: HEAD at convergence
- Relations verified: Requires UC-1 through seeded staff/owner form-login sessions and enforced staff-only routes

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-3 | Exact six-veterinarian, 15-minute day grid, opening/closure, effective availability, Confirmed/Held blocks, free capacity, and navigation at `StaffCalendarWebTests.java:90`; real-server calendar at `SchedulingE2eTests.java:535` | PASS |
| Main steps 4-8 | Staff detail, mandatory reason, current-state validation, immediate reschedule/audit, and owner visibility at `StaffCalendarWebTests.java:116` and `SchedulingE2eTests.java:535` | PASS |
| Extension 3a | Staff-only Held detail, hold age, request link, reasoned hold deletion, and `WITH_STAFF/HOLD_RELEASED` at `StaffCalendarWebTests.java:163` | PASS |
| Extension 3b | Free-capacity direct booking creates a reasoned Confirmed appointment with direct origin at `StaffCalendarWebTests.java:186` | PASS |
| Extension 5a | Staff cancellation actor/time/reason, final state, and owner visibility at `StaffCalendarWebTests.java:211` | PASS |
| Extensions 5b-5c | Prefilled/truncated completion, exactly one linked dated visit, and No-show without visit at `StaffCalendarWebTests.java:251` | PASS |
| Extensions 5d-5e | Premature finalization and final-state changes are typed refusals with unchanged database snapshots at `StaffCalendarWebTests.java:233` | PASS |
| Extension 7a | Overlap, availability, and exact staff-boundary refusals preserve all affected rows at `StaffCalendarWebTests.java:203` and `StaffSchedulingWebTests.java:166` | PASS |
| Extension 7b | Concurrent same-slot reschedules yield one winner and preserve the loser at its prior time at `ConcurrencyInvariantTests.java:189` | PASS |
| Extension 8a | Specialty mismatch displays before selection and warns without blocking a valid change at `StaffCalendarWebTests.java:116` | PASS |
| G1-G3 | Full calendar and staff-only Held detail at `StaffCalendarWebTests.java:90` and `StaffCalendarWebTests.java:163`; shared bounded staff validator at `StaffSlotValidator.java:23` | PASS |
| G4-G6 | Required reasons, immediate changes, finality, linked-visit exclusivity, and clock-derived clinic-local values across `StaffCalendarWebTests.java:116`, `StaffCalendarWebTests.java:211`, and `StaffCalendarWebTests.java:251` | PASS |
| G7 | Veterinarian lock, overlap recheck, and two-thread reschedule race at `ConcurrencyInvariantTests.java:189` | PASS |
| G8 | Shared layout and exact eleven-bundle localization parity in rendered UC-5 tests and `I18nPropertiesSyncTest` | PASS |
| Success postcondition | Real-server staff calendar/book/reschedule journey followed by affected owner observation at `SchedulingE2eTests.java:535` | PASS |
| Minimal guarantee | Full snapshots or exact prior state remain unchanged for invalid/conflicting/premature/final/losing actions | PASS |
| Requires UC-1 | Seeded staff/owner form login plus full route-security matrix | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1, RULE-2 | Delegating controller, read-only `StaffCalendarQueryService`, and transactional `StaffCalendarService` mutations | PASS |
| RULE-3, RULE-19, RULE-20 | One shared `StaffSlotValidator` reuses framework-free feasibility and `AvailabilityService` effective blocks in UC-4 and UC-5; exact boundary tests pass | PASS |
| RULE-4, RULE-5 | Approved request hold-release path plus aggregate lifecycle/refusal matrices and linked-visit assertions | PASS |
| RULE-6 | Pessimistic veterinarian lock, overlap recheck, and real two-thread one-winner evidence | PASS |
| RULE-7, RULE-8 | Local date/time, Flyway, exact seed, fidelity, and restart regressions | PASS |
| RULE-9, RULE-10 | Full role/CSRF route matrix; unchanged principal-derived owner scope and owner-visible real-server result | PASS |
| RULE-15 | Injected trusted clock for calendar date, hold age, finalization availability, and audit timestamps | PASS |
| RULE-16, RULE-17 | Shared PetClinic layout, state-dependent actions, localized templates/flash messages, and eleven identical key sets | PASS |
| RULE-18 | Real-server UC-5 journey, direct negative evidence, full green suite, and unchanged runtime database | PASS |
| RULE-23 | UC-5 exposes no configuration mutation; it reads current settings without changing them, leaving atomic configuration behavior to UC-7 | PASS |
| RULE-24 | Maven/H2-only inventory, runtime/test isolation, restart, README, and unchanged runtime H2 evidence | PASS |
| RULE-25 | Exactly one linked appointment-date visit only on completion, request-text prefill capped at 255, empty direct prefill, and no No-show visit | PASS |

## Validation

- Focused commands: UC-5 plus approved-path regressions passed 75 tests, 0 failures, 0 errors, 0 skipped; `SchedulingE2eTests` passed 16 tests at the real HTTP-server boundary.
- Full relevant suite: 355 tests, 0 failures, 0 errors, 0 skipped across 46 suites.
- Working tree impact from tests: none.
- Runtime evidence: staff form login, `/staff/calendar`, direct booking, appointment detail, reschedule, then owner form login and immediate `/my/appointments` visibility all passed through the real HTTP server.
- Runtime database: unchanged at 122880 bytes with SHA-256 `86e6f652cd4f220a089378fba479946a055a9de04b5270d7f6d75be9199831b6`.
- Changed files: appointment calendar/lifecycle services and routes, shared staff slot validation, two staff templates, eleven locale bundles, focused/concurrency/security/real-server tests, status, and this checkpoint.
- Approved UCs regression-tested: UC-1, UC-2, UC-3, and UC-4 in the 355-test suite.

## Notes

Automated UI evidence is complete. The shared-layout and actor-action walkthrough remains for convergence. RULE-23 is preserved but not exercised because UC-5 exposes no configuration mutation; UC-7 owns that actor goal.

READY FOR CONVERGENCE: UC-5
