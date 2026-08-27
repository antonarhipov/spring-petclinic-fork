# Acceptance Criteria: UC3 — Interpret an Appointment Request

## Functional

### UC3-AC1: Open a request for an owned pet

**Covers:** UC3-B1

When an authenticated owner starts scheduling for a pet linked to that owner, the system shall open request intake for that pet.

### UC3-AC2: Reject request intake for another owner's pet

**Covers:** UC3-B1

If an owner attempts to start scheduling for a pet not linked to that owner, then the system shall reject access and shall not create a request.

### UC3-AC3: Show urgent-care guidance

**Covers:** UC3-B2

When the system renders an appointment-request form, the system shall display localized application-authored urgent-care guidance and the configured clinic contact details.

### UC3-AC4: Default the request language

**Covers:** UC3-B3

When an owner opens a new request form, the system shall default the request-language selection to the current UI locale.

### UC3-AC5: Route unsupported languages to staff

**Covers:** UC3-B4

When an owner submits a selected request language other than English, the system shall create `STAFF_QUEUED(UNSUPPORTED_LANGUAGE)` without dispatching an AI call.

### UC3-AC6: Accept text within the length range

**Covers:** UC3-B5

When request text contains between 2 and 1,999 characters after Unicode NFC normalization, CRLF/CR-to-LF conversion, and outer trimming, the system shall accept its length when all other intake validation succeeds.

### UC3-AC7: Accept text at the length boundaries

**Covers:** UC3-B5

When request text contains exactly 1 or 2,000 characters after Unicode NFC normalization, CRLF/CR-to-LF conversion, and outer trimming, the system shall accept its length when all other intake validation succeeds.

### UC3-AC8: Reject text outside the length range

**Covers:** UC3-B5

If request text is empty or exceeds 2,000 characters after Unicode NFC normalization, CRLF/CR-to-LF conversion, and outer trimming, then the system shall reject intake and shall not create or dispatch an interpretation.

### UC3-AC9: Block a duplicate active pet request

**Covers:** UC3-B6

If the selected pet already has a nonterminal scheduling request, then the system shall reject creation of another request for that pet.

### UC3-AC10: Surface the existing pet request

**Covers:** UC3-B7

When a new request is blocked because the pet already has a nonterminal request, the system shall present that existing request to the owner.

### UC3-AC11: Permit active requests for different pets

**Covers:** UC3-B8

When an owner has nonterminal requests only for other owned pets, the system shall permit creation of a request for the selected pet.

### UC3-AC12: Dispatch below the hourly AI limit

**Covers:** UC3-B9

When an account has fewer than nine AI dispatches in the preceding rolling hour, the system shall permit another otherwise-valid owner-triggered dispatch.

### UC3-AC13: Dispatch at the hourly AI limit

**Covers:** UC3-B9

When an account has nine AI dispatches in the preceding rolling hour, the system shall permit the tenth otherwise-valid owner-triggered dispatch.

### UC3-AC14: Reject a dispatch beyond the hourly AI limit

**Covers:** UC3-B9, UC3-B12

If an account already has ten AI dispatches in the preceding rolling hour, then the system shall reject another dispatch and shall not create fallback work.

### UC3-AC15: Count failed dispatched calls

**Covers:** UC3-B10

When a dispatched AI call times out or fails, the system shall count that call against the account's rolling-hour allowance.

### UC3-AC16: Exclude non-dispatched actions from the allowance

**Covers:** UC3-B11

When intake fails before AI dispatch or the owner makes a non-AI structured edit, the system shall leave the account's AI-dispatch count unchanged.

### UC3-AC17: Show the AI retry time

**Covers:** UC3-B12

When an owner exceeds the rolling-hour AI allowance, the system shall show the earliest time at which another dispatch may be attempted.

### UC3-AC18: Disclose the outbound data categories

**Covers:** UC3-B13

When the system requests AI consent, the system shall first display that the outbound data consists of the free text, unnamed pet type, clinic date and time-zone context, named periods, and veterinarian and specialty names.

### UC3-AC19: Record version-specific consent

**Covers:** UC3-B14

When an owner consents to AI interpretation, the system shall record consent for the exact free-text version before dispatching any AI data.

### UC3-AC20: Handle declined consent

**Covers:** UC3-B15

When an owner declines AI consent, the system shall create `STAFF_QUEUED(CONSENT_DECLINED)` without dispatching an AI call.

### UC3-AC21: Exclude prohibited AI data

**Covers:** UC3-B16

When the system constructs an AI request, the system shall omit owner contact data, account data, pet name, the full calendar, existing appointments, and unrelated clinical history.

### UC3-AC22: Persist before AI dispatch

**Covers:** UC3-B17

