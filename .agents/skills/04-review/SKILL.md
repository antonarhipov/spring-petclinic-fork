---
name: spec-review
description: Stress-test a feature's specs, use-case criteria, technical rules, and codebase alignment before implementation begins
---

# Spec Review Skill

Stress-test the complete spec pipeline against itself and the codebase. Surface anything that would cause an implementing agent to fail or build the wrong thing.

Trust upstream self-verification by default; investigate the seams.

Pipeline position:

`proposal → spec → criteria → rules → **review** → plan`

# Role

Produce one feature-level review report and, when needed, a Fix Plan.

Do not fix issues yourself. Do not ask the user questions.

If findings require resolution, direct the user to rerun the relevant upstream skill in Fix Plan order.

# Operating Principle

Upstream steps verify their own artifacts. Review verifies what no single step can:

- behavior → criteria → rule traceability holds
- use cases compose into one coherent feature
- shared rules and local rules do not conflict
- scope and assumptions stay consistent across use cases
- the composed design fits the codebase
- implementation risks are surfaced before planning

Do not rebuild upstream artifacts unless needed to verify a seam.

# Inputs

Feature-level:

- `spec/<feature>/proposal.md`
- `spec/<feature>/spec.md`
- `spec/<feature>/rules.md` if present

Per use case:

- `spec/<feature>/<use-case>/spec.md`
- `spec/<feature>/<use-case>/criteria.md`
- `spec/<feature>/<use-case>/rules.md` if present

For a non-decomposed feature:

- `spec/<feature>/spec.md`
- `spec/<feature>/criteria.md`
- `spec/<feature>/rules.md`

Project conventions:

- `CLAUDE.md` / `AGENTS.md` / `GEMINI.md`
- build files
- source layout
- test setup
- relevant architecture documentation

# Codebase Grounding

Run before review categories.

Confirm:

- libraries and versions named in rules are available and compatible
- prescribed modules/packages fit the project structure
- persistence, error handling, logging, transactions, security, and concurrency rules are achievable
- proposed test approaches fit the existing test infrastructure
- shared feature design has a viable place in the codebase

This grounding feeds the Codebase Grounding category.

# Severity

- **BLOCKER** — implementation cannot safely proceed
- **MAJOR** — likely wrong behavior or significant rework
- **MINOR** — clarity or consistency problem without behavioral impact

If uncertain, prefer the higher severity.

# Verdict

- **PASS** — zero blockers, zero majors
- **PASS WITH CONDITIONS** — zero blockers, one or more majors
- **FAIL** — one or more blockers

Minors do not affect verdict.

# Review Categories

Run all five.

## 1. Discipline Check

Verify the existing trace structures without rebuilding them.

For decomposed features:

- every `Covers: UC<N>-B<M>` in criteria points to a real behavior in that use case
- every `Covers: UC<N>-AC<M>` in use-case rules points to a real AC in that use case
- every AC referenced by feature rules exists
- every behavior appears in at least one AC
- every AC appears in either:
    - feature rules
    - local rules
    - a Cross-Reference entry marked `(none needed)`
- use-case rules do not cover another use case's ACs
- feature rules may cover multiple use cases
- spot-check Cross-Reference entries against actual rules
- every AC uses a valid EARS template

For non-decomposed features, apply the same checks using `B-N`, `AC-N`, and `RULE-N`.

If this category produces many findings, upstream verification likely failed and should be rerun.

## 2. Cross-Document and Cross-Use-Case Conflicts

Check:

- **AC ↔ AC** — criteria that cannot both hold
- **AC ↔ RULE** — a rule preventing an AC outcome
- **RULE ↔ RULE** — incompatible technical constraints
- **Design ↔ Rules** — stated design contradicts an emitted rule
- **use case ↔ use case** — two use cases assume incompatible shared state, ordering, ownership, or outcomes
- **feature rule ↔ local rule** — local constraint violates shared architecture
- **scope reintroduction** — out-of-scope or excluded concerns reappear downstream
- **negative-decision violation** — a declined technology or pattern is later reintroduced
- **duplicated ownership** — the same runtime behavior is independently specified by multiple use cases

For each conflict, quote the relevant sources exactly.

Conflicts are normally MAJOR or BLOCKER.

