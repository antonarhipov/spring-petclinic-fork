package org.springframework.samples.petclinic.availability;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:availability-convergence;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE" })
class AvailabilityMutationConvergenceTests {

	@Autowired
	private AvailabilityAdministrationService administrationService;

	@Autowired
	private CalendarMutationCoordinator calendarCoordinator;

	@Autowired
	private ClinicClosureRepository closureRepository;

	@Autowired
	private VeterinarianLeaveRepository leaveRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private WorkflowRevisionRepository workflowRevisionRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private ProtectedPayloadService payloadService;

	@Autowired
	private Clock clock;

	@Test
	void clinicClosureListsAppointmentAndHoldRecoveryPathsAndRollsBackEverything() {
		LocalDate date = LocalDate.of(2026, 11, 2);
		Instant startAt = at(date, LocalTime.of(10, 0));
		Instant endAt = at(date, LocalTime.of(10, 30));
		Appointment appointment = this.appointmentRepository
			.saveAndFlush(new Appointment(1, 1, 1, startAt, endAt, "Europe/Amsterdam"));
		Offer offer = createActiveOffer(startAt, endAt);
		long revisionBefore = this.calendarCoordinator.getCurrentRevision();

		AvailabilityConflictException conflict = catchThrowableOfType(AvailabilityConflictException.class,
				() -> this.administrationService.createClinicClosure(date, date, "Staff training", null, 1L));

		assertThat(conflict).isNotNull();
		assertThat(conflict.getBlockingItems()).extracting(AvailabilityConflictException.AvailabilityBlocker::type)
			.containsExactly("ACTIVE_HOLD", "APPOINTMENT");
		assertThat(conflict.getBlockingItems()).anySatisfy(blocker -> {
			assertThat(blocker.id()).isEqualTo(appointment.getId());
			assertThat(blocker.recoveryPath()).isEqualTo("/staff/appointments/" + appointment.getId());
		}).anySatisfy(blocker -> {
			assertThat(blocker.id()).isEqualTo(offer.getId());
			assertThat(blocker.recoveryPath()).isEqualTo("/staff/queue?requestId=" + offer.getRequest().getId());
		});
		assertThat(this.closureRepository.findActiveClosuresOnDate(date)).isEmpty();
		assertThat(this.calendarCoordinator.getCurrentRevision()).isEqualTo(revisionBefore);
		assertThat(this.appointmentRepository.findById(appointment.getId())).isPresent();
		assertThat(this.offerRepository.findById(offer.getId())).isPresent();
	}

	@Test
	void veterinarianLeaveWithAppointmentSavesNothingOnConflict() {
		LocalDate date = LocalDate.of(2026, 11, 9);
		Instant startAt = at(date, LocalTime.of(11, 0));
		Instant endAt = at(date, LocalTime.of(11, 30));
		Appointment appointment = this.appointmentRepository
			.saveAndFlush(new Appointment(2, 2, 1, startAt, endAt, "Europe/Amsterdam"));
		long revisionBefore = this.calendarCoordinator.getCurrentRevision();

		AvailabilityConflictException conflict = catchThrowableOfType(AvailabilityConflictException.class,
				() -> this.administrationService.createVeterinarianLeave(1, date, date, "TRAINING", null, 1L));

		assertThat(conflict).isNotNull();
		assertThat(conflict.getBlockingItems()).singleElement().satisfies(blocker -> {
			assertThat(blocker.type()).isEqualTo("APPOINTMENT");
			assertThat(blocker.id()).isEqualTo(appointment.getId());
		});
		assertThat(this.leaveRepository.findActiveLeaveForVetOnDate(1, date)).isEmpty();
		assertThat(this.calendarCoordinator.getCurrentRevision()).isEqualTo(revisionBefore);
	}

	private Offer createActiveOffer(Instant startAt, Instant endAt) {
		Instant now = this.clock.instant();
		SchedulingRequest request = this.requestRepository
			.saveAndFlush(new SchedulingRequest(3, 3, RequestState.OFFERED, now));
		ProtectedPayload reasonPayload = this.payloadService.encrypt("Routine appointment");
		WorkflowRevision workflowRevision = this.workflowRevisionRepository.saveAndFlush(new WorkflowRevision(request,
				1, null, null, WorkflowRevisionState.CONFIRMED, reasonPayload, 30, 1, null, Urgency.ROUTINE, 0));
		request.setCurrentWorkflowRevision(workflowRevision);
		this.requestRepository.saveAndFlush(request);
		return this.offerRepository
			.saveAndFlush(new Offer(request, workflowRevision, 3, 3, 1, OfferOrigin.AUTOMATIC, startAt, endAt,
					"Europe/Amsterdam", now.plusSeconds(3600), OfferState.HELD, 1, 1L, "Held for convergence test"));
	}

	private Instant at(LocalDate date, LocalTime time) {
		return ZonedDateTime.of(date, time, ZoneId.of("Europe/Amsterdam")).toInstant();
	}

}
