## Purpose

Exposes the already-implemented guided appointment-request flow to owners so an owner can initiate a request in plain language and be walked through the accept/reject/ask-again suggestion loop, reusing the existing request lifecycle, holds, interpretation, and solver without introducing new backend behavior.

## ADDED Requirements

### Requirement: Owner-initiated appointment request

An authenticated `OWNER` SHALL be able to initiate an appointment request for one of their own pets through the UI. Initiating the request SHALL create a request in its initial `DRAFT` state linked to that owner and pet, entering the same lifecycle used by the existing scheduling flow. The owner SHALL provide their scheduling need in free text as the input to interpretation.

#### Scenario: Owner initiates a request in draft

- **GIVEN** an authenticated owner with a pet
- **WHEN** the owner submits a new self-scheduling request for that pet with a free-text description of their availability and need
- **THEN** a new appointment request is created in state `DRAFT` linked to that owner and pet

#### Scenario: Initiation is limited to the owner's own pets

- **GIVEN** an authenticated owner
- **WHEN** the owner attempts to initiate a request for a pet they do not own
- **THEN** no request is created and the action is refused

### Requirement: Owner passes through the consent gate before interpretation

Before any AI interpretation of an owner's free-text input, the system SHALL require the owner to pass the existing consent gate. Interpretation SHALL NOT run for a request that has not obtained consent.

#### Scenario: Owner grants consent and the request is interpreted

- **GIVEN** an owner-initiated request awaiting consent
- **WHEN** the owner grants consent
- **THEN** the request proceeds to interpretation of the owner's free-text input

#### Scenario: Without consent no interpretation occurs

- **GIVEN** an owner-initiated request that has not obtained consent
- **WHEN** the owner tries to proceed to suggestions
- **THEN** the system does not run interpretation and keeps the request awaiting consent

### Requirement: Owner drives the guided accept/reject/ask-again loop

After interpretation, the owner SHALL be guided through one suggested slot at a time. The owner SHALL be able to accept the current suggestion to hold and confirm it, reject it to receive a different suggestion, or ask again for another option. Rejecting a suggestion SHALL permanently exclude that suggested slot from future suggestions for the request, consistent with the existing suggestion behavior.

#### Scenario: Owner accepts a suggested slot

- **GIVEN** an owner-initiated request presenting a suggested slot
- **WHEN** the owner accepts the suggestion
- **THEN** the system holds and confirms that slot for the owner's pet, resulting in a scheduled appointment

#### Scenario: Owner rejects a suggested slot and gets another

- **GIVEN** an owner-initiated request presenting a suggested slot
- **WHEN** the owner rejects the suggestion
- **THEN** the system offers a different slot and does not offer the rejected slot again for this request

#### Scenario: Owner asks again for another option

- **GIVEN** an owner-initiated request presenting a suggested slot
- **WHEN** the owner asks for another option without rejecting
- **THEN** the system offers an alternative suggestion for the same request

### Requirement: Owner self-scheduling failures fall back to the staff queue

When interpretation, suggestion, or hold acquisition cannot produce a bookable slot for an owner-initiated request, the system SHALL route the request to the existing staff fallback queue rather than exposing an error dead-end to the owner. This fallback behavior SHALL be unchanged from the existing flow.

#### Scenario: No bookable slot routes the request to staff

- **GIVEN** an owner-initiated request whose interpretation or solving yields no bookable slot
- **WHEN** the guided flow cannot present a suggestion
- **THEN** the request is queued for staff handling and the owner is informed it will be handled by the clinic

### Requirement: Urgent-care guidance remains visible during owner self-scheduling

The static urgent-care guidance SHALL remain visible on owner self-scheduling screens unconditionally, regardless of authentication state or request state, preserving the existing safety requirement.

#### Scenario: Urgent-care guidance shown on owner scheduling screens

- **WHEN** an owner views any owner self-scheduling screen
- **THEN** the static urgent-care guidance is shown
