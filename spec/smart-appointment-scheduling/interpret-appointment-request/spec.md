# UC3: Interpret an Appointment Request

## Summary

An authenticated owner submits an English free-text request for one owned pet, knowingly consents to a minimal Ollama payload, reviews a deterministic structured interpretation, and confirms the factual request that matching will use. Unsupported language, declined consent, urgency, unsafe output, and unavailable automation preserve access through staff fallback.

## Resolved ambiguities

### Request intake

- The form accepts 1–2,000 characters of normalized plain text and a request-language selection that defaults to the current UI locale. Normalization applies Unicode NFC, converts CRLF and CR line endings to LF, and trims outer whitespace while preserving internal whitespace and line breaks.
- English enters the consent and AI flow. Any other selected language creates `STAFF_QUEUED(UNSUPPORTED_LANGUAGE)` without sending text to AI.
- Localized, application-authored urgent-care guidance and staff-configured clinic contact details are always visible. AI never authors medical guidance.
- A pet may have only one nonterminal scheduling request. An owner may have one nonterminal request for each of several pets.
- Each owner account may dispatch at most ten AI calls in a rolling hour. Every dispatched call counts, including timeout or failure; pre-dispatch validation and non-AI edits do not. Exceeding the limit returns a retry time and does not create fallback work.

### Consent and outbound data

- Consent applies to the exact free-text version displayed to the owner and is recorded before dispatch.
- The consent screen identifies the data sent to Ollama: free text, pet type without its name, clinic date and time zone, named periods, and veterinarian/specialty names.
- Owner contact data, account data, pet name, full calendar, existing appointments, and unrelated clinical history are never sent.
- Declining consent creates `STAFF_QUEUED(CONSENT_DECLINED)` and does not call AI.

### Interpretation execution and schema

- The request is persisted as `INTERPRETING`; a status page polls without starting additional work.
- Immediately before AI dispatch, the request captures the current duration bounds and default, named-period definitions, owner-horizon length, and guided-hold duration as its interpretation settings. A later staff settings edit does not change the interpretation the owner reviews.
- AI execution is one attempt with a 60-second hard timeout. Missing configuration, connection failure, timeout, or execution failure creates `STAFF_QUEUED(AI_UNAVAILABLE)`.
- Output binds to a versioned closed schema containing: factual summary, duration, care type (`GENERAL` or `SPECIALTY`), optional specialty, preferred/allowed/excluded symbolic windows, optional preferred veterinarian, urgency indication, and zero or more issue codes from the fixed set `INCOMPLETE_AVAILABILITY`, `CONTRADICTORY_CLINICAL_ROUTING`, and `UNSAFE_CONTENT`.
- The server resolves veterinarian and specialty names against the live active catalog and resolves named periods against the interpretation settings captured at dispatch.
- A weekday without a concrete date represents every matching weekday in the concrete owner horizon materialized at confirmation. Named periods retain the definitions captured at dispatch.
- Missing duration uses the snapshot default. A supplied duration is clamped to the snapshot minimum/maximum and rounded upward to the next valid 15-minute increment without exceeding the maximum.

### Clarification, review, and confirmation

- Unknown preferred veterinarians and `INCOMPLETE_AVAILABILITY` create `CLARIFICATION_REQUIRED` so the owner can correct them.
- Unknown specialty, `CONTRADICTORY_CLINICAL_ROUTING`, `UNSAFE_CONTENT`, an unknown issue code, or malformed schema creates staff fallback without an automatic AI retry.
- Excluded windows override allowed and preferred windows. Preferred windows rank within the allowed space.
- No positive windows means the entire owner horizon is allowed only after the owner explicitly confirms having no time restriction.
- When exclusions remove every possible owner window, clarification is required before solving.
- Urgency stops automation and creates `STAFF_QUEUED(URGENCY)` at emergency priority.
- The owner reviews the complete structured interpretation before matching. The owner may edit availability windows, preferred veterinarian, and factual summary without AI.
- Duration, care type, specialty, and urgency are read-only to owners. Reporting one of those fields as wrong creates staff fallback.
- Changing the original free text requires fresh consent and a new AI interpretation.
- Editing confirmed factual structured fields releases any hold, clears all prior suggestions and rejections, and returns to `AWAITING_CONFIRMATION`.
- Confirmation preserves the duration rules, named-period resolution, owner-horizon length, and hold duration captured at AI dispatch; computes the absolute owner-horizon boundaries from the confirmation instant; freezes the resulting request settings snapshot; and moves the request to `MATCHING`.

## Explicit assumptions

- The request language selector is authoritative; the system does not promise automatic language detection.
- The owner can cancel any nonterminal request. Cancellation does not delete its audit trail immediately.
- Rate limiting is per authenticated account, not per IP, owner record, or pet.
- AI is a parser into bounded scheduling concepts, not a source of clinic catalogs or medical decisions.

## Handled edge cases

