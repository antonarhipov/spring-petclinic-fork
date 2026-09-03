# Spec Review: Smart Appointment Scheduling

## Summary
- Feature: Smart Appointment Scheduling (guided LLM + Timefold appointment flow for Spring PetClinic)
- Verdict: FAIL
- Counts: 1 blocker, 1 major, 1 minor
- Action: Emergency/urgent requests have no state transition to *With staff*, so the lifecycle guard contradicts the emergency ACs. Resolve BLOCKER-1 and MAJOR-1 via the Fix Plan (spec → criteria → rules → review), then rerun review.

## Discipline Check
Clean. The trace chain holds end-to-end: all 103 behaviors (B-1…B-103) appear in at least one AC `Covers:` line; all 141 ACs (AC-1…AC-141) appear in the rules Cross-Reference table; three spot-checked Cross-Reference rows (AC-13→RULE-13/RULE-2, AC-65→RULE-10/RULE-44, AC-125→RULE-42/RULE-12/RULE-14) match the rule contents. Every AC carries a `Flow:` tag, every UC step and extension is named by at least one AC's `Flow:` tag (UC-1…UC-6, all extensions), and every AC matches an EARS template. The normative tables (accounts, opening hours, vet schedules, exceptions, config defaults) are restated **verbatim** in `spec.md` §Normative data, in criteria AC-125…AC-134, and in rules RULE-42 — no pointer references. Lifecycle refusals are expressed generically (AC-123 for requests, AC-124 for appointments, backed by `IllegalRequestTransitionException`/`IllegalAppointmentTransitionException` in RULE-15/RULE-16) rather than one refusal AC per action; this is an acceptable upstream choice, not a finding. The one structural weakness is in the state tables themselves — see BLOCKER-1 and MAJOR-1.

## Conflicts

### BLOCKER-1: The emergency/urgent path has no state transition and is actively contradicted by the routing and guard rules
**Source documents:** `spec.md` (State model — Scheduling request lifecycle; RA-17; §Use cases UC-1 ext 1a; E-16), `criteria.md` (AC-87, AC-32), `rules.md` (RULE-15, RULE-19, RULE-30)

**Issue.** The emergency behavior is normative:
- AC-87: *"When either the AI urgent flag or the owner's urgent checkbox is set, the system shall pin the request to the top of the staff queue and skip the automated loop."*
- RULE-30: *"MUST pin a request to the top of the staff queue and skip the automated loop when either the AI `urgent` flag or the owner's \"this is urgent\" checkbox is set."*
- UC-1 ext 1a: *"Owner marks the request urgent (or the AI later flags it) → request pinned to staff queue, automated loop skipped; → UC-4"* (UC-4 precondition is *"request in With staff"*).

But the request state model in `spec.md` — mirrored exactly in RULE-15 — contains **no urgent/emergency transition into *With staff*** from *Awaiting consent*, *Interpreting*, or *Interpreted*, and it declares: *"Every transition not listed below is **refused by the system with no side effect** (B-101)."* Worse, the only *(system)* outcome for a well-formed urgent interpretation is the ordinary one — RULE-19: *"usable (well-formed, allowed-universe-minus-excluded non-empty in horizon) → *Interpreted*"* and the *Interpreting* row *"*(system)* interpretation usable | Interpreted"* — which routes an urgent-but-usable interpretation **into** the automated loop, the exact opposite of "skip the automated loop."

**Impact.** RULE-30/AC-87 mandate a transition the state guard (RULE-15) will refuse, and RULE-19 sends urgent requests to *Interpreted* instead of *With staff*. An implementer following the state table literally cannot pin an emergency to the queue; following AC-87 literally violates the guard. The headline emergency feature (proposal §8) is unbuildable as written, and the lifecycle E2E leg for UC-1 ext 1a (the emergency leg named in RULE-44 / the End-to-end scenarios table) cannot pass. Ambiguity compounds it: for an **owner-marked** urgent request created in *Awaiting consent*, the spec never says whether consent is still required and whether the text is still sent to the AI (AC-24/RULE-17: *"Text **MUST** be sent only after consent"*), since the only *Awaiting consent → With staff* edge is *"decline consent."*

**Resolution.** Rerun **spec** to add explicit urgent/emergency transitions to the request state model — at minimum *Awaiting consent → With staff* (owner-marked urgent) and an *Interpreting → With staff* *(system)* outcome for an AI-flagged urgent result — and to state whether an owner-marked emergency bypasses consent and interpretation. Then rerun **criteria** (add a transition AC for emergency → *With staff* and reconcile AC-87 with AC-32 so a usable-but-urgent interpretation routes to *With staff*, not *Interpreted*) and **rules** (RULE-15 table, RULE-19 routing, RULE-30).
**Related:** MAJOR-1 (same state tables).

### MINOR-1: Solver termination wording vs. configured property
**Source documents:** `spec.md` (RA-32), `rules.md` (RULE-11), `criteria.md` (AC-83)

**Issue.** RA-32 specifies *"Termination: 1 s unimproved or exhausted,"* while RULE-11 fixes *"a 1-second termination budget (`timefold.solver.solve.duration=1s`)"* and AC-83 says *"a 1-second termination budget."* `timefold.solver.solve.duration` is a total spent-limit, not an *unimproved*-spent-limit; the two describe different Timefold terminations.

**Impact.** Cosmetic/behavioral nuance only; either termination yields a fast suggestion. No AC asserts *which* termination, so nothing downstream breaks. Worth aligning the wording so the executor doesn't guess.

**Resolution.** Localized clarification in `spec.md` RA-32 (or RULE-11) stating the intended Timefold termination. Not in the Fix Plan (minor).

