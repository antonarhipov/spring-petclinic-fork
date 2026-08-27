# Technical Constraints: UC3 — Interpret an Appointment Request

## Design

A versioned SchedulingRequest aggregate stores normalized text, its consent metadata, captured interpretation settings, structured fields, issue codes, materialized horizon, and the current AI operation token. Intake, replacement, confirmation, and cancellation are transactional application-service commands. A Spring AI adapter receives an allowlisted prompt DTO and returns a strict closed-schema DTO; deterministic application validation, not the model, resolves catalogs, state transitions, and fallback reasons.

## Rules

### UC3-RULE1
**Covers:** UC3-AC1–UC3-AC11, UC3-AC54–UC3-AC55
**MUST** create a request while holding a pessimistic lock on the selected Pet row and resolving that Pet through the authenticated Owner. Creation MUST query for a nonterminal request after acquiring the lock, persist the captured interpretation settings and initial state in the same transaction, and give SchedulingRequest an optimistic version for stale form detection.
**Reason:** Locking the existing Pet row portably enforces one nonterminal request per pet without database-specific partial unique indexes while keeping intake capture atomic.

### UC3-RULE2
**Covers:** UC3-AC6–UC3-AC8, UC3-AC18–UC3-AC22, UC3-AC46, UC3-AC62
**MUST** normalize text by applying Unicode NFC, converting CRLF and CR to LF, and trimming outer whitespace in that order. The resulting value MUST be the sole value used for code-point length validation, display on the consent screen, SHA-256 consent-version metadata, persistence, and AI dispatch. Original-text replacement MUST apply the same normalization before creating the next text version.
**Reason:** One canonical text value makes the 1–2,000 boundary and exact-version consent reproducible without altering internal owner formatting.

### UC3-RULE3
**Covers:** UC3-AC12–UC3-AC17, UC3-AC59
**MUST** serialize the hourly AI allowance before recording consent by pessimistically locking the Account row, counting persisted dispatch records whose `dispatched_at` is greater than the injected-clock instant minus one hour, and inserting the next dispatch record in the same transaction. The retry instant MUST be the oldest counted dispatch instant plus one hour, and a denied dispatch MUST leave the request in `AWAITING_CONSENT` without consent metadata for that attempt.
**Reason:** A durable dispatch ledger counts failures and remains correct under concurrent tabs and application restarts.

### UC3-RULE4
**Covers:** UC3-AC18–UC3-AC28, UC3-AC36–UC3-AC38, UC3-AC42, UC3-AC53, UC3-AC56
**MUST** call Ollama through a Spring AI adapter using an outbound DTO containing only the consented normalized text, unnamed pet type, clinic date/zone, captured named periods, and active veterinarian/specialty names. The returned DTO MUST reject unknown JSON properties and unknown enum or issue-code values and MUST allow only `INCOMPLETE_AVAILABILITY`, `CONTRADICTORY_CLINICAL_ROUTING`, and `UNSAFE_CONTENT`. Catalog identifiers and every state transition MUST be resolved by the application after schema binding.
**Reason:** A strict, bounded schema makes malformed, clarification, clinical-dispute, urgency, and unsafe outcomes deterministic and keeps AI outside authorization and catalog authority.

### UC3-RULE5
**Covers:** UC3-AC22–UC3-AC26, UC3-AC53, UC3-AC60
**MUST** persist `INTERPRETING`, the AI operation token, attempt count one, and the 60-second deadline in one transaction, then dispatch after commit through the bounded AI executor using the settings captured for the current text version. Timeout, adapter failure, or executor rejection MUST conditionally move the matching token to `STAFF_QUEUED(AI_UNAVAILABLE)`; a completion whose token or state is no longer current MUST have no effect.
**Reason:** This specializes RULE-6 for one-attempt AI execution and prevents polling, resubmission, or late results from duplicating work.

