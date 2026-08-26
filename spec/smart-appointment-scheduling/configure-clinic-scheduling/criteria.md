# Acceptance Criteria: UC2 — Configure Clinic Scheduling

## Functional

### UC2-AC1: Use the clinic time zone

**Covers:** UC2-B1

When the system interprets a scheduling date or time, the system shall use the configured clinic time zone.

### UC2-AC2: Default the clinic time zone to UTC

**Covers:** UC2-B2

When no clinic time-zone property is supplied, the system shall use UTC as the clinic time zone.

### UC2-AC3: Reject an invalid clinic time zone

**Covers:** UC2-B3

If the configured clinic time-zone property is invalid, then the system shall refuse startup.

### UC2-AC4: Prevent staff time-zone editing

**Covers:** UC2-B4

If staff attempts to change the clinic time zone through scheduling configuration, then the system shall reject the change and shall preserve the configured application-property value.

### UC2-AC5: Apply scheduling defaults

**Covers:** UC2-B5

When no staff override exists, the system shall use a 30-minute visit duration, a 56-day owner horizon, a 365-day staff horizon, a five-minute guided hold, and named periods of `08:00–12:00`, `12:00–17:00`, and `17:00–20:00`.

### UC2-AC6: Accept an in-range duration

**Covers:** UC2-B6

When staff submits a duration setting between 15 and 120 minutes on a 15-minute increment, the system shall accept the value when all other settings validation succeeds.

### UC2-AC7: Accept duration boundaries

**Covers:** UC2-B6

When staff submits a duration setting of 15 or 120 minutes, the system shall accept the value when all other settings validation succeeds.

### UC2-AC8: Reject an out-of-range duration

**Covers:** UC2-B6

If staff submits a duration setting below 15 minutes or above 120 minutes, then the system shall reject the settings change and shall preserve the prior settings.

### UC2-AC9: Reject an off-grid duration

**Covers:** UC2-B6

If staff submits a duration setting that is not divisible into 15-minute increments, then the system shall reject the settings change and shall preserve the prior settings.

### UC2-AC10: Accept ordered duration settings

**Covers:** UC2-B7

When submitted minimum, default, and maximum durations satisfy minimum less than or equal to default and default less than or equal to maximum, the system shall accept their ordering.

### UC2-AC11: Accept equal duration boundaries

**Covers:** UC2-B7

When submitted minimum, default, and maximum durations are equal valid values, the system shall accept their ordering.

### UC2-AC12: Reject disordered duration settings

**Covers:** UC2-B7

If a submitted minimum duration exceeds the default or the default exceeds the maximum, then the system shall reject the settings change and shall preserve the prior settings.

### UC2-AC13: Accept positive horizons and hold duration

**Covers:** UC2-B8, UC2-B36

When staff submits positive owner-horizon, staff-horizon, and guided-hold values within their other limits, the system shall accept the values.

### UC2-AC14: Reject zero horizons or hold duration

**Covers:** UC2-B8

If staff submits zero for the owner horizon, staff horizon, or guided-hold duration, then the system shall reject the settings change and shall preserve the prior settings.

### UC2-AC15: Reject negative horizons or hold duration

**Covers:** UC2-B8

If staff submits a negative owner horizon, staff horizon, or guided-hold duration, then the system shall reject the settings change and shall preserve the prior settings.

### UC2-AC16: Accept an in-range staff horizon

**Covers:** UC2-B9

When staff submits a staff booking horizon below 730 days and above zero, the system shall accept the value when all other settings validation succeeds.

### UC2-AC17: Accept the maximum staff horizon

**Covers:** UC2-B9

When staff submits a staff booking horizon of 730 days, the system shall accept the value when all other settings validation succeeds.

### UC2-AC18: Reject a staff horizon above the maximum

**Covers:** UC2-B9

If staff submits a staff booking horizon above 730 days, then the system shall reject the settings change and shall preserve the prior settings.

### UC2-AC19: Reject overlapping named periods

**Covers:** UC2-B10

If two submitted named periods overlap, then the system shall reject the settings change and shall preserve the prior named periods.

### UC2-AC20: Create or edit a veterinarian

**Covers:** UC2-B11

When staff submits valid veterinarian details for creation or editing, the system shall persist the veterinarian changes.

### UC2-AC21: Deactivate a veterinarian without deleting history

**Covers:** UC2-B12

When staff deactivates a veterinarian, the system shall retain all history that references that veterinarian.

### UC2-AC22: Maintain the specialty catalog

**Covers:** UC2-B13

When staff submits a valid specialty creation, rename, deactivation, or veterinarian assignment, the system shall persist the specialty change.

### UC2-AC23: Prevent deletion of referenced catalog entries

**Covers:** UC2-B14

If staff attempts to delete a referenced veterinarian or specialty, then the system shall reject deletion and shall retain the referenced entry.

### UC2-AC24: Qualify active veterinarians for general care

**Covers:** UC2-B15

While a veterinarian is active, the system shall treat that veterinarian as eligible for general care.

### UC2-AC25: Require an active specialty assignment

**Covers:** UC2-B16

When specialty care is evaluated, the system shall treat a veterinarian as eligible only when both the veterinarian and the required assigned specialty are active.

### UC2-AC26: Save split weekly shifts

**Covers:** UC2-B17

