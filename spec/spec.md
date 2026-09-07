# Smart Appointment Scheduling - Use-Case Specification

## 1. Feature summary

Smart Appointment Scheduling lets a pet owner describe their availability in English, review a structured
interpretation of that description, and work through one held appointment suggestion at a time. Suggestions account
for the owner's stated windows, veterinarian availability, current appointments and holds, specialty needs, clinic
settings, and the owner's earlier rejections. When automation cannot or should not continue, clinic staff can take over
without leaving the owner in a dead end.

This is a proof of concept for one clinic, one persistent H2 runtime database, and the Maven build. It also introduces
authenticated owner and staff workspaces, a staff scheduling queue, a day calendar, appointment lifecycle management,
and editable scheduling configuration while retaining the established PetClinic owner, pet, veterinarian, and visit
workflows for staff.

`spec/decisions.md` D1-D36 are resolved inputs to this specification. If an older proposal statement can be read in
more than one way, the behavior stated here reflects the decision log.

## 2. Scope and resolved decisions

### In scope

- Form-login authentication for seeded owner and staff accounts, role-specific landing pages and navigation, visible
  signed-in identity, logout, and enforced owner-data isolation across the whole application.
- An owner workspace for read-only pet details, past and upcoming appointments, and active scheduling requests.
- Consent-based, asynchronous AI interpretation of English free text into a complete, immutable structured version.
- Deterministic single-request matching, one held suggestion at a time, persistent rejections, and safe concurrent
  booking behavior.
- Staff-created requests, staff-authored interpretation versions, staff suggestions, direct bookings, and a queue that
  exposes both requests needing staff and all other open requests.
- A staff day calendar showing opening hours, veterinarian availability, appointments, holds, and remaining capacity.
- Staff booking, rescheduling, cancellation, completion, and no-show handling, including creation of a linked visit on
  completion.
- Editable clinic hours and scheduling settings, veterinarian weekly working blocks, date exceptions and leave, and
  clinic-wide closures.
- Exact normative seed data, localized user-visible text, deterministic time-dependent behavior, and the verification
  evidence stated in the use-case guarantees.

### Resolved behavioral choices

- Availability is closed by default: only explicit preferred or allowed windows are bookable; exclusions only remove
  time. Windows can recur by weekday or name a concrete date.
- Ranking is strict and lexicographic: preferred window, preferred veterinarian, earliest start, fewest appointments
  for that veterinarian that day, then lowest veterinarian id.
- Suggestions begin after the configured minimum lead period and use a 15-minute start grid. The AI estimate is stored
  unchanged and bounded only when matching.
- A held suggestion has no timer. It ends only through an owner or staff action, a superseding suggestion, or a
  configuration change that invalidates it.
- A rejected veterinarian-and-time remains rejected for the entire request, including after the owner changes the text
  and obtains another interpretation.
- Interpretation is asynchronous. At most two interpretation jobs run concurrently; accepted additional jobs wait for
  a worker. A job has a 120-second overall deadline, a five-second connection timeout, and no automatic retry.
- The AI receives only the owner's free text and the enumerated clinic context described in UC-3. Staff never trigger
  AI interpretation and never consent for an owner.
- Staff can override owner windows, specialty, booking horizon, and lead time, but never clinic opening hours,
  veterinarian working time, or the no-overlap rule.
- Clinic-local dates and times are authoritative. The configured zone determines "today"; daylight-saving changes do
  not shift displayed appointment times.
- Exact concurrency mechanisms are deferred to technical rules, but the observable guarantees are fixed: at most one
  active request per pet and at most one winning hold or booking for a veterinarian at a time.
- Runtime support is H2 and Maven only. MySQL and PostgreSQL profiles, Gradle builds, Docker Compose, Kubernetes
  deployment artifacts, and their associated tests are not part of the delivered repository. The README documents
  this support boundary and the file-backed runtime database.

## 3. Actors and domain terms

### Actors

- **Owner** - an authenticated person linked to exactly one seeded owner record. The owner can see and act only on that
  record's pets, requests, and appointments.
- **Staff** - an authenticated clinic worker with access to all owners, pets, veterinarians, requests, appointments,
  schedules, and clinic settings.
- **AI interpreter** - an external model service that converts consented free text and non-personal clinic context into
  schema-constrained structured data.
- **Health monitor** - an anonymous client that checks only application health.
- **Clock** - the trusted source of current time in the clinic's configured time zone.

Veterinarians are scheduling resources, not authenticated actors.

### Domain terms

- **Scheduling request** - the owner/pet-specific lifecycle that starts with free text and ends as Accepted or
  Abandoned. A pet has at most one active request.
- **Interpretation version** - an immutable structured reading of the request, with origin `AI` or `STAFF`. The latest
  applicable version is current; earlier versions remain in history.
- **Preferred window** - an explicitly stated window that is feasible and ranks ahead of an allowed window.
- **Allowed window** - an explicitly stated feasible window that ranks below a preferred window.
- **Excluded window** - an explicitly stated window that can only remove time from preferred or allowed windows.
- **Window** - either a recurring weekday or a concrete date, plus a start time and end time in clinic-local time.
- **Suggestion** - exactly one veterinarian, local date, start time, duration, and rank reason offered to the owner.
- **Hold** - the appointment record that exclusively reserves an offered suggestion until the suggestion ends.
- **Rejection** - the durable exclusion of one exact veterinarian-and-time after the owner asks for another option.
- **With staff reason** - the recorded reason a request entered the staff queue: consent declined, AI unavailable,
  unmatched specialty, no slots available, owner requested staff, hold released by staff, schedule changed, or request
  created by staff.
- **Active request** - a request in any state other than Accepted or Abandoned.
- **Staff constraints** - clinic opening hours, the selected veterinarian's effective working blocks, and no overlap
  with an appointment or hold.
- **Effective working blocks** - a veterinarian's recurring weekly blocks after date exceptions, leave, and clinic
  closures are applied.

