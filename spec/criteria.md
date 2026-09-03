# Acceptance Criteria: Smart Appointment Scheduling

## Functional

### AC-1: Authentication required on every non-public URL
**Covers:** B-1
**Flow:** cross-cutting

If an anonymous user requests any URL other than the login page or a static resource, then the system shall redirect the response to `/login`.

### AC-2: Anonymous request discloses nothing and mutates nothing
**Covers:** B-2
**Flow:** cross-cutting

If an anonymous user requests any protected URL anywhere in the application, then the system shall disclose no protected data in the response body and shall not perform any database change before redirecting.

### AC-3: Form login authenticates seeded accounts
**Covers:** B-3
**Flow:** cross-cutting

When a user submits valid credentials for a seeded account, the system shall authenticate them with the application's password encoder and establish a session carrying the account's role.

### AC-4: Invalid login is rejected without a session
**Covers:** B-4
**Flow:** cross-cutting

If a login is submitted with an unknown username or a wrong password, then the system shall reject it and shall not create a session.

### AC-5: Owner landing page
**Covers:** B-5
**Flow:** cross-cutting

When an owner signs in or requests `/`, the system shall land the owner on the *My appointments* page.

### AC-6: Staff landing page
**Covers:** B-5
**Flow:** cross-cutting

When a staff member signs in or requests `/`, the system shall land the staff member on the *Scheduling queue* page.

### AC-7: Signed-in identity and logout on every page
**Covers:** B-6
**Flow:** cross-cutting

The system shall show the signed-in username and a *Logout* action on every page, and when *Logout* is invoked the system shall end the session.

### AC-8: Staff-only URL space enforced against owners
**Covers:** B-7
**Flow:** cross-cutting

If an owner requests any URL under `/owners/**`, `/vets*`, or `/staff/**`, then the system shall respond with a 403 page.

### AC-9: Owner on a staff-only URL discloses nothing and mutates nothing
**Covers:** B-8
**Flow:** cross-cutting

If an owner requests any page or action anywhere in the application — including pre-existing pages — that displays or modifies staff-only data, then the system shall deny it, shall disclose no staff data, and shall not perform any database change.

### AC-10: Owner id derives from the session
**Covers:** B-9
**Flow:** cross-cutting

The system shall serve owner pages under `/my/**` using the owner id taken from the session and shall never accept an owner id from the URL.

### AC-11: Other-owner id is indistinguishable from a missing id
**Covers:** B-10
**Flow:** UC-6 ext 1b

If an owner requests a pet, request, or appointment id that does not belong to them, then the system shall respond with 404 identical to the response for a genuinely missing id.

### AC-12: Acting on another owner's data discloses nothing and mutates nothing
**Covers:** B-11
**Flow:** UC-6 ext 1b

If an owner requests any page or action anywhere in the application — including pre-existing pages — that displays or modifies another owner's data, then the system shall deny it, shall not perform any database change, and shall disclose nothing about that resource.

### AC-13: Ownership guard enforced at the service layer
**Covers:** B-12
**Flow:** cross-cutting

The system shall enforce owner scoping in the service layer independently of the URL role matchers, such that ownership denial holds even when the URL matcher is bypassed.

### AC-14: Owner navigation entries
**Covers:** B-13
**Flow:** cross-cutting

While signed in as an owner, the system shall show only the *My pets* and *My appointments* navigation entries.

### AC-15: Staff navigation entries
**Covers:** B-14
**Flow:** cross-cutting

While signed in as staff, the system shall show the stock navigation entries plus *Scheduling queue*, *Calendar*, and *Clinic settings*.

### AC-16: New pages reuse the existing layout
**Covers:** B-15
**Flow:** cross-cutting

The system shall render every new page using the existing layout fragment, navigation bar, form controls, and styles, and shall not introduce a second layout or stylesheet.

### AC-17: My pets is read-only
**Covers:** B-16
**Flow:** cross-cutting

While an owner views the *My pets* page, the system shall show that owner's own owner record and pets without any edit controls.

### AC-18: Owner exposure to veterinarian data is limited to the request page
**Covers:** B-17
**Flow:** UC-1 step 4

While an owner uses the scheduling request page, the system shall show veterinarian names and specialties, and shall not expose owner search, owner/pet editing, visit entry, or the veterinarians page to the owner.

### AC-19: Emergency banner on owner scheduling pages
**Covers:** B-18
**Flow:** cross-cutting

The system shall display the urgent-care banner with the configured emergency phone on every owner scheduling page and on the request detail page.

### AC-20: Owner starts a request in Awaiting consent
**Covers:** B-21
**Flow:** UC-1 step 1

