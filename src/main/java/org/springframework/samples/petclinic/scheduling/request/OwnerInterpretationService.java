package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerInterpretationService {

	private static final Logger log = LoggerFactory.getLogger(OwnerInterpretationService.class);

	private final SchedulingRequestRepository requestRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final BackgroundJobRepository jobRepository;

	private final QueueItemRepository queueItemRepository;

	private final ProtectedPayloadService payloadService;

	private final StaffFallbackPort staffFallbackPort;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public OwnerInterpretationService(SchedulingRequestRepository requestRepository,
			WorkflowRevisionRepository workflowRevisionRepository, BackgroundJobRepository jobRepository,
			QueueItemRepository queueItemRepository, ProtectedPayloadService payloadService,
			StaffFallbackPort staffFallbackPort, OwnerHistoryService ownerHistoryService, Clock clock) {
		this.requestRepository = requestRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.jobRepository = jobRepository;
		this.queueItemRepository = queueItemRepository;
		this.payloadService = payloadService;
		this.staffFallbackPort = staffFallbackPort;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<InterpretationReviewDto> getInterpretationForReview(Long requestId, Integer ownerId) {
		return this.requestRepository.findByIdAndOwnerId(requestId, ownerId).map(request -> {
			String prose = "";
			if (request.getCurrentTextRevision() != null
					&& request.getCurrentTextRevision().getProsePayload() != null) {
				prose = this.payloadService.decrypt(request.getCurrentTextRevision().getProsePayload(), String.class);
			}

			WorkflowRevision workflowRev = request.getCurrentWorkflowRevision();
			String reason = "";
			if (workflowRev != null && workflowRev.getReasonPayload() != null) {
				reason = this.payloadService.decrypt(workflowRev.getReasonPayload(), String.class);
			}

			return new InterpretationReviewDto(request.getId(), request.getPetId(), request.getState(), prose,
					workflowRev != null ? workflowRev.getId() : null, reason,
					workflowRev != null ? workflowRev.getDurationMinutes() : null,
					workflowRev != null ? workflowRev.getPreferredVetId() : null,
					workflowRev != null ? workflowRev.getRequiredSpecialtyId() : null,
					workflowRev != null ? workflowRev.getUrgency() : Urgency.ROUTINE,
					workflowRev != null ? List.copyOf(workflowRev.getAvailabilityWindows()) : List.of());
		});
	}

	@Transactional
	public void confirmInterpretation(Long requestId, Integer ownerId) {
		SchedulingRequest request = this.requestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

		if (request.getState() != RequestState.AWAITING_REVIEW) {
			throw new IllegalStateException("Request is not awaiting review: " + request.getState());
		}

		WorkflowRevision workflowRev = request.getCurrentWorkflowRevision();
		if (workflowRev == null) {
			throw new IllegalStateException("No workflow revision found to confirm");
		}

		Instant now = this.clock.instant();
		workflowRev.setState(WorkflowRevisionState.CONFIRMED);
		workflowRev.setConfirmedAt(now);
		this.workflowRevisionRepository.save(workflowRev);

		if (workflowRev.getUrgency() == Urgency.ROUTINE) {
			request.setState(RequestState.READY_TO_MATCH);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			this.queueItemRepository.findByRequestId(requestId).ifPresent(q -> {
				if (q.getState() == QueueState.AWAITING_OWNER) {
					q.setState(QueueState.IN_REVIEW);
					q.setAwaitingReason(null);
					q.setUpdatedAt(now);
					this.queueItemRepository.save(q);
				}
			});

			BackgroundJob job = new BackgroundJob(JobType.MATCHING, null, workflowRev, JobState.PENDING, now);
			this.jobRepository.save(job);
			log.info("Interpretation confirmed as ROUTINE. Enqueued matching job for request {}", requestId);
		}
		else {
			request.setState(RequestState.STAFF_HANDLING);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			this.queueItemRepository.findByRequestId(requestId).ifPresentOrElse(q -> {
				q.setState(QueueState.IN_REVIEW);
				q.setAwaitingReason(null);
				q.setUrgency(workflowRev.getUrgency());
				q.setUpdatedAt(now);
				this.queueItemRepository.save(q);
			}, () -> {
				this.staffFallbackPort.sendToFallbackQueue(requestId, "URGENCY_" + workflowRev.getUrgency(),
						workflowRev.getUrgency(), "Owner confirmed non-routine request");
			});
			log.info("Interpretation confirmed as {}. Routed to staff queue for request {}", workflowRev.getUrgency(),
					requestId);
		}

		this.ownerHistoryService.recordOwnerHistory(ownerId, request.getPetId(), requestId, "INTERPRETATION_CONFIRMED",
				"Structured details confirmed by owner with urgency: " + workflowRev.getUrgency(), null);
	}

	public record InterpretationReviewDto(Long requestId, Integer petId, RequestState state, String originalProse,
			Long workflowRevisionId, String visitReason, Integer durationMinutes, Integer preferredVetId,
			Integer requiredSpecialtyId, Urgency urgency, List<AvailabilityWindow> windows) {
	}

}
