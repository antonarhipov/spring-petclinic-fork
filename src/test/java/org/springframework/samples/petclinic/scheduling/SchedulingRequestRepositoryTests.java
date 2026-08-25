/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SchedulingRequestRepositoryTests {

	@Autowired
	private SchedulingRequestRepository schedulingRequests;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private EntityManager entityManager;

	@Test
	void shouldSaveAndFindSchedulingRequest() {
		Owner owner = this.owners.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setAiConsent(true);
		request.setRawText("Checkup next Monday");
		request.setPreferredDateStart(LocalDate.now().plusDays(1));
		request.setPreferredDateEnd(LocalDate.now().plusDays(7));
		request.setState(RequestState.DRAFT);

		SchedulingRequest saved = this.schedulingRequests.saveAndFlush(request);
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getVersion()).isEqualTo(0);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getActivePetKey()).isEqualTo(pet.getId());

		Optional<SchedulingRequest> found = this.schedulingRequests.findById(saved.getId());
		assertThat(found).isPresent();
		assertThat(found.get().getRawText()).isEqualTo("Checkup next Monday");
		assertThat(found.get().isAiConsent()).isTrue();
	}

	/**
	 * Reproduces the {@code LazyInitializationException} that crashed the
	 * {@code scheduling/status} view. With {@code spring.jpa.open-in-view=false} the
	 * Hibernate session is closed once the repository call returns, so the lazy
	 * {@code owner}/{@code pet} associations must be fetched by
	 * {@link SchedulingRequestRepository#findByIdWithOwnerAndPet(Integer)}; otherwise
	 * they are uninitialized proxies that fail during view rendering.
	 */
	@Test
	void findByIdWithOwnerAndPetEagerlyInitializesAssociations() {
		Owner owner = this.owners.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setRawText("Checkup");
		request.setState(RequestState.INTERPRETING);
		SchedulingRequest saved = this.schedulingRequests.saveAndFlush(request);

		// Detach everything so the finder performs a fresh load, mirroring a new request
		// without an open session.
		this.entityManager.clear();

		SchedulingRequest loaded = this.schedulingRequests.findByIdWithOwnerAndPet(saved.getId()).orElseThrow();

		assertThat(loaded.getPet()).isNotNull();
		assertThat(Hibernate.isInitialized(loaded.getPet())).isTrue();
		assertThat(loaded.getPet().getName()).isNotBlank();
		assertThat(loaded.getOwner()).isNotNull();
		assertThat(Hibernate.isInitialized(loaded.getOwner())).isTrue();
		assertThat(loaded.getOwner().getFirstName()).isNotBlank();
	}

	/**
	 * Reproduces the {@code LazyInitializationException} that crashed the
	 * {@code scheduling/queueList} view for staff. With
	 * {@code spring.jpa.open-in-view=false} the Hibernate session is closed once the
	 * repository call returns, so the lazy {@code owner}/{@code pet} associations of
	 * every queued request must be fetched by
	 * {@link SchedulingRequestRepository#findByStateWithOwnerAndPet(RequestState)};
	 * otherwise they are uninitialized proxies that fail during view rendering.
	 */
	@Test
	void findByStateWithOwnerAndPetEagerlyInitializesAssociations() {
		Owner owner = this.owners.findById(5).orElseThrow();
		Pet pet = owner.getPets().get(0);

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setRawText("Emergency visit");
		request.setState(RequestState.STAFF_QUEUED);
		request.setQueueReason(QueueReason.EMERGENCY);
		this.schedulingRequests.saveAndFlush(request);

		// Detach everything so the finder performs a fresh load, mirroring a new request
		// without an open session.
		this.entityManager.clear();

		var loaded = this.schedulingRequests.findByStateWithOwnerAndPet(RequestState.STAFF_QUEUED);

		assertThat(loaded).isNotEmpty();
		SchedulingRequest queued = loaded.get(0);
		assertThat(queued.getOwner()).isNotNull();
		assertThat(Hibernate.isInitialized(queued.getOwner())).isTrue();
		assertThat(queued.getOwner().getFirstName()).isNotBlank();
		assertThat(queued.getPet()).isNotNull();
		assertThat(Hibernate.isInitialized(queued.getPet())).isTrue();
		assertThat(queued.getPet().getName()).isNotBlank();
	}

	@Test
	void shouldEnforceOptimisticLockingOnConcurrentUpdate() {
		Owner owner = this.owners.findById(2).orElseThrow();
		Pet pet = owner.getPets().get(0);

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setAiConsent(true);
		request.setRawText("Initial text");
		SchedulingRequest saved = this.schedulingRequests.saveAndFlush(request);
		Integer id = saved.getId();

		// Simulate concurrent transaction updating the row in database
		this.entityManager.createNativeQuery("UPDATE scheduling_requests SET version = version + 1 WHERE id = :id")
			.setParameter("id", id)
			.executeUpdate();

		saved.setRawText("Updated in first context with stale version");
		assertThatThrownBy(() -> this.schedulingRequests.saveAndFlush(saved))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void shouldEnforceUniqueActivePetConstraint() {
		Owner owner = this.owners.findById(3).orElseThrow();
		Pet pet = owner.getPets().get(0);

		SchedulingRequest request1 = new SchedulingRequest();
		request1.setOwner(owner);
		request1.setPet(pet);
		request1.setState(RequestState.DRAFT);
		this.schedulingRequests.saveAndFlush(request1);

		SchedulingRequest request2 = new SchedulingRequest();
		request2.setOwner(owner);
		request2.setPet(pet);
		request2.setState(RequestState.INTERPRETING);

		assertThatThrownBy(() -> this.schedulingRequests.saveAndFlush(request2))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shouldAllowNewActiveRequestAfterTerminalState() {
		Owner owner = this.owners.findById(4).orElseThrow();
		Pet pet = owner.getPets().get(0);

		SchedulingRequest request1 = new SchedulingRequest();
		request1.setOwner(owner);
		request1.setPet(pet);
		request1.setState(RequestState.CONFIRMED);
		this.schedulingRequests.saveAndFlush(request1);
		assertThat(request1.getActivePetKey()).isNull();

		SchedulingRequest request2 = new SchedulingRequest();
		request2.setOwner(owner);
		request2.setPet(pet);
		request2.setState(RequestState.DRAFT);
		SchedulingRequest saved2 = this.schedulingRequests.saveAndFlush(request2);
		assertThat(saved2.getId()).isNotNull();
		assertThat(saved2.getActivePetKey()).isEqualTo(pet.getId());
	}

}