When an owner submits the start-request form for one of their pets that has no active request, providing reason text, availability text, and an optional "this is urgent" checkbox, the system shall create a request in *Awaiting consent*.

### AC-21: Pet selector excludes pets with an active request
**Covers:** B-22
**Flow:** UC-1 step 1

While an owner is on the start-request form, the system shall offer in the pet selector only that owner's pets that have no active request.

### AC-22: At most one active request per pet under concurrency
**Covers:** B-23
**Flow:** UC-1 ext 8b, cross-cutting

When two active-request creations for the same pet are submitted simultaneously, the system shall permit at most one to become active, enforced by a nullable `active_pet_id` column plus a unique index.

### AC-23: Second active request for a pet is refused
**Covers:** B-24
**Flow:** UC-1 ext 8b

If a second active request is attempted for a pet that already has one, then the system shall refuse it and shall not create a second active request.

### AC-24: Consent moves the request to Interpreting and sends the text
**Covers:** B-25
**Flow:** UC-1 step 2, UC-3 step 2

When an owner grants explicit consent, the system shall move the request to *Interpreting* and shall send the owner's text to the AI, and shall not send any text to the AI before consent is granted.

### AC-25: Declining consent routes to staff without sending text
**Covers:** B-26
**Flow:** UC-1 ext 3a

When an owner declines consent, the system shall move the request to *With staff* and shall not send any text to the AI.

### AC-26: Exactly one in-flight interpretation on a dedicated executor
**Covers:** B-27
**Flow:** UC-1 step 3

While a request is in *Interpreting*, the system shall run exactly one in-flight interpretation for that request on a dedicated single-thread `@Async` executor, passing the request id.

### AC-27: Only abandon is allowed while Interpreting
**Covers:** B-28
**Flow:** UC-1 step 3

While a request is in *Interpreting*, if the owner attempts route-to-staff or edit-text, then the system shall defer that action until the interpretation result lands and shall allow only abandon in the meantime.

### AC-28: Polling via meta refresh only
**Covers:** B-29
**Flow:** UC-1 step 3

The system shall auto-refresh the request detail page via `<meta http-equiv="refresh" content="3">` and shall use no JavaScript and no JSON endpoint for polling.

### AC-29: Result for an abandoned request is discarded
**Covers:** B-30
**Flow:** UC-1 ext 4b

If an interpretation result arrives for a request that has been abandoned, then the system shall discard it and shall not change any request or interpretation state.

### AC-30: Startup recovery of in-flight interpretations
**Covers:** B-31
**Flow:** UC-1 ext 4a

When the application starts, the system shall move every request still in *Interpreting* to *Interpretation failed* with reason "interrupted".

### AC-31: Interpretation result applied in a fresh state-checked transaction
**Covers:** B-32
**Flow:** UC-1 step 4

When an interpretation result is applied, the system shall apply it in a fresh transaction that re-checks the request state before mutating it.

### AC-32: Usable interpretation moves to Interpreted
**Covers:** B-33
**Flow:** UC-1 step 4, UC-3 step 2

When a well-formed interpretation whose allowed-universe-minus-excluded is non-empty within the horizon is applied, the system shall move the request to *Interpreted*.

### AC-33: Contradictory or cannotInterpret result is a failed attempt
**Covers:** B-34
**Flow:** UC-3 ext 1b

If a well-formed interpretation has contradictory windows or an explicit `cannotInterpret` flag, then the system shall count it as a failed attempt and move the request to *Interpretation failed*.

### AC-34: Model unavailability routes to staff with a recorded reason
**Covers:** B-35
**Flow:** UC-3 ext 1a

If a model response is malformed, then the system shall retry exactly once, and if a second malformed result, a transport error, or a timeout (Ollama client timeout 60 s, configurable `scheduling.ai.timeout`) occurs, then the system shall treat it as "model unavailable", move the request to *With staff*, and record the unavailability reason.

### AC-35: Unmatched specialty routes to staff
**Covers:** B-36
**Flow:** UC-1 ext 5a

If an interpretation returns a specialty as `OTHER:<text>` that no veterinarian offers, then the system shall move the request to *With staff* and shall not downgrade it to general care.

### AC-36: Below the failed-attempt boundary no staff routing is recommended
**Covers:** B-37
**Flow:** UC-3 ext 2a

While a request has had fewer than three failed interpretation attempts, the system shall allow rephrasing and shall not recommend routing to staff.

### AC-37: At the third failed attempt staff routing is recommended
**Covers:** B-37
**Flow:** UC-3 ext 2a

When the third failed interpretation attempt occurs, the system shall recommend routing to staff while still allowing rephrasing.

