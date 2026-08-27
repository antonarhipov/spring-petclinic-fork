package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.ReservationType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class RepositoryFetchPlanTests {

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private SpecialtyRepository specialtyRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void requestReadMethodsLoadAssociationsUsedOutsideTheRepositoryTransaction() {
		SchedulingRequest saved = saveRequest(RequestStatus.STAFF_QUEUED);
		Integer requestId = saved.getId();
		Integer ownerId = saved.getOwner().getId();
		entityManager.clear();

		SchedulingRequest ownerRequest = requestRepository.findWithDetailsByIdAndOwnerId(requestId, ownerId)
			.orElseThrow();
		assertRequestDetailsInitialized(ownerRequest);

		entityManager.clear();
		SchedulingRequest staffRequest = requestRepository.findWithDetailsById(requestId).orElseThrow();
		assertRequestDetailsInitialized(staffRequest);

		entityManager.clear();
		SchedulingRequest queuedRequest = requestRepository
			.findByStatusInOrderByUrgentDescFirstQueuedAtAsc(List.of(RequestStatus.STAFF_QUEUED))
			.stream()
			.filter(request -> requestId.equals(request.getId()))
			.findFirst()
			.orElseThrow();
		assertRequestDetailsInitialized(queuedRequest);

		entityManager.clear();
		assertThat(queuedRequest.getOwner().getFirstName()).isNotBlank();
		assertThat(queuedRequest.getPet().getName()).isNotBlank();
		assertThat(queuedRequest.getPet().getType().getName()).isNotBlank();
		assertThat(queuedRequest.getRequiredSpecialty().getName()).isNotBlank();
		assertThat(queuedRequest.getPreferredVet().getLastName()).isNotBlank();
	}

	@Test
	void reservationsLoadTheVetUsedByTheStaffOfferView() {
		SchedulingRequest request = saveRequest(RequestStatus.STAFF_OFFERED);
		Reservation reservation = new Reservation();
		reservation.setReservationType(ReservationType.STAFF_OFFER);
		reservation.setStatus(ReservationStatus.ACTIVE);
		reservation.setRequest(request);
		reservation.setOwner(request.getOwner());
		reservation.setPet(request.getPet());
		reservation.setVet(request.getPreferredVet());
		reservation.setStartTime(Instant.parse("2030-01-01T10:00:00Z"));
		reservation.setEndTime(Instant.parse("2030-01-01T10:30:00Z"));
		reservation.setExpiresAt(Instant.parse("2030-01-02T10:00:00Z"));
		reservation = reservationRepository.saveAndFlush(reservation);
		Integer reservationId = reservation.getId();
		entityManager.clear();

		Reservation loaded = reservationRepository.findWithVetByRequestId(request.getId())
			.stream()
			.filter(candidate -> reservationId.equals(candidate.getId()))
			.findFirst()
			.orElseThrow();

		assertThat(Hibernate.isInitialized(loaded.getVet())).isTrue();
		assertThat(Hibernate.isInitialized(loaded.getVet().getSpecialties())).isTrue();
		entityManager.clear();
		assertThat(loaded.getVet().getFirstName()).isNotBlank();
	}

	private SchedulingRequest saveRequest(RequestStatus status) {
		Owner owner = ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().getFirst();
		Vet vet = vetRepository.findById(3).orElseThrow();
		Specialty specialty = specialtyRepository.findById(1).orElseThrow();

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setStatus(status);
		request.setOriginalText("Routine appointment");
		request.setLanguage("en");
		request.setRequiredSpecialty(specialty);
		request.setPreferredVet(vet);
		request.setFirstQueuedAt(Instant.parse("2030-01-01T09:00:00Z"));
		return requestRepository.saveAndFlush(request);
	}

	private void assertRequestDetailsInitialized(SchedulingRequest request) {
		assertThat(Hibernate.isInitialized(request.getOwner())).isTrue();
		assertThat(Hibernate.isInitialized(request.getPet())).isTrue();
		assertThat(Hibernate.isInitialized(request.getPet().getType())).isTrue();
		assertThat(Hibernate.isInitialized(request.getRequiredSpecialty())).isTrue();
		assertThat(Hibernate.isInitialized(request.getPreferredVet())).isTrue();
	}

}
