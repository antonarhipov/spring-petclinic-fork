package org.springframework.samples.petclinic.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.audit.AuditEventRepository;
import org.springframework.samples.petclinic.availability.AvailabilityExceptionDay;
import org.springframework.samples.petclinic.availability.AvailabilityExceptionDayRepository;
import org.springframework.samples.petclinic.availability.AvailabilityExceptionInterval;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
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
import org.springframework.samples.petclinic.scheduling.queue.QueueDirectBookingService;
import org.springframework.samples.petclinic.scheduling.queue.QueueDirectBookingService.QueueDirectBookCommand;
import org.springframework.samples.petclinic.scheduling.queue.QueueItem;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.ActiveSchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = { "petclinic.jobs.enabled=false" })
@Transactional
class StaffFallbackJourneyTests {

	@Autowired
	private SchedulingRequestService requestService;

	@Autowired
	private OwnerInterpretationService ownerInterpretationService;

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
	private QueueDirectBookingService queueDirectBookingService;

	@Autowired
	private OfferService offerService;

	@Autowired
	private QueueItemRepository queueItemRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private ActiveSchedulingRequestRepository activeRequestRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private ClinicPolicyRepository clinicPolicyRepository;

	@Autowired
	private AvailabilityExceptionDayRepository exceptionDayRepository;

	@Autowired
	private EffectiveAvailabilityService effectiveAvailabilityService;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	private Account staffAccount;

	@BeforeEach
	void setUp() {
		this.owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = this.owner.getPets().get(0);
		this.vet = this.vetRepository.findAll().get(0);
		this.staffAccount = this.accountRepository.findByUsername("staff").orElseGet(() -> {
			Account newAccount = new Account();
			newAccount.setUsername("staff");
			newAccount.setPasswordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoOhi59/U1J4hVp5sLzXN0x/o3jU5Q1qiq");
			newAccount.setRole(Role.STAFF);
			newAccount.setEnabled(true);
			newAccount.setPasswordChangeRequired(false);
			newAccount.setSessionVersion(0L);
			return this.accountRepository.save(newAccount);
		});

		LocalDate nextMonday = LocalDate.now().plusWeeks(1);
		while (nextMonday.getDayOfWeek().getValue() != 1) {
			nextMonday = nextMonday.plusDays(1);
		}

		AvailabilityExceptionDay exceptionDay = new AvailabilityExceptionDay(this.vet.getId(), nextMonday);
		exceptionDay.addInterval(new AvailabilityExceptionInterval(LocalTime.of(8, 0), LocalTime.of(18, 0)));
		this.exceptionDayRepository.save(exceptionDay);
	}

	@Test
	void journey1_declinedConsent_staffAssistedOffer_ownerAcceptance() {
		// 1. Owner submits with AI consent declined
		SchedulingRequest request = this.requestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Please check my dog's ears", false);

		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);

		QueueItem queueItem = this.queueItemRepository.findByRequestId(request.getId()).orElseThrow();
		assertThat(queueItem.getState()).isEqualTo(QueueState.NEW);
		assertThat(queueItem.getFallbackReason()).isEqualTo("DECLINED_AI_CONSENT");

		// 2. Staff claims queue item
		this.queueAssignmentService.claim(queueItem.getId(), this.staffAccount.getId());
		queueItem = this.queueItemRepository.findById(queueItem.getId()).orElseThrow();
		assertThat(queueItem.getState()).isEqualTo(QueueState.IN_REVIEW);
		assertThat(queueItem.getAssigneeAccountId()).isEqualTo(this.staffAccount.getId());

		// 3. Staff logs contact attempt
		this.queueContactService.recordContactAttempt(queueItem.getId(), this.staffAccount.getId(),
				ContactOutcome.REACHED_AGREED, "Spoke with owner, agreed to Tuesday morning slot");

		// 4. Staff manually structures interpretation
		this.staffInterpretationService.recordManualInterpretation(
				new StaffInterpretationService.ManualInterpretationCommand(queueItem.getId(), this.staffAccount.getId(),
						"Ear infection check", 30, this.vet.getId(), null, Urgency.ROUTINE, null, false));