### AC-38: Beyond the third failed attempt the recommendation persists
**Covers:** B-37
**Flow:** UC-3 ext 2a

While a request has had more than three failed interpretation attempts, the system shall continue to recommend routing to staff and shall continue to allow rephrasing.

### AC-39: Interpretation content is derived
**Covers:** B-38
**Flow:** UC-1 step 4

When the system interprets a request, it shall derive an estimated visit length, a care type, a specialty, preferred, allowed, and excluded windows, an optional preferred veterinarian, and an urgency flag.

### AC-40: Preferred veterinarian id validated and resolved to a name
**Covers:** B-39
**Flow:** UC-1 step 4

When the model returns a preferred veterinarian id from the prompt list, the system shall validate that the id exists and shall show the resolved veterinarian name in the read-only review.

### AC-41: Unknown or ambiguous preferred veterinarian yields null
**Covers:** B-39
**Flow:** UC-1 step 4

If the model returns an unknown or ambiguous preferred veterinarian, then the system shall record the preferred veterinarian as `null` and shall not substitute a fuzzy-matched veterinarian.

### AC-42: Duration within bounds is unchanged
**Covers:** B-40
**Flow:** cross-cutting

When an estimated duration between 15 and 60 minutes is derived, the system shall keep it unchanged.

### AC-43: Duration at the bounds is unchanged
**Covers:** B-40
**Flow:** cross-cutting

When an estimated duration of exactly 15 or exactly 60 minutes is derived, the system shall keep it unchanged.

### AC-44: Duration outside the bounds is clamped
**Covers:** B-40
**Flow:** cross-cutting

If an estimated duration below 15 minutes or above 60 minutes is derived, then the system shall clamp it to 15 or 60 minutes respectively; if no estimate is available it shall default to 30 minutes.

### AC-45: Prompt inputs
**Covers:** B-41
**Flow:** UC-1 step 3

When the system builds the interpretation prompt, it shall include the current date/time and horizon end from an injectable `Clock`, weekday opening hours, part-of-day tokens, veterinarians (id, name, specialties), and the offered specialties radiology, surgery, and dentistry.

### AC-46: Window structure
**Covers:** B-42
**Flow:** UC-1 step 4

The system shall represent a window as a {weekday | specific date | date range} paired with a start–end time, and shall recur weekday windows across the whole horizon.

### AC-47: Allowed union hard-excludes slots outside it
**Covers:** B-43
**Flow:** UC-1 step 6

When an interpretation names any preferred or allowed windows, the system shall hard-exclude every slot outside the union of those windows.

### AC-48: No windows means the whole horizon is allowed
**Covers:** B-43
**Flow:** UC-1 step 6

When an interpretation names no preferred and no allowed windows, the system shall treat the whole horizon as allowed.

### AC-49: Part-of-day tokens resolved per weekday
**Covers:** B-44
**Flow:** UC-1 step 4

When resolving a part-of-day token (MORNING, AFTERNOON, EVENING), the system shall resolve it per weekday against that day's opening hours and shall drop a token whose resolved interval is empty.

### AC-50: Earliest bookable slot within the two-hour lead
**Covers:** B-45
**Flow:** UC-1 step 6

When enumerating slots, the system shall offer a slot whose start is a 15-minute grid point more than 2 hours after the reference time.

### AC-51: Earliest bookable slot at the two-hour boundary
**Covers:** B-45
**Flow:** UC-1 step 6

When enumerating slots, the system shall treat the first 15-minute grid point at least 2 hours after the reference time as the earliest bookable slot.

### AC-52: Slot before the two-hour boundary is excluded
**Covers:** B-45
**Flow:** UC-1 step 6

If a candidate slot starts less than 2 hours after the reference time, then the system shall exclude it and shall not offer it.

### AC-53: Interpretation persistence and read-back fidelity
**Covers:** B-46
**Flow:** UC-1 step 4

When the interpreter yields an interpretation, the system shall persist and read it back such that all three window lists, care type, specialty, duration, preferred veterinarian, urgency, the raw model response, the model tag, and the prompt version are field-for-field identical to the produced values.

### AC-54: Read-only review is verbatim and changed only by editing text
**Covers:** B-47
**Flow:** UC-1 step 4, UC-3 step 1

The system shall show the interpretation verbatim in the read-only review, and if the owner edits the reason or availability text, then the system shall require fresh consent and re-interpretation.

### AC-55: Interpretations are versioned with provenance
**Covers:** B-48
**Flow:** UC-4 step 2

When staff author or edit an interpretation, the system shall store it as a new version with provenance `STAFF` and shall not overwrite the `AI` version.

### AC-56: Raw model provenance visible to staff only
**Covers:** B-49
**Flow:** UC-4 step 1

