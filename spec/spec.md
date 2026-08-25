# Spec: Smart Appointment Scheduling

Pipeline position: proposal → **spec** → criteria → rules → review → plan
Source proposal: `spec/proposal.md` (refined via `/grill-me` to a shared understanding before this spec).

## Feature summary

Add authenticated, AI-assisted appointment scheduling to Spring PetClinic. Pet **owners** describe their availability and reason for visit in free text; with explicit consent, the text is interpreted by an LLM (Spring AI `ChatClient` → Ollama) into a structured request. A Timefold constraint solver then guides the owner through a **one-suggestion-at-a-time** flow against a frozen view of the calendar, briefly holding each offered slot. Owners never see the full calendar. **Staff** manage clinic configuration, veterinarian availability, all accounts, appointment lifecycle, and a fallback queue that catches every case the automated flow cannot complete (consent declined, AI/solver unavailable, no matching specialty, suggestions exhausted, or a detected emergency). The app remains a fully server-rendered Thymeleaf application; both interpretation and solving run asynchronously behind auto-refreshing status pages.

## Resolved ambiguities

Each decision below was settled during grilling; rationale is one line.

- **R-1 Solver scope** — Timefold performs a *single-request* solve against a frozen snapshot (confirmed appointments + active holds + resolved vet availability as hard inputs), never a whole-clinic re-optimization. *Keeps confirmed bookings immutable and avoids one owner's activity reshuffling another's offer.*
- **R-2 Time grid** — appointment starts fall on a **15-minute grid**; visit **duration is variable**, derived from the interpretation and clamped to clinic min/max (clinic default when the text is vague). A request occupies N consecutive 15-minute units that must fit one contiguous availability block. *Grid makes holds/exclusions/overlap tractable; variable duration honors "likely visit length".*
- **R-3 Symbolic window resolution** — the LLM emits *symbolic* windows (day-of-week and/or concrete date + named part-of-day); the server resolves them to concrete time ranges using clinic `PartOfDay` settings. A symbolic day-of-week with no date resolves to **every matching day within the booking horizon**. *Stable model output; the clinic defines what "afternoon" means.*
- **R-4 Constraint model** — Hard: within resolved vet availability; no overlap with that vet's confirmed appointments or active holds; duration fits a contiguous free block; vet holds the required specialty when care is SPECIALTY; within booking horizon and not in the past; outside the owner's *excluded* windows; not a previously-rejected `(vet_id, start_time)` for this request. Soft: inside *preferred* windows ≫ inside *allowed* windows; matches preferred vet; earlier-is-better weighted by urgency; light load-balancing across vets. *Directly encodes the proposal's priorities.*
- **R-5 Reject granularity** — rejecting a suggestion excludes the **exact `(vet_id, start_time)` pair only** (duration is fixed per request, so start uniquely identifies the block). *Matches "that exact veterinarian-and-time".*
- **R-6 Both async** — interpretation *and* solve run on a bounded background executor; the owner waits on auto-refreshing (~2s meta-refresh) status pages. *Ollama latency is seconds; blocking a request thread is unacceptable and page state is the polling anchor.*
- **R-7 State machine** — `SchedulingRequest`: `DRAFT → INTERPRETING → AWAITING_CONFIRMATION → SUGGESTING ⇄ SLOT_HELD → CONFIRMED`, with escapes to `STAFF_QUEUED` (reasons: `CONSENT_DECLINED`, `AI_UNAVAILABLE`, `SOLVER_UNAVAILABLE`, `NO_SPECIALTY_VET`, `SUGGESTIONS_EXHAUSTED`, `EMERGENCY`) and terminal `CANCELLED`. Editing free text after confirmation resets to `DRAFT` requiring fresh consent + interpretation. *One aggregate, explicit transitions; escape hatches feed the queue.*
- **R-8 Consent** — explicit per-request consent (a checkbox on the request form) is required before any text is sent to the LLM; declining routes the request to the staff queue with reason `CONSENT_DECLINED`. *"Owner must knowingly agree" before AI use.*
- **R-9 Data retention** — raw free text **and** structured interpretation are stored indefinitely on the request record. *Staff fallback needs the original words to verify/complete an interpretation; retention/purge policy is out of scope.*
- **R-10 Holds** — default hold length is a clinic setting (default **5 minutes**). Expiry is validated lazily on the next owner action **and** reclaimed by a `@Scheduled` sweeper. A partial-unique constraint on `(vet_id, start_time)` across active holds + confirmed appointments prevents double-offer/double-book. From `SLOT_HELD` the owner has exactly two actions: **Accept** (→ `CONFIRMED`) or **Reject/ask-again** (release hold + exclude that pair + re-solve). A **pure timeout** releases the hold **without** excluding the slot and returns to `SUGGESTING`. *Prevents contention while letting an un-rejected slot be re-offered.*
- **R-11 Emergencies** — two tiers. `URGENT` stays in the automated flow with earlier-is-better weighted up. `EMERGENCY` **short-circuits the solver** straight to `STAFF_QUEUED` at top priority. Urgent-care guidance is **always visible** on the request form regardless of interpretation. *Never make an emergency click through accept/reject.*
- **R-12 Authentication** — introduce Spring Security **form login** (no security exists today). New `UserAccount` (username, bcrypt password, role `OWNER`|`STAFF`, `must_change_password`, nullable FK to `owner`). Username is a staff-assigned string. Staff are decoupled from `Vet`. *Reference-app-appropriate, matches staff-provisioned accounts.*
- **R-13 Account management** — staff manage **both** owner and staff accounts (create users, reset passwords). New accounts get a staff-set **temporary password** with `must_change_password=true`, forcing a password change on first login. The **initial staff account** is bootstrapped at application startup from config/system properties (not seeded in Flyway). *Bootstraps a usable system without self-registration.*
- **R-14 Appointment vs Visit** — new `Appointment` entity (FKs `Pet`/`Owner`/`Vet`/`SchedulingRequest`; `SchedulingRequest` nullable for staff direct-booking) with `startTime`, `durationMinutes`, `status` (`BOOKED`|`COMPLETED`|`NO_SHOW`|`CANCELLED`), `changeReason`. Marking an appointment **COMPLETED spawns a `Visit`** in the pet's history; the existing `Visit` entity is unchanged. *Appointment is the forward-looking booking; Visit stays the historical record.*
- **R-15 Request↔pet cardinality** — a `SchedulingRequest` targets **exactly one pet** and yields **at most one** `Appointment`. *One animal per request.*
- **R-16 Clinic configuration model** — `ClinicSettings` (single row: `timeZone`, `minVisitMinutes`/`maxVisitMinutes`/`defaultVisitMinutes`, `bookingHorizonDays`, `holdDurationMinutes`, `gridMinutes`=15); `PartOfDay` (`name`, `startTime`, `endTime`); `VetWeeklyShift` (`vet_id`, `dayOfWeek`, `startTime`, `endTime` — multiple rows per day = split shifts); `VetDateException` (`vet_id`, date or range, `type` `LEAVE`|`MODIFIED_HOURS`|`EXTRA`, optional times); `ClinicClosure` (date or range, `reason`, applies to all vets). *Reflects how the clinic actually operates.*
- **R-17 Availability resolution** — a vet's bookable time on a date = weekly shifts for that day, minus date exceptions/leave, minus clinic closures. *Single deterministic availability function feeds the solver.*
- **R-18 Single time zone** — the clinic operates in one configured time zone (`ClinicSettings.timeZone`); all availability, "now", the booking horizon, and appointment times are interpreted in it. *Anchors all temporal reasoning.*
- **R-19 Staff fallback queue** — modeled as a filtered view over `SchedulingRequest` where `state=STAFF_QUEUED`, carrying `queue_reason`, `priority`, `queued_at`; ordered emergency-first then FIFO. Staff verify/complete the interpretation, then **book on the owner's behalf**. *No separate table; the request is the queue item.*
- **R-20 Persistence & migrations** — introduce **Flyway** owning *all* schema + seed data; disable `spring.sql.init`. Locations `classpath:db/migration/common` + `classpath:db/migration/{vendor}`. V1 = today's schema/data baseline per vendor (H2/MySQL/Postgres) with current seed data preserved unchanged; new scheduling tables follow as V2+. Tests run the same migrations (H2 + Testcontainers MySQL). *House style is script-driven DDL; Flyway makes it versioned and consistent.*
- **R-21 AI integration** — Spring AI `ChatClient` (Spring AI 2.0.1) → Ollama, default model `gemma4:latest` (configurable via properties), structured output bound to the interpretation record. **No `ChatModel` bean configured** (e.g. no-key profile) → interpretation is skipped and the request routes to the staff queue (`AI_UNAVAILABLE`). *App stays runnable without a live model.*
- **R-22 Access zones** — three zones: `/staff/**` (`ROLE_STAFF`); owner self-service (`ROLE_OWNER`); public (login, forced change-password, welcome). The existing `/owners/**` owner+pet CRUD becomes **staff-only**. Owners get their own self-service routes and every owner-facing controller method **re-checks ownership** of the target request/pet/appointment (not just URL-guarding). *Owners see only their own data; staff manage the clinic.*
- **R-23 Owner self-service scope** — owners may: update their own profile (`firstName`, `lastName`, `address`, `city`, `telephone`; update-only, no self-delete of the account); manage their **own** pets (add/edit/delete); run scheduling for their own pets; view and cancel their own upcoming appointments. *Matches the confirmed owner capability set.*
- **R-24 Request concurrency** — at most **one active `SchedulingRequest` per pet** (any non-terminal state blocks a second for that pet); an owner may have one active request per each of their pets concurrently. *Prevents one owner holding two slots for the same animal.*
- **R-25 Owner cancellation** — an owner may cancel any of their own `BOOKED` appointments any time up to its start; **no lead-time cutoff**. Staff-initiated cancellations are always allowed with a recorded reason. *Matches the proposal's plain wording; simplest defensible rule.*

