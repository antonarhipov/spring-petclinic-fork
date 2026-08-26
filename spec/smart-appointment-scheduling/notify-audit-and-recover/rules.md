# Technical Constraints: UC7 — Notify, Audit, and Recover

## Design

Notifications and audit events are local persistent records written by the application service that commits each source transition. A shared lifecycle processor handles overdue reservations, fallback, operations, account locks, and retention in bounded batches and is called both periodically and during startup recovery. Staff diagnostics read sanitized adapter and worker status without sharing their detail with public actuator endpoints.

## Rules

### UC7-RULE1
**Covers:** UC7-AC1–UC7-AC11, UC7-AC44
**MUST** persist notifications with Owner, event type, message-bundle key, allowlisted non-sensitive arguments, target type/identifier, created instant, and nullable read instant. Creation MUST occur through RULE-7; unread count and mark-read queries MUST be Owner-scoped, and mark-read MUST update only `read_at`. Target links MUST be built only after the same authorization-scoped lookup used by the target page succeeds.
**Reason:** A keyed notification can be localized at render time and remain durable without copying request or clinical text into navigation or links.

### UC7-RULE2
**Covers:** UC7-AC12–UC7-AC16
**MUST** store audit events as immutable rows with actor or SYSTEM, event type from a closed catalog, target type/identifier, instant, safe reason code, and allowlisted scalar before/after metadata serialized in a database-neutral text format. Production code MUST expose append and query operations only; owner timeline queries MUST join through that owner's request or appointment rather than filter an already loaded global audit list.
**Reason:** A closed append-only schema supports all three databases and prevents audit payloads or owner timelines from expanding accidentally into sensitive content.

### UC7-RULE3
**Covers:** UC7-AC17–UC7-AC24, UC7-AC39, UC7-AC45
**MUST** persist one nullable `sensitive_purge_at` and `purged_at` on SchedulingRequest. The first linked Appointment transition to `COMPLETED`, `NO_SHOW`, or `CANCELLED` MUST set `sensitive_purge_at` only when absent; `NO_SHOW` correction MUST NOT change it. An unlinked request terminal transition MUST set it similarly. Purge MUST lock the request, null every purge-target column, set `purged_at`, and preserve all specified structured, consent, link, state, Visit, and audit fields.
**Reason:** Persisting an immutable first deadline implements the resolved no-show correction behavior and makes repeated purge processing harmless.

### UC7-RULE4
**Covers:** UC7-AC21–UC7-AC33, UC7-AC37–UC7-AC39
**MUST** implement one idempotent lifecycle processor with separate bounded queries for overdue guided reservations, staff offers/fallbacks, claims, account locks, and retention. Each candidate identifier MUST be processed in its own short transaction with the same pessimistic locks and transition policies used by user commands; fallback expiry MUST precede offer expiry for the same request. A configurable Spring fixed-delay scheduler and startup runner MUST call this processor, but neither MAY calculate alternate transition behavior.
**Reason:** Reusing command policies prevents scheduled, startup, and user-triggered deadline handling from producing different states or side-effect counts.

### UC7-RULE5
**Covers:** UC7-AC29–UC7-AC36, UC7-AC39
**MUST** recover persisted operations and reservations without changing unexpired deadlines or dispatching AI/solver work. An overdue operation token in `INTERPRETING` or active automated `MATCHING` MUST be invalidated before `RECOVERY_REQUIRED` fallback; late executor completion must then fail its token check. Recovery MUST write the same audit and notification effects as ordinary lifecycle processing exactly once.
**Reason:** Operation-token invalidation closes the race between startup recovery and a late in-process result without introducing retries.

### UC7-RULE6
**Covers:** UC7-AC25, UC7-AC40–UC7-AC43
**MUST** expose detailed diagnostics only through a staff-authorized MVC projection. Ollama status MUST use a bounded connectivity/model check without prompts; solver status MUST reflect successful local solver configuration; worker status MUST report its last completed run and last failure; migration status MUST come from Flyway migration metadata. Public liveness/readiness MUST expose only aggregate status and MUST remain free of those details; Ollama unavailability MUST NOT make application startup fail.
**Reason:** Diagnostics are operationally useful only when grounded in real subsystem state, but dependency and migration detail is sensitive and AI is optional.

