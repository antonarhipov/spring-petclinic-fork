# Use-Case Status: Smart Appointment Scheduling

## Current

- Use case: UC-4
- Status: PENDING_WALKTHROUGH
- Next eligible: none

## Progress

| Use case | Status | Depends on | Implementation | Convergence |
|---|---|---|---|---|
| UC-1 | APPROVED | none | `0261b04` | [APPROVED](convergence/UC-1.md) - walkthrough passed |
| UC-2 | APPROVED | UC-1 | `72f152e` | [APPROVED](convergence/UC-2.md) - walkthrough passed |
| UC-3 | APPROVED | UC-1 | `4477d22` | [APPROVED](convergence/UC-3.md) - walkthrough passed |
| UC-4 | PENDING_WALKTHROUGH | UC-1 | `b5dd39d` | [PENDING_WALKTHROUGH](convergence/UC-4.md) - automated checks passed |
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

## UC-2 Evidence

- Started: 2026-09-08T00:17:25+02:00
- Started from: `0538d4fec3f2135d594cced02ac09fa374e9291f`
- Revision started from: `94b320e` for convergence finding C-1
- Pre-existing dirty files: none
- Implementation submission: HEAD at convergence
- Changed files:
  - Read model and delegation: `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerActivityQueryService.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/MyAppointmentsController.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/request/MyPetsController.java`.
  - Owner cancellation boundary and eligibility: `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/Appointment.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/AppointmentService.java`, `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerAppointmentExceptionAdvice.java`.
  - Presentation and localization: `src/main/resources/templates/my/pets.html`, `src/main/resources/templates/my/appointments.html`, and all eleven files under `src/main/resources/messages/messages*.properties`.
  - Verification: `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerActivityWebTests.java`, `src/test/java/org/springframework/samples/petclinic/scheduling/appointment/OwnerActivityE2ETests.java`.
