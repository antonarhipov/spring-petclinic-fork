# UC2: Configure Clinic Scheduling

## Summary

Staff maintain the clinic rules, veterinarian catalog, recurring availability, date-specific exceptions, leave, and closures that define bookable time. Configuration changes affect future matching without silently moving existing appointments.

## Resolved ambiguities

### Clinic time and defaults

- The clinic time zone is an application property, defaults to UTC, and is not staff-editable. An invalid zone prevents startup.
- Appointment starts and durations use a fixed 15-minute increment.
- Default owner booking horizon is 56 clinic-local calendar days.
- Guided hold duration is staff-configurable and defaults to five minutes. Staff-offer duration is fixed at 24 hours; staff-fallback lifetime is fixed at seven days.
- Visit duration defaults to 30 minutes and is staff-configurable in 15-minute increments within the hard range 15–120 minutes.
- Staff booking horizon defaults to 365 days, is staff-configurable, and cannot exceed 730 days.
- Named periods default to morning `08:00–12:00`, afternoon `12:00–17:00`, and evening `17:00–20:00`.
- The owner horizon starts at the next future grid boundary after its materialization instant and ends exclusively at the start of the clinic-local date the configured number of days after that instant's clinic-local date. Owner confirmation or initial staff fallback supplies the materialization instant. The configured staff horizon uses the same local-date boundary rule.

### Veterinarian and specialty catalog

- Staff may create, edit, and deactivate veterinarians.
- Staff may create, rename, and deactivate specialties and assign active specialties to veterinarians.
- Referenced veterinarians and specialties are deactivated rather than deleted.
- Any active veterinarian is eligible for general care. Specialty care requires an explicit active specialty assignment.
- Deactivation blocks new bookings but does not move or cancel existing appointments.

### Availability model and precedence

- Each veterinarian has zero or more non-overlapping weekly shifts per day, allowing split shifts.
- A date-specific modification replaces that date's weekly schedule. Extra hours extend it. Leave removes all veterinarian availability for the covered period.
- Clinic closures override every veterinarian rule. Veterinarian leave overrides weekly, modified, and extra hours.
- Shifts, exceptions, leave, and closures use clinic-local dates and times. Date ranges are inclusive.
- Overnight availability is unsupported: every time range must start before it ends on one local date.
- Overlapping ranges and starts or ends that violate the 15-minute increment are invalid.
- Nonexistent or ambiguous DST local times are excluded from bookable availability.

### Effects of configuration changes

- **Confirmed appointments stay** `pinned` when a new closure, leave period, or reduced schedule conflicts with them. The system flags each conflict for staff resolution.
- A configuration change that conflicts with a pinned appointment requires a reason. Other configuration changes record their before/after values without mandatory prose.
- Every valid request captures duration rules, named-period definitions, owner-horizon length, and guided-hold duration before language and consent routing. Later setting changes do not alter those captured values or a horizon materialized at owner confirmation or initial staff fallback.
- Every new solve uses the live veterinarian catalog, appointments, holds, leave, exceptions, and closures.
- The full staff calendar distinguishes confirmed appointments, guided holds using their captured duration, and 24-hour staff offers.
- The `demo-data` profile supplies Monday–Friday `09:00–17:00` weekly schedules for seeded veterinarians.

## Explicit assumptions

- Staff are responsible for resolving pinned appointments that conflict with newly recorded unavailability.
- An active request's snapshot prevents its owner-visible meaning from changing mid-flow, while live calendar inputs prevent stale booking.
- Clinic-local recurring schedules are converted to absolute instants only when resolving a concrete date.

## Handled edge cases

- A closure and extra-hours rule on the same date yields no availability because closure wins.
- A leave period and modified-hours rule for the same veterinarian yields no availability because leave wins.
- A modified-hours rule on a date with a weekly shift replaces rather than merges with that shift.
- Deactivating a veterinarian with future appointments leaves those appointments visible and flagged while preventing new assignments.
- A settings edit cannot place minimum, default, or maximum duration out of order.
- A settings edit cannot create non-positive deadlines or horizons.
- A local time inside a DST gap or repeated hour never becomes a candidate.

