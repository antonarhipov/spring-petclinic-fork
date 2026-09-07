package org.springframework.samples.petclinic.scheduling.request;

import java.security.Principal;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.security.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthenticatedOwnerService {

	private final UserAccountRepository accounts;

	public AuthenticatedOwnerService(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	public Owner requireOwner(Principal principal) {
		if (principal == null) {
			throw new OwnerResourceNotFoundException();
		}
		Owner owner = this.accounts.findByUsername(principal.getName())
			.map(account -> account.getOwner())
			.orElseThrow(OwnerResourceNotFoundException::new);
		return owner;
	}

	@Transactional(readOnly = true)
	public Pet requirePet(Principal principal, int petId) {
		Pet pet = requireOwner(principal).getPet(petId);
		if (pet == null) {
			throw new OwnerResourceNotFoundException();
		}
		return pet;
	}

}
