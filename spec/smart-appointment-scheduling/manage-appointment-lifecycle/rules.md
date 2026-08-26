# Technical Constraints: UC6 — Manage Appointment Lifecycle

## Design

Appointment is a new JPA aggregate independent of legacy Visit. It stores timed booking state, durable clinical routing, and an optional unique SchedulingRequest link. Application services own owner cancellation, staff direct booking, rescheduling, cancellation, completion, and no-show commands through the shared lock and feasibility policies. Durable calendar-conflict records are resolved by successful movement or cancellation, and completion alone creates one legacy Visit through a unique database link.

## Rules

### UC6-RULE1
**Covers:** UC6-AC1–UC6-AC6, UC6-AC41–UC6-AC44, UC6-AC49
**MUST** persist Appointment with owner, pet, veterinarian, start instant, end instant, duration minutes, care type, optional required-specialty foreign key, status, booking source, optional unique request foreign key, optimistic version, and created/updated instants. Foreign keys MUST retain referenced history, intervals MUST be half-open, and legacy Visit rows MUST NOT be read by any timed-overlap query.
**Reason:** A distinct timed aggregate preserves legacy clinical history while supporting indexed overlap and concurrency checks.

### UC6-RULE2
**Covers:** UC6-AC2–UC6-AC3, UC6-AC7–UC6-AC12
**MUST** expose separate owner and staff appointment projections. Owner cancellation MUST resolve the Appointment through the authenticated Owner, lock all affected resources in RULE-4 order, compare the injected-clock instant with the start, and transition only Appointment state; it MUST NOT update the linked terminal SchedulingRequest.
**Reason:** Owner visibility and rebooking semantics differ from staff calendar access and must not reopen the completed request workflow.

### UC6-RULE3
**Covers:** UC6-AC13–UC6-AC24, UC6-AC41–UC6-AC42, UC6-AC48–UC6-AC50
**MUST** run staff direct booking and rescheduling through the shared live feasibility policy after acquiring every applicable Owner, Pet, Vet, SchedulingRequest, Appointment, and Reservation lock in RULE-4 order. Under those locks, unlinked direct booking MUST reject a nonterminal request for the pet and MUST accept only `GENERAL` with no required specialty or `SPECIALTY` with one active required specialty. Rescheduling MUST retain the Appointment identifier and stored clinical routing, require a normalized nonblank reason, append audit before/after metadata, and update the existing row rather than cancel-and-recreate it.
**Reason:** One globally ordered command boundary prevents deadlocks, preserves identity and clinical routing, and makes direct or rescheduled bookings obey the same capacity rules as guided bookings.

### UC6-RULE4
**Covers:** UC6-AC7–UC6-AC10, UC6-AC21–UC6-AC27, UC6-AC40, UC6-AC46, UC6-AC47
**MUST** apply owner/staff cancellation, rescheduling, completion, and no-show through the explicit Appointment transition policy while holding the Appointment pessimistically after resource locks. A stale version or already-applied transition MUST return the locked current state; completed and cancelled states MUST reject every later mutation.
**Reason:** Lifecycle races must commit at most one transition without relying on controller checks or duplicate side effects.

### UC6-RULE5
**Covers:** UC6-AC28–UC6-AC40
**MUST** create a legacy Visit in the same transaction that first transitions an Appointment to `COMPLETED`. The Visit table MUST contain a nullable unique Appointment foreign key, and completion MUST set the Visit date from the appointment start in the clinic zone and set only the normalized staff-authored description. Repeated completion MUST resolve that unique Visit; `NO_SHOW` MUST create none and MAY later execute the same completion command.
**Reason:** A database uniqueness invariant provides exactly-once Visit creation while leaving all pre-feature Visits unlinked and unchanged.

### UC6-RULE6
**Covers:** UC6-AC45
**MUST** remove the legacy new-Visit navigation, GET handler, POST handler, and booking form from the runnable application. Historical Visit rendering MAY remain, but no service or route MAY persist a standalone Visit except UC6-RULE5.
**Reason:** Merely hiding the old link would leave a second direct URL booking path that bypasses timed scheduling constraints.