## Behaviors to verify

- UC2-B1: The system interprets scheduling dates and times in the configured clinic time zone.
- UC2-B2: The system uses UTC when no clinic time-zone property is supplied.
- UC2-B3: The system refuses startup when the configured clinic time zone is invalid.
- UC2-B4: The system prevents staff from editing the clinic time zone.
- UC2-B5: The system provides the approved default duration, owner and staff horizons, guided-hold duration, and named periods when no staff override exists.
- UC2-B6: The system rejects a duration setting outside 15–120 minutes or off the 15-minute increment.
- UC2-B7: The system rejects duration settings unless minimum is no greater than default and default is no greater than maximum.
- UC2-B8: The system rejects a non-positive owner horizon, staff horizon, or guided-hold duration.
- UC2-B9: The system rejects a staff booking horizon above 730 days.
- UC2-B10: The system rejects overlapping named periods.
- UC2-B11: The system permits staff to create and edit a veterinarian.
- UC2-B12: The system permits staff to deactivate a veterinarian without deleting referenced history.
- UC2-B13: The system permits staff to create, rename, deactivate, and assign specialties.
- UC2-B14: The system prevents deletion of a referenced veterinarian or specialty.
- UC2-B15: The system treats every active veterinarian as eligible for general care.
- UC2-B16: The system treats a veterinarian as eligible for specialty care only when the veterinarian has that active specialty.
- UC2-B17: The system permits staff to define multiple non-overlapping weekly shifts for a veterinarian on one day.
- UC2-B18: The system applies a date-specific modified-hours rule instead of the recurring weekly shift for that date.
- UC2-B19: The system adds date-specific extra hours to otherwise effective veterinarian availability.
- UC2-B20: The system removes veterinarian availability covered by leave.
- UC2-B21: The system removes all veterinarian availability covered by a clinic closure.
- UC2-B22: The system evaluates clinic closure before leave, exceptions, extra hours, and weekly shifts.
- UC2-B23: The system evaluates veterinarian leave before modified hours, extra hours, and weekly shifts.
- UC2-B24: The system rejects an overnight or non-positive local availability range.
- UC2-B25: The system rejects overlapping or off-grid shifts and exceptions.
- UC2-B26: The system treats the start and end dates of leave and closure ranges as included.
- UC2-B27: The system excludes nonexistent and ambiguous DST local times from bookable availability.
- UC2-B28: The system preserves a confirmed appointment when a later calendar edit conflicts with it.
- UC2-B29: The system flags a confirmed appointment affected by a later calendar edit for staff resolution.
- UC2-B30: The system requires a reason before saving a calendar edit that conflicts with a confirmed appointment.
- UC2-B31: The system preserves a request's captured settings and materialized horizon after later staff setting changes.
- UC2-B32: The system uses the live effective calendar for every new matching or booking attempt.
- UC2-B33: The system renders guided holds and staff offers as distinct temporary entries on the staff calendar.
- UC2-B34: The system seeds weekday `09:00–17:00` schedules for seeded veterinarians when `demo-data` is enabled.
- UC2-B35: The system prevents new booking with a deactivated veterinarian while preserving that veterinarian's existing appointments.
- UC2-B36: The system permits staff to update duration bounds, owner and staff horizons, guided-hold duration, and named periods within their validation rules.
- UC2-B37: The system permits staff to create, edit, and remove weekly shifts, date exceptions, leave, and clinic closures within their validation rules.
- UC2-B38: The system rejects a named-period boundary that is not aligned to the 15-minute increment.
- UC2-B39: The system permits staff to update the clinic contact details displayed with urgent-care guidance.

## Out of scope

- Multiple clinics or staff-editable time zones
- Overnight shifts
- Rooms, equipment, assistants, or capacity pools
- Automatic cancellation or reassignment of appointments after calendar changes
- Hard deletion of referenced veterinarians or specialties
