# Smart Appointment Scheduling - Refined Decisions

This document records the decisions reached during the feature-design grilling
session for [proposal.md](proposal.md). It refines the proposal; it does not
replace its functional scope.

## Scheduling flow and appointment lifecycle

### Q1. Initial scheduling outcome

**Question:** Does owner acceptance merely request a booking, or immediately
create a confirmed appointment?

**Decision:** Owner acceptance immediately confirms the appointment. Staff
intervene only for fallback, exceptions, or direct management.

### Q2. Appointment duration

**Question:** What is the authoritative duration for a booking when AI infers
one?

**Decision:** AI proposes a duration from a staff-configured set. The owner
confirms it as part of the interpretation, and staff may override it during
fallback.

### Q3. Urgency policy

**Question:** What happens when a request suggests an emergency?

**Decision:** Immediately show fixed, clinic-configured urgent-care guidance.
Create a high-priority staff-queue item, but do not allow automated booking to
delay care.

### Q4. Hold expiry behavior

**Question:** What happens when an offered-slot hold expires, and may the same
slot be offered again?

**Decision:** Return the request to the "request another" state, release the
slot, and exclude the expired offer from automatic re-offer for that request.

### Q5. AI confidence and correction

**Question:** How may owners correct incomplete or low-confidence
interpretations?

**Decision:** Show editable structured fields with clear uncertainty markers.
Edits trigger confirmation but not another AI call unless the source text
changes.

### Q8. No-match outcome

**Question:** What happens if no eligible future slot matches the confirmed
request?

**Decision:** Tell the owner that no automatic match is currently available,
allow them to revise and reconfirm availability, and automatically add the
request to the staff queue.

### Q9. Scheduling rules versus preferences

**Question:** Which availability terms are hard constraints and which are
ranking preferences?

**Decision:** Explicit "cannot" statements and confirmed allowed windows are
hard constraints. Preferred windows and veterinarians are soft constraints,
and "if necessary" denotes a lower-ranked acceptable fallback. Never offer a
slot outside confirmed allowed windows.

### Q10. Default calendar policy

**Question:** What are the default appointment durations, grid, horizon, hold
duration, and clinic hours?

**Decision:** Use 15/30/45/60-minute visits, 15-minute starts, a 90-day booking
horizon, and a 10-minute hold. Default clinic hours are weekdays 09:00-17:00
in `Europe/Amsterdam`; no veterinarian shifts exist until staff configure them.

### Q11. Suggestion ranking and repeatability

**Question:** How is one slot selected when several are eligible?

**Decision:** Rank hard eligibility first, then confirmed owner preferences,
then earliest suitable time, with a stable veterinarian/time tie-breaker.
Display a short, non-sensitive explanation without exposing alternatives or
staff-calendar detail.

### Q12. Hold expiry and recovery

**Question:** Is the displayed hold deadline authoritative, and what happens
to acceptance submitted at its boundary?

**Decision:** Show the exact expiry in the clinic time zone. Acceptance succeeds
only after an atomic check confirms the hold is active. An expired acceptance
reports that the offer is unavailable and returns the request to its next-
suggestion state.

### Q13. Owner cancellation policy

**Question:** May owners cancel freely, must they give a reason, and does
cancellation reopen the original request?

**Decision:** Owners may cancel until appointment start without a required
reason. Record an optional owner reason and do not reopen the request; the
owner creates a new request when another appointment is needed.

### Q17. Active-request and rejection limits

**Question:** Can a pet have multiple active requests, and when should
automation stop retrying offers?

**Decision:** Permit at most one active request per pet. After five rejected or
expired offers, route the request to staff review.

### Q18. Veterinarian and conflict model

**Question:** What resources does an appointment reserve?

**Decision:** Each appointment reserves exactly one veterinarian for its entire
duration. Prevent overlaps for the assigned veterinarian and pet. General-care
requests may use any available veterinarian; specialty requests require a
veterinarian with that specialty. Rooms, equipment, and assistants are out of
scope.