While signed in as staff, the system shall show the raw model response, model tag, and prompt version, and shall not disclose them to owners.

### AC-57: Suggestions only from a confirmed interpretation
**Covers:** B-50
**Flow:** UC-1 step 5

If a suggestion is attempted for a request that is not in *Interpreted* or *Suggestion offered*, then the system shall refuse it and shall not change the request's state or produce a hold.

### AC-58: Confirmation enumerates, ranks, offers, and records the hold
**Covers:** B-51
**Flow:** UC-1 step 6

When an owner confirms the interpretation and feasible slots exist, the system shall enumerate feasible slots, rank them with Timefold, offer the top slot, move the request to *Suggestion offered*, and record the hold (held veterinarian, start, duration) on the request row.

### AC-59: A held slot is offered to no other owner
**Covers:** B-52
**Flow:** UC-1 step 6, cross-cutting

While a slot is held, the system shall offer it to no other owner and shall union appointment rows with requests whose hold fields are set when performing calendar and overlap checks.

### AC-60: A hold has no timer
**Covers:** B-53
**Flow:** UC-1 step 6

The system shall keep a hold with no timer, releasing it only when the owner accepts, asks for another option, routes to staff, edits text, or abandons, when a new suggestion supersedes it, or when staff release it.

### AC-61: No feasible slots routes to staff
**Covers:** B-54
**Flow:** UC-1 ext 6a, UC-2 ext 2a

If no feasible slots remain, then the system shall move the request to *With staff* and shall not show a dead end.

### AC-62: Re-validation on viewing a suggestion
**Covers:** B-55
**Flow:** UC-2 ext 3a

When a request in *Suggestion offered* is opened and its hold is invalid, the system shall immediately produce the next suggestion or move to *With staff* with an explanatory notice, and shall not show an error page.

### AC-63: Accept re-validates inside a locked transaction
**Covers:** B-56
**Flow:** UC-1 step 7, UC-1 step 8, UC-4 step 4

When an owner accepts a suggestion, the system shall re-validate the hold inside a transaction that first takes `SELECT … FOR UPDATE` on the veterinarian row and re-checks overlaps, and if the hold is valid it shall confirm the appointment and move the request to *Accepted*.

### AC-64: Accepted slot lost yields a suggestion, not an error
**Covers:** B-57
**Flow:** UC-1 ext 8a

If the accepted slot has become unavailable inside the locked transaction, then the system shall show the next suggestion or the staff hand-off with an explanation and shall not show an error page.

### AC-65: Two owners can never confirm the same slot
**Covers:** B-58
**Flow:** UC-1 ext 8b, cross-cutting

When two owners attempt to confirm the same veterinarian-and-time simultaneously, the system shall permit at most one to succeed, enforced by the per-veterinarian row lock and overlap re-check.

### AC-66: Ask for another option releases, excludes, and re-offers
**Covers:** B-59
**Flow:** UC-2 step 1, UC-2 step 2

When an owner asks for another option, the system shall reject the current suggestion, release the hold, permanently exclude that exact veterinarian-and-time for this request, and offer the next best slot, with no cap on repetitions.

### AC-67: Scope chip "not this time"
**Covers:** B-60
**Flow:** UC-2 step 1

When an owner rejects with the "not this time" scope chip, the system shall hard-exclude this veterinarian at this start for the current interpretation only.

### AC-68: Scope chip "not this day"
**Covers:** B-60
**Flow:** UC-2 step 1

When an owner rejects with the "not this day" scope chip, the system shall hard-exclude this calendar date for all veterinarians for the current interpretation only.

### AC-69: Scope chip "not this vet"
**Covers:** B-60
**Flow:** UC-2 step 1

When an owner rejects with the "not this vet" scope chip, the system shall hard-exclude this veterinarian for the whole horizon for the current interpretation only.

### AC-70: Editing text clears rejections from application
**Covers:** B-61
**Flow:** UC-3 step 1

When an owner edits the text, the system shall clear prior rejections from application while retaining them in the event log for audit.

### AC-71: Ruled-out list shown without undo
**Covers:** B-62
**Flow:** UC-2 step 2

The system shall show a compact "You have ruled out: …" list on the request page with no undo control.

### AC-72: Each rejection is recorded as an event
**Covers:** B-63
**Flow:** UC-2 step 2

When an owner rejects a suggestion, the system shall record it as a `scheduling_request_event` carrying the excluded veterinarian, time, and scope.

### AC-73: Solver model shape
**Covers:** B-64
**Flow:** UC-1 step 6, UC-4 step 3

