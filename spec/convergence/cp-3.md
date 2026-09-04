# Convergence: cp-3 — Staff resolves queued requests

## Summary

- Checkpoint: cp-3 (phase-3, 6/6 tasks claimed complete); executor report: `spec/checkpoints/cp-3.md` at `903474b`.
- Verdict: **REJECT**.
- Counts: **4 critical, 6 gaps, 4 protocol, 0 drift, 0 cosmetic**; carried forward: none from this phase.
- Suite run by converge: **265 run / 4 failures / 0 errors / 0 skipped**. The failing methods are
  `ArchitectureBoundaryTests.productionCodeSatisfiesNoControllerDirectRepositoryAccess`,
  `LocalizationKeyTests.everyReferencedTemplateKeyExistsInTheDefaultBundle`,
  `LocalizationKeyTests.featureTemplatesEmitNoUnkeyedTextOrVisibleAttributes`, and
  `I18nPropertiesSyncTest.checkNonInternationalizedStrings`.
- Plan guards: **PASS**, 6/0/0/0 from explicit
  `./mvnw -q -Dtest=RouteInventoryTest,ArtifactInventoryTest,AcTagCoverageTest test`.
- Working tree was clean before verification and remained clean after the suite and runtime attempt.
- Runtime: **FAIL**. `./mvnw -DskipTests spring-boot:run` logs application-context failure for
  `ollamaRequestInterpreter`: `No default constructor found`. Devtools lets Maven exit zero, but no usable application
  context or HTTP server exists.

## Protocol Gate

Items 1 and 2 pass: the report is committed, and the six task commits are separate and ordered. Items 5–7 have
blocking findings.

| Task | Commit | Files in commit match artifact + supporting | Artifacts by exact name | Gate evidence in `status.md` | Blocker due / raised |
|---|---|---|---|---|---|
| task-3.1 | `e96b0f9` | yes by path, but delivered controllers violate RULE-2 | yes, 6/6 | present, but falsely says RULE-2 and localization pass | due / not raised |
| task-3.2 | `df4a516` | no: production `SchedulingRequestRepository.java` is outside `artifact`/`modifies` | yes, 2/2 | present and discloses the extra file only after execution | due / not raised |
| task-3.3 | `0b56548` | no: all 11 bundles belong to task-3.1 and were modified here | yes, 4/4 | present and discloses the scope bleed | due / not raised |
| task-3.4 | `f959d24` | yes | yes, 2/2 | stale: names nonexistent `StaffSuggestTests.java` and calendar-pick assertions not in the commit | due / not raised |
| task-3.5 | `760c363` | yes | yes, 3/3 | stale base/file list and contradicts the actual leave-open assertion | due / not raised |
| task-3.6 | `3a110a0` | yes (declared E2E plus supporting route test) | yes, 1/1 | stale base/file list; says the suite is green | due / not raised |

`spec/plan-review.md` is PASS. All declared phase-3 artifacts exist at their exact paths. No workaround/disabled-test
entry provides a waiver for the findings below.

## Runtime Reproduction

The executor report says these URLs were exercised in a running application. Converge could not reach the first URL:
the default Ollama application context fails before the web server starts.

| Actor | URL | Executor reported | Converge observed |
|---|---|---|---|
| application | startup | context implicitly running | **FAIL** — `ollamaRequestInterpreter`: `No default constructor found` |
| staff | `/staff/queue`, `/staff/requests/{id}`, `/staff/requests/{id}/interpretation` | 200 / 200 / 200 | not reachable; context failed |
| staff | interpretation, suggest, release-hold POSTs | 302 | not reachable; context failed |
| staff | `/staff/appointments/new`, `/staff/appointments` | 200 / 302 | not reachable; context failed |
| owner | `/my/requests/{id}`, accept/another POSTs, `/my/appointments` | 200 / 302 / 200 | not reachable; context failed |

The phase routes do resolve under the test profile because `src/test/resources/application.properties:8` selects the
stub provider and therefore excludes the broken Ollama bean. That is test evidence, not runtime evidence.

## Evidence Ledger