### Q19. Appointment lifecycle and visit history

**Question:** How does completion relate to the existing `Visit` history?

**Decision:** Completion creates one immutable linked visit-history entry
containing the actual completion date, assigned veterinarian, and clinical
notes. Cancelled and no-show appointments create no visit. Staff correct
history through an audited edit rather than retroactively changing appointment
status.

### Q20. Staff changes and audit reasons

**Question:** How are reasons recorded for staff booking, rescheduling, and
cancellation, and may staff override conflicts?

**Decision:** Require a category and allow a concise optional note. Do not
permit conflict overrides. Rescheduling retains appointment identity and audits
the old and new times, actor, timestamp, and reason.

### Q25. Revising a request after offers begin

**Question:** What happens when an owner changes confirmed availability or
duration after offers have been made?

**Decision:** Release an active hold, retain prior offers and rejections as
audit history, and begin a new request revision with a cleared rejection count
and exclusion list. Editing the prose additionally requires renewed consent and
a new AI interpretation.

### Q26. Solver scope and immutability

**Question:** May solving a new request move an existing confirmed appointment?

**Decision:** Solve one request against the fixed current calendar. Never move
confirmed appointments automatically; only staff may reschedule through the
audited workflow.

### Q31. Staff booking versus owner holds

**Question:** Can staff override a slot held for another owner?

**Decision:** No. Holds block everyone except staff acting on behalf of the
holder. Staff may release a hold only through an audited cancellation or expiry
action, then may book the released slot if it remains available.

### Q32. Withdrawing an unscheduled request

**Question:** May owners withdraw requests before confirmation?

**Decision:** Yes, in every pre-confirmation state. Withdrawal immediately
releases a hold and removes active queue work, while retaining `CLOSED` audit
history. Withdrawn requests cannot reopen.

### Q33. Multiple upcoming appointments

**Question:** Can a pet have a new request while it already has a future
appointment?

**Decision:** Yes, provided future appointments do not overlap. Show existing
upcoming appointments before a new request is submitted, while retaining the
one-active-request-per-pet limit.

### Q34. Completion and no-show authority

**Question:** Who may mark an appointment completed or no-show, and can this
be reversed?

**Decision:** Only staff may set either status after scheduled end. Staff may
reverse it only through an audited correction that creates, removes, or
corrects the linked visit-history entry.

### Q38. Clinic-efficiency tie-breaker

**Question:** May the solver optimize calendar gaps or workload balance?

**Decision:** Clinic efficiency is only a final deterministic tie-breaker after
eligibility, owner preferences, and earliest time. It must not displace a
better-matching owner option.

### Q40. Visit-duration configuration depth

**Question:** Are duration choices global or specific to specialty/care type?

**Decision:** Use one clinic-wide duration set. The confirmed interpretation
selects one value; specialty-specific defaults and rules are deferred.

### Q43. Direct staff booking boundaries

**Question:** May staff book outside configured clinic or veterinarian
availability?

**Decision:** No. Staff must first record the appropriate availability
exception, then create the appointment through ordinary conflict checks.

### Q45. Other scheduling constraints

**Question:** What does "other relevant scheduling constraints" mean for this
POC?

**Decision:** Limit it to clinic/veterinarian availability, closures and leave,
booking horizon, duration, veterinarian and pet non-overlap, confirmed owner
hard windows, and rejected/held slots. Defer workload balancing, rooms,
equipment, buffers, daily caps, and travel time.

### Q48. Capacity changes after no match

**Question:** Should new capacity automatically trigger offers for waiting
requests?

**Decision:** No unsolicited offers are created. New capacity is considered
only when an owner requests another option or a staff member works the queue.

### Q52. Minimum notice for owner booking

**Question:** What minimum lead time applies to owner and staff bookings?

