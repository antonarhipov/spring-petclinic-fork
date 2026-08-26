# Acceptance Criteria: UC1 — Secure Scheduling Access

## Functional

### UC1-AC1: Permit public routes

**Covers:** UC1-B1

When an unauthenticated user requests the welcome page, login page, urgent-care guidance, a static resource, or a detail-free liveness or readiness endpoint, the system shall permit access.

### UC1-AC2: Require login for protected HTML

**Covers:** UC1-B2

When an unauthenticated user requests any other HTML route, the system shall redirect the user to form login.

### UC1-AC3: Permit staff routes

**Covers:** UC1-B3

When an authenticated staff user requests a staff scheduling or existing owner-administration route, the system shall permit access.

### UC1-AC4: Deny owner access to staff routes

**Covers:** UC1-B4

If an authenticated owner requests a staff scheduling or existing owner-administration route, then the system shall deny access and shall not perform the requested operation.

### UC1-AC5: Render authorized navigation

**Covers:** UC1-B5

When the system renders navigation for an authenticated user, the system shall include only links authorized for that user's role.

### UC1-AC6: Limit owner self-service data

**Covers:** UC1-B6

When an authenticated owner requests a self-service record, the system shall permit access only when the record is linked to that owner's account.

### UC1-AC7: Conceal another owner's resource

**Covers:** UC1-B7

If an owner addresses another owner's protected owner, pet, request, appointment, notification, or audit identifier, then the system shall return `404` without disclosing the resource.

### UC1-AC8: Update supported owner contact fields

**Covers:** UC1-B8

When an owner submits valid changes to their first name, last name, address, city, or telephone, the system shall update those fields on that owner's profile.

### UC1-AC9: Add an owned pet

**Covers:** UC1-B9

When an owner submits valid details for a new pet, the system shall add the pet to that owner's profile.

### UC1-AC10: Edit an owned pet

**Covers:** UC1-B10

When an owner submits valid changes for a pet linked to that owner, the system shall update that pet.

### UC1-AC11: Prevent owner deletion

**Covers:** UC1-B11

If an owner attempts to delete their account or a pet, then the system shall reject the request and shall not delete the account or pet.

### UC1-AC12: Show veterinarian catalog details

**Covers:** UC1-B12

When an authenticated owner or staff user requests veterinarian information, the system shall expose veterinarian names and specialties.

### UC1-AC13: Conceal veterinarian availability

**Covers:** UC1-B13

When veterinarian information is returned through owner-accessible HTML or JSON, the system shall omit veterinarian schedules and availability.

### UC1-AC14: Create a linked owner account

**Covers:** UC1-B14

When staff submits a valid unique username, temporary password, and unlinked Owner, the system shall create one `OWNER` account permanently linked to that Owner.

### UC1-AC15: Create an unlinked staff account

**Covers:** UC1-B15

When staff submits a valid unique username and temporary password for a staff account, the system shall create an unlinked `STAFF` account.

### UC1-AC16: Reject a short temporary password

**Covers:** UC1-B14, UC1-B15

If staff submits a temporary password shorter than eight characters while creating an account, then the system shall reject account creation and shall not create the account.

### UC1-AC17: Restrict a forced-change account

**Covers:** UC1-B16

While a normal account requires a password change, when that account requests an authenticated function other than password change or logout, the system shall redirect the user to password change.

### UC1-AC18: Clear the password-change requirement

**Covers:** UC1-B17

When a normal account replaces its temporary password with a valid password, the system shall clear the password-change requirement.

### UC1-AC19: Reset an account password

**Covers:** UC1-B18

When staff resets an account with a valid temporary password, the system shall require that account to change the password before using other authenticated functions.

### UC1-AC20: Bootstrap the initial staff account

**Covers:** UC1-B19

When a non-demo database has no active staff account and valid deployment credentials are configured, the system shall create one initial staff account from those credentials.

### UC1-AC21: Preserve an existing active staff account during bootstrap

**Covers:** UC1-B19

When a non-demo database already has an active staff account, the system shall not create an initial staff account from deployment credentials.

### UC1-AC22: Create missing demo accounts

**Covers:** UC1-B20

When the `demo-data` profile starts, the system shall create every fixed demo staff and owner account that is missing.

### UC1-AC23: Keep demo accounts immediately usable

**Covers:** UC1-B21

When a demo account is created by `demo-data`, the system shall leave the account usable without a required password change.

### UC1-AC24: Preserve existing demo credentials

**Covers:** UC1-B22

