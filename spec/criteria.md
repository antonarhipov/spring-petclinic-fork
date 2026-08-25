# Acceptance Criteria: Smart Appointment Scheduling

Pipeline position: proposal → spec → **criteria** → rules → review → plan
Source: `spec/spec.md` ("Behaviors to verify" B-1..B-45 is the contract). Spec precedes proposal on conflict.

## Functional

### AC-1: Authentication required with form login
**Covers:** B-1

The system shall present a form-based login and establish an authenticated session on valid credentials.

### AC-2: Unauthenticated access to protected routes is refused
**Covers:** B-1

If an unauthenticated request targets a non-public route, then the system shall redirect to the login page and shall not render the protected content.

### AC-3: Forced password change on first login
**Covers:** B-2

While the authenticated user has `must_change_password` set, when the user requests any route other than logout or the change-password page, the system shall redirect to the change-password page.

### AC-4: Staff create accounts
**Covers:** B-3

When a staff user creates an owner or staff account, the system shall persist a user account with the staff-assigned username, a temporary password, and `must_change_password` set to true.

### AC-5: Staff reset passwords
**Covers:** B-4

When a staff user resets a user's password, the system shall replace the stored password with a new temporary password and set `must_change_password` to true.

### AC-6: Initial staff account bootstrapped at startup
**Covers:** B-5

When the application starts with the configured bootstrap staff properties present, the system shall create or reconcile a staff account matching those properties.

### AC-7: Staff-only zones refuse owners
**Covers:** B-6

If a user without `ROLE_STAFF` requests a `/staff/**` or owner/pet CRUD (`/owners/**`) route, then the system shall respond with an authorization failure and shall not perform the requested action.

### AC-8: Owner updates own profile
**Covers:** B-7

When an owner submits an update to their own profile, the system shall persist changes to firstName, lastName, address, city, and telephone only.

### AC-9: Owner cannot access another owner's profile
**Covers:** B-7

If an owner requests to view or modify a profile that is not their own, then the system shall respond with an authorization failure and shall not disclose or modify that profile.

### AC-10: Owner manages own pets
**Covers:** B-8

When an owner adds, edits, or deletes a pet they own, the system shall apply the change to that pet.

### AC-11: Owner cannot act on pets they do not own
**Covers:** B-8

If an owner requests to view or modify a pet they do not own, then the system shall respond with an authorization failure and shall not disclose or modify that pet.

### AC-12: Urgent-care guidance always shown
**Covers:** B-9

While the scheduling request form is displayed, the system shall show urgent-care guidance regardless of request content.

### AC-13: Consent required before LLM use
**Covers:** B-10

If free text would be sent to the LLM without recorded explicit consent for that request, then the system shall not send the text to the LLM.

### AC-14: Declining consent routes to staff queue
**Covers:** B-11

When an owner declines consent, the system shall transition the request to `STAFF_QUEUED` with reason `CONSENT_DECLINED` and shall not send any text to the LLM.

### AC-15: Asynchronous structured interpretation
**Covers:** B-12

While a request is in `INTERPRETING`, the system shall produce a structured interpretation containing visit duration, care type with optional specialty, preferred/allowed/excluded windows, optional preferred vet, urgency, and a summary.

### AC-16: Auto-refreshing status page advances on state change
**Covers:** B-13

While a request is in `INTERPRETING` or `SUGGESTING`, the system shall serve an auto-refreshing status page that navigates to the next screen when the request state changes.

### AC-17: Interpretation failure routes to staff queue
**Covers:** B-14

If interpretation errors, times out, or no `ChatModel` bean is configured, then the system shall transition the request to `STAFF_QUEUED` with reason `AI_UNAVAILABLE`.

### AC-18: Interpretation presented for confirmation
**Covers:** B-15

While a request is in `AWAITING_CONFIRMATION`, the system shall present the structured interpretation to the owner for review before any slot is suggested.

### AC-19: Editing free text resets to DRAFT
**Covers:** B-16

While a request is in `AWAITING_CONFIRMATION`, when the owner edits the free text, the system shall transition the request to `DRAFT` and require fresh consent and interpretation.

### AC-20: Interpreted duration within bounds is used as-is
**Covers:** B-17

When the interpreted visit duration is strictly between the clinic minimum and maximum, the system shall schedule using that duration.

### AC-21: Interpreted duration at a bound is used as-is
**Covers:** B-17

When the interpreted visit duration equals the clinic minimum or the clinic maximum, the system shall schedule using that duration.

### AC-22: Interpreted duration outside bounds is clamped
**Covers:** B-17

If the interpreted visit duration is below the clinic minimum or above the clinic maximum, then the system shall clamp it to the nearest bound.

### AC-23: Unspecified duration falls back to default
**Covers:** B-17

If the interpretation specifies no visit duration, then the system shall use the clinic default duration.

