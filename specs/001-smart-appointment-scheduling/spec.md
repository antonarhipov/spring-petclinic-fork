# Feature Specification: Smart Appointment Scheduling

**Feature Branch**: `appointment-scheduling-speckit`

**Created**: 2026-08-28

**Status**: Draft

**Input**: User description: Smart appointment scheduling, refined through the
recorded design-grilling decisions.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Requesting and Accepting a Guided Appointment (Priority: P1)

An authenticated owner selects one of their pets, describes the reason for a
visit and availability in plain English, gives explicit permission for
interpretation, reviews the resulting structured request, and receives one
matching appointment offer at a time. The owner accepts a still-active offer to
confirm the appointment without seeing the full clinic calendar.

**Why this priority**: This is the primary owner outcome: arranging care
without manually searching a clinic calendar.

**Independent Test**: An owner can submit a valid request for their pet, confirm
the interpretation, accept a valid held offer, and see the resulting upcoming
appointment.

**Acceptance Scenarios**:

1. **Given** an owner has a pet and an eligible available slot exists, **When**
   they submit consented free text and confirm the interpreted request, **Then**
   they receive exactly one held offer with its date, time, duration, and
   veterinarian name and specialty.
2. **Given** an owner has a current offer, **When** they accept it before its
   hold expires, **Then** the system creates a confirmed appointment and shows
   it in the owner's upcoming appointments.
3. **Given** an owner has a current offer, **When** the hold has expired,
   **Then** acceptance does not create an appointment and the owner can request
   another option.

---

### User Story 2 - Managing Owner Requests and Appointments (Priority: P2)

An authenticated owner can reject an unsuitable offer, request the next option,
revise or withdraw an unscheduled request, and cancel their own upcoming
appointment before it starts. The owner can review their own request,
appointment, and completed-visit history without accessing staff notes or
other owners' data.

**Why this priority**: Owners need control over a guided flow when an initial
offer is unsuitable or their plans change.

**Independent Test**: An owner rejects an offer, receives a different eligible
offer, and cancels a future appointment without exposing another owner's
records.

**Acceptance Scenarios**:

1. **Given** an owner rejects an offer, **When** they request another option,
   **Then** the rejected veterinarian-and-time combination is never offered
   again for that request revision.
2. **Given** an owner revises confirmed availability or duration, **When** they
   reconfirm the revision, **Then** the existing hold is released and future
   matching uses the revised constraints.
3. **Given** an owner has an upcoming appointment, **When** they cancel before
   its start time, **Then** the appointment is cancelled and the original
   request is not automatically reopened.

---

### User Story 3 - Handling Exceptions Through Staff (Priority: P2)

Staff can work a queue for requests that cannot safely be scheduled
automatically, including declined consent, unavailable automation, missing
specialty coverage, no match, and suspected emergencies. Staff can review or
complete the interpretation, offer a held slot for owner acceptance, or book on
an owner's behalf after recording agreement.

**Why this priority**: The clinic must not leave an owner at a dead end when
automation cannot proceed.

**Independent Test**: A request with no automatic match enters the staff queue;
a staff member claims it, creates an owner-facing offer or direct booking, and
the request history records the action.

**Acceptance Scenarios**:

1. **Given** an owner declines permission for automated interpretation, **When**
   they submit the request, **Then** it enters the staff queue and the original
   text is not submitted for automated interpretation.
2. **Given** an automated request has no eligible match, **When** matching
   completes, **Then** the owner sees a plain status and staff can claim the
   queued request.
3. **Given** staff have owner agreement, **When** they select a valid slot,
   **Then** they may create a confirmed appointment directly and the action is
   recorded.

---

### User Story 4 - Managing the Clinic Calendar (Priority: P3)

Staff configure clinic scheduling settings and each veterinarian's recurring
split-shift availability, date-specific exceptions, leave, and clinic
closures. They directly schedule, reschedule, cancel, complete, and mark
appointments as no-show while preserving an auditable calendar.

**Why this priority**: Automated offers are useful only when the calendar
accurately reflects how the clinic operates.

**Independent Test**: Staff configure a veterinarian's availability, make an
appointment available for guided scheduling, and manage that appointment
through completion.

**Acceptance Scenarios**:

1. **Given** a veterinarian has configured availability, **When** staff add
   leave that conflicts with a confirmed appointment, **Then** the change is
   blocked until staff deliberately reschedule or cancel the appointment.
2. **Given** an appointment has ended, **When** staff mark it completed,
   **Then** one linked visit-history entry is recorded.
3. **Given** staff mark an appointment as no-show, **When** the status is
   saved, **Then** the owner sees only the no-show status and no visit-history
   entry is created.

### Edge Cases

- A suspected emergency always shows urgent-care guidance and enters the
  highest-priority staff queue path; it is never delayed by automated booking.
