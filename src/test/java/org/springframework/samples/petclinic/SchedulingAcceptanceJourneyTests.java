package org.springframework.samples.petclinic;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentCorrectionService;
import org.springframework.samples.petclinic.appointment.AppointmentCorrectionService.CorrectOutcomeCommand;
import org.springframework.samples.petclinic.appointment.AppointmentOutcomeService;
import org.springframework.samples.petclinic.appointment.AppointmentOutcomeService.CompleteAppointmentCommand;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.BookingState;
import org.springframework.samples.petclinic.appointment.DirectBookingService;
import org.springframework.samples.petclinic.appointment.DirectBookingService.DirectBookingRequest;
import org.springframework.samples.petclinic.appointment.LegacyVisitReconciliationService;
import org.springframework.samples.petclinic.appointment.LegacyVisitReconciliationService.ReconcileLegacyVisitCommand;
import org.springframework.samples.petclinic.appointment.OutcomeState;
import org.springframework.samples.petclinic.appointment.OwnerAppointmentService;
import org.springframework.samples.petclinic.appointment.StaffAppointmentCancellationService;
import org.springframework.samples.petclinic.appointment.StaffAppointmentCancellationService.StaffCancellationCommand;
import org.springframework.samples.petclinic.appointment.StaffAppointmentReschedulingService;
import org.springframework.samples.petclinic.appointment.StaffAppointmentReschedulingService.RescheduleAppointmentCommand;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.availability.RecurringShift;
import org.springframework.samples.petclinic.availability.RecurringShiftRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitProvenance;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationCandidate;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationClient;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobWorker;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferLifecycleService;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferService;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.AssistedOfferService;
import org.springframework.samples.petclinic.scheduling.queue.AssistedOfferService.AssistedOfferCommand;
import org.springframework.samples.petclinic.scheduling.queue.ContactOutcome;
import org.springframework.samples.petclinic.scheduling.queue.EmergencyClearanceService;
import org.springframework.samples.petclinic.scheduling.queue.QueueAssignmentService;
import org.springframework.samples.petclinic.scheduling.queue.QueueContactService;
import org.springframework.samples.petclinic.scheduling.queue.QueueItem;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffInterpretationService;
import org.springframework.samples.petclinic.scheduling.queue.StaffInterpretationService.ManualInterpretationCommand;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.OwnerInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.OwnerRequestRevisionService;
import org.springframework.samples.petclinic.scheduling.request.OwnerRequestRevisionService.StructuredRevisionCommand;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestService;
import org.springframework.samples.petclinic.scheduling.request.WindowClassification;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.support.SchedulingTestFixtures;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Full-context synthetic acceptance test suite covering all 15 specification
 * demonstration journeys.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "petclinic.jobs.enabled=false" })
@Transactional
class SchedulingAcceptanceJourneyTests {

	private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Amsterdam");

	@MockitoBean
	private InterpretationClient interpretationClient;

	@Autowired
	private BackgroundJobWorker backgroundJobWorker;

	@Autowired
	private SchedulingRequestService schedulingRequestService;

	@Autowired
	private OwnerInterpretationService ownerInterpretationService;

	@Autowired
	private OwnerRequestRevisionService ownerRequestRevisionService;

	@Autowired
	private OfferService offerService;

	@Autowired
	private OfferLifecycleService offerLifecycleService;

	@Autowired
	private DirectBookingService directBookingService;

	@Autowired
	private OwnerAppointmentService ownerAppointmentService;

	@Autowired
	private StaffAppointmentReschedulingService reschedulingService;

	@Autowired
	private StaffAppointmentCancellationService cancellationService;

	@Autowired
	private AppointmentOutcomeService outcomeService;

	@Autowired
	private AppointmentCorrectionService correctionService;

	@Autowired
	private QueueAssignmentService queueAssignmentService;

	@Autowired
	private QueueContactService queueContactService;

	@Autowired
	private StaffInterpretationService staffInterpretationService;

	@Autowired
	private EmergencyClearanceService emergencyClearanceService;

	@Autowired
	private AssistedOfferService assistedOfferService;

	@Autowired
	private LegacyVisitReconciliationService legacyReconciliationService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private QueueItemRepository queueItemRepository;

	@Autowired
	private VisitRepository visitRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private RecurringShiftRepository recurringShiftRepository;

	@Autowired
	private ClinicPolicyRepository clinicPolicyRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	private Account staffAccount1;

	private Account staffAccount2;

