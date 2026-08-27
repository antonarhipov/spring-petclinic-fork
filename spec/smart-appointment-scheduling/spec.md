# Smart Appointment Scheduling

## Feature summary

Smart Appointment Scheduling adds a production-shaped, single-clinic scheduling capability to Spring PetClinic. Authenticated owners describe a pet's reason for visiting and availability in English, consent to bounded local AI interpretation, confirm the resulting structured request, and receive one feasible appointment suggestion at a time. Staff configure the clinic calendar, provision accounts, manage appointments, and complete every request that automation cannot safely finish. The feature provides real authorization, privacy, time, concurrency, and fallback guarantees while remaining a reference application rather than production clinic software.

## Use cases

- UC1 — [Secure Scheduling Access](secure-scheduling-access/spec.md)
- UC2 — [Configure Clinic Scheduling](configure-clinic-scheduling/spec.md)
- UC3 — [Interpret an Appointment Request](interpret-appointment-request/spec.md)
- UC4 — [Guide Appointment Selection](guide-appointment-selection/spec.md)
- UC5 — [Resolve a Request Through Staff](resolve-staff-fallback/spec.md)
- UC6 — [Manage Appointment Lifecycle](manage-appointment-lifecycle/spec.md)
- UC7 — [Notify, Audit, and Recover](notify-audit-and-recover/spec.md)

## Cross-cutting decisions

### Product and actor boundary

- The feature is a production-shaped reference implementation for one clinic. It enforces real correctness and privacy boundaries but does not claim production operations, regulatory compliance, or external-service integration.
- This specification is authoritative when the source proposal differs from a resolved decision. In particular, the proposal's Europe/Amsterdam default is superseded by UTC, and demo accounts are exempt from the normal first-login password change.
- The only roles are `OWNER` and `STAFF`. The demo username `admin` has ordinary `STAFF` authority; there is no administrator role.
- One owner account is linked permanently to exactly one PetClinic `Owner`. An owner may manage multiple pets. Staff accounts are not linked to veterinarians.
- Navigation is authorization-aware: users see only links whose endpoints they may access. Endpoint authorization remains authoritative even when a link is hidden.

### Scheduling and time boundary

- A new `Appointment` represents a future booking and preserves its care type and optional required specialty. A legacy `Visit` remains a historical clinical record and is created only when an appointment is completed.
- Veterinarian time is the only solved resource. Rooms, equipment, and assistants are not modeled.
- Appointment starts and durations use 15-minute increments. The staff-configurable owner horizon defaults to 56 clinic-local calendar days; staff scheduling has a separate 365-day default horizon with a hard maximum of 730 days.
- Timestamps are stored as absolute instants. A single application property supplies the clinic time zone, defaults to UTC, and causes startup to fail when invalid. Staff cannot edit the time zone.
- Nonexistent and ambiguous local times at daylight-saving transitions are not bookable.
- General care may be assigned to any active veterinarian. Specialty care requires an active veterinarian with the explicit specialty.
- Owner availability may use bounded calendar language: explicit dates; weekdays; this, next, or a numbered future week; the end of a working week; this, next, numbered future, or named months; and the first, second, third, fourth, or last calendar week of a month. Relative expressions are anchored when the owner confirms the interpretation and are clipped to the configured owner horizon.
- Calendar weeks run Monday through Sunday. Ordinal weeks of a month are the visible Monday-through-Sunday calendar rows intersecting that month and are clipped at month boundaries.

### Request state model

`SchedulingRequest` uses these states:

| State | Meaning |
| --- | --- |
| `AWAITING_CONSENT` | Valid English text and captured interpretation settings await version-specific AI consent and dispatch. |
| `INTERPRETING` | A consented English request is awaiting or undergoing AI interpretation. |
| `CLARIFICATION_REQUIRED` | The owner must resolve an incomplete or ambiguous factual interpretation. |
| `AWAITING_CONFIRMATION` | The owner must review and confirm structured fields. |
| `MATCHING` | The request is eligible for a new guided solve. |
| `SLOT_HELD` | One automated suggestion is held for the owner. |
| `STAFF_QUEUED` | Automation has handed the request to staff. |
| `STAFF_OFFERED` | One staff-selected slot is held for owner approval. |
| `CONFIRMED` | The request produced a confirmed appointment and is terminal. |
| `CANCELLED` | The request was cancelled and is terminal. |
| `EXPIRED` | The staff-fallback lifetime ended and is terminal. |

- A pet may have at most one nonterminal scheduling request. It may have multiple non-overlapping confirmed appointments.
- Expected business outcomes are validation failure, not found, conflict, exhausted, clarification required, fallback created, or success. Unexpected failures remain errors; they are not disguised as ordinary business outcomes.

### AI and clinical-safety boundary

- AI supplies a bounded, versioned structured interpretation. It does not perform clinical triage, write medical advice, choose outside configured catalogs, or bypass deterministic validation.
- An urgency indication stops automated matching and prioritizes staff review. Application-provided urgent-care guidance remains visible independently of AI.
- English is the only guaranteed AI input language. Other selected languages bypass AI and enter staff fallback.
- AI receives only the consented text, pet type without its name, clinic date/time-zone context, named periods, and veterinarian/specialty names. It never receives owner contact details, account data, the full calendar, or unrelated history.
- AI calls have a 60-second hard timeout and one attempt. Solver calls have a two-second hard timeout and one attempt. Failure creates durable staff fallback.
- The application remains usable without Ollama or successful solving: staff security, calendars, direct booking, appointment management, and fallback continue to work.

### Concurrency and calendar boundary

