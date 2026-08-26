---
name: rules
description: Capture feature-level and use-case-specific technical design decisions and constraints, grounded in the codebase and traceable to acceptance criteria
---

# Technical Design and Constraints Skill

Translate specs and criteria into the technical design decisions an implementing agent would otherwise invent.

Constraints specify HOW the system should be built, complementing acceptance criteria which specify WHAT.

Pipeline position: proposal → spec → criteria → **rules** → review → plan

# Role

Make non-trivial technical decisions and record them as validatable constraints.

Do not invent product decisions. If a rule requires resolving product behavior, route the gap to `spec`. If an acceptance criterion is too vague, route it to `criteria`.

# Inputs

Feature-level:

- `spec/<feature>/spec.md`
- `spec/<feature>/proposal.md`
- `spec/<feature>/rules.md` (output, when shared rules exist)

Per use case:

- `spec/<feature>/<use-case>/spec.md`
- `spec/<feature>/<use-case>/criteria.md`
- `spec/<feature>/<use-case>/rules.md` (output, when local rules exist)

Project conventions:

- `CLAUDE.md` / `AGENTS.md` / `GEMINI.md`
- top-level build files
- existing source layout and implementation patterns
- ADRs and `docs/architecture`, if present

Spec and criteria take precedence over proposal.

# Rule Scope

Assign each decision to the narrowest scope where it remains correct.

**Feature-level rule:** use when the decision affects multiple use cases or defines shared architecture, data models, dependencies, integrations, security, persistence, concurrency, or API boundaries.

**Use-case rule:** use when the decision constrains only one use case.

Feature-level rules are inherited by all use cases. Do not duplicate them in use-case files.

If a supposedly local rule affects another use case, promote it to feature level.

For a feature without separate use cases, keep all rules in `spec/<feature>/rules.md`.

# Codebase Grounding

Before writing any rule, inspect project guidance and relevant code.

Identify:

- package/module layout
- frameworks and versions
- persistence and transaction patterns
- error handling
- logging and observability
- testing conventions
- security patterns
- dependency policy
- relevant architectural boundaries

This is non-negotiable.

Rules capture the **diff from existing conventions**. If the project already establishes the answer, inherit it instead of restating it.

# Analysis Pass

Read the feature spec, all use-case specs and criteria, then inspect the relevant codebase.

Consider:

1. Project structure and boundaries
2. Components and responsibilities
3. Libraries and technology choices
4. Design patterns
5. Error handling
6. Testing strategy
7. Security
8. Observability
9. Concurrency
10. Persistence and transactions
11. API contracts
12. Performance constraints
13. Dependencies

For each ask:

> Does this require a feature-specific decision, a use-case-specific decision, or can it inherit existing conventions?

Before deciding use-case rules, identify shared capabilities so separate use cases do not invent incompatible models, services, persistence strategies, or dependencies.

# Ecosystem Survey

For technology or architecture decisions, check whether:

- a canonical framework or library exists that the project does not use
- multiple equally valid approaches exist
- the feature introduces a new architectural pattern
- the choice creates long-term coupling beyond one use case

If yes, treat it as a judgment call.

Examples: Spring Batch, Spring Integration, Flyway/Liquibase, Testcontainers, Resilience4j, Timefold.

Do not silently choose the existing stack just because it is already present.

# Interactive Resolution

Mechanical decisions inherit from the project.

Judgment calls require user input.

Use AskUserTool with:

- 2–4 concrete options
- the leanest viable path included
- one option marked `(recommended)`
- one-line reason and trade-off per option
- one question at a time

Resolve shared decisions at feature level. Resolve local decisions while processing that use case.

If input depends on an unavailable stakeholder, record it under `External dependencies` with blocker and default.

# Worth-Recording Bar

Emit a rule only if at least one holds:

- feature-specific decision not already established by the project
- use-case-specific technical constraint
- deliberate deviation from project conventions
- AC requires a technical invariant
- meaningful ecosystem option was deliberately declined
- shared boundary between use cases must be protected

Do not record generic best practices or restate project defaults.

Negative decisions matter and should be preserved.

# Constraint Language

Use RFC 2119 language:

- **MUST / MUST NOT** — required for correctness, security, compatibility, AC, or invariant
- **SHOULD / SHOULD NOT** — strong preference; deviation requires justification
- **MAY** — explicit implementation freedom

Use the weakest level that accurately expresses the constraint.

# Identifiers

Feature-level:

`RULE-1`, `RULE-2`, ...

Use-case-specific:

`UC1-RULE1`, `UC1-RULE2`, `UC2-RULE1`, ...

Keep IDs stable once written.

# Rule Format

Each rule has:

- stable ID
- `Covers:` line
- concrete RFC 2119 statement
- `Reason:` line

Feature-level example:

```markdown
### RULE-2
**Covers:** UC1-AC2, UC2-AC1
**MUST** represent scheduling constraints using the shared `SchedulingConstraints` domain model before slot selection.
**Reason:** Both use cases depend on the same interpretation of scheduling constraints.
```

Use-case example:

```markdown
### UC1-RULE1
**Covers:** UC1-AC3
**MUST** reject temporal expressions that cannot be mapped to the supported scheduling model.
**Reason:** UC1-AC3 requires ambiguous input to fail explicitly.
```

For architectural decisions not tied directly to one AC:

`Covers: feature-wide`

Use sparingly.

# Acceptance-Criteria Coverage

Evaluate every AC for whether it needs a technical constraint.

It is valid for an AC to need none.

Do not manufacture rules to achieve one-to-one coverage.

Record `(none needed)` in the cross-reference when observable behavior is sufficiently constrained by the AC and existing conventions.

Feature rules may cover ACs from multiple use cases.

Use-case rules normally cover only ACs from their own use case.

# Anti-Patterns

**Bad:** `MUST be well-architected.`  
**Better:** `MUST keep constraint evaluation behind the SchedulingService boundary.`

**Bad:** `MUST handle errors properly.`  
**Better:** `MUST NOT propagate optimizer exceptions across the application-service boundary.`

**Bad:** `MUST use Spring Boot because the project uses it.`  
Drop it; this is inherited.

**Bad:** duplicate the same shared rule in multiple use cases.  
Promote it to feature level.

**Bad:** choose an ecosystem option silently.  
Surface the judgment call first.

# Route-Back Triggers

Route to `spec` when:

- a rule requires inventing product behavior
- use-case ownership is unclear
- a missing interaction between use cases affects behavior

Route to `criteria` when:

- an AC is too vague to derive or validate a constraint
- a non-functional AC lacks a necessary threshold
- competing technical approaches create materially different observable behavior that criteria do not disambiguate

Do not hide missing requirements behind `SHOULD`.

# Success Criteria

Complete only when:

- codebase conventions were inspected first
- shared architecture was considered before local rules
- every relevant design category was considered
- every Ecosystem Survey judgment call was resolved interactively
- every emitted rule passes the Worth-Recording Bar
- every rule has ID, modal, concrete statement, `Reason:`, and `Covers:`
- shared rules appear only at feature level
- local rules affect only their use case
- every AC maps to rules or `(none needed)`
- deviations from project conventions are justified
- no rule invents product behavior

Run a verification pass before writing.

# Output: Feature Rules

If shared technical decisions exist, write:

`spec/<feature>/rules.md`

```markdown
# Technical Design and Constraints: <Feature>

## Overview
<feature and relevant stack>

## Design
Components: <shared components and responsibilities>
Boundaries: <shared architectural boundaries>
Flow: <how use cases interact through the design>
Key dependencies: <added, reused, or deliberately declined dependencies>

## Codebase Alignment
<inherited conventions and justified deviations>

## Rules

### RULE-1
**Covers:** ...
**MUST/SHOULD/...** ...
**Reason:** ...

## Cross-Reference

| AC | Rules |
|---|---|
| UC1-AC1 | RULE-1 |
| UC1-AC2 | (none needed) |

## Design Exclusions
<only when relevant>

## External Dependencies
<only when relevant>
```

Do not create an empty feature `rules.md` if no shared decisions exist.

# Output: Use-Case Rules

For a use case with local constraints, write:

`spec/<feature>/<use-case>/rules.md`

```markdown
# Technical Constraints: UC<N> — <Use Case>

## Design
<local components or flow only>

## Rules

### UC<N>-RULE1
**Covers:** UC<N>-AC1
**MUST/SHOULD/...** ...
**Reason:** ...

## Cross-Reference

| AC | Rules |
|---|---|
| UC<N>-AC1 | UC<N>-RULE1 |
| UC<N>-AC2 | (none needed) |

## Design Exclusions
<only when relevant>

## External Dependencies
<only when relevant>
```

Do not repeat feature-level design or rules here.

Do not create a use-case `rules.md` when no local rules are needed.

# Final Verification

Before completing:

1. Verify shared decisions appear only at feature level.
2. Verify local rules constrain only their use case.
3. Verify every AC maps to rules or `(none needed)`.
4. Verify no rule restates project conventions.
5. Verify all ecosystem judgment calls were surfaced.
6. Verify no rule invents product behavior.
7. Verify negative technical decisions were preserved.
8. Verify an implementing agent can understand feature architecture before implementing a use case.
