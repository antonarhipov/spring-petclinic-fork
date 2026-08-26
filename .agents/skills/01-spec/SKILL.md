---
name: spec
description:  Turn a feature proposal into an implementation-ready specification by identifying the feature boundary, decomposing sufficiently large features into independently implementable use cases, and interviewing the user one question at a time to resolve ambiguities, assumptions, dependencies, and edge cases. Use when preparing requirements for a spec-driven development workflow before criteria, rules, review, and planning.
---

# Requirements Analyst Skill

Transform a feature proposal into a clean, implementation-ready specification.

Decompose sufficiently large features into use cases before specifying their detailed behavior. Treat the feature as the unit of product intent and each use case as the primary unit of implementation and traceability.

Pipeline position: proposal → **spec** → criteria → rules → review → plan

# Role

Prepare requirements for implementation by an AI coding agent.

Do not design the implementation unless an implementation constraint materially affects required behavior.

# Input

Primary input: `spec/proposal.md`

If `spec/proposal.md` does not exist, ask the user for the proposal and save it there before proceeding.

The proposal is initially unclassified. Infer the feature name and final specification hierarchy from its content.

# Target Structure

After analysis, organize the feature under:

```
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

Move the original `spec/proposal.md` to:

`spec/<feature-name>/proposal.md`

Do not leave two authoritative copies of the proposal.

For a feature that does not warrant decomposition, use:

```
spec/
└── <feature-name>/
    ├── proposal.md
    └── spec.md
```
Do not create use-case directories merely for structural consistency.

# Naming

Infer stable names from product intent rather than implementation details.

## Feature name

Use a short kebab-case capability or feature name.

Prefer:

* smart-appointment-scheduling
* order-refunds
* user-notifications

Avoid:

* implementation class names
* ticket IDs
* generic names such as feature
* names tied to a proposed technical solution unless that solution is itself the feature

## Use-case name

Use a short kebab-case action or user/system intent.

Prefer:

* request-appointment
* suggest-appointment-slot
* reject-suggested-slot
* confirm-appointment

Avoid splitting simple validation rules, internal processing steps, or minor variants into separate use cases.

# Phase 1: Analyze the Proposal

Before interviewing the user:

1. Read the proposal.
2. Inspect relevant code, configuration, tests, documentation, and conventions.
3. Infer the feature boundary.
4. Identify cross-cutting decisions.
5. Identify candidate use cases.
6. Identify ambiguities, missing information, implicit assumptions, edge cases, and decision dependencies.
7. Draft candidate observable behaviors to verify.

Use this analysis to determine both the feature structure and interview order.

Do not ask the user for information already answered by the codebase.

# Phase 2: Decide Whether to Decompose

Decompose the feature only when doing so creates meaningful implementation boundaries.

A candidate use case should normally satisfy at least two of these:

* It represents a distinct actor intent or system outcome.
* It can be implemented or tested substantially independently.
* It has its own meaningful decisions, rules, or edge cases.
* It could reasonably proceed through criteria, rules, review, or planning independently.
* Separating it reduces coupling or ambiguity in the resulting specification.

Do not create a separate use case for:

* a single validation rule
* an error message
* an internal helper operation
* one step that has no meaningful behavior outside another use case
* trivial CRUD variations that share the same decisions and behavior

Prefer the smallest set of use cases that gives each use case a coherent behavioral boundary.

# Decomposition Confidence

Do not ask the user to approve decomposition merely because decomposition exists.

If the feature boundary and use cases follow clearly from the proposal and codebase, decide them yourself.

Ask only when two or more materially different decompositions would change:

* product behavior
* scope
* ownership of behavior
* implementation sequencing
* traceability

When decomposition is ambiguous, ask one concrete question and recommend the structure you would choose.

# Phase 3: Interview

Walk the decision tree until the feature and every use case are implementation-ready.

Resolve feature-wide decisions before decisions that belong only to a single use case.

Then work through each use case independently while checking for interactions with the others.

# Interview Rules

* Ask one question at a time.
* Re-plan after every answer. An answer may collapse existing branches or reveal new ones.
* Use concrete, mutually exclusive options covering the realistic answer space.
* Recommend one option and give a one-line reason.
* Explain why the question matters in one short sentence.
* Check the codebase before asking.
* Prefer product-behavior questions over implementation-design questions.
* Never ask merely to populate a section of the document.
* Do not ask the user to choose something that can safely be inferred from existing product behavior or conventions.

# Worth-Asking Bar

Ask only if the answer changes at least one of:

* which behavior the system must exhibit
* which use case owns a behavior
* whether a use case exists at all
* which edge case is handled, deferred, or out of scope
* which assumption is safe to make
* which interaction between use cases is valid
* which observable result an implementing agent must produce

If none applies, decide it yourself.

Record such decisions under either:

* Resolved ambiguities
* Explicit assumptions

Include a short rationale.

Trivial questions waste turns and erode trust in the interview.

# Cross-Cutting vs Use-Case Decisions

Keep decisions at the narrowest level where they apply.

Put a decision in the feature-level spec when it affects:

* multiple use cases
* the overall product contract
* shared terminology
* ordering or interaction between use cases
* feature-wide constraints
* shared external dependencies

Put it in a use-case spec when it affects only that use case.

Do not duplicate the same decision across multiple files.

# Use-Case Relationships

After specifying individual use cases, verify their relationships.

Define where relevant:

* prerequisites
* ordering
* state transitions
* shared data
* mutually exclusive outcomes
* retries
* cancellation
* failure propagation
* behavior when one use case has already completed

Do not turn these relationships into implementation architecture unless required to define observable behavior.

# Behaviors to Verify

The criteria step depends on behaviors as its primary handoff. Treat them as a contract.

Each runtime behavior belongs to exactly one use case unless it is inherently feature-wide.

Use identifiers scoped to the use case:

UC1-B1, UC1-B2, UC2-B1, …

Rules:

* Number use cases in feature document order: UC1, UC2, …
* Number behaviors independently inside each use case.
* Keep identifiers stable once written unless the corresponding use case is removed.
* Emit one observable behavior per entry.
* Phrase behaviors as:

`the system <verb> <object> <under condition>`

Every resolved ambiguity, explicit assumption, and handled edge case that implies runtime behavior must produce at least one behavior.

Pure scoping decisions do not require a behavior.

Avoid:

* implementation steps
* internal method calls
* database design
* UI layout details unless layout itself is required behavior
* combining multiple observable outcomes into one behavior

# Success Criteria

Complete only when all these criteria are met:
* Feature boundary is explicit.
* Feature is either whole or decomposed into coherent use cases.
* Every use case has a clear outcome and boundary.
* Every worth-asking ambiguity has a concrete decision.
* All missing information is filled in.
* Implicit assumptions are made explicit.
* Every edge case is handled, deferred, or explicitly out of scope.
* Cross-use-case interactions are defined.
* Every runtime behavior is captured with an identifier.
* Behavior ownership is unambiguous.
* No implementable questions remain.gent would need to ask.

A clean spec has **zero open questions**.

Before writing the final files, perform a final verification pass.

If any criterion fails, return to the interview loop.

# External Dependencies

If a decision genuinely depends on information the user cannot currently provide, such as:

* another stakeholder
* blocked review
* vendor response
* unavailable policy decision

record it under `External` dependencies.

For each dependency include:

* the unresolved issue
* why it is blocked
* the proposed default behavior
* what would cause the default to be revisited

An external dependency is not an open question. The specification must still contain a concrete default that allows implementation to proceed.

# Output: Feature-Level Spec

Write:

spec/<feature-name>/spec.md

Use this structure:

```markdown
# <Feature Name>

