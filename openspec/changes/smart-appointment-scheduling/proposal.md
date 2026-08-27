## Why

Today PetClinic is fully anonymous: anyone can view and edit any owner, pet, vet, or visit, and appointments do not exist as a concept. Booking a visit means a human picks a date out of thin air with no availability model, no conflict protection, and no notion of who is allowed to do what.

This change introduces **smart appointment scheduling**: pet **owners** describe when they can and cannot come in plain language, AI turns that into a structured interpretation, and **Timefold** guides the owner to **one** suitable slot at a time in an accept/reject loop protected by short-lived holds. Clinic **staff** configure the clinic, manage veterinarian availability, and pick up anything the automated flow cannot handle so no owner is ever dead-ended. Because the base app has no identity at all, authentication, roles, and per-user data scoping are introduced from scratch as part of this work.

## What Changes

- **BREAKING — authentication is introduced.** A dedicated `app_user` identity (username, password hash, role `OWNER | STAFF`, `enabled`, `mustChangePassword`) backs a single `UserDetailsService`. All existing controllers (owner/pet/vet/visit) become secured; only login, error, static assets, and the static urgent-care guidance remain anonymous. Owner accounts are staff-provisioned with a forced first-login password change; staff can reset passwords. Staff "act on behalf" of an owner through `ownerId`-scoped screens (no credential impersonation).
- **Clinic calendar model.** Clinic-wide settings (visit-duration bounds, booking horizon, hold duration, start-time grid granularity, named day-parts) with sensible defaults; per-vet recurring weekly schedules with split shifts, date-specific exceptions, and leave; clinic-wide closures; a single configured time zone (`Europe/Amsterdam` default). Instants are stored in UTC and reasoned about in the clinic zone with one DST-aware conversion boundary.
- **First-class appointment request lifecycle.** A new `AppointmentRequest` with an explicit state machine (`DRAFT → AWAITING_CONSENT → INTERPRETED → CONFIRMED → SUGGESTING/HELD → SCHEDULED | QUEUED_FOR_STAFF | CANCELLED | EXPIRED`), guided one-slot-at-a-time suggestions, short-lived `(vet, start)` holds with mutual exclusion, and accept/reject/ask-again. A new `Appointment` entity (a future slot) is kept distinct from the existing `Visit` (a past, completed event); completing an appointment records a `Visit`. Owners get self-service view/cancel of their own upcoming appointments.
- **AI interpretation behind a consent gate.** An explicit consent gate precedes any AI call; the AI produces a strict typed structured interpretation (care type, estimated duration, preferred/allowed/excluded windows, optional preferred vet, urgency) that is validated, clamped, and normalized to day-parts and the clinic time zone server-side. Owners may edit structured fields without re-consent; revising free text requires fresh consent and a fresh interpretation; validation/AI failures route to the staff queue.
- **Timefold single-slot solver.** A per-request single-slot solve over a fixed start-time grid with hard feasibility (inside effective availability, no overlap with appointments/active holds, not excluded, not a previously rejected `(vet, start)` pair, specialty match when needed) and an ordered soft score (preferred window > allowed, preferred-vet soft bonus, sooner-is-better) with a deterministic `(vet id, start instant)` tie-break so "ask again" provably progresses and terminates.
- **Staff fallback and lifecycle.** A staff fallback queue fed by AI/solver-unavailable, declined-consent, incomplete-interpretation, and no-specialty triggers; a staff-unblock-then-owner-resumes flow (holds stay short, no notification needed); direct book/reschedule/cancel with a recorded reason; complete (→ records a `Visit`) / no-show lifecycle; and emergency handling where AI urgency is **advisory only** while static urgent-care guidance is shown **unconditionally**.
- **Build & dependency updates.** Add Spring Security, Spring AI (Ollama), Timefold, and Flyway. Fold the current per-vendor `spring.sql.init` scripts into a Flyway **V1 baseline** across H2/MySQL/Postgres and disable `spring.sql.init`. Coordinates are added to `pom.xml` first (canonical) and mirrored into `build.gradle`.

