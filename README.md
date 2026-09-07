# Spring PetClinic with Smart Appointment Scheduling

This fork adds guided appointment scheduling to the Spring PetClinic sample.
The application uses Maven, Java 17 or newer, and an H2 database managed by
Flyway. Runtime data is stored under `./data`; automated tests use isolated
in-memory H2 databases.

## Prerequisites

- Java 17 or newer
- Ollama 0.13.1 or newer
- The `ministral-3:14b` Ollama model

Install and start Ollama, then fetch the model:

```bash
ollama pull ministral-3:14b
```

Ollama must be reachable at `localhost:11434`.

The scheduling assistant accepts English input only. Enter symptoms,
preferences, and scheduling constraints in English.

## Run the application

```bash
./mvnw spring-boot:run
```

Open `localhost:8080` in a browser. The application creates its H2 database in
`./data` on first startup and applies all Flyway migrations automatically.

## Run the tests

```bash
./mvnw test
```

Tests that load the database activate the isolated test profile and use a
unique in-memory H2 database.

## License

Spring PetClinic is released under the Apache License 2.0.
