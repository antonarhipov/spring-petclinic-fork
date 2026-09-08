# Use-Case Checkpoint: UC-3 - Schedule a pet appointment through the guided flow

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `c3cc9075b4502993c7d0b0c96ec7142952476436`
- Submission commit: HEAD at convergence
- Prior approval reopened: live Ollama exposed an offset-bearing time that the former provider schema accepted but the domain could not deserialize
- Relations verified: Requires UC-1; every actor journey uses the approved form-login and role-scoped session boundary

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| Main steps 1-10 | `SchedulingE2eTests.ownerObtainsAppointmentThroughSuggestion` (`SchedulingE2eTests.java:72`), exact consent disclosure (`ConsentAndSuggestionWebTests.java:91`), fidelity (`InterpretationPersistenceTests.java:119`), and complete review rendering (`InterpretationWebTests.java:124`) | PASS |
| Extension 1a | Existing-request HTTP redirect at `SchedulingE2eTests.java:123`; concurrent one-row winner at `ConcurrencyInvariantTests.java:66` | PASS |
| Extension 1b | Foreign/unknown identical 404 plus unchanged table at `SchedulingE2eTests.java:133` | PASS |
| Extension 1c | Null reason fields retained; general/30-minute defaults applied only to display/matching at `InterpretationWebTests.java:170` | PASS |
| Extension 1d | Non-English text accepted unchanged at `SchedulingE2eTests.java:145` | PASS |
| Extension 2a | Revised text remains Awaiting consent at `OwnerTransitionServiceTests.java:111` | PASS |
| Extension 2b | Decline, no interpreter, no appointment/version at `OwnerTransitionServiceTests.java:88` and real HTTP at `SchedulingE2eTests.java:155` | PASS |
| Extension 3a | Concurrent duplicate consent produces one call/version at `InterpretationConcurrencyTests.java:83` | PASS |
| Extension 4a | Abandoned late result discarded at `SchedulingE2eTests.java:215` and `InterpretationPersistenceTests.java:323` | PASS |
| Extension 4b | Three semantic failures through HTTP and exact persistence at `SchedulingE2eTests.java:187` and `InterpretationPersistenceTests.java:289` | PASS |
| Extension 4c | Fresh consent and third-attempt recommendation while rephrase remains available at `InterpretationWebTests.java:200`, `InterpretationWebTests.java:237`, and `OwnerRequestOutcomeTests.java:97` | PASS |
| Extension 4d | Failure-to-staff owner choice at `OwnerRequestOutcomeTests.java:215` | PASS |
| Extension 4e | One-call transport/deadline fallback at `InterpreterContractTests.java:159` and `SchedulingE2eTests.java:207` | PASS |
| Extension 4f | Startup recovery at `InterpreterContractTests.java:178` and restart at `RuntimePersistenceRestartTests.java:41` | PASS |
| Extension 5a | `OTHER` retained and routed without hold at `OwnerRequestOutcomeTests.java:126` and `SchedulingE2eTests.java:276` | PASS |
| Extension 5b | Unknown preferred veterinarian becomes absent at `InterpretationPersistenceTests.java:213` | PASS |
| Extension 6a | Raw/effective/clamped duration at `DurationAndTimeTests.java:93` and `InterpretationWebTests.java:124` | PASS |
| Extension 6b | Edit releases hold, retains rejection/version history, and returns to consent at `OwnerTransitionServiceTests.java:165` and `SlotSuggestionPortTests.java:301` | PASS |
| Extension 6c | Interpreted-to-staff owner choice at `OwnerRequestOutcomeTests.java:226` | PASS |
| Extension 7a | No-slot handoff, explanation, no hold, abandon-only actions at `OwnerRequestOutcomeTests.java:160` | PASS |
| Extension 8a | Exact durable rejection and different replacement hold at `SlotSuggestionPortTests.java:179` and `SchedulingE2eTests.java:292` | PASS |
| Extension 8b | Suggestion-to-staff owner choice deletes hold at `OwnerRequestOutcomeTests.java:237` | PASS |
| Extension 8c | Suggestion edit follows fresh-consent path at `SchedulingE2eTests.java:170` | PASS |
| Extension 8d | Missing reason refuses; reasoned HTTP release deletes hold and stores reason at `SlotSuggestionPortTests.java:286` and `SchedulingE2eTests.java:303` | PASS |
| Extension 8e | Schedule invalidation deletes hold and routes with reason at `SlotSuggestionPortTests.java:269` | PASS |
| Extension 9a | Acceptance overlap recheck and replacement notice at `SlotSuggestionPortTests.java:336` and `SchedulingE2eTests.java:314` | PASS |
| Extension 2c | All permitted states abandon, clear active pet, and delete hold at `OwnerTransitionServiceTests.java:207` | PASS |
| Extension 3b | Complete wrong-state snapshots and HTTP refusal at `RequestStateTransitionTests.java:108` and `SchedulingE2eTests.java:333` | PASS |
| G1 | One suggestion and no calendar at `ConsentAndSuggestionWebTests.java:177` | PASS |
| G2-G4 | Exact feasibility boundaries at `FeasibilityCheckerTests.java:22` and `FeasibilityBoundaryTests.java:40` | PASS |
| G3 | Deterministic next-weekday payload, exact one-off weekday instructions, schema-enforced date/weekday and offset-free `HH:mm` values at `PromptBuilder.java:35`, `OllamaInterpreter.java:23`, and `InterpreterContractTests.java:85`; persistence remains exact at `InterpretationPersistenceTests.java:119` | PASS |
| G5-G6 | Ordered tie-breaks, workload semantics, and localized rank keys at `SlotRankerTests.java:44`, `SlotRankerTests.java:129`, and `SlotSuggestionPortTests.java:124` | PASS |
| G7 | One-winner booking/hold races at `ConcurrencyInvariantTests.java:91` and `ConcurrencyInvariantTests.java:114` | PASS |
| G8-G9 | Field-perfect persistence plus strict provider value format/mapping, prompt/schema/model/temperature/privacy, and request/response logging without raw JSON at `InterpretationPersistenceTests.java:119`, `OllamaInterpretationResponse.java:19`, and `InterpreterContractTests.java:85` | PASS |
| G10 | Urgent-care guidance on every scheduling page/state at `ConsentAndSuggestionWebTests.java:128` | PASS |
| G11 | Complete request and appointment state matrices at `RequestStateTransitionTests.java:108` and `AppointmentStateTransitionTests.java:82` | PASS |
| G12 | Pinned clinic clock, seeded exception, DST stability at `TestClockProfileIntegrationTests.java:41`, `SlotSuggestionPortTests.java:142`, `DurationAndTimeTests.java:175` | PASS |
| G13 | Deterministic substitute modes plus semantic/unavailable/late/startup coverage in the interpretation suite | PASS |
| G14 | Required `RANDOM_PORT` owner/staff HTTP continuations at `SchedulingE2eTests.java:72` and `SchedulingE2eTests.java:223` | PASS |
| G15 | Active-request and overlapping-slot one-winner races at `ConcurrencyInvariantTests.java:66` and `ConcurrencyInvariantTests.java:91` | PASS |
| G16 | In-memory isolation and unchanged runtime data at `TestDataIsolationTests.java:50` | PASS |
| G17 | Shared shell, external polling, Refresh, localization scans, and README English-input statement | PASS |
| G18 | Close/reopen persistence for every named structure at `RuntimePersistenceRestartTests.java:41` | PASS |
| G19 | Two active plus queued third job at `InterpretationConcurrencyTests.java:63` | PASS |
| Success postcondition | Accepted request, cleared active pet, Confirmed-only appointment, rendered My appointments at `SchedulingE2eTests.java:109` | PASS |
| Minimal guarantee | Cross-owner, concurrency, late-result, refusal, no-slot, and abandonment snapshot evidence described above | PASS |
| Requires UC-1 | Real-server journeys authenticate through approved UC-1; shared security/presentation regressions pass | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1 | Domain packages and delegated controllers; lifecycle/matching reside in services | PASS |
| RULE-2 | `RequestService.java:93`, `AppointmentService.java:41`, atomic refusal snapshots | PASS |
| RULE-3 | Framework-free `FeasibilityChecker`/`SlotRanker` and their boundary suites | PASS |
| RULE-4 | `RequestStateTransitionTests.java:108` | PASS |
| RULE-5 | `AppointmentStateTransitionTests.java:82` and deletion evidence | PASS |
| RULE-6 | `ConcurrencyInvariantTests.java:66`, `ConcurrencyInvariantTests.java:91`, `RequestStateTransitionTests.java:85` | PASS |
| RULE-7 | `SeedMigrationTests.java:47`, `InterpretationPersistenceTests.java:119`, `RuntimePersistenceRestartTests.java:41` | PASS |
| RULE-8 | `SeedMigrationTests.java:47` through `SeedMigrationTests.java:272` | PASS |
| RULE-9 | `SecurityMatrixWebTests.java:60`, `SchedulingE2eTests.java:349` | PASS |
| RULE-10 | `AuthenticatedOwnerService`, `SchedulingE2eTests.java:133`, `InterpretationWebTests.java:103` | PASS |
| RULE-11 | Provider-structured regex schema, deterministic mapping, Spring AI options, timeouts, and no retry at `InterpreterContractTests.java:116` and production/test properties | PASS |
| RULE-12 | Exact prompt minimization plus payload/mapped-response and privacy-safe failure logging at `InterpreterContractTests.java:85` and `InterpreterContractTests.java:188`; consent disclosure at `ConsentAndSuggestionWebTests.java:91` | PASS |
| RULE-13 | `InterpretationConcurrencyTests.java:63`, `InterpreterContractTests.java:159`, `InterpretationPersistenceTests.java:289` | PASS |
| RULE-14 | `InterpretationWebTests.java:86`, `InterpretationWebTests.java:103` | PASS |
| RULE-15 | Injected `Clock`; `TestClockProfileIntegrationTests.java:41`, `SlotSuggestionPortTests.java:142` | PASS |
| RULE-16 | Shared-layout templates, DOM tests, real-server pages; human walkthrough deferred to convergence | PASS |
| RULE-17 | `I18nPropertiesSyncTest.java:85`, `InterpretationWebTests.java:293` | PASS |
| RULE-18 | Mapped boundary evidence above; corrective 348/0/0/0 suite; no runtime-data change | PASS |
| RULE-20 | Shared `AvailabilityService`; `SlotSuggestionPortTests.java:162` | PASS |
| RULE-21 | `InterpretationPersistenceTests.java:119`, `DurationAndTimeTests.java:93` | PASS |
| RULE-22 | `SlotSuggestionPortTests.java:301`, `RuntimePersistenceRestartTests.java:41` | PASS |
| RULE-24 | `RepositoryScopeTests.java:54`, `TestDataIsolationTests.java:50`, `RuntimePersistenceRestartTests.java:41` | PASS |

