# Use-Case Checkpoint: UC-4 - Resolve a scheduling request that needs staff

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `04a2776db29429e8aa926313dd21bde6e5b8e460`
- Submission commit: HEAD at convergence
- Relations verified: Requires UC-1 through seeded staff/owner form-login sessions and enforced staff-only routes

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-4 | `StaffSchedulingWebTests.java:82`, `StaffSchedulingWebTests.java:106`, and real-server entry at `SchedulingE2eTests.java:285` | PASS |
| Main steps 5-6 | Latest/empty prefill, immutable history, STAFF version, and no-duplicate evidence at `StaffSchedulingWebTests.java:106` and `StaffSchedulingWebTests.java:142` | PASS |
| Main steps 7-10 | Reasoned direct booking, mismatch display, constraints, Accepted/Confirmed state, queue removal, and owner view at `StaffSchedulingWebTests.java:247` and `SchedulingE2eTests.java:312` | PASS |
| Extension 1a | Staff-created With staff request and zero AI calls at `SchedulingE2eTests.java:289` | PASS |
| Extension 1b | Existing-request behavior at `RequestCreationServiceTests.java:110`, `SchedulingE2eTests.java:350`, and concurrent winner at `ConcurrencyInvariantTests.java:72` | PASS |
| Extensions 2a-2b | Reasoned deletion/routing plus complete read-only In progress inspection surface at `StaffSchedulingWebTests.java:318` | PASS |
| Extensions 3a and 4a | Abandoned removal and later no-op at `StaffSchedulingWebTests.java:373` | PASS |
| Extension 5a | Unchanged complete interpretation creates no duplicate at `StaffSchedulingWebTests.java:106` | PASS |
| Extension 5b | Full state/action refusal matrix at `RequestStateTransitionTests.java:108` | PASS |
| Extension 5c | Localized missing/invalid field feedback with no persistence at `StaffSchedulingWebTests.java:195` | PASS |
| Extension 7a | One reasoned Held suggestion and owner visibility at `StaffSchedulingWebTests.java:318` and `SchedulingE2eTests.java:339` | PASS |
| Extension 7b | Constraint and malformed-input refusals at `StaffSchedulingWebTests.java:166`, `StaffSchedulingWebTests.java:218`, and `StaffSchedulingWebTests.java:247` | PASS |
| Extension 8a | Owner-window/horizon/lead/specialty overrides at `StaffSchedulingWebTests.java:247` and mismatch display at `SchedulingE2eTests.java:309` | PASS |
| Extension 8b | Opening, effective-block, grid, duration, and overlap refusals at `StaffSchedulingWebTests.java:166` and `StaffSchedulingWebTests.java:247` | PASS |
| Extension 9a | HTTP stale refusal and raced same-request optimistic claim at `StaffSchedulingWebTests.java:373` and `ConcurrencyInvariantTests.java:108` | PASS |
| Extension 9b | One same-slot winner and one unchanged request at `ConcurrencyInvariantTests.java:88` | PASS |
| G1-G2 | Exact partition/order/fields and no claim state at `StaffSchedulingWebTests.java:82`; optimistic version at `ConcurrencyInvariantTests.java:108` | PASS |
| G3-G4 | Zero AI, wrong-state refusals, complete immutable versions, and latest-version use at `SchedulingE2eTests.java:289`, `RequestStateTransitionTests.java:108`, and `StaffSchedulingWebTests.java:106` | PASS |
| G5-G6 | Override/bounded constraints and three concurrent invariants at `StaffSchedulingWebTests.java:166`, `StaffSchedulingWebTests.java:247`, and `ConcurrencyInvariantTests.java:72` | PASS |
| G7-G8 | Owner visibility/abandonment, shared localized pages, and required persisted reasons at `SchedulingE2eTests.java:323`, `OwnerTransitionServiceTests.java:209`, and `StaffSchedulingWebTests.java:318` | PASS |
| Success postcondition | Both Accepted/Confirmed and Suggestion offered/one Held owner-visible outcomes in `SchedulingE2eTests.java:285` | PASS |
| Minimal guarantee | Invalid, stale, abandoned, wrong-state, and losing concurrent actions preserve state/history/capacity in the staff and concurrency suites | PASS |
| Requires UC-1 | Seeded staff/owner form-login journeys and full security regression suite | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1, RULE-2 | Delegating controllers, read-only query service, and transactional request/appointment services | PASS |
| RULE-3, RULE-19, RULE-20 | Framework-free feasibility plus shared effective availability and exact staff boundaries at `DefaultSlotSuggestionPort.java:278` and `StaffSchedulingWebTests.java:166` | PASS |
| RULE-4, RULE-5 | Complete request and appointment lifecycle matrix suites | PASS |
| RULE-6 | Unique active pet, optimistic request claim, pessimistic veterinarian lock, overlap recheck, and two-thread evidence at `ConcurrencyInvariantTests.java:72` | PASS |
| RULE-7, RULE-8 | Immutable value round trips plus fresh exact normative seed suite | PASS |
| RULE-9, RULE-10 | Whole-route matrix, CSRF, staff-only routes, and unchanged owner-scope regressions | PASS |
| RULE-12 | Zero interpreter invocations on all staff paths | PASS |
| RULE-15 | Injected clock for hold age and pinned time tests | PASS |
| RULE-16, RULE-17 | Shared layout, state actions, eleven-bundle key parity, localized validation, and German weekday rendering | PASS |
| RULE-18 | Real-server UC-4 journey, direct negative evidence, full green suite, and unchanged runtime database | PASS |
| RULE-21, RULE-22 | Verbatim STAFF interpretation values, no duplicate version, preserved history/rejections | PASS |
| RULE-24 | Maven/H2 inventory, restart, isolation, and README regressions | PASS |

## Validation

- Focused commands: `-Dtest=StaffSchedulingWebTests,I18nPropertiesSyncTest test` passed 12/0/0/0; staff lifecycle and concurrency focused suites also passed.
- Full relevant suite: 338 tests, 0 failures, 0 errors, 0 skipped across 45 suites.
- Working tree impact from tests: none.
- Runtime evidence: staff form login, `/staff/queue`, staff create/author/book/suggest actions, and owner `/my/appointments` plus `/my/requests/{id}` views all passed through the real HTTP server.
- Runtime database: unchanged at 114688 bytes, timestamp `2026-09-08T15:16:05+0200`, SHA-256 `6bd75c0c92324582c86d98d90eada9612d7b40f8711cedf2d8f79c08e37a6820`.
- Changed files: request/appointment/matching services and domain; staff controllers/query/form/templates; eleven locale bundles; staff, concurrency, lifecycle, security, and real-server tests; status and this checkpoint.
- Approved UCs regression-tested: UC-1, UC-2, and UC-3 in the 338-test suite.

## Notes

Automated UI evidence is complete. The shared-layout and actor-action walkthrough remains for convergence.

READY FOR CONVERGENCE: UC-4