## Explicit assumptions

- **A-1** — A `SchedulingRequest` reaching `CONFIRMED` is terminal. If the resulting appointment is later cancelled, the request is **not** reopened; the owner starts a new request to rebook. (Keeps the aggregate lifecycle simple.)
- **A-2** — When the interpretation yields no availability windows at all (owner supplied only a reason), the request is treated as having no excluded/preferred windows and the solver offers the earliest feasible slot within the horizon.
- **A-3** — Staff **direct booking** bypasses AI, consent, holds, and the suggestion loop entirely; staff pick a vet + time directly against the full calendar. Such an `Appointment` has a null `SchedulingRequest`.
- **A-4** — Staff **reschedule** re-validates the new time against vet availability and overlap (same hard rules as booking) and records a `changeReason`.
- **A-5** — Password policy is minimal (non-empty, a sensible minimum length); complexity rules, history, and expiry are out of scope. No login lockout/throttling.
- **A-6** — During the guided flow an owner sees, for the single current suggestion only: vet name, vet specialty, date, start time, duration, and the hold countdown. The full calendar is never rendered to owners.
- **A-7** — "Suggestions exhausted" means a single-request solve returns infeasible given all hard constraints and accumulated exclusions; the request then moves to `STAFF_QUEUED(SUGGESTIONS_EXHAUSTED)`.
- **A-8** — A visit's duration end need not align to the grid; only the **start** is grid-aligned. The occupied block is `[start, start + durationMinutes)` and must lie within one contiguous availability block.
- **A-9** — Consent, interpretation, and solving apply only to the owner-initiated smart flow. When staff complete a queued request they may edit/replace the interpretation fields directly without re-invoking the LLM.
- **A-10** — Appointment statuses `COMPLETED`, `NO_SHOW`, and `CANCELLED` are terminal for an `Appointment`.
- **A-11** — H2 is the primary dev/test database; MySQL and Postgres migrations are maintained in parallel per-vendor.