		// 5. Staff creates assisted offer hold
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		LocalDate nextMonday = LocalDate.now().plusWeeks(1);
		while (nextMonday.getDayOfWeek().getValue() != 1) {
			nextMonday = nextMonday.plusDays(1);
		}
		Instant startAt = ZonedDateTime.of(nextMonday, LocalTime.of(9, 0), zoneId).toInstant();
		Instant endAt = startAt.plusSeconds(1800);

		Offer offer = this.assistedOfferService.createAssistedOffer(new AssistedOfferCommand(queueItem.getId(),
				this.staffAccount.getId(), this.vet.getId(), startAt, endAt, "Held slot per phone discussion"));

		assertThat(offer.getOrigin()).isEqualTo(OfferOrigin.STAFF_ASSISTED);
		assertThat(offer.getState()).isEqualTo(OfferState.HELD);

		request = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.OFFERED);

		// 6. Owner accepts offer in portal
		Appointment appointment = this.offerService.acceptOffer(offer.getId(), this.owner.getId());

		assertThat(appointment).isNotNull();
		assertThat(appointment.getVetId()).isEqualTo(this.vet.getId());

		request = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.CONFIRMED);

		queueItem = this.queueItemRepository.findById(queueItem.getId()).orElseThrow();
		assertThat(queueItem.getState()).isEqualTo(QueueState.RESOLVED);
	}

	@Test
	void journey2_emergencyKeyword_staffClearance_ownerConfirmation() {
		// 1. Owner submits emergency prose
		SchedulingRequest request = this.requestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"My pet is bleeding heavily from an open wound", true);

		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);

		QueueItem queueItem = this.queueItemRepository.findByRequestId(request.getId()).orElseThrow();
		assertThat(queueItem.getUrgency()).isEqualTo(Urgency.EMERGENCY_SUSPECTED);
		assertThat(queueItem.getFallbackReason()).isEqualTo("EMERGENCY_PROSE");

		// 2. Staff claims and clears emergency
		this.queueAssignmentService.claim(queueItem.getId(), this.staffAccount.getId());
		this.emergencyClearanceService.clearEmergency(queueItem.getId(), this.staffAccount.getId(), Urgency.ROUTINE,
				"Owner called back, minor scratch only and bleeding stopped");

		queueItem = this.queueItemRepository.findById(queueItem.getId()).orElseThrow();
		assertThat(queueItem.getUrgency()).isEqualTo(Urgency.ROUTINE);
		assertThat(queueItem.getState()).isEqualTo(QueueState.AWAITING_OWNER);

		request = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_REVIEW);

		// 3. Owner confirms cleared routine interpretation
		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());

		request = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.READY_TO_MATCH);
	}

	@Test
	void journey3_declinedConsent_staffDirectBooking() {
		// 1. Owner submits declined consent
		SchedulingRequest request = this.requestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Annual vaccination appointment needed", false);

		QueueItem queueItem = this.queueItemRepository.findByRequestId(request.getId()).orElseThrow();

		// 2. Staff directly books agreed slot from queue
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		LocalDate nextMonday = LocalDate.now().plusWeeks(1);
		while (nextMonday.getDayOfWeek().getValue() != 1) {
			nextMonday = nextMonday.plusDays(1);
		}
		Instant startAt = ZonedDateTime.of(nextMonday, LocalTime.of(10, 0), zoneId).toInstant();
		Instant endAt = startAt.plusSeconds(1800);

		Appointment appointment = this.queueDirectBookingService
			.directBookFromQueue(new QueueDirectBookCommand(queueItem.getId(), this.staffAccount.getId(),
					this.vet.getId(), startAt, endAt, true, "PHONE", "Agreed directly on phone"));

		assertThat(appointment).isNotNull();

		request = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.CONFIRMED);

		queueItem = this.queueItemRepository.findById(queueItem.getId()).orElseThrow();
		assertThat(queueItem.getState()).isEqualTo(QueueState.RESOLVED);
	}

}
