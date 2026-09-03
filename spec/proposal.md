# Smart Appointment Scheduling — Proposal

## 1. Purpose and POC boundaries

Add smart appointment scheduling to the PetClinic application.

**NB!** This is a POC. We **do not** intend to implement this feature for all possible deployment scenarios.
Supporting one database (H2, file-based so data survives a restart) and one build tool (Maven) is sufficient. The goal is
to demonstrate the feasibility of combining an LLM and Timefold for appointment scheduling in a veterinary clinic
application. Java 21 is required.

## 2. The owner experience

Pet owners request an appointment for their pet using a free-form description of their availability. Instead of picking
a date and time, the owner describes when they would prefer to come and when they cannot come. For example:

> "I'd prefer Tuesday or Thursday after lunch. I can't come on Wednesday, and mornings before 10 don't work for me.
> Friday morning would also be possible if necessary."

The application interprets the request into structured scheduling information, and the scheduling system uses Timefold
to propose a suitable slot, taking into account the owner's availability and preferences, veterinarian availability,
existing appointments and the clinic's scheduling constraints. The owner is guided to **one suggestion at a time**; the
clinic's full availability calendar is never shown to owners.

## 3. Actors and access

Two kinds of authenticated users interact with the application: pet **owners** and clinic **staff**. Veterinarians are
scheduling resources with availability; they do not log in.

- **Owner** — acts only on behalf of their own pets and sees only their own data (their owner record, pets, requests,
  appointments) plus veterinarian names and specialties.
- **Staff** — see everything: all owners and pets, the clinic calendar, veterinarian schedules, clinic settings and the
  fallback queue. Staff can act on behalf of any owner and pet.
- **Authentication** — minimal form login with two roles (owner, staff) and seeded accounts (see *Seed data*).
  Self-registration, forced first-login password change and password reset are **out of scope** for the POC; accounts
  are provisioned through seed data.
- **Isolation is enforced, not cosmetic** — an owner who tries to open or act on another owner's data is denied and
  learns nothing about that data. This applies to **every page in the application**, not only the scheduling pages.

## 4. User experience and navigation

The feature is part of the existing PetClinic web UI, not a separate application.

- **Coherent look** — new pages use the existing page layout, navigation bar, form controls and styles. No second
  layout, stylesheet or inline styling.
- **Signed-in state** — every page shows who is signed in and a *Logout* action; the login page uses the same layout.
- **Login everywhere** — every page requires login. There are no anonymous pages besides the login page and static
  resources.
- **Role-specific navigation** — the navigation bar shows only the entries the signed-in role may use:
  - *Owner*: **My pets** (their own owner record and pets, read-only view — editing owner/pet records stays a staff
    task by default) and **My appointments** (all upcoming and past appointments and any in-progress scheduling request
    for each of their pets, with the actions the owner may take: start a request, resume a request, cancel an upcoming
    appointment).
  - *Staff*: the stock pages (find owners, owner/pet/visit management, veterinarians) plus **Scheduling queue**,
    **Calendar** and **Clinic settings**.
- **Stock pages are staff-only** — owner search, owner/pet create-and-edit, visit entry and the veterinarians page are
  available to staff only. Owners see veterinarian names and specialties inside the scheduling request page.
- **Landing page (default)** — after login owners land on *My appointments*; staff land on the scheduling queue.

## 5. Understanding the request (AI interpretation and consent)

The owner describes the reason for the visit and when they are (and are not) available in free text; the application
uses AI to turn that into a structured interpretation.

- **What is derived** — estimated visit length, whether general or specialty care is needed (and which specialty),
  preferred / allowed / excluded time windows, and an optional preferred veterinarian.
- **Explicit consent** — the owner must knowingly agree before their text is sent to the AI. If they decline, the
  request goes to staff.
- **Read-only review and confirmation** — the owner reviews the interpretation in readable form and confirms it before
  anything is scheduled. Nothing derived by the AI is discarded or recomputed: the **complete interpretation is
  persisted** and shown verbatim (all three window lists, care type and specialty, duration, preferred vet).
  To change it, the owner edits the text, which requires fresh consent and a fresh interpretation.
