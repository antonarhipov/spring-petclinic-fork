# Feature Specification: Smart Appointment Scheduling

**Feature Branch**: `appointment-scheduling-speckit-with-clarification`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "Create the smart appointment scheduling specification from `proposal/proposal.md` as refined by `proposal/proposal-refined.md`."

## Clarifications

### Session 2026-08-30

- Q: Which pet appointments may staff confirm without recording owner agreement? → A: Only documented clinic-directed follow-ups or rechecks.
- Q: After how many failed logins should further attempts be blocked, and for how long? → A: Do not block or throttle failed logins in this POC.
- Q: May this POC process real owner or pet information, or must it use non-production data only? → A: Use synthetic or demo data only.
- Q: What operating scale should acceptance testing require for this single-clinic POC? → A: 25 concurrent users and 10,000 scheduling records.
- Q: When no preferred slot is available but an allowed fallback exists, should the owner choose whether to see that fallback before it is offered? → A: First report no preferred match, then offer Find an alternative and Forward to staff actions.
- Q: Which device experience should define acceptance for owner and staff scheduling pages? → A: Desktop-only POC; mobile support is out of scope.
- Q: How should the owner-facing flow connect request entry, interpretation review, suggestions, and confirmation? → A: Use separate full pages with a shared progress/status header and dashboard resume action.
- Q: What should owners see while interpretation or slot matching is still running? → A: Show a plain-status processing page with no percentage, allow leaving, continue automatically, and support dashboard resume.
- Q: How should the offer page communicate an approaching and completed hold expiry? → A: Show exact expiry and a live countdown, warn at two minutes, then disable acceptance and show next actions automatically.
- Q: How is a shipped scheduling page proven to be usable? → A: Reachability from the home page by following rendered links only, verified automatically per role; a page no navigation links to counts as unbuilt.
- Q: How should staff navigate between fallback requests, the clinic calendar, veterinarian availability, and settings? → A: Use separate Queue, Calendar, Availability, and Settings pages with persistent navigation; queue items open full detail pages.
- Q: How should owners give or decline consent before their request text is sent for automated interpretation? → A: Use a dedicated consent page with a data-use summary, unchecked agreement, and separate Agree and interpret and Continue without AI actions.
- Q: How should the interpretation review page show uncertain, invalid, or contradictory fields? → A: Show a top summary and text-and-icon field messages, and disable confirmation until editable issues are fixed or staff handling is selected.
- Q: How should owners confirm actions that cancel an appointment, withdraw a request, or reject a held offer? → A: Use confirmation dialogs for cancel and withdraw, inline two-step confirmation for reject, and no undo after completion.
- Q: What accessibility standard should the desktop scheduling POC meet? → A: No explicit accessibility requirements for the POC.
- Q: What should happen when an owner or staff member acts from a stale page after another tab or user has changed the request, hold, appointment, or queue item? → A: Reject the stale action, show the current status and changed information, preserve safe unsaved input, and provide a refresh or continue action.
- Q: Must every owner-facing automated slot suggestion be selected by Timefold, or may simpler application logic bypass the solver? → A: Every owner-facing automated slot suggestion must be selected by Timefold; staff direct booking does not require Timefold.
- Q: What output contract must the LLM satisfy before interpreted data can reach Timefold? → A: Enforce versioned structured output, reject missing or invalid required fields, and record and ignore unknown fields.
- Q: When an LLM call fails before producing a valid structured interpretation, how many automatic retries should occur within the existing 10-second limit? → A: Retry once within the original 10-second total limit; do not retry a valid but uncertain interpretation.
- Q: Which execution details should be retained for reproducing and auditing each LLM and Timefold decision? → A: Retain versioned inputs, outputs, configuration identifiers, attempts, timing, outcomes, and score explanation without duplicating the reconstructible full prompt.
- Q: What should happen when Timefold selects a slot but the service cannot acquire its hold because the calendar changed after the solver input snapshot was created? → A: Refresh the snapshot and rerun Timefold once within the original five-second limit; route to staff if a hold still cannot be acquired.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Describe and Confirm an Appointment Need (Priority: P1)

An authenticated pet owner selects one of their pets, describes the reason for the visit and their availability in everyday English, knowingly consents to automated interpretation, and reviews the resulting structured request before scheduling begins. The owner can correct ordinary scheduling details, sees exactly how relative dates were resolved, and is never allowed to proceed automatically with ambiguous clinical or timing information.

**Why this priority**: A safe, confirmed interpretation is the foundation for every automated scheduling decision and prevents the clinic from acting on misunderstood owner text.

**Independent Test**: An owner can submit a valid description for an owned pet, consent to interpretation, review and edit the supported structured fields, and confirm a complete request without creating an appointment.

**Acceptance Scenarios**:

1. **Given** an authenticated owner viewing an owned pet, **When** they submit a 10-2,000 character English plain-text request and consent to interpretation, **Then** they see all interpreted fields, uncertainty markers, and resolved calendar dates before confirmation; visit reason, requested duration, allowed, preferred, and excluded windows, and preferred veterinarian are editable, while care type, specialty, and urgency require staff review to change.
2. **Given** an interpreted request with ordinary availability or preference uncertainty, **When** the owner edits an allowed field, **Then** the revised structured request requires confirmation without another interpretation attempt.
3. **Given** an owner changes the original prose, **When** they resubmit it, **Then** prior consent no longer applies and the new text is not interpreted until the owner gives fresh consent.
4. **Given** required information is missing, contradictory, outside clinic configuration, clinically uncertain, or contains unresolved timing, **When** interpretation finishes, **Then** automated suggestions remain unavailable and the request is routed to staff review.
5. **Given** the owner declines consent, **When** they continue, **Then** the original text is not sent for automated interpretation and the request enters staff handling for manual completion.
6. **Given** the first LLM call fails before producing a valid structured interpretation, **When** time remains inside the original 10-second deadline, **Then** the service retries once; if no valid result exists by the deadline, it retains the request and routes it to staff rather than losing it.
7. **Given** interpretation is still running, **When** the owner views its processing page, refreshes it, or leaves and later resumes, **Then** they see a plain-language status without a percentage and the existing attempt continues without duplicate submission.
8. **Given** the owner has entered request text, **When** they continue toward interpretation, **Then** a dedicated consent page explains in plain language what text is sent, why it is used, and what records are retained, leaves agreement unchecked, and presents separate **Agree and interpret** and **Continue without AI** actions.
9. **Given** an interpretation contains uncertain, invalid, or contradictory fields, **When** the owner reviews it, **Then** a summary identifies all affected fields, text-and-icon messages explain each issue beside its field, and confirmation remains disabled until editable issues are fixed or the owner selects staff handling.
10. **Given** an LLM response, **When** it is validated, **Then** missing or invalid required fields fail interpretation, unknown fields are recorded and ignored, and only recognized fields from the enforced versioned schema can populate the owner-reviewable interpretation.

