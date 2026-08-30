# HTTP and Browser UI Contract

## General Conventions

- The application is server-rendered Spring MVC with Thymeleaf. JSON is used only for owner-safe status polling and session status.
- Public, owner, and staff pages use separate layouts with shared branding only.
- `GET` and `HEAD` are read-only. Every business mutation uses `POST` with CSRF protection.
- Every mutation form contains an issued `commandId`, `expectedVersion`, and the current workflow/queue version when applicable.
- A successful mutation uses Post/Redirect/Get to the canonical detail page. The redirect must not cause a browser to replay a POST.
- Protected responses, temporary-password pages, status responses, and forms carrying command tokens use `Cache-Control: no-store`.
- Owner handlers resolve `ownerId` from the authenticated principal. No `/owner/**` route accepts an owner identifier.
- A foreign owner resource returns 404. An authenticated user requesting the other role's route family receives 403.
- Browser navigation that requires authentication redirects to `/login` and may save only a safe GET/HEAD destination. JSON polling returns 401 after expiry. POST bodies are never cached or replayed after login.
- Expected validation errors render the form with its safe submitted values. Stale, expired, lifecycle-invalid, or concurrent commands return the canonical current state and a safe next action without partial writes.

## Public and Shared Routes

| Method | Path | Access | Purpose |
|---|---|---|---|
| GET | `/` | Public | Non-operational landing page |
| GET | `/login` | Public | Login form |
| POST | `/login` | Public + login CSRF/session protection | Form authentication |
| GET | `/resources/**` | Public | Required static assets |
| ANY | `/error` | Internal error dispatch | Safe error rendering; not an operational page |
| POST | `/logout` | Authenticated + CSRF | End current session |
| GET | `/account/password-change` | Authenticated | Required/voluntary password form |
| POST | `/account/password-change` | Authenticated + command | Change password and rotate session ID |
| GET | `/session/status` | Authenticated, non-interactive | Return server time and inactivity deadline |
| POST | `/session/extend` | Authenticated + CSRF | Explicitly record interactive activity |

A principal with `passwordChangeRequired=true` may access only password change, logout, session status, static assets, and error dispatch.

## Owner Routes

All routes require `ROLE_OWNER` and use owner-scoped service queries.

| Method | Path | Purpose |
|---|---|---|
| GET | `/owner/dashboard` | Action-required requests, active requests, upcoming appointments, and recent history |
| GET | `/owner/profile` | Show own profile |
| POST | `/owner/profile` | Update permitted own contact fields |
| GET | `/owner/requests/new` | Select an owned pet; optional `petId` preselection |
| POST | `/owner/requests` | Submit one request, text revision, consent, and first job/queue action |
| GET | `/owner/requests/{requestId}` | Canonical request page for its current state |
| GET | `/owner/requests/{requestId}/status` | Non-interactive status JSON; see `request-status.schema.json` |
| GET | `/owner/requests/{requestId}/interpretation` | Review original prose and validated structured details |
| POST | `/owner/requests/{requestId}/interpretation/edit` | Create a workflow revision without invoking AI |
| POST | `/owner/requests/{requestId}/interpretation/confirm` | Confirm the current workflow revision |
| GET | `/owner/requests/{requestId}/text-revision` | Show original-text revision form |
| POST | `/owner/requests/{requestId}/text-revision` | Add exact text revision and revision-specific consent |
| POST | `/owner/requests/{requestId}/match` | Explicit **Find another time** command |
| GET | `/owner/requests/{requestId}/offers/{offerId}` | Show one scoped held offer |
| POST | `/owner/requests/{requestId}/offers/{offerId}/accept` | Atomically confirm active hold |
| GET | `/owner/requests/{requestId}/offers/{offerId}/reject` | Rejection confirmation page |
| POST | `/owner/requests/{requestId}/offers/{offerId}/reject` | Reject, release, and exclude the slot |
| GET | `/owner/requests/{requestId}/withdraw` | Withdrawal confirmation page |
| POST | `/owner/requests/{requestId}/withdraw` | Close a pre-confirmation request and release work |
| GET | `/owner/appointments/{appointmentId}` | Show own appointment |
| GET | `/owner/appointments/{appointmentId}/cancel` | Cancellation confirmation page |
| POST | `/owner/appointments/{appointmentId}/cancel` | Cancel own appointment before start |
| GET | `/owner/history` | Paginated complete request/appointment/visit history |

The owner request page maps the canonical aggregate state to exactly one primary action. It never contains clinic-wide availability, staff notes, audit reasons, raw AI output, solver diagnostics, or detailed clinical notes.

## Staff Queue and Request Routes