**Decision:** Owners require at least two hours' notice for scheduling and
offers. Staff may book any future slot, including one within two hours, but
cannot bypass availability or conflict rules.

### Q55. Request-state model

**Question:** Should the owner-facing lifecycle use explicit scheduling states?

**Decision:** Yes. Distinguish interpretation review, ready for a suggestion,
offer held, staff handling, confirmed, and closed, with the agreed staff-queue
sub-states. Show owners a clear plain-language status.

### Q56. Offer-rejection feedback

**Question:** Must owners explain why they reject an offer?

**Decision:** No. A short reason is optional, is not used in automated ranking,
and is visible to staff only as context.

### Q57. Existing future Visit records

**Question:** Should a future-dated legacy `Visit` reserve appointment
capacity?

**Decision:** No. Preserve legacy visits as unlinked historical records and do
not let them reserve capacity. Staff manually recreate a real future booking as
a proper appointment when needed.

### Q61. No-show documentation

**Question:** What documentation is required for a no-show?

**Decision:** Require a staff-selected no-show reason and allow an optional
note. Owners see only the plain "No-show" status.

## AI interpretation, consent, and emergency handling

### Q7. Confirmed interpretation fields

**Question:** Which interpreted fields may an owner edit?

**Decision:** Owners may edit visit reason, allowed/preferred/excluded
availability, preferred veterinarian, and requested duration from configured
options. Changes to care type, specialty, or urgency require staff review.
Editing original prose requires fresh AI consent and interpretation.

### Q22. AI data retention and supported language

**Question:** What AI records are retained, and what language/date phrasing is
supported?

**Decision:** Retain the original prose, consent record, model output, and
final confirmed interpretation, including consent timestamp and model
identifier. Support English only. Resolve relative dates using submission time
in the configured clinic time zone and show the resolved dates before
confirmation.

### Q23. Emergency guidance and detection

**Question:** How is emergency guidance maintained and how does detection work
when AI fails?

**Decision:** Use staff-configurable guidance with safe default copy and show it
permanently on the request form. Add a small, audited emergency-keyword screen
that may only raise priority and must never assure an owner that a condition is
non-urgent.

### Q24. Migration and service-time budget

**Question:** What database migration approach and AI/solver timeout budget
apply?

**Decision:** Adopt Flyway for the entire schema, baseline existing schema/data,
and version future changes uniformly. Allow 10 seconds for AI interpretation
and 5 seconds for solving; on failure or timeout persist the request and route
it to staff.

### Q27. Consent in staff fallback

**Question:** May staff submit previously declined owner prose to AI?

**Decision:** No. Staff complete the interpretation manually unless the owner
later gives explicit consent in the owner portal. Consent applies only to its
specific text revision.

### Q28. Retention and deletion

**Question:** How long are sensitive scheduling records retained, and what
happens when an owner or pet is removed?

**Decision:** Retain records while their owner and pet records exist. Defer
deletion and anonymization workflows from the POC, keep records staff-only, and
preserve them after cancellation or completion.

### Q44. Request-input boundaries

**Question:** What may an owner submit besides the selected pet and prose?

**Decision:** Require one owned pet and a plain-text description of 10-2,000
characters. Do not support attachments, markup, or other free-form fields.
Availability, duration, and veterinarian preference emerge in the reviewed
interpretation.

### Q47. Request-to-pet identity

**Question:** May owners change the selected pet on an in-progress request?

**Decision:** No. The selected pet is immutable after submission. Owners
withdraw and create a new request for another pet.

### Q60. Low-confidence or ambiguous clinical interpretation

**Question:** May uncertain care type, specialty, urgency, or timing proceed to
automated scheduling?

**Decision:** No. Route safety-critical uncertainty and unresolved timing to
staff review before automated suggestions. Owners may correct ordinary
availability and preference fields; staff resolve clinical or ambiguous
elements.

### Q63. Reprocessing previously consented text

**Question:** May staff re-run AI on unchanged owner prose?