The system shall model the ranking as a single planning entity with one planning variable (the slot) over enumerated feasible slots, applying the hard constraints both at enumeration and as Timefold hard constraints.

### AC-74: Hard constraint — opening hours
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot falls outside clinic opening hours, then the solver shall exclude it and shall not offer it.

### AC-75: Hard constraint — veterinarian working block
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot does not fit within one veterinarian's continuous working block after exceptions, leave, and closures, then the solver shall exclude it and shall not offer it.

### AC-76: Hard constraint — no overlap with appointments or holds
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot overlaps an existing appointment or an active hold, then the solver shall exclude it and shall not offer it.

### AC-77: Hard constraint — required specialty present
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot's veterinarian lacks the required specialty, then the solver shall exclude it and shall not offer it.

### AC-78: Hard constraint — excluded windows
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot falls inside the owner's excluded windows, then the solver shall exclude it and shall not offer it.

### AC-79: Hard constraint — preferred∪allowed union
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot falls outside the union of the owner's preferred and allowed windows, then the solver shall exclude it and shall not offer it.

### AC-80: Hard constraint — booking horizon
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot falls outside the booking horizon, then the solver shall exclude it and shall not offer it.

### AC-81: Hard constraint — owner self-overlap
**Covers:** B-65
**Flow:** cross-cutting

If a candidate slot overlaps the same owner's other confirmed appointments or active holds across all their pets, then the solver shall exclude it and shall not offer it.

### AC-82: Soft ranking priority
**Covers:** B-66
**Flow:** UC-1 step 6

When ranking feasible slots, the solver shall use `HardMediumSoft` scoring so that preferred window (medium) outranks preferred veterinarian (soft, high weight), which outranks earliest date/time (soft, per-minute tie-breaker).

### AC-83: Grid and synchronous solver budget
**Covers:** B-67
**Flow:** UC-1 step 6

The system shall place start times on a 15-minute grid and run the solver synchronously inside the locked transaction with a 1-second termination budget.

### AC-84: Needs staff tab ordering
**Covers:** B-68
**Flow:** UC-4 step 1

While the *Needs staff* tab is shown, the system shall list *With staff* requests with emergencies pinned first, then oldest first, and shall show each request's hand-off trigger.

### AC-85: All open tab contents
**Covers:** B-69
**Flow:** cross-cutting

While the *All open* tab is shown, the system shall list every non-terminal request with its state, held slot, and age, plus a release-hold action.

### AC-86: Staff release-hold
**Covers:** B-70
**Flow:** cross-cutting

When staff release a hold, the system shall move the request to *With staff* with a reason and release the hold.

### AC-87: Emergency pinning skips the automated loop
**Covers:** B-71
**Flow:** UC-1 ext 1a

When either the AI urgent flag or the owner's urgent checkbox is set, the system shall pin the request to the top of the staff queue and skip the automated loop.

### AC-88: Clearing the emergency flag keeps the request with staff
**Covers:** B-72
**Flow:** UC-4 ext 2a

When staff clear the emergency flag, the system shall keep the request in *With staff* and shall not return it to the automated loop.

### AC-89: Staff create or edit an interpretation
**Covers:** B-73
**Flow:** UC-4 step 2

When staff create or edit a structured interpretation, the system shall store it as a new `STAFF` version, and it shall require one for a declined-consent request that has none.

### AC-90: Staff place a suggestion
**Covers:** B-74
**Flow:** UC-4 step 3

When staff place a suggestion by clicking a free calendar cell in picking mode or by pressing *Suggest* to run the solver, the system shall create a hold and move the request to *Suggestion offered* under the same rules as any suggestion.

### AC-91: Staff-placed suggestion follows the same rules
**Covers:** B-75
**Flow:** UC-4 step 4

When an owner accepts or rejects a staff-placed suggestion, the system shall apply exactly the same rules as for any other suggestion.

### AC-92: Staff direct booking needs no interpretation
**Covers:** B-76
**Flow:** UC-4 ext 3a, UC-4 ext 3b

The system shall let staff book an appointment directly for any pet at any time without an interpretation.

### AC-93: Direct booking with attach
**Covers:** B-77
**Flow:** UC-4 ext 3a

When staff book for a pet with an open request in any non-terminal state and choose *attach*, the system shall move the request to *Accepted*, release its hold, and log the event.

### AC-94: Direct booking leaving the request open
**Covers:** B-77
**Flow:** UC-4 ext 3b

When staff book for a pet with an open request and choose *leave open*, the system shall leave the request untouched and shall still show the appointment under the owner's list.

### AC-95: Directly booked appointment visible to the owner
**Covers:** B-78
**Flow:** UC-4 ext 3b, UC-6 step 1