---

### User Story 2 - Receive and Accept One Safe Suggestion (Priority: P1)

After confirming the interpretation, an owner requests a suitable appointment. The service evaluates the fixed current calendar and presents exactly one eligible slot with a short, non-sensitive explanation. The slot is temporarily held while the owner decides. Acceptance immediately confirms the appointment only if the hold is still active.

**Why this priority**: This is the central scheduling outcome: turning a natural-language need into a confirmed appointment without exposing the clinic's full calendar or creating double bookings.

**Independent Test**: With configured veterinarian availability and a confirmed request, an owner can request one suggestion, see its hold deadline, accept it, and obtain a confirmed non-conflicting appointment.

**Acceptance Scenarios**:

1. **Given** several eligible slots, **When** the owner requests a suggestion, **Then** they see only the highest-ranked slot, its veterinarian name and specialty, a brief explanation, the exact hold expiry in the clinic time zone, and a live countdown.
2. **Given** no eligible slot exists inside the owner's preferred windows but a lower-ranked allowed fallback exists, **When** preferred matching finishes, **Then** no fallback is shown or held and the owner is told that no preferred match is available and may choose either **Find an alternative** or **Forward to staff**.
3. **Given** the no-preferred-match choice, **When** the owner selects **Find an alternative**, **Then** they see exactly one held, lower-ranked allowed slot clearly labelled as a fallback without seeing other slots or calendar details.
4. **Given** an active hold, **When** the owner accepts before expiry and the final hold check succeeds, **Then** one confirmed appointment is created immediately without any opportunity for the slot to be booked between the check and confirmation.
5. **Given** a hold has two minutes remaining, **When** the offer page remains open, **Then** it displays a prominent warning; at expiry it automatically disables acceptance, explains that the offer is unavailable, and displays the next available actions without requiring an attempted acceptance or page refresh.
6. **Given** two actors attempt to obtain the same veterinarian and time, **When** their actions overlap, **Then** no more than one active hold or confirmed appointment reserves that slot.
7. **Given** Timefold selects a slot from a snapshot that becomes stale before a hold is acquired, **When** the service detects the conflict, **Then** it refreshes the snapshot and reruns Timefold once within the original five-second deadline; if the second selected slot also cannot be held, no offer is shown and the retained request enters staff handling.

---

### User Story 3 - Reject, Revise, or Withdraw Before Booking (Priority: P1)

An owner can reject a suggestion and ask for another, revise a confirmed request, or withdraw an unscheduled request. Rejected and expired offers remain in history and are not automatically repeated within the same request revision. Owners retain control without seeing the full calendar.

**Why this priority**: Guided scheduling is only usable if owners can decline unsuitable options and recover cleanly from expired holds or changed circumstances.

**Independent Test**: An owner rejects one held offer, receives a different eligible offer, revises availability to start a fresh revision, and can withdraw before confirmation while all prior activity remains auditable.

**Acceptance Scenarios**:

1. **Given** an active offered slot, **When** the owner starts rejection, **Then** an inline second step explains that the offer will be released and excluded and allows an optional reason; only explicit confirmation releases the hold, excludes the exact veterinarian-and-time from automatic re-offer for that revision, and makes the reason visible only to staff, with no undo.
2. **Given** a rejected or expired offer, **When** the owner requests another option, **Then** matching uses the confirmed request, the current calendar, and the revision's exclusion history and presents at most one new held offer.
3. **Given** five rejected or expired offers in one revision, **When** another automated attempt would be required, **Then** automation stops and the request enters staff review.
4. **Given** offers have already been made, **When** the owner changes confirmed availability or duration, **Then** any active hold is released, prior history is retained, and a new revision begins with a cleared rejection count and exclusion list.
5. **Given** any pre-confirmation request state, **When** the owner chooses withdrawal, **Then** a confirmation dialog explains that holds and active queue work will be released and the request cannot reopen; only explicit confirmation closes the request with retained history, with no undo.
6. **Given** the pet already has an active request, **When** the owner attempts to start another request for the same pet, **Then** the new request is refused until the active one is confirmed, closed, or withdrawn.
7. **Given** an owner leaves an active request at any stage, **When** they return to their dashboard, **Then** they see its current plain-language status and one **Resume** action that opens the correct current stage.

---

### User Story 4 - Recover Through Staff Assistance (Priority: P1)

Clinic staff work a prioritized fallback queue for requests that cannot proceed automatically. A staff member claims a request, resolves missing or uncertain information, and either offers one held slot for owner acceptance or books directly after recording owner agreement. Owners see a clear plain-language status but not internal notes or assignments.

**Why this priority**: The feature must not strand urgent, ambiguous, unsupported, or technically failed requests.

**Independent Test**: A request deliberately forced into fallback can be claimed, completed manually, offered to the owner, and resolved without exposing staff-only information.

**Acceptance Scenarios**:

