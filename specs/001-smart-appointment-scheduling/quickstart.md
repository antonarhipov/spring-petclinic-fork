# Quickstart and Validation Guide

This guide describes the expected development and acceptance workflow after the implementation tasks are complete. It uses synthetic PetClinic demo data only.

## Prerequisites

- JDK 21 selected for both Maven and Gradle
- Docker for MySQL/PostgreSQL integration tests
- Ollama installed for the optional live interpretation check
- `gemma4:latest` downloaded and warmed before testing the 10-second live budget

Verify the toolchain and model:

```bash
java -version
./mvnw -version
./gradlew -version
ollama pull gemma4:latest
ollama run gemma4:latest "Reply with OK"
```

Expected: both build tools report Java 21, and Ollama responds without downloading the model during the scheduling journey.

## Deterministic automated validation

Run both supported builds:

```bash
./mvnw test
./gradlew test
```

Recorded 2026-08-30 on Java 21:

- `./mvnw test` — BUILD SUCCESS, Tests run: 300, Failures: 0, Errors: 0, Skipped: 1
- `./gradlew test` — BUILD SUCCESSFUL

Expected coverage:

- existing PetClinic behavior remains green;
- both builds resolve the same feature dependency versions;
- Flyway creates and upgrades H2, MySQL, and PostgreSQL without losing legacy owner, pet, veterinarian, specialty, or visit rows;
- owner/staff route and ownership tests cover unauthenticated, wrong-role, cross-owner, CSRF, and temporary-password behavior;
- raw LLM responses cover missing, invalid, unknown, valid-uncertain, retry, and deadline cases through a deterministic adapter;
- full Timefold tests prove every automated path crosses the solver, preferred/fallback modes are distinct, repeated inputs select the same candidate, and partial deadline results are discarded;
- competing hold, acceptance, staff booking, expiry, and stale-snapshot tests produce one authoritative winner on each database;
- appointment completion/no-show/correction tests keep the Visit link consistent;
- the 10,000-record/25-user acceptance fixture meets the specified 10-second and five-second outcomes.

The default suite must not require a live LLM. A live Ollama probe is opt-in because model output and cold-start time are not deterministic CI dependencies.

## Check dependency convergence

Spring AI 2.0.1's Ollama starter declares Spring Boot 4.1.1 web-client artifacts while the application and Timefold 2.5.0 target Boot 4.1.0. Confirm that application dependency management resolves a coherent Boot 4.1.0 graph:

```bash
./mvnw dependency:tree -Dincludes=org.springframework.boot,org.springframework.ai,ai.timefold.solver
./gradlew dependencies --configuration runtimeClasspath
```

Expected: no mixed Spring Boot runtime versions, Spring AI artifacts are 2.0.1, Timefold artifacts are 2.5.0, and both builds compile against Java 21.

## Run the desktop POC with H2

Start Ollama in a separate terminal if it is not already running:

```bash
ollama serve
```

Start the application with deterministic demo accounts:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Gradle equivalent:

```bash
./gradlew bootRun --args='--spring.profiles.active=demo'
```

Open `http://localhost:8080` in a desktop browser. Demo credentials are `admin/admin123` for staff and lowercase first name plus `123` for owners, for example `george/george123`.

Expected startup evidence:

- Flyway applies the H2 legacy baseline and scheduling migrations;
- demo accounts are created only under the demo profile;
- no veterinarian is bookable until staff configure a shift;
- the application starts no duplicate interpretation or matching work when a processing page is refreshed.

## Core owner journey

1. Sign in as `admin` and configure at least one future veterinarian shift under **Availability**. Confirm defaults under **Settings**: Europe/Amsterdam, 15-minute grid, 90-day horizon, 10-minute hold, and durations 15/30/45/60.
2. Sign out and sign in as `george`.
3. Start a request for George's pet. Verify that upcoming appointments and urgent-care guidance appear before submission.
4. Enter 10–2,000 characters such as: “I need a routine visit next Tuesday or Thursday afternoon. Wednesday is impossible. Friday morning is possible if necessary.”
5. Verify the dedicated consent page has an unchecked agreement and separate **Agree and interpret** and **Continue without AI** actions.
6. Agree. Leave or refresh the processing page, then resume from the dashboard. Verify there is one operation, plain status, and no percentage.
7. Review every interpreted field and resolved concrete date. Confirm that visit reason, duration, windows, and preferred veterinarian are editable, while care type, specialty, and urgency are not.
8. Confirm the interpretation and request a suggestion. Verify that exactly one Timefold-selected held slot appears, with veterinarian, specialty, safe explanation, exact zone-aware expiry, and countdown.
9. Accept before expiry. Verify immediate appointment confirmation and that the original request no longer remains active for the pet.

