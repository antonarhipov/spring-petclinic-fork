/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.springframework.samples.petclinic.scheduling;

import java.sql.Connection;
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
import org.springframework.samples.petclinic.scheduling.request.ActiveRequestExistsException;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentActiveRequestTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Test
	@Tag("AC-22")
	void simultaneousCreationAllowsAtMostOneActiveRequest_AC22() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
		}
		Integer petId = firstPetId();
		long requestCountBefore = this.requestRepository.count();

		List<CreationOutcome> outcomes = raceCreations();

		assertThat(outcomes).filteredOn(CreationOutcome::succeeded).hasSize(1);
		assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).hasSize(1);
		assertThat(this.requestRepository.count()).isEqualTo(requestCountBefore + 1);
		assertThat(this.requestRepository.findAll()).filteredOn(request -> petId.equals(request.getActivePetId()))
			.singleElement();
	}

	@Test
	@Tag("AC-23")
	void secondActiveRequestRefusedWithoutSecondRow_AC23() throws Exception {
		Integer petId = firstPetId();
		long requestCountBefore = this.requestRepository.count();
		long eventCountBefore = this.eventRepository.count();

		List<CreationOutcome> outcomes = raceCreations();

		assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded()).singleElement().satisfies(rejected -> {
			assertThat(rejected.failure()).isExactlyInstanceOf(ActiveRequestExistsException.class);
			assertThat(rejected.failure()).hasMessage("An active request already exists");
		});
		assertThat(this.requestRepository.count()).isEqualTo(requestCountBefore + 1);
		assertThat(this.eventRepository.count()).isEqualTo(eventCountBefore + 1);
		assertThat(this.requestRepository.findAll()).filteredOn(request -> petId.equals(request.getActivePetId()))
			.singleElement();
	}

	private List<CreationOutcome> raceCreations() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<CreationOutcome> first = executor.submit(() -> createInTransaction("first", ready, start));
			Future<CreationOutcome> second = executor.submit(() -> createInTransaction("second", ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private CreationOutcome createInTransaction(String label, CountDownLatch ready, CountDownLatch start) {
		TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
		try {
			Integer requestId = transaction.execute(status -> {
				Owner owner = this.ownerRepository.findById(1).orElseThrow();
				Pet pet = owner.getPets().stream().findFirst().orElseThrow();
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

	private Integer firstPetId() {
		return this.ownerRepository.findById(1).orElseThrow().getPets().stream().findFirst().orElseThrow().getId();
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent request latch timed out");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Concurrent request interrupted", ex);
		}
	}

	private record CreationOutcome(Integer requestId, ActiveRequestExistsException failure) {

		boolean succeeded() {
			return this.requestId != null;
		}

	}

}
