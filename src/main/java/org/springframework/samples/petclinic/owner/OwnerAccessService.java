package org.springframework.samples.petclinic.owner;

import java.util.Objects;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OwnerAccessService {

	private final OwnerRepository ownerRepository;

	private final OwnerHistoryService ownerHistoryService;

	public OwnerAccessService(OwnerRepository ownerRepository, OwnerHistoryService ownerHistoryService) {
		this.ownerRepository = Objects.requireNonNull(ownerRepository, "ownerRepository must not be null");
		this.ownerHistoryService = Objects.requireNonNull(ownerHistoryService, "ownerHistoryService must not be null");
	}

	public void verifyOwnerAccess(PetClinicPrincipal principal, Integer requestedOwnerId) {
		if (principal == null) {
			throw new AccessDeniedException("Authentication required");
		}
		if (principal.getRole() == Role.STAFF) {
			return; // Staff can access any owner
		}
		if (principal.getRole() == Role.OWNER) {
			if (principal.getOwnerId() == null || !principal.getOwnerId().equals(requestedOwnerId)) {
				throw new AccessDeniedException("Access denied to owner record: " + requestedOwnerId);
			}
			return;
		}
		throw new AccessDeniedException("Unsupported role: " + principal.getRole());
	}

	@Transactional(readOnly = true)
	public Owner getOwnerProfile(Integer ownerId, PetClinicPrincipal principal) {
		verifyOwnerAccess(principal, ownerId);
		return this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	public Owner updateOwnerContact(Integer ownerId, String address, String city, String telephone,
			PetClinicPrincipal principal) {
		verifyOwnerAccess(principal, ownerId);
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));

		// Only contact fields are permitted for update
		owner.setAddress(address);
		owner.setCity(city);
		owner.setTelephone(telephone);
		Owner saved = this.ownerRepository.save(owner);

		this.ownerHistoryService.recordEvent(ownerId, "PROFILE_UPDATE",
				"Contact information updated (address, city, telephone)", "Owner", String.valueOf(ownerId));

		return saved;
	}

}