## Handled edge cases

- **E-1** — LLM call errors or times out during `INTERPRETING` → `STAFF_QUEUED(AI_UNAVAILABLE)`; the status page redirects to a "handed to staff" notice.
- **E-2** — No `ChatModel` bean configured → request never enters `INTERPRETING`; routed to `STAFF_QUEUED(AI_UNAVAILABLE)`.
- **E-3** — Solver unavailable/errors during a solve → `STAFF_QUEUED(SOLVER_UNAVAILABLE)`.
- **E-4** — Interpretation requires SPECIALTY care but no vet holds that specialty → `STAFF_QUEUED(NO_SPECIALTY_VET)` (no attempt to offer a general vet).
- **E-5** — Owner declines consent → `STAFF_QUEUED(CONSENT_DECLINED)`; no text sent to the LLM.
- **E-6** — Interpretation flagged `EMERGENCY` → solver short-circuited; `STAFF_QUEUED(EMERGENCY)` at top priority; urgent-care banner shown throughout.
- **E-7** — Hold expires while the owner is deciding (pure timeout) → hold released, slot **not** excluded, request returns to `SUGGESTING`; next action re-solves (may re-offer the same slot if still free).
- **E-8** — Two owners are eligible for the same `(vet, start)` → the partial-unique constraint lets only one hold/booking win; the loser's solve excludes it and offers the next slot.
- **E-9** — Solve finds no feasible slot given exclusions → `STAFF_QUEUED(SUGGESTIONS_EXHAUSTED)`.
- **E-10** — Owner edits the free text after confirming the interpretation → request resets to `DRAFT`, requiring fresh consent and a fresh interpretation.
- **E-11** — Owner attempts a second scheduling request for a pet that already has an active one → blocked; the existing active request is surfaced instead.
- **E-12** — First login with `must_change_password=true` → all requests redirect to the change-password page until the password is changed.
- **E-13** — An owner attempts to access another owner's request/pet/appointment (URL tampering) → access denied by the per-method ownership check, not merely the URL zone.
- **E-14** — Preferred vet is unavailable in a feasible window → soft constraint only; the solver may offer a different qualified vet, which the owner can reject.
- **E-15** — Cancelling a confirmed appointment frees its slot on the calendar (the `(vet, start)` becomes bookable again for other requests).