When demo initialization finds an existing fixed demo account, the system shall preserve that account and its current password.

### UC1-AC25: Display demo-mode warnings

**Covers:** UC1-B23

While `demo-data` is active, the system shall display a demo-mode warning on the login page and authenticated pages.

### UC1-AC26: Reject conflicting runtime profiles

**Covers:** UC1-B24

If `demo-data` and a production profile are active together, then the system shall refuse startup.

### UC1-AC27: Reject a case-insensitive duplicate username

**Covers:** UC1-B25

If account creation or username change uses a username equal to an existing username without regard to case, then the system shall reject the change and shall preserve the existing identities.

### UC1-AC28: Accept an above-minimum replacement password

**Covers:** UC1-B26

When a normal account submits a replacement password longer than eight characters and different from its temporary password, the system shall accept the password when all other validation succeeds.

### UC1-AC29: Accept a minimum-length replacement password

**Covers:** UC1-B26

When a normal account submits an eight-character replacement password different from its temporary password, the system shall accept the password when all other validation succeeds.

### UC1-AC30: Reject a below-minimum replacement password

**Covers:** UC1-B26

If a normal account submits a replacement password shorter than eight characters, then the system shall reject the password and shall retain the password-change requirement.

### UC1-AC31: Reject reuse of the temporary password

**Covers:** UC1-B26

If a normal account submits its temporary password as the replacement password, then the system shall reject the password and shall retain the password-change requirement.

### UC1-AC32: Remain unlocked below the failure threshold

**Covers:** UC1-B27

When an account accumulates four consecutive failed login attempts, the system shall leave the account unlocked.

### UC1-AC33: Lock at the failure threshold

**Covers:** UC1-B27

When an account accumulates a fifth consecutive failed login attempt, the system shall lock the account for 15 minutes.

### UC1-AC34: Enforce the lock before its deadline

**Covers:** UC1-B27

While an account's 15-minute lock deadline has not passed, when correct credentials are submitted, the system shall deny authentication.

### UC1-AC35: End the lock at its deadline

**Covers:** UC1-B27

When an account's 15-minute lock deadline is reached, the system shall permit a correct login attempt to authenticate.

### UC1-AC36: Clear failed-login history

**Covers:** UC1-B28

When an unlocked account authenticates successfully, the system shall clear that account's consecutive failed-login count.

### UC1-AC37: Unlock an account early

**Covers:** UC1-B29

While an account is locked, when staff unlocks the account before its deadline, the system shall make the account eligible for authentication.

### UC1-AC38: Enforce CSRF protection

**Covers:** UC1-B30

If an authenticated state-changing request lacks a valid CSRF token, then the system shall reject the request and shall not change application state.

### UC1-AC39: Rotate the authenticated session

**Covers:** UC1-B31

When authentication succeeds, the system shall replace the pre-authentication session identifier with a new session identifier.

### UC1-AC40: Invalidate sessions after identity changes

**Covers:** UC1-B32

When an account's password, username, or active status changes through password change, password reset, username change, or deactivation, the system shall invalidate every active session for that account.

### UC1-AC41: Deactivate without deleting history

**Covers:** UC1-B33

When staff deactivates an account that may be deactivated, the system shall disable the account while retaining its linked history.

### UC1-AC42: Protect the final active staff account

**Covers:** UC1-B34

If staff attempts to deactivate the final active staff account, then the system shall reject the request and shall leave that account active.

### UC1-AC43: Rename a username without changing identity

**Covers:** UC1-B35

When staff renames an account to a valid unique username, the system shall preserve the account's role and Owner link.

### UC1-AC44: Prevent role or Owner reassignment

**Covers:** UC1-B36

If a user attempts to change an existing account's role or linked Owner, then the system shall reject the change and shall preserve the original assignment.

### UC1-AC45: Refuse startup without an administrable staff identity

**Covers:** UC1-B37

If a non-demo database has no active staff account and valid bootstrap credentials are absent, then the system shall refuse startup.

### UC1-AC46: Prevent a second account for one Owner

**Covers:** UC1-B38

If staff attempts to create a second owner account linked to an Owner that is already linked, then the system shall reject creation and shall preserve the existing link.

### UC1-AC47: Localize scheduling-interface messages

**Covers:** UC1-B39

When the system renders a new owner or staff scheduling-interface message, the system shall resolve the message through the existing localization mechanism.

## Coverage exclusions

- Performance: the specification defines no measurable authentication or authorization latency contract.
- Accessibility: the specification defines localized messages but no measurable accessibility contract for the access screens.