- Confirmed appointments are pinned. No solve moves or re-optimizes existing bookings.
- Owner, pet, and veterinarian overlap; closures; leave; specialty eligibility; owner allowed/excluded windows; and prior exact rejections are hard constraints.
- Hold, acceptance, cancellation, direct-booking, and rescheduling operations recheck all affected overlaps atomically. Concurrent losers return to the current resource state rather than double-booking.
- Guided holds use the request's staff-configurable snapshot duration, defaulting to five minutes. Staff offers last 24 hours. Staff fallback lasts seven days.
- Persisted deadlines are enforced both during user actions and by lifecycle cleanup. Polling never starts duplicate work or silently acquires a new hold.

### Privacy, persistence, and compatibility boundary

- Raw text and full AI output are retained until 30 days after a linked appointment first becomes `COMPLETED`, `NO_SHOW`, or `CANCELLED`, or 30 days after a request without an appointment becomes terminal. Purging preserves only the minimal structured scheduling record and privacy-safe audit metadata.
- Audit metadata is append-only and retained indefinitely. It never contains passwords, purged AI text, or clinical descriptions.
- Flyway is the sole schema owner. Fresh databases build the legacy baseline at V1 and add scheduling at V2 or later. Existing databases require an explicit one-time baseline at V1; unknown schemas are never baselined automatically.
- Authentication and authorization use Spring Security form login. AI integration uses Spring AI 2.0.1 with Ollama. Scheduling uses Timefold 2.5.0 in the single-request boundary defined by UC4.
- H2, MySQL, and PostgreSQL remain supported. Maven and Gradle remain equivalent Java 21 builds and both run unit and UI-security verification. One CI integration matrix verifies the database-specific migrations and concurrency contract without duplicating that matrix under both build tools.
- The web experience remains Spring MVC and Thymeleaf. JavaScript is limited to status polling and countdowns; no public scheduling REST API is introduced.

## Explicit assumptions

- The clinic has one configured time zone and no overnight shifts.
- Owners attend their pets' appointments, so an owner's appointments and holds cannot overlap even across different pets.
- The configured `gemma4:latest` Ollama model is replaceable by property. Reproducible CI pins the configured model to a digest.
- Staff coordinate direct bookings with owners outside the application; the application records that staff booked directly instead of representing it as owner approval.
- A staff-authored completion description, not owner text or AI output, becomes the legacy Visit description.
- Existing date-only Visits are historical, even when their stored dates are in the future, and never block timed scheduling.
- All new owner-visible screens and urgent guidance use the existing localized message-bundle mechanism, even though AI interpretation is guaranteed only for English.

## Use-case relationships

1. UC1 establishes an authenticated owner or staff identity and limits every later use case to the actor's authorized data.
2. UC2 supplies the live veterinarian catalog, clinic rules, and availability consumed by UC3 through UC6.
3. UC3 captures interpretation settings when valid intake or replacement text is persisted, before language and consent routing. Valid English text enters `AWAITING_CONSENT`; consented dispatch enters `INTERPRETING`; a valid result or fully resolved clarification enters `AWAITING_CONFIRMATION`. Confirmation preserves the captured values, materializes the absolute owner horizon from the confirmation instant, freezes the request snapshot, and moves the request to `MATCHING`.
4. UC4 solves and holds one automated suggestion. Acceptance creates an appointment and terminates the request as `CONFIRMED`; rejection or expiry returns it to `MATCHING`.
5. Any defined automation dead end moves the request to UC5 as `STAFF_QUEUED`. Staff may issue one owner-approved offer or book directly after offline coordination.
6. UC6 owns the resulting appointment independently of the terminal request. Cancelling an appointment never reopens its request; rebooking starts a new request.
7. UC7 records and communicates state changes from all other use cases, purges sensitive data, and restores safe states after deadlines or interrupted work.
8. Editing confirmed structured request fields releases any hold, clears suggestions and rejections, returns the request to `AWAITING_CONFIRMATION`, and materializes a new absolute owner horizon from the reconfirmation instant while retaining the settings captured for that interpretation.
9. Replacing original text in an automated nonterminal state invalidates any active AI or solver operation, releases its guided hold, clears the old structured interpretation, suggestion and rejection history, and materialized horizon, captures current interpretation settings, and returns the request to `AWAITING_CONSENT`. Text replacement is unavailable after staff fallback begins or the request becomes terminal.
10. When a request first enters `STAFF_QUEUED` without a materialized owner horizon, UC5 materializes that horizon from `first_queued_at` using the settings captured by UC3. A horizon already materialized by owner confirmation is preserved.
11. Owner cancellation of any owned nonterminal request releases every active reservation and removes any staff claim in the same state transition.

## Out of scope

- Production operations, regulatory compliance tooling, or claims that Spring PetClinic is safe for clinical deployment
- Owner self-registration, owner-initiated password recovery, or account self-deletion
- Payments, waitlists, recurring appointments, telehealth, or group appointments
- Multiple clinics or per-user time zones
- Rooms, equipment, assistants, or other schedulable resources
- Overnight veterinarian shifts
- Email, SMS, push, or other external notifications
- Exposing the full availability calendar to owners
- A public scheduling REST API
- Automatic conversion of existing persistent owners into predictable-password accounts
- Reopening terminal requests or automatically reversing completed clinical Visits

## External dependencies

- **Ollama and configured model:** AI interpretation requires a reachable Ollama instance serving the configured model. Absence, timeout, invalid output, or connection failure uses `STAFF_QUEUED` as the complete default behavior; implementation is not blocked.
- **Deployment-supplied initial staff credentials:** A non-demo database with no active staff account requires configured initial staff credentials. Predictable `admin/admin123` credentials exist only under the explicit `demo-data` profile.