## 4. Use-case map

| ID | Actor goal | Primary actor | Relations |
|---|---|---|---|
| UC-1 | Sign in and enter the permitted workspace | Owner or staff | None |
| UC-2 | Review owned pets and scheduling activity | Owner | Requires UC-1 |
| UC-3 | Schedule a pet appointment through the guided flow | Owner | Requires UC-1 |
| UC-4 | Resolve a scheduling request that needs staff | Staff | Requires UC-1 |
| UC-5 | Operate the clinic calendar and appointment lifecycle | Staff | Requires UC-1 |
| UC-6 | Cancel an upcoming owned appointment | Owner | Requires UC-1 |
| UC-7 | Maintain clinic scheduling configuration | Staff | Requires UC-1 |
| UC-8 | Maintain established clinic records and walk-in visits | Staff | Requires UC-1 |

The only execution dependency is successful authentication in UC-1. A request can reach a staff outcome from UC-3,
or begin with staff in UC-4; an appointment visible in UC-5 or UC-6 can likewise originate in either automated or
staff-assisted scheduling. Those outcome paths are scenario continuations, not use-case execution dependencies.

## 5. State models

### Scheduling request lifecycle

| State | Meaning | Allowed exits |
|---|---|---|
| Awaiting consent | Owner text exists but has not been sent to the AI | Interpreting on consent; With staff on decline; Awaiting consent on edit; Abandoned on abandon |
| Interpreting | Consented text is waiting for or undergoing interpretation | Interpreted on usable result; Interpretation failed on defined interpretation failure; With staff on AI unavailability or unmatched specialty; Abandoned on abandon |
| Interpretation failed | The result was unparseable, contained no preferred or allowed windows, or reported `understood = false` | Awaiting consent on rephrase; With staff on owner hand-off; Abandoned on abandon |
| Interpreted | A complete interpretation is displayed read-only and awaits owner confirmation | Suggestion offered when a candidate is held; With staff on owner hand-off or no candidates; Awaiting consent on text edit; Abandoned on abandon |
| Suggestion offered | Exactly one slot is held for the request | Accepted on successful acceptance; Suggestion offered when a rejection is followed by another candidate; With staff on owner hand-off, candidate exhaustion, staff release, or schedule invalidation; Awaiting consent on text edit; Abandoned on abandon |
| With staff | The request is available in the staff queue | Suggestion offered when staff place a suggestion; Accepted when staff book directly; Abandoned when the owner abandons |
| Accepted | The request produced a confirmed appointment | None |
| Abandoned | The owner closed the request without an appointment | None |

All transitions not listed in this table are refused without changing the request, its current interpretation,
rejections, holds, or appointments. In particular, a suggestion can be generated only from an owner-confirmed
interpretation or from the current staff-authored interpretation of a request in With staff.

### Appointment lifecycle

| State | Meaning | Allowed exits |
|---|---|---|
| Held | One suggestion exclusively reserves the veterinarian and time | Confirmed on acceptance; removed on rejection, replacement, staff release, text edit, abandonment, or schedule invalidation |
| Confirmed | A booked appointment visible to owner and staff | Confirmed on staff reschedule; Cancelled on owner or staff cancellation; Completed or No-show after its start time |
| Cancelled | A final cancelled appointment | None |
| Completed | A final appointment with a linked visit | None |
| No-show | A final missed appointment | None |

A direct staff booking begins as Confirmed. A removed hold is not retained as another appointment state; the request's
rejection history is the durable history when the owner asked for another option.

## 6. Detailed use cases

## UC-1 - Sign in and enter the permitted workspace

- Goal: Reach the workspace and application capabilities permitted to the signed-in role.
- Primary actor: Owner or staff
- Supporting actors: Health monitor
- Trigger: A person opens the application or submits seeded credentials.
- Preconditions: The account exists in the exact Accounts data set under Normative data.
- Relations:
  - Requires: none
  - Includes: none
  - Extends: none

### Main success scenario

1. The person opens the application without an authenticated session.
2. The system shows the login page using the established PetClinic layout and discloses no clinic or owner data.
3. The person submits valid credentials.
4. The system creates an authenticated session and displays the signed-in username and a Logout action on every page.
5. The system opens the role's landing page: My appointments with only My pets and My appointments navigation for an
   owner; Scheduling queue with the established staff pages plus Scheduling queue, Calendar, and Clinic settings for
   staff.

### Extensions

- 3a. If the credentials are invalid, the system keeps the person on the login experience, reveals neither whether the
  username nor password was wrong, and creates no authenticated session; end.
- 4a. If the signed-in person logs out, the system ends the session and returns to the login page; resume at step 1.
- 5a. If an owner requests any staff-only page or action, the system responds with 403 and discloses and changes
  nothing; end.
- 5b. If an owner supplies an identifier belonging to another owner through an owner-scoped page or action, the system
  returns the standard 404 page, indistinguishable from an unknown identifier, and discloses and changes nothing; end.
- 5c. If staff request an owner workspace page under `/my/**`, the system refuses access and changes nothing; end.
- 1a. If an anonymous health monitor requests `/actuator/health`, the system returns the health result without asking
  for credentials or disclosing other operational data; end.
- 1b. If an anonymous client requests the login page or a static resource, the system serves it; end.
- 1c. If an anonymous client requests any other page, action, status resource, console, or operational endpoint, the
  system requires authentication and performs no requested action; end.
- 5d. If staff request the H2 console or an operational endpoint other than health, the system allows access; end.

### Guarantees

- G1. Anonymous access is limited to `/login`, static resources, and `/actuator/health`.
- G2. Owner pages use `/my/**`, derive the owner from the authenticated identity, and never place an owner id in an
  owner URL. Established `/owners/**`, `/vets/**`, visit-entry, and other clinic-management pages are staff-only.
