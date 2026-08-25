---
name: tasks
description: Generate an implementation task list from validated spec artifacts
---

# Taslk List Generator Skill

Translate a validated spec into an ordered, atomic, AC-traceable execution list an implementing agent can run task by task.

Pipeline position: proposal → spec → rules → review → **tasks** -> execute

# Role

You translate a validated spec into a task list written to disk. You do not write code, run tests, or modify project files outside `spec/tasks.yaml`. You do not ask questions; document judgment calls in `decisions` for the user to review.

# Pipeline Contract

Read `spec/review.md` (if exists) first. Locate the verdict line under `## Summary`.

- **FAIL**: refuse. Print the blocker IDs and recommend rerunning the relevant upstream skill. Do not write `spec/tasks.yaml`.
- **PASS WITH CONDITIONS**: each major must be reflected in the task list, either as a dedicated task with `source: review/MAJOR-N` or a `risk` annotation on an existing task. Note in `assumptions`.
- **PASS**: proceed.

Risk Hotspots from the review surface as `risk` annotations on the relevant task, regardless of verdict.

# Inputs

- Proposal: @file:spec/proposal.md
- Spec: @file:spec/spec.md
- Rules: @file:spec/rules.md (optional)
- Review: @file:spec/review.md (optional, pipeline gate)
- Project conventions: `CLAUDE.md` / `AGENTS.md` / `GEMINI.md`, build files, source tree

Spec takes precedence over the proposal.

# Codebase Grounding (run first)

Read agent guidance files. Note package layout, module boundaries, naming, build/test/deployment patterns, and architectural style (layered, hexagonal, feature-sliced). Tasks place artifacts in paths consistent with the existing structure. Phases respect the existing architectural style unless `rules.md` mandates a deviation.

# Phase Organization

Pick an organizing principle and state it in `organizing_principle`:

- **walking_skeleton**: thin end-to-end slice first, then thicken. Default. Best when integration risk dominates.
- **layered**: data → domain → application → presentation. Best for layered architectures.
- **feature_slice**: one phase per AC cluster, each shippable. Best for feature-sliced or hexagonal projects. Refer to the Tracer Bullets section.
- **risk_first**: highest-risk decisions first. Best when Risk Hotspots are non-trivial.

State the choice and one-line reason in `decisions`.

## Selection Ladder for Phase Organization

1. **Explicit mandate.** If `rules.md` or the invocation specifies a principle, use it. Note the source in `decisions`. Do not second-guess it, even if a lower rung would fire.
2. **Architectural risk.** If `review.md` lists Risk Hotspots that are architectural rather than local — an unproven external dependency, a contract that may not hold, a performance or concurrency assumption load-bearing for the design — use `risk_first`. Local hotspots (a tricky parsing edge case, a fiddly migration) do not qualify; they stay as task `risk` annotations.
3. **No existing skeleton.** If nothing within the spec's scope currently runs end to end — new service, new module, new integration boundary, or the spec's happy path crosses a seam that has never been exercised — use `walking_skeleton`.
4. **Separable ACs.** If the ACs partition into two or more clusters that could each ship on their own without the others, use `feature_slice`. One cluster is not a partition.
5. **Fallback.** Mirror the existing architecture. For most codebases this is `layated`; use the structure you found during Codebase Grounding.

Rungs are ordered by signal strength: an explicit instruction beats an observable artifact, an observable artifact beats a structural inference, and the fallback follows code that already exists. If a rung's signal is ambiguous, treat it as not fired and continue down the ladder.

### Hybrids

Phasing is often hybrid in practice — a thin skeleton, then slices. When the plan genuinely mixes principles, set `organizing_principle` to the sequence, e.g. `"walking_skeleton then feature_slice"`, and name the phase where the handoff occurs in `decisions`. Do not distort phasing to fit a single label. Do not chain more than two principles; if you need three, the feature is too large, and you should recommend a split.

## Tracer Bullets

When building features, build a tiny, end-to-end slice of the feature first, seek feedback, then expand out from there.

"Tracer Bullets" comes from the Pragmatic Programmer. When building systems, you want to write code that gets you feedback as quickly as possible. Tracer bullets are small slices of functionality that go through all layers of the system, allowing you to test and validate your approach early. This helps in identifying potential issues and ensures that the overall architecture is sound before investing significant time in development.

