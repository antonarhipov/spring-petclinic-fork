---
name: tasks
description: Generate an implementation task list from validated spec artifacts
---

# Task List Generator Skill

Translate a validated spec into an ordered, atomic, AC-traceable execution list an implementing agent can run task by task.

Pipeline position: proposal → spec → criteria → rules → review → **tasks** → execute ⇄ converge

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
- Spec: @file:spec/spec.md (including "State model", "Normative data", "Presentation and navigation", "Verification expectations")
- Criteria: @file:spec/criteria.md (the AC list every task's `covers.acs` draws from)
- Rules: @file:spec/rules.md (optional; its Security Surface and Testing Strategy tables shape the verification tasks)
- Review: @file:spec/review.md (optional, pipeline gate)
- Project conventions: `CLAUDE.md` / `AGENTS.md` / `GEMINI.md`, build files, source tree

Spec takes precedence over the proposal. Tasks never reference a table, state model or matrix by pointer to another
file; when a task's `description` needs the rows, transitions or routes, it lists them (or names the exact spec
section heading and row count the executor must reproduce).

# Codebase Grounding (run first)

Read agent guidance files. Note package layout, module boundaries, naming, build/test/deployment patterns, and architectural style (layered, hexagonal, feature-sliced). Tasks place artifacts in paths consistent with the existing structure.

Architecture governs **where artifacts go**; it does not govern **phase order**. Phase order is decided by the
selection ladder in *Phase Organization*. A layered codebase is not by itself a reason to phase data → domain →
application → presentation; that is the fallback rung, reached only when the stronger signals do not fire.

While grounding, also record the **seams the feature's happy path crosses that have never run end to end** in this
codebase (e.g. no login exists yet, no page for this actor, no call to the external model or solver, no persisted
entity of this kind). This list is the signal for rung 3 of the ladder and is quoted in `dec-1`.

# Phase Organization

Set `organizing_principle` using the selection ladder below. Stop at the first rung that fires. Record the choice, the
rung that fired, and the signal that triggered it as `dec-1` (see *Recording the choice*).

## Principles

- **walking_skeleton**: thin end-to-end slice first, then thicken. Also called the *Tracer Bullet* approach. Best when
  nothing in scope runs end to end yet.
- **layered**: data → domain → application → presentation. Best when extending an existing layered structure that
  already runs end to end.
- **feature_slice**: one phase per AC cluster, each independently demoable. Best when the ACs partition cleanly.
- **risk_first**: highest-risk decision first, as a spike phase. Best when a wrong call invalidates later work. Never
  stands alone — see rung 2.

## Selection Ladder

1. **Explicit mandate.** If `rules.md` or the invocation specifies a principle, use it. Note the source in `dec-1`. Do
   not second-guess it, even if a lower rung would fire.
2. **Architectural risk.** If `review.md` lists Risk Hotspots that are architectural rather than local — an unproven
   external dependency, a contract that may not hold, a performance or concurrency assumption load-bearing for the
   design — the plan **starts with** `risk_first`: one spike phase whose checkpoint criterion is "the risky assumption
   is proven, or the design is changed and the tasks re-derived". Then continue down rungs 3–5 for the remaining work
   and record the hybrid `risk_first then <result>`. Local hotspots (a tricky parsing edge case, a fiddly migration) do
   not qualify; they stay as task `risk` annotations.
3. **No existing skeleton.** If nothing within the spec's scope currently runs end to end — new service, new module,
   new integration boundary, a new actor with no login or page, or the spec's happy path crosses a seam that has never
   been exercised (see the seam list from Codebase Grounding) — use `walking_skeleton`. The skeleton phase is
   **phase-1** and MUST contain, thinly but really:
   - login for each role the feature introduces, with the security surface for the whole URL→role matrix;
   - at least one page per role rendered inside the existing layout with that role's menu entries, signed-in state and
     logout (the *Presentation and navigation* mandatory task);
   - one persisted entity of the feature, created through its migration and read back;
   - one round trip across **every** external seam the happy path crosses, through the production interface with its
     test double (AI interpreter, solver, message broker, …);
   - the isolated test datasource and the clean-working-tree check (the *Test environment* mandatory task);
   - the human walkthrough criterion at `cp-1`.
   A skeleton that is a service with a unit test is not a skeleton. After rung 3 fires, **re-apply rungs 4–5 to the
   remaining ACs** to order the thickening phases and record the hybrid `walking_skeleton then <result>`.
4. **Separable ACs.** If the ACs partition into two or more clusters that could each ship on their own without the
   others, use `feature_slice`. One cluster is not a partition.
5. **Fallback.** Mirror the existing architecture. For most codebases this is `layered`; use the structure you found
   during Codebase Grounding.

