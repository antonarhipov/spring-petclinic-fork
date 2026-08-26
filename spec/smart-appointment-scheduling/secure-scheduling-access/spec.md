# UC1: Secure Scheduling Access

## Summary

Owners and staff authenticate through form login and receive only the routes, navigation, and data allowed for their role. Staff provision and maintain accounts; owners maintain their own contact and pet data. The use case establishes the authorization boundary required by every scheduling flow.

## Resolved ambiguities

### Roles and route zones

- `OWNER` and `STAFF` are the only roles. `admin` is a demo username with `STAFF`, not a separate role.
- Welcome, login, urgent-care guidance, static resources, and detail-free liveness/readiness are public.
- Existing `/owners/**` owner and pet administration is staff-only. Owners use separate self-service routes.
- Authenticated owners and staff may view veterinarian names and specialties through the HTML and existing JSON representations. Neither representation includes schedules or availability.
- Each rendered navigation menu contains only links authorized for the current identity. Server-side authorization remains mandatory for direct or forged requests.
- An owner requesting another owner's owner, pet, request, appointment, notification, or audit identifier receives `404` so resource existence is not disclosed.

### Account identity and provisioning

- One `OWNER` account links to exactly one `Owner`; that link and the account role are immutable.
- Staff may rename a username, but the change is audited and invalidates active sessions. Usernames are globally unique without regard to case.
- Staff create owner and staff accounts with a temporary password. Normal accounts must change that password before accessing any other authenticated function.
- Staff reset passwords by issuing another temporary password and restoring the change-required state.
- When a non-demo database has no active staff account, deployment-supplied credentials bootstrap one initial staff account. No known default is used.
- A non-demo deployment with neither an active staff account nor valid bootstrap credentials fails startup rather than becoming unadministrable.
- Staff deactivate accounts rather than deleting them. The final active staff account cannot be deactivated.
- An Owner already linked to an owner account cannot be linked to a second owner account.

### Demo accounts

The explicit `demo-data` profile creates missing demo accounts without overwriting accounts or passwords that already exist:

| Account | Username | Initial password | Role |
| --- | --- | --- | --- |
| Initial staff | `admin` | `admin123` | `STAFF` |
| George Franklin | `george` | `george123` | `OWNER` |
| Betty Davis | `betty` | `betty123` | `OWNER` |
| Eduardo Rodriquez | `eduardo` | `eduardo123` | `OWNER` |
| Harold Davis | `harold` | `harold123` | `OWNER` |
| Peter McTavish | `peter` | `peter123` | `OWNER` |
| Jean Coleman | `jean` | `jean123` | `OWNER` |
| Jeff Black | `jeff` | `jeff123` | `OWNER` |
| Maria Escobito | `maria` | `maria123` | `OWNER` |
| David Schroeder | `david` | `david123` | `OWNER` |
| Carlos Estaban | `carlos` | `carlos123` | `OWNER` |

- Demo accounts do not require a password change.
- Demo mode is visibly identified on login and authenticated pages.
- `demo-data` and a production profile are mutually exclusive.
- Existing owners in a non-demo persistent database do not receive derived accounts; staff provisions them individually.

### Password, login, and session controls

- Passwords are BCrypt hashes and contain at least eight characters. A normal account's replacement password must differ from its temporary password.
- Five consecutive failed logins lock an account for 15 minutes. A successful login clears the failure count; staff may unlock early.
- Login rotates the session identifier. State-changing requests require CSRF protection.
- Password change, password reset, username change, account deactivation, or any permitted security-identity change invalidates every active session for that account.

### Owner self-service

- Owners may update their own first name, last name, address, city, and telephone.
- Owners may add and edit pets linked to their Owner record.
- Owners cannot delete pets, delete their account, change their role, or reassign an account or pet to another owner.

## Explicit assumptions

- Staff communicate normal temporary credentials outside the application.
- The fixed demo owner first names are unique and do not collide with the reserved `admin` username.
- Staff may continue using the existing staff-only owner and pet administration flows after security is introduced.

## Handled edge cases

