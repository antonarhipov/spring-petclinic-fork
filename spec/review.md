# Spec Review: Smart Appointment Scheduling

## Summary
- Feature: Smart Appointment Scheduling (guided LLM + Timefold appointment flow for Spring PetClinic)
- Verdict: PASS
- Counts: 0 blockers, 0 major, 1 minor (BLOCKER-1 and MAJOR-1 resolved 2026-09-03)
- Action: BLOCKER-1 resolved by removing urgency from the automated flow entirely — only the always-visible urgent-care banner remains (RA-17; B-71/B-72, AC-87/AC-88 and RULE-30 retired). MAJOR-1 resolved by adding the missing *staff book (leave open)* self-transition rows to the *Suggestion offered* and *With staff* states in both the spec state model and RULE-15. Only MINOR-1 (solver termination wording) remains; it is not in the Fix Plan. Rerun review to confirm.

## Discipline Check
Clean. The trace chain holds end-to-end: all behaviors (B-1…B-103, minus the retired B-71/B-72) appear in at least one AC `Covers:` line; all ACs (AC-1…AC-141, minus the retired AC-87/AC-88) appear in the rules Cross-Reference table; three spot-checked Cross-Reference rows (AC-13→RULE-13/RULE-2, AC-65→RULE-10/RULE-44, AC-125→RULE-42/RULE-12/RULE-14) match the rule contents. Every AC carries a `Flow:` tag, every UC step and extension is named by at least one AC's `Flow:` tag (UC-1…UC-6, all extensions), and every AC matches an EARS template. The normative tables (accounts, opening hours, vet schedules, exceptions, config defaults) are restated **verbatim** in `spec.md` §Normative data, in criteria AC-125…AC-134, and in rules RULE-42 — no pointer references. Lifecycle refusals are expressed generically (AC-123 for requests, AC-124 for appointments, backed by `IllegalRequestTransitionException`/`IllegalAppointmentTransitionException` in RULE-15/RULE-16) rather than one refusal AC per action; this is an acceptable upstream choice, not a finding. The state tables are now internally consistent: BLOCKER-1 and MAJOR-1 have both been resolved.

## Conflicts

### BLOCKER-1 (RESOLVED): The emergency/urgent path had no state transition and was contradicted by the routing and guard rules
**Source documents:** `spec.md` (State model — Scheduling request lifecycle; RA-17; §Use cases UC-1 ext 1a; E-16), `criteria.md` (AC-87, AC-32), `rules.md` (RULE-15, RULE-19, RULE-30)

**Issue.** The emergency behavior is normative:
- AC-87: *"When either the AI urgent flag or the owner's urgent checkbox is set, the system shall pin the request to the top of the staff queue and skip the automated loop."*
- RULE-30: *"MUST pin a request to the top of the staff queue and skip the automated loop when either the AI `urgent` flag or the owner's \"this is urgent\" checkbox is set."*
- UC-1 ext 1a: *"Owner marks the request urgent (or the AI later flags it) → request pinned to staff queue, automated loop skipped; → UC-4"* (UC-4 precondition is *"request in With staff"*).

But the request state model in `spec.md` — mirrored exactly in RULE-15 — contains **no urgent/emergency transition into *With staff*** from *Awaiting consent*, *Interpreting*, or *Interpreted*, and it declares: *"Every transition not listed below is **refused by the system with no side effect** (B-101)."* Worse, the only *(system)* outcome for a well-formed urgent interpretation is the ordinary one — RULE-19: *"usable (well-formed, allowed-universe-minus-excluded non-empty in horizon) → *Interpreted*"* and the *Interpreting* row *"*(system)* interpretation usable | Interpreted"* — which routes an urgent-but-usable interpretation **into** the automated loop, the exact opposite of "skip the automated loop."

**Impact.** RULE-30/AC-87 mandate a transition the state guard (RULE-15) will refuse, and RULE-19 sends urgent requests to *Interpreted* instead of *With staff*. An implementer following the state table literally cannot pin an emergency to the queue; following AC-87 literally violates the guard. The headline emergency feature (proposal §8) is unbuildable as written, and the lifecycle E2E leg for UC-1 ext 1a (the emergency leg named in RULE-44 / the End-to-end scenarios table) cannot pass. Ambiguity compounds it: for an **owner-marked** urgent request created in *Awaiting consent*, the spec never says whether consent is still required and whether the text is still sent to the AI (AC-24/RULE-17: *"Text **MUST** be sent only after consent"*), since the only *Awaiting consent → With staff* edge is *"decline consent."*