| AC | Pattern | Claimed in checkpoint | Evidence located | Strength | Verified |
|---|---|---|---|---|---|
| AC-55 | event-driven | `StaffInterpretationTests.staffEditCreatesNewVersionWithoutOverwrite_AC55` | `StaffInterpretationTests.java:120-175`; full persisted shape at `Interpretation.java:44-88` | **WEAK** — prior AI specialty, preferred vet, `cannotInterpret`, created time, and windows are not pinned | no — G-1 |
| AC-56 | authorization/state-driven | `StaffInterpretationTests.provenanceVisibleOnlyToStaff_AC56` | `StaffInterpretationTests.java:178-217` checks all three values present for staff and absent for owner | **STRONG** | yes |
| AC-84 | state-driven | `StaffQueueTests.needsStaffOldestFirstWithTrigger_AC84` | service order/trigger assertions `StaffQueueTests.java:133-152`; HTTP assertions `:154-163` | **WEAK** — rendered order is not asserted | no — G-2 |
| AC-85 | state-driven | `StaffQueueTests.allOpenContainsEveryNonTerminalStateHoldAndAge_AC85` | service assertions `StaffQueueTests.java:213-233`; HTTP assertions `:235-244`; template `staff/queue.html:49-72` | **WEAK** — HTTP checks only state names, not held slot, age, or release action | no — G-3 |
| AC-86 | event-driven | `StaffQueueTests.releaseHoldMovesToStaffAndClearsTuple_AC86` | `StaffQueueTests.java:265-290` pins state, all three null hold fields, persistence, reason, actor, and event | **STRONG** | yes |
| AC-89 | event-driven | two methods in `StaffInterpretationTests` | HTTP/read-back/versioning `StaffInterpretationTests.java:220-304`; declined-consent requirement `:306-336` | **STRONG** | yes |
| AC-91 | event-driven | `StaffSuggestionTests.ownerAcceptRejectUsesSameRules_AC91` | accept `StaffSuggestionTests.java:161-180`; one rejection scope with event-only assertion `:182-211` | **WEAK** — no all-scope parity and no rejection state/new-hold/release assertions | no — G-4 |
| AC-92 | ubiquitous | `StaffDirectBookingTests.bookingNeedsNoInterpretation_AC92` | real staff HTTP without interpretation/request `StaffDirectBookingTests.java:105-134`; direct service has no interpretation precondition `StaffBookingService.java:67-119` | **STRONG** | yes |
| AC-93 | event-driven/state matrix | `StaffDirectBookingTests.attachAcceptsReleasesAndLogsFromEveryNonTerminalState_AC93` | six-state matrix `StaffDirectBookingTests.java:138-193` | **WEAK** — held duration is not asserted null and event count is not asserted exactly one | no — G-5 |
| AC-94 | unwanted behavior/state matrix | `StaffDirectBookingTests.leaveOpenPreservesRequestInEveryNonTerminalState_AC94` | test `StaffDirectBookingTests.java:197-258`; mutation `StaffBookingService.java:109-114`, `RequestLifecycleService.java:248-253,268-291` | **IMPOSSIBLE** — implementation and test deliberately mutate/log the request while claiming untouched | no — C-4 |
| AC-121 | ubiquitous audit invariant | `StaffInterpretationTests.timelineShowsEveryTransitionAndAction_AC121` | sample timeline `StaffInterpretationTests.java:338-410`; required tuple `criteria.md:717-721` | **WEAK** — six sample events; actor/reason/payload/timestamp are not pinned for every row or every action | no — G-6 |

Only **4 of 11** phase ACs have strong evidence.

## Findings

### Critical

#### C-1 — Default application context does not start

- **Spec:** cp-3 criterion: “the application context starts without ambiguous-mapping errors”; RULE-17 selects Ollama
  by default.
- **Code evidence:** `OllamaRequestInterpreter.java:36-45` declares two constructors but marks neither for Spring
  injection. With multiple constructors Spring looks for a no-arg constructor and fails.
- **Test gap that let it pass:** `src/test/resources/application.properties:5-8` pins `scheduling.ai.provider=stub`, so
  every phase integration test excludes the default Ollama bean. The checkpoint's focused command at
  `spec/checkpoints/cp-3.md:94-99` has no default-provider context test.
- **Owner:** task-1.6 (runtime adapter), exposed by cp-3.
- **Resolution:** `REVISE: task-1.6 - make the intended Ollama injection constructor unambiguous and add a context test
  that activates the shipped default provider without calling a live model.`

