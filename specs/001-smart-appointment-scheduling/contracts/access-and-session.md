# Access and Session UI Contract

All state-changing forms include CSRF protection. Server-side handlers resolve
the signed-in account and never trust an owner or staff identity supplied in a
route or form field.

| Route | Access | Request and behavior | Success outcome |
|-------|--------|----------------------|-----------------|
| `GET /login` | Public | Shows username/password form. | Login page. |
| `POST /login` | Public | Authenticates a local account and applies failed-attempt throttling. | Owner or staff landing page; temporary-password accounts go to password change. |
| `GET /password/change` | Authenticated temporary-password account | Shows required password-change form. | Password-change page. |
| `POST /password/change` | Authenticated temporary-password account | Validates a new password of at least six characters. | Clears temporary-password state and starts a new session. |
| `POST /logout` | Authenticated | Ends the current session. | Public landing page. |
| `GET /` | Public | Shows non-operational landing content. | Landing/login links. |

## Authorization Rules

| Area | Owner | Staff |
|------|-------|-------|
| Own profile, pets, requests, offers, appointments, visit history | Read only for the signed-in owner's records | Full clinic access |
| Request creation, revision, confirmation, offer actions, withdrawal, cancellation | Permitted only for the signed-in owner's pets and appointments | Permitted while acting on behalf of an owner and audited |
| Queue, calendar, availability, clinic settings, audit records | Denied | Permitted |
| Owner/pet administration | Denied | Permitted |
| Veterinarian/specialty catalog administration | Denied | Out of scope |

Unauthorized or cross-owner requests return an access-denied response without
disclosing whether the target record exists.
