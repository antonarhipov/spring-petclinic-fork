# Smart Appointment Scheduling — Proposal

_Revised 2026-09-06 to incorporate the decisions recorded in `decisions.md` (D1–D36)._

## 1. Purpose and POC boundaries

Add smart appointment scheduling to the PetClinic application.

**NB!** This is a POC. We **do not** intend to implement this feature for all possible deployment scenarios.
Supporting one database (H2, file-based so data survives a restart) and one build tool (Maven) is sufficient. The goal
is
to demonstrate the feasibility of using the LLM for appointment scheduling in a veterinary clinic application.

## 2. The owner experience

Pet owners request an appointment for their pet using a free-form description of their availability. Instead of picking
a date and time, the owner describes when they would prefer to come and when they cannot come. For example:

> "I'd prefer Tuesday or Thursday after lunch. I can't come on Wednesday, and mornings before 10 don't work for me.
> Friday morning would also be possible if necessary."

The application interprets the request into structured scheduling information to propose a suitable slot, taking into
account the owner's availability and preferences, veterinarian availability, existing appointments and the clinic's 
scheduling constraints. The owner is guided to **one suggestion at a time**; the clinic's full availability calendar is 
never shown to owners.

## 3. Actors and access

Two kinds of authenticated users interact with the application: pet **owners** and clinic **staff**. Veterinarians are
scheduling resources with availability; they do not log in.

- **Owner** — acts only on behalf of their own pets and sees only their own data (their owner record, pets, requests,
  appointments) plus veterinarian names and specialties.
- **Staff** — see everything: all owners and pets, the clinic calendar, veterinarian schedules, clinic settings and the
  fallback queue. Staff can act on behalf of any owner and pet: they may create a scheduling request for any pet (it
  starts directly in *With staff*, see §8), book, reschedule and cancel appointments, and release held slots. Staff
  never trigger AI interpretation and never give consent on an owner's behalf.
- **Authentication** — minimal form login with two roles (owner, staff) and seeded accounts (see *Seed data*).
  Self-registration, forced first-login password change and password reset are **out of scope** for the POC; accounts
  are provisioned through seed data.
- **Isolation is enforced, not cosmetic** — an owner who tries to open or act on another owner's data is denied and
  learns nothing about that data. This applies to **every page in the application**, not only the scheduling pages.
  Concretely: an owner opening another owner's data receives the standard not-found page (**404**), indistinguishable
  from a non-existent id; an owner opening a staff page receives **403**.

## 4. User experience and navigation

The feature is part of the existing PetClinic web UI, not a separate application.

- **Coherent look** — new pages use the existing page layout, navigation bar, form controls and styles. No second
  layout, stylesheet or inline styling.
- **Signed-in state** — every page shows who is signed in and a *Logout* action; the login page uses the same layout.
- **Login everywhere** — every page requires login. There are no anonymous pages besides the login page, static
  resources and `/actuator/health` (used by container health checks). All other actuator endpoints and the H2 console
  require the staff role.
- **Role-specific navigation** — the navigation bar shows only the entries the signed-in role may use:
    - *Owner*: **My pets** (their own owner record and pets, read-only view — editing owner/pet records stays a staff
      task by default) and **My appointments** (all upcoming and past appointments and any in-progress scheduling
      request
      for each of their pets, with the actions the owner may take: start a request, resume a request, cancel an upcoming
      appointment).
    - *Staff*: the stock pages (find owners, owner/pet/visit management, veterinarians) plus **Scheduling queue**,
      **Calendar** and **Clinic settings**.
- **Stock pages are staff-only** — owner search, owner/pet create-and-edit, visit entry and the veterinarians page
  (`/owners/**`, `/vets/**`) are available to staff only. Owners see veterinarian names and specialties inside the
  scheduling request page.
