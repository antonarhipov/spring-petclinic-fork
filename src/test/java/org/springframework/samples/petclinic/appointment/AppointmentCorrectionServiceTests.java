package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

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
class AppointmentCorrectionServiceTests {

	@Autowired
	private AppointmentCorrectionService correctionService;

	@Autowired
	private AppointmentOutcomeService outcomeService;

	@Autowired
	private AppointmentRepository appointmentRepository;

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

	private Appointment appointment;

	private final Integer ownerId = 1;

	private final Integer petId = 1;

	private final Integer vetId = 1;

	private final Long staffAccountId = 2L;

	@BeforeEach
	void setUp() {
		Instant now = this.clock.instant();
		Instant startAt = now.minus(3, ChronoUnit.HOURS);
		Instant endAt = startAt.plus(30, ChronoUnit.MINUTES);

		this.appointment = new Appointment(this.ownerId, this.petId, this.vetId, startAt, endAt, "America/New_York");
		this.appointment.setBookingState(BookingState.CONFIRMED);
		this.appointment.setOutcomeState(OutcomeState.PENDING);
		this.appointment = this.appointmentRepository.save(this.appointment);
	}

	@Test
	void correctingCompletedToNoShowUnlinksVisitAndAppendsCorrectionEvent() {
		// First complete
		this.outcomeService.completeAppointment(new AppointmentOutcomeService.CompleteAppointmentCommand(
				this.appointment.getId(), this.staffAccountId, "Routine exam", "Healthy pet"));

		Visit visitBefore = this.visitRepository.findByAppointmentId(this.appointment.getId()).orElseThrow();
		assertThat(visitBefore.getProvenance()).isEqualTo(VisitProvenance.APPOINTMENT_COMPLETION);

		// Correct to NO_SHOW
		String correctionReason = "Accidentally marked as completed instead of no-show";
		AppointmentCorrectionService.CorrectOutcomeCommand cmd = new AppointmentCorrectionService.CorrectOutcomeCommand(
				this.appointment.getId(), this.staffAccountId, OutcomeState.NO_SHOW, correctionReason, null, null);

		AppointmentOutcomeEvent correctionEvent = this.correctionService.correctOutcome(cmd);

		assertThat(correctionEvent).isNotNull();
		assertThat(correctionEvent.getEventType()).isEqualTo(AppointmentOutcomeEventType.CORRECTION);
		assertThat(correctionEvent.getPreviousOutcome()).isEqualTo(OutcomeState.COMPLETED);
		assertThat(correctionEvent.getNewOutcome()).isEqualTo(OutcomeState.NO_SHOW);
		assertThat(correctionEvent.getResultingVisitId()).isNull();

		// Verify appointment state updated
		Appointment updated = this.appointmentRepository.findById(this.appointment.getId()).orElseThrow();
		assertThat(updated.getOutcomeState()).isEqualTo(OutcomeState.NO_SHOW);

		// Verify visit is unlinked from appointment (non-destructive)
		assertThat(this.visitRepository.findByAppointmentId(this.appointment.getId())).isEmpty();
		Visit unlinkedVisit = this.visitRepository.findById(visitBefore.getId()).orElseThrow();
		assertThat(unlinkedVisit.getAppointmentId()).isNull();

		// Verify events history has 2 entries (COMPLETED, then CORRECTION)
		List<AppointmentOutcomeEvent> events = this.outcomeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(this.appointment.getId());
		assertThat(events).hasSize(2);
		assertThat(events.get(0).getEventType()).isEqualTo(AppointmentOutcomeEventType.COMPLETED);
		assertThat(events.get(1).getEventType()).isEqualTo(AppointmentOutcomeEventType.CORRECTION);
	}

