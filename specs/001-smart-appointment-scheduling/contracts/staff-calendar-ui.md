# Staff Calendar and Queue UI Contract

Staff pages expose the full clinic calendar and operational details. Every
state-changing action records the signed-in staff account and any required
reason.

| Route | Staff action | Required inputs | Result |
|-------|--------------|-----------------|--------|
| `GET /staff/queue` | View fallback queue | Optional state/priority filter | Emergency-first, oldest-first queue with claim state. |
| `POST /staff/queue/{itemId}/claim` | Claim item | None | Item enters `IN_REVIEW`. |
| `POST /staff/queue/{itemId}/reassign` | Reassign or unclaim item | Target staff or unclaim choice, reason | Updated claim and audit record. |
| `GET /staff/queue/{itemId}` | Review item | None | Request, interpretation history, owner/pet context, and staff-only notes. |
| `POST /staff/queue/{itemId}/interpretation` | Complete/correct interpretation | Structured care and availability values | Validated request ready for staff offer or booking. |
| `POST /staff/queue/{itemId}/offers` | Make owner-facing offer | Veterinarian, start, duration | Held owner offer or conflict result. |
| `POST /staff/queue/{itemId}/appointments` | Book on behalf of owner | Veterinarian, start, duration, recorded agreement | Confirmed appointment or conflict result. |
| `GET /staff/calendar` | View full calendar | Date range and veterinarian filter | Appointments, holds, availability, leave, exceptions, and closures. |
| `POST /staff/appointments` | Directly book | Pet, veterinarian, time, duration, source/agreement when applicable, reason category | Confirmed appointment or validation/conflict result. |
| `POST /staff/appointments/{appointmentId}/reschedule` | Reschedule | New veterinarian/time, reason category, optional note | Same appointment identity at valid new time with audit history. |
| `POST /staff/appointments/{appointmentId}/cancel` | Cancel | Reason category, optional note | Cancelled appointment and released capacity. |
| `POST /staff/appointments/{appointmentId}/complete` | Complete after end | Completion details/clinical notes | Completed appointment and one linked visit. |
| `POST /staff/appointments/{appointmentId}/no-show` | Mark no-show after end | Required reason, optional note | No-show appointment without a visit entry. |
| `POST /staff/appointments/{appointmentId}/correct-status` | Correct completion/no-show | Corrected status/details, reason | Audited lifecycle correction. |
| `GET /staff/settings/scheduling` | View settings | None | Clinic time zone, durations, horizon, notice, holds, periods, and urgent guidance. |
| `POST /staff/settings/scheduling` | Update settings | Valid bounded settings | Updated settings or a conflict/immutability result. |
| `POST /staff/veterinarians/{vetId}/availability/*` | Manage shifts, exceptions, leave | Valid 15-minute aligned range and reason where required | Updated availability or conflict with appointment/hold result. |
| `POST /staff/closures` | Manage closure | Full local date/range and optional reason | Closure or conflict result. |
| `GET /staff/audit` | View audit history | Date/actor/target filters | Staff-only audit trail. |

## Calendar Safety Rules

- Staff cannot override an appointment or held-offer conflict.
- Staff must resolve conflicting confirmed appointments before saving any
  availability reduction.
- Staff must deliberately release an affected hold before an availability change
  can take effect.
- Staff actions for owner requests retain the source request and revision
  relationship.
