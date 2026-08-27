## Purpose

Makes an appointment request a first-class, state-driven entity that guides an owner to one suitable slot at a time under short-lived holds, and keeps future `Appointment` records distinct from the clinic's existing completed-`Visit` history.

## ADDED Requirements

### Requirement: Appointment request lifecycle

The system SHALL represent each scheduling attempt as a first-class `AppointmentRequest` with an explicit state machine. States SHALL include `DRAFT`, `AWAITING_CONSENT`, `INTERPRETED`, `CONFIRMED`, `SUGGESTING`, `HELD`, `SCHEDULED`, `QUEUED_FOR_STAFF`, `CANCELLED`, and `EXPIRED`. Every action that changes a request SHALL be a guarded transition; transitions that are not defined for the current state SHALL be rejected.

#### Scenario: Request starts as a draft

- **WHEN** an owner begins a new appointment request for one of their pets
- **THEN** the request is created in state `DRAFT` linked to that owner and pet

#### Scenario: A confirmed request enters the suggestion loop

- **GIVEN** a request in state `CONFIRMED`
- **WHEN** the guided suggestion flow begins
- **THEN** the request transitions to `SUGGESTING`

#### Scenario: Undefined transition is rejected

- **GIVEN** a request in state `SCHEDULED`
- **WHEN** an action valid only for `DRAFT` is attempted
- **THEN** the transition is rejected and the request stays in `SCHEDULED`

### Requirement: Guided one-slot-at-a-time suggestions

The system SHALL offer at most one suggested slot at a time for a request and SHALL NOT expose the clinic's complete availability calendar to the owner. The owner SHALL be able to accept the suggested slot or reject it and ask for another option. Rejecting a suggestion SHALL permanently exclude that exact `(veterinarian, start)` pair from further suggestions for the same request.

#### Scenario: Only one slot is offered at a time

- **GIVEN** a request in the suggestion loop
- **WHEN** the system offers a slot
- **THEN** exactly one suggested `(veterinarian, start)` slot is shown and the full calendar is not revealed

#### Scenario: Rejected slot never reappears for the request

- **GIVEN** an owner rejected the slot `(vet V, start T)` for a request
- **WHEN** the owner asks for another option
- **THEN** the next suggestion is never `(vet V, start T)` and that pair remains excluded for the life of the request

#### Scenario: Ask again re-runs against current calendar and prior rejections

- **WHEN** the owner asks for another option
- **THEN** the system re-runs matching against the confirmed request, the current state of the calendar, and all previously rejected suggestions for that request

### Requirement: Short-lived slot holds with mutual exclusion

While a slot is offered to an owner the system SHALL place a short-lived hold on that `(veterinarian, start)` pair so a second owner cannot be offered or confirm the same pair. A hold SHALL have an expiry based on the configured hold duration. Mutual exclusion SHALL be enforced by a database unique constraint on `(veterinarian, start)` for active holds rather than a long-held lock. Expired holds SHALL be cleaned up by a scheduled sweep and lazily on read.

#### Scenario: Two owners cannot hold the same slot

- **GIVEN** owner A holds `(vet V, start T)`
- **WHEN** owner B's flow attempts to be offered or to hold `(vet V, start T)` before A's hold expires
- **THEN** B does not acquire the hold and B's flow advances to a different slot rather than dead-ending

#### Scenario: Hold expires after the configured duration

- **GIVEN** a hold placed on `(vet V, start T)`
- **WHEN** the configured hold duration elapses without acceptance
- **THEN** the hold is treated as expired and the slot becomes available again for others

#### Scenario: Expired holds are cleaned up

- **GIVEN** holds that have passed their expiry
- **WHEN** the scheduled sweep runs or the slot is read
- **THEN** the expired holds are removed so they no longer block the slot

### Requirement: Accept re-validates and auto-recovers

Accepting a suggested slot SHALL be a transactional operation that re-validates the hold within the transaction. If the slot is still free the system SHALL confirm the appointment. If the hold has expired and the slot has since been taken, the system SHALL NOT dead-end: it SHALL inform the owner the slot is no longer available and automatically offer the next suitable slot.

#### Scenario: Accept confirms when the slot is still held

- **GIVEN** an owner accepts a slot whose hold is still valid
- **WHEN** the accept transaction runs
- **THEN** the hold is converted into a confirmed `Appointment` and the request transitions to `SCHEDULED`

#### Scenario: Accept recovers when the slot was taken after hold expiry

- **GIVEN** an owner accepts a slot whose hold expired and was then taken by someone else
- **WHEN** the accept transaction runs
- **THEN** the system reports the slot is no longer available and automatically offers the next suitable slot instead of returning an error

### Requirement: Appointment entity distinct from visit

The system SHALL model a future `Appointment` (a scheduled slot with veterinarian, start instant, duration, status, and optional link to its originating request) as an entity distinct from the existing `Visit` (a past, completed event on a pet). The existing pet-history user interface SHALL continue to work unchanged.

#### Scenario: Confirmed appointment is a future slot, not a visit

- **WHEN** an appointment is confirmed
- **THEN** an `Appointment` record is created with the veterinarian, start instant, and duration, and no `Visit` record is created at this point

#### Scenario: Existing pet history keeps working

- **GIVEN** a pet with existing visits
- **WHEN** its history page is viewed
- **THEN** the existing visits display unchanged and are not affected by the new appointment model

### Requirement: Completion records a visit

When an appointment is completed the system SHALL record a `Visit` in the pet's history for the completed appointment. The recorded `Visit` MAY be back-linked to its originating appointment.

#### Scenario: Completing an appointment adds a visit to pet history

- **GIVEN** a scheduled appointment for a pet
- **WHEN** the appointment is marked completed
- **THEN** a `Visit` for that pet appears in the existing pet-history user interface

### Requirement: Owner self-service view and cancel

An authenticated owner SHALL be able to view and cancel their own upcoming appointments and SHALL never see or act on another owner's appointments. Owner self-cancellation SHALL be permitted only up to 24 hours before the appointment start.

#### Scenario: Owner views only their own upcoming appointments

- **GIVEN** an authenticated owner
- **WHEN** the owner opens their appointments page
- **THEN** only that owner's upcoming appointments are listed and no other owner's appointments are visible

#### Scenario: Owner cancels more than 24 hours ahead

- **GIVEN** an owner's upcoming appointment that starts more than 24 hours from now
- **WHEN** the owner cancels it
- **THEN** the appointment is cancelled and the slot is freed

#### Scenario: Owner cannot self-cancel inside the 24-hour window

- **GIVEN** an owner's upcoming appointment that starts within the next 24 hours
- **WHEN** the owner attempts to cancel it
- **THEN** the system refuses the self-cancellation and directs the owner to contact staff
