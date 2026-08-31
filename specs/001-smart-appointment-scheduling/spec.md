# Feature Specification: Smart Appointment Scheduling

**Feature Branch**: `001-smart-appointment-scheduling`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "Create smart appointment scheduling for PetClinic, incorporating the approved technical-feasibility decisions and scope boundaries."

## Clarifications

### Session 2026-08-30

- Q: When may staff book an appointment without recording fresh owner agreement? → A: Only when reconciling an already-agreed future legacy visit; every newly selected slot requires owner agreement.
- Q: May this phase be used with real owner or clinical data? → A: No. Phase one is restricted to synthetic and demonstration data.
- Q: How should a veterinarian's date-specific availability exception interact with recurring shifts for that date? → A: It replaces the complete recurring schedule for that local date with explicitly entered intervals.
- Q: What should happen when an owner rejects or lets a staff-assisted portal offer expire? → A: Release and exclude the slot, return the same assigned queue item to `IN_REVIEW`, and do not consume an automatic attempt.
- Q: When staff clears an emergency suspicion, how is the replacement urgency chosen? → A: Staff explicitly selects `ROUTINE` or `PRIORITY` and records a reason; the owner reconfirms, and only `ROUTINE` may return to automatic scheduling.

### Session 2026-08-31

- Q: Is staff claiming required before a staff member or admin can resolve a queue item? → A: No. Claiming is discarded as a mandatory barrier; any authenticated clinic staff or admin can directly confirm (direct book or create an assisted offer) or cancel/close a queue item.
- Q: At what stages can an owner cancel/withdraw their request? → A: Owners can cancel/withdraw their request at any stage before confirmation (including while awaiting interpretation, in review, or in the staff queue), releasing any held resources and closing the queue item, and can cancel confirmed future appointments.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Owner Books a Routine Appointment (Priority: P1)

An authenticated owner selects one of their pets, describes the visit reason and availability in plain English, consents to AI processing for that exact text, reviews the structured interpretation, and receives one suitable appointment offer. The owner confirms the offer without seeing the clinic's complete availability calendar.

**Why this priority**: This is the feature's primary owner outcome and validates the complete smart-scheduling journey.

**Independent Test**: With one owner, one pet, configured clinic availability, and at least one eligible veterinarian, submit and confirm a routine request. The test is complete when one appointment is booked and no ineligible slot or clinic-wide calendar is exposed.

**Acceptance Scenarios**:

1. **Given** an authenticated owner with a pet and no active request for that pet, **When** the owner submits valid text with consent, confirms the interpreted details, and an eligible slot exists, **Then** the system offers exactly one held slot.
2. **Given** an active, unexpired offer, **When** the owner confirms it, **Then** the system atomically creates one confirmed appointment and closes the scheduling request as confirmed.
3. **Given** a submitted request that is still being interpreted or matched, **When** the owner leaves and returns later, **Then** the owner sees the persisted current state and can continue without duplicate work.
4. **Given** the same confirmed request revision and the same calendar snapshot, **When** matching is evaluated repeatedly, **Then** the same highest-ranked eligible slot is selected.

---

### User Story 2 - Staff Resolves a Request Requiring Fallback (Priority: P1)

A staff member claims a request that cannot be scheduled automatically, reviews the current request revision and protected context, contacts the owner, and either creates a portal offer, directly books an agreed appointment, asks the owner to revise, or explicitly closes the request.

**Why this priority**: Manual fallback is the safety net for declined consent, clinical uncertainty, priority requests, emergencies, technical failures, and lack of an automatic match.

**Independent Test**: Submit a request without AI consent, claim it as staff, manually complete the interpretation, record owner agreement, and create a compliant appointment. The test succeeds without invoking automated interpretation.

**Acceptance Scenarios**:

1. **Given** an owner declines AI consent, **When** the request is submitted, **Then** it is persisted and placed in the staff queue without sending the declined text to AI.
2. **Given** an unclaimed fallback item, **When** one staff member claims it, **Then** other staff can view it but cannot edit it unless it is explicitly reassigned or unclaimed.
3. **Given** staff have contacted the owner and selected a compliant slot, **When** staff create an assisted offer, **Then** the slot is held for the owner and the queue item awaits owner action.
4. **Given** staff record the owner's agreement to the interpretation and exact slot, **When** staff directly book, **Then** a confirmed appointment is created and the queue item is resolved.

---