### UC3-RULE6
**Covers:** UC3-AC27–UC3-AC45, UC3-AC56–UC3-AC58
**MUST** validate a bound AI result in this deterministic order: schema and issue-code catalog; active specialty/veterinarian resolution; care-type consistency; duration normalization; named-period/window normalization; exclusions; unrestricted-window confirmation requirement; urgency; then owner review. `INCOMPLETE_AVAILABILITY` and an unknown preferred veterinarian MUST enter clarification; unknown specialty, `CONTRADICTORY_CLINICAL_ROUTING`, or `UNSAFE_CONTENT` MUST enter `INVALID_AI_OUTPUT` fallback; every other valid result MUST enter `AWAITING_CONFIRMATION`. Clarification edits MUST rerun deterministic issue validation and remain `CLARIFICATION_REQUIRED` until no issue remains, then enter `AWAITING_CONFIRMATION`.
**Reason:** A fixed validation and transition order prevents the same interpretation or clarification from producing different owner-visible outcomes on incidental code paths.

### UC3-RULE7
**Covers:** UC3-AC29–UC3-AC35, UC3-AC39–UC3-AC41, UC3-AC50–UC3-AC51, UC3-AC54, UC3-AC62
**MUST** persist captured duration rules, named-period definitions, horizon length, and hold duration separately from live clinic settings for every valid intake or replacement text version before language and consent routing. At confirmation, the service MUST compute the next future grid boundary and exclusive local-date horizon end from the confirmation instant, expand undated weekdays within those boundaries, revalidate all windows, and freeze the snapshot before the `MATCHING` transition.
**Reason:** This anchors request meaning to intake or replacement while anchoring relative dates to owner confirmation or UC5's initial fallback instant.

### UC3-RULE8
**Covers:** UC3-AC43–UC3-AC45, UC3-AC47–UC3-AC51
**MUST** expose separate commands and form DTOs for owner-correctable facts, clinical-field dispute, original-text replacement, confirmation, and cancellation. Editing confirmed correctable facts MUST acquire applicable resource locks in RULE-4 order, release the current reservation, delete suggestion/rejection rows for the request, and transition to `AWAITING_CONFIRMATION` in one transaction.
**Reason:** Separate commands prevent mass assignment of clinical fields and make invalidation of prior matching state atomic.

### UC3-RULE9
**Covers:** UC3-AC46, UC3-AC60–UC3-AC63, UC3-AC65
**MUST** permit original-text replacement only from `AWAITING_CONSENT`, `INTERPRETING`, `CLARIFICATION_REQUIRED`, `AWAITING_CONFIRMATION`, `MATCHING`, or `SLOT_HELD`. The command MUST acquire applicable locks in RULE-4 order, invalidate the current AI or solver operation, release a guided reservation, delete all prior interpretation, issue, suggestion, and rejection data, clear the materialized horizon, capture current interpretation settings for the incremented text version, clear prior consent, and enter `AWAITING_CONSENT` in one transaction. Attempts from staff fallback or terminal states MUST return a rejected outcome without mutation.
**Reason:** Text meaning, consent, interpretation, and matching artifacts form one versioned unit that cannot safely survive replacement or a late operation result.

### UC3-RULE10
**Covers:** UC3-AC52, UC3-AC64, UC3-AC66
**MUST** implement owner cancellation by locking Owner, Pet, every affected Vet, SchedulingRequest, and active Reservations in RULE-4 order, moving any owned nonterminal request to `CANCELLED`, releasing every active reservation, and removing its staff claim under the locked request in one transaction.
**Reason:** Cancellation must release capacity and staff ownership immediately without leaving side effects attached to terminal work.

### UC3-RULE11
**Covers:** UC3-AC67–UC3-AC80
**MUST** extend the closed AI window schema with strict enums for window classification, date-expression kind, weekday, month, and ordinal week plus bounded non-negative integer offsets and nullable explicit date/time fields. The adapter MUST provide the clinic-local current date and zone in the prompt, MUST require the model to return symbolic relative expressions rather than calculated relative dates, and MUST reject unknown fields, enums, invalid field combinations, and negative offsets.
**Reason:** AI identifies bounded language concepts, while a closed symbolic contract keeps calendar arithmetic deterministic and prevents unsupported language from silently becoming an arbitrary date.

### UC3-RULE12
**Covers:** UC3-AC69–UC3-AC78, UC3-AC81–UC3-AC82
**MUST** resolve symbolic dates with `java.time` from the injected Clock and clinic ZoneId at confirmation: weeks use Monday through Sunday; end-of-week uses Thursday through Sunday; relative months use complete `YearMonth` values; named months select the nearest occurrence whose end is not before the anchor; and ordinal month weeks use Monday-through-Sunday calendar rows clipped to `YearMonth.atDay(1)` and `YearMonth.atEndOfMonth()`. The resolver MUST intersect an optional weekday and captured local-time or named-period bounds, convert only unique local offsets to half-open `Instant` intervals, and clip results to the next future grid boundary and exclusive captured owner horizon.
**Reason:** One deterministic resolver makes month boundaries, year rollover, DST, confirmation anchoring, and combined date/time constraints reproducible without trusting model arithmetic.

