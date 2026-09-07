# Convergence: UC-1 - Sign in and enter the permitted workspace

## Summary

- Submission: `spec/checkpoints/UC-1.md` at `0261b04225b2a9242fad2195f5058443337c3c11`
- Verdict: PENDING WALKTHROUGH
- Findings: 0 critical, 0 gaps, 0 protocol, 0 drift, 0 cosmetic
- Suite: 102 run, 0 failed, 0 errors, 0 skipped
- Working tree impact from verification: none

## Protocol Gate

1. PASS - UC-1 is the only target and was `READY_FOR_CONVERGENCE` before this report.
2. PASS - The replacement `spec/checkpoints/UC-1.md` and revised implementation are committed together at `0261b04225b2a9242fad2195f5058443337c3c11`; the original implementation base is `2b8fe6fc52c133b9bdbc3a06026f2903ff00fc43` and the prior rejected convergence boundary is `04e5d3b`.
3. PASS - UC-1 has no Requires, Includes, or Extends dependencies.
4. PASS - UC-2 through UC-8 are `NOT_STARTED`; no other use case is active or ready.
5. PASS - The replacement checkpoint maps the main scenario, all ten extensions, all ten guarantees, both postconditions, and every applicable rule to evidence; it records the 31-test focused run, 102-test full run, complete implementation file set, resolved findings, and absence of approved-UC regressions.
6. PASS - Inspection of the original 96-path implementation and the 17-path revision found only UC-1 authentication, authorization, localization, presentation, Flyway/normative seed, H2/Maven-boundary work, supporting tests, required removals, and the resolved convergence artifacts. No later use-case action is exposed beyond the owner-scoped state-only status resource required by UC-1/RULE-14.

## Runtime Reproduction

| Actor | Step or extension | Executor reported | Converge observed |
|---|---|---|---|
| Anonymous | Main steps 1-2 | Root requires login; login page uses the PetClinic layout and discloses no protected data. | Real-server tests returned 302 `/login` for `/`, then 200 for `/login`; the page contained the shared stylesheet and Sign in and omitted owner data. |
| Owner `george` | Main steps 3-5 | Valid credentials create a role-scoped session at My appointments with the exact owner menu, identity, and Logout. | Real-server form login returned 302 `/my/appointments`; the rendered page contained `george`, Logout, My pets, and My appointments and omitted every staff item. |
| Staff `staff` | Main steps 3-5 | Valid credentials create a role-scoped session at Scheduling queue with the exact staff menu, identity, and Logout. | Real-server form login returned 302 `/staff/queue`; the rendered page contained `staff`, Logout, established staff items, Scheduling queue, Calendar, and Clinic settings and omitted owner items. |
| Anonymous | Extensions 3a, 1a-1c | Invalid credentials are neutral; health and static assets are public; all other routes require login. | Unknown-user and wrong-password failures were byte-equivalent after CSRF normalization and created no usable session; health and stylesheet returned 200; protected and unknown routes redirected to login. |
| Owner `george` | Extensions 4a, 5a, 5b | Logout ends the session; staff routes return 403; foreign/unknown owner resources return indistinguishable 404 without mutation. | Logout returned to login and the old session was denied; staff routes returned 403 with unchanged database snapshots; foreign/unknown status responses were identical 404 pages and request rows were unchanged. |
| Staff `staff` | Extensions 5c, 5d | `/my/**` returns 403; H2 console and non-health operational endpoints are available. | Owner paths returned 403 with unchanged database snapshots; `/actuator` and `/h2-console/` returned 200. |

## Evidence Ledger

