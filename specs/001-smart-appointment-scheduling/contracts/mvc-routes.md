# Desktop MVC Route Contract

## Global rules

- Only `GET /`, `GET /login`, the Spring Security login POST, and static assets are public. All other routes require an authenticated session.
- Existing `/owners/**`, `/pets/**`, and `/vets/**` management routes become `STAFF` only. Owner self-service uses `/owner/**` and derives the owner ID from the authenticated account.
- Owner requests for another owner's object return 404 without confirming its existence. Authenticated owners requesting a staff route receive 403. Unauthenticated requests are redirected to login for HTML and receive 401 for JSON.
- Every state-changing route is POST, is CSRF-protected, and applies Post/Redirect/Get after success. GET routes do not create jobs, expire holds, or otherwise mutate domain state.
- Every mutation includes the current aggregate `expectedVersion`. A mismatch returns a 409 HTML page with the current status, changed fields, safe submitted values, and a canonical refresh/continue link. No stale command is automatically replayed.
- Sensitive pages and all polling responses use `Cache-Control: no-store`.
- Temporary-password users may access only password change and logout until the change succeeds.

## Account routes

| Method | Route | Role | Contract |
|---|---|---|---|
| GET | `/login` | Public | Render form login; never disclose whether a username exists |
| GET | `/account/password/change` | Authenticated | Render required/voluntary password-change form |
| POST | `/account/password/change` | Authenticated | Validate current/temporary credential and new password, rotate session ID, clear change-required flag |
| POST | `/logout` | Authenticated | Invalidate current session |
| POST | `/staff/owners/{ownerId}/account` | STAFF | Provision one owner account and display the generated one-time password once |
| POST | `/staff/owners/{ownerId}/account/reset` | STAFF | Replace with a seven-day one-time password, invalidate every owner session, and display password once |

## Owner pages and actions

| Method | Route | Required state | Result |
|---|---|---|---|
| GET | `/owner/dashboard` | Any | Own pets, upcoming appointments, active request status, and one Resume link to its canonical stage |
| GET | `/owner/pets/{petId}/scheduling-requests/new` | No active request for owned pet | Request form, upcoming appointments, and persistent urgent-care guidance |
| POST | `/owner/scheduling-requests` | Owned pet, no active request | Persist request/text revision and redirect to consent |
| GET | `/owner/scheduling-requests/{id}/consent` | `AWAITING_CONSENT` | Dedicated data-use summary; agreement unchecked |
| POST | `/owner/scheduling-requests/{id}/consent/interpret` | `AWAITING_CONSENT` | Record consent, create one LLM operation, redirect to its processing page |
| POST | `/owner/scheduling-requests/{id}/consent/manual` | `AWAITING_CONSENT` | Record decline, create/retain queue item, redirect to staff-handling status |
| GET | `/owner/scheduling-requests/{id}/processing/{operationId}` | Matching active operation | Plain status with no percentage; JS polls the operation-specific JSON URL |
| GET | `/owner/scheduling-requests/{id}/interpretation` | `INTERPRETATION_REVIEW` | Full interpreted values, resolved dates, top issue summary, and text-plus-icon inline issues |
| POST | `/owner/scheduling-requests/{id}/interpretation` | `INTERPRETATION_REVIEW` | Save only owner-editable fields as a new draft revision; no LLM call |
| POST | `/owner/scheduling-requests/{id}/interpretation/confirm` | Valid draft review | Confirm revision and redirect to ready-for-suggestion page |
| GET | `/owner/scheduling-requests/{id}/revise` | Any pre-confirmation state | Render source-text revision form; disclose that new consent is required |
| POST | `/owner/scheduling-requests/{id}/revise` | Any pre-confirmation state | Release active hold if present, append text revision, redirect to consent |
| GET | `/owner/scheduling-requests/{id}/suggestion` | `READY_FOR_SUGGESTION` | Explain one-at-a-time suggestions and provide Request suggestion action |
| POST | `/owner/scheduling-requests/{id}/suggestions` | `READY_FOR_SUGGESTION` | Create one preferred Timefold operation and redirect to processing |
| GET | `/owner/scheduling-requests/{id}/fallback-choice` | `AWAITING_FALLBACK_CHOICE` | Report no preferred match; show Find an alternative and Forward to staff |
| POST | `/owner/scheduling-requests/{id}/suggestions/alternative` | `AWAITING_FALLBACK_CHOICE` | Create one fallback-authorized Timefold operation and redirect to processing |
| POST | `/owner/scheduling-requests/{id}/forward-to-staff` | Any unresolved pre-confirmation state | Create/retain queue item and redirect to staff-handling status |
| GET | `/owner/scheduling-requests/{id}/offers/{offerId}` | Active or historical own offer | Active hold/countdown or immutable offer outcome; never calendar alternatives |
| POST | `/owner/scheduling-requests/{id}/offers/{offerId}/accept` | Active unexpired hold | Atomically create appointment or render unavailable outcome; never partial success |
| POST | `/owner/scheduling-requests/{id}/offers/{offerId}/reject` | Active hold | Final inline-confirmation POST releases hold, records optional staff-only reason, excludes slot |
| POST | `/owner/scheduling-requests/{id}/withdraw` | Any pre-confirmation state | Final dialog-confirmation POST releases work/hold and closes request permanently |
| GET | `/owner/scheduling-requests/{id}/status` | Any own request | Canonical plain-language current status and actions |
| GET | `/owner/appointments` | Any | Own upcoming and historical appointments |
| GET | `/owner/appointments/{appointmentId}` | Own appointment | Owner-safe detail only |
| POST | `/owner/appointments/{appointmentId}/cancel` | Before start, own confirmed appointment | Final dialog-confirmation POST; optional reason; original request stays closed |
| GET | `/owner/history` | Any | Own request, offer, appointment, and completed visit projections only |