Rungs are ordered by signal strength: an explicit instruction beats an observable artifact, an observable artifact
beats a structural inference, and the fallback follows code that already exists. If a rung's signal is ambiguous,
treat it as not fired and continue down the ladder.

## Executor modifier

If the invocation names the executing model and it is a low-reasoning tier (fast/flash-class models), a **pure**
`layered` result is forbidden: rung 5 yields `walking_skeleton then layered` instead. Such executors work task by task
and do not infer conventions they have not yet touched; a working, convention-correct slice in phase-1 is what every
later task imitates, and it lets converge catch UI, security and localization drift at `cp-1` instead of at the last
checkpoint. This is a modifier on the fallback, not a rung; it never overrides rungs 1–4.

## Hybrids

Phasing is often hybrid in practice — a spike, then a skeleton, then slices. When the plan mixes principles, set
`organizing_principle` to the sequence, e.g. `"walking_skeleton then feature_slice"`, and name the phase where the
handoff occurs in `dec-1`. Do not distort phasing to fit a single label. Do not chain more than two principles; if you
need three, the feature is too large and you should recommend a split.

## Recording the choice

`dec-1` is always the phasing decision and carries three extra fields so converge can audit it:

- `principle` — the value of `organizing_principle`;
- `rung` — the rung number that fired (and, for hybrids, the rung that ordered the remainder);
- `signal` — a quote of the artifact that fired it: the RULE id or invocation text (rung 1), the Risk Hotspot title
  (rung 2), the list of seams that have never run (rung 3), the AC clusters (rung 4), or the architectural style found
  (rung 5). "PetClinic is layered" is a rung-5 signal and is only valid if rungs 2–4 were checked and did not fire.

## Demo sentence per phase

Every `phase.description` ends with a **demo sentence**: what a human can *do* against the running application at the
end of the phase ("an owner can log in, submit a request, and see one held suggestion in *My appointments*"). If no
truthful demo sentence exists for a phase — only "tests pass" — the phasing is wrong for that phase; revisit the
ladder. For a low-reasoning executor the demo sentence is also the clearest statement of the phase goal.

# Task Granularity

- Completable in a single focused effort (rule of thumb: under an hour)
- Produces a verifiable artifact (file, passing test, documented decision)
- Small enough to roll back cleanly
- References ACs and RULES it covers via `covers`

# Validation Shape

`validation` is the sentence converge will execute, so it states the **assertion shape**, not the activity. "Run
the tests" or "tests pass" is never a valid `validation`. Derive it from the AC pattern (see the rules' Testing
Strategy table):

| AC pattern of the task's `covers.acs` | `validation` must say |
|---|---|
| Data exactness (seed, accounts, defaults) | which test asserts **every row by value** on a fresh database and verifies every credential with the encoder; "row count" is not acceptable |
| State transition / refusal | which service-level test sets up each disallowed state and asserts the named refusal **and** `never()` on collaborators |
| Negative authz | which web-slice test, over **which routes** (the whole matrix, pre-existing routes included), asserts status **and** no disclosure **and** no mutation, for anonymous, wrong-role and wrong-owner |
| Fidelity | which round-trip test compares every enumerated field |
| Lifecycle / end-to-end | which HTTP-level test drives the real endpoints as each role through every named step, on an isolated in-memory database |
| Presentation / localization | which scan or test covers templates (text **and** attributes **and** inline expressions) and code-produced messages; plus the walkthrough criterion at the checkpoint |
| Boundary | the three values asserted |

Every task that ships a test double states in `description` that the double conforms to the production interface
(same exceptions, nullability, `Optional` semantics). Every task that ships tests names the isolated datasource.

# Mandatory Tasks

Whenever the spec contains the corresponding section, the task list contains a dedicated task (not a bullet inside
another task) for:

- **Normative data** — the migration/fixture *and* its by-value test, in the same task, so the test is written against
  the table rather than against the migration
- **State model** — the service-level guards for every action, *and* the refusal tests, before any controller that
  calls those actions is built
- **Security surface** — the security configuration for the **whole** URL→role matrix (pre-existing routes
  included) and the negative matrix test over it, as one task in the phase that introduces authentication
- **Presentation and navigation** — layout/menu/login/logout/landing pages, per role, as a task in the first UI phase,
  not as leftovers of the last one
- **Test environment** — the isolated test datasource and the "working tree unchanged after the suite" check, in the
  first phase that adds a test

Under a `walking_skeleton` plan all five land in phase-1 by construction (see rung 3); under any other principle each
lands in the phase named above, never later.

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

Checkpoints are verified by the `converge` skill, so write `checkpoint.criteria` as sentences converge can check, not
as goals: name the observable outcome and the evidence shape ("migration test asserts all 7 clinic-hour rows by value",
"anonymous GET on every route in the security matrix redirects to `/login` and no service is invoked"), never "tests
pass" or "UI works".