## Validation

- Focused command: JDK 21 `-Dtest=InterpreterContractTests test` - 7 tests, 0 failures, 0 errors, 0 skipped.
- Full relevant suite: JDK 21 Maven test with the Byte Buddy agent - 348 tests, 0 failures, 0 errors, 0 skipped.
- Format and diff: `spring-javaformat:validate` and `git diff --check` - PASS.
- Working tree impact from tests: none; `data/petclinic.mv.db` remains 122880 bytes with SHA-256 `86e6f652cd4f220a089378fba479946a055a9de04b5270d7f6d75be9199831b6`.
- Runtime evidence: one-off live Ollama smoke for `Schedule a visit for next Thursday`, pinned to `today=2026-09-08`, returned one mapped allowed window on `2026-09-10` from `09:00` to `17:00`; the temporary smoke source was removed before the automated suite.
- Changed files: `InterpretationResult.java`, new `OllamaInterpretationResponse.java`, `OllamaInterpreter.java`, `PromptBuilder.java`, `InterpreterContractTests.java`, `spec/status.md`, and this checkpoint.
- Approved UCs regression-tested: UC-1, UC-2, and UC-4 plus stock PetClinic pass within the 348-test suite.

## Notes

The provider DTO deliberately separates wire values from the domain model so the generated schema can require exact
string formats while the domain continues to store typed local dates and times. Invalid formats fail once without
logging raw provider JSON.

READY FOR CONVERGENCE: UC-3