The system shall show a directly booked appointment under the owner's *My appointments* on next login and shall allow the owner to cancel it.

### AC-96: Day-view calendar layout
**Covers:** B-79
**Flow:** UC-5 step 1

The system shall render the staff calendar as a day view with one column per veterinarian and one row per 15-minute grid step, cells coloured closed/off-shift/free/booked/held, with prev/next-day navigation and a date picker over a range covering the horizon.

### AC-97: Clicking a free cell
**Covers:** B-80
**Flow:** UC-4 step 3, UC-5 step 1

When staff click a free calendar cell, the system shall start a direct booking with veterinarian and start prefilled, or, in picking mode for a queued request, place the hold.

### AC-98: Calendar per-vet per-day contents
**Covers:** B-81
**Flow:** UC-5 step 1

While the calendar is shown, the system shall present, per veterinarian per day, the opening hours or closure, effective working blocks, booked appointments, held slots, and remaining free capacity.

### AC-99: Clinic settings are staff-editable with seeded defaults
**Covers:** B-82
**Flow:** cross-cutting

The system shall let staff edit clinic settings — opening hours per weekday, visit-duration bounds and default, booking horizon, parts of day, emergency phone, and time zone — in the UI, starting from seeded defaults.

### AC-100: Veterinarian availability model
**Covers:** B-83
**Flow:** cross-cutting

The system shall support, per veterinarian, a recurring weekly schedule with split shifts, date exceptions (a date with zero or more replacement blocks, zero meaning unavailable all day), leave (an inclusive whole-day date range), and clinic-wide closures (a single date).

### AC-101: Effective blocks precedence
**Covers:** B-84
**Flow:** cross-cutting

The system shall compute a day's effective blocks as closure ? none : leave ? none : exception ? its blocks : weekly blocks, all intersected with opening hours, applying precedence closure > leave > exception > weekly.

### AC-102: Availability edit conflicting a confirmed appointment is refused
**Covers:** B-85
**Flow:** UC-5 ext 5a

If an availability edit (exception, leave, closure, or opening hours) conflicts with a confirmed appointment, then the system shall refuse it with the list of conflicts and shall not change the availability until staff reschedule or cancel the appointment.

### AC-103: Availability edit conflicting only holds invalidates them
**Covers:** B-86
**Flow:** UC-5 ext 5b

When an availability edit conflicts only with holds, the system shall invalidate those holds and apply the edit.

### AC-104: Owner within-horizon booking is allowed
**Covers:** B-87
**Flow:** cross-cutting

When an owner-flow slot falls strictly within the booking horizon, the system shall allow it.

### AC-105: Owner booking at the horizon boundary is allowed
**Covers:** B-87
**Flow:** cross-cutting

When an owner-flow slot falls on the last day of the booking horizon, the system shall allow it.

### AC-106: Owner booking beyond the horizon is excluded
**Covers:** B-87
**Flow:** cross-cutting

If an owner-flow slot falls beyond the booking horizon, then the system shall exclude it for owners and the solver, while allowing staff to book any future date whose duration is a grid multiple that fits the veterinarian's block, and shall keep past days viewable but not bookable.

### AC-107: Out-of-hours weekly blocks accepted with a warning
**Covers:** B-88
**Flow:** cross-cutting

When staff enter weekly blocks outside opening hours, the system shall accept them with a page warning and intersect them with opening hours at runtime.

### AC-108: Single configured time zone
**Covers:** B-89
**Flow:** cross-cutting

The system shall operate in one configured time zone, defaulting to `Europe/Amsterdam`.

### AC-109: Staff reschedule before start
**Covers:** B-90
**Flow:** UC-5 step 2

When staff reschedule a confirmed appointment before its start, the system shall mutate the appointment in place and record the old time in the audit event.

### AC-110: Staff cancel before start
**Covers:** B-91
**Flow:** UC-5 step 2

When staff cancel a confirmed appointment before its start with a reason, the system shall move it to `CANCELLED_BY_STAFF` and record the reason.

### AC-111: Completion before start is refused
**Covers:** B-92
**Flow:** UC-5 step 3

If staff attempt to mark an appointment completed or no-show before its scheduled start has passed, then the system shall refuse it and shall not change the appointment's state.

### AC-112: Completion exactly at the start boundary is allowed
**Covers:** B-92
**Flow:** UC-5 step 3

When the scheduled start time has just passed, the system shall allow staff to mark the appointment completed or no-show.

### AC-113: Completion after the start boundary is allowed
**Covers:** B-92
**Flow:** UC-5 step 3

While an appointment's scheduled start is in the past, the system shall allow staff to mark it completed or no-show.