- G3. Authorization is enforced at the system boundary and not only through hidden navigation.
- G4. Every guarded failure denies both the response data and any side effect. Cross-owner not-found responses cannot
  reveal whether the requested object exists.
- G5. Every rendered page, including login and errors, uses the established application layout, navigation, form
  controls, and stylesheet; no feature page presents a second visual system or inline styling.
- G6. Navigation contains only actions available to the current role. Signed-in identity and Logout remain visible on
  every authenticated page.
- G7. All user-visible text, HTML text and attributes, and server-produced status or flash messages resolve through
  message keys present in all eleven shipped locale bundles. New English text is copied as the placeholder value in
  the other bundles; application behavior emits no hard-coded English message.
- G8. Automated security evidence covers the entire application URL space, including established pages and the request
  status resource, and asserts status, absence of disclosure, and absence of side effects for anonymous, wrong-role,
  and cross-owner requests.
- G9. A rendered-page walkthrough as both an owner and staff confirms the common layout, exact role menus, identity,
  Logout, landing pages, and denial of cross-owner data before a UI-bearing delivery is accepted.
- G10. Automated localization evidence scans rendered template text and attributes and server-produced messages, and
  fails when a required key is absent from any shipped locale bundle or an application message bypasses a key.

### Postconditions

- Success: The person has an authenticated role-scoped session at the correct landing page.
- Minimal guarantee: No invalid, anonymous, wrong-role, or cross-owner attempt discloses protected data or changes
  application state.

## UC-2 - Review owned pets and scheduling activity

- Goal: Understand the owner's pets, their appointment history and future bookings, and any scheduling work in
  progress.
- Primary actor: Owner
- Supporting actors: none
- Trigger: The owner opens My pets or My appointments.
- Preconditions: UC-1 succeeded for an owner account.
- Relations:
  - Requires: UC-1, because the owner identity scopes all displayed data
  - Includes: none
  - Extends: none

### Main success scenario

1. The owner opens My pets.
2. The system shows the signed-in owner's record and pets in read-only form with no create or edit action.
3. The owner opens My appointments.
4. The system shows every upcoming and past appointment for the owner's pets and any active scheduling request for
   each pet.
5. The system shows only actions currently valid for each pet or item: start or resume a request and cancel an upcoming
   appointment.

### Extensions

- 2a. If the owner has no pets, the system shows an empty read-only state and no scheduling action; end.
- 4a. If a pet has no appointments or active request, the system shows that absence and offers a new request; end.
- 4b. If an appointment was rescheduled by staff, the system shows its new time and the staff-entered reason; end.
- 1a. If the owner attempts to identify another owner's pet, request, or appointment, the system follows UC-1
  extension 5b; end.

### Guarantees

- G1. The page never displays another owner's identity, contact details, pets, requests, appointments, or visits.
- G2. Owners can see veterinarian names and specialties inside a scheduling request but cannot open the staff
  veterinarian directory.
- G3. Owner and pet creation or editing remains a staff task; the owner representation is read-only.
- G4. Request state and current interpretation origin shown here agree with the request detail shown in UC-3 or UC-4.
- G5. Presentation and messages satisfy UC-1 G5-G7.

### Postconditions

- Success: The owner has a complete role-scoped view of their pets, appointment history, future appointments, and
  active request status.
- Minimal guarantee: Missing data is represented as an empty state without disclosing or fabricating records.

## UC-3 - Schedule a pet appointment through the guided flow (primary)

- Goal: Obtain and accept a suitable appointment without seeing the clinic's full availability calendar.
- Primary actor: Owner
- Supporting actors: AI interpreter, clock, staff
- Trigger: The owner starts or resumes a scheduling request for one of their pets.
- Preconditions: UC-1 succeeded for an owner account; the selected pet belongs to that owner.
- Relations:
  - Requires: UC-1, because the owner identity and pet ownership must be established
  - Includes: none
  - Extends: none

### Main success scenario

1. The owner selects their pet and describes the reason for the visit and their preferred, allowed, and unavailable
   times in free-form English.
2. The system creates the request in Awaiting consent and shows what will be sent to the AI: the free text,
   specialties, veterinarian names, clinic opening hours by weekday, today's clinic-local date, time zone, and duration
   bounds, with no owner or pet identifier.
3. The owner consents to that disclosed AI processing.
4. The system moves the request to Interpreting, accepts it for asynchronous processing, shows a visible status and
   Refresh action, and exposes automatic state-only polling that returns only `{"state":"..."}` for this owner's
   request.
5. The AI produces a usable structured interpretation, and the system stores an immutable AI-origin version containing
   `understood`, care type, required specialty or `OTHER` plus its free label, raw duration estimate, all preferred,
   allowed, and excluded windows, preferred veterinarian or none, raw model JSON, model tag, and prompt version.
6. The system shows the complete structured interpretation read-only using localized labels and clinic-local formats,
   identifies its AI origin, shows the effective duration and any clamping note, and asks the owner to confirm or
   revise it.
7. The owner confirms the interpretation.
8. The system evaluates current availability and holds, exclusively holds the highest-ranked feasible slot, changes
   the request to Suggestion offered, and shows exactly that one slot with veterinarian, specialty, local date, time,
   effective duration, and localized rank reason.
9. The owner accepts the suggestion.
10. The system rechecks availability, changes the held appointment to Confirmed, changes the request to Accepted, and
    shows the appointment under My appointments.

### Extensions

- 1a. If the pet already has an active request, including under a concurrent submission, the system opens the one
  existing request and creates no second active request; end.
- 1b. If the selected pet is not owned by the signed-in owner or does not exist, the system returns the same standard
  404 page and creates nothing; end.
- 1c. If the description omits a reason for the visit, the system permits the request; interpretation defaults to
  general care and the normative default duration; resume at step 2.
- 1d. If the text is not English, the system neither detects nor rejects the language; it continues with the documented
  English-only expectation and normal interpretation outcomes; resume at step 2.
