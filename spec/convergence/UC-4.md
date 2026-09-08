# Convergence: UC-4 - Resolve a scheduling request that needs staff

## Summary

- Submission: `spec/checkpoints/UC-4.md` at `4c8d3079f300ade92d9e3e6c4df412e0e2269923`
- Verdict: REJECT
- Findings: 1 critical, 0 gaps, 0 protocol, 0 drift, 0 cosmetic
- Suite: 338 run, 0 failed, 0 errors, 0 skipped; focused convergence run 33/0/0/0
- Working tree impact from verification: none

## Protocol Gate

1. PASS - UC-4 is the only target and was `READY_FOR_CONVERGENCE` before this report.
2. PASS - `spec/checkpoints/UC-4.md` and the implementation are committed together at `4c8d3079f300ade92d9e3e6c4df412e0e2269923`, based on `04a2776db29429e8aa926313dd21bde6e5b8e460`.
3. PASS - UC-4 requires only UC-1, which is `APPROVED` at `0261b04` with its walkthrough passed.
4. PASS - UC-5 through UC-8 are `NOT_STARTED`; no other use case is active or ready.
5. PASS - The checkpoint maps all ten main steps, all 15 extensions, all eight guarantees, both postconditions, the UC-1 relationship, all applicable rules, validation commands, changed files, and approved-UC regression evidence.
6. PASS - Inspection of all 36 changed files found only the UC-4 staff-assisted scheduling slice, its required shared request/appointment behavior, presentation/localization, status/checkpoint artifacts, and verification. No later-UC or unrelated change is included.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Staff `staff` | Main steps 1-10 | Opens the queue, creates or selects a request, authors an interpretation, books directly, and makes the result owner-visible. | The real-server convergence run used form login, cookies, CSRF, staff queue/detail pages, and database assertions. It observed `WITH_STAFF/STAFF_CREATED`, one immutable STAFF version, an audited Confirmed appointment, an Accepted request, and the appointment in George's owner view. |
| Staff `staff` and owner `betty` | Extension 7a and success alternative | Staff places one audited suggestion and the owner sees it. | The same real-server journey observed `SUGGESTION_OFFERED`, exactly one Held appointment, persisted staff reason, and the slot in Betty's request and appointments views. |
| Staff `staff` | Extensions 5c, 7b, 8a, and 8b | Incomplete/malformed input is localized; allowed overrides succeed; clinic constraints refuse atomically. | MVC and real persistence evidence reproduced missing-field and malformed-input feedback, owner-window/specialty/horizon/lead overrides, and duration/grid/opening/block/overlap refusals with unchanged losing state. |
| Concurrent staff actions | Extensions 1b, 9a, and 9b | Active-request, request-version, and same-slot races have one winner. | Real two-thread transactions observed one active request, one optimistic request action, and one overlapping hold/booking winner; losing requests and capacity remained unchanged. |
| Staff `staff` | UC-4 G4 and RULE-21 raw duration boundary | Staff-authored raw values are preserved verbatim and bounded only for the staff default. | Production-class reproduction with care `GENERAL`, raw duration `0`, and a valid preferred window returned `[scheduling.staff.interpretation.duration.invalid]`. Code inspection also found the raw duration copied directly into both slot forms rather than resolved through `DurationPolicy`. This contradicts the contract; see C-1. |

