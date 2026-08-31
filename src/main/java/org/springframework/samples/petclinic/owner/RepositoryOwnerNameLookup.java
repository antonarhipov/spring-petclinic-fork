package org.springframework.samples.petclinic.owner;

import org.springframework.samples.petclinic.account.OwnerNameLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RepositoryOwnerNameLookup implements OwnerNameLookup {

	private final OwnerRepository ownerRepository;

	public RepositoryOwnerNameLookup(OwnerRepository ownerRepository) {
		this.ownerRepository = ownerRepository;
	}

	@Override
	public OwnerName findOwnerName(Integer ownerId) {
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
		return new OwnerName(owner.getFirstName(), owner.getLastName());
	}

}