- 2a. If the owner edits the text before consent, the system retains Awaiting consent, replaces the unsubmitted text,
  and requires consent to the revised text; resume at step 2.
- 2b. If the owner declines consent, the system sends no text to the AI, records consent declined, moves the request to
  With staff, and makes it available in UC-4; end.
- 3a. If consent submission is repeated, the system accepts at most one interpretation job for that request and does
  not create duplicate versions; resume at step 4.
- 4a. If the owner abandons while interpretation is pending, the system changes the request to Abandoned; any later AI
  result is discarded without creating a version or appointment; end.
- 4b. If the result is unparseable, contains zero preferred plus allowed windows, or reports `understood = false`, the
  system changes the request to Interpretation failed and offers rephrase, staff assistance, and abandon; end.
- 4c. If interpretation fails and the owner rephrases, the system returns the request to Awaiting consent and requires
  fresh consent. From the third failed attempt onward it recommends staff assistance but continues to allow rephrasing;
  resume at step 2.
- 4d. If the owner chooses staff assistance after a failure, the system moves the request to With staff with the
  applicable reason and makes it available in UC-4; end.
- 4e. If the interpreter has a transport error, cannot connect within five seconds, or does not complete within the
  120-second deadline, the system performs no retry, records AI unavailable, moves the request to With staff, and makes
  it available in UC-4; end.
- 4f. If the application starts with the request still Interpreting, the system records AI unavailable and moves it to
  With staff without resubmitting the job; end.
- 5a. If the model reports `OTHER` because no veterinarian offers the required specialty, the system preserves the
  free specialty label, does not downgrade to general care, moves the request to With staff as unmatched specialty,
  and makes it available in UC-4; end.
- 5b. If the model emits a veterinarian value outside the enumerated veterinarian ids, the system records no preferred
  veterinarian and otherwise preserves the interpretation; resume at step 6.
- 6a. If the raw duration is below or above the configured bounds, the system preserves that raw value, shows the
  bounded effective duration and a localized note, and uses the effective value only for matching; resume at step 7.
- 6b. If the owner edits the text after interpretation, the system releases any existing hold, retains all earlier slot
  rejections and immutable interpretation versions, returns to Awaiting consent, and requires fresh consent and a new
  interpretation; resume at step 2.
- 6c. If the owner asks for staff assistance, the system moves the request to With staff with the owner's-choice reason
  and makes it available in UC-4; end.
- 7a. If no feasible candidate exists, the system moves the request automatically to With staff with reason no slots
  available, explains the outcome, retains abandon as the only owner action, and makes it available in UC-4; end.
- 8a. If the owner asks for another option, the system permanently records the exact veterinarian-and-time rejection,
  removes the current hold, re-evaluates current availability and all request rejections, and either holds and displays
  the next highest-ranked candidate at step 8 or follows extension 7a; resume at step 8 or end through 7a.
- 8b. If the owner asks staff for help, the system removes the hold, moves the request to With staff with the
  owner's-choice reason, and makes it available in UC-4; end.
- 8c. If the owner edits the text, the system follows extension 6b; resume at step 2.
- 8d. If staff release the hold with a reason, the system removes the hold, moves the request to With staff with that
  reason, and makes it available in UC-4; end.
- 8e. If a schedule or opening-hours change invalidates the hold, the system removes the hold, moves the request to
  With staff with reason schedule changed, and makes it available in UC-4; end.
- 9a. If the held slot is no longer available when accepted, the system does not show an error page; it removes the
  unavailable hold and either holds and explains the next candidate at step 8 or follows extension 7a; resume at step 8
  or end through 7a.
- 2c. If the owner abandons in Awaiting consent, Interpretation failed, Interpreted, Suggestion offered, or With staff,
  the system changes the request to Abandoned, removes any hold, sends no new AI request, and retains no further owner
  action; end.
- 3b. If the owner attempts to generate a suggestion from any state other than a confirmed Interpreted request or a
  staff-placed Suggestion offered request, the system refuses the action without changing requests, versions, holds,
  rejections, or appointments; end.

### Guarantees

- G1. The owner is shown one suggestion at a time and never sees the clinic's full availability calendar.
- G2. A feasible automated candidate starts on a 15-minute grid and in the inclusive date horizon from the start of
  `today + minimum lead days` through `today + booking horizon`; lies within an explicit preferred or allowed window
  and outside every excluded window; fits clinic opening hours and one veterinarian's continuous effective working
  block; does not overlap a Confirmed appointment or Held suggestion; satisfies required specialty; is not before the
  configured minimum lead date; lies within the booking horizon; and has not been rejected for this request.
- G3. Recurring weekday windows apply to matching weekdays inside the horizon. Concrete dates are resolved relative to
  request creation and stored as absolute clinic-local dates. The interpreter resolves named day parts to explicit
  times from current clinic settings and expands statements such as "any day except Wednesday" into explicit windows.
- G4. Unmentioned time is never feasible. Preferred and allowed windows are equally feasible; preference affects only
  ranking. Exclusions can only subtract time.
- G5. Feasible candidates are ranked lexicographically, with no weighting: preferred window first; then preferred
  veterinarian; then earliest start; then fewest Confirmed appointments for that veterinarian on that day; then lowest
  veterinarian id. Specialty has no ranking preference after feasibility is established.
- G6. The rank reason is localized application text derived from preferred versus allowed and whether a preferred
  veterinarian was honored. No model-authored prose is displayed.
- G7. A hold has no timer and prevents every other request or staff action from holding or booking overlapping time for
  the same veterinarian. Concurrent attempts yield exactly one winning hold or booking.
- G8. Interpretation fidelity is exact: reading a stored version returns the same structured fields and values that
  were accepted from the interpreter, including all window lists and raw duration. A staff version never overwrites an
  earlier AI version.
- G9. The AI call uses temperature zero and schema-constrained output. Specialty is one enumerated clinic specialty or
  `OTHER`; preferred veterinarian is one enumerated id or none. No owner name, owner id, pet name, or pet id is sent.
