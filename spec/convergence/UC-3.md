# Convergence: UC-3 - Schedule a pet appointment through the guided flow

## Summary

- Submission: `spec/checkpoints/UC-3.md` at `f9b39dbb8b6a05a8f0509d7425e28af015e384b6`
- Verdict: APPROVE
- Findings: 0 critical, 0 gaps, 0 protocol, 0 drift, 0 cosmetic
- Suite: 348 run, 0 failed, 0 errors, 0 skipped
- Working tree impact from verification: none

## Protocol Gate

1. PASS - UC-3 is the only target and was `READY_FOR_CONVERGENCE` before this report.
2. PASS - `spec/checkpoints/UC-3.md` and the corrective implementation are committed together at `f9b39dbb8b6a05a8f0509d7425e28af015e384b6`, based on `c3cc9075b4502993c7d0b0c96ec7142952476436`.
3. PASS - UC-3 requires only UC-1, which is `APPROVED` at `0261b04` with its walkthrough passed.
4. PASS - UC-2 and UC-4 are `APPROVED`; UC-5 through UC-8 are `NOT_STARTED`; no other use case is active or ready.
5. PASS - The checkpoint maps all ten main steps, all 28 extensions, all 19 guarantees, both postconditions, the UC-1 relationship, all 22 applicable rules, validation commands, changed files, and approved-UC regression evidence.
6. PASS - Inspection of every file in `c3cc907..f9b39db` found only the corrective provider DTO/schema and mapping, domain window invariants, deterministic upcoming-weekday prompt values, LLM interaction logging, contract tests, and UC-3 evidence. No later-UC or unrelated behavior is included.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Owner `george` | Main steps 1-10 | Creates a request for an owned pet, consents, reviews the immutable interpretation, receives one held suggestion, accepts it, and sees the confirmed appointment. | The 14-test real-server suite used form login, cookies, CSRF, HTML pages, external polling, and database assertions on a dynamic port; the complete owner journey passed with the request Accepted, hold converted to Confirmed, active pet cleared, and appointment rendered. |
| Owner `george` | Extensions 1a-3b | Duplicate/cross-owner creation and action, omitted/non-English reason, edit/decline/abandon, duplicate consent, and wrong-state actions follow their specified branches without forbidden mutation. | Real HTTP and transactional/concurrency tests observed the specified redirect, indistinguishable 404, preserved input/default behavior, consent branches, one accepted job, and unchanged full snapshots for refusals. |
| AI interpreter substitute | Extensions 4a-5b | Late, semantic-failure, unavailable, startup-recovery, unmatched-specialty, and unknown-veterinarian results follow the declared lifecycle. | Deterministic adapter and persistence tests covered every outcome without a live Ollama call; late results were discarded and startup `INTERPRETING` rows moved to staff without resubmission. |
| Owner `george` | Extensions 6a-9a | Duration clamping, edit, staff hand-off, no-slot, reject/re-suggest, hold release/invalidation, and stale acceptance preserve histories and holds exactly as specified. | Boundary, persistence, service, and real-server tests verified raw/effective duration, exact durable rejection, hold deletion, hand-off reasons, replacement suggestions, and acceptance-time overlap recheck. |
| Owner and staff | G14 continuations | Real-server coverage continues through staff suggestion, staff booking after declined consent, completion, and no-show. | `SchedulingE2eTests` passed all 14 journeys on dynamically allocated embedded-server ports, including owner/staff role boundaries and consequential state. |
| AI interpreter | Exact reported failure phrase | `Schedule a visit for next Thursday` should produce an absolute Thursday window with exact clinic-local time values. | The verifier inspected the provider regex schema and deterministic `upcomingWeekdayDates` path, then reran the clean contract/full suites. Executor live evidence additionally mapped one allowed window on Thursday `2026-09-10`, `09:00`-`17:00`; live output is supporting evidence, not a substitute for the deterministic checks. |