### AC-24: Symbolic windows resolved to concrete ranges
**Covers:** B-18

When resolving an interpretation, the system shall convert symbolic windows (day-of-week and/or date plus named part-of-day) into concrete time ranges using `PartOfDay` settings and the booking horizon.

### AC-25: Raw text and interpretation persisted durably
**Covers:** B-44

The system shall persist the raw free text and the structured interpretation on the request such that they survive an application restart.

### AC-26: One suggestion at a time via single-request solve
**Covers:** B-19

While a request is in `SUGGESTING`, the system shall produce at most one suggested slot per solve, computed against confirmed appointments, active holds, and resolved vet availability.

### AC-27: Only hard-valid slots are offered
**Covers:** B-20

The system shall offer only slots that fall within resolved vet availability, do not overlap that vet's confirmed appointments or active holds, and fit the required duration in one contiguous block.

### AC-28: Slot within booking horizon is eligible
**Covers:** B-20

When a candidate slot starts before the end of the booking horizon, the system shall treat it as eligible on the horizon dimension.

### AC-29: Slot on the last horizon day is eligible
**Covers:** B-20

When a candidate slot starts on the last day within the booking horizon, the system shall treat it as eligible on the horizon dimension.

### AC-30: Slot beyond booking horizon is not offered
**Covers:** B-20

If a candidate slot starts after the booking horizon, then the system shall not offer it.

### AC-31: Slot in the past is not offered
**Covers:** B-20

If a candidate slot starts before the current time in the clinic time zone, then the system shall not offer it.

### AC-32: Specialty requirement enforced
**Covers:** B-20

If the interpretation requires specialty care, then the system shall offer only slots with a vet holding the required specialty.

### AC-33: Excluded and previously-rejected slots are not offered
**Covers:** B-20

If a candidate slot falls in an excluded window or matches a previously-rejected `(vet_id, start_time)` for the request, then the system shall not offer it.

### AC-34: Suggestions ordered by soft constraints
**Covers:** B-21

When more than one eligible slot exists, the system shall prefer slots in preferred windows over allowed windows, prefer the preferred vet, prefer earlier slots weighted by urgency, and balance load across vets.

### AC-35: Urgent requests weight earlier slots higher
**Covers:** B-32

While an interpretation is `URGENT`, the system shall increase the weight of the earlier-is-better preference and keep the request in the automated flow.

### AC-36: Slot held on offer
**Covers:** B-22

When the system offers a slot, the system shall place a hold on that slot for the configured hold duration and display a countdown.

### AC-37: Owner sees only the current suggestion details
**Covers:** B-22

While a request is in `SLOT_HELD`, the system shall expose only the offered vet name, specialty, date, start time, and duration, and shall not expose the full calendar.

### AC-38: Action before hold expiry is honored
**Covers:** B-22

While a hold has not reached its expiry, when the owner accepts or rejects, the system shall process the action against the held slot.

### AC-39: Action at or after hold expiry is treated as expired
**Covers:** B-22

If the owner acts on a hold at or after its expiry time, then the system shall treat the hold as expired rather than processing it against the held slot.

### AC-40: No two concurrent holds or bookings on one slot
**Covers:** B-23

If a hold or booking is requested for a `(vet_id, start_time)` that already has an active hold or confirmed appointment, then the system shall reject the second and shall not create a duplicate hold or booking.

### AC-41: Accepting confirms the appointment
**Covers:** B-24

While a request is in `SLOT_HELD`, when the owner accepts, the system shall transition the request to `CONFIRMED` and create a `BOOKED` appointment for the held slot.

### AC-42: Rejecting excludes the slot and re-solves
**Covers:** B-25

While a request is in `SLOT_HELD`, when the owner rejects or asks again, the system shall release the hold, exclude the exact `(vet_id, start_time)` for the request, and transition to `SUGGESTING`.

### AC-43: Expired hold releases without exclusion
**Covers:** B-26

If a hold expires without an owner decision, then the system shall release the hold, leave the slot un-excluded, and return the request to `SUGGESTING`.

### AC-44: Abandoned holds reclaimed
**Covers:** B-27

The system shall reclaim expired holds via a scheduled sweeper and shall invalidate an expired hold on the next owner action.

### AC-45: No specialty vet routes to staff queue
**Covers:** B-28

If specialty care is required but no vet holds that specialty, then the system shall transition the request to `STAFF_QUEUED` with reason `NO_SPECIALTY_VET`.

### AC-46: Exhausted suggestions route to staff queue
**Covers:** B-29

If a solve is infeasible given the accumulated exclusions, then the system shall transition the request to `STAFF_QUEUED` with reason `SUGGESTIONS_EXHAUSTED`.

### AC-47: Solver failure routes to staff queue
**Covers:** B-30

If the solver errors or is unavailable, then the system shall transition the request to `STAFF_QUEUED` with reason `SOLVER_UNAVAILABLE`.

