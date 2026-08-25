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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.model.Hold;
import org.springframework.samples.petclinic.scheduling.model.HoldRepository;
import org.springframework.samples.petclinic.scheduling.model.HoldStatus;
import org.springframework.samples.petclinic.scheduling.model.OccupancyType;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancy;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancyRepository;
import org.springframework.samples.petclinic.scheduling.model.SuggestionView;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(HoldService.class)
class HoldServiceTests {

	@Autowired
	private HoldService holdService;

	@Autowired
	private HoldRepository holds;

	@Autowired
	private SlotOccupancyRepository slotOccupancies;

	@Autowired
	private SchedulingRequestRepository schedulingRequests;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private VetRepository vets;

	private Owner owner1;

	private Owner owner2;

	private Vet vet;

	@BeforeEach
	void setUp() {
		this.owner1 = this.owners.findById(1).orElseThrow();
		this.owner2 = this.owners.findById(2).orElseThrow();
		this.vet = this.vets.findById(1).orElseThrow();
	}

	@Test
	void shouldPlaceHoldAndOccupySlotTransactionally() {
		Pet pet = this.owner1.getPets().get(0);
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner1);
		request.setPet(pet);
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDateTime start = LocalDateTime.of(2026, 9, 1, 10, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 1, 10, 30);

		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));

		assertThat(hold.getId()).isNotNull();
		assertThat(hold.getStatus()).isEqualTo(HoldStatus.ACTIVE);
		assertThat(hold.getExpiresAt()).isNotNull();

		Optional<SlotOccupancy> occupancy = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start);
		assertThat(occupancy).isPresent();
		assertThat(occupancy.get().getOccupancyType()).isEqualTo(OccupancyType.HOLD);
		assertThat(occupancy.get().getReferenceId()).isEqualTo(hold.getId());

		SchedulingRequest reloaded = this.schedulingRequests.findById(request.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.SLOT_HELD);
	}

	@Test
	void shouldEnforceSlotExclusivityAtDatabaseLevelUnderConcurrentHoldAttempts() {
		Pet pet1 = this.owner1.getPets().get(0);
		SchedulingRequest request1 = new SchedulingRequest();
		request1.setOwner(this.owner1);
		request1.setPet(pet1);
		request1.setState(RequestState.SUGGESTING);
		request1 = this.schedulingRequests.saveAndFlush(request1);

		Pet pet2 = this.owner2.getPets().get(0);
		SchedulingRequest request2 = new SchedulingRequest();
		request2.setOwner(this.owner2);
		request2.setPet(pet2);
		request2.setState(RequestState.SUGGESTING);
		request2 = this.schedulingRequests.saveAndFlush(request2);

		LocalDateTime start = LocalDateTime.of(2026, 9, 1, 14, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 1, 14, 30);

		// First hold succeeds
		this.holdService.placeHold(request1, this.vet, start, end, Duration.ofMinutes(10));

		// Second hold on the same (vet, start) must fail at DB constraint level (RULE-11,
		// AC-38)
		final SchedulingRequest r2 = request2;
		assertThatThrownBy(() -> this.holdService.placeHold(r2, this.vet, start, end, Duration.ofMinutes(10)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shouldExposeOnlySuggestionDetailsToOwner() {
		Pet pet = this.owner1.getPets().get(0);
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner1);
		request.setPet(pet);
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDateTime start = LocalDateTime.of(2026, 9, 2, 9, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 2, 9, 30);

		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));
		SuggestionView view = this.holdService.toSuggestionView(hold);

		assertThat(view.vetName()).isEqualTo(this.vet.getFirstName() + " " + this.vet.getLastName());
		assertThat(view.date()).isEqualTo(start.toLocalDate());
		assertThat(view.startTime()).isEqualTo(start.toLocalTime());
		assertThat(view.durationMinutes()).isEqualTo(30);
		assertThat(view.secondsRemaining()).isGreaterThan(0);
	}

	@Test
	void shouldExpireHoldAndReleaseSlotOccupancy() {
		Pet pet = this.owner1.getPets().get(0);
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(this.owner1);
		request.setPet(pet);
		request.setState(RequestState.SUGGESTING);
		request = this.schedulingRequests.saveAndFlush(request);

		LocalDateTime start = LocalDateTime.of(2026, 9, 3, 11, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 3, 11, 30);

		Hold hold = this.holdService.placeHold(request, this.vet, start, end, Duration.ofMinutes(10));
		this.holdService.expireHold(hold);

		Optional<Hold> reloadedHold = this.holds.findById(hold.getId());
		assertThat(reloadedHold).isPresent();
		assertThat(reloadedHold.get().getStatus()).isEqualTo(HoldStatus.EXPIRED);

		Optional<SlotOccupancy> occupancy = this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start);
		assertThat(occupancy).isEmpty();
	}

	@Test
	void shouldSweepExpiredHoldsInBatch() {
		Pet pet1 = this.owner1.getPets().get(0);
		SchedulingRequest request1 = new SchedulingRequest();
		request1.setOwner(this.owner1);
		request1.setPet(pet1);
		request1.setState(RequestState.SUGGESTING);
		request1 = this.schedulingRequests.saveAndFlush(request1);

		LocalDateTime start = LocalDateTime.of(2026, 9, 3, 14, 0);
		LocalDateTime end = LocalDateTime.of(2026, 9, 3, 14, 30);

		Hold hold = this.holdService.placeHold(request1, this.vet, start, end, Duration.ofMinutes(10));
		hold.setExpiresAt(Instant.now().minusSeconds(60));
		this.holds.saveAndFlush(hold);

		this.holdService.sweepExpiredHolds();

		Hold reloaded = this.holds.findById(hold.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(HoldStatus.EXPIRED);
		assertThat(this.slotOccupancies.findByVetIdAndStartTime(this.vet.getId(), start)).isEmpty();

		SchedulingRequest reloadedReq = this.schedulingRequests.findById(request1.getId()).orElseThrow();
		assertThat(reloadedReq.getState()).isEqualTo(RequestState.SUGGESTING);
	}

}
