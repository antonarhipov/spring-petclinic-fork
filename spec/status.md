# Use-Case Status: Smart Appointment Scheduling

## Current

- Use case: none
- Status: APPROVED
- Next eligible: UC-2, UC-3, UC-4, UC-5, UC-6, UC-7, UC-8

## Progress

| Use case | Status | Depends on | Implementation | Convergence |
|---|---|---|---|---|
| UC-1 | APPROVED | none | `0261b04` | [APPROVED](convergence/UC-1.md) - walkthrough passed |
| UC-2 | NOT_STARTED | UC-1 | - | - |
| UC-3 | NOT_STARTED | UC-1 | - | - |
| UC-4 | NOT_STARTED | UC-1 | - | - |
| UC-5 | NOT_STARTED | UC-1 | - | - |
| UC-6 | NOT_STARTED | UC-1 | - | - |
| UC-7 | NOT_STARTED | UC-1 | - | - |
| UC-8 | NOT_STARTED | UC-1 | - | - |

## UC-1 Evidence

- Started: 2026-09-07T21:52:06+02:00
- Started from: `2b8fe6fc52c133b9bdbc3a06026f2903ff00fc43`
- Revision started from: `04e5d3b` for convergence findings C-1 and K-1
- Pre-existing dirty files: none
- Implementation submission: HEAD at convergence
- Changed files:
  - Build and runtime support: `.gitignore`, `README.md`, `pom.xml`, `src/main/resources/application.properties`, `src/test/resources/application-test.properties`, `src/main/java/org/springframework/samples/petclinic/PetClinicRuntimeHints.java`, `src/main/java/org/springframework/samples/petclinic/system/FlywayConfiguration.java`.
  - Removed unsupported paths: `.github/workflows/deploy-and-test-cluster.yml`, `.github/workflows/gradle-build.yml`, `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `docker-compose.yml`, `k8s/db.yml`, `k8s/petclinic.yml`, `src/main/resources/application-mysql.properties`, `src/main/resources/application-postgres.properties`, `src/main/resources/db/h2/data.sql`, `src/main/resources/db/h2/schema.sql`, `src/main/resources/db/mysql/data.sql`, `src/main/resources/db/mysql/petclinic_db_setup_mysql.txt`, `src/main/resources/db/mysql/schema.sql`, `src/main/resources/db/mysql/user.sql`, `src/main/resources/db/postgres/data.sql`, `src/main/resources/db/postgres/petclinic_db_setup_postgres.txt`, `src/main/resources/db/postgres/schema.sql`, `src/test/java/org/springframework/samples/petclinic/MySqlIntegrationTests.java`, `src/test/java/org/springframework/samples/petclinic/MysqlTestApplication.java`, `src/test/java/org/springframework/samples/petclinic/PostgresIntegrationTests.java`.
  - Authentication and scoped web surface: `src/main/java/org/springframework/samples/petclinic/system/LoginController.java`, `src/main/java/org/springframework/samples/petclinic/system/NavigationModelAdvice.java`, `src/main/java/org/springframework/samples/petclinic/system/RoleAwareAuthenticationSuccessHandler.java`, `src/main/java/org/springframework/samples/petclinic/system/SecurityConfig.java`, `src/main/java/org/springframework/samples/petclinic/system/WelcomeController.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/security/UserAccount.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/security/UserAccountRepository.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/security/UserRole.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/MyAppointmentsController.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/AuthenticatedOwnerService.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/MyPetsController.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerResourceNotFoundAdvice.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/OwnerResourceNotFoundException.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestState.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/RequestStatusController.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequest.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/SchedulingRequestRepository.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/StaffQueueController.java`.
  - Migrations: `src/main/resources/db/migration/V1__stock_schema_and_data.sql`, `src/main/resources/db/migration/V2__scheduling_schema.sql`, `src/main/resources/db/migration/V3__seed_accounts.sql`, `src/main/resources/db/migration/V4__seed_clinic_configuration.sql`.
  - Presentation and localization: `src/main/resources/templates/error.html`, `src/main/resources/templates/fragments/layout.html`, `src/main/resources/templates/login.html`, `src/main/resources/templates/my/appointments.html`, `src/main/resources/templates/my/pets.html`, `src/main/resources/templates/owners/ownerDetails.html`, `src/main/resources/templates/owners/ownersList.html`, `src/main/resources/templates/pets/createOrUpdateVisitForm.html`, `src/main/resources/templates/staff/queue.html`, `src/main/resources/messages/messages.properties`, `src/main/resources/messages/messages_de.properties`, `src/main/resources/messages/messages_en.properties`, `src/main/resources/messages/messages_es.properties`, `src/main/resources/messages/messages_fa.properties`, `src/main/resources/messages/messages_hi.properties`, `src/main/resources/messages/messages_ja.properties`, `src/main/resources/messages/messages_ko.properties`, `src/main/resources/messages/messages_pt.properties`, `src/main/resources/messages/messages_ru.properties`, `src/main/resources/messages/messages_tr.properties`, `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`, `src/main/java/org/springframework/samples/petclinic/owner/PetController.java`, `src/main/java/org/springframework/samples/petclinic/owner/VisitController.java`.
  - Verification: `src/test/java/org/springframework/samples/petclinic/system/AuthenticationE2ETests.java`, `src/test/java/org/springframework/samples/petclinic/system/AuthenticationWebTests.java`, `src/test/java/org/springframework/samples/petclinic/system/SecurityMatrixWebTests.java`, `src/test/java/org/springframework/samples/petclinic/system/RepositoryScopeTests.java`, `src/test/java/org/springframework/samples/petclinic/system/I18nPropertiesSyncTest.java`, `src/test/java/org/springframework/samples/petclinic/scheduling/PresentationShellTests.java`, `src/test/java/org/springframework/samples/petclinic/scheduling/request/OwnerHttpSurfaceTests.java`, `src/test/java/org/springframework/samples/petclinic/scheduling/SeedMigrationTests.java`, `src/test/java/org/springframework/samples/petclinic/scheduling/support/TestDataIsolationTests.java`, `src/test/java/org/springframework/samples/petclinic/PetClinicConcurrencyTests.java`, `src/test/java/org/springframework/samples/petclinic/PetClinicIntegrationTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/PetControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/owner/VisitControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/service/ClinicServiceTests.java`, `src/test/java/org/springframework/samples/petclinic/system/CrashControllerIntegrationTests.java`, `src/test/java/org/springframework/samples/petclinic/system/WelcomeControllerTests.java`, `src/test/java/org/springframework/samples/petclinic/vet/VetControllerTests.java`.
- Commands and results:
  - `./mvnw -q spring-javaformat:apply` - PASS.
  - Ambient-JDK diagnostic focused run - 30 tests, 5 context errors because JDK 25 could not self-attach Mockito; rerun on the required JDK 21 with the Byte Buddy agent.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q clean -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AuthenticationWebTests,SecurityMatrixWebTests,PresentationShellTests,OwnerHttpSurfaceTests,SeedMigrationTests,RepositoryScopeTests,I18nPropertiesSyncTest,TestDataIsolationTests test` - PASS, 30 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ClinicServiceTests,TestDataIsolationTests test` - PASS, 15 tests, 0 failures, 0 errors, 0 skipped; both contexts used unique in-memory H2 URLs.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test` - PASS, 101 tests, 0 failures, 0 errors, 0 skipped.
  - Revision diagnostic `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -Dtest=I18nPropertiesSyncTest test` initially failed 1 of 3 tests because the new action message keys were absent; the keys were then added with English placeholder values to all eleven bundles and the same command passed 3 tests.
  - Revision focused run: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q clean -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=AuthenticationWebTests,SecurityMatrixWebTests,PresentationShellTests,OwnerHttpSurfaceTests,SeedMigrationTests,RepositoryScopeTests,I18nPropertiesSyncTest,TestDataIsolationTests test` - PASS, 31 tests, 0 failures, 0 errors, 0 skipped.
  - Revision full suite: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test` - PASS, 102 tests, 0 failures, 0 errors, 0 skipped.
  - Surefire scan for `jdbc:h2:file` plus `find data -type f` - no runtime database reference and no runtime data file.
  - `git diff --check` - PASS; test execution created no tracked change.

