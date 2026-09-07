# Use-Case Checkpoint: UC-1 - Sign in and enter the permitted workspace

## Summary

- Status: READY_FOR_CONVERGENCE
- Base commit: `2b8fe6fc52c133b9bdbc3a06026f2903ff00fc43`
- Submission commit: HEAD at convergence
- Relations verified: none; UC-1 has no Requires, Includes, or Extends relations

## Contract Evidence

| Contract element | Test or runtime evidence | Result |
|---|---|---|
| UC-1 main steps 1-5 | `AuthenticationE2ETests.java:41` drives owner and staff form-login journeys through a real server; `AuthenticationWebTests.java:38` verifies seeded roles and sessions; `PresentationShellTests.java:39` verifies the rendered role menus. | PASS |
| UC-1 extension 3a | `AuthenticationE2ETests.java:67` and `AuthenticationWebTests.java:67` compare normalized failures for unknown users and wrong passwords and prove that neither reaches a protected page. | PASS |
| UC-1 extension 4a | `AuthenticationE2ETests.java:94` proves logout and post-logout denial; `AuthenticationWebTests.java:100` proves session invalidation. | PASS |
| UC-1 extension 5a | `SecurityMatrixWebTests.java:78` exercises all staff route shapes as an owner and compares protected disclosure and database state before and after each 403 response. | PASS |
| UC-1 extension 5b | `OwnerHttpSurfaceTests.java:47` compares foreign and unknown owner-scoped status responses and unchanged database rows; `AuthenticationE2ETests.java:105` repeats the boundary check through the real server. | PASS |
| UC-1 extension 5c | `SecurityMatrixWebTests.java:113` exercises every `/my/**` route shape as staff and verifies 403, no disclosure, and unchanged database state. | PASS |
| UC-1 extension 1a | `AuthenticationWebTests.java:77` and `AuthenticationE2ETests.java:82` prove anonymous health access while other actuator endpoints require authentication. | PASS |
| UC-1 extension 1b | `AuthenticationE2ETests.java:45` and `AuthenticationE2ETests.java:84` prove anonymous login-page and stylesheet access. | PASS |
| UC-1 extension 1c | `SecurityMatrixWebTests.java:34` and `SecurityMatrixWebTests.java:56` exercise the complete application route inventory anonymously; `SecurityMatrixWebTests.java:95` proves denied mutations leave the database unchanged. | PASS |
| UC-1 extension 5d | `AuthenticationE2ETests.java:98` proves staff access to the H2 console and operational endpoints through the real server. | PASS |
| UC-1 G1-G4 | `SecurityConfig.java:23`, `SecurityMatrixWebTests.java:56`, and `RequestStatusController.java:22` establish the single security boundary, protected-route denial, mutation absence, and principal-derived owner status scope. | PASS |
| UC-1 G5-G6 | `PresentationShellTests.java:31` proves the shared layout, absence of inline styling, exact role menus, identity, and Logout; `templates/fragments/layout.html:13` supplies the shared shell. | PASS |
| UC-1 G7 | `I18nPropertiesSyncTest.java:72` scans template and Java visible text and verifies exact key parity across all eleven locale bundles. | PASS |
| UC-1 G8 | `SecurityMatrixWebTests.java:34` inventories known routes and exercises anonymous, owner, and staff access with disclosure and database-snapshot assertions. | PASS |
| UC-1 G9 | `PresentationShellTests.java:31` supplies automated rendered-page evidence; the required human owner/staff walkthrough remains for convergence. | PASS |
| UC-1 G10 | `I18nPropertiesSyncTest.java:72` and `I18nPropertiesSyncTest.java:103` enforce message-key use and all-bundle parity. | PASS |
| UC-1 success postcondition | `AuthenticationE2ETests.java:41` proves real form-login sessions land in the correct role-scoped workspace. | PASS |
| UC-1 minimal guarantee | `AuthenticationE2ETests.java:67`, `SecurityMatrixWebTests.java:78`, and `OwnerHttpSurfaceTests.java:47` prove invalid, anonymous, wrong-role, and cross-owner attempts disclose and mutate nothing. | PASS |

