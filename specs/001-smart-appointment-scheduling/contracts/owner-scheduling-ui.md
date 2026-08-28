# Owner Scheduling UI Contract

These server-rendered pages implement the owner journey. They present one offer
at a time and never include a full veterinarian availability calendar.

| Route | Owner action | Required inputs | Result |
|-------|--------------|-----------------|--------|
| `GET /my/appointments` | View dashboard | None | Own upcoming appointments, request statuses, and visit history. |
| `GET /my/scheduling/requests/new` | Start a request | None | Pet selector, persistent urgent-care guidance, consent choice, and plain-text form. |
| `POST /my/scheduling/requests` | Submit request | Owned pet, 10-2,000 character text, consent decision | Interpretation review or staff-handling status when consent is declined or safe automation cannot proceed. |
| `GET /my/scheduling/requests/{requestId}/review` | Review request | None | Structured interpretation, resolved dates, uncertainty markers, editable permitted fields. |
| `POST /my/scheduling/requests/{requestId}/confirm` | Confirm interpretation | Permitted edited fields | Ready state and first held offer, or staff-handling status. |
| `POST /my/scheduling/requests/{requestId}/revise` | Revise request | Structured fields or new text and renewed consent | Releases a current hold and creates the next revision. |
| `GET /my/scheduling/requests/{requestId}/offer` | View current offer | None | Exactly one veterinarian name/specialty, date, local time, duration, short match rationale, and precise expiry. |
| `POST /my/scheduling/offers/{offerId}/accept` | Accept offer | None | Confirmed appointment or expired/unavailable result. |
| `POST /my/scheduling/offers/{offerId}/reject` | Reject offer | Optional short reason | Releases the offer and makes the request ready for another option or staff handling after the limit. |
| `POST /my/scheduling/requests/{requestId}/next` | Ask for another option | None | One new held offer or staff-handling status. |
| `POST /my/scheduling/requests/{requestId}/withdraw` | Withdraw unscheduled request | None | Closed status and released hold. |
| `POST /my/appointments/{appointmentId}/cancel` | Cancel upcoming appointment | Optional reason | Cancelled appointment; original request remains closed. |

## Display and Error Rules

- Owner pages show only the signed-in owner's records.
- A held offer remains visible from every owner session until it is accepted,
  rejected, expires, or is invalidated.
- Expired, released, or unavailable acceptance attempts state that the offer is
  no longer available without exposing competing appointments.
- Staff-handling pages use plain language and do not reveal staff assignments,
  notes, audit reasons, solver output, or other calendar availability.
- Completed visits show completed-care information. No-show appointments show
  only their status.
