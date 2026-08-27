## Purpose

Introduces authentication, roles, and per-user data scoping to the previously anonymous PetClinic so that owners and staff have distinct identities and every existing screen is protected while a small set of public resources stays reachable.

## ADDED Requirements

### Requirement: Application user identity

The system SHALL maintain a dedicated application user identity that is separate from the domain `Owner` record. Each user SHALL have a unique username, a securely hashed password, a role of `OWNER` or `STAFF`, an `enabled` flag, and a `mustChangePassword` flag. A single `UserDetailsService` SHALL resolve credentials for both roles.

#### Scenario: User authenticates with valid credentials

- **GIVEN** an enabled user with a known username and password
- **WHEN** the user submits those credentials at the login form
- **THEN** the system authenticates the user and establishes an authenticated session with the user's role

#### Scenario: Disabled user cannot authenticate

- **GIVEN** a user whose `enabled` flag is false
- **WHEN** the user submits otherwise-correct credentials
- **THEN** authentication is refused and no session is established

#### Scenario: Password is never stored in clear text

- **WHEN** a user account is created or its password is changed
- **THEN** the stored password is a one-way hash and the clear-text value is never persisted

### Requirement: Roles and owner linkage

The system SHALL support exactly two roles. A user with role `OWNER` SHALL be linked to exactly one domain `Owner`, and a user with role `STAFF` SHALL be linked to no `Owner`. The link SHALL be nullable so that staff accounts carry no owner association.

#### Scenario: Owner account links to a single owner

- **GIVEN** a user with role `OWNER`
- **THEN** the user resolves to exactly one `Owner` record used to scope that user's data

#### Scenario: Staff account has no owner link

- **GIVEN** a user with role `STAFF`
- **THEN** the user has no linked `Owner` and is not scoped to any single owner's data

### Requirement: Existing controllers are secured

All existing owner, pet, veterinarian, and visit screens SHALL require an authenticated session. Only the login page, the error page, static assets, and the static urgent-care guidance SHALL remain reachable without authentication.

#### Scenario: Anonymous access to a secured page is redirected to login

- **GIVEN** no authenticated session
- **WHEN** an anonymous visitor requests any owner, pet, veterinarian, or visit page
- **THEN** the system redirects the visitor to the login page and does not reveal the requested data

#### Scenario: Public resources remain reachable while anonymous

- **GIVEN** no authenticated session
- **WHEN** an anonymous visitor requests the login page, the error page, a static asset, or the urgent-care guidance
- **THEN** the system serves that resource without requiring authentication

### Requirement: Owner data scoping

An authenticated `OWNER` SHALL only be able to view and act on data belonging to their linked `Owner`, plus veterinarian names and specialties. An owner SHALL never view or act on another owner's pets, visits, or appointments.

#### Scenario: Owner is limited to their own records

- **GIVEN** an authenticated owner linked to owner A
- **WHEN** the owner requests a page scoped to owner B's data
- **THEN** the system denies access and does not display owner B's data

#### Scenario: Owner may view veterinarian names and specialties

- **GIVEN** an authenticated owner
- **WHEN** the owner views veterinarian information
- **THEN** the system shows veterinarian names and specialties but not the clinic's complete availability calendar

### Requirement: Staff act on behalf of owners

Staff SHALL manage clinic data and act on behalf of any owner and pet through `ownerId`-scoped screens. Acting on behalf SHALL NOT use credential impersonation; the staff member remains authenticated as themselves while the target owner is selected explicitly by identifier.

#### Scenario: Staff operate on a selected owner without impersonation

- **GIVEN** an authenticated staff user
- **WHEN** the staff user opens an owner-scoped screen for a chosen `ownerId`
- **THEN** the system performs the action on behalf of that owner while the audit identity remains the staff user, and no owner credentials are used

### Requirement: Staff-provisioned owner accounts

Owner accounts SHALL be created by staff rather than through self-registration. A newly provisioned owner account SHALL have `mustChangePassword` set so the owner is forced to change their password on first login. Staff SHALL be able to reset the password of an existing owner, which re-enables the forced-change behavior.

#### Scenario: Newly provisioned owner is forced to change password on first login

- **GIVEN** a staff user provisions a new owner account with `mustChangePassword` set
- **WHEN** the owner logs in for the first time
- **THEN** the system requires the owner to set a new password before granting access to any other screen

#### Scenario: Staff reset an owner's password

- **GIVEN** an existing owner account
- **WHEN** a staff user resets that owner's password
- **THEN** the system stores the new hashed password and re-arms the forced first-login password change

#### Scenario: Owner self-registration is not offered

- **WHEN** an anonymous visitor looks for a way to create an account
- **THEN** the system provides no self-registration path; accounts are created only by staff

### Requirement: Demo account seeding

For demonstration environments the system SHALL seed sample accounts. Each sample owner SHALL receive a username equal to the lowercase first name (for example `George` becomes `george`) and a password of `<username>123`, and these demo accounts SHALL skip the forced first-login password change. A seeded staff/admin account SHALL also be created.

#### Scenario: Sample owner demo account is seeded without forced change

- **GIVEN** demo seeding runs for a sample owner named George
- **THEN** an owner account with username `george` and password `george123` exists, is enabled, and does not require a first-login password change

#### Scenario: Seeded staff account exists

- **WHEN** demo seeding runs
- **THEN** a staff/admin account is created that can log in and manage the clinic