The convergence run used a clean build with dynamic localhost ports enabled. It removed all stale compiled classes before running and made no live Ollama call.

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| UC-3 main step 1 | Owner selects an owned pet and enters the request text. | `SchedulingE2eTests.ownerObtainsAppointmentThroughSuggestion` drives the authenticated `/my/**` form and persists the exact pet/text; ownership failures are reproduced separately. | STRONG | yes |
| UC-3 main step 2 | Consent disclosure precedes the AI action. | `ConsentAndSuggestionWebTests` asserts the exact disclosed data and actions, while the service tests prove that editing stays Awaiting consent and declining invokes no interpreter. | STRONG | yes |
| UC-3 main step 3 | One asynchronous interpretation job is accepted. | `InterpretationConcurrencyTests` races duplicate consent and observes one call/version; the capacity test observes two running jobs and a queued third. | STRONG | yes |
| UC-3 main step 4 | Interpreting page exposes state-only polling and Refresh. | `InterpretationWebTests` compares the exact state JSON, owner scope, rendered status page, external script, and visible Refresh link. | STRONG | yes |
| UC-3 main step 5 | A usable immutable AI version is stored losslessly. | `OllamaInterpretationResponse` validates and maps wire values deterministically; `InterpretationPersistenceTests` round-trips every structured field, raw JSON, model tag, prompt version, origin, and window by value. | STRONG | yes |
| UC-3 main step 6 | Complete interpretation is rendered read-only in clinic-local formats. | `InterpretationWebTests` asserts every displayed field, absence, clamp note, action, and localized label against the stored version. | STRONG | yes |
| UC-3 main step 7 | Owner confirms the current interpretation. | The real-server success journey submits confirmation through CSRF and asserts the consequential request transition before suggestion. | STRONG | yes |
| UC-3 main step 8 | Exactly one best candidate is held and shown with application-authored rank reason. | `SlotSuggestionPortTests`, `SlotRankerTests`, and `ConsentAndSuggestionWebTests` verify the exact candidate, persisted hold, one suggestion, no calendar, and localized reason key. | STRONG | yes |
| UC-3 main step 9 | Owner accepts the still-available held slot. | The real-server journey submits acceptance and asserts request, active-pet, and appointment state; overlap recheck evidence covers the stale branch. | STRONG | yes |
| UC-3 main step 10 | Confirmed appointment is visible in My appointments. | `SchedulingE2eTests` fetches the authenticated page and verifies the confirmed veterinarian, date, time, duration, and pet after acceptance. | STRONG | yes |
| UC-3 extension 1a | An existing active request wins without creating a duplicate. | Real HTTP redirects to the existing request; `ConcurrencyInvariantTests` observes exactly one row from two concurrent creations. | STRONG | yes |
| UC-3 extension 1b | Foreign and unknown pets are indistinguishable and unchanged. | `SchedulingE2eTests` compares the complete 404 response and guarded-table snapshots for foreign and unknown identifiers. | STRONG | yes |
| UC-3 extension 1c | Missing visit reason is permitted and defaults only for matching/display. | `InterpretationWebTests` verifies null stored reason fields and general/30-minute effective defaults without altering the immutable version. | STRONG | yes |
| UC-3 extension 1d | Non-English text is accepted unchanged with the English expectation. | Real HTTP persists the exact submitted text and renders the localized expectation without language detection or rejection. | STRONG | yes |
| UC-3 extension 2a | Editing before consent retains Awaiting consent. | `OwnerTransitionServiceTests` compares state and exact revised text with no job/version/appointment. | STRONG | yes |
| UC-3 extension 2b | Declined consent routes to staff without AI. | Service and real-HTTP evidence asserts With staff/CONSENT_DECLINED and no interpreter, appointment, or version. | STRONG | yes |
| UC-3 extension 3a | Repeated consent accepts at most one job. | The two-thread consent test observes one interpreter invocation and one immutable version. | STRONG | yes |
| UC-3 extension 4a | Abandoning during interpretation discards late output. | Real HTTP and persistence tests release the action, preserve Abandoned, and prove the late result adds no version or appointment. | STRONG | yes |
| UC-3 extension 4b | Every unusable semantic result becomes Interpretation failed. | The interpretation suite drives malformed/schema, offset-bearing time, missing-time, and impossible-window results; exact format failures are classified once as unparseable without a hold or raw-output log. | STRONG | yes |
| UC-3 extension 4c | Rephrase requires fresh consent and recommends staff after the third failure. | Web and outcome tests assert the new text/state, retained failures/versions, fresh consent, rephrase availability, and third-attempt recommendation. | STRONG | yes |
| UC-3 extension 4d | Owner can route a failed interpretation to staff. | `OwnerRequestOutcomeTests` asserts With staff with the owner-choice reason and no hold/appointment. | STRONG | yes |
| UC-3 extension 4e | Transport, connect, and deadline failures make one call and route to staff. | `InterpreterContractTests` and real HTTP assert no retry, With staff/AI_UNAVAILABLE, and no partial interpretation. | STRONG | yes |
| UC-3 extension 4f | Startup recovery routes pending interpretation to staff without resubmission. | Startup and close/reopen tests load a durable Interpreting row, observe With staff/AI_UNAVAILABLE, and record zero interpreter calls. | STRONG | yes |
| UC-3 extension 5a | `OTHER` specialty is retained and routes to staff unmatched. | Persistence, outcome, and real-server tests assert exact `OTHER` data, With staff/UNMATCHED_SPECIALTY, and no hold. | STRONG | yes |
| UC-3 extension 5b | Unknown preferred veterinarian is treated as absent. | `InterpretationPersistenceTests` supplies an unknown id and compares the stored/read interpretation with no preferred veterinarian. | STRONG | yes |
| UC-3 extension 6a | Raw duration is preserved and effective duration is clamped/defaulted. | `DurationAndTimeTests` covers below/at/inside/at/above bounds; web evidence separately renders raw and effective values. | STRONG | yes |
| UC-3 extension 6b | Editing after interpretation releases a hold and retains histories. | Service and suggestion tests compare deleted hold, Awaiting consent, revised text, and unchanged earlier versions/rejections. | STRONG | yes |
| UC-3 extension 6c | Owner can route an interpreted request to staff. | `OwnerRequestOutcomeTests` asserts With staff/OWNER_REQUESTED_STAFF and preserved current interpretation without a hold. | STRONG | yes |
| UC-3 extension 7a | No candidate routes to staff with explanation and no hold. | Outcome tests exhaust candidates and assert With staff/NO_SLOTS_AVAILABLE, rendered explanation, abandon-only actions, and absent hold. | STRONG | yes |
| UC-3 extension 8a | Rejecting records the exact slot and holds a different next candidate. | Suggestion and real-server tests compare the immutable rejection tuple, deleted old hold, and different replacement hold. | STRONG | yes |
| UC-3 extension 8b | Asking staff for help removes the suggestion hold. | Outcome tests assert hold deletion and With staff/OWNER_REQUESTED_STAFF with unchanged histories. | STRONG | yes |
| UC-3 extension 8c | Editing from a suggestion returns through fresh consent. | Real HTTP asserts deleted hold, revised text, Awaiting consent, no new AI call until consent, and retained history. | STRONG | yes |
| UC-3 extension 8d | Staff release requires and stores a reason. | Service and real-server tests prove blank reason refusal is atomic, while a reason deletes the hold and records With staff/HOLD_RELEASED_BY_STAFF. | STRONG | yes |
| UC-3 extension 8e | Schedule invalidation deletes the hold and records schedule changed. | Suggestion-port tests change effective availability and assert deleted hold and With staff/SCHEDULE_CHANGED atomically. | STRONG | yes |
| UC-3 extension 9a | Unavailable acceptance transparently advances or hands off. | Port and real-server tests create an acceptance-time overlap, assert old-hold deletion, no error page, and the exact replacement/no-slot continuation. | STRONG | yes |
| UC-3 extension 2c | Abandon works in every permitted owner state. | The state-matrix test covers Awaiting consent, Interpretation failed, Interpreted, Suggestion offered, and With staff, including hold deletion and active-pet clearing. | STRONG | yes |
| UC-3 extension 3b | Suggestion generation from every wrong state is refused atomically. | Domain/service matrices and real HTTP compare complete request/version/hold/rejection/appointment snapshots for each refused state. | STRONG | yes |
| UC-3 G1 | One suggestion is shown and the clinic calendar is never exposed. | Rendered DOM asserts exactly one suggestion and no calendar/list of alternatives. | STRONG | yes |
| UC-3 G2 | Candidate feasibility implements every boundary. | Plain-Java feasibility and boundary suites compare exact 15-minute-grid candidates across horizon, windows, exclusions, hours, work, overlap, specialty, and rejection inputs. | STRONG | yes |
| UC-3 G3 | Recurring/concrete/day-part windows resolve to exact clinic-local values. | `PromptBuilder` derives the strict next occurrence of all seven weekdays from the request date; the system prompt requires copying those values for one-off weekday phrases, while the provider schema and mapper distinguish absolute dates from recurring uppercase weekdays and require offset-free `HH:mm`. | STRONG | yes |
| UC-3 G4 | Unmentioned time is infeasible and exclusions only subtract. | Boundary tests assert empty feasibility without positive windows and exact set subtraction for exclusions. | STRONG | yes |
| UC-3 G5 | Ranking uses the stated lexicographic order without weights. | `SlotRankerTests` independently varies preferred window, veterinarian, start, workload, and id and compares exact order. | STRONG | yes |
| UC-3 G6 | Rank reason is localized application text. | Ranker/suggestion/web tests assert message keys for preferred/allowed and veterinarian-honored combinations and reject model prose. | STRONG | yes |
| UC-3 G7 | Holds have no timer and overlapping attempts have one winner. | Persistence and two-thread transaction tests observe exactly one hold/booking winner for the same veterinarian/time. | STRONG | yes |
| UC-3 G8 | Stored interpretation fidelity is exact and versions are immutable. | Round-trip and edit/reinterpret tests compare every field/list/value and retain prior AI versions unchanged. | STRONG | yes |
| UC-3 G9 | AI call is schema-constrained, temperature zero, enumerated, and private. | Adapter tests inspect the generated regex schema, prompt, model, temperature, and one-call behavior. Log capture proves the sent payload and mapped response are logged while raw provider JSON and invalid temporal output are absent. | STRONG | yes |
| UC-3 G10 | Every scheduling page displays urgent-care call guidance without detection. | Rendered-page inventory verifies the shared urgent-care fragment in every request state and absence of an urgency field/classifier. | STRONG | yes |
| UC-3 G11 | Request and appointment lifecycle matrices reject every unlisted transition. | Complete real-state matrices exercise allowed exits and compare unchanged snapshots for every refusal below MVC. | STRONG | yes |
| UC-3 G12 | Matching uses the injected clinic clock and remains DST-stable. | Fixed-clock integration tests pin 2026-09-07 09:00 Europe/Amsterdam, exercise the seeded exception, and cover DST boundaries. | STRONG | yes |
| UC-3 G13 | Tests use deterministic interpretation and cover all outcomes without Ollama. | The clean 348-test convergence run contains no live-smoke class and uses deterministic usable/failure/unavailable/late/startup modes; no test contacted Ollama. | STRONG | yes |
| UC-3 G14 | Required actor continuations run through a real dynamic-port server. | All 14 `SchedulingE2eTests` passed with real clients, sessions, CSRF, rendered HTML, and database checks, including staff/completion/no-show continuations. | STRONG | yes |
| UC-3 G15 | Active-request and overlapping-slot races each have one winner. | `ConcurrencyInvariantTests` runs real concurrent transactions and asserts one success plus one typed loser and one durable winner. | STRONG | yes |
| UC-3 G16 | Tests use isolated in-memory H2 and do not touch runtime data. | Unique in-memory JDBC URLs were observed; `data/petclinic.mv.db` remained 122880 bytes with SHA-256 `86e6f652cd4f220a089378fba479946a055a9de04b5270d7f6d75be9199831b6`, and Git stayed clean. | STRONG | yes |
| UC-3 G17 | Presentation, polling, localization, and README requirements hold. | Shared-shell DOM, external-script/Refresh, exact eleven-bundle parity, hard-coded-text scans, and README boundary checks passed. | STRONG | yes |
| UC-3 G18 | Runtime scheduling data survives file-backed restart. | `RuntimePersistenceRestartTests` closes and reopens the application around exact request, version, rejection, appointment, and configuration values. | STRONG | yes |
| UC-3 G19 | Two interpretation jobs run while later jobs wait. | Latch-based concurrency evidence observes two active calls, a queued third, and eventual completion without pending-limit failure. | STRONG | yes |
| UC-3 success postcondition | Accepted request, Confirmed appointment, cleared active pet, and owner-visible appointment. | The real-server main journey asserts every field and page after acceptance. | STRONG | yes |
| UC-3 minimal guarantee | Every refusal/failure preserves declared durable state and excludes forbidden side effects. | Cross-owner, state-matrix, concurrency, late-result, no-slot, and abandonment evidence compares full affected-table snapshots and collaborator calls. | STRONG | yes |
| UC-3 Requires UC-1 | Every actor uses the approved authenticated role boundary. | Real owner/staff journeys use UC-1 form login and session authorization; all UC-1 security, navigation, localization, repository, and stock regressions pass in the full suite. | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | Domain packages; controllers contain no lifecycle or matching decisions. | Package/diff inspection and controller-to-transactional-service paths; lifecycle/matching matrices exercise the shared services. | PASS |
| RULE-2 | Each mutation is one transaction; detached lazy queries are read-only transactions. | `RequestService`, `AppointmentService`, interpretation/suggestion services, and complete rollback snapshots. | PASS |
| RULE-3 | Matching is framework-free and implements G2-G6 without weights. | Plain-Java `FeasibilityChecker`/`SlotRanker` and exact boundary/order suites. | PASS |
| RULE-4 | Exact request lifecycle with typed, side-effect-free refusals. | `RequestStateTransitionTests` complete action-by-state matrix. | PASS |
| RULE-5 | One appointment aggregate; released holds are deleted; invalid transitions are refused. | `AppointmentStateTransitionTests`, hold-deletion checks, and appointment snapshot evidence. | PASS |
| RULE-6 | Active-pet uniqueness, veterinarian locking/recheck, and optimistic staff version. | Migration/entity inspection plus real-transaction races and stale-version tests. | PASS |
| RULE-7 | Flyway-only schema and lossless local/interpretation persistence. | Fresh migration, field-perfect round trip, and restart suites. | PASS |
| RULE-8 | Normative sets and BCrypt credentials are exact. | `SeedMigrationTests` compares every required row/value and all 12 passwords. | PASS |
| RULE-9 | One exact path security chain with form login, CSRF, BCrypt, and logout. | Route-inventory and real-server security journeys cover anonymous/owner/staff responses and no mutation. | PASS |
| RULE-10 | `/my/**` scope derives from the principal with indistinguishable 404. | `AuthenticatedOwnerService`, cross-owner real HTTP, equal-body checks, and unchanged snapshots. | PASS |
| RULE-11 | Spring AI 2.0.1 Ollama adapter and exact properties/timeouts/schema/no-retry. | The provider DTO supplies exact day/time regex patterns to provider structured output; contract tests inspect those patterns, deterministic mapping, properties, request factory, prompt, and call count. | PASS |
| RULE-12 | Interpreter port sends only free text and enumerated clinic context and does not log raw model output. | Exact prompt comparison excludes prohibited identity fields; captured logs contain the sent payload and mapped response but exclude valid and invalid raw provider JSON. | PASS |
| RULE-13 | One asynchronous job, two-worker capacity, no retry, exact outcomes and recovery. | Latch, timeout/transport, semantic-failure, late-result, and startup-recovery suites. | PASS |
| RULE-14 | Exact state-only scoped JSON plus external polling and Refresh. | Byte-for-byte JSON, owner-isolation, DOM, and static-resource tests. | PASS |
| RULE-15 | Current time comes only from injected `Clock`. | Static scan and fixed/mutable-clock exception, horizon, and DST tests. | PASS |
| RULE-16 | One PetClinic layout with exact state/role actions. | Template inspection, rendered-page DOM suite, and the user-confirmed walkthrough below. | PASS |
| RULE-17 | All visible text is keyed in all eleven bundles. | Exact bundle parity and Java/template hard-coded-visible-text scans. | PASS |
| RULE-18 | Real-server and direct evidence cover the complete UC without repository impact. | Evidence ledger, dynamic-port journeys, clean 348-test full suite, clean Git state, and unchanged runtime database. | PASS |
| RULE-20 | One effective-availability calculation serves all consumers. | Shared `AvailabilityService` inspection and cross-boundary fixture parity tests. | PASS |
| RULE-21 | Immutable raw interpretation; defaults/clamps only at consumption. | Field-perfect round trips and duration boundary/rendering evidence. | PASS |
| RULE-22 | Exact rejections survive edits/versions and never return. | End-to-end reject/edit/reinterpret/rematch persistence evidence. | PASS |
| RULE-24 | Maven/H2-only, file-backed runtime, in-memory tests, complete README. | Repository inventory, dependency/property checks, restart/isolation suites, and README assertions. | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| UC-1 | Required authentication plus shared security, owner scoping, presentation, localization, Flyway, and Maven/H2 boundary. | The 348-test full suite includes approved UC-1 real-server authentication, route matrix, presentation shell, localization, seed, repository-scope, and stock PetClinic tests with zero failures/errors/skips. | PASS |
| UC-2 | Shared owner workspace, appointment projection, security, presentation, and persistence. | The clean full suite reran the approved UC-2 owner activity and cancellation boundaries with zero failures/errors/skips. | PASS |
| UC-4 | Shared interpretation values, duration policy, queue, matching, logging, and staff booking/suggestion paths. | The clean full suite reran the approved UC-4 staff web, concurrency, real-server, and duration tests with zero failures/errors/skips. | PASS |

