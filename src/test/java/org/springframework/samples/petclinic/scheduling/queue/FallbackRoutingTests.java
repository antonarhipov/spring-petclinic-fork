package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyKeywordScreen;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.request.ActiveSchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestService;
import org.springframework.samples.petclinic.scheduling.request.TextRevision;
import org.springframework.samples.petclinic.scheduling.request.TextRevisionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FallbackRoutingTests {

	@Mock
	private SchedulingRequestRepository requestRepository;

	@Mock
	private ActiveSchedulingRequestRepository activeRequestRepository;

	@Mock
	private TextRevisionRepository textRevisionRepository;

	@Mock
	private BackgroundJobRepository jobRepository;

	@Mock
	private OfferRepository offerRepository;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private ProtectedPayloadService payloadService;

	@Mock
	private StaffFallbackPort staffFallbackPort;

	@Mock
	private OwnerHistoryService ownerHistoryService;

	@Mock
	private AuditService auditService;

	@Mock
	private QueueItemRepository queueItemRepository;

	private final EmergencyKeywordScreen emergencyScreen = new EmergencyKeywordScreen();

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneId.of("UTC"));

	private SchedulingRequestService requestService;

	private Owner owner;

	private Pet pet;

	@BeforeEach
	void setUp() {
		this.requestService = new SchedulingRequestService(this.requestRepository, this.activeRequestRepository,
				this.textRevisionRepository, this.jobRepository, this.offerRepository, this.ownerRepository,
				this.payloadService, this.emergencyScreen, this.staffFallbackPort, this.ownerHistoryService,
				this.auditService, this.queueItemRepository, this.clock);

		this.owner = new Owner();
		this.owner.setId(1);
		this.owner.setFirstName("George");
		this.owner.setLastName("Franklin");

		this.pet = new Pet();
		this.pet.setId(1);
		this.pet.setName("Leo");
		PetType type = new PetType();
		type.setName("cat");
		this.pet.setType(type);
		this.owner.getPets().add(this.pet);
	}

	@Test
	void declinedConsentBypassesAiAndRoutesToStaffQueue() {
		when(this.ownerRepository.findById(1)).thenReturn(Optional.of(this.owner));
		when(this.activeRequestRepository.findById(1)).thenReturn(Optional.empty());

		ProtectedPayload dummyPayload = new ProtectedPayload();
		when(this.payloadService.encrypt(any())).thenReturn(dummyPayload);

		when(this.requestRepository.save(any(SchedulingRequest.class))).thenAnswer(invocation -> {
			SchedulingRequest req = invocation.getArgument(0);
			req.setId(100L);
			return req;
		});
		when(this.textRevisionRepository.save(any(TextRevision.class))).thenAnswer(invocation -> {
			TextRevision revision = invocation.getArgument(0);
			revision.setId(200L);
			return revision;
		});

		SchedulingRequest result = this.requestService.submitRequest(1, 1, "Routine vaccination please", false);

		assertThat(result.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		verify(this.jobRepository, never()).save(any(BackgroundJob.class));
		verify(this.staffFallbackPort).sendToFallbackQueue(eq(100L), eq("DECLINED_AI_CONSENT"), eq(Urgency.ROUTINE),
				eq("Owner declined automated AI interpretation"));
	}

	@Test
	void emergencyProseImmediatelyRoutesToStaffQueueWithEmergencyUrgency() {
		when(this.ownerRepository.findById(1)).thenReturn(Optional.of(this.owner));
		when(this.activeRequestRepository.findById(1)).thenReturn(Optional.empty());

		ProtectedPayload dummyPayload = new ProtectedPayload();
		when(this.payloadService.encrypt(any())).thenReturn(dummyPayload);

		when(this.requestRepository.save(any(SchedulingRequest.class))).thenAnswer(invocation -> {
			SchedulingRequest req = invocation.getArgument(0);
			req.setId(101L);
			return req;
		});
		when(this.textRevisionRepository.save(any(TextRevision.class))).thenAnswer(invocation -> {
			TextRevision revision = invocation.getArgument(0);
			revision.setId(201L);
			return revision;
		});

		SchedulingRequest result = this.requestService.submitRequest(1, 1, "My cat is bleeding heavily from an injury",
				true);

		assertThat(result.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		verify(this.jobRepository, never()).save(any(BackgroundJob.class));
		verify(this.staffFallbackPort).sendToFallbackQueue(eq(101L), eq("EMERGENCY_PROSE"),
				eq(Urgency.EMERGENCY_SUSPECTED), any());
	}

	@Test
	void fallbackServiceCreatesOrUpdatesQueueItemCorrectly() {
		QueueItemRepository queueItemRepository = org.mockito.Mockito.mock(QueueItemRepository.class);
		AuditService auditService = org.mockito.Mockito.mock(AuditService.class);

		FallbackService fallbackService = new FallbackService(this.requestRepository, queueItemRepository, auditService,
				this.ownerHistoryService, this.clock);

		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION,
				this.clock.instant());
		request.setId(200L);
		when(this.requestRepository.findById(200L)).thenReturn(Optional.of(request));
		when(queueItemRepository.findByRequestId(200L)).thenReturn(Optional.empty());
		when(queueItemRepository.save(any(QueueItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

		fallbackService.sendToFallbackQueue(200L, "UNUSABLE_OUTPUT", Urgency.ROUTINE, "AI failed twice");

		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		verify(queueItemRepository).save(any(QueueItem.class));
		verify(this.ownerHistoryService).recordOwnerHistory(eq(1), eq(1), eq(200L), eq("REQUEST_ROUTED_TO_STAFF"),
				eq("Request routed to clinic staff for assistance: UNUSABLE_OUTPUT"), any());
	}

}
