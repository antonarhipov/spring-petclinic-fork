## Purpose

Models how the clinic actually operates over time — clinic-wide scheduling settings, per-veterinarian availability, and closures anchored to a single configured time zone — so that scheduling decisions rest on an accurate, staff-maintained calendar.

## ADDED Requirements

### Requirement: Clinic settings with defaults

The system SHALL provide clinic-wide scheduling settings that staff can adjust: minimum, maximum, and default visit duration; how far ahead appointments may be booked (booking horizon); how long a suggested slot is held; the start-time grid granularity; and named parts of the day. Sensible defaults SHALL apply out of the box.

#### Scenario: Defaults apply out of the box

- **GIVEN** a clinic with no custom configuration
- **THEN** visit duration bounds are 15–120 minutes with a default of 30 minutes, the booking horizon is 60 days ahead, the hold duration is 10 minutes, and the start-time grid granularity is 15 minutes

#### Scenario: Default day-parts are defined

- **GIVEN** a clinic with no custom configuration
- **THEN** the named day-parts are morning 08:00–12:00, afternoon 12:00–17:00, and evening 17:00–20:00, expressed in the clinic time zone

#### Scenario: Staff adjust a clinic setting

- **GIVEN** an authenticated staff user
- **WHEN** the staff user changes a clinic setting such as the booking horizon
- **THEN** the new value is persisted and used by subsequent scheduling decisions

#### Scenario: Visit duration is clamped to configured bounds

- **GIVEN** clinic settings with a minimum of 15 and a maximum of 120 minutes
- **WHEN** a requested visit duration falls below the minimum or above the maximum
- **THEN** the system clamps the duration into the configured range

### Requirement: Per-veterinarian recurring weekly schedule

Each veterinarian SHALL have a recurring weekly working schedule expressed in the clinic time zone. The schedule SHALL support split shifts within a single day (for example a morning block and an afternoon block separated by a break).

#### Scenario: Split shift produces two working blocks in a day

- **GIVEN** a veterinarian with a morning block 09:00–12:00 and an afternoon block 13:00–17:00 on Tuesdays
- **WHEN** availability for a Tuesday is resolved
- **THEN** two separate working blocks are produced and the 12:00–13:00 break is not available

#### Scenario: Outside recurring hours is unavailable

- **GIVEN** a veterinarian whose weekly schedule has no block on Sundays
- **WHEN** availability for a Sunday is resolved
- **THEN** the veterinarian has no available working blocks that day from the recurring schedule

### Requirement: Date-specific availability exceptions

The system SHALL allow date-specific exceptions to a veterinarian's recurring schedule that either add availability on a date or remove/replace the recurring availability for that date. Exceptions SHALL take precedence over the recurring schedule for the affected date.

#### Scenario: Exception overrides the recurring schedule for a date

- **GIVEN** a veterinarian whose recurring schedule would make them available on a specific date
- **WHEN** a date-specific exception removes availability for that date
- **THEN** the resolved availability for that date reflects the exception, not the recurring schedule

#### Scenario: Exception adds availability on an otherwise-off day

- **GIVEN** a veterinarian with no recurring block on a given weekday
- **WHEN** a date-specific exception adds a working block on that date
- **THEN** the resolved availability for that date includes the added block

### Requirement: Per-veterinarian leave

The system SHALL allow recording leave for a veterinarian over a date range. During leave the veterinarian SHALL have no available working blocks regardless of the recurring schedule or exceptions.

#### Scenario: Leave removes all availability in range

- **GIVEN** a veterinarian on leave from a start date through an end date
- **WHEN** availability for any date within that range is resolved
- **THEN** the veterinarian has no available working blocks on those dates

### Requirement: Clinic-wide closures

The system SHALL allow clinic-wide closures for a date or date range that apply to every veterinarian. On a closed date no veterinarian SHALL have available working blocks.

#### Scenario: Closure overrides all veterinarian availability

- **GIVEN** a clinic-wide closure on a specific date
- **WHEN** availability for that date is resolved for any veterinarian
- **THEN** no veterinarian has available working blocks on that date

### Requirement: Single configured time zone

The clinic SHALL operate in a single configured time zone (default `Europe/Amsterdam`) that anchors how availability, day-parts, and appointments are interpreted. Instants SHALL be stored in UTC and reasoned about in the clinic time zone, with conversion happening at a single daylight-saving-aware boundary.

#### Scenario: Local working hours map to stored instants via the clinic zone

- **GIVEN** the clinic time zone is `Europe/Amsterdam`
- **WHEN** a veterinarian's local working block is resolved into concrete instants
- **THEN** the local times are interpreted in the clinic time zone and stored as UTC instants

#### Scenario: Daylight-saving transition is handled at the conversion boundary

- **GIVEN** a booking-horizon date that falls on a daylight-saving transition in the clinic time zone
- **WHEN** local working hours are converted to instants
- **THEN** the conversion accounts for the gap or overlap so resolved availability remains correct and continuous