Any phase that ships templates gets one **human walkthrough** criterion: "signed in as <each role>, the pages <list>
render inside the existing layout with only that role's menu entries, the signed-in user and a logout action are
visible, and <role> is denied on <URLs>". Automated tests cannot judge alignment or coherence; the walkthrough is the
acceptance gate for UI, and converge cannot approve the phase until the user confirms it.

Every terminal checkpoint also carries: "full suite green; `git status` shows no tracked file modified by the run".

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

Coverage is per assertion, not per mention. A task whose `covers.acs` lists a refusal AC, a negative authz AC or a
data-exactness AC must have a `validation` of the matching shape (see *Validation Shape*); otherwise the AC is not
covered and the task must be split or its `validation` sharpened. A single task covering more than seven ACs is a
smell: check that it is not "write the tests" for a whole phase.

# Output Schema

```yaml
tasks:
  feature: "<name>"
  review_verdict: "<PASS | PASS WITH CONDITIONS>"
  organizing_principle: "<walking_skeleton | layered | feature_slice | risk_first>, or \"<A> then <B>\" for a hybrid"
  assumptions:
    - "<assumption to verify>"
  decisions:
    - id: dec-1                     # always the phasing decision
      decision: "<organizing_principle chosen>"
      principle: "<same value as organizing_principle>"
      rung: "<1-5; for hybrids: '3, then 4 for phases 2+'>"
      signal: "<quoted artifact: RULE id / hotspot title / seams never run / AC clusters / architecture found>"
      reason: "<why>"
      alternatives: ["<alt 1>", "<alt 2>"]
    - id: dec-2
      decision: "<judgment call>"
      reason: "<why>"
      alternatives: ["<alt 1>", "<alt 2>"]
  coverage_deferrals:
    - ac: AC-12
      reason: "<why not in a task>"
  phases:
    - id: phase-1
      name: "<phase name>"
      description: "<what this accomplishes>. Demo: <what a human can do against the running app at the end of this phase>"
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
          validation: "<assertion shape: which test, at which level, asserts what — see Validation Shape>"
          risk: "<from Risk Hotspots, if applicable>"
          source: "<review/MAJOR-N if addressing a review finding>"
      checkpoint:
        id: cp-1
        description: "<what to review>"
        criteria:
          - "<criterion 1>"
```

**Required**: top-level `feature`, `review_verdict`, `organizing_principle`, `phases`, and `decisions` containing `dec-1` with `principle`, `rung`, `signal`; phase `id`, `name`, `description` (ending with a demo sentence), `covers`, `tasks`, `checkpoint`; task `id`, `name`, `description`, `artifact`, `covers`, `depends_on`, `validation`; checkpoint `id`, `description`, `criteria`.

**Optional**: `assumptions`, further `decisions`, `coverage_deferrals`, `entry_criteria`, `complexity`, `risk`, `source`.

# Success Criteria

Complete only when ALL hold:

- Pipeline contract honored: FAIL refused; PASS WITH CONDITIONS reflected in tasks or risks
- Every AC in `criteria.md` in some task's `covers.acs` or in `coverage_deferrals`
- Every task has required fields
- Every `depends_on` references an earlier task in execution order
- No circular dependencies
- Every phase ends with a checkpoint whose criteria name an observable outcome and an evidence shape; UI phases have a walkthrough criterion; terminal checkpoints have the clean-working-tree criterion
- Every `validation` states an assertion shape matching the AC pattern (no "run tests"/"tests pass")
- Mandatory tasks present for every spec section that triggers them (normative data, state model, security surface, presentation and navigation, test environment)
- No task references a table, state model or matrix by pointer to another file
- `organizing_principle` set by the selection ladder, with the rung and quoted signal in `dec-1`; hybrids name the handoff phase; a pure `layered` result is accompanied by evidence that rungs 2–4 did not fire
- If rung 3 fired, phase-1 contains every skeleton item listed under rung 3 and `cp-1` has the walkthrough criterion
- Every `phase.description` ends with a truthful demo sentence
- All Risk Hotspots reflected in task `risk` annotations; architectural hotspots produced a `risk_first` spike phase
- Soft limits met, or deviation justified in `decisions`

Verification pass before writing:
- Walk phases in order; every `depends_on` points to a task that has appeared
- AC IDs in `covers.acs` (across all tasks) equals AC IDs in `criteria.md` minus `coverage_deferrals`
- Every RULE ID in `covers.rules` is real in `rules.md`
- `feature`, `review_verdict`, `organizing_principle`, `dec-1` (`principle`, `rung`, `signal`), every phase checkpoint present

Do not write a partial file.

# Output

Write to `spec/tasks.yaml`.
