# Convergence: UC-4 - Resolve a scheduling request that needs staff

## Summary

- Submission: `spec/checkpoints/UC-4.md` at `b5dd39d35207a76207e70ccf5502ff534d8dca68`
- Verdict: PENDING WALKTHROUGH
- Findings: 0 critical, 0 gaps, 0 protocol, 0 drift, 0 cosmetic
- Suite: 347 run, 0 failed, 0 errors, 0 skipped; focused convergence run 52/0/0/0
- Working tree impact from verification: none

## Protocol Gate

1. PASS - UC-4 is the only target and was `READY_FOR_CONVERGENCE` before this report.
2. PASS - `spec/checkpoints/UC-4.md` and the revision are committed together at `b5dd39d35207a76207e70ccf5502ff534d8dca68`, based on rejection commit `85a4217aba7b348cbfcdf80560ce0d178eae8648`.
3. PASS - UC-4 requires only UC-1, which is `APPROVED` at `0261b04` with its walkthrough passed.
4. PASS - UC-5 through UC-8 are `NOT_STARTED`; no other use case is active or ready.
5. PASS - The checkpoint maps all ten main steps, all 15 extensions, all eight guarantees, both postconditions, the UC-1 relationship, all applicable rules, validation commands, changed files, and approved-UC regression evidence.
6. PASS - Inspection of all 19 revision files found only the C-1 duration correction, its presentation/localization and tests, the requested privacy-bounded LLM interaction logging, and executor artifacts. No later-UC or unrelated change is included.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Staff `staff` | Main steps 1-10 | Opens the queue, creates or selects a request, authors an interpretation, books directly, and makes the result owner-visible. | The real-server convergence run used form login, cookies, CSRF, staff queue/detail pages, and database assertions. It observed `WITH_STAFF/STAFF_CREATED`, one immutable STAFF version, an audited Confirmed appointment, an Accepted request, and the appointment in George's owner view. |
| Staff `staff` and owner `betty` | Extension 7a and success alternative | Staff places one audited suggestion and the owner sees it. | The same real-server journey observed `SUGGESTION_OFFERED`, exactly one Held appointment, persisted staff reason, and the slot in Betty's request and appointments views. |
| Staff `staff` | Extensions 5c, 7b, 8a, and 8b | Incomplete/malformed input is localized; allowed overrides succeed; clinic constraints refuse atomically. | MVC and real persistence evidence reproduced missing-field and malformed-input feedback, owner-window/specialty/horizon/lead overrides, and duration/grid/opening/block/overlap refusals with unchanged losing state. |
| Concurrent staff actions | Extensions 1b, 9a, and 9b | Active-request, request-version, and same-slot races have one winner. | Real two-thread transactions observed one active request, one optimistic request action, and one overlapping hold/booking winner; losing requests and capacity remained unchanged. |
| Staff `staff` | UC-4 G4 and RULE-21 raw duration boundary | Staff-authored raw values are preserved verbatim and bounded only for the staff default. | Authenticated MVC submissions preserved absent, `0`, `19`, `20`, `35`, `50`, and `51` exactly with configured bounds `20/35/50`; the rendered suggestion and booking defaults were respectively `35`, `20`, `20`, `20`, `35`, `50`, and `50`. Preferred-veterinarian `0` remained independently invalid. |
| Interpreter adapter | RULE-12 LLM interaction observability | Log the allowed request payload and mapped response without raw provider JSON. | A recording `ChatModel` observed the production adapter call. Captured output contained model, temperature, system prompt, user prompt, response type, and mapped `ModelOutput`; it excluded the raw provider JSON and identity/credential fields remain absent from the prompt contract. |

