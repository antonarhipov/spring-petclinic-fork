# Quickstart: Smart Appointment Scheduling Validation

## Prerequisites

- Java 21 JDK (`java -version` reports 21 or newer).
- Docker running for persistent-database integration tests.
- Ollama available locally for interactive AI validation.
- The POC model pre-pulled before starting the application:

  ```bash
  ollama pull gemma4:latest
  ```

- `application.properties` configures the model through
  `spring.ai.ollama.chat.model=gemma4:latest`. Change that property, rather
  than source code, to select another available Ollama model.

## Build and Test

Run the existing build path after implementation:

```bash
./mvnw test
```

or:

```bash
./gradlew test
```

Run targeted tests while developing:

```bash
./mvnw test -Dtest='*Scheduling*Test,*Security*Test'
```

The repository's scheduling migration coverage runs against H2, MySQL, and
PostgreSQL through Flyway. Docker must be available for the vendor suites.

Persistent-database tests require Docker. They must verify Flyway migrations,
reservation-block uniqueness, and the same-slot booking race on MySQL and
PostgreSQL. Unit tests cover request state transitions and solver scoring; MVC
tests cover role and ownership checks. AI integration tests must verify
provider-schema output, local schema validation, semantic validation, and staff
fallback for each validation failure.

## Run Locally

Start Ollama if it is not already running:

```bash
ollama serve
```

Start the application with the local/demo profile after its profile settings are
implemented:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Enable the scheduling integration diagnostic logger only while investigating an
issue. Its debug output must identify the correlation ID, model/options,
validation stage, solver candidate count, score, and timing without printing
owner prose or credentials.

The seeded local/demo accounts are:

| Account | Username | Password |
|---------|----------|----------|
| Owner | `george` | `george123` |
| Staff | `admin` | `admin123` |

Before automated matching can return an offer, sign in as staff and configure at
least one veterinarian's recurring availability. A new clinic deliberately has
no veterinarian shifts.

## End-to-End Validation Scenarios

### 1. Owner guided booking

1. Sign in as `admin`, configure a veterinarian's weekday availability, and
   confirm clinic defaults.
2. Sign out and sign in as `george`.
3. Select George's pet, enter a 10-2,000-character English request, grant
   consent, and submit.
4. Confirm the structured interpretation and request an option.
5. Verify the page shows one held offer only, with expiry and no calendar list.
6. Accept before expiry and verify one upcoming confirmed appointment appears.

Expected result: the appointment reserves the veterinarian and pet for every
15-minute block in its duration.

### 2. Offer lifecycle and conflict safety

1. Create a request that receives a held offer.
2. Reject it with and without an optional reason, then request another option.
3. Verify the rejected veterinarian/time is never repeated for that revision.
4. Create competing acceptance/direct-booking attempts for the same held time.
5. Verify exactly one operation succeeds and no overlapping reservation remains.

### 3. Staff fallback and emergency safety

1. Submit a request without AI consent and verify it enters `NEW` staff queue
   state without an interpretation call.
2. Submit text that triggers the emergency screen and verify urgent-care
   guidance stays visible and the queue item has emergency priority.
3. As staff, claim the item, complete the interpretation manually, and either
   make a held offer or record owner agreement for direct booking.

### 4. Availability and lifecycle management

1. Create a future appointment and try to add conflicting leave, closure, shift
   reduction, or exception.
2. Verify the change is blocked until the appointment is deliberately
   rescheduled or cancelled.
3. Create a held offer and verify a conflicting availability change requires
   explicit hold release.
4. After an appointment ends, mark it completed and verify one visit-history
   entry exists. Mark another appointment no-show and verify no visit entry
   exists.

### 5. Access control

1. Sign in as an owner and attempt to access another owner's route by changing
   an identifier.
2. Verify access is denied without record disclosure.
3. Verify owner pages do not show staff notes, audit records, queue assignments,
   full calendars, or other owners' data.
4. Verify staff pages allow the approved clinic-management actions.