- **Rephrasing** — if the AI cannot produce usable availability, the owner may rephrase as often as they like; the
  option to route the request to staff is always offered; from the third failed attempt on, the application recommends
  routing to staff (rephrasing remains allowed).
- **Specialty no veterinarian offers** — a specialty need that no veterinarian in the clinic provides is routed to
  staff. It is never downgraded to general care.
- **Testability** — the interpreter sits behind an interface with a deterministic test double; automated tests never
  call the live model. If the model is unavailable at runtime, the request goes to staff.

## 6. Guided appointment selection and holds

The system suggests **one suitable slot at a time**. While a slot is offered to an owner it is **held**: no other owner
is offered it. A hold has **no timer** — it lasts until the owner accepts, asks for another option, routes the request
to staff, edits the text, or abandons the request, or until a new suggestion supersedes it.

The owner can:

- **accept** the suggestion — the appointment is confirmed;
- **ask for another option** — this rejects the current suggestion: the hold is released, that exact veterinarian-and-
  time is permanently excluded for this request, and the next best slot is suggested using the confirmed
  interpretation, the current calendar and holds, and all earlier rejections (there is no cap on how often);
- **ask for staff assistance** — the request goes to the staff queue;
- **abandon** the request at any time, including after it has been forwarded to staff for handling.

When no candidate slots remain, the system offers staff assistance instead of a dead end. An owner can have **at most
one active request per pet**.

### Request lifecycle