### User Story 3 - Staff Maintains Availability and Books Directly (Priority: P1)

Staff configure clinic policy, veterinarian shifts, date-specific exceptions, leave, and closures; inspect the clinic calendar; and directly create appointments on behalf of owners while obeying all availability and conflict rules.

**Why this priority**: Reliable availability is a prerequisite for both staff booking and smart owner scheduling, and direct booking provides the first independently deliverable scheduling slice.

**Independent Test**: Configure a veterinarian shift, add an exception, and directly book an appointment inside the resulting availability. Verify that a conflicting or out-of-hours booking is rejected without partial changes.

**Acceptance Scenarios**:

1. **Given** a recurring shift and no higher-precedence override, **When** staff choose a free slot within it, **Then** staff can review and commit a direct booking after recording the required reason and owner agreement.
2. **Given** a clinic closure, veterinarian leave, date exception, and recurring shift overlap the same period, **When** availability is calculated, **Then** the precedence is closure, leave, date exception, then recurring shift.
3. **Given** an availability change would invalidate a confirmed appointment or active hold, **When** staff attempt to save it, **Then** nothing is saved and all conflicts are shown with links to resolve them.
4. **Given** an existing owner, veterinarian, pet, or hold conflict, **When** staff attempt to book or reschedule, **Then** the operation fails without an override option.

---

### User Story 4 - Owner Manages an Active Request and Appointment (Priority: P2)

An owner can resume a request, correct interpreted details, revise the original text with new consent, reject or let an offer expire, ask for another option, withdraw before confirmation, and cancel a future confirmed appointment.

**Why this priority**: Real scheduling rarely completes in one uninterrupted session; safe recovery and revision prevent abandoned or incorrect bookings.

**Independent Test**: Start a request, reject one offer, revise availability, obtain and confirm a replacement, then cancel the appointment. Verify the history and state after each action.

**Acceptance Scenarios**:

1. **Given** an owner edits only confirmed structured details, **When** the revision is saved, **Then** no new AI interpretation occurs and a new workflow revision is created.
2. **Given** an owner revises original prose, **When** the new text is submitted, **Then** a new text revision and consent decision are required and stale work cannot alter it.
3. **Given** an offered slot, **When** the owner rejects it or it expires, **Then** the hold is released, that exact veterinarian/time is excluded for the current revision, and no replacement is generated until requested.
4. **Given** a pre-confirmation request, **When** the owner confirms withdrawal, **Then** active holds and queue work are released and the request remains closed in history.
5. **Given** a confirmed future appointment, **When** its owner confirms cancellation before the start time, **Then** it is cancelled without reopening the original request.

---

### User Story 5 - Staff Completes and Corrects Appointment Outcomes (Priority: P2)

After an appointment ends, staff mark it completed or no-show. Completion creates owner-visible history and protected clinical detail. Mistakes are corrected through a dedicated, audited correction rather than by silently rewriting history.

**Why this priority**: The scheduling lifecycle must end in trustworthy visit history and preserve accountability for corrections.

**Independent Test**: Complete an ended appointment, verify its linked visit record, reverse it through the correction flow, and verify both the revised history and append-only audit trail.

**Acceptance Scenarios**:

1. **Given** an appointment whose scheduled end has passed, **When** staff confirm completion with required visit information, **Then** one linked visit record is created.
2. **Given** an ended appointment, **When** staff confirm no-show with a reason, **Then** its status changes to no-show and no visit record is created.
3. **Given** a completed or no-show appointment, **When** staff submit a correction with a reason after reviewing the impact, **Then** the corrected state and any visit change are recorded together with an immutable correction event.

---

### User Story 6 - Staff Provisions Secure Owner Access (Priority: P3)

Staff create or reset an owner's account, communicate a one-time password outside the application, and the owner replaces it on first use before accessing scheduling data.

**Why this priority**: Owners need secure access, but provisioning can be delivered after the staff-only scheduling foundation.

**Independent Test**: Provision an account, sign in with the temporary password, require a password change, and verify that the expired temporary password and prior sessions cannot be reused.

**Acceptance Scenarios**:

1. **Given** an owner without an account, **When** staff choose a unique username and provision access, **Then** a random one-time password is displayed once and expires after seven days.
2. **Given** a valid temporary password, **When** the owner first signs in, **Then** the owner must set a new password before reaching operational pages.
3. **Given** staff reset an owner's password, **When** the reset completes, **Then** all existing sessions for that owner are invalidated.