### AC-48: Emergency short-circuits to staff queue
**Covers:** B-31

If the interpretation is `EMERGENCY`, then the system shall skip the solver and transition the request to `STAFF_QUEUED` with reason `EMERGENCY` at top priority.

### AC-49: Staff queue ordered emergency-first then FIFO
**Covers:** B-33

While staff view the fallback queue, the system shall order entries emergency-first then by queued time ascending, showing each entry's queue reason and queued time.

### AC-50: Staff complete interpretation and book on behalf
**Covers:** B-34

When a staff user completes a queued request's interpretation and selects a slot, the system shall create a `BOOKED` appointment on the owner's behalf.

### AC-51: Staff direct booking
**Covers:** B-35

When a staff user books a slot directly, the system shall create a `BOOKED` appointment with no originating scheduling request.

### AC-52: Staff reschedule re-validates and records reason
**Covers:** B-35

When a staff user reschedules an appointment, the system shall validate the new time against vet availability and overlap and record a change reason.

### AC-53: Staff cancel records reason
**Covers:** B-35

When a staff user cancels an appointment, the system shall set its status to `CANCELLED` and record a change reason.

### AC-54: Completion spawns a visit
**Covers:** B-36

While an appointment is `BOOKED`, when a staff user marks it `COMPLETED`, the system shall set its status to `COMPLETED` and create a `Visit` in the pet's history.

### AC-55: No-show records no visit
**Covers:** B-37

While an appointment is `BOOKED`, when a staff user marks it `NO_SHOW`, the system shall set its status to `NO_SHOW` and shall not create a `Visit`.

### AC-56: Owner cancels own upcoming appointment before start
**Covers:** B-38

While an owner's appointment is `BOOKED` and its start time is in the future, when the owner cancels it, the system shall set its status to `CANCELLED` and free the slot.

### AC-57: Owner cannot cancel an appointment at or after its start
**Covers:** B-38

If an owner attempts to cancel an appointment at or after its start time, then the system shall not present it as an upcoming appointment and shall not cancel it.

### AC-58: Owner cannot access others' appointments
**Covers:** B-39

If an owner requests to view or cancel an appointment that is not their own, then the system shall respond with an authorization failure and shall not disclose or modify it.

### AC-59: Second active request per pet is blocked
**Covers:** B-40

If an owner starts a scheduling request for a pet that already has a non-terminal request, then the system shall refuse the new request and surface the existing one.

### AC-60: Concurrent requests across different pets allowed
**Covers:** B-40

When an owner starts scheduling requests for distinct pets they own, the system shall allow one active request per pet concurrently.

### AC-61: Availability computed from shifts minus exceptions and closures
**Covers:** B-41

When computing a vet's bookable time on a date, the system shall take that day's weekly shifts, subtract date exceptions and leave, subtract clinic closures, and interpret all times in the clinic time zone.

### AC-62: Staff configure scheduling parameters and availability
**Covers:** B-42

When a staff user edits clinic settings, parts-of-day, vet weekly shifts, vet date exceptions or leave, or clinic closures, the system shall persist those changes.

### AC-63: Default clinic settings applied out of the box
**Covers:** B-43

Where no clinic settings have been customized, the system shall apply default scheduling parameters.

### AC-64: Schema and seed managed by versioned migrations
**Covers:** B-45

When a fresh database is initialized, the system shall apply versioned migrations that create the schema and seed data, preserving the existing pre-feature seed data.

## Non-functional

### AC-65: Consent submission does not block on the LLM
**Covers:** B-12, B-13

When an owner submits consent, the system shall return the status-page response without waiting for LLM completion.

### AC-66: Solve is time-bounded
**Covers:** B-19

When a solve runs, the system shall terminate it within the configured time bound and return either a suggestion or a no-slot outcome.

### AC-67: Passwords stored non-recoverably
**Covers:** B-3, B-4, Resolved ambiguities "UserAccount ... bcrypt password"

The system shall store user passwords using a one-way hash such that the plaintext cannot be recovered from storage.

### AC-68: Ownership enforced at the handler, not only the URL
**Covers:** B-7, B-8, B-39, Resolved ambiguities "re-checks ownership"

The system shall verify resource ownership within each owner-facing handler before disclosing or modifying a request, pet, profile, or appointment.

## Coverage exclusions

- **Performance/load (throughput, p95 under concurrency):** not specified in the spec beyond async responsiveness (AC-65) and solve bounding (AC-66); no load targets to test against.
- **Accessibility (WCAG):** not in scope for this feature; app inherits existing Thymeleaf/Bootstrap UI conventions.
- **Internationalization of new screens:** existing i18n message-bundle convention applies; no new localization requirements were specified.
- **Observability/metrics/audit logging:** not specified; `changeReason` (AC-52, AC-53) is the only recorded audit field required.
- **Rate limiting / login lockout:** explicitly out of scope per spec (A-5).