### UC6-RULE7
**Covers:** UC6-AC51–UC6-AC52
**MUST** resolve every unresolved calendar-conflict record linked to an Appointment in the same transaction as its successful reschedule or cancellation. Resolution MUST persist `RESCHEDULED` or `CANCELLED`, the resolving audit-event identifier, and the injected-clock instant while retaining the original conflict and configuration-event data.
**Reason:** Atomic durable resolution removes stale staff work without erasing why the conflict existed or how it was resolved.

## Cross-Reference

| AC | Rules |
|---|---|
| UC6-AC1 | UC6-RULE1 |
| UC6-AC2 | RULE-8, UC6-RULE2 |
| UC6-AC3 | RULE-8, UC6-RULE2 |
| UC6-AC4 | RULE-4, RULE-5, UC6-RULE1 |
| UC6-AC5 | RULE-5, UC6-RULE1 |
| UC6-AC6 | RULE-4, RULE-5, UC6-RULE1 |
| UC6-AC7 | RULE-4, UC6-RULE2, UC6-RULE4 |
| UC6-AC8 | RULE-2, UC6-RULE2 |
| UC6-AC9 | RULE-2, UC6-RULE2 |
| UC6-AC10 | RULE-4, UC6-RULE2 |
| UC6-AC11 | UC6-RULE2 |
| UC6-AC12 | UC6-RULE2 |
| UC6-AC13 | RULE-4, RULE-5, UC6-RULE3 |
| UC6-AC14 | UC6-RULE3 |
| UC6-AC15 | RULE-2, UC6-RULE3 |
| UC6-AC16 | RULE-2, UC6-RULE3 |
| UC6-AC17 | RULE-2, UC6-RULE3 |
| UC6-AC18 | RULE-2, UC6-RULE3 |
| UC6-AC19 | RULE-5, UC6-RULE3 |
| UC6-AC20 | RULE-4, UC6-RULE3 |
| UC6-AC21 | RULE-4, RULE-5, UC6-RULE3, UC6-RULE4 |
| UC6-AC22 | UC6-RULE3 |
| UC6-AC23 | RULE-7, UC6-RULE3 |
| UC6-AC24 | UC6-RULE4 |
| UC6-AC25 | RULE-4, UC6-RULE4 |
| UC6-AC26 | UC6-RULE4 |
| UC6-AC27 | RULE-2, UC6-RULE4 |
| UC6-AC28 | RULE-2, UC6-RULE5 |
| UC6-AC29 | RULE-2, UC6-RULE5 |
| UC6-AC30 | RULE-2, UC6-RULE5 |
| UC6-AC31 | UC6-RULE5 |
| UC6-AC32 | RULE-7, UC6-RULE5 |
| UC6-AC33 | RULE-12, UC6-RULE5 |
| UC6-AC34 | RULE-2, UC6-RULE5 |
| UC6-AC35 | RULE-10, UC6-RULE5 |
| UC6-AC36 | RULE-2, UC6-RULE5 |
| UC6-AC37 | RULE-2, UC6-RULE5 |
| UC6-AC38 | UC6-RULE5 |
| UC6-AC39 | RULE-7, UC6-RULE5 |
| UC6-AC40 | UC6-RULE4 |
| UC6-AC41 | UC6-RULE1 |
| UC6-AC42 | RULE-5, UC6-RULE3 |
| UC6-AC43 | UC6-RULE1 |
| UC6-AC44 | UC6-RULE1 |
| UC6-AC45 | UC6-RULE6 |
| UC6-AC46 | RULE-12, UC6-RULE4 |
| UC6-AC47 | RULE-4, RULE-12, UC6-RULE4 |
| UC6-AC48 | RULE-4, RULE-5, UC6-RULE3 |
| UC6-AC49 | UC6-RULE1, UC6-RULE3 |
| UC6-AC50 | RULE-5, UC6-RULE3 |
| UC6-AC51 | RULE-2, RULE-4, RULE-7, UC6-RULE4, UC6-RULE7 |
| UC6-AC52 | RULE-2, RULE-4, RULE-7, UC6-RULE2, UC6-RULE4, UC6-RULE7 |

## Design Exclusions

- No provisional Appointment state for holds and no timed meaning assigned to legacy Visits
- No cancel-and-recreate rescheduling and no owner rescheduling command
- No JPA cascade that deletes Appointment, Visit, request, owner, pet, or veterinarian history
