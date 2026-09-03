# Smart Appointment Scheduling — Scope Decisions

Record of the grilling session held against `spec/proposal.md` on 2026-09-03. Four rounds, 49 questions. Each entry
lists the question as asked, the options that were on the table, and the resolution. Where the resolution differs from
the recommended option this is called out explicitly. `spec/proposal.md` was **not** modified during the session; the
section *Proposal sections affected* at the end lists what must be folded back into it.

Conventions: **Q-n** — question id in the order asked · *Resolution* — the binding decision · *Options* — alternatives
considered and rejected.

---

## A. Data model and persistence

### Q1 — Appointment vs. stock Visit
**Question.** Stock PetClinic already has `visits` (pet, date, description) and a staff "add visit" page. §9 says
completing an appointment "records a visit in the pet's history". What is the relationship between the new appointment
and the existing visit?
**Options.** Separate entity linked to visit · Extend the `visits` table with vet/time/status · Separate with no link.
**Resolution.** *Separate + link.* New `appointment` table (pet, vet, start, duration, status, reason) separate from
`visits`. On *completed*, a `visits` row is created carrying a nullable `appointment_id` back-reference. The stock
add-visit page stays as-is for walk-ins (staff only).

### Q21 — Interpretation storage
**Question.** §12 demands a read-back fidelity test. Normalized tables or a single JSON column?
**Options.** Normalized tables + raw CLOB · Single JSON CLOB column.
**Resolution.** *Normalized + raw CLOB.* `interpretation` + `interpretation_window` rows (kind
`PREFERRED`/`ALLOWED`/`EXCLUDED`), plus the raw model response as a CLOB (see Q16). Interpretations are versioned per
request with provenance `AI` / `STAFF` (see Q8).

### Q22 — Where a hold lives
**Question.** Exactly one hold exists per request in *Suggestion offered*. Fields on the request row, a separate
`slot_hold` table, or an `appointment` row with status `HELD`?
**Options.** On the request row · Appointment status `HELD` · Separate hold table.
**Resolution.** *On the request row* (held vet, start, duration). "One hold per request" is then structural. Calendar
and overlap checks union `appointment` rows with requests whose hold fields are set.

### Q11 — Cancellation and audit
**Question.** When an owner cancels an accepted appointment, does the request stay closed or reopen? Is a cancelled
appointment kept as a visible record? Where do mandatory staff reasons (book/reschedule/cancel) live?
**Options.** Keep record + audit table · Only the latest reason as a column · Reopen the request.
**Resolution.** *Keep record + audit table.* Cancellation keeps the request closed. The appointment stays with status
`CANCELLED_BY_OWNER` / `CANCELLED_BY_STAFF` and is shown under past items. Staff reasons go into an
`appointment_change` audit table (appointment, actor, action, reason, timestamp), shown per appointment on the staff
calendar.

### Q24 — Request-level event log
**Question.** Requests also have staff actions with reasons (release hold, clear emergency flag, staff-authored
interpretation) and owner actions (rejections with chips, edits, abandon). Keep a request-level event log?
**Options.** Full request event log · Rejections table only.
**Resolution.** *Request event log.* One `scheduling_request_event` table (request, from-state, to-state, actor, action,
reason, payload, timestamp). Rejections are events carrying the excluded vet/time/scope, which also gives Q7's "kept for
history but not applied". Staff see the timeline on the request detail page.

### Q13 — Concurrency mechanism
**Question.** How is "two owners never confirm the same vet-and-time" actually guaranteed? Overlapping intervals of
differing durations cannot be expressed as a unique constraint, and H2 has no partial unique indexes.
**Options.** Per-vet row lock + nullable unique · `SERIALIZABLE` transactions with retry · JVM-level striped lock.
**Resolution.** *Per-vet row lock + nullable unique.* (1) Every hold/confirm/book operation runs in a transaction that
first takes `SELECT … FOR UPDATE` on the veterinarian row (serialising per vet), then re-checks overlaps against
appointments and holds. (2) "One active request per pet" via a nullable `active_pet_id` column set only while the
request is active, plus a unique index (H2 treats NULLs as distinct).

