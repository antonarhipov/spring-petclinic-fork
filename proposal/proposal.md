# Smart Appointment Scheduling

## Purpose

Add user-facing smart appointment scheduling to PetClinic.

An authenticated pet owner describes the reason for a visit and their
availability in free-form English. The application uses AI to produce a
structured interpretation, lets the owner review and correct it, and uses
Timefold to find a suitable appointment.

Owners never see the clinic's complete availability calendar. The application
offers one suitable slot at a time and briefly holds that slot while the owner
decides. Staff manage the clinic calendar and handle any request that cannot
proceed safely or automatically.

This document is the authoritative feature specification. Existing application
behavior that conflicts with it is incomplete implementation, not an accepted
change to the feature.

## Scope

### In scope

- Authenticated owner scheduling for an owned pet.
- AI interpretation with explicit, revision-specific consent.
- Owner review and correction of the structured interpretation.
- One held appointment suggestion at a time.
- Owner appointment and request history.
- Staff fallback, direct scheduling, calendar management, and appointment
  lifecycle management.
- Clinic settings, veterinarian shifts, exceptions and leave, and clinic
  closures.
- Local username/password accounts for owners and staff.
- H2, MySQL, and PostgreSQL.
- English UI and English scheduling requests.

### Out of scope

- Owner self-registration and self-service password recovery.
- A full availability calendar for owners.
- External notifications.
- Waitlists and unsolicited offers when capacity changes.
- Multiple clinics.
- Rooms, equipment, assistants, travel time, buffers, daily caps, and other
  resources beyond one veterinarian.
- Owner editing of pet records.
- Staff administration of the veterinarian and specialty catalogs.
- Staff-account administration and separate staff permission tiers.
- Attachments, markup, and overnight veterinarian shifts.
- Deletion and anonymization of scheduling history.
- Login throttling, AI request-rate limits, and product or operational
  telemetry.
- Calendar-file export.
- Formal accessibility, responsive-device, or browser-support acceptance
  requirements for this POC.
- Gradle and non-English localization.

## Actors and authorization

### Owner

An owner:

- may see and act only on their own profile, pets, requests, appointments, and
  visit history;
- may see veterinarian names and specialties associated with their own
  scheduling flow;
- may edit their own first name, last name, address, city, and telephone;
- may not change their username, role, credentials, or pet records through
  profile editing;
- never sees clinic-wide availability, other owners' data, staff notes, audit
  reasons, raw AI output, solver details, or detailed clinical notes.

Owner profile changes are audited. Updated contact details become immediately
visible to staff handling an active request.

### Staff

All clinic staff use one `STAFF` role. Staff may:

- manage owners, pets, owner accounts, clinic settings, and availability;
- view the complete clinic scheduling calendar;
- claim and work fallback requests;
- act on behalf of any owner and pet;
- book, reschedule, cancel, complete, and mark appointments as no-shows;
- view staff audit history and protected AI records.

The system supports multiple staff identities so queue ownership and
reassignment remain meaningful. Staff-account administration is out of scope.

### Public surface

Only a non-operational landing page, login page, and required static assets may
be public. Every operational page requires authentication. After login:

- owners land on their appointment dashboard;
- staff land on the fallback queue.

Navigation and return links must remain within the authenticated user's
authorized area.

## Owner experience

### Appointment dashboard

The owner dashboard is the persistent home for scheduling. It contains:

1. requests and offers that need owner action;
2. other active requests;
3. upcoming appointments;
4. recent request and appointment history;
5. access to complete history;
6. a primary **Schedule appointment** action.

Each item has one clear primary action. An active workflow survives refresh,
back navigation, logout, session expiry, and later login.

Starting a request begins with pet selection. Before submission, show the
selected pet's existing upcoming appointments. A pet may have several future
appointments provided they do not overlap, but may have only one active
scheduling request. Attempting to start another request opens the existing
request instead of creating a duplicate.

### Request form

The form shows, in order:

- permanent urgent-care guidance and clinic contact information;
- the selected pet;
- a short example of useful availability prose;
- one plain-text description field;
- a visible 10-2,000-character count and validation;
- the AI-consent choice and manual-routing alternative;
- one explicit submission action.

The description normally includes the reason for the visit and when the owner
can, prefers to, or cannot attend. It accepts plain text only. Attachments and
markup are not supported.

The selected pet becomes immutable after submission. Scheduling another pet
requires withdrawing the request and creating a new one.