| Contract element | Evidence |
|---|---|
| UC-1 main steps 1-5 | Real-server owner and staff journeys in `AuthenticationE2ETests.java:41`; seeded-account role/session checks in `AuthenticationWebTests.java:38`; rendered menus in `PresentationShellTests.java:39`. |
| UC-1 extension 3a | Unknown-user and wrong-password responses are normalized and compared in `AuthenticationE2ETests.java:67` and `AuthenticationWebTests.java:67`; no session reaches a protected page. |
| UC-1 extension 4a | Real logout and post-logout denial in `AuthenticationE2ETests.java:94`; explicit session invalidation in `AuthenticationWebTests.java:100`. |
| UC-1 extension 5a | All staff route shapes are exercised as owner with 403, disclosure checks, and before/after database equality in `SecurityMatrixWebTests.java:78`. |
| UC-1 extension 5b | Foreign and unknown owner-scoped status requests have the same standard 404 and unchanged rows in `OwnerHttpSurfaceTests.java:47`; repeated through the real server in `AuthenticationE2ETests.java:105`. |
| UC-1 extension 5c | Every `/my/**` route shape is exercised as staff with 403, disclosure checks, and unchanged rows in `SecurityMatrixWebTests.java:113`. |
| UC-1 extension 1a | Anonymous health succeeds while other actuator endpoints require login in `AuthenticationWebTests.java:77` and the real-server journey at `AuthenticationE2ETests.java:82`. |
| UC-1 extension 1b | Real-server login and stylesheet responses are 200 in `AuthenticationE2ETests.java:45` and `AuthenticationE2ETests.java:84`. |
| UC-1 extension 1c | The application route inventory is exercised anonymously for GET and POST in `SecurityMatrixWebTests.java:34` and `SecurityMatrixWebTests.java:56`, with database equality for denied mutations at `SecurityMatrixWebTests.java:95`. |
| UC-1 extension 5d | Authenticated staff reaches actuator and H2 console through the real server in `AuthenticationE2ETests.java:98`. |
| UC-1 G1-G4 | Single security chain in `SecurityConfig.java:23`; whole-route denial and no-mutation evidence in `SecurityMatrixWebTests.java:56`; owner-derived status scope in `RequestStatusController.java:22`. |
| UC-1 G5-G6 | Shared-layout, no-inline-style, identity, logout, and exact-menu assertions in `PresentationShellTests.java:31`; shared layout implementation in `templates/fragments/layout.html:13`. |
| UC-1 G7 and G10 | Staff pet/visit labels and actions now resolve through message keys in `createOrUpdatePetForm.html:20` and `createOrUpdateVisitForm.html:32`; template/Java visible-string scanning, Thymeleaf-expression bypass fixtures, and exact eleven-bundle key parity are enforced in `I18nPropertiesSyncTest.java:85`, `I18nPropertiesSyncTest.java:116`, and `I18nPropertiesSyncTest.java:174`. |
| UC-1 G8 | Full known route lists plus anonymous, owner, and staff matrix with response disclosure and database snapshots in `SecurityMatrixWebTests.java:34`. |
| UC-1 G9 | Automated rendered-page evidence is complete in `PresentationShellTests.java:31`; human walkthrough remains for convergence. |
| UC-1 success postcondition | Real form-login sessions land at the correct role workspace in `AuthenticationE2ETests.java:41`. |
| UC-1 minimal guarantee | Invalid, anonymous, wrong-role, and cross-owner paths assert denial, no disclosure, and unchanged database state in `AuthenticationE2ETests.java:67`, `SecurityMatrixWebTests.java:78`, and `OwnerHttpSurfaceTests.java:47`. |
| RULE-1 | Controllers are limited to authentication, landing-page rendering, principal resolution, and repository delegation; `RequestStatusController.java:22` contains no lifecycle or matching decision. |
| RULE-7 | Flyway is the sole initializer; four fresh migrations and field-by-field schema/data assertions run in `SeedMigrationTests.java:44`. |
| RULE-8 | Accounts, BCrypt credentials, specialties, veterinarian associations, hours, 17 blocks, six exceptions, zero leave/closures, and settings are asserted by value in `SeedMigrationTests.java:44`, `SeedMigrationTests.java:170`, and `SeedMigrationTests.java:210`. |
| RULE-9 | Form login, CSRF, BCrypt, logout, and full path matrix are configured in `SecurityConfig.java:23` and exercised in `SecurityMatrixWebTests.java:34`. |
| RULE-10 | `/my/**` owner identity comes from the principal in `AuthenticatedOwnerService.java:20`; repository-scoped lookup and indistinguishable 404 evidence are in `OwnerHttpSurfaceTests.java:47`. |
| RULE-14 | The owner-scoped endpoint returns exactly one `state` field in `RequestStatusController.java:22`, asserted byte-for-byte in `OwnerHttpSurfaceTests.java:37`. |
| RULE-16 | One shared PetClinic layout with role menus, identity, and CSRF logout is implemented at `templates/fragments/layout.html:13` and rendered in `PresentationShellTests.java:31`. |
| RULE-17 | Exact bundle parity and hard-coded visible text/status/Thymeleaf-expression scans are enforced by `I18nPropertiesSyncTest.java:85`; all eleven bundles contain the new action keys. |
| RULE-18 | Real-server UC-1 journey is `AuthenticationE2ETests.java:41`; final suite is 101/0/0/0; profile and runtime-data isolation guards are `TestDataIsolationTests.java:48`. |
| RULE-24 | Maven/H2-only dependency, path, property, README, and permanent-deletion checks are in `RepositoryScopeTests.java:37`. |

## Blockers

None.

## Deviations

None.