### Q16 — AI provenance
**Question.** Should the raw model response, model tag and prompt version also be stored on the request for
debugging/fidelity evidence?
**Options.** Store raw + model tag · Structured only.
**Resolution.** *Store raw + model tag.* Raw response text, model tag and a prompt version string are stored alongside
the structured interpretation; visible to staff only.

### Q29 — Emergency guidance text
**Question.** What does the always-visible urgent-care guidance say, and is any part of it configurable?
**Options.** Banner + configurable phone · Static text only.
**Resolution.** *Banner + configurable phone.* Fixed banner via message keys ("If your pet is in immediate danger, do
not wait for a suggestion — call {0} now"); `emergency_phone` added to clinic settings, seeded with a placeholder
number; shown on every owner scheduling page and the request detail page.

### Q18 — Repository facts and non-H2 artefacts
**Question.** `pom.xml` is on Spring Boot 4.1.0 with `java.version` 17 (spec requires 21); no Security, Flyway,
Timefold or Spring AI yet; schema comes from `schema.sql`/`data.sql` under `db/h2` plus MySQL/Postgres property files
and SQL folders; 11 message bundles exist; stock specialties are radiology, surgery, dentistry. What do we do with the
MySQL/Postgres artefacts, `docker-compose.yml` and `k8s/`?
**Options.** Delete non-H2 artefacts · Leave untouched (silently broken) · Keep and write migrations for them.
**Resolution.** *Delete non-H2 artefacts.* Bump to Java 21; move the stock schema/data into Flyway `V1__` (H2 only,
`spring.sql.init` disabled); delete the MySQL/Postgres property files and SQL folders, `docker-compose.yml` and `k8s/`;
README notes H2-only. Gradle files are left untouched but unmaintained.

---

## B. Interpretation (§5)

### Q2 — Owner input shape
**Question.** §5 says the owner describes "the reason for the visit and when they are (and are not) available in free
text". One textarea or two fields?
**Options.** Two fields · Single textarea.
**Resolution.** *Two fields* — *reason* and *availability* — which together form "the text". Editing either requires
fresh consent and re-interpretation; both are re-sent to the AI on any edit.

### Q3 — Window model and semantics
**Question.** "Tuesday or Thursday after lunch" relative to a 30-day horizon — every Tuesday or the next one? Absolute
dates allowed? §7 lists only *excluded* windows as hard; if an owner names preferred/allowed windows, is a slot outside
all of them still legal?
**Options.** Recurring + union is hard · Only excluded is hard (literal spec) · Next occurrence only.
**Resolution.** *Recurring + union is hard.* A window = {weekday | specific date | date range} × {start–end time};
weekday windows recur across the whole horizon. If the owner gives **any** preferred/allowed windows, slots outside
their union are **hard-excluded** ("allowed" defines the universe, "preferred" ranks within it). No windows at all →
whole horizon is allowed. **This changes §7 of the proposal.**

### Q4 — Vague phrases and time reference
**Question.** "after lunch", "mornings", "next week", "tomorrow": should the LLM emit concrete HH:mm or named tokens
resolved by the app? What is the earliest bookable slot? Tests need a fixed clock.
**Options.** Tokens + 2 h lead · Tokens + from tomorrow · LLM emits concrete times.
**Resolution.** *Tokens + 2 h lead.* The LLM emits HH:mm when explicit, otherwise a part-of-day token; the app resolves
tokens against clinic settings. Reference date/time is injected into the prompt from an injectable `Clock`. Earliest
bookable slot = first grid point ≥ 2 hours from now.

### Q19 — Parts-of-day vocabulary
**Question.** Reconcile Q4's "afternoon = 12:00–closing" with §10's three parts (morning = opening–12:00, afternoon =
12:00–17:00, evening = 17:00–closing). How do "after lunch", "late afternoon", "end of day" map?
**Options.** Three tokens, after-lunch = AFTERNOON+EVENING · Two parts only (drop evening).
**Resolution.** *Three tokens.* Token vocabulary `MORNING`, `AFTERNOON`, `EVENING` as configured in §10; a window may
carry several tokens. Prompt rules: "after lunch" → AFTERNOON+EVENING; "end of day"/"late" → EVENING; "before N"/"after
N" → explicit HH:mm. Tokens are resolved per weekday against that day's opening hours (Friday MORNING = 10:00–12:00;
Monday EVENING = empty and is dropped).

