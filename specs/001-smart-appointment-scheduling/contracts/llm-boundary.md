# LLM Interpretation Boundary

## Purpose and trust boundary

`InterpretationPort` accepts one consented `RequestTextRevision` plus a versioned clinic vocabulary and absolute deadline. `SpringAiOllamaInterpretationAdapter` is its production implementation. The LLM proposes structured scheduling data; it never creates a confirmed revision, selects a slot, or changes urgency downward.

The checked-in [v1 JSON Schema](llm-interpretation-v1.schema.json) is used in two places:

1. pass the exact schema to Ollama through `OllamaChatOptions.outputSchema` for native structured generation;
2. validate the captured raw response again inside the application before any field can reach owner review or Timefold.

Provider conformance is useful but is never trusted as validation.

## Invocation input

The versioned prompt input contains only:

- exact consented source text and its submission instant;
- clinic zone and concrete “now” anchor;
- allowed duration values;
- booking horizon and owner minimum notice;
- named-period definitions;
- stable veterinarian and specialty codes/names allowed in output;
- prompt-template version and schema version;
- safety instruction that uncertainty must be represented rather than guessed.

The input contains no staff notes, audit history, other owners, calendar availability, account credentials, or full clinical history. The adapter verifies an effective consent record for the exact text revision before sending anything.

## Output validation pipeline

For every raw non-streaming response:

1. Persist the raw response on the attempt before normalization.
2. Parse one JSON value; prose, code fences, trailing content, or malformed JSON is invalid.
3. Walk the tree against the recognized v1 shape. Record every unknown JSON-pointer path and value, then remove unknown properties from the operational projection.
4. Validate the recognized projection against the JSON Schema. Every top-level field is required, including nullable optionals and arrays.
5. Bind to an immutable DTO and apply Jakarta/type/length/format validation.
6. Apply deterministic semantic rules against the supplied clinic vocabulary:
   - duration is in the configured set;
   - specialty/veterinarian codes exist and are compatible;
   - specialty is present exactly for specialty care;
   - veterinarian preference strength and ID agree;
   - resolved window endpoints are both present, ordered, offset-aware, and use the clinic resolution context;
   - preferred/excluded windows do not contradict hard allowed windows;
   - unresolved dates and uncertainties identify valid field paths/codes.
7. Normalize recognized values to canonical JSON and classify the response.

Classification:

| Result | Meaning | Retry? | Workflow |
|---|---|---|---|
| `VALID_REVIEWABLE` | Structurally and semantically complete | No | Owner review |
| `VALID_NEEDS_STAFF` | Schema-valid but uncertain, unresolved, or clinically ambiguous | No | Retain result and route to staff |
| `INVALID_STRUCTURED_OUTPUT` | Missing/invalid required field, malformed JSON, invalid configured code, or contradiction that makes the structure unusable | Once if time remains | Retry or route to staff |
| `TRANSIENT_FAILURE` | Connection/service failure before valid output | Once if time remains | Retry or route to staff |
| `TIMEOUT` | Shared deadline exhausted | No | Route to staff |

Unknown fields alone do not invalidate an otherwise valid recognized projection. They are recorded and ignored, as required. A model-provided numeric confidence is neither accepted nor used.

## Attempt and deadline policy

- One monotonic deadline is set ten seconds after operation trigger; persisted queue delay, both calls, parsing, validation, and completion handling consume it.
- The application coordinator performs at most two total model calls.
- Spring AI framework retries and `.validateSchema()` repeats are disabled. Otherwise their defaults could exceed the clarified attempt count.
- Attempt 2 is allowed only after attempt 1 returns `TRANSIENT_FAILURE` or `INVALID_STRUCTURED_OUTPUT` and positive useful time remains.
- A valid-but-uncertain response is authoritative for that source revision and is never retried.
- Cancellation/timeout interrupts the call when possible, but a late response is still discarded by the monotonic deadline and request-version check.
- Refreshing/polling never invokes this boundary; only the unique persisted operation worker does.

## Result and persistence contract

One execution retains:

- requested alias (`gemma4:latest`) and Ollama-resolved model identifier/digest;
- prompt-template version/hash and structured-output schema version/hash;
- source-text revision and canonical prompt-input JSON needed to reconstruct the prompt;
- operation trigger/deadline/start/finish and final outcome/error classification;
- attempt count and per-attempt start/finish, raw response, normalized output, unknown fields, token/usage metadata when available, and result class.

The separately rendered full prompt is not persisted when reconstruction from source text, versioned template, and canonical input is possible. Credentials, HTTP headers, and connection details are never retained.

Only recognized, normalized, validated fields may populate `InterpretationRecord`. Only an owner- or staff-confirmed `RequestRevision` may populate a Timefold snapshot.

## Owner-visible behavior

- Raw model text, unknown fields, confidence, error class, retry count, model digest, and prompt details are never owner-visible.
- Issue text is server-owned copy keyed by validated uncertainty/validation codes; arbitrary LLM strings are not rendered as trusted guidance.
- `NO_CONCERN_IDENTIFIED` is internal structured data and must never become reassurance that the pet is non-urgent.
- The independent audited emergency-term screen may raise urgency and show fixed clinic guidance before or without an LLM result. Neither the LLM nor any other automation may lower that concern.
