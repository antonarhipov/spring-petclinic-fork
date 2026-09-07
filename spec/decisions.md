# Smart Appointment Scheduling — Decisions Log

Outcome of a structured review ("grilling") of `proposal.md` held on 2026-09-06. Each entry records a question the
proposal left open or contradictory, the decision taken, and the alternatives that were rejected. Where a decision
changes or refines the proposal, the affected section is referenced. All decisions below have been folded into
`proposal.md` (revision of 2026-09-06); this log remains the record of the alternatives considered and the rationale.
Should the two ever disagree, this log wins.

Facts established during the review: the project is Spring Boot 4.1.0 / Java 17 with no Spring Security, Spring AI or
Flyway yet; the database seeds exactly three specialties (`radiology`: Leary, Stevens · `surgery`: Douglas, Ortega ·
`dentistry`: Douglas; Carter and Jenkins have none); eleven message bundles are shipped; the Ollama tag
`ministral-3:14b` exists (9.1 GB, JSON output, requires Ollama ≥ 0.13.1).

---

## A. Availability semantics and matching (§5, §7)

### D1 — Unmentioned times are **not** bookable (closed by default)
A slot is feasible only if it lies inside a *preferred* or *allowed* window. Excluded windows are purely subtractive.
An interpretation with zero preferred + allowed windows is an *Interpretation failed* outcome.
*Rejected:* open by default (anything not excluded); open only on an explicit "anything works".
*Rationale:* with the proposal's own example, "earliest date" would otherwise offer Monday — a day the owner never
mentioned — before the Friday morning they explicitly offered.

### D2 — Window model
A window is `{weekday | concrete date, start time, end time}` in clinic-local time. Recurring weekday windows apply to
every matching day inside the booking horizon. Concrete dates are resolved by the interpreter relative to the request
creation date and persisted as absolute dates. Named parts of day are resolved to times by the interpreter using the
clinic settings passed in the prompt.
*Rejected:* weekday patterns only; absolute date ranges only.

### D3 — The model expands exclusions into explicit windows
The prompt carries opening hours per weekday, today's date and the time zone; the model must emit explicit allowed
windows for phrases like "any day except Wednesday". No special "all week" marker exists, so what is persisted is
exactly what is matched.
*Rejected:* application-side expansion of a marker; treating pure exclusions as failure.

### D4 — Ranking is strict lexicographic
Preferred window > preferred veterinarian > earliest start > fewest appointments already booked for that vet on that day
> lowest vet id. No weights, no configuration. Specialists get no preference beyond the hard specialty constraint.
*Rejected:* earliest-date-first; weighted score; generalists-first.

### D5 — Minimum lead time
The suggestion horizon is `[start of tomorrow, today + booking horizon]`. Same-day slots are never suggested. The lead
time is a clinic setting (`minimum lead days`, default 1) and must be added to the *Configuration defaults* table.
Staff bookings are not subject to it.
*Rejected:* now + fixed buffer; no lead time.

### D6 — Raw duration persisted, clamped at match time
The model's raw duration estimate is persisted untouched (honours "persisted verbatim"). The matcher applies the
configured bounds at run time. The review page shows the effective duration and, when clamping occurred, a message-key
note that the clinic's bounds apply.
*Rejected:* clamp before persisting.

### D7 — Rank reason shown to the owner
Each suggestion carries a one-line, message-key based reason derived from the ranking tier (preferred vs allowed
window; whether the preferred vet was honoured). No model-generated text is shown.

---

## B. Holds, rejections and the request lifecycle (§6)

### D8 — Holds have no timer, but staff can release them
A hold lasts until the owner acts, **or until staff release it**. Release is available from the scheduling queue and
the calendar; it moves the request to *With staff* with a recorded reason. The staff calendar shows owner, pet and hold
age for every held slot.
*Rejected:* hold expiry; no escape hatch.

### D9 — Rejections persist for the whole request
A rejected veterinarian-and-time stays excluded for the request even after the owner edits the text and obtains a new
interpretation. "Ask for another option" is irreversible; the UI says so.
*Rejected:* reset on new interpretation; browsable history of earlier offers.

### D10 — Exhaustion routes automatically to *With staff*
When no candidate slot remains, the request transitions to *With staff* with reason "no slots available" and an
explanatory message. The owner keeps *abandon*; editing the text is not offered in that state.
*Rejected:* returning to *Interpreted* with an offer.

### D11 — One active request per pet is enforced by the database
A nullable `active_pet_id` column equals `pet_id` while the request is open and is nulled on *Accepted* / *Abandoned*,
with `UNIQUE(active_pet_id)`. A losing concurrent insert redirects the owner to the existing request.
*Rejected:* pessimistic lock on the pet row; application-level check only.

### D12 — Owner cancellation needs no reason
Owners cancel upcoming appointments without a reason; the appointment records `cancelled_by = OWNER` and the timestamp.
Cancelling never re-opens the request.

---