The focused convergence command ran `StaffSchedulingWebTests`, `InterpreterContractTests`, `I18nPropertiesSyncTest`, `DurationAndTimeTests`, `ConcurrencyInvariantTests`, and `SchedulingE2eTests` together and passed 52/0/0/0. The independent full suite passed 347/0/0/0 across 45 suites. Both runs left Git clean and preserved `data/petclinic.mv.db` at 126976 bytes, timestamp `2026-09-08T15:40:44+0200`, and SHA-256 `745f17b36795677662e6fa7593f60e78fe2006bd39ab9428c7b488c550f9cc86`.

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
| UC-4 G4 | STAFF versions preserve every structured field and history; latest is used. | `StaffInterpretationForm.java:75-116` accepts any parseable duration independently of positive veterinarian ids. `StaffSchedulingWebTests.java:169-239` proves exact stored raw values and configured bounded defaults at the authenticated HTTP boundary. | STRONG | yes |
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
| RULE-12 | Staff paths cannot disclose to or invoke the interpreter; interpreter logs exclude identities, credentials, and raw model output. | Real HTTP and negative staff tests observe zero interpreter calls. `OllamaInterpreter.java:44-60` logs the allowed semantic payload and mapped response; `InterpreterContractTests.java:161-174` proves raw provider JSON is absent, while the existing complete-prompt test rejects identity and credential fields. | PASS |
| RULE-15 | Current time comes only from injected `Clock`. | Queue hold age uses injected `Clock`; fixed-clock matching and lifecycle tests pass. | PASS |
| RULE-16 | Pages use the shared PetClinic presentation and exact actions. | Both templates use the shared layout; rendered role/state action tests pass. | PASS |
| RULE-17 | Visible text and validation are keyed in all eleven bundles. | Key-set parity, source scans, malformed-field message, and German weekday rendering pass. | PASS |
| RULE-18 | Real-server and direct evidence cover the complete UC without repository impact. | Dynamic-port actor journey, evidence ledger, 347-test suite, clean Git state, and unchanged runtime database. | PASS |
| RULE-19 | Staff constraints enforce hours, effective work, grid, duration bounds, and overlap but not owner constraints. | Exact production boundary and accepted-override tests. | PASS |
| RULE-20 | One effective-availability function serves all consumers. | Staff validation calls `AvailabilityService`; approved cross-boundary matching parity remains green. | PASS |
| RULE-21 | Preserve every raw structured value; default/clamp only while building a match or staff default. | `StaffInterpretationForm.java:75-116` preserves every parseable integer. `StaffQueueQueryService.java:91-101` resolves only the staff slot default through `DurationPolicy` and current clinic settings; the template consumes that derived value. Authenticated boundary tests cover absent, zero, below, minimum, inside, maximum, and above values by exact persisted/rendered value. | PASS |
| RULE-22 | Exact rejection survives all request versions and later candidate sets. | UC-4 does not mutate rejections; approved edit/reinterpret/rematch and restart evidence stays green. | PASS |
| RULE-24 | Maven/H2-only support with isolated in-memory tests and documented runtime. | Repository, dependency, README, restart, and test-isolation suites pass; runtime DB is unchanged. | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required authentication, staff-only routes, shared shell, localization, Flyway, and H2/Maven boundary. | Full 347-test suite includes approved security, navigation, localization, seed, repository, and real-server authentication evidence. | PASS |
| UC-2 | Owner appointment/activity views consume staff outcomes. | Owner activity and real-server owner rendering regressions pass, including the staff-created Confirmed appointment. | PASS |
| UC-3 | Shared request/interpretation/hold/booking lifecycle, matching, rejection fidelity, and staff continuation. | Full owner-guided, matching, interpretation, lifecycle, concurrency, and real-server suites pass with no regression. | PASS |

## Findings

None. Prior C-1 is resolved by the exact persistence and bounded-default evidence above.

## Walkthrough

Result: pending user confirmation.

1. Start the application with `./mvnw spring-boot:run`, sign in as `staff` / `staff123`, and open Scheduling queue. Confirm Needs staff contains only With staff requests oldest first with reason/origin, while In progress contains every other active request with state and any held-slot age.
2. Create a request for a pet. Confirm it opens directly in With staff with reason Request created by staff, appears in Needs staff, and no consent or AI action is offered.
3. Open its detail. Confirm owner, pet, hand-off reason, request version, current origin, latest interpretation, and immutable history are visible. Submit incomplete interpretation values and confirm the page identifies them while creating no usable version or booking action.
4. Author a complete interpretation with raw duration `0` and a valid preferred or allowed window. Confirm the interpretation field remains `0`, while the suggestion and booking duration fields use the clinic minimum rather than `0`. Confirm veterinarian specialty match/mismatch labels are visible.
5. Direct-book a clinic-valid veterinarian/date/time with a reason. Confirm the request becomes Accepted, disappears from the active queue, and the Confirmed appointment is visible after signing in as the corresponding owner.
6. For another With staff request, place a clinic-valid suggestion with a reason. Confirm it moves to In progress as Suggestion offered with exactly one held slot; sign in as the owner and confirm the standard accept, reject, staff-help, edit, and abandon actions are shown.
7. Sign back in as staff, inspect that held request, and release its hold with a reason. Confirm the hold disappears and the request returns to Needs staff with reason Hold released by staff. Report PASS, or provide the step number and observed mismatch.

## Status Update

`READY_FOR_CONVERGENCE` -> `PENDING_WALKTHROUGH`; no later use case is eligible until the walkthrough is confirmed.

## Response to execute

PENDING WALKTHROUGH: automated UC-4 convergence passed; confirm the seven-step staff/owner walkthrough.