- If interpretation or matching does not complete within the allowed service
  time, the submitted request is preserved and routed to staff.
- If no veterinarian has the required specialty, the request enters staff
  review rather than reporting a terminal no-availability result.
- If an owner rejects or lets five offers expire, the request enters staff
  review; rejected or expired slots remain excluded for that revision.
- If two people attempt to accept or book the same held slot, at most one
  appointment is confirmed.
- If staff need to change availability that conflicts with a current hold, they
  must deliberately release the hold with a recorded reason before the change.
- If a request contains ambiguous timing, contradictory constraints, or
  uncertain specialty or urgency, staff resolve it before automated offers.
- If an owner withdraws an unscheduled request, its hold is released, it leaves
  active queue work, and its closed history remains available to staff.
- Legacy future-dated visit records do not reserve appointment capacity because
  they lack a time and assigned veterinarian.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST require authentication for all operational pages
  and provide an owner role and a staff role.
- **FR-002**: The system MUST ensure an owner can access only their own profile,
  pets, requests, offers, appointments, and completed-visit history.
- **FR-003**: The system MUST permit staff to manage all owner, pet, request,
  appointment, queue, availability, and clinic-setting records within the POC.
- **FR-004**: Local, demo, and test environments MUST create accounts for the
  existing seed owners using lowercase first-name usernames and
  `<username>123` passwords, plus the staff account `admin/admin123`; these
  existing seed-owner accounts MUST NOT require an initial password change.
- **FR-005**: Deployed environments MUST NOT create predictable seed
  credentials and MUST obtain their initial staff credentials from private
  runtime configuration.
- **FR-006**: Staff MUST be able to provision an owner account with a unique
  username and a one-time password that expires after seven days; first use and
  password resets MUST require a password change.
- **FR-007**: New account passwords MUST contain at least six characters.
- **FR-008**: The system MUST limit repeated failed sign-in attempts, end idle
  sessions after 30 minutes, invalidate owner sessions after password reset,
  and establish a new session at sign-in and password change.
- **FR-009**: Owners MUST be able to select exactly one pet they own and submit
  a plain-text request of 10 to 2,000 characters; attachments, markup, and
  additional free-form fields are out of scope.
- **FR-010**: The system MUST display clinic urgent-care guidance at all times
  on the request form and allow staff to maintain that guidance with safe
  default content.
- **FR-011**: The system MUST ask for explicit consent before sending an
  owner's text revision for automated interpretation and record the consent
  decision with its timestamp and interpretation identifier.
- **FR-012**: If an owner declines consent, the system MUST route the request
  to staff without submitting its text to automated interpretation.
- **FR-013**: The system MUST derive a structured interpretation containing
  visit reason, requested duration, care type, required specialty when
  applicable, allowed/preferred/excluded time windows, preferred veterinarian
  when applicable, and urgency indication.
- **FR-014**: Owners MUST be able to edit visit reason, availability,
  preferred veterinarian, and duration before confirming an interpretation.
  Changes to care type, specialty, or urgency MUST require staff review.
- **FR-015**: Editing the original free text MUST require renewed consent and a
  fresh interpretation. Editing structured fields without changing the text
  MUST require reconfirmation but not a new interpretation.
- **FR-016**: The system MUST retain original text, consent record,
  interpretation result, confirmed interpretation, and request history while
  the associated owner and pet records exist.
- **FR-017**: The system MUST support English request text and resolve relative
  dates using the clinic time zone; it MUST show resolved dates before owner
  confirmation.
- **FR-018**: The system MUST send safety-critical uncertainty, unresolved
  timing, missing required fields, contradictory constraints, and values
  outside clinic configuration to staff review before making automated offers.
- **FR-019**: The system MUST screen submitted text for configured
  emergency-indicating terms, record the result, and only use that result to
  raise priority; it MUST never assure an owner that a condition is non-urgent.
- **FR-020**: The system MUST preserve an initial successful interpretation as
  the authoritative result. Staff may correct it manually or require an owner
  revision and new consent; automatic retry is permitted only after no result
  was produced.
- **FR-021**: The system MUST present a confirmed request in explicit
  owner-facing states for interpretation review, ready for suggestion, offer
  held, staff handling, confirmed, and closed.
- **FR-022**: An owner MUST be able to withdraw an unscheduled request; the
  system MUST release any hold, remove it from active staff work, preserve it
  as closed history, and require a new request to resume scheduling.
- **FR-023**: The selected pet MUST remain immutable after request submission.
- **FR-024**: The system MUST allow at most one active scheduling request per
  pet while allowing multiple non-overlapping future appointments for that pet.
- **FR-025**: The system MUST show an owner's existing upcoming appointments
  before they submit another request for that pet.
- **FR-026**: The system MUST offer at most one suitable veterinarian-and-time
  slot at a time and MUST NOT expose a complete veterinarian availability
  calendar to owners.
