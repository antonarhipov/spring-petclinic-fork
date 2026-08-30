# Form and Interaction Contract

## Shared form behavior

- Forms submit `application/x-www-form-urlencoded` UTF-8 data and a Spring Security CSRF token.
- Mutations include hidden `expectedVersion` plus only identifiers needed to address the aggregate. Actor/owner/role values always come from the authenticated session.
- Server-side allowlists prevent mass assignment. In particular, owners cannot post care type, specialty, urgency, queue state, scores, assignment, status, expiry, veterinarian eligibility, or another owner ID.
- Validation failures render a top summary linked to every affected field and a text-plus-icon inline message. Color may supplement but never replace the message. No formal accessibility conformance target is added for this POC.
- Dates and times display with date, time, and clinic-zone abbreviation/offset. Server-side instants are authoritative across daylight-saving transitions.
- Confirmation dialogs for cancellation and withdrawal show consequences and submit only after an explicit final action. Offer rejection uses an inline first step and a distinct final submit. None has undo.
- On a 409 stale response, safe text/select/date inputs are echoed in a “Your unsaved changes” section beside current persisted values. Passwords, one-time credentials, hidden authorization evidence, and obsolete state/status selections are never echoed.

## Owner request capture

### Create request

| Field | Rules |
|---|---|
| `petId` | Required integer; selected pet must belong to current owner; becomes immutable |
| `description` | Required English plain text, trimmed length 10–2,000; HTML/markup is treated as text and escaped on render |

The page displays existing upcoming appointments for the pet and persistent clinic-maintained urgent-care guidance before submit. A unique active-pet guard handles the concurrent duplicate-request race.

### Consent

`/consent/interpret` requires hidden `textRevisionId`, `expectedVersion`, and checkbox `agree=true`. The checkbox is never preselected. `/consent/manual` has a separate submit button and does not accept or infer `agree`.

The page states what exact request text is sent, why, and which source/output/version/timing records are retained. Editing the text first creates a new text revision and returns to an unchecked consent page.

## Interpretation review

Owner-editable fields:

| Field | Rules |
|---|---|
| `visitReason` | Required, 1–500 characters |
| `durationMinutes` | Required member of clinic-configured duration choices |
| `preferredVeterinarianId` | Optional existing eligible veterinarian; always a preference for owner edits |
| `allowedWindows[]` | At least one concrete start/end pair; start before end |
| `preferredWindows[]` | Optional concrete windows contained in or intersecting an allowed window according to deterministic validator |
| `excludedWindows[]` | Optional concrete windows; may not eliminate every allowed interval without routing to staff |

Each window uses explicit clinic-local date, start time, end date/time, and a server-generated offset preview. The server re-resolves and validates submitted local values; it never trusts a hidden UTC instant.

Read-only clinical fields are `careType`, `specialty`, and `urgency`. If any is uncertain, invalid, or contradictory, owner confirmation remains disabled and Forward to staff is available. Resolved date phrases appear beside concrete results. Saving an editable field creates a new draft request revision and does not invoke the LLM. Confirmation posts the revision and request versions.

## Suggestion, fallback, and offer

- Request suggestion submits only request/revision/version and creates an idempotent preferred Timefold operation.
- Find an alternative is available only after `NO_PREFERRED_MATCH`; it creates a fallback-authorized Timefold operation. Forward to staff is a separate POST.
- The offer page renders exactly one slot, veterinarian name/specialty, public explanation copy, exact clinic-zone expiry, server time, and countdown seed. It renders no calendar or alternate slots.
- The countdown warns immediately when remaining time is at most two minutes and disables the visual Accept control at zero, but the final server check always decides.
- Accept posts request, offer, and hold versions. No submitted slot/time is accepted.
- Reject final submit posts request/offer/hold versions and optional `reason` of at most 500 characters. The reason is staff-only and never influences matching.

## Revise, withdraw, and cancel

- Source revision accepts the same description constraints as request creation, releases an active hold, supersedes draft interpretation, and requires fresh consent.
- Structured revision uses the interpretation-review fields. Matching-relevant changes release an active hold and start a revision with zero exclusions/rejections; prior history remains.
- Withdraw confirmation includes request/version and a literal confirmation intent. It is available from every state before `CONFIRMED` and permanently closes the request.
- Owner cancellation includes appointment/version, explicit confirmation, and an optional reason up to 500 characters. It succeeds only before authoritative scheduled start and does not reopen the request.

## Password and owner-account forms

### Change password

Fields are `currentPassword`, `newPassword`, and `confirmPassword`. New non-demo passwords are at least six characters, match confirmation, and are checked against current/temporary credential rules. Values are never logged, audited, cached, or echoed after failure.

### Staff provision/reset

Provision accepts `username` (normalized, unique, with a server suggestion) and owner/version. Reset accepts account/version and explicit confirmation. The random one-time password is displayed on the immediate success page once, then removed from server-side view state; only its BCrypt hash and seven-day expiry are stored.

## Staff queue forms

| Action | Required fields and validation |
|---|---|
| Claim | `expectedVersion`; item must be unclaimed/open |
| Unclaim | `expectedVersion`, nonblank audit reason up to 500 characters |
| Reassign | `expectedVersion`, valid staff account ID, nonblank audit reason |
| Add note | `expectedVersion`, note 1–1,000 characters; staff-only |
| Manual interpretation | Same structured fields/windows plus editable care type, specialty, urgency, and veterinarian strength; all deterministic rules still apply |
| Verify | Request/revision/item versions and explicit verification |
| Staff offer | Exact veterinarian/start/duration, versions, optional staff context; must pass ordinary eligibility and hold acquisition |
| Direct booking | Pet/vet/start/duration, reason category, optional note, and authorization evidence |

Direct-book authorization is exactly one of:

- `OWNER_AGREEMENT`: agreement time, method, and recording staff are required.
- `CLINIC_FOLLOW_UP` or `CLINIC_RECHECK`: an existing supporting Visit ID for the same pet is required; no owner agreement fields are fabricated.

## Availability and clinic-policy forms

- Duration set is nonempty, unique, positive whole minutes, and each duration occupies whole 15-minute blocks.
- Booking horizon is 1–365 days. Hold duration is 1–60 minutes. Scheduling grid remains read-only at 15 minutes. Owner minimum notice remains read-only at 120 minutes.
- Clinic zone is a valid `ZoneId`; after any request or appointment exists it is displayed read-only and a submitted change is rejected.
- Clinic hours, named periods, recurring shifts, and exception intervals have same-day `start < end`, 15-minute alignment where scheduling capacity is involved, and no overlap within their scope. Overnight values are rejected.
- Closure and leave start/end are inclusive local dates with start at or before end. Closures cannot contain times.
- Every capacity form posts its aggregate version. Before save, the service reports each conflicting appointment or active hold; it never saves partially. A hold must be deliberately released with a separate audited action.
- Urgent guidance must be nonblank. Emergency terms are normalized, deduplicated, versioned, and may only raise concern.

## Appointment lifecycle forms

- Staff book/reschedule/cancel forms require a configured reason category and optional concise note. They cannot request conflict override.
- Reschedule uses current appointment version plus a new exact slot; old time is server-loaded and retained in audit.
- Complete is enabled only at/after scheduled end and requires clinical notes. Assigned veterinarian and actual completion date are server-derived/validated, and exactly one Visit is created.
- No-show is enabled only at/after end and requires a no-show category; no Visit is created.
- Correct requires current version, correction category, reason, and desired consistent terminal outcome. Only this form may create, remove, or correct the appointment-linked Visit after an erroneous completion/no-show.