## Findings

None.

## Walkthrough

Prior user result: PASS. The corrective submission changes no template, route, action, message, or rendered value, so the approved walkthrough remains applicable.

1. Start the application with `./mvnw spring-boot:run`, sign in as `george` / `george123`, open My pets, and start scheduling for one of George's pets. Confirm the shared PetClinic layout shows `george`, Logout, and the urgent-care instruction to call.
2. Enter an English description with a reason, preferred days/times, and an optional veterinarian. Confirm the review page shows the exact text, the English-input expectation, the precise AI disclosure, Edit, Continue with AI, Continue without AI, and Abandon.
3. Choose Continue with AI. Confirm the Interpreting page shows Refresh and updates without displaying interpretation data in the polling response.
4. When interpretation completes, confirm every structured field is read-only and uses localized clinic-local labels/formats. Confirm the page still offers Edit, Ask staff for help, Abandon, and the action to continue.
5. Confirm the interpretation. Verify exactly one suggestion appears, no clinic-wide availability calendar is visible, and the veterinarian, date, start, duration, and localized rank explanation are shown.
6. Choose another time once. Confirm the prior exact slot disappears and a different single suggestion is shown. Accept it and confirm My appointments shows the resulting Confirmed appointment.
7. Start a second request for another owned pet, choose Continue without AI, and confirm the request moves to staff without showing an interpretation or suggestion. Start another request and confirm Abandon leaves no further owner action.
8. Report PASS, or provide the step number and observed mismatch.

## Status Update

`READY_FOR_CONVERGENCE` -> `APPROVED`; UC-5, UC-6, UC-7, and UC-8 are eligible.

## Response to execute

APPROVED