**Resolution (applied 2026-09-03).** The urgency contract was **removed from the automated flow** rather than reconciled with the state model. The AI `urgent` flag, the owner "this is urgent" checkbox, queue pinning and the "skip the automated loop" behavior are all gone: RA-17 is rewritten; B-71/B-72, AC-87/AC-88 and RULE-30 are retired; UC-1 ext 1a and UC-4 ext 2a are removed; the *(system)* "clear emergency flag" row is dropped from the request state model (spec.md and RULE-15); the `urgent`/`urgent_ai`/`urgent_owner` fields are removed from the interpretation record and schema (RULE-7, RULE-17, RULE-25) and from the fidelity field list (AC-53); the queue no longer pins emergencies (RA-36, B-68, AC-84, RULE-29); and the `stub` interpreter no longer keys off "emergency" (RA-22). Urgent care is now handled solely by the always-visible urgent-care banner with the clinic phone (RA-8/B-18/AC-19/RULE-38), which was never part of the lifecycle. With no urgent transition demanded, the state guard (RULE-15) and the interpretation routing (RULE-19) are internally consistent.
**Related:** MAJOR-1 (same state tables, now resolved).

### MAJOR-1 (RESOLVED): State tables omitted the *staff book (leave open)* self-transition for *Suggestion offered* and *With staff*
**Source documents:** `spec.md` (State model — Scheduling request lifecycle), `rules.md` (RULE-15), `criteria.md` (AC-94)

**Issue.** Both state tables prefaced themselves with the note that *"Staff book (attach)" and "Staff book (leave open)" are available from every non-terminal, non-*Accepted*, non-*Abandoned* state"* (RA-31), and AC-94/UC-4 ext 3b make *book directly and leave the request open* a first-class action. Yet the *Awaiting consent*, *Interpreting*, *Interpretation failed* and *Interpreted* rows each carried **both** staff-book edges while the *Suggestion offered* and *With staff* rows listed only *staff book (attach)*, silently dropping the *leave open* self-transition. Under the guard's own rule — *"Every transition not listed below is refused by the system with no side effect (B-101)"* — an implementer would refuse a book-and-leave-open from those two states, contradicting the note and AC-94.

**Impact.** The state guard (RULE-15) and the spec state model disagreed with their own preamble and with AC-94 for exactly two states, so the behavior was under-specified where it matters most (an open request already holding a slot, or already queued with staff).

**Resolution (applied 2026-09-03).** Added the missing self-transition rows so both tables match their preamble: *Suggestion offered* + *staff book (leave open)* → *Suggestion offered*, and *With staff* + *staff book directly (leave open)* → *With staff*, in both `spec.md` and RULE-15. All six non-terminal states now carry both staff-book edges.

### MINOR-1: Solver termination wording vs. configured property
**Source documents:** `spec.md` (RA-32), `rules.md` (RULE-11), `criteria.md` (AC-83)

**Issue.** RA-32 specifies *"Termination: 1 s unimproved or exhausted,"* while RULE-11 fixes *"a 1-second termination budget (`timefold.solver.solve.duration=1s`)"* and AC-83 says *"a 1-second termination budget."* `timefold.solver.solve.duration` is a total spent-limit, not an *unimproved*-spent-limit; the two describe different Timefold terminations.

**Impact.** Cosmetic/behavioral nuance only; either termination yields a fast suggestion. No AC asserts *which* termination, so nothing downstream breaks. Worth aligning the wording so the executor doesn't guess.

**Resolution.** Localized clarification in `spec.md` RA-32 (or RULE-11) stating the intended Timefold termination. Not in the Fix Plan (minor).

## Codebase Grounding
The composed design fits the project. The build already sits on `spring-boot-starter-parent` 4.1.0; RA-9 plans the required Java 17→21 bump (`pom.xml` currently `<java.version>17</java.version>`), which is mandatory because Timefold Solver 2.x requires Java 21. Both new libraries exist at the declared versions and are Boot-4/Java-21 compatible: `ai.timefold.solver:timefold-solver-spring-boot-starter:2.5.0` (Spring Boot 4.x only; property `timefold.solver.solve.duration` matches RULE-11) and `org.springframework.ai:spring-ai-starter-model-ollama:2.0.1` (designed for Spring Boot 4.0/4.1, provides `BeanOutputConverter` used by RULE-17). The package-by-feature layout (RULE-1), the 11 message bundles under `messages/` (matches EA-9/RULE-41), and the presentation fragments (`fragments/layout.html` defining `layout(template, menu)` + `menuItem`, plus `inputField.html`/`selectField.html`) all exist as the design assumes. The claim that there is **no** existing security config and **no** Flyway is correct. Every pre-existing route (`/`, `/oups`, `/owners/**` incl. pets/visits, `/vets`, `/vets.html`, `/error`, static) appears in the RULE-12 Security Surface matrix, and `permitAll` is limited to `/login`, `/error`, and static resources — none of which expose owner-scoped data — so the negative-authz ACs (AC-2, AC-8/9, AC-11/12, AC-118) are enforceable over the whole URL space. Stock `db/{h2,mysql,postgres}` SQL and `application-{mysql,postgres}.properties` exist and are deletable as RA-9 prescribes. (Immaterial nit: the Spring AI 2.0.1 starter transitively references Boot 4.1.1 while the parent pins 4.1.0; Boot dependency management resolves this — no action needed.)

