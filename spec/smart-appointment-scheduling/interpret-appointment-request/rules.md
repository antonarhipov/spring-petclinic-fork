# Technical Constraints: UC3 — Interpret an Appointment Request

## Design

A versioned SchedulingRequest aggregate stores normalized text, its consent metadata, captured interpretation settings, structured fields, issue codes, and the current AI operation token. Intake and confirmation are transactional application-service commands. A Spring AI adapter receives an allowlisted prompt DTO and returns a strict closed-schema DTO; deterministic application validation, not the model, resolves catalogs, state transitions, and fallback reasons.

## Rules

### UC3-RULE1
**Covers:** UC3-AC1–UC3-AC11, UC3-AC52
**MUST** create or cancel a request while holding a pessimistic lock on the selected Pet row and resolving that Pet through the authenticated Owner. Creation MUST query for a nonterminal request after acquiring the lock, and SchedulingRequest MUST carry an optimistic version for stale form detection.
**Reason:** Locking the existing Pet row portably enforces one nonterminal request per pet without database-specific partial unique indexes.

### UC3-RULE2
**Covers:** UC3-AC6–UC3-AC8, UC3-AC18–UC3-AC22, UC3-AC46, UC3-AC50
**MUST** normalize text by applying Unicode NFC, converting CRLF and CR to LF, and trimming outer whitespace in that order. The resulting value MUST be the sole value used for code-point length validation, display on the consent screen, SHA-256 consent-version metadata, persistence, and AI dispatch. Changing it MUST increment the text version and remove dispatch eligibility until fresh consent is recorded.
**Reason:** One canonical text value makes the 1–2,000 boundary and exact-version consent reproducible without altering internal owner formatting.

### UC3-RULE3
**Covers:** UC3-AC12–UC3-AC17
**MUST** serialize the hourly AI allowance by pessimistically locking the Account row, counting persisted dispatch records whose `dispatched_at` is greater than the injected-clock instant minus one hour, and inserting the next dispatch record in the same transaction. The retry instant MUST be the oldest counted dispatch instant plus one hour.
**Reason:** A durable dispatch ledger counts failures and remains correct under concurrent tabs and application restarts.

### UC3-RULE4
**Covers:** UC3-AC18–UC3-AC28, UC3-AC36–UC3-AC38, UC3-AC42, UC3-AC53
**MUST** call Ollama through a Spring AI adapter using an outbound DTO containing only the consented normalized text, unnamed pet type, clinic date/zone, captured named periods, and active veterinarian/specialty names. The returned DTO MUST reject unknown JSON properties and unknown enum or issue-code values and MUST allow only `INCOMPLETE_AVAILABILITY`, `CONTRADICTORY_CLINICAL_ROUTING`, and `UNSAFE_CONTENT`. Catalog identifiers and every state transition MUST be resolved by the application after schema binding.
**Reason:** A strict, bounded schema makes malformed, clarification, clinical-dispute, urgency, and unsafe outcomes deterministic and keeps AI outside authorization and catalog authority.

### UC3-RULE5
**Covers:** UC3-AC22–UC3-AC26, UC3-AC53
**MUST** persist `INTERPRETING`, the captured interpretation settings, the AI operation token, attempt count one, and the 60-second deadline in one transaction, then dispatch after commit through the bounded AI executor. Timeout, adapter failure, or executor rejection MUST conditionally move the matching token to `STAFF_QUEUED(AI_UNAVAILABLE)`; a completion whose token or state is no longer current MUST have no effect.
**Reason:** This specializes RULE-6 for one-attempt AI execution and prevents polling, resubmission, or late results from duplicating work.

### UC3-RULE6
**Covers:** UC3-AC27–UC3-AC45
**MUST** validate a bound AI result in this deterministic order: schema and issue-code catalog; active specialty/veterinarian resolution; care-type consistency; duration normalization; named-period/window normalization; exclusions; unrestricted-window confirmation requirement; urgency; then owner review. `INCOMPLETE_AVAILABILITY` and an unknown preferred veterinarian MUST enter clarification; unknown specialty, `CONTRADICTORY_CLINICAL_ROUTING`, or `UNSAFE_CONTENT` MUST enter `INVALID_AI_OUTPUT` fallback.
**Reason:** A fixed validation order prevents the same output from producing different owner-visible outcomes depending on incidental code paths.

### UC3-RULE7
**Covers:** UC3-AC29–UC3-AC35, UC3-AC39–UC3-AC41, UC3-AC50–UC3-AC51
**MUST** persist captured duration rules, named-period definitions, horizon length, and hold duration separately from live clinic settings. At confirmation, the service MUST compute the next future grid boundary and exclusive local-date horizon end from the confirmation instant, expand undated weekdays within those boundaries, revalidate all windows, and freeze the snapshot before the `MATCHING` transition.
**Reason:** This implements the resolved dispatch-time settings decision while anchoring relative dates to the owner's actual confirmation time.