### Q20 — Prompt inputs and output shape
**Question.** What context does the LLM get, and in which shape does it answer?
**Options.** Closed vocabulary + JSON record · Free-form specialty string · Tool calling.
**Resolution.** *Closed vocabulary + JSON record.* System prompt carries: current date/time and horizon end, weekday
opening hours, part-of-day tokens, veterinarians (id, name, specialties), offered specialties (stock: radiology,
surgery, dentistry). The model answers strictly as JSON matching a Java record (Spring AI structured output /
`BeanOutputConverter`): `reasonSummary`, `estimatedMinutes`, `careType` (`GENERAL`|`SPECIALTY`), `specialty` (closed
list or `OTHER:<text>`), `preferredVetId`, `urgent`, `cannotInterpret`, and three window lists. `OTHER` specialty →
unmatched → *With staff*.

### Q43 — Preferred veterinarian resolution
**Question.** "Dr. Carter", "Helen", "the surgeon I saw last time" — who resolves the name?
**Options.** Model returns vet id · Model returns name, app matches in Java.
**Resolution.** *Model returns vet id* (or `null`) chosen from the list in the prompt; unknown/ambiguous names → `null`
(no preference); the app validates that the id exists. The read-only interpretation shows the resolved vet name so the
owner can catch a wrong match. No fuzzy matching in Java. A preferred vet lacking the required specialty is simply
removed by the hard specialty constraint.

### Q15 — Definition of "cannot produce usable availability"
**Question.** Is "anytime is fine" usable? Is a contradiction (excluded covers all allowed) a failure? Is malformed
model output a failed attempt or "model unavailable"?
**Options.** Non-empty universe rule · Model decides · Strict (no windows = failure).
**Resolution.** *Non-empty universe rule.* Usable = well-formed interpretation whose allowed universe (Q3) minus
excluded windows is non-empty within the horizon — "anytime" is usable. Contradictory windows and an explicit
`cannotInterpret` flag count as a failed attempt (owner may rephrase). Malformed JSON after one retry, transport errors
and timeouts = "model unavailable" → *With staff*.

### Q14 — Emergency detection
**Question.** "Any sign of urgency" is AI-derived, but an owner who declines consent can't be flagged. Owner checkbox
too? May staff downgrade a false positive?
**Options.** AI or checkbox, staff clears · AI only · Checkbox only.
**Resolution.** *AI or checkbox, staff clears.* Either the AI `urgent` flag **or** an owner "this is urgent" checkbox
pins the request to the top of the queue. Staff can clear the flag and either book directly or place a suggestion; the
request stays *With staff* and never returns to the automated loop.

### Q36 — Interpretation latency
**Question.** The interpretation call may take 10–60 s on a local `gemma4` model. Synchronous form POST or async job
with a waiting page? What timeout counts as "model unavailable"?
**Options.** Synchronous, 60 s (recommended) · Async with polling page.
**Resolution.** *Async with polling page* — **deviates from the recommendation.** Adds an `Interpreting` state to the
lifecycle (Q46) and a waiting page (Q45). Ollama client timeout 60 s (configurable `scheduling.ai.timeout`), one retry
on malformed output, no retry on timeout; timeout → *With staff* with the unavailability reason recorded.

