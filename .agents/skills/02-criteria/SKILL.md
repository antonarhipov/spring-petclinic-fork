---
name: criteria
description: Translate a resolved feature specification into complete, traceable acceptance criteria in EARS form. For decomposed features, process each use case independently and write acceptance criteria beside its spec while using the feature-level spec for shared context and constraints. Use after the spec step and before rules, review, and planning.
---

# Acceptance Criteria Skill

Translate the resolved specification into independently testable, implementation-agnostic acceptance criteria using EARS templates.

For decomposed features, treat each use case as the primary unit of acceptance criteria and traceability.

Pipeline position:

proposal → spec → **criteria** → rules → review → plan

# Role

Translate specified behaviors into testable acceptance criteria.

Do not invent product decisions.

If a behavior cannot be expressed as a testable criterion without making a new product decision, route the gap back to the spec step.

# Inputs

Specifications live under:

```text
spec/
└── <feature-name>/
    ├── proposal.md
    ├── spec.md
    ├── <use-case-name>/
    │   └── spec.md
    ├── <use-case-name>/
    │   └── spec.md
    └── ...
```

The feature-level spec is:

`spec/<feature-name>/spec.md`

The proposal is:

`spec/<feature-name>/proposal.md`

For decomposed features, each use-case spec is:

`spec/<feature-name>/<use-case-name>/spec.md`

The `Behaviors to verify` section of each use-case spec is the primary contract.

The feature-level spec provides shared context such as:

- cross-cutting decisions
- explicit feature-wide assumptions
- use-case relationships
- out-of-scope decisions
- external dependencies

Spec takes precedence over proposal where they disagree.

# Determine Processing Mode

Read the feature-level spec first.

If it contains use cases, process each use case independently.

For each use case:

1. Read the feature-level spec.
2. Read the use-case spec.
3. Read the proposal only when additional context is useful.
4. Extract acceptance criteria for that use case.
5. Write `criteria.md` beside that use-case spec.

Example:

```text
spec/
└── smart-appointment-scheduling/
    ├── proposal.md
    ├── spec.md
    │
    ├── request-appointment/
    │   ├── spec.md
    │   └── criteria.md
    │
    ├── suggest-appointment-slot/
    │   ├── spec.md
    │   └── criteria.md
    │
    └── confirm-appointment/
        ├── spec.md
        └── criteria.md
```

Do not produce one feature-wide `criteria.md` when the feature has separate use cases.

If the feature was intentionally not decomposed, write:

`spec/<feature-name>/criteria.md`

# Scope Isolation

When processing a use case, generate criteria only for behaviors owned by that use case.

Do not copy criteria from other use cases merely because they are related.

Use feature-level decisions only when they constrain the current use case.

Example:

If the feature-level spec states:

> Only one appointment slot may be presented at a time.

and that constraint affects `suggest-appointment-slot`, create acceptance criteria for it there.

Do not repeat the same criterion in unrelated use cases.

# Cross-Use-Case Behavior

Some observable behavior may arise specifically from the relationship between use cases.

Assign the criterion to the use case where the observable outcome occurs.

For example:

- rejecting a slot causes another slot to become eligible  
  → belongs to `reject-suggested-slot`

- confirming a slot prevents it from being offered again  
  → belongs to `confirm-appointment`

Do not create a feature-level criterion solely because multiple use cases participate in the behavior.

If ownership cannot be determined without a product decision, route the ambiguity back to the spec step.

# EARS Templates

Use the smallest template that fits the requirement.

Do not invent new templates.

## Ubiquitous

Always-true behavior:

`The <system> shall <response>.`

## Event-driven

Triggered by an event:

`When <trigger>, the <system> shall <response>.`

## State-driven

Active during a state:

`While <state>, the <system> shall <response>.`

## Optional feature

Applies when a feature is included:

`Where <feature is included>, the <system> shall <response>.`

## Unwanted behavior

Explicit handling of an undesired trigger:

`If <trigger>, then the <system> shall <response>.`

## Combined

State plus event:

`While <state>, when <trigger>, the <system> shall <response>.`

Example:

```text
If an appointment request contains no usable availability constraints,
then the scheduling system shall reject the request and report that
availability information is required.
```