### UC7-RULE7
**Covers:** UC7-AC12–UC7-AC25, UC7-AC42
**MUST** define explicit safe metadata projections for every audited event and disable request/response body logging for scheduling and authentication routes. Exception translation MUST log opaque identifiers and reason codes only; entity `toString()` implementations for new sensitive aggregates MUST omit text, prompts, output, hashes, clinical descriptions, and notes.
**Reason:** Retention and audit guarantees are ineffective if the same sensitive values survive indefinitely in logs or diagnostic payloads.

## Cross-Reference

| AC | Rules |
|---|---|
| UC7-AC1 | RULE-7, UC7-RULE1 |
| UC7-AC2 | RULE-7, UC7-RULE1 |
| UC7-AC3 | RULE-7, UC7-RULE1 |
| UC7-AC4 | RULE-7, UC7-RULE1 |
| UC7-AC5 | RULE-7, UC7-RULE1 |
| UC7-AC6 | RULE-7, UC7-RULE1 |
| UC7-AC7 | RULE-10, UC7-RULE1 |
| UC7-AC8 | RULE-8, UC7-RULE1 |
| UC7-AC9 | RULE-8, UC7-RULE1 |
| UC7-AC10 | UC7-RULE1 |
| UC7-AC11 | RULE-8, UC7-RULE1 |
| UC7-AC12 | RULE-7, RULE-10, UC7-RULE2, UC7-RULE7 |
| UC7-AC13 | RULE-10, UC7-RULE2, UC7-RULE7 |
| UC7-AC14 | UC7-RULE2 |
| UC7-AC15 | RULE-8, UC7-RULE2 |
| UC7-AC16 | RULE-8, UC7-RULE2 |
| UC7-AC17 | RULE-2, UC7-RULE3 |
| UC7-AC18 | RULE-2, UC7-RULE3 |
| UC7-AC19 | UC7-RULE3 |
| UC7-AC20 | RULE-2, UC7-RULE3 |
| UC7-AC21 | RULE-2, UC7-RULE3 |
| UC7-AC22 | UC7-RULE3 |
| UC7-AC23 | RULE-10, UC7-RULE3, UC7-RULE7 |
| UC7-AC24 | UC7-RULE3 |
| UC7-AC25 | RULE-10, UC7-RULE7 |
| UC7-AC26 | RULE-2, UC7-RULE4 |
| UC7-AC27 | RULE-12, UC7-RULE4 |
| UC7-AC28 | UC7-RULE4 |
| UC7-AC29 | RULE-3, UC7-RULE4, UC7-RULE5 |
| UC7-AC30 | UC7-RULE4, UC7-RULE5 |
| UC7-AC31 | RULE-3, UC7-RULE4, UC7-RULE5 |
| UC7-AC32 | UC7-RULE4, UC7-RULE5 |
| UC7-AC33 | UC7-RULE4, UC7-RULE5 |
| UC7-AC34 | RULE-6, UC7-RULE5 |
| UC7-AC35 | RULE-6, UC7-RULE5 |
| UC7-AC36 | RULE-2, UC7-RULE5 |
| UC7-AC37 | RULE-2, UC7-RULE4 |
| UC7-AC38 | RULE-2, UC7-RULE4 |
| UC7-AC39 | RULE-12, UC7-RULE3, UC7-RULE4, UC7-RULE5 |
| UC7-AC40 | RULE-8, UC7-RULE6 |
| UC7-AC41 | RULE-8, UC7-RULE6 |
| UC7-AC42 | RULE-10, UC7-RULE6, UC7-RULE7 |
| UC7-AC43 | UC7-RULE6 |
| UC7-AC44 | RULE-7, UC7-RULE1 |
| UC7-AC45 | UC7-RULE3 |

## Design Exclusions

- No external notification delivery, eventual notification projection, mutable audit payload, or owner access to global audit
- No automatic AI/solver retry during periodic or startup recovery
- No public prompt, model, worker, migration, database, or user detail
