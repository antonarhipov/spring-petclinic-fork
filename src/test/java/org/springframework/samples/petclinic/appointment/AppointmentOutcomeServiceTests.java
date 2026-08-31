package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.audit.AuditEventRepository;
import org.springframework.samples.petclinic.audit.OwnerHistoryRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitProvenance;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppointmentOutcomeServiceTests {

	@Autowired
	private AppointmentOutcomeService outcomeService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeEventRepository changeEventRepository;

	@Autowired
	private AppointmentOutcomeEventRepository outcomeEventRepository;

	@Autowired
	private VisitRepository visitRepository;

	@Autowired
	private ProtectedPayloadService payloadService;

	@Autowired
	private ProtectedPayloadRepository payloadRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private OwnerHistoryRepository ownerHistoryRepository;

	@Autowired
	private Clock clock;

	private Appointment pastAppointment;

	private Appointment futureAppointment;

	private final Integer ownerId = 1;

	private final Integer petId = 1;

	private final Integer vetId = 1;

	private final Long staffAccountId = 2L;

	@BeforeEach
	void setUp() {
		Instant now = this.clock.instant();
		Instant pastStart = now.minus(2, ChronoUnit.HOURS);
		Instant pastEnd = pastStart.plus(30, ChronoUnit.MINUTES);

		this.pastAppointment = new Appointment(this.ownerId, this.petId, this.vetId, pastStart, pastEnd,
				"America/New_York");
		this.pastAppointment.setBookingState(BookingState.CONFIRMED);
		this.pastAppointment.setOutcomeState(OutcomeState.PENDING);
		this.pastAppointment = this.appointmentRepository.save(this.pastAppointment);

		Instant futureStart = now.plus(2, ChronoUnit.DAYS);
		Instant futureEnd = futureStart.plus(30, ChronoUnit.MINUTES);

		this.futureAppointment = new Appointment(this.ownerId, this.petId, this.vetId, futureStart, futureEnd,
				"America/New_York");
		this.futureAppointment.setBookingState(BookingState.CONFIRMED);
		this.futureAppointment.setOutcomeState(OutcomeState.PENDING);
		this.futureAppointment = this.appointmentRepository.save(this.futureAppointment);
	}

	@Test
	void completingPastAppointmentCreatesVisitAndOutcomeEvents() {
		String clinicalNotes = "Administered annual rabies vaccine, pet is healthy.";
		String summary = "Annual vaccination completed";

		AppointmentOutcomeService.CompleteAppointmentCommand cmd = new AppointmentOutcomeService.CompleteAppointmentCommand(
				this.pastAppointment.getId(), this.staffAccountId, clinicalNotes, summary);

		Visit visit = this.outcomeService.completeAppointment(cmd);

		assertThat(visit).isNotNull();
		assertThat(visit.getId()).isNotNull();
		assertThat(visit.getPetId()).isEqualTo(this.petId);
		assertThat(visit.getVetId()).isEqualTo(this.vetId);
		assertThat(visit.getAppointmentId()).isEqualTo(this.pastAppointment.getId());
		assertThat(visit.getProvenance()).isEqualTo(VisitProvenance.APPOINTMENT_COMPLETION);
		assertThat(visit.getDescription()).isEqualTo(summary);
		assertThat(visit.getProtectedClinicalPayloadId()).isNotNull();

		// Verify decrypted clinical notes
		ProtectedPayload clinicalPayload = this.payloadRepository.findById(visit.getProtectedClinicalPayloadId())
			.orElseThrow();
		String decryptedNotes = this.payloadService.decryptToString(clinicalPayload);
		assertThat(decryptedNotes).isEqualTo(clinicalNotes);

		// Verify appointment state updated
		Appointment updated = this.appointmentRepository.findById(this.pastAppointment.getId()).orElseThrow();
		assertThat(updated.getOutcomeState()).isEqualTo(OutcomeState.COMPLETED);

		// Verify outcome event
		List<AppointmentOutcomeEvent> outcomeEvents = this.outcomeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(this.pastAppointment.getId());
		assertThat(outcomeEvents).hasSize(1);
		AppointmentOutcomeEvent event = outcomeEvents.get(0);
		assertThat(event.getEventType()).isEqualTo(AppointmentOutcomeEventType.COMPLETED);
		assertThat(event.getActorAccountId()).isEqualTo(this.staffAccountId);
		assertThat(event.getPreviousOutcome()).isEqualTo(OutcomeState.PENDING);
		assertThat(event.getNewOutcome()).isEqualTo(OutcomeState.COMPLETED);
		assertThat(event.getResultingVisitId()).isEqualTo(visit.getId());

		// Verify change event
		List<AppointmentChangeEvent> changeEvents = this.changeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(this.pastAppointment.getId());
		assertThat(changeEvents).hasSize(1);
		assertThat(changeEvents.get(0).getEventType()).isEqualTo("APPOINTMENT_COMPLETED");
	}

	@Test
	void completingFutureAppointmentFails() {
		AppointmentOutcomeService.CompleteAppointmentCommand cmd = new AppointmentOutcomeService.CompleteAppointmentCommand(
				this.futureAppointment.getId(), this.staffAccountId, "Notes", "Summary");

		assertThatThrownBy(() -> this.outcomeService.completeAppointment(cmd)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cannot be completed");
	}

	@Test
	void recordingNoShowSetsOutcomeStateAndCreatesOutcomeEventWithoutVisit() {
		String reason = "Owner called stating they could not make it due to transport issues.";
		AppointmentOutcomeService.NoShowCommand cmd = new AppointmentOutcomeService.NoShowCommand(
				this.pastAppointment.getId(), this.staffAccountId, reason);

		AppointmentOutcomeEvent outcomeEvent = this.outcomeService.recordNoShow(cmd);

		assertThat(outcomeEvent).isNotNull();
		assertThat(outcomeEvent.getEventType()).isEqualTo(AppointmentOutcomeEventType.NO_SHOW);
		assertThat(outcomeEvent.getNewOutcome()).isEqualTo(OutcomeState.NO_SHOW);
		assertThat(outcomeEvent.getResultingVisitId()).isNull();
		assertThat(outcomeEvent.getReasonPayloadId()).isNotNull();

		// Verify appointment state
		Appointment updated = this.appointmentRepository.findById(this.pastAppointment.getId()).orElseThrow();
		assertThat(updated.getOutcomeState()).isEqualTo(OutcomeState.NO_SHOW);

		// Verify no visit was created for this appointment
		assertThat(this.visitRepository.findByAppointmentId(this.pastAppointment.getId())).isEmpty();

		// Verify decrypted reason
		ProtectedPayload reasonPayload = this.payloadRepository.findById(outcomeEvent.getReasonPayloadId())
			.orElseThrow();
		String decryptedReason = this.payloadService.decryptToString(reasonPayload);
		assertThat(decryptedReason).isEqualTo(reason);
	}

	@Test
	void recordingNoShowForFutureAppointmentFails() {
		AppointmentOutcomeService.NoShowCommand cmd = new AppointmentOutcomeService.NoShowCommand(
				this.futureAppointment.getId(), this.staffAccountId, "Reason");

		assertThatThrownBy(() -> this.outcomeService.recordNoShow(cmd)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot record no-show");
	}

}
