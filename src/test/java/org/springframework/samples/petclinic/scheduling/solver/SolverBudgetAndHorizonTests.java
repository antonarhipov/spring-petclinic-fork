/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.samples.petclinic.scheduling.TestClockConfig.PINNED_DATE_TIME;

@SpringBootTest
@Import(TestClockConfig.class)
@Transactional
class SolverBudgetAndHorizonTests {

	@Autowired
	private Environment environment;

	@Autowired
	private ClinicConfigRepository configRepository;

	@Autowired
	private SlotRanker slotRanker;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private Clock clock;

	@Test
	@Tag("AC-83")
	void gridAndSpentLimitAreExact_AC83() {
		assertThat(this.configRepository.findById(1).orElseThrow().getGridIntervalMinutes()).isEqualTo(15);
		assertThat(this.environment.getProperty("timefold.solver.termination.spent-limit")).isEqualTo("1s");
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		SchedulingRequest request = request(owner, owner.getPet(1));

		List<SlotRanker.RankedSlot> ranked = this.slotRanker.rankSlots(request, null);

		assertThat(ranked).isNotEmpty().allSatisfy(slot -> {
			assertThat(slot.startTime().getMinute() % 15).isZero();
			assertThat(slot.startTime().getSecond()).isZero();
			assertThat(slot.startTime().getNano()).isZero();
		});
	}

	@Test
	@Tag("AC-83")
	void solverRunsSynchronouslyInsideLockedConfirmTransaction_AC83() {
		Vet selectedVet = this.vetRepository.findById(1).orElseThrow();
		AtomicReference<Thread> invocationThread = new AtomicReference<>();
		AtomicBoolean transactionActive = new AtomicBoolean();
		AtomicBoolean vetLockHeld = new AtomicBoolean();
		SlotRanker recordingDouble = (request, interpretation) -> {
			invocationThread.set(Thread.currentThread());
			transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
			Vet managedVet = this.entityManager.find(Vet.class, selectedVet.getId());
			vetLockHeld.set(this.entityManager.getLockMode(managedVet) == LockModeType.PESSIMISTIC_WRITE);
			return List.of(new SlotRanker.RankedSlot(selectedVet, PINNED_DATE_TIME.plusDays(7), 30, "test", "test"));
		};
		SchedulingRequestRepository requests = mock(SchedulingRequestRepository.class);
		RequestLifecycleService lifecycle = mock(RequestLifecycleService.class);
		InterpretationRepository interpretations = mock(InterpretationRepository.class);
		AppointmentLifecycleService appointments = mock(AppointmentLifecycleService.class);
		AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
		when(interpretations.findTopByRequestIdOrderByVersionDesc(any())).thenReturn(Optional.empty());
		when(appointmentRepository.findConfirmedByVetIdAndDateRange(anyInt(), any(), any())).thenReturn(List.of());
		when(requests.findActiveHoldsByVetId(anyInt())).thenReturn(List.of());
		SuggestionService service = new SuggestionService(requests, lifecycle, interpretations, recordingDouble,
				appointments, appointmentRepository, this.vetRepository, this.clock);
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		SchedulingRequest request = request(owner, owner.getPet(1));

		Thread callingThread = Thread.currentThread();
		service.confirm(request, "owner");

		assertThat(invocationThread.get()).isSameAs(callingThread);
		assertThat(transactionActive).isTrue();
		assertThat(vetLockHeld).isTrue();
	}

	@Test
	@Tag("AC-104")
	void interiorHorizonAllowed_AC104() {
		assertThat(ownerCandidateAllowed(PINNED_DATE_TIME.plusDays(8))).isTrue();
	}

	@Test
	@Tag("AC-105")
	void lastHorizonDayAllowed_AC105() {
		assertThat(ownerCandidateAllowed(PINNED_DATE_TIME.plusDays(30).withHour(10))).isTrue();
	}

	@Test
	@Tag("AC-106")
	void beyondHorizonOwnerExcludedStaffAllowed_AC106() {
		ZonedDateTime beyond = PINNED_DATE_TIME.plusDays(31).withHour(10);
		assertThat(ownerCandidateAllowed(beyond)).isFalse();
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Vet vet = this.vetRepository.findById(4).orElseThrow();

		Appointment staffBooked = this.appointmentLifecycleService.bookAppointment(owner.getPet(1), vet, beyond, 75,
				"staff direct booking", null, "staff");

		assertThat(staffBooked.getStartTime()).isEqualTo(beyond);
		assertThat(staffBooked.getDuration()).isEqualTo(75);
	}

	private boolean ownerCandidateAllowed(ZonedDateTime candidate) {
		Vet vet = vet(99);
		ClinicOpeningHour opening = opening(candidate.getDayOfWeek());
		VetWeeklyBlock block = block(vet, candidate.getDayOfWeek());
		ZonedDateTime horizonEnd = PINNED_DATE_TIME.toLocalDate()
			.plusDays(30)
			.atTime(LocalTime.MAX)
			.atZone(PINNED_DATE_TIME.getZone());
		return DefaultSlotRanker.isCandidateFeasible(vet, candidate, 30, List.of(opening), List.of(block), List.of(),
				List.of(), null, List.of(), PINNED_DATE_TIME, horizonEnd, null, null);
	}

	private static SchedulingRequest request(Owner owner, Pet pet) {
		SchedulingRequest request = new SchedulingRequest();
		request.setId(999);
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.INTERPRETED);
		return request;
	}

	private static Vet vet(int id) {
		Vet vet = new Vet();
		vet.setId(id);
		vet.setFirstName("Boundary");
		vet.setLastName("Vet");
		return vet;
	}

	private static ClinicOpeningHour opening(DayOfWeek day) {
		ClinicOpeningHour opening = new ClinicOpeningHour();
		ReflectionTestUtils.setField(opening, "dayOfWeek", day);
		ReflectionTestUtils.setField(opening, "openTime", LocalTime.of(9, 0));
		ReflectionTestUtils.setField(opening, "closeTime", LocalTime.of(17, 0));
		return opening;
	}

	private static VetWeeklyBlock block(Vet vet, DayOfWeek day) {
		VetWeeklyBlock block = new VetWeeklyBlock();
		ReflectionTestUtils.setField(block, "vet", vet);
		ReflectionTestUtils.setField(block, "dayOfWeek", day);
		ReflectionTestUtils.setField(block, "startTime", LocalTime.of(9, 0));
		ReflectionTestUtils.setField(block, "endTime", LocalTime.of(17, 0));
		return block;
	}

}