- **FR-027**: A suggested slot MUST satisfy all confirmed hard constraints:
  clinic and veterinarian availability, closures and leave, booking horizon,
  minimum notice, duration, veterinarian and pet non-overlap, owner allowed
  windows, and prior offer exclusions.
- **FR-028**: The system MUST treat explicit unavailable times and confirmed
  allowed windows as hard constraints; preferred times and veterinarians are
  soft preferences, and "if necessary" availability is a lower-ranked
  acceptable fallback.
- **FR-029**: The system MUST rank eligible slots by owner preferences, then
  earliest suitable time, then a stable veterinarian/time order. Calendar
  efficiency may only break an otherwise equal result.
- **FR-030**: Each offered slot MUST be held for the configured hold duration,
  display its exact local expiry, and be unavailable to all other owners and
  staff except staff acting for the holder.
- **FR-031**: Accepting an offer MUST atomically verify that its hold is active
  and create a confirmed appointment; an expired or unavailable offer MUST NOT
  create an appointment.
- **FR-032**: Rejecting an offer MUST release its hold and permanently exclude
  that exact veterinarian-and-time combination from future offers for the
  current request revision. Owners may optionally record a short rejection
  reason for staff context.
- **FR-033**: Expiring an offer MUST release it, return the request to the
  next-suggestion state, and exclude it from automatic re-offer for that
  request revision.
- **FR-034**: After five rejected or expired offers, the system MUST route the
  request to staff review.
- **FR-035**: Revising a confirmed request MUST release its current hold,
  preserve prior revision history, and begin a new revision with its own offer
  exclusions and rejection count.
- **FR-036**: If no automatic match is available, the system MUST allow the
  owner to revise and reconfirm availability and MUST create a staff-queue item.
- **FR-037**: The system MUST not create unsolicited offers when new capacity
  appears. New capacity is considered only after an owner asks again or staff
  actively work a request.
- **FR-038**: Owners MUST be able to cancel their own upcoming appointments
  until appointment start, with an optional cancellation reason; cancellation
  MUST NOT reopen the original request.
- **FR-039**: The system MUST treat appointments as the sole mechanism for
  scheduling future care. Legacy visits remain history and do not reserve
  capacity.
- **FR-040**: The system MUST route a request to staff when consent is declined,
  interpretation or matching is unavailable or times out, no matching specialty
  exists, no eligible match exists, five offers are exhausted, or safety-
  critical uncertainty prevents automated scheduling.
- **FR-041**: Staff queue items MUST support `NEW`, `IN_REVIEW`,
  `AWAITING_OWNER`, `RESOLVED`, and `CLOSED` states; suspected emergencies sort
  before all other items, followed by oldest request.
- **FR-042**: Staff MUST claim an item before working it. Any staff member MUST
  be able to explicitly reassign or unclaim it with a recorded reason.
- **FR-043**: Staff MUST be able to offer a held slot for owner acceptance or
  directly confirm a valid appointment after recording owner agreement. Direct
  booking without agreement is limited to staff-initiated operational
  appointments.
- **FR-044**: Each appointment MUST reserve exactly one veterinarian for its
  full duration, prevent overlap with another appointment for that veterinarian
  or pet, and require a matching specialty for specialty care.
- **FR-045**: General-care requests MAY use any available veterinarian.
- **FR-046**: Staff MUST be able to directly book, reschedule, and cancel
  appointments using normal availability and conflict checks. Each action MUST
  record a selected reason category and may include a concise note.
- **FR-047**: Staff MUST NOT override calendar conflicts or book outside
  configured availability; they must first record a valid availability
  exception where appropriate.
- **FR-048**: Staff MUST be able to configure a clinic-wide 1-365-day booking
  horizon, 1-60-minute offer hold, 15/30/45/60-minute duration choices, named
  non-overlapping time periods within one local day, and a fixed 15-minute
  scheduling grid.
- **FR-049**: A newly configured clinic MUST default to weekday 09:00-17:00
  local clinic hours, a 90-day horizon, a 10-minute hold, and no veterinarian
  shifts until staff configure them.
- **FR-050**: Staff MUST be able to configure non-overlapping same-day
  recurring split shifts, date-specific exceptions, leave, and full-day clinic
  closures. Overnight shifts and partial-day closures are out of scope.
- **FR-051**: Availability precedence MUST be clinic closure, veterinarian
  leave, date-specific exception, then recurring shift.
- **FR-052**: The system MUST block any availability change that conflicts with
  a confirmed appointment until staff reschedule or cancel that appointment.
  It MUST block a conflicting change against a hold until staff deliberately
  release that hold with a recorded reason.
- **FR-053**: The system MUST snapshot concrete time windows from named periods
  when an owner confirms a request; later period changes affect only new or
  revised requests.
