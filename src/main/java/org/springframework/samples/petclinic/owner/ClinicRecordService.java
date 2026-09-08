package org.springframework.samples.petclinic.owner;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class ClinicRecordService {

	private static final String PET_NAME_CONSTRAINT = "unique_owner_pet_name";

	private final OwnerRepository owners;

	public ClinicRecordService(OwnerRepository owners) {
		this.owners = owners;
	}

	@Transactional
	public Owner saveOwner(Owner owner) {
		return this.owners.saveAndFlush(owner);
	}

	@Transactional
	public void createPet(Owner owner, Pet pet) {
		if (hasOtherPetNamed(owner, pet)) {
			throw new DuplicatePetNameException();
		}
		owner.addPet(pet);
		savePetChange(owner);
	}

	@Transactional
	public void updatePet(Owner owner, Pet pet) {
		Integer id = pet.getId();
		Assert.state(id != null, "'pet.getId()' must not be null");
		if (hasOtherPetNamed(owner, pet)) {
			throw new DuplicatePetNameException();
		}
		Pet existingPet = owner.getPet(id);
		Assert.state(existingPet != null, "Pet must belong to owner");
		existingPet.setName(pet.getName());
		existingPet.setBirthDate(pet.getBirthDate());
		existingPet.setType(pet.getType());
		savePetChange(owner);
	}

	@Transactional
	public void addWalkInVisit(Owner owner, int petId, Visit visit) {
		visit.setAppointment(null);
		owner.addVisit(petId, visit);
		this.owners.saveAndFlush(owner);
	}

	private boolean hasOtherPetNamed(Owner owner, Pet submitted) {
		if (submitted.getName() == null) {
			return false;
		}
		return owner.getPets()
			.stream()
			.anyMatch(pet -> !Objects.equals(pet.getId(), submitted.getId()) && pet.getName() != null
					&& pet.getName().equalsIgnoreCase(submitted.getName()));
	}

	private void savePetChange(Owner owner) {
		try {
			this.owners.saveAndFlush(owner);
		}
		catch (DataIntegrityViolationException ex) {
			if (!isDuplicatePetNameViolation(ex)) {
				throw ex;
			}
			throw new DuplicatePetNameException();
		}
	}

	private boolean isDuplicatePetNameViolation(DataIntegrityViolationException ex) {
		Throwable cause = ex;
		while (cause != null) {
			String message = cause.getMessage();
			if (message != null && message.toLowerCase().contains(PET_NAME_CONSTRAINT)) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

}