| Contract element | Executor claim | Evidence obtained | Strength | Verified |
|---|---|---|---|---|
| UC-1 main step 1 | Anonymous root is guarded. | `AuthenticationE2ETests.uc1MainOwnerAndStaffSignInAndEnterOnlyTheirWorkspace` observed the real-server redirect to `/login`. | STRONG | yes |
| UC-1 main step 2 | Login uses the common layout and discloses no data. | The real HTTP login page returned 200 with `petclinic.css` and no owner data; `PresentationShellTests` verified the one shared shell and no inline styles. | STRONG | yes |
| UC-1 main step 3 | Seeded credentials authenticate. | All 12 exact credentials authenticate with the expected role in `AuthenticationWebTests`; owner and staff do so through real HTTP in `AuthenticationE2ETests`. | STRONG | yes |
| UC-1 main step 4 | Session, identity, and Logout are present. | Real cookie sessions rendered the username and CSRF-protected Logout; post-logout use of the session was denied. | STRONG | yes |
| UC-1 main step 5 | Each role reaches the required landing and exact menu. | Real owner and staff form-login journeys asserted both landing redirects and all present/absent navigation items. | STRONG | yes |
| UC-1 extension 3a | Invalid login is neutral and creates no session. | Unknown-user and wrong-password pages were equal after CSRF normalization, omitted submitted values, and could not reach `/my/appointments`. | STRONG | yes |
| UC-1 extension 4a | Logout invalidates the session. | Real HTTP logout returned to login and the old browser session was denied; MockMvc also asserted session invalidation. | STRONG | yes |
| UC-1 extension 5a | Owner receives 403 from staff surfaces without disclosure or mutation. | Every inventoried staff route shape was exercised with GET/POST as owner; bodies omitted protected values and complete guarded-table snapshots were unchanged. | STRONG | yes |
| UC-1 extension 5b | Foreign and unknown owner resources are indistinguishable 404 responses without mutation. | MockMvc and real-server paths compared status/body and unchanged request rows for the owner-scoped status resource. | STRONG | yes |
| UC-1 extension 5c | Staff receives 403 from `/my/**` without mutation. | Every inventoried owner route shape was exercised with GET/POST as staff; bodies omitted owner data and guarded-table snapshots were unchanged. | STRONG | yes |
| UC-1 extension 1a | Anonymous health is available alone among operational endpoints. | Real HTTP and MockMvc returned 200 for health and required login for actuator root/env. | STRONG | yes |
| UC-1 extension 1b | Anonymous login and static resources are available. | Real HTTP returned 200 for `/login` and `/resources/css/petclinic.css`. | STRONG | yes |
| UC-1 extension 1c | All other anonymous routes require authentication with no side effect. | Full known route-shape GET/POST inventory redirected to login; guarded-table snapshots were unchanged. | STRONG | yes |
| UC-1 extension 5d | Staff can reach H2 console and other operational endpoints. | Real HTTP returned 200 for `/h2-console/` and `/actuator` as staff. | STRONG | yes |
| UC-1 G1 | Public access is limited to login, static resources, and health. | Single security chain plus route-inventory and real-server tests cover the public and protected surfaces. | STRONG | yes |
| UC-1 G2 | Owner routes are `/my/**`, principal-derived, id-free for owner, and clinic management is staff-only. | `AuthenticatedOwnerService` resolves username to owner; the status repository query applies that owner id; security tests guard every established staff handler. | STRONG | yes |
| UC-1 G3 | Authorization is enforced at the boundary. | Real security-filter-chain responses, rather than menu hiding alone, enforce each actor matrix. | STRONG | yes |
| UC-1 G4 | Guard failures disclose and mutate nothing. | Response-body comparisons and before/after snapshots establish denial and no side effects, including indistinguishable cross-owner 404. | STRONG | yes |
| UC-1 G5 | Every page uses the shared presentation system. | All non-fragment templates reference the sole layout; rendered login/owner/staff/error evidence uses the PetClinic stylesheet and no inline styles. | STRONG | yes |
| UC-1 G6 | Menus expose only the role's actions and every authenticated page has identity/Logout. | The shared layout selects the exact owner/staff menus and injects identity/CSRF logout for all templates; rendered landings verify both variants. | STRONG | yes |
| UC-1 G7 | All visible text resolves through keys in eleven bundles. | The previously hard-coded pet/visit labels and actions now resolve through keys at `createOrUpdatePetForm.html:20-27` and `createOrUpdateVisitForm.html:32-33`; all eleven bundles have exact key parity and the new action keys. | STRONG | yes |
| UC-1 G8 | Automated security evidence covers the whole route space, disclosure, and side effects. | Handler-shape inventory plus future scoped action shapes are exercised as anonymous, owner, and staff with body and database assertions. | STRONG | yes |
| UC-1 G9 | Owner/staff rendered walkthrough is required before acceptance. | Automated DOM evidence passed; the human walkthrough below is now the only pending acceptance evidence. | STRONG | pending walkthrough |
| UC-1 G10 | Localization scanning fails on bypassed messages or incomplete bundles. | `I18nPropertiesSyncTest.java:85-187` scans all production templates/Java and exact bundle parity; the explicit fixture proves direct, text-assignment, and fragment-label Thymeleaf literals are rejected while internal field/type literals are ignored. | STRONG | yes |
| UC-1 success postcondition | Authenticated actors have the correct role-scoped landing/session. | Real form-login cookie journeys reached `/my/appointments` and `/staff/queue` with exact role presentation. | STRONG | yes |
| UC-1 minimal guarantee | Invalid and guarded attempts disclose and mutate nothing. | Real-server and MockMvc negative paths compare bodies, sessions, and complete relevant database snapshots. | STRONG | yes |
| UC-1 relations | No relations. | Specification and map both declare Requires, Includes, and Extends as none. | STRONG | yes |

