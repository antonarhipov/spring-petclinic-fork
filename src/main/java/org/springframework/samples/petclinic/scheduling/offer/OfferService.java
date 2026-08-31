package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEvent;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEventRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
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

	private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

	private final OfferRepository offerRepository;

	private final SchedulingRequestRepository requestRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final ActiveSchedulingRequestRepository activeRequestRepository;

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final QueueItemRepository queueItemRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final CapacityConflictService capacityConflictService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public OfferService(OfferRepository offerRepository, SchedulingRequestRepository requestRepository,
			WorkflowRevisionRepository workflowRevisionRepository,
			ActiveSchedulingRequestRepository activeRequestRepository, AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository changeEventRepository, QueueItemRepository queueItemRepository,
			CalendarMutationCoordinator calendarCoordinator, CapacityConflictService capacityConflictService,
			OwnerHistoryService ownerHistoryService, Clock clock) {
		this.offerRepository = offerRepository;
		this.requestRepository = requestRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.activeRequestRepository = activeRequestRepository;
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.queueItemRepository = queueItemRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.capacityConflictService = capacityConflictService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	@Transactional
	public Offer createHeldOffer(SchedulingRequest request, WorkflowRevision workflowRevision, CandidateSlot slot,
			OfferOrigin origin, long calendarRevision, String matchExplanation) {
		return this.calendarCoordinator.executeWithLock(() -> {
			Instant now = this.clock.instant();
			if (this.capacityConflictService.hasOverlappingBlocker(slot.getVetId(), request.getPetId(),
					request.getOwnerId(), slot.getStartAt(), slot.getEndAt())) {
				throw new AvailabilityConflictException("Selected candidate slot is no longer available");
			}

			Instant expiresAt = now.plus(HOLD_DURATION);
			int nextAttempt = (workflowRevision.getAutomaticOfferCount() != null
					? workflowRevision.getAutomaticOfferCount() : 0) + 1;
			workflowRevision.setAutomaticOfferCount(nextAttempt);
			this.workflowRevisionRepository.save(workflowRevision);

			Offer offer = new Offer(request, workflowRevision, request.getOwnerId(), request.getPetId(),
					slot.getVetId(), origin, slot.getStartAt(), slot.getEndAt(), slot.getZoneId(), expiresAt,
					OfferState.HELD, nextAttempt, calendarRevision, matchExplanation);
			Offer savedOffer = this.offerRepository.save(offer);

			request.setState(RequestState.OFFERED);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			this.ownerHistoryService.recordOwnerHistory(request.getOwnerId(), request.getPetId(), request.getId(),
					"OFFER_CREATED", "Appointment offer held until " + expiresAt, null);

			log.info("Created held offer {} for request {} (expires at {})", savedOffer.getId(), request.getId(),
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

			if (offer.getState() != OfferState.HELD) {
				throw new IllegalStateException("Offer is not in HELD state: " + offer.getState());
			}
			if (offer.getExpiresAt().isBefore(now)) {
				offer.setState(OfferState.EXPIRED);
				this.offerRepository.save(offer);
				throw new IllegalStateException("Offer has expired");
			}

			SchedulingRequest request = offer.getRequest();

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

			log.info("Accepted offer {} and confirmed appointment {}", offer.getId(), savedAppointment.getId());
			return savedAppointment;
		});
	}

}