#### C-2 — Phase controllers bypass services and call repositories directly

- **Spec:** RULE-2(b): any `@Controller` calling a `*Repository` directly must fail the build; task-3.1 additionally
  says every mapping “uses services rather than repositories.”
- **Code evidence:** `StaffBookingController.java:41-52,58-80` injects/calls three repositories;
  `StaffRequestController.java:54-65,68-85,137-139` injects/calls two repositories.
- **Test evidence:** `ArchitectureBoundaryTests.java:96-104` enforces the rule and the full suite reports 17 violations.
  The checkpoint ran only six phase classes (`spec/checkpoints/cp-3.md:94`) and omitted this test.
- **Owner:** task-3.1.
- **Resolution:** `REVISE: task-3.1 - move request/vet/pet read operations behind services, remove repository
  dependencies from both controllers, and run the architecture test in checkpoint validation.`

#### C-3 — Phase-3 pages violate the localization contract

- **Spec:** RULE-41 requires every introduced visible string to use a key present in all 11 bundles; task-3.1 requires
  every phase-3 key in all bundles and localized pages.
- **Code evidence:** `staff/bookingForm.html:8` references missing `openRequests`; `staff/interpretationForm.html:42-43,59,80`
  emits literal option/help text.
- **Test evidence:** `LocalizationKeyTests.java:54-66,88-112` and `I18nPropertiesSyncTest.java:40-85` catch the defects;
  all three methods fail in the full suite but were omitted from the focused checkpoint command.
- **Owner:** task-3.1 (bundle ownership), task-3.3 and task-3.5 (templates).
- **Resolution:** `REVISE: task-3.1/task-3.3/task-3.5 - key every listed literal, add every referenced key to all
  11 bundles in the owning task, and include both localization classes in validation.`

#### C-4 — Leave-open booking mutates the request

- **Spec:** AC-94 and RULE-32 require *leave open* to leave the request untouched.
- **Code evidence:** `StaffBookingService.java:109-114` invokes `staffBookLeaveOpen`; `RequestLifecycleService.java:248-253`
  applies a same-state transition, and `:268-291` updates `updatedAt`, saves the request, and inserts an event.
- **Test evidence:** `StaffDirectBookingTests.java:250-257` explicitly expects that request event, while asserting only
  state and hold fields at `:232-244`. This is the opposite of the plan's “complete before/after snapshot equal ... no
  request event” requirement (`tasks.yaml:952`).
- **Owner:** task-3.5.
- **Resolution:** `REVISE: task-3.5 - make leave-open create only the independent appointment; do not save, timestamp,
  transition, or log the request, and assert a complete before/after snapshot plus unchanged event count.`

### Gaps

#### G-1 — AC-55 does not prove that the AI version is unmodified

- **Spec:** AC-55: a staff edit stores a new STAFF version and “shall not overwrite the AI version”; RULE-25 requires
  complete field-for-field interpretation persistence.
- **Evidence:** the entity includes specialty, preferred vet, `cannotInterpret`, created time, and windows
  (`Interpretation.java:65-88`), but `StaffInterpretationTests.java:155-163` does not assert them on the AI row.
- **Owner / resolution:** `REVISE: task-3.3 - seed non-default values for every persisted AI field/window and compare a
  complete pre/post snapshot after the STAFF version is saved.`

#### G-2 — AC-84 ordering is proven only in the service result

- **Spec:** AC-84 requires the shown tab to list With-staff requests oldest first with each trigger.
- **Evidence:** `StaffQueueTests.java:137-152` pins service order; HTTP at `:154-163` only searches for strings and would
  pass if the template reversed the rows.
- **Owner / resolution:** `REVISE: task-3.2 - parse/assert the rendered queue row order and pair every displayed request
  with its exact hand-off trigger.`

#### G-3 — AC-85's rendered details and action are unproven

- **Spec:** AC-85/RULE-29 require every non-terminal request on All open with state, held slot, age, and release action.
- **Evidence:** service values are checked at `StaffQueueTests.java:213-233`; HTTP at `:235-244` asserts only enum text.
- **Owner / resolution:** `REVISE: task-3.2 - assert rendered rows by request with state, held slot, clock-derived age,
  and release-hold form/action, including absence for terminal requests.`