### UC3-RULE8
**Covers:** UC3-AC43–UC3-AC49, UC3-AC52
**MUST** expose separate commands and form DTOs for owner-correctable facts, clinical-field dispute, original-text replacement, confirmation, and cancellation. Editing confirmed correctable facts MUST lock the request and current reservation, release that reservation, delete suggestion/rejection rows for the request, and transition to `AWAITING_CONFIRMATION` in one transaction.
**Reason:** Separate commands prevent mass assignment of clinical fields and make invalidation of prior matching state atomic.

## Cross-Reference

| AC | Rules |
|---|---|
| UC3-AC1 | RULE-8, UC3-RULE1 |
| UC3-AC2 | RULE-8, UC3-RULE1 |
| UC3-AC3 | (none needed) |
| UC3-AC4 | (none needed) |
| UC3-AC5 | UC3-RULE4 |
| UC3-AC6 | UC3-RULE2 |
| UC3-AC7 | UC3-RULE2 |
| UC3-AC8 | UC3-RULE2 |
| UC3-AC9 | RULE-4, UC3-RULE1 |
| UC3-AC10 | UC3-RULE1 |
| UC3-AC11 | UC3-RULE1 |
| UC3-AC12 | UC3-RULE3 |
| UC3-AC13 | UC3-RULE3 |
| UC3-AC14 | UC3-RULE3 |
| UC3-AC15 | UC3-RULE3 |
| UC3-AC16 | UC3-RULE3 |
| UC3-AC17 | UC3-RULE3 |
| UC3-AC18 | RULE-10, UC3-RULE4 |
| UC3-AC19 | UC3-RULE2 |
| UC3-AC20 | UC3-RULE4 |
| UC3-AC21 | RULE-10, UC3-RULE4 |
| UC3-AC22 | RULE-6, UC3-RULE2, UC3-RULE5 |
| UC3-AC23 | RULE-6, UC3-RULE5 |
| UC3-AC24 | RULE-6, UC3-RULE5 |
| UC3-AC25 | RULE-6, UC3-RULE5 |
| UC3-AC26 | RULE-6, UC3-RULE5 |
| UC3-AC27 | UC3-RULE4, UC3-RULE6 |
| UC3-AC28 | UC3-RULE4, UC3-RULE6 |
| UC3-AC29 | UC3-RULE7 |
| UC3-AC30 | UC3-RULE7 |
| UC3-AC31 | UC3-RULE6, UC3-RULE7 |
| UC3-AC32 | UC3-RULE6, UC3-RULE7 |
| UC3-AC33 | UC3-RULE6, UC3-RULE7 |
| UC3-AC34 | UC3-RULE6, UC3-RULE7 |
| UC3-AC35 | UC3-RULE6, UC3-RULE7 |
| UC3-AC36 | UC3-RULE4, UC3-RULE6 |
| UC3-AC37 | UC3-RULE4, UC3-RULE6 |
| UC3-AC38 | UC3-RULE4, UC3-RULE6 |
| UC3-AC39 | UC3-RULE6, UC3-RULE7 |
| UC3-AC40 | UC3-RULE6, UC3-RULE7 |
| UC3-AC41 | UC3-RULE6, UC3-RULE7 |
| UC3-AC42 | UC3-RULE4, UC3-RULE6 |
| UC3-AC43 | UC3-RULE8 |
| UC3-AC44 | UC3-RULE8 |
| UC3-AC45 | UC3-RULE8 |
| UC3-AC46 | UC3-RULE2, UC3-RULE8 |
| UC3-AC47 | RULE-4, UC3-RULE8 |
| UC3-AC48 | UC3-RULE8 |
| UC3-AC49 | RULE-1, UC3-RULE8 |
| UC3-AC50 | UC3-RULE2, UC3-RULE7 |
| UC3-AC51 | RULE-1, UC3-RULE7 |
| UC3-AC52 | RULE-1, UC3-RULE1, UC3-RULE8 |
| UC3-AC53 | RULE-6, RULE-12, UC3-RULE5 |

## Design Exclusions

- No raw Ollama client, permissive map binding, automatic retry, second validation call, or semantic server guess for AI issue categories
- No IP-based rate limit and no in-memory-only dispatch counter
- No owner editing of duration, care type, specialty, or urgency

## External Dependencies

- Ollama model availability is not a prerequisite for application startup; it affects only the one interpretation operation and diagnostics.