1. **Given** a request with declined consent, interpretation or matching failure, unresolved uncertainty, no eligible specialty, no match, or five excluded offers, **When** automated handling stops, **Then** one retained queue item represents the request and the owner sees a plain staff-handling status.
2. **Given** multiple open queue items, **When** staff view the queue, **Then** suspected emergencies appear first and other work appears oldest first.
3. **Given** an unclaimed queue item, **When** a staff member claims it, **Then** the item becomes in review under that staff member while remaining visible to other staff.
4. **Given** a claimed item, **When** another staff member explicitly reassigns or unclaims it with an audit reason, **Then** ownership changes without losing its history; claims never expire automatically.
5. **Given** an owner previously declined consent for the current text, **When** staff handle the request, **Then** staff complete the interpretation manually and cannot submit that text for automated interpretation.
6. **Given** staff have verified a request, **When** they arrange a slot, **Then** they may either hold one offer for owner acceptance or directly confirm after recording owner agreement; booking without owner agreement is limited to clinic-directed follow-ups or rechecks documented in the pet's existing care history.
7. **Given** staff are working in the scheduling area, **When** they move among fallback requests, the clinic calendar, veterinarian availability, and settings, **Then** persistent navigation opens separate full pages for each workspace and selecting a queue item opens its full request-detail page.

---

### User Story 5 - Handle Possible Emergencies Safely (Priority: P1)

Every owner sees clinic-maintained urgent-care guidance while composing a request. If the text may indicate an emergency, the owner is immediately shown that guidance and a high-priority staff item is created. Automated screening may raise concern but never reassure the owner that their condition is non-urgent.

**Why this priority**: Appointment automation must never delay urgent care or create false reassurance.

**Independent Test**: A request containing an audited emergency term triggers immediate fixed guidance and high-priority staff handling even when automated interpretation is unavailable.

**Acceptance Scenarios**:

1. **Given** any owner on the request form, **When** the form is displayed, **Then** current clinic urgent-care guidance is permanently visible before any text is submitted.
2. **Given** submitted text matches an emergency-screening term or automated interpretation flags urgency, **When** the request is processed, **Then** urgent guidance is shown immediately, a high-priority staff item is created, and automated booking does not delay the owner-facing instruction.
3. **Given** the emergency screen finds no match, **When** the request continues, **Then** the service does not state or imply that the condition is non-urgent.

---

### User Story 6 - Manage Clinic Capacity and Scheduling Policy (Priority: P2)

Staff configure clinic-wide scheduling rules, recurring veterinarian shifts, date-specific exceptions, leave, and clinic closures. These rules determine eligible capacity while protecting existing appointments and active holds.

**Why this priority**: Suggestions are only trustworthy when they reflect the clinic's actual working hours and enforce existing commitments.

**Independent Test**: Staff can configure a split shift and an exception, observe the documented precedence, and cannot save a change that would invalidate an appointment or hold.

**Acceptance Scenarios**:

1. **Given** a veterinarian with recurring shifts, **When** staff add multiple same-day, non-overlapping, 15-minute-aligned intervals, **Then** those intervals define ordinary availability.
2. **Given** overlapping availability rules, **When** eligibility is evaluated, **Then** clinic closure takes precedence over veterinarian leave, leave over a date-specific exception, and an exception over the recurring shift.
3. **Given** a proposed availability change conflicts with a confirmed appointment, **When** staff save it, **Then** the change is blocked until the appointment is deliberately rescheduled or cancelled.
4. **Given** a proposed availability change conflicts with an active hold, **When** staff save it, **Then** the change is blocked until staff deliberately release the hold with an audit reason.
5. **Given** at least one request or appointment exists, **When** staff attempt to change the clinic time zone, **Then** the change is refused.
6. **Given** a named period was resolved and confirmed on an existing request, **When** staff later change that named period, **Then** the existing request keeps its concrete confirmed windows and only new or revised requests use the new definition.

---

### User Story 7 - Manage Appointments Through Their Lifecycle (Priority: P2)

Staff can directly book, reschedule, and cancel appointments within configured capacity, recording a reason for every staff-initiated change. After a scheduled appointment ends, staff mark it completed or no-show. Completion creates visit history; audited corrections keep that history consistent.

**Why this priority**: The clinic needs a complete operational workflow after automated booking, not merely a slot suggestion.

**Independent Test**: Staff book and reschedule an appointment, mark it completed after its end, verify one linked visit-history entry, and perform an audited correction.

**Acceptance Scenarios**:

1. **Given** an eligible future slot, **When** staff book it with a required reason category and optional note, **Then** a confirmed appointment reserves exactly one veterinarian and does not overlap that veterinarian or pet.
2. **Given** a confirmed appointment, **When** staff reschedule it with a reason, **Then** its identity is retained and the old time, new time, actor, timestamp, and reason are recorded.
3. **Given** a time outside configured clinic or veterinarian availability, **When** staff attempt direct booking, **Then** booking is refused until the appropriate availability exception exists; conflicts can never be overridden.
4. **Given** the scheduled end has passed, **When** staff mark the appointment completed with the required clinical details, **Then** exactly one linked visit-history entry records the actual completion date, assigned veterinarian, and clinical notes.
5. **Given** the scheduled end has passed, **When** staff mark the appointment no-show with a required category and optional note, **Then** no visit is created and the owner sees only the plain no-show status.
6. **Given** a completion or no-show was recorded incorrectly, **When** staff perform an audited correction, **Then** status and linked visit history are created, removed, or corrected consistently without rewriting the original audit trail.

---

### User Story 8 - Use Owner Appointment Self-Service (Priority: P2)

Owners view their own request history, held offer, rejected and expired offers, upcoming appointments, and completed visits. They can cancel an upcoming appointment until its start or withdraw an unscheduled request, but cannot edit owner or pet records or act on another owner's data.

**Why this priority**: Owners need visibility and control over their commitments without gaining access to clinic operations or other clients' data.

**Independent Test**: An owner can view and cancel one of their future appointments while another owner's records and staff-only details remain inaccessible.

**Acceptance Scenarios**:

