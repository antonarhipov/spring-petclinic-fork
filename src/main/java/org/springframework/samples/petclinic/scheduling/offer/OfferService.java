package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEvent;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEventRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.request.ActiveSchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {

	private static final Logger log = LoggerFactory.getLogger(OfferService.class);

	private final OfferRepository offerRepository;

	private final SchedulingRequestRepository requestRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final ActiveSchedulingRequestRepository activeRequestRepository;

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final QueueItemRepository queueItemRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final CapacityConflictService capacityConflictService;

	private final ClinicPolicyRepository clinicPolicyRepository;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public OfferService(OfferRepository offerRepository, SchedulingRequestRepository requestRepository,
			WorkflowRevisionRepository workflowRevisionRepository,
			ActiveSchedulingRequestRepository activeRequestRepository, AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository changeEventRepository, QueueItemRepository queueItemRepository,
			CalendarMutationCoordinator calendarCoordinator, CapacityConflictService capacityConflictService,
			ClinicPolicyRepository clinicPolicyRepository, OwnerHistoryService ownerHistoryService,
			AuditService auditService, Clock clock) {
		this.offerRepository = offerRepository;
		this.requestRepository = requestRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.activeRequestRepository = activeRequestRepository;
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.queueItemRepository = queueItemRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.capacityConflictService = capacityConflictService;
		this.clinicPolicyRepository = clinicPolicyRepository;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional
	public Offer createHeldOffer(SchedulingRequest request, WorkflowRevision workflowRevision, CandidateSlot slot,
			OfferOrigin origin, long calendarRevision, String matchExplanation) {
		Long requestId = request.getId();
		Long workflowRevisionId = workflowRevision.getId();
		request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Scheduling request not found: " + requestId));
		workflowRevision = this.workflowRevisionRepository.findById(workflowRevisionId)
			.orElseThrow(() -> new IllegalArgumentException("Workflow revision not found: " + workflowRevisionId));
		if (request.getCurrentWorkflowRevision() == null
				|| !request.getCurrentWorkflowRevision().getId().equals(workflowRevision.getId())
				|| request.getState() != RequestState.READY_TO_MATCH) {
			throw new IllegalStateException("Matching result is stale for the current request state");
		}
		SchedulingRequest currentRequest = request;
		WorkflowRevision currentWorkflowRevision = workflowRevision;
		return this.calendarCoordinator.executeWithLock(() -> {
			Instant now = this.clock.instant();
			RequestState priorRequestState = currentRequest.getState();
			if (this.calendarCoordinator.getCurrentRevision() != calendarRevision) {
				throw new AvailabilityConflictException("Calendar changed after the matching snapshot was created");
			}
			if (this.capacityConflictService.hasOverlappingBlocker(slot.getVetId(), currentRequest.getPetId(),
					currentRequest.getOwnerId(), slot.getStartAt(), slot.getEndAt())) {
				throw new AvailabilityConflictException("Selected candidate slot is no longer available");
			}

			int holdDurationMinutes = this.clinicPolicyRepository.findSingleton()
				.orElseThrow(() -> new IllegalStateException("Clinic policy not initialized"))
				.getHoldDurationMinutes();
			Instant expiresAt = now.plusSeconds(holdDurationMinutes * 60L);
			int nextAttempt = (currentWorkflowRevision.getAutomaticOfferCount() != null
					? currentWorkflowRevision.getAutomaticOfferCount() : 0) + 1;
			currentWorkflowRevision.setAutomaticOfferCount(nextAttempt);
			this.workflowRevisionRepository.save(currentWorkflowRevision);

			Offer offer = new Offer(currentRequest, currentWorkflowRevision, currentRequest.getOwnerId(),
					currentRequest.getPetId(), slot.getVetId(), origin, slot.getStartAt(), slot.getEndAt(),
					slot.getZoneId(), expiresAt, OfferState.HELD, nextAttempt, calendarRevision, matchExplanation);
			Offer savedOffer = this.offerRepository.save(offer);

			currentRequest.setState(RequestState.OFFERED);
			currentRequest.setUpdatedAt(now);
			this.requestRepository.save(currentRequest);

			this.ownerHistoryService.recordOwnerHistory(currentRequest.getOwnerId(), currentRequest.getPetId(),
					currentRequest.getId(), "OFFER_CREATED", "Appointment offer held until " + expiresAt, null);
			this.auditService.recordStructuredEvent(null, "MATCHING_OFFER_CREATED", "Offer",
					savedOffer.getId().toString(), "SUCCESS", null, null,
					Map.of("calendarRevision", calendarRevision, "requestState", priorRequestState.name()),
					Map.of("offerState", savedOffer.getState().name(), "requestState", currentRequest.getState().name(),
							"vetId", savedOffer.getVetId(), "startAt", savedOffer.getStartAt(), "endAt",
							savedOffer.getEndAt(), "expiresAt", savedOffer.getExpiresAt()));

			log.info("Created held offer {} for request {} (expires at {})", savedOffer.getId(), currentRequest.getId(),
					expiresAt);
			return savedOffer;
		});
	}

	@Transactional
	public Appointment acceptOffer(Long offerId, Integer ownerId) {
		return this.calendarCoordinator.executeWithLock(() -> {
			Instant now = this.clock.instant();
			Offer offer = this.offerRepository.findByIdAndOwnerId(offerId, ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Offer not found for owner"));
			OfferState priorOfferState = offer.getState();

			if (offer.getState() != OfferState.HELD) {
				throw new IllegalStateException("Offer is not in HELD state: " + offer.getState());
			}
			if (offer.getExpiresAt().isBefore(now)) {
				offer.setState(OfferState.EXPIRED);
				this.offerRepository.save(offer);
				throw new IllegalStateException("Offer has expired");
			}

			SchedulingRequest request = offer.getRequest();
			RequestState priorRequestState = request.getState();

			// Remove this offer from the active-hold blocker query while validating the
			// acceptance. The calendar lock and surrounding transaction make this
			// intermediate state invisible to competing mutations and roll it back on
			// conflict.
			offer.setState(OfferState.ACCEPTED);
			this.offerRepository.saveAndFlush(offer);
			if (this.capacityConflictService.hasOverlappingBlocker(offer.getVetId(), offer.getPetId(),
					offer.getOwnerId(), offer.getStartAt(), offer.getEndAt())) {
				throw new AvailabilityConflictException("Capacity conflict detected during offer acceptance");
			}

			Appointment appointment = new Appointment(offer.getOwnerId(), offer.getPetId(), offer.getVetId(),
					offer.getStartAt(), offer.getEndAt(), offer.getZoneId());
			appointment.setOriginatingRequestId(request.getId());
			appointment.setOriginatingOfferId(offer.getId());
			Appointment savedAppointment = this.appointmentRepository.save(appointment);

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(savedAppointment.getId(), null,
					"ROLE_OWNER", "OFFER_ACCEPTED", true, "ONLINE");
			this.changeEventRepository.save(changeEvent);

			offer.setState(OfferState.ACCEPTED);
			offer.setAppointmentId(savedAppointment.getId());
			this.offerRepository.save(offer);

			request.setState(RequestState.CONFIRMED);
			request.setAppointmentId(savedAppointment.getId());
			request.setClosedAt(now);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			this.activeRequestRepository.deleteByRequestId(request.getId());

			this.queueItemRepository.findByRequestId(request.getId()).ifPresent(q -> {
				q.setState(QueueState.RESOLVED);
				q.setResolvedAt(now);
				q.setAwaitingReason(null);
				q.setUpdatedAt(now);
				this.queueItemRepository.save(q);
			});

			this.ownerHistoryService.recordOwnerHistory(ownerId, offer.getPetId(), request.getId(),
					"APPOINTMENT_CONFIRMED", "Appointment accepted and confirmed for " + offer.getStartAt(), null);
			this.auditService.recordStructuredEvent(null, "OFFER_ACCEPTED", "Offer", offer.getId().toString(),
					"SUCCESS", null, null,
					Map.of("offerState", priorOfferState.name(), "requestState", priorRequestState.name()),
					Map.of("offerState", offer.getState().name(), "requestState", request.getState().name(),
							"appointmentId", savedAppointment.getId(), "startAt", savedAppointment.getStartAt(),
							"endAt", savedAppointment.getEndAt(), "vetId", savedAppointment.getVetId()));

			log.info("Accepted offer {} and confirmed appointment {}", offer.getId(), savedAppointment.getId());
			return savedAppointment;
		});
	}

}