The focused convergence command ran `StaffSchedulingWebTests`, `ConcurrencyInvariantTests`, `SchedulingE2eTests`, and `I18nPropertiesSyncTest` together and passed 33/0/0/0. The independent full suite passed 338/0/0/0 across 45 suites. Both runs left Git clean and preserved `data/petclinic.mv.db` at 114688 bytes, timestamp `2026-09-08T15:16:05+0200`, and SHA-256 `6bd75c0c92324582c86d98d90eada9612d7b40f8711cedf2d8f79c08e37a6820`.

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| UC-4 main step 1 | Staff opens Scheduling queue. | Dynamic-port form login reaches `/staff/queue`; queue rendering is also asserted in `StaffSchedulingWebTests.java:82`. | STRONG | yes |
| UC-4 main step 2 | Exact Needs staff and In progress partitions are shown. | `StaffSchedulingWebTests.java:82` compares exact ordered ids, required fields, rendered sections, and terminal exclusion. | STRONG | yes |
| UC-4 main step 3 | Staff selects a With staff request. | Real-server creation redirects to the exact detail route and the follow-up returns the selected request. | STRONG | yes |
| UC-4 main step 4 | Detail shows hand-off, people, text/version/origin, latest interpretation, and history. | `StaffSchedulingWebTests.java:106` and `StaffSchedulingWebTests.java:142` compare the query model and rendered current/history values, including localized weekday. | STRONG | yes |
| UC-4 main step 5 | Staff reviews or authors a complete structured interpretation with correct prefill. | Latest and declined-consent cases are rendered and asserted at `StaffSchedulingWebTests.java:106` and `StaffSchedulingWebTests.java:142`; normal real HTTP authoring passes. | STRONG | yes |
| UC-4 main step 6 | Authored values create one immutable current STAFF version without changing history. | Persistence assertions compare origin, current pointer, prior AI/STAFF rows, all shown values, and no duplicate on unchanged submission. | STRONG | yes |
| UC-4 main step 7 | Staff chooses a required-reason direct booking. | Real HTTP posts veterinarian/date/time/duration/reason; blank-reason MVC evidence proves no appointment or state change. | STRONG | yes |
| UC-4 main step 8 | Specialty match/mismatch is displayed and staff constraints are checked. | Rendered options show per-veterinarian match information; exact duration/grid/opening/block/overlap boundaries run through production validation. | STRONG | yes |
| UC-4 main step 9 | Staff confirms the booking. | Real client submits the CSRF-protected direct-booking form using the current optimistic version. | STRONG | yes |
| UC-4 main step 10 | Confirmed/Accepted outcome leaves queue and appears to owner. | Real-server and MVC tests assert appointment status/fields/reason, cleared active pet, Accepted state, queue exclusion, and owner-rendered result. | STRONG | yes |
| UC-4 extension 1a | Staff creates directly With staff without AI. | Real-server journey asserts `STAFF_CREATED`, no interpretation, and zero interpreter calls before opening detail. | STRONG | yes |
| UC-4 extension 1b | Existing active request wins, including concurrency. | Sequential HTTP and service tests return the existing id; two-thread staff and approved owner races each leave one active row under the shared unique constraint. | STRONG | yes |
| UC-4 extension 2a | Reasoned hold release deletes the hold and returns to Needs staff. | `StaffSchedulingWebTests.java:318` submits the release form and asserts deleted appointment plus `WITH_STAFF/HOLD_RELEASED`. | STRONG | yes |
| UC-4 extension 2b | Other in-progress requests are inspect-only, except held release. | Queue/detail DOM assertions compare state, origin, held slot/age, and absence of booking/suggestion actions. | STRONG | yes |
| UC-4 extension 3a | Abandoned request refuses later action without side effects. | `StaffSchedulingWebTests.java:373` abandons before the stale staff submission and compares version, appointments, queue membership, and interpreter calls. | STRONG | yes |
| UC-4 extension 5a | Unedited complete interpretation is reused. | Resubmission of identical structured values leaves the interpretation row count unchanged and retains current values. | STRONG | yes |
| UC-4 extension 5b | Authoring outside With staff is refused atomically. | Complete request action-by-state matrix invokes the production service and compares full aggregates/collaborators. | STRONG | yes |
| UC-4 extension 5c | Incomplete values identify errors and create nothing. | MVC submits missing specialty, invalid duration text, and no positive windows; response contains each localized error with no version/appointment. | STRONG | yes |
| UC-4 extension 7a | Staff suggestion creates one Held owner-visible slot. | MVC and real-server paths assert exact request/appointment state, audit reason, one hold, and owner-rendered slot. | STRONG | yes |
| UC-4 extension 7b | No staff-constraint slot explains refusal and remains unresolved. | Specific localized duration/grid/opening/block/overlap results are asserted with unchanged request/capacity. | STRONG | yes |
| UC-4 extension 8a | Owner-window, specialty, horizon, and lead mismatches do not block. | A past/out-of-window specialty-mismatching booking is shown as mismatched, then succeeds while clinic constraints hold. | STRONG | yes |
| UC-4 extension 8b | Clinic hours, effective blocks, and overlaps always block. | Boundary and rendered-refusal tests assert each production refusal and compare zero new appointment/state change. | STRONG | yes |
| UC-4 extension 9a | Stale request version refuses before side effects. | HTTP stale action plus a same-request two-thread race each observe one winner, one stale result, and exactly one appointment/version effect. | STRONG | yes |
| UC-4 extension 9b | Same veterinarian/time concurrency has one winner. | Two real transactions observe `COMPLETED` plus `UNAVAILABLE`, one Held row, and one unchanged With staff request. | STRONG | yes |
| UC-4 extension 4a | Owner abandonment removes Needs staff and blocks later action. | Abandonment test compares terminal state/version, queue exclusion, no appointment, and no interpreter call after staff submission. | STRONG | yes |
| UC-4 G1 | Queue partition/order/fields are exact. | Query-model and rendered DOM evidence compares all required values and exact membership. | STRONG | yes |
| UC-4 G2 | No assignment state; stale action cannot overwrite. | Diff/schema inspection finds no assignment/claim field; optimistic bulk claim and concurrency tests prove overwrite prevention. | STRONG | yes |
| UC-4 G3 | Staff never invokes AI/consent or mutates forbidden states. | Zero-call assertions and the complete action-by-state matrix cover all staff paths and forbidden states. | STRONG | yes |
| UC-4 G4 | STAFF versions preserve every structured field and history; latest is used. | Normal values/history pass, but raw duration `0` is rejected by `StaffInterpretationForm.parse()` before a version can be stored. | STRONG | no - C-1 |
| UC-4 G5 | Staff overrides only owner constraints. | Exact accepted override and refused clinic-constraint fixtures use the production availability and overlap path. | STRONG | yes |
| UC-4 G6 | Active-request and slot races retain one winner. | Three real two-thread tests assert the exact durable winner and unchanged loser. | STRONG | yes |
| UC-4 G7 | Owner can view/abandon With staff but cannot edit. | Owner HTTP/service evidence covers view/abandon; the lifecycle matrix refuses edit from With staff without mutation. | STRONG | yes |
| UC-4 G8 | Presentation/localization hold and every changed appointment/hold records reason. | Shared-layout and eleven-bundle scans pass; blank reasons refuse, while suggestion/booking/release store the supplied audit reason. | STRONG | yes |
| UC-4 success postcondition | Accepted/Confirmed or Suggestion offered/one Held is owner-visible. | Both branches are completed through the real server and checked by persisted values plus owner pages. | STRONG | yes |
| UC-4 minimal guarantee | Invalid, stale, abandoned, and losing actions preserve unresolved state/history. | Negative MVC, lifecycle, and concurrency evidence compares request, interpretation, appointment, and collaborator effects. | STRONG | yes |
| UC-4 Requires UC-1 | Staff actions consume approved authentication/authorization. | Every real-server journey uses seeded form login/session cookies; the full UC-1 security and role regressions pass. | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | Scheduling responsibilities stay separated; controllers contain no lifecycle or matching decisions. | Controllers delegate to read/query and transactional services; matching stays in `DefaultSlotSuggestionPort`. | PASS |
| RULE-2 | Mutations are transactional and lazy detached queries are read-only transactions. | `RequestService`, `AppointmentService`, and `StaffQueueQueryService` annotations plus rollback/no-effect tests. | PASS |
| RULE-3 | Candidate feasibility/ranking remains framework-free and deterministic. | Staff validation delegates to `FeasibilityChecker`; approved matching boundary/order suites pass. | PASS |
| RULE-4 | Exact request lifecycle with typed side-effect-free refusal. | Complete action-by-state matrix and stale/abandoned HTTP assertions. | PASS |
| RULE-5 | One appointment aggregate; released holds are deleted; other transitions refuse. | Appointment matrix, release deletion, direct booking, and suggestion evidence. | PASS |
| RULE-6 | Active-pet uniqueness, veterinarian lock/recheck, and optimistic staff version. | Entity/repository/service inspection and all three real two-thread outcomes. | PASS |
| RULE-7 | Flyway owns schema; local values and immutable versions are lossless. | Fresh migration, normal STAFF round trip, approved interpretation fidelity, and restart suites. | PASS |
| RULE-8 | Normative sets and BCrypt credentials are exact. | `SeedMigrationTests` passes by-value set and credential comparisons in the full suite. | PASS |
| RULE-9 | Single exact security chain, form login, CSRF, BCrypt, and logout. | Route inventory includes every new staff endpoint and checks anonymous/owner/staff response plus no mutation. | PASS |
| RULE-10 | `/my/**` derives owner scope from principal with indistinguishable not-found. | No owner id was added; all approved owner-isolation tests pass. | PASS |
| RULE-12 | Staff paths cannot disclose to or invoke the interpreter. | Real HTTP and negative staff tests observe zero interpreter calls; existing prompt/log scans remain green. | PASS |
| RULE-15 | Current time comes only from injected `Clock`. | Queue hold age uses injected `Clock`; fixed-clock matching and lifecycle tests pass. | PASS |
| RULE-16 | Pages use the shared PetClinic presentation and exact actions. | Both templates use the shared layout; rendered role/state action tests pass. | PASS |
| RULE-17 | Visible text and validation are keyed in all eleven bundles. | Key-set parity, source scans, malformed-field message, and German weekday rendering pass. | PASS |
| RULE-18 | Real-server and direct evidence cover the complete UC without repository impact. | Dynamic-port actor journey, evidence ledger, 338-test suite, clean Git state, and unchanged runtime database. | PASS |
| RULE-19 | Staff constraints enforce hours, effective work, grid, duration bounds, and overlap but not owner constraints. | Exact production boundary and accepted-override tests. | PASS |
| RULE-20 | One effective-availability function serves all consumers. | Staff validation calls `AvailabilityService`; approved cross-boundary matching parity remains green. | PASS |
| RULE-21 | Preserve every raw structured value; default/clamp only while building a match or staff default. | `StaffInterpretationForm.java:75` uses a positive-only parser and rejects zero at lines 93-107, although `DurationAndTimeTests.java:113` establishes zero as a raw clamped boundary. `request-detail.html:187` and `request-detail.html:204` copy the raw value directly into both staff defaults. | FAIL C-1 |
| RULE-22 | Exact rejection survives all request versions and later candidate sets. | UC-4 does not mutate rejections; approved edit/reinterpret/rematch and restart evidence stays green. | PASS |
| RULE-24 | Maven/H2-only support with isolated in-memory tests and documented runtime. | Repository, dependency, README, restart, and test-isolation suites pass; runtime DB is unchanged. | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required authentication, staff-only routes, shared shell, localization, Flyway, and H2/Maven boundary. | Full 338-test suite includes approved security, navigation, localization, seed, repository, and real-server authentication evidence. | PASS |
| UC-2 | Owner appointment/activity views consume staff outcomes. | Owner activity and real-server owner rendering regressions pass, including the staff-created Confirmed appointment. | PASS |
| UC-3 | Shared request/interpretation/hold/booking lifecycle, matching, rejection fidelity, and staff continuation. | Full owner-guided, matching, interpretation, lifecycle, concurrency, and real-server suites pass with no regression. | PASS |