- **FR-054**: The clinic MUST use one configured time zone, defaulting to
  Europe/Amsterdam. Staff may change it only before the first request or
  appointment exists.
- **FR-055**: Staff MUST be able to mark an appointment completed or no-show
  after its scheduled end. Completion creates one linked visit-history entry;
  no-show requires a reason, may include a note, and creates no visit entry.
- **FR-056**: Staff MUST be able to make an audited correction to completed or
  no-show status and its linked visit entry. Owners see a no-show status but
  not its internal reason or note.
- **FR-057**: The system MUST retain an audit history for staff changes, queue
  claims, request revisions, consent decisions, interpretation and matching
  outcomes, offers and holds, acceptances and rejections, and appointment
  lifecycle events. Each record MUST identify the actor, time, action, target,
  and relevant before/after values.
- **FR-058**: Owners MUST see only their submitted information, ordinary
  request and offer history, appointment details, and veterinarian names and
  specialties. Staff notes, audit reasons, matching details, and calendar
  availability MUST remain staff-only.

### Key Entities

- **Account**: A staff or owner sign-in identity linked to an owner where
  applicable, with account status, password-change requirement, and session
  security state.
- **Scheduling Request**: An owner's request for one immutable pet, including
  original text, consent, current state, urgency priority, current revision,
  and staff-queue relationship.
- **Request Interpretation and Revision**: The confirmed structured scheduling
  information and the historical record of changes, consent, uncertainty, and
  resolved local time windows.
- **Offer Hold**: The single proposed veterinarian/time/duration combination,
  local expiry, outcome, and exclusion history for a request revision.
- **Appointment**: A confirmed or lifecycle-managed booking for one pet and
  veterinarian, with scheduled time, duration, origin, status, and change
  history.
- **Visit History Entry**: The completed-care record linked to a completed
  appointment, including completion date, veterinarian, and clinical notes.
- **Veterinarian Availability**: Recurring shifts, date-specific exceptions,
  leave, and specialty eligibility used to determine valid appointment times.
- **Clinic Scheduling Settings**: The clinic time zone, horizon, minimum
  notice, duration choices, hold duration, named periods, and closure dates.
- **Staff Queue Item**: Staff-owned work for a request that needs manual
  handling, with priority, state, assignee, and history.
- **Audit Record**: An immutable record of a relevant actor action, time,
  affected record, and prior and resulting values.

### Scope Boundaries

- In scope: authenticated owner guided scheduling, staff fallback and calendar
  management, appointment lifecycle management, owner cancellation, consented
  interpretation, and emergency prioritization.
- Out of scope: owner self-registration and self-service password recovery;
  external notifications; owner profile or pet editing; staff-account,
  veterinarian, and specialty catalog administration; waitlists; multiple
  clinics; attachments; overnight shifts; partial-day closures; rooms,
  equipment, assistants, buffers, daily caps, travel time, and automatic
  movement of confirmed appointments.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An owner with a complete eligible request either sees a structured
  interpretation within 10 seconds or sees a staff-handling status with their
  submitted request preserved.
- **SC-002**: An owner who confirms an eligible request sees one suitable offer
  within 5 seconds of requesting an option, or sees staff-handling status
  without losing the request.
- **SC-003**: In concurrent acceptance and direct-booking tests for the same
  offered slot, exactly one confirmed appointment is created in 100% of runs.
- **SC-004**: In acceptance tests, 100% of automatically offered slots satisfy
  the confirmed hard constraints and never overlap the selected veterinarian or
  pet.
- **SC-005**: In authorization tests, owners cannot view or change another
  owner's records in 100% of attempted cross-owner scenarios.
- **SC-006**: In acceptance tests, every automated failure, no-match result,
  missing-specialty result, declined-consent request, and safety-critical
  uncertainty produces a staff-queue item rather than a terminal dead end.
- **SC-007**: In acceptance tests, an owner sees no more than one current offer
  and no complete veterinarian availability calendar.
- **SC-008**: In lifecycle tests, each completed appointment produces exactly
  one linked visit-history entry, while cancelled and no-show appointments
  produce none.
- **SC-009**: In configuration tests, every conflicting availability change is
  blocked until affected confirmed appointments are resolved and affected holds
  are deliberately released.

## Assumptions

- This is a single-clinic POC operating in one local time zone; the default is
  Europe/Amsterdam.
- Staff configure veterinarian shifts before automated offers can be made.
- Owners communicate with staff through the clinic outside this feature when
  staff-assisted action requires it; the POC sends no external notifications.
- Staff provide new owners with one-time credentials through an approved
  out-of-band channel.
- Existing veterinarian specialty assignments are sufficient for specialty
  eligibility in the POC.
- Supported local and persistent deployment profiles preserve the same
  scheduling, privacy, and conflict-prevention behavior.