#### G-4 — AC-91 does not prove accept/reject parity

- **Spec:** AC-91/RULE-31 require owner accept/reject of a staff suggestion to follow exactly the normal suggestion
  rules.
- **Evidence:** `StaffSuggestionTests.java:161-180` checks one accept. The sole rejection at `:182-211` checks only a
  `NOT_THIS_TIME` event and does not assert hold release, exclusion, next suggestion/With-staff outcome, or the other
  scopes.
- **Owner / resolution:** `REVISE: task-3.4 - reuse the normal-suggestion acceptance and three-scope rejection matrix;
  assert hold, exclusion/event, resulting state, replacement suggestion or exhaustion, and appointment effects.`

#### G-5 — AC-93 does not completely prove hold release or event cardinality

- **Spec:** AC-93 requires attach from every non-terminal state to accept, release the hold, and log the event; the task
  validation requires all three hold fields null and exactly one attach event.
- **Evidence:** `StaffDirectBookingTests.java:174-192` omits `heldDuration == null` and reads only the last event.
- **Owner / resolution:** `REVISE: task-3.5 - assert all three hold fields null and exactly one new attach event for
  every state in the matrix.`

#### G-6 — AC-121 tests a sample, not the complete audit invariant

- **Spec:** AC-121/RULE-43 require each request transition/action to record from/to, actor, action, reason, payload, and
  timestamp and render the timeline to staff.
- **Evidence:** `StaffInterpretationTests.java:366-399` checks six events, only two actors, one reason, no payload value,
  and merely non-null timestamps; HTTP `:401-410` searches a subset of text.
- **Owner / resolution:** `REVISE: task-3.3 - define the phase action/transition matrix and assert the complete seven-field
  tuple, chronological rendering, and owner non-disclosure for every covered row.`

### Protocol

#### P-1 — task-3.2 changed undeclared production scope

`df4a516` modifies `SchedulingRequestRepository.java`, absent from task-3.2 `artifact` and `modifies`. The status note
calls it supporting after the fact; no blocker/deviation was raised. `REVISE: task-3.2 - declare and justify the fetch
change in the plan/status scope or move it to an owning task before re-closing the gate.`

#### P-2 — task-3.3 took task-3.1's message-bundle ownership

`0b56548` modifies all 11 bundles even though task-3.1 explicitly owns all phase-3 keys and task-3.3 declares none of
them. The extra edit still failed to produce a valid localized phase. `REVISE: task-3.3 - reconcile bundle ownership
with task-3.1 and record the corrected per-task scope.`

#### P-3 — Closure-gate records are materially inaccurate

`status.md:73-83` says task-3.1 satisfies RULE-2; names nonexistent `StaffSuggestTests.java` and a deferred calendar-pick
test for task-3.4; and records non-parent bases and file lists for tasks 3.5/3.6. `REVISE: task-3.6 - regenerate each
task's closure record from its actual parent and commit, with exact artifact names, assertions, rules, and suite result.`

#### P-4 — Checkpoint scope and validation are overstated

The AC table claims AC-90, AC-95, and AC-138 although phase-3 `covers` lists none of them (`tasks.yaml:820`), and calls a
19-test focused command checkpoint validation while the required full suite is red. Runtime evidence was inferred from
tests, not a runnable default application. `REVISE: task-3.6 - limit the ledger to phase covers, label supporting ACs as
out of phase, run the full suite, and record actual default-runtime HTTP observations.`

### Drift (Δ candidates)

None.

### Cosmetic

None.

## Category Notes

1. **Task closure:** fail — C-2, C-3, C-4, P-1, P-2, P-3.
2. **AC evidence audit:** fail — only AC-56/86/89/92 are STRONG; G-1..G-6 and C-4 cover the rest.
3. **Normative data by value:** no phase-3 normative tables or migrations; existing seed tests are outside this phase.
4. **Lifecycle/state guards:** fail — leave-open is implemented as a mutating same-state transition (C-4); attach covers
   the six allowed source states but its release proof is incomplete (G-5).
5. **Fidelity/round trips:** fail — complete preservation of the prior AI version is not asserted (G-1); new STAFF
   structured-field read-back is otherwise strong.
