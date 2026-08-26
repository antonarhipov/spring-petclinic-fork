# Technical Constraints: UC5 — Resolve a Request Through Staff

## Design

The SchedulingRequest itself remains the fallback queue item. A single current claim row coordinates staff work, while audit events preserve claim history. Staff edit the bounded structured model through application services, then either create one shared STAFF_OFFER reservation or create an Appointment directly through the common feasibility and locking boundary.

## Rules

### UC5-RULE1
**Covers:** UC5-AC1–UC5-AC5, UC5-AC36–UC5-AC40
**MUST** persist fallback reason, immutable `first_queued_at`, and absolute `fallback_deadline` on the request when it first enters `STAFF_QUEUED`. Re-entry after offer rejection or expiry MUST retain those original values. Queue queries MUST order by an explicit urgency sort key and then `first_queued_at`, followed by request identifier for stable pagination.
**Reason:** The request is the queue item, and durable original timestamps are required for FIFO order and a seven-day lifetime that cannot be reset by later offers.

### UC5-RULE2
**Covers:** UC5-AC6–UC5-AC11, UC5-AC44, UC5-AC45
**MUST** represent the current staff claim as one row unique by request with claimant, claimed instant, last-activity instant, and optimistic version. Claim, reclaim, and release MUST lock the SchedulingRequest and claim row; reclaim is valid when `now >= last_activity_at + 30 minutes`. Claiming/reclaiming and a successful claimant mutation MUST set activity to the commit instant, while reads, polling, failed validation, and rejected commands MUST NOT update it.
**Reason:** One locked current-claim row serializes competing staff and records the resolved definition of activity without letting passive browser traffic hoard work.

### UC5-RULE3
**Covers:** UC5-AC12–UC5-AC14, UC5-AC32
**MUST** bind staff interpretation edits to the same versioned structured DTO used after AI validation and validate it through the shared catalog, duration, window, and feasibility policies. Staff slot selection MAY use a full calendar projection but MUST submit one veterinarian/start/duration value to the reservation service; it MUST NOT invoke AI or Timefold.
**Reason:** Staff can replace the interpretation but cannot bypass the bounded scheduling model or create a second implementation of hard constraints.

### UC5-RULE4
**Covers:** UC5-AC14–UC5-AC27, UC5-AC39, UC5-AC41, UC5-AC43
**MUST** create a staff offer as one `STAFF_OFFER` reservation whose deadline is `min(issued_at + 24 hours, fallback_deadline)`. Issue, replace, revoke, reject, accept, expiry, and fallback expiry MUST lock the request and current reservation and perform release/replacement as distinct rows and audit events; an offer deadline MUST NOT be extended in place.
**Reason:** The shared reservation model enforces one visible offer and makes the 24-hour/fallback bound and replacement history explicit.

### UC5-RULE5
**Covers:** UC5-AC20–UC5-AC32, UC5-AC41
**MUST** route offer acceptance and fallback direct booking through the common locked Appointment creation service. Direct booking MUST require a normalized nonblank reason, record booking source `STAFF_DIRECT`, and skip AI, Timefold, and owner consent while still applying the live hard-constraint policy. A conflict MUST roll back Appointment and Reservation changes and preserve `STAFF_QUEUED`.
**Reason:** Direct completion differs in approval source, not in calendar correctness or transactional guarantees.

### UC5-RULE6
**Covers:** UC5-AC22–UC5-AC27, UC5-AC33–UC5-AC43
**MUST** implement reject, revoke, cancel, offer expiry, and fallback expiry as idempotent transition-policy commands. Request cancellation MUST require a normalized nonblank reason and remove the claim plus every active reservation in the same transaction. Fallback expiry MUST win over offer expiry when both deadlines are overdue and MUST leave the request permanently terminal.
**Reason:** One precedence rule and atomic cleanup prevent repeated lifecycle processing or concurrent owner/staff actions from reviving work or retaining capacity.

## Cross-Reference

| AC | Rules |
|---|---|
| UC5-AC1 | UC5-RULE1 |
| UC5-AC2 | UC5-RULE1 |
| UC5-AC3 | RULE-5, UC5-RULE1 |
| UC5-AC4 | UC5-RULE1 |
| UC5-AC5 | UC5-RULE1 |
| UC5-AC6 | RULE-4, UC5-RULE2 |
| UC5-AC7 | UC5-RULE2 |
| UC5-AC8 | UC5-RULE2 |
| UC5-AC9 | UC5-RULE2 |
| UC5-AC10 | RULE-2, UC5-RULE2 |
| UC5-AC11 | RULE-2, UC5-RULE2 |
| UC5-AC12 | UC5-RULE3 |
| UC5-AC13 | RULE-5, UC5-RULE3 |
| UC5-AC14 | RULE-3, RULE-4, UC5-RULE3, UC5-RULE4 |
| UC5-AC15 | RULE-2, UC5-RULE4 |
| UC5-AC16 | RULE-2, UC5-RULE4 |
| UC5-AC17 | RULE-2, UC5-RULE4 |
| UC5-AC18 | RULE-2, UC5-RULE4 |
| UC5-AC19 | RULE-8, UC5-RULE4 |
| UC5-AC20 | RULE-4, UC5-RULE5 |
| UC5-AC21 | RULE-1, UC5-RULE5 |
| UC5-AC22 | UC5-RULE4, UC5-RULE6 |
| UC5-AC23 | UC5-RULE4, UC5-RULE6 |
| UC5-AC24 | RULE-1, UC5-RULE6 |
| UC5-AC25 | RULE-2, UC5-RULE4, UC5-RULE6 |
| UC5-AC26 | RULE-2, UC5-RULE6 |
| UC5-AC27 | UC5-RULE4, UC5-RULE6 |
| UC5-AC28 | RULE-4, RULE-5, UC5-RULE5 |
| UC5-AC29 | UC5-RULE5 |
| UC5-AC30 | UC5-RULE5 |
| UC5-AC31 | RULE-1, UC5-RULE5 |
| UC5-AC32 | RULE-5, UC5-RULE3, UC5-RULE5 |
| UC5-AC33 | RULE-1, UC5-RULE6 |
| UC5-AC34 | UC5-RULE6 |
| UC5-AC35 | RULE-3, UC5-RULE6 |
| UC5-AC36 | RULE-2, UC5-RULE1 |
| UC5-AC37 | RULE-2, UC5-RULE1, UC5-RULE6 |
| UC5-AC38 | UC5-RULE1, UC5-RULE6 |
| UC5-AC39 | RULE-3, UC5-RULE4, UC5-RULE6 |
| UC5-AC40 | UC5-RULE1, UC5-RULE6 |
| UC5-AC41 | RULE-4, UC5-RULE4, UC5-RULE5 |
| UC5-AC42 | UC5-RULE6 |
| UC5-AC43 | RULE-3, UC5-RULE4, UC5-RULE6 |
| UC5-AC44 | RULE-4, UC5-RULE2 |
| UC5-AC45 | UC5-RULE2 |

## Design Exclusions

- No second queue/work-item aggregate, passive claim heartbeat, or claim refresh on reads or failed commands
- No staff override of overlap, availability, specialty, grid, horizon, closure, leave, or DST constraints
- No in-place offer extension or reopening of expired requests