When staff submits multiple non-overlapping weekly shifts for one veterinarian and day, the system shall save all submitted shifts.

### UC2-AC27: Replace weekly hours with modified hours

**Covers:** UC2-B18

When a date has a date-specific modified-hours rule, the system shall use that rule instead of the veterinarian's recurring weekly shift for that date.

### UC2-AC28: Add date-specific extra hours

**Covers:** UC2-B19

When valid extra hours apply on a date, the system shall add them to the otherwise effective veterinarian availability for that date.

### UC2-AC29: Remove availability during leave

**Covers:** UC2-B20

While a veterinarian leave period applies, the system shall remove availability covered by that leave.

### UC2-AC30: Remove availability during closure

**Covers:** UC2-B21

While a clinic closure applies, the system shall remove all clinic availability covered by that closure.

### UC2-AC31: Give closure highest precedence

**Covers:** UC2-B22

When a clinic closure overlaps leave, modified hours, extra hours, or weekly shifts, the system shall treat the overlapping clinic time as unavailable.

### UC2-AC32: Give leave precedence over veterinarian hours

**Covers:** UC2-B23

When veterinarian leave overlaps modified hours, extra hours, or weekly shifts, the system shall treat the overlapping veterinarian time as unavailable.

### UC2-AC33: Accept a positive same-day availability range

**Covers:** UC2-B24

When an availability range starts before it ends on the same clinic-local date, the system shall accept its ordering when all other validation succeeds.

### UC2-AC34: Reject a zero-length availability range

**Covers:** UC2-B24

If an availability range starts and ends at the same local time, then the system shall reject the range and shall preserve the prior calendar.

### UC2-AC35: Reject an overnight or negative availability range

**Covers:** UC2-B24

If an availability range ends before its start or crosses into another local date, then the system shall reject the range and shall preserve the prior calendar.

### UC2-AC36: Reject overlapping availability ranges

**Covers:** UC2-B25

If submitted shifts or exceptions overlap, then the system shall reject the calendar change and shall preserve the prior calendar.

### UC2-AC37: Reject off-grid availability boundaries

**Covers:** UC2-B25

If a submitted shift or exception starts or ends off the 15-minute grid, then the system shall reject the calendar change and shall preserve the prior calendar.

### UC2-AC38: Include both range dates

**Covers:** UC2-B26

When leave or a closure is configured for a date range, the system shall apply it on both the start date and the end date.

### UC2-AC39: Exclude invalid DST times

**Covers:** UC2-B27

If a clinic-local time is nonexistent or ambiguous because of a daylight-saving transition, then the system shall exclude that time from bookable availability.

### UC2-AC40: Preserve pinned appointments after calendar edits

**Covers:** UC2-B28

When a later calendar edit conflicts with a confirmed appointment, the system shall preserve the confirmed appointment in its existing interval.

### UC2-AC41: Flag a pinned appointment conflict

**Covers:** UC2-B29

When a later calendar edit conflicts with a confirmed appointment, the system shall flag that appointment for staff resolution.

### UC2-AC42: Require a calendar-conflict reason

**Covers:** UC2-B30

If a calendar edit conflicts with a confirmed appointment and no reason is supplied, then the system shall reject the edit and shall preserve the prior calendar.

### UC2-AC43: Preserve request settings snapshots

**Covers:** UC2-B31

When staff changes scheduling settings after a request has captured interpretation settings or materialized its horizon, the system shall preserve that request's captured duration rules, named periods, owner-horizon length, guided-hold duration, and materialized horizon boundaries.

### UC2-AC44: Use live calendar inputs

**Covers:** UC2-B32

When a new matching or booking attempt begins, the system shall use the current veterinarian catalog, appointments, active holds, leave, exceptions, and closures.

### UC2-AC45: Distinguish temporary calendar reservations

**Covers:** UC2-B33

When the staff calendar contains guided holds and staff offers, the system shall render them as distinct temporary entry types.

### UC2-AC46: Seed demo veterinarian schedules

**Covers:** UC2-B34

When `demo-data` is enabled, the system shall provide Monday-through-Friday `09:00–17:00` weekly schedules for seeded veterinarians.

### UC2-AC47: Block new assignments to a deactivated veterinarian

**Covers:** UC2-B35

If a new booking targets a deactivated veterinarian, then the system shall reject the assignment and shall preserve that veterinarian's existing appointments.

### UC2-AC48: Update valid scheduling settings

**Covers:** UC2-B36

When staff submits duration bounds, horizons, guided-hold duration, and named periods that satisfy every validation rule, the system shall persist the new settings.

### UC2-AC49: Maintain valid calendar rules

**Covers:** UC2-B37

When staff submits a valid creation, edit, or removal of a weekly shift, date exception, leave period, or clinic closure, the system shall persist the calendar change.

### UC2-AC50: Reject off-grid named-period boundaries

**Covers:** UC2-B38

If a named period starts or ends off the 15-minute grid, then the system shall reject the settings change and shall preserve the prior named periods.

### UC2-AC51: Update urgent-care contact details

**Covers:** UC2-B39

When staff submits valid clinic contact details for urgent-care guidance, the system shall persist the new contact details for owner display.

## Coverage exclusions

- Authorization: denial of clinic-configuration access to owners is owned by UC1's route authorization criteria.
- Performance: the specification defines no measurable calendar-rendering or configuration-update latency contract.