### Edge Cases

- Two owners attempt to hold or confirm the same veterinarian interval concurrently; at most one hold or appointment succeeds.
- One owner attempts overlapping appointments for different pets; the second overlapping hold or booking is rejected.
- Two intervals have different start times but overlap for part of their duration; they are treated as conflicting.
- An offer expires while the owner is confirming it; the server rejects acceptance, releases the hold, and returns the current state with a valid next action.
- Availability changes after matching but before hold creation or acceptance; the stale result is discarded and the request is re-evaluated at most once against the new calendar state.
- An old interpretation or matching job finishes after the owner creates a new revision; the stale job records its outcome but cannot modify the active revision.
- The same accept, reject, withdraw, cancel, claim, or booking command is submitted twice; it produces one state transition and returns the canonical result.
- Relative dates or named periods cross a daylight-saving transition; displayed local times and stored instants remain unambiguous and the confirmed revision is not reinterpreted later.
- Allowed, preferred, and excluded windows contradict one another; confirmation is blocked until the owner or staff resolves the contradiction.
- No eligible slot exists now but capacity is added later; no unsolicited offer is created, and the owner or staff must explicitly request new matching.
- Five offers are rejected or expire for one workflow revision; the request moves to staff handling and cannot request a sixth automatic offer.
- An owner revises while staff are editing the prior revision; the staff save fails without partial changes and the queue item returns to new work.
- A staff member cannot reach the owner; the request can wait without reserving a slot and closes only through an explicit reasoned action.
- A required encryption key is unavailable; protected content is not exposed or replaced with plaintext, affected processing fails safely, and the request remains recoverable.
- A session expires during a mutating action; authentication is required again and the action is not replayed automatically.

## Requirements *(mandatory)*

### Functional Requirements

#### Access and Accounts

- **FR-001**: The system MUST require authentication for every operational screen and action; only the landing page, login page, and required static assets may be public.
- **FR-002**: The system MUST support separate `OWNER` and `STAFF` authorization roles and MUST prevent navigation or direct requests across their protected areas.
- **FR-003**: An owner MUST be able to access only their own profile, pets, scheduling requests, offers, appointments, and owner-visible visit history.
- **FR-004**: Staff MUST be able to access all clinic scheduling records needed for administration, while owner-only and staff-only fields remain separated.
- **FR-005**: Owners MUST be able to edit their own first name, last name, address, city, and telephone, but not their username, role, credentials, or pet records through profile editing.
- **FR-006**: The system MUST audit owner profile changes and immediately present current contact details to staff handling active requests.
- **FR-007**: Staff MUST be able to provision an owner account with a unique username and receive a normalized username suggestion that can be changed before creation.
- **FR-008**: New and reset owner accounts MUST receive a random one-time password of at least six characters that is displayed once, expires after seven days, and requires replacement on first use.
- **FR-009**: Password reset MUST invalidate every active session belonging to that owner.
- **FR-010**: Designated local, demonstration, and test environments MUST seed predictable owner and staff accounts for repeatable scenarios, including `admin/admin123`; predictable credentials MUST never be created in a deployed environment, which MUST require initial staff credentials and fail safely when they are absent.
- **FR-011**: The system MUST rotate the session identifier after login and password change.
- **FR-012**: Authenticated sessions MUST expire after 30 minutes of user inactivity, and background status refresh MUST NOT reset that inactivity period.
- **FR-013**: The system MUST warn an authenticated user before inactivity expiry and return them to the persisted workflow state after reauthentication without replaying an interrupted mutation.
- **FR-014**: After login, owners MUST land on their appointment dashboard and staff MUST land on the fallback queue.

#### Submission, Consent, and Revisions