Unsubmitted prose is not saved on the server or in browser storage. If the
owner attempts to leave after changing the form, warn that the text will be
lost.

### Consent

AI consent is unchecked by default and applies only to the exact submitted text
revision. Before consent, explain:

- which text and contextual data will be sent to AI;
- why AI is used;
- which records are retained;
- who can see them;
- that declining AI still submits the text to clinic staff for manual handling.

If the owner declines, persist the request and route it to staff. Staff must not
later submit that declined text to AI. The owner may subsequently revise the
text and give new consent.

### Asynchronous processing

Persist the request before invoking AI or the solver. Interpretation and
matching run asynchronously, so the owner may leave and return.

The UI displays a named progress state and refreshes it without creating
duplicate work. Background polling must not extend the authenticated session's
inactivity deadline.

Allow one internal AI retry only when the first attempt produced no usable
result. Re-run solving only after a detected concurrent calendar change.
Otherwise, timeout or failure routes the persisted request to staff. Once a
request has entered staff fallback, do not expose an owner retry button.

### Interpretation review

Before matching, the owner reviews:

- original prose;
- visit reason;
- allowed, preferred, and excluded availability;
- resolved concrete dates for relative phrases;
- requested duration from the clinic-configured duration set;
- optional preferred veterinarian;
- care type, specialty, and urgency.

The page clearly separates:

- **Edit interpreted details**, which does not invoke AI again; and
- **Revise original text**, which creates a new text revision and requires new
  consent and interpretation.

Owners may edit the visit reason, availability, duration, and preferred
veterinarian. Care type, specialty, and urgency use plain-language labels,
make no diagnostic claims, show no numeric confidence, and are read-only for
owners. Safety-critical uncertainty in these fields routes the request to
staff.

#### Availability editor

Use a structured, list-based editor. Each row records:

- whether the window is allowed, preferred, or excluded;
- a one-off local date or weekly recurrence;
- for recurrence, a bounded date range and weekday selection;
- local start and end time.

Explicit \"cannot\" statements and confirmed allowed windows are hard
constraints. Excluded windows subtract from allowed windows. Preferred windows
must fall within allowed windows and are ranking preferences. \"If necessary\"
denotes a lower-ranked allowed fallback. Contradictions block confirmation.
Never offer a slot outside confirmed allowed windows.

Named periods such as \"morning\" and \"afternoon\" resolve to concrete local
time windows at confirmation and are snapshotted on that request revision.
Later configuration changes do not reinterpret confirmed requests.

### Owner-facing request states

The internal lifecycle distinguishes:

- interpretation review;
- ready for a suggestion;
- offer held;
- staff handling;
- confirmed;
- closed.

The owner sees plain-language states:

- **Interpreting request**
- **Review interpretation**
- **Finding an appointment**
- **Appointment offered**
- **With clinic staff**
- **Confirmed**
- **Closed**

Each state presents one appropriate primary action. Staff-fallback messages
explain the broad reason in plain language: manual choice, technical failure,
clinical uncertainty, unsupported specialty, or no automatic match. They never
expose raw enums, model details, or staff notes.

Owner history shows ordinary events: submission, revision, interpretation
confirmation, offer, rejection or expiry, fallback, appointment confirmation,
withdrawal, and closure. It does not expose internal audit data.

### Matching and suggestions

Timefold solves one request against a fixed snapshot of the current calendar.
It never moves a confirmed appointment. Only staff may reschedule an
appointment through the audited workflow.

Hard eligibility includes:

- clinic and veterinarian availability;
- closures, leave, and date-specific exceptions;
- booking horizon and appointment duration;
- veterinarian and pet non-overlap;
- confirmed owner allowed and excluded windows;
- current holds and appointments;
- rejected and expired offers;
- required veterinarian specialty;
- the owner's two-hour minimum scheduling notice.

After hard eligibility, rank:

1. confirmed owner time preferences;
2. preferred veterinarian;
3. lower-ranked \"if necessary\" windows;
4. earliest suitable time;
5. clinic efficiency only as a final tie-breaker;
6. a stable veterinarian/time tie-breaker.

Clinic efficiency must never displace an option that better matches the
owner's preferences. General-care requests may use any available veterinarian.
Specialty requests require a veterinarian with that specialty.

### Held offer

Offer exactly one slot. The offer page shows:

- pet;
- veterinarian name and specialty;
- full appointment date;
- start and end time;
- duration;
- configured clinic time zone;
- a short, non-sensitive explanation of why the slot matches;
- exact hold-expiry time;
- a server-derived countdown;
- remaining automatic attempts.

The server expiry instant is authoritative. Acceptance succeeds only after an
atomic check proves that the hold is still active and the slot remains valid.
The clearly labelled **Confirm appointment** action immediately creates a
confirmed appointment and needs no second dialog.

Rejecting:

- requires confirmation that the slot will not be offered again;
- may include an optional reason visible to staff only;
- never uses that optional reason in automated ranking;
- releases the hold;
- permanently excludes that exact veterinarian/time for the current revision;
- counts toward the five-offer limit.

An expired offer is released, excluded, and counted in the same way. Expiry
does not automatically produce another offer. Show an explanation and a
**Find another time** action. At the acceptance boundary, an expired or
otherwise unavailable offer returns the current state and a clear next action.

After five rejected or expired offers, route the request to staff.

### No match and revision

If no eligible slot exists:

- explain that no automatic match is currently available;
- add the request to the staff queue;
- allow the owner to revise and reconfirm availability.

New capacity does not create an unsolicited offer. It is considered only when
the owner explicitly requests another option or staff work the queue.

Revising confirmed availability or duration after offers have started:

- releases an active hold;
- preserves previous revisions, offers, and rejections as audit history;
- creates a new request revision;
- clears the rejection count and automatic exclusion list;
- returns an existing queue item to `NEW`;
- invalidates stale staff edits.

Revision is allowed until an offer or appointment created by staff exists.
Revising original prose additionally requires new AI consent and
interpretation.

### Withdrawal

Owners may withdraw in every pre-confirmation state. Withdrawal:

- requires confirmation;
- immediately releases a hold;
- removes active queue work;
- retains closed audit and ordinary owner history.

A withdrawn request cannot reopen.

### Confirmed appointments and cancellation

Appointment acceptance immediately confirms the booking. The confirmation
view shows the pet, veterinarian and specialty, visit reason, full clinic-local
date and time, duration, and status. It links to the dashboard and cancellation
flow. Calendar-file export is not included.

Display appointment times in the configured clinic zone using a full,
unambiguous format such as:

> 12 November 2026, 14:30-15:00 (Europe/Amsterdam)

Owners may cancel their own booked appointment until its start time.
Cancellation:

- requires confirmation showing the affected appointment;
- accepts an optional owner reason;
- does not reopen the original request;
- offers a **Schedule another appointment** action afterward.

### Session and error recovery

Sessions expire after 30 minutes of user inactivity. Warn before expiry. After
authentication, return the user to the persisted request. Never replay an
interrupted accept, reject, withdrawal, cancellation, or other mutation.

Every action returns a human-readable result and the current canonical state.
Preserve recoverable form input. Distinguish validation errors, expired offers,
concurrent calendar changes, technical failures, and expired sessions. Actions
must be safe to retry. Never display Java exceptions, database errors, raw
internal state names, or other implementation details.

Irreversible owner actions require a clear confirmation:

- reject offer;
- withdraw request;
- cancel appointment.

## Emergency handling

Urgent-care guidance is staff-configurable and has safe default copy. Clinic
phone number and contact hours are configurable as well. Show this information
on the request form and throughout an emergency or staff-contact flow.

The guidance must state that the portal and fallback queue are not monitored
emergency-response channels.

Before AI interpretation, run a small audited keyword screen that may raise
priority but can never classify a request as non-urgent. AI may also flag
urgency.

When either mechanism suspects an emergency:

- stop automated scheduling;
- immediately show urgent-care guidance;
- create a high-priority staff-queue item;
- do not imply that waiting for staff is an adequate response.

Staff may clear the emergency flag only after recording a reason and validating
the clinical interpretation. The owner must then reconfirm the interpretation
before automated scheduling resumes.

## Staff experience

### Staff navigation

Staff land on the fallback queue. Primary navigation provides:

- fallback queue;
- clinic calendar and appointments;
- clinic configuration and veterinarian availability;
- owner, pet, and owner-account administration.

### Fallback queue

Queue states are:

- `NEW`
- `IN_REVIEW`
- `AWAITING_OWNER`
- `RESOLVED`
- `CLOSED`

The normal staff-assisted offer flow is:

`NEW -> IN_REVIEW -> AWAITING_OWNER -> RESOLVED`

Direct booking may move `IN_REVIEW` directly to `RESOLVED`. Withdrawal or
an explicit staff closure may move an active item to `CLOSED`.

Requests are visible to all staff but must be claimed before editing. Sort
suspected emergencies first and otherwise oldest first. The queue list shows:

- urgency;
- request age;
- owner and pet;
- fallback reason;
- state;
- assignee;
- last update.

Provide filters for state, assignee, urgency, and fallback reason.

Any staff member may explicitly reassign or unclaim work with an audit reason.
Claims do not expire automatically.

The queue detail view shows:

- current owner contact details;
- selected pet and original prose;
- consent record;
- protected AI interpretation when one exists;
- confirmed or staff-edited structured fields;
- request and offer history;
- queue ownership;
- audit history;
- actions to edit, record contact, create an offer, direct-book, reassign,
  unclaim, or close.

Owner revision returns the queue item to `NEW` and marks it as updated. Any
staff submission based on a stale revision fails without partially saving.

### Contacting the owner

External notifications are out of scope. Staff must contact an owner outside
the application before creating a ten-minute portal offer. Create the hold only
when the owner is ready to review it.

If the owner is unreachable:

- set `AWAITING_OWNER`;
- do not reserve a slot;
- show the owner that the clinic needs contact and display the clinic phone
  number;
- do not auto-close the request.

Staff may close an unreachable or otherwise unresolvable request only
explicitly and with a recorded reason.

### Staff interpretation and owner agreement

Staff manually complete interpretation when consent was declined, AI failed,
or clinical or timing uncertainty requires review. They must not run AI again
on unchanged prose that already produced a result. They may correct it
manually or ask the owner to revise and re-consent.

A manually completed interpretation must be confirmed by the owner before a
portal offer. For direct booking, staff record that the owner agreed to both
the structured interpretation and exact appointment. Direct booking without
owner agreement is limited to staff-initiated operational appointments.

### Staff calendar

Provide:

- a week view;
- veterinarian filtering;
- a tabular alternative;
- explicit forms rather than drag-and-drop mutations.

The calendar shows:

- confirmed appointments;
- active holds and their expiry;
- veterinarian recurring shifts;
- date-specific exceptions;
- veterinarian leave;
- clinic closures.

Use limited labels in the calendar grid. Reveal full owner and pet details only
after selection.

Staff may ask the solver for a ranked suggestion or manually choose a
compliant slot from the calendar. Both paths use the same availability,
specialty, horizon, hold, and conflict validation.

Staff may not:

- override an appointment or hold conflict;
- book outside clinic or veterinarian availability;
- displace a hold belonging to another owner.

Staff acting for the hold's owner may complete that booking. Otherwise, a hold
can be released only through an audited, confirmed action or expiry. To book
outside a normal shift, staff must first create the appropriate availability
exception and then pass ordinary conflict checks.

### Availability changes

Every availability change is validated against appointments and active holds.
This applies to recurring shifts, date exceptions, leave, and closures.

When blocked, show all conflicting appointments and holds with links. Save
nothing until staff deliberately reschedule or cancel appointments, or release
holds with an audit reason.

Precedence is:

1. clinic closure;
2. veterinarian leave;
3. date-specific exception;
4. recurring shift.

### Direct booking, rescheduling, and cancellation

Each staff-created change requires a reason category and permits a concise
optional note. There are no conflict overrides.

Rescheduling:

- retains appointment identity;
- requires recorded owner agreement;
- audits old and new times, veterinarian, actor, timestamp, and reason;
- uses a review step before commit.

Staff cancellation:

- requires confirmation;
- records an internal category and optional note;
- includes a separate owner-facing explanation;
- records a contact attempt when prior owner agreement is impossible.

Owners see the owner-facing explanation, not internal audit reasons.

### Completion, no-show, and correction

Only staff may mark an appointment completed or no-show, and only after its
scheduled end.

Completion uses a dedicated form and creates one immutable linked visit-history
entry containing:

- actual completion instant;
- assigned veterinarian;
- owner-visible visit summary;
- staff-only detailed clinical notes.

No-show uses a dedicated form, requires a selected reason, and permits an
optional note. Owners see only the plain **No-show** status. Cancelled and
no-show appointments create no visit entry.

