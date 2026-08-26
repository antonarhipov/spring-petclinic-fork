# UC7: Notify, Audit, and Recover

## Summary

The system gives owners durable in-app notice of scheduling changes, records privacy-safe state history for staff and owner timelines, removes sensitive AI data on schedule, and restores safe request states after deadline passage or interrupted automation.

## Resolved ambiguities

### In-app notifications

- External email, SMS, and push are excluded. Notifications are persistent in-app records linked to an authorized resource.
- Owners receive notifications for a new staff offer, staff-offer expiry, appointment confirmation, appointment rescheduling, appointment cancellation, and request cancellation by staff.
- Authenticated navigation shows an unread count but no sensitive request, pet, clinical, or appointment text.
- A notification link is rendered only when its target is accessible to the current identity.
- Reading a notification changes only its read state; it never accepts an offer or changes a request or appointment.

### Audit and timeline visibility

- Append-only audit metadata records: consent, AI schema/model metadata, interpretation edits, request transitions, clarification, claims, holds, offers, rejections, appointment booking and lifecycle changes, calendar/settings changes, account creation and deactivation, username change, lock/unlock, password change, and password reset.
- Audit events contain actor, event type, target identifier, timestamp, safe reason code, and safe before/after metadata where applicable.
- Passwords, password hashes, raw or purged free text, full AI output, clinical descriptions, and sensitive staff notes never appear in audit payloads.
- Audit metadata is retained indefinitely.
- Staff may view the complete audit. Owners may view only the request and appointment timeline for their own resources; they do not see account-security internals or staff-only notes.

### Sensitive-data retention

- A request linked to an appointment retains raw free text and full AI output until 30 days after the appointment becomes `COMPLETED`, `NO_SHOW`, or `CANCELLED`.
- The first transition of a linked appointment to `COMPLETED`, `NO_SHOW`, or `CANCELLED` fixes that purge deadline. Correcting `NO_SHOW` to `COMPLETED` does not extend it.
- A request without an appointment retains those fields until 30 days after the request becomes `CANCELLED` or `EXPIRED`.
- Purge removes raw free text, full AI input/output, and owner-facing factual AI summary.
- Purge preserves care type, specialty, duration, consent metadata, state history, appointment linkage, and audit metadata. The staff-authored Visit description remains on the Visit.
- Sensitive text and passwords are never written to application logs.

### Deadline enforcement and restart recovery

- Hold, offer, fallback, claim, lockout, and retention deadlines are persisted as absolute instants.
- Deadline effects are checked inside the relevant user transaction and by periodic lifecycle processing.
- On startup, an overdue guided hold is released without rejection and its request becomes `MATCHING`.
- On startup, an overdue staff offer is released and becomes `STAFF_QUEUED`, unless the seven-day fallback deadline has passed, in which case the request becomes `EXPIRED`.
- On startup, a request left in `INTERPRETING` or active automated `MATCHING` beyond its operation deadline becomes `STAFF_QUEUED(RECOVERY_REQUIRED)`.
- Recovery never repeats an interrupted AI or solver call automatically.
- Unexpired holds and offers retain their original deadlines after restart.
- A claim inactive for 30 minutes becomes reclaimable; recovery does not discard its request history.

### Diagnostics

- Staff diagnostics show Ollama connectivity, configured model identifier, solver availability, lifecycle-worker health, and database migration status without exposing prompts or user text.
- The runtime Ollama model is configurable and defaults to `gemma4:latest`. Reproducible CI pins that tag to the approved digest.
- Public liveness/readiness contains no detailed dependency, model, user, or database information.

## Explicit assumptions

- Notification delivery is transactional with the state change that creates it, so the notification cannot claim a change that did not commit.
- All deadline evaluation uses the injected application clock and configured clinic zone where local-date interpretation is required.
- Indefinite audit retention applies only to safe metadata; it is not an exception to sensitive-payload purge.
- Lifecycle processing may run more than once for the same overdue record and must produce the same final state.

## Handled edge cases