- **Separate owner routes** — owner pages live under `/my/**` (`/my/pets`, `/my/appointments`, `/my/requests/**`) and
  are bound to the signed-in owner; no owner id ever appears in an owner URL. Security is path-based and the stock
  controllers stay essentially untouched.
- **Landing page (default)** — after login owners land on *My appointments*; staff land on the scheduling queue.

## 5. Understanding the request (AI interpretation and consent)

The owner describes the reason for the visit and when they are (and are not) available in free text; the application
uses AI to turn that into a structured interpretation.

- **What is derived** — estimated visit length, whether general or specialty care is needed (and which specialty),
  preferred / allowed / excluded time windows (window model in §7), and an optional preferred veterinarian. A missing
  reason for the visit is **not** a failure: it defaults to general care with the default duration.
- **Input language** — free text is expected in English. This is documented in the README and hinted on the request
  page; it is neither detected nor enforced.
- **Explicit consent** — the owner must knowingly agree before their text is sent to the AI. The consent page states
  exactly what is sent: the owner's free text plus clinic data (specialties, veterinarian names, opening hours per
  weekday, today's date, time zone, duration bounds) — **no owner or pet identifiers**. If they decline, the request
  goes to staff.
- **Asynchronous interpretation** — consent enqueues the interpretation on a bounded background executor and puts the
  request into the *Interpreting* state (§6), in which the owner may only view the status or abandon. The request
  detail page polls a state-only JSON endpoint (`GET /my/requests/{id}/status` → `{"state": "..."}`) with a small
  script and shows a visible *Refresh* link when the script does not run. The job has an overall deadline of 120 s
  (connect timeout 5 s, **no** automatic retry). On expiry, on any transport error, and for every request still
  *Interpreting* when the application starts, the request goes to staff as "AI unavailable". A result arriving for an
  abandoned request is discarded.