Completion and no-show require confirmation. Reversal uses a dedicated audited
correction form that requires a reason and previews the visit record that will
be created, corrected, or removed. Do not rewrite historical appointment state
without that correction event.

## Scheduling and clinic rules

### Reserved resources and conflicts

Each appointment reserves exactly one veterinarian and one pet for its entire
duration. Prevent overlapping holds and appointments for both the veterinarian
and pet. Rooms, equipment, assistants, and other resources are out of scope.

Hold creation, appointment acceptance, direct booking, and rescheduling must
perform atomic conflict validation. Differently starting intervals that overlap
are conflicts; equality of start time alone is not sufficient.

### Default clinic policy

Defaults are:

- clinic time zone: `Europe/Amsterdam`;
- clinic operating window: weekdays 09:00-17:00;
- duration set: 15, 30, 45, and 60 minutes;
- appointment starts: fixed 15-minute grid;
- booking horizon: 90 days;
- held-offer duration: 10 minutes;
- owner minimum notice: 2 hours.

Staff may book any future time, including within two hours, but cannot bypass
availability or conflict rules.

No veterinarian shifts exist in normal profiles until staff configure them.
The explicit demo profile is handled separately under **Demo data**.

### Configuration guardrails

- Booking horizon: 1-365 days.
- Hold duration: 1-60 minutes.
- The 15-minute grid is fixed.
- Appointment durations come from one clinic-wide configured set.
- Named periods must be non-overlapping and remain within one local day.

### Veterinarian availability

Each veterinarian may have multiple non-overlapping, same-day recurring shift
intervals on the 15-minute grid. Split shifts are supported; overnight shifts
are not.

Date-specific exceptions and leave override recurring shifts. Clinic closures
cover full local dates or date ranges and override all veterinarian
availability.

The clinic time zone may change only before the first scheduling request or
appointment exists. Store instants together with the configured zone so DST
transitions remain unambiguous.

## AI interpretation, privacy, and logging

### AI integration

Use:

- Spring AI 2.0.1;
- Ollama with a configurable model in `application.properties`;
- a structured output schema;
- a 10-second AI interpretation budget.

AI receives only:

- owner prose;
- pet type;
- clinic time zone;
- configured duration options and named periods;
- public veterinarian names and specialties.

Do not send owner contact details, account data, other pets, or medical
history.

The model produces structured data only. Owners and staff never see raw,
unvalidated model output. Validate completeness, contradictions, configured
values, dates, and safety flags deterministically. Do not trust a
model-provided numeric confidence.

Route to staff when required values are missing, contradictory, outside clinic
configuration, clinically uncertain, or unresolved.

Resolve relative dates from the request submission instant in the clinic time
zone and show the concrete dates before confirmation.

### Retained AI and consent records

Retain:

- original prose and every revision;
- consent decision and timestamp for each revision;
- model identifier;
- protected raw model output;
- validated structured output;
- final owner-confirmed or staff-confirmed interpretation.

Preserve the first successful AI result for a text revision as authoritative.
Staff correct it manually or request revised, newly consented text. Retry AI
automatically only when the original attempt produced no result.

### Application logging

Use a dedicated debug logger for detailed AI and solver tracing.

In local, demo, and test profiles, the detailed DEBUG events must include:

- request or correlation identifier;
- exact outbound prompt;
- raw AI response;
- validated interpretation;
- data passed to the solver;
- solver outcome.

Apply configured size limits and escape line breaks and control characters to
prevent log injection.

Raw prompts and responses must not be emitted in deployed profiles, even when
ordinary application debug logging is enabled. Deployed logs contain metadata
only: identifiers, model, timing, validation outcome, solver outcome, and
failure category.

No product analytics or aggregate operational telemetry is required.

## Accounts and session security

### Authentication

Use local username/password authentication and BCrypt password hashes. Minimum
password length is six characters. No composition rule or failed-login
throttling is required for this POC.

Rotate the session identifier at login and password change. Expire sessions
after 30 minutes of user inactivity. Password reset invalidates all active
sessions for that owner.

### Owner account provisioning

Staff create owner accounts. Staff choose a unique username; the UI suggests a
normalized lowercase first name and numeric suffix where necessary.

For a new account:

- generate a random one-time password;
- display it once for staff to communicate securely;
- expire it after seven days;
- require a password change at first use.

Staff password resets use the same temporary-password flow and also require a
change at first use.

### Seeded and deployed accounts

In local, demo, and test profiles:

- create accounts for existing seed owners;
- derive usernames from lowercase first names;
- use `<username>123` passwords, for example
  `george/george123`;
- create `admin/admin123` as the primary staff account.

Seeded accounts represent existing accounts and do not require a first-login
password change.

Predictable credentials must never be created in a deployed profile. A
deployed profile obtains its initial staff credentials from required
environment configuration and fails safely when they are absent.

The demo profile may seed a second documented staff account to demonstrate
queue claiming and reassignment. Normal profiles seed only `admin`.

## Persistence, audit, and data lifecycle

### Database migrations

Use Flyway for the entire schema. Baseline existing schema and data, then
version every feature change through Flyway. Preserve support for H2, MySQL,
and PostgreSQL and validate concurrency against each production-style
database.

### Durable audit

Use append-only audit events. Record actor, timestamp, action, target, outcome,
and relevant before/after structured values for:

- staff configuration and availability changes;
- staff appointment changes and lifecycle corrections;
- queue claims, unclaims, and reassignments;
- profile changes;
- request submission, revision, and withdrawal;
- consent decisions;
- AI and solver outcomes;
- offers, holds, expiry, acceptance, and rejection;
- appointment confirmation, cancellation, rescheduling, completion, no-show,
  and correction.

Audit events reference protected AI artifacts rather than duplicating raw
prompts, raw responses, or owner prose. Staff may read durable audit data.
Owners see only ordinary request, appointment, and visit history.

### Retention and deletion

Retain scheduling, consent, AI, audit, appointment, and visit records while
their owner and pet records exist, including after cancellation, withdrawal,
completion, or no-show.

Deletion and anonymization workflows are out of scope. Block deletion of an
owner or pet when scheduling history exists and explain the reason. A future
deactivation capability may be added separately.

## Legacy visits

Appointments are the only way to schedule future care. `Visit` represents
historical care created through appointment completion.

Existing legacy visits remain historical records. A future-dated legacy visit
does not reserve capacity. Provide a one-time staff reconciliation list with a
**Create appointment** action. Preserve the legacy visit and record its link to
the replacement appointment.

## Demo data and required scenarios

Normal profiles use the default clinic settings but contain no veterinarian
shifts until staff configure them.

The explicit demo profile provides representative:

- veterinarian shifts and split shifts;
- clinic closures;
- date-specific exceptions and leave;
- confirmed appointments;
- active holds;
- owner requests in different lifecycle states;
- fallback queue items;
- owner and staff accounts.

Demo data and controllable failure configuration must support these end-to-end
scenarios:

1. successful interpretation, confirmation, suggestion, and booking;
2. offer rejection followed by another suggestion;
3. offer expiry and explicit request for another option;
4. no automatic match and owner revision;
5. consent decline and manual staff interpretation;
6. AI failure and staff fallback;
7. emergency detection and staff clearance;
8. staff-assisted portal offer;
9. staff direct booking with recorded agreement;
10. owner cancellation;
11. staff rescheduling and cancellation;
12. completion, no-show, and audited correction;
13. prevention of veterinarian, pet, appointment, and hold conflicts;
14. queue claiming and reassignment by two staff identities;
15. legacy future-visit reconciliation.

## Technical implementation constraints

- Use Maven only and remove Gradle support.
- Use Timefold 2.5.0 with a five-second solving budget.
- Use Spring AI 2.0.1 and configurable Ollama.
- Make every workflow reachable through application UI routes; no API-only
  workflow is sufficient.
- Keep only English message resources.
- Remove unrelated functionality such as `CrashController`.
- Use server-side validation for every browser constraint and preserve submitted
  form values after a recoverable error.
- Make mutating operations idempotent or otherwise safe against duplicate
  submission.

## Acceptance summary

The feature is complete only when:

- owners can finish or resume every scheduling flow without seeing another
  owner's data or the clinic's full availability;
- consent, interpretation, correction, emergency handling, fallback, and
  held-offer behavior follow this specification;
- no eligible offer violates owner hard windows, specialty, clinic
  availability, horizon, notice, or veterinarian/pet conflict rules;
- holds and booking acceptance remain correct under concurrent requests;
- all staff scheduling and lifecycle operations are available in the UI and
  durably audited;
- every failure state preserves the request and presents a clear next action;
- predictable credentials and raw AI payload logs are confined to the
  explicitly permitted profiles;
- the required demo scenarios can be exercised from seeded data.
