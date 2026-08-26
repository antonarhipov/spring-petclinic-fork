# Technical Constraints: UC2 — Configure Clinic Scheduling

## Design

Clinic configuration consists of one versioned settings row, active/deactivated veterinarian and specialty catalog entries, recurring shifts, date exceptions, leave, and closures. An `EffectiveAvailabilityService` converts those local rules to immutable instant intervals for a requested date range. Settings and calendar commands are transactional and produce audit metadata through RULE-7.

## Rules

### UC2-RULE1
**Covers:** UC2-AC1–UC2-AC19, UC2-AC48, UC2-AC50, UC2-AC51
**MUST** bind the clinic zone through validated `@ConfigurationProperties` with default `UTC` and no writable form field. All staff-editable settings MUST live in one optimistically versioned singleton row and be validated as an atomic form: duration ordering/grid/range, positive horizons and hold duration, maximum staff horizon, non-overlapping on-grid named periods, and allowlisted urgent-contact fields.
**Reason:** Application properties own deployment time semantics, while one settings aggregate prevents partially applied combinations of interdependent values.

### UC2-RULE2
**Covers:** UC2-AC20–UC2-AC38, UC2-AC47, UC2-AC49
**MUST** persist active flags on veterinarians and specialties and model weekly shifts, modified-hours ranges, extra-hours ranges, inclusive leave dates, and inclusive closure dates as separate typed records using clinic-local values. Calendar commands MUST reject non-positive, overnight, off-grid, and overlapping ranges before persistence; foreign keys MUST prevent deletion of referenced catalog rows.
**Reason:** Typed records preserve the specified precedence and deactivation history more clearly than an unstructured calendar-event table.

### UC2-RULE3
**Covers:** UC2-AC24–UC2-AC39, UC2-AC44
**MUST** calculate effective availability in this order for each veterinarian/date: return empty for closure; return empty for leave; otherwise choose modified hours when present or weekly shifts when absent; union valid extra hours; normalize adjacent intervals; then convert each grid point to an instant only when the clinic-local time has exactly one valid offset.
**Reason:** A single deterministic resolver prevents Timefold, staff calendar, and direct-booking paths from interpreting calendar precedence differently.

### UC2-RULE4
**Covers:** UC2-AC40–UC2-AC42, UC2-AC45
**MUST** evaluate a proposed calendar reduction against locked future BOOKED appointments before saving it. When conflicts exist, the command MUST require a reason, keep each Appointment interval unchanged, and append an explicit unresolved calendar-conflict record linked to the appointment and configuration audit event. The staff calendar projection MUST label Appointment, GUIDED_HOLD, and STAFF_OFFER entries distinctly.
**Reason:** A durable conflict record is necessary for staff resolution; a transient warning would be lost after the configuration transaction.

### UC2-RULE5
**Covers:** UC2-AC43, UC2-AC44
**MUST** copy duration bounds/default, named-period definitions, owner-horizon length, and guided-hold duration into request interpretation settings when valid intake or replacement text is persisted, before language and consent routing. Confirmation MUST preserve those values, calculate absolute horizon boundaries from its own instant, and freeze the request snapshot; initial fallback MUST preserve or materialize that horizon as specified by UC5; later solves MUST combine the stable request values with a newly loaded live calendar and active catalog.
**Reason:** Capturing before routing gives unsupported-language and declined-consent fallback the same stable scheduling meaning as AI-assisted requests while keeping live capacity current.

### UC2-RULE6
**Covers:** UC2-AC21–UC2-AC25, UC2-AC46, UC2-AC47
**MUST** evict the existing veterinarian cache after any veterinarian, specialty, assignment, or active-status commit. Demo schedule initialization MUST use the same calendar service and insert missing weekday shifts idempotently without overwriting staff edits.
**Reason:** The existing cached veterinarian catalog would otherwise make eligibility stale, and demo startup must not become a second configuration writer.

## Cross-Reference

| AC | Rules |
|---|---|
| UC2-AC1 | RULE-2, UC2-RULE1 |
| UC2-AC2 | RULE-2, UC2-RULE1 |
| UC2-AC3 | RULE-2, UC2-RULE1 |
| UC2-AC4 | UC2-RULE1 |
| UC2-AC5 | UC2-RULE1 |
| UC2-AC6 | UC2-RULE1 |
| UC2-AC7 | UC2-RULE1 |
| UC2-AC8 | UC2-RULE1 |
| UC2-AC9 | UC2-RULE1 |
| UC2-AC10 | UC2-RULE1 |
| UC2-AC11 | UC2-RULE1 |
| UC2-AC12 | UC2-RULE1 |
| UC2-AC13 | UC2-RULE1 |
| UC2-AC14 | UC2-RULE1 |
| UC2-AC15 | UC2-RULE1 |
| UC2-AC16 | UC2-RULE1 |
| UC2-AC17 | UC2-RULE1 |
| UC2-AC18 | UC2-RULE1 |
| UC2-AC19 | UC2-RULE1 |
| UC2-AC20 | UC2-RULE2 |
| UC2-AC21 | UC2-RULE2, UC2-RULE6 |
| UC2-AC22 | UC2-RULE2, UC2-RULE6 |
| UC2-AC23 | UC2-RULE2 |
| UC2-AC24 | RULE-5, UC2-RULE2 |
| UC2-AC25 | RULE-5, UC2-RULE2 |
| UC2-AC26 | UC2-RULE2 |
| UC2-AC27 | UC2-RULE2, UC2-RULE3 |
| UC2-AC28 | UC2-RULE2, UC2-RULE3 |
| UC2-AC29 | UC2-RULE2, UC2-RULE3 |
| UC2-AC30 | UC2-RULE2, UC2-RULE3 |
| UC2-AC31 | UC2-RULE3 |
| UC2-AC32 | UC2-RULE3 |
| UC2-AC33 | UC2-RULE2 |
| UC2-AC34 | UC2-RULE2 |
| UC2-AC35 | UC2-RULE2 |
| UC2-AC36 | UC2-RULE2 |
| UC2-AC37 | UC2-RULE2 |
| UC2-AC38 | UC2-RULE2 |
| UC2-AC39 | RULE-2, UC2-RULE3 |
| UC2-AC40 | UC2-RULE4 |
| UC2-AC41 | UC2-RULE4 |
| UC2-AC42 | UC2-RULE4 |
| UC2-AC43 | UC2-RULE5 |
| UC2-AC44 | RULE-5, UC2-RULE3, UC2-RULE5 |
| UC2-AC45 | RULE-3, UC2-RULE4 |
| UC2-AC46 | UC2-RULE6 |
| UC2-AC47 | UC2-RULE2, UC2-RULE6 |
| UC2-AC48 | UC2-RULE1 |
| UC2-AC49 | UC2-RULE2 |
| UC2-AC50 | UC2-RULE1 |
| UC2-AC51 | UC2-RULE1 |

## Design Exclusions

- No staff-editable zone, overnight shift, generic capacity resource, or hard deletion of referenced catalog rows
- No automatic movement or cancellation of an appointment after a calendar edit
- No independent availability calculation in controllers, Timefold constraints, or templates