All routes require `ROLE_STAFF`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/staff/queue` | Sorted/filterable fallback queue |
| GET | `/staff/queue/{queueItemId}` | Queue detail and current request revision |
| POST | `/staff/queue/{queueItemId}/claim` | Claim an item for editing |
| POST | `/staff/queue/{queueItemId}/unclaim` | Release assignment with reason |
| POST | `/staff/queue/{queueItemId}/reassign` | Assign another staff identity with reason |
| POST | `/staff/queue/{queueItemId}/contact-attempts` | Record external contact result |
| POST | `/staff/queue/{queueItemId}/interpretation` | Save a manual structured interpretation |
| POST | `/staff/queue/{queueItemId}/request-owner-confirmation` | Move to owner interpretation confirmation |
| GET | `/staff/queue/{queueItemId}/emergency-clearance` | Review emergency-clearance form |
| POST | `/staff/queue/{queueItemId}/emergency-clearance` | Choose replacement urgency and record reason |
| GET | `/staff/queue/{queueItemId}/offer` | Select/review a compliant slot after contact |
| POST | `/staff/queue/{queueItemId}/offer` | Create a staff-assisted hold |
| GET | `/staff/queue/{queueItemId}/direct-book` | Select and review a compliant direct booking |
| POST | `/staff/queue/{queueItemId}/direct-book` | Create the agreed appointment |
| GET | `/staff/queue/{queueItemId}/close` | Explicit closure confirmation |
| POST | `/staff/queue/{queueItemId}/close` | Close with reason |

Queue mutations require the current staff member to be the assignee except claim, reassign, and unclaim. Every save includes the queue version and current workflow revision. Stale submissions change nothing.

## Staff Calendar and Policy Routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/staff/calendar` | Week calendar with veterinarian filter |
| GET | `/staff/calendar/table` | Tabular alternative |
| GET | `/staff/clinic-policy` | Policy/contact/urgent-guidance form |
| POST | `/staff/clinic-policy` | Validate and update policy |
| GET | `/staff/availability/shifts` | List recurring shifts |
| GET | `/staff/availability/shifts/new` | Shift form |
| POST | `/staff/availability/shifts` | Create shift |
| GET | `/staff/availability/shifts/{shiftId}/edit` | Edit shift form |
| POST | `/staff/availability/shifts/{shiftId}` | Update shift |
| POST | `/staff/availability/shifts/{shiftId}/delete` | Delete shift after conflict review |
| GET | `/staff/availability/exceptions/{vetId}/{date}` | Complete replacement schedule for one date |
| POST | `/staff/availability/exceptions/{vetId}/{date}` | Replace that date's full schedule |
| POST | `/staff/availability/exceptions/{vetId}/{date}/delete` | Restore recurring schedule after conflict review |
| GET/POST | `/staff/availability/leave/**` | Create, edit, or delete veterinarian leave |
| GET/POST | `/staff/availability/closures/**` | Create, edit, or delete clinic closures |

All availability POST operations run through the atomic calendar service. A blocked operation renders all conflicting active holds and appointments and saves nothing.

## Staff Appointment and Administration Routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/staff/appointments/{appointmentId}` | Appointment detail and audit history |
| GET/POST | `/staff/appointments/{appointmentId}/reschedule` | Review then commit agreed reschedule |
| GET/POST | `/staff/appointments/{appointmentId}/cancel` | Review then commit staff cancellation |
| GET/POST | `/staff/appointments/{appointmentId}/complete` | Completion form and confirmation |
| GET/POST | `/staff/appointments/{appointmentId}/no-show` | No-show form and confirmation |
| GET/POST | `/staff/appointments/{appointmentId}/correct-outcome` | Preview then commit audited correction |
| GET | `/staff/legacy-visits/reconciliation` | Future-dated legacy visits needing review |
| GET/POST | `/staff/legacy-visits/{visitId}/create-appointment` | Create linked appointment with fresh agreement when evidence is absent |
| GET/POST | `/staff/owners/**` | Existing owner and pet administration under staff authorization |
| GET/POST | `/staff/owners/{ownerId}/account/**` | Provision/reset an owner account |
| GET | `/staff/veterinarians` | Staff catalog view |

The old anonymous `/owners/**`, `/vets`, `/vets.html`, and direct future-visit creation routes are retired or redirected only to an authorized canonical GET. They are never left as parallel unsecured behavior.

## Form Command Contract

Every mutation DTO contains:

| Field | Required | Meaning |
|---|---|---|
| `commandId` | Yes | Previously issued UUID scoped to actor/action/target |
| `expectedVersion` | For mutable aggregate | Version rendered with form |
| `workflowRevisionId` | Request/queue actions | Exact immutable revision used by the actor |
| `queueVersion` | Queue actions | Prevents stale staff saves |
| Domain fields | Per action | Server validated; never bind an entity directly |

The service hashes canonical domain fields and compares actor, action, target, and hash with the issued command record. A completed duplicate returns the stored canonical result. Reusing a token with modified input is rejected.

## Response Outcomes

| Condition | Browser outcome | JSON outcome |
|---|---|---|
| Successful mutation | Redirect to canonical GET | Not used for business mutations |
| Field validation | 200 with bound form and field/global errors | 400 if applicable |
| Foreign owner object | 404 safe page | 404 |
| Wrong role | 403 safe page | 403 |
| Unauthenticated/expired session | Redirect to login for safe GET; mutation is not replayed | 401 |
| CSRF failure | 403 safe page | 403 |
| Stale version/revision | 409 current-state page with next action | 409 |
| Expired/unavailable offer | 409 canonical request/offer page | 409 |
| Duplicate completed command | Redirect to stored canonical result | Stored canonical status/body |
| Technical interpretation/matching failure | Persist fallback, then redirect/render staff-handling state | Current status response |