When a consented English request is ready for AI interpretation, the system shall persist it in `INTERPRETING` before dispatching AI work.

### UC3-AC23: Poll without duplicate work

**Covers:** UC3-B18

While a request is `INTERPRETING`, when the owner polls its status, the system shall return the current status without dispatching another AI call.

### UC3-AC24: Limit AI interpretation attempts

**Covers:** UC3-B19

When an AI interpretation is dispatched for a free-text version, the system shall make no more than one attempt for that version.

### UC3-AC25: Enforce the AI deadline

**Covers:** UC3-B19, UC3-B20

If an AI interpretation has not returned a usable result when its 60-second deadline is reached, then the system shall create `STAFF_QUEUED(AI_UNAVAILABLE)`.

### UC3-AC26: Handle unavailable AI

**Covers:** UC3-B20

If Ollama is unconfigured, unreachable, or returns an execution failure, then the system shall create `STAFF_QUEUED(AI_UNAVAILABLE)` without retrying automatically.

### UC3-AC27: Enforce the closed schema version

**Covers:** UC3-B21

If AI output does not conform to the current versioned closed interpretation schema or contains an issue code outside its fixed catalog, then the system shall reject the output and shall not use it as a structured interpretation.

### UC3-AC28: Resolve names against active catalog entries

**Covers:** UC3-B22

When AI output names a veterinarian or specialty, the system shall resolve the name only to a matching active catalog entry.

### UC3-AC29: Expand an undated weekday

**Covers:** UC3-B23

When the owner confirms an interpretation containing a weekday without a concrete date, the system shall resolve it to every matching weekday inside the concrete owner horizon materialized from the confirmation instant.

### UC3-AC30: Resolve captured named periods

**Covers:** UC3-B24

When the interpretation contains a named period, the system shall resolve it using the named-period definitions captured for the request.

### UC3-AC31: Default an omitted duration

**Covers:** UC3-B25

When valid AI output omits duration, the system shall use the default duration from the request's settings snapshot.

### UC3-AC32: Preserve an in-range grid duration

**Covers:** UC3-B26

When AI supplies a duration within the snapshot bounds and on the 15-minute grid, the system shall retain that duration.

### UC3-AC33: Normalize a duration at the snapshot bounds

**Covers:** UC3-B26

When AI supplies a duration equal to the snapshot minimum or maximum, the system shall retain that boundary duration.

### UC3-AC34: Clamp a duration outside the snapshot bounds

**Covers:** UC3-B26

When AI supplies a duration below the snapshot minimum or above the snapshot maximum, the system shall clamp the duration to the applicable snapshot boundary.

### UC3-AC35: Round an off-grid duration upward

**Covers:** UC3-B26

When AI supplies an in-range duration off the 15-minute grid, the system shall round it upward to the next grid value without exceeding the snapshot maximum.

### UC3-AC36: Clarify an unknown preferred veterinarian

**Covers:** UC3-B27

When AI output names a preferred veterinarian that cannot be resolved to the active catalog, the system shall move the request to `CLARIFICATION_REQUIRED`.

### UC3-AC37: Clarify incomplete availability

**Covers:** UC3-B28

When valid AI output contains `INCOMPLETE_AVAILABILITY`, the system shall move the request to `CLARIFICATION_REQUIRED`.

### UC3-AC38: Route unsafe interpretation outcomes to staff

**Covers:** UC3-B29

If AI output contains an unknown specialty, `CONTRADICTORY_CLINICAL_ROUTING`, `UNSAFE_CONTENT`, an unknown issue code, or a malformed schema, then the system shall create `STAFF_QUEUED(INVALID_AI_OUTPUT)` without an automatic AI retry.

### UC3-AC39: Apply excluded windows first

**Covers:** UC3-B30

When an excluded window overlaps an allowed or preferred window, the system shall remove the overlap from the owner's available space.

### UC3-AC40: Require confirmation of unrestricted availability

**Covers:** UC3-B31

When an interpretation contains no positive availability windows, the system shall require the owner to confirm explicitly that the entire horizon is acceptable before matching.

### UC3-AC41: Clarify fully excluded availability

**Covers:** UC3-B32

When exclusions remove every possible owner window, the system shall move the request to `CLARIFICATION_REQUIRED` without starting matching.

### UC3-AC42: Escalate urgency

**Covers:** UC3-B33

When the interpretation indicates urgency, the system shall create emergency-priority `STAFF_QUEUED(URGENCY)` without starting automated matching.

### UC3-AC43: Present the full structured interpretation

**Covers:** UC3-B34

When an interpreted request reaches owner review, the system shall present its complete structured interpretation before permitting matching.

### UC3-AC44: Edit owner-correctable structured facts