- G10. Every owner scheduling page and request detail displays the clinic's urgent-care contact guidance and instructs
  an owner needing immediate help to call. The system performs no urgency detection and provides no urgency field.
- G11. Every lifecycle transition in the request and appointment state models is enforced at the system boundary. Every
  unlisted transition is refused with no side effect.
- G12. All matching uses the injected clock and clinic-local date and time. Automated evidence pins the clock to a
  known value, including 2026-09-07 09:00 Europe/Amsterdam where seeded exceptions must affect matching.
- G13. Automated interpretation tests use a deterministic interpreter substitute and never call a live model. They
  cover usable output, every Interpretation failed condition, every AI unavailable condition, abandoned-result
  discard, and startup recovery. Separate contract evidence proves that the status resource returns only the state and
  applies owner scoping.
- G14. Automated end-to-end evidence drives a real embedded HTTP server on a dynamically allocated port with a real
  client handling session cookies and CSRF. It covers owner request, consent, HTML-page polling while Interpreting,
  interpretation, suggestion, rejection, staff hand-off, staff suggestion, acceptance, and completed visit, plus a
  declined-consent request booked by staff and closed as No-show. A mocked web transport alone is insufficient.
- G15. Automated concurrency evidence races two attempts for the same veterinarian-and-time and two active-request
  creations for one pet; it observes exactly one winner in each case.
- G16. Automated tests use an isolated in-memory database and never create, change, or depend on the persistent runtime
  database file. The runtime database file is not committed.
- G17. Presentation, status, polling fallback, and messages satisfy UC-1 G5-G7. The README states that input is expected
  in English.
- G18. Runtime requests, versions, rejections, appointments, and scheduling configuration survive an application
  restart in the file-backed database; the startup handling of Interpreting requests remains UC-3 extension 4f.
- G19. At most two interpretation jobs execute concurrently. Once consent is accepted, additional jobs wait for a
  worker rather than failing because a fixed pending-job limit was reached.

### Postconditions

- Success: The request is Accepted, the appointment is Confirmed, the pet has no active request, and the owner can see
  the appointment under My appointments.
- Minimal guarantee: At most one active request and one overlapping appointment or hold exist; failed, refused,
  abandoned, unavailable, and exhausted paths disclose no other owner's data and leave no orphan hold.

## UC-4 - Resolve a scheduling request that needs staff

- Goal: Give a request in With staff a valid direct booking or one owner-facing suggestion.
- Primary actor: Staff
- Supporting actors: Owner, clock
- Trigger: Staff open the Scheduling queue or choose to create a request for a pet.
- Preconditions: UC-1 succeeded for staff.
- Relations:
  - Requires: UC-1, because the queue and all staff actions are staff-only
  - Includes: none
  - Extends: none

### Main success scenario

1. Staff open the Scheduling queue.
2. The system shows Needs staff, ordered oldest first, and In progress, containing every other active request.
3. Staff select a request in With staff from Needs staff.
4. The system shows the hand-off reason, owner and pet, request version, current interpretation origin, complete latest
   interpretation if one exists, and immutable earlier versions.
5. Staff review or author a complete structured interpretation. If there is a latest version, the form begins with its
   values; if consent was declined or no version exists, it begins empty.
6. The system creates an immutable STAFF-origin version when values were authored and makes the selected latest version
   current without altering history.
7. Staff choose direct booking and identify a veterinarian, local date, start time, duration, and required reason.
8. The system highlights veterinarians matching the interpretation's specialty, warns without blocking when the chosen
   veterinarian does not match, and checks the staff constraints.
9. Staff confirm the booking.
10. The system creates a Confirmed appointment, changes the request to Accepted, removes it from the active queue, and
    makes the appointment visible to the owner on the next view or login.

### Extensions

- 1a. If staff create a request for any pet, the system creates it directly in With staff with reason request created
  by staff, sends nothing to the AI, and shows it in Needs staff; resume at step 3.
- 1b. If that pet already has an active request, including under a concurrent staff or owner submission, the system
  opens the one existing request in its applicable queue section and creates no second active request; end.
- 2a. If staff select a held request from In progress and release its hold with a reason, the system removes the hold,
  moves the request to With staff with reason hold released by staff, and displays it in Needs staff; resume at step 3.
- 2b. If staff inspect any other In progress request, the system shows its state, current interpretation origin, held
  slot if any, and hold age, but offers no action other than release hold for a held request; end.
- 3a. If the owner already abandoned the request, the system refuses the staff action, refreshes the queue, and creates
  no version, hold, or appointment; end.
- 5a. If a complete current interpretation already exists and staff do not edit it, the system uses that latest version
  for booking or suggestion without creating a duplicate version; resume at step 7.
- 5b. If staff attempt to author a version while the request is not With staff, the system refuses the action and
  changes nothing; end.
- 5c. If a request has no current complete interpretation and staff submit incomplete interpretation values, the system
  identifies the missing or invalid values, keeps the request in With staff, and creates no version or appointment;
  end.
- 7a. If staff choose to place a suggestion, the system obtains a slot under the staff constraints, creates one Held
  appointment, changes the request to Suggestion offered, and shows it to the owner with the standard accept, reject,
  staff-help, edit, and abandon behavior in UC-3 from main step 8; end.
- 7b. If no slot satisfies the staff constraints, the system explains why and leaves the request in With staff without
  creating an appointment or hold; end.
- 8a. If the chosen time is outside owner windows, horizon, or lead time, or the veterinarian lacks the interpreted
  specialty, the system permits confirmation after showing the applicable mismatch information, provided all staff
  constraints hold; resume at step 9.
- 8b. If the time falls outside clinic opening hours or the veterinarian's effective working blocks, or overlaps an
  appointment or hold, the system refuses the booking and leaves the request and calendar unchanged; end.