- **FR-015**: The owner dashboard MUST show items needing action, other active requests, upcoming appointments, recent history, access to full history, and one primary scheduling action.
- **FR-016**: An owner MUST select one of their own pets before starting a request and MUST see that pet's existing upcoming appointments before submission.
- **FR-017**: A pet MUST have at most one active scheduling request; starting another MUST open the existing request rather than create a duplicate.
- **FR-018**: A pet MAY have multiple future appointments when they do not overlap and when the owner is not double-booked across pets.
- **FR-019**: The request form MUST show permanent urgent-care guidance, clinic contact details, the selected pet, an availability example, a visible character count, consent choices, and one explicit submit action.
- **FR-020**: Owner request text MUST be plain text between 10 and 2,000 characters; attachments and markup MUST be rejected.
- **FR-021**: Unsubmitted prose MUST NOT be retained by the service or browser storage, and leaving a changed form MUST warn the owner that the text will be lost.
- **FR-022**: The selected pet MUST become immutable after submission; changing pets requires withdrawal and a new request.
- **FR-023**: AI consent MUST be unchecked by default, apply only to the exact submitted text revision, and identify the data sent, purpose, retained records, viewers, and manual alternative.
- **FR-024**: Declining AI consent MUST still persist the request and route it to staff, and the declined text MUST NOT later be submitted to AI unless the owner creates a new revision and consents to that revision.
- **FR-025**: The system MUST persist the request and its consent decision before starting interpretation or matching work.
- **FR-026**: An owner MUST be able to edit visit reason, allowed/preferred/excluded availability, duration, and preferred veterinarian without invoking AI again.
- **FR-027**: Revising original prose MUST create a new immutable text revision, require a new consent decision, and start new interpretation only when that revision is consented.
- **FR-028**: Revising confirmed availability or duration after offers begin MUST release any active hold, preserve prior history, create a new workflow revision, reset its offer count and exclusions, and return any queue item to `NEW`.
- **FR-029**: Owner revision MUST be permitted until staff create an offer or confirmed appointment for the request; after that boundary, changes require staff assistance.
- **FR-030**: An owner MUST be able to withdraw in every pre-confirmation state after explicit confirmation; withdrawal MUST release active holds, remove active queue work, retain history, and be irreversible.
- **FR-031**: Each active request MUST expose one owner-facing primary action appropriate to the states **Interpreting request**, **Review interpretation**, **Finding an appointment**, **Appointment offered**, **With clinic staff**, **Confirmed**, and **Closed**, and MUST survive refresh, back navigation, logout, session expiry, and later login.

#### Interpretation and Safety

- **FR-032**: Automated interpretation MUST produce a structured candidate containing visit reason, allowed/preferred/excluded availability, resolved dates, duration, optional preferred veterinarian, required specialty, and urgency, using only owner prose, pet type, clinic time zone, configured scheduling choices, and public veterinarian names and specialties.
- **FR-033**: Urgency MUST have exactly three business values: `ROUTINE`, `PRIORITY`, and `EMERGENCY_SUSPECTED`; no separate care-type classification is part of this feature.
- **FR-034**: Only `ROUTINE` requests MAY proceed to automatic matching; `PRIORITY` requests MUST route to staff and `EMERGENCY_SUSPECTED` requests MUST route to the high-priority safety flow.
- **FR-035**: Before AI processing, the system MUST apply an audited emergency keyword screen that may raise urgency but can never lower it or classify a request as safe.
- **FR-036**: When either deterministic screening or AI suspects an emergency, automated scheduling MUST stop, urgent-care guidance MUST appear immediately, and a high-priority queue item MUST be created.
- **FR-037**: Urgent-care guidance MUST state that the portal and staff queue are not emergency-response channels and MUST show configured clinic phone and contact hours.
- **FR-038**: Only staff MAY clear an emergency suspicion, and only after validating the clinical interpretation, explicitly selecting `ROUTINE` or `PRIORITY`, and recording a reason. The owner MUST reconfirm the resulting interpretation; only a resulting `ROUTINE` request MAY resume automatic scheduling.
- **FR-039**: Owners MUST review the original prose and all validated structured fields before matching, with specialty and urgency displayed as read-only, plain-language, non-diagnostic labels.
- **FR-040**: The system MUST NOT send owner contact details, account data, other pets, or medical history for automated interpretation and MUST NOT show raw AI output, numeric confidence, model details, or diagnostic claims to owners.
- **FR-041**: Interpretation MUST be rejected as unsuitable for automation when required values are missing, contradictory, outside current clinic policy, clinically uncertain, or unresolved.
- **FR-042**: Relative dates MUST be resolved from the request submission instant in the clinic time zone and shown as concrete dates before owner confirmation.
- **FR-043**: The first successful interpretation for a text revision MUST remain authoritative; unchanged prose MUST NOT be reinterpreted after success, and later corrections MUST be manual or use newly revised and consented text.
- **FR-044**: Interpretation MAY retry once only when the first attempt produced no usable result; failure or expiry of the interpretation time budget MUST preserve the request and route it to staff.

#### Availability, Matching, and Offers