## Rule Evidence

| Rule | Evidence | Result |
|---|---|---|
| RULE-1 | Controllers are limited to actor resolution and delegation; `RequestStatusController.java:22` delegates the owner-scoped lookup and contains no lifecycle or matching decision. | PASS |
| RULE-7 | Four production Flyway migrations exclusively initialize schema and data; `SeedMigrationTests.java:44` verifies a fresh migration and schema/data values. | PASS |
| RULE-8 | `SeedMigrationTests.java:44`, `SeedMigrationTests.java:170`, and `SeedMigrationTests.java:210` compare accounts, BCrypt credentials, specialties, veterinarian associations, clinic hours, 17 blocks, six exceptions, zero leave/closures, and configuration defaults by value. | PASS |
| RULE-9 | `SecurityConfig.java:23` configures form login, generic failure, CSRF, BCrypt, logout, and the path matrix; `SecurityMatrixWebTests.java:34` exercises it. | PASS |
| RULE-10 | `AuthenticatedOwnerService.java:20` derives owner scope from the principal; `OwnerHttpSurfaceTests.java:47` proves indistinguishable foreign/unknown 404 responses without mutation. | PASS |
| RULE-14 | `RequestStatusController.java:22` returns the one-field state response; `OwnerHttpSurfaceTests.java:37` compares the exact JSON response. | PASS |
| RULE-16 | `templates/fragments/layout.html:13` supplies one shared PetClinic layout with role menus, identity, and CSRF logout; `PresentationShellTests.java:31` verifies rendered pages. | PASS |
| RULE-17 | `I18nPropertiesSyncTest.java:72` enforces exact bundle parity and rejects hard-coded visible template or Java messages. | PASS |
| RULE-18 | `AuthenticationE2ETests.java:41` is the real-server actor journey; every extension and guarantee is mapped above; `TestDataIsolationTests.java:48` enforces test/runtime database isolation. | PASS |
| RULE-24 | `RepositoryScopeTests.java:37` proves the Maven/H2-only dependency, path, property, README, and removed-artifact boundary. | PASS |

## Validation

- Focused commands:
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q clean -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AuthenticationWebTests,SecurityMatrixWebTests,PresentationShellTests,OwnerHttpSurfaceTests,SeedMigrationTests,RepositoryScopeTests,I18nPropertiesSyncTest,TestDataIsolationTests test` - PASS, 30 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ClinicServiceTests,TestDataIsolationTests test` - PASS, 15 tests, 0 failures, 0 errors, 0 skipped; each context used a unique in-memory H2 URL.
- Full relevant suite: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test` - PASS, 101 tests, 0 failures, 0 errors, 0 skipped.
- Working tree impact from tests: none; no tracked change, runtime database reference, or runtime data file was produced.
- Runtime evidence: owner `george` and staff `staff` logged in through real HTTP form-login sessions, reached `/my/appointments` and `/staff/queue` respectively, retained identity and Logout, and were denied the other role's surface; invalid login, logout, health, static, console, actuator, and cross-owner paths were also reproduced through the real server.
- Changed files: the complete implementation file set is recorded under `UC-1 Evidence` in `spec/status.md`; it consists of the Maven/H2 support boundary, Flyway migrations and seeds, authentication and owner-scoping code, shared templates/localization, removal of unsupported build/database/deployment paths, and UC-1 verification tests.
- Approved UCs regression-tested: none; UC-1 is the first use case.

## Notes

The ambient-JDK diagnostic run failed because JDK 25 could not self-attach Mockito. All reported verification used the project's required JDK 21 and an explicit Byte Buddy agent. Human verification remains limited to the UC-1 G9 owner/staff walkthrough required by convergence.

READY FOR CONVERGENCE: UC-1