- 9a. If the request version changed since staff opened it, the system refuses the stale action, shows a flash message,
  and creates no version, hold, or appointment; end.
- 9b. If another concurrent action wins the same veterinarian-and-time, the system explains that the time is no longer
  available and leaves this request in With staff; end.
- 4a. If the owner abandons while the request is With staff, the system removes it from Needs staff. Later staff actions
  are refused without side effects; end.

### Guarantees

- G1. Needs staff contains exactly requests in With staff, oldest first, with trigger reason and current interpretation
  origin. In progress contains exactly every other active request, with state, current origin, held slot, and hold age.
- G2. The queue has no claim or assignment state. Stale staff actions cannot overwrite a newer request version.
- G3. Staff never trigger AI interpretation, never provide owner consent, and cannot act inside Interpreted or
  Suggestion offered except to release a hold.
- G4. Staff-authored versions preserve all interpretation fields defined in UC-3 G8, carry STAFF origin, and never
  delete or mutate version history. Bookings and suggestions use the latest applicable version.
- G5. Direct bookings and staff suggestions are not limited by owner windows, specialty, booking horizon, or lead time;
  they always respect clinic opening hours, effective veterinarian working blocks, and no overlap.
- G6. One active request per pet and one winning appointment or hold per veterinarian-and-time remain true under staff
  creation and concurrent staff actions.
- G7. The owner can view a With staff request's status and abandon it, but cannot edit its text in that state.
- G8. Presentation and messages satisfy UC-1 G5-G7, and every staff action that changes an appointment or hold records
  the required reason.

### Postconditions

- Success: The request is Accepted with a Confirmed appointment, or Suggestion offered with exactly one Held
  appointment visible to the owner.
- Minimal guarantee: An unresolved request remains safely in With staff with its reason and history intact; no invalid,
  stale, abandoned, or losing concurrent action changes it.

## UC-5 - Operate the clinic calendar and appointment lifecycle

- Goal: See full clinic capacity and safely create or change appointments through their final outcome.
- Primary actor: Staff
- Supporting actors: Owner, clock
- Trigger: Staff open Calendar or an appointment detail.
- Preconditions: UC-1 succeeded for staff.
- Relations:
  - Requires: UC-1, because calendar and lifecycle actions are staff-only
  - Includes: none
  - Extends: none

### Main success scenario

1. Staff choose a clinic-local date in Calendar.
2. The system shows a day view with one column per veterinarian and 15-minute rows across that day's clinic opening
   hours, with previous day, next day, and date-picker navigation covering the booking horizon.
3. The system shades closed and unavailable ranges and displays Confirmed appointments and Held suggestions as blocks,
   leaving remaining capacity visible.
4. Staff open a Confirmed appointment.
5. The system shows owner, pet, veterinarian, local date and time, status, origin information, and the permitted
   lifecycle actions.
6. Staff choose a new veterinarian or local date and time and provide a rescheduling reason.
7. The system checks the staff constraints against current calendar state.
8. The system directly reschedules the Confirmed appointment and makes the new time and reason visible to the owner
   without requiring owner re-acceptance.

### Extensions

- 3a. If staff open a Held block, the system shows owner, pet, request, and hold age. If staff provide a release reason,
  the system removes the hold and moves the request to With staff as in UC-4 extension 2a; end.
- 3b. If staff select free capacity to book directly, the system asks for owner, pet, veterinarian, local date, start
  time, duration, and reason, applies the staff constraints, creates a Confirmed appointment, and makes it visible to
  the owner; end.
- 5a. If staff cancel a Confirmed appointment with a reason, the system changes it to Cancelled, records staff as the
  cancelling actor and the cancellation time, and makes the outcome visible to the owner; end.
- 5b. If staff mark an appointment Completed after its start time and enter or accept the pre-filled description, the
  system makes Completed final and records a visit for the pet using the appointment date and a link to that
  appointment; end.
- 5c. If staff mark an appointment No-show after its start time, the system makes No-show final and creates no completed
  visit; end.
- 5d. If staff attempt Completed or No-show before the appointment start, the system refuses the action and leaves the
  appointment unchanged; end.
- 5e. If staff attempt to change Cancelled, Completed, or No-show to another state, or complete a Cancelled
  appointment, the system refuses the action and changes nothing; end.
- 7a. If the proposed booking or reschedule violates a staff constraint, the system identifies the conflict and leaves
  all appointments and holds unchanged; end.
- 7b. If another concurrent action takes the proposed time first, the system reports that it is no longer available and
  leaves this appointment at its earlier time; end.
- 8a. If the selected veterinarian does not match the request specialty, the system shows a warning but allows the
  change when all staff constraints hold; resume at step 8.

### Guarantees

- G1. "Full calendar" means clinic opening or closure, each veterinarian's effective working blocks, all current
  Confirmed appointments, all Held suggestions, and remaining capacity for every veterinarian shown that day.
- G2. Held blocks disclose owner and pet details only to staff and always display hold age and a path to request detail.
- G3. Staff direct bookings, suggestions, and reschedules always obey the staff constraints and are not constrained by
  owner windows, specialty, booking horizon, or minimum lead time.
- G4. Staff-initiated booking changes and cancellations record a reason. Rescheduling is immediate and never creates an
  owner acceptance step.
- G5. Completed, No-show, and Cancelled are final. Completion is the only appointment outcome that creates a linked
  visit.
- G6. Calendar calculations and rendering use clinic-local dates and times from the trusted clock. The configured zone
  derives today but does not convert stored appointment time across DST boundaries.
- G7. Concurrent hold, confirm, direct-book, and reschedule attempts for the same veterinarian recheck the latest
  calendar and yield one winner without overlap.
- G8. Presentation and messages satisfy UC-1 G5-G7.

### Postconditions

- Success: Staff have a complete view of the selected day and the requested valid appointment change is immediately
  visible to staff and the affected owner.