- **FR-045**: Confirmed availability MUST use list entries identified as allowed, preferred, or excluded, each representing a local one-off date or a bounded weekly recurrence with weekday and start/end times.
- **FR-046**: Explicit exclusions and confirmed allowed windows MUST be hard constraints; exclusions MUST subtract from allowed windows, preferences MUST fall inside allowed windows, and contradictions MUST block confirmation.
- **FR-047**: Named periods MUST resolve to concrete local time windows when a revision is confirmed and MUST remain unchanged for that revision after clinic policy changes.
- **FR-048**: Matching MUST use one fixed snapshot of the active clinic calendar and request revision and MUST NOT move or modify any confirmed appointment.
- **FR-049**: An eligible owner offer MUST fit clinic and veterinarian availability, closures, leave, exceptions, booking horizon, configured duration and start grid, required specialty, the owner's confirmed windows, the two-hour owner notice, and all active holds and appointments.
- **FR-050**: Holds and appointments MUST prevent overlap for the veterinarian, the pet, and the owner across all of that owner's pets for the full interval.
- **FR-051**: Among eligible options, matching MUST rank owner time preferences first, preferred veterinarian second, lower-ranked "if necessary" windows third, earliest suitable time fourth, clinic efficiency fifth, and a stable veterinarian/time order last.
- **FR-052**: A clinic-efficiency preference MUST NOT displace an option that better satisfies the owner's preference ranking.
- **FR-053**: General requests MAY use any available veterinarian; a specialty request MUST use a veterinarian assigned that specialty.
- **FR-054**: Automatic matching MUST finish within five seconds and MUST select the same option for the same request revision and calendar snapshot.
- **FR-055**: A detected concurrent calendar change MAY cause one re-evaluation against a fresh snapshot; other matching failures MUST route the request to staff without an owner retry control.
- **FR-056**: The system MUST present exactly one held offer at a time and MUST NOT reveal clinic-wide availability to owners.
- **FR-057**: An owner offer MUST show the pet, veterinarian and specialty, full local date, start/end time, duration, clinic time zone, non-sensitive match explanation, exact expiry time, server-derived countdown, and remaining automatic attempts.
- **FR-058**: Offer acceptance MUST atomically verify that the hold is active and the slot remains valid, then create exactly one confirmed appointment without a second confirmation dialog.
- **FR-059**: Rejecting an offer MUST require confirmation, optionally capture a staff-only reason, release the hold, and permanently exclude that veterinarian/time for the current workflow revision. Rejection MUST count toward the five-offer limit only when the offer was generated automatically.
- **FR-060**: Expired offers MUST be released and excluded like rejected offers; expiry MUST NOT automatically generate another offer and MUST present an explicit next action. Expiry MUST count toward the five-offer limit only when the offer was generated automatically.
- **FR-061**: After five rejected or expired offers for one workflow revision, the request MUST route to staff and MUST NOT produce another automatic offer for that revision.
- **FR-062**: When no eligible slot exists, the request MUST route to staff and MAY be revised by the owner; later capacity MUST NOT trigger an unsolicited offer.
- **FR-063**: Hold creation, acceptance, direct booking, and rescheduling MUST make conflict validation and state change indivisible so concurrent attempts cannot double-book a veterinarian, pet, or owner.

#### Staff Queue and Calendar