| State | Meaning | Owner may |
|---|---|---|
| Awaiting consent | Text entered, not yet sent to the AI | consent · decline (→ With staff) · edit text · abandon |
| Interpretation failed | AI could not produce usable availability | rephrase (fresh consent) · route to staff · abandon |
| Interpreted | Read-only interpretation shown, awaiting confirmation | confirm and get a suggestion · edit text · route to staff · abandon |
| Suggestion offered | Exactly one slot is held for this owner | accept · ask for another option · route to staff · edit text · abandon |
| With staff | In the staff queue (declined consent, AI or solver unavailable, unmatched specialty, exhausted, or owner's choice) | view status · abandon — staff book directly or place a suggestion |
| Accepted | Appointment confirmed | manage it under *My appointments* |
| Abandoned | Closed by the owner | — |

Invariants:

- A suggestion may be produced **only** from a confirmed interpretation (state *Interpreted*, or *Suggestion offered*
  when asking again). Requests in any other state — including declined-consent and staff-queued requests —
  are refused by the system itself, not merely hidden by the UI.
- If the slot the owner accepts has become unavailable in the meantime, the owner is shown the next suggestion (or the
  staff hand-off) with an explanation — **never an error page**.
- A staff-placed suggestion puts the request into *Suggestion offered* and follows exactly the same rules.
- Concurrency is resolved safely: two owners can never confirm the same veterinarian-and-time, and a pet can never end
  up with two active requests, even under simultaneous submissions.

## 7. Matching (Timefold as a ranker)

The guided flow is not a full-schedule optimization. The system enumerates the feasible slots for the single request
and uses Timefold to rank them; the top slot is suggested, and re-ranking happens on "ask for another option".

- **Hard (never violated)** — within clinic opening hours; within one veterinarian's continuous working block after
  date exceptions, leave and clinic closures; no overlap with existing appointments or active holds; required
  specialty present; not inside the owner's excluded windows; within the booking horizon.
- **Soft (preferences)** — prefer the owner's preferred windows, prefer the preferred veterinarian, prefer the earliest
  date.
- **Slot sizing** — start times on a 15-minute grid; estimated duration clamped to the configured bounds (default
  15–60 minutes, default duration 30). The solver runs with a short fixed time budget so suggestions feel instant.

## 8. Staff fallback and urgent care

The automated flow never leaves an owner stuck: when it cannot proceed, the request is handed to staff.

- **Triggers** — owner declines consent; AI or solver unavailable; no veterinarian offers the required specialty;
  request still un-interpretable after rephrasing; suggestions exhausted; owner asks for staff.
- **Staff-assisted scheduling** — staff verify or complete the interpretation and then either **book directly**
  (the owner sees the appointment on next login and may cancel it) or **place a suggestion** the owner accepts or
  rejects like any other.
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
- **Direct scheduling** — staff can book, reschedule or cancel appointments directly; every staff-initiated change
  records a reason.
- **Lifecycle** — after an appointment, staff mark it **completed** (which records a visit in the pet's history) or
  **no-show**.
- **Owner self-service** — owners view and cancel their own upcoming appointments at any time before the start (no
  minimum-notice rule). Past visits and no-shows cannot be cancelled. Owners never see or act on other owners'
  appointments.

## 10. Clinic configuration and veterinarian availability

Scheduling behavior is driven by configuration that staff can edit in the UI; sensible defaults are seeded.

- **Clinic settings** — clinic opening hours per weekday (open/closed, opening and closing time), visit-duration bounds
  and default, booking horizon, and the named parts of the day (morning = opening–12:00, afternoon = 12:00–17:00,
  evening = 17:00–closing).
- **Veterinarian availability** — each veterinarian has a recurring weekly schedule that supports split shifts, plus
  date-specific exceptions and leave. Clinic-wide closures apply to everyone.
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
  **entire** URL space of the application (stock pages included), for anonymous users, owners on staff pages, and
  owners on other owners' data.
- **End-to-end** — at least one automated test drives the complete lifecycle through the real HTTP endpoints as an
  owner and as staff: request → consent → interpretation → suggestion → ask again → staff hand-off → staff suggestion
  → accept → completed visit, plus a declined-consent request booked by staff and closed as no-show.
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
  owners, external notifications, multiple clinics, waitlists, non-H2 databases, Gradle.

## 14. Technical details to consider

- Flyway for database migrations (baseline of the stock schema and data, then the scheduling schema and seed data).
- Spring Security form login.
- Spring AI 2.0.1 for LLM integration; Ollama as backend. The model tag is a configurable property, default
  `gemma4:latest`.
- Timefold 2.5.0 as the candidate-slot ranker (requires Java 21).
- H2 in persistent file mode for the running application; in-memory H2 for tests.

## 15. Seed data (normative)

The migration **must seed exactly the rows below — no more, no fewer**. A blank cell means the veterinarian does not
work that day; "Closed" means no opening hours.

### Accounts

Usernames are the owner's lowercased first name; passwords are the username followed by `123`. Staff accounts are
`admin` and `staff`. No other accounts or roles exist.

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

### Clinic opening hours

| Clinic | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|--------|--------|---------|-----------|----------|--------|----------|--------|
| Clinic A | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 18:00 | 9:00 - 17:00 | 10:00 - 16:00 | Closed | Closed |

### Veterinarian weekly schedules (17 working blocks)

| Vet | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|---|---|---|---|---|---|---|---|
| James Carter | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | 11:00 - 12:00 | | |
| Helen Leary | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | | | |
| Linda Douglas | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | | | |
| Rafael Ortega | | | | 9:00 - 17:00 | 10:00 - 16:00 | | |
| Henry Stevens | | | | 9:00 - 17:00 | 10:00 - 16:00 | | |
| Sharon Jenkins | 13:00 - 14:00 | | | 9:00 - 17:00 | 10:00 - 16:00 | | |

### Seeded veterinarian exceptions (all *unavailable*)

| Vet | Unavailable on |
|---|---|
| James Carter | 2026-09-15 |
| Henry Stevens | 2026-09-15 |
| Henry Stevens | 2026-09-17 |
| Henry Stevens | 2026-09-21 |
| Henry Stevens | 2026-10-22 |
| Sharon Jenkins | 2026-10-22 |

No leave and no clinic closures are seeded.

### Configuration defaults

| Setting | Default |
|---|---|
| Booking horizon | 30 days ahead |
| Visit duration bounds / default | 15–60 minutes / 30 minutes |
| Start-time grid | 15 minutes |
| Parts of day | morning 09:00–12:00 · afternoon 12:00–17:00 · evening 17:00–18:00 (i.e. opening–12:00, 12:00–17:00, 17:00–closing) |
| Time zone | Europe/Amsterdam |
