/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentConfirmationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private InterpretationRepository interpretationRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private ClinicOpeningHourRepository openingHourRepository;

	@Autowired
	private VetWeeklyBlockRepository weeklyBlockRepository;

	@Autowired
	private VetExceptionRepository exceptionRepository;

	@Autowired
	private HoldService holdService;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private Clock clock;

	@Test
	@Tag("AC-65")
	void simultaneousOwnersAtMostOneConfirmsSameVetTime_AC65() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
		}
		SchedulingRequest george = interpretedRequest(1, "george");
		SchedulingRequest betty = interpretedRequest(2, "betty");
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime contested = ZonedDateTime.parse("2026-09-08T10:00:00+02:00[Europe/Amsterdam]");
		SlotRanker sameSlot = (request, interpretation) -> List
			.of(new SlotRanker.RankedSlot(vet, contested, 30, "contested", "0hard/0medium/0soft"));
		SuggestionService service = new SuggestionService(this.lifecycleService, this.holdService,
				this.interpretationRepository, sameSlot, this.appointmentLifecycleService, this.vetRepository,
				this.openingHourRepository, this.weeklyBlockRepository, this.exceptionRepository, this.eventRepository,
				this.clock);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<RequestState> first = executor
				.submit(() -> confirmInTransaction(service, george.getId(), "george", ready, start));
			Future<RequestState> second = executor
				.submit(() -> confirmInTransaction(service, betty.getId(), "betty", ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF);
		}
		finally {
			executor.shutdownNow();
		}

		List<SchedulingRequest> persisted = List.of(this.requestRepository.findById(george.getId()).orElseThrow(),
				this.requestRepository.findById(betty.getId()).orElseThrow());
		assertThat(persisted).filteredOn(SchedulingRequest::hasHold).singleElement().satisfies(winner -> {
			assertThat(winner.getHeldVet().getId()).isEqualTo(vet.getId());
			assertThat(winner.getHeldStart()).isEqualTo(contested);
		});
		assertThat(this.appointmentRepository.findAll()).isEmpty();
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
				throw new IllegalStateException("Concurrent confirmation latch timed out");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Concurrent confirmation interrupted", ex);
		}
	}

}