1. **Given** an authenticated owner, **When** they view scheduling history, **Then** they see only their own submitted information, plain request and appointment statuses, offer history, appointment times, veterinarian names and specialties, and completed visit history.
2. **Given** an upcoming appointment before its start, **When** the owner chooses cancellation, **Then** a confirmation dialog explains that the slot will be released and the original request will not reopen, allows an optional reason, and cancels only after explicit confirmation, with no undo.
3. **Given** a pet already has future appointments, **When** the owner starts a new request, **Then** those appointments are shown first and the request is allowed if the one-active-request limit and non-overlap rules can still be met.
4. **Given** an owner attempts to access another owner's request, appointment, pet, or profile, **When** the access is evaluated, **Then** no protected data or action is available.

---

### User Story 9 - Provision and Protect Accounts (Priority: P3)

Staff provision owner accounts with unique usernames and temporary credentials. Operational pages require authentication, permissions separate owners from staff, and account/session safeguards protect non-demo use. Local demonstration environments remain easy to access without introducing predictable credentials into deployed environments.

**Why this priority**: Scheduling contains personal and clinical context and therefore requires controlled access, while predictable demo access must remain isolated from real deployments.

**Independent Test**: Staff provision a new owner, the owner completes a required password change, unauthorized roles are denied, and demo-only credentials are absent from a deployed profile.

**Acceptance Scenarios**:

1. **Given** staff create an owner account, **When** they accept the suggested or another unique username, **Then** a random one-time password is displayed once, expires after seven days, and forces a password change at first use.
2. **Given** staff reset an owner password, **When** the reset completes, **Then** the same one-time-password rules apply and all existing sessions for that owner are invalidated.
3. **Given** local, demo, or test use, **When** seed data starts, **Then** existing owners receive documented first-name demo credentials without a forced change and the single seeded staff login is `admin/admin123`.
4. **Given** a deployed environment, **When** the service starts, **Then** it creates no predictable owner credentials and requires externally supplied initial staff credentials.
5. **Given** a successful login or password change, **When** the new authenticated session begins, **Then** its identifier differs from the prior session and it expires after 30 minutes of inactivity.

### Edge Cases

- A request's selected pet is removed, transferred, or no longer belongs to the owner while the request is active; further owner action must be denied and staff must resolve the retained record.
- An owner tries to change the selected pet after submission; the pet remains immutable and the owner must withdraw and create a new request.
- Relative wording crosses midnight, daylight-saving transitions, the 90-day default horizon, or the two-hour owner notice boundary; all displayed resolved dates and hold deadlines use the clinic time zone unambiguously.
- An allowed window is shorter than the confirmed duration, conflicts with an explicit exclusion, or falls wholly outside capacity; it yields no eligible slot and never weakens a hard constraint.
- A preferred veterinarian is unavailable but another eligible veterinarian exists; no alternative is shown or held until the owner chooses **Find an alternative**, after which another veterinarian may be suggested as a labelled fallback unless veterinarian identity was confirmed as a hard requirement through staff review.
- Matching exceeds five seconds or its supporting service is unavailable; the retained request enters staff handling without exposing failure internals to the owner.
- A hold expires exactly as acceptance arrives; only the authoritative final hold check decides, and an expired acceptance creates no appointment.
- A Timefold result becomes stale before its hold is acquired; the stale result is never exposed as an offer, one fresh-snapshot solve is allowed within the original deadline, and failure to acquire the second hold routes the retained request to staff.
- A staff booking competes with another owner's hold; the hold blocks booking unless staff act for its holder or first release it through an audited cancellation or expiry action.
- New capacity appears for a waiting request; no unsolicited offer is created, and capacity is reconsidered only when the owner requests another option or staff work the queue.
- A closure or leave range spans several dates; it removes each full local date and cannot represent a partial-day closure.
- An overnight shift is entered; it is rejected because each supported interval must start and end on the same local date.
- A legacy future-dated visit exists; it remains historical, reserves no capacity, and must be recreated manually as an appointment to become a future booking.
- A cancelled or no-show appointment is marked completed; the action is refused unless staff use the audited correction workflow and provide the required visit details.
- Two requests for the same pet would create overlapping appointments even with different veterinarians; the conflicting suggestion or booking is ineligible.
- An owner or staff member submits an action from a page made stale by another tab or user; the action is rejected without overwriting current data, the page explains the current status and what changed, safe unsaved input is preserved for review, and a refresh or continue action leads to the current state.

## Requirements *(mandatory)*

### Functional Requirements

#### Access and Account Protection

- **FR-001**: The service MUST require authentication for every operational owner and staff page; only a non-operational landing and login surface MAY be public.
- **FR-002**: The service MUST support exactly two permission levels for this feature: `OWNER` and `STAFF`; `admin` MUST remain a username rather than a separate permission tier.
- **FR-003**: Owners MUST be limited to requests, appointments, pets, profile data, and visit history belonging to their own account.
- **FR-004**: Owners MUST see only their submitted data, ordinary status and offer history, appointment time and status, and assigned veterinarian names and specialties; they MUST NOT see staff notes, assignments, audit reasons, matching details, or calendar availability.
- **FR-005**: Staff MUST be able to manage all owners and pets, scheduling policy, veterinarian availability, fallback work, appointments, and audit history, but MUST NOT administer additional staff accounts or veterinarian and specialty catalogs in this feature.
- **FR-006**: Owners MUST be able to view but not edit their profile and pet records; staff MUST retain record-management authority.
- **FR-007**: Staff-created owner accounts MUST use a unique staff-selected username, offer a normalized first-name suggestion with numeric collision suffixes, and receive a random one-time password displayed once, expiring after seven days, and requiring change at first use.
- **FR-008**: Staff password resets MUST follow the same one-time-password rules and invalidate every active session for the affected owner.
- **FR-009**: Non-demo passwords MUST contain at least six characters and MUST be stored only as strong one-way hashes.
- **FR-010**: The service MUST rotate the session identifier after login and password change and expire authenticated sessions after 30 minutes of inactivity.
- **FR-011**: Local, demo, and test profiles MUST seed existing owner accounts with lowercase first-name usernames and `<username>123` passwords without forced password changes and MUST seed `admin/admin123` as the sole staff login.
- **FR-012**: Deployed profiles MUST NOT create predictable owner credentials and MUST require initial staff credentials from protected deployment configuration.

