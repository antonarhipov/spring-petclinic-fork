# Spring PetClinic with Smart Appointment Scheduling [![Build Status](https://github.com/spring-projects/spring-petclinic/actions/workflows/maven-build.yml/badge.svg)](https://github.com/spring-projects/spring-petclinic/actions/workflows/maven-build.yml)

## Overview

This is an extended Spring PetClinic application featuring **Smart Appointment Scheduling** — a comprehensive system enabling pet owners to book veterinary appointments through natural language requests, with AI-powered interpretation and deterministic Timefold-optimized slot matching. Clinic staff manage availability, handle fallback requests, and track appointment outcomes with full audit trails.

### Key Features

- **Owner Smart Scheduling**: Pet owners describe appointment needs in natural language, review AI-interpreted structured details, and receive a single held appointment offer within 10 minutes.
- **AI Interpretation**: Spring AI integration with Ollama for natural language processing with strict schema validation and 10-second timeout.
- **Deterministic Timefold Matching**: Single-threaded solver with 6 lexicographical soft constraint levels, completing within 5 seconds.
- **Staff Availability Management**: Recurring shifts, date-specific exceptions, veterinarian leave, and clinic closures with strict precedence rules.
- **Direct Booking**: Staff can directly schedule appointments with conflict detection and owner agreement recording.
- **Fallback Queue**: Automatic routing for declined consent, ambiguous requests, or system failures with staff claim/unclaim/reassign workflow.
- **Appointment Lifecycle**: Owner request revisions, offer rejection/expiry/retry (up to 5 automatic offers), withdrawal, and cancellation.
- **Appointment Outcomes**: Staff completion/no-show recording with clinical notes, append-only audited corrections, and legacy visit reconciliation.
- **Role-Based Security**: Distinct `OWNER` and `STAFF` roles with 30-minute interactive session timeout, CSRF protection, and temporary password enforcement.
- **Sensitive Data Protection**: AES-256-GCM envelope encryption for prose, consent records, clinical notes, and AI responses.
- **Audit & History**: Append-only audit events and owner history with zero update/delete APIs.

## Technology Stack

- **Java 21** with Spring Boot 4.1.1
- **Maven** (Gradle removed)
- **H2 Database** (file-backed for demo, in-memory for testing)
- **Flyway** for database migrations (V1–V7)
- **Spring Security** with form login and role-based access control
- **Spring Data JPA** with Hibernate
- **Spring AI** with Ollama for natural language interpretation
- **Timefold Solver 2.5.0** for deterministic appointment matching
- **Thymeleaf** for server-side templating
- **ArchUnit** for architecture boundary testing

## Quick Start

### Prerequisites

- Java 21 or newer (full JDK)
- Maven 3.8.1 or newer
- Git
- (Optional) Ollama for live AI interpretation; tests use synthetic stubs by default

### Clone and Build

```bash
git clone https://github.com/spring-projects/spring-petclinic.git
cd spring-petclinic
./mvnw clean verify
```

### Run Locally

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080/`.

### Demo Credentials

The application seeds demo accounts on startup:

**Owner Portal:**
- Username: `owner1`
- Password: `owner1`

**Staff Portal:**
- Username: `staff1`
- Password: `staff1`

### Database

By default, the application uses an in-memory H2 database populated with demo data. For persistent storage, use the file-backed H2 profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2-file"
```

The H2 console is available at `http://localhost:8080/h2-console` (use `jdbc:h2:mem:petclinic` for in-memory or `jdbc:h2:file:./data/petclinic` for file-backed).

## Running Tests

Execute the full verification gate:

```bash
./mvnw -B verify
```

Run specific test suites:

```bash
# Account provisioning and password management
./mvnw -B test -Dtest='*Account*,*Password*'

# Scheduling acceptance journeys (all 15 demo scenarios)
./mvnw -B test -Dtest='SchedulingAcceptanceJourneyTests'

# Performance acceptance (Timefold matching <5s, workflow <20s)
./mvnw -B test -Dtest='SchedulingPerformanceAcceptanceTests'

# Key rotation and security regression
./mvnw -B test -Dtest='KeyRotationServiceTests,OperationalSecurityRegressionTests'

# Architecture boundary enforcement
./mvnw -B test -Dtest='ModuleBoundaryTests'
```

## Project Structure

```
src/main/java/org/springframework/samples/petclinic/
├── account/               # Account provisioning, password management
├── appointment/           # Appointment lifecycle, outcomes, direct booking
├── audit/                 # Audit events, protected payload encryption, key rotation
├── availability/          # Clinic policy, shifts, exceptions, leave, closures
├── config/                # Time, Ollama, Timefold configuration
├── owner/                 # Owner, Pet, Visit entities and controllers
├── scheduling/
│   ├── interpretation/    # AI interpretation, emergency keyword screening
│   ├── job/               # Background job worker with database leasing
│   ├── matching/          # Timefold solver and constraint provider
│   ├── offer/             # Offer lifecycle, holds, expiry
│   ├── queue/             # Staff fallback queue, assisted resolution
│   └── request/           # Scheduling request, revisions, workflow
├── security/              # Security configuration, session management
├── shared/                # Command service, time intervals, base entities
├── system/                # Welcome controller, exception handler, demo seeder
└── vet/                   # Veterinarian and specialty entities
```

## Documentation

- **[Smart Appointment Scheduling Specification](specs/001-smart-appointment-scheduling/spec.md)**: Complete functional and non-functional requirements.
- **[Quick Start Guide](specs/001-smart-appointment-scheduling/quickstart.md)**: Step-by-step demo walkthrough.
- **[Data Model](specs/001-smart-appointment-scheduling/data-model.md)**: Entity relationships and database schema.
- **[HTTP UI Contracts](specs/001-smart-appointment-scheduling/contracts/http-ui.md)**: API endpoints and request/response formats.

## Development

### Code Formatting

Apply Spring Java Format:

```bash
./mvnw spring-javaformat:apply
```

### IDE Setup

**IntelliJ IDEA:**
1. Open `pom.xml` via `File → Open`
2. Run `PetClinicApplication` main class
3. Navigate to `http://localhost:8080`

**VS Code:**
1. Install Extension Pack for Java
2. Open the project folder
3. Run the application via the Spring Boot Dashboard

## Architecture Highlights

### Concurrency & Calendar Locking

All calendar mutations (shifts, leaves, closures, direct bookings, hold creation) acquire a pessimistic lock on the singleton `CalendarState` row to prevent double-booking.

### Deterministic Matching

Timefold Solver operates on read-only snapshots with fixed random seeds, guaranteeing identical slot selection given the same input. Matching completes within 5 seconds.

### Append-Only Audit

All staff actions append to `audit_events`; all owner actions append to `owner_history_events`. Neither table exposes update or delete APIs.

### Session Lifecycle

- 30-minute interactive session timeout (polling on `/owner/requests/{id}/status` does not extend the session).
- Password resets increment `sessionVersion`, invalidating all existing sessions globally.
- Temporary passwords expire after 7 days and force password change on first login.

### Sensitive Data Encryption

Prose, consent records, clinical notes, and AI responses are encrypted using AES-256-GCM with versioned key envelopes. Key rotation rewraps envelopes without changing ciphertext.

## Contributing

For bug reports, feature requests, or pull requests, please use the [issue tracker](https://github.com/spring-projects/spring-petclinic/issues).

## License

The Spring PetClinic sample application is released under version 2.0 of the [Apache License](https://www.apache.org/licenses/LICENSE-2.0).
