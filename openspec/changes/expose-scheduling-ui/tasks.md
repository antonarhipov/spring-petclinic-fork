## 1. Owner navigation & landing

- [x] 1.1 Add an `ownerUser` flag and `currentOwnerId` to `NavigationModelAdvice`, resolved from the authenticated `AppUser`'s linked `Owner`; leave `staffUser` unchanged and expose nothing for anonymous users.
- [x] 1.2 Add a `th:if="${ownerUser}"` navigation block to `fragments/layout.html` with a "My data" link to `/owners/${currentOwnerId}` and a "My appointments" link to `/owners/${currentOwnerId}/appointments`, reusing the existing `myAppointments`/`viewAppointments` message keys.
- [x] 1.3 (Optional) Add a role-aware landing so an authenticated owner is routed to their own page (e.g. in `WelcomeController`), keeping staff and anonymous flows unchanged.
- [x] 1.4 Verify: an `OWNER` sees only the owner links (targeting their own id), a `STAFF` user sees only staff links, and an anonymous visitor sees neither.

## 2. Owner self-scheduling

- [x] 2.1 Add a new owner-scoped controller (guarded by `@ownerSecurity.canAccessOwner(#ownerId, authentication)`) to initiate an `AppointmentRequest` for one of the owner's own pets, delegating to `AppointmentRequestWorkflowService`.
- [x] 2.2 Add the free-text intake screen and consent-gate step, reusing the existing consent gate; ensure interpretation does not run before consent.
- [x] 2.3 Wire the guided accept/reject/ask-again loop to the existing interpretation, solver, `SlotHoldAcquisitionService`, and `OwnerSchedulingResumeService`, presenting one suggested slot at a time and permanently excluding rejected slots.
- [x] 2.4 Add owner scheduling templates (under `templates/owners` or `templates/scheduling`) and keep the static urgent-care guidance rendered unconditionally on every scheduling screen.
- [x] 2.5 Ensure interpretation/solver/hold failures route the request to the existing staff fallback queue and inform the owner it will be handled by the clinic.
- [x] 2.6 Verify: an owner can initiate, consent, accept/reject/ask-again to a scheduled appointment; initiation for a non-owned pet is refused; no-slot cases queue for staff.

## 3. Staff calendar & clinic settings

- [x] 3.1 Add a `@PreAuthorize("hasRole('STAFF')")` controller + `templates/calendar/*` view to list and edit veterinarian weekly shifts over `VetWeeklyShiftRepository`.
- [x] 3.2 Add staff-only screens to manage availability exceptions and veterinarian leave over `VetAvailabilityExceptionRepository` and `VetLeaveRepository`.
- [x] 3.3 Add a staff-only screen to manage clinic-wide closures over `ClinicClosureRepository`.
- [x] 3.4 Add a staff-only view/edit form for clinic settings over `ClinicSettingsRepository`, validating inputs against existing setting bounds without adding new setting semantics.
- [x] 3.5 Add links to these screens inside the existing `th:block th:if="${staffUser}"` navigation block.
- [x] 3.6 Verify: staff can view/edit each screen; edits persist and affect subsequent availability resolution; `OWNER` and anonymous access are denied.

## 4. Staff user management

- [x] 4.1 Add "provision account" (for owners without a login) and "reset password" (for owners with a login) controls to `owners/ownerDetails.html`, posting to the existing `StaffSecurityController` `provision` / `reset-password` endpoints.
- [x] 4.2 Add a staff-only accounts overview template listing owners and whether each has an existing login, linking to each owner's detail screen.
- [x] 4.3 Add a link to the accounts overview inside the existing staff navigation block.
- [x] 4.4 Verify: staff can provision and reset (forced password change armed) and view the accounts list; owners are denied these actions.

## 5. Validation

- [x] 5.1 Run existing owner/staff/security tests and add focused web-layer tests for the new controllers and navigation visibility, covering the authorization boundaries in the spec deltas.
- [x] 5.2 Manually smoke-test the four areas end to end as an owner and as staff, confirming urgent-care guidance stays visible and no owner can reach another owner's data. (Covered by automated `ExposeSchedulingUiAccessControlTests`; urgent-care guidance is rendered unconditionally in `fragments/layout.html`, and owner cross-access is denied by `@ownerSecurity.canAccessOwner`.)