#### Request Capture, Interpretation, and Consent

- **FR-013**: An owner MUST select exactly one pet they own and submit an English plain-text description between 10 and 2,000 characters; attachments, markup, and additional free-form fields MUST NOT be accepted.
- **FR-014**: The selected pet MUST become immutable after submission; changing pets requires withdrawal and a new request.
- **FR-015**: The service MUST allow at most one active scheduling request per pet while allowing multiple non-overlapping future appointments.
- **FR-016**: The service MUST show the pet's existing upcoming appointments before accepting a new request.
- **FR-017**: Urgent-care guidance with safe default copy MUST be visible continuously on the request form and MUST be editable by staff.
- **FR-018**: Before text is sent for automated interpretation, a dedicated consent page MUST explain in plain language what text is sent, why it is used, and what records are retained, MUST leave the agreement unchecked, and MUST record explicit owner consent scoped to that exact text revision with its timestamp only when the owner selects **Agree and interpret**.
- **FR-019**: The consent page MUST provide a separate **Continue without AI** action that routes the request to manual staff handling without sending the text for automated interpretation; staff MUST NOT submit that revision for automated interpretation unless the owner later consents.
- **FR-020**: Automated interpretation MUST produce the visit reason, a duration from the clinic-configured set, general or specialty care, required specialty, allowed, preferred, and excluded time windows, optional preferred veterinarian, urgency indication, unresolved dates, and uncertainty indicators.
- **FR-021**: The owner MUST be able to edit the visit reason, allowed, preferred, and excluded availability, preferred veterinarian, and duration from configured choices; changing care type, specialty, or urgency MUST require staff review.
- **FR-022**: Editing structured fields MUST require renewed owner confirmation but MUST NOT trigger another interpretation attempt; editing source text MUST require renewed consent and a fresh interpretation.
- **FR-023**: Relative dates MUST be resolved using the submission time and clinic time zone, and concrete resolved dates MUST be shown before confirmation.
- **FR-024**: A completed interpretation MUST be accepted for automated scheduling only when deterministic completeness and validation rules find all required fields present, consistent, within configuration, clinically unambiguous, and temporally resolved.
- **FR-025**: The service MUST route a request to staff if required fields are missing or contradictory, values fall outside clinic configuration, specialty or urgency remains uncertain, or dates remain unresolved.
- **FR-026**: An interpretation attempt MUST finish within 10 seconds; failure or timeout MUST retain the request and route it to staff.
- **FR-027**: The service MUST preserve the source text, consent decision and timestamp, interpretation output, model identifier, and final confirmed interpretation for as long as the related owner and pet records exist.
- **FR-028**: A successful interpretation of unchanged source text MUST remain authoritative; staff MUST correct it manually or ask the owner to revise and re-consent, and a valid but uncertain interpretation MUST NOT trigger another LLM call.
- **FR-029**: A small audited emergency-term screen MUST be able only to raise urgency, MUST work independently of automated interpretation, and MUST never classify a request as non-urgent.
- **FR-030**: Suspected emergencies MUST immediately display the clinic's fixed urgent-care guidance and create a high-priority staff item; automated booking MUST NOT delay the guidance.

#### Request Lifecycle, Matching, Offers, and Holds

- **FR-031**: Every request MUST have one explicit owner-facing lifecycle state representing interpretation review, ready for suggestion, offer held, staff handling, confirmed, or closed and MUST display that state in plain language.
- **FR-032**: The owner MUST confirm the structured interpretation before any automatic suggestion is generated.
- **FR-033**: Explicit exclusions and confirmed allowed windows MUST be hard constraints; preferences and preferred veterinarians MUST affect ranking; wording such as "if necessary" MUST denote a lower-ranked acceptable fallback.
- **FR-034**: A slot MUST be ineligible unless it satisfies clinic and veterinarian availability, closures and leave, booking horizon, confirmed duration, veterinarian and pet non-overlap, confirmed owner hard windows, specialty eligibility, minimum notice, and hold and rejection exclusions.
- **FR-035**: Every appointment MUST reserve exactly one eligible veterinarian for its full duration; general care MAY use any available veterinarian and specialty care MUST use a veterinarian with that specialty.
- **FR-036**: Owner suggestions and bookings MUST start at least two hours in the future; staff MAY book any future time but MUST still obey availability and conflict rules.
- **FR-037**: Matching MUST evaluate one request against the fixed current calendar and MUST NOT move or invalidate a confirmed appointment.
- **FR-038**: Eligible slots MUST be ranked first by hard eligibility, then confirmed owner preferences, then earliest suitable time, and finally a stable veterinarian/time and clinic-efficiency tie-breaker; clinic efficiency MUST NOT displace a better owner match or earlier suitable slot.
- **FR-039**: Repeating the same match against unchanged inputs MUST select the same slot.
- **FR-040**: The owner MUST receive no more than one suggested slot at a time and MUST NOT receive the complete availability calendar or undisclosed alternatives.
- **FR-041**: If no preferred slot is eligible but a lower-ranked allowed fallback exists, the service MUST NOT hold or display that fallback until it reports that no preferred match is available and offers **Find an alternative** and **Forward to staff** actions; choosing **Find an alternative** MUST produce one held slot clearly labelled as a fallback without revealing other slots or staff-calendar details.
- **FR-042**: Generating an offer MUST create one exclusive hold for the request and MUST prevent any other owner or staff member from holding or booking that veterinarian and time, except staff acting for the holder.
- **FR-043**: The offer page MUST display the hold's exact expiry in the clinic time zone and a live countdown, show a prominent warning when two minutes remain, and perform the final hold check and appointment reservation as one indivisible action so no competing booking can occur between them.
- **FR-044**: Successful owner acceptance MUST immediately create a confirmed appointment.
- **FR-045**: Rejecting an offer MUST immediately release the hold, retain the offer in history, and exclude that exact veterinarian-and-time from automatic re-offer for the current revision; an optional rejection reason MUST be visible to staff but MUST NOT affect ranking.
- **FR-046**: Hold expiry MUST release the slot, retain and exclude the expired offer for the current revision, automatically disable acceptance on an open offer page, explain the expiry, display the next available actions, and return the request to the state where the owner can request another suggestion.
- **FR-047**: An acceptance that fails because the hold expired or became unavailable MUST create no appointment, explain that the offer is unavailable, and return the request to the next-suggestion state.
- **FR-048**: Requesting another option MUST rematch against the confirmed request, current calendar, and all rejection and expiry exclusions for that revision.
- **FR-049**: Revising confirmed availability or duration after an offer MUST release any active hold, retain prior offer history, create a new request revision, and clear the rejection count and exclusion list.
- **FR-050**: After five rejected or expired offers in one revision, the service MUST stop automatic offers and route the request to staff review.
- **FR-051**: If no eligible slot exists, the service MUST state that no automatic match is currently available, allow availability revision and reconfirmation, and create or retain a staff-queue item.
- **FR-052**: New capacity MUST NOT create an unsolicited offer; it MUST be reconsidered only when the owner requests another option or staff work the queue.
- **FR-053**: A matching attempt MUST finish within five seconds; failure or timeout MUST retain the request and route it to staff without exposing internal failure details.
- **FR-054**: Owners MUST be able to withdraw a request from every pre-confirmation state; withdrawal MUST release active holds and queue work, close the request with retained audit history, and prevent reopening.