- **FR-064**: Fallback queue items MUST have separate lifecycle states `NEW`, `IN_REVIEW`, `AWAITING_OWNER`, `RESOLVED`, and `CLOSED`, independent of request, background work, offer, and appointment states; the normal assisted flow is `NEW` to `IN_REVIEW` to `AWAITING_OWNER` to `RESOLVED`. Rejection or expiry of a staff-assisted offer MUST return the same assigned item to `IN_REVIEW` without consuming an automatic offer attempt.
- **FR-065**: The queue MUST sort suspected emergencies first and all other requests oldest first, and MUST support filters by state, assignee, urgency, and fallback reason.
- **FR-066**: Queue rows MUST show urgency, request age, owner, pet, broad fallback reason, state, assignee, and last update without exposing raw internal errors.
- **FR-067**: Requests MUST remain visible to all staff but MUST be claimed before editing; claims MUST persist until explicit unclaim or reassignment.
- **FR-068**: Any staff member MUST be able to unclaim or reassign work only with an audit reason.
- **FR-069**: Queue detail MUST show the current owner contact details, pet, original prose, consent, protected interpretation, confirmed or staff-edited fields, history, ownership, audit history, and valid staff actions.
- **FR-070**: Staff saves MUST identify the request revision they are based on; a stale save MUST fail without partial changes, and owner revision MUST return the queue item to `NEW` marked as updated.
- **FR-071**: When the owner is unreachable, staff MUST be able to set `AWAITING_OWNER` without reserving a slot; the owner MUST see clinic contact guidance, and the item MUST NOT auto-close.
- **FR-072**: Staff MUST contact the owner outside the application before creating an assisted portal offer and MUST create its hold only when the owner is ready to review it.
- **FR-073**: A staff-completed interpretation MUST be confirmed by the owner before a portal offer; direct booking MUST record agreement to both the interpretation and exact appointment. Fresh agreement MAY be omitted only when converting an already-agreed future legacy visit without changing its veterinarian or scheduled interval.
- **FR-074**: Staff MUST have a week calendar, veterinarian filter, and tabular alternative showing appointments, active holds and expiries, shifts, date exceptions, leave, and closures; full owner and pet details MUST appear only after selection.
- **FR-075**: Staff MUST be able to request a ranked suggestion or manually select a slot, and both paths MUST enforce identical availability, specialty, horizon, hold, owner, pet, and veterinarian conflict rules.
- **FR-076**: Staff MUST NOT override conflicts, book outside effective availability, or displace another owner's hold; releasing a non-expired hold requires an audited confirmed action unless staff are completing the booking for that hold's owner.

#### Clinic Policy

- **FR-077**: Initial clinic policy MUST use time zone `Europe/Amsterdam`, weekday operating hours 09:00-17:00, durations of 15, 30, 45, and 60 minutes, a fixed 15-minute start grid, a 90-day booking horizon, a 10-minute hold, and two-hour owner minimum notice.
- **FR-078**: Staff MUST be able to configure a booking horizon from 1 through 365 days, a hold duration from 1 through 60 minutes, clinic contact guidance, permitted durations, and non-overlapping named periods within one local day.
- **FR-079**: The clinic time zone MUST become immutable after the first scheduling request or appointment exists, and all displayed appointment instants MUST include the clinic zone in an unambiguous format.
- **FR-080**: Staff MUST be able to configure multiple non-overlapping, same-day recurring shift intervals per veterinarian; split shifts are supported and overnight shifts are not.
- **FR-081**: Staff MUST be able to create date-specific veterinarian exceptions, veterinarian leave, and full-local-date clinic closures, with precedence of closure, leave, date exception, then recurring shift. A date-specific exception MUST replace that veterinarian's complete recurring schedule for the local date with the exception's explicitly entered intervals.
- **FR-082**: An availability change that conflicts with an appointment or active hold MUST save nothing and MUST list every blocking item with a path to reschedule, cancel, or release it through its normal audited workflow.
- **FR-083**: Staff MAY book within the owner's two-hour notice window but MUST still obey future-time, availability, specialty, owner, pet, veterinarian, and hold constraints.

#### Appointment and Visit Lifecycle

- **FR-084**: Staff direct booking and rescheduling MUST require a reason category, allow a concise note, record owner agreement when applicable, and present a review step before commit.
- **FR-085**: Staff rescheduling MUST retain appointment identity and audit the prior and new time, veterinarian, actor, timestamp, and reason.
- **FR-086**: Owners MUST be able to cancel their own booked appointment until its start time after reviewing the affected appointment; cancellation MAY capture an owner reason, MUST NOT reopen the request, and MUST offer a new-scheduling action.
- **FR-087**: Staff cancellation MUST require confirmation, an internal reason category, an owner-facing explanation, and a contact-attempt record when prior owner agreement is unavailable; owners MUST NOT see internal reasons.
- **FR-088**: Only staff MAY mark an appointment completed or no-show, only after its scheduled end, and only after a dedicated confirmation flow.
- **FR-089**: Completion MUST create exactly one linked visit record containing completion instant, assigned veterinarian, owner-visible summary, and staff-only clinical detail.
- **FR-090**: No-show MUST require a reason and MAY include a staff note; owners see only the no-show status, and cancelled or no-show appointments MUST NOT create visit records.
- **FR-091**: Reversing or correcting completion or no-show MUST use a dedicated preview-and-confirm flow, require a reason, update any linked visit consistently, and append a correction event rather than erase history.

#### Durability, Privacy, Audit, and Recovery