### Q45 — Polling mechanism
**Question.** Stock templates carry no custom JavaScript. Meta-refresh page or JS `fetch` loop against a JSON status
endpoint?
**Options.** Meta-refresh page · JS polling + JSON endpoint.
**Resolution.** *Meta-refresh page.* `<meta http-equiv="refresh" content="3">`, no JS, no JSON endpoint. The request
detail URL is the single entry point and renders whatever state the request is in.

### Q46 — The `Interpreting` state
**Question.** New lifecycle row `Interpreting` (consent given, model call in flight). What may the owner do while it
runs?
**Options.** Abandon only · Abandon or route to staff.
**Resolution.** *Abandon only.* A late result for an abandoned request is discarded. *Route to staff* and *edit text*
wait until the result lands. Exactly one in-flight interpretation per request. §6's table and §12's transition tests
gain this state.

### Q47 — Executor and crash recovery
**Question.** How is the background call run, and what happens to a request stuck in `Interpreting` after an
application restart?
**Options.** `@Async` + fail-on-restart · `@Async` + re-run on restart · Persistent job table.
**Resolution.** *`@Async` + fail-on-restart.* Spring `@Async` on a dedicated single-thread executor (a local Ollama
serialises anyway); request id is passed; the result is applied in a fresh transaction that re-checks the state. On
startup every request still in `Interpreting` is moved to *Interpretation failed* with reason "interrupted" so the
owner can consent again. Tests use a synchronous executor.

### Q37 — Demo without Ollama
**Question.** Tests use the deterministic double, but the human UI walkthrough (§12) and CI have no model either. Should
the double be selectable at runtime?
**Options.** Selectable stub provider · Test-only double.
**Resolution.** *Selectable stub provider.* Property `scheduling.ai.provider=ollama|stub` (default `ollama`). `stub`
wires a rule-based interpreter (keyword matching for weekdays, parts of day, "not", specialties, "emergency") so the
full flow can be demoed offline. Tests use this stub plus hand-built fixtures.

---

## C. Guided flow, holds and rejections (§6)

### Q5 — Stale holds
**Question.** A hold has no timer; an owner who closes the browser in *Suggestion offered* holds that vet-and-time
forever. Who can free it?
**Options.** Staff can release · Accept the risk · Add an expiry.
**Resolution.** *No timer stays. Staff can release the hold, and the owner can also cancel* (free-text answer). Held
slots are visible on the staff calendar/queue; *release hold* moves the request to *With staff* with a reason. The
owner returning to the request sees the same suggestion and may abandon it. No background job.

### Q6 — Rejection granularity
**Question.** "Ask for another option" excludes only that exact vet-and-time; on a 15-minute grid the next suggestion
is very likely the same vet 15 minutes later. Is exact-slot exclusion really the intent?
**Options.** Exact + reason chips · Exact only · Exclude whole day.
**Resolution.** *Exact + reason chips.* Exact exclusion stays the default; the owner picks a quick scope when rejecting
— *not this time*, *not this day*, *not this vet* — stored as an additional exclusion applied on re-ranking. Still one
suggestion at a time, no calendar exposure.

### Q49 — Rejection scope semantics
**Question.** Exact meaning of the chips for re-ranking.
**Options.** As stated · "Not this day" = this vet only.
**Resolution.** *As stated:* *not this time* = this vet at this start; *not this day* = this calendar date for **all**
vets; *not this vet* = this vet for the whole horizon. All three are hard exclusions for the current interpretation
only (Q7).

### Q7 — Rejections after a text edit
**Question.** §6 says re-ranking uses "all earlier rejections". If the owner edits the text (new consent, new
interpretation), do old rejections still apply?
**Options.** Clear on edit · Keep forever.
**Resolution.** *Clear on edit.* Rejections belong to an interpretation; editing the text clears them. They stay in the
event log for audit but are not applied.

### Q34 — Rejection visibility
**Question.** Do owners see what they have ruled out? Allow undo?
**Options.** Show, no undo · Show with undo · Hide.
**Resolution.** *Show, no undo.* A compact "You have ruled out: …" list on the request page. Editing the text resets
everything (Q7); no additional transitions.