### UC3-RULE13
**Covers:** UC3-AC68, UC3-AC79–UC3-AC83
**MUST** write valid symbolic windows after schema and named-period validation, then replace all materialized intervals in the same locked confirmation transaction before entering `MATCHING`. At least one `ALLOWED` interval MUST contain every eligible candidate when hard positive availability exists; any overlap with `EXCLUDED` MUST make a candidate ineligible; `PREFERRED` MUST affect only the first soft rank. If every hard allowed interval is empty after clipping, confirmation MUST leave the request in `CLARIFICATION_REQUIRED` with a deterministic outside-horizon or incomplete-calendar reason and MUST NOT dispatch a solver operation.
**Reason:** Transactional materialization and explicit interval classes preserve the owner's hard limits, preference order, and correction path from review through solving.

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
| UC3-AC46 | UC3-RULE2, UC3-RULE9 |
| UC3-AC47 | RULE-4, UC3-RULE8 |
| UC3-AC48 | UC3-RULE8 |
| UC3-AC49 | RULE-1, UC3-RULE8 |
| UC3-AC50 | UC3-RULE7, UC3-RULE8 |
| UC3-AC51 | RULE-1, UC3-RULE7 |
| UC3-AC52 | RULE-1, RULE-4, UC3-RULE10 |
| UC3-AC53 | RULE-6, RULE-12, UC3-RULE5 |
| UC3-AC54 | RULE-4, UC3-RULE1, UC3-RULE7 |
| UC3-AC55 | RULE-1, UC3-RULE1 |
| UC3-AC56 | RULE-1, RULE-12, UC3-RULE4, UC3-RULE6 |
| UC3-AC57 | RULE-1, RULE-12, UC3-RULE6 |
| UC3-AC58 | RULE-1, RULE-12, UC3-RULE6 |
| UC3-AC59 | UC3-RULE3 |
| UC3-AC60 | RULE-6, RULE-12, UC3-RULE5, UC3-RULE9 |
| UC3-AC61 | RULE-1, RULE-12, UC3-RULE9 |
| UC3-AC62 | RULE-1, RULE-12, UC3-RULE2, UC3-RULE7, UC3-RULE9 |
| UC3-AC63 | RULE-1, RULE-12, UC3-RULE9 |
| UC3-AC64 | RULE-1, RULE-3, RULE-4, RULE-12, UC3-RULE10 |
| UC3-AC65 | RULE-1, RULE-3, RULE-4, RULE-12, UC3-RULE9 |
| UC3-AC66 | RULE-1, RULE-4, RULE-12, UC3-RULE10 |
| UC3-AC67 | RULE-10, RULE-13, UC3-RULE11 |
| UC3-AC68 | RULE-12, RULE-13, UC3-RULE11, UC3-RULE13 |
| UC3-AC69 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC70 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC71 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC72 | RULE-5, RULE-13, UC3-RULE12, UC3-RULE13 |
| UC3-AC73 | RULE-5, RULE-13, UC3-RULE12, UC3-RULE13 |
| UC3-AC74 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC75 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC76 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC77 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC78 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC79 | RULE-5, RULE-13, UC3-RULE13 |
| UC3-AC80 | RULE-5, RULE-13, UC3-RULE13 |
| UC3-AC81 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC82 | RULE-2, RULE-13, UC3-RULE12 |
| UC3-AC83 | RULE-1, RULE-12, RULE-13, UC3-RULE13 |

## Design Exclusions

- No raw Ollama client, permissive map binding, automatic retry, second validation call, or semantic server guess for AI issue categories
- No IP-based rate limit and no in-memory-only dispatch counter
- No owner editing of duration, care type, specialty, or urgency
- No model-calculated relative dates, permissive free-form date expressions, JSON availability source of truth, or authoritative legacy single-window projection

## External Dependencies

- Ollama model availability is not a prerequisite for application startup; it affects only the one interpretation operation and diagnostics.
