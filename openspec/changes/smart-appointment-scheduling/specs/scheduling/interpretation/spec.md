## Purpose

Turns an owner's free-text scheduling request into a strict, validated, clinic-normalized structured interpretation behind an explicit consent gate, so that no text is sent to AI without permission and every interpretation the solver receives is well-formed.

## ADDED Requirements

### Requirement: Explicit consent gate before any AI call

The system SHALL require the owner's explicit consent before any free-text scheduling request is sent to the AI interpretation service. No AI call SHALL be made until consent is recorded. The recorded consent SHALL capture a per-request boolean, the timestamp of consent, and a snapshot of the exact text the owner consented to send.

#### Scenario: No AI call happens without consent

- **GIVEN** an appointment request in state `DRAFT` with free text entered
- **WHEN** the owner has not yet given consent
- **THEN** the system does not send the text to the AI service and the request waits in `AWAITING_CONSENT`

#### Scenario: Consent is recorded with a text snapshot

- **GIVEN** an owner reviewing their free-text request
- **WHEN** the owner grants consent to send the text to AI
- **THEN** the system records a consent boolean, a consent timestamp, and a snapshot of the exact consented text before any AI call is made

#### Scenario: Declined consent routes to staff

- **GIVEN** an appointment request in state `AWAITING_CONSENT`
- **WHEN** the owner declines to consent to AI processing
- **THEN** no AI call is made and the request is routed to the staff fallback queue so the owner is not dead-ended

### Requirement: Strict typed structured interpretation

Once consent is given the AI interpretation SHALL be produced as a strict, typed structured output rather than free text. The interpretation SHALL contain: a care type; an optional required specialty; an estimated visit duration in minutes; preferred, allowed, and excluded time windows; an optional preferred veterinarian; and an urgency indicator. Windows SHALL be expressed either as a day-of-week plus a named day-part or as explicit local time ranges in the clinic time zone.

#### Scenario: Interpretation is returned as typed fields

- **GIVEN** an owner who consented to AI processing of their free text
- **WHEN** the AI interpretation succeeds
- **THEN** the system stores a typed interpretation containing care type, optional specialty, estimated duration, preferred/allowed/excluded windows, optional preferred veterinarian, and urgency

#### Scenario: Windows use day-parts or explicit ranges in the clinic zone

- **GIVEN** a produced interpretation with time windows
- **WHEN** the windows are stored
- **THEN** each window is expressed as a day-of-week plus a named day-part or as an explicit local time range, interpreted in the clinic time zone

### Requirement: Server-side validation, clamping, and normalization

The system SHALL validate every AI interpretation server-side before it can be used for scheduling. Estimated duration SHALL be clamped into the clinic's configured minimum/maximum visit-duration bounds. Named day-parts SHALL be normalized to the clinic's configured day-part definitions, and all windows SHALL be normalized to the clinic time zone. An interpretation that cannot be validated into a usable structured form SHALL route the request to the staff fallback queue.

#### Scenario: Duration is clamped to configured bounds

- **GIVEN** an interpretation whose estimated duration is below the minimum or above the maximum configured visit duration
- **WHEN** the interpretation is validated
- **THEN** the duration is clamped into the configured `[minimum, maximum]` range before scheduling uses it

#### Scenario: Day-parts and windows are normalized to the clinic zone

- **GIVEN** an interpretation referencing a named day-part such as "morning"
- **WHEN** the interpretation is validated
- **THEN** the day-part is resolved to the clinic's configured day-part hours and all windows are normalized to the clinic time zone

#### Scenario: Unusable interpretation routes to staff

- **GIVEN** a consented request whose AI interpretation fails validation and cannot be normalized into a usable structured form
- **WHEN** validation completes
- **THEN** the request is routed to the staff fallback queue rather than proceeding to the solver

### Requirement: Owner editing of structured fields without re-consent

After an interpretation is produced the owner SHALL be able to review it and edit its structured fields (such as care type, duration, preferred/allowed/excluded windows, and preferred veterinarian) directly, without giving fresh consent, because consent gates AI calls only and editing structured fields makes no AI call. Edited fields SHALL be re-validated, clamped, and normalized under the same server-side rules.

#### Scenario: Owner corrects a structured field with no new AI call

- **GIVEN** a request in state `INTERPRETED` with a produced interpretation
- **WHEN** the owner edits a structured field such as the preferred window or estimated duration
- **THEN** the change is applied without any new consent and without any AI call, and the edited value is re-validated and clamped server-side

#### Scenario: Confirming the reviewed interpretation advances the request

- **GIVEN** a request in state `INTERPRETED` whose structured fields the owner has reviewed
- **WHEN** the owner confirms the interpretation
- **THEN** the request transitions to `CONFIRMED` and becomes eligible for the guided suggestion flow

### Requirement: Fresh free text requires fresh consent and interpretation

If the owner revises the free-text request itself (as opposed to editing structured fields), the system SHALL require fresh consent and SHALL produce a fresh interpretation from the new text. The previously recorded consent SHALL NOT authorize sending the revised text to AI.

#### Scenario: Revising free text re-arms the consent gate

- **GIVEN** a request with a prior interpretation and a prior recorded consent
- **WHEN** the owner changes the free-text request
- **THEN** the request returns to `AWAITING_CONSENT`, the prior consent no longer authorizes the new text, and a fresh consent is required before any AI call

#### Scenario: Fresh consent triggers a fresh interpretation

- **GIVEN** revised free text for which the owner has just granted fresh consent
- **WHEN** the AI interpretation runs
- **THEN** a new interpretation is produced from the revised text and replaces the prior one

### Requirement: AI failures route to the staff queue

If the AI interpretation service is unavailable, times out, or returns a result that cannot be parsed into the structured schema, the system SHALL NOT dead-end the owner. It SHALL route the request to the staff fallback queue so staff can pick it up.

#### Scenario: AI service unavailable routes to staff

- **GIVEN** a consented request awaiting interpretation
- **WHEN** the AI service is unavailable or the call times out
- **THEN** the request is routed to the staff fallback queue and the owner is informed staff will follow up

#### Scenario: Unparseable AI output routes to staff

- **GIVEN** a consented request whose AI call returns output that cannot be parsed into the structured interpretation schema
- **WHEN** parsing fails
- **THEN** the request is routed to the staff fallback queue rather than surfacing a raw error to the owner