## Capabilities

### New Capabilities
<!-- Mirrors the empty package scaffolding already present in the codebase; `notification` is intentionally omitted (out of scope). -->
- `security`: `app_user` identity and `OWNER | STAFF` roles, single `UserDetailsService`, nullable one-to-one `Owner` link, securing all existing controllers (login/error/static/urgent-care stay anonymous), staff act-on-behalf via `ownerId`-scoped screens, staff-provisioned accounts with forced first-login change and password reset, and demo seeding.
- `calendar`: clinic settings with confirmed defaults, per-vet recurring weekly schedules with split shifts, date-specific exceptions, per-vet leave, clinic-wide closures, and a single configured time zone with UTC storage and clinic-zone reasoning.
- `appointment`: the `AppointmentRequest` state machine, guided one-slot-at-a-time suggestions, `(vet, start)` holds (unique constraint + TTL) with re-validate/auto-recover on accept, the `Appointment` entity distinct from `Visit`, completion recording a `Visit`, and owner self-service view/cancel within the 24h window.
- `scheduling/interpretation`: the explicit consent gate, the strict typed structured interpretation schema, server-side validation/clamping and day-part/time-zone normalization, owner editing of structured fields without re-consent (fresh text = fresh consent), and routing of validation/AI failures to the staff queue.
- `scheduling/solver`: per-request single-slot Timefold solve over the fixed start-time grid, hard feasibility (availability/overlap/excluded/rejected-pair/specialty), ordered soft score, deterministic `(vet id, start instant)` tie-break, and provable progression/termination via permanent rejected-pair exclusion.
- `staff`: fallback queue triggers, the staff-unblock-then-owner-resumes flow, direct book/reschedule/cancel with recorded reason, complete/no-show lifecycle, and emergency handling (advisory-only AI urgency + unconditional static urgent-care guidance).

### Modified Capabilities
<!-- The base PetClinic has no OpenSpec specs today, so there are no existing capabilities to modify; existing owner/pet/vet/visit behavior is preserved and only wrapped by the new `security` capability. -->
- None.

## Actors

- **Owner** — an authenticated pet owner. Acts only on behalf of their own pets and sees only their own data plus veterinarian names and specialties. Uses the guided smart-scheduling flow; the clinic's complete availability calendar is never exposed to them.
- **Staff** — authenticated clinic personnel. Manage clinic settings, veterinarian availability, closures, and the fallback queue; can act on behalf of any owner and pet through `ownerId`-scoped screens; own the appointment lifecycle end to end.

## Scope

**In scope**
- Authenticated owner smart scheduling, the guided single-suggestion flow with holds, and staff management with a fallback queue.
- The full feature is described at the proposal/specs/design level; `tasks.md` is scoped to the **slice-1 walking-skeleton foundation** only.

**Out of scope (for now)**
- Owner self-registration and password recovery, exposing the full calendar to owners, external notifications, multiple clinics, and waitlists.
- The `notification` package remains empty (notifications are out of feature scope).
- Writing feature/production Java, Thymeleaf, or migration code — that happens later via the apply workflow.

## Delivery slicing (walking-skeleton first)

The feature is delivered as sequential slices, thinnest end-to-end vertical first, so each slice is independently reviewable and keeps the app runnable:

1. **Foundation (slice 1)** — Spring Security identity + demo seeding, core entities (`AppointmentRequest`, `Appointment`, `ClinicSettings`, vet availability) via a Flyway V1 baseline, the effective-availability resolver + fixed start-time grid, and **staff booking directly against the grid** (no Timefold, no AI yet). This is the walking skeleton.
2. **Solver + holds** — Timefold single-slot solve, `(vet, start)` holds with unique-constraint + TTL, accept/reject/ask-again.
3. **AI + consent** — the consent gate and Spring AI structured interpretation with validation/clamping/normalization.
4. **Staff fallback + lifecycle** — the fallback queue, staff-assisted resume, reschedule/complete/no-show, and emergency handling.

