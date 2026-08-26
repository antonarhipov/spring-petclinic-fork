# Technical Constraints: UC1 — Secure Scheduling Access

## Design

Spring Security form login fronts separate public, owner self-service, and staff route zones. A persistent account aggregate maps authentication to either an immutable Owner link or no domain link for staff. Security handlers delegate mutable identity operations to transactional services; resource services resolve owner-visible records through ownership-scoped repository methods.

## Rules

### UC1-RULE1
**Covers:** UC1-AC1–UC1-AC5, UC1-AC38, UC1-AC39
**MUST** configure one explicit `SecurityFilterChain` that allowlists only the specified public routes, requires `STAFF` for `/owners/**` and staff scheduling routes, requires authentication for owner self-service, retains CSRF for every state-changing request, and enables Spring Security's session-fixation migration on login. Thymeleaf authorization conditions MAY hide links but MUST NOT replace route rules.
**Reason:** The application currently has no security boundary, so the new route zones must be explicit and testable rather than dependent on controller conventions.

### UC1-RULE2
**Covers:** UC1-AC14–UC1-AC16, UC1-AC18–UC1-AC19, UC1-AC27–UC1-AC31, UC1-AC41–UC1-AC46
**MUST** persist accounts with a normalized `Locale.ROOT` lowercase username column protected by a database unique constraint, a BCrypt hash, role, active/change-required flags, nullable Owner foreign key, and optimistic version. The Owner foreign key MUST be unique when present, and role and Owner link MUST be immutable after insert. Provision, rename, deactivation, and bootstrap operations MUST lock one persistent account-administration guard row before validating cross-account invariants such as the final active staff account.
**Reason:** Database constraints protect identity uniqueness, while one portable guard row serializes invariants that span multiple account rows on all supported databases.

### UC1-RULE3
**Covers:** UC1-AC17–UC1-AC19, UC1-AC28–UC1-AC40
**MUST** register authenticated container sessions in Spring Security's `SessionRegistry` with `HttpSessionEventPublisher`. Authentication success/failure handlers MUST update failure state under a pessimistic account-row lock; identity-change services MUST expire every registered session for the account after their database transaction commits. Password replacement MUST compare the submitted value with the current temporary BCrypt hash through `PasswordEncoder.matches` and MUST never retain plaintext.
**Reason:** This implements the selected single-process session design while making lockout counters and post-change invalidation race-safe.

### UC1-RULE4
**Covers:** UC1-AC6–UC1-AC13
**MUST** resolve owner, pet, request, appointment, notification, and timeline records with repository queries containing both the resource identifier and the authenticated Owner identifier. Owner veterinarian views MUST use a projection containing only veterinarian name and active specialties; they MUST NOT reuse the staff calendar entity graph or DTO.
**Reason:** Query-scoped ownership produces indistinguishable absent/foreign results and prevents availability fields from leaking through lazy or future serialization changes.

### UC1-RULE5
**Covers:** UC1-AC20–UC1-AC26, UC1-AC45
**MUST** run bootstrap and demo initialization after successful Flyway migration through idempotent application services. Non-demo startup MUST validate or create the initial staff account while holding the account-administration guard; `demo-data` MUST insert only missing normalized usernames and MUST NOT update existing password hashes or change-required flags. A startup environment validator MUST reject `demo-data` combined with a production profile before the web server accepts traffic.
**Reason:** Initialization must remain repeatable without overwriting identity state or leaving a non-demo deployment unadministrable.

### UC1-RULE6
**Covers:** UC1-AC8–UC1-AC11, UC1-AC47
**MUST** bind owner self-service forms to allowlisted form objects rather than Owner or Pet entities, and every new validation, authorization, login, demo-warning, and scheduling message MUST use a message-bundle key in every existing locale bundle.
**Reason:** Form DTOs prevent mass assignment of identity and relationship fields, and the feature explicitly extends the existing localized UI contract.

## Cross-Reference

| AC | Rules |
|---|---|
| UC1-AC1 | UC1-RULE1 |
| UC1-AC2 | UC1-RULE1 |
| UC1-AC3 | UC1-RULE1 |
| UC1-AC4 | UC1-RULE1 |
| UC1-AC5 | UC1-RULE1 |
| UC1-AC6 | RULE-8, UC1-RULE4 |
| UC1-AC7 | RULE-8, UC1-RULE4 |
| UC1-AC8 | UC1-RULE4, UC1-RULE6 |
| UC1-AC9 | UC1-RULE4, UC1-RULE6 |
| UC1-AC10 | UC1-RULE4, UC1-RULE6 |
| UC1-AC11 | UC1-RULE6 |
| UC1-AC12 | UC1-RULE4 |
| UC1-AC13 | RULE-8, UC1-RULE4 |
| UC1-AC14 | UC1-RULE2 |
| UC1-AC15 | UC1-RULE2 |
| UC1-AC16 | UC1-RULE2 |
| UC1-AC17 | UC1-RULE3 |
| UC1-AC18 | UC1-RULE2, UC1-RULE3 |
| UC1-AC19 | UC1-RULE2, UC1-RULE3 |
| UC1-AC20 | UC1-RULE5 |
| UC1-AC21 | UC1-RULE5 |
| UC1-AC22 | UC1-RULE5 |
| UC1-AC23 | UC1-RULE5 |
| UC1-AC24 | UC1-RULE5 |
| UC1-AC25 | UC1-RULE5, UC1-RULE6 |
| UC1-AC26 | UC1-RULE5 |
| UC1-AC27 | UC1-RULE2 |
| UC1-AC28 | UC1-RULE2, UC1-RULE3 |
| UC1-AC29 | UC1-RULE2, UC1-RULE3 |
| UC1-AC30 | UC1-RULE2, UC1-RULE3 |
| UC1-AC31 | UC1-RULE2, UC1-RULE3 |
| UC1-AC32 | UC1-RULE3 |
| UC1-AC33 | UC1-RULE3 |
| UC1-AC34 | UC1-RULE3 |
| UC1-AC35 | UC1-RULE3 |
| UC1-AC36 | UC1-RULE3 |
| UC1-AC37 | UC1-RULE3 |
| UC1-AC38 | UC1-RULE1 |
| UC1-AC39 | UC1-RULE1 |
| UC1-AC40 | UC1-RULE3 |
| UC1-AC41 | UC1-RULE2, UC1-RULE3 |
| UC1-AC42 | UC1-RULE2 |
| UC1-AC43 | UC1-RULE2, UC1-RULE3 |
| UC1-AC44 | UC1-RULE2 |
| UC1-AC45 | UC1-RULE5 |
| UC1-AC46 | UC1-RULE2 |
| UC1-AC47 | UC1-RULE6 |

## Design Exclusions

- No self-registration, password recovery, OAuth, MFA, administrator role, or persistent Spring Session store
- No JPA entity binding for identity or ownership-sensitive forms
- No public or owner-visible actuator detail
