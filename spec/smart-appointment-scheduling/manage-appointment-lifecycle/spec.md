# UC6: Manage Appointment Lifecycle

## Summary

Owners view and cancel their own upcoming bookings. Staff book against the full calendar, reschedule or cancel with reasons, and close elapsed appointments as completed or no-show. Completion creates one historical PetClinic Visit; legacy Visits never act as timed bookings.

## Resolved ambiguities

### Appointment model and visibility

- `Appointment` is separate from legacy `Visit` and records owner, pet, veterinarian, start instant, duration, care type, optional required specialty, status, booking source, optional scheduling request, and staff change reason.
- Appointment statuses are `BOOKED`, `COMPLETED`, `NO_SHOW`, and `CANCELLED`.
- Owners may view their own `BOOKED` upcoming appointments. Staff may view all appointments and the full clinic calendar.
- Multiple future appointments for one owner or pet are permitted only when their intervals do not overlap.

### Owner cancellation and rebooking

- An owner may cancel an owned `BOOKED` appointment until its start instant. Owner cancellation reason is optional.
- Cancellation atomically frees veterinarian, owner, and pet time.
- Cancelling an appointment does not reopen its terminal scheduling request. The owner starts a new request to rebook.

### Staff direct booking, rescheduling, and cancellation

- Staff may directly book without a scheduling request after offline coordination. A nonblank reason is required.
- Staff must classify an unlinked direct booking as `GENERAL` with no required specialty or `SPECIALTY` with one active required specialty. The care type and optional specialty are stored on the Appointment and cannot be changed by rescheduling.
- Staff direct booking uses the live calendar, must start in the future, and may be no farther than the configured staff horizon.
- Staff direct booking, rescheduling, and cancellation apply the same veterinarian, owner, pet, specialty, availability, closure, leave, 15-minute, and DST rules as guided booking.
- A staff member must resolve or cancel a pet's existing nonterminal scheduling request before creating an unrelated direct booking for that pet.
- Staff may reschedule only a `BOOKED` appointment to another future feasible interval. The same appointment identity and history are retained, and a nonblank reason is required.
- Rescheduling reuses the Appointment's stored care type and required specialty when checking veterinarian eligibility. Changing clinical routing requires cancellation and a new direct booking.
- Staff may cancel a `BOOKED` appointment before its scheduled end with a nonblank reason. Once the end has passed, staff records completion or no-show instead.
- Successful rescheduling marks every unresolved calendar-conflict record for that appointment resolved as `RESCHEDULED`; successful cancellation marks it resolved as `CANCELLED`. Resolution records the resolving event and instant while retaining the original conflict history.

### Completion, no-show, and Visit creation

- Staff may mark an appointment completed or no-show only after its scheduled end.
- Completion requires a staff-authored nonblank clinical description. The system creates exactly one `Visit` dated by the appointment's clinic-local date with that description.
- AI text, owner text, and AI summary are never copied automatically into the Visit description.
- No-show creates no Visit. `NO_SHOW` may later be corrected to `COMPLETED`, at which point the required description creates the Visit.
- `COMPLETED` and `CANCELLED` are irreversible. The generated Visit is not automatically deleted or reversed.

### Legacy behavior

- Existing date-only Visits remain historical records and do not block timed availability, including when their stored date is in the future.
- The existing “new visit” form and route no longer act as a booking path.
- Manual entry of standalone historical Visits is not added by this feature.

## Explicit assumptions

- Appointment intervals are half-open `[start, end)`.
- Staff cancellation before scheduled end covers clinic-initiated cancellation after start; after end the record must become completed or no-show.
- A direct appointment without a request uses booking source `STAFF_DIRECT`.
- Conflict flags caused by later calendar edits remain visible until a successful staff reschedule or cancellation records their resolution.

## Handled edge cases