# Patterns

## Boundary

For any bounded value, write three criteria:

1. one within bounds
2. one at the boundary
3. one beyond the boundary

Do this only when the specification defines or clearly implies the bound.

Do not invent numeric limits.

## Error

Use the Unwanted behavior template.

Specify what the system shall do, not merely what it shall avoid.

Define atomicity when relevant:

- all-or-nothing
- partial completion with reported failures

## State transition

Write one criterion per transition.

Example:

`While an appointment proposal is pending, when the pet owner confirms it, the scheduling system shall transition the appointment to confirmed.`

If the spec requires an observable event or side effect, give that outcome its own criterion unless it is inseparable from the transition.

## Negative

For authorization, forbidden actions, or side-effect-sensitive paths, use the Unwanted behavior template and explicitly state the prohibited outcome.

Example:

`If an unauthorized user attempts to confirm an appointment, then the system shall reject the request and shall not modify the appointment.`

The explicit prohibited outcome is required for negative criteria.

## Non-functional

Use the same EARS templates with measurable thresholds and operating context.

Example:

`When 100 concurrent scheduling requests are sustained for 60 seconds, the scheduling system shall return responses with p95 latency below 200 ms.`

Never invent thresholds that are absent from the spec, codebase, or established project requirements.

# Anti-Patterns

Bad:

`The system SHALL store appointments in PostgreSQL using JDBC.`

Better:

`The system SHALL persist confirmed appointments such that they survive application restart.`

Do not prescribe implementation.

Bad:

`The system SHALL validate the request AND select a veterinarian AND create the appointment.`

Better:

Create separate criteria for distinct observable outcomes.

Compound criteria are not independently testable.

Bad:

`The system SHALL be fast and intuitive.`

Better:

Use measurable requirements only.

If no measurable requirement exists, record the category under `Coverage exclusions`.

Bad:

`When the user clicks the blue button in the bottom-right...`

Better:

`When the user confirms the proposed appointment...`

Describe user intent, not incidental UI implementation.

# Granularity

Use one observable outcome per criterion.

If a SHALL clause naturally contains multiple independently observable outcomes, split it.

A criterion may include a necessary negative clause when both parts describe one atomic contract.

Example:

`If the slot is no longer available, then the scheduling system shall reject confirmation and shall not create an appointment.`

Do not split this if the absence of creation is part of the same atomic failure contract.

# Traceability

Every criterion must have:

- a stable acceptance-criterion ID
- a `Covers:` line
- exactly one EARS statement

For decomposed features, behavior IDs are already scoped by use case:

- `UC1-B1`
- `UC1-B2`
- `UC2-B1`

Acceptance-criterion IDs are scoped the same way:

- `UC1-AC1`
- `UC1-AC2`
- `UC2-AC1`

Use the use-case number declared in the feature-level spec.

Example:

```markdown
### UC2-AC1: Return one appointment proposal

**Covers:** UC2-B1

When an eligible appointment slot exists, the scheduling system shall return one appointment slot to the pet owner.
```

Keep IDs stable once written unless the corresponding criterion is removed.

# Traceability to Shared Decisions

Normally, every criterion should cover at least one behavior ID.

If a feature-level decision implies runtime behavior but does not map to a behavior ID, do not silently derive a criterion from it.

Flag this as a spec gap.

Example:

```text
Spec gap:
Cross-cutting decision "Only one slot may be exposed at a time"
implies observable runtime behavior but is not represented by any UC<N>-B<N>.
```

Route this back to the spec step so the behavior has explicit ownership.

Do not use section-heading references as a substitute for missing behavior IDs when the content implies runtime behavior.

The behaviors are the contract.

# Coverage Rule

Every behavior in the current use-case spec must be covered by at least one acceptance criterion.

One behavior may require several criteria.

This is expected for:

- multiple triggers
- boundary cases
- error paths
- state transitions
- negative outcomes

One criterion may cover multiple behavior IDs only when they describe the same indivisible observable contract.

Prefer one primary behavior per criterion.

# Codebase Resolution

If a missing detail can be resolved from existing code, configuration, tests, or established project conventions:

1. inspect the codebase
2. use the discovered value
3. note the source during analysis
4. generate the corresponding criterion

Do not route back to the spec merely because the spec omitted something already authoritative in the project.

However, if existing code conflicts with the resolved specification, the specification wins unless explicitly marked otherwise.

# Route-Back Threshold

Route back to the spec step when any of the following is true:

- A behavior cannot be phrased as a testable trigger/response without inventing a product decision.
- Two or more behaviors depend on the same unresolved decision.
- A boundary value is required but unspecified.
- An error path is implied but its behavior is undefined.
- A state transition is implied but its resulting state is undefined.
- A feature-level decision implies runtime behavior but no use case owns it.
- A behavior appears to belong to multiple use cases and ownership matters.
- A required interaction between use cases is ambiguous.

Do not create `criteria.md` with guessed decisions merely to avoid returning to the spec step.

# Non-Functional Coverage

For every use case, consider whether these categories apply:

- performance
- security
- authorization
- observability
- accessibility
- compatibility
- reliability

Create acceptance criteria only when the specification, project requirements, or codebase establishes a measurable contract.

Otherwise record the relevant category under `Coverage exclusions`.

Do not mechanically add every category to every use case.

Only list categories that were considered and are plausibly relevant.

# Success Criteria

Complete a use case only when all of the following hold:

- Every behavior ID in the use-case spec is covered by at least one acceptance criterion.
- Every criterion traces to at least one behavior ID.
- Every bounded value requiring validation has the three-criterion boundary pattern.
- Every specified error path uses the Unwanted behavior template.
- Error criteria define observable behavior and atomicity where relevant.
- Every specified state transition has a transition criterion.
- Every authorization-relevant path has a negative criterion.
- Relevant non-functional categories are either covered or recorded under `Coverage exclusions`.
- Every criterion uses an EARS template.
- Every criterion has one observable SHALL outcome.
- No criterion prescribes implementation.
- No criterion contains rationale.
- No new product decision has been invented.
- No cross-use-case behavior lacks clear ownership.

Run a verification pass before writing.

Do not write a partial `criteria.md`.

If verification fails because the spec is incomplete, route the issue back to the spec step instead.

# Output: Decomposed Feature

For each use case, write:

`spec/<feature-name>/<use-case-name>/criteria.md`

Use:

```markdown
# Acceptance Criteria: UC<N> — <Use Case Title>

## Functional

### UC<N>-AC1: <title>

**Covers:** UC<N>-B1

<EARS statement>

### UC<N>-AC2: <title>

**Covers:** UC<N>-B2

<EARS statement>

## Non-functional

### UC<N>-AC<N>: <title>

**Covers:** UC<N>-B<N>

<EARS statement>

## Coverage exclusions

- <category>: <reason>
```

Omit an empty `Non-functional` section.

Omit `Coverage exclusions` when there are no meaningful exclusions to record.

# Output: Non-Decomposed Feature

If the feature has no separate use cases, write:

`spec/<feature-name>/criteria.md`

Use the original feature-wide identifiers:

- `B-1`, `B-2`, ...
- `AC-1`, `AC-2`, ...

Structure:

```markdown
# Acceptance Criteria: <Feature>

## Functional

### AC-1: <title>

**Covers:** B-1

<EARS statement>

### AC-2: <title>

**Covers:** B-2

<EARS statement>

## Non-functional

### AC-N: <title>

**Covers:** B-N

<EARS statement>

## Coverage exclusions

- <category>: <reason>
```

# Final Verification Pass

Before completing each `criteria.md`:

1. Re-read the feature-level spec.
2. Re-read the current use-case spec.
3. Enumerate every behavior ID.
4. Verify that every behavior is covered.
5. Verify that every criterion maps back to an owned behavior.
6. Check boundaries, error paths, state transitions, and negative paths.
7. Check feature-wide constraints that apply to this use case.
8. Check that no criterion belongs more naturally to another use case.
9. Check that no implementation detail has leaked into the criteria.
10. Check that no criterion requires an unstated product decision.

For a decomposed feature, repeat this verification independently for every use case.

Only consider the criteria step complete when every use case passes verification.