Expected: no full calendar, raw LLM content, alternate slots, solver score, staff note, or internal error appears anywhere in the owner journey.

## UX recovery journeys

### Preferred fallback

Configure capacity that is allowed only by the “if necessary” window. Run matching.

Expected: no slot is shown or held initially. The page offers **Find an alternative** and **Forward to staff**. Choosing the former runs a new Timefold solve and labels the single held result as fallback.

### Reject and revise

Start Reject and verify nothing is released at the first inline step. Confirm rejection with an optional reason, request another option, then revise duration or availability.

Expected: rejection releases/excludes the exact slot with no undo; the revision releases any active hold, retains history, and starts with zero rejection/expiry count.

### Hold expiry

Use a short hold in demo settings. Keep the offer page open.

Expected: the warning appears at two minutes remaining (immediately for a one- or two-minute hold), Accept disables at zero, and next actions appear. A boundary-time POST still relies on the server and creates no appointment if expired.

### Stale page

Open the same request in two tabs. Mutate it in one and submit a different mutation from the other.

Expected: the second action receives a 409 current-state page, does not overwrite state, preserves safe input for comparison, and offers a canonical continue/refresh action.

### Consent decline and emergency

Decline consent on one request. On another, submit text containing a configured emergency term.

Expected: declined text never reaches Ollama and enters staff handling. Emergency guidance appears immediately, the queue item is high priority, and no message reassures the owner that a request is non-urgent.

## Staff journey

1. Sign in as `admin`; verify persistent **Queue**, **Calendar**, **Availability**, and **Settings** navigation.
2. Claim a queue item, add a private note, then unclaim/reassign it with a reason. Confirm all staff can still see it and the owner sees only plain staff-handling status.
3. Complete a manual interpretation for a declined-consent request. Do not expose any action that sends that text to the LLM.
4. Create one manual held offer, then separately test direct booking with recorded owner agreement.
5. Test booking without agreement using a documented supporting Visit and `CLINIC_FOLLOW_UP` or `CLINIC_RECHECK`; reject any other basis.
6. Reschedule and cancel with required reason categories. Attempt a conflict and verify no override is available.
7. After a scheduled end, mark one appointment completed and another no-show. Verify exactly one linked Visit for the completed appointment and none for no-show. Use correction to reconcile an erroneous terminal action.
8. Inspect audit history and reconstruct LLM and Timefold attempts from stored versions, input/output, timing, outcomes, and domain score components without runtime logs.

## Validate MySQL and PostgreSQL profiles

Start one database at a time:

```bash
docker compose up -d mysql
SPRING_PROFILES_ACTIVE=mysql,demo ./mvnw spring-boot:run
```

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=postgres,demo ./mvnw spring-boot:run
```

Expected on a fresh database: Flyway runs V1 then V2 and demo fixtures are available.

### Controlled one-time adoption of a pre-Flyway database

Use this procedure once against a backup of a populated PetClinic schema that predates Flyway:

1. Keep a full backup of the target database.
2. Start with the `migration` profile in addition to the store profile, for example `SPRING_PROFILES_ACTIVE=mysql,migration` or `SPRING_PROFILES_ACTIVE=postgres,migration`.
3. Flyway baselines the existing catalog at version 1 (`spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=1` in `application-migration.properties`) and then applies `V2__smart_appointment_scheduling.sql`.
4. Confirm owner, pet, veterinarian, specialty, and visit row counts are unchanged and that legacy visits have a null `appointment_id`.
5. Remove the `migration` profile after this one-time adoption so later startups do not baseline other databases.

`LegacyDataPreservationAcceptanceTests` covers the fresh and one-time-baseline record-count/constraint checks on H2, MySQL, and PostgreSQL.

For each database, repeat the automated reservation race. Exactly one transaction may reserve overlapping veterinarian/pet blocks; the loser must receive a controlled conflict or stale-rerun outcome, never a partial hold.

## Completion checklist

- Both Maven and Gradle suites pass on Java 21.
- H2, MySQL, and PostgreSQL migration/data-preservation checks pass.
- All automated owner suggestions have a Timefold execution reference.
- All LLM calls use schema v1, at most two attempts, and one 10-second deadline.
- All Timefold attempts, including one permitted stale rerun, share one five-second deadline.
- No stale solver result becomes an Offer.
- Authorization, ownership, CSRF, stale form, and session-reset checks pass.
- Manual desktop owner/staff journeys satisfy the UX behavior above.
- No mobile or formal accessibility conformance claim is made for the POC.
- Owner/staff copy uses text-plus-icon field issues, explicit confirmation language with no undo, and a desktop-only POC notice (`scheduling.poc.desktop`).