## C. AI interpretation (§5, §14)

### D13 — Interpretation runs asynchronously
Consent enqueues a job on a bounded `@Async` pool (2 threads, unbounded queue). A new lifecycle state **Interpreting**
sits between *Awaiting consent* and *Interpreted* / *Interpretation failed*:

| State        | Meaning                              | Owner may       |
|--------------|--------------------------------------|-----------------|
| Interpreting | Text sent to the AI, result pending  | view · abandon  |

A result arriving for an abandoned request is discarded. Overall job deadline is 120 s; on expiry the request goes to
*With staff* as "AI unavailable". Connect timeout 5 s; no automatic retry.
On application startup every request still in *Interpreting* is swept to *With staff* with reason "AI unavailable".
*Rejected:* synchronous call (30 s + retry, or 90 s); startup sweep back to *Awaiting consent*; re-submitting jobs.

### D14 — Polling via JavaScript against a state-only JSON endpoint
`GET /my/requests/{id}/status` returns `{"state": "..."}` only. It is owner-scoped with the same 404 rule as the pages,
is included in the whole-URL-space security tests, and the page shows a visible *Refresh* link when the script does not
run. The end-to-end test polls the HTML detail page; the JSON endpoint is covered by the security suite and a unit test.
*Rejected:* meta-refresh; manual refresh; full request JSON.

### D15 — Definition of failure and unavailability
*Interpretation failed* = unparseable output **or** zero preferred + allowed windows **or** the model's own
`understood=false` flag. A missing reason for the visit is **not** a failure (defaults: 30 minutes, general care).
*AI unavailable* = any transport error or the deadline in D13. Both are asserted through the deterministic test double.
*Rejected:* strict schema validation of every field; treating parse failure as "unavailable".

### D16 — Closed vocabulary for specialties and veterinarians
The prompt enumerates the clinic's specialties and veterinarian names. The output schema constrains `specialty` to one
of those values or `OTHER` (the model's free label is persisted for staff) and `preferredVet` to an enumerated vet id or
`null`. `OTHER` is the "specialty no veterinarian offers" trigger. An unrecognised vet name yields `null`.
*Rejected:* fuzzy matching; exact-string matching.

### D17 — Prompt contents and determinism
Only the owner's free text plus clinic data (specialties, vet names, opening hours, today's date, time zone, duration
bounds) is sent — no owner or pet identifiers. Calls use temperature 0 and Ollama structured output (JSON schema). Each
AI-origin interpretation persists the raw model JSON, the model tag and the prompt version. The consent page states
exactly this.
*Rejected:* including pet species/age; sending owner and pet details.

### D18 — English input only, structured display
Free text is expected in English; this is documented in the README and hinted on the request page, not detected or
enforced. The persisted interpretation is purely structured; the review page renders it from message keys and
locale-aware formatters. The model never produces user-visible prose.
*Rejected:* language-agnostic prompt; model-written summary.

---

## D. Staff behaviour (§8, §9)

### D19 — Interpretation versions are immutable and carry an origin
An interpretation record has `origin ∈ {AI, STAFF}` and is never mutated. A staff edit creates a new STAFF-origin
version that becomes the confirmed one; the AI original stays in history. Owner detail page and queue show the current
origin.
*Rejected:* in-place staff edits; staff never touching the interpretation.

### D20 — Staff may author a version only while the request is *With staff*
The form pre-fills from the latest version (AI or STAFF) or is empty for declined-consent requests. Staff-placed
suggestions and direct bookings are always computed from the latest version.
*Rejected:* staff intervention in owner-visible states with forced re-confirmation.

### D21 — Staff acting on behalf of an owner
Staff can create a request for any pet; it starts directly in *With staff* (no AI, no consent). Staff never trigger
interpretation and never act inside an owner's *Interpreted* / *Suggestion offered* states except releasing a hold
(D8).
*Rejected:* staff running the full owner flow as a proxy with staff-given consent.

### D22 — Constraints that bind staff
Staff bookings, staff-placed suggestions and reschedules must respect opening hours, the vet's working blocks and
no-overlap. They are **not** bound by the owner's windows, the booking horizon, the lead time or the required
specialty; the booking form highlights matching vets and warns on a specialty mismatch.
*Rejected:* all §7 hard constraints for staff; unrestricted overrides.

### D23 — Reschedule is a direct change
No owner re-acceptance. The owner sees the new time and the reason under *My appointments* and may cancel.
*Rejected:* reschedule-as-suggestion.

### D24 — Staff queue: two sections, optimistic locking
(1) *Needs staff* — requests in *With staff*, oldest first, with the trigger reason. (2) *In progress* — every other
open request (state, held slot, hold age), read-only except *release hold*. Requests carry a version; a staff action on
a stale request fails with a flash message. No claiming.
*Rejected:* *With staff* only; explicit claim/unclaim.

