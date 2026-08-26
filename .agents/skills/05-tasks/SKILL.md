---
name: tasks
description: Generate an ordered, atomic implementation task list from validated feature and use-case spec artifacts, traceable to acceptance criteria and technical rules
---

# Task List Generator Skill

Translate a validated feature specification into an ordered, atomic, AC-traceable execution list an implementing agent can run task by task.

Pipeline position: proposal → spec → criteria → rules → review → **tasks** → execute

# Role

Translate validated spec artifacts into one feature-level task list.

Do not write code, run tests, or modify project files outside the feature's `tasks.yaml`.

Do not ask questions. Record planning judgment calls in `decisions`.

# Inputs

Feature-level:

- `spec/<feature>/proposal.md`
- `spec/<feature>/spec.md`
- `spec/<feature>/rules.md` if present
- `spec/<feature>/review.md` if present

Per use case:

- `spec/<feature>/<use-case>/spec.md`
- `spec/<feature>/<use-case>/criteria.md`
- `spec/<feature>/<use-case>/rules.md` if present

For non-decomposed features:

- `spec/<feature>/criteria.md`
- `spec/<feature>/rules.md` if present

Project conventions:

- `CLAUDE.md` / `AGENTS.md` / `GEMINI.md`
- build files
- source tree

Spec and criteria take precedence over proposal.

# Pipeline Contract

Read `spec/<feature>/review.md` first when present.

Locate the verdict under `## Summary`.

- **FAIL**: refuse. Print blocker IDs and recommend rerunning the upstream skills from the Fix Plan. Do not write `tasks.yaml`.
- **PASS WITH CONDITIONS**: every unresolved major must appear either as a dedicated task with `source: review/MAJOR-N` or as a `risk` annotation. Note this in `assumptions`.
- **PASS**: proceed.

Risk Hotspots become `risk` annotations on relevant tasks regardless of verdict.

# Codebase Grounding

Read project guidance and inspect the source tree.

Identify:

- package and module boundaries
- naming conventions
- architectural style
- build and test patterns
- existing implementation paths for the relevant feature area

Place task artifacts consistently with the existing codebase unless rules explicitly require a deviation.

# Use Cases as Planning Units

For decomposed features, use cases are the default implementation slices.

Each use case already represents an independently specified behavioral unit with scoped acceptance criteria:

`UC<N>-AC<M>`

Prefer keeping tasks for one use case together unless a shared prerequisite or architectural dependency requires another order.

Shared infrastructure or feature-level design may be implemented before multiple use cases depend on it.

Do not force one phase per use case when this would delay necessary end-to-end feedback.

# Phase Organization

Choose and record `organizing_principle`:

- **feature_slice** — implement use cases or AC clusters as independently testable slices
- **walking_skeleton** — establish a thin end-to-end path first
- **risk_first** — resolve load-bearing technical uncertainty first
- **layered** — data → domain → application → presentation when the architecture strongly favors it

## Selection Ladder

1. **Explicit mandate**  
   Follow rules or invocation instructions.

2. **Architectural risk**  
   If review identifies a load-bearing architectural hotspot, use `risk_first`.

3. **Unproven end-to-end path**  
   If the feature crosses a new integration or architectural seam, use `walking_skeleton`.

4. **Decomposed use cases**  
   If use cases can be implemented and validated substantially independently, use `feature_slice`.

5. **Fallback**  
   Mirror the existing architecture, commonly `layered`.

If two principles genuinely apply, use at most two:

`walking_skeleton then feature_slice`

Record the handoff in `decisions`.

# Tracer Bullets

When integration risk exists, implement a minimal end-to-end path first, validate it, then expand behavior.

A tracer bullet should cross the real architectural boundaries without attempting to satisfy every AC.

Use it for feedback, not as an excuse to build throwaway architecture.

# Task Granularity

Each task should:

- fit one focused implementation effort
- produce a verifiable artifact or outcome
- be independently understandable
- be small enough to roll back
- reference the ACs and rules it implements

Avoid tasks such as:

`Implement UC2`

Prefer:

`Add scheduling constraint domain model`
`Map parsed availability into scheduling constraints`
`Add slot-selection integration test for UC2-AC1`

# Traceability

Use scoped identifiers from upstream artifacts.

Acceptance criteria:

- `UC1-AC1`
- `UC2-AC3`

Use-case rules:

- `UC1-RULE1`
- `UC2-RULE2`

Feature rules:

- `RULE-1`
- `RULE-2`

Every AC must appear in:

- at least one task's `covers.acs`, or
- `coverage_deferrals`

No third option.

Feature rules may be referenced by tasks from multiple use cases.

# Dependency Rules

- no circular dependencies
- every dependency references an earlier task
- minimize cross-phase dependencies
- shared prerequisites before dependent use cases
- interfaces before implementations when consumers depend on them
- migrations before code requiring the new schema
- fixtures/support before tests depending on them

Do not impose infrastructure-before-business-logic mechanically when a walking skeleton gives faster validation.

# Checkpoints

Add checkpoints where human review materially reduces risk:

- shared architecture or scaffolding completed
- first end-to-end slice works
- major integration boundary works
- a use case's ACs are satisfied
- migrations or irreversible changes are ready
- phase test suite is green

Every phase ends with a checkpoint.

# Stable IDs

- phases: `phase-1`, `phase-2`, ...
- tasks: `task-N.M`
- checkpoints: `cp-N` or `cp-N.M`
- decisions: `dec-1`, `dec-2`, ...

Task IDs describe execution order, not use-case ownership.

Use `covers.acs` for ownership and traceability.

# Soft Limits

Aim for:

- ≤ 5 phases
- ≤ 7 tasks per phase

If substantially exceeded, record whether the feature should be split.

Do not merge unrelated tasks merely to satisfy the limit.

# Output Schema

```yaml
tasks:
  feature: "<name>"
  review_verdict: "<PASS | PASS WITH CONDITIONS>"
  organizing_principle: "feature_slice | walking_skeleton | risk_first | layered | <two-part hybrid>"

  assumptions:
    - "<assumption>"

  decisions:
    - id: dec-1
      decision: "<planning decision>"
      reason: "<why>"
      alternatives: ["<alternative>"]

  coverage_deferrals:
    - ac: UC3-AC4
      reason: "<why deferred>"

  phases:
    - id: phase-1
      name: "<phase>"
      description: "<outcome>"
      covers: [UC1-AC1, UC2-AC1]
      entry_criteria: "<optional prerequisite>"

      tasks:
        - id: task-1.1
          name: "<task>"
          description: "<what to implement>"
          artifact: "<file path or observable outcome>"
          covers:
            acs: [UC1-AC1]
            rules: [RULE-2, UC1-RULE1]
          depends_on: []
          complexity: "S | M | L"
          validation: "<how completion is verified>"
          risk: "<review hotspot if applicable>"
          source: "<review/MAJOR-N if applicable>"

      checkpoint:
        id: cp-1
        description: "<what to review>"
        criteria:
          - "<checkpoint criterion>"
```

Required:

- top-level: `feature`, `review_verdict`, `organizing_principle`, `phases`
- phase: `id`, `name`, `description`, `covers`, `tasks`, `checkpoint`
- task: `id`, `name`, `description`, `artifact`, `covers`, `depends_on`, `validation`
- checkpoint: `id`, `description`, `criteria`

Optional:

- `assumptions`
- `decisions`
- `coverage_deferrals`
- `entry_criteria`
- `complexity`
- `risk`
- `source`

# Success Criteria

Complete only when:

- Pipeline Contract is honored
- every use case has been considered
- every AC appears in a task or `coverage_deferrals`
- every referenced AC and RULE exists
- feature and local rules are both respected
- every dependency points backward in execution order
- no circular dependencies exist
- every phase ends with a checkpoint
- organizing principle is recorded and justified
- every review Risk Hotspot maps to a task risk
- PASS WITH CONDITIONS findings are represented
- soft-limit deviations are justified

# Verification Pass

Before writing:

1. Walk tasks in execution order and validate every `depends_on`.
2. Collect all AC IDs from every criteria file.
3. Verify:

   `task covers.acs ∪ coverage_deferrals = all ACs`

4. Verify every local rule ID exists in its use-case `rules.md`.
5. Verify every feature rule ID exists in feature `rules.md`.
6. Verify shared prerequisites occur before dependent use-case tasks.
7. Verify no use case was accidentally omitted.
8. Verify every phase has a checkpoint.

Do not write a partial task list.

# Output

Write one feature-level file:

`spec/<feature>/tasks.yaml`

Do not create separate task lists per use case.