## Feature summary

One paragraph describing the user-visible capability and its purpose.

## Use cases

- UC1 — <Use case title>
  `<use-case-name>/spec.md`
- UC2 — <Use case title>
  `<use-case-name>/spec.md`

Omit this section when the feature intentionally has no separate use cases.

## Cross-cutting decisions

Feature-wide decisions with concise rationale.

## Explicit assumptions

Only assumptions applying across the feature.

## Use-case relationships

Ordering, prerequisites, state transitions, or interactions between use cases.

## Out of scope

Product decisions intentionally deferred or excluded.

## External dependencies

Only when applicable.
```

The feature-level spec is a map and shared contract. Do not copy all use-case behaviors into it.

# Output: Use-Case Spec

For every use case, write:

`spec/<feature-name>/<use-case-name>/spec.md`

Use this structure:

```markdown
# UC<N>: <Use Case Title>

## Summary

One paragraph describing the actor intent or system outcome.

## Resolved ambiguities

Decisions made for this use case, with rationale.

## Explicit assumptions

Assumptions specific to this use case.

## Handled edge cases

Each relevant edge case and its defined behavior.

## Behaviors to verify

- UC<N>-B1: The system ...
- UC<N>-B2: The system ...

## Out of scope

Behavior intentionally deferred or excluded from this use case.

## External dependencies

Only when applicable.
```

# Output: Non-Decomposed Feature

If decomposition would create artificial boundaries, keep the feature as one specification.

Write:

`spec/<feature-name>/spec.md`

using:

```markdown
# <Feature Name>

## Feature summary

...

## Resolved ambiguities

...

## Explicit assumptions

...

## Handled edge cases

...

## Behaviors to verify

- B-1: The system ...
- B-2: The system ...

## Out of scope

...

## External dependencies

...
```

Use plain B-1, B-2, … only when there are no separate use cases.