- A second completion submission resolves to the existing Visit rather than creating another one.
- An owner cancellation racing with staff reschedule or another cancellation commits at most one valid transition.
- A deactivated veterinarian cannot receive a new or rescheduled appointment but remains attached to existing appointments.
- An unlinked direct booking uses the staff horizon and may therefore be valid beyond the configured owner horizon.
- A late-arriving pet may be corrected from no-show to completed; a completed Visit is never silently removed.
- A direct booking attempt while the pet has a nonterminal request requires staff to resolve that request first.

## Behaviors to verify

- UC6-B1: The system stores future bookings as Appointments separate from legacy Visits.
- UC6-B2: The system permits an owner to view that owner's upcoming `BOOKED` appointments.
- UC6-B3: The system permits staff to view all appointments on the full clinic calendar.
- UC6-B4: The system permits multiple future appointments for one owner or pet only when they do not overlap.
- UC6-B5: The system permits an owner to cancel an owned `BOOKED` appointment before its start instant.
- UC6-B6: The system prevents owner cancellation at or after the appointment start instant.
- UC6-B7: The system atomically frees veterinarian, owner, and pet time after appointment cancellation.
- UC6-B8: The system leaves the linked scheduling request terminal after appointment cancellation.
- UC6-B9: The system requires a new scheduling request when an owner wants to rebook a cancelled appointment.
- UC6-B10: The system permits staff to create an unlinked direct appointment after recording a nonblank reason.
- UC6-B11: The system prevents staff direct booking in the past or beyond the configured staff horizon.
- UC6-B12: The system applies every hard availability and overlap constraint to staff direct booking.
- UC6-B13: The system prevents unrelated direct booking for a pet with an unresolved nonterminal request.
- UC6-B14: The system permits staff to reschedule a `BOOKED` appointment to a future feasible interval with a nonblank reason.
- UC6-B15: The system retains the appointment identity and history after rescheduling.
- UC6-B16: The system prevents rescheduling a terminal appointment.
- UC6-B17: The system permits staff to cancel a `BOOKED` appointment before its scheduled end with a nonblank reason.
- UC6-B18: The system prevents staff cancellation after the appointment's scheduled end.
- UC6-B19: The system permits completion only after the appointment's scheduled end.
- UC6-B20: The system requires a staff-authored nonblank description for completion.
- UC6-B21: The system creates exactly one Visit when an appointment becomes `COMPLETED`.
- UC6-B22: The system dates the generated Visit using the appointment's clinic-local date.
- UC6-B23: The system uses only the staff-authored completion description as the generated Visit description.
- UC6-B24: The system permits no-show only after the appointment's scheduled end.
- UC6-B25: The system creates no Visit when an appointment becomes `NO_SHOW`.
- UC6-B26: The system permits `NO_SHOW` to transition to `COMPLETED` after a completion description is supplied.
- UC6-B27: The system prevents reversal of `COMPLETED` or `CANCELLED` appointment status.
- UC6-B28: The system preserves a deactivated veterinarian on existing appointments while preventing new assignment.
- UC6-B29: The system preserves existing date-only Visits as historical records.
- UC6-B30: The system excludes legacy Visits from timed overlap calculations.
- UC6-B31: The system removes the legacy “new visit” route as a booking mechanism.
- UC6-B32: The system returns the current appointment state after a stale or concurrent lifecycle action instead of applying a second transition.
- UC6-B33: The system rejects an unlinked direct booking unless its clinical routing is `GENERAL` without a specialty or `SPECIALTY` with one active required specialty.
- UC6-B34: The system persists the care type and optional required specialty on every Appointment.
- UC6-B35: The system reuses an Appointment's stored care type and required specialty during rescheduling.
- UC6-B36: The system marks an appointment's unresolved calendar-conflict records resolved as `RESCHEDULED` after successful rescheduling.
- UC6-B37: The system marks an appointment's unresolved calendar-conflict records resolved as `CANCELLED` after successful cancellation.

## Out of scope

- Owner-initiated rescheduling
- Automatic reopening of a scheduling request
- Automatic reversal or deletion of completed Visits
- Manual standalone historical-Visit entry
- Changing an Appointment's care type or required specialty through rescheduling
- Recurring, group, or waitlisted appointments