#### Staff Queue and Assisted Scheduling

- **FR-055**: A retained request MUST enter staff handling when consent is declined, interpretation or matching fails or times out, required specialty is unavailable, uncertainty remains, no match exists, or the rejection/expiry limit is reached.
- **FR-056**: Staff queue work MUST use `NEW`, `IN_REVIEW`, `AWAITING_OWNER`, `RESOLVED`, and `CLOSED` sub-states.
- **FR-057**: Open queue items MUST sort suspected emergencies first and then oldest request first.
- **FR-058**: All staff MUST see open queue items, and a staff member MUST claim an item before working it.
- **FR-059**: Any staff member MUST be able to explicitly reassign or unclaim a queue item at any time with an audit reason; claims MUST NOT expire automatically.
- **FR-060**: Owners MUST see only a plain staff-handling status and MUST NOT see queue assignment, sub-state details, or staff notes.
- **FR-061**: Staff MUST be able to complete or correct an interpretation manually and then either hold one slot for owner acceptance or directly book after recording owner agreement.
- **FR-062**: Direct staff booking without recorded owner agreement MUST be limited to clinic-directed follow-ups or rechecks documented in the pet's existing care history.

#### Clinic Configuration and Availability

- **FR-063**: Staff MUST manage one clinic-wide duration set, clinic hours, booking horizon, hold duration, named parts of day, urgent-care guidance, clinic time zone, closures, and veterinarian availability.
- **FR-064**: Default configuration MUST provide 15, 30, 45, and 60-minute durations, 15-minute start times, a 90-day horizon, a 10-minute hold, weekday clinic hours of 09:00-17:00, and the `Europe/Amsterdam` time zone; veterinarians MUST have no bookable shifts until staff configure them.
- **FR-065**: The booking horizon MUST be configurable from 1 to 365 days, the hold duration from 1 to 60 minutes, and the 15-minute scheduling grid MUST remain fixed.
- **FR-066**: Named periods MUST be non-overlapping and wholly contained in one local day; confirmed named periods MUST be stored as concrete windows so later configuration changes affect only new or revised requests.
- **FR-067**: Veterinarian availability MUST support multiple non-overlapping, same-day recurring shift intervals aligned to the 15-minute grid, date-specific exceptions, and full-local-date leave; overnight shifts are out of scope.
- **FR-068**: Clinic closures MUST cover full local dates or date ranges rather than partial-day intervals.
- **FR-069**: Availability precedence MUST be clinic closure, veterinarian leave, date-specific exception, then recurring shift.
- **FR-070**: Every availability change MUST be blocked if it conflicts with a confirmed appointment until staff deliberately reschedule or cancel that appointment.
- **FR-071**: An availability change affecting an active hold MUST be blocked until staff deliberately release that hold with an audit reason; the owner MUST then see that the offer is unavailable and may request another.
- **FR-072**: Staff MUST be able to change the clinic time zone only before the first request or appointment exists; all later scheduling and display MUST remain unambiguous across daylight-saving transitions.

#### Appointment and Visit Lifecycle

- **FR-073**: Appointments MUST be the only mechanism for scheduling future care; legacy visit records MUST remain unlinked history and MUST NOT reserve capacity.
- **FR-074**: Owners MUST be able to cancel their own appointment until its scheduled start without a required reason, MAY add an optional reason, and MUST create a new request if another appointment is needed.
- **FR-075**: Staff MUST be able to book, reschedule, or cancel within configured availability using a required reason category and optional concise note; conflict overrides MUST NOT be permitted.
- **FR-076**: Rescheduling MUST retain appointment identity and record the old time, new time, actor, timestamp, and reason.
- **FR-077**: Staff MUST first create an appropriate availability exception before booking outside ordinary clinic or veterinarian availability and MUST still pass ordinary conflict checks.
- **FR-078**: Staff MUST NOT override a hold belonging to another owner; staff MAY release it only through an audited cancellation or expiry action before attempting to book the released slot.
- **FR-079**: Only staff MUST be able to mark an appointment completed or no-show, and only after its scheduled end.
- **FR-080**: Completion MUST create exactly one linked visit-history entry containing the actual completion date, assigned veterinarian, and clinical notes; cancelled and no-show appointments MUST create no visit.
- **FR-081**: A no-show MUST require a staff-selected reason and MAY include an optional note; owners MUST see only the plain no-show status.
- **FR-082**: Reversing or correcting completion or no-show MUST use an audited correction that consistently creates, removes, or corrects the linked visit entry without erasing prior history.

