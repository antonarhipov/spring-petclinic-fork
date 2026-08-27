## Purpose

Exposes staff-facing screens over the already-implemented clinic calendar and settings data so staff can view and edit veterinarian weekly shifts, availability exceptions, leave, clinic closures, and clinic-wide scheduling settings, all restricted to the staff role and without changing the underlying scheduling behavior.

## ADDED Requirements

### Requirement: Staff manage veterinarian weekly shifts

Staff SHALL be able to view and edit each veterinarian's recurring weekly working shifts through a staff-only screen. Changes SHALL be persisted against the existing weekly-shift data and SHALL take effect for subsequent availability resolution without altering how availability is computed.

#### Scenario: Staff view weekly shifts

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** the user opens the veterinarian weekly-shifts screen
- **THEN** the current recurring weekly shifts for veterinarians are displayed

#### Scenario: Staff edit a weekly shift

- **GIVEN** an authenticated user with role `STAFF` viewing the weekly-shifts screen
- **WHEN** the user adds, changes, or removes a weekly shift for a veterinarian and saves
- **THEN** the change is persisted and reflected the next time that veterinarian's availability is resolved

### Requirement: Staff manage availability exceptions and leave

Staff SHALL be able to view and edit date-specific availability exceptions and veterinarian leave through a staff-only screen. These entries SHALL override the recurring weekly shifts for the affected dates, consistent with existing availability rules.

#### Scenario: Staff record a veterinarian leave

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** the user records leave for a veterinarian over a date range and saves
- **THEN** the leave is persisted and that veterinarian has no available working blocks on those dates

#### Scenario: Staff add an availability exception

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** the user adds a date-specific availability exception for a veterinarian and saves
- **THEN** the exception is persisted and overrides the recurring weekly shift for that date

### Requirement: Staff manage clinic closures

Staff SHALL be able to view and edit clinic-wide closures for a date or date range through a staff-only screen. A saved closure SHALL apply to every veterinarian, consistent with existing closure rules.

#### Scenario: Staff add a clinic closure

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** the user adds a clinic-wide closure for a date and saves
- **THEN** the closure is persisted and no veterinarian has available working blocks on that date

### Requirement: Staff view and edit clinic settings

Staff SHALL be able to view and edit the clinic-wide scheduling settings through a staff-only screen. Saved settings SHALL replace the current configuration and SHALL be used by subsequent scheduling, without introducing new setting semantics.

#### Scenario: Staff view clinic settings

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** the user opens the clinic-settings screen
- **THEN** the current clinic-wide scheduling settings are displayed

#### Scenario: Staff update clinic settings

- **GIVEN** an authenticated user with role `STAFF` viewing the clinic-settings screen
- **WHEN** the user changes a setting to a valid value and saves
- **THEN** the updated setting is persisted and used by subsequent scheduling

### Requirement: Calendar and settings screens require staff role

All veterinarian-calendar and clinic-settings screens SHALL require the `STAFF` role. Anonymous visitors and `OWNER` users SHALL be denied access.

#### Scenario: Owner denied access to calendar and settings

- **GIVEN** an authenticated user with role `OWNER`
- **WHEN** the user requests any veterinarian-calendar or clinic-settings screen
- **THEN** access is denied

#### Scenario: Anonymous denied access to calendar and settings

- **GIVEN** no authenticated session
- **WHEN** a visitor requests any veterinarian-calendar or clinic-settings screen
- **THEN** the visitor is redirected to login and the data is not revealed