- Minimal guarantee: A conflict, premature finalization, invalid final-state action, or losing concurrent action leaves
  all prior appointments, holds, visits, and request states intact.

## UC-6 - Cancel an upcoming owned appointment

- Goal: Cancel the owner's own future appointment without contacting staff.
- Primary actor: Owner
- Supporting actors: Clock
- Trigger: The owner selects Cancel for an upcoming appointment under My appointments.
- Preconditions: UC-1 succeeded for an owner account; the appointment belongs to one of that owner's pets and has not
  started.
- Relations:
  - Requires: UC-1, because ownership and current identity scope the action
  - Includes: none
  - Extends: none

### Main success scenario

1. The owner opens an upcoming Confirmed appointment.
2. The system shows its veterinarian, local date and time, and a Cancel action without requiring a reason.
3. The owner confirms cancellation.
4. The system changes the appointment to Cancelled, records owner as the cancelling actor and the cancellation time,
   and removes the Cancel action.
5. The system keeps the originating request closed and shows the cancelled appointment in history.

### Extensions

- 1a. If the appointment belongs to another owner or does not exist, the system returns the same standard 404 response
  and changes nothing; end.
- 2a. If the appointment has started, is already Cancelled, or is Completed or No-show, the system offers no Cancel
  action; end.
- 3a. If a forged or stale cancellation is submitted for an ineligible appointment, the system refuses it without
  changing the appointment or request; end.

### Guarantees

- G1. No minimum cancellation notice and no owner-provided reason applies before the appointment start.
- G2. Owner cancellation never reopens or creates a scheduling request and never creates a visit.
- G3. The owner never sees or acts on another owner's appointment.
- G4. Staff reschedule reasons and cancellation outcomes remain visible under My appointments.
- G5. Presentation and messages satisfy UC-1 G5-G7.

### Postconditions

- Success: The appointment is finally Cancelled with owner and timestamp recorded; its request remains closed.
- Minimal guarantee: An ineligible or unauthorized cancellation changes and discloses nothing.

## UC-7 - Maintain clinic scheduling configuration

- Goal: Keep clinic hours, scheduling limits, and veterinarian availability accurate without invalidating confirmed
  care silently.
- Primary actor: Staff
- Supporting actors: Clock
- Trigger: Staff open Clinic settings or a veterinarian availability editor.
- Preconditions: UC-1 succeeded for staff.
- Relations:
  - Requires: UC-1, because configuration is staff-only
  - Includes: none
  - Extends: none

### Main success scenario

1. Staff choose clinic settings, clinic closures, or a veterinarian's availability.
2. The system shows current clinic opening hours by weekday, visit-duration bounds and default, booking horizon,
   minimum lead days, named day parts, time zone, recurring working blocks, date exceptions, leave, and closures as
   applicable.
3. Staff change one or more values and submit them.
4. The system identifies every Confirmed appointment and Held suggestion that would conflict with the proposed
   effective schedule.
5. If no Confirmed appointment conflicts, the system saves the configuration.
6. The system removes each conflicting hold, changes its request to With staff with reason schedule changed, and shows
   staff which requests were affected.
7. Subsequent owner matching, staff validation, calendar rendering, and AI clinic context use the saved values.

### Extensions

- 3a. If a value is internally invalid, such as reversed hours, non-positive duration, a default outside its bounds, or
  overlapping blocks for the same veterinarian, the system explains the validation problem and saves nothing; end.
- 4a. If one or more Confirmed appointments conflict, the system rejects the complete proposed change, lists those
  appointments, instructs staff to reschedule them first, and releases no holds; end.
- 5a. If no hold conflicts, the system saves the configuration and reports no affected requests; resume at step 7.

### Guarantees

- G1. Clinic opening hours support open or closed weekdays. Veterinarian recurring schedules support split shifts;
  date-specific exceptions and leave alter one veterinarian, and clinic closures alter all veterinarians.
- G2. Named day parts resolve from the day's applicable opening hours: morning from opening to 12:00, afternoon from
  12:00 to 17:00, and evening from 17:00 to closing.
- G3. A saved configuration can never leave a Confirmed appointment outside clinic hours or its veterinarian's
  effective working blocks. Staff must reschedule all listed conflicts first.
- G4. Holds do not block a valid configuration change: they are removed atomically with the change and their requests
  become actionable in Needs staff.
- G5. The application operates in one clinic time zone, default Europe/Amsterdam. Local dates and times remain stable
  across DST changes; the zone determines today's date from the clock.
- G6. The seed values under Normative data are exact defaults. Automated migration evidence asserts every seeded value,
  not only row counts, and verifies every seeded password with the application's configured password encoder.
- G7. The fixed exception dates are normative fixtures and are expected to affect time-dependent behavior only while
  they fall within the active horizon around September and October 2026.
- G8. Presentation and messages satisfy UC-1 G5-G7.

### Postconditions

- Success: The valid configuration is current, no Confirmed appointment conflicts with it, and every invalidated hold
  has become a reasoned staff hand-off.
- Minimal guarantee: An invalid configuration or one conflicting with a Confirmed appointment is not partially saved
  and changes neither appointments nor holds.

## UC-8 - Maintain established clinic records and walk-in visits

- Goal: Let staff continue managing PetClinic owner, pet, veterinarian, and visit information alongside scheduling.
- Primary actor: Staff
- Supporting actors: none
- Trigger: Staff open Find owners, an owner or pet record, the veterinarian directory, or Add visit.
- Preconditions: UC-1 succeeded for staff.
- Relations:
  - Requires: UC-1, because established clinic-management pages are staff-only
  - Includes: none
  - Extends: none

### Main success scenario

1. Staff find or select an owner.
2. The system shows that owner's contact details, pets, and visit history using the established PetClinic workflow.
3. Staff create or edit an owner or pet with valid values.
4. The system saves the record and returns to the owner detail.
5. Staff open the veterinarian directory.
6. The system lists veterinarians and their specialties.

### Extensions