### Q9 — Staff actions vs. holds and confirmed appointments
**Question.** An accepted slot can only "become unavailable in the meantime" through a staff action (booking over a
hold, adding a vet exception/leave/closure, editing opening hours). What are the rules?
**Options.** Override holds, protect bookings · Holds are sacred · Allow edits, flag conflicts.
**Resolution.** *Override holds, protect bookings.* Staff **may** book over a hold (calendar shows it as held and asks
for confirmation; the owner's request falls back to the next suggestion on their next action). Availability edits
(exception, leave, closure, opening hours) that conflict with **confirmed** appointments are **refused** with the list
of conflicts — staff must reschedule/cancel first. Conflicting holds are simply invalidated.

### Q30 — Invalid hold detection
**Question.** When does the owner learn their hold was overridden — only on *accept*, or proactively on viewing?
**Options.** Re-validate on view and accept · Only on accept.
**Resolution.** *Re-validate on view and accept.* Opening a request in *Suggestion offered* re-validates the hold; if
invalid, the system immediately produces the next suggestion (or the staff hand-off) with an explanatory notice. Accept
re-validates again inside the locked transaction (Q13).

### Q23 — Owner double-booking
**Question.** An owner with two pets may have two active requests. Are the owner's own other appointments and holds a
hard constraint?
**Options.** Hard for solver, staff may override · Not a constraint · Prefer adjacent slots.
**Resolution.** *Hard for solver, staff may override.* No overlap with any confirmed appointment or active hold
belonging to the same owner (any pet). Staff can override with a confirmation, like holds in Q9.

### Q42 — Direct booking attaches to an open request
**Question.** When staff attach a direct booking to a pet's open request the request becomes *Accepted*. May staff
book for a pet whose request is mid-flow (*Awaiting consent* / *Interpreted*), or only for *With staff* requests?
**Options.** Attach from any state · Attach only from *With staff*.
**Resolution.** *Attach from any state.* Staff may book for any pet at any time. If an open request exists in **any**
non-terminal state, the form offers *attach* (→ *Accepted*, hold released, event logged) or *leave open* (request
untouched; the appointment appears under the owner's list regardless). The lifecycle table gains "staff booked"
transitions from every open state.

---

## D. Matching (§7)

### Q10 — Solver model and weights
**Question.** With a single request the natural Timefold model is one planning entity with one planning variable (the
slot) over enumerated feasible slots — "best of N". (a) Single-entity or multi-request solve? (b) Priority order of the
soft constraints?
**Options.** Single entity, window > vet > earliest · Single entity, weighted sum · Multi-request solve.
**Resolution.** *Single entity, window > vet > earliest.* Hard constraints applied when enumerating candidates **and**
re-asserted as Timefold hard constraints. `HardMediumSoft`: preferred window (medium) > preferred vet (soft, high
weight) > earliest date/time (soft, per-minute penalty as tie-breaker). Termination: 1 s unimproved or exhausted.

### Q48 — Solver synchronous
**Question.** Does the solver call stay synchronous, now that interpretation is async?
**Options.** Synchronous · Async like the LLM (extra `Ranking` state).
**Resolution.** *Synchronous*, inside the same transaction that takes the per-vet locks (Q13) for the chosen slot. Only
the LLM call is async.

### Q44 — Stack wiring
**Question.** Spring AI 2.0.1 and Timefold 2.5.0 both target Spring Boot 4.x, Jackson 3 (`tools.jackson`) and require
Java 21. Timefold Spring Boot starter (auto-config) or core library wired manually?
**Options.** Both starters, behind interfaces · Timefold core with manual `SolverFactory`.
**Resolution.** *Both starters, behind interfaces.* `timefold-solver-spring-boot-starter` with
`timefold.solver.solve.duration=1s` (and unimproved-spent-limit where supported) and `spring-ai-starter-model-ollama`
from the Spring AI BOM; both hidden behind the app's own `SlotRanker` and `RequestInterpreter` interfaces so tests
never touch the libraries.

---

## E. Staff surfaces (§8–§10)

### Q8 — Staff complete the interpretation
**Question.** Is there a structured form for staff to edit/create the interpretation (e.g. for declined-consent
requests that have none)? Is "place a suggestion" a manual calendar pick, running the solver, or both?
**Options.** Form + both paths · Form + manual only · No staff edit.
**Resolution.** *Form + both paths.* Staff get a structured interpretation form; a staff-authored/edited interpretation
is stored as a new version with provenance (`AI` vs `STAFF`); the AI version is never overwritten. "Place a
suggestion" = click a free cell on the calendar **or** press *Suggest* to run the solver — both create a hold and put
the request into *Suggestion offered*.

### Q25 — Queue scope and order
**Question.** Only *With staff* requests, or all open requests so staff can oversee stuck owner-flow requests?
**Options.** Two tabs · Needs staff only.
**Resolution.** *Two tabs.* Default **Needs staff** = *With staff* requests, emergencies pinned first, then oldest
first, hand-off trigger shown (declined consent, unmatched specialty, exhausted…). **All open** lists every
non-terminal request with state, held slot and age, with the *release hold* action.

### Q12 — Calendar layout
**Question.** §9 requires opening hours, effective working blocks, appointments, holds and free capacity per vet per
day over the horizon. Which layout?
**Options.** Day view with vet columns · Week view per vet · List with capacity.
**Resolution.** *Day view, vet columns.* One column per veterinarian, one row per 15-minute grid step, cells coloured
closed / off-shift / free / booked / held, prev/next-day navigation and a date picker. Clicking a free cell starts a
direct booking (or places a suggestion for a queued request).

### Q26 — Booking and suggestion flows on the calendar
**Question.** From the queue, *Place suggestion* needs a slot; from the calendar, a free cell needs a pet. Does *book
directly* require an interpretation to exist?
**Options.** Picking mode + attach · Forms only (calendar view-only).
**Resolution.** *Picking mode + attach.* (a) Queue → *Place suggestion* opens the calendar in **picking mode** for that
request (banner with pet/duration/specialty, incompatible cells greyed); clicking a cell places the hold. Alternatively
*Suggest* runs the solver. (b) Calendar free cell → booking form with vet/start prefilled, owner search → pet,
duration, reason; if that pet has an open request, staff choose *attach* or *leave open* (Q42). (c) Direct booking
needs no interpretation.

### Q27 — Exceptions and leave semantics
**Question.** Seed data has only *unavailable* exceptions. Should an exception also express altered hours for a date?
Is leave a date range?
**Options.** Exception may alter hours · Unavailable only.
**Resolution.** *Exception may alter hours.* Exception = single date with zero or more replacement blocks (zero =
unavailable all day); leave = inclusive date range, whole days; clinic closure = single date. Effective blocks for a
day = closure ? none : leave ? none : exception ? its blocks : weekly blocks — all intersected with opening hours.
Precedence closure > leave > exception > weekly.

### Q40 — Horizon and staff
**Question.** Does the booking horizon bind staff bookings and calendar navigation?
**Options.** Horizon for owners only · Horizon for everyone.
**Resolution.** *Horizon for owners only.* The horizon constrains owners and the solver. Staff may book any future date;
the calendar navigates freely forward; past days are viewable, not bookable.

### Q41 — Duration bounds and staff
**Question.** Do the visit-duration bounds (15–60) bind staff? Surgery may need 90 minutes.
**Options.** Bounds for owner flow only · Bounds for everyone.
**Resolution.** *Bounds for owner flow only.* Bounds clamp the AI estimate and the owner flow. Staff may enter any
duration that is a multiple of the 15-minute grid and fits the vet's block; the staff form defaults to the configured
default duration.

### Q38 — Owner sees staff changes
**Question.** When staff reschedule or cancel with a mandatory reason, what does the owner see under *My appointments*?
**Options.** Show reason · Status only.
**Resolution.** *Show reason.* A "changed by the clinic" marker with the staff reason text and the original time for
rescheduled/cancelled items. The reason field is owner-facing by design; staff know it is visible.

### Q17 — Completion timing
**Question.** When may staff mark *completed* / *no-show*?
**Options.** After start · After end · Any time.
**Resolution.** *After start.* Only once the scheduled start time has passed; before that, the only staff actions are
reschedule and cancel.

### Q33 — Visit created on completion
**Question.** What goes into the visit `description` created on completion (Q1)?
**Options.** Prefilled, editable · Automatic, not editable.
**Resolution.** *Prefilled, editable.* Completion form prefilled with the owner's reason text (or the staff booking
reason), editable before saving; visit date = appointment date; `appointment_id` link set.

---

## F. Security, URLs and verification (§3, §12)

### Q35 — URL space
**Question.** Stock URLs are `/owners/**`, `/vets`, `/vets.html`, `/oups`, `/`. Should owner pages live under a
distinct prefix, or reuse `/owners/{id}` with an ownership check?
**Options.** `/my/**` for owners · Reuse `/owners/{id}`.
**Resolution.** *`/my/**` for owners.* Owner pages: `/my/pets`, `/my/appointments`, `/my/requests/{id}/…`. Everything
under `/owners/**`, `/vets*`, `/staff/**` (queue, calendar, settings, vet availability) is staff-only by URL matcher.
Owner pages never take an owner id in the URL — the id comes from the session, so cross-owner access can only happen
via pet/request/appointment ids, which the service guard checks (Q28).

### Q28 — Denial semantics
**Question.** What does "denied and learns nothing" look like on the wire for an owner requesting another owner's
resource, an owner on a staff-only URL, and an anonymous user?
**Options.** 404 / 403 / redirect, double-enforced · Always 403.
**Resolution.** *404 / 403 / redirect, double-enforced.* Other-owner data → **404** (same page as a genuinely missing
id); owner on a staff-only URL → **403** page; anonymous → redirect to `/login`. Enforced twice: URL role matchers in
the `SecurityFilterChain` and an ownership guard inside services. Security tests assert status **and** no secret in
the body **and** no DB change.

### Q31 — End-to-end transport
**Question.** "Drives the complete lifecycle through the real HTTP endpoints": MockMvc against the full context with
Spring Security, or a real embedded server with an HTTP client?
**Options.** MockMvc + one real-server smoke · Real server throughout.
**Resolution.** *MockMvc + one real-server smoke.* MockMvc with the real filter chain and the deterministic AI double,
two sessions (owner + staff) interleaved through the whole flow, CSRF included. Exactly one smoke test on a random-port
server (login page + one authenticated page) proves wiring.

### Q32 — Test clock
**Question.** Seed exceptions are dated 2026-09-15/17/21 and 2026-10-22. Tests need a pinned "now".
**Options.** 2026-09-07 09:00 · Pick during implementation.
**Resolution.** *Monday 2026-09-07 09:00 Europe/Amsterdam.* Horizon = 2026-09-07..2026-10-07, covering the September
exceptions and excluding 2026-10-22 (which then tests the horizon boundary). Runtime uses the system clock.

### Q39 — Localization check scope
**Question.** The repo ships 11 message bundles. Apply the automated check (key existence in every bundle; no literal
English text) to new/modified templates only or to all templates including untouched stock ones?
**Options.** Keys: all, literals: new only · Everything strict.
**Resolution.** *Keys: all, literals: new only.* Key-existence check for **all** templates and all Java
`MessageSource` usages; the no-literal-text check for templates and Java classes **introduced or modified by this
feature** (listed explicitly in the test). Stock templates are not retro-fitted.

---

## G. Facts verified during the session

- `pom.xml`: Spring Boot 4.1.0, `java.version` 17; no Spring Security, Flyway, Timefold or Spring AI dependencies.
- Schema and data come from `src/main/resources/db/h2/schema.sql` and `data.sql`; MySQL/Postgres property files and
  `db/mysql`, `db/postgres` folders exist; `docker-compose.yml` and `k8s/` exist at the root.
- 11 message bundles (`messages*.properties`, incl. `de, en, es, fa, hi, ja, ko, pt, ru, tr`).
- Stock specialties: radiology, surgery, dentistry. Six veterinarians.
- Spring AI 2.0.1 targets Spring Boot 4.0/4.1 and Jackson 3 (`tools.jackson`).
- Timefold 2.5.0 requires Java 21, targets Spring Boot 4 and Jackson 3; the starter auto-configures
  `SolverManager`/`SolverFactory` and reads `timefold.solver.solve.duration`.

## H. Assumptions made explicit (accepted unless challenged)

- Request creation is two steps: form (pet — only pets without an active request —, reason, availability, urgent
  checkbox) → *Awaiting consent* → consent page.
- Flyway layout: `V1__stock_schema`, `V2__stock_data`, `V3__scheduling_schema`, `V4__scheduling_seed`;
  `spring.sql.init` disabled; the same migrations run against the in-memory test H2.
- Accounts: `users` (username, bcrypt password, role, nullable `owner_id` FK); `/` redirects by role; the login page
  uses the stock layout.
- A preferred vet who lacks the required specialty is filtered by the hard constraint; no owner-facing warning.
- Weekly blocks outside opening hours are accepted in the editor but intersected at runtime (with a warning on the
  page), not rejected.
- Rescheduling mutates the appointment in place (the audit event holds the old time); cancelled appointments keep
  their row and appear under past items.
- README is rewritten for H2-only / Java 21 / Ollama setup.

## I. Proposal sections affected

Decisions that change or sharpen `spec/proposal.md` and must be folded back in:

| Section | Change |
|---|---|
| §3 / §4 | Owner URL space `/my/**`; stock and `/staff/**` URLs staff-only (Q35). Denial semantics 404/403/redirect (Q28). |
| §5 | Two input fields (Q2); window model with preferred∪allowed as hard universe (Q3); tokens and 2 h lead (Q4, Q19); closed-vocabulary JSON output (Q20, Q43); "usable" definition (Q15); emergency = AI or checkbox (Q14); async interpretation with `Interpreting` state (Q36, Q45–Q47); stub provider (Q37); provenance columns (Q16). |
| §6 | New `Interpreting` state; rejection scope chips and their semantics (Q6, Q49); rejections cleared on edit (Q7); staff release hold (Q5); hold re-validation on view (Q30); staff booking over holds and attach-from-any-state (Q9, Q42); ruled-out list shown (Q34). |
| §7 | Owner self-overlap hard constraint (Q23); preferred/allowed union as hard constraint (Q3); `HardMediumSoft` priority order and single-entity model (Q10, Q48). |
| §8 | Staff interpretation form with versioning/provenance; suggest via calendar pick or solver (Q8); staff may clear the emergency flag (Q14); queue tabs (Q25). |
| §9 | Day-view calendar, picking mode, booking form, attach (Q12, Q26); availability edits refused on conflicts (Q9); horizon and duration bounds for owner flow only (Q40, Q41); completion after start (Q17); visit prefilled/editable (Q33); owner sees staff reasons (Q38); cancellation record + audit tables (Q11, Q24). |
| §10 | `emergency_phone` setting (Q29); exception may alter hours, precedence rule (Q27). |
| §12 | Test clock 2026-09-07 09:00 (Q32); MockMvc + smoke (Q31); localization check scope (Q39); `Interpreting` transitions in lifecycle tests (Q46); concurrency mechanism to target (Q13). |
| §14 | Async `@Async` executor and restart recovery (Q47); `scheduling.ai.provider`, `scheduling.ai.timeout` (Q36, Q37); starters behind `SlotRanker` / `RequestInterpreter` (Q44); Flyway layout, Java 21 bump, removal of non-H2 artefacts (Q18). |
| §15 | `emergency_phone` placeholder in configuration defaults (Q29). |