### AC-114: Completion creates a linked visit
**Covers:** B-93
**Flow:** UC-5 step 4

When staff mark an appointment completed, the system shall create a `visits` row linked by `appointment_id`, with the description prefilled from the owner's reason or the staff booking reason and editable, and the visit date equal to the appointment date.

### AC-115: Staff actions record an audit reason
**Covers:** B-94
**Flow:** UC-5 step 2, cross-cutting

When staff book, reschedule, or cancel an appointment, the system shall record a reason in the `appointment_change` audit table (appointment, actor, action, reason, timestamp) and show it per appointment on the staff calendar.

### AC-116: Owner cancels an upcoming appointment
**Covers:** B-95
**Flow:** UC-6 step 1, UC-6 step 2

When an owner cancels their own upcoming confirmed appointment before its start, the system shall move it to `CANCELLED_BY_OWNER`, keep it as a past record, and leave the request closed.

### AC-117: Cancelling a past visit or no-show is refused
**Covers:** B-96
**Flow:** UC-6 ext 1a

If an owner attempts to cancel a past visit or a no-show, then the system shall refuse it and shall not change any state.

### AC-118: Owner cannot cancel or view another owner's appointment
**Covers:** B-97
**Flow:** UC-6 ext 1b

If an owner attempts to cancel or view another owner's appointment, then the system shall respond with 404, shall make no database change, and shall disclose nothing about that appointment.

### AC-119: Clinic-change marker for owners
**Covers:** B-98
**Flow:** cross-cutting

The system shall show a "changed by the clinic" marker with the staff reason and the original time for staff-rescheduled or staff-cancelled items under *My appointments*.

### AC-120: Cancelled appointments retained under past items
**Covers:** B-99
**Flow:** UC-6 step 2

The system shall keep cancelled appointments as rows and show them under past items.

### AC-121: Request event timeline recorded and shown to staff
**Covers:** B-100
**Flow:** UC-4 step 1

The system shall record each request state transition and staff/owner action (from-state, to-state, actor, action, reason, payload, timestamp) in `scheduling_request_event` and show the timeline to staff on the request detail page.

### AC-122: Route to staff transition
**Covers:** B-100
**Flow:** UC-3 ext 3a

When an owner routes a request to staff from *Interpreted*, *Interpretation failed*, or *Suggestion offered*, the system shall move it to *With staff* and log the event.

### AC-123: Disallowed request transitions refused
**Covers:** B-101
**Flow:** cross-cutting

If any request-lifecycle action is attempted while the request is not in a state whose transition table lists that action, then the system shall refuse it itself and shall not change the request's state or produce any side effect, regardless of whether the UI hides it.

### AC-124: Disallowed appointment transitions refused
**Covers:** B-102
**Flow:** cross-cutting

If any appointment-lifecycle action is attempted while the appointment is not in a state whose transition table lists that action, then the system shall refuse it and shall not change the appointment's state or produce any side effect.

### AC-125: Seeded accounts are exactly these rows
**Covers:** B-103
**Flow:** cross-cutting

The seeded `users`/accounts data shall contain exactly the following rows:

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
| admin | admin123 | staff | — |
| staff | staff123 | staff | — |

### AC-126: No accounts or roles beyond the seeded set
**Covers:** B-103
**Flow:** cross-cutting

The seeded accounts data shall contain no other accounts and no roles other than `owner` and `staff`.

### AC-127: Each seeded password verifies
**Covers:** B-103
**Flow:** cross-cutting

Each listed secret shall verify against the stored value using the application's password encoder.

### AC-128: Seeded clinic opening hours are exactly this row
**Covers:** B-103
**Flow:** cross-cutting

The seeded clinic opening hours shall contain exactly the following row, where "Closed" means no opening hours:

| Clinic | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|--------|--------|---------|-----------|----------|--------|----------|--------|
| Clinic A | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 18:00 | 9:00 - 17:00 | 10:00 - 16:00 | Closed | Closed |

### AC-129: Seeded veterinarian weekly schedules are exactly these rows
**Covers:** B-103
**Flow:** cross-cutting

The seeded veterinarian weekly schedules shall contain exactly the following rows and blocks (17 working blocks in total), where a blank cell means the veterinarian does not work that day:

| Vet | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|---|---|---|---|---|---|---|---|
| James Carter | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | 11:00 - 12:00 | | |
| Helen Leary | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | | | |
| Linda Douglas | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | | | |
| Rafael Ortega | | | | 9:00 - 17:00 | 10:00 - 16:00 | | |
| Henry Stevens | | | | 9:00 - 17:00 | 10:00 - 16:00 | | |
| Sharon Jenkins | 13:00 - 14:00 | | | 9:00 - 17:00 | 10:00 - 16:00 | | |

