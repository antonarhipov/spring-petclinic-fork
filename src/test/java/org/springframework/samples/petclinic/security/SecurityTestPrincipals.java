package org.springframework.samples.petclinic.security;

import org.springframework.samples.petclinic.account.Role;

/**
 * Factory helpers for authenticated {@link PetClinicPrincipal} instances in security
 * tests.
 */
public final class SecurityTestPrincipals {

	private SecurityTestPrincipals() {
	}

	public static PetClinicPrincipal owner(long accountId, String username, int ownerId) {
		return owner(accountId, username, ownerId, 0L, false);
	}

	public static PetClinicPrincipal owner(long accountId, String username, int ownerId, long sessionVersion,
			boolean passwordChangeRequired) {
		return new PetClinicPrincipal(accountId, username, "{noop}password", Role.OWNER, ownerId, true, null,
				passwordChangeRequired, sessionVersion);
	}

	public static PetClinicPrincipal staff(long accountId, String username) {
		return staff(accountId, username, 0L, false);
	}

	public static PetClinicPrincipal staff(long accountId, String username, long sessionVersion,
			boolean passwordChangeRequired) {
		return new PetClinicPrincipal(accountId, username, "{noop}password", Role.STAFF, null, true, null,
				passwordChangeRequired, sessionVersion);
	}

}