**Covers:** UC3-B35

When an owner edits interpreted availability, preferred veterinarian, or factual summary, the system shall update the structured interpretation without dispatching AI.

### UC3-AC45: Escalate a clinical-field dispute

**Covers:** UC3-B36

When an owner reports interpreted duration, care type, specialty, or urgency as wrong, the system shall create `STAFF_QUEUED(CLINICAL_DISPUTE)` instead of allowing the owner to edit that field.

### UC3-AC46: Require fresh consent for changed text

**Covers:** UC3-B37

While a request is in `AWAITING_CONSENT`, `INTERPRETING`, `CLARIFICATION_REQUIRED`, `AWAITING_CONFIRMATION`, `MATCHING`, or `SLOT_HELD`, when its owner replaces the original free text with a valid new version, the system shall move the request to `AWAITING_CONSENT`.

### UC3-AC47: Release a hold after confirmed-fact editing

**Covers:** UC3-B38

While a request has an active hold, when the owner edits a confirmed factual structured field, the system shall release the hold.

### UC3-AC48: Clear prior suggestion history after editing

**Covers:** UC3-B39

When an owner edits a confirmed factual structured field, the system shall clear that request's prior suggestions and exact rejections.

### UC3-AC49: Return an edited request to review

**Covers:** UC3-B40

When an owner edits a confirmed factual structured field, the system shall move the request to `AWAITING_CONFIRMATION` before further matching.

### UC3-AC50: Finalize settings at confirmation

**Covers:** UC3-B41

When the owner confirms the structured interpretation, the system shall preserve the interpretation settings captured for the current text version, compute the absolute owner-horizon boundaries from the confirmation instant, and freeze the resulting request settings snapshot.

### UC3-AC51: Enter matching after confirmation

**Covers:** UC3-B42

While a request is `AWAITING_CONFIRMATION`, when the owner confirms its structured interpretation, the system shall move the request to `MATCHING`.

### UC3-AC52: Cancel an owned nonterminal request

**Covers:** UC3-B43

While an owned scheduling request is nonterminal, when its owner cancels it, the system shall move the request to `CANCELLED`.

### UC3-AC53: Ignore a late AI result

**Covers:** UC3-B44

If an AI result arrives after its request has become terminal, then the system shall ignore the result and shall preserve the terminal request state.

### UC3-AC54: Capture settings before intake routing

**Covers:** UC3-B45

When valid normalized request intake is accepted, the system shall capture the current duration bounds and default, named-period definitions, owner-horizon length, and guided-hold duration before routing the request by language or consent.

### UC3-AC55: Await consent for valid English intake

**Covers:** UC3-B46

When an owner submits valid English request intake, the system shall persist the request in `AWAITING_CONSENT` before asking for version-specific AI consent.

### UC3-AC56: Send a valid interpretation to owner review

**Covers:** UC3-B47

While a request is `INTERPRETING`, when its valid AI result requires neither clarification nor staff fallback, the system shall move the request to `AWAITING_CONFIRMATION`.

### UC3-AC57: Finish a resolved clarification

**Covers:** UC3-B48

While a request is `CLARIFICATION_REQUIRED`, when an owner edit passes deterministic validation with every clarification issue resolved, the system shall move the request to `AWAITING_CONFIRMATION`.

### UC3-AC58: Retain unresolved clarification

**Covers:** UC3-B49

While a request is `CLARIFICATION_REQUIRED`, when deterministic validation finds a remaining clarification issue after an owner edit, the system shall keep the request in `CLARIFICATION_REQUIRED` with the remaining issues.

### UC3-AC59: Preserve consent state after rate limiting

**Covers:** UC3-B55

While a request is `AWAITING_CONSENT`, when its owner consents but the account has no AI dispatch available in the rolling hour, the system shall keep the request in `AWAITING_CONSENT`.

### UC3-AC60: Invalidate automated work after text replacement

**Covers:** UC3-B50

While a request has active AI or solver work, when its owner replaces the original text, the system shall prevent that work from updating the request.

### UC3-AC61: Clear prior-version derived state

**Covers:** UC3-B51

When an owner replaces permitted original text, the system shall clear the prior structured interpretation, issues, suggestions, exact rejections, and materialized horizon.

### UC3-AC62: Capture settings for replacement text

**Covers:** UC3-B52

When an owner replaces permitted original text, the system shall capture the current duration bounds and default, named-period definitions, owner-horizon length, and guided-hold duration for the new text version.

### UC3-AC63: Reject text replacement after automation

**Covers:** UC3-B53

If an owner attempts to replace original text after the request has entered `STAFF_QUEUED`, `STAFF_OFFERED`, or a terminal state, then the system shall reject the replacement and shall preserve the request.

