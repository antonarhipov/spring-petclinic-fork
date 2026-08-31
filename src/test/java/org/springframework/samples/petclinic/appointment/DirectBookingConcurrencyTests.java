package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.appointment.DirectBookingService.DirectBookingRequest;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DirectBookingConcurrencyTests {

	@Autowired
	private DirectBookingService directBookingService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeEventRepository appointmentChangeEventRepository;

	@Autowired
	private EffectiveAvailabilityService effectiveAvailabilityService;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		this.executorService = Executors.newFixedThreadPool(4);
		this.appointmentChangeEventRepository.deleteAll();
		this.appointmentRepository.deleteAll();
	}

	@AfterEach
	void tearDown() {
		this.executorService.shutdown();
		this.appointmentChangeEventRepository.deleteAll();
		this.appointmentRepository.deleteAll();
	}

	@Test
	void concurrentDirectBookingForSameVetAndSlot_onlyOneSucceeds() throws Exception {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		// Monday within operating hours
		LocalDate monday = LocalDate.of(2026, 9, 7);
		Instant startAt = ZonedDateTime.of(monday.atTime(10, 0), zoneId).toInstant();
		Instant endAt = ZonedDateTime.of(monday.atTime(10, 30), zoneId).toInstant();

		// Two different owners and pets attempting to book the same vet at the same time
		DirectBookingRequest req1 = new DirectBookingRequest(1, 1, 1, startAt, endAt, true, "PHONE", "Owner 1", 1L,
				null);
		DirectBookingRequest req2 = new DirectBookingRequest(2, 2, 1, startAt, endAt, true, "PHONE", "Owner 2", 1L,
				null);

		CountDownLatch startLatch = new CountDownLatch(1);

		Callable<Appointment> task1 = () -> {
			startLatch.await();
			return this.directBookingService.bookDirectly(req1);
		};

		Callable<Appointment> task2 = () -> {
			startLatch.await();
			return this.directBookingService.bookDirectly(req2);
		};

		Future<Appointment> f1 = this.executorService.submit(task1);
		Future<Appointment> f2 = this.executorService.submit(task2);

		startLatch.countDown();

		int successCount = 0;
		int failureCount = 0;

		try {
			f1.get(5, TimeUnit.SECONDS);
			successCount++;
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof AvailabilityConflictException
					|| ex.getCause() instanceof IllegalArgumentException) {
				failureCount++;
			}
			else {
				throw ex;
			}
		}

		try {
			f2.get(5, TimeUnit.SECONDS);
			successCount++;
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof AvailabilityConflictException
					|| ex.getCause() instanceof IllegalArgumentException) {
				failureCount++;
			}
			else {
				throw ex;
			}
		}

		assertThat(successCount).isEqualTo(1);
		assertThat(failureCount).isEqualTo(1);

		List<Appointment> booked = this.appointmentRepository.findOverlappingByVet(1, BookingState.CONFIRMED, startAt,
				endAt);
		assertThat(booked).hasSize(1);
	}

	@Test
	void concurrentDirectBookingForSamePet_onlyOneSucceeds() throws Exception {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		LocalDate monday = LocalDate.of(2026, 9, 7);
		Instant startAt = ZonedDateTime.of(monday.atTime(11, 0), zoneId).toInstant();
		Instant endAt = ZonedDateTime.of(monday.atTime(11, 30), zoneId).toInstant();

		// Same pet (owner 1, pet 1), but two different vets (vet 1 and vet 2)
		DirectBookingRequest req1 = new DirectBookingRequest(1, 1, 1, startAt, endAt, true, "PHONE", "Booking 1", 1L,
				null);
		DirectBookingRequest req2 = new DirectBookingRequest(1, 1, 2, startAt, endAt, true, "PHONE", "Booking 2", 1L,
				null);

		CountDownLatch startLatch = new CountDownLatch(1);

		Callable<Appointment> task1 = () -> {
			startLatch.await();
			return this.directBookingService.bookDirectly(req1);
		};

		Callable<Appointment> task2 = () -> {
			startLatch.await();
			return this.directBookingService.bookDirectly(req2);
		};

		Future<Appointment> f1 = this.executorService.submit(task1);
		Future<Appointment> f2 = this.executorService.submit(task2);

		startLatch.countDown();

		int successCount = 0;
		int failureCount = 0;

		try {
			f1.get(5, TimeUnit.SECONDS);
			successCount++;
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof AvailabilityConflictException
					|| ex.getCause() instanceof IllegalArgumentException) {
				failureCount++;
			}
			else {
				throw ex;
			}
		}

		try {
			f2.get(5, TimeUnit.SECONDS);
			successCount++;
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof AvailabilityConflictException
					|| ex.getCause() instanceof IllegalArgumentException) {
				failureCount++;
			}
			else {
				throw ex;
			}
		}

		assertThat(successCount).isEqualTo(1);
		assertThat(failureCount).isEqualTo(1);

		List<Appointment> booked = this.appointmentRepository.findOverlappingByPet(1, BookingState.CONFIRMED, startAt,
				endAt);
		assertThat(booked).hasSize(1);
	}

}
