package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOffer;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RepositoryFetchPlanTests {

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private AccountRepository accounts;

	@Autowired
	private PetRepository pets;

	@Autowired
	private VetRepository vets;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private RequestRevisionRepository revisions;

	@Autowired
	private AppointmentOfferRepository offers;

	@Test
	void ownerAccessLookupFetchesTheOwnerAndPets() {
		this.entityManager.clear();
		Account account = this.accounts.findOwnerAccessByUsername("george").orElseThrow();
		this.entityManager.clear();

		assertThat(account.getOwner().getPets()).extracting(Pet::getName).contains("Leo");
	}

	@Test
	void ownedRequestLookupFetchesDetailsUsedAfterTheRepositoryCall() {
		RequestRevision revision = createRequestForPet(1);
		Integer requestId = revision.getRequest().getId();
		this.entityManager.clear();

		assertThat(this.requests.findOwnedById(requestId, 2)).isEmpty();
		SchedulingRequest request = this.requests.findOwnedById(requestId, 1).orElseThrow();
		this.entityManager.clear();

		assertThat(request.getCurrentRevision().getSourceText()).isEqualTo("Routine appointment request");
		assertThat(request.getCurrentRevision().getRequest().getPet().getName()).isEqualTo("Leo");
	}

	@Test
	void currentOfferLookupFetchesTheVeterinarian() {
		RequestRevision revision = createRequestForPet(1);
		Vet vet = this.vets.findById(1).orElseThrow();
		Instant now = Instant.parse("2030-01-01T09:00:00Z");
		this.offers.saveAndFlush(new AppointmentOffer(revision, vet, now, 30, now, now.plusSeconds(600), "Match"));
		this.entityManager.clear();

		AppointmentOffer offer = this.offers
			.findFirstByRevisionIdAndStateOrderByOfferedAtDesc(revision.getId(), OfferState.HELD)
			.orElseThrow();
		this.entityManager.clear();

		assertThat(offer.getVet().getLastName()).isEqualTo("Carter");
		assertThat(offer.getRationale()).isEqualTo("Match");
	}

	private RequestRevision createRequestForPet(Integer petId) {
		Pet pet = this.pets.findById(petId).orElseThrow();
		SchedulingRequest request = this.requests.save(new SchedulingRequest(pet, false));
		RequestRevision revision = this.revisions.save(new RequestRevision(request, 1, "Routine appointment request",
				true, Instant.parse("2030-01-01T08:00:00Z"), "fetch-plan-test"));
		request.setCurrentRevision(revision);
		this.requests.flush();
		return revision;
	}

}