#### Audit, Retention, and Compatibility

- **FR-083**: Durable audit history MUST record actor, timestamp, action, target, and relevant before/after values for staff changes, queue claims, request revisions, consent decisions, interpretation and matching outcomes, offers and holds, acceptances and rejections, and appointment lifecycle events.
- **FR-084**: Staff MUST be able to read audit history; owners MUST receive ordinary request, offer, appointment, and visit history without staff-only audit details.
- **FR-085**: Scheduling records MUST be retained while their related owner and pet records exist, including after cancellation or completion; deletion and anonymization workflows are outside this feature.
- **FR-086**: Introducing scheduling MUST preserve existing owner, pet, veterinarian, specialty, and visit records and MUST remain compatible with every data-store deployment currently supported by the application.

#### Experience and Interaction

- **FR-087**: Owner and staff scheduling experiences MUST be designed and acceptance-tested for desktop use; mobile support is out of scope for this POC.
- **FR-088**: Request entry, consent, interpretation review, suggestion or fallback choice, and confirmation MUST use separate full pages with a shared progress/status header; the owner dashboard MUST provide one **Resume** action that opens the active request's correct current stage.
- **FR-089**: While interpretation or matching is running, the owner MUST see a dedicated processing page with plain-language status and no completion percentage, MAY safely leave or refresh without starting duplicate work, and MUST automatically advance when the result is ready or resume the resulting state from the dashboard.
- **FR-090**: The staff experience MUST provide separate full-page **Queue**, **Calendar**, **Availability**, and **Settings** workspaces through persistent navigation; selecting a queue item MUST open a full request-detail page.
- **FR-099**: Every owner-facing and staff-facing scheduling workspace MUST be reachable by navigation from the application home page without typing a URL: the site-wide navigation MUST expose the owner dashboard to signed-in owners and the staff scheduling workspaces to staff, MUST NOT offer a link the signed-in role is not authorized to open, and the owner dashboard MUST provide a per-pet action that starts a new scheduling request. Reachability MUST be verified by an automated check that starts at the home page and follows only rendered links.
- **FR-091**: The interpretation review page MUST show a top-of-page summary and a text-and-icon message beside every uncertain, invalid, or contradictory field, MUST NOT rely on color alone, and MUST keep confirmation disabled until the owner fixes every editable issue or selects staff handling for issues they cannot change.
- **FR-092**: Owner appointment cancellation and request withdrawal MUST use confirmation dialogs that explain their consequences; offer rejection MUST use an inline two-step confirmation; cancellation, withdrawal, and rejection MUST provide no undo after explicit confirmation.
- **FR-093**: When an action is based on stale request, hold, appointment, or queue data, the service MUST reject it without overwriting the current state, show the user the current status and what changed, preserve unsaved input that remains safe to reuse, and provide a refresh or continue action leading to the current state.

#### LLM and Solver Integration

- **FR-094**: Timefold MUST select every owner-facing automated slot suggestion, including initial, requested-alternative, and fallback suggestions; staff direct booking MUST use ordinary availability and conflict checks without requiring Timefold.
- **FR-095**: Every LLM invocation MUST enforce structured output against a versioned schema; responses missing required fields or containing invalid types, formats, or configured values MUST fail validation, unknown fields MUST be recorded and ignored, and only recognized validated fields MAY populate the interpretation and reach Timefold after owner confirmation.
- **FR-096**: If the first LLM call fails because of a transient communication error or because it produces no valid structured interpretation, the service MUST retry at most once when time remains, MUST keep both calls within the original 10-second total deadline, and MUST route the retained request to staff if no valid result exists by that deadline.
- **FR-097**: For each LLM interpretation, the service MUST retain the model identifier, prompt-template version, structured-output schema version, raw response, normalized result, attempt count, timing, outcome, and error classification; for each Timefold solve, it MUST retain the solver-configuration version, versioned input snapshot, selected result, score explanation, timing, outcome, and error classification. The service MUST NOT separately retain the full rendered prompt when it can be reconstructed from the retained source text and prompt-template version.
- **FR-100**: The service MUST emit operational logs that let an operator determine, from a single run, whether the LLM and Timefold integrations actually executed: per interpretation attempt the resolved model, prompt size, latency, transient-failure flag and validation classification; per solve the candidate-slot count, the availability and window inputs it was built from, the outcome, score and selected slot; and every transition that routes a request to staff, with its reason. Full prompts and raw model responses MUST be available at debug level only, because they contain owner-supplied free text. A stubbed or absent integration MUST log a warning that names itself as stubbed rather than failing silently.
- **FR-098**: A Timefold result MUST remain advisory until its hold is acquired against the authoritative calendar; if acquisition fails because the input snapshot became stale, the service MUST refresh the snapshot and rerun Timefold at most once within the original five-second total deadline, MUST never expose the stale result as an offer, and MUST route the retained request to staff if the second result cannot be held or the deadline expires.

### Key Entities