## Findings

### C-1 CRITICAL - Staff raw duration is rejected and not converted into a bounded staff default

- Contract: UC-4 G4 requires that “Staff-authored versions preserve all interpretation fields defined in UC-3 G8,” and RULE-21 requires the implementation to “preserve every accepted structured value verbatim, including ... out-of-range raw duration” while making “defaults and clamping ... only while building a match or staff default.”
- Evidence: `StaffInterpretationForm.java:75` sends duration through `parsePositiveInteger`; lines 93-107 reject `0`. Direct production-class reproduction with otherwise complete values returned `scheduling.staff.interpretation.duration.invalid`, while the already-approved boundary at `DurationAndTimeTests.java:113` proves raw `0` resolves to the configured minimum rather than being invalid. For existing AI versions, `request-detail.html:187` and `request-detail.html:204` use the raw duration directly as the suggestion and booking defaults, bypassing `DurationPolicy` and clinic settings.
- Why this fails: Staff cannot persist every valid raw out-of-range interpretation value, and an existing raw out-of-range version presents an invalid slot duration rather than a bounded staff default. Normal-path tests with duration 30 or 45 stay green, so the full suite does not detect the contract breach.
- Revision outcome: accept and persist any parseable raw duration, including zero and other out-of-range values, separately validate veterinarian ids, and derive the suggestion/booking default through the shared configured `DurationPolicy`. Add outer-boundary field-by-field tests for absent, below, at, inside, at, and above bounds proving the STAFF row retains the raw value while the rendered staff default is bounded.

## Status Update

`READY_FOR_CONVERGENCE` -> `NEEDS_REVISION`; no later use case is eligible until C-1 is resolved and UC-4 converges again.

## Response to execute

REVISE UC-4: C-1 requires raw staff duration fidelity and a bounded configured staff default.