**Decision:** Preserve the original AI result as authoritative. Staff correct it
manually or ask the owner to revise and re-consent. Retry automatically only
when the original interpretation failed before producing a result.

### Q64. AI uncertainty rule

**Question:** How does the system decide an interpretation is too uncertain?

**Decision:** Use deterministic completeness and validation rules rather than a
model-provided numeric confidence. Route to staff when required fields are
missing, contradictory, or outside configuration, or when output flags
specialty/urgency uncertainty or unresolved dates.

## Roles, accounts, authorization, and visibility

### Q6. Identity and account bootstrap

**Question:** How are staff and owner accounts provisioned?

**Decision:** Use local username/password authentication with BCrypt password
hashes. In local/demo/test profiles, automatically create accounts for existing
seed owners using lowercase first names as usernames and `<username>123` as
passwords (for example, `george/george123`), without a forced password change.
Also create `admin/admin123` as the seeded staff login. New accounts are
created by staff with one-time passwords and must change passwords on first
login; staff resets use the same mechanism.

### Q14. Staff roles and provisioning

**Question:** Is `admin` a separate administrator role, and can staff accounts
be administered in the POC?

**Decision:** Use one `STAFF` role and only the seeded `admin/admin123` staff
account. `admin` is a username, not a separate permission tier. Defer
staff-account administration and separate roles.

### Q29. Predictable POC credentials

**Question:** May predictable seeded credentials exist in every deployment?

**Decision:** No. Generate them only in local, demo, and test profiles. A
deployed profile must obtain initial staff credentials from required environment
configuration and must not create predictable owner passwords.

### Q30. Owner-visible history

**Question:** Which scheduling data may an owner see?

**Decision:** Owners see their own request status/history, current held offer,
rejected/expired-offer history, appointments, and completed visit history. Show
only their submitted data, appointment time/status, and veterinarian names and
specialties. Never show staff notes, audit reasons, solver details, or calendar
availability.

### Q39. New-owner usernames and temporary passwords

**Question:** How do staff provision credentials for new owners and resolve
first-name collisions?

**Decision:** Staff choose a unique username, with a normalized first-name
suggestion and numeric suffix where needed. Generate a random one-time password,
display it once for staff to communicate securely, expire it after seven days,
and require a password change at first use. Resets use the same flow.

### Q49. Owner profile and pet management

**Question:** May owners edit their contact and pet records?

**Decision:** Owners may view their own profile and pets but cannot edit them
in the POC. Staff retain full owner/pet administration.

### Q50. Veterinarian and specialty administration

**Question:** May staff manage veterinarian and specialty catalogs?

**Decision:** No. Staff manage schedules, exceptions, and clinic settings only.
The existing veterinarian and specialty catalog, including assignments, remains
the source of matching eligibility.

### Q51. Public application surface

**Question:** Which pages remain public after authentication is introduced?

**Decision:** Require authentication for all operational pages. Owners receive
only their authorized views and staff receive clinic-management views. A
non-operational landing/login page may remain public.

### Q62. Authentication safeguards

**Question:** What password and session safeguards apply to non-demo accounts?

**Decision:** New passwords require a minimum of 6 characters. Store only BCrypt
hashes, rate-limit failed logins, rotate session identifiers at login and
password change, expire sessions after 30 minutes of inactivity, and invalidate
all owner sessions on password reset.

## Staff queue, calendar, and clinic configuration

### Q15. Fallback queue workflow

**Question:** What queue states and ownership model prevent requests from being
lost or handled twice?

**Decision:** Use `NEW`, `IN_REVIEW`, `AWAITING_OWNER`, `RESOLVED`, and `CLOSED`.
Requests are initially visible to all staff; a staff member claims one before
working it. Sort suspected emergencies first, then oldest request. Owners see
only a plain status, not staff notes or assignments.

### Q16. Staff-assisted acceptance

**Question:** Must queued offers be accepted in the owner portal, or may staff
confirm them directly?