Because `specs/**` and `design.md` describe the **full** target behavior while `tasks.md` covers **slice 1 only**, this change is intentionally not archived when slice-1 tasks complete; later slices are implemented via follow-up tasks/changes. This deviation is called out in `design.md`.

## Confirmed defaults

Baked into the `calendar` and `appointment` specs:

| Setting | Default |
|---|---|
| Start-time grid granularity | 15 min |
| Hold TTL | 10 min |
| Booking horizon | 60 days ahead |
| Visit duration bounds | 15–120 min (default 30) |
| Day-parts | morning 08:00–12:00, afternoon 12:00–17:00, evening 17:00–20:00 |
| Owner self-cancel window | up to 24h before start |
| Consent record | per-request boolean + timestamp + snapshot of consented text |
| Expired-hold cleanup | scheduled sweep every 5 min + lazy-on-read |
| AI language | English-only for now |
| Clinic time zone | `Europe/Amsterdam` |

## Corrected technical details

The feature brief's "Technical details to consider" are refined as follows (rationale and exact coordinates live in `design.md`):

- **Spring AI `2.0.1`** — this is the correct GA release on Maven Central and targets Boot 4; no milestone repository is required. The AI call is wrapped behind our own port/interface.
- **Ollama** — `gemma4:latest` is valid; the model is set via `spring.ai.ollama.chat.model`. Per-call options (temperature 0, structured-output format, timeout, retries) are applied **only** via `mutate()` from the auto-configured defaults — never by constructing `OllamaChatOptions` from scratch, which would wipe the globally configured model.
- **Flyway** — use `spring-boot-starter-flyway` plus `flyway-database-postgresql` and `flyway-mysql` (Boot-managed version). The current per-vendor `spring.sql.init` scripts are folded into a **V1 baseline**, `spring.sql.init` is disabled, and all three vendors (H2/MySQL/Postgres) are maintained at full parity.
- **Timefold** — the brief's `2.5.0` is treated as provisional; `design.md` pins an actually-released Timefold 2.x and its Boot-4 starter, and notes that 2.x removed the classic `VariableListener`/chained-variable machinery (not needed for a single-slot solve).
- **Build** — `pom.xml` is canonical; the same coordinates are mirrored into `build.gradle` so the dual Maven + Gradle build stays green.

## Impact

- **Affected code (existing, wrapped not rewritten):** `owner/`, `vet/`, and `system/` controllers become secured; the existing `Visit` entity and pet-history UI keep working unchanged. Completed appointments back-fill into the existing `Visit` history.
- **New packages (currently empty scaffolding, now populated):** `security/`, `calendar/`, `appointment/`, `scheduling/interpretation/`, `scheduling/solver/`, `staff/`. `notification/` stays empty.
- **Dependencies:** add Spring Security, Spring AI (Ollama), Timefold, and Flyway; declared in `pom.xml` and mirrored to `build.gradle`.
- **Database:** migration strategy switches from `spring.sql.init` to Flyway (V1 baseline) across H2/MySQL/Postgres; new tables (`app_user`, `clinic_settings`, `vet_weekly_shift`, `vet_availability_exception`, `vet_leave`, `clinic_closure`, `appointment_request`, `rejected_suggestion`, `appointment`, `slot_hold`) with a unique `(vet_id, start_instant)` constraint on holds.
- **Config/infra:** a local Ollama model (size/latency budget, per-call timeouts/retries); a single configured clinic time zone.
- **Non-functional:** concurrency correctness (DB unique constraint + TTL, no long-held locks), deterministic and unit-testable ranking + effective-availability resolver, offline-friendly local LLM, and the safety guarantee that urgent-care guidance can never be hidden by a model miss.