### UC3-AC64: Clear request resources on owner cancellation

**Covers:** UC3-B54

When an owner cancels a nonterminal request, the system shall release every active request reservation in the cancellation transaction.

### UC3-AC65: Release a guided hold after text replacement

**Covers:** UC3-B56

While a request has an active guided hold, when its owner replaces the original text, the system shall release the hold.

### UC3-AC66: Remove a request claim on owner cancellation

**Covers:** UC3-B57

When an owner cancels a nonterminal request, the system shall remove its staff claim in the cancellation transaction.

### UC3-AC67: Accept bounded calendar expressions

**Covers:** UC3-B58

When AI output represents an explicit date, bare weekday, supported relative week, end of the working week, supported relative or named month, or supported ordinal week of a month, the system shall accept the calendar expression for deterministic resolution.

### UC3-AC68: Clarify unsupported calendar language

**Covers:** UC3-B58

If AI output cannot represent the owner's calendar language with the bounded vocabulary or omits a required calendar component, then the system shall move the request to `CLARIFICATION_REQUIRED` without guessing a date.

### UC3-AC69: Anchor relative expressions at confirmation

**Covers:** UC3-B59

When the owner confirms an interpretation containing a relative calendar expression, the system shall resolve that expression from the clinic-local confirmation date.

### UC3-AC70: Resolve relative calendar weeks

**Covers:** UC3-B60

When an interpretation contains next week or a numbered future week, the system shall resolve it to the complete Monday-through-Sunday calendar week at the requested offset from the confirmation week before horizon clipping.

### UC3-AC71: Resolve the end of the working week

**Covers:** UC3-B61

When an interpretation contains end of the working week, the system shall resolve its date range from Thursday through Sunday of the applicable calendar week.

### UC3-AC72: Exclude closed weekend time

**Covers:** UC3-B61

While an end-of-working-week range includes Saturday or Sunday, when the clinic has no effective veterinarian availability then, the system shall produce no weekend suggestion.

### UC3-AC73: Permit open weekend time

**Covers:** UC3-B61

While an end-of-working-week range includes Saturday or Sunday, when an otherwise-feasible veterinarian has effective availability then, the system shall treat that weekend time as eligible.

### UC3-AC74: Resolve relative calendar months

**Covers:** UC3-B62

When an interpretation contains next month or a numbered future month, the system shall resolve it to the complete requested calendar month before horizon clipping.

### UC3-AC75: Resolve a named current or future month

**Covers:** UC3-B63

When an interpretation names a month whose final day is not before the confirmation date, the system shall resolve it to that month in the confirmation year before horizon clipping.

### UC3-AC76: Roll a past named month into the next year

**Covers:** UC3-B63

When an interpretation names a month whose final day precedes the confirmation date, the system shall resolve it to that month in the next year before horizon clipping.

### UC3-AC77: Resolve an ordinal calendar row within a month

**Covers:** UC3-B64

When an interpretation selects the first, second, third, fourth, or last week of a referenced month, the system shall resolve the corresponding Monday-through-Sunday calendar row and clip it to the month's first and last dates.

### UC3-AC78: Intersect calendar and time concepts

**Covers:** UC3-B65

When one positive window contains multiple date, weekday, or named-time concepts, the system shall retain only instants satisfying all of those concepts.

### UC3-AC79: Enforce unqualified availability

**Covers:** UC3-B66

When an owner states a positive date or time window without preferential wording, the system shall exclude every candidate outside that window.

### UC3-AC80: Rank explicitly preferred availability

**Covers:** UC3-B66

When an owner marks a positive window as preferred within a broader allowed range, the system shall keep the broader allowed candidates eligible and rank candidates inside the preferred window first.

### UC3-AC81: Preserve the future portion at the lower boundary

**Covers:** UC3-B67, UC3-B69

When a resolved hard window begins before the next future grid boundary and ends after it, the system shall clip the window start to that boundary and preserve its remaining future portion.

### UC3-AC82: Clip an overlapping upper boundary

**Covers:** UC3-B67

When a resolved hard window begins before the exclusive owner-horizon end and ends after it, the system shall clip the window end to the exclusive owner-horizon end.

### UC3-AC83: Clarify a range at or beyond the horizon

**Covers:** UC3-B68

If every resolved hard window begins at or after the exclusive owner-horizon end, then the system shall move the request to `CLARIFICATION_REQUIRED`, explain that the requested period is outside the bookable horizon, and shall not start matching or staff fallback.

## Coverage exclusions

- Accessibility: the specification requires localization but defines no measurable accessibility contract for intake, consent, clarification, or review screens.
- Compatibility: English-only AI interpretation and non-English staff fallback are specified, but no browser-compatibility matrix is defined.