## EARS ↔ Test Strategy
Each EARS pattern used in criteria has a fitting approach in the rules' Testing Strategy tables and RULE-44. Negative-authz ACs get the "denial status **and** body excludes protected data **and** `never()` on the mutating collaborator" shape over the real `SecurityFilterChain` across the whole URL space. State-refusal ACs get a service test asserting the named exception plus `never()` save and no event row. Data-exactness ACs (AC-125…AC-134) get a fresh-DB migration test asserting every row **by value** with password-encoder verification (not count-only). Fidelity (AC-53) gets a field-for-field round-trip. Boundary ACs (duration, 2-hour lead, horizon, completion timing) get three-point coverage. Lifecycle (AC-138) gets an HTTP MockMvc test with steps copied per UC and one leg per state-changing extension. RULE-44 fixes the level, the isolated in-memory H2 fixture, and the double contract ("a double MUST NOT return a value production cannot produce"). The End-to-end scenarios table no longer lists any emergency leg (the UC-1 ext 1a and UC-4 ext 2a emergency legs were removed with BLOCKER-1's resolution), so every listed leg is now writable.

## Risk Hotspots
- **Ollama model tag `gemma4:latest`** (proposal §14, ED-1): this tag is unlikely to exist in Ollama's registry (current families are `gemma`/`gemma2`/`gemma3`). *Mitigation:* tests use the `stub` provider so this cannot break CI, but a live demo needs the property pointed at a real installed model; document a concrete default (e.g. `gemma3`).
- **`SELECT … FOR UPDATE` on H2** (RULE-10, AC-65/AC-141): H2's pessimistic row-locking semantics differ from Postgres/MySQL and can lock at a coarser granularity or behave differently under the in-memory test datasource; the concurrency guarantees could pass in one mode and be flaky in another. *Mitigation:* verify H2 lock behavior explicitly in the concurrency integration test and pin the isolation level.
- **Async `@Async` interpretation vs. synchronous test executor** (RULE-18, RULE-44): tests swap in a synchronous executor, so the fresh-transaction re-check (AC-31), abandon-discard (AC-29), and startup recovery (AC-30) run on a code path tests never exercise concurrently. *Mitigation:* add targeted tests for the async apply/discard/startup-recovery paths that use the real executor semantics.
- **Structured-output parsing / retry mapping** (RULE-17, AC-34): the malformed-JSON-retry-once and timeout→unavailable mapping is central but fully hidden behind the stub. *Mitigation:* a contract test for the `ollama` adapter's parse/retry/timeout-to-"model unavailable" behavior, independent of a live model.
- **Modifying stock `layout.html`/navbar** for role-based menus, signed-in identity, and logout (RULE-39/AC-7/AC-14/AC-15) pulls the stock template under the feature's no-literal localization check (RULE-41/RA-49, which exempts unmodified stock templates). *Mitigation:* ensure every new navbar/label string is a key present in all 11 bundles so the localization check and stock template tests stay green.

## Fix Plan

Execution order: spec → criteria → rules → review

### spec (rerun first)
1. [DONE 2026-09-03] BLOCKER-1: Resolved by **removing** urgency from the automated flow (not by adding transitions). Urgent care is handled solely by the urgent-care banner; the urgent flag/checkbox, queue pinning, "skip the automated loop", the "clear emergency flag" transition, and the `urgent`/`urgent_ai`/`urgent_owner` fields are all retired across proposal/spec/criteria/rules (RA-17; B-71/B-72, AC-87/AC-88, RULE-30 retired).
2. [DONE 2026-09-03] MAJOR-1: Added the missing *"staff book (leave open)"* self-transition rows to the *Suggestion offered* and *With staff* states so the table matches the note that both staff-book actions are available from every non-terminal, non-*Accepted*, non-*Abandoned* state.
   Downstream: [DONE] RULE-15's transition table now mirrors the added rows.

### review
BLOCKER-1 and MAJOR-1 are now resolved upstream. Only MINOR-1 remains (a wording nuance not in the Fix Plan). Rerun the review to confirm the state tables are internally consistent and to settle the verdict.
