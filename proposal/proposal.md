# Smart Appointment Scheduling

Add smart appointment scheduling to the PetClinic application.

Pet owners should be able to request an appointment for their pet using a free-form text description of their availability.
Instead of selecting a specific date and time, the owner can describe when they would prefer to come and when they cannot come.

For example:

> "I'd prefer Tuesday or Thursday afternoon. I can't come on Wednesday, and mornings before 10 don't work for me. Friday morning would also be possible if necessary."

The application should interpret the owner's request and translate it into structured scheduling information that can be processed by the scheduling system.

The scheduling system should use Timefold to find a suitable appointment slot, taking into account:

- the owner's availability and preferences;
- veterinarian availability;
- existing appointments;
- other relevant scheduling constraints.

## Guided appointment selection

The application should not expose the complete calendar of available veterinarian time slots to the pet owner.

Showing many available slots at once can cause multiple owners to compete for the same desirable slots while those slots still appear available. Instead, appointment selection should be a guided process.

For each request, the system should suggest **one suitable appointment slot at a time**.

The pet owner can:

- accept the suggested slot and proceed with the appointment;
- reject the suggestion and ask for another option.

When the owner asks for another option, the system should suggest the next suitable slot based on the original request and previously rejected suggestions.

The goal is to allow pet owners to express their availability naturally while letting the scheduling system guide them toward an appropriate appointment without exposing the clinic's entire availability calendar.

## Actors and access

Two kinds of authenticated users interact with scheduling: pet **owners** and clinic **staff**.
Owners use the smart scheduling flow for their own pets, while staff manage the clinic and step in when the automated flow cannot help.

- **Owner** — acts only on behalf of their own pets and sees only their own data plus veterinarian names and specialties; the clinic's complete availability calendar is never exposed to them.
- **Staff** — manage the clinic calendar, veterinarian schedules, clinic settings, and the fallback queue, and can act on behalf of any owner and pet.
- **Account provisioning** — owner accounts are created by staff rather than through self-registration, and a newly provisioned owner changes their password on first login. The staff can also reset passwords for existing owners.

## Understanding the request (AI interpretation and consent)

The owner describes their need in free text — typically the reason for the visit and when they are (and are not) available — and the application uses AI to turn that into a structured interpretation the scheduling system can act on.

- **Structured interpretation** — from the free text the system derives things like the likely visit length, whether general or specialty care is needed, preferred/allowed/excluded time windows, an optional preferred veterinarian, and any sign of urgency.
- **Explicit consent** — the owner must knowingly agree before their text is sent to AI for interpretation; if they decline, the request is routed to staff instead.
- **Owner confirmation** — before anything is scheduled, the owner reviews and confirms the interpretation; revising the free text afterwards requires fresh consent and a fresh interpretation.

## Guided suggestions and holds

This extends the guided one-at-a-time selection above with the notion of a short hold, so that a slot the owner is currently considering cannot be taken by someone else.

- **Held while offered** — each suggested slot is briefly held for the owner while they decide, preventing two owners from being offered the same slot at once.
- **Accept or reject** — accepting confirms the appointment; rejecting releases the hold and permanently excludes that exact veterinarian-and-time from further suggestions for this request.
- **Ask again** — requesting another option re-runs matching against the confirmed request, the current state of the calendar, and all previously rejected suggestions.

## Staff fallback and emergencies

The automated flow should never leave an owner stuck. When it cannot proceed on its own, the request is handed to staff rather than reported as a dead end.

- **When staff step in** — if AI interpretation or the solver is unavailable, the owner declines consent, or no veterinarian has the required specialty, the request enters a staff queue instead of returning a bare "no availability" result.
- **Staff-assisted scheduling** — staff verify or complete the interpretation and then offer the owner a suitable slot to accept or reject, mirroring the guided flow.
- **Emergencies** — suspected emergencies detected from the request are prioritized, and clear urgent-care guidance is always visible on the request form so owners know what to do right away.

## Staff calendar and appointment management

Staff work directly against the clinic's full calendar and are responsible for appointments through their whole lifecycle.

- **Direct scheduling** — staff can book, reschedule, or cancel appointments directly, with a reason recorded for staff-initiated changes.
- **Lifecycle** — after an appointment, staff mark it completed (which records a visit in the pet's history) or as a no-show.
- **Owner self-service** — owners can view and cancel their own upcoming appointments, but never see or act on other owners' appointments.

## Clinic configuration and veterinarian availability

Scheduling behavior is driven by clinic configuration and per-veterinarian availability that staff can adjust, so the system reflects how the clinic actually operates.

- **Clinic settings** — staff configure clinic-wide scheduling parameters (such as visit-duration bounds, how far ahead appointments can be booked, how long slots are held, and named parts of the day) and sensible defaults apply out of the box.
- **Veterinarian availability** — each veterinarian has a recurring weekly schedule that supports split shifts, along with date-specific exceptions and leave; clinic-wide closures apply to everyone.
- **Single time zone** — the clinic operates in one configured time zone, which anchors how availability and appointments are interpreted. Default time zone is Europe/Amsterdam

## Scope boundaries (high-level)

- **In scope** — authenticated owner smart scheduling, the guided single-suggestion flow, and staff management with fallback.
- **Out of scope (for now)** — owner self-registration and password recovery, exposing the full calendar to owners, external notifications, multiple clinics, and waitlists.

## Technical details to consider

- Flyway for database migrations
- Spring AI 2.0.1 for LLM integration
- Ollama as LLM backend with gemma4:latest model
- Timefold 2.5.0 for as a solver for schedules