- 1a. If no owner matches a search, the system shows the established not-found validation and exposes no unrelated
  record; end.
- 3a. If owner or pet values are invalid, the system shows field validation and saves nothing; end.
- 3b. If another pet for the same owner already has the submitted name, including under a concurrent save, the system
  reports the duplicate and saves no duplicate pet; end.
- 2a. If staff add a walk-in visit, the system accepts its date and description through the established Add visit form,
  saves it without an appointment link, and shows it in pet history; end.
- 2b. If staff complete a scheduled appointment, the system follows UC-5 extension 5b rather than creating an unrelated
  walk-in visit; end.

### Guarantees

- G1. Established owner search, owner and pet create/edit, veterinarian listing, and walk-in visit behavior remains
  available to staff.
- G2. A pet name is unique within one owner but may be reused by a different owner.
- G3. Walk-in visits have no appointment link. Visits created by appointment completion retain their appointment link
  and appointment date.
- G4. Owners cannot access these staff pages or mutate owner, pet, veterinarian, or visit records.
- G5. Presentation and messages satisfy UC-1 G5-G7.

### Postconditions

- Success: The requested valid clinic record or walk-in visit is stored and visible in the established staff workflow.
- Minimal guarantee: Invalid, duplicate, or unauthorized changes do not alter clinic records.

## 7. Normative data

All rows in this section are exact: seed exactly these rows - no more, no fewer. A blank working-block cell means that
the veterinarian does not work that day. `Closed` means the clinic has no opening hours that day. No veterinarian leave
or clinic closure is seeded.

### Accounts

Owner usernames are the owner's lowercased first name. Staff accounts are `admin` and `staff`. No other account or role
is seeded.

| Username | Password | Role | Linked owner |
|---|---|---|---|
| george | george123 | owner | George Franklin |
| betty | betty123 | owner | Betty Davis |
| eduardo | eduardo123 | owner | Eduardo Rodriquez |
| harold | harold123 | owner | Harold Davis |
| peter | peter123 | owner | Peter McTavish |
| jean | jean123 | owner | Jean Coleman |
| jeff | jeff123 | owner | Jeff Black |
| maria | maria123 | owner | Maria Escobito |
| david | david123 | owner | David Schroeder |
| carlos | carlos123 | owner | Carlos Estaban |
| admin | admin123 | staff | - |
| staff | staff123 | staff | - |

### Veterinary specialties

Exactly three specialties and these veterinarian associations are available to interpretation and matching.

| Veterinarian | Specialties |
|---|---|
| James Carter | - |
| Helen Leary | radiology |
| Linda Douglas | surgery, dentistry |
| Rafael Ortega | surgery |
| Henry Stevens | radiology |
| Sharon Jenkins | - |

### Clinic opening hours

| Clinic | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|---|---|---|---|---|---|---|---|
| Clinic A | 09:00-17:00 | 09:00-17:00 | 09:00-18:00 | 09:00-17:00 | 10:00-16:00 | Closed | Closed |

### Veterinarian weekly schedules

These cells contain exactly 17 working blocks.

| Veterinarian | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|---|---|---|---|---|---|---|---|
| James Carter | 09:00-17:00 | 09:00-17:00 | 09:00-12:00 |  | 11:00-12:00 |  |  |
| Helen Leary | 09:00-17:00 | 09:00-17:00 | 09:00-12:00 |  |  |  |  |
| Linda Douglas | 09:00-17:00 | 09:00-17:00 | 09:00-12:00 |  |  |  |  |
| Rafael Ortega |  |  |  | 09:00-17:00 | 10:00-16:00 |  |  |
| Henry Stevens |  |  |  | 09:00-17:00 | 10:00-16:00 |  |  |
| Sharon Jenkins | 13:00-14:00 |  |  | 09:00-17:00 | 10:00-16:00 |  |  |

### Veterinarian date exceptions

Every seeded exception makes the veterinarian unavailable for the full date.

| Veterinarian | Unavailable on |
|---|---|
| James Carter | 2026-09-15 |
| Henry Stevens | 2026-09-15 |
| Henry Stevens | 2026-09-17 |
| Henry Stevens | 2026-09-21 |
| Henry Stevens | 2026-10-22 |
| Sharon Jenkins | 2026-10-22 |

### Configuration defaults

| Setting | Default |
|---|---|
| Booking horizon | 30 days ahead |
| Minimum lead days | 1; suggestions start tomorrow; staff bookings are exempt |
| Visit duration bounds and default | 15-60 minutes; default 30 minutes |
| Start-time grid | 15 minutes |
| Parts of day | morning = opening-12:00; afternoon = 12:00-17:00; evening = 17:00-closing |
| Time zone | Europe/Amsterdam |

## 8. Out of scope

- Self-registration, forced first-login password change, password recovery, and account administration.
- Veterinarian login or a veterinarian-specific workspace.
- Showing owners the clinic's full availability calendar.
- External email, SMS, push, or other appointment notifications.
- Emergency detection, triage, or urgency flags; urgent owners are directed to call the clinic.
- Multiple clinics, waitlists, full-schedule optimization, weighted ranking, and hold-expiry timers.
- Automatic translation, language detection, or a supported non-English interpretation contract.
- Owner editing of owner or pet records.
- Multiple database vendors, Gradle, Docker Compose, and Kubernetes deployment.

## 9. External dependencies

- Live interpretation requires Ollama 0.13.1 or newer, reachable locally on `localhost:11434`, with the configurable
  model tag defaulting to `ministral-3:14b`. The model is approximately 9.1 GB and must be pulled as documented in the
  README. Ollama is not started by the application or a Compose service.
- Lack of a reachable or timely model is not a specification blocker: UC-3 extensions 4e and 4f define the complete
  owner-visible fallback.
- The seeded September-October 2026 exception dates are fixed verification fixtures. Outside a booking horizon that
  contains those dates, their lack of effect is expected rather than a missing dependency.