- A forced-change user who requests another authenticated page is redirected to password change.
- A locked user remains locked until the deadline or a staff unlock, even with a correct password.
- A stale session belonging to a changed or deactivated account cannot continue authorizing requests.
- Attempting to provision or rename an account to a username differing only by case is a validation conflict.
- Re-running demo initialization does not reset a voluntarily changed demo password.
- Direct URL entry never exposes an endpoint merely because its navigation link is hidden.

## Behaviors to verify

- UC1-B1: The system permits unauthenticated access to the welcome page, login page, urgent-care guidance, static resources, and detail-free liveness/readiness endpoints.
- UC1-B2: The system redirects an unauthenticated HTML request for any other route to form login.
- UC1-B3: The system permits a staff user to access staff scheduling and existing owner-administration routes.
- UC1-B4: The system denies an owner access to staff scheduling and existing owner-administration routes.
- UC1-B5: The system renders only navigation links authorized for the authenticated user's role.
- UC1-B6: The system permits an owner to access only self-service records linked to that owner's account.
- UC1-B7: The system returns `404` when an owner addresses another owner's protected resource identifier.
- UC1-B8: The system permits an owner to update the supported contact fields on that owner's profile.
- UC1-B9: The system permits an owner to add a pet to that owner's profile.
- UC1-B10: The system permits an owner to edit a pet belonging to that owner.
- UC1-B11: The system prevents an owner from deleting an account or pet.
- UC1-B12: The system permits either authenticated role to view veterinarian names and specialties.
- UC1-B13: The system omits veterinarian schedules and availability from owner-accessible HTML and JSON.
- UC1-B14: The system permits staff to create an owner account linked to exactly one Owner with a temporary password.
- UC1-B15: The system permits staff to create an unlinked staff account with a temporary password.
- UC1-B16: The system redirects a normal account marked for password change away from every authenticated function except password change and logout.
- UC1-B17: The system clears the password-change requirement after a normal account selects a valid replacement password.
- UC1-B18: The system permits staff to reset an account to a temporary password that must be changed.
- UC1-B19: The system bootstraps a non-demo initial staff account from deployment credentials only when no active staff account exists.
- UC1-B20: The system creates the fixed missing staff and owner accounts when `demo-data` is enabled.
- UC1-B21: The system leaves demo accounts immediately usable without forced password change.
- UC1-B22: The system preserves an existing demo account and its current password during repeated demo initialization.
- UC1-B23: The system displays a demo-mode warning on login and authenticated pages when `demo-data` is active.
- UC1-B24: The system refuses startup when `demo-data` and a production profile are active together.
- UC1-B25: The system rejects a username that duplicates another username without regard to case.
- UC1-B26: The system rejects a normal replacement password shorter than eight characters or equal to its temporary password.
- UC1-B27: The system locks an account for 15 minutes after five consecutive failed logins.
- UC1-B28: The system clears an account's failed-login count after successful authentication.
- UC1-B29: The system permits staff to unlock a locked account before its deadline.
- UC1-B30: The system rejects a state-changing authenticated request without a valid CSRF token.
- UC1-B31: The system rotates the session identifier after successful authentication.
- UC1-B32: The system invalidates all sessions for an account after password change, password reset, username change, or deactivation.
- UC1-B33: The system permits staff to deactivate an account while retaining its linked history.
- UC1-B34: The system prevents deactivation of the final active staff account.
- UC1-B35: The system permits staff to rename a username without changing its immutable role or Owner link.
- UC1-B36: The system refuses reassignment of an account's role or linked Owner after creation.
- UC1-B37: The system refuses non-demo startup when no active staff account and no valid bootstrap credentials exist.
- UC1-B38: The system rejects creation of a second owner account for an Owner already linked to an account.
- UC1-B39: The system resolves every new owner and staff scheduling-interface message through the existing localization mechanism.

## Out of scope

- Owner self-registration or owner-initiated password recovery
- Account or pet deletion by owners
- Reassigning an account or its historical data to another identity
- OAuth, social login, multi-factor authentication, or a third administrator role
- Public veterinarian data or public detailed diagnostics