- **Scheduling Request**: An owner's request for one pet, including immutable pet identity, source text, lifecycle state, urgency, owner-visible status, active revision, queue relationship, and closure outcome.
- **Request Revision**: A confirmed scheduling interpretation at a point in time, including resolved allowed, preferred, and excluded windows, duration, care type, specialty, preferred veterinarian, interpretation provenance, rejection count, and excluded offers.
- **Consent Record**: The owner's decision for one exact source-text revision, with timestamp and whether automated interpretation was authorized.
- **Interpretation Record**: The original automated result or manual staff interpretation, structured-output schema version, recognized and unknown fields, uncertainty and validation outcome, model identifier, corrections, and the final owner-confirmed values.
- **Integration Execution Record**: Reproducibility and audit data for one LLM interpretation or Timefold solve, including versioned configuration and input reference, raw and normalized output where applicable, attempt count, timing, outcome or error classification, and Timefold score explanation.
- **Offer**: One veterinarian, start time, duration, ranking explanation, status, expiry, optional owner rejection reason, and relationship to a request revision.
- **Hold**: The exclusive, time-limited reservation attached to one offer and holder, including its active, released, expired, or accepted outcome.
- **Appointment**: A confirmed commitment for one pet and one veterinarian, with start, duration, status, source request when applicable, and lifecycle history.
- **Visit History Entry**: The immutable-by-default clinical history created from completion, linked to one appointment and containing actual completion date, veterinarian, and notes, with audited corrections.
- **Staff Queue Item**: The fallback work record with priority, sub-state, current assignee, staff-only notes, and resolution.
- **Veterinarian Availability**: Recurring same-day shifts, date-specific exceptions, and leave for one veterinarian.
- **Clinic Scheduling Policy**: Clinic time zone, hours, closures, visit-duration choices, fixed scheduling grid, booking horizon, hold duration, minimum owner notice, named periods, and urgent-care guidance.
- **Audit Event**: A durable record of actor, time, action, target, and relevant before/after values for a protected scheduling change or decision.
- **Account**: Login identity, owner or staff permission, credential lifecycle, session state, and optional link to an owner record.

### Scope Boundaries

The feature includes authenticated owner smart scheduling, explicit interpretation consent, guided one-at-a-time offers, holds, staff fallback, owner self-service, staff calendar and appointment management, clinic configuration, account provisioning, audit history, and the completed-appointment link to visit history.

The feature excludes formal accessibility requirements or conformance testing, mobile support, real personal or clinical data, owner self-registration and self-service password recovery, failed-login blocking or throttling, external notifications, waitlists, multiple clinics, attachments and markup, owner editing of owner/pet records, staff-account administration, veterinarian/specialty catalog administration, specialty-specific duration defaults or rules, a full owner-visible calendar, unsolicited offers, overnight shifts, partial-day clinic closures, and scheduling of rooms, equipment, assistants, buffers, daily caps, travel time, or workload balancing beyond the final tie-breaker.

### Dependencies

- Existing owner, pet, veterinarian, specialty, and visit-history records remain the authoritative catalogs for identity, ownership, care eligibility, and historical care.
- A reliable authenticated identity and session capability is available to distinguish owners from staff.
- Automated language interpretation is available for consented English text, with manual staff handling as the required fallback.
- Timefold is the authoritative engine for every owner-facing automated slot suggestion, with manual staff handling as the required fallback; staff direct booking uses ordinary availability and conflict checks.
- Clinic staff are responsible for configuring veterinarian shifts before owner scheduling can produce offers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In usability testing, at least 90% of owners can submit, review, correct, and confirm a valid scheduling request in under five minutes without staff help.
- **SC-002**: In 100% of acceptance tests, each owner suggestion satisfies every confirmed hard window, specialty, notice, capacity, horizon, and overlap constraint and exposes exactly one slot.
- **SC-003**: Across all concurrent hold and acceptance tests, no veterinarian or pet has overlapping active reservations or appointments, and no slot is confirmed more than once.
- **SC-004**: For unchanged request and calendar inputs, 100% of repeated matching tests select the same slot and explanation category.
- **SC-005**: 100% of interpretation attempts either present a result within 10 seconds or retain and route the request to staff; 100% of matching attempts either present a result within five seconds or retain and route the request to staff.
- **SC-006**: 100% of suspected-emergency tests show urgent-care guidance immediately and create a high-priority staff item even when automated interpretation is unavailable.
- **SC-007**: In task-based testing, at least 90% of owners can identify their request's current status and next available action without assistance.
- **SC-008**: In task-based testing, at least 90% of staff can claim a fallback item and either offer or directly arrange an eligible appointment in under three minutes once required information is available.
- **SC-009**: Authorization tests prevent 100% of cross-owner and owner-to-staff data access attempts while allowing each role to complete its documented workflows.
- **SC-010**: Audit verification finds an actor, timestamp, action, target, and relevant change details for 100% of the events listed in FR-083.
- **SC-011**: Lifecycle verification finds exactly one correct visit entry for every completed appointment and no visit entry for every cancelled or no-show appointment.
- **SC-012**: Migration and compatibility verification preserves 100% of pre-existing owner, pet, veterinarian, specialty, and visit records across all currently supported application data-store deployments.
- **SC-013**: With 10,000 stored scheduling records and 25 concurrent authenticated users, all tested core journeys continue to meet SC-002 through SC-005 and SC-009 without lost requests, conflicting holds, or duplicate appointments.
- **SC-014**: Audit verification can reconstruct the versioned inputs, configuration, outputs, timing, and outcome of 100% of LLM and Timefold executions from retained source records without depending on runtime logs.
- **SC-015**: In 100% of simulated calendar-race tests, a stale Timefold result is never offered, no conflicting hold is created, and the service either acquires a hold from one permitted fresh-snapshot rerun within five seconds or routes the retained request to staff.

## Assumptions

- This is a proof of concept for one clinic operating in one configured time zone and using synthetic or demo owner, pet, and clinical data only.
- Owners and staff use a desktop web experience with a stable connection; mobile support, external messages, and background offers are intentionally absent.
- The existing veterinarian and specialty catalog is correct and remains managed outside this feature.
- Staff obtain owner agreement through an appropriate clinic channel before recording a staff-assisted direct booking.
- Staff-entered clinical notes and urgent-care guidance follow the clinic's existing privacy and content policies.
- Real personal or clinical data MUST NOT be used until record deletion, anonymization, formal retention schedules, and applicable privacy and regulatory requirements are specified and implemented.
- Technology choices for language interpretation, matching, schema evolution, credential hashing, and storage portability are planning concerns; this specification defines their required user-visible and operational outcomes.
