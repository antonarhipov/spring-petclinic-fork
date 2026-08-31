package org.springframework.samples.petclinic.owner;

import java.util.Objects;

import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Enforces the scheduling-history retention boundary before owner or pet deletion. */
@Service
public class OwnerRetentionService {

	private final OwnerRepository ownerRepository;

	private final SchedulingRequestRepository requestRepository;

	private final AppointmentRepository appointmentRepository;

	private final VisitRepository visitRepository;

	public OwnerRetentionService(OwnerRepository ownerRepository, SchedulingRequestRepository requestRepository,
			AppointmentRepository appointmentRepository, VisitRepository visitRepository) {
		this.ownerRepository = ownerRepository;
		this.requestRepository = requestRepository;
		this.appointmentRepository = appointmentRepository;
		this.visitRepository = visitRepository;
	}

	@Transactional
	public void deleteOwner(Integer ownerId) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
		if (this.requestRepository.existsByOwnerId(ownerId) || this.appointmentRepository.existsByOwnerId(ownerId)
				|| owner.getPets().stream().anyMatch(pet -> this.visitRepository.existsByPetId(pet.getId()))) {
			throw retainedHistoryException();
		}
		this.ownerRepository.delete(owner);
	}

	@Transactional
	public void deletePet(Integer ownerId, Integer petId) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Objects.requireNonNull(petId, "petId must not be null");
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found for this owner: " + petId);
		}
		if (this.requestRepository.existsByPetId(petId) || this.appointmentRepository.existsByPetId(petId)
				|| this.visitRepository.existsByPetId(petId)) {
			throw retainedHistoryException();
		}
		owner.getPets().remove(pet);
		this.ownerRepository.saveAndFlush(owner);
	}

	private IllegalStateException retainedHistoryException() {
		return new IllegalStateException(
				"This record cannot be deleted because scheduling or care history must be retained. Keep the record and update its active details instead.");
	}

}