## Rule Conformance

| Rule | Constraint | Evidence | Result |
|---|---|---|---|
| RULE-1 | Scheduling responsibilities are separated and controllers contain no lifecycle or matching decisions. | The submitted UC-1 code is split between security, request, appointment, and system web packages; controllers resolve/delegate only. | PASS |
| RULE-7 | Flyway exclusively creates and seeds the schema and local date/time fields remain lossless. | `FlywayConfiguration` runs the four migrations; SQL initialization is removed; fresh migration tests compare schema and seed values. | PASS |
| RULE-8 | Exact normative seeds and BCrypt credentials are required. | Fresh-database assertions compare every account/role/link/password, specialty association, opening hour, 17 block, six exception, empty leave/closure set, and setting. | PASS |
| RULE-9 | One path security chain, generic form failure, CSRF, BCrypt, and logout invalidation are required. | `SecurityConfig.java:23-67` and the full actor route matrix reproduce these behaviors. | PASS |
| RULE-10 | Every `/my/**` operation derives owner scope from the principal and foreign/unknown identifiers share 404. | `AuthenticatedOwnerService.java:20-27`, the owner-scoped repository query, and exact 404/no-mutation comparisons satisfy the constraint. | PASS |
| RULE-14 | Owner status returns exactly one state field. | `OwnerHttpSurfaceTests.java:37-44` compares the exact JSON `{"state":"AWAITING_CONSENT"}`. | PASS |
| RULE-16 | Every page uses the common layout and role-current presentation. | Sole-layout static inspection and rendered login/owner/staff/error checks pass. | PASS |
| RULE-17 | All visible template and server text must use keys present in all eleven bundles. | Production scanning, explicit scanner-negative fixtures, exact eleven-bundle parity, and message-key-backed staff form labels/actions all pass. | PASS |
| RULE-18 | Real-server journey and direct evidence for every contract element are required without test data impact. | The real actor journey and all mapped evidence are strong; the focused suite is 31/0/0/0, full suite is 102/0/0/0, and verification left no runtime or tracked data. | PASS |
| RULE-24 | Only Maven/H2 support may remain and README must document the boundary. | Repository/dependency inventory, runtime/test properties, and README assertions pass; unsupported database/build/deployment paths are absent and the test command is now `./mvnw test`. | PASS |

## Related-UC Regression

| Use case | Relationship/shared surface | Evidence | Result |
|---|---|---|---|
| None | UC-1 is the first use case and has no approved dependency or shared-surface regression target. | UC-2 through UC-8 remain `NOT_STARTED`. | N/A |

## Findings

No active findings.

- Prior C-1 is resolved: visible staff-form copy now uses message keys and the scanner has a regression fixture for Thymeleaf expression literals.
- Prior K-1 is resolved: README now documents the existing `./mvnw test` command.

## Walkthrough

User result: pending.

1. Start the application with `./mvnw spring-boot:run` and open `/` in a private browser window. Confirm redirect to the PetClinic-styled Sign in page and that no owner or clinic data is visible.
2. Submit `george` with an incorrect password. Confirm the page says only “Invalid username or password,” remains at Sign in, and exposes neither which value was wrong nor protected data.
3. Sign in as `george` / `george123`. Confirm the landing is My appointments; the only navigation actions are My pets and My appointments; `george` and Logout are visible.
4. While signed in as George, open `/owners/2`. Confirm a 403 response and that Betty's record is not shown. Open `/my/requests/999999/status`; confirm the normal PetClinic 404 page without request or owner data.
5. Log out. Confirm return to Sign in and that reopening `/my/appointments` requires authentication.
6. Sign in as `staff` / `staff123`. Confirm the landing is Scheduling queue; navigation contains Home, Find Owners, Veterinarians, Scheduling queue, Calendar, Clinic settings, and Error; My pets and My appointments are absent; `staff` and Logout are visible.
7. As staff, open `/owners/2` and confirm the established owner page uses the same layout. Then open `/my/pets` and confirm a 403 response with no owner data.
8. Log out and report PASS, or provide the step number and observed mismatch.

## Status Update

`READY_FOR_CONVERGENCE` -> `PENDING_WALKTHROUGH`; no later use case is eligible until the user confirms UC-1 G9.

## Response to execute

PENDING WALKTHROUGH: complete the eight UC-1 owner/staff checks above and report PASS or the failing step.