## Codebase Grounding
The composed design fits the project. The build already sits on `spring-boot-starter-parent` 4.1.0; RA-9 plans the required Java 17→21 bump (`pom.xml` currently `<java.version>17</java.version>`), which is mandatory because Timefold Solver 2.x requires Java 21. Both new libraries exist at the declared versions and are Boot-4/Java-21 compatible: `ai.timefold.solver:timefold-solver-spring-boot-starter:2.5.0` (Spring Boot 4.x only; property `timefold.solver.solve.duration` matches RULE-11) and `org.springframework.ai:spring-ai-starter-model-ollama:2.0.1` (designed for Spring Boot 4.0/4.1, provides `BeanOutputConverter` used by RULE-17). The package-by-feature layout (RULE-1), the 11 message bundles under `messages/` (matches EA-9/RULE-41), and the presentation fragments (`fragments/layout.html` defining `layout(template, menu)` + `menuItem`, plus `inputField.html`/`selectField.html`) all exist as the design assumes. The claim that there is **no** existing security config and **no** Flyway is correct. Every pre-existing route (`/`, `/oups`, `/owners/**` incl. pets/visits, `/vets`, `/vets.html`, `/error`, static) appears in the RULE-12 Security Surface matrix, and `permitAll` is limited to `/login`, `/error`, and static resources — none of which expose owner-scoped data — so the negative-authz ACs (AC-2, AC-8/9, AC-11/12, AC-118) are enforceable over the whole URL space. Stock `db/{h2,mysql,postgres}` SQL and `application-{mysql,postgres}.properties` exist and are deletable as RA-9 prescribes. (Immaterial nit: the Spring AI 2.0.1 starter transitively references Boot 4.1.1 while the parent pins 4.1.0; Boot dependency management resolves this — no action needed.)

## EARS ↔ Test Strategy
Each EARS pattern used in criteria has a fitting approach in the rules' Testing Strategy tables and RULE-44. Negative-authz ACs get the "denial status **and** body excludes protected data **and** `never()` on the mutating collaborator" shape over the real `SecurityFilterChain` across the whole URL space. State-refusal ACs get a service test asserting the named exception plus `never()` save and no event row. Data-exactness ACs (AC-125…AC-134) get a fresh-DB migration test asserting every row **by value** with password-encoder verification (not count-only). Fidelity (AC-53) gets a field-for-field round-trip. Boundary ACs (duration, 2-hour lead, horizon, completion timing) get three-point coverage. Lifecycle (AC-138) gets an HTTP MockMvc test with steps copied per UC and one leg per state-changing extension. RULE-44 fixes the level, the isolated in-memory H2 fixture, and the double contract ("a double MUST NOT return a value production cannot produce"). One caveat, tied to BLOCKER-1: the End-to-end scenarios table lists the UC-1 ext 1a emergency leg, but no fitting lifecycle leg can be written until the emergency state transition exists.

## Risk Hotspots
- **Ollama model tag `gemma4:latest`** (proposal §14, ED-1): this tag is unlikely to exist in Ollama's registry (current families are `gemma`/`gemma2`/`gemma3`). *Mitigation:* tests use the `stub` provider so this cannot break CI, but a live demo needs the property pointed at a real installed model; document a concrete default (e.g. `gemma3`).
- **`SELECT … FOR UPDATE` on H2** (RULE-10, AC-65/AC-141): H2's pessimistic row-locking semantics differ from Postgres/MySQL and can lock at a coarser granularity or behave differently under the in-memory test datasource; the concurrency guarantees could pass in one mode and be flaky in another. *Mitigation:* verify H2 lock behavior explicitly in the concurrency integration test and pin the isolation level.
- **Async `@Async` interpretation vs. synchronous test executor** (RULE-18, RULE-44): tests swap in a synchronous executor, so the fresh-transaction re-check (AC-31), abandon-discard (AC-29), and startup recovery (AC-30) run on a code path tests never exercise concurrently. *Mitigation:* add targeted tests for the async apply/discard/startup-recovery paths that use the real executor semantics.
- **Structured-output parsing / retry mapping** (RULE-17, AC-34): the malformed-JSON-retry-once and timeout→unavailable mapping is central but fully hidden behind the stub. *Mitigation:* a contract test for the `ollama` adapter's parse/retry/timeout-to-"model unavailable" behavior, independent of a live model.
- **Modifying stock `layout.html`/navbar** for role-based menus, signed-in identity, and logout (RULE-39/AC-7/AC-14/AC-15) pulls the stock template under the feature's no-literal localization check (RULE-41/RA-49, which exempts unmodified stock templates). *Mitigation:* ensure every new navbar/label string is a key present in all 11 bundles so the localization check and stock template tests stay green.

## Fix Plan

Execution order: spec → criteria → rules → review

### spec (rerun first)
1. [cascades] BLOCKER-1: Add explicit urgent/emergency transitions to the request state model — at least *Awaiting consent → With staff* for an owner-marked-urgent request and an *(system)* *Interpreting → With staff* outcome for an AI-flagged urgent result — and state whether an owner-marked emergency skips consent and interpretation entirely. Also reconcile RA-17 with the routing so a usable-but-urgent interpretation does not fall through to *Interpreted*.
   Downstream: rerun **criteria** to add an emergency → *With staff* transition AC and reconcile AC-87 with AC-32; rerun **rules** to update the RULE-15 table, RULE-19 routing, and RULE-30.
2. [cascades] MAJOR-1: Add the missing *"staff book (leave open)"* self-transition rows to the *Suggestion offered* and *With staff* states so the table matches the note that both staff-book actions are available from every non-terminal, non-*Accepted*, non-*Abandoned* state.
   Downstream: rerun **rules** so RULE-15's transition table mirrors the added rows.

### review
Rerun once all upstream fixes are in (not per-fix).