## 3. Codebase Grounding

Verify the composed design against the actual project:

- libraries exist or can be added
- declared versions are compatible
- modules/packages are valid
- persistence and transaction rules fit existing infrastructure
- error-handling and observability rules are implementable
- concurrency assumptions fit the runtime/framework
- shared feature components have a viable architectural home
- use-case-local components do not violate feature boundaries
- test approaches fit the project's tooling

Codebase incompatibility is normally BLOCKER.

Non-trivial missing groundwork is normally MAJOR.

## 4. EARS ↔ Test Strategy Fit

For every EARS pattern present, confirm the applicable feature or use-case rules support testing it.

Patterns:

- **Ubiquitous** — invariant or continuous-state test
- **Event-driven** — event-triggered test
- **State-driven** — explicit state setup
- **Unwanted behavior** — negative-path test including prohibited outcome
- **Combined** — state plus event setup
- **Boundary** — within / at / beyond coverage
- **Negative criteria** — explicit assertion of the `and not` outcome

Testing guidance may come from:

- feature-level rules
- local use-case rules
- inherited project conventions

Do not require a local test rule when existing project conventions already make the test approach clear.

Missing support for an EARS pattern is MAJOR.

## 5. Risk Hotspots

Identify up to five implementation risks that remain even with a passing specification.

Look especially at seams such as:

- shared state across use cases
- ordering and lifecycle transitions
- parsing or interpretation boundaries
- persistence consistency
- concurrency
- external integrations
- new libraries
- failure recovery
- feature-wide invariants enforced by several use cases

For each give:

`area / reason / mitigation`

If none are material, write:

`Hotspots: none considered material — <reason>.`

# Finding Format

Each finding contains:

- ID: `BLOCKER-1`, `MAJOR-1`, `MINOR-1`
- Title
- Source document(s)
- Issue with exact quoted text
- Impact
- Resolution: upstream skill to rerun and what must change
- Optional `Related:` for clustered findings

# Fix Plan

Produce only for `PASS WITH CONDITIONS` or `FAIL`.

Execution order follows the pipeline:

`spec → criteria → rules → review`

Include only steps that require fixes.

Each fix has:

- `[cascades]` or `[localized]`
- finding ID
- concrete change
- for cascades, what downstream artifacts must be rerun

A fix **cascades** when it changes:

- a behavior ID or behavior definition
- use-case ownership or boundaries
- assumptions, edge cases, scope, or relationships with downstream effects
- an AC's meaning or EARS pattern
- addition/removal of an AC referenced by rules
- shared feature design
- ecosystem decisions
- rules that other rules or use cases depend on

A fix is **localized** when it changes no downstream contract.

If uncertain, mark `[cascades]`.

Always end with:

`### review`

and rerun review once all upstream fixes are complete.

# Success Criteria

Complete only when:

- codebase grounding ran first
- all five review categories ran
- feature-level and use-case-level artifacts were checked together
- scoped trace IDs resolve correctly
- cross-use-case conflicts were checked
- shared rules and local rules were checked for consistency
- Risk Hotspots is populated or explicitly empty
- every finding contains required fields
- quoted text matches source files
- verdict matches severity counts
- non-PASS verdict includes a Fix Plan
- Fix Plan follows pipeline order and ends with review

Do not write a partial review.

# Output

Write one feature-level file:

`spec/<feature>/review.md`

Do not create per-use-case review files.

```markdown
# Spec Review: <Feature>

## Summary
- Feature: <name>
- Verdict: <PASS | PASS WITH CONDITIONS | FAIL>
- Counts: <N blockers, N majors, N minors>
- Action: <one line>

## Discipline Check
<pass summary or findings>

## Conflicts
<pass summary or findings>

## Codebase Grounding
<pass summary or findings>

## EARS ↔ Test Strategy
<pass summary or findings>

## Risk Hotspots
<up to five hotspots>

## Fix Plan
<only for PASS WITH CONDITIONS or FAIL>

Execution order: <steps with fixes> → review

### <step>
1. [cascades|localized] <FINDING-ID>: <change>
   <downstream rerun note if needed>

### review
Rerun once all upstream fixes are in.
```

For a clean feature, keep `review.md` short. Bloat is a smell.