The server-generated Resume URL is state-derived and never accepted from a submitted `nextUrl`.

## Staff workspaces

The Queue, Calendar, Availability, and Settings templates share persistent staff navigation.

### Queue

| Method | Route | Contract |
|---|---|---|
| GET | `/staff/queue` | Emergency items first, then oldest; all open items visible |
| GET | `/staff/queue/{itemId}` | Full request detail, staff notes, provenance, and allowed actions |
| POST | `/staff/queue/{itemId}/claim` | Unclaimed `NEW` → current staff `IN_REVIEW` |
| POST | `/staff/queue/{itemId}/unclaim` | Required audit reason; `IN_REVIEW` → `NEW` |
| POST | `/staff/queue/{itemId}/reassign` | Required target staff and audit reason; stays `IN_REVIEW` |
| POST | `/staff/queue/{itemId}/notes` | Append a concise staff-only note |
| POST | `/staff/queue/{itemId}/interpretation` | Save manual/corrected structured interpretation; never send declined text to LLM |
| POST | `/staff/queue/{itemId}/interpretation/verify` | Confirm staff-verified request data |
| POST | `/staff/queue/{itemId}/offers` | Hold one exact staff-selected eligible slot for owner acceptance |
| POST | `/staff/queue/{itemId}/appointments` | Direct booking after evidence and ordinary availability/conflict validation |
| POST | `/staff/queue/{itemId}/close` | Required resolution and reason; no appointment created |

A future staff action labelled “suggest automatically” must invoke the same Timefold boundary as an owner request. The exact-slot manual offer route does not invoke Timefold.

### Calendar and appointment lifecycle

| Method | Route | Contract |
|---|---|---|
| GET | `/staff/calendar?from={date}&to={date}` | Full staff calendar in clinic zone; bounded date range |
| GET | `/staff/appointments/{id}` | Full appointment, lifecycle, reservation, and audit context |
| POST | `/staff/appointments` | Direct eligible booking with required reason/evidence |
| POST | `/staff/appointments/{id}/reschedule` | Preserve appointment ID, atomically replace blocks, record old/new time and reason |
| POST | `/staff/appointments/{id}/cancel` | Required reason category; release blocks |
| POST | `/staff/appointments/{id}/complete` | Allowed only at/after scheduled end; required clinical notes; create exactly one Visit |
| POST | `/staff/appointments/{id}/no-show` | Allowed only at/after end; required no-show category; no Visit |
| POST | `/staff/appointments/{id}/correct` | Reconcile erroneous terminal state and Visit through audited correction only |

### Availability and settings

| Method | Route | Contract |
|---|---|---|
| GET | `/staff/availability` | Veterinarian list and selected schedule editor |
| POST | `/staff/availability/shifts` | Create/update same-day grid-aligned recurring interval |
| POST | `/staff/availability/shifts/{id}/delete` | Delete only after appointment/hold conflict validation |
| POST | `/staff/availability/exceptions` | Replace one veterinarian date with zero or more valid intervals |
| POST | `/staff/availability/leave` | Add full local-date range |
| POST | `/staff/availability/{ruleType}/{id}/delete` | Audited removal after conflict validation |
| GET | `/staff/settings` | Clinic policy, hours, durations, periods, guidance, closures, emergency terms |
| POST | `/staff/settings/policy` | Update bounded horizon/hold/guidance; zone immutable after first request/appointment |
| POST | `/staff/settings/durations` | Replace nonempty allowed duration set |
| POST | `/staff/settings/hours` | Replace valid non-overlapping local intervals |
| POST | `/staff/settings/named-periods` | Replace non-overlapping same-day periods |
| POST | `/staff/settings/closures` | Add full local-date/range closure after conflict validation |
| POST | `/staff/settings/emergency-terms` | Replace versioned raise-only term set |
| GET | `/staff/audit` | Filtered, paginated durable audit history |

## Response behavior

| Condition | HTML behavior | JSON behavior |
|---|---|---|
| Unauthenticated | 302 to `/login` | 401 |
| Authenticated wrong role | 403 page | 403 Problem Detail |
| Cross-owner or absent object | 404 page | 404 Problem Detail |
| Field validation failure | 200 form with issue summary and inline errors | 400 Problem Detail if JSON is ever added |
| Stale `expectedVersion` | 409 current-state page with safe input preserved | 409 Problem Detail with canonical current URL |
| Invalid lifecycle action | 409 current-state page and allowed next action | 409 Problem Detail |
| Successful mutation | 303 redirect to canonical GET | Not applicable to current form-only mutations |

No owner response includes queue assignment, staff notes, raw/model output, error class, prompt, input snapshot, solver score, constraint explanation, or undisclosed slots.
