## Purpose

Gives staff a fallback queue and direct-management tools so that no owner is ever dead-ended when the automated flow cannot proceed, and defines the appointment lifecycle staff own end to end plus the emergency-handling safety rules.

## ADDED Requirements

### Requirement: Staff fallback queue

The system SHALL provide a staff fallback queue that collects every appointment request the automated flow cannot complete. A request SHALL enter the queue when the AI or solver is unavailable, when the owner declines consent, when the interpretation is incomplete or fails validation, or when no veterinarian has the required specialty. Each queued item SHALL carry enough context (owner, pet, free text, any partial interpretation, and the trigger reason) for staff to act.

#### Scenario: Declined consent enters the queue

- **GIVEN** an owner who declines consent to AI processing
- **WHEN** the request is routed to staff
- **THEN** it appears in the staff fallback queue tagged with the declined-consent trigger and the owner's free text

#### Scenario: AI or solver unavailable enters the queue

- **GIVEN** a consented request whose AI interpretation or solver run fails or is unavailable
- **WHEN** the failure is handled
- **THEN** the request appears in the staff fallback queue tagged with the unavailable-service trigger

#### Scenario: No matching specialty enters the queue

- **GIVEN** an interpretation that requires a specialty no available veterinarian has
- **WHEN** the solver finds no feasible slot for that specialty
- **THEN** the request appears in the staff fallback queue tagged with the no-specialty trigger

#### Scenario: Incomplete interpretation enters the queue

- **GIVEN** a consented request whose interpretation cannot be validated into a usable structured form
- **WHEN** validation fails
- **THEN** the request appears in the staff fallback queue tagged with the incomplete-interpretation trigger

### Requirement: Staff unblock then owner resumes

For queued requests that can be returned to the automated flow, staff SHALL be able to unblock the request so the owner resumes the guided suggestion flow in-app. Because holds are short-lived, the resume flow SHALL rely on the owner returning to the app rather than on any external notification.

#### Scenario: Staff unblock returns the request to the owner flow

- **GIVEN** a queued request that staff have resolved enough to continue automatically
- **WHEN** staff unblock the request
- **THEN** the request re-enters the guided suggestion flow so the owner can be offered a slot again

#### Scenario: Resume relies on the owner returning, not on notification

- **GIVEN** an unblocked request awaiting the owner
- **WHEN** the owner next opens the app
- **THEN** the guided flow resumes from where it left off without depending on any external notification

### Requirement: Staff direct booking, reschedule, and cancel

As an escape hatch for cases the automated flow cannot resolve — such as a request needing a specialty no veterinarian has — staff SHALL be able to directly book, reschedule, and cancel appointments on behalf of an owner. Every staff cancel or reschedule SHALL record a reason. Direct booking SHALL respect the same feasibility rules (effective availability and no overlap) as the automated flow.

#### Scenario: Staff book directly for an unsolvable request

- **GIVEN** a queued request that the automated flow cannot resolve
- **WHEN** staff book an appointment directly on behalf of the owner
- **THEN** an `Appointment` is created for the owner's pet and the request transitions to `SCHEDULED`

#### Scenario: Staff reschedule records a reason

- **GIVEN** an existing appointment
- **WHEN** staff reschedule it to a new feasible slot
- **THEN** the appointment moves to the new slot and the recorded reason is stored

#### Scenario: Staff cancel records a reason

- **GIVEN** an existing appointment
- **WHEN** staff cancel it
- **THEN** the appointment is cancelled, the slot is freed, and the recorded reason is stored

#### Scenario: Direct booking still respects feasibility

- **GIVEN** a slot that overlaps an existing appointment or falls outside the veterinarian's effective availability
- **WHEN** staff attempt to book it directly
- **THEN** the system rejects the booking as infeasible

### Requirement: Appointment completion and no-show lifecycle

Staff SHALL own the appointment lifecycle after scheduling. Staff SHALL be able to mark an appointment completed, which records a `Visit` in the pet's history, or mark it as a no-show. Both outcomes SHALL move the appointment out of the active upcoming set.

#### Scenario: Completing an appointment records a visit

- **GIVEN** a scheduled appointment for a pet
- **WHEN** staff mark it completed
- **THEN** a `Visit` is recorded in the pet's history and the appointment is marked completed

#### Scenario: No-show is recorded without a visit

- **GIVEN** a scheduled appointment the owner did not attend
- **WHEN** staff mark it as a no-show
- **THEN** the appointment is marked no-show, no `Visit` is recorded, and it leaves the active upcoming set

### Requirement: Emergency prioritization is advisory only

The AI-derived urgency indicator SHALL be advisory only: it MAY help staff prioritize suspected emergencies in the fallback queue and MAY influence ranking, but it SHALL NEVER by itself schedule, reject, or hide anything for the owner. Staff SHALL be able to see and act on flagged suspected emergencies.

#### Scenario: Suspected emergency is flagged for staff prioritization

- **GIVEN** an interpretation whose urgency indicates a suspected emergency
- **WHEN** the request reaches the staff fallback queue
- **THEN** it is flagged as a suspected emergency so staff can prioritize it

#### Scenario: AI urgency never overrides safety on its own

- **GIVEN** an AI urgency signal
- **WHEN** the flow proceeds
- **THEN** the urgency only advises prioritization and does not by itself schedule, cancel, or suppress any information for the owner

### Requirement: Urgent-care guidance is shown unconditionally

Static urgent-care guidance SHALL be visible to owners unconditionally and SHALL remain reachable without authentication. It SHALL NEVER be hidden or gated by an AI result, so a model miss can never suppress safety guidance.

#### Scenario: Urgent-care guidance is always visible

- **GIVEN** any owner-facing scheduling screen
- **WHEN** the owner views it
- **THEN** the static urgent-care guidance is present regardless of any AI urgency result

#### Scenario: Urgent-care guidance survives a model miss

- **GIVEN** the AI service is unavailable or returns no urgency
- **WHEN** an owner uses the flow
- **THEN** the static urgent-care guidance is still shown and remains reachable without authentication