	@Test
	void correctingNoShowToCompletedCreatesVisitAndAppendsCorrectionEvent() {
		// First mark NO_SHOW
		this.outcomeService.recordNoShow(new AppointmentOutcomeService.NoShowCommand(this.appointment.getId(),
				this.staffAccountId, "Client was late"));

		// Correct to COMPLETED
		String correctionReason = "Owner actually arrived 10 min late and examination was conducted";
		String notes = "Administered medicine and checked ears";
		String summary = "Late arrival exam completed";

		AppointmentCorrectionService.CorrectOutcomeCommand cmd = new AppointmentCorrectionService.CorrectOutcomeCommand(
				this.appointment.getId(), this.staffAccountId, OutcomeState.COMPLETED, correctionReason, notes,
				summary);

		AppointmentOutcomeEvent correctionEvent = this.correctionService.correctOutcome(cmd);

		assertThat(correctionEvent).isNotNull();
		assertThat(correctionEvent.getPreviousOutcome()).isEqualTo(OutcomeState.NO_SHOW);
		assertThat(correctionEvent.getNewOutcome()).isEqualTo(OutcomeState.COMPLETED);
		assertThat(correctionEvent.getResultingVisitId()).isNotNull();

		// Verify visit created
		Optional<Visit> createdVisit = this.visitRepository.findByAppointmentId(this.appointment.getId());
		assertThat(createdVisit).isPresent();
		assertThat(createdVisit.get().getDescription()).isEqualTo(summary);
		assertThat(createdVisit.get().getProvenance()).isEqualTo(VisitProvenance.APPOINTMENT_COMPLETION);

		Appointment updated = this.appointmentRepository.findById(this.appointment.getId()).orElseThrow();
		assertThat(updated.getOutcomeState()).isEqualTo(OutcomeState.COMPLETED);
	}

	@Test
	void updatingClinicalNotesOnCompletedAppointmentUpdatesVisitAndAppendsCorrectionEvent() {
		// First complete
		this.outcomeService.completeAppointment(new AppointmentOutcomeService.CompleteAppointmentCommand(
				this.appointment.getId(), this.staffAccountId, "Initial notes", "Summary 1"));

		// Correct with updated notes
		String updatedNotes = "Initial notes + lab tests were all clear";
		String updatedSummary = "Summary 1 (Labs Clear)";
		AppointmentCorrectionService.CorrectOutcomeCommand cmd = new AppointmentCorrectionService.CorrectOutcomeCommand(
				this.appointment.getId(), this.staffAccountId, OutcomeState.COMPLETED, "Added lab results",
				updatedNotes, updatedSummary);

		AppointmentOutcomeEvent correctionEvent = this.correctionService.correctOutcome(cmd);

		assertThat(correctionEvent).isNotNull();
		assertThat(correctionEvent.getPreviousOutcome()).isEqualTo(OutcomeState.COMPLETED);
		assertThat(correctionEvent.getNewOutcome()).isEqualTo(OutcomeState.COMPLETED);

		Visit updatedVisit = this.visitRepository.findByAppointmentId(this.appointment.getId()).orElseThrow();
		assertThat(updatedVisit.getDescription()).isEqualTo(updatedSummary);
		String decryptedNotes = this.payloadService.decryptToString(
				this.payloadRepository.findById(updatedVisit.getProtectedClinicalPayloadId()).orElseThrow());
		assertThat(decryptedNotes).isEqualTo(updatedNotes);
	}

	@Test
	void correctingPendingAppointmentFails() {
		AppointmentCorrectionService.CorrectOutcomeCommand cmd = new AppointmentCorrectionService.CorrectOutcomeCommand(
				this.appointment.getId(), this.staffAccountId, OutcomeState.COMPLETED, "Reason", "Notes", "Summary");

		assertThatThrownBy(() -> this.correctionService.correctOutcome(cmd)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot correct outcome for appointment in state: PENDING");
	}

	@Test
	void blankReasonThrowsIllegalArgumentException() {
		this.outcomeService.completeAppointment(new AppointmentOutcomeService.CompleteAppointmentCommand(
				this.appointment.getId(), this.staffAccountId, "Notes", "Summary"));

		AppointmentCorrectionService.CorrectOutcomeCommand cmd = new AppointmentCorrectionService.CorrectOutcomeCommand(
				this.appointment.getId(), this.staffAccountId, OutcomeState.NO_SHOW, "  ", null, null);

		assertThatThrownBy(() -> this.correctionService.correctOutcome(cmd))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Correction reason must not be blank");
	}

}