- **Deterministic, schema-constrained call** — the model is called with temperature 0 and structured (JSON-schema)
  output. The prompt enumerates the clinic's specialties and veterinarian names; the schema constrains the specialty to
  one of those values or `OTHER` (the model's free label is kept for staff) and the preferred veterinarian to one of the
  enumerated ids or none — an unrecognised name simply yields no preference. The model must emit **explicit**
  preferred/allowed windows: phrases like "any day except Wednesday" are expanded by the model from the opening hours,
  date and time zone in the prompt. Exclusions are only subtractive and there is no "whole week" marker, so what is
  persisted is exactly what is matched.
- **Read-only review and confirmation** — the owner reviews the interpretation in readable form and confirms it before
  anything is scheduled. Nothing derived by the AI is discarded or recomputed: the **complete interpretation is
  persisted** verbatim as structured data (all three window lists, care type and specialty, duration, preferred vet)
  together with the raw model JSON, the model tag and the prompt version, and shown in full. The persisted duration is
  the model's **raw** estimate; the clinic's bounds are applied at matching time (§7), and the review page shows the
  effective duration with a message-key note when it was clamped. The page is rendered from the structured data through
  message keys and locale-aware formatters — the model never produces user-visible prose.
  To change the interpretation, the owner edits the text, which requires fresh consent and a fresh interpretation.
- **Interpretation versions** — every interpretation is an immutable version tagged with its origin, `AI` or `STAFF`.
  A staff edit (§8) creates a new `STAFF` version that becomes the current one; the AI original stays in history. The
  owner's request page and the staff queue show which origin is current.
- **Interpretation failed** means exactly one of: the output is unparseable; **or** it contains zero preferred +
  allowed windows; **or** the model reports it did not understand the text (`understood = false`).
- **Rephrasing** — after a failed interpretation the owner may rephrase as often as they like; the option to route the
  request to staff is always offered; from the third failed attempt on, the application recommends routing to staff
  (rephrasing remains allowed).
- **Specialty no veterinarian offers** — a specialty need that no veterinarian in the clinic provides (`OTHER`) is
  routed to staff. It is never downgraded to general care.
- **Testability** — the interpreter sits behind an interface with a deterministic test double; automated tests never
  call the live model. Both *Interpretation failed* and *AI unavailable* are exercised through the test double.

## 6. Guided appointment selection and holds

The system suggests **one suitable slot at a time**. While a slot is offered to an owner it is **held**: no other owner
is offered it. A hold has **no timer** — it lasts until the owner accepts, asks for another option, routes the request
to staff, edits the text, or abandons the request, until a new suggestion supersedes it, **until staff release it**
(from the scheduling queue or the calendar, with a recorded reason; the request then moves to *With staff*), or until a
schedule change makes the slot unavailable (§10, same outcome).

The owner can:

- **accept** the suggestion — the appointment is confirmed;
- **ask for another option** — this rejects the current suggestion: the hold is released, that exact veterinarian-and-
  time is permanently excluded for this request, and the next best slot is suggested using the confirmed
  interpretation, the current calendar and holds, and all earlier rejections (there is no cap on how often).
  Rejections persist for the **whole request**, also across text edits and fresh interpretations; asking for another
  option is irreversible and the UI says so. Every suggestion shows the rank reason (§7);
- **ask for staff assistance** — the request goes to the staff queue;
- **abandon** the request at any time, including after it has been forwarded to staff for handling.

When no candidate slots remain, the request moves **automatically** to *With staff* with reason "no slots available"
and an explanatory message; the owner keeps *abandon* (editing the text is not offered in that state). An owner can
have **at most one active request per pet**.

### Request lifecycle

| State                 | Meaning                                                                                                            | Owner may                                                              |
|-----------------------|--------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| Awaiting consent      | Text entered, not yet sent to the AI                                                                               | consent · decline (→ With staff) · edit text · abandon                 |
| Interpreting          | Text sent to the AI, result pending (120 s deadline, then → With staff as "AI unavailable")                        | view status · abandon                                                  |
| Interpretation failed | AI could not produce usable availability (unparseable, no preferred/allowed windows, or `understood = false`)       | rephrase (fresh consent) · route to staff · abandon                    |
| Interpreted           | Read-only interpretation shown, awaiting confirmation                                                              | confirm and get a suggestion · edit text · route to staff · abandon    |
| Suggestion offered    | Exactly one slot is held for this owner                                                                            | accept · ask for another option · route to staff · edit text · abandon |
| With staff            | In the staff queue (declined consent, AI unavailable, unmatched specialty, no slots available, hold released by staff, schedule changed, staff-created, or owner's choice) | view status · abandon — staff book directly or place a suggestion      |
| Accepted              | Appointment confirmed                                                                                              | manage it under *My appointments*                                      |
| Abandoned             | Closed by the owner                                                                                                | —                                                                      |

Invariants:

- A suggestion may be produced **only** from a confirmed interpretation (state *Interpreted*, or *Suggestion offered*
  when asking again). Requests in any other state — including declined-consent and staff-queued requests —
  are refused by the system itself, not merely hidden by the UI.
- If the slot the owner accepts has become unavailable in the meantime, the owner is shown the next suggestion (or the
  staff hand-off) with an explanation — **never an error page**.
- A staff-placed suggestion puts the request into *Suggestion offered* and follows exactly the same rules.
- A request enters the AI flow only through the owner's own consent; staff-created requests start in *With staff*.
  Staff never act inside *Interpreted* / *Suggestion offered* except to release the hold.
- Concurrency is resolved safely: two owners can never confirm the same veterinarian-and-time, and a pet can never end
  up with two active requests, even under simultaneous submissions (mechanisms in §14).

## 7. Matching 

The guided flow is not a full-schedule optimization. The system enumerates the feasible slots for the single request
and ranks them; the top slot is suggested, and re-ranking happens on "ask for another option".

- **Window model** — a window is `{weekday or concrete date, start time, end time}` in clinic-local time. Recurring
  weekday windows apply to every matching day inside the horizon; concrete dates ("the 18th", "next week") are
  resolved by the interpreter relative to the request creation date and persisted as absolute dates; named parts of the
  day are resolved to times by the interpreter using the clinic settings passed in the prompt.
- **Closed by default** — a slot is feasible only if it lies inside one of the owner's **preferred or allowed**
  windows; times the owner never mentioned are not bookable. Excluded windows are purely subtractive. Preferred and
  allowed windows are equally feasible and differ only in ranking.
- **Hard (never violated)** — within clinic opening hours; within one veterinarian's continuous working block after
  date exceptions, leave and clinic closures; no overlap with existing appointments or active holds; required
  specialty present; inside a preferred or allowed window and not inside an excluded window; not before the minimum
  lead time; within the booking horizon.
- **Horizon and lead time** — candidates lie in `[start of (today + minimum lead days), today + booking horizon]`;
  with the defaults (1 day, 30 days) same-day slots are never suggested. Staff bookings are subject to neither (§8).
- **Ranking (strict lexicographic, no weights)** — 1. preferred window before allowed window; 2. preferred
  veterinarian before others; 3. earliest start; 4. fewest appointments already booked for that veterinarian on that
  day; 5. lowest veterinarian id. Specialists get no preference beyond the hard specialty constraint. The result is
  deterministic.
- **Rank reason** — every suggestion shows the owner a one-line, message-key based reason derived from the ranking tier
  (preferred vs allowed window; whether the preferred veterinarian was honoured). No model text is shown.
- **Slot sizing** — start times on a 15-minute grid; the persisted raw duration estimate is clamped at matching time
  to the configured bounds (default 15–60 minutes, default duration 30).

## 8. Staff fallback and urgent care

The automated flow never leaves an owner stuck: when it cannot proceed, the request is handed to staff.

- **Triggers** — owner declines consent; AI unavailable (transport error, 120 s deadline, or the startup sweep of
  requests still *Interpreting*); no veterinarian offers the required specialty; request still un-interpretable after
  rephrasing; no slots available; hold released by staff; schedule changed under a held slot; owner asks for staff;
  request created by staff.
- **Staff-created requests** — staff can create a request for any pet; it starts directly in *With staff* with no AI
  and no consent. Staff never trigger interpretation.
- **Staff-assisted scheduling** — while a request is *With staff* (and only then), staff may author a new
  interpretation version (`STAFF` origin; the form is pre-filled from the latest version, or empty for declined-consent
  requests) and then either **book directly** (the owner sees the appointment on next login and may cancel it) or
  **place a suggestion** the owner accepts or rejects like any other. Bookings and suggestions are always computed from
  the latest version.
- **Constraints that bind staff** — staff bookings, staff-placed suggestions and reschedules must respect opening
  hours, the veterinarian's working blocks and no-overlap. They are **not** bound by the owner's windows, the booking
  horizon, the lead time or the required specialty; the booking form highlights matching veterinarians and warns on a
  specialty mismatch.
- **Scheduling queue** — two sections: *Needs staff* (requests in *With staff*, oldest first, with the trigger reason
  and the current interpretation origin) and *In progress* (every other open request with its state, held slot and
  hold age; read-only except *release hold*). There is no claiming: requests carry a version, and a staff action on a
  request that changed underneath fails with a flash message.
- **Urgent care** — the automated scheduling flow does **not** attempt to detect or handle emergencies, and there is no
  urgency flag or checkbox. Instead, clear urgent-care guidance with the clinic's contact details is always visible on
  every owner scheduling page and the request detail page, so an owner whose pet needs immediate attention knows to
  call the clinic right away rather than wait for a suggestion.

## 9. Staff calendar and appointment management

Staff work against the clinic's **full calendar** and own appointments through their whole lifecycle.

- **Full calendar means** — for each veterinarian and each day, over a navigable range that covers the booking horizon:
  the clinic's opening hours or closure, the veterinarian's effective working blocks (weekly schedule adjusted by
  exceptions and leave), booked appointments, currently held slots, and remaining free capacity. Not merely a list of
  appointments.
- **Calendar shape** — a **day view**: one column per veterinarian, rows on the 15-minute grid within that day's
  opening hours, previous/next-day and date-picker navigation covering the horizon; closed and unavailable ranges
  shaded; appointments and holds as blocks with click-through to details. Held slots show owner, pet and hold age and
  can be released from the calendar.
- **Direct scheduling** — staff can book, reschedule or cancel appointments directly under the constraints in §8;
  every staff-initiated change records a reason. A reschedule is a **direct change** (no owner re-acceptance): the
  owner sees the new time and the reason under *My appointments* and may cancel.
- **Lifecycle** — an appointment is `HELD`, `CONFIRMED`, `CANCELLED`, `COMPLETED` or `NO_SHOW`. Only **after the
  appointment's start time** may staff mark it **completed** or **no-show**; both are final, and cancelled appointments
  cannot be completed. On completion staff enter (or accept a pre-filled) description, and a visit linked to the
  appointment is recorded in the pet's history with the appointment date. The stock *Add visit* form remains available
  for walk-ins (visits without an appointment).
- **Owner self-service** — owners view and cancel their own upcoming appointments at any time before the start (no
  minimum-notice rule, **no reason required**; the appointment records that the owner cancelled and when). Cancelling
  never re-opens the request. Past visits and no-shows cannot be cancelled. Owners never see or act on other owners'
  appointments.

## 10. Clinic configuration and veterinarian availability

Scheduling behavior is driven by configuration that staff can edit in the UI; sensible defaults are seeded.

- **Clinic settings** — clinic opening hours per weekday (open/closed, opening and closing time), visit-duration bounds
  and default, booking horizon, minimum lead days for suggestions, and the named parts of the day (morning =
  opening–12:00, afternoon = 12:00–17:00, evening = 17:00–closing).
- **Veterinarian availability** — each veterinarian has a recurring weekly schedule that supports split shifts, plus
  date-specific exceptions and leave. Clinic-wide closures apply to everyone.
- **Changes versus existing bookings** — a change to a weekly schedule, exception, leave, opening hours or closure that
  conflicts with a `CONFIRMED` appointment is **rejected** and the conflicting appointments are listed; staff must
  reschedule them first. Conflicting `HELD` slots are released automatically and their requests move to *With staff*
  with reason "schedule changed".
- **Single time zone** — the clinic operates in one configured time zone, default `Europe/Amsterdam`.

## 11. Localization

All user-visible text produced by the feature — page text, form labels, placeholders and other attributes, and
status/flash messages produced in Java — is resolved through message keys that exist in **every** shipped locale bundle.
New strings are authored in English with the same English text as placeholder in the other locales (no translation
required). No English literal may be emitted directly from application code.

## 12. Verification expectations

The feature is accepted only with evidence of the following:

- **Seed data** — a migration test asserts every seeded row of *Seed data* by value (not by count) and verifies every
  seeded password with the application's password encoder.
- **Interpretation fidelity** — a test proves that an interpretation persisted and read back is identical to the one
  produced.
- **Request lifecycle** — tests prove that every disallowed transition in §6 is refused by the system and has no side
  effect.
- **Security** — negative-path tests assert both the denial **and** the absence of disclosure or side effect, for the
  **entire** URL space of the application (stock pages and the JSON status endpoint included), for anonymous users,
  owners on staff pages (403), and owners on other owners' data (404).
- **Concurrency** — a test races two threads on the same veterinarian-and-time and proves that exactly one hold or
  booking wins; a test proves that simultaneous request creation for one pet yields exactly one active request.
- **Deterministic time** — tests pin the injected `Clock` (e.g. 2026-09-07 09:00 Europe/Amsterdam) so suggestions,
  horizon and the seeded exceptions are reproducible.
- **End-to-end** — at least one automated test drives the complete lifecycle through the real HTTP endpoints — a real
  embedded server (`webEnvironment = RANDOM_PORT`) with a real HTTP client handling session cookies and CSRF, not
  MockMvc — as an owner and as staff: request → consent → interpretation (polling the HTML detail page while
  *Interpreting*) → suggestion → ask again → staff hand-off → staff suggestion → accept → completed visit, plus a
  declined-consent request booked by staff and closed as no-show.
- **Test isolation** — automated tests run against an isolated in-memory database; the runtime database file is not
  committed to the repository and is never modified by tests.
- **Localization** — an automated check covers templates (text and attributes) and Java-produced messages.
- **UI walkthrough** — before a UI phase is accepted, a human signs in as an owner and as staff and confirms on the
  rendered pages: same layout/navigation/styles as stock pages, only that role's menu entries, signed-in user and
  logout visible, and that an owner cannot open another owner's pages.

## 13. Scope boundaries

- **In scope** — authenticated owner smart scheduling, the guided single-suggestion flow with holds, staff queue with
  book-or-suggest, full clinic calendar, appointment lifecycle, editable clinic and veterinarian configuration,
  role-based navigation, owner data isolation across the whole application.
- **Out of scope** — self-registration, first-login password change, password recovery, exposing the full calendar to
  owners, external notifications, multiple clinics, waitlists, hold expiry timers, non-English owner input, non-H2
  databases, Gradle, Docker Compose and Kubernetes deployment.
- **Removed from the repository** — `db/mysql`, `db/postgres`, the `mysql`/`postgres` profiles and their integration
  tests, `docker-compose.yml`, `k8s/`, `build.gradle` and `settings.gradle`; the README states that the POC is H2
  (file-based) + Maven only. A repository should not contain configurations that are known not to work.

## 14. Technical details to consider

- Flyway for database migrations (baseline of the stock schema and data, then the scheduling schema and seed data);
  only the H2 migration path exists.
- Spring Security form login. Security is path-based: `/my/**` for the owner role; stock and staff pages (`/owners/**`,
  `/vets/**`, queue, calendar, settings, actuator endpoints other than health, H2 console) for the staff role; anonymous
  access only to `/login`, static resources and `/actuator/health`. Cross-owner access → 404, owner on a staff page →
  403.
- Spring AI 2.0.1 for LLM integration; Ollama as backend, **installed locally** (≥ 0.13.1, reachable on
  `localhost:11434`; no Compose file — the README instructs to install Ollama and pull the model). The model tag is a
  configurable property, default `ministral-3:14b`. Calls use temperature 0 and JSON-schema structured output.
  Interpretation runs on a bounded `@Async` executor (2 threads, unbounded queue); on startup, requests still
  *Interpreting* are swept to *With staff*.
- Time — an injectable `java.time.Clock` bean provides "now" (production: system clock in the configured zone; tests
  pin it). Appointments, holds, windows, exceptions and opening hours are stored as clinic-local `LocalDate` +
  `LocalTime`; the configured zone is used only to derive "today" (DST-proof comparisons and grid).
- Data model — a single `appointments` table with a status (`HELD`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`);
  a hold is a `HELD` row linked to its request and is **deleted** when the hold ends (the request's rejection list is
  the durable record). Interpretation versions are immutable rows with `origin ∈ {AI, STAFF}`; AI versions also store
  the raw model JSON, model tag and prompt version. Visits carry a nullable FK to the appointment. Cancelled
  appointments record who cancelled (owner or staff) and when.
- Concurrency — all hold/confirm/book/reschedule operations for a veterinarian run under a pessimistic row lock on that
  veterinarian (`SELECT … FOR UPDATE`) and re-check overlap inside the transaction. One active request per pet is
  enforced by a nullable `active_pet_id` column (equal to `pet_id` while the request is open, nulled on *Accepted* /
  *Abandoned*) with `UNIQUE(active_pet_id)`; a losing insert redirects the owner to the existing request. Requests
  carry a version for optimistic locking of staff actions.

## 15. Seed data (normative)

The migration **must seed exactly the rows below — no more, no fewer**. A blank cell means the veterinarian does not
work that day; "Closed" means no opening hours.

### Accounts

Usernames are the owner's lowercased first name; passwords are the username followed by `123`. Staff accounts are
`admin` and `staff`. No other accounts or roles exist.

| Username | Password   | Role  | Linked owner      |
|----------|------------|-------|-------------------|
| george   | george123  | owner | George Franklin   |
| betty    | betty123   | owner | Betty Davis       |
| eduardo  | eduardo123 | owner | Eduardo Rodriquez |
| harold   | harold123  | owner | Harold Davis      |
| peter    | peter123   | owner | Peter McTavish    |
| jean     | jean123    | owner | Jean Coleman      |
| jeff     | jeff123    | owner | Jeff Black        |
| maria    | maria123   | owner | Maria Escobito    |
| david    | david123   | owner | David Schroeder   |
| carlos   | carlos123  | owner | Carlos Estaban    |
| admin    | admin123   | staff | —                 |
| staff    | staff123   | staff | —                 |

### Clinic opening hours

| Clinic   | Monday       | Tuesday      | Wednesday    | Thursday     | Friday        | Saturday | Sunday |
|----------|--------------|--------------|--------------|--------------|---------------|----------|--------|
| Clinic A | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 18:00 | 9:00 - 17:00 | 10:00 - 16:00 | Closed   | Closed |

### Veterinarian weekly schedules (17 working blocks)

| Vet            | Monday        | Tuesday      | Wednesday    | Thursday     | Friday        | Saturday | Sunday |
|----------------|---------------|--------------|--------------|--------------|---------------|----------|--------|
| James Carter   | 9:00 - 17:00  | 9:00 - 17:00 | 9:00 - 12:00 |              | 11:00 - 12:00 |          |        |
| Helen Leary    | 9:00 - 17:00  | 9:00 - 17:00 | 9:00 - 12:00 |              |               |          |        |
| Linda Douglas  | 9:00 - 17:00  | 9:00 - 17:00 | 9:00 - 12:00 |              |               |          |        |
| Rafael Ortega  |               |              |              | 9:00 - 17:00 | 10:00 - 16:00 |          |        |
| Henry Stevens  |               |              |              | 9:00 - 17:00 | 10:00 - 16:00 |          |        |
| Sharon Jenkins | 13:00 - 14:00 |              |              | 9:00 - 17:00 | 10:00 - 16:00 |          |        |

### Seeded veterinarian exceptions (all *unavailable*)

| Vet            | Unavailable on |
|----------------|----------------|
| James Carter   | 2026-09-15     |
| Henry Stevens  | 2026-09-15     |
| Henry Stevens  | 2026-09-17     |
| Henry Stevens  | 2026-09-21     |
| Henry Stevens  | 2026-10-22     |
| Sharon Jenkins | 2026-10-22     |

No leave and no clinic closures are seeded. The exception dates are normative test fixtures (tests pin the clock, see
§12); they only have an observable effect on suggestions while they fall inside the booking horizon, i.e. around
September–October 2026.

### Configuration defaults

| Setting                         | Default                                                                                                            |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Booking horizon                 | 30 days ahead                                                                                                      |
| Minimum lead days               | 1 (suggestions start tomorrow; not applied to staff bookings)                                                      |
| Visit duration bounds / default | 15–60 minutes / 30 minutes                                                                                         |
| Start-time grid                 | 15 minutes                                                                                                         |
| Parts of day                    | morning 09:00–12:00 · afternoon 12:00–17:00 · evening 17:00–18:00 (i.e. opening–12:00, 12:00–17:00, 17:00–closing) |
| Time zone                       | Europe/Amsterdam                                                                                                   |
