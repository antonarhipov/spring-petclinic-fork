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
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CalendarState;
import org.springframework.samples.petclinic.availability.CalendarStateRepository;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.AssistedOfferService.AssistedOfferCommand;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusion;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistedOfferServiceTests {

	@Mock
	private QueueItemRepository queueItemRepository;

	@Mock
	private SchedulingRequestRepository requestRepository;

	@Mock
	private OfferRepository offerRepository;

	@Mock
	private OfferExclusionRepository offerExclusionRepository;

	@Mock
	private CalendarStateRepository calendarStateRepository;

	@Mock
	private CapacityConflictService capacityConflictService;

	@Mock
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@Mock
	private VetRepository vetRepository;

	@Mock
	private AuditService auditService;

	@Mock
	private OwnerHistoryService ownerHistoryService;

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneId.of("UTC"));

	private AssistedOfferService assistedOfferService;

	private SchedulingRequest request;

	private WorkflowRevision workflowRevision;

	private QueueItem queueItem;

	private Vet vet;

	@BeforeEach
	void setUp() {
		CalendarMutationCoordinator calendarCoordinator = new CalendarMutationCoordinator(this.calendarStateRepository);

		this.assistedOfferService = new AssistedOfferService(this.queueItemRepository, this.requestRepository,
				this.offerRepository, this.offerExclusionRepository, calendarCoordinator, this.calendarStateRepository,
				this.capacityConflictService, this.effectiveAvailabilityService, this.vetRepository, this.auditService,
				this.ownerHistoryService, this.clock);

		this.request = new SchedulingRequest(1, 1, RequestState.STAFF_HANDLING, this.clock.instant());
		this.request.setId(100L);
		ProtectedPayload reasonPayload = new ProtectedPayload();
		reasonPayload.setId(1L);

		this.workflowRevision = new WorkflowRevision(this.request, 1, null, null,
				WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED, reasonPayload, 30, 1, null, Urgency.ROUTINE, 0);
		this.workflowRevision.setId(200L);
		this.request.setCurrentWorkflowRevision(this.workflowRevision);

		this.queueItem = new QueueItem(this.request, this.workflowRevision, QueueState.IN_REVIEW, "NO_MATCH",
				Urgency.ROUTINE, this.clock.instant());
		this.queueItem.setId(1L);
		this.queueItem.setAssigneeAccountId(10L);

		this.vet = new Vet();
		this.vet.setId(1);
		this.vet.setFirstName("James");
		this.vet.setLastName("Carter");
	}

	@Test
	void createAssistedOfferHoldsSlotWithoutConsumingAutomaticAttempt() {
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));
		when(this.vetRepository.findById(1)).thenReturn(Optional.of(this.vet));
		when(this.capacityConflictService.hasOverlappingBlocker(eq(1), eq(1), eq(1), any(), any())).thenReturn(false);

		CalendarState calendarState = new CalendarState();
		calendarState.setId(1);
		calendarState.setRevision(1L);
		calendarState.setUpdatedAt(this.clock.instant());
		when(this.calendarStateRepository.findSingletonForUpdate()).thenReturn(Optional.of(calendarState));

		ClinicPolicy policy = new ClinicPolicy();
		policy.setZoneId("America/New_York");
		when(this.effectiveAvailabilityService.getClinicPolicy()).thenReturn(policy);

		when(this.offerRepository.save(any(Offer.class))).thenAnswer(inv -> {
			Offer o = inv.getArgument(0);
			o.setId(501L);
			return o;
		});

		Instant startAt = Instant.parse("2026-08-31T14:00:00Z");
		Instant endAt = Instant.parse("2026-08-31T14:30:00Z");

		AssistedOfferCommand cmd = new AssistedOfferCommand(1L, 10L, 1, startAt, endAt, "Assisted slot offer");
		Offer offer = this.assistedOfferService.createAssistedOffer(cmd);

		assertThat(offer.getOrigin()).isEqualTo(OfferOrigin.STAFF_ASSISTED);
		assertThat(offer.getAutomaticAttemptNumber()).isNull();
		assertThat(offer.getState()).isEqualTo(OfferState.HELD);
		assertThat(this.request.getState()).isEqualTo(RequestState.OFFERED);
		assertThat(this.queueItem.getState()).isEqualTo(QueueState.AWAITING_OWNER);
		assertThat(this.queueItem.getAwaitingReason()).isEqualTo(AwaitingReason.PORTAL_OFFER);

		verify(this.auditService).recordEvent(eq(10L), eq("STAFF_ASSISTED_OFFER_CREATED"), eq("QueueItem"), eq("1"),
				eq("SUCCESS"), any(), any(), any());
	}

	@Test
	void handleAssistedOfferExpiryCreatesExclusionAndReturnsToInReview() {
		Offer offer = new Offer(this.request, this.workflowRevision, 1, 1, 1, OfferOrigin.STAFF_ASSISTED,
				Instant.parse("2026-08-31T14:00:00Z"), Instant.parse("2026-08-31T14:30:00Z"), "America/New_York",
				this.clock.instant().plusSeconds(600), OfferState.EXPIRED, null, 1L, "Explanation");
		offer.setId(502L);

		this.queueItem.setState(QueueState.AWAITING_OWNER);
		this.queueItem.setAwaitingReason(AwaitingReason.PORTAL_OFFER);
		this.request.setState(RequestState.OFFERED);

		when(this.offerRepository.findById(502L)).thenReturn(Optional.of(offer));
		when(this.queueItemRepository.findByRequestId(100L)).thenReturn(Optional.of(this.queueItem));

		this.assistedOfferService.handleAssistedOfferRejectionOrExpiry(502L);

		verify(this.offerExclusionRepository).save(any(OfferExclusion.class));
		assertThat(this.queueItem.getState()).isEqualTo(QueueState.IN_REVIEW);
		assertThat(this.queueItem.getAwaitingReason()).isNull();
		assertThat(this.request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
	}

}
