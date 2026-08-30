# Quickstart and Validation Guide

This guide describes the expected validation workflow after implementation. Phase one is for synthetic and demonstration data only.

## Prerequisites

- JDK 21 selected for Maven and the IDE.
- No Gradle, MySQL, PostgreSQL, Docker, native-image, or externally exposed Actuator dependency is required.
- Ollama is required only for the live interpretation scenario. The normal automated suite uses a deterministic interpretation stub.
- A stable encryption key ring is required for every run that opens a persistent H2 file.

Verify the toolchain:

```bash
java -version
./mvnw -version
./mvnw -B dependency:tree
```

Expected: Java 21; Spring Boot 4.1.1; Spring AI 2.0.1; Timefold 2.5.0; no MySQL/PostgreSQL driver or Gradle build.

## Configure Protected Data

Generate a local 256-bit key once and keep it outside version control:

```bash
openssl rand -base64 32
```

Supply the result through the planned runtime configuration:

```bash
export PETCLINIC_ENCRYPTION_ACTIVE_KEY_ID=demo-v1
export PETCLINIC_ENCRYPTION_KEYRING='demo-v1:<base64-32-byte-key>'
```

Reuse the same key for subsequent runs against the same file-backed database. A missing active key must stop startup; a missing historical key must make only affected protected records unavailable and must never expose plaintext or ciphertext.

## Optional Live Ollama Setup

Install any local model that supports the configured structured-output request. For example:

```bash
ollama pull llama3.2:3b
export SPRING_AI_OLLAMA_BASE_URL=http://localhost:11434
export SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL=llama3.2:3b
```

The application never downloads a model during startup. If the configured model is absent, slow, or returns unusable output, the persisted request must reach staff fallback within the user-visible time budget.

## Run the Automated Suite

```bash
./mvnw -B verify
```

The suite must include:

- Framework-free unit tests for interval algebra, availability precedence, state machines, structured-output validation, encryption/rotation, and Timefold constraints.
- Secured MVC slices for public, owner, staff, password-change, polling, CSRF, foreign-owner 404, and wrong-role 403 behavior.
- In-memory H2/Flyway persistence slices for mappings, queries, uniqueness, and state projections.
- File-backed H2 tests using temporary directories for clean migration, restart persistence, lease recovery, hold expiry, key continuity, and concurrent transactions.
- One full-context startup smoke test and request-level demonstrations with AI/clock/failure boundaries controlled.

An opt-in live-model test may be run separately:

```bash
./mvnw -B -Dgroups=ollama test
```

It must verify schema-shaped output or the defined safe fallback; it is not part of the deterministic default suite.

## Start the Demonstration Profile

```bash
SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Expected:

- The database is file-backed beneath the configured data directory and survives restart.
- Flyway validates/applies the baseline and feature migrations; Hibernate validates rather than creates schema.
- Synthetic demo data is inserted idempotently.
- `admin/admin123`, a documented second staff identity, and owner accounts such as `george/george123` are available only in local/demo/test environments.
- Public navigation contains only landing/login/static resources.
- Owner and staff identities land on their distinct dashboards.

Stop and start the same command again. Existing requests, jobs, queue assignments, offers, appointments, audit events, and protected payloads must remain readable and must not be reseeded or duplicated.

## Validate the First Functional Slice

1. Sign in as staff.
2. Configure a recurring veterinarian shift.
3. Create a date-specific replacement schedule and verify it fully replaces that date's recurring shift.
4. Add leave and a clinic closure and verify precedence: closure, leave, replacement date, recurring shift.
5. Direct-book a compliant appointment after recording owner agreement and reason.
6. Attempt veterinarian, pet, owner-across-pets, hold, and out-of-availability conflicts.

Expected: the compliant appointment is committed; each invalid operation saves nothing and lists its blocking records. Calendar changes and bookings append audit events.

## Validate Owner Request and Manual Fallback

1. Sign in as an owner and submit a valid 10-2,000 character request without AI consent.
2. Confirm that the exact text is not sent to AI and that one staff queue item appears.
3. Claim it as the first staff identity, manually complete the interpretation, and request owner confirmation.
4. Reassign it to the second staff identity with a reason and verify the audit trail.
5. Confirm the interpretation as the owner.
6. Create a staff-assisted offer after recording contact.
7. Reject or allow it to expire.

Expected: the same assigned queue item returns to `IN_REVIEW`; the slot is excluded; no automatic attempt is consumed. A later staff-assisted offer can be accepted into one appointment.

## Validate Emergency Handling

1. Submit text that activates the deterministic emergency screen or controlled AI emergency result.
2. Confirm immediate urgent-care guidance and high-priority staff routing; no matching job may run.
3. As assigned staff, validate the interpretation, select `ROUTINE` or `PRIORITY`, and record a reason.
4. Reconfirm as the owner.

Expected: `ROUTINE` enqueues matching only after reconfirmation. `PRIORITY` remains staff handled. The portal never claims to be an emergency-response channel.

## Validate AI Interpretation

1. Submit a new exact text revision with consent.
2. Leave the page while interpretation runs, then return.
3. Review original prose, concrete relative dates, availability, duration, veterinarian preference, specialty, and urgency.
4. Edit structured details and verify no new AI call occurs.
5. Revise original text and verify a new consent decision and interpretation job are required.
6. Run controlled no-result, timeout, malformed-output, and crash-after-call cases.

Expected: at most one retry occurs only when no valid result was durably committed; stale completions change no current state; failures preserve the request and route to staff.

## Validate Timefold and Automatic Offers

1. Configure a routine request with multiple eligible candidate slots that exercise every preference tier.
2. Solve repeatedly against an unchanged calendar snapshot.
3. Verify the same highest-ranked slot is chosen every time and that Timefold completes exhaustive solving within five seconds.
4. Force an incomplete solve and verify that no partial best solution becomes an offer.
5. Change the calendar between solve and hold creation once, then twice.

Expected: one changed revision permits one fresh solve; a second change routes to staff. There is no custom slot-ranker path and no owner-visible full calendar.

## Validate Offer and Concurrency Boundaries

Use independent threads/connections and an injected clock to cover:

- competing holds for the same interval;
- owner overlap across different pets;
- acceptance racing expiry;
- availability mutation racing hold creation;
- duplicate accept/reject/withdraw/cancel commands;
- restart with a pending/running job or expired hold.

Expected: at most one conflicting operation succeeds. Duplicate commands return one canonical outcome. An expired hold never blocks capacity or becomes accepted after restart.

Reject or expire five automatic offers for one workflow revision. Expected: the request enters staff handling and cannot request a sixth automatic offer. A new owner revision resets the count and exclusions while preserving history.

## Validate Appointment and Legacy Lifecycles

1. Cancel a future appointment as its owner and verify the request does not reopen.
2. Reschedule and cancel as staff with review, owner agreement, internal reason, and owner-facing explanation.
3. After scheduled end, complete one appointment and mark another no-show.
4. Correct both outcomes and inspect retained prior outcome/visit history.
5. Reconcile a future-dated legacy visit.

Expected: completion creates a linked visit; cancellation/no-show does not. Corrections append events rather than erase history. Because current legacy visits lack veterinarian, exact interval, and agreement evidence, reconciliation requires a new exact slot and fresh owner agreement while retaining the legacy visit.

## Acceptance Completion

The feature is ready for acceptance when all 15 demonstration journeys in `spec.md` pass, all route/state contracts under `contracts/` hold, restart and concurrency cases pass against file-backed H2, and the measurable outcomes in `spec.md` are recorded without real owner or clinical data.