- **FR-092**: Interpretation and matching MUST run as durable work with states separate from the request; at most one active job of each type may exist per workflow revision, and restart or retry MUST NOT duplicate completed effects.
- **FR-093**: Completion of stale work MUST NOT change a newer workflow revision, offer, queue item, or appointment; its outcome MAY be retained for audit.
- **FR-094**: Every mutating owner, staff, and background command MUST be safe to retry and MUST return the current canonical state after duplicate, stale, expired, or concurrent submission.
- **FR-095**: The system MUST append immutable audit events containing actor, timestamp, action, target, outcome, and relevant structured before/after values for security, request, AI, matching, queue, offer, availability, appointment, and lifecycle changes.
- **FR-096**: Owners MUST see ordinary request, offer, appointment, and visit history but MUST NOT see staff notes, internal audit reasons, raw AI content, detailed clinical notes, or solver diagnostics.
- **FR-097**: Original text revisions, consent decisions, raw and validated AI artifacts, staff-only clinical detail, and sensitive audit payloads MUST be encrypted at rest with versioned key references that permit controlled key rotation while retaining authorized readability.
- **FR-098**: If protected data cannot be encrypted or decrypted with an authorized active key, the system MUST fail closed, avoid plaintext fallback, preserve recoverable workflow state, and expose no protected content to an unauthorized user.
- **FR-099**: Scheduling, consent, AI, audit, appointment, and visit records MUST remain retained while their owner and pet exist; deletion of an owner or pet with scheduling history MUST be blocked with an explanation.
- **FR-100**: Every recoverable failure MUST preserve submitted state where safe, distinguish validation, expiry, concurrency, interpretation, matching, and session failures in plain language, and provide a valid next action without exposing implementation errors.
- **FR-101**: Existing legacy visits MUST remain historical and MUST NOT reserve future capacity; staff MUST be able to create and link a replacement appointment for each future-dated legacy visit without deleting or rewriting the original visit. If staff change its veterinarian or scheduled interval during reconciliation, they MUST record fresh owner agreement.

### Key Entities

- **Account**: An authenticated owner or staff identity, with username, role, credential lifecycle, session-invalidating version, and owner association when applicable.
- **Clinic Policy**: The clinic time zone, operating bounds, duration choices, start grid, booking horizon, hold duration, notice period, named periods, urgent-care copy, and contact details.
- **Veterinarian Availability**: A veterinarian's recurring same-day shift intervals plus optional date-specific replacement intervals, leave, and clinic closures used to derive the effective schedule for each local date.
- **Scheduling Request**: The owner/pet scheduling case and its owner-facing lifecycle; it links current and historical revisions, work, queue handling, offers, and an eventual appointment.
- **Text Revision**: One immutable version of owner prose with its submission instant and revision-specific consent decision.
- **Workflow Revision**: One confirmed set of visit reason, availability, duration, specialty, urgency, and preferred veterinarian used for matching and offer exclusions.
- **Interpretation**: The protected automated or staff-created structured interpretation, its validation outcome, source revision, and owner-confirmation state.
- **Background Job**: Durable interpretation or matching work with type, target revision, lifecycle, attempt count, timing, outcome, and stale-result disposition.
- **Queue Item**: Staff fallback work with reason, urgency, state, assignee, current revision, contact status, and resolution.
- **Offer / Hold**: Exactly one proposed veterinarian interval reserved until a server-authoritative expiry, with origin, revision, attempt number, and accept/reject/expiry outcome.
- **Appointment**: A confirmed reservation of one veterinarian, one pet, and one owner for an interval, with lifecycle state and links to its originating request and changes.
- **Visit**: Historical care produced only by appointment completion, separating the owner-visible summary from staff-only clinical detail.
- **Audit Event**: An append-only record of an attempted or completed action and its outcome, referring to protected artifacts instead of copying sensitive raw content.
- **Command Record**: The identity and canonical outcome of a mutating command, used to make retries and duplicate submissions safe.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In usability testing, at least 90% of owners can submit, review, and confirm an eligible routine appointment on their first attempt without staff help or exposure to clinic-wide availability.
- **SC-002**: Under normal operating conditions, at least 95% of consented, valid routine requests reach either an offer or a clear staff-fallback state within 20 seconds of submission.
- **SC-003**: Every automated match completes its slot-selection step within five seconds and returns the same result when evaluated against the same request revision and calendar snapshot.
- **SC-004**: Across the acceptance suite, 100% of offered and confirmed appointments satisfy owner windows, specialty, notice, horizon, effective availability, and veterinarian/pet/owner non-overlap rules.
- **SC-005**: In concurrent booking tests, zero scenarios produce overlapping active holds or appointments for the same veterinarian, pet, or owner.
- **SC-006**: Every suspected-emergency and priority test request is prevented from automatic booking; 100% reaches the appropriate staff path, and suspected emergencies also display urgent-care guidance immediately.
- **SC-007**: After refresh, logout, restart, or session expiry, 100% of persisted workflows resume at their canonical state without duplicate interpretation, matching, offer, or appointment effects.
- **SC-008**: Replaying any covered mutating command produces no additional business transition and returns the same canonical outcome in 100% of duplicate-submission tests.
- **SC-009**: Staff can configure a veterinarian's effective availability and complete a compliant direct booking in under three minutes during task-based usability testing.
- **SC-010**: Authorization tests show zero cross-owner record disclosures and zero successful owner-to-staff or staff-only-field access attempts.
- **SC-011**: Every required staff mutation and lifecycle correction produces a complete append-only audit event, while owners see none of the protected staff, AI, or clinical fields.
- **SC-012**: All 15 required demonstration journeys complete from seeded or controlled starting conditions without manual data repair between steps.

