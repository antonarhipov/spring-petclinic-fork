## Why

The `smart-appointment-scheduling` feature is largely implemented in the domain, service, and repository layers, but a significant part of it is **unreachable through the UI**. Owners have no navigation to their own data and no way to start a scheduling request, while staff have no screens at all for user management, the veterinarian calendar, or clinic settings — even though the controllers, services, and repositories already exist. This change closes that exposure gap so the already-built backend becomes usable end to end.

> This is a **planning artifact only**. It authors OpenSpec documents (proposal, spec deltas, design, tasks); no Java/Thymeleaf/migration code is written here. Implementation happens later via the apply workflow.

## What Changes

- **Owner navigation & landing** — expose owner-visible menu links ("My data" → `/owners/{id}`, "My appointments" → `/owners/{id}/appointments`) and an owner-aware landing, by adding an `ownerUser`/`currentOwnerId` flag to `NavigationModelAdvice` and a `th:if="${ownerUser}"` block in `fragments/layout.html`, reusing the existing `myAppointments`/`viewAppointments` message keys.
- **Owner self-scheduling** — add an owner-facing entry point that lets an owner **initiate** an `AppointmentRequest` and drive the guided **accept/reject/ask-again** loop through the **full guided AI flow** (consent gate → AI interpretation → Timefold single-slot suggestion), reusing existing services. Today only `resume` is wired and the only booking entry point is guarded by `hasRole('STAFF')`.
- **Staff calendar & clinic settings** — expose staff screens over the already-built `calendar` repositories (`VetWeeklyShiftRepository`, `VetAvailabilityExceptionRepository`, `VetLeaveRepository`, `ClinicClosureRepository`) and `ClinicSettingsRepository`, guarded by `hasRole('STAFF')`.
- **Staff user management** — expose a users/accounts screen that wires the existing `provision` / `reset-password` endpoints (`StaffSecurityController`) into `ownerDetails.html` plus a simple accounts list.

No new backend capabilities are introduced; every change surfaces existing behavior in the UI while preserving current security guarantees (owner scoping, staff-only management, anonymous urgent-care guidance).

## Capabilities

### New Capabilities

<!-- The parent change `smart-appointment-scheduling` is not yet archived, so
     `openspec/specs/` is empty and these capability paths do not yet exist in the
     shared spec store. They are therefore declared here as new spec files, using the
     same capability paths as the parent change so the two align once archived. Each
     file adds only UI-reachability requirements over already-implemented behavior. -->

- `security`: Owner-scoped navigation is rendered only for authenticated owners and links to owner-guarded endpoints; owner self-scheduling entry is reachable by owners; staff management screens remain guarded by `hasRole('STAFF')`.
- `appointment`: An owner can initiate an `AppointmentRequest` from the UI and drive the guided accept/reject/ask-again suggestion loop reusing existing services, with unchanged fallback-queue behavior on validation/AI/solver failure.
- `calendar`: Staff can view and edit veterinarian weekly shifts, availability exceptions, leave, clinic closures, and clinic settings through dedicated staff-only screens over existing repositories.
- `staff`: Staff can manage user accounts (provision an owner login, reset a password) from a user-management screen that wires the existing security endpoints, plus a simple accounts list.

### Modified Capabilities

<!-- None: the shared spec store is empty (parent change not archived), so there are no
     existing requirements to modify. All deltas above are additive UI-exposure
     requirements over already-implemented behavior. -->

## Impact

- **Affected code (later, at apply time):** `NavigationModelAdvice`, `fragments/layout.html`, optional owner-aware landing in `WelcomeController`; a new owner self-scheduling controller + templates; new staff calendar/clinic-settings controllers + `templates/calendar/*`; user-management wiring in `owners/ownerDetails.html` and a new accounts list template.
- **Reused (unchanged) backend:** `OwnerController`, `OwnerAppointmentController`, `AppointmentRequestWorkflowService`, `OwnerSchedulingResumeService`, `SlotHoldAcquisitionService`, the `scheduling/solver` and `scheduling/interpretation` services, the four `calendar` availability repositories, `ClinicSettingsRepository`, and `StaffSecurityController`.
- **Dependencies:** Owner self-scheduling assumes the parent feature's AI/solver slices are usable; when they are not, requests fall back to the existing staff queue (unchanged behavior).
- **Security:** No relaxation of existing guarantees — owner nav and self-scheduling reach only owner-guarded endpoints, staff screens stay `hasRole('STAFF')`, and unconditional urgent-care guidance is preserved.
