# Convergence: UC-6 - Cancel an upcoming owned appointment

## Summary

- Submission: `spec/checkpoints/UC-6.md` at `737d8ea8a8f26561bf4214554b29fbc1e5d86d7b`
- Verdict: PENDING WALKTHROUGH
- Findings: 0 critical, 0 gap, 0 protocol; human UI walkthrough pending
- Suite: 358 run, 0 failed, 0 errors, 0 skipped across 46 suites
- Working tree impact from verification: none; the tracked runtime database remained unchanged

## Protocol Gate

1. Exactly UC-6 was submitted and `spec/status.md` named it `READY_FOR_CONVERGENCE` at the start of the audit.
2. The checkpoint and implementation are committed together at immutable submission `737d8ea8a8f26561bf4214554b29fbc1e5d86d7b` from clean base `d425fad023f9ba0554e41450cc5e0b8699c8449e`.
3. Its only dependency, UC-1, is `APPROVED`; UC-2 through UC-5 are also approved.
4. No other use case was active or ready, and the working tree was clean before verification.
5. The checkpoint maps every main step, extension, guarantee, postcondition, relation, and applicable rule to executable evidence.
6. Inspection of all 20 changed files found one UC-6 vertical slice: owner detail/confirmation, aggregate audit preservation, localized presentation, and boundary tests. No later-use-case behavior is included.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Owner | Main steps 1-3 | Select Cancel in history, inspect local appointment detail, and confirm without a reason | `OwnerActivityE2ETests.uc6MainOwnerOpensConfirmsAndRetainsCancellationThroughRealHttp` passed through seeded form login, list navigation, detail rendering, CSRF, and POST on a fresh in-memory H2 server. |
| Owner | Main steps 4-5 | See final actor/time, no further Cancel, retained history, and closed request | The same real-server journey observed persisted `CANCELLED`/`OWNER`/`2026-09-07 09:00`, final detail without the action, the history row, an unchanged Accepted request, and no new visit. |
| Owner | Extension 1a | Foreign and unknown appointments are indistinguishable | Real HTTP GET and POST responses were both 404, byte-equivalent after CSRF normalization, free of Betty's data, and left complete snapshots unchanged. |
| Owner | Extensions 2a-3a | Started/final appointments expose no action and forged posts do nothing | Focused rendered-page tests covered started Confirmed, Cancelled, Completed, and No-show; forged posts returned conflict with identical database snapshots. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| Main step 1 | Eligible history action opens the owned appointment | List-to-detail URL and scoped model at `OwnerActivityWebTests.java:145`; real HTTP navigation at `OwnerActivityE2ETests.java:96` | STRONG | yes |
| Main step 2 | Detail shows veterinarian and local date/time plus Cancel without reason | Exact rendered values and absence of `name="reason"` at `OwnerActivityWebTests.java:151`; reproduced through real HTTP | STRONG | yes |
| Main step 3 | Owner confirms cancellation | CSRF POST and redirect to final detail at `OwnerActivityWebTests.java:160` and `OwnerActivityE2ETests.java:107` | STRONG | yes |
| Main step 4 | Final state records OWNER and trusted timestamp and removes Cancel | Rendered final detail plus exact appointment row at `OwnerActivityWebTests.java:166`; real-server persistence at `OwnerActivityE2ETests.java:113` | STRONG | yes |
| Main step 5 | Request stays closed and appointment stays in history | Accepted/null-active-pet row, unchanged request snapshot, and retained list row at `OwnerActivityWebTests.java:172`; real HTTP repeat at `OwnerActivityE2ETests.java:125` | STRONG | yes |
| Extension 1a | Foreign and unknown detail/action return the same 404 without effects | MockMvc GET comparison at `OwnerActivityWebTests.java:225`; real GET/POST comparison and complete snapshot at `OwnerActivityE2ETests.java:151` | STRONG | yes |
| Extension 2a | Started and final appointments show no Cancel | All four ineligible states render without confirmation/action at `OwnerActivityWebTests.java:193` | STRONG | yes |
| Extension 3a | Forged or stale cancellation is refused without mutation | Conflict response and full snapshot equality for started and all final states at `OwnerActivityWebTests.java:215` | STRONG | yes |
| G1 | No minimum notice and no owner reason | Same-day appointment one minute after now remains eligible; confirmation has no reason input at `OwnerActivityWebTests.java:193` | STRONG | yes |
| G2 | Cancellation does not reopen/create requests or create visits | Request/interpretation/visit snapshots are unchanged and existing Accepted request remains closed at `OwnerActivityWebTests.java:176` | STRONG | yes |
| G3 | Owner cannot see or act on a foreign appointment | Both GET and POST owner-scoped lookup paths produce disclosure-free equivalent 404 responses at `OwnerActivityE2ETests.java:151` | STRONG | yes |
| G4 | Staff reasons and cancellation outcomes remain visible | Prior staff reason survives owner cancellation at `OwnerActivityWebTests.java:166`; staff actor/time/reason render at `OwnerActivityWebTests.java:193` | STRONG | yes |
| G5 | Shared localized presentation with exact state actions | Both owner templates use `fragments/layout`; rendered tests and exact eleven-bundle parity pass; human visual check remains pending | STRONG | yes |
| Success postcondition | Final owner-cancelled audit plus closed request | Real-server journey and field-exact database assertions at `OwnerActivityE2ETests.java:89` | STRONG | yes |
| Minimal guarantee | Ineligible and unauthorized paths disclose/change nothing | Equivalent 404 responses, typed conflict responses, and full before/after snapshots above | STRONG | yes |
| Requires UC-1 | Owner identity/session scopes the use case | Seeded form login in the real-server journey and full anonymous/owner/staff security matrix | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | Controllers MUST delegate lifecycle decisions | `MyAppointmentsController` delegates to `OwnerActivityQueryService` and `AppointmentService`; eligibility/finality remain below MVC | PASS |
| RULE-2 | Mutation MUST be atomic and detached queries read-only | `AppointmentService.cancelByOwner` is transactional; `OwnerActivityQueryService` is read-only; refused-action snapshots are unchanged | PASS |
| RULE-4 | Request lifecycle MUST reject unlisted changes without effects | Owner cancellation never calls a request transition; the linked Accepted request is byte-for-byte unchanged | PASS |
| RULE-5 | One appointment aggregate MUST enforce finality | `Appointment.isCancellableByOwnerAt` and `AppointmentService.cancelByOwner` enforce Confirmed-before-start; lifecycle and forged-action matrices pass | PASS |
| RULE-7 | Flyway/local time/data fidelity MUST hold | No schema path changed; migration, local-field mapping, restart, and fidelity suites passed | PASS |
| RULE-8 | Exact normative seeds MUST remain | Full `SeedMigrationTests` values and BCrypt checks passed with no migration change | PASS |
| RULE-9 | One path chain, form login, CSRF, and role boundary MUST hold | Both owner appointment route shapes are inventoried in `SecurityMatrixWebTests`; anonymous/staff denial and missing-CSRF refusal pass | PASS |
| RULE-10 | `/my/**` MUST derive owner from principal and normalize foreign/unknown | Both query and mutation resolve the authenticated owner and use `findByIdAndPetOwnerId`; equivalent 404 evidence passes | PASS |
| RULE-15 | Scheduling time MUST come from injected Clock | Query eligibility and cancellation date/time use the injected clock; static scan found no unclocked scheduling `now()` | PASS |
| RULE-16 | Pages MUST use shared layout and state-permitted actions | New detail uses the established layout and renders confirmation only for eligible state; automated DOM passes and human visual check remains pending | PASS |
| RULE-17 | Visible text MUST use keys in all eleven bundles | All six new keys have exact eleven-bundle parity; template/Java localization scans pass | PASS |
| RULE-18 | Real-server/direct evidence MUST cover the contract without test residue | 59 focused tests, the real-server UC-6 journey, full ledger, 358-test suite, clean tree, and unchanged runtime H2 pass | PASS |
| RULE-24 | Maven/H2-only and test isolation MUST remain | Repository inventory, H2 restart/isolation, README, and runtime-data guards pass | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required owner authentication, route authorization, shared layout, localization, and seeds | Full security, presentation, localization, seed, and real-server suites | PASS |
| UC-2 | Owner history/read model and pre-existing cancellation boundary | Full owner activity MockMvc and E2E suites, including empty and cross-owner cases | PASS |
| UC-3 | Held/Confirmed lifecycle and accepted-request linkage | Full request, matching, lifecycle, and concurrency suites | PASS |
| UC-4 | Staff-authored reasons and request outcomes shown to owner | Full staff scheduling and owner outcome suites | PASS |
| UC-5 | Staff reschedule/cancel audit and final appointment states | Full calendar/lifecycle/E2E suites; owner cancellation preserves prior staff reason | PASS |

## Findings

No critical, gap, protocol, drift, or cosmetic finding remains. Human confirmation of the UI-bearing flow is the only open gate.

## Walkthrough

1. Ensure George has a future Confirmed appointment. If needed, sign in as `staff` / `staff123`, use Calendar to book Leo into a future free slot with a reason, then sign out.
2. Sign in as `george` / `george123`, open My appointments, and select Cancel appointment. Confirm the detail shows Leo, veterinarian, local date/time, Confirmed status, and any staff reason.
3. Confirm there is no cancellation-reason input. Select Confirm cancellation.
4. Confirm the detail now shows Cancelled, Cancelled by owner, a cancellation timestamp, the prior staff reason if one existed, and no Cancel action.
5. Return to My appointments and confirm the Cancelled appointment remains in history with View appointment instead of Cancel appointment.

User result: pending.

## Status Update

`READY_FOR_CONVERGENCE` -> `PENDING_WALKTHROUGH`; no use case is eligible until the UC-6 walkthrough passes.

## Response to execute

PENDING WALKTHROUGH: complete the UC-6 owner-cancellation script above, then report PASS or the failing step.