**Decision:** Support both. Staff may offer a held slot for owner acceptance or
directly book after recording that they obtained owner agreement. Direct booking
without agreement is limited to staff-initiated operational appointments.

### Q21. Availability exceptions and existing bookings

**Question:** What is the precedence among shifts, exceptions, leave, and
closures, and how are affected bookings handled?

**Decision:** Clinic closure overrides everything, followed by veterinarian
leave, date-specific exception, then recurring shift. Block changes that
conflict with confirmed appointments until staff reschedule or cancel them
deliberately.

### Q35. Configuration changes versus confirmed requests

**Question:** Does changing a named period such as "afternoon" reinterpret
already confirmed owner availability?

**Decision:** No. Resolve named periods into concrete local windows at
confirmation and snapshot them on the request. Configuration changes apply only
to new or revised requests.

### Q36. Configuration changes versus active holds

**Question:** How may staff make an availability change that affects a current
hold?

**Decision:** Block the change until staff deliberately releases the affected
hold with an audit reason. The owner then sees the offer is unavailable and may
request another option.

### Q37. Clinic time-zone changes

**Question:** May staff change the clinic time zone after scheduling has begun?

**Decision:** Only before the first request or appointment exists. Thereafter it
is immutable in the POC. Store instants with the configured zone to handle DST
unambiguously.

### Q41. Legacy Visit flow

**Question:** Should the current direct future-visit flow remain alongside
appointments?

**Decision:** No. Appointments are the only way to schedule future care.
`Visit` is historical care recorded upon appointment completion; preserve
existing visit data as history.

### Q42. Availability-shape rules

**Question:** What structure and validation applies to recurring shifts and
date-specific exceptions?

**Decision:** Support multiple non-overlapping, same-day split-shift intervals
on the 15-minute grid; defer overnight shifts. Closures are full local dates or
date ranges, not partial-day intervals.

### Q46. Audit-log coverage

**Question:** What events require durable audit history and who may read it?

**Decision:** Record actor, timestamp, action, target, and before/after values
for staff changes, queue claims, request revisions, consent decisions, AI/solver
outcomes, offers/holds, acceptances/rejections, and appointment lifecycle
events. Staff may read it; owners see only ordinary request and appointment
history.

### Q53. Stale staff claims

**Question:** How can another staff member take over a claimed fallback item?

**Decision:** Any staff member may explicitly reassign or unclaim it at any
time, with an audit reason. Do not automatically expire claims in the POC; keep
the queue transparent to all staff.

### Q58. Configuration guardrails

**Question:** What bounds constrain staff settings?

**Decision:** Permit a 1-365-day booking horizon, 1-60-minute holds, and
non-overlapping named periods wholly within one local day. Keep the 15-minute
scheduling grid fixed.

### Q59. All availability changes versus bookings

**Question:** Does the conflict rule cover recurring shifts and exceptions as
well as closures and leave?

**Decision:** Yes. Block every availability change that conflicts with a
confirmed appointment until staff deliberately reschedule or cancel it.

## Technical and persistence decisions

### Q54. Supported databases

**Question:** Must scheduling work across all currently supported databases?

**Decision:** Yes. Preserve H2, MySQL, and PostgreSQL support. Use portable
Flyway migrations where practical and validate hold and booking concurrency
against each supported production-style database.

## Consequences captured by these decisions

- Owners receive one held offer at a time and never see a full veterinarian
  availability calendar.
- The application never silently loses a request: automated failures,
  unsupported specialty, no match, ambiguity, and expired offer limits route
  to staff as appropriate.
- Confirmed appointments are fixed scheduling commitments and cannot be moved
  by the solver or invalidated by a configuration edit.
- The POC explicitly excludes external notifications, waitlists, owner
  self-registration, owner record editing, staff-account administration,
  veterinarian/specialty catalog administration, attachments, overnight
  shifts, and resource scheduling beyond one veterinarian.
