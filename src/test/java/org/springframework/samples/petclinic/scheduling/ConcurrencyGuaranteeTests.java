/*
 * Copyright 2012-2026 the original author or authors.
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

import java.sql.Connection;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHourRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetExceptionRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlockRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.request.ActiveRequestExistsException;
import org.springframework.samples.petclinic.scheduling.request.HoldService;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
import org.springframework.samples.petclinic.scheduling.solver.SlotRanker;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrencyGuaranteeTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private HoldService holdService;

	@Autowired
	private ClinicOpeningHourRepository openingHourRepository;

	@Autowired
	private VetWeeklyBlockRepository weeklyBlockRepository;

	@Autowired
	private VetExceptionRepository exceptionRepository;

	@Autowired
	private Clock clock;

	@Test
	@Tag("AC-141")
	void simultaneousConfirmAndActiveRequestEachHaveOneWinner_AC141() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
		}

		// -----------------------------------------------------------------------------------------
		// Guarantee 1: Two simultaneous requests for the same pet yield at most one
		// active request (RULE-9)
		// -----------------------------------------------------------------------------------------
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		int petId = pet.getId();
		long requestCountBefore = this.requestRepository.count();

		List<CreationOutcome> creationOutcomes = raceCreations(petId);

		assertThat(creationOutcomes).filteredOn(CreationOutcome::succeeded).hasSize(1);
		assertThat(creationOutcomes).filteredOn(outcome -> !outcome.succeeded()).hasSize(1);
		assertThat(this.requestRepository.count()).isEqualTo(requestCountBefore + 1);
		assertThat(this.requestRepository.findAll())
			.filteredOn(request -> Integer.valueOf(petId).equals(request.getActivePetId()))
			.singleElement();

		// Clean up active request before Guarantee 2
		SchedulingRequest activeReq = this.requestRepository.findAll()
			.stream()
			.filter(r -> Integer.valueOf(petId).equals(r.getActivePetId()))
			.findFirst()
			.orElseThrow();
		this.lifecycleService.abandon(activeReq, "george", "Cleanup");

		// -----------------------------------------------------------------------------------------
		// Guarantee 2: Two simultaneous confirms of the same hold yield at most one
		// winner (RULE-10)
		// -----------------------------------------------------------------------------------------
		SchedulingRequest reqGeorge = interpretedRequest(1, "george");
		SchedulingRequest reqBetty = interpretedRequest(2, "betty");

		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime contested = ZonedDateTime.parse("2026-09-08T10:00:00+02:00[Europe/Amsterdam]");
		SlotRanker sameSlot = (request, interpretation) -> List
			.of(new SlotRanker.RankedSlot(vet, contested, 30, "contested", "0hard/0medium/0soft"));
		SuggestionService suggestionService = new SuggestionService(this.lifecycleService, this.holdService,
				this.interpretationRepository, sameSlot, this.appointmentLifecycleService, this.vetRepository,
				this.openingHourRepository, this.weeklyBlockRepository, this.exceptionRepository, this.eventRepository,
				this.clock);

		List<RequestState> confirmOutcomes = raceConfirmations(suggestionService, reqGeorge.getId(), reqBetty.getId());

		assertThat(confirmOutcomes).containsExactlyInAnyOrder(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF);

		List<SchedulingRequest> persisted = List.of(this.requestRepository.findById(reqGeorge.getId()).orElseThrow(),
				this.requestRepository.findById(reqBetty.getId()).orElseThrow());
		assertThat(persisted).filteredOn(SchedulingRequest::hasHold).singleElement().satisfies(winner -> {
			assertThat(winner.getHeldVet().getId()).isEqualTo(vet.getId());
			assertThat(winner.getHeldStart()).isEqualTo(contested);
		});
		assertThat(this.appointmentRepository.findAll()).isEmpty();
	}

	private List<CreationOutcome> raceCreations(int petId) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<CreationOutcome> first = executor.submit(() -> createInTransaction("first", petId, ready, start));
			Future<CreationOutcome> second = executor.submit(() -> createInTransaction("second", petId, ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private CreationOutcome createInTransaction(String label, int petId, CountDownLatch ready, CountDownLatch start) {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		try {
			Integer requestId = transaction.execute(status -> {
				Owner owner = this.ownerRepository.findById(1).orElseThrow();
				Pet pet = owner.getPet(petId);
				ready.countDown();
				await(start);
				SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "Concurrent " + label,
						"Tuesday morning", "george");
				return request.getId();
			});
			return new CreationOutcome(requestId, null);
		}
		catch (ActiveRequestExistsException ex) {
			return new CreationOutcome(null, ex);
		}
	}

	private List<RequestState> raceConfirmations(SuggestionService service, int reqId1, int reqId2) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<RequestState> first = executor
				.submit(() -> confirmInTransaction(service, reqId1, "george", ready, start));
			Future<RequestState> second = executor
				.submit(() -> confirmInTransaction(service, reqId2, "betty", ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private RequestState confirmInTransaction(SuggestionService service, Integer requestId, String actor,
			CountDownLatch ready, CountDownLatch start) {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		return transaction.execute(status -> {
			SchedulingRequest request = this.requestRepository.findById(requestId).orElseThrow();
			ready.countDown();
			await(start);
			return service.confirm(request, actor).getState();
		});
	}

	private SchedulingRequest interpretedRequest(int ownerId, String actor) {
		Owner owner = this.ownerRepository.findById(ownerId).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, "Concurrent request", "Tuesday",
				actor);
		this.lifecycleService.consent(request, actor);
		return this.lifecycleService.interpretationUsable(request, "system");
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrency latch timed out");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Concurrency operation interrupted", ex);
		}
	}

	private record CreationOutcome(Integer requestId, ActiveRequestExistsException failure) {

		boolean succeeded() {
			return this.requestId != null;
		}

	}

}