- Marking an offer notification read does not accept the offer.
- A notification whose target no longer has an available action still opens the current authorized state.
- A restart after an AI call was dispatched but before its result was persisted creates recovery fallback rather than another call.
- A restart during an unexpired hold preserves the remaining time rather than granting a fresh duration.
- A purge rerun over an already-purged request is harmless.
- A request confirmed long before its appointment keeps staff-needed context until 30 days after the appointment first becomes `COMPLETED`, `NO_SHOW`, or `CANCELLED`.

## Behaviors to verify

- UC7-B1: The system creates an owner notification when staff issues a new offer.
- UC7-B2: The system creates an owner notification when a staff offer expires.
- UC7-B3: The system creates an owner notification when an appointment is confirmed.
- UC7-B4: The system creates an owner notification when staff reschedules an appointment.
- UC7-B5: The system creates an owner notification when an appointment is cancelled.
- UC7-B6: The system creates an owner notification when staff cancels a nonterminal request.
- UC7-B7: The system displays an authenticated owner's unread notification count without sensitive text.
- UC7-B8: The system renders a notification link only when its target is authorized for the current identity.
- UC7-B9: The system changes only notification read state when an owner marks a notification read.
- UC7-B10: The system opens the target's current authorized state when a notification action is no longer available.
- UC7-B11: The system appends safe audit metadata for every defined security, request, scheduling, calendar, and appointment event.
- UC7-B12: The system excludes passwords, hashes, sensitive text, AI payloads, clinical descriptions, and staff-only notes from audit payloads.
- UC7-B13: The system retains privacy-safe audit metadata indefinitely.
- UC7-B14: The system permits staff to view the complete audit history.
- UC7-B15: The system permits an owner to view only that owner's request and appointment timelines.
- UC7-B16: The system retains raw text and full AI output for a linked request until 30 days after the linked appointment first becomes `COMPLETED`, `NO_SHOW`, or `CANCELLED`.
- UC7-B17: The system retains raw text and full AI output for an unlinked request until 30 days after that request becomes terminal.
- UC7-B18: The system purges raw free text, full AI input/output, and AI factual summary when the applicable retention deadline passes.
- UC7-B19: The system preserves care type, specialty, duration, consent metadata, state history, appointment linkage, and audit metadata after purge.
- UC7-B20: The system never writes passwords or sensitive request text to application logs.
- UC7-B21: The system persists hold, offer, fallback, claim, lockout, and retention deadlines as absolute instants.
- UC7-B22: The system applies an overdue deadline during a user action before accepting that action.
- UC7-B23: The system periodically applies overdue lifecycle deadlines without user action.
- UC7-B24: The system releases an overdue guided hold after restart without recording rejection.
- UC7-B25: The system returns a recovered overdue guided request to `MATCHING`.
- UC7-B26: The system releases an overdue staff offer after restart.
- UC7-B27: The system returns a recovered overdue staff offer to `STAFF_QUEUED` when fallback time remains.
- UC7-B28: The system moves a recovered request to `EXPIRED` when its fallback deadline has passed.
- UC7-B29: The system creates `STAFF_QUEUED(RECOVERY_REQUIRED)` for interrupted interpretation or automated matching beyond its operation deadline.
- UC7-B30: The system performs no automatic AI or solver retry during recovery.
- UC7-B31: The system preserves the original deadline of every unexpired hold or offer across restart.
- UC7-B32: The system makes a claim reclaimable after 30 minutes without activity.
- UC7-B33: The system applies repeated lifecycle processing idempotently.
- UC7-B34: The system shows dependency, configured-model, lifecycle-worker, and migration diagnostics only to staff.
- UC7-B35: The system omits prompts, request text, and sensitive dependency details from diagnostics.
- UC7-B36: The system reports Ollama unavailability in staff diagnostics without failing application startup.
- UC7-B37: The system preserves the original sensitive-data purge deadline when `NO_SHOW` is corrected to `COMPLETED`.

## Out of scope

- Email, SMS, push, webhooks, or other external delivery
- Owner access to global audit or staff-only security events
- Sensitive-payload retention beyond the defined deadlines
- Automatic AI or solver retry after restart
- Production observability platforms or compliance export tooling