## Assumptions

- The feature is a single-clinic proof of concept operated as one application instance and restricted to synthetic or demonstration owner and clinical data.
- Phase-one persistence targets the project's embedded relational environment. Compatibility with additional database products is deferred.
- The owner and staff experiences are English-only in this phase.
- Existing veterinarian, specialty, owner, pet, and legacy visit records remain authoritative catalogs; this feature does not add veterinarian/specialty administration or owner pet editing.
- Staff are trusted clinic employees sharing one staff permission tier, but use distinct identities for ownership and audit.
- Staff contact owners outside the application; email, SMS, push notifications, waitlists, and unsolicited capacity alerts are not part of this feature.
- Test environments may use ephemeral data. The running and demonstration environments retain state across normal application restarts.
- Historical future-dated legacy visits do not reserve capacity; staff may reconcile them by creating and linking a replacement appointment while retaining the legacy record.
- Detailed implementation choices for constraint solving, persistence, schema evolution, runtime, build tooling, and AI integration are planning concerns and must honor the separately approved technical constraints.

## Required Demonstration Journeys

1. Successful interpretation, owner confirmation, suggestion, and booking.
2. Offer rejection followed by another owner-requested suggestion.
3. Offer expiry followed by an explicit request for another option.
4. No automatic match followed by owner revision.
5. Consent decline followed by manual staff interpretation.
6. Automated interpretation failure followed by staff fallback.
7. Emergency detection, staff selection of replacement urgency with reason, and owner reconfirmation.
8. Staff-assisted portal offer after owner contact.
9. Staff direct booking with recorded owner agreement.
10. Owner cancellation of a future appointment.
11. Staff rescheduling and staff cancellation.
12. Completion, no-show, and audited correction.
13. Prevention of veterinarian, pet, owner, appointment, and hold conflicts.
14. Queue claiming and reassignment by two distinct staff identities.
15. Reconciliation of a future-dated legacy visit.

## Scope Boundaries

### In Scope

- Owner request submission, revision, review, held offers, appointment confirmation, history, withdrawal, and cancellation.
- Staff availability management, clinic policy, queue handling, assisted offers, direct booking, rescheduling, cancellation, completion, no-show, and audited correction.
- Secure owner-account provisioning, authenticated role separation, revision-specific AI consent, emergency handling, durable recovery, encryption, and append-only audit.
- One veterinarian per appointment, with veterinarian, pet, and owner conflict prevention.
- Seeded demonstration data and controlled failure scenarios covering the 15 journeys represented by the proposal.

### Out of Scope

- Owner self-registration, self-service password recovery, and staff-account administration.
- Multiple clinics, owner-visible full availability, external notifications, waitlists, or unsolicited offers.
- Rooms, equipment, assistants, travel time, buffers, daily caps, or resources beyond one veterinarian.
- Attachments, rich text, overnight shifts, calendar-file export, owner pet editing, and veterinarian/specialty catalog administration.
- Real owner or clinical data, production privacy-compliance certification, deletion or anonymization of scheduling history, formal device/browser/accessibility certification, and non-English localization.
- Native executables, compatibility work for additional database products, and externally exposed operational-management endpoints.