### AC-130: No veterinarian working blocks beyond the seeded set
**Covers:** B-103
**Flow:** cross-cutting

The seeded veterinarian weekly schedules shall contain no other working blocks beyond the 17 listed above.

### AC-131: Seeded veterinarian exceptions are exactly these rows
**Covers:** B-103
**Flow:** cross-cutting

The seeded veterinarian exceptions (all *unavailable*) shall contain exactly the following rows:

| Vet | Unavailable on |
|---|---|
| James Carter | 2026-09-15 |
| Henry Stevens | 2026-09-15 |
| Henry Stevens | 2026-09-17 |
| Henry Stevens | 2026-09-21 |
| Henry Stevens | 2026-10-22 |
| Sharon Jenkins | 2026-10-22 |

### AC-132: No seeded leave or clinic closures
**Covers:** B-103
**Flow:** cross-cutting

The seeded data shall contain no leave rows and no clinic-closure rows.

### AC-133: Seeded configuration defaults are exactly these rows
**Covers:** B-103
**Flow:** cross-cutting

The seeded configuration defaults shall contain exactly the following rows:

| Setting | Default |
|---|---|
| Booking horizon | 30 days ahead |
| Visit duration bounds / default | 15–60 minutes / 30 minutes |
| Start-time grid | 15 minutes |
| Parts of day | morning 09:00–12:00 · afternoon 12:00–17:00 · evening 17:00–18:00 |
| Time zone | Europe/Amsterdam |
| Emergency phone | placeholder number (seeded, staff-editable) |

### AC-134: No configuration defaults beyond the seeded set
**Covers:** B-103
**Flow:** cross-cutting

The seeded configuration shall contain no other defaults beyond those listed above.

## Non-functional

### AC-135: Isolated test database
**Covers:** Verification expectations, "Test data isolation"; RA-9; EA-2
**Flow:** cross-cutting

The automated tests shall run against an isolated in-memory H2 database created by the same Flyway migrations, and shall not modify the runtime H2 file.

### AC-136: Pinned test clock and horizon
**Covers:** Verification expectations, "Test clock"; RA-48
**Flow:** cross-cutting

The automated tests shall pin "now" to Monday 2026-09-07 09:00 Europe/Amsterdam via an injectable `Clock`, yielding an owner horizon of 2026-09-07..2026-10-07.

### AC-137: Deterministic AI in tests
**Covers:** Verification expectations, "Deterministic AI"; RA-21; RA-22; RA-34
**Flow:** cross-cutting

The automated tests shall use the `stub` deterministic `RequestInterpreter` with a synchronous executor and shall never call the live model.

### AC-138: End-to-end MockMvc lifecycle coverage
**Covers:** Verification expectations, "End-to-end level"; RA-47
**Flow:** cross-cutting

The test suite shall include at least one MockMvc test with the real filter chain that, interleaving an owner session and a staff session with CSRF, drives request → consent → interpretation → suggestion → ask again → staff hand-off → staff suggestion → accept → completed visit, plus a declined-consent request booked by staff and closed as no-show.

### AC-139: Single random-port smoke test
**Covers:** Verification expectations, "End-to-end level"; RA-47
**Flow:** cross-cutting

The test suite shall include exactly one random-port smoke test that loads the login page and one authenticated page to prove wiring.

### AC-140: Localization key existence and no literals
**Covers:** B-19, B-20; RA-49
**Flow:** cross-cutting

The system shall resolve every user-visible string introduced by the feature — page text, labels, placeholders, other attributes, and Java-produced status/flash messages — through a message key present in every shipped locale bundle, authored in English with identical English placeholder text in each non-English bundle, and shall emit no English literal directly from application code introduced or modified by this feature.

### AC-141: Concurrency guarantees under simultaneous submission
**Covers:** B-23, B-24, B-58; Verification expectations, "Concurrency"
**Flow:** cross-cutting

When submissions are made simultaneously, the system shall guarantee that two owners can never confirm the same veterinarian-and-time and that a pet can never end up with two active requests.

## Coverage exclusions

- Performance/load thresholds: the spec sets only the solver's 1-second termination budget (covered by AC-83); no throughput or p95 latency target is specified, so no load-performance AC is written.
- Accessibility: no accessibility requirement (e.g., WCAG level) is stated in the spec; presentation coherence is covered by AC-16.
- Browser/OS compatibility: not specified; the feature reuses the stock layout (AC-16) and no compatibility matrix is given.
- Non-H2 databases, Gradle, external notifications, self-registration, password recovery, multiple clinics, and waitlists: explicitly out of scope per the spec's *Out of scope* section.
