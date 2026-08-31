package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StaffInterpretationService {

	private static final Logger log = LoggerFactory.getLogger(StaffInterpretationService.class);

	private final QueueItemRepository queueItemRepository;

	private final SchedulingRequestRepository requestRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final BackgroundJobRepository jobRepository;

	private final ProtectedPayloadService payloadService;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public StaffInterpretationService(QueueItemRepository queueItemRepository,
			SchedulingRequestRepository requestRepository, WorkflowRevisionRepository workflowRevisionRepository,
			BackgroundJobRepository jobRepository, ProtectedPayloadService payloadService, AuditService auditService,
			OwnerHistoryService ownerHistoryService, Clock clock) {
		this.queueItemRepository = queueItemRepository;
		this.requestRepository = requestRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.jobRepository = jobRepository;
		this.payloadService = payloadService;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	public record ManualInterpretationCommand(Long queueItemId, Long actorAccountId, String visitReason,
			Integer durationMinutes, Integer preferredVetId, Integer requiredSpecialtyId, Urgency urgency,
			List<AvailabilityWindow> windows, boolean requestOwnerConfirmation) {
	}

	public WorkflowRevision recordManualInterpretation(ManualInterpretationCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.queueItemId(), "queueItemId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");
		Objects.requireNonNull(cmd.urgency(), "urgency must not be null");

		QueueItem queueItem = this.queueItemRepository.findById(cmd.queueItemId())
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + cmd.queueItemId()));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException("Cannot interpret inactive queue item in state: " + queueItem.getState());
		}

		SchedulingRequest request = queueItem.getRequest();
		Instant now = this.clock.instant();

		WorkflowRevision priorRevision = request.getCurrentWorkflowRevision();
		if (priorRevision != null) {
			priorRevision.setState(WorkflowRevisionState.SUPERSEDED);
			this.workflowRevisionRepository.save(priorRevision);
		}

		ProtectedPayload reasonPayload = null;
		if (cmd.visitReason() != null && !cmd.visitReason().isBlank()) {
			reasonPayload = this.payloadService.store(UUID.randomUUID(), "VISIT_REASON", 1, "text/plain",
					cmd.visitReason().trim());
		}

		int nextRevisionNumber = priorRevision != null ? priorRevision.getRevisionNumber() + 1 : 1;
		WorkflowRevisionState state = cmd.requestOwnerConfirmation() ? WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED
				: WorkflowRevisionState.CONFIRMED;

		WorkflowRevision newRevision = new WorkflowRevision(request, nextRevisionNumber, state, reasonPayload,
				cmd.durationMinutes() != null ? cmd.durationMinutes() : 30, cmd.preferredVetId(),
				cmd.requiredSpecialtyId(), cmd.urgency(), now);

		if (state == WorkflowRevisionState.CONFIRMED) {
			newRevision.setConfirmedAt(now);
		}

		if (cmd.windows() != null) {
			for (AvailabilityWindow window : cmd.windows()) {
				newRevision.addAvailabilityWindow(window);
			}
		}

		WorkflowRevision savedRevision = this.workflowRevisionRepository.save(newRevision);

		request.setCurrentWorkflowRevision(savedRevision);
		queueItem.setWorkflowRevision(savedRevision);
		queueItem.setUrgency(cmd.urgency());

		if (cmd.requestOwnerConfirmation()) {
			request.setState(RequestState.AWAITING_REVIEW);
			queueItem.setState(QueueState.AWAITING_OWNER);
			queueItem.setAwaitingReason(AwaitingReason.INTERPRETATION_CONFIRMATION);
		}
		else {
			if (cmd.urgency() == Urgency.ROUTINE) {
				request.setState(RequestState.READY_TO_MATCH);
				queueItem.setState(QueueState.IN_REVIEW);
				queueItem.setAwaitingReason(null);
				BackgroundJob job = new BackgroundJob(JobType.MATCHING, null, savedRevision, JobState.PENDING, now);
				this.jobRepository.save(job);
			}
			else {
				request.setState(RequestState.STAFF_HANDLING);
				queueItem.setState(QueueState.IN_REVIEW);
				queueItem.setAwaitingReason(null);
			}
		}

		request.setUpdatedAt(now);
		queueItem.setUpdatedAt(now);
		this.requestRepository.save(request);
		this.queueItemRepository.save(queueItem);

		this.auditService.recordEvent(cmd.actorAccountId(), "STAFF_MANUAL_INTERPRETATION", "QueueItem",
				queueItem.getId().toString(), "SUCCESS", UUID.randomUUID(), null,
				reasonPayload != null ? reasonPayload.getId() : null);

		this.ownerHistoryService.recordOwnerHistory(request.getOwnerId(), request.getPetId(), request.getId(),
				"MANUAL_INTERPRETATION_SAVED", "Clinic staff updated appointment interpretation details", null);

		log.info("Recorded staff manual interpretation for queue item {} (request {}, new revision {})",
				queueItem.getId(), request.getId(), savedRevision.getId());
		return savedRevision;
	}

}
