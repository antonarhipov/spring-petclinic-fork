## Purpose

Exposes a staff-only user-management surface that wires the already-implemented account provisioning and password-reset actions into the UI and adds a simple accounts overview, so staff can grant an owner a login and reset credentials without changing how those actions behave.

## ADDED Requirements

### Requirement: Staff user-management surface

Staff SHALL have a reachable UI surface to manage owner logins. From an owner's detail screen, staff SHALL be able to provision a login for an owner who has none and reset the password for an owner who already has one. These actions SHALL reuse the existing account provisioning and password-reset behavior without modifying it. The surface SHALL require the `STAFF` role.

#### Scenario: Staff provision a login for an owner without one

- **GIVEN** an authenticated user with role `STAFF` viewing an owner who has no login
- **WHEN** the user triggers "provision account" for that owner
- **THEN** an owner login is created with a forced password change on first login, using the existing provisioning behavior

#### Scenario: Staff reset an existing owner's password

- **GIVEN** an authenticated user with role `STAFF` viewing an owner who already has a login
- **WHEN** the user triggers "reset password" for that owner
- **THEN** the owner's password is reset and a forced password change is armed, using the existing reset behavior

#### Scenario: Owner cannot reach the user-management actions

- **GIVEN** an authenticated user with role `OWNER`
- **WHEN** the user attempts to provision or reset an account
- **THEN** the action is denied

### Requirement: Staff accounts overview

Staff SHALL be able to view a simple list of owner accounts that indicates, for each owner, whether a login exists. The list SHALL let staff navigate to the owner's detail screen to perform provisioning or password reset. The overview SHALL require the `STAFF` role.

#### Scenario: Staff view the accounts list

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** the user opens the accounts overview
- **THEN** the list shows owners and whether each has an existing login

#### Scenario: Accounts overview is denied to owners

- **GIVEN** an authenticated user with role `OWNER`
- **WHEN** the user requests the accounts overview
- **THEN** access is denied