## Behaviors to verify (handoff to criteria)

- **B-1** — The system requires authentication for all non-public routes and presents a form login.
- **B-2** — The system forces a user with `must_change_password=true` to change their password before accessing any other authenticated route.
- **B-3** — The system lets staff create owner and staff accounts with a staff-assigned username and temporary password, setting `must_change_password=true`.
- **B-4** — The system lets staff reset any user's password (re-issuing a temporary password and re-setting the change-required flag).
- **B-5** — The system creates/reconciles an initial staff account at startup from configured properties.
- **B-6** — The system restricts `/staff/**` and the (now staff-only) `/owners/**` owner+pet CRUD to `ROLE_STAFF`.
- **B-7** — The system lets an owner update only their own profile fields (firstName, lastName, address, city, telephone) and denies access to other owners' profiles.
- **B-8** — The system lets an owner add, edit, and delete only their own pets, and denies action on pets they do not own.
- **B-9** — The system displays always-visible urgent-care guidance on the scheduling request form.
- **B-10** — The system requires explicit per-request consent before sending any free text to the LLM.
- **B-11** — The system routes a request to the staff queue with reason `CONSENT_DECLINED` when the owner declines consent, without calling the LLM.
- **B-12** — The system interprets the owner's free text asynchronously into a structured interpretation (visit duration, care type + optional specialty, preferred/allowed/excluded windows, optional preferred vet, urgency, summary) while the request is in `INTERPRETING`.
- **B-13** — The system shows an auto-refreshing status page while a request is in `INTERPRETING` or `SUGGESTING` and advances the page when the state changes.
- **B-14** — The system routes a request to `STAFF_QUEUED(AI_UNAVAILABLE)` when interpretation errors, times out, or no `ChatModel` bean is configured.
- **B-15** — The system presents the structured interpretation for owner review/confirmation before any scheduling.
- **B-16** — The system resets a request to `DRAFT` and requires fresh consent + interpretation when the owner edits the free text after confirmation.
- **B-17** — The system clamps interpreted visit duration to the clinic min/max and applies the clinic default when duration is unspecified.
- **B-18** — The system resolves symbolic windows (day-of-week and/or date + named part-of-day) into concrete time ranges using `PartOfDay` settings and the booking horizon.
- **B-19** — The system produces at most one suggested slot at a time via a single-request solve against confirmed appointments, active holds, and resolved vet availability.
- **B-20** — The system offers only slots that satisfy all hard constraints (vet availability; no overlap; contiguous fit; required specialty; within horizon and not past; outside excluded windows; not previously rejected).
- **B-21** — The system orders candidate slots by the soft constraints (preferred ≫ allowed windows; preferred vet; earlier-is-better weighted by urgency; load-balancing).
- **B-22** — The system places a hold on each offered slot for the configured hold duration and shows a countdown, exposing only vet name, specialty, date, start, and duration.
- **B-23** — The system prevents two concurrent holds/bookings on the same `(vet_id, start_time)`.
- **B-24** — The system confirms the appointment and transitions the request to `CONFIRMED` when the owner accepts a held slot.
- **B-25** — The system releases the hold, permanently excludes the exact `(vet_id, start_time)` for that request, and re-solves when the owner rejects/asks again.
- **B-26** — The system releases an expired hold without excluding the slot and returns the request to `SUGGESTING`.
- **B-27** — The system reclaims abandoned holds via a scheduled sweeper and also invalidates expired holds lazily on the next owner action.
- **B-28** — The system routes a request to `STAFF_QUEUED(NO_SPECIALTY_VET)` when specialty care is required but no vet holds that specialty.
- **B-29** — The system routes a request to `STAFF_QUEUED(SUGGESTIONS_EXHAUSTED)` when the solve is infeasible given accumulated exclusions.
- **B-30** — The system routes a request to `STAFF_QUEUED(SOLVER_UNAVAILABLE)` when the solver errors or is unavailable.
- **B-31** — The system short-circuits the solver and routes to `STAFF_QUEUED(EMERGENCY)` at top priority when the interpretation is `EMERGENCY`.
- **B-32** — The system weights earlier-is-better more strongly for `URGENT` interpretations while keeping them in the automated flow.
- **B-33** — The system presents the staff queue ordered emergency-first then FIFO, showing queue reason and queued time.
- **B-34** — The system lets staff verify/complete a queued request's interpretation and book a slot on the owner's behalf.
- **B-35** — The system lets staff book, reschedule, and cancel appointments directly against the full calendar, recording a `changeReason` for staff-initiated changes, and re-validates rescheduled times against availability and overlap.
- **B-36** — The system creates a `Visit` in the pet's history when staff mark an appointment `COMPLETED`.
- **B-37** — The system records an appointment as `NO_SHOW` when staff so mark it, without creating a `Visit`.
- **B-38** — The system lets an owner view and cancel their own `BOOKED` upcoming appointments any time before start, and frees the slot on cancellation.
- **B-39** — The system denies an owner any view or action on another owner's appointments.
- **B-40** — The system blocks a second active `SchedulingRequest` for a pet that already has one, while allowing an owner one active request per each of their pets.
- **B-41** — The system computes a vet's bookable availability as weekly shifts minus date exceptions/leave minus clinic closures, interpreted in the clinic time zone.
- **B-42** — The system lets staff configure clinic settings, parts-of-day, vet weekly shifts (including split shifts), vet date exceptions/leave, and clinic closures.
- **B-43** — The system applies sensible clinic-setting defaults out of the box.
- **B-44** — The system persists both the raw free text and the structured interpretation on the request record.
- **B-45** — The system manages schema and seed data via Flyway (common + per-vendor migrations), preserving existing seed data, with `spring.sql.init` disabled.

## Out of scope

- Owner self-registration and password recovery/reset-by-self.
- Exposing the clinic's full availability calendar to owners.
- External notifications (email/SMS/push).
- Multiple clinics / multi-tenancy.
- Waitlists.
- Retention/purge policy for stored free text and interpretations (kept indefinitely for now).
- Password complexity/history/expiry policy and login lockout/throttling.
- Reopening a `CONFIRMED` request after its appointment is cancelled (owner starts a new request).

## External dependencies

- **D-1** — A reachable **Ollama** instance serving the configured model (default `gemma4:latest`). *Blocker:* runtime environment. *Default:* when no `ChatModel` is configured/reachable, interpretation is skipped and requests route to the staff queue (`AI_UNAVAILABLE`), so the application remains fully functional without it (R-21, B-14).