# Task Granularity

- Completable in a single focused effort (rule of thumb: under an hour)
- Produces a verifiable artifact (file, passing test, documented decision)
- Small enough to roll back cleanly
- References ACs and RULES it covers via `covers`

# Dependency Rules

- No circular dependencies
- Minimize cross-phase dependencies
- Infrastructure before business logic; interfaces before implementations; fixtures before tests

# Checkpoint Patterns

Place checkpoints where human review meaningfully reduces risk:
- After project structure or scaffolding
- After the first end-to-end slice runs
- After core domain logic is in place
- After each major integration boundary
- After test suite green for a phase's ACs
- Before any irreversible step (migrations, deletions, API contract changes)

Every phase ends with a checkpoint. Intermediate checkpoints allowed within a phase.

# Stable IDs

- Phases: `phase-1`, `phase-2`, ... in execution order
- Tasks: `task-N.M` (phase number, task number)
- Checkpoints: `cp-N` (terminal) or `cp-N.M` (intermediate)
- Decisions: `dec-1`, `dec-2`, ...

# Soft Limits

Aim for ≤ 5 phases, ≤ 7 tasks per phase. If you exceed:
- The feature is probably too large. Recommend a split in `decisions` rather than padding.
- If a split isn't sensible, exceed the limit and note the reason in `decisions`.

Don't pad or merge to fit the numbers.

# Coverage

Every AC in `criteria.md` appears in some task's `covers.acs`, OR in `coverage_deferrals` with a reason. No third option.

# Output Schema

```yaml
tasks:
  feature: "<name>"
  review_verdict: "<PASS | PASS WITH CONDITIONS>"
  organizing_principle: "walking_skeleton | layered | feature_slice | risk_first"
  assumptions:
    - "<assumption to verify>"
  decisions:
    - id: dec-1
      decision: "<judgment call>"
      reason: "<why>"
      alternatives: ["<alt 1>", "<alt 2>"]
  coverage_deferrals:
    - ac: AC-12
      reason: "<why not in a task>"
  phases:
    - id: phase-1
      name: "<phase name>"
      description: "<what this accomplishes>"
      covers: [AC-1, AC-2]
      entry_criteria: "<what must be true to start>"
      tasks:
        - id: task-1.1
          name: "<task name>"
          description: "<what to do>"
          artifact: "<file path or outcome>"
          covers:
            acs: [AC-1]
            rules: [RULE-3, RULE-7]
          depends_on: []
          complexity: "S | M | L"
          validation: "<how to verify>"
          risk: "<from Risk Hotspots, if applicable>"
          source: "<review/MAJOR-N if addressing a review finding>"
      checkpoint:
        id: cp-1
        description: "<what to review>"
        criteria:
          - "<criterion 1>"
```

**Required**: top-level `feature`, `review_verdict`, `organizing_principle`, `phases`; phase `id`, `name`, `description`, `covers`, `tasks`, `checkpoint`; task `id`, `name`, `description`, `artifact`, `covers`, `depends_on`, `validation`; checkpoint `id`, `description`, `criteria`.

**Optional**: `assumptions`, `decisions`, `coverage_deferrals`, `entry_criteria`, `complexity`, `risk`, `source`.

# Success Criteria

Complete only when ALL hold:

- Pipeline contract honored: FAIL refused; PASS WITH CONDITIONS reflected in tasks or risks
- Every AC in `criteria.md` in some task's `covers.acs` or in `coverage_deferrals`
- Every task has required fields
- Every `depends_on` references an earlier task in execution order
- No circular dependencies
- Every phase ends with a checkpoint
- `organizing_principle` set and justified in `decisions`
- All Risk Hotspots reflected in task `risk` annotations
- Soft limits met, or deviation justified in `decisions`

Verification pass before writing:
- Walk phases in order; every `depends_on` points to a task that has appeared
- AC IDs in `covers.acs` (across all tasks) equals AC IDs in `criteria.md` minus `coverage_deferrals`
- Every RULE ID in `covers.rules` is real in `rules.md`
- `feature`, `review_verdict`, `organizing_principle`, every phase checkpoint present

Do not write a partial file.

# Output

Write to `spec/tasks.yaml`.
