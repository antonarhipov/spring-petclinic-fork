## Purpose

Makes the already-secured owner screens and the owner self-scheduling entry point reachable through role-aware navigation, so an authenticated owner can find their own data and start a booking without staff-only menus, while staff-only management screens stay behind the staff role.

## ADDED Requirements

### Requirement: Owner-scoped navigation

The system SHALL render owner-scoped navigation entries only to an authenticated user whose role is `OWNER`. The navigation SHALL expose the identity of the current owner to the view layer so links can target that owner's own resources. These entries SHALL NOT be rendered for anonymous visitors or for `STAFF` users.

#### Scenario: Owner sees links to their own data

- **GIVEN** an authenticated user with role `OWNER` linked to a domain `Owner`
- **WHEN** any page with the shared navigation is rendered
- **THEN** the navigation shows an entry linking to that owner's own data page and an entry linking to that owner's own appointments page

#### Scenario: Staff does not see owner-scoped navigation

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** any page with the shared navigation is rendered
- **THEN** no owner-scoped navigation entries are shown and the staff management entries remain visible

#### Scenario: Anonymous visitor does not see owner-scoped navigation

- **GIVEN** no authenticated session
- **WHEN** a page with the shared navigation is rendered
- **THEN** no owner-scoped navigation entries are shown

### Requirement: Owner navigation targets owner-guarded resources

Owner-scoped navigation entries SHALL link only to resources scoped to the current owner and already protected by owner-scoping authorization. The system SHALL NOT expose links that would let one owner navigate to another owner's data.

#### Scenario: Owner navigation links resolve to the owner's own resources

- **GIVEN** an authenticated owner linked to owner identifier `X`
- **WHEN** the owner-scoped navigation entries are rendered
- **THEN** the "my data" entry targets the data page for owner `X` and the "my appointments" entry targets the appointments page for owner `X`

#### Scenario: Owner cannot reach another owner's data via navigation

- **GIVEN** an authenticated owner linked to owner identifier `X`
- **WHEN** the navigation is rendered
- **THEN** no rendered entry targets a different owner's identifier

### Requirement: Owner self-scheduling entry point

An authenticated `OWNER` SHALL have a reachable UI entry point to start a new self-scheduling request for one of their own pets. The entry point SHALL be owner-scoped so an owner can only begin a request for a pet they own. Staff SHALL retain their existing separate booking entry point.

#### Scenario: Owner can start a self-scheduling request

- **GIVEN** an authenticated owner with at least one pet
- **WHEN** the owner opens the self-scheduling entry point
- **THEN** the system presents a way to begin a new appointment request for one of the owner's own pets

#### Scenario: Owner cannot start a request for a pet they do not own

- **GIVEN** an authenticated owner
- **WHEN** the owner attempts to start a self-scheduling request for a pet linked to a different owner
- **THEN** the system refuses the action and does not create a request

### Requirement: Staff management screens remain staff-only

The user-management, veterinarian-calendar, and clinic-settings screens introduced by this change SHALL require the `STAFF` role. Anonymous visitors and `OWNER` users SHALL NOT be able to reach these screens.

#### Scenario: Owner is denied access to staff management screens

- **GIVEN** an authenticated user with role `OWNER`
- **WHEN** the user requests a user-management, calendar, or clinic-settings screen
- **THEN** access is denied

#### Scenario: Staff can reach management screens

- **GIVEN** an authenticated user with role `STAFF`
- **WHEN** the user requests a user-management, calendar, or clinic-settings screen
- **THEN** the screen is served