- Commands and results:
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q spring-javaformat:apply` followed by `... ./mvnw -q -Dspring-javaformat.skip=true -DskipTests compile` - PASS.
  - First focused attempt stopped during test compilation because the new test helper shadowed MockMvc's `get`; renaming it resolved the diagnostic.
  - Second focused attempt ran 39 tests with one assertion failure caused by expecting capitalized `Pets` instead of the localized `My pets`/`No pets`; correcting the test expectation resolved it without production changes.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=OwnerActivityWebTests,I18nPropertiesSyncTest,AppointmentStateTransitionTests test` - PASS, 39 tests, 0 failures, 0 errors, 0 skipped.
  - Same Maven/JDK/agent invocation with `-Dtest=OwnerActivityE2ETests` - PASS, 3 tests, 0 failures, 0 errors, 0 skipped; dynamic localhost binding was allowed for the real-server boundary.
  - Same Maven/JDK/agent invocation without `-Dtest` - PASS, 322 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q spring-javaformat:validate` - PASS.
  - Revision diagnostic focused run reached the new controller and returned 302, but 1 of 41 assertions failed because direct JDBC observed the test's outer transaction before JPA flushed; the service now flushes the completed cancellation.
  - Revision focused rerun with `-Dtest=OwnerActivityWebTests,I18nPropertiesSyncTest,AppointmentStateTransitionTests` - PASS, 41 tests, 0 failures, 0 errors, 0 skipped.
  - Revision real-server sandbox diagnostic could not bind localhost and produced 4 context errors; the identical allowed run with `-Dtest=OwnerActivityE2ETests` - PASS, 4 tests, 0 failures, 0 errors, 0 skipped.
  - One later full run had 1 of 325 fail in pre-existing `PetClinicConcurrencyTests.testDuplicatePetNameRaceConditionIsBlocked`; the isolated allowed rerun passed 1/0/0/0, and the identical complete suite rerun passed without a production change.
  - Revision full Maven/JDK/agent suite - PASS, 325 tests, 0 failures, 0 errors, 0 skipped.
  - Revision `spring-javaformat:validate` and `git diff --check` - PASS.
  - `git diff --check` - PASS; `data/petclinic.mv.db` remained at `2026-09-07T22:56:54+0200`; `.agents/` remained excluded.

| Contract element | Evidence |
|---|---|
| UC-2 main steps 1-2 | Real owner login and `/my/pets` rendering at `OwnerActivityE2ETests.java:58`; field-perfect owner/pet and absence-of-edit-action assertions at `OwnerActivityWebTests.java:41`; principal-derived read model at `OwnerActivityQueryService.java:44`. |
| UC-2 main steps 3-4 | Real `/my/appointments` journey asserts every past/future/final appointment, active request, veterinarian, specialty, origin, and staff reason at `OwnerActivityE2ETests.java:73`; exact DOM/state evidence at `OwnerActivityWebTests.java:56`. |
| UC-2 main step 5 | Start/resume mutual exclusion and the full cancel visibility matrix remain at `OwnerActivityWebTests.java:77` and `OwnerActivityWebTests.java:96`; focused and real-server POSTs at `OwnerActivityWebTests.java:111` and `OwnerActivityE2ETests.java:89` prove the displayed CSRF form produces exact `CANCELLED/OWNER` state and disappears after use. |
| UC-2 extension 2a | A real authenticated owner with no pets sees the exact owner record and empty states without scheduling actions at `OwnerActivityE2ETests.java:88`; focused DOM and zero-pet persistence checks at `OwnerActivityWebTests.java:73`. |
| UC-2 extension 4a | George's pet with no appointments/request renders both absences and exactly the new-request action at `OwnerActivityWebTests.java:86`. |
| UC-2 extension 4b | Current rescheduled date/time and exact staff-entered reason are rendered through real HTTP at `OwnerActivityE2ETests.java:58` and focused DOM at `OwnerActivityWebTests.java:41`. |
| UC-2 extension 1a | Foreign and unknown pet, request, and cancellation POST identifiers produce equal standard 404 pages; complete tables are unchanged in `OwnerActivityE2ETests.java:135` and `OwnerActivityWebTests.java:146`. |
| UC-2 G1 | Principal-scoped repositories and cancellation lookup exclude Betty's identity, contacts, pet, request, appointment, and reason; equal foreign/unknown POST responses and before/after snapshots prove no disclosure or mutation. |
| UC-2 G2 | Appointment and active-request veterinarian names and exact specialty lists render without a veterinarian-directory link at `OwnerActivityWebTests.java:56` and the real-server journey at `OwnerActivityE2ETests.java:73`. |
| UC-2 G3 | `/my/pets` renders immutable query records and contains no owner/pet create or edit action at `templates/my/pets.html:4` and `OwnerActivityE2ETests.java:67`. |
| UC-2 G4 | The listing and request detail render the same AI origin, surgery specialty, and Helen Leary current interpretation at `OwnerActivityWebTests.java:56` and `OwnerActivityE2ETests.java:73`. |
| UC-2 G5 | Both templates use the shared layout at `templates/my/pets.html:2` and `templates/my/appointments.html:2`; all new labels use message keys and exact eleven-bundle parity passes `I18nPropertiesSyncTest`. |
| UC-2 success postcondition | Real-server journeys prove the complete principal-scoped view and that its displayed cancellation action changes only the eligible appointment. |
| UC-2 minimal guarantee | Empty owners/pets/activities show explicit empty states; foreign, unknown, and state-invalid cancellation attempts disclose and change nothing. |
| UC-2 Requires UC-1 | All four real-server journeys authenticate through the approved form-login/cookie session; the 325-test suite re-passes UC-1 authentication, route, presentation, localization, and data guarantees. |
| RULE-1 | `MyPetsController.java:19` and `MyAppointmentsController.java:24` delegate; lifecycle enforcement stays in `AppointmentService.java:115`, and read assembly stays in `OwnerActivityQueryService.java:24`. |
| RULE-2 | `AppointmentService.java:115` runs owned lookup, state/time validation, cancellation, metadata, and flush in one transaction; refused boundary tests compare every affected row. |
| RULE-4 | UC-2 changes no request state; complete approved request and appointment transition matrices and no-side-effect assertions pass in the 325-test suite. |
| RULE-7 | Fresh Flyway, mapping, persistence, and restart suites pass unchanged; UC-2 reads the existing `LocalDate`, `LocalTime`, and immutable interpretation fields. |
| RULE-8 | `SeedMigrationTests` passes exact normative set and credential comparisons in the 325-test suite. |
| RULE-9 | Cancellation renders and submits a CSRF-protected POST; the same owner POST without a token returns 403 unchanged, and the owner/staff/anonymous route matrix passes under the single `/my/**` owner rule. |
| RULE-10 | `MyAppointmentsController.java:36` derives owner identity from `Principal`, and `AppointmentService.java:116` scopes the appointment lookup; foreign/unknown cancellation responses are equal and unchanged. |
| RULE-16 | Shared layout, exact owner menu/identity/logout, read-only cards/tables, state-valid cancellation form, and its removal after success are rendered and DOM-tested; human walkthrough remains for convergence. |
| RULE-17 | Five new visible labels use message keys copied to all eleven bundles; hard-coded-template/Java and key-parity scans pass. |
| RULE-18 | `OwnerActivityE2ETests` supplies real-server review, cancellation, empty, and cross-owner journeys; all contract elements map to direct tests; full suite is 325/0/0/0 with no repository/runtime-data impact. |
| RULE-24 | Repository-scope, H2 isolation, restart, README, and unsupported-stack absence tests pass within the 325-test suite. |

## UC-3 Evidence

- Started: 2026-09-07T22:58:42+02:00
- Started from: `e556a93874f10d79237d47e444ea6b4b2dade5ff`
- Pre-existing dirty files: `.agents/` (untracked environment-mounted skill files; excluded from implementation)
- Implementation submission: HEAD at convergence
- Changed files:
  - Build and runtime configuration: `pom.xml`, `src/main/resources/application.properties`, `src/test/resources/application-test.properties`.
  - Scheduling domain and services: `src/main/java/org/springframework/samples/petclinic/scheduling/appointment/Appointment.java`, `AppointmentRepository.java`, `AppointmentService.java`, `AppointmentStatus.java`, `CancelledBy.java`, `IllegalAppointmentTransitionException.java`, `StaffAppointmentController.java`; every file under `scheduling/config`, `scheduling/interpretation`, and `scheduling/matching`; and `CareType.java`, `DuplicateActiveRequestException.java`, `HeldSlotInvalidationService.java`, `IllegalRequestTransitionException.java`, `Interpretation.java`, `InterpretationFailure.java`, `InterpretationFailureKind.java`, `InterpretationLauncher.java`, `InterpretationOrigin.java`, `InterpretationRepository.java`, `InterpretationViewMapper.java`, `InterpretationWindow.java`, `MyRequestController.java`, `Rejection.java`, `RequestService.java`, `SlotSuggestionPort.java`, `StaffRequestController.java`, and `WithStaffReason.java` under `scheduling/request`.
  - Existing-domain integration: `src/main/java/org/springframework/samples/petclinic/owner/Visit.java`, `scheduling/appointment/MyAppointmentsController.java`, `scheduling/request/AuthenticatedOwnerService.java`, `SchedulingRequest.java`, `SchedulingRequestRepository.java`, `scheduling/security/UserAccountRepository.java`, and `vet/VetRepository.java`.
  - Presentation and localization: `src/main/resources/static/resources/js/request-status.js`, `templates/fragments/urgent-care.html`, `templates/my/appointments.html`, all nine `templates/my/request-*.html` pages, and all eleven `messages*.properties` bundles.
  - Verification: `src/test/java/org/springframework/samples/petclinic/scheduling/SchedulingE2eTests.java`, `ConcurrencyInvariantTests.java`, `RuntimePersistenceRestartTests.java`; every file under `scheduling/appointment`, `scheduling/interpretation`, and `scheduling/matching`; `ConsentAndSuggestionWebTests.java`, `InterpretationPersistenceTests.java`, `InterpretationWebTests.java`, `OwnerRequestOutcomeTests.java`, `OwnerTransitionServiceTests.java`, `RequestCreationServiceTests.java`, `RequestStateTransitionTests.java`; the three files under `scheduling/support`; `system/RepositoryScopeTests.java`; and `system/SecurityMatrixWebTests.java`.
- Commands and results:
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q spring-javaformat:validate` - PASS.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=SchedulingE2eTests test` - PASS, 14 tests, 0 failures, 0 errors, 0 skipped.
  - Broad UC-3 plus UC-1 regression run - PASS, 231 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test` - PASS, 315 tests, 0 failures, 0 errors, 0 skipped.
  - `git diff --check` - PASS; `data/petclinic.mv.db` remained at `2026-09-07T22:56:54+0200`; test execution created no tracked runtime-data change and made no live Ollama call.

| Contract element | Evidence |
|---|---|
| UC-3 main steps 1-10 | Real form-login, CSRF, cookie, HTTP, state, hold, acceptance, and My appointments journey in `SchedulingE2eTests.java:72`; complete disclosure in `ConsentAndSuggestionWebTests.java:91`; interpretation fidelity in `InterpretationPersistenceTests.java:119`; exact review in `InterpretationWebTests.java:124`. |
| UC-3 extension 1a | Duplicate HTTP start returns the existing request in `SchedulingE2eTests.java:123`; concurrent starts leave one active row in `ConcurrencyInvariantTests.java:66`. |
| UC-3 extension 1b | Foreign and unknown pets return identical 404 bodies with an unchanged request table in `SchedulingE2eTests.java:133`. |
| UC-3 extension 1c | Missing reason preserves null interpretation fields while rendering and matching with general-care/30-minute defaults in `InterpretationWebTests.java:170`. |
| UC-3 extension 1d | Non-English text is accepted unchanged and sent through the normal interpreter boundary in `SchedulingE2eTests.java:145`. |
| UC-3 extension 2a | Pre-consent text replacement remains Awaiting consent and preserves durable rejections in `OwnerTransitionServiceTests.java:111`. |
| UC-3 extension 2b | Decline routes to `WITH_STAFF/DECLINED_CONSENT`, invokes no interpreter, and creates no interpretation or appointment in `OwnerTransitionServiceTests.java:88` and `SchedulingE2eTests.java:155`. |
| UC-3 extension 3a | A raced repeated consent has one winner, one interpreter call, and one version in `InterpretationConcurrencyTests.java:83`. |
| UC-3 extension 4a | Real-server pending abandonment discards the late result in `SchedulingE2eTests.java:215`; persistence-level late results create no version or failure in `InterpretationPersistenceTests.java:323`. |
| UC-3 extension 4b | All three semantic failure modes reach Interpretation failed through HTTP in `SchedulingE2eTests.java:187` and retain the exact failure kind/raw output in `InterpretationPersistenceTests.java:289`. |
| UC-3 extension 4c | Five rephrase cycles retain failure history, allow further edits, and recommend staff only from the third failure in `InterpretationWebTests.java:200`, `InterpretationWebTests.java:237`, and `OwnerRequestOutcomeTests.java:97`. |
| UC-3 extension 4d | Staff assistance after Interpretation failed records `OWNER_CHOICE` in `OwnerRequestOutcomeTests.java:215`. |
| UC-3 extension 4e | Transport and deadline modes each call once and route to `WITH_STAFF/AI_UNAVAILABLE` in `InterpreterContractTests.java:159` and the real-server legs at `SchedulingE2eTests.java:207`. |
| UC-3 extension 4f | Startup sweep moves every Interpreting request to `WITH_STAFF/AI_UNAVAILABLE` without resubmission in `InterpreterContractTests.java:178`; restart evidence repeats it in `RuntimePersistenceRestartTests.java:41`. |
| UC-3 extension 5a | `OTHER` and its free label are preserved, then confirmation routes without a hold to unmatched specialty in `OwnerRequestOutcomeTests.java:126` and `SchedulingE2eTests.java:276`. |
| UC-3 extension 5b | An out-of-enumeration veterinarian id becomes absent while every other field survives in `InterpretationPersistenceTests.java:213`. |
| UC-3 extension 6a | Raw duration survives; bounds, effective duration, and localized clamp key are checked in `DurationAndTimeTests.java:93` and `InterpretationWebTests.java:124`. |
| UC-3 extension 6b | Edits from Interpreted and Suggestion offered return to consent, delete the hold, and preserve versions/rejections in `OwnerTransitionServiceTests.java:111`, `OwnerTransitionServiceTests.java:165`, and `SlotSuggestionPortTests.java:301`. |
| UC-3 extension 6c | Owner assistance from Interpreted records `OWNER_CHOICE` in `OwnerRequestOutcomeTests.java:226` and the HTTP journey at `SchedulingE2eTests.java:162`. |
| UC-3 extension 7a | No candidate routes to `WITH_STAFF/NO_SLOTS`, creates no hold, explains the result, and leaves only abandon in `OwnerRequestOutcomeTests.java:160`. |
| UC-3 extension 8a | Another option persists the exact rejection, deletes the old hold, and creates a different single hold in `SlotSuggestionPortTests.java:179` and `SchedulingE2eTests.java:292`. |
| UC-3 extension 8b | Owner assistance from Suggestion offered removes the hold and records `OWNER_CHOICE` in `OwnerRequestOutcomeTests.java:237`. |
| UC-3 extension 8c | Suggestion-page edit follows the 6b hold-release and fresh-consent path in `SchedulingE2eTests.java:170`. |
| UC-3 extension 8d | Blank staff release reason is refused without deleting the hold in `SlotSuggestionPortTests.java:286`; a reasoned HTTP release deletes it and records the reason in `SchedulingE2eTests.java:303`. |
| UC-3 extension 8e | Schedule invalidation deletes the hold and records `WITH_STAFF/SCHEDULE_CHANGED` in `SlotSuggestionPortTests.java:269`. |
| UC-3 extension 9a | Acceptance rechecks overlap, removes a stale hold, selects the next candidate, and returns a notice rather than an error in `SlotSuggestionPortTests.java:336` and `SchedulingE2eTests.java:314`. |
| UC-3 extension 2c | Every permitted non-terminal state becomes Abandoned, clears `active_pet_id`, deletes a hold, and permits no repeat in `OwnerTransitionServiceTests.java:207`. |
| UC-3 extension 3b | Wrong-state actions preserve the complete request/interpretation/rejection/hold/appointment/visit snapshots in `RequestStateTransitionTests.java:108` and through HTTP in `SchedulingE2eTests.java:333`. |
| UC-3 G1 | Suggestion DOM contains exactly one slot and no staff calendar in `ConsentAndSuggestionWebTests.java:177`. |
| UC-3 G2-G4 | Exact 15-minute, window, exclusion, opening-hours, effective-block, overlap, specialty, rejection, lead, and inclusive-horizon boundaries are asserted in `FeasibilityCheckerTests.java:22` and `FeasibilityBoundaryTests.java:40`. |
| UC-3 G3 | Prompt contract defines relative-date, named-day-part, and exclusion expansion; stored weekday/date windows round-trip exactly in `InterpreterContractTests.java:79` and `InterpretationPersistenceTests.java:119`. |
| UC-3 G5-G6 | Every lexicographic tie-break and localized rank-reason key is asserted without weights or model prose in `SlotRankerTests.java:44` and `SlotRankerTests.java:129`; only Confirmed appointments affect workload in `SlotSuggestionPortTests.java:124`. |
| UC-3 G7 | Raced bookings and raced owner confirmations leave exactly one live overlapping booking/hold in `ConcurrencyInvariantTests.java:91` and `ConcurrencyInvariantTests.java:114`. |
| UC-3 G8-G9 | Field-by-field immutable persistence is proved in `InterpretationPersistenceTests.java:119`; exact prompt minimization, schema, model, temperature, timeout, and one-call behavior in `InterpreterContractTests.java:79` and `InterpreterContractTests.java:104`. |
| UC-3 G10 | Every request state plus new-request and My appointments pages render urgent-care guidance/contact in `ConsentAndSuggestionWebTests.java:128`. |
| UC-3 G11 | Complete request and appointment action-by-state matrices refuse all unlisted transitions without side effects in `RequestStateTransitionTests.java:108` and `AppointmentStateTransitionTests.java:82`. |
| UC-3 G12 | Test profile pins `2026-09-07 09:00 Europe/Amsterdam`; seeded exceptions and DST-stable local time affect matching in `TestClockProfileIntegrationTests.java:41`, `SlotSuggestionPortTests.java:142`, and `DurationAndTimeTests.java:175`. |
| UC-3 G13 | Deterministic interpreter contract covers all modes in `InterpreterContractTests.java:192`; semantic/unavailable/late/startup behaviors are asserted in the interpretation suite without Ollama. |
| UC-3 G14 | Fourteen `RANDOM_PORT` real-client journeys cover the required owner flow and the staff-suggestion/completion plus declined-consent/direct-booking/no-show continuations in `SchedulingE2eTests.java:72` and `SchedulingE2eTests.java:223`. |
| UC-3 G15 | Two active-request creations and two overlapping slot claims each produce exactly one winner in `ConcurrencyInvariantTests.java:66` and `ConcurrencyInvariantTests.java:91`. |
| UC-3 G16 | Unique in-memory H2 URLs and unchanged runtime data are asserted in `TestDataIsolationTests.java:50`; final runtime database timestamp was unchanged. |
| UC-3 G17 | External state-only polling, Refresh fallback, shared layout, all-bundle parity, and source text scans are covered by `InterpretationWebTests.java:86`, `PresentationShellTests.java:32`, and `I18nPropertiesSyncTest.java:85`; README documents English input. |
| UC-3 G18 | Requests, versions, windows, rejections, appointments, configuration, and startup recovery survive a close/reopen cycle in a temporary file-backed H2 database in `RuntimePersistenceRestartTests.java:41`. |
| UC-3 G19 | Two jobs execute while a third queues, then all finish without rejection in `InterpretationConcurrencyTests.java:63`. |
| UC-3 success postcondition | The real-server journey asserts Accepted, no active pet, no Held row, one Confirmed row, and the rendered My appointments date/time in `SchedulingE2eTests.java:109`. |
| UC-3 minimal guarantee | Concurrency, cross-owner, late-result, wrong-state, no-slot, abandonment, and transition-matrix tests prove one live request/slot, no disclosure, no orphan hold, and unchanged state on refusal. |
| UC-3 Requires UC-1 | Every real-server journey consumes UC-1's approved form-login/session boundary; `SchedulingE2eTests.java:73` reaches owner-only `/my/**`, and UC-1 regressions remain green. |
| RULE-1 | Package inventory separates request, appointment, matching, interpretation, configuration, security, and web responsibilities; controllers delegate lifecycle work to `RequestService` and `AppointmentService`. |
| RULE-2 | State changes are transactional in `RequestService.java:93` and `AppointmentService.java:41`; refused-action snapshot tests prove atomic no-ops. |
| RULE-3 | `FeasibilityChecker` and `SlotRanker` are framework-free and their complete deterministic order is tested by `FeasibilityCheckerTests.java:22` and `SlotRankerTests.java:44`. |
| RULE-4 | Typed request refusals and complete real-state matrix evidence are in `RequestStateTransitionTests.java:108`. |
| RULE-5 | One appointment aggregate and complete typed refusal matrix evidence are in `AppointmentStateTransitionTests.java:82`; hold releases are asserted as deletion. |
| RULE-6 | Unique active-pet enforcement, veterinarian locking, overlap recheck, and one-winner races are evidenced in `ConcurrencyInvariantTests.java:66`; stale versions are refused in `RequestStateTransitionTests.java:85`. |
| RULE-7 | Flyway-created local date/time columns and immutable version round trips are verified in `SeedMigrationTests.java:47`, `InterpretationPersistenceTests.java:119`, and `RuntimePersistenceRestartTests.java:41`. |
| RULE-8 | Every normative row and all BCrypt passwords are compared by value in `SeedMigrationTests.java:47`. |
| RULE-9 | Owner/staff/anonymous route behavior, disclosure absence, and mutation absence are checked in `SecurityMatrixWebTests.java:60` and the real-server access legs in `SchedulingE2eTests.java:349`. |
| RULE-10 | All `/my/**` controllers resolve the principal through `AuthenticatedOwnerService`; foreign/unknown parity is proved in `SchedulingE2eTests.java:133` and `InterpretationWebTests.java:103`. |
| RULE-11 | Spring AI 2.0.1 starter/property shape, schema, temperature zero, five/120-second timeouts, model default, and zero retries are asserted in `InterpreterContractTests.java:104`; tests inject the deterministic adapter. |
| RULE-12 | Exact allowed prompt and prohibited identity fields are compared in `InterpreterContractTests.java:79`; the consent disclosure repeats the privacy boundary in `ConsentAndSuggestionWebTests.java:91`. |
| RULE-13 | Duplicate job, two-worker/unbounded-queue, semantic/unavailable/late/startup cases are covered in `InterpretationConcurrencyTests.java:63`, `InterpreterContractTests.java:159`, and `InterpretationPersistenceTests.java:289`. |
| RULE-14 | Exact JSON-only owner-scoped status plus external script and Refresh link are asserted in `InterpretationWebTests.java:86` and `InterpretationWebTests.java:103`. |
| RULE-15 | Production scheduling paths use injected `Clock`; pinned-clock and exception evidence is in `TestClockProfileIntegrationTests.java:41` and `SlotSuggestionPortTests.java:142`. |
| RULE-16 | Every new template uses the shared layout; role/state actions and real rendered journeys are covered by `ConsentAndSuggestionWebTests.java:91` and `SchedulingE2eTests.java:72`; walkthrough remains for convergence. |
| RULE-17 | All new text uses keys present in eleven identical key sets, enforced by `I18nPropertiesSyncTest.java:85`; temporal labels localize in `InterpretationWebTests.java:293`. |
| RULE-18 | `SchedulingE2eTests` supplies the real-server boundary; every extension/guarantee maps above; 315 tests pass and runtime data is unchanged. |
| RULE-20 | Matching and staff continuation use `AvailabilityService`; parity/refusal evidence is `SlotSuggestionPortTests.java:162`. |
| RULE-21 | Exact absent/raw values and duration boundaries are covered in `InterpretationPersistenceTests.java:119`, `InterpretationPersistenceTests.java:213`, and `DurationAndTimeTests.java:93`. |
| RULE-22 | The exact rejected vet/date/time survives edit, reinterpretation, restart, and rematch in `SlotSuggestionPortTests.java:301` and `RuntimePersistenceRestartTests.java:41`. |
| RULE-24 | Maven/H2-only inventory and flattened AI property assertions are in `RepositoryScopeTests.java:54`; isolated test/runtime persistence boundaries are covered by `TestDataIsolationTests.java:50` and `RuntimePersistenceRestartTests.java:41`. |

## UC-4 Evidence

- Started: 2026-09-08T08:42:59+02:00
- Started from: `04a2776db29429e8aa926313dd21bde6e5b8e460`
- Revision resumed: 2026-09-08T15:42:41+02:00 from `85a4217aba7b348cbfcdf80560ce0d178eae8648` for convergence finding C-1
- Pre-existing dirty files: none
- Implementation submission: HEAD at convergence
- Changed files:
  - Staff request and appointment behavior: `Appointment.java`, `AppointmentService.java`, `DefaultSlotSuggestionPort.java`, `RequestService.java`, `SchedulingRequest.java`, `SchedulingRequestRepository.java`, `SlotSuggestionPort.java`, `StaffInterpretationForm.java`, `StaffQueueController.java`, `StaffQueueQueryService.java`, `StaffRequestController.java`, and `StaffSlotUnavailableException.java` under `src/main/java/org/springframework/samples/petclinic/scheduling`.
  - LLM interaction observability: `src/main/java/org/springframework/samples/petclinic/scheduling/interpretation/OllamaInterpreter.java` logs the complete allowed request payload and mapped structured response without logging raw provider JSON.
  - Presentation and localization: `src/main/resources/templates/staff/queue.html`, `src/main/resources/templates/staff/request-detail.html`, and all eleven `src/main/resources/messages/messages*.properties` bundles.
  - Verification: `ConcurrencyInvariantTests.java`, `SchedulingE2eTests.java`, `AppointmentStateTransitionTests.java`, `SlotSuggestionPortTests.java`, `OwnerTransitionServiceTests.java`, `RequestCreationServiceTests.java`, `RequestStateTransitionTests.java`, `StaffSchedulingWebTests.java`, `SecurityMatrixWebTests.java`, and `InterpreterContractTests.java` under `src/test/java/org/springframework/samples/petclinic`.
- Commands and results:
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=ConcurrencyInvariantTests test` - PASS, 6 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=SchedulingE2eTests test` - PASS, 15 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=StaffSchedulingWebTests,I18nPropertiesSyncTest test` - PASS, 12 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test` - PASS, 338 tests, 0 failures, 0 errors, 0 skipped.
  - `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q spring-javaformat:validate` and `git diff --check` - PASS.
  - `data/petclinic.mv.db` remained 114688 bytes with SHA-256 `6bd75c0c92324582c86d98d90eada9612d7b40f8711cedf2d8f79c08e37a6820` and timestamp `2026-09-08T15:16:05+0200` across the final test run.
  - C-1 focused revision: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar -Dtest=StaffSchedulingWebTests,InterpreterContractTests,I18nPropertiesSyncTest,DurationAndTimeTests test` - PASS, 31 tests, 0 failures, 0 errors, 0 skipped.
  - UC-4 revision regression: the focused revision plus `ConcurrencyInvariantTests` and `SchedulingE2eTests` - PASS, 52 tests, 0 failures, 0 errors, 0 skipped.
  - Revision full suite: `JAVA_HOME=/Users/anton/Library/Java/JavaVirtualMachines/jbr-21.0.8/Contents/Home ./mvnw -q -Dspring-javaformat.skip=true -DargLine=-javaagent:/Users/anton/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar test` - PASS, 347 tests, 0 failures, 0 errors, 0 skipped across 45 suites.
  - Revision tests left `data/petclinic.mv.db` unchanged at 126976 bytes, timestamp `2026-09-08T15:40:44+0200`, SHA-256 `745f17b36795677662e6fa7593f60e78fe2006bd39ab9428c7b488c550f9cc86`.

| Contract element | Evidence |
|---|---|
| UC-4 main steps 1-4 | Exact queue partition/order and rendered staff detail are asserted in `StaffSchedulingWebTests.java:82` and `StaffSchedulingWebTests.java:106`; form-login queue entry is exercised at real-server boundary in `SchedulingE2eTests.java:285`. |
| UC-4 main steps 5-6 | Latest-value prefill, empty declined-consent form, immutable history, STAFF origin, and no duplicate version are asserted in `StaffSchedulingWebTests.java:106` and `StaffSchedulingWebTests.java:142`; real HTTP authoring is at `SchedulingE2eTests.java:301`. |
| UC-4 main steps 7-10 | Required reason, specialty match/mismatch display, bounded constraint validation, Confirmed/Accepted state, audit values, queue removal, and owner visibility are asserted in `StaffSchedulingWebTests.java:247` and the real-server journey at `SchedulingE2eTests.java:312`. |
| UC-4 extension 1a | Staff creation produces `WITH_STAFF/STAFF_CREATED`, no interpretation, and zero AI calls in `SchedulingE2eTests.java:289`. |
| UC-4 extension 1b | Sequential and concurrent duplicates return the existing request in `RequestCreationServiceTests.java:110`, `SchedulingE2eTests.java:350`, and `ConcurrencyInvariantTests.java:72`. |
| UC-4 extension 2a | Reasoned hold release deletes the hold and routes to `WITH_STAFF/HOLD_RELEASED` in `StaffSchedulingWebTests.java:318`. |
| UC-4 extension 2b | In-progress state, origin, held slot, age, inspect action, and release-only mutation surface are rendered and asserted in `StaffSchedulingWebTests.java:318`. |
| UC-4 extension 3a | An abandoned request refuses the later staff action, remains out of the queue, creates nothing, and invokes no interpreter in `StaffSchedulingWebTests.java:373`. |
| UC-4 extension 5a | Resubmitting unchanged complete structured values does not create a version in `StaffSchedulingWebTests.java:106`. |
| UC-4 extension 5b | The complete request action-by-state matrix refuses authoring outside With staff without mutation in `RequestStateTransitionTests.java:108`. |
| UC-4 extension 5c | Missing specialty, invalid duration, and absent usable windows render localized errors and create no version or appointment in `StaffSchedulingWebTests.java:195`. |
| UC-4 extension 7a | A reasoned staff suggestion creates one Held appointment, moves to Suggestion offered, and is owner-visible in `StaffSchedulingWebTests.java:318` and `SchedulingE2eTests.java:339`. |
| UC-4 extension 7b | Duration, grid, opening, effective-block, overlap, and malformed-input refusals remain With staff and create nothing in `StaffSchedulingWebTests.java:166`, `StaffSchedulingWebTests.java:218`, and `StaffSchedulingWebTests.java:247`. |
| UC-4 extension 8a | A booking before lead/horizon, outside the authored window, and with a specialty-mismatching veterinarian succeeds after the mismatch display in `StaffSchedulingWebTests.java:247` and `SchedulingE2eTests.java:309`. |
| UC-4 extension 8b | Clinic-closed, outside-block, grid, duration, and overlap inputs are specifically refused with unchanged appointment capacity in `StaffSchedulingWebTests.java:166` and `StaffSchedulingWebTests.java:247`. |
| UC-4 extension 9a | Stale HTTP and raced same-request actions create no losing side effect in `StaffSchedulingWebTests.java:373` and `ConcurrencyInvariantTests.java:108`. |
| UC-4 extension 9b | Two same-slot staff claims yield one Held winner and one unchanged With staff request in `ConcurrencyInvariantTests.java:88`. |
| UC-4 extension 4a | Owner abandonment removes the request from Needs staff and makes later staff action a no-op in `StaffSchedulingWebTests.java:373`. |
| UC-4 G1-G2 | Exact queue membership/order/fields are in `StaffSchedulingWebTests.java:82`; optimistic claim behavior is in `ConcurrencyInvariantTests.java:108` and `SchedulingRequestRepository.java:23`. |
| UC-4 G3 | Zero AI calls and no booking/suggestion actions without a complete interpretation are asserted in `SchedulingE2eTests.java:289` and `StaffSchedulingWebTests.java:142`; all forged wrong-state staff actions are refused in `RequestStateTransitionTests.java:108`. |
| UC-4 G4 | Complete current/history field values, origin, localized windows, preservation, and no duplicate version are asserted in `StaffSchedulingWebTests.java:106` and `RequestCreationServiceTests.java:125`; `rawStaffDurationIsPersistedAndSlotFormsUseConfiguredBoundedDefault` proves absent, zero, below/at/inside/at/above raw duration fidelity. |
| UC-4 G5 | Staff override success and every non-overridable constraint boundary are asserted in `StaffSchedulingWebTests.java:166` and `StaffSchedulingWebTests.java:247`. |
| UC-4 G6 | Concurrent staff creation, same-slot claims, and same-request actions each leave one winner in `ConcurrencyInvariantTests.java:72`. |
| UC-4 G7 | Owner visibility and abandonment from With staff pass in `SchedulingE2eTests.java:323` and `OwnerTransitionServiceTests.java:209`; the state matrix refuses editing in With staff. |
| UC-4 G8 | Shared layout/localization scans pass; suggestion, booking, and release require and persist a reason in `StaffSchedulingWebTests.java:247` and `StaffSchedulingWebTests.java:318`. |
| UC-4 success postcondition | The real-server journey proves both Accepted/Confirmed and Suggestion offered/one Held outcomes visible to the relevant owner in `SchedulingE2eTests.java:285`. |
| UC-4 minimal guarantee | Constraint, malformed, stale, abandoned, wrong-state, and losing-concurrency tests compare state and side effects in `StaffSchedulingWebTests.java:166`, `StaffSchedulingWebTests.java:218`, `StaffSchedulingWebTests.java:373`, and `ConcurrencyInvariantTests.java:88`. |
| UC-4 Requires UC-1 | The real-server journey authenticates seeded staff and owners through the approved form-login session boundary in `SchedulingE2eTests.java:286`; full security regressions pass. |
| RULE-1 | Controllers delegate queries and lifecycle mutations to `StaffQueueQueryService.java:53` and `RequestService.java:276`; matching stays in `DefaultSlotSuggestionPort`. |
| RULE-2 | Query models use read-only transactions at `StaffQueueQueryService.java:53`; all mutations are transactional in `RequestService.java:256` and `AppointmentService.java:56`. |
| RULE-3 | Staff feasibility reuses framework-free `FeasibilityChecker` through `DefaultSlotSuggestionPort.java:278`; exact boundary tests are `StaffSchedulingWebTests.java:166`. |
| RULE-4 | Allowed staff transitions and every unlisted state/action no-op are asserted in `RequestStateTransitionTests.java:108`. |
| RULE-5 | Staff holds and direct bookings use the existing appointment aggregate; lifecycle/refusal regressions pass in `AppointmentStateTransitionTests.java`. |
| RULE-6 | The unique active-pet constraint, optimistic staff claim, veterinarian lock, overlap recheck, and all three two-thread outcomes are exercised in `ConcurrencyInvariantTests.java:72`. |
| RULE-7 | Immutable STAFF rows retain local date/time and interpretation values in `RequestCreationServiceTests.java:125` and `StaffSchedulingWebTests.java:106`; Flyway/restart regressions pass. |
| RULE-8 | `SeedMigrationTests` passes exact normative sets and all credential comparisons in the 338-test suite. |
| RULE-9 | New staff routes are covered as anonymous/owner/staff with CSRF and no-mutation assertions in `SecurityMatrixWebTests.java:61`. |
| RULE-10 | No owner id is introduced under `/my/**`; owner result visibility continues through principal-scoped pages, and all owner-isolation regressions pass. |
| RULE-12 | Staff paths invoke no interpreter in `SchedulingE2eTests.java:289` and `StaffSchedulingWebTests.java:373`; `InterpreterContractTests.llmInteractionLogsRequestPayloadAndStructuredResponseWithoutRawProviderJson` proves allowed payload/structured-response logging without raw provider JSON. |
| RULE-15 | Queue hold age uses only the injected clock at `StaffQueueQueryService.java:46`; time-dependent tests remain pinned. |
| RULE-16 | Both staff templates use the shared PetClinic layout and state-specific actions; rendered DOM tests pass, with human walkthrough left to convergence. |
| RULE-17 | Every new validation, flash, reason, and label key is present in all eleven bundles; `I18nPropertiesSyncTest` and localized weekday rendering at `StaffSchedulingWebTests.java:132` pass. |
| RULE-18 | `SchedulingE2eTests.java:285` is the real-server actor journey; direct evidence maps every contract element above; 338 tests pass and runtime data is unchanged. |
| RULE-19 | Duration, grid, opening-hours, continuous effective-block, and overlap checks run at `DefaultSlotSuggestionPort.java:278`; override and refusal evidence is `StaffSchedulingWebTests.java:166`. |
| RULE-20 | Staff validation uses the same `AvailabilityService` calls at `DefaultSlotSuggestionPort.java:287`; approved matching parity tests remain green. |
| RULE-21 | `StaffSchedulingWebTests.rawStaffDurationIsPersistedAndSlotFormsUseConfiguredBoundedDefault` proves raw absent/zero/below/at/inside/at/above values persist verbatim while `StaffQueueQueryService` derives only the booking/suggestion defaults through configured `DurationPolicy`; veterinarian-id validation remains independently positive-only. |
| RULE-22 | UC-4 does not mutate rejection history; edit/history and durable-rejection regressions remain green in `OwnerTransitionServiceTests.java:111` and `SlotSuggestionPortTests`. |
| RULE-24 | Repository-scope, H2 isolation, restart, README, and unsupported-stack absence tests pass in the full suite. |

## Blockers

None.

## Deviations

None.
