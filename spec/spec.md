# Smart Appointment Scheduling — Specification

This specification consolidates `spec/proposal.md` with the binding decisions recorded in `spec/scope-decisions.md`
(grilling session of 2026-09-03, 49 resolved questions). It is self-contained: every decision, table and definition an
implementing agent needs is stated here, not referenced by pointer. Where a decision changed the original proposal, the
change is recorded under *Resolved ambiguities*.

---

## Feature summary

Add smart appointment scheduling to the PetClinic web application as a proof of concept (H2 file database, Maven, Java
21, Ollama-backed Spring AI, Timefold). Authenticated pet **owners** request an appointment for one of their pets by
describing, in free text, the reason for the visit and when they can and cannot come. After explicit consent, an LLM
turns that text into a structured, persisted interpretation that the owner reviews and confirms; Timefold then ranks the
feasible slots and the system guides the owner **one held suggestion at a time** (accept, ask for another option, route
to staff, edit, or abandon) without ever exposing the clinic calendar. Clinic **staff** work against the full calendar,
own the staff fallback queue, may complete or edit interpretations, book directly or place suggestions, and manage every
appointment through its lifecycle. All access is role-scoped and owner-isolated across the entire application, and all
user-visible text is localized through message keys.

---

## Resolved ambiguities

Each entry is a binding decision; the rationale follows. Items marked **[changes proposal]** override the original
proposal text.

### Data model and persistence
- **RA-1 Appointment vs. stock visit (Q1).** A new `appointment` table (pet, vet, start, duration, status, reason) is
  separate from the stock `visits` table. On completion a `visits` row is created carrying a nullable `appointment_id`
  back-reference. The stock add-visit page is retained for walk-ins (staff only). *Rationale:* keeps stock behavior
  intact while giving appointments their own lifecycle.
- **RA-2 Interpretation storage (Q21).** Normalized `interpretation` + `interpretation_window` rows (window kind
  `PREFERRED`/`ALLOWED`/`EXCLUDED`) plus the raw model response stored as a CLOB. Interpretations are versioned per
  request with provenance `AI`/`STAFF`. *Rationale:* satisfies the read-back fidelity test and staff editing without
  losing the raw output.
- **RA-3 Hold location (Q22).** The single hold per request lives as fields on the request row (held vet, start,
  duration); "one hold per request" is therefore structural. Calendar and overlap checks union `appointment` rows with
  requests whose hold fields are set. *Rationale:* simplest correct representation.
- **RA-4 Cancellation and audit (Q11).** Cancelling an accepted appointment keeps the request closed. The appointment
  is retained with status `CANCELLED_BY_OWNER`/`CANCELLED_BY_STAFF` and shown under past items. Mandatory staff reasons
  go into an `appointment_change` audit table (appointment, actor, action, reason, timestamp), shown per appointment on
  the staff calendar.
- **RA-5 Request event log (Q24).** One `scheduling_request_event` table (request, from-state, to-state, actor, action,
  reason, payload, timestamp). Rejections are events carrying the excluded vet/time/scope. Staff see the timeline on the
  request detail page.
- **RA-6 Concurrency mechanism (Q13).** Every hold/confirm/book operation runs in a transaction that first takes
  `SELECT … FOR UPDATE` on the veterinarian row (serialising per vet) then re-checks overlaps against appointments and
  holds. "One active request per pet" is enforced by a nullable `active_pet_id` column set only while the request is
  active, plus a unique index (H2 treats NULLs as distinct).
- **RA-7 AI provenance (Q16).** The raw response text, model tag and a prompt-version string are stored alongside the
  structured interpretation and are visible to staff only.