- A localized UI user may select English and use the AI flow or select another language and use staff fallback.
- A request containing a reason but no availability can proceed only after explicit confirmation that any time in the horizon is acceptable.
- An unknown preferred veterinarian is correctable because it is a preference; an unknown required specialty is not silently downgraded to general care.
- A timeout consumes rate-limit quota because the call was dispatched.
- Polling or a repeated form submission never dispatches the same interpretation twice.
- Editing structured facts after a slot was offered invalidates that offer and its rejection history.
- A late AI result cannot move a request forward after the owner has cancelled it.

## Behaviors to verify

- UC3-B1: The system lets an authenticated owner open a scheduling request only for a pet linked to that owner.
- UC3-B2: The system displays localized urgent-care guidance and clinic contact details on every request form.
- UC3-B3: The system defaults the request-language selection to the current UI locale.
- UC3-B4: The system creates `STAFF_QUEUED(UNSUPPORTED_LANGUAGE)` for a selected non-English request language without calling AI.
- UC3-B5: The system rejects blank text or normalized text outside 1–2,000 characters.
- UC3-B6: The system blocks a new request when the selected pet already has a nonterminal request.
- UC3-B7: The system surfaces the existing nonterminal request when a duplicate per-pet request is blocked.
- UC3-B8: The system permits one owner to hold separate nonterminal requests for different pets.
- UC3-B9: The system permits no more than ten owner-triggered AI dispatches per account in a rolling hour.
- UC3-B10: The system counts a dispatched timeout or failed AI call against the account's hourly allowance.
- UC3-B11: The system excludes pre-dispatch validation and non-AI structured edits from the AI allowance.
- UC3-B12: The system shows a retry time without creating fallback when an owner exceeds the AI allowance.
- UC3-B13: The system displays the exact outbound data categories before requesting AI consent.
- UC3-B14: The system records consent for the exact free-text version before sending any data to AI.
- UC3-B15: The system creates `STAFF_QUEUED(CONSENT_DECLINED)` when the owner declines consent.
- UC3-B16: The system sends no owner contact, account, pet-name, full-calendar, appointment, or unrelated-history data to AI.
- UC3-B17: The system persists a consented English request as `INTERPRETING` before dispatching AI work.
- UC3-B18: The system lets the owner poll an interpreting request without starting duplicate AI work.
- UC3-B19: The system limits an AI interpretation to one attempt and 60 seconds.
- UC3-B20: The system creates `STAFF_QUEUED(AI_UNAVAILABLE)` when Ollama is unconfigured, unreachable, timed out, or fails.
- UC3-B21: The system accepts AI output only when it conforms to the current closed interpretation schema version.
- UC3-B22: The system resolves a returned veterinarian or specialty only against the active database catalog.
- UC3-B23: The system resolves an undated weekday to every matching weekday inside the concrete owner horizon materialized at confirmation.
- UC3-B24: The system resolves a named period using the definitions captured for the request.
- UC3-B25: The system applies the snapshot default when AI omits duration.
- UC3-B26: The system normalizes a supplied duration to a permitted 15-minute value within the snapshot bounds.
- UC3-B27: The system enters `CLARIFICATION_REQUIRED` for an unknown preferred veterinarian.
- UC3-B28: The system enters `CLARIFICATION_REQUIRED` for incomplete factual availability.
- UC3-B29: The system creates staff fallback for unknown specialty, contradictory clinical routing, malformed schema, or unsafe AI output.
- UC3-B30: The system applies excluded windows before allowed and preferred windows.
- UC3-B31: The system requires explicit confirmation before treating absent positive windows as the whole horizon.
- UC3-B32: The system enters `CLARIFICATION_REQUIRED` when exclusions eliminate all owner availability.
- UC3-B33: The system creates emergency-priority `STAFF_QUEUED(URGENCY)` when interpretation indicates urgency.
- UC3-B34: The system presents the structured interpretation for owner review before matching.
- UC3-B35: The system permits an owner to edit interpreted availability, preferred veterinarian, and factual summary without calling AI.
- UC3-B36: The system creates staff fallback when an owner disputes interpreted duration, care type, specialty, or urgency.
- UC3-B37: The system returns changed original text to unconsented interpretation intake.
- UC3-B38: The system releases an active hold after the owner edits confirmed factual structured fields.
- UC3-B39: The system clears prior suggestions and exact rejections after the owner edits confirmed factual structured fields.
- UC3-B40: The system returns an edited request to `AWAITING_CONFIRMATION` before further matching.
- UC3-B41: The system freezes the captured interpretation settings and materializes the absolute owner horizon when the owner confirms the interpretation.
- UC3-B42: The system moves a confirmed interpretation to `MATCHING`.
- UC3-B43: The system permits an owner to move any owned nonterminal request to `CANCELLED`.
- UC3-B44: The system ignores a late AI result after its request has become terminal.

## Out of scope

- Guaranteed AI interpretation for languages other than English
- AI-authored medical advice or clinical triage
- Automatic retries or best-effort acceptance of malformed output
- Automatic language detection
- Sending the full calendar or clinical history to AI

## External dependencies

- A reachable Ollama model is required only for automated English interpretation. Its absence has the complete fallback behavior defined above.