### D25 — Completion and no-show
Allowed only after the appointment's start time; both are final; cancelled appointments cannot be completed. On
*complete*, staff enter (or accept a pre-filled) description; the visit stores a nullable FK to the appointment and
takes the appointment date. The stock *Add visit* form remains for walk-ins (null appointment).
*Rejected:* any-time closure; reversible closure; auto-copied reason without link; retiring the stock form.

### D26 — Schedule and opening-hour changes vs existing bookings
A change (weekly schedule, exception, leave, opening hours, closure) that conflicts with a `CONFIRMED` appointment is
rejected and the conflicting appointments are listed; staff must reschedule first. Conflicting `HELD` slots are released
automatically and their requests move to *With staff* with reason "schedule changed".
*Rejected:* allow-and-flag; cascade cancel.

### D27 — Calendar shape
Day view: one column per veterinarian, rows on the 15-minute grid within that day's opening hours, previous/next-day
and date-picker navigation covering the booking horizon; closed/unavailable ranges shaded; appointments and holds as
blocks with click-through.
*Rejected:* per-vet week view; both views.

---

## E. Platform, data and security (§3, §4, §12, §13, §14)

### D28 — Injectable clock
A `java.time.Clock` bean provides "now". Tests pin it (e.g. 2026-09-07 09:00 Europe/Amsterdam) so suggestions, the
horizon and the seeded exceptions are deterministic. Seeded exception dates stay normative fixtures; the spec notes that
they are only meaningful around September–October 2026.
*Rejected:* relative seed dates; system clock everywhere.

### D29 — Clinic-local date and time storage
Appointments, holds, windows, exceptions and opening hours are stored as `LocalDate` + `LocalTime`. The configured zone
is used only to derive "today" from the clock. (DST ends 2026-10-25, inside a mid-October horizon.)
*Rejected:* UTC instants.

### D30 — One appointments table with status
`status ∈ {HELD, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW}`. A hold is a `HELD` row linked to its request; accept
flips it to `CONFIRMED`; when a hold ends (rejection, release, edit, abandon) the row is **deleted** — the rejection list
(D9) is the durable record.
*Rejected:* separate holds table; keeping `RELEASED` rows.

### D31 — Concurrency for slot mutations
All hold/confirm/book/reschedule operations for a veterinarian run under a pessimistic row lock on that vet
(`SELECT … FOR UPDATE`) and re-check overlap inside the transaction. A unit-level test races two threads on the same
slot.
*Rejected:* unique constraint on (vet, start); JVM-global lock.

### D32 — Owner routes are separate from stock pages
Owner pages live under `/my/**` (`/my/pets`, `/my/appointments`, `/my/requests/**`) and are bound to the signed-in
owner; no owner id appears in owner URLs. Stock `/owners/**` and `/vets/**` are staff-only. Security is path-based;
stock controllers stay essentially untouched.
*Rejected:* reusing stock pages with role branching.

### D33 — Anonymous surface and denial semantics
Anonymous: `/login`, static resources and `/actuator/health` only. Other actuator endpoints and the H2 console require
the staff role. An owner opening another owner's data receives **404** with the standard not-found page
(indistinguishable from a non-existent id). An owner on a staff page receives **403**.
*Rejected:* nothing anonymous / 403 everywhere; all actuator anonymous.

### D34 — End-to-end tests use a real server
`webEnvironment = RANDOM_PORT` with a real HTTP client handling session cookies and CSRF, signed in as owner and as
staff. MockMvc does not satisfy "real HTTP endpoints".

### D35 — Non-H2 and non-Maven artefacts are removed
Delete `db/mysql`, `db/postgres`, the `mysql`/`postgres` profiles and their integration tests, `docker-compose.yml`,
`k8s/`, `build.gradle` and `settings.gradle`. The README states the POC is H2 (file-based) + Maven only.
*Rejected:* leaving them untouched; multi-vendor Flyway migrations.

### D36 — Ollama is installed locally
No Compose file. The README instructs to install Ollama (≥ 0.13.1), pull `ministral-3:14b` and run on
`localhost:11434`. Automated tests use the deterministic test double and never need Ollama.
*Rejected:* Compose with an Ollama service; Spring Boot Docker Compose support.

---

## Edits applied to `proposal.md` (summary)

- §5: add D3, D6, D15–D18; state English-only input.
- §6: insert the *Interpreting* state (D13); add "staff release" as a hold terminator (D8); state D9, D10, D12.
- §7: replace the hard availability rule with D1; define windows (D2); replace the soft-constraint list with the
  lexicographic order in D4; add lead time (D5) and the rank reason (D7).
- §8: add D19–D22, D24.
- §9: add D23, D25–D27.
- §10: add `minimum lead days` (D5) and D26.
- §12: add the clock (D28), the concurrency test (D31), the JSON endpoint in the security suite (D14), and the
  real-server requirement (D34).
- §13/§14: add D32–D36; note the async executor (D13).
- §15 *Configuration defaults*: add `Minimum lead days | 1`.