- **RA-8 Emergency guidance text (Q29). [changes proposal §10/§15].** A fixed banner rendered from message keys ("If
  your pet is in immediate danger, do not wait for a suggestion — call {0} now"); an `emergency_phone` setting is added
  to clinic settings, seeded with a placeholder number; shown on every owner scheduling page and the request detail
  page.
- **RA-9 Repository cleanup (Q18). [changes proposal §14].** Bump `pom.xml` to Java 21; move the stock schema/data into
  Flyway `V1__`/`V2__` (H2 only, `spring.sql.init` disabled); delete the MySQL/Postgres property files and SQL folders,
  `docker-compose.yml` and `k8s/`; README notes H2-only. Gradle files are left untouched but unmaintained.

### Interpretation
- **RA-10 Owner input shape (Q2). [changes proposal §5].** Two fields — *reason* and *availability* — together form
  "the text". Editing either requires fresh consent and re-interpretation; both are re-sent on any edit.
- **RA-11 Window model (Q3). [changes proposal §7].** A window = {weekday | specific date | date range} × {start–end
  time}; weekday windows recur across the whole horizon. If the owner gives **any** preferred/allowed windows, slots
  outside their union are **hard-excluded** ("allowed" defines the universe; "preferred" ranks within it). No windows at
  all → the whole horizon is allowed.
- **RA-12 Vague phrases and time reference (Q4). [changes proposal §5].** The LLM emits `HH:mm` when explicit, otherwise
  a part-of-day token that the app resolves against clinic settings. The reference date/time is injected from an
  injectable `Clock`. Earliest bookable slot = first 15-minute grid point ≥ 2 hours from now.
- **RA-13 Parts-of-day vocabulary (Q19).** Token vocabulary `MORNING`, `AFTERNOON`, `EVENING` as configured in clinic
  settings; a window may carry several tokens. Prompt mapping: "after lunch" → AFTERNOON+EVENING; "end of day"/"late" →
  EVENING; "before N"/"after N" → explicit `HH:mm`. Tokens are resolved per weekday against that day's opening hours
  (e.g. Friday MORNING = 10:00–12:00; Monday EVENING is empty and is dropped).
- **RA-14 Prompt inputs and output shape (Q20).** System prompt carries: current date/time and horizon end, weekday
  opening hours, part-of-day tokens, veterinarians (id, name, specialties), and offered specialties (radiology, surgery,
  dentistry). The model answers strictly as JSON matching a Java record (Spring AI structured output /
  `BeanOutputConverter`): `reasonSummary`, `estimatedMinutes`, `careType` (`GENERAL`|`SPECIALTY`), `specialty` (closed
  list or `OTHER:<text>`), `preferredVetId`, `cannotInterpret`, and three window lists. An `OTHER` specialty
  is unmatched → *With staff*.
- **RA-15 Preferred veterinarian resolution (Q43).** The model returns a vet id from the prompt list or `null`;
  unknown/ambiguous names → `null`. The app validates the id exists. The read-only review shows the resolved vet name.
  No fuzzy matching in Java. A preferred vet lacking the required specialty is removed by the hard specialty constraint
  with no owner-facing warning.
- **RA-16 "Usable availability" definition (Q15).** Usable = a well-formed interpretation whose allowed universe
  (RA-11) minus excluded windows is non-empty within the horizon ("anytime" is usable). Contradictory windows and an
  explicit `cannotInterpret` flag count as a failed attempt (owner may rephrase). Malformed JSON after one retry,
  transport errors and timeouts = "model unavailable" → *With staff*.
- **RA-17 Emergency / urgent care (Q14). [changes proposal §8].** The automated scheduling flow does **not** detect or
  act on urgency: there is no AI `urgent` flag, no owner "this is urgent" checkbox, and no queue pinning. Urgent care is
  handled solely by a fixed, always-visible urgent-care banner (RA-8) that tells the owner to call the clinic; nothing in
  the request lifecycle, interpretation, ranking or staff queue branches on urgency.
- **RA-18 Interpretation latency (Q36). [changes proposal §5/§6].** Interpretation is **asynchronous** with a polling
  page. A new `Interpreting` lifecycle state is added. Ollama client timeout is 60 s (configurable
  `scheduling.ai.timeout`), one retry on malformed output, no retry on timeout; timeout → *With staff* with the
  unavailability reason recorded.
- **RA-19 Polling mechanism (Q45).** Meta-refresh only: `<meta http-equiv="refresh" content="3">`, no JavaScript, no
  JSON endpoint. The request detail URL is the single entry point and renders whatever state the request is in.
- **RA-20 `Interpreting` state actions (Q46).** While in `Interpreting` the only owner action is **abandon**; a late
  result for an abandoned request is discarded. *Route to staff* and *edit text* wait until the result lands. Exactly
  one in-flight interpretation per request.
- **RA-21 Executor and crash recovery (Q47).** Spring `@Async` on a dedicated single-thread executor; the request id is
  passed and the result applied in a fresh transaction that re-checks state. On startup every request still in
  `Interpreting` is moved to *Interpretation failed* with reason "interrupted". Tests use a synchronous executor.
- **RA-22 Runtime AI provider (Q37).** Property `scheduling.ai.provider=ollama|stub` (default `ollama`). `stub` wires a
  rule-based interpreter (keyword matching for weekdays, parts of day, "not", specialties) so the full flow
  can be demoed offline. Tests use this stub plus hand-built fixtures.

### Guided flow, holds and rejections
- **RA-23 Stale holds (Q5). [changes proposal §6].** No timer. Staff can release a hold (→ *With staff* with a reason)
  and the owner can also cancel it. Held slots are visible on the staff calendar/queue. No background job.
- **RA-24 Rejection granularity (Q6).** Exact vet-and-time exclusion is the default; on rejection the owner picks a
  scope chip — *not this time*, *not this day*, *not this vet* — stored as an additional exclusion applied on
  re-ranking. Still one suggestion at a time, no calendar exposure.
- **RA-25 Rejection scope semantics (Q49).** *Not this time* = this vet at this start; *not this day* = this calendar
  date for **all** vets; *not this vet* = this vet for the whole horizon. All three are hard exclusions for the current
  interpretation only.
- **RA-26 Rejections after a text edit (Q7).** Rejections belong to an interpretation; editing the text clears them from
  application. They remain in the event log for audit but are not applied.
- **RA-27 Rejection visibility (Q34).** A compact "You have ruled out: …" list on the request page, with no undo;
  editing the text resets everything.
- **RA-28 Staff actions vs. holds and confirmed appointments (Q9).** Staff **may** book over a hold (the calendar shows
  it as held and asks for confirmation; the owner's request falls back to the next suggestion on their next action).
  Availability edits (exception, leave, closure, opening hours) that conflict with **confirmed** appointments are
  **refused** with the list of conflicts — staff must reschedule/cancel first. Conflicting holds are simply invalidated.
- **RA-29 Invalid hold detection (Q30).** Opening a request in *Suggestion offered* re-validates the hold; if invalid,
  the system immediately produces the next suggestion (or the staff hand-off) with an explanatory notice. Accept
  re-validates again inside the locked transaction.
- **RA-30 Owner self-overlap (Q23). [changes proposal §7].** No overlap with any confirmed appointment or active hold
  belonging to the same owner (across all their pets) is a hard constraint; staff may override with a confirmation.
- **RA-31 Direct booking attaches to an open request (Q42).** Staff may book for any pet at any time. If an open request
  exists in **any** non-terminal state, the booking form offers *attach* (→ *Accepted*, hold released, event logged) or
  *leave open* (request untouched; the appointment appears under the owner's list regardless).

### Matching
- **RA-32 Solver model and weights (Q10). [changes proposal §7].** A single planning entity with one planning variable
  (the slot) over enumerated feasible slots. Hard constraints are applied when enumerating candidates **and** re-asserted
  as Timefold hard constraints. `HardMediumSoft` priority: preferred window (medium) > preferred vet (soft, high weight)
  > earliest date/time (soft, per-minute penalty tie-breaker). Termination: 1 s unimproved or exhausted.
- **RA-33 Solver synchronous (Q48).** The solver call is synchronous, inside the same transaction that takes the per-vet
  locks for the chosen slot. Only the LLM call is async.
- **RA-34 Stack wiring (Q44).** `timefold-solver-spring-boot-starter` (`timefold.solver.solve.duration=1s`) and
  `spring-ai-starter-model-ollama` from the Spring AI BOM, both hidden behind the app's own `SlotRanker` and
  `RequestInterpreter` interfaces so tests never touch the libraries.

### Staff surfaces
- **RA-35 Staff complete the interpretation (Q8).** Staff get a structured interpretation form; a staff-authored/edited
  interpretation is stored as a new version with provenance `STAFF`; the `AI` version is never overwritten. "Place a
  suggestion" = click a free cell on the calendar **or** press *Suggest* to run the solver — both create a hold and put
  the request into *Suggestion offered*.
- **RA-36 Queue scope and order (Q25).** Two tabs. **Needs staff** (default) = *With staff* requests, oldest first, with
  the hand-off trigger shown. **All open** lists every non-terminal request with state,
  held slot and age, plus the *release hold* action.
- **RA-37 Calendar layout (Q12).** Day view, one column per veterinarian, one row per 15-minute grid step; cells
  coloured closed / off-shift / free / booked / held; prev/next-day navigation and a date picker. Clicking a free cell
  starts a direct booking (or places a suggestion for a queued request).
- **RA-38 Booking and suggestion flows on the calendar (Q26).** Queue → *Place suggestion* opens the calendar in
  **picking mode** for that request (banner with pet/duration/specialty, incompatible cells greyed); clicking a cell
  places the hold; alternatively *Suggest* runs the solver. Calendar free cell → booking form with vet/start prefilled,
  owner search → pet, duration, reason; if that pet has an open request, staff choose *attach* or *leave open*. Direct
  booking needs no interpretation.
- **RA-39 Exceptions and leave semantics (Q27). [changes proposal §10].** Exception = single date with zero or more
  replacement blocks (zero = unavailable all day); leave = inclusive whole-day date range; clinic closure = single date.
  Effective blocks for a day = closure ? none : leave ? none : exception ? its blocks : weekly blocks, all intersected
  with opening hours; precedence closure > leave > exception > weekly.
- **RA-40 Horizon and staff (Q40).** The booking horizon constrains owners and the solver only. Staff may book any
  future date; the calendar navigates freely forward; past days are viewable, not bookable.
- **RA-41 Duration bounds and staff (Q41).** The visit-duration bounds (15–60) clamp the AI estimate and the owner flow
  only. Staff may enter any duration that is a multiple of the 15-minute grid and fits the vet's block; the staff form
  defaults to the configured default duration (30).
- **RA-42 Owner sees staff changes (Q38).** A "changed by the clinic" marker with the staff reason text and the original
  time is shown for rescheduled/cancelled items under *My appointments*. The reason field is owner-facing by design.
- **RA-43 Completion timing (Q17).** Staff may mark *completed*/*no-show* only once the scheduled start time has passed;
  before that, the only staff actions are reschedule and cancel.
- **RA-44 Visit created on completion (Q33).** The completion form is prefilled with the owner's reason text (or the
  staff booking reason), editable before saving; visit date = appointment date; `appointment_id` link set.

### Security, URLs and verification
- **RA-45 URL space (Q35). [changes proposal §3/§4].** Owner pages live under `/my/**` (`/my/pets`,
  `/my/appointments`, `/my/requests/{id}/…`) and never take an owner id in the URL — the id comes from the session.
  Everything under `/owners/**`, `/vets*`, `/staff/**` (queue, calendar, settings, vet availability) is staff-only by
  URL matcher.
- **RA-46 Denial semantics (Q28). [changes proposal §3].** Other-owner data → **404** (the same page as a genuinely
  missing id); an owner on a staff-only URL → **403** page; anonymous → redirect to `/login`. Enforced twice: URL role
  matchers in the `SecurityFilterChain` **and** an ownership guard inside services.
- **RA-47 End-to-end transport (Q31).** MockMvc with the real filter chain and the deterministic AI double, two sessions
  (owner + staff) interleaved through the whole flow, CSRF included. Exactly one smoke test on a random-port server
  (login page + one authenticated page) proves wiring.
- **RA-48 Test clock (Q32).** Pinned "now" = Monday **2026-09-07 09:00 Europe/Amsterdam**. The owner horizon is
  2026-09-07..2026-10-07, covering the September seed exceptions and excluding 2026-10-22 (which tests the horizon
  boundary). Runtime uses the system clock.
- **RA-49 Localization check scope (Q39).** Key-existence check for **all** templates and all Java `MessageSource`
  usages; the no-literal-text check applies to templates and Java classes **introduced or modified by this feature**
  (listed explicitly in the test). Stock templates are not retro-fitted.

---

## Explicit assumptions

Accepted unless challenged; each is confirmed as part of this spec.

- **EA-1** Request creation is two steps: a form (pet — only pets without an active request —, reason, availability)
  creating the request in *Awaiting consent*, followed by a consent page.
- **EA-2** Flyway layout: `V1__stock_schema`, `V2__stock_data`, `V3__scheduling_schema`, `V4__scheduling_seed`;
  `spring.sql.init` disabled; the same migrations run against the in-memory test H2.
- **EA-3** Accounts live in a `users` table (username, bcrypt password, role, nullable `owner_id` FK). `/` redirects by
  role. The login page uses the stock layout.
- **EA-4** A preferred vet who lacks the required specialty is filtered by the hard specialty constraint; no
  owner-facing warning.
- **EA-5** Weekly blocks outside opening hours are accepted in the editor (with a warning on the page) and intersected
  at runtime, not rejected.
- **EA-6** Rescheduling mutates the appointment in place; the audit event holds the old time. Cancelled appointments
  keep their row and appear under past items.
- **EA-7** The README is rewritten for H2-only / Java 21 / Ollama setup.
- **EA-8** The offered specialties are the stock set: radiology, surgery, dentistry. Six veterinarians exist.
- **EA-9** Eleven message bundles ship (`messages*.properties`, incl. `de, en, es, fa, hi, ja, ko, pt, ru, tr`); new
  keys are added to every bundle.

---

## Handled edge cases

Edge cases that branch off a use-case step are written as extensions of that use case and cross-referenced here.

- **E-1 Owner declines consent** → request goes to staff. `E-1 → UC-1 ext 3a`. (B-26)
- **E-2 Model unavailable / timeout / malformed after retry** → *With staff* with reason recorded. `E-2 → UC-3 ext 1a`.
  (B-35)
- **E-3 Interpretation not usable (contradiction or `cannotInterpret`)** → *Interpretation failed*; owner may rephrase.
  `E-3 → UC-3 ext 1b`. (B-34)
- **E-4 Third failed interpretation attempt** → system recommends routing to staff, rephrasing still allowed.
  `E-4 → UC-3 ext 2a`. (B-37)
- **E-5 Specialty no vet offers (`OTHER`)** → *With staff*, never downgraded to general care. `E-5 → UC-1 ext 5a`.
  (B-36)
- **E-6 No feasible slots on confirmation** → *With staff* instead of a dead end. `E-6 → UC-1 ext 6a`. (B-54)
- **E-7 Suggestions exhausted on "ask for another option"** → *With staff*. `E-7 → UC-2 ext 2a`. (B-54)
- **E-8 Hold invalidated (staff booked over it or availability edit)** discovered on view → next suggestion or staff
  hand-off with an explanatory notice, never an error page. `E-8 → UC-2 ext 3a`. (B-55)
- **E-9 Accepted slot became unavailable inside the locked transaction** → next suggestion or staff hand-off with an
  explanation, never an error page. `E-9 → UC-1 ext 8a`. (B-57)
- **E-10 App restart while `Interpreting`** → request moved to *Interpretation failed* (reason "interrupted").
  `E-10 → UC-1 ext 4a`. (B-31)
- **E-11 Late interpretation result for an abandoned request** → discarded. `E-11 → UC-1 ext 4b`. (B-30)
- **E-12 Availability edit conflicts with a confirmed appointment** → refused with the conflict list; staff must
  reschedule/cancel first. `E-12 → UC-5 ext 5a`. (B-85)
- **E-13 Availability edit conflicts only with holds** → those holds are invalidated. `E-13 → UC-5 ext 5b`. (B-86)
- **E-14 Owner attempts to cancel a past visit or no-show** → refused with no side effect. `E-14 → UC-6 ext 1a`. (B-96)
- **E-15 Two owners attempt to confirm the same vet-and-time / second active request for the same pet** → serialised;
  exactly one succeeds. `E-15 → UC-1 ext 8b`. (B-23, B-24, B-58)

---

## State model

### Scheduling request lifecycle

One `scheduling_request` per (pet, active attempt). Actions marked *(system)* are performed by the application, not the
owner. "Staff book (attach)" and "Staff book (leave open)" are available from every non-terminal, non-*Accepted*,
non-*Abandoned* state (RA-31). Every transition not listed below is **refused by the system with no side effect**
(B-101).

| State | Allowed action | Resulting state |
|---|---|---|
| **Awaiting consent** | consent | Interpreting |
| | decline consent | With staff |
| | edit text | Awaiting consent |
| | abandon | Abandoned |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Awaiting consent |
| **Interpreting** | abandon | Abandoned |
| | *(system)* interpretation usable | Interpreted |
| | *(system)* interpretation failed / contradiction / `cannotInterpret` | Interpretation failed |
| | *(system)* model unavailable / timeout / malformed-after-retry | With staff |
| | *(system)* restart while in flight | Interpretation failed (reason "interrupted") |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Interpreting |
| **Interpretation failed** | edit text / rephrase (fresh consent) | Awaiting consent |
| | route to staff | With staff |
| | abandon | Abandoned |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Interpretation failed |
| **Interpreted** | confirm — feasible slots exist | Suggestion offered |
| | confirm — no feasible slots | With staff |
| | edit text | Awaiting consent |
| | route to staff | With staff |
| | abandon | Abandoned |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Interpreted |
| **Suggestion offered** | accept — hold valid | Accepted |
| | accept — hold invalid, next slot exists | Suggestion offered |
| | accept — hold invalid, none left | With staff |
| | ask for another option — slots remain | Suggestion offered |
| | ask for another option — exhausted | With staff |
| | *(system)* view re-validates — hold invalid, next exists | Suggestion offered |
| | *(system)* view re-validates — hold invalid, none left | With staff |
| | route to staff | With staff |
| | edit text | Awaiting consent |
| | abandon | Abandoned |
| | staff release hold | With staff |
| | staff place a suggestion | Suggestion offered |
| | staff book (attach) | Accepted |
| | staff book (leave open) | Suggestion offered |
| **With staff** | view status | With staff |
| | abandon | Abandoned |
| | staff create/edit interpretation (new STAFF version) | With staff |
| | staff place a suggestion | Suggestion offered |
| | staff book directly (attach) | Accepted |
| | staff book directly (leave open) | With staff |
| **Accepted** | *(terminal for the request)* — the appointment is managed under the appointment lifecycle; owner cancellation does **not** reopen the request | Accepted |
| **Abandoned** | *(terminal)* — none | Abandoned |

### Appointment lifecycle

One `appointment` per confirmed/booked slot. Every transition not listed is **refused by the system with no side
effect** (B-102).

| State | Allowed action | Resulting state |
|---|---|---|
| **Confirmed** | owner cancel (before start) | Cancelled by owner |
| | staff cancel (before start, with reason) | Cancelled by staff |
| | staff reschedule (before start, with reason; mutated in place) | Confirmed |
| | staff mark completed (after start) | Completed (creates a visit) |
| | staff mark no-show (after start) | No-show |
| **Cancelled by owner** | *(terminal)* — shown under past items; cannot be cancelled | Cancelled by owner |
| **Cancelled by staff** | *(terminal)* — shown under past items; cannot be cancelled | Cancelled by staff |
| **Completed** | *(terminal)* — a visit is recorded | Completed |
| **No-show** | *(terminal)* — cannot be cancelled | No-show |

---

## Use cases

Steps are at the intention level and each cites at least one behavior. Exactly one use case is marked `(primary)`.

```text
UC-1  Owner books an appointment through the guided flow                          (primary)
Actor: Owner   Precondition: signed in; selected pet has no active request
Main success scenario
  1. Owner starts a request, entering reason and availability text for a pet      → B-21, B-22, B-18
  2. Owner reviews the request and grants explicit consent to interpret it        → B-25
  3. System interprets the text asynchronously and shows a waiting page           → B-27, B-28, B-29
  4. System presents the persisted interpretation for read-only review            → B-33, B-38, B-46, B-47
  5. Owner confirms the interpretation                                            → B-50, B-51
  6. System holds and offers exactly one ranked slot                              → B-51, B-52, B-53, B-64, B-66
  7. Owner accepts the suggestion                                                 → B-56, B-58
  8. System confirms the appointment and shows it under My appointments           → B-56, B-78
Extensions
  3a. Owner declines consent → request → With staff; → UC-4                                                              → B-26   (E-1)
  4a. Application restarts while interpreting → request → Interpretation failed (reason "interrupted"); resume at 2      → B-31   (E-10)
  4b. Interpretation result arrives after the owner abandoned → result discarded; → end                                 → B-30   (E-11)
  5a. Specialty no vet offers (OTHER) → request → With staff; → UC-4                                                     → B-36   (E-5)
  6a. No feasible slots on confirmation → request → With staff; → UC-4                                                   → B-54   (E-6)
  8a. Held slot became unavailable inside the locked transaction → next suggestion or staff hand-off, explained; resume at 6 | → UC-4   → B-57   (E-9)
  8b. Concurrent confirm/second-active-request attempt → serialised, at most one succeeds; the loser is re-offered or refused; resume at 6   → B-23, B-24, B-58   (E-15)
Postcondition: request Accepted; a Confirmed appointment exists and is visible to the owner and on the staff calendar
```

```text
UC-2  Owner asks for another option
Actor: Owner   Precondition: request in Suggestion offered
Main success scenario
  1. Owner rejects the current suggestion and picks a scope chip                  → B-59, B-60, B-63
  2. System releases the hold, records the exclusion, and offers the next slot    → B-53, B-59, B-62, B-66
Extensions
  2a. No candidate slots remain → request → With staff; → UC-4                                                          → B-54   (E-7)
  3a. Owner reopens the request and the hold is now invalid → system produces the next suggestion or the staff hand-off with a notice; resume at 2 | → UC-4   → B-55   (E-8)
Postcondition: a new slot is held and offered, or the request is With staff
```

```text
UC-3  Owner rephrases after an interpretation problem
Actor: Owner   Precondition: request in Interpretation failed
Main success scenario
  1. Owner edits the reason/availability text, clearing prior rejections          → B-47, B-61
  2. Owner grants fresh consent; System re-interprets and shows the review        → B-25, B-33, B-46
Extensions
  1a. Model unavailable / timeout / malformed after retry → request → With staff, reason recorded; → UC-4              → B-35   (E-2)
  1b. Interpretation still not usable (contradiction or cannotInterpret) → request → Interpretation failed; resume at 1 → B-34   (E-3)
  2a. This is the third or later failed attempt → System recommends routing to staff (rephrasing still allowed); resume at 1 | → UC-4   → B-37   (E-4)
  3a. Owner chooses to route to staff → request → With staff; → UC-4                                                    → B-101  
Postcondition: request Interpreted, or With staff, or awaiting another rephrase
```

```text
UC-4  Staff resolve a queued request
Actor: Staff   Precondition: request in With staff
Main success scenario
  1. Staff open the request from the Needs staff queue and review its timeline     → B-68, B-100, B-49
  2. Staff create or complete the structured interpretation (new STAFF version)    → B-73, B-48
  3. Staff place a suggestion via calendar pick or by running the solver           → B-74, B-52, B-64
  4. Owner accepts or rejects the staff-placed suggestion like any other           → B-75, B-56
Extensions
  3a. Staff instead book directly for the pet and attach to the open request → request → Accepted, hold released, event logged; → UC-5   → B-76, B-77   
  3b. Staff book directly and leave the request open → appointment appears under the owner's list; request untouched   → B-77, B-78
Postcondition: request Accepted or in Suggestion offered
```

```text
UC-5  Staff manage an appointment through its lifecycle
Actor: Staff   Precondition: a Confirmed appointment exists
Main success scenario
  1. Staff open the appointment on the day-view calendar                           → B-79, B-81
  2. Staff reschedule or cancel it before start, recording a reason                → B-90, B-91, B-94, B-98
  3. After the start time passes, staff mark it completed or no-show               → B-92
  4. On completion System creates a visit prefilled and editable                   → B-93
Extensions
  5a. An availability edit conflicts with a confirmed appointment → refused with the conflict list; staff reschedule/cancel first; resume at 2   → B-85   (E-12)
  5b. An availability edit conflicts only with holds → those holds are invalidated; resume at 1                         → B-86   (E-13)
Postcondition: the appointment is Completed, No-show, Cancelled, or rescheduled; audit rows recorded
```

```text
UC-6  Owner cancels an upcoming appointment
Actor: Owner   Precondition: a Confirmed appointment for the owner's pet, before its start
Main success scenario
  1. Owner opens My appointments and cancels an upcoming appointment               → B-95
  2. System marks it Cancelled by owner and keeps it under past items              → B-95, B-99
Extensions
  1a. Owner attempts to cancel a past visit or no-show → refused with no side effect; → end                            → B-96   (E-14)
  1b. Owner attempts to cancel another owner's appointment → 404, no change; → end                                     → B-97   (E-15)
Postcondition: the appointment is Cancelled by owner; the request stays closed
```

---

## Normative data

Copied verbatim from `spec/proposal.md` §15, extended with the `emergency_phone` default (RA-8). Each table is
**exactly these rows — no more, no fewer**. Any account, role, row or default not listed is out of scope and its
presence in the implementation is a defect.

### Accounts

Usernames are the owner's lowercased first name; passwords are the username followed by `123`. Staff accounts are
`admin` and `staff`. No other accounts or roles exist. **Exactly these rows — no more, no fewer.**

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

**Exactly these rows — no more, no fewer.** "Closed" means no opening hours.

| Clinic | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|--------|--------|---------|-----------|----------|--------|----------|--------|
| Clinic A | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 18:00 | 9:00 - 17:00 | 10:00 - 16:00 | Closed | Closed |

### Veterinarian weekly schedules (17 working blocks)

**Exactly these rows and blocks — no more, no fewer.** A blank cell means the veterinarian does not work that day.

| Vet | Monday | Tuesday | Wednesday | Thursday | Friday | Saturday | Sunday |
|---|---|---|---|---|---|---|---|
| James Carter | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | 11:00 - 12:00 | | |
| Helen Leary | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | | | |
| Linda Douglas | 9:00 - 17:00 | 9:00 - 17:00 | 9:00 - 12:00 | | | | |
| Rafael Ortega | | | | 9:00 - 17:00 | 10:00 - 16:00 | | |
| Henry Stevens | | | | 9:00 - 17:00 | 10:00 - 16:00 | | |
| Sharon Jenkins | 13:00 - 14:00 | | | 9:00 - 17:00 | 10:00 - 16:00 | | |

### Seeded veterinarian exceptions (all *unavailable*)

**Exactly these rows — no more, no fewer.** No leave and no clinic closures are seeded.

| Vet | Unavailable on |
|---|---|
| James Carter | 2026-09-15 |
| Henry Stevens | 2026-09-15 |
| Henry Stevens | 2026-09-17 |
| Henry Stevens | 2026-09-21 |
| Henry Stevens | 2026-10-22 |
| Sharon Jenkins | 2026-10-22 |

### Configuration defaults

**Exactly these rows — no more, no fewer.**

| Setting | Default |
|---|---|
| Booking horizon | 30 days ahead |
| Visit duration bounds / default | 15–60 minutes / 30 minutes |
| Start-time grid | 15 minutes |
| Parts of day | morning 09:00–12:00 · afternoon 12:00–17:00 · evening 17:00–18:00 (i.e. opening–12:00, 12:00–17:00, 17:00–closing) |
| Time zone | Europe/Amsterdam |
| Emergency phone | placeholder number (seeded, staff-editable) |

---

## Presentation and navigation

- **Layout reuse.** New pages use the existing layout fragment (`fragments/layout.html`), navigation bar, form controls
  (`fragments/inputField.html`, `fragments/selectField.html`) and styles. No second layout, stylesheet or inline
  styling. (B-15)
- **Signed-in state.** Every page shows the signed-in username and a *Logout* action; the login page uses the same
  layout. (B-6)
- **Login everywhere.** Every page requires login. The only anonymous resources are the login page and static
  resources. (B-1)
- **Menu entries per role.**
  - *Owner:* **My pets** (own owner record and pets, read-only) and **My appointments** (upcoming/past appointments and
    any in-progress request per pet, with start/resume/cancel actions). (B-13, B-16)
  - *Staff:* the stock pages (find owners, owner/pet/visit management, veterinarians) plus **Scheduling queue**,
    **Calendar** and **Clinic settings**. (B-14)
- **Landing page per role.** Owners land on *My appointments*; staff land on the *Scheduling queue*. `/` redirects by
  role. (B-5)
- **Access policy for pre-existing pages.** `/owners/**`, `/vets`, `/vets.html` and all `/staff/**` routes are
  staff-only. Owners see veterinarian names and specialties only inside the scheduling request page. (B-7, B-17)
- **Owner URL scoping.** Owner pages live under `/my/**` and never carry an owner id in the URL; the id comes from the
  session. (B-9)
- **Denial semantics.** Other-owner resource → 404; owner on a staff-only URL → 403; anonymous → redirect to `/login`.
  (B-7, B-10, B-1)
- **Emergency banner.** Every owner scheduling page and the request detail page show the urgent-care banner with the
  configured emergency phone. (B-18)
- **Localization rule.** All user-visible text produced by the feature — page text, labels, placeholders and other
  attributes, and Java-produced status/flash messages — is resolved through message keys present in every shipped locale
  bundle; new strings are authored in English with identical English placeholder text in the other locales; no English
  literal is emitted directly from application code. (B-19, B-20)

---

## Verification expectations

- **Test data isolation.** Automated tests run against an isolated in-memory H2 database; the same Flyway migrations run
  against it. The runtime H2 file is not committed and is never modified by tests. (RA-9, EA-2)
- **Test clock.** Tests pin "now" to Monday 2026-09-07 09:00 Europe/Amsterdam via an injectable `Clock`; the owner
  horizon is 2026-09-07..2026-10-07. (RA-48)
- **Deterministic AI.** Tests use the `stub`/deterministic `RequestInterpreter` with a synchronous executor; automated
  tests never call the live model. (RA-21, RA-22, RA-34)
- **End-to-end level.** At least one MockMvc test with the real filter chain drives the complete lifecycle over real
  HTTP endpoints, interleaving an owner session and a staff session with CSRF: request → consent → interpretation →
  suggestion → ask again → staff hand-off → staff suggestion → accept → completed visit, plus a declined-consent request
  booked by staff and closed as no-show. Exactly one random-port smoke test proves wiring. (RA-47)
- **Seed data.** A migration test asserts every seeded row of *Normative data* by value (not by count) and verifies
  every seeded password with the application's password encoder. (B-103)
- **Interpretation fidelity.** A test proves an interpretation persisted and read back is field-for-field identical to
  the one produced. (B-46)
- **Request lifecycle.** Tests prove every disallowed transition in the state model (including the `Interpreting`
  transitions) is refused by the system with no side effect. (B-101, B-102)
- **Security / negative paths.** For the entire URL space (stock pages included), negative tests assert both the denial
  status and the absence of disclosure or side effect, for anonymous users, owners on staff pages, and owners on other
  owners' data. (B-2, B-8, B-11)
- **Concurrency.** Tests prove two owners can never confirm the same vet-and-time and a pet can never end up with two
  active requests under simultaneous submissions. (B-23, B-24, B-58)
- **Localization check.** An automated check covers key existence across all templates and Java `MessageSource` usages,
  and the no-literal-text rule for templates and Java classes introduced or modified by this feature. (B-19, B-20,
  RA-49)
- **UI walkthrough.** Before a UI phase is accepted, a human signs in as an owner and as staff and confirms on the
  rendered pages: same layout/navigation/styles as stock pages, only that role's menu entries, signed-in user and
  logout visible, and that an owner cannot open another owner's pages.

---

## Behaviors to verify

Handoff to the criteria step. One observable behavior per entry, in document order.

### Authentication and access
- **B-1** The system requires an authenticated session for every URL except the login page and static resources; an
  anonymous request to any other URL is redirected to `/login`.
- **B-2** *(negative twin of B-1)* When an anonymous user requests a protected URL, the response body discloses no
  protected data and no database change occurs before the redirect.
- **B-3** The system authenticates form login against seeded accounts using the application's password encoder and
  establishes a session carrying the account's role.
- **B-4** The system rejects login with an unknown username or wrong password and creates no session.
- **B-5** After login the system lands an owner on *My appointments* and staff on the *Scheduling queue*; `/` redirects
  by role.
- **B-6** Every page shows the signed-in username and a *Logout* action; *Logout* ends the session.
- **B-7** The system restricts `/owners/**`, `/vets*` and `/staff/**` to staff; an owner requesting any such URL
  receives a 403 page.
- **B-8** *(negative twin of B-7)* When an owner requests a staff-only URL, the response discloses no staff data and no
  database change occurs.
- **B-9** The system serves owner pages under `/my/**` using the owner id from the session, never from the URL.
- **B-10** When an owner requests a pet/request/appointment id not belonging to them, the system responds 404,
  identical to a genuinely missing id.
- **B-11** *(negative twin of B-10)* When an owner attempts to act on another owner's pet/request/appointment, the
  system makes no database change and discloses nothing about that resource.
- **B-12** The service-layer ownership guard enforces owner scoping independently of the URL role matchers (double
  enforcement).

### Navigation and presentation
- **B-13** The navigation bar shows an owner only *My pets* and *My appointments*.
- **B-14** The navigation bar shows staff the stock pages plus *Scheduling queue*, *Calendar* and *Clinic settings*.
- **B-15** New pages render using the existing layout fragment, navigation bar, form controls and styles, with no
  second layout or stylesheet.
- **B-16** The *My pets* page shows the owner their own owner record and pets read-only, with no edit controls.
- **B-17** The scheduling request page shows owners veterinarian names and specialties; owners never see owner search,
  owner/pet edit, visit entry, or the veterinarians page.
- **B-18** Every owner scheduling page and the request detail page display the urgent-care banner with the configured
  emergency phone.

### Localization
- **B-19** Every user-visible string introduced by the feature (page text, labels, placeholders, other attributes,
  Java-produced status/flash messages) resolves through a message key present in every shipped locale bundle.
- **B-20** New strings are authored in English and seeded with identical English placeholder text in every non-English
  bundle; no English literal is emitted directly from application code.

### Request creation and single-active-request
- **B-21** The system lets an owner start a request by selecting one of their pets with no active request, entering
  reason and availability text, creating a request in *Awaiting consent*.
- **B-22** The pet selector offers only pets without an active request.
- **B-23** The system enforces at most one active request per pet, even under simultaneous submissions (nullable
  `active_pet_id` + unique index).
- **B-24** *(negative twin of B-23)* When a second active request is attempted for the same pet, the system refuses it
  and creates no second active request.

### Consent
- **B-25** The system sends the owner's text to the AI only after explicit consent; on consent the request moves to
  *Interpreting*.
- **B-26** When the owner declines consent, the request moves to *With staff* and no text is sent to the AI.

### Asynchronous interpretation
- **B-27** While in *Interpreting* the system runs exactly one in-flight interpretation per request on a dedicated
  single-thread `@Async` executor, passing the request id.
- **B-28** While in *Interpreting* the only owner action allowed is abandon; route-to-staff and edit-text are deferred
  until the result lands.
- **B-29** The request detail page auto-refreshes via `<meta http-equiv="refresh" content="3">` with no JavaScript and
  no JSON endpoint.
- **B-30** When an interpretation result arrives for an abandoned request, the system discards it.
- **B-31** On application startup, the system moves every request still in *Interpreting* to *Interpretation failed*
  with reason "interrupted".
- **B-32** The interpretation result is applied in a fresh transaction that re-checks the request state.

### Interpretation outcomes
- **B-33** A well-formed interpretation whose allowed-universe-minus-excluded is non-empty within the horizon moves the
  request to *Interpreted*.
- **B-34** A well-formed interpretation with contradictory windows or an explicit `cannotInterpret` flag counts as a
  failed attempt and moves the request to *Interpretation failed*.
- **B-35** Malformed model JSON is retried once; a second malformed result, a transport error, or a timeout (Ollama
  client timeout 60 s, configurable `scheduling.ai.timeout`) is treated as "model unavailable" and moves the request to
  *With staff* with the unavailability reason recorded.
- **B-36** A specialty returned as `OTHER:<text>` (no vet offers it) moves the request to *With staff* and is never
  downgraded to general care.
- **B-37** From the third failed interpretation attempt onward the system recommends routing to staff while still
  allowing rephrasing.

### Interpretation content
- **B-38** The system derives estimated visit length, care type and specialty, preferred/allowed/excluded windows, and an
  optional preferred veterinarian.
- **B-39** The model returns a preferred veterinarian id from the prompt list or `null`; the system validates the id
  exists and shows the resolved vet name in the read-only review; unknown/ambiguous names yield `null` (no fuzzy
  matching in Java).
- **B-40** The system clamps the estimated duration to the configured bounds (default 15–60), defaulting to 30.
- **B-41** The prompt includes current date/time and horizon end from an injectable `Clock`, weekday opening hours,
  part-of-day tokens, veterinarians (id, name, specialties), and the offered specialties (radiology, surgery,
  dentistry).

### Windows and tokens
- **B-42** A window is {weekday | specific date | date range} × {start–end time}; weekday windows recur across the whole
  horizon.
- **B-43** When the owner names any preferred or allowed windows, the system hard-excludes slots outside their union;
  with no windows the whole horizon is allowed.
- **B-44** The system resolves part-of-day tokens (MORNING/AFTERNOON/EVENING) per weekday against that day's opening
  hours; an empty resolved token is dropped.
- **B-45** The earliest bookable slot is the first 15-minute grid point at least 2 hours from the reference time.

### Interpretation storage and fidelity
- **B-46** The system persists the complete interpretation (all three window lists, care type, specialty, duration,
  preferred vet) plus the raw model response, model tag and prompt version; a read-back is field-for-field
  identical to what was produced.
- **B-47** The read-only review shows the interpretation verbatim; to change it the owner edits the text, which requires
  fresh consent and re-interpretation.
- **B-48** Interpretations are versioned per request with provenance `AI` or `STAFF`; a staff-authored/edited
  interpretation is stored as a new version and never overwrites the AI version.
- **B-49** The raw model response, model tag and prompt version are visible to staff only.

### Suggestions and holds
- **B-50** A suggestion may be produced only from a confirmed interpretation (*Interpreted*, or *Suggestion offered*
  when asking again); a suggestion attempt from any other state is refused with no side effect.
- **B-51** On confirmation the system enumerates feasible slots, ranks them with Timefold, offers the top slot, moves
  the request to *Suggestion offered*, and records the hold (held vet, start, duration) on the request row.
- **B-52** While a slot is held no other owner is offered it; calendar and overlap checks union appointment rows with
  requests whose hold fields are set.
- **B-53** A hold has no timer; it persists until the owner accepts, asks for another option, routes to staff, edits
  text, or abandons, a new suggestion supersedes it, or staff release it.
- **B-54** When no feasible slots remain, the system moves the request to *With staff* instead of showing a dead end.
- **B-55** Opening a request in *Suggestion offered* re-validates the hold; if invalid the system immediately produces
  the next suggestion, or moves to *With staff*, with an explanatory notice and never an error page.
- **B-56** On accept the system re-validates the hold inside a transaction that first takes `SELECT … FOR UPDATE` on the
  veterinarian row and re-checks overlaps; if valid the appointment is confirmed and the request moves to *Accepted*.
- **B-57** If the accepted slot has become unavailable, the system shows the next suggestion or the staff hand-off with
  an explanation, never an error page.
- **B-58** The system guarantees two owners can never confirm the same veterinarian-and-time (per-vet row lock + overlap
  re-check).

### Rejections
- **B-59** "Ask for another option" rejects the current suggestion, releases the hold, permanently excludes that exact
  vet-and-time for this request, and suggests the next best slot, with no cap.
- **B-60** On rejection the owner picks a scope chip: "not this time" excludes this vet at this start; "not this day"
  excludes this calendar date for all vets; "not this vet" excludes this vet for the whole horizon; all are hard
  exclusions for the current interpretation only.
- **B-61** Rejections belong to an interpretation; editing the text clears them from application while retaining them in
  the event log for audit.
- **B-62** The request page shows a compact "You have ruled out: …" list with no undo.
- **B-63** Each rejection is recorded as a `scheduling_request_event` carrying the excluded vet/time/scope.

### Solver
- **B-64** The solver models a single planning entity with one planning variable (the slot) over enumerated feasible
  slots, applying hard constraints both at enumeration and as Timefold hard constraints.
- **B-65** Hard constraints: within opening hours; within one vet's continuous working block after
  exceptions/leave/closures; no overlap with existing appointments or active holds; required specialty present; not
  inside the owner's excluded windows; within the preferred∪allowed union; within the booking horizon; and no overlap
  with the same owner's other confirmed appointments or active holds.
- **B-66** Soft ranking uses `HardMediumSoft`: preferred window (medium) > preferred vet (soft, high weight) > earliest
  date/time (soft per-minute tie-breaker).
- **B-67** Start times fall on a 15-minute grid; the solver runs synchronously inside the locked transaction with a 1 s
  termination budget.

### Staff queue
- **B-68** The *Needs staff* tab lists *With staff* requests oldest first, showing each hand-off trigger.
- **B-69** The *All open* tab lists every non-terminal request with state, held slot and age, plus a release-hold
  action.
- **B-70** Staff release-hold moves the request to *With staff* with a reason and releases the hold.

*(B-71 and B-72 retired: urgency is no longer part of the automated flow — see RA-17. Urgent care is handled solely by the always-visible urgent-care banner, B-18.)*

### Staff interpretation and suggestion placement
- **B-73** Staff can create or edit a structured interpretation (stored as a new `STAFF` version), required for
  declined-consent requests that have none.
- **B-74** Staff place a suggestion by clicking a free calendar cell in picking mode or by pressing *Suggest* to run the
  solver; both create a hold and move the request to *Suggestion offered* under the same rules.
- **B-75** A staff-placed suggestion the owner then accepts or rejects follows exactly the same rules as any suggestion.

### Staff booking and attach
- **B-76** Staff may book directly for any pet at any time; direct booking needs no interpretation.
- **B-77** When staff book for a pet with an open request in any non-terminal state, the form offers *attach* (→
  *Accepted*, hold released, event logged) or *leave open* (request untouched; appointment still appears under the
  owner's list).
- **B-78** A directly booked appointment appears under the owner's *My appointments* on next login and the owner may
  cancel it.

### Calendar
- **B-79** The staff calendar is a day view with one column per veterinarian and one row per 15-minute grid step, cells
  coloured closed/off-shift/free/booked/held, with prev/next-day navigation and a date picker, over a range covering
  the horizon.
- **B-80** Clicking a free cell starts a direct booking (form with vet/start prefilled, owner search → pet, duration,
  reason) or, in picking mode for a queued request, places the hold.
- **B-81** The calendar shows per vet per day: opening hours or closure, effective working blocks, booked appointments,
  held slots, and remaining free capacity.

### Availability configuration
- **B-82** Clinic settings (opening hours per weekday, visit-duration bounds and default, booking horizon, parts of
  day, emergency phone, time zone) are staff-editable in the UI with seeded defaults.
- **B-83** A veterinarian has a recurring weekly schedule supporting split shifts, plus date exceptions (a date with
  zero or more replacement blocks; zero = unavailable all day), leave (inclusive whole-day date range), and clinic-wide
  closures (single date).
- **B-84** Effective blocks for a day = closure ? none : leave ? none : exception ? its blocks : weekly blocks, all
  intersected with opening hours; precedence closure > leave > exception > weekly.
- **B-85** An availability edit (exception, leave, closure, opening hours) that conflicts with a confirmed appointment
  is refused with the list of conflicts; staff must reschedule/cancel first.
- **B-86** An availability edit that conflicts only with holds invalidates those holds.
- **B-87** The booking horizon and duration bounds constrain owners and the solver only; staff may book any future date
  and any grid-multiple duration that fits the vet's block; past days are viewable but not bookable.
- **B-88** Weekly blocks entered outside opening hours are accepted in the editor with a page warning and intersected at
  runtime.
- **B-89** The clinic operates in one configured time zone (default `Europe/Amsterdam`).

### Appointment lifecycle
- **B-90** Staff may reschedule a confirmed appointment before its start; the appointment is mutated in place and the
  old time recorded in the audit event.
- **B-91** Staff may cancel a confirmed appointment before its start (`CANCELLED_BY_STAFF`), recording a reason.
- **B-92** Staff may mark an appointment completed or no-show only once its scheduled start has passed.
- **B-93** Marking completed creates a `visits` row linked by `appointment_id`, with description prefilled from the
  owner's reason (or staff booking reason) and editable, and visit date = appointment date.
- **B-94** Every staff-initiated book/reschedule/cancel records a reason in the `appointment_change` audit table
  (appointment, actor, action, reason, timestamp), shown per appointment on the staff calendar.
- **B-95** Owners may cancel their own upcoming confirmed appointment any time before start (no minimum notice); the
  appointment becomes `CANCELLED_BY_OWNER`, is kept as a past record, and the request stays closed.
- **B-96** Past visits and no-shows cannot be cancelled; a cancel attempt on them is refused with no side effect.
- **B-97** *(negative twin of B-95)* An owner cannot cancel or view another owner's appointment (404, no change).
- **B-98** The owner sees a "changed by the clinic" marker with the staff reason and original time for
  staff-rescheduled/cancelled items under *My appointments*.
- **B-99** Cancelled appointments keep their row and appear under past items.

### Audit and refusal invariants
- **B-100** The system records request state transitions and staff/owner actions (from-state, to-state, actor, action,
  reason, payload, timestamp) in `scheduling_request_event`; staff see the timeline on the request detail page.
- **B-101** Every disallowed request-lifecycle transition (per the state model) is refused by the system itself with no
  side effect, not merely hidden by the UI.
- **B-102** Every disallowed appointment transition is refused with no side effect.

### Seed data
- **B-103** The Flyway migration seeds exactly the accounts, opening hours, veterinarian weekly schedules (17 blocks),
  exceptions, and configuration defaults listed under *Normative data* — no more, no fewer — and each seeded password is
  verifiable with the application's password encoder.

---

## Out of scope

- Self-registration, forced first-login password change, and password recovery.
- Exposing the full clinic calendar to owners.
- External notifications (email/SMS/push).
- Multiple clinics and waitlists.
- Non-H2 databases (MySQL/Postgres artefacts, `docker-compose.yml` and `k8s/` are deleted) and Gradle (files left
  untouched but unmaintained).
- Fuzzy matching of veterinarian names in Java (the model resolves the id or returns `null`).
- Any account, role, seed row or configuration default not listed under *Normative data*.

---

## External dependencies

- **ED-1 Local Ollama model availability.** The runtime default provider is `ollama` with model tag `gemma4:latest` (*as built: `ministral-3:14b`, Δ D-4*),
  which may be unavailable in CI or on a reviewer's machine. *Decision/default:* the selectable `stub` provider
  (`scheduling.ai.provider=stub`, RA-22) provides a deterministic rule-based interpreter so the full flow can be demoed
  and tested offline; automated tests always use the stub. *Resolution path:* install/point Ollama at the configured
  model for a live demo; no code change required to switch providers.
- **ED-2 Emergency phone number.** The real clinic phone number for the urgent-care banner is a business value not
  provided. *Decision/default:* seed `emergency_phone` with a placeholder number, staff-editable in clinic settings
  (RA-8). *Resolution path:* staff update it via the *Clinic settings* page.

## As-built convergence (cp-1)

Checkpoint cp-1 was independently verified on 2026-09-04 (REJECT), revised by commit 7a5be39, and re-verified on
2026-09-04 (REJECT again; see `convergence/cp-1.md`). Findings F-1..F-13 of the first verification are closed by
7a5be39 as verified in the report, except that F-7 continues as F-18. The REJECT was answered by four revision commits
(`8d35373`, `8b55b4a`, `6e32f14`, `df964f8`) and re-verified in task mode on 2026-09-04 as **APPROVED WITH NOTES**
(`convergence/cp-1.1.md`): F-14, F-15, F-16 and F-20 closed; F-17 and F-18 waived by the user (`status.md` Deviations);
F-19, F-21..F-25 remain open and are carried into the `tasks` re-plan as `Checkpoint 1 Remediation`.

### Δ accepted as built (cp-1)

- **Δ D-1 — RULE-11:** Timefold termination budget is configured as `timefold.solver.termination.spent-limit=1s`
  (the planned key `timefold.solver.solve.duration` does not exist in Timefold Solver 2.5). Same observable behavior.
- **Δ D-3 — RULE-2 / RULE-26:** the Timefold/Spring-AI import boundary is an exact class set
  (`FRAMEWORK_ADAPTER_BOUNDARY` in `ArchitectureBoundaryTests`): the ranker/interpreter adapters plus the Timefold
  planning entity, solution and constraint-provider support types; no substring exemptions.
- **Δ D-4 — ED-1 / rules Overview & ED-2 (post cp-1.1, user commit `9aa650c`):** the shipped Ollama model tag default is
  `ministral-3:14b` instead of `gemma4:latest`. The tag is configuration (`SPRING_AI_OLLAMA_CHAT_MODEL`), no AC asserts
  it; the test copy of `application.properties` is synchronized by plan task-2.1 (plan review BLOCKER-1).
- **Rules amendment (plan review BLOCKER-5):** `/403` (GET, authenticated any role) added to the rules' Security
  Surface matrix; closes the "Unlisted `/403`" spec weakness below.

### F-n open (not accepted as as-built behavior)

- **F-14 (cp-1/C-1) — CLOSED at cp-1.1 by `8d35373`:** `GET /my/requests/{id}` returned 500 (`LazyInitializationException` on `request.pet`); the owner read models now fetch every rendered association (`@EntityGraph` on `SchedulingRequestRepository.findByIdAndOwnerId`, `InterpretationRepository.findTopByRequestIdOrderByVersionDesc`); reproduced 200 at runtime.
- **F-15 (cp-1/C-2) — CLOSED at cp-1.1 by `8d35373`:** `GET /my/appointments` returned 500 once an appointment existed; `AppointmentRepository.findAllOwnedBy`/`findOwnedById` now `JOIN FETCH` pet and vet; reproduced 200 at runtime.
- **F-16 (cp-1/C-3) — CLOSED at cp-1.1 by `8b55b4a`:** a refused lifecycle transition on an owner action route surfaced as a 500; the owner action handlers now redirect to the request with the keyed notice `requestActionNotAllowed` (11 bundles); HTTP-level test `6e32f14`; reproduced 302 at runtime.
- **F-17 (cp-1/C-4) — WAIVED (user, 2026-09-04, `status.md` Deviations):** the two stock random-port classes predate the feature; AC-139 is read as exactly one *feature* random-port smoke test (`SmokeTests`).
- **F-18 (cp-1/G-1, ex F-7) — WAIVED for cp-1 (user, 2026-09-04, `status.md` Deviations):** phase-1 delivers the thin owner-only UC-1 1–8 (now rendered without a test transaction, `6e32f14`); the interleaved owner/staff scenario of AC-138 is a single claim of the phase that completes it; the re-plan removes AC-138 from phase-1 `covers`.
- **F-19 (cp-1/G-2):** AC-21 pet-selector exclusion is not asserted on the rendered form.
- **F-20 (cp-1/G-3) — CLOSED at cp-1.1 by `df964f8`:** AC-137 — `src/test/resources/application.properties` pins `scheduling.ai.provider=stub`; `SchedulingProviderPinningTests` asserts the stub bean, the absence of any Ollama interpreter/`ChatClient` bean in the test context, and that the shipped default stays `${SCHEDULING_AI_PROVIDER:ollama}`.
- **F-21 (cp-1/G-4):** AC-118 has no owner appointment view/cancel route in phase-1; only service-level evidence exists.
- **F-22 (cp-1/G-5):** 29 of 43 phase-1 ACs are not cited by any test; two citations are mis-scoped.
- **F-23 (cp-1/G-6):** AC-58 — Timefold ranking and "top slot offered" are not asserted.
- **F-24 (cp-1/G-7):** AC-7/16/17/18/19/140 rendered-page evidence covers two pages only; session invalidation, banner on every owner page, read-only My pets, layout reuse and Java literals are unproven.
- **F-25 (cp-1/G-8):** no HTTP-level legal-URL/illegal-state refusal test for the owner action routes — *narrowed at cp-1.1:* `SchedulingLifecycleE2eTests` now covers one route/state (decline in ACCEPTED, `6e32f14`); the remaining routes/states stay open.
- **Decision closed (cp-1/D-2, P-5) at cp-1.1:** RULE-17 default provider `ollama` restored by `df964f8` (user decision, `status.md` Deviations); not a Δ — the spec text stands as written. New cosmetic K-4 (cp-1.1): the owner action handlers catch the broad `IllegalStateException`; carried into the re-plan with K-1..K-3.

### Spec weaknesses exposed by cp-1

- **Unlisted `/403` and stale `/oups` (upstream: rules):** the Security Surface omits the explicit `/403` controller used
  by the access-denied forward and lists `/oups` as pre-existing although its stock controller was removed; the
  matcher now guards a route that does not exist.
- **RULE-17 default vs runnable checkout (upstream: rules):** the default `ollama` cannot run on a fresh checkout
  without a local model (`tasks.yaml` assumptions admit this); the rule should either choose a runnable default or
  prescribe a test profile that pins `stub`.
- **AC-118 flow placement (upstream: criteria/spec):** AC-118 (owner cannot view/cancel another owner's appointment)
  is unverifiable until the owner appointment view/cancel route of UC-6 exists; tie it to that use case's phase.
- **AC-139 vs stock tests (upstream: criteria/rules):** "exactly one random-port smoke test" conflicts with the stock
  `PetClinicIntegrationTests`/`PetClinicConcurrencyTests`, which the spec never told the executor to remove or fold.
- **No rendering/persistence-context rule (upstream: rules):** with `open-in-view=false` (RULE-4) and lazy
  associations, nothing forbids lazy access from templates; a rule requiring fetch-joined read models (or DTOs) for
  every view would have caught F-14/F-15 at plan review.
- **AC-138 phase placement (upstream: tasks/criteria):** phase-1 claims AC-138 while task-1.7 delivers only the thin
  UC-1; the mandatory interleaved scenario belongs to the phase that completes it.
- **Missing web-controller ownership (upstream: tasks/review):** phase-1's artifact map assigns no controller artifact
  for its required owner/staff HTTP skeleton.