6. **Security surface:** phase route inventory matches the eight new staff mappings and four reused owner routes;
   `StaffRouteSurfaceTests.java:141-186` checks anonymous redirect, owner 403, protected-body absence, and unchanged DB.
   Live status reproduction is blocked by C-1.
7. **Loaded words:** “every”, “complete”, “same”, and “untouched” expose G-1/G-3/G-4/G-6 and C-4. RULE-29/31/32/43
   define them sufficiently; evidence did not meet those definitions.
8. **Presentation/navigation/localization:** fail — C-3. All new templates reuse the layout; phase navigation/security
   guards are present.
9. **Test hygiene:** fail — full suite red; no skipped tests and no tracked runtime-data mutation. The focused checkpoint
   command omitted all four failing methods.
10. **Constraint conformance:** RULE-2 fails (C-2); RULE-12/13 pass for the phase route matrix; RULE-15's allowed
    source-state matrix is exercised, while RULE-16 introduces no new phase transition; RULE-25 is incomplete (G-1);
    RULE-29/31/32/43 are violated or insufficiently proven (G-2..G-6, C-4). RULE-44 isolation/determinism are supported
    by the test profile, but its end-to-end confidence is blocked by C-1 and the narrowed validation.

## Walkthrough Script

Taken directly from UC-4, not newly invented:

1. Staff opens a With-staff request from **Needs staff** and reviews its timeline, raw response, model tag, and prompt
   version; an owner must not see those provenance fields.
2. Staff creates/completes the structured interpretation; a new STAFF version must exist and the prior AI version must
   remain unchanged.
3. Staff presses **Suggest**; one complete hold appears and the request becomes Suggestion offered. Calendar pick remains
   explicitly deferred to cp-4.
4. Owner accepts, then in a separate scenario rejects, the staff-placed suggestion under the normal rules and sees the
   resulting appointment/new suggestion/state.
5. Extensions: direct-book with **attach** (Accepted, hold released, event logged) and **leave open** (request byte-for-
   byte/domain-field unchanged, appointment visible to owner).
6. Denials: anonymous staff URLs redirect to login; owner receives 403 with no staff data and no mutation.

Walkthrough confirmation remains pending and cannot begin until C-1 is fixed.

## Spec Reconciliation

- Δ folded in: none.
- F-n open: F-26 (C-1), F-27 (C-2), F-28 (C-3), F-29 (C-4), F-30 (G-1), F-31 (G-2),
  F-32 (G-3), F-33 (G-4), F-34 (G-5), F-35 (G-6).
- Spec weakness exposed: “any pet at any time” in AC-92/RULE-32 is broader than the task validation and should be
  reconciled with RULE-35's future-date/grid/vet-block limits by `spec`/`criteria` before later reuse.
- Plan weaknesses exposed: task-3.2's UI AC validations do not prescribe rendered-row assertion shape; task-3.5's
  required no-event snapshot contradicts the delivered test; task-3.6 has no phase ACs but its checkpoint report claims
  AC-138; phase localization validation omitted the global localization tests. Owners: `tasks` and `review (plan)`.

## Resolution

Apply the directives in dependency order:

1. `REVISE: task-1.6 - repair default Ollama constructor injection and add a no-live-call default-provider context test.`
2. `REVISE: task-3.5 - make leave-open non-mutating; strengthen AC-93/94 snapshots, hold tuple, and event cardinality.`
3. `REVISE: task-3.1 - remove controller-to-repository dependencies and restore RULE-2.`
4. `REVISE: task-3.1/task-3.3/task-3.5 - repair all phase localization keys/literals and run both localization suites.`
5. `REVISE: task-3.2 - assert rendered Needs-staff ordering and complete All-open row/action content.`
6. `REVISE: task-3.3 - fully snapshot AC-55 and prove the complete AC-121 event/timeline matrix.`
7. `REVISE: task-3.4 - prove accept and all rejection scopes have normal-suggestion parity.`
8. `REVISE: task-3.6 - correct closure/checkpoint evidence, run the full suite and default runtime, record live URLs,
   and return cp-3 for convergence plus human walkthrough.`

## Approval response

REVISE: task-1.6 - repair default Ollama constructor injection and add a no-live-call default-provider context test.