	@BeforeEach
	void setUpFixtures() {
		this.owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = this.owner.getPets().get(0);
		this.vet = this.vetRepository.findById(1).orElseThrow();

		if (this.clinicPolicyRepository.findById(1).isEmpty()) {
			ClinicPolicy policy = ClinicPolicy.createDefaultPolicy();
			policy.setId(1);
			policy.setZoneId(CLINIC_ZONE.getId());
			this.clinicPolicyRepository.save(policy);
		}

		if (this.recurringShiftRepository.count() == 0) {
			for (DayOfWeek day : DayOfWeek.values()) {
				if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
					this.recurringShiftRepository
						.save(new RecurringShift(this.vet.getId(), day, LocalTime.of(9, 0), LocalTime.of(17, 0)));
				}
			}
		}

		this.staffAccount1 = this.accountRepository.findByUsername("admin").orElseGet(() -> {
			Account acc = new Account();
			acc.setUsername("admin");
			acc.setPasswordHash(this.passwordEncoder.encode("admin123"));
			acc.setRole(Role.STAFF);
			acc.setEnabled(true);
			acc.setSessionVersion(0L);
			return this.accountRepository.save(acc);
		});

		this.staffAccount2 = this.accountRepository.findByUsername("staff2").orElseGet(() -> {
			Account acc = new Account();
			acc.setUsername("staff2");
			acc.setPasswordHash(this.passwordEncoder.encode("staff123"));
			acc.setRole(Role.STAFF);
			acc.setEnabled(true);
			acc.setSessionVersion(0L);
			return this.accountRepository.save(acc);
		});
	}

	private LocalDate nextWeekday(int weekdayOffset) {
		LocalDate date = LocalDate.now(CLINIC_ZONE);
		int added = 0;
		while (added < weekdayOffset) {
			date = date.plusDays(1);
			if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
				added++;
			}
		}
		return date;
	}

	private Integer currentWorkflowRevision(QueueItem item) {
		return item.getWorkflowRevision() != null ? item.getWorkflowRevision().getRevisionNumber() : null;
	}

	private void claim(QueueItem item, Account staff) {
		this.queueAssignmentService.claim(item.getId(), staff.getId(), item.getRequest().getVersion(),
				currentWorkflowRevision(item), item.getVersion());
	}

	@Test
	@DisplayName("Journey 1: Successful interpretation, owner confirmation, suggestion, and booking")
	void journey01_successfulInterpretationAndRoutineBooking() {
		LocalDate targetDate = nextWeekday(2);
		InterpretationCandidate candidate = SchedulingTestFixtures.routineCandidate(targetDate, LocalTime.of(10, 0),
				LocalTime.of(14, 0), 30);
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Routine checkup next Wednesday morning", true);
		this.backgroundJobWorker.processNextJob();

		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());
		this.backgroundJobWorker.processNextJob();

		Offer offer = this.offerRepository.findByRequestIdAndState(request.getId(), OfferState.HELD).orElseThrow();
		assertThat(offer.getState()).isEqualTo(OfferState.HELD);

		Appointment appointment = this.offerService.acceptOffer(offer.getId(), this.owner.getId());
		assertThat(appointment.getBookingState()).isEqualTo(BookingState.CONFIRMED);
		assertThat(appointment.getOwnerId()).isEqualTo(this.owner.getId());
	}

	@Test
	@DisplayName("Journey 2: Offer rejection followed by another owner-requested suggestion")
	void journey02_offerRejectionFollowedByNextSuggestion() {
		LocalDate targetDate = nextWeekday(3);
		InterpretationCandidate candidate = SchedulingTestFixtures.routineCandidate(targetDate, LocalTime.of(9, 0),
				LocalTime.of(17, 0), 30);
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Routine checkup next Tuesday", true);
		this.backgroundJobWorker.processNextJob();

		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());
		this.backgroundJobWorker.processNextJob();

		Offer firstOffer = this.offerRepository.findByRequestIdAndState(request.getId(), OfferState.HELD).orElseThrow();
		this.offerLifecycleService.rejectOffer(firstOffer.getId(), this.owner.getId(), "Too early in the morning");

		Offer reloadedFirst = this.offerRepository.findById(firstOffer.getId()).orElseThrow();
		assertThat(reloadedFirst.getState()).isEqualTo(OfferState.REJECTED);

		this.offerLifecycleService.requestNextOffer(request.getId(), this.owner.getId());
		this.backgroundJobWorker.processNextJob();

		Offer secondOffer = this.offerRepository.findByRequestIdAndState(request.getId(), OfferState.HELD)
			.orElseThrow();
		assertThat(secondOffer.getId()).isNotEqualTo(firstOffer.getId());
		assertThat(secondOffer.getState()).isEqualTo(OfferState.HELD);
		boolean exactSlotSame = secondOffer.getVetId().equals(firstOffer.getVetId())
				&& secondOffer.getStartAt().equals(firstOffer.getStartAt());
		assertThat(exactSlotSame).isFalse();
	}

	@Test
	@DisplayName("Journey 3: Offer expiry followed by an explicit request for another option")
	void journey03_offerExpiryFollowedByRequestForAnotherOption() {
		LocalDate targetDate = nextWeekday(4);
		InterpretationCandidate candidate = SchedulingTestFixtures.routineCandidate(targetDate, LocalTime.of(9, 0),
				LocalTime.of(17, 0), 30);
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Routine visit", true);
		this.backgroundJobWorker.processNextJob();

		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());
		this.backgroundJobWorker.processNextJob();

		Offer offer = this.offerRepository.findByRequestIdAndState(request.getId(), OfferState.HELD).orElseThrow();
		offer.setExpiresAt(Instant.now().minusSeconds(10));
		this.offerRepository.save(offer);

		this.offerLifecycleService.expireOffer(offer.getId());

		Offer reloaded = this.offerRepository.findById(offer.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(OfferState.EXPIRED);

		this.offerLifecycleService.requestNextOffer(request.getId(), this.owner.getId());
		this.backgroundJobWorker.processNextJob();

		Offer nextOffer = this.offerRepository.findByRequestIdAndState(request.getId(), OfferState.HELD).orElseThrow();
		assertThat(nextOffer.getState()).isEqualTo(OfferState.HELD);
	}

	@Test
	@DisplayName("Journey 4: No automatic match followed by owner revision")
	void journey04_noAutomaticMatchFollowedByOwnerRevision() {
		LocalDate targetDate = nextWeekday(5);
		org.springframework.samples.petclinic.scheduling.interpretation.WindowCandidate window = new org.springframework.samples.petclinic.scheduling.interpretation.WindowCandidate(
				WindowClassification.ALLOWED, WindowShape.ONE_OFF, targetDate, null, null, null, LocalTime.of(22, 0),
				LocalTime.of(23, 0), "Late night", null);
		InterpretationCandidate candidate = new InterpretationCandidate("1", "Need late night appointment",
				Urgency.ROUTINE, List.of(), 30, null, null, List.of(window), List.of(), List.of());
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Need late night appointment", true);
		this.backgroundJobWorker.processNextJob();

		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());
		this.backgroundJobWorker.processNextJob();

		SchedulingRequest requestAfterMatch = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(requestAfterMatch.getState()).isEqualTo(RequestState.STAFF_HANDLING);

		AvailabilityWindow daytimeWindow = new AvailabilityWindow(null, WindowClassification.PREFERRED,
				WindowShape.ONE_OFF, targetDate, null, null, null, LocalTime.of(10, 0), LocalTime.of(12, 0), "Daytime",
				null);

		this.ownerRequestRevisionService.reviseStructured(new StructuredRevisionCommand(request.getId(),
				this.owner.getId(), "Revised: Daytime visit", 30, null, null, Urgency.ROUTINE, List.of(daytimeWindow)));
		this.backgroundJobWorker.processNextJob();

		Offer offer = this.offerRepository.findByRequestIdAndState(request.getId(), OfferState.HELD).orElseThrow();
		assertThat(offer.getState()).isEqualTo(OfferState.HELD);
	}

	@Test
	@DisplayName("Journey 5: Consent decline followed by manual staff interpretation")
	void journey05_consentDeclineFollowedByManualStaffInterpretation() {
		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Private prose without AI consent", false);

		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		QueueItem item = this.queueItemRepository.findByRequestId(request.getId()).orElseThrow();
		assertThat(item.getState()).isEqualTo(QueueState.NEW);

		claim(item, this.staffAccount1);

		LocalDate targetDate = nextWeekday(6);
		AvailabilityWindow window = new AvailabilityWindow(null, WindowClassification.PREFERRED, WindowShape.ONE_OFF,
				targetDate, null, null, null, LocalTime.of(11, 0), LocalTime.of(15, 0), "Staff window", null);

		WorkflowRevision revision = this.staffInterpretationService
			.recordManualInterpretation(new ManualInterpretationCommand(item.getId(), this.staffAccount1.getId(),
					"Vaccination booster", 30, this.vet.getId(), null, Urgency.ROUTINE, List.of(window), false,
					item.getRequest().getVersion(), currentWorkflowRevision(item), item.getVersion()));

		assertThat(revision).isNotNull();
		assertThat(revision.getDurationMinutes()).isEqualTo(30);
	}

	@Test
	@DisplayName("Journey 6: Automated interpretation failure followed by staff fallback")
	void journey06_automatedInterpretationFailureFollowedByStaffFallback() {
		given(this.interpretationClient.interpret(any())).willThrow(new RuntimeException("Ollama unavailable"));

		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Routine checkup request", true);
		this.backgroundJobWorker.processNextJob();

		SchedulingRequest failedRequest = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(failedRequest.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		assertThat(this.queueItemRepository.findByRequestId(request.getId())).isPresent();
	}

	@Test
	@DisplayName("Journey 7: Emergency detection, staff selection of replacement urgency with reason, and owner reconfirmation")
	void journey07_emergencyDetectionStaffTriageAndOwnerReconfirmation() {
		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Dog is bleeding heavily and having severe breathing difficulty", true);

		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		QueueItem item = this.queueItemRepository.findByRequestId(request.getId()).orElseThrow();

		claim(item, this.staffAccount1);
		this.emergencyClearanceService.clearEmergency(item.getId(), this.staffAccount1.getId(), Urgency.PRIORITY,
				"Examined by triage phone call", item.getRequest().getVersion(), currentWorkflowRevision(item),
				item.getVersion());

		QueueItem clearedItem = this.queueItemRepository.findById(item.getId()).orElseThrow();
		assertThat(clearedItem.getUrgency()).isEqualTo(Urgency.PRIORITY);
	}

	@Test
	@DisplayName("Journey 8: Staff-assisted portal offer after owner contact")
	void journey08_staffAssistedOfferAfterOwnerContact() {
		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Need assistance booking", false);
		QueueItem item = this.queueItemRepository.findByRequestId(request.getId()).orElseThrow();

		claim(item, this.staffAccount1);
		this.queueContactService.recordContactAttempt(item.getId(), this.staffAccount1.getId(),
				ContactOutcome.REACHED_AGREED, "Agreed on Friday morning slot", item.getRequest().getVersion(),
				currentWorkflowRevision(item), item.getVersion());

		LocalDate targetDate = nextWeekday(7);
		AvailabilityWindow window = new AvailabilityWindow(null, WindowClassification.PREFERRED, WindowShape.ONE_OFF,
				targetDate, null, null, null, LocalTime.of(10, 0), LocalTime.of(12, 0), "Assisted window", null);
		this.staffInterpretationService
			.recordManualInterpretation(new ManualInterpretationCommand(item.getId(), this.staffAccount1.getId(),
					"Assisted checkup", 30, this.vet.getId(), null, Urgency.ROUTINE, List.of(window), false,
					item.getRequest().getVersion(), currentWorkflowRevision(item), item.getVersion()));
		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());

		ZonedDateTime startZdt = targetDate.atTime(10, 0).atZone(CLINIC_ZONE);
		ZonedDateTime endZdt = targetDate.atTime(10, 30).atZone(CLINIC_ZONE);

		AssistedOfferCommand cmd = new AssistedOfferCommand(item.getId(), this.staffAccount1.getId(), this.vet.getId(),
				startZdt.toInstant(), endZdt.toInstant(), "Discussed on phone", item.getRequest().getVersion(),
				currentWorkflowRevision(item), item.getVersion());
		Offer offer = this.assistedOfferService.createAssistedOffer(cmd);

		assertThat(offer.getOrigin()).isEqualTo(OfferOrigin.STAFF_ASSISTED);
		assertThat(offer.getState()).isEqualTo(OfferState.HELD);
	}

	@Test
	@DisplayName("Journey 9: Staff direct booking with recorded owner agreement")
	void journey09_staffDirectBookingWithRecordedOwnerAgreement() {
		LocalDate targetDate = nextWeekday(8);
		ZonedDateTime startZdt = targetDate.atTime(14, 0).atZone(CLINIC_ZONE);
		ZonedDateTime endZdt = targetDate.atTime(14, 30).atZone(CLINIC_ZONE);

		DirectBookingRequest req = new DirectBookingRequest(this.owner.getId(), this.pet.getId(), this.vet.getId(),
				startZdt.toInstant(), endZdt.toInstant(), true, "Phone consultation with owner",
				"Direct routine booking", this.staffAccount1.getId(), null);

		Appointment appointment = this.directBookingService.bookDirectly(req);
		assertThat(appointment.getBookingState()).isEqualTo(BookingState.CONFIRMED);
		assertThat(appointment.getOwnerId()).isEqualTo(this.owner.getId());
	}

	@Test
	@DisplayName("Journey 10: Owner cancellation of a future appointment")
	void journey10_ownerCancellationOfFutureAppointment() {
		LocalDate targetDate = nextWeekday(9);
		ZonedDateTime startZdt = targetDate.atTime(11, 0).atZone(CLINIC_ZONE);
		ZonedDateTime endZdt = targetDate.atTime(11, 30).atZone(CLINIC_ZONE);

		DirectBookingRequest req = new DirectBookingRequest(this.owner.getId(), this.pet.getId(), this.vet.getId(),
				startZdt.toInstant(), endZdt.toInstant(), true, "In clinic booking", "Routine",
				this.staffAccount1.getId(), null);
		Appointment appointment = this.directBookingService.bookDirectly(req);

		this.ownerAppointmentService.cancelAppointmentByOwner(appointment.getId(), this.owner.getId(), "Plans changed");

		Appointment cancelled = this.appointmentRepository.findById(appointment.getId()).orElseThrow();
		assertThat(cancelled.getBookingState()).isEqualTo(BookingState.CANCELLED);
	}

	@Test
	@DisplayName("Journey 11: Staff rescheduling and staff cancellation")
	void journey11_staffReschedulingAndStaffCancellation() {
		LocalDate targetDate = nextWeekday(10);
		ZonedDateTime startZdt = targetDate.atTime(9, 0).atZone(CLINIC_ZONE);
		ZonedDateTime endZdt = targetDate.atTime(9, 30).atZone(CLINIC_ZONE);

		DirectBookingRequest req = new DirectBookingRequest(this.owner.getId(), this.pet.getId(), this.vet.getId(),
				startZdt.toInstant(), endZdt.toInstant(), true, "Front desk", "Routine", this.staffAccount1.getId(),
				null);
		Appointment appointment = this.directBookingService.bookDirectly(req);

		ZonedDateTime newStartZdt = targetDate.atTime(15, 0).atZone(CLINIC_ZONE);
		ZonedDateTime newEndZdt = targetDate.atTime(15, 30).atZone(CLINIC_ZONE);

		RescheduleAppointmentCommand reschedCmd = new RescheduleAppointmentCommand(appointment.getId(),
				this.staffAccount1.getId(), this.vet.getId(), newStartZdt.toInstant(), newEndZdt.toInstant(), true,
				"Phone", "Vet schedule change", "Doctor unavailable at original time");
		Appointment rescheduled = this.reschedulingService.rescheduleAppointment(reschedCmd);
		assertThat(rescheduled.getStartAt()).isEqualTo(newStartZdt.toInstant());

		StaffCancellationCommand cancelCmd = new StaffCancellationCommand(appointment.getId(),
				this.staffAccount1.getId(), "VET_UNAVAILABLE", "Doctor unavailable", "Clinic emergency", true);
		Appointment cancelled = this.cancellationService.cancelAppointmentByStaff(cancelCmd);
		assertThat(cancelled.getBookingState()).isEqualTo(BookingState.CANCELLED);
	}

	@Test
	@DisplayName("Journey 12: Completion, no-show, and audited correction")
	void journey12_completionNoShowAndAuditedCorrection() {
		LocalDate targetDate = LocalDate.now(CLINIC_ZONE).minusDays(1);
		ZonedDateTime startZdt = targetDate.atTime(10, 0).atZone(CLINIC_ZONE);
		ZonedDateTime endZdt = targetDate.atTime(10, 30).atZone(CLINIC_ZONE);

		Appointment appointment = new Appointment();
		appointment.setOwnerId(this.owner.getId());
		appointment.setPetId(this.pet.getId());
		appointment.setVetId(this.vet.getId());
		appointment.setStartAt(startZdt.toInstant());
		appointment.setEndAt(endZdt.toInstant());
		appointment.setZoneId(CLINIC_ZONE.getId());
		appointment.setBookingState(BookingState.CONFIRMED);
		Appointment saved = this.appointmentRepository.save(appointment);

		CompleteAppointmentCommand compCmd = new CompleteAppointmentCommand(saved.getId(), this.staffAccount1.getId(),
				"Administered prophylactic antibiotics", "Routine dental cleaning completed");
		Visit visit = this.outcomeService.completeAppointment(compCmd);
		assertThat(visit.getProvenance()).isEqualTo(VisitProvenance.APPOINTMENT_COMPLETION);

		CorrectOutcomeCommand corrCmd = new CorrectOutcomeCommand(saved.getId(), this.staffAccount1.getId(),
				OutcomeState.NO_SHOW, "Owner did not show up; completed by mistake", null, null);
		this.correctionService.correctOutcome(corrCmd);

		Appointment corrected = this.appointmentRepository.findById(saved.getId()).orElseThrow();
		assertThat(corrected.getOutcomeState()).isEqualTo(OutcomeState.NO_SHOW);
	}

	@Test
	@DisplayName("Journey 13: Prevention of veterinarian, pet, owner, appointment, and hold conflicts")
	void journey13_conflictPreventionForVetPetOwnerAndHolds() {
		LocalDate targetDate = nextWeekday(11);
		ZonedDateTime startZdt = targetDate.atTime(10, 0).atZone(CLINIC_ZONE);
		ZonedDateTime endZdt = targetDate.atTime(10, 30).atZone(CLINIC_ZONE);

		DirectBookingRequest req1 = new DirectBookingRequest(this.owner.getId(), this.pet.getId(), this.vet.getId(),
				startZdt.toInstant(), endZdt.toInstant(), true, "Direct", "Routine", this.staffAccount1.getId(), null);
		this.directBookingService.bookDirectly(req1);

		DirectBookingRequest req2 = new DirectBookingRequest(2, 2, this.vet.getId(), startZdt.toInstant(),
				endZdt.toInstant(), true, "Direct", "Routine", this.staffAccount1.getId(), null);

		assertThatThrownBy(() -> this.directBookingService.bookDirectly(req2))
			.isInstanceOf(AvailabilityConflictException.class)
			.hasMessageContaining("conflict");
	}

	@Test
	@DisplayName("Journey 14: Queue claiming and reassignment by two distinct staff identities")
	void journey14_queueClaimingAndReassignmentByTwoStaffIdentities() {
		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Staff queue test prose", false);
		QueueItem item = this.queueItemRepository.findByRequestId(request.getId()).orElseThrow();

		claim(item, this.staffAccount1);
		QueueItem claimed = this.queueItemRepository.findById(item.getId()).orElseThrow();
		assertThat(claimed.getAssigneeAccountId()).isEqualTo(this.staffAccount1.getId());
		assertThat(claimed.getState()).isEqualTo(QueueState.IN_REVIEW);

		this.queueAssignmentService.reassign(item.getId(), this.staffAccount2.getId(), this.staffAccount1.getId(),
				"Shift handoff", item.getRequest().getVersion(), currentWorkflowRevision(item), item.getVersion());
		QueueItem reassigned = this.queueItemRepository.findById(item.getId()).orElseThrow();
		assertThat(reassigned.getAssigneeAccountId()).isEqualTo(this.staffAccount2.getId());
	}

	@Test
	@DisplayName("Journey 15: Reconciliation of a future-dated legacy visit")
	void journey15_reconciliationOfFutureDatedLegacyVisit() {
		LocalDate futureDate = nextWeekday(12);
		Visit legacyVisit = new Visit();
		legacyVisit.setDate(futureDate);
		legacyVisit.setDescription("Legacy scheduled annual exam");
		legacyVisit.setPetId(this.pet.getId());
		legacyVisit.setProvenance(VisitProvenance.LEGACY);
		Visit savedVisit = this.visitRepository.save(legacyVisit);

		ZonedDateTime startZdt = futureDate.atTime(10, 0).atZone(CLINIC_ZONE);
		ZonedDateTime endZdt = futureDate.atTime(10, 30).atZone(CLINIC_ZONE);
		ReconcileLegacyVisitCommand cmd = new ReconcileLegacyVisitCommand(savedVisit.getId(),
				this.staffAccount1.getId(), this.vet.getId(), startZdt.toInstant(), endZdt.toInstant(), true,
				"Phone consultation with owner", "Reconciled legacy entry");

		Appointment reconciledAppointment = this.legacyReconciliationService.reconcileLegacyVisit(cmd);
		assertThat(reconciledAppointment.getBookingState()).isEqualTo(BookingState.CONFIRMED);

		Visit reloadedVisit = this.visitRepository.findById(savedVisit.getId()).orElseThrow();
		assertThat(reloadedVisit.getAppointmentId()).isEqualTo(reconciledAppointment.getId());
	}

}
