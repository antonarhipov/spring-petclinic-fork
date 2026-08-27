## Context

See `proposal.md` — Why. The `smart-appointment-scheduling` backend is largely implemented, but several capabilities have no UI reachability. Verified current state:

- **Owner data & appointments exist but are unlinked in nav.** `OwnerController` serves `GET /owners/{ownerId}` (`hasRole('STAFF') or @ownerSecurity.canAccessOwner(#ownerId, authentication)`); `OwnerAppointmentController` serves `GET /owners/{ownerId}/appointments`, `POST /owners/{ownerId}/appointments/{appointmentId}/cancel`, and `POST /owners/{ownerId}/appointment-requests/{requestId}/resume`, all owner-scoped. `NavigationModelAdvice` exposes only a `staffUser` flag, so `fragments/layout.html` cannot render an owner-specific link. Message keys `myAppointments`/`viewAppointments` already exist but are unused in nav.
- **Owner self-scheduling initiation is missing.** The guided services exist (`AppointmentRequestWorkflowService`, `scheduling/solver/*`, `scheduling/interpretation/*`, `SlotHoldAcquisitionService`, `OwnerSchedulingResumeService`). Only `resume` is wired for owners; the sole booking-initiation entry point (`/owners/{ownerId}/pets/{petId}/appointments/new`) lives in `StaffBookingController` under `hasRole('STAFF')`.
- **Staff calendar & clinic settings have no web layer.** The `calendar/` package has entities + repositories only (`VetWeeklyShiftRepository`, `VetAvailabilityExceptionRepository`, `VetLeaveRepository`, `ClinicClosureRepository`, `ClinicSettingsRepository`); `templates/calendar` is empty; there is no `@Controller`.
- **Staff user management has endpoints but no screen.** `StaffSecurityController` (`hasRole('STAFF')`) exposes `POST /staff/owners/{ownerId}/provision` and `POST /staff/owners/{ownerId}/reset-password`; `owners/ownerDetails.html` has no button for them and there is no accounts list.

Constraint: this change is a **planning artifact**. It authors OpenSpec documents only. The "how" below describes the implementation the later apply workflow will follow; no application code is written now.

## Goals / Non-Goals

**Goals:**
- Describe an implementation that makes existing owner and staff behavior reachable in the UI, reusing existing controllers, services, and repositories.
- Preserve all current authorization boundaries: owner-scoped endpoints stay owner-scoped, staff screens stay `hasRole('STAFF')`, urgent-care guidance stays anonymous-visible.
- Keep owner self-scheduling on the full guided AI flow (consent → interpret → suggest → accept/reject/ask-again), with the existing staff-queue fallback on failure.

**Non-Goals:**
- No new backend capability, entity, migration, service, solver, or AI logic.
- No owner self-registration, password recovery, owner-facing full calendar, notifications, multi-clinic, or waitlists.
- No change to how availability, holds, interpretation, or solving are computed.

## Decisions

### Owner navigation via `NavigationModelAdvice` flags
Add an owner-aware flag and the current owner id (e.g. `ownerUser` boolean and `currentOwnerId`) to `NavigationModelAdvice`, resolved from the authenticated `AppUser`'s linked `Owner`. `fragments/layout.html` gains a `th:if="${ownerUser}"` block linking to `/owners/${currentOwnerId}` ("My data") and `/owners/${currentOwnerId}/appointments` ("My appointments"), reusing existing `myAppointments`/`viewAppointments` keys.
- **Why:** the target endpoints already enforce `@ownerSecurity.canAccessOwner`, so exposure is pure nav wiring; resolving the id server-side avoids trusting a client-supplied owner id.
- **Alternative considered:** compute links in each template from the principal — rejected as duplicative and error-prone versus a single model-advice source.

### Owner-aware landing (optional, role-aware)
Optionally route an authenticated owner from the generic welcome to their own page (e.g. via a role-aware redirect in `WelcomeController` or `defaultSuccessUrl` handling), while staff and anonymous flows are unchanged.
- **Why:** improves reachability without altering staff/anonymous behavior.
- **Alternative considered:** a global `defaultSuccessUrl` — rejected because it would also move staff/anonymous users.

### Owner self-scheduling: new owner-facing controller reusing existing services
Add a new owner-scoped controller (guarded by `@ownerSecurity.canAccessOwner`) that lets an owner initiate an `AppointmentRequest` for one of their own pets and drive the guided loop, delegating to the same `AppointmentRequestWorkflowService`, interpretation, solver, `SlotHoldAcquisitionService`, and `OwnerSchedulingResumeService` used today. New Thymeleaf templates (under `templates/owners` or `templates/scheduling`) render the free-text intake, consent gate, and the one-slot-at-a-time accept/reject/ask-again screens. Urgent-care guidance stays rendered unconditionally.
- **Why:** the only true gap on the owner side is an initiation/accept-reject entry point; the staff booking entry point cannot be reused directly because it is `hasRole('STAFF')`.
- **Alternative considered:** relax `StaffBookingController` guards to also allow owners — rejected because it mixes staff and owner authorization on one endpoint and risks over-exposure.

### Staff calendar & clinic settings: new staff-only controllers over existing repositories
Add `@PreAuthorize("hasRole('STAFF')")` controllers plus `templates/calendar/*` views for viewing/editing weekly shifts, availability exceptions, leave, clinic closures, and clinic settings, each backed by the corresponding existing repository. No changes to availability computation.
- **Why:** the domain and repositories exist; only a thin web/CRUD layer is missing.
- **Alternative considered:** a single mega-screen for all calendar data — rejected in favor of focused screens matching each repository for reviewability.

### Staff user management: wire existing endpoints + accounts list
Add "provision account" / "reset password" controls to `owners/ownerDetails.html` posting to the existing `StaffSecurityController` endpoints, and a simple staff-only accounts list template showing owners and whether each has a login.
- **Why:** endpoints already exist and behave correctly; only the screen/buttons are missing.
- **Alternative considered:** a new dedicated user CRUD subsystem — rejected as out of scope; provisioning/reset already cover the need.

## Risks / Trade-offs

- **Owner self-scheduling depends on the parent feature's AI/solver slices being usable** → when interpretation/solving yields no bookable slot, the request routes to the existing staff fallback queue (unchanged behavior), so the owner never hits a dead-end.
- **Owner-aware landing could disrupt staff/anonymous flows** → keep the redirect role-aware and optional; do not change the global default success URL.
- **Spec delta placement could conflict with the parent change's capability paths** → this change reuses the same capability paths (`security`, `appointment`, `calendar`, `staff`) and only ADDs UI-reachability requirements, so it composes with the parent without modifying already-correct backend requirements.
- **Nav exposes owner id in URLs** → acceptable because target endpoints independently enforce `@ownerSecurity.canAccessOwner`; the id is resolved server-side from the principal, not trusted from the client.

## Migration Plan

No data migration. Delivery is sliced by the four gap areas (owner nav & landing → owner self-scheduling → staff calendar & settings → staff user management), each independently shippable behind existing authorization. Rollback is removal of the added nav block/controllers/templates; no schema or service changes to revert.
