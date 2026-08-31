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
import org.springframework.samples.petclinic.audit.ProtectedPayloadRepository;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
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
class LegacyVisitReconciliationServiceTests {

	@Autowired
	private LegacyVisitReconciliationService reconciliationService;

	@Autowired
	private VisitRepository visitRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeEventRepository changeEventRepository;

	@Autowired
	private ProtectedPayloadRepository payloadRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private OwnerHistoryRepository ownerHistoryRepository;

	@Autowired
	private Clock clock;

	private Visit futureLegacyVisit;

	private final Integer ownerId = 1;

	private final Integer petId = 1;

	private final Integer vetId = 1;

	private final Long staffAccountId = 2L;

	@BeforeEach
	void setUp() {
		LocalDate today = LocalDate.now(this.clock);
		LocalDate futureDate = today.plusDays(7);

		this.futureLegacyVisit = new Visit();
		this.futureLegacyVisit.setPetId(this.petId);
		this.futureLegacyVisit.setDate(futureDate);
		this.futureLegacyVisit.setDescription("Legacy scheduled checkup");
		this.futureLegacyVisit.setProvenance(VisitProvenance.LEGACY);
		this.futureLegacyVisit = this.visitRepository.save(this.futureLegacyVisit);
	}

	@Test
	void findUnreconciledFutureLegacyVisitsReturnsOnlyUnreconciledFutureVisits() {
		// Past legacy visit
		Visit pastVisit = new Visit();
		pastVisit.setPetId(this.petId);
		pastVisit.setDate(LocalDate.now(this.clock).minusDays(5));
		pastVisit.setDescription("Past checkup");
		pastVisit.setProvenance(VisitProvenance.LEGACY);
		this.visitRepository.save(pastVisit);

		List<LegacyVisitReconciliationService.LegacyVisitSummaryDto> results = this.reconciliationService
			.findUnreconciledFutureLegacyVisits();

		assertThat(results).isNotEmpty();
		assertThat(results).extracting(LegacyVisitReconciliationService.LegacyVisitSummaryDto::visitId)
			.contains(this.futureLegacyVisit.getId())
			.doesNotContain(pastVisit.getId());
	}

	@Test
	void reconcilingLegacyVisitCreatesConfirmedAppointmentAndLinksVisit() {
		Instant now = this.clock.instant();
		Instant startAt = now.plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
		Instant endAt = startAt.plus(30, ChronoUnit.MINUTES);

		LegacyVisitReconciliationService.ReconcileLegacyVisitCommand cmd = new LegacyVisitReconciliationService.ReconcileLegacyVisitCommand(
				this.futureLegacyVisit.getId(), this.staffAccountId, this.vetId, startAt, endAt, true, "PHONE",
				"Spoke with owner and scheduled routine visit");

		Appointment appointment = this.reconciliationService.reconcileLegacyVisit(cmd);

		assertThat(appointment).isNotNull();
		assertThat(appointment.getId()).isNotNull();
		assertThat(appointment.getBookingState()).isEqualTo(BookingState.CONFIRMED);
		assertThat(appointment.getOutcomeState()).isEqualTo(OutcomeState.PENDING);
		assertThat(appointment.getOwnerId()).isEqualTo(this.ownerId);
		assertThat(appointment.getPetId()).isEqualTo(this.petId);
		assertThat(appointment.getVetId()).isEqualTo(this.vetId);
		assertThat(appointment.getLegacyVisitId()).isEqualTo(this.futureLegacyVisit.getId().longValue());

		// Verify visit is updated
		Visit updatedVisit = this.visitRepository.findById(this.futureLegacyVisit.getId()).orElseThrow();
		assertThat(updatedVisit.getAppointmentId()).isEqualTo(appointment.getId());
		assertThat(updatedVisit.getVetId()).isEqualTo(this.vetId);

		// Verify change event
		List<AppointmentChangeEvent> changeEvents = this.changeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(appointment.getId());
		assertThat(changeEvents).hasSize(1);
		assertThat(changeEvents.get(0).getEventType()).isEqualTo("LEGACY_RECONCILED");

		// Future query should no longer include this reconciled visit
		List<LegacyVisitReconciliationService.LegacyVisitSummaryDto> remaining = this.reconciliationService
			.findUnreconciledFutureLegacyVisits();
		assertThat(remaining).extracting(LegacyVisitReconciliationService.LegacyVisitSummaryDto::visitId)
			.doesNotContain(this.futureLegacyVisit.getId());
	}

	@Test
	void reconcilingWithoutOwnerAgreementFails() {
		Instant now = this.clock.instant();
		Instant startAt = now.plus(7, ChronoUnit.DAYS);
		Instant endAt = startAt.plus(30, ChronoUnit.MINUTES);

		LegacyVisitReconciliationService.ReconcileLegacyVisitCommand cmd = new LegacyVisitReconciliationService.ReconcileLegacyVisitCommand(
				this.futureLegacyVisit.getId(), this.staffAccountId, this.vetId, startAt, endAt, false, "PHONE",
				"Reason");

		assertThatThrownBy(() -> this.reconciliationService.reconcileLegacyVisit(cmd))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Owner agreement is mandatory");
	}

	@Test
	void reconcilingWithConflictingSlotFails() {
		Instant now = this.clock.instant();
		Instant startAt = now.plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
		Instant endAt = startAt.plus(30, ChronoUnit.MINUTES);

		// Existing conflicting appointment for same vet
		Appointment existing = new Appointment(2, 2, this.vetId, startAt, endAt, "America/New_York");
		existing.setBookingState(BookingState.CONFIRMED);
		existing.setOutcomeState(OutcomeState.PENDING);
		this.appointmentRepository.save(existing);

		LegacyVisitReconciliationService.ReconcileLegacyVisitCommand cmd = new LegacyVisitReconciliationService.ReconcileLegacyVisitCommand(
				this.futureLegacyVisit.getId(), this.staffAccountId, this.vetId, startAt, endAt, true, "PHONE",
				"Reason");

		assertThatThrownBy(() -> this.reconciliationService.reconcileLegacyVisit(cmd))
			.isInstanceOf(AvailabilityConflictException.class)
			.hasMessageContaining("capacity conflict");
	}

}
